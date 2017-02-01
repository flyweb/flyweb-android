package ca.vijayan.flyweb.proxy;

import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

/**
 * Created by Kannan Vijayan on 1/18/2017.
 */

public class ProxyConnection {
    private SocketChannel mLocalSocket;
    private SelectionKey mLocalSocketKey;
    private ByteBuffer mLocalRecvBuffer;
    private ByteBuffer mLocalSendBuffer;
    enum LocalState {
        CONNECTED,      /* Local socket is connected. */
        RECV_CLOSED,    /* Local socket's input stream (recv) is closed. */
        SEND_CLOSED,    /* Local socket's output stream (send) is closed. */
        CLOSED,         /* Local socket is closed. */
        ERRORED
        ;

        public boolean isRecvClosed() {
            return this == LocalState.RECV_CLOSED || this == LocalState.CLOSED;
        }
        public boolean isSendClosed() {
            return this == LocalState.SEND_CLOSED || this == LocalState.CLOSED;
        }
    }
    private LocalState mLocalState;

    private SocketChannel mServiceSocket;
    private SelectionKey mServiceSocketKey;
    private ByteBuffer mServiceRecvBuffer;
    private ByteBuffer mServiceSendBuffer;
    enum ServiceState {
        WAITING,        /* Waiting for enough info to make connection. */
        CONNECTING,     /* Waiting to create a connection. */
        CONNECTED,      /* Service socket is connected. */
        RECV_CLOSED,    /* Service socket's input stream (recv) is closed. */
        SEND_CLOSED,    /* Service socket's output stream (send) is closed. */
        CLOSED,         /* Service socket is closed. */
        ERRORED
        ;
        public boolean isRecvClosed() {
            return this == ServiceState.RECV_CLOSED || this == ServiceState.CLOSED;
        }
        public boolean isSendClosed() {
            return this == ServiceState.SEND_CLOSED || this == ServiceState.CLOSED;
        }
    }
    private ServiceState mServiceState;


    public ProxyConnection(SocketChannel localSocket) {
        mLocalSocket = localSocket;
        mLocalSocketKey = null;
        mLocalSendBuffer = ByteBuffer.allocate(1024);
        mLocalRecvBuffer = ByteBuffer.allocate(1024);
        mLocalState = LocalState.CONNECTED;

        mServiceSocket = null;
        mServiceSocketKey = null;
        mServiceSendBuffer = ByteBuffer.allocate(1024);
        mServiceRecvBuffer = ByteBuffer.allocate(1024);
        mServiceState = ServiceState.WAITING;
    }

    public SocketChannel getLocalSocket() {
        return mLocalSocket;
    }

    public SelectionKey getLocalSocketKey() {
        return mLocalSocketKey;
    }
    public void setLocalSocketKey(SelectionKey localSocketKey) {
        mLocalSocketKey = localSocketKey;
    }

    public ByteBuffer getLocalSendBuffer() {
        return mLocalSendBuffer;
    }
    public void writeLocalSendBuffer(ByteBuffer data) {
        while (data.remaining() > mLocalSendBuffer.remaining()) {
            mLocalSendBuffer = enlargeBuffer(mLocalSendBuffer);
        }
        mLocalSendBuffer.put(data);
    }

    public ByteBuffer getLocalRecvBuffer() {
        return mLocalRecvBuffer;
    }
    public void writeLocalRecvBuffer(ByteBuffer data) {
        while (data.remaining() > mLocalRecvBuffer.remaining()) {
            mLocalRecvBuffer = enlargeBuffer(mLocalRecvBuffer);
        }
        mLocalRecvBuffer.put(data);
    }

    public SocketChannel getServiceSocket() {
        return mServiceSocket;
    }
    public void setServiceSocket(SocketChannel serviceSocket) {
        mServiceSocket = serviceSocket;
    }

    public SelectionKey getServiceSocketKey() {
        return mServiceSocketKey;
    }
    public void setServiceSocketKey(SelectionKey serviceSocketKey) {
        mServiceSocketKey = serviceSocketKey;
    }

    public ByteBuffer getServiceSendBuffer() {
        return mServiceSendBuffer;
    }
    public ByteBuffer getServiceRecvBuffer() {
        return mServiceRecvBuffer;
    }

    public int newLocalSocketKeyOps() {
        // Ignore errored connections.
        if (mLocalState == LocalState.ERRORED) {
            return 0;
        }

        int interestOps = 0;
        // We always want to read from the socket.
        if (! mLocalState.isRecvClosed()) {
            interestOps |= SelectionKey.OP_READ;
        }
        if (mLocalSendBuffer.hasRemaining()) {
            interestOps |= SelectionKey.OP_WRITE;
        }
        return interestOps;
    }
    public int newServiceSocketKeyOps() {
        // Ignore errored connections.
        if (mServiceState == ServiceState.ERRORED || mServiceState == ServiceState.WAITING) {
            return 0;
        }

        if (mServiceState == ServiceState.CONNECTING) {
            return SelectionKey.OP_CONNECT;
        }

        int interestOps = 0;
        // We always want to read from the socket.
        if (! mServiceState.isRecvClosed()) {
            interestOps |= SelectionKey.OP_READ;
        }
        if (mServiceSendBuffer.hasRemaining()) {
            interestOps |= SelectionKey.OP_WRITE;
        }
        return interestOps;
    }
    public void markLocalSocketError() {
        mLocalState = LocalState.ERRORED;
    }
    public void markServiceSocketError() {
        mServiceState = ServiceState.ERRORED;
    }
    public void markServiceSocketConnected() {
        assert(mServiceState == ServiceState.CONNECTING);
        mServiceState = ServiceState.CONNECTED;
    }

    static ByteBuffer enlargeBuffer(ByteBuffer buf) {
        ByteBuffer newbuf = ByteBuffer.allocate(buf.capacity() * 2);
        newbuf.put(buf);
        return newbuf;
    }
}
