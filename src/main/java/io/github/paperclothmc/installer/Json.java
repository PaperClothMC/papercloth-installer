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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
	private final String text;
	private int position;

	private Json(String text) {
		this.text = text;
	}

	static Object parse(String text) {
		Json parser = new Json(text);
		parser.skipWhitespace();
		Object value = parser.readValue();
		parser.skipWhitespace();

		if (parser.position != text.length()) {
			throw parser.error("trailing characters");
		}

		return value;
	}

	@SuppressWarnings("unchecked")
	static Map<String, Object> object(Object value, String what) {
		if (!(value instanceof Map)) {
			throw new IllegalArgumentException(what + " is not a JSON object");
		}

		return (Map<String, Object>) value;
	}

	@SuppressWarnings("unchecked")
	static List<Object> array(Object value, String what) {
		if (value == null) {
			return new ArrayList<Object>();
		}

		if (!(value instanceof List)) {
			throw new IllegalArgumentException(what + " is not a JSON array");
		}

		return (List<Object>) value;
	}

	static String string(Map<String, Object> object, String key, String fallback) {
		Object value = object.get(key);
		return value instanceof String ? (String) value : fallback;
	}

	static String requireString(Map<String, Object> object, String key, String what) {
		Object value = object.get(key);

		if (!(value instanceof String)) {
			throw new IllegalArgumentException(what + " has no \"" + key + "\"");
		}

		return (String) value;
	}

	static boolean bool(Map<String, Object> object, String key, boolean fallback) {
		Object value = object.get(key);
		return value instanceof Boolean ? (Boolean) value : fallback;
	}

	static long number(Map<String, Object> object, String key, long fallback) {
		Object value = object.get(key);
		return value instanceof Number ? ((Number) value).longValue() : fallback;
	}

	private Object readValue() {
		if (this.position >= this.text.length()) {
			throw this.error("unexpected end");
		}

		char c = this.text.charAt(this.position);

		switch (c) {
		case '{':
			return this.readObject();
		case '[':
			return this.readArray();
		case '"':
			return this.readString();
		case 't':
			this.expect("true");
			return Boolean.TRUE;
		case 'f':
			this.expect("false");
			return Boolean.FALSE;
		case 'n':
			this.expect("null");
			return null;
		default:
			return this.readNumber();
		}
	}

	private Map<String, Object> readObject() {
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		this.position++;
		this.skipWhitespace();

		if (this.peek() == '}') {
			this.position++;
			return result;
		}

		while (true) {
			this.skipWhitespace();
			String key = this.readString();
			this.skipWhitespace();
			this.consume(':');
			this.skipWhitespace();
			result.put(key, this.readValue());
			this.skipWhitespace();

			if (this.peek() == ',') {
				this.position++;
				continue;
			}

			this.consume('}');
			return result;
		}
	}

	private List<Object> readArray() {
		List<Object> result = new ArrayList<Object>();
		this.position++;
		this.skipWhitespace();

		if (this.peek() == ']') {
			this.position++;
			return result;
		}

		while (true) {
			this.skipWhitespace();
			result.add(this.readValue());
			this.skipWhitespace();

			if (this.peek() == ',') {
				this.position++;
				continue;
			}

			this.consume(']');
			return result;
		}
	}

	private String readString() {
		this.consume('"');
		StringBuilder builder = new StringBuilder();

		while (true) {
			if (this.position >= this.text.length()) {
				throw this.error("unterminated string");
			}

			char c = this.text.charAt(this.position++);

			if (c == '"') {
				return builder.toString();
			}

			if (c != '\\') {
				builder.append(c);
				continue;
			}

			char escape = this.text.charAt(this.position++);

			switch (escape) {
			case 'n':
				builder.append('\n');
				break;
			case 't':
				builder.append('\t');
				break;
			case 'r':
				builder.append('\r');
				break;
			case 'b':
				builder.append('\b');
				break;
			case 'f':
				builder.append('\f');
				break;
			case 'u':
				builder.append((char) Integer.parseInt(this.text.substring(this.position, this.position + 4), 16));
				this.position += 4;
				break;
			default:
				builder.append(escape);
			}
		}
	}

	private Number readNumber() {
		int start = this.position;

		while (this.position < this.text.length() && "+-0123456789.eE".indexOf(this.text.charAt(this.position)) >= 0) {
			this.position++;
		}

		if (start == this.position) {
			throw this.error("unexpected character");
		}

		String number = this.text.substring(start, this.position);

		if (number.indexOf('.') >= 0 || number.indexOf('e') >= 0 || number.indexOf('E') >= 0) {
			return Double.parseDouble(number);
		}

		return Long.parseLong(number);
	}

	private void expect(String word) {
		if (!this.text.startsWith(word, this.position)) {
			throw this.error("expected " + word);
		}

		this.position += word.length();
	}

	private void consume(char expected) {
		if (this.peek() != expected) {
			throw this.error("expected '" + expected + "'");
		}

		this.position++;
	}

	private char peek() {
		return this.position < this.text.length() ? this.text.charAt(this.position) : '\0';
	}

	private void skipWhitespace() {
		while (this.position < this.text.length() && Character.isWhitespace(this.text.charAt(this.position))) {
			this.position++;
		}
	}

	private IllegalArgumentException error(String message) {
		return new IllegalArgumentException("Invalid JSON at " + this.position + ": " + message);
	}
}
