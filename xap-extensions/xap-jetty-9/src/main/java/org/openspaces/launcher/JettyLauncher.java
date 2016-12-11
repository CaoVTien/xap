/*
 * Copyright (c) 2008-2016, GigaSpaces Technologies, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openspaces.launcher;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.SimpleInstanceManager;
import org.eclipse.jetty.annotations.ServletContainerInitializersStarter;
import org.eclipse.jetty.apache.jsp.JettyJasperInitializer;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.jsp.JettyJspServlet;
import org.eclipse.jetty.plus.annotation.ContainerInitializer;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SessionManager;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Password;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.webapp.WebAppContext;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Niv Ingberg
 * @since 10.0.0
 */
public class JettyLauncher extends WebLauncher {

    private static final Log logger = LogFactory.getLog(JettyLauncher.class);

    @Override
    public void launch(WebLauncherConfig config) throws Exception {
        Server server = new Server();
        ServerConnector connector;

        //Set JSP to use Standard JavaC always
        System.setProperty("org.apache.jasper.compiler.disablejsr199","false");

        if( config.isSslEnabled() ){
            connector = createSslConnector(server,config);
        }
        else{
            connector = new ServerConnector(server);
        }
        connector.setPort(config.getPort());

        //GS-12102, fix for 10.1, added possibility to define host address
        if (config.getHostAddress() != null) {
            connector.setHost(config.getHostAddress());
        }
        connector.setReuseAddress(false);

        server.setConnectors(new Connector[]{connector});

        WebAppContext webAppContext = new WebAppContext();
        webAppContext.setContextPath("/");
        webAppContext.setWar(config.getWarFilePath());

        File tempDir = new File(config.getTempDirPath());
        boolean createdDirs = tempDir.mkdirs();

        if (logger.isDebugEnabled()) {
            boolean canRead = tempDir.canRead();
            boolean canWrite = tempDir.canWrite();
            boolean canExecute = tempDir.canExecute();

            logger.debug("Temp dir:" + tempDir.getName() + ", canRead=" + canRead + ", canWrite=" + canWrite +
                    ", canExecute=" + canExecute + ", exists=" + tempDir.exists() +
                    ", createdDirs=" + createdDirs + ", path=" + config.getTempDirPath());
        }

        webAppContext.setTempDirectory(tempDir);
        webAppContext.setCopyWebDir(false);
        webAppContext.setParentLoaderPriority(true);

        webAppContext.setAttribute("javax.servlet.context.tempdir",getScratchDir());
        webAppContext.setAttribute("org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern",
                ".*/[^/]*servlet-api-[^/]*\\.jar$|.*/javax.servlet.jsp.jstl-.*\\.jar$|.*/.*taglibs.*\\.jar$");

        webAppContext.setAttribute("org.eclipse.jetty.containerInitializers", jspInitializers());

        webAppContext.setAttribute(InstanceManager.class.getName(), new SimpleInstanceManager());

        webAppContext.addBean(new ServletContainerInitializersStarter(webAppContext), true);

        webAppContext.addServlet(jspServletHolder(), "*.jsp");

        String sessionManager = System.getProperty("org.openspaces.launcher.jetty.session.manager");
        if (sessionManager != null) {
            //change default session manager implementation ( in order to change "JSESSIONID" )
            //GS-10830
            try {
                Class sessionManagerClass = Class.forName(sessionManager);
                SessionManager sessionManagerImpl = (SessionManager) sessionManagerClass.newInstance();
                webAppContext.getSessionHandler().setSessionManager(sessionManagerImpl);
            } catch (Throwable t) {
                System.out.println("Session Manager [" + sessionManager + "] was not set cause following exception:" + t.toString());
                t.printStackTrace();
            }
        } else {
            System.out.println("Session Manager was not provided");
        }

        server.setHandler(webAppContext);

        server.start();
    }

    /**
     * Create JSP Servlet (must be named "jsp")
     */
    private ServletHolder jspServletHolder()
    {
        ServletHolder holderJsp = new ServletHolder("jsp", JettyJspServlet.class);
        holderJsp.setInitOrder(0);
        holderJsp.setInitParameter("logVerbosityLevel", "DEBUG");
        holderJsp.setInitParameter("fork", "false");
        holderJsp.setInitParameter("xpoweredBy", "false");
        holderJsp.setInitParameter("compilerTargetVM", "1.7");
        holderJsp.setInitParameter("compilerSourceVM", "1.7");
        holderJsp.setInitParameter("keepgenerated", "true");
        return holderJsp;
    }

    /**
     * Ensure the jsp engine is initialized correctly
     */
    private List<ContainerInitializer> jspInitializers()
    {
        JettyJasperInitializer sci = new JettyJasperInitializer();
        ContainerInitializer initializer = new ContainerInitializer(sci, null);
        List<ContainerInitializer> initializers = new ArrayList<ContainerInitializer>();
        initializers.add(initializer);
        return initializers;
    }

    private File getScratchDir() throws IOException
    {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        File scratchDir = new File(tempDir.toString(), "embedded-jetty-jsp");

        if (!scratchDir.exists())
        {
            if (!scratchDir.mkdirs())
            {
                throw new IOException("Unable to create scratch directory: " + scratchDir);
            }
        }
        return scratchDir;
    }

    private ServerConnector createSslConnector( Server server, WebLauncherConfig config ) {

        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setKeyStorePath(config.getSslKeyStorePath());
        sslContextFactory.setTrustStorePath(config.getSslTrustStorePath());
        sslContextFactory.setKeyStorePassword(Password.obfuscate(config.getSslKeyStorePassword()));
        sslContextFactory.setKeyManagerPassword(Password.obfuscate(config.getSslKeyManagerPassword()));
        sslContextFactory.setTrustStorePassword(Password.obfuscate(config.getSslTrustStorePassword()));

        sslContextFactory.setExcludeCipherSuites(
                new String[]{
                        "SSL_RSA_WITH_DES_CBC_SHA",
                        "SSL_DHE_RSA_WITH_DES_CBC_SHA",
                        "SSL_DHE_DSS_WITH_DES_CBC_SHA",
                        "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
                        "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
                        "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
                        "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA"
                });

        // HTTP Configuration
        HttpConfiguration http_config = new HttpConfiguration();
        http_config.setSecureScheme("https");
        http_config.setSecurePort(config.getPort());
        http_config.setOutputBufferSize(32768);
//        http_config.setRequestHeaderSize(8192);
//        http_config.setResponseHeaderSize(8192);
        http_config.setSendServerVersion(true);
        http_config.setSendDateHeader(false);

        // SSL HTTP Configuration
        HttpConfiguration https_config = new HttpConfiguration(http_config);
        https_config.addCustomizer(new SecureRequestCustomizer());

        ServerConnector sslConnector = new ServerConnector( server,
                new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()),
                new HttpConnectionFactory(https_config));

        return sslConnector;
    }
}