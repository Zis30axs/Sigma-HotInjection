package dev.zis30axs.sigma.hotinjection.host.overlay;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/** Loads the user-provided Sigma Jello watermark and a pre-blurred bloom mask. */
final class JelloWatermark {
    private static final String RESOURCE = "/assets/sigma/jello-watermark.png";
    private static volatile BufferedImage image;
    private static volatile BufferedImage bloom;

    private JelloWatermark() {
    }

    static BufferedImage image() {
        ensureLoaded();
        return image;
    }

    static BufferedImage bloom() {
        ensureLoaded();
        return bloom;
    }

    private static synchronized void ensureLoaded() {
        if (image != null && bloom != null) return;
        InputStream stream = null;
        try {
            stream = JelloWatermark.class.getResourceAsStream(RESOURCE);
            if (stream == null) throw new IllegalStateException("Missing " + RESOURCE);
            BufferedImage decoded = ImageIO.read(stream);
            if (decoded == null) throw new IllegalStateException("Watermark PNG could not be decoded");
            image = decoded;
            bloom = createBloom(decoded);
        } catch (Throwable failure) {
            image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            bloom = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            System.err.println("[Sigma HotInjection] Watermark load failed: " + failure);
        } finally {
            if (stream != null) {
                try { stream.close(); } catch (IOException ignored) { }
            }
        }
    }

    private static BufferedImage createBloom(BufferedImage source) {
        int padding = 18;
        BufferedImage mask = new BufferedImage(
                source.getWidth() + padding * 2,
                source.getHeight() + padding * 2,
                BufferedImage.TYPE_INT_ARGB);
        Color tint = new Color(183, 205, 255);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int alpha = source.getRGB(x, y) >>> 24;
                if (alpha == 0) continue;
                int bloomAlpha = Math.min(150, alpha * 3 / 5);
                mask.setRGB(x + padding, y + padding,
                        (bloomAlpha << 24) | (tint.getRed() << 16)
                                | (tint.getGreen() << 8) | tint.getBlue());
            }
        }

        int size = 13;
        float[] weights = new float[size * size];
        Arrays.fill(weights, 1.0f / weights.length);
        ConvolveOp blur = new ConvolveOp(new Kernel(size, size, weights), ConvolveOp.EDGE_NO_OP, null);
        BufferedImage first = new BufferedImage(mask.getWidth(), mask.getHeight(), BufferedImage.TYPE_INT_ARGB);
        BufferedImage second = new BufferedImage(mask.getWidth(), mask.getHeight(), BufferedImage.TYPE_INT_ARGB);
        blur.filter(mask, first);
        blur.filter(first, second);
        return second;
    }
}
