package com.gigaspaces.internal.services;

import com.gigaspaces.start.ClasspathBuilder;

public class WebuiServiceFactory extends ServiceFactory {
    @Override
    public String getServiceName() {
        return "WEBUI";
    }

    @Override
    protected String getServiceClassName() {
        return "org.openspaces.launcher.Launcher";
    }

    @Override
    protected void initializeClasspath(ClasspathBuilder classpath) {
        classpath
                // $GS_JARS
                .appendRequired(ClasspathBuilder.startsWithFilter("xap-", "spring-", "commons-"))
                .appendPlatform("ext")
                .appendOptional("spring").appendOptional("security")        // $SPRING_JARS
                .appendOptional("jetty").appendOptional("jetty/xap-jetty")
                .appendOptional("interop")
                .appendOptional("memoryxtend/off-heap")
                .appendOptional("memoryxtend/rocksdb")
                .appendPlatform("commons")
                .appendPlatform("service-grid");
    }
}
