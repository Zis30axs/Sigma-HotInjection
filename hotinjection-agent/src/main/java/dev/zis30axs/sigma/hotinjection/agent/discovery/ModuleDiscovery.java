package dev.zis30axs.sigma.hotinjection.agent.discovery;

import dev.zis30axs.sigma.hotinjection.HotInjectionRuntime;
import dev.zis30axs.sigma.hotinjection.event.EventBus;
import dev.zis30axs.sigma.hotinjection.module.Module;
import dev.zis30axs.sigma.hotinjection.util.LogUtil;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Discovers concrete modules under the built-in module package. */
public final class ModuleDiscovery {
    private static final String BASE_PACKAGE =
            "dev.zis30axs.sigma.hotinjection.agent.modules";
    private static final String BASE_PATH = BASE_PACKAGE.replace('.', '/');

    private ModuleDiscovery() {
    }

    public static int discoverAndRegister(HotInjectionRuntime runtime) {
        if (runtime == null) throw new NullPointerException("runtime");
        Set<String> classNames = new LinkedHashSet<String>();
        ClassLoader loader = ModuleDiscovery.class.getClassLoader();
        collectClassLoaderResources(loader, classNames);
        collectCodeSource(classNames);

        java.util.List<String> ordered = new java.util.ArrayList<String>(classNames);
        Collections.sort(ordered);
        int registered = 0;
        for (String className : ordered) {
            try {
                Class<?> raw = Class.forName(className, false, loader);
                if (!Module.class.isAssignableFrom(raw)
                        || raw == Module.class
                        || raw.isInterface()
                        || Modifier.isAbstract(raw.getModifiers())
                        || raw.isAnonymousClass()
                        || raw.isLocalClass()) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Class<? extends Module> moduleClass = (Class<? extends Module>) raw;
                Module module = instantiate(moduleClass, runtime);
                if (module != null) {
                    runtime.getModuleManager().register(module);
                    registered++;
                }
            } catch (Throwable failure) {
                LogUtil.warn("Could not load module " + className + ": " + rootMessage(failure));
            }
        }
        LogUtil.info("Module discovery registered " + registered + " module(s).");
        return registered;
    }

    private static Module instantiate(Class<? extends Module> type, HotInjectionRuntime runtime) throws Exception {
        Constructor<? extends Module> constructor = constructor(type, HotInjectionRuntime.class);
        if (constructor != null) return constructor.newInstance(runtime);

        constructor = constructor(type, EventBus.class);
        if (constructor != null) return constructor.newInstance(runtime.getEventBus());

        constructor = constructor(type);
        if (constructor != null) return constructor.newInstance();

        LogUtil.warn("Skipping module without a supported constructor: " + type.getName());
        return null;
    }

    private static Constructor<? extends Module> constructor(
            Class<? extends Module> type, Class<?>... parameters) {
        try {
            Constructor<? extends Module> constructor = type.getDeclaredConstructor(parameters);
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static void collectClassLoaderResources(ClassLoader loader, Set<String> classNames) {
        try {
            Enumeration<URL> resources = loader.getResources(BASE_PATH);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                if ("file".equalsIgnoreCase(resource.getProtocol())) {
                    scanDirectory(toFile(resource), BASE_PACKAGE, classNames);
                } else if ("jar".equalsIgnoreCase(resource.getProtocol())) {
                    JarURLConnection connection = (JarURLConnection) resource.openConnection();
                    connection.setUseCaches(false);
                    JarFile jar = connection.getJarFile();
                    try {
                        scanJar(jar, classNames);
                    } finally {
                        jar.close();
                    }
                }
            }
        } catch (Throwable failure) {
            LogUtil.warn("Classpath module scan failed: " + rootMessage(failure));
        }
    }

    private static void collectCodeSource(Set<String> classNames) {
        try {
            URL location = ModuleDiscovery.class.getProtectionDomain().getCodeSource().getLocation();
            if (location == null) return;
            File source = new File(location.toURI());
            if (source.isDirectory()) {
                scanDirectory(new File(source, BASE_PATH), BASE_PACKAGE, classNames);
            } else if (source.isFile() && source.getName().toLowerCase().endsWith(".jar")) {
                JarFile jar = new JarFile(source);
                try {
                    scanJar(jar, classNames);
                } finally {
                    jar.close();
                }
            }
        } catch (Throwable failure) {
            LogUtil.warn("Code-source module scan failed: " + rootMessage(failure));
        }
    }

    private static void scanDirectory(File directory, String packageName, Set<String> classNames) {
        if (directory == null || !directory.isDirectory()) return;
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, packageName + "." + file.getName(), classNames);
            } else if (isConcreteClassFile(file.getName())) {
                classNames.add(packageName + "." + file.getName().substring(0, file.getName().length() - 6));
            }
        }
    }

    private static void scanJar(JarFile jar, Set<String> classNames) {
        Enumeration<JarEntry> entries = jar.entries();
        String prefix = BASE_PATH + "/";
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (!entry.isDirectory() && name.startsWith(prefix) && isConcreteClassFile(name)) {
                classNames.add(name.substring(0, name.length() - 6).replace('/', '.'));
            }
        }
    }

    private static boolean isConcreteClassFile(String name) {
        return name != null && name.endsWith(".class") && name.indexOf('$') < 0;
    }

    private static File toFile(URL resource) throws UnsupportedEncodingException {
        return new File(URLDecoder.decode(resource.getPath(), "UTF-8"));
    }

    private static String rootMessage(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null) root = root.getCause();
        String message = root.getMessage();
        return root.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }
}
