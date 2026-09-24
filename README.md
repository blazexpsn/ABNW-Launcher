
# A Brand New World (ABNW)

**A Brand New World (ABNW)** is an independent, fan-made Minecraft: Java Edition project combining a built-in modding platform with extensive engine-level changes.

ABNW is currently in **beta** and is actively developing its own identity, content, systems and modding ecosystem.

Rather than distributing a complete modified copy of Minecraft, ABNW releases are delivered as small binary patches. The ABNW Launcher obtains the required Minecraft files legitimately from Mojang and applies the selected ABNW patch locally on the player's computer.

> **NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**

---

## The brand

| | |
| --- | --- |
| **Name** | A Brand New World |
| **Abbreviation** | ABNW |
| **Launcher** | ABNW Launcher |
| **Tagline** | "A Brand New World" |
| **Logo** | A pixel-art sunset over hills with the words "A BRAND NEW WORLD" |

ABNW has its own name, logo, launcher and visual identity.

The Minecraft name, logo, fonts and trademarks are not used as part of ABNW's branding. References to Minecraft are used only where necessary to explain what game ABNW is built from or compatible with.

---

## What ABNW is

ABNW is more than a conventional Minecraft mod.

It combines:

- a mod loader built directly into the game;
- a public modding API;
- extensive engine-level rewrites;
- its own content updates;
- its own versioning;
- its own launcher;
- and, eventually, its own mod ecosystem.

ABNW remains based on Minecraft: Java Edition, but ABNW releases are treated as their own versions rather than tracking each new Minecraft release.

---

## A mod loader built into the game

ABNW's mod loader and modding API are integrated directly into the patched game.

Mods can interact with the engine through supported systems rather than requiring another mod loader to sit between them and Minecraft.

Current and planned capabilities include:

- **Mods** — metadata, dependencies and entrypoints.
- **Events and hooks** — lifecycle and gameplay event systems.
- **Content registration** — blocks, items, entities and other game content.
- **Custom registries** — mods can create and expose registries of their own.
- **Networking** — dedicated networking channels for mods.
- **Data-driven content** — game content and configuration defined through data files.
- **Game modes** — custom game types can be registered rather than being limited to a fixed enum.
- **Enchantments and effects** — extensible effect systems.
- **Mixins and transformations** — available for mods that genuinely need engine-level access.

The goal is for normal mod development to use supported APIs wherever possible, while still allowing advanced mods to change deeper parts of the game when necessary.

---

## Engine-level changes

ABNW modifies systems far below the level normally reached by a Minecraft mod.

### Large cubic worlds

ABNW uses a cubic world architecture with 64-bit world addressing.

This allows worlds to extend dramatically farther vertically than vanilla Minecraft while loading only the areas that are actually needed.

The normal ABNW build and generation range is intentionally enormous, allowing terrain and world-generation systems to make use of vertical scales that would not be practical in vanilla.

### Rendering

ABNW contains its own rendering work, including:

- **Vulkan rendering** as the primary backend;
- **OpenGL** as a compatibility fallback;
- renderer selection directly from the ABNW Launcher;
- ongoing lighting and rendering-engine development.

Players do not need to manually configure JVM properties to select a renderer.

### World generation

ABNW expands Minecraft's terrain and biome systems and is intended to support extremely large vertical environments, including deep cave systems and terrain far beyond vanilla's normal scale.

### New content

ABNW also adds its own gameplay content and systems, including work on:

- new materials;
- new enchantments;
- new terrain and biomes;
- expanded farming and plants;
- magic;
- 3D audio;
- proximity voice chat;
- and other gameplay systems.

ABNW is intended to be worth playing even without third-party mods installed.

---

## ABNW has its own updates

ABNW does not automatically move to each new Minecraft version.

The current foundation is:

**Minecraft 26.1.2**

Future ABNW releases build upon that foundation as new ABNW versions.

For example:

```text
Minecraft 26.1.2
        +
ABNW engine changes
        +
ABNW content updates
        =
ABNW 1.x
```

A future Minecraft feature may inspire an ABNW feature, but ABNW is not required to reproduce or port every feature Mojang adds after its foundation version.

---

