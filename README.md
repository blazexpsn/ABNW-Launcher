# A Brand New World (ABNW)

**A Brand New World (ABNW)** is an independent, fan-made project for Minecraft: Java Edition. It
is a mod loader and an engine-level rewrite of the game in one. It releases its own content
updates as small binary patches that each player applies to their own legitimately owned copy of
the game. **ABNW never distributes Minecraft.**

**NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**

---

## The brand

| | |
|---|---|
| **Name** | A Brand New World, abbreviated **ABNW** |
| **Launcher** | ABNW Launcher |
| **Tagline** | "A Brand New World" |
| **Logo** | A pixel-art sunset over hills with the words "A BRAND NEW WORLD" (above) |

ABNW's name, logo and launcher are its own. They do not use the Minecraft name, logo, fonts or
trademarks as part of the brand, and they are not designed to look official. "Minecraft" appears
only to say which game ABNW is made for.

---

## What ABNW is

### A mod loader, built into the game
ABNW's modding API is part of the patched game itself, so mods talk to the engine directly:

- **Mods:** metadata, dependencies and entrypoints.
- **Hooks:** an event bus and lifecycle hooks.
- **Content:** registration APIs for content, and custom registries of your own.
- **Networking:** networking channels for mods.
- **Data-driven content:** blocks, items, game modes and enchantment effects defined in JSON.

### An engine-level rewrite
- **Larger worlds.** A 64-bit, cubic world engine, so worlds can be far taller and deeper than
  vanilla.
- **Rendering:** a Vulkan renderer, with OpenGL as a fallback.
- **Terrain:** new terrain generation and biomes.
- **New content:** new materials, enchantments, 3D audio and proximity voice chat.

### Its own updates
Each ABNW release is a new version of ABNW, published on this repository's
[Releases](../../releases) page and installed through the ABNW Launcher.

---

## How ABNW is delivered: patches, never the game

ABNW releases **do not contain Minecraft**, in whole or in part. Each release publishes a
**binary delta**: an `.xdelta` file in the standard VCDIFF format that describes only the
difference between the original Minecraft jar and the ABNW jar. A delta cannot be used on its
own; it can only be applied to a genuine copy of the game.

When a player installs ABNW:

1. **They sign in** with their own Microsoft account. The launcher confirms they own Minecraft:
   Java Edition, or have PC Game Pass, which includes it.
2. **The original Minecraft files come straight from Mojang's official servers**, the same
   downloads the official launcher uses.
3. **The ABNW patch is downloaded** from this repository and applied to that jar on the player's
   own computer.
4. **Every step is verified with SHA-256.**
   - The Minecraft jar must be exactly the one the patch was built for.
   - The patch must be exactly the one published here.
   - The rebuilt jar must match the published hash before it is used.

Each release has three files:

| File | What it is |
|---|---|
| `abnw-<version>-mc<minecraft>.xdelta` | The patch |
| `abnw-<version>-mc<minecraft>.json` | Sizes and SHA-256 hashes of the patch, the original jar and the result |
| `org.teamzetaverse.abnw.libraries.json` | Open-source libraries the release needs, downloaded from their public Maven repositories |

[`abnw-versions.json`](abnw-versions.json) lists every release.

---

## The ABNW Launcher and Microsoft sign-in

The ABNW Launcher is the only way to install and play ABNW. Its Microsoft (Azure) application is
used **only to sign players in to Minecraft**, the same way every Minecraft launcher does.

- **Sign-in method:** the Microsoft device code flow, with the scopes
  `XboxLive.signin offline_access`. The player enters a code at microsoft.com/link, so the
  launcher never sees or handles their password.
- **What it requests:** a Minecraft access token (Microsoft → Xbox Live → XSTS → Minecraft
  services), the account's Minecraft entitlements, and its Minecraft profile (name and UUID).
- **Why:**
  - to confirm the player owns Minecraft: Java Edition or has PC Game Pass;
  - to download the game from Mojang;
  - to start the game signed in as that player, so they can join servers normally.
- **Ownership is required.** There is no offline mode, no demo bypass and no way to play without
  owning the game.
- **Data handling:**
  - Tokens are stored only on the player's own computer, to keep them signed in.
  - They are sent only to Microsoft, Xbox Live and Mojang's own services.
  - ABNW does not collect, sell or share account data, and has no analytics or telemetry
    servers.

---

## Minecraft EULA and Usage Guidelines

ABNW is built to respect the
[Minecraft EULA](https://www.minecraft.net/eula) and
[Minecraft Usage Guidelines](https://www.minecraft.net/usage-guidelines):

- **No redistribution.** No Minecraft code or assets are distributed, modified or otherwise;
  only patches are.
- **Genuine copies only.** Players must own Minecraft: Java Edition (or PC Game Pass). The game
  files come from Mojang.
- **No confusion with official products.** ABNW has its own name and branding and is clearly
  marked as unofficial.
- **Original work.** Content added by ABNW, such as textures, models and code, is ABNW's own.

---

## Contact

- **Project:** A Brand New World (ABNW)
- **Repository:** https://github.com/blazexpsn/Minecraft-ABNW
- **Contact:** *<your email address>*

---

Minecraft is a trademark of Mojang Synergies AB. ABNW is not affiliated with, endorsed by or
sponsored by Mojang or Microsoft.
