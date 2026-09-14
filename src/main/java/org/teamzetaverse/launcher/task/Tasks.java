package org.teamzetaverse.launcher.task;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class Tasks {
    @FunctionalInterface
    public interface Work<T> {
        T run(Progress progress) throws Exception;
    }

    private final ExecutorService pool = Executors.newFixedThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "ABNW Launcher worker");
        thread.setDaemon(true);
        return thread;
    });
    private final ConcurrentLinkedQueue<Runnable> uiQueue = new ConcurrentLinkedQueue<>();
    private final List<Progress> running = new CopyOnWriteArrayList<>();
    private final AtomicInteger active = new AtomicInteger();

    public <T> Progress submit(final String title, final Work<T> work, final Consumer<T> onSuccess, final Consumer<Throwable> onFailure) {
        Progress progress = new Progress(title);
        this.running.add(progress);
        this.active.incrementAndGet();
        this.pool.execute(() -> {
            try {
                T result = work.run(progress);
                this.onUi(() -> {
                    if (onSuccess != null) {
                        onSuccess.accept(result);
                    }
                });
            } catch (Progress.CancelledException e) {
            } catch (Throwable e) {
                e.printStackTrace();
                this.onUi(() -> {
                    if (onFailure != null) {
                        onFailure.accept(e);
                    }
                });
            } finally {
                this.running.remove(progress);
                this.active.decrementAndGet();
            }
        });
        return progress;
    }

    public void onUi(final Runnable action) {
        this.uiQueue.add(action);
    }

    public void drainUiQueue() {
        Runnable action;
        while ((action = this.uiQueue.poll()) != null) {
            action.run();
        }
    }

    public List<Progress> running() {
        return this.running;
    }

    public boolean isBusy() {
        return this.active.get() > 0;
    }

    public void shutdown() {
        this.running.forEach(Progress::cancel);
        this.pool.shutdownNow();
    }
}
