package ca.vijayan.flyweb.proxy;

import android.util.Log;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Kannan Vijayan on 1/18/2017.
 */

public class ProxyServer implements Runnable {
    InetAddress mServiceAddr;
    int mServicePort;
    boolean mIsSecure;

    Selector mSelector;
    ServerSocketChannel mServerSocket;
    SelectionKey mServerKey;
    boolean mServerStopRequest;
    Set<ProxyConnection> mConnections;

    Thread mThread;

    public ProxyServer(InetAddress serviceAddr, int servicePort, boolean isSecure) throws IOException {
        mServiceAddr = serviceAddr;
        mServicePort = servicePort;
        mIsSecure = isSecure;

        mSelector = Selector.open();
        mServerSocket = ServerSocketChannel.open();
        mServerSocket.configureBlocking(false);
        mServerSocket.socket().bind(new InetSocketAddress(0));
        mServerKey = mServerSocket.register(mSelector, SelectionKey.OP_ACCEPT);

        mServerStopRequest = false;
        mConnections = new HashSet<>();

        mThread = new Thread(this);
        mThread.start();
    }

    public void requestStop() {
        synchronized (this) {
            mServerStopRequest = true;
        }
    }
    boolean isStopRequested() {
        synchronized (this) {
            return mServerStopRequest;
        }
    }

    @Override
    public void run() {
        // Accept sockets from client and handle them.
        for (;;) {
            if (isStopRequested()) {
                break;
            }

            int nselected = 0;
            try {
                nselected = mSelector.select(100);
            } catch (IOException exc) {
                Log.e("ProxyServer", "Failed to select.");
                throw new RuntimeException("ProxyServer failed to call Selector.select().", exc);
            }

            if (nselected == 0) {
                continue;
            }

            // Check selected keys.
            Set<SelectionKey> selectedKeys = mSelector.selectedKeys();
            if (selectedKeys.contains(mServerKey)) {
                // Try an accept.
                if (mServerKey.isAcceptable()) {
                    doAccept();
                }
            }
            KEY: for (SelectionKey selectedKey : selectedKeys) {
                // Check selected key against map.
                ProxyConnection conn = (ProxyConnection) selectedKey.attachment();
                if (conn != null && mConnections.contains(conn)) {
                    if (selectedKey == conn.getLocalSocketKey()) {
                        if (selectedKey.isReadable()) {
                            handleLocalRead(conn);
                        }
                        if (selectedKey.isWritable()) {
                            handleLocalWrite(conn);
                        }
                        int newOps = conn.newLocalSocketKeyOps();
                        int curOps = selectedKey.interestOps();
                        if (newOps == 0) {
                            selectedKey.cancel();
                            selectedKey.attach(null);
                            conn.setLocalSocketKey(null);
                            continue KEY;
                        }
                        selectedKey.interestOps(newOps);
                        continue KEY;
                    }

                    if (selectedKey == conn.getServiceSocketKey()) {
                        if (selectedKey.isConnectable()) {
                            handleServiceConnect(conn);
                        }
                        if (selectedKey.isReadable()) {
                            handleServiceRead(conn);
                        }
                        if (selectedKey.isWritable()) {
                            handleServiceWrite(conn);
                        }
                        int newOps = conn.newServiceSocketKeyOps();
                        if (newOps == 0) {
                            selectedKey.cancel();
                            selectedKey.attach(null);
                            conn.setServiceSocketKey(null);
                            continue KEY;
                        }

                        selectedKey.interestOps(newOps);
                        continue KEY;
                    }
                }
            }
        }
    }

    void doAccept() {
        SocketChannel chan = null;
        try {
            chan = mServerSocket.accept();
        } catch (IOException exc) {
            Log.e("ProxyServer", "Failed to accept connection.");
            return;
        }

        if (chan == null) {
            return;
        }

        try {
            chan.configureBlocking(false);
        } catch (IOException exc) {
            Log.e("ProxyServer", "Failed to set local socket to nonblocking.");
            return;
        }

        // Register the local-side socket's read listening.
        SelectionKey inputKey;
        try {
            inputKey = chan.register(mSelector, SelectionKey.OP_READ);
        } catch (ClosedChannelException exc) {
            Log.e("ProxyServer", "Failed to register socket channel.", exc);
            return;
        }

        // Create a proxy connection.
        ProxyConnection proxyConn = new ProxyConnection(chan);
        proxyConn.setLocalSocketKey(inputKey);
        inputKey.attach(proxyConn);

        // Add it to the set and map.
        mConnections.add(proxyConn);
    }

    void handleLocalRead(ProxyConnection conn) {
        // Read from the local socket input stream.
        final int BUFFER_SIZE = 1024;
        ByteBuffer buf = ByteBuffer.allocate(BUFFER_SIZE);
        int totalRead = 0;
        for (;;) {
            int nread = 0;
            try {
                nread = conn.getLocalSocket().read(buf);
            } catch (IOException exc) {
                Log.e("ProxyServer", "Failed to read from local connection.");
                conn.markLocalSocketError();
                break;
            }
            if (nread <= 0) {
                break;
            }
            buf.flip();
            conn.writeLocalRecvBuffer(buf);
            totalRead += nread;
            if (nread < BUFFER_SIZE) {
                break;
            }

            buf.reset();
        }

        if (totalRead > 0) {
            // Handle new local incoming data.
            handleLocalIncomingData(conn, totalRead);
        }
    }
    void handleLocalIncomingData(ProxyConnection conn, int nbytes) {
        // TODO: IMPLEMENT.
    }

    void handleLocalWrite(ProxyConnection conn) {
        int totalWritten = 0;
        for (;;) {
            if (! conn.getLocalSendBuffer().hasRemaining()) {
                // Nothing to write.
                return;
            }

            int nwritten;
            try {
                nwritten = conn.getLocalSocket().write(conn.getLocalSendBuffer());
            } catch (IOException exc) {
                Log.e("ProxyServer", "Failed to write to local connection.");
                conn.markLocalSocketError();
                break;
            }

            if (nwritten <= 0) {
                break;
            }
            totalWritten += nwritten;
        }

        if (totalWritten > 0) {
            // Handle new local incoming data.
            handleLocalOutgoingData(conn, totalWritten);
        }
    }
    void handleLocalOutgoingData(ProxyConnection conn, int nbytes) {
        // TODO: IMPLEMENT.
    }

    void handleServiceConnect(ProxyConnection conn) {
        // Finish connecting to the service.
        try {
            conn.getServiceSocket().finishConnect();
        } catch (IOException exc) {
            Log.e("ProxyServer", "Failed to write to connect to service.");
            conn.markServiceSocketError();
            return;
        }
        conn.markServiceSocketConnected();
    }

    void handleServiceRead(ProxyConnection conn) {
        // Finish connecting to the service.
        try {
            conn.getServiceSocket().finishConnect();
        } catch (IOException exc) {
            Log.e("ProxyServer", "Failed to write to connect to service.");
            conn.markServiceSocketError();
            return;
        }
        conn.markServiceSocketConnected();
    }
    void handleServiceWrite(ProxyConnection conn) {
    }
}