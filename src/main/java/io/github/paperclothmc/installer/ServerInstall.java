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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

final class ServerInstall {
	interface Listener {
		void status(String message);

		void progress(int done, int total);
	}

	static final String MOJANG_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
	private static final String RECORD = ".papercloth/install.json";

	private ServerInstall() {
	}

	static void install(Meta.Release release, File directory, Listener listener) throws IOException {
		File root = directory.getAbsoluteFile();

		if (!root.isDirectory() && !root.mkdirs()) {
			throw new IOException("Could not create " + root);
		}

		List<String> previous = previousFiles(root);
		List<String> installed = new ArrayList<String>();
		int steps = release.files.size() + 2;
		int step = 0;

		for (Meta.FileEntry file : release.files) {
			File target = inside(root, file.path);
			listener.status("Downloading " + file.path);
			listener.progress(step++, steps);
			Http.download(file.url, target, "SHA-256", file.sha256, file.size, null);
			installed.add(normalise(file.path));
		}

		listener.status("Downloading the Minecraft " + release.minecraft + " server from Mojang");
		listener.progress(step++, steps);
		downloadMinecraft(release.minecraft, new File(root, "server.jar"));

		listener.status("Writing the launcher");
		listener.progress(step, steps);
		writeLauncher(release, root);
		writeText(new File(root, "fabric-server-launcher.properties"), "serverJar=server.jar\n", true);
		String start = "java -Xms2G -Xmx4G -jar papercloth-server.jar nogui";
		writeText(new File(root, "start.bat"), "@echo off\r\n" + start + "\r\npause\r\n", false);
		File unix = new File(root, "start.sh");
		writeText(unix, "#!/usr/bin/env sh\ncd \"$(dirname \"$0\")\"\nexec " + start + "\n", false);
		unix.setExecutable(true);

		for (String old : previous) {
			if (!installed.contains(old)) {
				File stale = inside(root, old);

				if (stale.isFile() && !stale.delete()) {
					throw new IOException("Could not remove the old " + old);
				}
			}
		}

		writeRecord(root, release, installed);
		listener.progress(steps, steps);
		listener.status("Installed PaperCloth " + release.version + " for Minecraft " + release.minecraft);
	}

	private static void downloadMinecraft(String minecraft, File target) throws IOException {
		Map<String, Object> manifest = Json.object(Json.parse(Http.text(URI.create(MOJANG_MANIFEST))), "Mojang version manifest");

		for (Object entry : Json.array(manifest.get("versions"), "Mojang versions")) {
			Map<String, Object> version = Json.object(entry, "Mojang version");

			if (!minecraft.equals(version.get("id"))) {
				continue;
			}

			Map<String, Object> details = Json.object(Json.parse(Http.text(URI.create(Json.requireString(version, "url", "Mojang version")))), "Mojang version");
			Map<String, Object> downloads = Json.object(details.get("downloads"), "Mojang downloads");
			Map<String, Object> server = Json.object(downloads.get("server"), "Mojang server download");
			Http.download(URI.create(Json.requireString(server, "url", "Mojang server download")), target, "SHA-1",
					Json.string(server, "sha1", null), Json.number(server, "size", -1), null);
			return;
		}

		throw new IOException("Mojang does not list Minecraft " + minecraft);
	}

	private static void writeLauncher(Meta.Release release, File root) throws IOException {
		StringBuilder classPath = new StringBuilder();

		for (Meta.FileEntry file : release.files) {
			if (file.classpath) {
				if (classPath.length() > 0) {
					classPath.append(' ');
				}

				classPath.append(normalise(file.path).replace(" ", "%20"));
			}
		}

		Manifest manifest = new Manifest();
		Attributes attributes = manifest.getMainAttributes();
		attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
		attributes.put(Attributes.Name.MAIN_CLASS, release.mainClass);
		attributes.put(Attributes.Name.CLASS_PATH, classPath.toString());
		File target = new File(root, "papercloth-server.jar");
		JarOutputStream jar = new JarOutputStream(new FileOutputStream(target), manifest);

		try {
			jar.putNextEntry(new JarEntry("fabric-server-launch.properties"));
			jar.write(("launch.mainClass=" + release.launchMainClass + "\n").getBytes(StandardCharsets.UTF_8));
			jar.closeEntry();
		} finally {
			jar.close();
		}
	}

	private static List<String> previousFiles(File root) {
		List<String> result = new ArrayList<String>();
		File record = new File(root, RECORD);

		if (!record.isFile()) {
			return result;
		}

		try {
			Map<String, Object> object = Json.object(Json.parse(Http.text(record.toURI())), RECORD);

			for (Object path : Json.array(object.get("files"), "files")) {
				if (path instanceof String) {
					result.add(normalise((String) path));
				}
			}
		} catch (IOException | RuntimeException ignored) {
			result.clear();
		}

		return result;
	}

	private static void writeRecord(File root, Meta.Release release, List<String> files) throws IOException {
		StringBuilder json = new StringBuilder();
		json.append("{\n  \"installer\": \"").append(Installer.VERSION).append("\",\n");
		json.append("  \"version\": \"").append(escape(release.version)).append("\",\n");
		json.append("  \"minecraft\": \"").append(escape(release.minecraft)).append("\",\n");
		json.append("  \"files\": [");

		for (int i = 0; i < files.size(); i++) {
			json.append(i == 0 ? "\n    \"" : ",\n    \"").append(escape(files.get(i))).append('"');
		}

		json.append("\n  ]\n}\n");
		File record = new File(root, RECORD);
		record.getParentFile().mkdirs();
		writeText(record, json.toString(), true);
	}

	private static File inside(File root, String path) throws IOException {
		String clean = normalise(path);

		if (clean.isEmpty() || clean.startsWith("/") || clean.contains(":") || ("/" + clean + "/").contains("/../")) {
			throw new IOException("Refusing to write outside the server folder: " + path);
		}

		File target = new File(root, clean);

		if (!target.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator)) {
			throw new IOException("Refusing to write outside the server folder: " + path);
		}

		return target;
	}

	private static String normalise(String path) {
		return path.replace('\\', '/');
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private static void writeText(File file, String text, boolean replace) throws IOException {
		if (!replace && file.exists()) {
			return;
		}

		OutputStream out = new FileOutputStream(file);
		Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);

		try {
			writer.write(text);
		} finally {
			writer.close();
		}
	}
}
