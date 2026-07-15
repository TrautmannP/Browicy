

set -u

if [ -n "${JAVA_HOME:-}" ]; then
    JAVA_BIN="$JAVA_HOME/bin/java"
    if [ ! -x "$JAVA_BIN" ]; then
        echo "Java was not found at $JAVA_BIN." >&2
        echo "Set JAVA_HOME to a valid GraalVM JDK or unset it to use java from PATH." >&2
        exit 1
    fi
    PATH="$JAVA_HOME/bin:$PATH"
    export PATH
else
    JAVA_BIN=$(command -v java 2>/dev/null || true)
    if [ -z "$JAVA_BIN" ]; then
        echo "Java was not found. Install a GraalVM JDK or set JAVA_HOME." >&2
        exit 1
    fi
fi

JAVA_FLAGS="--enable-native-access=ALL-UNNAMED"
if "$JAVA_BIN" --sun-misc-unsafe-memory-access=allow -version >/dev/null 2>&1; then
    JAVA_FLAGS="$JAVA_FLAGS --sun-misc-unsafe-memory-access=allow"
fi

MAVEN_OPTS="$JAVA_FLAGS${MAVEN_OPTS:+ $MAVEN_OPTS}"
export MAVEN_OPTS

exec mvn "$@"
