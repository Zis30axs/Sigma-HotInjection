package dev.zis30axs.sigma.hotinjection.host;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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

        try {
            byte[] bytes = readAll(input);
            String digest = sha256(bytes);

            File cacheRoot = new File(System.getProperty("java.io.tmpdir"), "sigma-hotinjection");
            if (!cacheRoot.exists() && !cacheRoot.mkdirs()) {
                return null;
            }

            File target = new File(cacheRoot, "sigma-hotinjection-agent-" + digest + ".jar");
            if (isMatchingFile(target, digest)) {
                return target;
            }

            File temp = new File(cacheRoot,
                    "sigma-hotinjection-agent-" + digest + "." + uniqueSuffix() + ".tmp");
            write(temp, bytes);

            if (target.exists()) {
                if (isMatchingFile(target, digest)) {
                    deleteQuietly(temp);
                    return target;
                }
                // Never overwrite a possibly loaded/locked Agent JAR. A unique cache file
                // is safe for this Host process and remains available to the target JVM.
                return temp;
            }

            if (temp.renameTo(target)) {
                return target;
            }

            // Another Host may have won the race to publish the same content-addressed JAR.
            if (isMatchingFile(target, digest)) {
                deleteQuietly(temp);
                return target;
            }

            // Keep and use the unique file instead of failing because Windows has a cache
            // path locked by an already-attached JVM.
            return temp.isFile() ? temp : null;
        } catch (IOException ignored) {
            return null;
        } finally {
            try {
                input.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void write(File target, byte[] bytes) throws IOException {
        FileOutputStream output = new FileOutputStream(target);
        try {
            output.write(bytes);
            output.getFD().sync();
        } finally {
            output.close();
        }
    }

    private static boolean isMatchingFile(File file, String expectedDigest) {
        if (!file.isFile()) return false;
        try {
            return expectedDigest.equals(sha256(file));
        } catch (IOException ignored) {
            return false;
        }
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 is unavailable", impossible);
        }
    }

    private static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            InputStream input = new FileInputStream(file);
            try {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            } finally {
                input.close();
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 is unavailable", impossible);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            hex.append(Character.forDigit((value >>> 4) & 0x0f, 16));
            hex.append(Character.forDigit(value & 0x0f, 16));
        }
        return hex.toString();
    }

    private static String uniqueSuffix() {
        String runtime = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
        int at = runtime.indexOf('@');
        String pid = at > 0 ? runtime.substring(0, at) : runtime;
        return pid + "-" + Long.toHexString(System.nanoTime());
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists() && !file.delete()) {
            // Best effort only. Old cache files are harmless and may be locked by a JVM.
        }
    }
}
