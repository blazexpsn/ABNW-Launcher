#!/usr/bin/env sh
set -eu

HERE="$(cd "$(dirname "$0")/.." && pwd)"
RUNTIME="$HERE/runtime"
FEATURE=21

case "$(uname -m)" in
    x86_64|amd64) ARCH=x64 ;;
    aarch64|arm64) ARCH=aarch64 ;;
    *)
        echo "No portable Java is available for $(uname -m). Install Java $FEATURE or newer with your package manager." >&2
        exit 1 ;;
esac

if command -v curl >/dev/null 2>&1; then
    fetch() { curl -fsSL -o "$2" "$1"; }
    resolve() { curl -fsSI "$1" | sed -n 's/^[Ll]ocation: *//p' | head -n 1 | tr -d '\r'; }
elif command -v wget >/dev/null 2>&1; then
    fetch() { wget -q -O "$2" "$1"; }
    resolve() { wget -S --spider --max-redirect=0 "$1" 2>&1 | sed -n 's/^ *[Ll]ocation: *//p' | head -n 1 | sed 's/ \[following\]$//' | tr -d '\r'; }
else
    echo "curl or wget is needed to download Java." >&2
    exit 1
fi

if command -v sha256sum >/dev/null 2>&1; then
    sha256() { sha256sum "$1" | cut -d' ' -f1; }
elif command -v shasum >/dev/null 2>&1; then
    sha256() { shasum -a 256 "$1" | cut -d' ' -f1; }
else
    echo "sha256sum is needed to verify the download." >&2
    exit 1
fi

API="https://api.adoptium.net/v3/binary/latest/$FEATURE/ga/linux/$ARCH/jre/hotspot/normal/eclipse"
echo "Finding the latest Eclipse Temurin $FEATURE JRE for $ARCH."
URL="$(resolve "$API" || true)"
case "$URL" in
    https://*.tar.gz) ;;
    *)
        echo "Could not find a download link from Adoptium." >&2
        exit 1 ;;
esac

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT INT TERM

echo "Downloading $URL"
fetch "$URL" "$WORK/jre.tar.gz"
fetch "$URL.sha256.txt" "$WORK/jre.sha256.txt"

EXPECTED="$(cut -d' ' -f1 "$WORK/jre.sha256.txt")"
ACTUAL="$(sha256 "$WORK/jre.tar.gz")"
if [ -z "$EXPECTED" ] || [ "$EXPECTED" != "$ACTUAL" ]; then
    echo "Checksum mismatch: expected $EXPECTED, got $ACTUAL. Nothing was installed." >&2
    exit 1
fi

mkdir -p "$WORK/extract"
tar -xzf "$WORK/jre.tar.gz" -C "$WORK/extract" --strip-components=1
if [ ! -x "$WORK/extract/bin/java" ]; then
    echo "The download did not contain bin/java. Nothing was installed." >&2
    exit 1
fi

rm -rf "$RUNTIME"
mv "$WORK/extract" "$RUNTIME"
echo "Installed Java $FEATURE into $RUNTIME. Run ./launch.sh to start the ABNW Launcher."
