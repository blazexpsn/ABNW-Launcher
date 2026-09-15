package org.teamzetaverse.launcher.discord;

import com.google.gson.JsonObject;
import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.teamzetaverse.launcher.util.Json;
import org.teamzetaverse.launcher.util.OperatingSystem;

final class DiscordIpc implements Closeable {
    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;
    private static final int OP_CLOSE = 2;
    private static final int OP_PING = 3;
    private static final int OP_PONG = 4;
    private static final int MAX_FRAME = 1 << 20;

    private interface Pipe extends Closeable {
        void write(byte[] bytes) throws IOException;

        void readFully(byte[] bytes) throws IOException;
    }

    private final Pipe pipe;

    private DiscordIpc(final Pipe pipe) {
        this.pipe = pipe;
    }

    static DiscordIpc connect(final String clientId) throws IOException {
        IOException last = new IOException("Discord is not running");
        for (int i = 0; i < 10; i++) {
            try {
                DiscordIpc ipc = new DiscordIpc(open(i));
                try {
                    JsonObject handshake = new JsonObject();
                    handshake.addProperty("v", 1);
                    handshake.addProperty("client_id", clientId);
                    ipc.send(OP_HANDSHAKE, handshake);
                    ipc.receive();
                    return ipc;
                } catch (IOException e) {
                    ipc.close();
                    throw e;
                }
            } catch (IOException e) {
                last = e;
            }
        }
        throw last;
    }

    private static Pipe open(final int index) throws IOException {
        if (OperatingSystem.CURRENT == OperatingSystem.WINDOWS) {
            RandomAccessFile file = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + index, "rw");
            return new Pipe() {
                @Override
                public void write(final byte[] bytes) throws IOException {
                    file.write(bytes);
                }

                @Override
                public void readFully(final byte[] bytes) throws IOException {
                    file.readFully(bytes);
                }

                @Override
                public void close() throws IOException {
                    file.close();
                }
            };
        }
        for (Path candidate : unixCandidates(index)) {
            if (!Files.exists(candidate)) {
                continue;
            }
            SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
            try {
                channel.connect(UnixDomainSocketAddress.of(candidate));
            } catch (IOException e) {
                channel.close();
                continue;
            }
            return new Pipe() {
                @Override
                public void write(final byte[] bytes) throws IOException {
                    ByteBuffer buffer = ByteBuffer.wrap(bytes);
                    while (buffer.hasRemaining()) {
                        channel.write(buffer);
                    }
                }

                @Override
                public void readFully(final byte[] bytes) throws IOException {
                    ByteBuffer buffer = ByteBuffer.wrap(bytes);
                    while (buffer.hasRemaining()) {
                        if (channel.read(buffer) < 0) {
                            throw new IOException("Discord closed the connection");
                        }
                    }
                }

                @Override
                public void close() throws IOException {
                    channel.close();
                }
            };
        }
        throw new IOException("no discord-ipc-" + index + " socket");
    }

    private static List<Path> unixCandidates(final int index) {
        List<String> roots = new ArrayList<>();
        for (String variable : new String[]{"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
            String value = System.getenv(variable);
            if (value != null && !value.isBlank()) {
                roots.add(value);
            }
        }
        roots.add("/tmp");
        String[] subfolders = {"", "app/com.discordapp.Discord", "app/com.discordapp.DiscordCanary", "snap.discord", ".flatpak/dev.vencord.Vesktop/xdg-run"};
        List<Path> candidates = new ArrayList<>();
        for (String root : roots) {
            for (String sub : subfolders) {
                Path base = sub.isEmpty() ? Path.of(root) : Path.of(root, sub);
                candidates.add(base.resolve("discord-ipc-" + index));
            }
        }
        return candidates;
    }

    void setActivity(final JsonObject activity) throws IOException {
        JsonObject args = new JsonObject();
        args.addProperty("pid", ProcessHandle.current().pid());
        args.add("activity", activity);
        JsonObject frame = new JsonObject();
        frame.addProperty("cmd", "SET_ACTIVITY");
        frame.add("args", args);
        frame.addProperty("nonce", UUID.randomUUID().toString());
        this.send(OP_FRAME, frame);
        JsonObject response = this.receive();
        if ("ERROR".equals(Json.string(response, "evt"))) {
            JsonObject data = Json.object(response, "data");
            throw new IOException("Discord rejected the activity: " + Json.string(data, "message"));
        }
    }

    private void send(final int op, final JsonObject payload) throws IOException {
        byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
        ByteBuffer frame = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
        frame.putInt(op).putInt(body.length).put(body);
        this.pipe.write(frame.array());
    }

    private JsonObject receive() throws IOException {
        while (true) {
            byte[] header = new byte[8];
            this.pipe.readFully(header);
            ByteBuffer parsed = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            int op = parsed.getInt();
            int length = parsed.getInt();
            if (length < 0 || length > MAX_FRAME) {
                throw new IOException("Discord sent an invalid frame");
            }
            byte[] body = new byte[length];
            this.pipe.readFully(body);
            JsonObject payload = length == 0 ? new JsonObject() : Json.parseObject(new String(body, StandardCharsets.UTF_8));
            switch (op) {
                case OP_PING -> this.send(OP_PONG, payload);
                case OP_CLOSE -> throw new IOException("Discord closed the connection: " + Json.string(payload, "message"));
                default -> {
                    return payload;
                }
            }
        }
    }

    @Override
    public void close() {
        try {
            this.pipe.close();
        } catch (IOException ignored) {
        }
    }
}
