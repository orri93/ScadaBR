package com.serotonin.mango.rt.publish.persistent;

import br.org.scadabr.utils.ImplementMeException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.text.ParseException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import br.org.scadabr.ShouldNeverHappenException;
import com.serotonin.mango.Common;
import com.serotonin.mango.db.dao.DataPointDao;
import com.serotonin.mango.rt.dataImage.types.ImageValue;
import com.serotonin.mango.rt.dataImage.types.MangoValue;
import com.serotonin.mango.rt.publish.PublishQueueEntry;
import com.serotonin.mango.rt.publish.SendThread;
import com.serotonin.mango.vo.hierarchy.PointFolder;
import com.serotonin.mango.vo.hierarchy.PointHierarchy;
import com.serotonin.mango.vo.hierarchy.PointHierarchyEventDispatcher;
import com.serotonin.mango.vo.hierarchy.PointHierarchyListener;
import com.serotonin.mango.vo.publish.persistent.PersistentPointVO;
import com.serotonin.mango.vo.publish.persistent.PersistentSenderVO;
import br.org.scadabr.timer.cron.CronExpression;
import br.org.scadabr.timer.cron.SystemCronTask;
import br.org.scadabr.timer.cron.SystemRunnable;
import br.org.scadabr.util.queue.ByteQueue;
import java.util.TimeZone;

class PersistentSendThread extends SendThread {

    static final Log LOG = LogFactory.getLog(PersistentSendThread.class);

    final PersistentSenderRT publisher;

    Socket socket;
    int connecting = -1;
    InputStream in;
    OutputStream out;
    ByteQueue writeBuffer = new ByteQueue();
    int version = 3;
    SyncTimer syncTimer;
    SyncHandler syncHandler;
    PointHierarchySync pointHierarchySync;
    final List<Packet> packetsToSend = new CopyOnWriteArrayList<>();
    private long lastTestPacket;

    public PersistentSendThread(PersistentSenderRT publisher) {
        super("PersistentSenderRT.SendThread");
        this.publisher = publisher;
    }

    public boolean isConnected() {
        return socket != null;
    }

    public int getConnectingIndex() {
        return connecting;
    }

    @Override
    public void initialize() {
        super.initialize();

        if (publisher.vo.getSyncType() != PersistentSenderVO.SYNC_TYPE_NONE) {
            // Add a schedule to do the sync
            //String pattern = "15 0/5 * * * ?"; // Testing pattern. Every 5 minutes.
            //

            if (true) {
                throw new ImplementMeException();
            }
            // The pattern is not valis for our cron stuff
            String pattern;
            if (publisher.vo.getSyncType() == PersistentSenderVO.SYNC_TYPE_DAILY) {
                pattern = "0 0 1 * * ?";
            } else if (publisher.vo.getSyncType() == PersistentSenderVO.SYNC_TYPE_WEEKLY) {
                pattern = "0 0 1 ? * MON";
            } else if (publisher.vo.getSyncType() == PersistentSenderVO.SYNC_TYPE_MONTHLY) {
                pattern = "0 0 1 1 * ?";
            } else {
                throw new ShouldNeverHappenException("Invalid sync type: " + publisher.vo.getSyncType());
            }

            try {
                syncTimer = new SyncTimer(pattern, CronExpression.TIMEZONE_UTC);
            } catch (ParseException e) {
                throw new ShouldNeverHappenException(e);
            }

            Common.systemCronPool.schedule(syncTimer);
        }

        pointHierarchySync = new PointHierarchySync();
        PointHierarchyEventDispatcher.addListener(pointHierarchySync);
    }

    @Override
    public void terminate() {
        PointHierarchyEventDispatcher.removeListener(pointHierarchySync);

        super.terminate();

        if (syncTimer != null) // Cancel the sync timer.
        {
            syncTimer.cancel();
        }
    }

