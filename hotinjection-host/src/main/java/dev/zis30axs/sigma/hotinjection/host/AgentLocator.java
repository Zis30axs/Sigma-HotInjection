package dev.zis30axs.sigma.hotinjection.host;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public final class AgentLocator {
    private static final String EMBEDDED_AGENT = "/embedded/sigma-hotinjection-agent.jar";

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

        File development = locateDevelopmentAgent();
        if (development != null) return development;

        File embedded = extractEmbeddedAgent();
        if (embedded != null) return embedded;

        return locateSiblingAgent();
    }

    private static File locateDevelopmentAgent() {
        try {
            URI location = HostMain.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            File source = new File(location);
            File directory = source.isDirectory() ? source : source.getParentFile();
            if (directory == null) return null;

            File dev = new File(directory, "../../hotinjection-agent/target/sigma-hotinjection-agent.jar");
            return dev.isFile() ? dev.getCanonicalFile() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static File locateSiblingAgent() {
        try {
            URI location = HostMain.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            File source = new File(location);
            File directory = source.isDirectory() ? source : source.getParentFile();
            if (directory == null) return null;
            File sibling = new File(directory, "sigma-hotinjection-agent.jar");
            return sibling.isFile() ? sibling : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static File extractEmbeddedAgent() {
        InputStream input = AgentLocator.class.getResourceAsStream(EMBEDDED_AGENT);
        if (input == null) return null;

        FileOutputStream output = null;
        try {
            File cacheRoot = new File(System.getProperty("java.io.tmpdir"), "sigma-hotinjection");
            if (!cacheRoot.exists() && !cacheRoot.mkdirs()) {
                return null;
            }

            File target = new File(cacheRoot, "sigma-hotinjection-agent.jar");
            File temp = new File(cacheRoot, "sigma-hotinjection-agent.jar.tmp");
            output = new FileOutputStream(temp);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            output.close();
            output = null;

            if (target.exists() && !target.delete()) {
                return null;
            }
            if (!temp.renameTo(target)) {
                copy(temp, target);
                if (!temp.delete()) temp.deleteOnExit();
            }
            target.deleteOnExit();
            return target;
        } catch (IOException ignored) {
            return null;
        } finally {
            try {
                input.close();
            } catch (IOException ignored) {
            }
            if (output != null) {
                try {
                    output.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void copy(File source, File target) throws IOException {
        java.io.FileInputStream input = new java.io.FileInputStream(source);
        FileOutputStream output = new FileOutputStream(target);
        try {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
        } finally {
            input.close();
            output.close();
        }
    }
}
