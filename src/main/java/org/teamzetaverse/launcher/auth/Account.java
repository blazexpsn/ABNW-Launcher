package org.teamzetaverse.launcher.auth;

public final class Account {
    public String uuid = "";
    public String name = "";
    public String xuid = "";
    public String msaRefreshToken = "";
    public String minecraftToken = "";
    public long minecraftTokenExpiry;

    public boolean hasValidMinecraftToken() {
        return !this.minecraftToken.isEmpty() && System.currentTimeMillis() < this.minecraftTokenExpiry - 60_000L;
    }
}
