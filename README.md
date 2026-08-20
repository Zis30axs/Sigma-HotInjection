# Sigma HotInjection

Experimental, explicit Java-agent hot-loading architecture for Sigma.

This repository is intentionally independent from Sigma Legacy, Sigma Modern,
and SigmaJelloBootstrap-Reborn. It shares a control protocol, not a client
architecture.

## Current milestone

The first skeleton provides:

- Java 8 compatible injected API/agent bytecode.
- Java 17 standalone/host process using the standard JDK Attach API.
- Module registry with enable/disable lifecycle.
- Event bus with priorities and cancellable events.
- Method registry for future Bootstrap Lite/IPC commands.
- Utility package for logging/reflection helpers.
- Version adapter slots for Minecraft 1.7.10, 1.8.9, 1.20.1, 1.21.11 and 26.2.
- A real injection-success proof: after attach, `InjectionNoticeEvent` is posted. Unless cancelled, `[SIGMA] Injected!` is written into your own chat. No chat/network packet is sent to a server.
- A ClickGUI toggled with RIGHT SHIFT while you are in a world, with a `TEST` button that writes `Already Injected!` into your own chat only.
- An outbound packet hook: every packet the client writes passes through the cancellable `PacketSendEvent` before it is encoded.
- A `quiet-notice` module demonstrating that the notification can be cancelled through the event bus.
- Host modes for standalone UI, CLI attach, target listing, and a minimal stdin/stdout protocol intended for Bootstrap Lite.

Client messages, ClickGUI state and outgoing packets all pass through
cancellable events (`ClientMessageEvent`, `ChatSendEvent`, `ClickGuiToggleEvent`,
`PacketSendEvent`), so a module can drop or rewrite any of them before anything
becomes visible or reaches the network.

## Repository layout

```text
hotinjection-api/    Java 8 shared runtime contracts
hotinjection-agent/  Java 8 self-contained agent loaded into the target JVM
hotinjection-host/   Java 17 standalone UI / Attach API / Bootstrap host mode
```

## Build

A JDK 17+ with Maven is recommended for the whole reactor:

```bash
mvn -DskipTests package
```

Important artifacts:

```text
hotinjection-agent/target/sigma-hotinjection-agent.jar
hotinjection-host/target/sigma-hotinjection-host.jar
```

The host requires a JDK/runtime containing `jdk.attach`.

## Standalone

Open the minimal standalone target picker:

```bash
java --add-modules jdk.attach -jar hotinjection-host/target/sigma-hotinjection-host.jar
```

List visible Java processes:

```bash
java --add-modules jdk.attach -jar hotinjection-host/target/sigma-hotinjection-host.jar --list
```

Attach explicitly to a selected PID:

```bash
java --add-modules jdk.attach -jar hotinjection-host/target/sigma-hotinjection-host.jar \
  --attach <pid> \
  --version 1.8.9 \
  --agent hotinjection-agent/target/sigma-hotinjection-agent.jar
```

Use `--quiet` to enable the sample `quiet-notice` module before the `InjectionNoticeEvent` is posted. The attach still succeeds, but the local success notification is cancelled. Use `--no-clickgui` to attach without the ClickGUI module.

## ClickGUI

One implementation serves every supported version, because it never touches
Minecraft's own rendering: the menu is a Swing overlay created inside the target
process.

- `RIGHT SHIFT` toggles it while a world is loaded.
- `TEST` writes `Already Injected!` into your own chat.
- `ESC` or the `✕` in the header closes it; the header is draggable.
- Entries come from `ClickGuiRegistry`, so modules can register their own buttons.

Keyboard support per runtime:

| Versions | Input API | Notes |
| --- | --- | --- |
| 1.7.10, 1.8.9 | LWJGL 2 `Keyboard` | works on vanilla and modded launches |
| 1.20.1, 1.21.11, 26.2 | LWJGL 3 / GLFW | window handle is read from the client, or from `glfwGetCurrentContext()` on the render thread |

GLFW is only ever called from the game's own task queue, and no window pointer
is guessed. If neither input API is reachable the agent logs it and the ClickGUI
stays available through the method registry instead of the hotkey.

Methods exposed to the host/Bootstrap layer:

```text
clickgui.toggle [open|close|toggle]
clickgui.state
clickgui.click <button-id>
client.message <text...>
chat.state
```

The Swing overlay needs a windowed or borderless game window; an exclusive
fullscreen window can paint over it.

## Client-only chat

HotInjection messages behave like a client-only command: the client really runs
its own chat send path, and the packet it produces is cancelled on the way out,
so the server never receives anything.

```text
sendClientMessage()
  -> ClientMessageEvent      (cancel = nothing happens at all)
  -> ChatSendEvent           (cancel = no packet is ever created)
  -> client chat send method (invoked on the game thread, like typing in chat)
  -> PacketSendEvent         (local-chat module cancels it before the encoder)
  -> local echo into the chat HUD, or the toast fallback
```

The echo target is the game's real chat HUD, located reflectively: named lookups
cover MCP, Yarn, Fabric intermediary and Forge SRG runtimes, and a structural
pass handles fully obfuscated clients. When no chat HUD can be reached the
message falls back to the local toast window, so the proof of injection never
disappears silently.

The packet hook is a `ChannelOutboundHandler` proxy appended to the client's
netty pipeline. The channel is found by its netty type rather than by any
Minecraft name, so the same code works on all five versions; packets that no
listener cancels are forwarded untouched.

The design fails closed, never towards the network: the real send path runs
**only** when the packet guard is installed and the `local-chat` module is
listening. Otherwise the message stays a purely local echo. While a message is
in flight the guard drops every outgoing packet that carries text, so a renamed
or reformatted chat packet cannot slip through either.

Attach with `--option packetguard=false` to skip the network hook entirely and
keep the plain local echo. `chat.state` reports which paths are live.

## Bootstrap Lite protocol seed

The host exposes a deliberately tiny line protocol for the next milestone:

```text
READY protocol=1
LIST
TARGET\t<pid>\t<display name>
END
ATTACH <pid> <version-or-auto>
ATTACHED\t<pid>\t<version>
QUIT
```

Start it with:

```bash
java --add-modules jdk.attach -jar hotinjection-host/target/sigma-hotinjection-host.jar \
  --stdio hotinjection-agent/target/sigma-hotinjection-agent.jar
```

This protocol is only the host bootstrap layer. Module/value ClickGUI messages will be added as a versioned protocol rather than coupling Bootstrap to the HotInjection internal architecture.

## Safety / scope

HotInjection only attaches to a PID explicitly selected by the user. The project does not hide the attach operation, bypass security software, or evade server-side/anti-cheat detection. Version-specific game integration should keep that same explicit model.