## The ABNW Launcher

ABNW is installed and launched through the **ABNW Launcher**.

The launcher handles the parts of installation that would otherwise require users to manually modify Minecraft files.

It can:

- list available ABNW versions;
- download release metadata;
- obtain the required Minecraft version;
- verify installation files;
- download ABNW patches;
- apply patches locally;
- install ABNW's required open-source libraries;
- verify the resulting installation;
- select Vulkan or OpenGL;
- manage launcher settings;
- and start ABNW with the required runtime configuration.

The goal is for the player-facing process to remain simple:

```text
Sign in
↓
Choose ABNW version
↓
Choose renderer
↓
Install
↓
Play
```

The patching, library management and JVM configuration happen behind the scenes.

---

## How ABNW is delivered

ABNW does **not distribute a complete Minecraft installation or Mojang's original Minecraft JAR as an ABNW download**.

Instead, each ABNW release publishes a binary delta in the standard **VCDIFF/xdelta** format.

That delta describes the transformation between a specific Minecraft JAR and the corresponding ABNW JAR.

It is applied locally by the ABNW Launcher.

A release patch is therefore tied to an exact Minecraft base version and cannot be treated as a standalone ABNW installation.

### Installation process

When a player installs ABNW:

1. The launcher obtains the required Minecraft files from Mojang's services.
2. The original Minecraft JAR is checked against the hash expected by the ABNW release.
3. The ABNW release manifest and patch are downloaded.
4. The patch is verified.
5. The patch is applied locally.
6. The resulting ABNW JAR is verified before it is used.
7. Any additional open-source libraries required by ABNW are downloaded from their respective repositories.

SHA-256 verification is used throughout the release pipeline.

The launcher verifies:

- the expected original Minecraft JAR;
- the ABNW release manifest;
- the ABNW patch;
- the additional library manifest;
- and the resulting patched game JAR.

---

## Release files

Each ABNW release contains files similar to:

| File | Purpose |
| --- | --- |
| `abnw-<version>-mc<minecraft>.xdelta` | Binary patch applied to the supported Minecraft JAR |
| `abnw-<version>-mc<minecraft>.json` | Release manifest containing expected hashes and sizes |
| `org.teamzetaverse.abnw.libraries.json` | Additional open-source libraries required by ABNW |

The release index:

```text
abnw-versions.json
```

lists the ABNW versions currently available to the launcher.

A version entry contains enough information for the launcher to discover the release and locate its corresponding release manifest.

The release manifest then contains the detailed information required to install and verify that version.

---

## Integrity chain

ABNW's update system is designed around an explicit verification chain:

```text
abnw-versions.json
        ↓
ABNW release manifest
        ↓
verify manifest
        ↓
download patch
        ↓
verify patch
        ↓
verify original Minecraft JAR
        ↓
apply patch locally
        ↓
verify resulting ABNW JAR
```

A failed verification stops the installation rather than launching an unexpected build.

---

## Microsoft sign-in

The ABNW Launcher is designed to use Microsoft authentication to sign players into Minecraft.

The intended authentication chain is:

```text
Microsoft
↓
Xbox Live
↓
XSTS
↓
Minecraft Services
```

The launcher uses Microsoft's device-code authentication flow so the player's Microsoft password is entered on Microsoft's own website rather than into the ABNW Launcher.

ABNW needs Minecraft authentication in order to:

- verify that the account is entitled to Minecraft: Java Edition;
- obtain the required game files through Mojang's services;
- retrieve the player's Minecraft profile;
- and launch the game with a valid Minecraft session.

Public Microsoft/Minecraft authentication is currently dependent on the required application approval process.

ABNW does not provide an ownership bypass or public cracked/offline mode.

---

## Account data

ABNW is not intended to operate an account-data or analytics service.

Authentication credentials and tokens used by the launcher are intended to remain on the player's own computer and be sent only to the services required for Microsoft/Xbox/Minecraft authentication.

ABNW does not require analytics or telemetry servers in order to install or run the game.

---

## Renderer selection

The ABNW Launcher allows the player to select the rendering backend.

### Vulkan

Vulkan is ABNW's primary renderer.