    @Override
    protected void runImpl() {
        while (isRunning()) {
            if (socket == null) {
                try {
                    openConnection();
                } catch (IOException e) {
                    Common.ctx.getEventManager().returnToNormal(publisher.connectionAbortedEventType,
                            System.currentTimeMillis());
                    Common.ctx.getEventManager().returnToNormal(publisher.protocolFailureEventType,
                            System.currentTimeMillis());
                    publisher.raiseConnectionEvent(publisher.connectionFailedEventType, e);
                    closeConnection(2000);
                } catch (PersistentAbortException e) {
                    Common.ctx.getEventManager().returnToNormal(publisher.protocolFailureEventType,
                            System.currentTimeMillis());
                    publisher.raiseConnectionEvent(publisher.connectionAbortedEventType, e);
                    closeConnection(10000);
                } catch (PersistentProtocolException e) {
                    Common.ctx.getEventManager().returnToNormal(publisher.connectionAbortedEventType,
                            System.currentTimeMillis());
                    publisher.raiseConnectionEvent(publisher.protocolFailureEventType, e);
                    closeConnection(60000);
                }

                if (socket == null) {
                    continue;
                }

                Common.ctx.getEventManager().returnToNormal(publisher.connectionAbortedEventType,
                        System.currentTimeMillis());
                Common.ctx.getEventManager().returnToNormal(publisher.protocolFailureEventType,
                        System.currentTimeMillis());
                Common.ctx.getEventManager().returnToNormal(publisher.connectionLostEventType,
                        System.currentTimeMillis());

                writePointHierarchy();
            }

            PublishQueueEntry<PersistentPointVO> entry = publisher.getPublishQueue().next();

            if (entry != null) {
                try {
                    send(entry);
                    publisher.getPublishQueue().remove(entry);
                } catch (IOException e) {
                    publisher.raiseConnectionEvent(publisher.connectionLostEventType, e);
                    // The send failed. Close the connection and attempt to re-open.
                    closeConnection(0);
                }
            } else if (packetsToSend.size() > 0) {
                Packet packet = packetsToSend.remove(0);
                try {
                    Packet.writePacket(out, version, packet);
                } catch (IOException e) {
                    publisher.raiseConnectionEvent(publisher.connectionLostEventType, e);
                    // The send failed. Close the connection and attempt to re-open.
                    closeConnection(0);
                } finally {
                    packet.release();
                }
            } else {
                try {
                    // Read messages from the server.
                    Packet packet = Packet.readPacketNoBlock(in, version);
                    if (packet != null) {
                        try {
                            // Handle the packet
                            if (packet.getType() == PacketType.RANGE_COUNT) {
                                if (syncHandler != null) {
                                    syncHandler.responseReceived(packet);
                                }
                            } else {
                                LOG.error("Unexpected packet type: " + packet.getType());
                            }
                        } finally {
                            packet.release();
                        }
                    } else if (lastTestPacket + Packet.TEST_PACKET_SEND_DELAY < System.currentTimeMillis()) {
                        // Fire off a test packet for fun.
                        Packet.writePacket(out, version, PacketType.TEST, Packet.EMPTY);
                        lastTestPacket = System.currentTimeMillis();
                    } else // Take a break.
                    {
                        waitImpl(2000);
                    }
                } catch (IOException e) {
                    publisher.raiseConnectionEvent(publisher.connectionLostEventType, e);
                    closeConnection(0);
                } catch (PersistentAbortException e) {
                    publisher.raiseConnectionEvent(publisher.connectionLostEventType, e.getLocalizableMessage());
                    closeConnection(0);
                } catch (PersistentProtocolException e) {
                    publisher.raiseConnectionEvent(publisher.connectionLostEventType, e);
                    closeConnection(0);
                }
            }
        }

        closeConnection(0);
    }

    void sendPacket(Packet packet) {
        packetsToSend.add(packet);
        synchronized (this) {
            notify();
        }
    }

    private void send(PublishQueueEntry<PersistentPointVO> entry) throws IOException {
        //
        // Data
        writeBuffer.pushU2B(entry.getVo().getIndex());
        MangoValue value = entry.getPvt().getValue();
        writeBuffer.push(value.getDataType().mangoDbId);
        switch (entry.getPvt().getValue().getDataType()) {
            case BINARY:
                writeBuffer.push(value.getBooleanValue() ? 1 : 0);
                break;
            case MULTISTATE:
                writeBuffer.pushS4B(value.getIntegerValue());
                break;
            case NUMERIC:
                Packet.pushDouble(writeBuffer, value.getDoubleValue());
                break;
            case ALPHANUMERIC:
                Packet.pushString(writeBuffer, value.getStringValue());
                break;
            case IMAGE:
                byte[] data;
                try {
                    data = ((ImageValue) value).getImageData();
                } catch (IOException e) {
                    LOG.warn("Error reading image data", e);
                    // Don't propagate the exception since the problem is on this side of the connection.
                    return;
                }
                writeBuffer.pushS4B(((ImageValue) value).getType());
                writeBuffer.pushS4B(data.length);
                writeBuffer.push(data);
                break;
        }

        Packet.pushLong(writeBuffer, entry.getPvt().getTime());

        Packet.writePacket(out, version, PacketType.DATA, writeBuffer);
    }

