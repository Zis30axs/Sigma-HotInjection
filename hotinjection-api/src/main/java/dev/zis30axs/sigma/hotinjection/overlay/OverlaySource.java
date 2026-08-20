package dev.zis30axs.sigma.hotinjection.overlay;

import java.util.List;

/**
 * Implemented by modules that want to draw into the external overlay.
 *
 * <p>The control channel polls every <em>enabled</em> source once per overlay
 * frame. Implementations run on the polling thread, must not block and should
 * return an empty list instead of throwing when the game state is unusable.</p>
 */
public interface OverlaySource {
    /**
     * @param aspectRatio width / height of the Minecraft client area, as
     *                    measured by the Host overlay window.
     * @return boxes in normalized client-area coordinates, never {@code null}.
     */
    List<OverlayBox> collectOverlay(double aspectRatio);
}
