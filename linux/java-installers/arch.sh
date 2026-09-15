#!/usr/bin/env sh
set -eu

HERE="$(cd "$(dirname "$0")" && pwd)"
SUDO=""
[ "$(id -u)" -eq 0 ] || SUDO="sudo"

echo "Installing Java for Arch Linux and derivatives with pacman."
echo "pacman installs packages together with a full system upgrade, as Arch requires."

for package in jre21-openjdk jre-openjdk; do
    if pacman -Si "$package" >/dev/null 2>&1; then
        $SUDO pacman -Syu --needed "$package"
        echo "Installed $package. Run ./launch.sh to start the ABNW Launcher."
        exit 0
    fi
done

echo "Your mirrors do not package Java 21 or newer."
echo "Installing a portable Java next to the launcher instead."
exec sh "$HERE/portable.sh"
