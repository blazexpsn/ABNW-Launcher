package org.teamzetaverse.launcher.ui;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.opengl.GL;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.teamzetaverse.launcher.util.OperatingSystem;

public final class LauncherWindow {
    private final ImGuiImplGlfw imguiGlfw = new ImGuiImplGlfw();
    private final ImGuiImplGl3 imguiGl3 = new ImGuiImplGl3();
    private long window;

    public void run(final LauncherUi ui) {
        GLFWErrorCallback.createPrint(System.err).set();
        this.window = this.open();
        glfwSetWindowSizeLimits(this.window, 1040, 660, GLFW_DONT_CARE, GLFW_DONT_CARE);
        this.setIcon();
        glfwMakeContextCurrent(this.window);
        glfwSwapInterval(1);
        GL.createCapabilities();

        float scale = this.contentScale() * ui.uiScale();
        ImGui.createContext();
        ImGuiIO io = ImGui.getIO();
        io.setIniFilename(null);
        io.setConfigWindowsMoveFromTitleBarOnly(true);
        Fonts.load(io, scale);
        Theme.apply(scale);

        this.imguiGlfw.init(this.window, true);
        this.imguiGl3.init("#version 150");

        glfwShowWindow(this.window);
        try {
            while (!glfwWindowShouldClose(this.window)) {
                if (ui.isAnimating()) {
                    glfwPollEvents();
                } else {
                    glfwWaitEventsTimeout(ui.idleWait());
                }
                ui.beforeFrame();

                this.imguiGl3.newFrame();
                this.imguiGlfw.newFrame();
                ImGui.newFrame();
                ui.draw();
                ImGui.render();

                try (MemoryStack stack = MemoryStack.stackPush()) {
                    IntBuffer w = stack.mallocInt(1);
                    IntBuffer h = stack.mallocInt(1);
                    glfwGetFramebufferSize(this.window, w, h);
                    glViewport(0, 0, w.get(0), h.get(0));
                }
                glClearColor(0.059f, 0.043f, 0.051f, 1f);
                glClear(GL_COLOR_BUFFER_BIT);
                this.imguiGl3.renderDrawData(ImGui.getDrawData());
                glfwSwapBuffers(this.window);
            }
        } finally {
            ui.shutdown();
            this.imguiGl3.shutdown();
            this.imguiGlfw.shutdown();
            ImGui.destroyContext();
            glfwDestroyWindow(this.window);
            glfwTerminate();
        }
    }

    private long open() {
        int requested = requestedPlatform();
        long opened = this.tryOpen(requested);
        if (opened != MemoryUtil.NULL) {
            return opened;
        }
        boolean waylandSession = System.getenv("WAYLAND_DISPLAY") != null || "wayland".equalsIgnoreCase(System.getenv("XDG_SESSION_TYPE"));
        if (OperatingSystem.CURRENT == OperatingSystem.LINUX && requested == GLFW_ANY_PLATFORM && waylandSession
            && glfwPlatformSupported(GLFW_PLATFORM_X11)) {
            System.err.println("The launcher window could not open on Wayland; retrying with X11.");
            opened = this.tryOpen(GLFW_PLATFORM_X11);
            if (opened != MemoryUtil.NULL) {
                return opened;
            }
        }
        throw new IllegalStateException(OperatingSystem.CURRENT == OperatingSystem.LINUX
            ? "Could not create the launcher window on Wayland or X11 (OpenGL 3.2 is required)"
            : "Could not create the launcher window (OpenGL 3.2 is required)");
    }

    private long tryOpen(final int platform) {
        if (platform != GLFW_ANY_PLATFORM && glfwPlatformSupported(platform)) {
            glfwInitHint(GLFW_PLATFORM, platform);
        } else {
            glfwInitHint(GLFW_PLATFORM, GLFW_ANY_PLATFORM);
        }
        if (!glfwInit()) {
            return MemoryUtil.NULL;
        }
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        if (OperatingSystem.CURRENT == OperatingSystem.MACOS) {
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        }
        glfwWindowHint(GLFW_SCALE_TO_MONITOR, GLFW_TRUE);
        long created = glfwCreateWindow(1320, 840, "ABNW Launcher", MemoryUtil.NULL, MemoryUtil.NULL);
        if (created == MemoryUtil.NULL) {
            glfwTerminate();
        }
        return created;
    }

    private static int requestedPlatform() {
        String value = System.getenv("ABNW_WINDOW_PLATFORM");
        if (value == null) {
            return GLFW_ANY_PLATFORM;
        }
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "x11" -> GLFW_PLATFORM_X11;
            case "wayland" -> GLFW_PLATFORM_WAYLAND;
            default -> GLFW_ANY_PLATFORM;
        };
    }

    private float contentScale() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var x = stack.mallocFloat(1);
            var y = stack.mallocFloat(1);
            glfwGetWindowContentScale(this.window, x, y);
            return Math.max(1f, Math.max(x.get(0), y.get(0)));
        }
    }

    private void setIcon() {
        if (OperatingSystem.CURRENT == OperatingSystem.MACOS || glfwGetPlatform() == GLFW_PLATFORM_WAYLAND) {
            return;
        }
        String[] sizes = {"16", "32", "48", "256"};
        GLFWImage.Buffer images = GLFWImage.malloc(sizes.length);
        ByteBuffer[] pixels = new ByteBuffer[sizes.length];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int loaded = 0;
            for (String size : sizes) {
                ByteBuffer file = resource("/assets/icon_" + size + ".png");
                if (file == null) {
                    continue;
                }
                IntBuffer w = stack.mallocInt(1);
                IntBuffer h = stack.mallocInt(1);
                IntBuffer channels = stack.mallocInt(1);
                ByteBuffer image = STBImage.stbi_load_from_memory(file, w, h, channels, 4);
                MemoryUtil.memFree(file);
                if (image == null) {
                    continue;
                }
                pixels[loaded] = image;
                images.position(loaded).width(w.get(0)).height(h.get(0)).pixels(image);
                loaded++;
            }
            if (loaded > 0) {
                images.position(0).limit(loaded);
                glfwSetWindowIcon(this.window, images);
            }
        } finally {
            for (ByteBuffer image : pixels) {
                if (image != null) {
                    STBImage.stbi_image_free(image);
                }
            }
            images.free();
        }
    }

    private static ByteBuffer resource(final String name) {
        try (InputStream in = LauncherWindow.class.getResourceAsStream(name)) {
            if (in == null) {
                return null;
            }
            byte[] bytes = in.readAllBytes();
            ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
            buffer.put(bytes).flip();
            return buffer;
        } catch (IOException e) {
            return null;
        }
    }
}
