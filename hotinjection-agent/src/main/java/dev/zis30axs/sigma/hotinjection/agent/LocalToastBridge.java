package dev.zis30axs.sigma.hotinjection.agent;

public final class LocalToastBridge {
    private LocalToastBridge() {
    }

    public static boolean show(String message) {
        return LocalToast.show(message);
    }
}
