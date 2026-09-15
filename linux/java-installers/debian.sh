#!/usr/bin/env sh
set -eu

HERE="$(cd "$(dirname "$0")" && pwd)"
SUDO=""
[ "$(id -u)" -eq 0 ] || SUDO="sudo"

echo "Installing Java for Debian, Ubuntu and derivatives with apt."
$SUDO apt-get update

for package in openjdk-25-jre openjdk-21-jre; do
    if apt-cache policy "$package" 2>/dev/null | grep -q "Candidate: [^(]"; then
        $SUDO apt-get install -y "$package"
        echo "Installed $package. Run ./launch.sh to start the ABNW Launcher."
        exit 0
    fi
done

echo "Your release does not package Java 21 or newer."
echo "Installing a portable Java next to the launcher instead."
exec sh "$HERE/portable.sh"
