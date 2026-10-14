// The download page's builds, read from the repository's GitHub releases when the page opens: the newest stable
// release (from main), the development build (the dev-build pre-release, from dev), and the older stable releases.
// The app's build workflow publishes them; this only lists them, and every download comes straight from GitHub.
// When GitHub can't be reached (or its limit of requests is used up), the page keeps its links to the releases page.
(() => {
  const REPO = "fidesosu/Materialdrain";
  const DEV_TAG = "dev-build";
  const OLDER_SHOWN = 8;
  // GitHub allows a visitor 60 requests an hour: the list is kept for a few minutes rather than asked for on every visit
  const CACHE_KEY = "materialdrain-releases";
  const CACHE_MILLIS = 5 * 60 * 1000;

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

  function fillCard(id, release) {
    const card = document.getElementById(id);
    const field = (name) => card.querySelector(`[data-field="${name}"]`);
    const apk = release && apkOf(release);
    if (!apk) {
      card.classList.add("empty");
      field("version").textContent = "Not published yet";
      return;
    }
    card.href = apk.browser_download_url;
    card.setAttribute("download", "");
    field("version").textContent = versionOf(release);
    field("meta").textContent = `${dateOf(release)} · ${sizeOf(apk)}`;
  }

  function fillOlder(releases) {
    const list = document.getElementById("older-list");
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
      link.setAttribute("download", "");
      link.textContent = versionOf(release);
      const meta = document.createElement("span");
      meta.className = "version-meta";
      meta.textContent = `${dateOf(release)} · ${sizeOf(apk)}`;
      item.append(link, meta);
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

  // "Last refresh: 2 min 5 s ago", counting up from when the list was read from GitHub (a kept list's, not the visit's)
  function showRefreshed(at) {
    const label = document.getElementById("releases-refreshed");
    label.title = new Date(at).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "medium" });
    const tick = () => {
      const seconds = Math.max(0, Math.floor((Date.now() - at) / 1000));
      const h = Math.floor(seconds / 3600), m = Math.floor(seconds / 60) % 60, s = seconds % 60;
      const ago = seconds < 5 ? "just now"
        : seconds < 60 ? `${s} s ago`
        : seconds < 3600 ? `${m} min ${s} s ago`
        : `${h} h ${m} min ago`;
      label.textContent = `Last refresh: ${ago}`;
    };
    tick();
    setInterval(tick, 1000);
  }

  // The releases and when they were read from GitHub
  async function load() {
    try {
      const cached = JSON.parse(sessionStorage.getItem(CACHE_KEY) || "null");
      if (cached && Date.now() - cached.at < CACHE_MILLIS) return cached;
    } catch (_) { /* no cache to use */ }
    const response = await fetch(`https://api.github.com/repos/${REPO}/releases?per_page=30`, {
      headers: { Accept: "application/vnd.github+json" },
    });
    if (!response.ok) throw new Error(`GitHub answered ${response.status}`);
    const fetched = { at: Date.now(), releases: await response.json() };
    try {
      sessionStorage.setItem(CACHE_KEY, JSON.stringify(fetched));
    } catch (_) { /* storage full or off: fine */ }
    return fetched;
  }

  load().then(({ at, releases }) => {
    show(releases);
    showRefreshed(at);
  }).catch(() => {
    document.getElementById("releases-status").textContent =
      "Couldn't load the builds from GitHub just now: the buttons open the releases page instead.";
  });
})();
