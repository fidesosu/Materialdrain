# Materialdrain website

This branch, `website`, is the source of https://materialdrain.senko.tools. The app itself is on `main` (releases) and
`dev` (development); this branch shares no history with them.

| Path | What it is |
| --- | --- |
| `website/` | The home page, served at `/`: plain HTML and CSS |
| `docs/` | The documentation, served at `/docs/`: how to use the app, how to add a host, and how the app is built. Built with [MkDocs Material](https://squidfunk.github.io/mkdocs-material/) (`mkdocs.yml`) |
| `docs/stylesheets/extra.css` | The documentation's colours, the same palette as `website/style.css` |
| `docs/server-setup/` | How the server serves the site, and the files it needs (not published) |
| `.github/workflows/site.yml` | Builds the site on every push to this branch, into the `site` branch |

Every push here rebuilds the site; the server picks the new build up within about 5 minutes.

## Building it locally

```bash
pip install -r docs/requirements.txt
mkdocs serve
```

`mkdocs serve` shows the documentation at http://127.0.0.1:8000, and reloads on every change. The home page is plain
HTML, linked from the site's root (`/style.css`), so serve it rather than opening the file:
`python3 -m http.server --directory website 8001`, then http://127.0.0.1:8001.
