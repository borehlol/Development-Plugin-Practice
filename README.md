# SkinnyMC

A Paper 1.20.1 plugin (Kotlin) and the Docker network it runs on: a Velocity proxy in front of
lobby, lifesteal, kitpvp, and minigame servers, with MySQL for network-wide data like ranks.

## Set up a new computer

1. Install [Git](https://git-scm.com) and clone the repo:
   ```powershell
   git clone https://github.com/borehlol/Development-Plugin-Practice.git
   cd Development-Plugin-Practice
   ```
2. Double-click **`setup.cmd`** (or run `.\setup.cmd`). It installs Java and Docker Desktop if they're
   missing, creates `network/.env` with random passwords, builds the plugin, and starts the network.
   If it installs Docker, follow the steps it prints, then run it again.
3. To bring your worlds and ranks along, copy a backup `.zip` over and run `.\restore.cmd <path to zip>`.
4. Connect in Minecraft 1.20.1 to `localhost`.

`setup.cmd` is safe to run again any time; it keeps an existing `.env` and only starts what isn't running.

## Everyday commands

Run these from the `network` folder.

| What | Command |
|---|---|
| Start everything | `docker compose up -d` |
| Stop everything (worlds are kept) | `docker compose down` |
| Watch a server's console | `docker compose logs -f lifesteal` |
| Type a console command | `docker compose exec lifesteal rcon-cli op <name>` |
| Restart one server | `docker compose restart lobby` |

**Never** run `docker compose down -v` or Docker Desktop's "Clean up / Purge data": they delete the worlds and ranks.

## Changing the plugin

```powershell
.\gradlew.bat deployNetwork          # from the project folder: builds into network/plugins/
cd network
docker compose restart lobby         # each server loads the new build when it restarts
```

Changes to `network/docker-compose.yml` need `docker compose up -d` instead of a restart.

`.\gradlew.bat runServer` still starts a single local test server (no proxy or database; ranks go in `run/plugins/TestPlugin/ranks.yml`).
Stop the network first, since both use port 25565.

## Backups

- **`backup.cmd`** saves every world, plugin data, the MySQL ranks, and `network/.env` into
  `backups/backup-<date>.zip`. The network is stopped for about a minute while it runs.
- **`restore.cmd [zip]`** replaces everything with a backup (the newest one in `backups/` if no path is given).
  It asks before changing anything.

Backups contain your passwords: keep them private. Git ignores `backups/` and `network/.env`.

## Layout

| Path | What |
|---|---|
| `src/main/kotlin/gg/skinny/test/` | Plugin code. `NetworkServer.kt` reads which server it's running on. |
| `network/docker-compose.yml` | Every server and its settings |
| `network/velocity/velocity.toml` | Proxy settings: MOTD, server list |
| `network/config/paper-global.yml` | Copied into every Paper server; enables Velocity forwarding |
| `network/.env` | Passwords (not in Git; `setup.cmd` creates it) |
| `scripts/` | What `setup.cmd`, `backup.cmd`, and `restore.cmd` run |
