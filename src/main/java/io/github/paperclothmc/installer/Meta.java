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

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Meta {
	static final int SCHEMA = 1;

	final URI source;
	final List<MinecraftVersion> minecraft;
	final List<Release> releases;
	final String message;

	private Meta(URI source, List<MinecraftVersion> minecraft, List<Release> releases, String message) {
		this.source = source;
		this.minecraft = minecraft;
		this.releases = releases;
		this.message = message;
	}

	static Meta load(String location) throws IOException {
		URI source = Http.toUri(location);
		Map<String, Object> root = Json.object(Json.parse(Http.text(source)), "versions.json");
		long schema = Json.number(root, "schema", 1);

		if (schema > SCHEMA) {
			throw new IOException("This installer reads version list schema " + SCHEMA + " but the list uses " + schema
					+ ". Download the newest installer.");
		}

		Map<String, MinecraftVersion> minecraft = new LinkedHashMap<String, MinecraftVersion>();

		for (Object entry : Json.array(root.get("minecraft"), "minecraft")) {
			Map<String, Object> object = Json.object(entry, "minecraft entry");
			String id = Json.requireString(object, "id", "minecraft entry");
			minecraft.put(id, new MinecraftVersion(id, "snapshot".equals(Json.string(object, "type", "release"))));
		}

		List<Release> releases = new ArrayList<Release>();

		for (Object entry : Json.array(root.get("versions"), "versions")) {
			Map<String, Object> object = Json.object(entry, "version entry");
			String version = Json.requireString(object, "version", "version entry");
			String minecraftId = Json.requireString(object, "minecraft", "version " + version);
			List<FileEntry> files = new ArrayList<FileEntry>();

			for (Object file : Json.array(object.get("files"), "files of " + version)) {
				Map<String, Object> fileObject = Json.object(file, "file of " + version);
				files.add(new FileEntry(
						Json.requireString(fileObject, "path", "file of " + version),
						Http.resolve(source, Json.requireString(fileObject, "url", "file of " + version)),
						Json.string(fileObject, "sha256", null),
						Json.number(fileObject, "size", -1),
						Json.bool(fileObject, "classpath", false)));
			}

			releases.add(new Release(version, minecraftId, Json.bool(object, "stable", true), (int) Json.number(object, "java", 0),
					Json.string(object, "mainClass", "net.fabricmc.loader.impl.launch.server.FabricServerLauncher"),
					Json.string(object, "launchMainClass", "net.fabricmc.loader.impl.launch.knot.KnotServer"),
					Collections.unmodifiableList(files)));

			if (!minecraft.containsKey(minecraftId)) {
				minecraft.put(minecraftId, new MinecraftVersion(minecraftId, false));
			}
		}

		return new Meta(source, Collections.unmodifiableList(new ArrayList<MinecraftVersion>(minecraft.values())),
				Collections.unmodifiableList(releases), Json.string(root, "message", null));
	}

	List<MinecraftVersion> minecraftVersions(boolean snapshots) {
		List<MinecraftVersion> result = new ArrayList<MinecraftVersion>();

		for (MinecraftVersion version : this.minecraft) {
			if ((snapshots || !version.snapshot) && !this.releasesFor(version.id, true).isEmpty()) {
				result.add(version);
			}
		}

		return result;
	}

	List<Release> releasesFor(String minecraftId, boolean unstable) {
		List<Release> result = new ArrayList<Release>();

		for (Release release : this.releases) {
			if (release.minecraft.equals(minecraftId) && (unstable || release.stable)) {
				result.add(release);
			}
		}

		return result;
	}

	Release find(String minecraftId, String version) {
		for (Release release : this.releasesFor(minecraftId, true)) {
			if (release.version.equals(version)) {
				return release;
			}
		}

		return null;
	}

	static final class MinecraftVersion {
		final String id;
		final boolean snapshot;

		MinecraftVersion(String id, boolean snapshot) {
			this.id = id;
			this.snapshot = snapshot;
		}

		@Override
		public String toString() {
			return this.id;
		}
	}

	static final class Release {
		final String version;
		final String minecraft;
		final boolean stable;
		final int java;
		final String mainClass;
		final String launchMainClass;
		final List<FileEntry> files;

		Release(String version, String minecraft, boolean stable, int java, String mainClass, String launchMainClass, List<FileEntry> files) {
			this.version = version;
			this.minecraft = minecraft;
			this.stable = stable;
			this.java = java;
			this.mainClass = mainClass;
			this.launchMainClass = launchMainClass;
			this.files = files;
		}

		@Override
		public String toString() {
			return this.stable ? this.version : this.version + " (beta)";
		}
	}

	static final class FileEntry {
		final String path;
		final URI url;
		final String sha256;
		final long size;
		final boolean classpath;

		FileEntry(String path, URI url, String sha256, long size, boolean classpath) {
			this.path = path;
			this.url = url;
			this.sha256 = sha256;
			this.size = size;
			this.classpath = classpath;
		}
	}
}
