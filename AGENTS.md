# kzen-shell — AI agent guide

## Purpose

kzen-shell is the **desktop composition root** — the only thing a packaged end user actually launches. It binds Ktor on `127.0.0.1:8080`, spawns child JVM processes (the launcher first, then user-selected projects), and **reverse-proxies HTTP traffic** to them under a name-prefix URL scheme. From the browser, everything looks like a single app on one port; under the hood it's several child JVMs.

Unlike the other siblings, kzen-shell is **JVM-only, single-module**, and does NOT consume kzen-lib's notation/CQRS model directly. It's a process manager and HTTP proxy.

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
java -jar build/libs/kzen-shell-0.30.0-SNAPSHOT.jar
# Use the JDK 26 toolchain explicitly if PATH java is older (the jar is now class-file v70):
& "C:/Users/ostro/.jdks/temurin-26.0.1/bin/java" -jar build/libs/kzen-shell-0.30.0-SNAPSHOT.jar
```

End-user packaging (the `kzen-<v>.zip` distribution on GitHub releases) is produced by the `distWindows` Gradle task — see **Distribution** below (neither `dist` task is wired into `build`).

## How the reverse proxy works

**Routing contract: `/<process-name>/<subpath>` is forwarded to whichever child process registered under `<process-name>` in `ProcessRegistry`.** This contract is the load-bearing piece — every child UI must serve its own assets as if mounted at that prefix.

Special rules in `KzenShellMain.kt`:

- `/` and `/index.html` → redirect to `/main/index.html`.
- The literal name `main` is rewritten by `ProxyHandler` to whichever process was registered with `attributes["location"] == <launcherDir>/main.jar` — i.e. the launcher. So `/main/...` always reaches the launcher.
- `/shell/project`, `/shell/project/start`, `/shell/project/stop` are kzen-shell's only first-class endpoints (project lifecycle CRUD).
- Everything else (GET/PUT/POST/GET-with-trailing-slash) falls through to `ProxyHandler.handle()`.

## Key directories

| Path (under `src/main/kotlin/tech/kzen/shell/`) | What lives here |
|----|----|
| `KzenShellMain.kt` | Entry point, Ktor wiring, routing |
| `context/KzenShellContext.kt` | Composition: properties, registries, proxy handler, launcher runner |
| `context/KzenShellProperties.kt` | Config: launcher dir, launcher zip URL, port |
| `ui/DesktopUi.kt` | Opens the system browser to `http://localhost:<port>` |
| `proxy/ProxyHandler.kt` | The reverse-proxy core: name-prefix routing, header forwarding, `main` rewrite |
| `proxy/ProxyApi.kt`, `proxy/ProxyResult.kt` | Proxy types |
| `registry/ProcessRegistry.kt` | Registry of running child processes (name → port + attributes) |
| `registry/ProjectRegistry.kt` | Guava cache of user-launched project processes |
| `process/MainJarRunner.kt`, `process/MainJarProcess.kt` | Spawns `main.jar` files (used for both launcher and projects) |
| `process/GradleRunner.kt`, `process/GradleProcess.kt` | Alternate spawn path via Gradle (dev convenience) |
| `repo/ArtifactRepo.kt`, `repo/DownloadService.kt` | Download + cache of launcher/project zips |
| `run/LauncherRunner.kt` | Specialized runner for the launcher (first child) |
| `util/FreePortUtil.kt` | Allocates free ports for child processes |
| `util/ProcessAwaitUtil.kt` | Waits for a child to become healthy on its port |

## Gotchas

- **Launcher zip source is config, not a hard-coded path.** `KzenShellProperties.load` resolves it from `--launcher.zip=` arg > `kzen-shell.properties` (read from the working dir); `launcher.dir` is the unpack dir. The `dist` zip bundles a release `kzen-shell.properties` pointing at the launcher's GitHub URL (generated from `version`). `file://` sources are re-extracted on every boot (`ArtifactRepo.downloadIfAbsent`), so a rebuilt launcher zip is picked up automatically; `https` release sources install once. As of 0.30.0 the download+extract is staged in a sibling `<dir>.staging/`, verified (`main.jar` must exist), then atomically swapped into place, and the presence check keys on `main.jar` (not the dir) — so a crash mid-extract self-heals on the next boot instead of leaving a half-populated dir that looks complete. On a version bump edit the versions in `kzen-shell.properties`. Full release procedure: [`../kzen/docs/RELEASING.md`](../kzen/docs/RELEASING.md).
- **`MainJarRunner` looks for `main.jar`.** The launcher renames its downloaded fat jar to `main.jar` (`kzen-launcher/.../ProjectCreator.kt:62`); don't change this convention without updating both sides.
- **No KMP, no kotlin-wrappers.** This is the only sibling that's plain JVM. Don't try to apply `-common`/`-jvm`/`-js` module-split assumptions here.
- **The `/main/` URL prefix is special.** Child processes (launcher, projects) don't know what name they're registered under — they have to serve from `/<their-name>/...` consistently. The launcher hard-codes `main` as its self-name in some places.
- **`work/` and `logs/` are runtime dirs.** `work/` holds downloaded/extracted launcher and project payloads. `logs/` holds shell + child-process stdout/stderr. Both `.gitignore`d.

## End-to-end runtime

1. User runs `kzen.bat` (windowless, via the bundled `jdk\`) or `kzen-cmd.bat` (console) → `javaw -jar kzen-<v>.jar`.
2. Shell downloads `kzen-launcher-<v>.zip` (if missing) into `../work/kzen-launcher/...`, unpacks it.
3. `MainJarRunner` spawns the launcher as `main.jar` on a free port; registers it in `ProcessRegistry` as `main`.
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
