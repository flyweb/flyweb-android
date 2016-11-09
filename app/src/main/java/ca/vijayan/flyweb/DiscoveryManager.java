package ca.vijayan.flyweb;

import android.app.Activity;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.Pair;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.SynchronousQueue;

import ca.vijayan.flyweb.mdns.MDNSManager;

/**
 * Created by kvijayan on 04/11/16.
 */

class DiscoveryManager implements NsdManager.DiscoveryListener {
    static final String SERVICE_TYPE = "_flyweb._tcp";

    Activity mContext;
    Handler mServiceInfoHandler;

    MDNSManager mMDNSManager;
    NsdManager mNsdManager;
    RichDataResolver mRichDataResolver;

    static enum CurrentState { INACTIVE, STARTING, ACTIVE, STOPPING, FAILED }
    CurrentState mCurrentState;

    static enum DesiredState { ACTIVE, INACTIVE }
    DesiredState mDesiredState;

    DiscoveryManager(Activity context, Handler serviceInfoHandler) {
        Log.e("DiscoveryManager", "Initializing");
        mContext = context;
        mServiceInfoHandler = serviceInfoHandler;
        mRichDataResolver = new RichDataResolver();
        mRichDataResolver.start();

        mMDNSManager = new MDNSManager();
        mNsdManager = (NsdManager) mContext.getSystemService(Context.NSD_SERVICE);
        mCurrentState = CurrentState.INACTIVE;
        mDesiredState = DesiredState.INACTIVE;
    }

    public void startDiscovery() {
        Log.e("DiscoveryManager", "startDiscovery()");
        mDesiredState = DesiredState.ACTIVE;
        makeActive();
        mMDNSManager.start();
    }

    public void stopDiscovery() {
        Log.e("DiscoveryManager", "stopDiscovery()");
        mDesiredState = DesiredState.INACTIVE;
        makeInactive();
        mMDNSManager.stop();
    }

