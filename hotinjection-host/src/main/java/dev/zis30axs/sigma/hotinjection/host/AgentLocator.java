package dev.zis30axs.sigma.hotinjection.host;

import java.io.File;
import java.net.URI;

public final class AgentLocator {
    private AgentLocator() {
    }

    public static File locate() {
        String configured = System.getProperty("sigma.hotinjection.agent");
        if (configured == null || configured.trim().isEmpty()) {
            configured = System.getenv("SIGMA_HOTINJECTION_AGENT");
        }
        if (configured != null && !configured.trim().isEmpty()) {
            File explicit = new File(configured.trim());
            if (explicit.isFile()) return explicit;
        }

        try {
            URI location = HostMain.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            File source = new File(location);
            File directory = source.isDirectory() ? source : source.getParentFile();
            if (directory != null) {
                File sibling = new File(directory, "sigma-hotinjection-agent.jar");
                if (sibling.isFile()) return sibling;
                File dev = new File(directory, "../../hotinjection-agent/target/sigma-hotinjection-agent.jar");
                if (dev.isFile()) return dev.getCanonicalFile();
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
