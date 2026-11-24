#!/usr/bin/env bash
# Fails when a Termux RUN_COMMAND extra key is written outside the library.
#
# The point of the library is that the intent contract has one owner. Hosts
# may mention the action or the permission, but the extra-key prefix must not
# be re-typed in Bridge.java, the Nix build, docs, or another module.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

readonly allowed_prefix="termux-run-command/"
readonly self="scripts/check-run-command-constants.sh"
readonly pattern='com\.termux\.RUN_COMMAND_'

violations=0
while IFS= read -r -d '' file; do
    case "$file" in
        "$allowed_prefix"*|"$self") continue ;;
    esac
    [ -f "$file" ] || continue
    if grep -I -nE "$pattern" "$file" >/dev/null 2>&1; then
        echo "error: $file re-implements a Termux RUN_COMMAND extra key:" >&2
        grep -I -nE "$pattern" "$file" >&2 || true
        violations=$((violations + 1))
    fi
done < <(git ls-files -z --cached --others --exclude-standard)

if [ "$violations" -ne 0 ]; then
    echo >&2
    echo "Keep those keys in termux-run-command/src/main/java/io/github/mbanucu/termux/TermuxIntentSpec.java." >&2
    exit 1
fi

echo "ok: no Termux RUN_COMMAND extra keys outside $allowed_prefix"
