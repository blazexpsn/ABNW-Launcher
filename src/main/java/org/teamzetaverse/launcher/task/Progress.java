package org.teamzetaverse.launcher.task;

import java.util.concurrent.atomic.AtomicLong;

public final class Progress {
    private final String title;
    private volatile String status = "";
    private final AtomicLong done = new AtomicLong();
    private volatile long total;
    private volatile boolean cancelled;

    public Progress(final String title) {
        this.title = title;
    }

    public String title() {
        return this.title;
    }

    public String status() {
        return this.status;
    }

    public void status(final String status) {
        this.status = status;
    }

    public void stage(final String status, final long total) {
        this.status = status;
        this.total = total;
        this.done.set(0);
    }

    public void advance(final long amount) {
        this.done.addAndGet(amount);
    }

    public float fraction() {
        long t = this.total;
        return t <= 0 ? -1f : Math.min(1f, this.done.get() / (float)t);
    }

    public void cancel() {
        this.cancelled = true;
    }

    public boolean isCancelled() {
        return this.cancelled;
    }

    public void checkCancelled() throws CancelledException {
        if (this.cancelled) {
            throw new CancelledException();
        }
    }

    public static final class CancelledException extends Exception {
        public CancelledException() {
            super("Cancelled");
        }
    }
}
