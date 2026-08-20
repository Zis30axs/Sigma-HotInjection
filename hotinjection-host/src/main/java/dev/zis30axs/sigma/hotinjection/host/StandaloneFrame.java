package dev.zis30axs.sigma.hotinjection.host;

import dev.zis30axs.sigma.hotinjection.host.overlay.HudOverlayWindow;
import dev.zis30axs.sigma.hotinjection.host.ui.JelloClickGuiPanel;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.util.List;

public final class StandaloneFrame extends JFrame {
    private final AttachService attachService = new AttachService();
    private final DefaultListModel<TargetJvm> targets = new DefaultListModel<TargetJvm>();
    private final JList<TargetJvm> targetList = new JList<TargetJvm>(targets);
    private final JComboBox<String> version = new JComboBox<String>(new String[] {
            "auto", "1.7.10", "1.8.9", "1.20.1", "1.21.11", "26.2"
    });
    private final JTextField agentPath = new JTextField();
    private final JCheckBox notice = new JCheckBox("Show local injection notice", true);
    private final JLabel status = new JLabel("Ready");
    private final JButton refresh = new JButton("Refresh");
    private final JButton attach = new JButton("Inject");
    private AgentSession session;
    private HudOverlayWindow hudOverlay;

    public StandaloneFrame() {
        super("Sigma HotInjection");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(620, 430);
        setLocationRelativeTo(null);
        showInjector();
        refreshTargets();
    }

    private void showInjector() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setContentPane(root);
        targetList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        root.add(new JScrollPane(targetList), BorderLayout.CENTER);

        JPanel top = new JPanel(new BorderLayout(6, 6));
        top.add(new JLabel("Java processes"), BorderLayout.WEST);
        for (java.awt.event.ActionListener listener : refresh.getActionListeners()) refresh.removeActionListener(listener);
        refresh.addActionListener(event -> refreshTargets());
        top.add(refresh, BorderLayout.EAST);
        root.add(top, BorderLayout.NORTH);

        File detectedAgent = AgentLocator.locate();
        if (detectedAgent != null) agentPath.setText(detectedAgent.getAbsolutePath());

        JPanel settings = new JPanel(new GridLayout(3, 1, 4, 4));
        JPanel versionRow = new JPanel(new BorderLayout(6, 0));
        versionRow.add(new JLabel("Minecraft version:"), BorderLayout.WEST);
        versionRow.add(version, BorderLayout.CENTER);
        settings.add(versionRow);

        JPanel agentRow = new JPanel(new BorderLayout(6, 0));
        agentRow.add(new JLabel("Agent JAR:"), BorderLayout.WEST);
        agentRow.add(agentPath, BorderLayout.CENTER);
        JButton browse = new JButton("Browse...");
        browse.addActionListener(event -> chooseAgent());
        agentRow.add(browse, BorderLayout.EAST);
        settings.add(agentRow);
        settings.add(notice);

        JPanel bottom = new JPanel(new BorderLayout(6, 6));
        bottom.add(settings, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(status);
        for (java.awt.event.ActionListener listener : attach.getActionListeners()) attach.removeActionListener(listener);
        attach.addActionListener(event -> attachSelected());
        actions.add(attach);
        bottom.add(actions, BorderLayout.SOUTH);
        root.add(bottom, BorderLayout.SOUTH);
        revalidate();
        repaint();
    }

    private void refreshTargets() {
        refresh.setEnabled(false);
        status.setText("Scanning JVMs...");
        new SwingWorker<List<TargetJvm>, Void>() {
            @Override protected List<TargetJvm> doInBackground() { return attachService.listTargets(); }
            @Override protected void done() {
                refresh.setEnabled(true);
                try {
                    List<TargetJvm> discovered = get();
                    targets.clear();
                    for (TargetJvm target : discovered) targets.addElement(target);
                    if (!targets.isEmpty()) targetList.setSelectedIndex(0);
                    status.setText(targets.isEmpty() ? "No JVMs found" : targets.size() + " JVM(s)");
                } catch (Exception error) {
                    status.setText("Scan failed");
                    showError("JVM scan failed", error);
                }
            }
        }.execute();
    }

    private void chooseAgent() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose Sigma HotInjection agent JAR");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            agentPath.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void attachSelected() {
        final TargetJvm target = targetList.getSelectedValue();
        if (target == null) {
            JOptionPane.showMessageDialog(this, "Select a target Java process first.");
            return;
        }
        final File agent = new File(agentPath.getText().trim());
        final String selectedVersion = String.valueOf(version.getSelectedItem());
        final boolean showNotice = notice.isSelected();
        attach.setEnabled(false);
        status.setText("Injecting into " + target.getPid() + "...");

        new SwingWorker<AgentSession, Void>() {
            @Override protected AgentSession doInBackground() throws Exception {
                return attachService.attachSession(target.getPid(), agent, selectedVersion, showNotice);
            }
            @Override protected void done() {
                attach.setEnabled(true);
                try {
                    session = get();
                    showController(session, target.getPid());
                } catch (Exception error) {
                    status.setText("Injection failed");
                    showError("Injection failed for PID " + target.getPid(), error);
                }
            }
        }.execute();
    }

    private void showController(AgentSession activeSession, String targetPid) {
        setTitle("Sigma HotInjection · Minecraft " + activeSession.getVersion());
        setContentPane(new JelloClickGuiPanel(activeSession));
        setSize(1180, 720);
        setMinimumSize(new Dimension(820, 560));
        setAlwaysOnTop(true);
        setLocationRelativeTo(null);
        revalidate();
        repaint();

        if (hudOverlay != null) hudOverlay.close();
        try {
            hudOverlay = new HudOverlayWindow(Long.parseLong(targetPid), activeSession);
            hudOverlay.start();
        } catch (RuntimeException failure) {
            hudOverlay = null;
            System.err.println("[Sigma HotInjection] HUD overlay could not start: " + failure);
        }
    }

    private void showError(String title, Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        String message = cause.getClass().getSimpleName();
        if (cause.getMessage() != null && !cause.getMessage().trim().isEmpty()) {
            message += ": " + cause.getMessage().trim();
        }
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void dispose() {
        if (hudOverlay != null) {
            hudOverlay.close();
            hudOverlay = null;
        }
        if (session != null) {
            try { session.close(); } catch (Exception ignored) { }
        }
        super.dispose();
    }
}
