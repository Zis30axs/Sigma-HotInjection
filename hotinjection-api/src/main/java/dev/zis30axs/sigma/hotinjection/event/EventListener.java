package dev.zis30axs.sigma.hotinjection.event;

public interface EventListener<E extends Event> {
    void onEvent(E event) throws Exception;
}
