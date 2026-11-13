# PaperCloth Installer

Installs a PaperCloth server: Fabric with the Paper API built in, so Paper plugins and Fabric mods run on the same server.

The installer reads the available versions from [papercloth-releases](https://github.com/PaperClothMC/papercloth-releases), so new PaperCloth releases show up without downloading a new installer. The Minecraft server jar is downloaded from Mojang during the install.

The installer runs on Java 8 or newer. The server needs the Java version listed for its release (25 for Minecraft 26.3).

## Usage

Download `papercloth-installer-<version>.jar` from [Releases](../../releases) and double-click it, or run it with `java -jar`. Pick the Minecraft version, the PaperCloth version and a folder, then click Install.

From a terminal:

```
java -jar papercloth-installer.jar list
java -jar papercloth-installer.jar server --dir <folder> [--mc 26.3] [--version <papercloth version>] [--snapshots]
```

Running `server` on an existing install updates it. Files the new version no longer uses are removed; worlds, plugins and configs are kept. Start the server with `start.bat` or `start.sh`.

`--meta <url or file>` reads the version list from another location.

## Version list

The installer reads `versions.json` (schema 1):

```json
{
  "schema": 1,
  "message": "optional text shown in the installer",
  "minecraft": [{ "id": "26.3", "type": "release" }],
  "versions": [
    {
      "version": "0.167.0",
      "minecraft": "26.3",
      "stable": true,
      "java": 25,
      "mainClass": "net.fabricmc.loader.impl.launch.server.FabricServerLauncher",
      "launchMainClass": "net.fabricmc.loader.impl.launch.knot.KnotServer",
      "files": [
        { "path": "libraries/papercloth-loader-0.20.1.jar", "url": "https://...", "sha256": "...", "size": 1895000, "classpath": true },
        { "path": "mods/papercloth-api-0.167.0.jar", "url": "https://...", "sha256": "...", "size": 34677388, "classpath": false }
      ]
    }
  ]
}
```

- `versions` is ordered newest first. The newest stable version for the selected Minecraft version is selected by default.
- `url` is absolute or relative to `versions.json`. `path` is the file's location in the server folder. Files with `classpath: true` are put on the launcher's class path.
- Minecraft versions with `"type": "snapshot"` only show when "Show snapshots" is ticked.
- Unknown fields are ignored. Changes older installers can't read raise `schema`, and those installers ask for an update.

## Building

```
./build.sh
```

Needs JDK 9 or newer (`JAVA_HOME` or `javac` on the `PATH`) and writes `build/papercloth-installer-<version>.jar`.

## License

GPL-3.0, see [LICENSE](LICENSE).
