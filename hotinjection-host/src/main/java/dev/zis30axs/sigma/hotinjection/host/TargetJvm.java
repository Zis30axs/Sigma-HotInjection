package dev.zis30axs.sigma.hotinjection.host;

public final class TargetJvm {
    private final String pid;
    private final String displayName;

    public TargetJvm(String pid, String displayName) {
        this.pid = pid;
        this.displayName = displayName == null ? "" : displayName;
    }

    public String getPid() { return pid; }
    public String getDisplayName() { return displayName; }

    @Override
    public String toString() {
        return pid + "  " + (displayName.isEmpty() ? "Java process" : displayName);
    }
}
