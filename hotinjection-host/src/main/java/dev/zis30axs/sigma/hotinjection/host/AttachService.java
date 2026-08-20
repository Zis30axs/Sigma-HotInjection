package dev.zis30axs.sigma.hotinjection.host;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class AttachService {
    public List<TargetJvm> listTargets() {
        List<TargetJvm> targets = new ArrayList<TargetJvm>();
        for (VirtualMachineDescriptor descriptor : VirtualMachine.list()) {
            targets.add(new TargetJvm(descriptor.id(), descriptor.displayName()));
        }
        Collections.sort(targets, new Comparator<TargetJvm>() {
            @Override
            public int compare(TargetJvm a, TargetJvm b) {
                return Long.compare(pidAsLong(a), pidAsLong(b));
            }
        });
        return Collections.unmodifiableList(targets);
    }

    public void attach(String pid, File agentJar, String version, boolean showNotice) throws Exception {
        if (pid == null || pid.trim().isEmpty()) {
            throw new IllegalArgumentException("PID is required");
        }
        if (agentJar == null || !agentJar.isFile()) {
            throw new IOException("Agent JAR not found: " + agentJar);
        }

        StringBuilder options = new StringBuilder("source=host;notice=").append(showNotice);
        if (version != null && !version.trim().isEmpty() && !"auto".equalsIgnoreCase(version)) {
            options.append(";version=").append(version.trim());
        }

        VirtualMachine vm = VirtualMachine.attach(pid.trim());
        try {
            vm.loadAgent(agentJar.getCanonicalPath(), options.toString());
        } finally {
            vm.detach();
        }
    }

    private static long pidAsLong(TargetJvm target) {
        try {
            return Long.parseLong(target.getPid());
        } catch (NumberFormatException ignored) {
            return Long.MAX_VALUE;
        }
    }
}
