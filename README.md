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
- A real injection-success proof: after attach, `InjectionNoticeEvent` is posted. Unless cancelled, the current adapter displays a local-only notification. No chat/network packet is sent to a server.
- A `quiet-notice` module demonstrating that the notification can be cancelled through the event bus.
- Host modes for standalone UI, CLI attach, target listing, and a minimal stdin/stdout protocol intended for Bootstrap Lite.

The current version adapters deliberately use a mapping-independent local toast for the proof-of-injection message. Native Minecraft chat/HUD bridges belong in individual version adapters and can be added later without changing the core registries.

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

Use `--quiet` to enable the sample `quiet-notice` module before the `InjectionNoticeEvent` is posted. The attach still succeeds, but the local success notification is cancelled.

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
