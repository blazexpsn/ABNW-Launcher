package org.teamzetaverse.launcher.auth;

public final class Account {
    public String uuid = "";
    public String name = "";
    public String xuid = "";
    public transient String msaRefreshToken = "";
    public transient String minecraftToken = "";
    public transient long minecraftTokenExpiry;

    public boolean hasValidMinecraftToken() {
        return this.minecraftToken != null && !this.minecraftToken.isEmpty() && System.currentTimeMillis() < this.minecraftTokenExpiry - 60_000L;
    }
}
