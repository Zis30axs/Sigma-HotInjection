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
- Dynamic module settings: boolean, number, mode and range values.
- Event bus with priorities and cancellable events.
- Method registry for CLI/Bootstrap integration.
- Version adapter slots for Minecraft 1.7.10, 1.8.9, 1.20.1, 1.21.11 and 26.2.
- A local-only injection proof. `InjectionNoticeEvent` may cancel the notice before it is displayed.
- An authenticated loopback control channel (`127.0.0.1` + random per-agent token) used by the external Host GUI.
- A Jello-inspired external ClickGUI. After a successful injection the original Host window becomes the controller instead of opening a second application.
- A compatibility in-process ClickGUI module for older versions. The external controller is the preferred UI, especially for modern versions.
- `AutoClicker` (Combat) and `ESP` (Render), both mapping independent and both degrading instead of throwing when a runtime cannot be read.

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

private final RangeSetting window = setting(
        new RangeSetting("window", "Window", "Example range setting",
                8.0D, 12.0D, 0.0D, 20.0D, 0.5D));
```

A `RangeSetting` is a two-handle interval inside a fixed bound. It serializes as
`low:high`, the Host draws one bar with two draggable handles, and a handle can
never be dragged past the other one or outside the bound. `sample(random)` draws
a uniform value from the current interval, which is how AutoClicker keeps its
click interval moving.

The Host does not need module-specific UI code. It receives the setting schema
from the Agent and draws the matching toggle, slider, range bar or mode selector.
Sliders and range bars follow the mouse while dragging and only push the value to
the Agent every 60 ms.

## Local control channel

After initialization the Agent binds an ephemeral port on `127.0.0.1` and
generates a random token. The port and token are returned through the existing
attach acknowledgement file. The Host then authenticates and requests module
state/settings.

The channel currently supports module listing, enable/disable, setting listing,
setting updates and overlay frames. It is deliberately local-only and is not a
remote control service.

## Overlay frames

`OVERLAY <aspect>` asks the Agent for one frame. Every enabled module that
implements `OverlaySource` returns `OverlayBox` rectangles in normalized client
coordinates, where `0,0` is the top-left corner of the Minecraft client area and
`1,1` the bottom-right one:

```text
BOX <x0> <y0> <x1> <y1> <argb-hex> <base64-label>
END
```

The Host measures the client area, sends its aspect ratio and scales the answer
to the overlay window, so the Agent never learns the window size and the Host
never needs game knowledge. The HUD overlay polls this every 33 ms while
Minecraft is the foreground window.

## AutoClicker

`AutoClicker` (Combat) clicks the held mouse buttons:

- `CPS` - left click speed window, 0 to 20. Every click draws a new speed inside
  the dragged range, so the interval keeps changing.
- `Right Click` plus `Right CPS` - the same window for the right button.
- `Trigger` - left click only while the crosshair is on an entity.
- `Break Block` plus `Break Delay` - suspend left clicking while a block is being
  mined, and keep it suspended for the delay afterwards.
- `Require Hold` - click only while the physical button is down.
- `Dispatch` - `Auto` calls the client's own click handler on the game thread and
  falls back to a synthetic OS click, `Game` and `Native` pin one path.

Mining state is read from the player controller when the runtime exposes it and
is otherwise inferred from "attack held while pointing at a block".

## ESP

`ESP` (Render) reads the entity boxes, projects them and hands the Host plain
rectangles; nothing is hooked into the render pipeline. Settings are `Targets`
(Players or All), `Color`, `FOV`, `Range`, `Max Targets` and `Names`.

`FOV` must match the in-game field of view slider, because the Agent projects the
scene itself instead of reading the game's projection matrix.

Positions come from entity bounding boxes: an axis aligned box is the only game
class that carries exactly six instance doubles in `minX, minY, minZ, maxX, maxY,
maxZ` order, which makes it recognisable in a fully obfuscated runtime. Players
are recognised by their authlib `GameProfile`, which is a library class and
survives obfuscation as well. View angles are resolved by mapping name first and,
failing that, by the four consecutive floats an entity uses for
`yaw, pitch, previousYaw, previousPitch`. When even that fails the module logs one
warning and draws nothing.

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
