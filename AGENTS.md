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
java -jar build/libs/kzen-shell-0.29.1-SNAPSHOT.jar
# Use the JDK 25 toolchain explicitly if PATH java is older:
& "C:/Users/ostro/.jdks/temurin-25.0.3/bin/java" -jar build/libs/kzen-shell-0.29.1-SNAPSHOT.jar
```

End-user packaging (the `kzen-<v>.zip` distribution on GitHub releases) is hand-built, not produced by Gradle.

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

- **Hard-coded launcher zip path.** `KzenShellMain.kt:45` pins:
  ```
  file:///C:/Users/ostro/IdeaProjects/kzen-launcher/kzen-launcher-jvm/build/libs/kzen-launcher-0.29.1-SNAPSHOT.zip
  ```
  Bumping the launcher version means editing this line *and* the unpacked-dir name on line 44 (`../work/kzen-launcher/kzen-launcher-<v>/`). The commented-out GitHub releases URL on line 46 is the alternate source.
- **`MainJarRunner` looks for `main.jar`.** The launcher renames its downloaded fat jar to `main.jar` (`kzen-launcher/.../ProjectCreator.kt:62`); don't change this convention without updating both sides.
- **No KMP, no kotlin-wrappers.** This is the only sibling that's plain JVM. Don't try to apply `-common`/`-jvm`/`-js` module-split assumptions here.
- **The `/main/` URL prefix is special.** Child processes (launcher, projects) don't know what name they're registered under — they have to serve from `/<their-name>/...` consistently. The launcher hard-codes `main` as its self-name in some places.
- **`work/` and `logs/` are runtime dirs.** `work/` holds downloaded/extracted launcher and project payloads. `logs/` holds shell + child-process stdout/stderr. Both `.gitignore`d.

## End-to-end runtime

1. User runs `kzen.bat` → `java -jar kzen-shell-*.jar`.
2. Shell downloads `kzen-launcher-<v>.zip` (if missing) into `../work/kzen-launcher/...`, unpacks it.
3. `MainJarRunner` spawns the launcher as `main.jar` on a free port; registers it in `ProcessRegistry` as `main`.
4. Shell binds `127.0.0.1:8080`, opens the browser to `http://localhost:8080/` (redirects to `/main/index.html` → launcher's index).
5. User picks a project archetype in the launcher UI → launcher REST calls reach the shell via the proxy.
6. Shell's `/shell/project/start` spawns the chosen project as a new `main.jar` on a free port; registers it in `ProjectRegistry` + `ProcessRegistry` under the project's name.
7. Browser navigates to `/<project-name>/...`; proxy forwards everything there.

## Pointers

- **Composite build + toolchain** → [`../kzen/AGENTS.md`](../kzen/AGENTS.md).
- **First child process** → [`../kzen-launcher/AGENTS.md`](../kzen-launcher/AGENTS.md).
- **Typical project archetype** → [`../kzen-project/AGENTS.md`](../kzen-project/AGENTS.md).
