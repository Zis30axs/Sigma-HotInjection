package dev.zis30axs.sigma.hotinjection.host.model;

/**
 * One overlay rectangle received from the Agent, in normalized client-area
 * coordinates: {@code 0,0} is the top-left corner of the Minecraft client area
 * and {@code 1,1} the bottom-right one.
 */
public final class RemoteBox {
    private final double x0;
    private final double y0;
    private final double x1;
    private final double y1;
    private final int argb;
    private final String label;

    public RemoteBox(double x0, double y0, double x1, double y1, int argb, String label) {
        this.x0 = x0;
        this.y0 = y0;
        this.x1 = x1;
        this.y1 = y1;
        this.argb = argb;
        this.label = label == null ? "" : label;
    }

    public double getX0() { return x0; }
    public double getY0() { return y0; }
    public double getX1() { return x1; }
    public double getY1() { return y1; }
    public int getArgb() { return argb; }
    public String getLabel() { return label; }
}
