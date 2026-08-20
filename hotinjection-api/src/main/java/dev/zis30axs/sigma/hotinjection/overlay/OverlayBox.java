package dev.zis30axs.sigma.hotinjection.overlay;

/**
 * One rectangle produced by a module for the external click-through overlay.
 *
 * <p>Coordinates are normalized to the Minecraft client area: {@code 0,0} is the
 * top-left corner and {@code 1,1} the bottom-right one. The Agent never learns
 * the overlay window size, and the Host never needs game knowledge to draw the
 * result.</p>
 */
public final class OverlayBox {
    private final double x0;
    private final double y0;
    private final double x1;
    private final double y1;
    private final int argb;
    private final String label;

    public OverlayBox(double x0, double y0, double x1, double y1, int argb, String label) {
        this.x0 = Math.min(x0, x1);
        this.y0 = Math.min(y0, y1);
        this.x1 = Math.max(x0, x1);
        this.y1 = Math.max(y0, y1);
        this.argb = argb;
        this.label = label == null ? "" : label;
    }

    public double getX0() { return x0; }
    public double getY0() { return y0; }
    public double getX1() { return x1; }
    public double getY1() { return y1; }
    public int getArgb() { return argb; }

    /** Optional text drawn above the rectangle; empty when the module wants none. */
    public String getLabel() { return label; }
}
