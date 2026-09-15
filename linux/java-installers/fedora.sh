#!/usr/bin/env sh
set -eu

HERE="$(cd "$(dirname "$0")" && pwd)"
SUDO=""
[ "$(id -u)" -eq 0 ] || SUDO="sudo"

if command -v dnf >/dev/null 2>&1; then
    PM=dnf
elif command -v yum >/dev/null 2>&1; then
    PM=yum
else
    echo "Neither dnf nor yum was found."
    exec sh "$HERE/portable.sh"
fi

echo "Installing Java for Fedora, RHEL and derivatives with $PM."
for package in java-25-openjdk java-21-openjdk; do
    if $PM -q info "$package" >/dev/null 2>&1; then
        $SUDO $PM install -y "$package"
        echo "Installed $package. Run ./launch.sh to start the ABNW Launcher."
        exit 0
    fi
done

echo "Your release does not package Java 21 or newer."
echo "Installing a portable Java next to the launcher instead."
exec sh "$HERE/portable.sh"
