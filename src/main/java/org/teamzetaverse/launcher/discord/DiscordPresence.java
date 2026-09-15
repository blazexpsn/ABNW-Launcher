package org.teamzetaverse.launcher.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public final class DiscordPresence implements AutoCloseable {
    public enum State {
        DISABLED, NOT_CONFIGURED, CONNECTING, CONNECTED, DISCORD_NOT_RUNNING
    }

    public record Activity(String details, String state, long startedEpochSeconds, String buttonLabel, String buttonUrl) {
    }

    private static final long RETRY_NANOS = TimeUnit.SECONDS.toNanos(15);

    private final String clientId;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = this.lock.newCondition();
    private final Thread thread;
    private volatile boolean enabled;
    private volatile Activity desired;
    private volatile State state;
    private volatile boolean running = true;

    public DiscordPresence(final String clientId, final boolean enabled) {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.enabled = enabled;
        this.state = this.clientId.isEmpty() ? State.NOT_CONFIGURED : enabled ? State.CONNECTING : State.DISABLED;
        this.thread = new Thread(this::loop, "ABNW Discord presence");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    public boolean isConfigured() {
        return !this.clientId.isEmpty();
    }

    public State state() {
        return this.state;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        this.wake();
    }

    public void setActivity(final Activity activity) {
        if (!Objects.equals(this.desired, activity)) {
            this.desired = activity;
            this.wake();
        }
    }

    private void wake() {
        this.lock.lock();
        try {
            this.changed.signalAll();
        } finally {
            this.lock.unlock();
        }
    }

    private void waitFor(final long nanos) {
        this.lock.lock();
        try {
            this.changed.awaitNanos(nanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            this.running = false;
        } finally {
            this.lock.unlock();
        }
    }

    private void loop() {
        DiscordIpc ipc = null;
        Activity sent = null;
        long nextAttempt = 0;
        while (this.running) {
            if (this.clientId.isEmpty() || !this.enabled) {
                if (ipc != null) {
                    ipc.close();
                    ipc = null;
                    sent = null;
                }
                this.state = this.clientId.isEmpty() ? State.NOT_CONFIGURED : State.DISABLED;
                this.waitFor(TimeUnit.SECONDS.toNanos(5));
                continue;
            }
            if (ipc == null) {
                if (System.nanoTime() < nextAttempt) {
                    this.waitFor(Math.max(1, nextAttempt - System.nanoTime()));
                    continue;
                }
                this.state = State.CONNECTING;
                try {
                    ipc = DiscordIpc.connect(this.clientId);
                    sent = null;
                    this.state = State.CONNECTED;
                } catch (IOException e) {
                    this.state = State.DISCORD_NOT_RUNNING;
                    nextAttempt = System.nanoTime() + RETRY_NANOS;
                    continue;
                }
            }
            Activity target = this.desired;
            if (target != null && !target.equals(sent)) {
                try {
                    ipc.setActivity(toJson(target));
                    sent = target;
                } catch (IOException e) {
                    ipc.close();
                    ipc = null;
                    this.state = State.DISCORD_NOT_RUNNING;
                    nextAttempt = System.nanoTime() + RETRY_NANOS;
                    continue;
                }
            }
            this.waitFor(TimeUnit.SECONDS.toNanos(20));
        }
        if (ipc != null) {
            ipc.close();
        }
    }

    private static JsonObject toJson(final Activity activity) {
        JsonObject json = new JsonObject();
        if (activity.details() != null && !activity.details().isBlank()) {
            json.addProperty("details", clip(activity.details()));
        }
        if (activity.state() != null && !activity.state().isBlank()) {
            json.addProperty("state", clip(activity.state()));
        }
        if (activity.startedEpochSeconds() > 0) {
            JsonObject timestamps = new JsonObject();
            timestamps.addProperty("start", activity.startedEpochSeconds());
            json.add("timestamps", timestamps);
        }
        JsonObject assets = new JsonObject();
        assets.addProperty("large_image", "abnw");
        assets.addProperty("large_text", "A Brand New World");
        json.add("assets", assets);
        if (activity.buttonUrl() != null && activity.buttonUrl().startsWith("https://")) {
            JsonObject button = new JsonObject();
            button.addProperty("label", clip(activity.buttonLabel()));
            button.addProperty("url", activity.buttonUrl());
            JsonArray buttons = new JsonArray();
            buttons.add(button);
            json.add("buttons", buttons);
        }
        return json;
    }

    private static String clip(final String text) {
        return text.length() <= 120 ? text : text.substring(0, 119) + "…";
    }

    @Override
    public void close() {
        this.running = false;
        this.wake();
    }
}
