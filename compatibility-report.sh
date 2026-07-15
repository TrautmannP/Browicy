#!/usr/bin/env sh

set -u

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$SCRIPT_DIR" || exit 1

exec "$SCRIPT_DIR/mvn-graal.sh" -Pcompatibility-report -pl acid3-tests -am verify "$@"
