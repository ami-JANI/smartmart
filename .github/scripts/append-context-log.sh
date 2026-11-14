#!/bin/sh
# Appends one Change log entry to context.md for the commits in a push.
# Usage: append-context-log.sh <before-sha> <after-sha> <pusher>
# Prints nothing and changes nothing when there is no commit to log.

set -eu

before="$1"
after="$2"
pusher="$3"
file="context.md"
bot='github-actions[bot]'

# A new branch or a force push has no usable "before": log the last commit only.
if [ -z "$before" ] || [ "$before" = "0000000000000000000000000000000000000000" ] \
   || ! git cat-file -e "$before^{commit}" 2>/dev/null \
   || ! git merge-base --is-ancestor "$before" "$after"; then
  range="-1 $after"
else
  range="$before..$after"
fi

# Oldest first, skipping merges and this workflow's own commits.
commits=$(git rev-list --reverse --no-merges $range | while read -r sha; do
  [ "$(git log -1 --format=%an "$sha")" = "$bot" ] || echo "$sha"
done)

[ -n "$commits" ] || exit 0

{
  printf '\n### %s, pushed by %s\n\n' "$(date -u +%Y-%m-%d)" "$pusher"
  for sha in $commits; do
    subject=$(git log -1 --format=%s "$sha")
    author=$(git log -1 --format=%an "$sha")
    keys=$(git log -1 --format=%B "$sha" | grep -oE 'SCRUM-[0-9]+' | sort -u -t- -k2,2n | paste -sd, - | sed 's/,/, /g')
    breaking=""
    git log -1 --format=%B "$sha" | grep -Eq '^BREAKING[ -]CHANGE:' && breaking=" **(breaking)**"
    case "$subject" in *"!:"*) breaking=" **(breaking)**" ;; esac
    line="- \`$(git rev-parse --short "$sha")\` $subject ($author)"
    [ -z "$keys" ] || line="$line, $keys"
    printf '%s%s\n' "$line" "$breaking"
  done
} >> "$file"
