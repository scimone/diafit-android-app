#!/usr/bin/env bash
# Lists user-visible string literals that are still hard-coded in Kotlin (see docs/i18n-plan.md).
# Usage: scripts/find-hardcoded-strings.sh [package-dir]   (counts per package, then -v for lines)
cd "$(dirname "$0")/../app/src/main/java/uk/scimone/diafit" || exit 1
pat='(Text\(|text = |title = |label = |contentDescription = |placeholder = |showSnackbar\(|headline = |supportingText = )\s*"[A-Za-z]'
if [ "$1" = "-v" ]; then grep -rnE "$pat" --include=*.kt . ; exit 0; fi
dir="${1:-.}"
grep -rcE "$pat" --include=*.kt "$dir" | awk -F: '{split($1,a,"/"); n[a[2]]+=$2} END{for(k in n) if(n[k]>0) printf "%5d %s\n", n[k], k}' | sort -rn
