"""
What the documentation adds to each page as it's built (see mkdocs.yml, hooks):

- The line at the end of a page: when it was last changed, and which version of the app was the newest release then,
  so a reader can tell whether it was written before or after the version they have. Both come from git: the page's
  last commit in this branch, and the app's release tags (v1.5.20261014.1558), which are on the commit each release
  was built from. Shown by overrides/partials/source-file.html.
- :nightly: in a page becomes a small "Nightly" label, for what only the development build has so far. When those
  changes reach a stable release, the labels are taken out (grep for :nightly:).
- In a table, a name in code can break over lines after a _ . / or - and between the words of a camelCase one, so on
  a phone a narrow column breaks "pixeldrain_secure_prefs" as "pixeldrain_" "secure_prefs", not wherever the cell ends
  (extra.css keeps it from breaking anywhere else).

The site's workflow checks out the whole history (fetch-depth: 0) for this; without git, or for a page that's never
been committed, the line says so instead of a date.
"""
import os
import re
import subprocess
from datetime import datetime, timezone

NIGHTLY_LABEL = '<span class="nightly" title="Only in the Nightly build so far, not in a stable release yet">Nightly</span>'

# The app's releases, newest first: (the time of the commit it was built from, its version)
_releases: list[tuple[int, str]] = []
_root = "."


def _git(*args: str) -> str:
    try:
        return subprocess.run(["git", "-C", _root, *args], capture_output=True, text=True, check=True).stdout
    except (OSError, subprocess.CalledProcessError):
        return ""


def on_config(config):
    global _root
    _root = os.path.dirname(os.path.abspath(config.config_file_path))
    _releases.clear()
    tags = _git("for-each-ref", "--sort=-creatordate", "--format=%(creatordate:unix) %(refname:short)", "refs/tags/v*")
    for line in tags.splitlines():
        seconds, tag = line.split(" ", 1)
        _releases.append((int(seconds), tag.removeprefix("v")))
    return config


def _release_at(seconds: int) -> str | None:
    """The newest release made at or before [seconds], or None from before the first one."""
    return next((version for made, version in _releases if made <= seconds), None)


def _date(moment: datetime) -> str:
    return f"{moment.day} {moment.strftime('%B %Y')}"


def on_page_markdown(markdown, page, config, files):
    path = page.file.abs_src_path
    changed = _git("status", "--porcelain", "--", path).strip()
    last = _git("log", "-1", "--follow", "--format=%ct", "--", path).strip()
    if changed or not last:
        # Being written: what it will be once it's committed today
        moment = datetime.now(timezone.utc)
        page.meta["page_info"] = {"date": _date(moment), "iso": moment.date().isoformat(), "uncommitted": True,
                                  "release": _releases[0][1] if _releases else None}
    else:
        moment = datetime.fromtimestamp(int(last), timezone.utc)
        page.meta["page_info"] = {"date": _date(moment), "iso": moment.date().isoformat(), "uncommitted": False,
                                  "release": _release_at(int(last))}
    page.meta["page_info"]["nightly"] = ":nightly:" in markdown
    return markdown.replace(":nightly:", NIGHTLY_LABEL)


_TABLE = re.compile(r"<table>.*?</table>", re.DOTALL)
_CODE = re.compile(r"<code>([^<]*)</code>")
# After a separator, or between a small letter or digit and a capital
_BREAK = re.compile(r"(?<=[_./-])(?=[^_./-])|(?<=[a-z0-9])(?=[A-Z])")


def _breakable(match: re.Match) -> str:
    return "<code>" + _BREAK.sub("<wbr>", match.group(1)) + "</code>"


def on_page_content(html, page, config, files):
    return _TABLE.sub(lambda table: _CODE.sub(_breakable, table.group(0)), html)
