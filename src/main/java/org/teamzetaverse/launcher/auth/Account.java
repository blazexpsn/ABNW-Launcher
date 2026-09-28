package org.teamzetaverse.launcher.auth;

public final class Account {
    public String uuid = "";
    public String name = "";
    public String xuid = "";
    public boolean devOffline;
    /** The Microsoft client ID this account signed in with; refreshes must use the same one. Empty for accounts saved before this was tracked. */
    public String msaClientId = "";
    public transient String msaRefreshToken = "";
    public transient String minecraftToken = "";
    public transient long minecraftTokenExpiry;
    public transient String cosmeticsToken = "";

    public boolean hasValidMinecraftToken() {
        return this.minecraftToken != null && !this.minecraftToken.isEmpty() && System.currentTimeMillis() < this.minecraftTokenExpiry - 60_000L;
    }
}
