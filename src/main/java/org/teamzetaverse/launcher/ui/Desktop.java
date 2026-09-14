package org.teamzetaverse.launcher.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.teamzetaverse.launcher.util.OperatingSystem;

final class Desktop {
    private Desktop() {
    }

    static void open(final Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException ignored) {
        }
        run(path.toAbsolutePath().toString());
    }

    static void browse(final String url) {
        if (url.startsWith("https://") || url.startsWith("http://")) {
            run(url);
        }
    }

    private static void run(final String target) {
        String[] command = switch (OperatingSystem.CURRENT) {
            case WINDOWS -> new String[]{"rundll32", "url.dll,FileProtocolHandler", target};
            case MACOS -> new String[]{"open", target};
            case LINUX -> new String[]{"xdg-open", target};
        };
        try {
            new ProcessBuilder(command).start();
        } catch (IOException e) {
            System.err.println("Could not open " + target + ": " + e.getMessage());
        }
    }

    static Path chooseArchiveToOpen() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.abnw")).flip();
            String chosen = TinyFileDialogs.tinyfd_openFileDialog("Import an ABNW instance", "", filters, "ABNW instance (*.abnw)", false);
            return chosen == null ? null : Path.of(chosen);
        }
    }

    static Path chooseArchiveToSave(final String suggestedName) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.abnw")).flip();
            String initial = Path.of(System.getProperty("user.home"), suggestedName + ".abnw").toString();
            String chosen = TinyFileDialogs.tinyfd_saveFileDialog("Export instance", initial, filters, "ABNW instance (*.abnw)");
            if (chosen == null) {
                return null;
            }
            return chosen.toLowerCase().endsWith(".abnw") ? Path.of(chosen) : Path.of(chosen + ".abnw");
        }
    }

    static Path chooseJava() {
        String chosen = TinyFileDialogs.tinyfd_openFileDialog("Choose a Java executable", "", null, null, false);
        return chosen == null ? null : Path.of(chosen);
    }
}
