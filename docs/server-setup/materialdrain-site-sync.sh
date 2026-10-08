#!/bin/sh
# Brings the website in /var/www/materialdrain up to the site branch on GitHub, which the "Website" workflow rebuilds
# on every change to website/ or docs/. Run every few minutes by materialdrain-site-sync.timer, in the sandbox its
# service sets up. Only reads from GitHub: nothing on GitHub can reach this server.
set -eu

SITE=/var/www/materialdrain

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
