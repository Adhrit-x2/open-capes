#!/bin/bash
# Runs the in-game test for each Minecraft version and prints PASS/FAIL per version.
#
#   tools/test-versions.sh              # every version in versions/
#   tools/test-versions.sh 1.21.5 26.3  # just these
#
# 1.21.4+ use Fabric's client game test (src/gametest); older versions use the smoke test
# (src/smoketest). The log is checked for failures because some versions' test runner exits
# successfully even when a test fails. Logs go to build/test-logs/<version>.log.
cd "$(dirname "$0")/.." || exit 1
mkdir -p build/test-logs

if [ $# -eq 0 ]; then
    set -- $(ls versions | sort -t. -k1,1n -k2,2n -k3,3n)
fi

failed=0
for v in "$@"; do
    log="build/test-logs/$v.log"
    if [ "$(printf '%s\n' 1.21.4 "$v" | sort -V | head -1)" = 1.21.4 ]; then
        ./gradlew -Pmc="$v" ":$v:runClientGameTest" --console=plain > "$log" 2>&1
        if grep -q 'BUILD SUCCESSFUL' "$log" && ! grep -qE 'Client gametests failed|AssertionError|Game crashed' "$log"; then
            result=PASS
        else
            result=FAIL
        fi
    else
        ./gradlew -Pmc="$v" ":$v:runSmokeTest" --console=plain > "$log" 2>&1
        if grep -q 'CAPECREATOR SMOKE: PASS' "$log"; then result=PASS; else result=FAIL; fi
    fi
    if [ "$result" = PASS ]; then
        echo "$v: PASS"
    else
        failed=1
        echo "$v: FAIL (see $log)"
        grep -m3 -E 'AssertionError|CAPECREATOR SMOKE|What went wrong|Exception' "$log" | cut -c1-200 | sed 's/^/    /'
    fi
done
exit $failed