Normal player builds run without Vulkan's development validation layer so that debugging instrumentation does not unnecessarily reduce performance.

### OpenGL

OpenGL is provided as a compatibility fallback for systems where Vulkan is unavailable or unsuitable.

Renderer selection is handled by the launcher rather than requiring players to manually edit JVM arguments.

---

## Linux

Linux support is planned around the same ABNW Launcher used on other platforms.

Linux distributions may provide setup scripts appropriate to their package manager for installing the required Java runtime and other prerequisites.

A launch script then starts the ABNW Launcher using the configured Java runtime.

The launcher itself remains responsible for installing and updating ABNW.

---

## Building the Windows launcher

The launcher module exposes a Gradle `jpackage` task that creates a Windows installer `.exe` with a bundled Java runtime. The installer includes the ABNW logo, a Start Menu entry, and a desktop shortcut. Run it from the repository root:

```text
gradle :launcher:jpackage
```

The installer is written to `launcher/build/distributions/ABNW Launcher-<version>.exe`.
The task must run on Windows with a JDK that includes `jpackage`; it cannot create a Windows `.exe` from Linux or macOS.

The icon is generated from the launcher’s existing multi-size PNG assets during the build, so the installed launcher and its shortcuts use the ABNW logo.

The launcher is compiled and packaged with UTF-8 explicitly enabled so text remains intact when started from the installed Windows executable.

The task automatically runs `launcher/scripts/find-wix.py`. The script first validates the cached path in `launcher/build/jpackage/wixpath.txt`; if that file is missing or stale, it searches `PATH`, Windows installer registry entries, common package-manager locations, and all filesystem drives for WiX 3 (`candle.exe`/`light.exe`) or WiX 4/5 (`wix.exe`). A successful search rewrites `wixpath.txt`, and the discovered directory is added to the packaging process. If WiX is not installed, install WiX 3+ or WiX 4/5 and run the task again.

## Building the Flatpak

The launcher also exposes a `flatpak` task. It uses the shaded launcher JAR, the existing ABNW icons, and the Freedesktop OpenJDK 21 SDK extension to produce a self-contained Flatpak bundle with a Java runtime:

```text
gradle :launcher:flatpak
```

On Linux, run this task with `flatpak` and `flatpak-builder` installed. On Windows, the task stages only the launcher JAR, manifest, script, desktop entry, and icons with Windows Gradle, then copies those inputs into the persistent WSL-native workspace `~/abnw-flatpak-build/org.teamzetaverse.ABNWLauncher`. `flatpak-builder`, repository export, and bundle creation all run there; only the finished `.flatpak` is copied back to Windows. Gradle does not need to be installed in WSL.

The Windows task adds Flathub and automatically installs any missing 24.08 Platform, SDK, and OpenJDK 21 extension refs. Existing SDKs, runtimes, Flatpak downloads, and builder caches are preserved between builds. The task prints each stage as it runs and ends with a timing breakdown. The bundle is written to `launcher/build/distributions/ABNW Launcher-<version>.flatpak`. The Flatpak application ID is `org.teamzetaverse.ABNWLauncher`, and launcher data is kept in the app's sandbox data directory.

The normal build keeps the native WSL build directory and repository for incremental builds. Use `gradle :launcher:cleanFlatpakBuild` when those disposable directories need to be removed; Flatpak SDK and download caches are left intact. Use `gradle :launcher:flatpak -PflatpakFullClean=true` to force a clean build during the next run. Set `ABNW_FLATPAK_WSL_ROOT` or `-PflatpakWslRoot=/some/native/path` to override the WSL workspace. Paths under `/mnt/c`, `/mnt/d`, or another Windows-mounted directory are rejected because they are significantly slower.

Use `gradle :launcher:flatpakStatus` in another terminal to inspect active `flatpak`, `flatpak-builder`, OSTree, Gradle, and Java processes in WSL. Set `ABNW_WSL_DISTRIBUTION` when Ubuntu is not the default WSL distribution.

The root `publishLauncherRelease` task depends on this task and uploads the finished Windows-side Flatpak next to the JAR, Linux ZIP, and Windows installer.

## Mods for ABNW

ABNW has its own native modding platform.

