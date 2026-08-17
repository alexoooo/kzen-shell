# kzen-shell — AI agent guide

## Purpose

kzen-shell is the **desktop composition root** — the only thing a packaged end user actually launches. It binds Ktor on `127.0.0.1:8080`, spawns child JVM processes (the launcher first, then user-selected projects), and **reverse-proxies HTTP traffic** to them under a name-prefix URL scheme. From the browser, everything looks like a single app on one port; under the hood it's several child JVMs.

Unlike the other siblings, kzen-shell is **JVM-only, single-module**, and does NOT consume kzen-lib's notation/CQRS model directly. It's a process manager and HTTP proxy.

The shell is deliberately a tiny, stable kernel that ~never needs re-releasing, while the launcher UI evolves with the product and updates by artifact download — the shell/launcher split *is* the update mechanism, so don't merge the launcher into the shell to save a process.

## Module layout

Single Gradle project — no `-common` / `-jvm` / `-js` split. Sources at `src/main/kotlin/tech/kzen/shell/`.

## Entry points

| Class | Purpose |
|----|----|
| `tech.kzen.shell.KzenShellMain` (`fun main`) | Production entry. Inits context, downloads/extracts launcher zip if missing, spawns launcher as `main.jar` on a free port, starts Ktor on `127.0.0.1:8080`, opens the desktop UI. |

There's no dev-mode `BackendDevelopment`/`FrontendDevelopment` pair — kzen-shell has no JS frontend of its own (the UI lives in the embedded launcher and projects, which it proxies).

## Build & run

```powershell
./gradlew build
java -jar build/libs/kzen-shell-*.jar
# Use the JDK 26 toolchain explicitly if PATH java is older (see ../kzen/AGENTS.md Java-26-vs-PATH gotcha):
& "C:/Users/ostro/.jdks/temurin-26.0.2/bin/java" -jar build/libs/kzen-shell-*.jar
```

End-user packaging (the `kzen-<v>.zip` distribution on GitHub releases) is produced by the `distWindows` Gradle task — see **Distribution** below (neither `dist` task is wired into `build`).

## How the reverse proxy works

**Routing contract: `/<process-name>/<subpath>` is forwarded to whichever child process registered under `<process-name>` in `ProcessRegistry`.** This contract is the load-bearing piece — every child UI must serve its own assets as if mounted at that prefix. Children honour it by deriving their base URL from the browser path (`window.location.pathname.substringBeforeLast("/")` — launcher `ajaxUtil.kt`, kzen-auto `ClientContext.kt`) and serving relative asset URLs, so any child UI works at any prefix with zero configuration; the proxy never rewrites response bodies. Any new child UI must do the same.

Special rules in `KzenShellMain.kt`:

- `/` and `/index.html` → redirect to `/main/index.html`.
- The literal name `main` is rewritten by `ProxyHandler` to whichever process was registered with the launcher's jar path (`<launcherDir>/main.jar`, derived once as `KzenShellContext.launcherJarPath`) — i.e. the launcher. So `/main/...` always reaches the launcher.
- `/shell/project`, `/shell/project/start`, `/shell/project/stop` are kzen-shell's only first-class endpoints (project lifecycle CRUD).
- Everything else (GET/PUT/POST/GET-with-trailing-slash) falls through to `ProxyHandler.handle()`.

## Key directories

| Path (under `src/main/kotlin/tech/kzen/shell/`) | What lives here |
|----|----|
| `KzenShellMain.kt` | Entry point, Ktor wiring, routing |
| `context/KzenShellContext.kt` | Composition: properties, registries, proxy handler, launcher runner |
| `context/KzenShellProperties.kt` | Config: launcher dir, launcher zip URL, project home, port |
| `ui/DesktopUi.kt` | Opens the system browser to `http://localhost:<port>` |
| `proxy/ProxyHandler.kt` | The reverse-proxy core: name-prefix routing, header forwarding, `main` rewrite |
| `registry/ProcessRegistry.kt` | Registry of running child processes (name → port + spawned jar path) |
| `registry/ProjectRegistry.kt` | Async STARTING/RUNNING/STOPPING/FAILED state machine of user-launched project processes |
| `process/MainJarRunner.kt`, `process/MainJarProcess.kt` | Spawns `main.jar` files (used for both launcher and projects) |
| `repo/ArtifactInstaller.kt`, `repo/DownloadService.kt` | Download + extract of the launcher zip (single artifact, not a catalogue); prunes stale `-SNAPSHOT` sibling extractions |
| `util/FreePortUtil.kt` | Allocates free ports for child processes |
| `util/ProcessAwaitUtil.kt` | Waits for a child to become healthy on its port (bounded, checks `isAlive`) |


## Gotchas

