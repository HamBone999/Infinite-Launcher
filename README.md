# Infinite Launcher

The one-click way to play [Minecraft Infinite Reborn](https://github.com/HamBone999/Minecraft-Infinite-Reborn).
Sign in with Microsoft, pick a version, press Play. It downloads the game from that repo's
GitHub releases, keeps it up to date, and lets you roll back to any older release from a
dropdown if a new one breaks something.

Get it from this repo's [releases](../../releases/latest).

| | Download | Notes |
| --- | --- | --- |
| Windows | `InfiniteLauncher-Setup.exe` | Installs for your user only, no admin prompt. Start menu and desktop shortcuts. |
| macOS | `InfiniteLauncher-macOS.zip` | One app for Apple Silicon and Intel. Unzip, drag to Applications. |
| Linux / Steam Deck | `InfiniteLauncher-Linux.tar.gz` | Unpack, run `./infinite-launcher`. `--install` adds a menu entry. |

Each one carries its own Java 8, the version the game needs. Nothing else to install.

> [!NOTE]
> The downloads aren't code-signed, so the first launch gets a warning.
> **Windows:** "Windows protected your PC" → **More info** → **Run anyway**.
> **macOS:** if it says the app can't be opened, go to **System Settings → Privacy & Security** and
> click **Open Anyway** next to Infinite Launcher.

## What it does

- **Microsoft sign-in**, the same check the official launcher does. You enter a short code at
  microsoft.com/link; your password never touches the launcher. Online-mode servers accept you.
- **Versions from GitHub.** Every release with a client zip attached shows up in the dropdown.
  *Latest* follows new releases automatically. Pick a specific version to stay on it.
- **Installs only what's missing**, checksum-verified: the game jar from the release zip, its
  libraries from the URLs in the release's `version.json`, and Alpha 1.0.4 (for 27 textures)
  straight from Mojang, only after you've signed in with an account that owns the game.
- **One game folder** for every version: worlds, options, screenshots and mods are shared.
- **Updates itself.** A newer launcher is downloaded in the background and used after a
  restart. A launcher update that fails to start is skipped automatically.
- **Crash report** with the end of the game log when the game exits with an error.

Where it keeps things:

| Windows | macOS | Linux |
| --- | --- | --- |
| `%APPDATA%\InfiniteLauncher` | `~/Library/Application Support/InfiniteLauncher` | `~/.local/share/InfiniteLauncher` |

Inside: `game/` (your worlds and settings), `versions/`, `libraries/`, `logs/`. Uninstalling the
launcher leaves this folder alone.

## Releasing

### The game: nothing changes

Keep publishing game releases in Minecraft-Infinite-Reborn exactly as before. The launcher picks
up any release there that has a client
zip named like `Infinite-1.0-XXXXXX.zip` (the older `Infinite1.0XXXXXX.zip` spelling works too)
containing `version.json` and `Infinite.jar`. The release notes are what players see in the
launcher, so they're worth writing for players.

A running launcher re-checks GitHub every 20 minutes, and every time it starts.

### The launcher

1. Bump `VERSION`.
2. `./build.sh` then `./package.sh` (needs `makensis`: `apt install nsis`).
3. Publish a release in **this** repo tagged **`vX.Y.Z`** with the four files from `dist/`:
   `InfiniteLauncher.jar`, `InfiniteLauncher-Setup.exe`, `InfiniteLauncher-macOS.zip`,
   `InfiniteLauncher-Linux.tar.gz`.

`InfiniteLauncher.jar` is what installed launchers update themselves from, so it must be on
every launcher release. Which repos the launcher reads is set at build time: `INFINITE_REPO`
for game versions, `INFINITE_LAUNCHER_REPO` for its own updates.

## Microsoft sign-in setup (once)

Sign-in needs a Microsoft app ID that belongs to this project. It's free, and it isn't a secret:
it goes in `msa-client-id.txt` next to `build.sh` and is compiled into the launcher.

1. Go to <https://portal.azure.com> → **Microsoft Entra ID** → **App registrations** →
   **New registration**.
   - Name: `Infinite Launcher`
   - Supported account types: **Personal Microsoft accounts only**
   - Redirect URI: leave empty
2. Open the new app → **Authentication** → **Advanced settings** → set
   **Allow public client flows** to **Yes** → **Save**.
3. Copy the **Application (client) ID** into `msa-client-id.txt` next to `build.sh`.
4. Ask Mojang to allow that ID to use the Minecraft login API:
   <https://aka.ms/mce-reviewappid>. Every new third-party launcher has to do this. Until it's
   approved, sign-in gets as far as Microsoft and Xbox and then Minecraft refuses it; the
   launcher says so in plain words instead of failing mysteriously.

## Building

```sh
./build.sh      # dist/InfiniteLauncher.jar
./package.sh    # the three downloads, with Java bundled
```

Java 8 JDK to build (`JDK8=` overrides the path). No other dependencies: the launcher is plain
Java 8 and Swing, about 400 KB.

Command line, handy for testing a release before announcing it:

```sh
java -jar dist/InfiniteLauncher.jar --cli list             # versions on GitHub
java -jar dist/InfiniteLauncher.jar --cli install latest   # download and verify one
java -jar dist/InfiniteLauncher.jar --cli check 1.0-410926 # install, find Java, print the launch command
```

---

Minecraft is a trademark of Mojang AB / Microsoft. This project is not affiliated with,
endorsed by, or associated with either of them.
