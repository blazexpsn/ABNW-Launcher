package org.teamzetaverse.launcher.launch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class GameProcess {
    private static final int MAX_LINES = 4000;

    private final String instanceId;
    private final Process process;
    private final ArrayDeque<String> lines = new ArrayDeque<>();
    private final long started = System.currentTimeMillis();
    private volatile int version;

    GameProcess(final String instanceId, final Process process, final Path logFile, final Consumer<GameProcess> onExit) {
        this.instanceId = instanceId;
        this.process = process;
        Thread reader = new Thread(() -> this.pump(logFile, onExit), "ABNW game output");
        reader.setDaemon(true);
        reader.start();
    }

    private void pump(final Path logFile, final Consumer<GameProcess> onExit) {
        Writer log = null;
        try {
            Files.createDirectories(logFile.getParent());
            log = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            this.append("[launcher] could not write " + logFile + ": " + e.getMessage());
        }
        try (BufferedReader in = new BufferedReader(new InputStreamReader(this.process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                this.append(line);
                if (log != null) {
                    log.write(line);
                    log.write(System.lineSeparator());
                    log.flush();
                }
            }
        } catch (IOException ignored) {
        } finally {
            try {
                int code = this.process.waitFor();
                this.append("[launcher] The game exited with code " + code + ".");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (log != null) {
                try {
                    log.close();
                } catch (IOException ignored) {
                }
            }
            onExit.accept(this);
        }
    }

    void append(final String line) {
        synchronized (this.lines) {
            this.lines.addLast(line);
            while (this.lines.size() > MAX_LINES) {
                this.lines.removeFirst();
            }
            this.version++;
        }
    }

    public List<String> lines() {
        synchronized (this.lines) {
            return new ArrayList<>(this.lines);
        }
    }

    public int version() {
        return this.version;
    }

    public String instanceId() {
        return this.instanceId;
    }

    public boolean isAlive() {
        return this.process.isAlive();
    }

    public long startedMillis() {
        return this.started;
    }

    public void kill() {
        this.process.destroy();
    }
}
