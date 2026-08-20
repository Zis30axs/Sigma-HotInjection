package dev.zis30axs.sigma.hotinjection.host;

import javax.swing.SwingUtilities;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.List;

public final class HostMain {
    private HostMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            SwingUtilities.invokeLater(() -> new StandaloneFrame().setVisible(true));
            return;
        }

        AttachService service = new AttachService();
        if ("--list".equals(args[0])) {
            printTargets(service.listTargets());
            return;
        }
        if ("--attach".equals(args[0])) {
            runAttach(service, args);
            return;
        }
        if ("--stdio".equals(args[0])) {
            runStdio(service, args);
            return;
        }
        printUsage();
    }

    private static void runAttach(AttachService service, String[] args) throws Exception {
        if (args.length < 2) {
            printUsage();
            return;
        }
        String pid = args[1];
        String version = "auto";
        File agent = AgentLocator.locate();
        boolean notice = true;

        for (int i = 2; i < args.length; i++) {
            if ("--version".equals(args[i]) && i + 1 < args.length) {
                version = args[++i];
            } else if ("--agent".equals(args[i]) && i + 1 < args.length) {
                agent = new File(args[++i]);
            } else if ("--quiet".equals(args[i])) {
                notice = false;
            }
        }

        if (agent == null) {
            throw new IllegalStateException("Agent JAR was not found. Use --agent <path>.");
        }
        service.attach(pid, agent, version, notice);
        System.out.println("OK attached pid=" + pid + " version=" + version);
    }

    private static void runStdio(AttachService service, String[] args) throws Exception {
        File agent = args.length >= 2 ? new File(args[1]) : AgentLocator.locate();
        if (agent == null) {
            throw new IllegalStateException("Agent JAR was not found. Pass it after --stdio.");
        }

        System.out.println("READY protocol=1");
        System.out.flush();
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if ("QUIT".equalsIgnoreCase(line)) {
                System.out.println("BYE");
                System.out.flush();
                return;
            }
            if ("LIST".equalsIgnoreCase(line)) {
                for (TargetJvm target : service.listTargets()) {
                    System.out.println("TARGET\t" + target.getPid() + "\t" + sanitize(target.getDisplayName()));
                }
                System.out.println("END");
                System.out.flush();
                continue;
            }
            if (line.toUpperCase().startsWith("ATTACH ")) {
                String[] parts = line.split("\\s+", 3);
                String pid = parts.length >= 2 ? parts[1] : "";
                String version = parts.length >= 3 ? parts[2] : "auto";
                try {
                    service.attach(pid, agent, version, true);
                    System.out.println("ATTACHED\t" + pid + "\t" + version);
                } catch (Exception error) {
                    System.out.println("ERROR\t" + sanitize(error.getMessage()));
                }
                System.out.flush();
                continue;
            }
            System.out.println("ERROR\tUnknown command");
            System.out.flush();
        }
    }

    private static void printTargets(List<TargetJvm> targets) {
        for (TargetJvm target : targets) {
            System.out.println(target.getPid() + "\t" + target.getDisplayName());
        }
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
    }

    private static void printUsage() {
        System.out.println("Sigma HotInjection");
        System.out.println("  no args: open standalone UI");
        System.out.println("  --list");
        System.out.println("  --attach <pid> [--version <auto|1.7.10|1.8.9|1.20.1|1.21.11|26.2>] [--agent <jar>] [--quiet]");
        System.out.println("  --stdio [agent.jar]");
    }
}
