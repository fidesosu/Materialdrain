# Serving the website at materialdrain.senko.tools

The home page (with the downloads) is at `/`, the documentation at `/docs/`.

How it works:

1. GitHub builds the site whenever the `website` branch changes (the **Website** workflow) and stores it in the
   `site` branch: the home page from `website/` at its root, the documentation built into `docs/`. The app's branches
   (`main`, `dev`) hold none of it.
2. The server keeps a copy of that branch in `/var/www/materialdrain` and checks GitHub for a new build every
   5 minutes (`materialdrain-site-sync.timer`). It runs as root, so there's no user to look after, but in a sandbox
   which only lets it write that folder.
3. nginx serves the folder.

The server only ever reads from GitHub. Nothing on GitHub can reach the server.

| File | Goes to |
| --- | --- |
| `materialdrain-site-sync.sh` | `/usr/local/bin/materialdrain-site-sync` |
| `materialdrain-site-sync.service` | `/etc/systemd/system/` |
| `materialdrain-site-sync.timer` | `/etc/systemd/system/` |
| `materialdrain.senko.tools.conf` | `/etc/nginx/sites-available/` |

## Setting it up

1. **DNS:** a record `materialdrain` pointing at the server. `dig +short materialdrain.senko.tools` prints its address.
2. **Copy this folder to the server** (`scp -r docs/server-setup you@server:~/materialdrain-setup`) and `cd` into it.
3. **nginx and HTTPS:**
   ```bash
   sudo install -m 644 materialdrain.senko.tools.conf /etc/nginx/sites-available/
   sudo ln -s /etc/nginx/sites-available/materialdrain.senko.tools.conf /etc/nginx/sites-enabled/
   sudo nginx -t && sudo systemctl reload nginx
   sudo certbot --nginx -d materialdrain.senko.tools
   ```
   Choose the redirect to HTTPS when certbot asks.
4. **The first build:** once the **Website** workflow has run on `website` (Actions tab), the repository has a `site`
   branch. `/var/www/materialdrain` has to be empty or not exist:
   ```bash
   sudo git clone --branch site --single-branch --depth 1 \
       https://github.com/fidesosu/Materialdrain.git /var/www/materialdrain
   ```
5. **Keeping it up to date:**
   ```bash
   sudo install -m 755 materialdrain-site-sync.sh /usr/local/bin/materialdrain-site-sync
   sudo install -m 644 materialdrain-site-sync.service materialdrain-site-sync.timer /etc/systemd/system/
   sudo systemctl daemon-reload
   sudo systemctl enable --now materialdrain-site-sync.timer
   sudo systemctl start materialdrain-site-sync.service
   systemctl status materialdrain-site-sync.service --no-pager
   ```
   The service ends with `status=0/SUCCESS`.
6. **Clean up:** `rm -r ~/materialdrain-setup`.

Each update shows in `journalctl -u materialdrain-site-sync.service -n 5 --no-pager` as
`Updated the website: Site built from …`.

## If something goes wrong

- **The service failed:** `journalctl -u materialdrain-site-sync.service -n 30 --no-pager` shows git's error.
  `Read-only file system` means the site isn't at `/var/www/materialdrain`, the only folder the sandbox lets it write.
- **`sh\r` or `bad interpreter`:** the script got Windows line endings on the way:
  `sudo sed -i 's/\r$//' /usr/local/bin/materialdrain-site-sync`.
- **certbot can't verify the domain:** the `dig` of step 1 must print this server's address, and
  `curl -sI http://materialdrain.senko.tools` must answer from it.

## Taking it down

```bash
sudo systemctl disable --now materialdrain-site-sync.timer
sudo rm /etc/systemd/system/materialdrain-site-sync.{service,timer} /usr/local/bin/materialdrain-site-sync
sudo rm /etc/nginx/sites-enabled/materialdrain.senko.tools.conf /etc/nginx/sites-available/materialdrain.senko.tools.conf
sudo systemctl reload nginx
sudo rm -r /var/www/materialdrain
```
