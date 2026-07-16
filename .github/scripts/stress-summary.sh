#!/usr/bin/env bash
# Renders build/reports/stress/stress-report.txt as GitHub-flavored markdown,
# for the Actions job summary ($GITHUB_STEP_SUMMARY). Usage:
#   stress-summary.sh build/reports/stress/stress-report.txt >> "$GITHUB_STEP_SUMMARY"
set -euo pipefail

report="${1:?usage: stress-summary.sh <stress-report.txt>}"

if [[ ! -f "$report" ]]; then
    echo "## 💥 Stress test report not found"
    echo
    echo "The simulation crashed before writing \`$report\` — check the test log in the job output."
    exit 0
fi

# grabs the value following "key=" on the first line that contains it (up to the next space)
val() { grep -m1 -o "$1=[^ ]*" "$report" | head -n1 | cut -d= -f2; }

verdict=$(grep -o 'VERDICT: [A-Z]*' "$report" | awk '{print $2}')
if [[ "$verdict" == "PASS" ]]; then
    echo "# ✅ Storage stress simulation — PASS"
else
    echo "# ❌ Storage stress simulation — ${verdict:-UNKNOWN}"
fi
echo

echo "| | |"
echo "|---|---|"
echo "| Throughput | **$(val throughput) ops/s** |"
echo "| Sessions (join→play→quit) | $(grep -m1 -o 'quit)=[0-9]*' "$report" | cut -d= -f2) |"
echo "| Storage ops | $(val storageOps) (admin: $(val adminOps)) |"
echo "| Setup | $(val 'players(universe)') players · $(val workerThreads) worker + $(val adminThreads) admin threads · $(val run) |"
echo "| Latency percentiles | p50 $(val p50) · p95 $(val p95) · p99 $(val p99) |"
echo "| Heap | baseline $(val baseline) MB → peak $(val peak) MB → final $(val 'final(after gc)') MB (growth $(val growth) MB) |"
echo "| Autosave | flushed $(val flushedRows) rows · evicted $(val evictedOwners) owners · $(val finalDbChests) chests in DB |"
echo "| Failures / deadlock | $(grep -m1 'storage-op failures' "$report" | awk -F: '{gsub(/ /,"",$2); print $2}') / $(grep -m1 'deadlock observed' "$report" | awk -F: '{gsub(/ /,"",$2); print $2}') |"
echo "| Leak check (all must be 0) | $(grep -m1 'resident=' "$report" | sed 's/^ *//') |"
echo

echo "## Per-operation latency"
echo
echo "| Operation | Count | Avg (ms) | Max (ms) |"
echo "|---|---:|---:|---:|"
awk '
    /-- per-op latency/ { in_ops = 1; next }
    in_ops && /^ *$/    { in_ops = 0 }
    in_ops && /n=/ {
        gsub(/n=|avg=|max=/, "")
        printf "| `%s` | %s | %s | %s |\n", $1, $2, $3, $4
    }
' "$report"
echo

echo "<details><summary>Raw report</summary>"
echo
echo '```'
cat "$report"
echo '```'
echo
echo "</details>"
echo
echo "> ⚠️ GitHub runners are shared VMs, so **throughput and latency vary run to run** — treat them as indicative only."
echo "> The stable signals are the verdict, the failure/deadlock counts, and the leak check."
