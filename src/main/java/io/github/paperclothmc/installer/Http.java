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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class Http {
	interface Progress {
		void bytes(long done, long total);
	}

	private static final String AGENT = "PaperCloth-Installer/" + Installer.VERSION;

	private Http() {
	}

	static URI resolve(URI base, String location) {
		File local = new File(location);

		if (location.indexOf(':') <= 1 && local.isAbsolute()) {
			return local.toURI();
		}

		return base == null ? URI.create(location) : base.resolve(location);
	}

	static URI toUri(String location) {
		if (location.startsWith("http://") || location.startsWith("https://") || location.startsWith("file:")) {
			return URI.create(location);
		}

		return new File(location).getAbsoluteFile().toURI();
	}

	static String text(URI uri) throws IOException {
		InputStream in = open(uri, null);

		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			copy(in, out, null, -1);
			return new String(out.toByteArray(), StandardCharsets.UTF_8);
		} finally {
			in.close();
		}
	}

	static void download(URI uri, File target, String algorithm, String expectedHash, long expectedSize, Progress progress) throws IOException {
		if (target.isFile() && expectedHash != null && expectedHash.equalsIgnoreCase(hash(target, algorithm))) {
			if (progress != null) {
				progress.bytes(target.length(), target.length());
			}

			return;
		}

		File parent = target.getAbsoluteFile().getParentFile();

		if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
			throw new IOException("Could not create " + parent);
		}

		File temporary = new File(target.getPath() + ".part");
		long[] total = {expectedSize};
		InputStream in = open(uri, total);

		try {
			OutputStream out = new FileOutputStream(temporary);

			try {
				copy(in, out, progress, total[0]);
			} finally {
				out.close();
			}
		} finally {
			in.close();
		}

		if (expectedHash != null) {
			String actual = hash(temporary, algorithm);

			if (!expectedHash.equalsIgnoreCase(actual)) {
				temporary.delete();
				throw new IOException("Checksum mismatch for " + uri + ": expected " + expectedHash + ", got " + actual);
			}
		}

		if (target.exists() && !target.delete()) {
			throw new IOException("Could not replace " + target);
		}

		if (!temporary.renameTo(target)) {
			throw new IOException("Could not move " + temporary + " to " + target);
		}
	}

	static String hash(File file, String algorithm) throws IOException {
		MessageDigest digest;

		try {
			digest = MessageDigest.getInstance(algorithm);
		} catch (NoSuchAlgorithmException e) {
			throw new IOException(e);
		}

		InputStream in = new FileInputStream(file);

		try {
			byte[] buffer = new byte[65536];
			int read;

			while ((read = in.read(buffer)) != -1) {
				digest.update(buffer, 0, read);
			}
		} finally {
			in.close();
		}

		StringBuilder hex = new StringBuilder();

		for (byte b : digest.digest()) {
			hex.append(String.format("%02x", b & 0xff));
		}

		return hex.toString();
	}

	private static InputStream open(URI uri, long[] length) throws IOException {
		URL url = uri.toURL();

		for (int redirects = 0; redirects < 8; redirects++) {
			URLConnection connection = url.openConnection();
			connection.setConnectTimeout(15000);
			connection.setReadTimeout(60000);
			connection.setRequestProperty("User-Agent", AGENT);

			if (!(connection instanceof HttpURLConnection)) {
				if (length != null && length[0] <= 0) {
					length[0] = connection.getContentLengthLong();
				}

				return connection.getInputStream();
			}

			HttpURLConnection http = (HttpURLConnection) connection;
			http.setInstanceFollowRedirects(false);
			int status = http.getResponseCode();

			if (status >= 300 && status < 400 && http.getHeaderField("Location") != null) {
				url = new URL(url, http.getHeaderField("Location"));
				http.disconnect();
				continue;
			}

			if (status != 200) {
				http.disconnect();
				throw new IOException("HTTP " + status + " for " + url);
			}

			if (length != null && length[0] <= 0) {
				length[0] = http.getContentLengthLong();
			}

			return http.getInputStream();
		}

		throw new IOException("Too many redirects for " + uri);
	}

	private static void copy(InputStream in, OutputStream out, Progress progress, long total) throws IOException {
		byte[] buffer = new byte[65536];
		long done = 0;
		int read;

		while ((read = in.read(buffer)) != -1) {
			out.write(buffer, 0, read);
			done += read;

			if (progress != null) {
				progress.bytes(done, total);
			}
		}
	}
}
