package org.teamzetaverse.launcher;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
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
