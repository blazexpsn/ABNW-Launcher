package org.teamzetaverse.launcher;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import org.teamzetaverse.launcher.ui.LauncherUi;
import org.teamzetaverse.launcher.ui.LauncherWindow;
import org.teamzetaverse.launcher.util.OperatingSystem;

public final class LauncherMain {
    private LauncherMain() {
    }

    public static void main(final String[] args) {
        if (!OperatingSystem.isSupported()) {
            refuseUnsupportedSystem();
            return;
        }
        if (needsFirstThreadRelaunch()) {
            relaunchOnFirstThread(args);
        }
        LauncherPaths paths = LauncherPaths.defaultLocation();
        try {
            Files.createDirectories(paths.root());
            Files.createDirectories(paths.logs());
            PrintStream log = new PrintStream(Files.newOutputStream(paths.logs().resolve("launcher.log"),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING), true, "UTF-8");
            System.setOut(new TeePrintStream(System.out, log));
            System.setErr(new TeePrintStream(System.err, log));
        } catch (IOException e) {
            System.err.println("Could not prepare " + paths.root() + ": " + e.getMessage());
        }
        System.out.println("ABNW Launcher " + BuildInfo.VERSION + " -- data in " + paths.root());

        LauncherConfig config = LauncherConfig.load(paths);
        new LauncherWindow().run(new LauncherUi(paths, config));
    }

    private static boolean needsFirstThreadRelaunch() {
        return OperatingSystem.CURRENT == OperatingSystem.MACOS
            && !"1".equals(System.getenv("JAVA_STARTED_ON_FIRST_THREAD_" + ProcessHandle.current().pid()))
            && !Boolean.getBoolean("abnw.firstThreadRelaunch");
    }

    private static void relaunchOnFirstThread(final String[] args) {
        try {
            Path classPath = Path.of(LauncherMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            List<String> command = new ArrayList<>();
            command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
            command.add("-XstartOnFirstThread");
            command.add("-Dabnw.firstThreadRelaunch=true");
            try {
                command.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());
            } catch (RuntimeException | LinkageError ignored) {
            }
            command.add("-cp");
            command.add(classPath.toString());
            command.add(LauncherMain.class.getName());
            command.addAll(List.of(args));
            Process process = new ProcessBuilder(command).inheritIO().start();
            System.exit(process.waitFor());
        } catch (Exception e) {
            System.err.println("Could not restart the launcher with -XstartOnFirstThread: " + e.getMessage());
        }
    }

    private static void refuseUnsupportedSystem() {
        String message = "The ABNW Launcher runs on Windows, macOS and Linux. This system reports itself as \""
            + OperatingSystem.rawName() + "\", which is not supported.";
        System.err.println(message);
        System.exit(1);
    }

    private static final class TeePrintStream extends PrintStream {
        private final PrintStream second;

        TeePrintStream(final PrintStream first, final PrintStream second) {
            super(first, true);
            this.second = second;
        }

        @Override
        public void write(final int b) {
            super.write(b);
            this.second.write(b);
        }

        @Override
        public void write(final byte[] buf, final int off, final int len) {
            super.write(buf, off, len);
            this.second.write(buf, off, len);
        }

        @Override
        public void flush() {
            super.flush();
            this.second.flush();
        }
    }
}
