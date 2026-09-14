package org.teamzetaverse.launcher.minecraft;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.teamzetaverse.launcher.net.Http;
import org.teamzetaverse.launcher.task.Progress;
import org.teamzetaverse.launcher.util.Hashing;

final class Downloads {
    record Entry(String url, Path target, String sha1, long size) {
    }

    private final List<Entry> entries = new ArrayList<>();

    void add(final String url, final Path target, final String sha1, final long size) {
        this.entries.add(new Entry(url, target, sha1, size));
    }

    boolean isEmpty() {
        return this.entries.isEmpty();
    }

    void run(final String stage, final Progress progress) throws IOException, Progress.CancelledException {
        progress.stage(stage, this.entries.size());
        ExecutorService pool = Executors.newFixedThreadPool(8, runnable -> {
            Thread thread = new Thread(runnable, "ABNW download");
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Entry entry : this.entries) {
                futures.add(pool.submit(() -> {
                    if (progress.isCancelled()) {
                        return null;
                    }
                    if (!Hashing.matches(entry.target(), "SHA-1", entry.sha1(), entry.size())) {
                        Http.download(entry.url(), entry.target(), entry.sha1(), null);
                    }
                    progress.advance(1);
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    throw cause instanceof IOException io ? io : new IOException(cause);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new Progress.CancelledException();
                }
            }
            progress.checkCancelled();
        } finally {
            pool.shutdownNow();
        }
    }
}
