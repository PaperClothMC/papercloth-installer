/*
 * Copyright (c) 2026 PaperClothMC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.paperclothmc.installer;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.util.List;

public final class Installer {
	static final String VERSION = "1.0.0";
	static final String DEFAULT_META = "https://raw.githubusercontent.com/PaperClothMC/papercloth-releases/main/v1/versions.json";

	private Installer() {
	}

	public static void main(String[] args) throws Exception {
		String meta = System.getProperty("papercloth.meta", DEFAULT_META);

		for (int i = 0; i + 1 < args.length; i++) {
			if ("--meta".equals(args[i])) {
				meta = args[i + 1];
			}
		}

		if (args.length == 0 || (args.length == 2 && "--meta".equals(args[0]))) {
			if (GraphicsEnvironment.isHeadless()) {
				usage();
				return;
			}

			InstallerWindow.open(meta);
			return;
		}

		System.exit(command(args, meta));
	}

	private static int command(String[] args, String metaLocation) {
		String mode = args[0];

		if ("help".equals(mode) || "--help".equals(mode) || "-h".equals(mode)) {
			usage();
			return 0;
		}

		if (!"server".equals(mode) && !"list".equals(mode)) {
			System.err.println("Unknown command: " + mode);
			usage();
			return 2;
		}

		String directory = ".";
		String minecraft = null;
		String version = null;
		boolean snapshots = false;

		for (int i = 1; i < args.length; i++) {
			String arg = args[i];

			if ("--snapshots".equals(arg)) {
				snapshots = true;
			} else if (i + 1 < args.length && "--dir".equals(arg)) {
				directory = args[++i];
			} else if (i + 1 < args.length && "--mc".equals(arg)) {
				minecraft = args[++i];
			} else if (i + 1 < args.length && "--version".equals(arg)) {
				version = args[++i];
			} else if (i + 1 < args.length && "--meta".equals(arg)) {
				i++;
			} else {
				System.err.println("Unknown option: " + arg);
				return 2;
			}
		}

		try {
			Meta meta = Meta.load(metaLocation);

			if (meta.message != null) {
				System.out.println(meta.message);
			}

			if ("list".equals(mode)) {
				for (Meta.MinecraftVersion mc : meta.minecraftVersions(snapshots)) {
					System.out.println("Minecraft " + mc.id + (mc.snapshot ? " (snapshot)" : ""));

					for (Meta.Release release : meta.releasesFor(mc.id, true)) {
						System.out.println("  PaperCloth " + release);
					}
				}

				return 0;
			}

			List<Meta.MinecraftVersion> available = meta.minecraftVersions(snapshots);

			if (minecraft == null) {
				if (available.isEmpty()) {
					System.err.println("The version list has no Minecraft versions.");
					return 1;
				}

				minecraft = available.get(0).id;
			}

			Meta.Release release;

			if (version == null) {
				List<Meta.Release> releases = meta.releasesFor(minecraft, false);
				release = releases.isEmpty() ? null : releases.get(0);
			} else {
				release = meta.find(minecraft, version);
			}

			if (release == null) {
				System.err.println("No PaperCloth " + (version == null ? "release" : version) + " for Minecraft " + minecraft + ". Run 'list' to see what is available.");
				return 1;
			}

			ServerInstall.install(release, new File(directory), new ServerInstall.Listener() {
				@Override
				public void status(String message) {
					System.out.println(message);
				}

				@Override
				public void progress(int done, int total) {
				}
			});

			if (release.java > 0) {
				System.out.println("Start it with start.bat or start.sh (needs Java " + release.java + " or newer).");
			}

			return 0;
		} catch (Exception e) {
			System.err.println("Install failed: " + e.getMessage());
			return 1;
		}
	}

	private static void usage() {
		System.out.println("PaperCloth Installer " + VERSION);
		System.out.println();
		System.out.println("Without arguments it opens the installer window.");
		System.out.println();
		System.out.println("  server [--dir <folder>] [--mc <minecraft version>] [--version <papercloth version>] [--snapshots]");
		System.out.println("      install or update a PaperCloth server; defaults to the newest stable release");
		System.out.println("  list [--snapshots]");
		System.out.println("      show the versions you can install");
		System.out.println();
		System.out.println("  --meta <url or file>  read the version list from somewhere else (default " + DEFAULT_META + ")");
	}
}