- **Launcher zip source is config, not a hard-coded path.** `KzenShellProperties.load` resolves it from `--launcher.zip=` arg > `kzen-shell.properties` (read from the working dir); `launcher.dir` is the unpack dir. The `dist` zip bundles a release `kzen-shell.properties` pointing at the launcher's GitHub URL (generated from `version`). `file://` sources are re-extracted on every boot (`ArtifactInstaller.downloadIfAbsent`), so a rebuilt launcher zip is picked up automatically; `https` release sources install once. If re-acquisition fails (source zip cleaned, offline) the boot degrades to the existing extraction instead of crashing; old `-SNAPSHOT` sibling extractions are pruned at boot (released dirs are kept). The download+extract is staged in a sibling `<dir>.staging/`, verified (`main.jar` must exist), then atomically swapped into place, and the presence check keys on `main.jar` (not the dir) — so a crash mid-extract self-heals on the next boot. On a version bump edit the versions in `kzen-shell.properties`. Full release procedure: [`../kzen/docs/RELEASING.md`](../kzen/docs/RELEASING.md).
- **`project.home` names where user projects live** — same config pattern as `launcher.dir` (`--project.home=` arg > `kzen-shell.properties` > default `work/kzen-proj`), resolved to an absolute path and passed to the launcher as `--project.home=`. It sits *beside* `launcher.dir`, never inside it: `work/kzen-launcher/` is a managed artifact cache the shell prunes at boot, and user projects must outlive it. Without the arg the launcher would fall back to `../kzen-proj` relative to its own unpack dir, which is why a shell-spawned and an interactive launcher otherwise see different project lists.
- **Child JVMs self-reap via a managed lifeline — two independent reapers.** `MainJarProcess` spawns every child with `--managed.lifeline=stdin --parent.pid=<shell pid>`: the child exits on stdin EOF (parent died or closed the pipe; a `SHUTDOWN` line requests a graceful stop), and — the backstop that survives a force-kill of the shell — on a `ProcessHandle.of(parentPid).onExit()` watchdog. The reaper code is duplicated by design in two homes: `KzenLauncherMain` carries its own copy (the launcher depends on neither kzen-lib nor kzen-auto), and kzen-auto's lives in `kzenAutoInit` so both `KzenAutoMain` and `KzenProjectMain` inherit it. Both are gated on the flags, so dev/standalone runs start no watchers. Diagnosing orphans: `Get-CimInstance Win32_Process -Filter "Name='java.exe'"`, filter `CommandLine` on `main.jar` — a child whose `--parent.pid=` is not a live process is an orphan. Never `Stop-Process` a JVM you didn't start (it may be the user's own instance); surface the PID and ask.
- **`MainJarRunner` looks for `main.jar`.** Every distributable zip already ships its fat jar under that name at the zip root beside `dependencies/` (the `dist` layout — see [`../kzen/docs/RELEASING.md`](../kzen/docs/RELEASING.md)); nothing renames it at install time. Both installers merely *verify* it (`ArtifactInstaller` here, `ProjectCreator` in kzen-launcher), so changing the name means changing the dist build and both siblings together.
- **No KMP, no kotlin-wrappers.** This is the only sibling that's plain JVM. Don't try to apply `-common`/`-jvm`/`-js` module-split assumptions here.
- **The `/main/` URL prefix is special.** Child processes (launcher, projects) don't know what name they're registered under — they have to serve from `/<their-name>/...` consistently. The launcher hard-codes `main` as its self-name in some places.
- **`work/` and `logs/` are runtime dirs.** `work/` holds downloaded/extracted launcher and project payloads. `logs/` holds shell + child-process stdout/stderr. Both `.gitignore`d.
- **Launcher heap is pinned small on purpose** (`-Xmx64m`, `KzenShellContext`) — it's a registry + static file server. If the launcher ever OOMs, grow deliberately; don't inherit project-sized args.

## End-to-end runtime

1. User runs `kzen.bat` (windowless, via the bundled `jdk\`) or `kzen-cmd.bat` (console) → `javaw -jar kzen-<v>.jar`.
2. Shell downloads `kzen-launcher-<v>.zip` (if missing) into `../work/kzen-launcher/...`, unpacks it.
3. `MainJarRunner` spawns the launcher as `main.jar` on a free port (spawn args: `--server.port`, the managed-lifeline pair, and `--project.home`); registers it in `ProcessRegistry` as `main`.
4. Shell binds `127.0.0.1:8080`, opens the browser to `http://localhost:8080/` (redirects to `/main/index.html` → launcher's index).
5. User picks a project archetype in the launcher UI → launcher REST calls reach the shell via the proxy.
6. Shell's `/shell/project/start` spawns the chosen project as a new `main.jar` on a free port; registers it in `ProjectRegistry` + `ProcessRegistry` under the project's name.
7. Browser navigates to `/<project-name>/...`; proxy forwards everything there.

## Distribution

Two `dist` tasks build the app archives (neither wired into `build`): `distJars` → `kzen-<v>-jars.zip`
(jars only, for a bring-your-own-JDK user) and `distWindows` → `kzen-<v>.zip` (jars + a bundled Temurin
JDK + `kzen.bat`/`kzen-cmd.bat`). The JDK is fetched + SHA-256-verified + cached by the
`ProvisionAdoptiumJdk` task in `buildSrc/src/main/kotlin/dist/`. Full release procedure:
[`../kzen/docs/RELEASING.md`](../kzen/docs/RELEASING.md).

## Pointers

- **Composite build + toolchain** → [`../kzen/AGENTS.md`](../kzen/AGENTS.md).
- **First child process** → [`../kzen-launcher/AGENTS.md`](../kzen-launcher/AGENTS.md).
- **Typical project archetype** → [`../kzen-project/AGENTS.md`](../kzen-project/AGENTS.md).