    private void openConnection() throws IOException, PersistentProtocolException, PersistentAbortException {
        connecting = 0;
        try {
            Socket localSocket = new Socket(publisher.vo.getHost(), publisher.vo.getPort());
            localSocket.setSoTimeout(Packet.PUBLISHER_SOCKET_TIMEOUT);
            Common.ctx.getEventManager()
                    .returnToNormal(publisher.connectionFailedEventType, System.currentTimeMillis());
            in = localSocket.getInputStream();
            out = localSocket.getOutputStream();

            //
            // Version. Always sent and received in version 1.
            Packet.writePacket(out, 1, PacketType.VERSION, new byte[]{(byte) version});

            Packet packet = Packet.readPacket(in, 1);
            try {
                if (packet.getType() != PacketType.VERSION) {
                    throw new PersistentProtocolException("Expected version, got " + packet.getType());
                }
                version = packet.getPayload().popU1B();
            } finally {
                packet.release();
            }

            //
            // Authentication key
            Packet.pushString(writeBuffer, publisher.vo.getAuthorizationKey());
            Packet.writePacket(out, version, PacketType.AUTH_KEY, writeBuffer);

            packet = Packet.readPacket(in, version);
            try {
                if (packet.getType() != PacketType.AUTH_KEY) {
                    throw new PersistentProtocolException("Expected auth key, got " + packet.getType());
                }
                if (packet.getPayload().size() != 0) {
                    throw new PersistentProtocolException("Expected empty payload");
                }
            } finally {
                packet.release();
            }

            //
            // Points
            String prefix = "";
            if (!publisher.vo.getXidPrefix().isEmpty()) {
                prefix = publisher.vo.getXidPrefix();
            }

            for (PersistentPointVO point : publisher.vo.getPoints()) {
                connecting++;
                Packet.pushString(writeBuffer, prefix + point.getXid());
                writeBuffer.push(point.getSerializedDataPoint());
                Packet.writePacket(out, version, PacketType.POINT, writeBuffer);

                if (version < 3) // As of version 3 we do not expect this response.
                {
                    getPointResponse();
                }
            }

            // Send an empty packet to indicate that we're done.
            Packet.writePacket(out, version, PacketType.POINT, Packet.EMPTY);
            getPointResponse();

            socket = localSocket;
        } finally {
            connecting = -1;
        }
    }

    private void getPointResponse() throws IOException, PersistentAbortException, PersistentProtocolException {
        Packet packet = Packet.readPacket(in, version);
        try {
            if (packet.getType() != PacketType.POINT) {
                throw new PersistentProtocolException("Expected points, got " + packet.getType());
            }
            if (packet.getPayload().size() != 0) {
                throw new PersistentProtocolException("Expected empty payload");
            }
        } finally {
            packet.release();
        }
    }

    private void closeConnection(int sleep) {
        if (socket != null) {
            //
            // Close
            try {
                Packet.writePacket(out, version, PacketType.CLOSE, Packet.EMPTY);
                socket.close();
            } catch (IOException e) {
                // LOG.warn("", e);
            } finally {
                socket = null;
                in = null;
                out = null;
            }
        }

        SyncHandler sh = syncHandler;
        if (sh != null) {
            // Cancel the sync handler if it is running.
            sh.cancel();
            synchronized (sh) {
                sh.notify();
            }
        }

        if (sleep > 0) {
            waitImpl(sleep);
        }
    }

    class SyncTimer extends SystemCronTask {

        public SyncTimer(String pattern, TimeZone tz) throws ParseException {
            super(pattern, tz);
        }

        @Override
        protected void run(long scheduledExecutionTime) {
            startSync();
        }

        @Override
        protected boolean overrunDetected(long lastExecutionTime, long thisExecutionTime) {
            throw new ImplementMeException();
        }
    }

    synchronized boolean startSync() {
        if (syncHandler != null) {
            LOG.warn("A data synchronization run was not started because a previous one is still running");
            return false;
        }

        syncHandler = new SyncHandler(this);
        Common.systemCronPool.execute(syncHandler);

        return true;
    }

    void endSyncHandler() {
        syncHandler = null;
    }

    class PointHierarchySync implements PointHierarchyListener {

        @Override
        public void pointHierarchySaved(PointFolder root) {
            PointHierarchy hierarchy = new PointHierarchy(root);
            writePointHierarchy(hierarchy);
        }
    }

    void writePointHierarchy() {
        Common.systemCronPool.execute(new SystemRunnable() {
            @Override
            public void run() {
                writePointHierarchy(DataPointDao.getInstance().getPointHierarchy());
            }
        });
    }

    synchronized void writePointHierarchy(PointHierarchy hierarchy) {
        for (PersistentPointVO p : publisher.vo.getPoints()) {
            if (!isConnected()) {
                break;
            }

            List<String> path = hierarchy.getPath(p.getDataPointId());

            ByteQueue queue = new ByteQueue();
            queue.pushU2B(p.getIndex());
            queue.pushU2B(path.size());
            for (String s : path) {
                Packet.pushString(queue, s);
            }

            Packet packet = Packet.borrowPacket(PacketType.POINT_HIERARCHY, queue);
            sendPacket(packet);
        }
    }
}
