#!/bin/sh
# Brings the website in /var/www/materialdrain up to the site branch on GitHub, which the "Website" workflow rebuilds
# on every change to website/ or docs/, and saves the app's releases for the download page. Run every few minutes by
# materialdrain-site-sync.timer, in the sandbox its service sets up. Only reads from GitHub: nothing on GitHub can
# reach this server.
set -eu

SITE=/var/www/materialdrain
# Apart from the site's checkout, which git resets and cleans; nginx serves it as /releases.json
RELEASES=/var/www/materialdrain-releases/releases.json

# The releases, as GitHub lists them. The file is written on every check, so its time (nginx sends it as Last-Modified)
# is when the server last asked GitHub, which the download page shows. A failed check keeps the last good list, and
# the old time with it
if curl --fail --silent --show-error --compressed --max-time 30 \
    --header "Accept: application/vnd.github+json" \
    --output "$RELEASES.new" "https://api.github.com/repos/fidesosu/Materialdrain/releases?per_page=30"; then
    mv "$RELEASES.new" "$RELEASES"
    releases_failed=false
else
    rm -f "$RELEASES.new"
    echo "Couldn't read the releases from GitHub" >&2
    releases_failed=true
fi

cd "$SITE"
git fetch --quiet --depth 1 origin site

# Each build is a single new commit, so a different commit means a new build
if [ "$(git rev-parse HEAD)" != "$(git rev-parse FETCH_HEAD)" ]; then
    git reset --quiet --hard FETCH_HEAD
    git clean -fdq
    # The replaced builds are unreachable now: drop them, so the folder doesn't grow with every update
    git reflog expire --expire=now --all
    git gc --quiet --prune=now
    echo "Updated the website: $(git log -1 --format=%s)"
fi

# The site is up to date either way; the service still fails, so a releases check that didn't work shows in its status
if [ "$releases_failed" = true ]; then exit 1; fi
