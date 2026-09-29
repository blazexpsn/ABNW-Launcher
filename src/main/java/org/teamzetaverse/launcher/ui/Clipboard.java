package org.teamzetaverse.launcher.ui;

import java.nio.ByteBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;

final class Clipboard {
    private Clipboard() {
    }

    /**
     * Puts text on the system clipboard. {@link LauncherWindow} installs this as ImGui's clipboard writer, so
     * {@code ImGui.setClipboardText} and Ctrl+C in text fields all come through here, on the UI thread.
     *
     * <p>imgui-java's stock GLFW backend passes the text to LWJGL's {@code glfwSetClipboardString(long, CharSequence)},
     * which encodes it on LWJGL's per-thread memory stack (64 KB by default). Anything longer, such as the log of a
     * running game, overflows it, and the error is thrown inside a callback from ImGui's native code, which kills the
     * JVM. This encodes into a heap buffer of the right size instead.
     */
    static void set(final String text) {
        ByteBuffer utf8 = MemoryUtil.memUTF8(text, true);
        try {
            GLFW.nglfwSetClipboardString(GLFW.glfwGetCurrentContext(), MemoryUtil.memAddress(utf8));
        } finally {
            MemoryUtil.memFree(utf8);
        }
    }
}
