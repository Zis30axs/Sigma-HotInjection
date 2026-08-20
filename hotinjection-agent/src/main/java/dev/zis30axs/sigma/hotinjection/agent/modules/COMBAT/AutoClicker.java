package dev.zis30axs.sigma.hotinjection.agent.modules.COMBAT;

import dev.zis30axs.sigma.hotinjection.HotInjectionRuntime;
import dev.zis30axs.sigma.hotinjection.agent.client.ClickBridge;
import dev.zis30axs.sigma.hotinjection.agent.client.PlayerView;
import dev.zis30axs.sigma.hotinjection.input.MouseButton;
import dev.zis30axs.sigma.hotinjection.input.MouseProbe;
import dev.zis30axs.sigma.hotinjection.module.Module;
import dev.zis30axs.sigma.hotinjection.module.ModuleCategory;
import dev.zis30axs.sigma.hotinjection.module.setting.BooleanSetting;
import dev.zis30axs.sigma.hotinjection.module.setting.ModeSetting;
import dev.zis30axs.sigma.hotinjection.module.setting.NumberSetting;
import dev.zis30axs.sigma.hotinjection.module.setting.RangeSetting;
import dev.zis30axs.sigma.hotinjection.util.LogUtil;
import dev.zis30axs.sigma.hotinjection.version.VersionAdapter;

import java.util.Random;

/**
 * Auto clicker for the held mouse buttons.
 *
 * <p>Every click draws a fresh speed from the configured CPS window, so the
 * interval keeps changing inside the range the user dragged. Left and right have
 * independent windows and independent schedules.</p>
 */
public final class AutoClicker extends Module {
    private static final long LOOP_INTERVAL_MILLIS = 3L;
    private static final long DISPATCH_BACKOFF_MILLIS = 250L;

    private final RangeSetting cps = setting(new RangeSetting(
            "cps", "CPS",
            "Left click speed window. Each click picks a new speed inside the dragged range.",
            8.0D, 12.0D, 0.0D, 20.0D, 0.5D));
    private final BooleanSetting rightClick = setting(new BooleanSetting(
            "right-click", "Right Click",
            "Auto click the right button as well, using its own CPS window.", false));
    private final RangeSetting rightCps = setting(new RangeSetting(
            "right-cps", "Right CPS",
            "Right click speed window. Only used while Right Click is on.",
            8.0D, 12.0D, 0.0D, 20.0D, 0.5D));
    private final BooleanSetting trigger = setting(new BooleanSetting(
            "trigger", "Trigger",
            "Only left click while the crosshair is on a target entity.", false));
    private final BooleanSetting breakBlock = setting(new BooleanSetting(
            "break-block", "Break Block",
            "Suspend left clicking while a block is being mined.", true));
    private final NumberSetting breakDelay = setting(new NumberSetting(
            "break-delay", "Break Delay",
            "Milliseconds left clicking stays suspended after mining stopped.",
            200.0D, 0.0D, 1000.0D, 25.0D));
    private final BooleanSetting requireHold = setting(new BooleanSetting(
            "require-hold", "Require Hold",
            "Click only while the physical mouse button is held down.", true));
    private final ModeSetting dispatch = setting(new ModeSetting(
            "dispatch", "Dispatch",
            "Auto prefers the in-game click handler and falls back to a synthetic OS click.",
            "Auto", "Auto", "Game", "Native"));

    private final HotInjectionRuntime runtime;
    private final Random random = new Random();
    private final long[] nextClick = new long[2];
    private volatile boolean running;
    private volatile MouseProbe mouse;
    private Thread clickThread;
    private long miningUntil;
    private boolean crosshairWarned;

    public AutoClicker(HotInjectionRuntime runtime) {
        super("auto-clicker", "AutoClicker", ModuleCategory.COMBAT,
                "Clicks the held mouse buttons inside a CPS range, with trigger and mining guards.",
                runtime.getEventBus());
        this.runtime = runtime;
    }

