package org.teamzetaverse.launcher.release;

public final class Release {
    public String id = "";
    public String name = "";
    public String minecraft = "";
    public String releaseTime = "";
    public FileRef manifest = new FileRef();
    public FileRef delta = new FileRef();
    public FileRef source = new FileRef();
    public FileRef target = new FileRef();
    public FileRef libraries = new FileRef();

    public static final class FileRef {
        public String url = "";
        public long size = -1;
        public String sha256 = "";
    }

    public String displayName() {
        return this.name == null || this.name.isBlank() ? "ABNW " + this.id : this.name;
    }

    public boolean isResolved() {
        return isSha256(this.delta.sha256) && isSha256(this.source.sha256) && isSha256(this.target.sha256);
    }

    static boolean isSha256(final String value) {
        return value != null && value.matches("[0-9a-fA-F]{64}");
    }

    public static String safe(final String value) {
        String cleaned = value == null ? "" : value.replaceAll("[^A-Za-z0-9._+-]", "_");
        return cleaned.isEmpty() ? "_" : cleaned;
    }

    public String patchedJarName() {
        return "abnw-" + safe(this.id) + "-mc" + safe(this.minecraft) + "-" + this.target.sha256.substring(0, 12) + ".jar";
    }

    public String deltaFileName() {
        return "abnw-" + safe(this.id) + "-mc" + safe(this.minecraft) + "-" + this.delta.sha256.substring(0, 12) + ".xdelta";
    }
}
