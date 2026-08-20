package dev.zis30axs.sigma.hotinjection.host.ui;

import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ColorAlphaType;
import io.github.humbleui.skija.ColorType;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontEdging;
import io.github.humbleui.skija.FontHinting;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.skija.FontStyle;
import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Typeface;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/** CPU-rasterized Skija text for the standalone Swing controller. */
public final class SkijaFontRenderer {
    public enum Weight { LIGHT, REGULAR, SEMIBOLD }

    private static final int CACHE_LIMIT = 320;
    private final Map<String, BufferedImage> cache = new LinkedHashMap<String, BufferedImage>(CACHE_LIMIT, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
            return size() > CACHE_LIMIT;
        }
    };

    private Typeface light;
    private Typeface regular;
    private Typeface semibold;
    private boolean skijaAvailable = true;

    public SkijaFontRenderer() {
        try {
            light = Typeface.makeFromName("Segoe UI Light", FontStyle.NORMAL);
            regular = Typeface.makeFromName("Segoe UI", FontStyle.NORMAL);
            semibold = Typeface.makeFromName("Segoe UI Semibold", FontStyle.NORMAL);
            if (regular == null) regular = Typeface.makeFromName("Arial", FontStyle.NORMAL);
            if (light == null) light = regular;
            if (semibold == null) semibold = regular;
            skijaAvailable = regular != null;
        } catch (Throwable error) {
            skijaAvailable = false;
        }
    }

    public void draw(Graphics2D graphics, String text, float x, float yTop,
                     float size, java.awt.Color color, Weight weight) {
        if (text == null || text.isEmpty()) return;
        BufferedImage image = rasterize(text, size, color, weight);
        if (image != null) {
            Object old = graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(image, Math.round(x), Math.round(yTop), null);
            if (old != null) graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, old);
            return;
        }
        int style = weight == Weight.SEMIBOLD ? java.awt.Font.BOLD : java.awt.Font.PLAIN;
        graphics.setFont(new java.awt.Font("Segoe UI", style, Math.max(1, Math.round(size))));
        graphics.setColor(color);
        graphics.drawString(text, x, yTop + size);
    }

    public float measure(String text, float size, Weight weight) {
        if (text == null || text.isEmpty()) return 0.0f;
        if (skijaAvailable) {
            Typeface typeface = typeface(weight);
            if (typeface != null) {
                try (Font font = new Font(typeface, size)) {
                    configure(font);
                    return font.measureTextWidth(text);
                } catch (Throwable ignored) {
                    skijaAvailable = false;
                }
            }
        }
        return text.length() * size * 0.56f;
    }

    private BufferedImage rasterize(String text, float size, java.awt.Color color, Weight weight) {
        if (!skijaAvailable) return null;
        String key = weight.name() + '|' + Math.round(size * 10.0f) + '|' + color.getRGB() + '|' + text;
        synchronized (cache) {
            BufferedImage cached = cache.get(key);
            if (cached != null) return cached;
        }

        Typeface typeface = typeface(weight);
        if (typeface == null) return null;
        final int scale = 2;
        float rasterSize = Math.max(1.0f, size * scale);
        try (Font font = new Font(typeface, rasterSize); Paint paint = new Paint()) {
            configure(font);
            paint.setAntiAlias(true);
            paint.setColor(io.github.humbleui.skija.Color.makeARGB(255, 255, 255, 255));
            FontMetrics metrics = font.getMetrics();
            int pad = 3 * scale;
            float width = Math.max(1.0f, font.measureTextWidth(text, paint));
            int imageWidth = Math.max(1, (int) Math.ceil(width) + pad * 2);
            int imageHeight = Math.max(1, (int) Math.ceil(metrics.getDescent() - metrics.getAscent()) + pad * 2);
            float baseline = -metrics.getAscent() + pad;

            BufferedImage full = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
            Bitmap bitmap = new Bitmap();
            try {
                ImageInfo info = new ImageInfo(imageWidth, imageHeight, ColorType.RGBA_8888, ColorAlphaType.OPAQUE);
                if (!bitmap.allocPixels(info)) return null;
                Canvas canvas = new Canvas(bitmap);
                try {
                    canvas.clear(io.github.humbleui.skija.Color.makeARGB(255, 0, 0, 0));
                    canvas.drawString(text, pad, baseline, font, paint);
                    byte[] rgba = bitmap.readPixels(info, (long) imageWidth * 4L, 0, 0);
                    if (rgba == null) return null;
                    int source = 0;
                    for (int y = 0; y < imageHeight; y++) {
                        for (int x = 0; x < imageWidth; x++) {
                            int red = rgba[source++] & 0xFF;
                            int green = rgba[source++] & 0xFF;
                            int blue = rgba[source++] & 0xFF;
                            source++;
                            int coverage = Math.max(red, Math.max(green, blue));
                            int alpha = coverage * color.getAlpha() / 255;
                            full.setRGB(x, y, (alpha << 24)
                                    | (color.getRed() << 16)
                                    | (color.getGreen() << 8)
                                    | color.getBlue());
                        }
                    }
                } finally {
                    canvas.close();
                }
            } finally {
                bitmap.close();
            }

            BufferedImage scaled = new BufferedImage(
                    Math.max(1, imageWidth / scale), Math.max(1, imageHeight / scale), BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = scaled.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.drawImage(full, 0, 0, scaled.getWidth(), scaled.getHeight(), null);
            } finally {
                graphics.dispose();
            }
            synchronized (cache) { cache.put(key, scaled); }
            return scaled;
        } catch (Throwable error) {
            skijaAvailable = false;
            return null;
        }
    }

    private Typeface typeface(Weight weight) {
        if (weight == Weight.LIGHT) return light;
        if (weight == Weight.SEMIBOLD) return semibold;
        return regular;
    }

    private static void configure(Font font) {
        font.setSubpixel(true);
        font.setHinting(FontHinting.FULL);
        font.setEdging(FontEdging.SUBPIXEL_ANTI_ALIAS);
    }
}
