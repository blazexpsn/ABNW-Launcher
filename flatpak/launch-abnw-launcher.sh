#!/bin/sh
set -eu

JAVA="${JAVA_HOME:-/app/jre}/bin/java"
if [ ! -x "$JAVA" ]; then
    echo "ABNW Launcher could not find its bundled Java runtime." >&2
    exit 1
fi

exec "$JAVA" \
    -Dfile.encoding=UTF-8 \
    -Dstdout.encoding=UTF-8 \
    -Dstderr.encoding=UTF-8 \
    -jar /app/share/abnw-launcher/launcher.jar "$@"
