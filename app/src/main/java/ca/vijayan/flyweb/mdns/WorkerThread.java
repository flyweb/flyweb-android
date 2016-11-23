package ca.vijayan.flyweb.mdns;

import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by Kannan Vijayan on 11/16/2016.
 */

public abstract class WorkerThread extends Thread {
    private Queue<Runnable> mRunnables;
    private boolean mStop;
    private String mName;
    private PriorityQueue<TimeoutEntry> mTimeouts;
    private long mNextTimeoutId;

    public WorkerThread(String name) {
        mRunnables = new ConcurrentLinkedQueue<>();
        mStop = false;
        mName = name;
        mTimeouts = new PriorityQueue<>();
        mNextTimeoutId = 0;
    }

    public void addRunnable(Runnable runnable) {
        synchronized (mRunnables) {
            mRunnables.add(runnable);
            mRunnables.notify();
        }
    }

    private String logName() {
        return "WT/" + mName;
    }

    private static class TimeoutEntry implements Comparable<TimeoutEntry> {
        WorkerThread mWorkerThread;
        long mId;
        Date mWhen;
        Runnable mRunnable;

        TimeoutEntry(WorkerThread workerThread, long id, Date when, Runnable runnable) {
            mWorkerThread = workerThread;
            mId = id;
            mWhen = when;
            mRunnable = runnable;
        }

        @Override
        public int compareTo(TimeoutEntry timeoutEntry) {
            return mWhen.compareTo(timeoutEntry.mWhen);
        }

        public long msUntilTriggered() {
            return mWhen.getTime() - (new Date()).getTime();
        }

        public boolean isTriggered() {
            return msUntilTriggered() <= 0;
        }

        public void run() {
            try {
                mRunnable.run();
            } catch (Exception exc) {
                Log.e(mWorkerThread.logName(), "Timeout runnable threw exception.", exc);
            }
        }
    }

    public long setTimeout(int ms, Runnable runnable) {
        long nowms = (new Date()).getTime();
        Date when = new Date(nowms + ms);
        long id = ++mNextTimeoutId;
        TimeoutEntry entry = new TimeoutEntry(this, id, when, runnable);
        synchronized (mTimeouts) {
            mTimeouts.add(entry);
        }
        synchronized (mRunnables) {
            mRunnables.notify();
        }
        return id;
    }

    public boolean clearTimeout(long id) {
        TimeoutEntry toClear = null;
        synchronized (mTimeouts) {
            for (TimeoutEntry entry : mTimeouts) {
                if (entry.mId == id) {
                    toClear = entry;
                    break;
                }
            }
            if (toClear != null) {
                mTimeouts.remove(toClear);
                return true;
            }
            return false;
        }
    }

    private long calculateMsToWait() {
        synchronized (mTimeouts) {
            if (mTimeouts.isEmpty()) {
                return -1;
            }
            long msUntilNext = mTimeouts.peek().msUntilTriggered();
            if (msUntilNext < 0) {
                msUntilNext = 0;
            }
            return msUntilNext;
        }
    }

    abstract protected void onStart();

    @Override
    public void start() {
        onStart();
        super.start();
    }

    public void shutdown() {
        synchronized (this) {
            mStop = true;
        }
        synchronized (mRunnables) {
            mRunnables.notify();
        }
        while (isAlive()) {
            try {
                this.join();
            } catch (InterruptedException exc) {
                // Pass.
            }
        }
    }

    @Override
    public void run() {
        for (;;) {
            // Check for stop.
            synchronized (this) {
                if (mStop) {
                    break;
                }
            }

            // Execute all triggered timeouts.
            synchronized (mTimeouts) {
                while (!mTimeouts.isEmpty() && mTimeouts.peek().isTriggered()) {
                    mTimeouts.poll().run();
                }
            }

            // Drain the queue.
            List<Runnable> nextRunnables = new ArrayList<>();
            synchronized (mRunnables) {
                while (!mRunnables.isEmpty()) {
                    nextRunnables.add(mRunnables.poll());
                }
            }

            // Run all the queued runnables.
            for (Runnable runnable : nextRunnables) {
                try {
                    runnable.run();
                } catch (RuntimeException exc) {
                    Log.e(logName(), "Runnable threw exception.", exc);
                }
            }

            // Calculate time to wait.
            long msToWait = calculateMsToWait();
            synchronized (mRunnables) {
                try {
                    if (msToWait < 0) {
                        mRunnables.wait();
                    } else {
                        mRunnables.wait(msToWait);
                    }
                } catch (InterruptedException exc) {
                    // Pass.
                }
            }
        }

        // Ensure that the thread is out of work.
        if (! mRunnables.isEmpty()) {
            Log.e(logName(), "Stopped with queued runnables.");
        }
    }
}
