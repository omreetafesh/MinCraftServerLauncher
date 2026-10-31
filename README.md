# Minecraft Server Launcher

A dark-themed desktop dashboard for managing Minecraft Java Edition servers on Windows.  
Built with JavaFX 26 and packaged as a standalone Windows app — **no Java installation required**.

---

## Features

- **Start / Stop / Restart** your server with one click
- **Live console** — see server output and send commands in real time
- **Performance charts** — RAM usage, TPS, and player count over time
- **RCON support** — send commands to a running server remotely
- **Server properties editor** — edit `server.properties` with a GUI
- **Multi-profile support** — manage multiple server configurations
- **Automatic JAR downloader** — fetch Paper / Vanilla / Fabric server JARs (checksum-verified)
- **Safe backups** — pauses autosave while zipping; includes the Nether and End; one-click restore
- **Crash recovery & scheduled restarts** — with a 1-minute in-game warning
- **First-run help** — EULA prompt, Java auto-detection, and clear errors instead of silent failures
- **Custom window chrome** — no OS title bar, resizable from any edge, fully integrated controls
- **Ambient motion** — a glow that follows the mouse under glass panels, card spotlights, a drifting aurora and smooth transitions (can be turned off in Settings → Performance)

---

## Download & Install

1. Go to the [**Releases**](../../releases) page
2. Download `MinecraftServerLauncher-Setup.exe`
3. Run the installer — it puts a shortcut on your Start Menu (and optionally Desktop)
4. Launch **Minecraft Server Launcher** and point it at your server folder

The launcher itself needs no Java — its runtime is bundled inside the installer.
The **Minecraft server** it runs does need Java (Java 21+ for current versions, from
[adoptium.net](https://adoptium.net)). The launcher finds it automatically, or you can pick one in
Settings → Performance → Java.

---

## Building from Source

### Requirements

| Tool | Where to get |
|------|-------------|
| JDK 26 | [adoptium.net](https://adoptium.net) or [Oracle](https://www.oracle.com/java/technologies/downloads/) |
| JavaFX SDK 26 | [gluonhq.com/products/javafx](https://gluonhq.com/products/javafx/) |
| Inno Setup 6.7+ *(optional, for installer)* | [jrsoftware.org/isinfo](https://jrsoftware.org/isinfo.php) |

### Steps

1. Clone the repo:
   ```
   git clone https://github.com/omreetafesh/MinCraftServerLauncher.git
   cd MinCraftServerLauncher
   ```

2. Place the JavaFX SDK in the project root so the folder is named `javafx-sdk-26.0.1`  
   (or set `JAVAFX_HOME_OVERRIDE` at the top of `build.bat`)

3. Double-click **`build.bat`**

   The script auto-detects your JDK and JavaFX, compiles, packages, and:
   - If Inno Setup is installed → produces `dist\MinecraftServerLauncher-Setup.exe`
   - Otherwise → produces a portable folder at `dist\output\Minecraft Server Launcher\`

---

## Project Structure

```
src/
  module-info.java          module declaration
  main/
    Main.java               JavaFX entry point, window setup, icon rendering
    AppStarter.java         bootstrap launcher (needed by jpackage)
    LauncherController.java all UI logic
    RconClient.java         raw TCP RCON protocol implementation
    AppIcon.java            draws the app logo (window, tray, installer)
    Icon.java               vector UI icons
    Effects.java            cursor glow, card spotlight and aurora background
    Motion.java             shared animation helpers
    Launcher.fxml           scene layout
    style.css               dark theme

tools/
  MakeIcon.java             generates icon.ico at build time (no external tools needed)
  MakeWizardArt.java        generates the installer's background, banner and header art

build.bat                   one-click build script
installer.iss               Inno Setup installer script
```

---

## License

MIT — free to use, modify, and distribute.
