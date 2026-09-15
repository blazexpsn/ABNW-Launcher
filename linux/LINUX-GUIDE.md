# ABNW Launcher on Linux

This folder has everything you need to run the ABNW Launcher on Linux.

| File | What it does |
| --- | --- |
| `abnw-launcher-<version>.jar` | The launcher itself |
| `launch.sh` | Starts the launcher |
| `install-java.sh` | Installs Java for your distribution |
| `java-installers/` | The per-distribution install scripts that `install-java.sh` picks from |

## Quick start

Open a terminal in this folder and run:

```sh
./install-java.sh
./launch.sh
```

You only need `install-java.sh` once. After that, `./launch.sh` is all you need.

If your file manager unzipped the scripts without permission to run them, run this first:

```sh
chmod +x launch.sh install-java.sh java-installers/*.sh
```

## 1. Unzip

```sh
unzip abnw-launcher-<version>-linux.zip
cd abnw-launcher-<version>
```

Put the folder wherever you like, for example `~/Games/abnw-launcher`. Keep the files together.

## 2. Install Java

The launcher needs **Java 21 or newer**. If you already have it, skip to step 3. You can check with:

```sh
java -version
```

Otherwise run:

```sh
./install-java.sh
```

It reads `/etc/os-release` and uses your package manager. It asks for your password through `sudo`.

| Distribution family | Package manager | Package installed |
| --- | --- | --- |
| Debian, Ubuntu, Linux Mint, Pop!_OS, elementary, Zorin | apt | `openjdk-25-jre` or `openjdk-21-jre` |
| Fedora, RHEL, Rocky, AlmaLinux, Nobara | dnf / yum | `java-25-openjdk` or `java-21-openjdk` |
| Arch, Manjaro, EndeavourOS, Garuda, CachyOS | pacman | `jre21-openjdk` or `jre-openjdk` |
| openSUSE Leap, Tumbleweed | zypper | `java-25-openjdk` or `java-21-openjdk` |

On Arch-based systems pacman installs Java together with a full system upgrade, because Arch does not support partial upgrades. Pacman asks you to confirm first.

### Portable Java (no sudo)

If your distribution is not listed, or it does not package Java 21 (Debian 12, for example), the installer falls back to a portable Java. You can also choose it yourself:

```sh
./install-java.sh --portable
```

This downloads the Eclipse Temurin 21 JRE from Adoptium, checks its SHA-256 checksum, and unpacks it into a `runtime/` folder here. Nothing is installed system-wide and no password is needed. It works on x86_64 and ARM64 and needs `curl` or `wget`, plus `tar` and `sha256sum`.

To remove it, delete the `runtime/` folder.

## 3. Launch

```sh
./launch.sh
```

`launch.sh` uses the first Java 21+ it finds, in this order:

1. `ABNW_JAVA`, if you set it
2. `runtime/bin/java` from the portable install
3. `$JAVA_HOME/bin/java`
4. `java` on your `PATH`
5. Any Java in `/usr/lib/jvm`, `/usr/lib64/jvm` or `/opt`

The launcher downloads the Java version Minecraft itself needs when you play, so you do not have to install that separately.

### Options

Use a specific Java:

```sh
ABNW_JAVA=/usr/lib/jvm/java-21-openjdk/bin/java ./launch.sh
```

Pass extra JVM options to the launcher:

```sh
ABNW_JAVA_OPTS="-Xmx512m" ./launch.sh
```

## Desktop shortcut

To add the launcher to your application menu, create `~/.local/share/applications/abnw-launcher.desktop`. Replace `/path/to/abnw-launcher` with this folder's full path:

```ini
[Desktop Entry]
Type=Application
Name=ABNW Launcher
Exec=/path/to/abnw-launcher/launch.sh
Path=/path/to/abnw-launcher
Terminal=false
Categories=Game;
```

## Updating

Unzip the new version, then copy over the `runtime/` folder if you used portable Java. Your instances, accounts and settings are stored in `~/.local/share/abnwlauncher` (or `$XDG_DATA_HOME/abnwlauncher`), not in this folder, so they carry over.

## Troubleshooting

**"The ABNW Launcher needs Java 21 or newer, and none was found."**
Run `./install-java.sh`, or `./install-java.sh --portable` if that fails.

**"Permission denied" when running a script**
Run `chmod +x launch.sh install-java.sh java-installers/*.sh`, or start the script with `sh`, for example `sh launch.sh`.

**The window does not open, or you see GLFW / OpenGL errors**
The launcher needs OpenGL 3.2 or newer. Install your graphics drivers (Mesa, or NVIDIA's proprietary driver), and make sure you are running a desktop session, not a plain TTY or SSH.

**Signing in keeps asking again**
The launcher stores sign-in tokens in your keyring through `secret-tool`. Install `libsecret-tools` (Debian/Ubuntu) or `libsecret` (Fedora/Arch/openSUSE) and make sure a keyring such as GNOME Keyring or KWallet is running. Without one, the tokens are kept in a file only your user can read.

**Wayland**
On a Wayland desktop the launcher opens a native Wayland window. If that window cannot be created, the launcher retries with X11 (through XWayland). If the launcher crashes within its first 30 seconds on Wayland, `launch.sh` restarts it with X11 and creates a `.use-x11` file so later launches go straight to X11. Delete `.use-x11` to try Wayland again.

If the window opens but flickers, has no title bar, or misbehaves, force X11 yourself:

```sh
ABNW_WINDOW_PLATFORM=x11 ./launch.sh
```

`ABNW_WINDOW_PLATFORM=wayland` forces Wayland instead. Setting either one turns off the automatic crash restart.

## Help

Email ABrandNewWorldMC@outlook.com, or open an issue on https://github.com/blazexpsn/ABNW-Launcher.