Third-party mods are expected to target ABNW's APIs rather than requiring Fabric or NeoForge.

ABNW mods can range from simple content additions to large systems that interact directly with ABNW's engine APIs.

The long-term goal is for the ABNW Launcher to include an integrated mod browser where players can:

- discover ABNW mods;
- install them;
- update them;
- manage dependencies;
- and enable or disable installed content.

ABNW is **actively looking for a modding platform willing to support ABNW** and host community-created mods for the project.

Until a suitable platform is confirmed, no third-party mod hosting integration should be considered final.

---

## Mod distribution

The intended model is:

```text
Mod developer
↓
publishes an ABNW mod
↓
supported mod hosting platform
↓
ABNW Launcher
↓
Browse / Install / Update
↓
ABNW mods directory
```

ABNW itself will continue to be installed using the official ABNW patch system.

The external modding platform would be used for **third-party ABNW mods**, not as a replacement for ABNW's own release infrastructure.

---

## Beta status

ABNW is currently in active development.

Features, APIs, file formats and launcher behaviour may change while the project is in beta.

Some systems are already functional while others are still being developed or redesigned.

Bug reports and testing should therefore identify the exact ABNW version being used.

---

## Minecraft EULA and Usage Guidelines

ABNW is intended to operate in accordance with the Minecraft EULA and Minecraft Usage Guidelines.

The project's distribution model is designed around the following principles:

- **No complete-game redistribution.** ABNW does not publish Mojang's original Minecraft JAR or a complete modified Minecraft installation as its release artifact.
- **Local patching.** ABNW changes are applied locally to the supported Minecraft installation.
- **Legitimate game access.** Public ABNW installations are intended for players entitled to Minecraft: Java Edition through an eligible Microsoft account.
- **Separate branding.** ABNW uses its own project name, launcher and visual identity and is clearly presented as unofficial.
- **Original additions.** Original assets and code created specifically for ABNW remain separate from Mojang's original distribution.

---

## Project structure

At a high level:

```text
Minecraft 26.1.2
        │
        ├── ABNW engine patches
        ├── ABNW mod loader
        ├── ABNW modding API
        ├── ABNW renderer
        ├── ABNW content
        └── ABNW systems
                 │
                 ▼
            ABNW release
                 │
                 ▼
           Binary patch
                 │
                 ▼
          ABNW Launcher
```

Third-party mods then run on top:

```text
ABNW
 ├── Native ABNW mods
 ├── Datapacks
 └── Other supported custom content
```
## Launcher licensing and third-party launchers

The **official ABNW Launcher is proprietary software and is All Rights Reserved**.

Its source code, branding, artwork and other original launcher assets may not be copied, redistributed, modified or republished except where permission is explicitly granted.

However, ABNW does **not** prohibit independent developers from creating their own launchers that support ABNW.

Third-party launchers may implement ABNW's public installation and launch process, including:

- reading the public ABNW version index;
- downloading ABNW release manifests;
- obtaining the required Minecraft files through legitimate means;
- applying ABNW binary patches locally;
- verifying published hashes;
- downloading required public libraries;
- launching ABNW with the appropriate runtime configuration;
- and supporting native ABNW mods.

A third-party launcher must use its **own code, name, branding and assets** and must not present itself as the official ABNW Launcher.

Compatibility with ABNW does not grant permission to copy or redistribute the official ABNW Launcher's source code or proprietary assets.

The intention is simple:

> **The official launcher is All Rights Reserved. The ABNW platform is not launcher-locked.**

Developers are welcome to create independent launchers or add ABNW support to existing launcher projects, provided they respect the rights of ABNW, Mojang, Microsoft and any other relevant software or services.
---

## Contact

**Project:** A Brand New World (ABNW)

**Repository:**  
https://github.com/blazexpsn/Minecraft-ABNW

**Launcher repository:**  
https://github.com/blazexpsn/ABNW-Launcher

**Email:**  
ABrandNewWorldMC@outlook.com

---

Minecraft is a trademark of Mojang Synergies AB.

ABNW is an independent fan-made project and is not affiliated with, endorsed by, approved by or sponsored by Mojang or Microsoft.