    @Override
    protected void onEnable() {
        VersionAdapter adapter = runtime.getActiveAdapter();
        MouseProbe probe = adapter == null ? null : adapter.createMouseProbe();
        mouse = probe;
        nextClick[0] = 0L;
        nextClick[1] = 0L;
        miningUntil = 0L;
        crosshairWarned = false;

        if (probe == null || !probe.isAvailable()) {
            LogUtil.warn("AutoClicker has no mouse backend"
                    + (probe == null ? "" : " (" + probe.describe() + ")")
                    + "; enable Require Hold=false to click without button state.");
        } else {
            LogUtil.info("AutoClicker mouse backend: " + probe.describe());
        }
        LogUtil.info("AutoClicker left dispatch: " + ClickBridge.describe(MouseButton.LEFT)
                + ", right dispatch: " + ClickBridge.describe(MouseButton.RIGHT));

        running = true;
        clickThread = new Thread(new Runnable() {
            @Override
            public void run() {
                loop();
            }
        }, "Sigma-HotInjection-AutoClicker");
        clickThread.setDaemon(true);
        clickThread.start();
    }

    @Override
    protected void onDisable() {
        running = false;
        MouseProbe probe = mouse;
        if (probe != null) probe.close();
        mouse = null;
        clickThread = null;
    }

    private void loop() {
        while (running) {
            try {
                tick(System.currentTimeMillis());
            } catch (Throwable failure) {
                LogUtil.warn("AutoClicker tick failed: " + failure);
            }
            try {
                Thread.sleep(LOOP_INTERVAL_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void tick(long now) {
        if (!ready()) {
            nextClick[0] = 0L;
            nextClick[1] = 0L;
            return;
        }
        tickButton(MouseButton.LEFT, cps, now, leftAllowed(now));
        tickButton(MouseButton.RIGHT, rightCps, now, rightClick.getValue().booleanValue());
    }

    /** World and window state that gates both buttons. */
    private boolean ready() {
        VersionAdapter adapter = runtime.getActiveAdapter();
        if (adapter != null && !adapter.isInWorld()) return false;
        MouseProbe probe = mouse;
        return probe == null || !probe.isAvailable() || probe.isWindowFocused();
    }

    /** Trigger and mining only ever gate the attack button. */
    private boolean leftAllowed(long now) {
        if (trigger.getValue().booleanValue() && !crosshairOnEntity()) return false;
        return !breakBlock.getValue().booleanValue() || !suspendedForMining(now);
    }

    private boolean crosshairOnEntity() {
        PlayerView.Crosshair crosshair = PlayerView.crosshair();
        if (crosshair == PlayerView.Crosshair.UNKNOWN) {
            if (!crosshairWarned) {
                crosshairWarned = true;
                LogUtil.warn("AutoClicker cannot read the crosshair target in this runtime;"
                        + " Trigger keeps clicks suspended.");
            }
            return false;
        }
        return crosshair == PlayerView.Crosshair.ENTITY;
    }

    /**
     * Mining is read from the player controller when the runtime exposes it, and
     * otherwise inferred from "attack held while pointing at a block". Either way
     * the suspension outlives the mining itself by the Break Delay, so a click
     * cannot land right after a block broke.
     */
    private boolean suspendedForMining(long now) {
        Boolean breaking = PlayerView.breakingBlock();
        boolean mining = breaking != null
                ? breaking.booleanValue()
                : holding(MouseButton.LEFT) && PlayerView.crosshair() == PlayerView.Crosshair.BLOCK;
        if (mining) {
            miningUntil = now + Math.round(breakDelay.getValue().doubleValue());
            return true;
        }
        return now < miningUntil;
    }

    private void tickButton(MouseButton button, RangeSetting speed, long now, boolean allowed) {
        int index = button.getIndex();
        if (!allowed || !holding(button)) {
            nextClick[index] = 0L;
            return;
        }
        long due = nextClick[index];
        if (due == 0L || due < now - 500L) due = now;
        if (now < due) {
            nextClick[index] = due;
            return;
        }
        if (!ClickBridge.click(button, ClickBridge.Dispatch.parse(dispatch.getValue()))) {
            nextClick[index] = now + DISPATCH_BACKOFF_MILLIS;
            return;
        }
        double clicksPerSecond = speed.sample(random);
        if (clicksPerSecond < 0.05D) {
            nextClick[index] = now + 1000L;
            return;
        }
        nextClick[index] = Math.max(now + 1L, due + Math.round(1000.0D / clicksPerSecond));
    }

    private boolean holding(MouseButton button) {
        if (!requireHold.getValue().booleanValue()) return true;
        MouseProbe probe = mouse;
        return probe != null && probe.isAvailable() && probe.isDown(button);
    }
}
