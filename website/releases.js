// The download page's builds, from the repository's GitHub releases: the newest stable release (from main), the
// development build (the dev-build pre-release, from dev), and the older stable releases. The app's build workflow
// publishes them; this only lists them, and every download comes straight from GitHub.
// The server reads the list from GitHub every 5 minutes and serves it as /releases.json, so visitors don't use up
// GitHub's 60 requests an hour, and the page can say when the server last checked. When that file isn't there (a
// local preview) the page asks GitHub itself; when neither answers, it keeps its links to the releases page.
(() => {
  const REPO = "fidesosu/Materialdrain";
  const DEV_TAG = "dev-build";
  const OLDER_SHOWN = 8;
  // How often an open page looks for a newer list: the server's own checks are 5 minutes apart
  const RECHECK_MILLIS = 60 * 1000;
  // Several missed checks in a row: the page says the newest builds may be missing
  const STALE_MILLIS = 20 * 60 * 1000;

  const apkOf = (release) => (release.assets || []).find((asset) => asset.name.toLowerCase().endsWith(".apk"));

  // "Materialdrain-1.4.20261009.1530.apk" → "1.4.20261009.1530"; the releases before the build workflow named their
  // APKs differently, so for those it's the tag without its "v" ("v1.3.0" → "1.3.0")
  const versionOf = (release) => {
    const apk = apkOf(release);
    const fromFile = apk && apk.name.match(/^Materialdrain-(.+)\.apk$/i);
    return fromFile ? fromFile[1] : (release.tag_name || release.name || "").replace(/^v(?=\d)/, "");
  };

  // The APK's upload, not the release's: the dev-build release is published once and only edited after that, so its
  // own date stays the first build's, while its APK is uploaded again by every push to dev
  const dateOf = (release) => {
    const apk = apkOf(release);
    return new Date(apk.updated_at || apk.created_at || release.published_at)
      .toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
  };

  const sizeOf = (asset) => `${(asset.size / (1024 * 1024)).toFixed(1)} MB`;

  // The hex digits of GitHub's "sha256:94437ae2…" for an asset; null for uploads from before GitHub recorded it
  const sha256Of = (asset) => (asset.digest || "").match(/^sha256:([0-9a-f]{64})$/)?.[1] ?? null;

  const notesLink = (release) => {
    const link = document.createElement("a");
    link.href = release.html_url;
    link.textContent = "What's new";
    return link;
  };

  // The release notes, and the APK's SHA-256 with a button to copy it, to check the download against
  function fillExtra(extra, release, apk) {
    extra.replaceChildren(notesLink(release));
    const sha256 = sha256Of(apk);
    if (!sha256) return;
    const hash = document.createElement("code");
    hash.textContent = `${sha256.slice(0, 8)}…${sha256.slice(-8)}`;
    hash.title = sha256;
    const copy = document.createElement("button");
    copy.type = "button";
    copy.className = "copy";
    copy.textContent = "Copy";
    copy.setAttribute("aria-label", "Copy the SHA-256 checksum");
    copy.addEventListener("click", async () => {
      try {
        await navigator.clipboard.writeText(sha256);
        copy.textContent = "Copied";
      } catch (_) {
        copy.textContent = "Couldn't copy";
      }
      setTimeout(() => { copy.textContent = "Copy"; }, 2000);
    });
    extra.append(" · SHA-256 ", hash, " ", copy);
  }

  function fillCard(id, release) {
    const build = document.getElementById(id);
    const card = build.querySelector(".card");
    const field = (name) => build.querySelector(`[data-field="${name}"]`);
    const apk = release && apkOf(release);
    card.classList.toggle("empty", !apk);
    if (!apk) {
      // Without its link the card can't be reached with the keyboard either
      card.removeAttribute("href");
      card.setAttribute("aria-disabled", "true");
      field("version").textContent = "Not published yet";
      field("meta").textContent = "";
      field("extra").replaceChildren();
      return;
    }
    card.href = apk.browser_download_url;
    card.removeAttribute("aria-disabled");
    field("version").textContent = versionOf(release);
    field("meta").textContent = `${dateOf(release)} · ${sizeOf(apk)}`;
    fillExtra(field("extra"), release, apk);
  }

  function fillOlder(releases) {
    const list = document.getElementById("older-list");
    list.replaceChildren();
    if (releases.length === 0) {
      const item = document.createElement("li");
      item.className = "version-meta";
      item.textContent = "None yet.";
      list.append(item);
      return;
    }
    for (const release of releases.slice(0, OLDER_SHOWN)) {
      const apk = apkOf(release);
      const item = document.createElement("li");
      const link = document.createElement("a");
      link.href = apk.browser_download_url;
      link.textContent = versionOf(release);
      const sha256 = sha256Of(apk);
      if (sha256) link.title = `SHA-256: ${sha256}`;
      // The version and its notes on one line, when it was made and how big it is on the next
      const head = document.createElement("span");
      head.className = "version-head";
      const notes = notesLink(release);
      notes.className = "small";
      head.append(link, notes);
      const meta = document.createElement("span");
      meta.className = "version-meta";
      meta.textContent = `${dateOf(release)} · ${sizeOf(apk)}`;
      item.append(head, meta);
      list.append(item);
    }
  }

  function show(releases) {
    const published = releases.filter((release) => !release.draft && apkOf(release));
    const stable = published.filter((release) => !release.prerelease && release.tag_name !== DEV_TAG);
    fillCard("stable", stable[0]);
    fillCard("dev", published.find((release) => release.tag_name === DEV_TAG));
    fillOlder(stable.slice(1));
  }

  // "Checked GitHub 2 min 5 s ago", counting up from when the list was read from GitHub: by the server, or by this page
  // when it had to ask GitHub itself
  let checkedAt = null;
  function tick() {
    if (checkedAt === null) return;
    const label = document.getElementById("releases-refreshed");
    const seconds = Math.max(0, Math.floor((Date.now() - checkedAt) / 1000));
    const h = Math.floor(seconds / 3600), m = Math.floor(seconds / 60) % 60, s = seconds % 60;
    const ago = seconds < 5 ? "just now"
      : seconds < 60 ? `${s} s ago`
      : seconds < 3600 ? `${m} min ${s} s ago`
      : `${h} h ${m} min ago`;
    const when = new Date(checkedAt).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
    label.textContent = `Checked GitHub ${ago}`;
    label.title = new Date(checkedAt).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "medium" });
    // The server checks every 5 minutes: this much older, its checks are failing (its journal says why). A list the
    // page read from GitHub itself is only as old as the visit
    document.getElementById("releases-status").textContent = !fromGitHub && Date.now() - checkedAt > STALE_MILLIS
      ? `The server hasn't reached GitHub since ${when}, so a newer build may be missing here: all of them are on GitHub.`
      : "";
  }

  // The releases and when they were read from GitHub. The server's copy is written on every check, so its
  // Last-Modified is when the server last asked; a failed check leaves the file, and its time, as they were
  async function load() {
    try {
      const response = await fetch("/releases.json", { cache: "no-cache" });
      const modified = Date.parse(response.headers.get("Last-Modified"));
      if (response.ok && !Number.isNaN(modified)) {
        return { at: modified, releases: await response.json(), fromServer: true };
      }
    } catch (_) { /* not served here: ask GitHub */ }
    const response = await fetch(`https://api.github.com/repos/${REPO}/releases?per_page=30`, {
      headers: { Accept: "application/vnd.github+json" },
    });
    if (!response.ok) throw new Error(`GitHub answered ${response.status}`);
    return { at: Date.now(), releases: await response.json(), fromServer: false };
  }

  // Again every minute while the page is open, and when it's shown again after being in the background; the list is
  // only drawn again when the server has checked since. A list this page read from GitHub itself isn't asked for again,
  // which would only use up the visitor's requests
  let loading = false;
  let fromGitHub = false;
  async function refresh() {
    if (loading || fromGitHub || document.hidden) return;
    loading = true;
    try {
      const { at, releases, fromServer } = await load();
      fromGitHub = !fromServer;
      if (at !== checkedAt) {
        show(releases);
        checkedAt = at;
      }
      tick();
    } catch (_) {
      // A list already shown stays; only the first load has nothing to fall back on but the plain links
      if (checkedAt === null) {
        document.getElementById("releases-status").textContent =
          "Couldn't load the builds from GitHub just now: the buttons open the releases page instead.";
      }
    } finally {
      loading = false;
    }
  }

  refresh();
  setInterval(tick, 1000);
  setInterval(refresh, RECHECK_MILLIS);
  document.addEventListener("visibilitychange", refresh);
})();
