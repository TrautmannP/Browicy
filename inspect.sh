

set -u

if [ "$#" -eq 0 ]; then
    echo "Usage: ./inspect.sh URL [report.json | inspector options]" >&2
    exit 2
fi

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$SCRIPT_DIR" || exit 1

"$SCRIPT_DIR/mvn-graal.sh" -q -pl browser-cli -am package -DskipTests || exit $?

if [ -n "${JAVA_HOME:-}" ]; then
    JAVA_BIN="$JAVA_HOME/bin/java"
else
    JAVA_BIN=$(command -v java 2>/dev/null || true)
fi

if [ -z "${JAVA_BIN:-}" ] || [ ! -x "$JAVA_BIN" ]; then
    echo "Java was not found. Install a GraalVM JDK or set JAVA_HOME." >&2
    exit 1
fi

JAVA_UNSAFE_FLAG=""
if "$JAVA_BIN" --sun-misc-unsafe-memory-access=allow -version >/dev/null 2>&1; then
    JAVA_UNSAFE_FLAG="--sun-misc-unsafe-memory-access=allow"
fi

if [ "$#" -eq 1 ]; then
    exec "$JAVA_BIN" ${JAVA_UNSAFE_FLAG:+"$JAVA_UNSAFE_FLAG"} \
        -jar "$SCRIPT_DIR/browser-cli/target/browicy-inspect.jar" "$1"
fi

case "$2" in
    --*)
        exec "$JAVA_BIN" ${JAVA_UNSAFE_FLAG:+"$JAVA_UNSAFE_FLAG"} \
            -jar "$SCRIPT_DIR/browser-cli/target/browicy-inspect.jar" "$@"
        ;;
    *)
        exec "$JAVA_BIN" ${JAVA_UNSAFE_FLAG:+"$JAVA_UNSAFE_FLAG"} \
            -jar "$SCRIPT_DIR/browser-cli/target/browicy-inspect.jar" "$1" --output "$2"
        ;;
esac
