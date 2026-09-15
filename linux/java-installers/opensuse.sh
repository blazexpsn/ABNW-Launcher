#!/usr/bin/env sh
set -eu

HERE="$(cd "$(dirname "$0")" && pwd)"
if [ "$(id -u)" -eq 0 ]; then
    SUDO=""
elif command -v sudo >/dev/null 2>&1; then
    SUDO="sudo"
else
    echo "sudo is not available and you are not root; installing a portable Java next to the launcher instead."
    exec sh "$HERE/portable.sh"
fi

echo "Installing Java for openSUSE with zypper."
$SUDO zypper --non-interactive refresh

for package in java-25-openjdk java-21-openjdk; do
    if zypper --non-interactive info "$package" 2>/dev/null | grep -q "^Name *: *$package"; then
        $SUDO zypper --non-interactive install "$package"
        echo "Installed $package. Run ./launch.sh to start the ABNW Launcher."
        exit 0
    fi
done

echo "Your release does not package Java 21 or newer."
echo "Installing a portable Java next to the launcher instead."
exec sh "$HERE/portable.sh"
