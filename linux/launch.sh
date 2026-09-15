#!/usr/bin/env sh
set -eu

HERE="$(cd "$(dirname "$0")" && pwd)"
REQUIRED=21

JAR="$(ls "$HERE"/abnw-launcher-*.jar 2>/dev/null | sort | tail -n 1 || true)"
if [ -z "$JAR" ] || [ ! -f "$JAR" ]; then
    echo "Could not find abnw-launcher-*.jar next to launch.sh." >&2
    exit 1
fi

java_major() {
    "$1" -XshowSettings:properties -version 2>&1 \
        | sed -n 's/^ *java\.specification\.version = *//p' \
        | head -n 1 \
        | sed 's/^1\.//; s/\..*//'
}

usable() {
    [ -n "$1" ] && [ -x "$1" ] || return 1
    major="$(java_major "$1" || true)"
    case "$major" in
        ''|*[!0-9]*) return 1 ;;
    esac
    [ "$major" -ge "$REQUIRED" ]
}

JAVA=""
for candidate in \
    "${ABNW_JAVA:-}" \
    "$HERE/runtime/bin/java" \
    "${JAVA_HOME:+$JAVA_HOME/bin/java}" \
    "$(command -v java 2>/dev/null || true)"; do
    if usable "$candidate"; then
        JAVA="$candidate"
        break
    fi
done

if [ -z "$JAVA" ]; then
    for candidate in /usr/lib/jvm/*/bin/java /usr/lib64/jvm/*/bin/java /opt/*/bin/java; do
        if usable "$candidate"; then
            JAVA="$candidate"
            break
        fi
    done
fi

if [ -z "$JAVA" ]; then
    echo "The ABNW Launcher needs Java $REQUIRED or newer, and none was found." >&2
    echo "Run ./install-java.sh to install it for your distribution, then run ./launch.sh again." >&2
    exit 1
fi

cd "$HERE"

X11_MARKER="$HERE/.use-x11"
WAYLAND_SESSION=false
if [ -n "${WAYLAND_DISPLAY:-}" ] || [ "${XDG_SESSION_TYPE:-}" = "wayland" ]; then
    WAYLAND_SESSION=true
fi

if [ -n "${ABNW_WINDOW_PLATFORM:-}" ] || [ "$WAYLAND_SESSION" = false ] || [ -z "${DISPLAY:-}" ]; then
    exec "$JAVA" ${ABNW_JAVA_OPTS:-} -jar "$JAR" "$@"
fi

if [ -f "$X11_MARKER" ]; then
    ABNW_WINDOW_PLATFORM=x11 exec "$JAVA" ${ABNW_JAVA_OPTS:-} -jar "$JAR" "$@"
fi

STARTED="$(date +%s)"
set +e
"$JAVA" ${ABNW_JAVA_OPTS:-} -jar "$JAR" "$@"
STATUS=$?
set -e
ELAPSED=$(( $(date +%s) - STARTED ))

case "$STATUS" in
    134|135|136|139)
        if [ "$ELAPSED" -lt 30 ]; then
            echo "The launcher crashed on Wayland; restarting it with X11." >&2
            echo "X11 will be used from now on. Delete $X11_MARKER to try Wayland again." >&2
            touch "$X11_MARKER" 2>/dev/null || true
            ABNW_WINDOW_PLATFORM=x11 exec "$JAVA" ${ABNW_JAVA_OPTS:-} -jar "$JAR" "$@"
        fi
        ;;
esac
exit "$STATUS"
