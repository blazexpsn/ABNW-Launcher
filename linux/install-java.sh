#!/usr/bin/env sh
set -eu

HERE="$(cd "$(dirname "$0")" && pwd)"
SCRIPTS="$HERE/java-installers"

if [ "${1:-}" = "--portable" ]; then
    exec sh "$SCRIPTS/portable.sh"
fi

if [ ! -r /etc/os-release ]; then
    echo "Could not read /etc/os-release to detect your distribution."
    echo "Installing a portable Java next to the launcher instead."
    exec sh "$SCRIPTS/portable.sh"
fi

. /etc/os-release
IDS="${ID:-} ${ID_LIKE:-}"

for id in $IDS; do
    case "$id" in
        debian|ubuntu|linuxmint|pop|elementary|zorin|kali|raspbian|neon)
            exec sh "$SCRIPTS/debian.sh" ;;
        fedora|rhel|centos|rocky|almalinux|ol|nobara|ultramarine)
            exec sh "$SCRIPTS/fedora.sh" ;;
        arch|manjaro|endeavouros|garuda|cachyos|artix)
            exec sh "$SCRIPTS/arch.sh" ;;
        opensuse|opensuse-leap|opensuse-tumbleweed|suse|sles)
            exec sh "$SCRIPTS/opensuse.sh" ;;
    esac
done

echo "${PRETTY_NAME:-This distribution} is not one the installer knows."
echo "Installing a portable Java next to the launcher instead."
exec sh "$SCRIPTS/portable.sh"
