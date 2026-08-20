package dev.zis30axs.sigma.hotinjection.event;

public abstract class CancellableEvent implements Event {
    private boolean cancelled;

    public final boolean isCancelled() {
        return cancelled;
    }

    public final void cancel() {
        cancelled = true;
    }

    public final void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
