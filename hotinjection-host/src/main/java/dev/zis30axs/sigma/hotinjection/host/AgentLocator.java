package dev.zis30axs.sigma.hotinjection.host;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/** Makes the bundled agent available to the Attach API without an external distribution. */
public final class AgentLocator {
    private static final String EMBEDDED_AGENT = "/embedded/sigma-hotinjection-agent.jar";
    private static final String AGENT_CLASS = "dev.zis30axs.sigma.hotinjection.agent.HotInjectionAgent";
    private static Path extractedAgent;

    private AgentLocator() {
    }

    public static synchronized File locate() throws IOException {
        if (extractedAgent != null && Files.isRegularFile(extractedAgent, LinkOption.NOFOLLOW_LINKS)) {
            return extractedAgent.toFile();
        }

        byte[] bytes;
        try (InputStream input = AgentLocator.class.getResourceAsStream(EMBEDDED_AGENT)) {
            if (input == null) {
                throw new IOException("The embedded agent is missing. Build the complete application with "
                        + "mvn clean package and run sigma-hotinjection.jar.");
            }
            bytes = input.readAllBytes();
        }

        String digest = toHex(newDigest().digest(bytes));
        Path cacheRoot = Path.of(System.getProperty("java.io.tmpdir"), "sigma-hotinjection");
        Files.createDirectories(cacheRoot);
        if (!Files.isDirectory(cacheRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("The agent cache is not a directory: " + cacheRoot);
        }
        Path target = cacheRoot.resolve("sigma-hotinjection-agent-" + digest + ".jar");
        if (isMatchingAgent(target, digest)) {
            extractedAgent = target;
            return target.toFile();
        }

        Path temp = Files.createTempFile(cacheRoot, "sigma-hotinjection-agent-" + digest + "-", ".jar");
        try {
            Files.write(temp, bytes);
            validateAgent(temp);
            Path selected = temp;
            try {
                // Publish only the complete payload and never replace a loaded/locked JAR.
                Files.move(temp, target);
                selected = target;
            } catch (IOException publicationFailure) {
                if (isMatchingAgent(target, digest)) {
                    // Another Host already published the same complete payload.
                    deleteUnused(temp);
                    selected = target;
                } else if (!Files.isRegularFile(temp, LinkOption.NOFOLLOW_LINKS)) {
                    throw publicationFailure;
                }
                // A corrupt or locked cache path remains untouched; use our valid unique file.
            }

            // Retain successful files so target JVMs can load classes/resources after Host exit.
            extractedAgent = selected;
            return selected.toFile();
        } catch (IOException | RuntimeException failure) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private static boolean isMatchingAgent(Path path, String expectedDigest) {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return false;
        try {
            MessageDigest digest = newDigest();
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            if (!expectedDigest.equals(toHex(digest.digest()))) return false;
            validateAgent(path);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static MessageDigest newDigest() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
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

    private static void deleteUnused(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best effort: an unused cache file is harmless.
        }
    }

    private static void validateAgent(Path path) throws IOException {
        try (JarFile jar = new JarFile(path.toFile())) {
            Manifest manifest = jar.getManifest();
            Attributes attributes = manifest == null ? null : manifest.getMainAttributes();
            if (attributes == null || !AGENT_CLASS.equals(attributes.getValue("Agent-Class"))
                    || jar.getJarEntry(AGENT_CLASS.replace('.', '/') + ".class") == null
                    || jar.getJarEntry("dev/zis30axs/sigma/hotinjection/HotInjectionRuntime.class") == null) {
                throw new IOException("The embedded agent is incomplete. Rebuild with mvn clean package.");
            }
        }
    }
}
