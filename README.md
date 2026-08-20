# Sigma HotInjection

Experimental, explicit Java-agent hot-loading architecture for Sigma.

This repository is intentionally independent from Sigma Legacy, Sigma Modern,
and SigmaJelloBootstrap-Reborn. It shares a small control protocol, not a client
architecture.

## Current milestone

The current skeleton provides:

- Java 8 compatible injected API/agent bytecode.
- Java 17 standalone Host using the standard JDK Attach API.
- `ModuleManager` with ID/name lookup, categories, bulk registration, enable/disable and toggle lifecycle.
- Dynamic module settings: boolean, number and mode values.
- Event bus with priorities and cancellable events.
- Method registry for CLI/Bootstrap integration.
- Version adapter slots for Minecraft 1.7.10, 1.8.9, 1.20.1, 1.21.11 and 26.2.
- A local-only injection proof. `InjectionNoticeEvent` may cancel the notice before it is displayed.
- An authenticated loopback control channel (`127.0.0.1` + random per-agent token) used by the external Host GUI.
- A Jello-inspired external ClickGUI. After a successful injection the original Host window becomes the controller instead of opening a second application.
- A compatibility in-process ClickGUI module for older versions. The external controller is the preferred UI, especially for modern versions.

## Repository layout

```text
hotinjection-api/    Java 8 shared runtime contracts, ModuleManager and settings
hotinjection-agent/  Java 8 self-contained agent loaded into the target JVM
hotinjection-host/   Java 17 Attach API + standalone/external ClickGUI

docs/
  clickgui-prototype.html   visual reference for the external controller
```

## Build

Use a JDK 17+ installation containing `jdk.attach`:

```bash
mvn clean package
```

Important artifacts:

```text
hotinjection-agent/target/sigma-hotinjection-agent.jar
hotinjection-host/target/sigma-hotinjection.jar
```

The Host package embeds the Agent JAR and shades the Windows x64 Skija runtime,
so the normal standalone path only needs the Host artifact.

## Standalone

```bash
java --add-modules jdk.attach -jar hotinjection-host/target/sigma-hotinjection.jar
```

The initial window scans visible JVMs and lets the user explicitly choose a
target. After injection succeeds, the same window expands into the external
ClickGUI.

The ClickGUI has three columns:

```text
SIGMA PROD / categories | modules | selected module settings
```

The category list is intentionally shifted down to leave room for the Sigma Prod
wordmark. The visual layer uses translucent glass panels, blur/bloom accents and
a CPU-rasterized Skija font renderer inspired by Sigma Jello's font pipeline.
If Skija cannot initialize, the Host falls back to Segoe UI rather than losing
the controller.

## ModuleManager

Modules should be registered centrally:

```java
QuietNoticeModule quietNotice = new QuietNoticeModule(runtime.getEventBus());
LocalChatModule localChat = new LocalChatModule(runtime.getEventBus());
ClickGuiModule clickGui = new ClickGuiModule(runtime);

runtime.getModuleManager().registerAll(quietNotice, localChat, clickGui);
```

`ModuleRegistry` remains as a compatibility facade for older code, but new code
should prefer `runtime.getModuleManager()`.

A module may expose values to the external GUI with:

```java
private final BooleanSetting enabledOption = setting(
        new BooleanSetting("option", "Option", "Example boolean setting", true));

private final NumberSetting amount = setting(
        new NumberSetting("amount", "Amount", "Example number setting",
                1.0D, 0.0D, 5.0D, 0.1D));
```

The Host does not need module-specific UI code. It receives the setting schema
from the Agent and draws the matching toggle, slider or mode selector.

## Local control channel

After initialization the Agent binds an ephemeral port on `127.0.0.1` and
generates a random token. The port and token are returned through the existing
attach acknowledgement file. The Host then authenticates and requests module
state/settings.

The channel currently supports module listing, enable/disable, setting listing
and setting updates. It is deliberately local-only and is not a remote control
service.

## Client messages

Client status messages pass through `ClientMessageEvent` and can be cancelled.
The mapping-independent fallback remains a local notification; version-specific
native Minecraft chat/HUD bridges can be implemented separately without
changing ModuleManager or the external controller.

No generic Netty packet interception or anti-cheat bypass is claimed by the
current implementation.

## Compatibility ClickGUI

The injected `ClickGUI` module currently exposes two settings that are useful for
end-to-end controller testing:

- `Right Shift Hotkey`
- `Poll Interval`

1.7.10/1.8.9 can use the LWJGL 2 keyboard probe. Modern GLFW input remains
version-adapter work; this is why the external Host GUI is the primary modern
interface.

## Bootstrap Lite

The standalone Host control channel is also the foundation for Bootstrap Lite:
Bootstrap can reuse the same module/settings schema instead of linking against
HotInjection internals. The repositories share a protocol, not source-level
module architecture.

## Safety / scope

HotInjection only attaches to a PID explicitly selected by the user. The project
does not hide the attach operation, bypass security software, or implement
anti-cheat evasion. Version-specific integration should preserve that explicit
model.