    private void makeActive() {
        assert(mDesiredState == DesiredState.ACTIVE);

        // Only need to take action if we're currently INACTIVE.
        // If we're currently ACTIVE, we're already done.
        // If we're currently STARTING, then we've already started trying for ACTIVE state.
        // If we're currently STOPPING, then the on-stop handlers will check the desired
        //   state and call this method again.
        // If we're FAILED, then we're not going to start again.  Reset desired state to INACTIVE.
        if (mCurrentState == CurrentState.INACTIVE) {
            mNsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, this);
            mCurrentState = CurrentState.STARTING;
        } else if (mCurrentState == CurrentState.FAILED) {
            mDesiredState = DesiredState.INACTIVE;
        }
    }

    private void makeInactive() {
        assert(mDesiredState == DesiredState.INACTIVE);

        // Only need to take action if we're currently ACTIVE.
        // If we're currently STARTING, then the after-start handlers will check the
        //   desired state and call this method again.
        // If we're currently INACTIVE, then we're already done.
        // If we're currently STOPPING, then we've already started trying for INACTIVE state.
        // If we're currenetly FAILED, then that's equivalent to INACTIVE.
        if (mCurrentState == CurrentState.ACTIVE) {
            mNsdManager.stopServiceDiscovery(this);
            mCurrentState = CurrentState.STOPPING;
        }
    }

    private void makeFailed() {
        mCurrentState = CurrentState.FAILED;
        mDesiredState = DesiredState.INACTIVE;
    }

    @Override
    public void onStartDiscoveryFailed(String s, int i) {
        Log.e("DiscoveryManager", "onStartDiscoveryFailed: " + s + " - " + i);
        makeFailed();
    }

    @Override
    public void onStopDiscoveryFailed(String s, int i) {
        Log.e("DiscoveryManager", "onStopDiscoveryFailed: " + s + " - " + i);
        makeFailed();
    }

    @Override
    public void onDiscoveryStarted(String s) {
        Log.e("DiscoveryManager", "onDiscoveryStarted: " + s);
        assert(mCurrentState == CurrentState.STARTING);
        mCurrentState = CurrentState.ACTIVE;
        // Check for INACTIVE desired state.
        if (mDesiredState == DesiredState.INACTIVE) {
            makeInactive();
        }
    }

    @Override
    public void onDiscoveryStopped(String s) {
        Log.e("DiscoveryManager", "onDiscoveryStopped: " + s);
        assert(mCurrentState == CurrentState.STOPPING);
        mCurrentState = CurrentState.INACTIVE;
        // Check for ACTIVE desired state.
        if (mDesiredState == DesiredState.ACTIVE) {
            makeActive();
        }
    }

    @Override
    public void onServiceFound(NsdServiceInfo nsdServiceInfo) {
        Log.e("DiscoveryManager", "onServiceFound: " + nsdServiceInfo);
        // Only resolve if desired and current state is ACTIVE.
        if (mDesiredState != DesiredState.ACTIVE || mCurrentState != CurrentState.ACTIVE) {
            return;
        }

        // Resolve rich data.
        mNsdManager.resolveService(nsdServiceInfo, new ResolveListener());
    }

    @Override
    public void onServiceLost(NsdServiceInfo nsdServiceInfo) {
        Log.e("DiscoveryManager", "onServiceLost: " + nsdServiceInfo);
        // Only remove from list if desired and current state is ACTIVE.
        // Otherwise, service list will be empty.
        if (mDesiredState != DesiredState.ACTIVE || mCurrentState != CurrentState.ACTIVE) {
            return;
        }

        // Send message to remove service.
        Message msg = mServiceInfoHandler.obtainMessage(
                DiscoverActivity.MESSAGE_REMOVE_SERVICE, nsdServiceInfo);
        msg.sendToTarget();
    }

    class ResolveListener implements NsdManager.ResolveListener {
        @Override
        public void onResolveFailed(NsdServiceInfo nsdServiceInfo, int i) {
            Log.e("DiscoveryManager", "onResolveFailed: " + nsdServiceInfo + " - " + i);
        }

        @Override
        public void onServiceResolved(NsdServiceInfo nsdServiceInfo) {
            Log.e("DiscoveryManager", "onServiceResolved: " + nsdServiceInfo);
            if (mDesiredState != DesiredState.ACTIVE || mCurrentState != CurrentState.ACTIVE) {
                return;
            }

            mRichDataResolver.resolveRichData(nsdServiceInfo, new RichDataCallback() {
                @Override
                public void onRichDataResolved(NsdServiceInfo info, RichData richData) {
                    Log.e("DiscoveryManager", "onRichDataResolved: " + info +
                            " - " + richData.getDescription());
                    info.setAttribute("descr", richData.getDescription());
                    Message msg = mServiceInfoHandler.obtainMessage(
                            DiscoverActivity.MESSAGE_ADD_SERVICE, info);
                    msg.sendToTarget();
                }

                @Override
                public void onRichDataFailure(NsdServiceInfo info) {
                    Log.e("DiscoveryManager", "onRichDataFailure: " + info);
                    Message msg = mServiceInfoHandler.obtainMessage(
                            DiscoverActivity.MESSAGE_ADD_SERVICE, info);
                    msg.sendToTarget();
                }
            });
        }
    }

    class RichData {
        String mDescription = null;

        String getDescription() {
            return mDescription;
        }

        void setDescription(String description) {
            mDescription = description;
        }
    }

    interface RichDataCallback {
        void onRichDataResolved(NsdServiceInfo info, RichData richData);
        void onRichDataFailure(NsdServiceInfo info);
    }
    class RichDataResolver implements Runnable {
        static final String DESCR_PATH = "/flyweb-descr.txt";

        Thread mThread;
        Queue<Pair<NsdServiceInfo, RichDataCallback>> mQueue;
        boolean mStopRequested;

        RichDataResolver() {
            mThread = new Thread(this);
            mQueue = new ConcurrentLinkedQueue<>();
            mStopRequested = false;
        }

        void resolveRichData(final NsdServiceInfo serviceInfo,
                             final RichDataCallback callback)
        {
            final Handler handler = new Handler(new Handler.Callback() {
                @Override
                public boolean handleMessage(Message message) {
                    if (message.what == 0) {
                        // Failure.
                        callback.onRichDataFailure(serviceInfo);
                    } else {
                        callback.onRichDataResolved(serviceInfo, (RichData) message.obj);
                    }
                    return true;
                }
            });
            RichDataCallback wrappedCb = new RichDataCallback() {
                @Override
                public void onRichDataResolved(NsdServiceInfo info, RichData richData) {
                    handler.obtainMessage(1, richData).sendToTarget();
                }

                @Override
                public void onRichDataFailure(NsdServiceInfo info) {
                    handler.obtainMessage(0).sendToTarget();
                }
            };
            synchronized(this) {
                mQueue.add(new Pair<NsdServiceInfo, RichDataCallback>(serviceInfo, wrappedCb));
                this.notify();
            }
        }

        void start() {
            mThread.start();
        }
        void stop() {
            synchronized (this) {
                mStopRequested = true;
            }
            for (;;) {
                try {
                    mThread.join();
                } catch (InterruptedException exc) {
                    continue;
                }
            }
        }

        @Override
        public void run() {
            for (;;) {
                Log.e("BOO", "HERE1");
                NsdServiceInfo serviceInfo = null;
                RichDataCallback callback = null;
                synchronized (this) {
                    Log.e("BOO", "HERE2");
                    if (mStopRequested) {
                        break;
                    }

                    if (mQueue.isEmpty()) {
                        Log.e("BOO", "HERE3");
                        try {
                            this.wait();
                        } catch (InterruptedException exc) {
                            continue;
                        }
                    }

                    Log.e("BOO", "HERE4");
                    Pair<NsdServiceInfo, RichDataCallback> pair = mQueue.remove();
                    serviceInfo = pair.first;
                    callback = pair.second;
                }

                lookupRichData(serviceInfo, callback);
            }
        }

        private void lookupRichData(NsdServiceInfo serviceInfo, RichDataCallback callback) {
            // Construct URL for description.
            String hostString = serviceInfo.getHost().getHostAddress();
            int port = serviceInfo.getPort();
            String urlBase = "http://" + hostString + ":" + port;
            String urlDescr = urlBase + DESCR_PATH;

            try {
                URL descrUrl = new URL(urlDescr);
                HttpURLConnection conn = (HttpURLConnection) descrUrl.openConnection();
                conn.setDoInput(false);
                conn.setDoOutput(true);
                conn.connect();

                int code = conn.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    Log.e("RichDataResolver", "Bad Response: " + urlDescr + " - " + code);
                    callback.onRichDataFailure(serviceInfo);
                    return;
                }

                String contentType = conn.getHeaderField("Content-Type");
                if (contentType == null || contentType != "text/plain") {
                    Log.e("RichDataResolver", "Bad Content Type: " + urlDescr + " - " + contentType);
                    callback.onRichDataFailure(serviceInfo);
                    return;
                }

                String contentLengthString = conn.getHeaderField("Content-Length");
                if (contentLengthString == null) {
                    Log.e("RichDataResolver", "No Content Length: " + urlDescr);
                    callback.onRichDataFailure(serviceInfo);
                    return;
                }
                int length = Integer.parseInt(contentLengthString);
                if (length < 1 || length > 100) {
                    Log.e("RichDataResolver", "Bad Content Length: " + urlDescr + " - " + length);
                    callback.onRichDataFailure(serviceInfo);
                    return;
                }

                InputStream data = conn.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(data));
                char buf[] = new char[length];
                if (br.read(buf) != length) {
                    Log.e("RichDataResolver", "Bad Read: " + urlDescr);
                    callback.onRichDataFailure(serviceInfo);
                    return;
                }

                RichData richData = new RichData();
                richData.setDescription(new String(buf));

                callback.onRichDataResolved(serviceInfo, richData);
            } catch (MalformedURLException exc) {
                Log.e("RichDataResolver", "Malformed URL: " + urlDescr);
                callback.onRichDataFailure(serviceInfo);
            } catch (IOException exc) {
                Log.e("RichDataResolver", "IO Exception: " + urlDescr);
                callback.onRichDataFailure(serviceInfo);
            }
        }
    }
}
