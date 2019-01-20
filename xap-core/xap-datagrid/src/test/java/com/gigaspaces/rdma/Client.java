package com.gigaspaces.rdma;/*
 * DiSNI: Direct Storage and Networking Interface
 *
 * Author: Patrick Stuedi <stu@zurich.ibm.com>
 *
 * Copyright (C) 2016-2018, IBM Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

import com.ibm.disni.RdmaActiveEndpoint;
import com.ibm.disni.RdmaActiveEndpointGroup;
import com.ibm.disni.RdmaEndpointFactory;
import com.ibm.disni.util.DiSNILogger;
import com.ibm.disni.verbs.*;
import org.apache.log4j.BasicConfigurator;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;


public class Client implements RdmaEndpointFactory<Client.CustomClientEndpoint> {
    RdmaActiveEndpointGroup<CustomClientEndpoint> endpointGroup;
    private String host = "192.168.33.137";
    private int port = 8888;
    private ClientTransport transport;

    public Client.CustomClientEndpoint createEndpoint(RdmaCmId idPriv, boolean serverSide) throws IOException {
        return new CustomClientEndpoint(endpointGroup, idPriv, serverSide, 1000);
    }

    public void run() throws Exception {
        try {
            //create a EndpointGroup. The RdmaActiveEndpointGroup contains CQ processing and delivers CQ event to the endpoint.dispatchCqEvent() method.
            endpointGroup = new RdmaActiveEndpointGroup<CustomClientEndpoint>(1000, false, 128, 4, 128);
            endpointGroup.init(this);
            //we have passed our own endpoint factory to the group, therefore new endpoints will be of type CustomClientEndpoint
            //let's create a new client endpoint
            Client.CustomClientEndpoint endpoint = endpointGroup.createEndpoint();

            //connect to the server
            InetAddress ipAddress = InetAddress.getByName(host);
            InetSocketAddress address = new InetSocketAddress(ipAddress, port);
            endpoint.connect(address, 1000);

            DiSNILogger.getLogger().info("SimpleClient::client channel set up ");

            String msg = "i am the client";
            transport = new ClientTransport(endpoint);
            endpoint.setTransport(transport);
            CompletableFuture<RdmaMsg> future = transport.send(new RdmaMsg(msg));
            RdmaMsg respond = future.get();
            DiSNILogger.getLogger().info(respond.getPayload().toString());

            respond = transport.send(new RdmaMsg("i am connected")).get();
            DiSNILogger.getLogger().info(respond.getPayload().toString());

            //close everything
            endpoint.close();
            DiSNILogger.getLogger().info("endpoint closed");
            endpointGroup.close();
            DiSNILogger.getLogger().info("group closed");
        } catch (Exception e) {
            DiSNILogger.getLogger().info("got exception", e);
        } finally {
            System.exit(0);
        }
    }

    public void launch(String[] args) throws Exception {
        this.run();
    }

    public static void main(String[] args) throws Exception {
        BasicConfigurator.configure();
        Client simpleClient = new Client();
        simpleClient.launch(args);
    }

    public static class CustomClientEndpoint extends RdmaActiveEndpoint {
        private ByteBuffer buffers[];
        private IbvMr mrlist[];
        private int buffercount = 3;

        private ByteBuffer dataBuf;
        private IbvMr dataMr;
        private ByteBuffer sendBuf;
        private IbvMr sendMr;
        private ByteBuffer recvBuf;
        private IbvMr recvMr;

        private LinkedList<IbvSendWR> wrList_send;
        private IbvSge sgeSend;
        private LinkedList<IbvSge> sgeList;
        private IbvSendWR sendWR;

        private LinkedList<IbvRecvWR> wrList_recv;
        private IbvSge sgeRecv;
        private LinkedList<IbvSge> sgeListRecv;
        private IbvRecvWR recvWR;

        private ArrayBlockingQueue<IbvWC> wcEvents;

        private ClientTransport transport;

        public CustomClientEndpoint(RdmaActiveEndpointGroup<CustomClientEndpoint> endpointGroup,
                                    RdmaCmId idPriv, boolean serverSide, int buffersize) throws IOException {
            super(endpointGroup, idPriv, serverSide);
            this.buffercount = 2;
            buffers = new ByteBuffer[buffercount];
            this.mrlist = new IbvMr[buffercount];

            for (int i = 0; i < buffercount; i++) {
                buffers[i] = ByteBuffer.allocateDirect(buffersize);
            }

            this.wrList_send = new LinkedList<IbvSendWR>();
            this.sgeSend = new IbvSge();
            this.sgeList = new LinkedList<IbvSge>();
            this.sendWR = new IbvSendWR();

            this.wrList_recv = new LinkedList<IbvRecvWR>();
            this.sgeRecv = new IbvSge();
            this.sgeListRecv = new LinkedList<IbvSge>();
            this.recvWR = new IbvRecvWR();

            this.wcEvents = new ArrayBlockingQueue<IbvWC>(10);
        }

        //important: we override the init method to prepare some buffers (memory registration, post recv, etc).
        //This guarantees that at least one recv operation will be posted at the moment this endpoint is connected.
        public void init() throws IOException {
            super.init();

            this.recvBuf = buffers[1];
            this.recvMr = registerMemory(this.recvBuf).execute().free().getMr();

            sgeRecv.setAddr(recvMr.getAddr());
            sgeRecv.setLength(recvMr.getLength());
            int lkey = recvMr.getLkey();
            sgeRecv.setLkey(lkey);
            sgeListRecv.add(sgeRecv);
            recvWR.setSg_list(sgeListRecv);
            recvWR.setWr_id(2003);
            wrList_recv.add(recvWR);
        }

        public void dispatchCqEvent(IbvWC wc) throws IOException {
            DiSNILogger.getLogger().info("CLIENT: op code = " + IbvWC.IbvWcOpcode.valueOf(wc.getOpcode()) + ", id = " + wc.getWr_id());
            getTransport().onCompletionEvent(wc);
        }

        public ArrayBlockingQueue<IbvWC> getWcEvents() {
            return wcEvents;
        }

        public LinkedList<IbvSendWR> getWrList_send() {
            return wrList_send;
        }

        public LinkedList<IbvRecvWR> getWrList_recv() {
            return wrList_recv;
        }

        public ByteBuffer getDataBuf() {
            return dataBuf;
        }

        public ByteBuffer getSendBuf() {
            return sendBuf;
        }

        public ByteBuffer getRecvBuf() {
            return recvBuf;
        }

        public IbvSendWR getSendWR() {
            return sendWR;
        }

        public IbvRecvWR getRecvWR() {
            return recvWR;
        }

        public ClientTransport getTransport() {
            return transport;
        }

        public void setTransport(ClientTransport transport) {
            this.transport = transport;
        }
    }

}
