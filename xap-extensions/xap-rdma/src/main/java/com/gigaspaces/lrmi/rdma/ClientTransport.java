package com.gigaspaces.lrmi.rdma;

import com.ibm.disni.RdmaActiveEndpoint;
import com.ibm.disni.util.DiSNILogger;
import com.ibm.disni.verbs.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

// @todo - handle timeouts ?

public class ClientTransport {

    private final RdmaSender rdmaSender;
    private ConcurrentHashMap<Long, RdmaMsg> repMap = new ConcurrentHashMap<>();
    private AtomicLong nextId = new AtomicLong(0);
    private static final LinkedList<ByteBuffer> resources = new LinkedList<>();
    private final ArrayBlockingQueue<RdmaMsg> writeRequests = new ArrayBlockingQueue<>(100);
    private final ArrayBlockingQueue<IbvWC> recvEventQueue = new ArrayBlockingQueue<>(100);
    private final ExecutorService recvHandler = Executors.newFixedThreadPool(1);
    private final ExecutorService sendHandler = Executors.newFixedThreadPool(1);
    private final RdmaResourceManager resourceManager;

    public ClientTransport(GSRdmaClientEndpoint endpoint, RdmaResourceFactory factory, Function<ByteBuffer, Object> deserializer) throws IOException {
        resourceManager = new RdmaResourceManager(factory, 1);
        recvHandler.submit(new RdmaClientReceiver(recvEventQueue, repMap, endpoint, deserializer));
        rdmaSender = new RdmaSender(resourceManager, writeRequests);
        sendHandler.submit(rdmaSender);
    }

    public static LinkedList<IbvRecvWR> createRecvWorkRequest(long id, IbvMr mr) {
        LinkedList<IbvRecvWR> wr_list = new LinkedList<>();
        IbvRecvWR recvWR = new IbvRecvWR();
        recvWR.setWr_id(id);
        LinkedList<IbvSge> sgeLinkedList = new LinkedList<>();
        IbvSge sge = new IbvSge();
        sge.setAddr(mr.getAddr());
        sge.setLength(mr.getLength());
        sge.setLkey(mr.getLkey());
        sgeLinkedList.add(sge);
        recvWR.setSg_list(sgeLinkedList);
        wr_list.add(recvWR);
        return wr_list;
    }

    public <REQ, REP> CompletableFuture<REP> send(REQ request) {
        RdmaMsg<REQ, REP> rdmaMsg = new RdmaMsg<>(request);
        long id = nextId.incrementAndGet();
        rdmaMsg.setId(id);
        repMap.put(id, rdmaMsg);
        rdmaMsg.getFuture().whenComplete((T, throwable) -> repMap.remove(id));
        try {
            writeRequests.add(rdmaMsg);
        } catch (Exception e) {
            System.out.println("throwing exception " + e);
            rdmaMsg.getFuture().completeExceptionally(e);
        }
        return rdmaMsg.getFuture();
    }

    public void onCompletionEvent(IbvWC event) throws IOException {

        if (IbvWC.IbvWcOpcode.valueOf(event.getOpcode()).equals(IbvWC.IbvWcOpcode.IBV_WC_SEND)) {
            resourceManager.releaseResource((short) event.getWr_id());
        }
        if (IbvWC.IbvWcOpcode.valueOf(event.getOpcode()).equals(IbvWC.IbvWcOpcode.IBV_WC_RECV)) {
            recvEventQueue.add(event); //TODO mybe offer? protect capacity
        }
    }

    public static LinkedList<IbvSendWR> createSendWorkRequest(long id, IbvMr mr) {
        LinkedList<IbvSendWR> wr_list = new LinkedList<>();
        IbvSendWR sendWR = new IbvSendWR();
        sendWR.setWr_id(id);
        //@todo sendMsg id in the envelop
        LinkedList<IbvSge> sgeLinkedList = new LinkedList<>();
        IbvSge sge = new IbvSge();
        sge.setAddr(mr.getAddr());
        sge.setLength(mr.getLength());
        sge.setLkey(mr.getLkey());
        sgeLinkedList.add(sge);
        sendWR.setSg_list(sgeLinkedList);
        sendWR.setOpcode(IbvSendWR.IBV_WR_SEND);
        sendWR.setSend_flags(IbvSendWR.IBV_SEND_SIGNALED);
        wr_list.add(sendWR);
        return wr_list;
    }


    static Object readResponse(ByteBuffer buffer) {
        byte[] arr = new byte[buffer.remaining()];
        buffer.get(arr);
        buffer.clear();
        try (ByteArrayInputStream ba = new ByteArrayInputStream(arr); ObjectInputStream in = new ObjectInputStream(ba)) {
            return in.readObject();
        } catch (Exception e) {
            DiSNILogger.getLogger().error(" failed to read object from stream", e);
            return e;
        }

    }

}
