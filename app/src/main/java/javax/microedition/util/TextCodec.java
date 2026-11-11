/*
 * Copyright 2026 ZenithUzu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package javax.microedition.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.Locale;

/**
 * Text of the game in the right encoding (ñ, á, ¿, ¡...).
 * <p>
 * Old phones read text as ISO-8859-1 (or the encoding the game expects), but many games and
 * translations store it as UTF-8. When the game uses the default encoding (new String(bytes),
 * new InputStreamReader(in)...), the installer sends the call here: if the bytes are valid
 * UTF-8 they are read as UTF-8, otherwise with the game's encoding (ISO-8859-1 becomes
 * windows-1252, which adds € “ ” … and never loses a letter). Encodings outside the Latin
 * family (Shift_JIS, windows-1251...) are used as they are, without guessing.
 */
public final class TextCodec {
	private static final Charset UTF8 = Charset.forName("UTF-8");

	private TextCodec() {
	}

	/** Latin family: guessing UTF-8 is safe and a 1-byte table can convert it. */
	private static boolean guesses(String enc) {
		if (enc == null) return true;
		String e = enc.toUpperCase(Locale.ROOT).replace("_", "-");
		return e.equals("UTF-8") || e.equals("UTF8") || e.startsWith("ISO-8859-1") || e.startsWith("ISO8859-1")
				|| e.equals("LATIN1") || e.equals("ISO-LATIN-1") || e.equals("CP1252") || e.equals("WINDOWS-1252")
				|| e.equals("US-ASCII") || e.equals("ASCII");
	}

	private static Charset latin(String enc) {
		String e = enc == null ? "" : enc.toUpperCase(Locale.ROOT);
		if (e.startsWith("UTF")) return Charset.forName("windows-1252");
		try {
			Charset c = Charset.forName(enc);
			return c.name().equals("ISO-8859-1") || c.name().equals("US-ASCII") ? Charset.forName("windows-1252") : c;
		} catch (Exception ex) {
			return Charset.forName("windows-1252");
		}
	}

	/**
	 * True when the bytes have non-ASCII text that is valid UTF-8. A sequence cut at the end only
	 * counts as fine when partial (a chunk of a stream) and some full one was seen before.
	 */
	static boolean isUtf8(byte[] b, int off, int len, boolean partial) {
		boolean multi = false;
		int end = off + len;
		for (int i = off; i < end; ) {
			int c = b[i] & 0xFF;
			int n;
			if (c < 0x80) {
				i++;
				continue;
			} else if (c >= 0xC2 && c <= 0xDF) n = 1;
			else if (c >= 0xE0 && c <= 0xEF) n = 2;
			else if (c >= 0xF0 && c <= 0xF4) n = 3;
			else return false;
			if (i + n >= end) return partial && multi; // cut at the end: only fine inside a stream
			for (int k = 1; k <= n; k++) {
				if ((b[i + k] & 0xC0) != 0x80) return false;
			}
			if (n == 2 && c == 0xE0 && (b[i + 1] & 0xFF) < 0xA0) return false; // overlong
			if (n == 3 && c == 0xF0 && (b[i + 1] & 0xFF) < 0x90) return false;
			multi = true;
			i += n + 1;
		}
		return multi;
	}

	private static Charset pick(byte[] b, int off, int len, String enc) {
		if (!guesses(enc)) {
			try {
				return Charset.forName(enc);
			} catch (Exception e) {
				return latin(null);
			}
		}
		return isUtf8(b, off, len, false) ? UTF8 : latin(enc);
	}

	private static char[] decode(byte[] b, int off, int len, Charset cs) {
		try {
			CharBuffer out = cs.newDecoder().onMalformedInput(CodingErrorAction.REPLACE)
					.onUnmappableCharacter(CodingErrorAction.REPLACE).decode(ByteBuffer.wrap(b, off, len));
			char[] chars = new char[out.remaining()];
			out.get(chars);
			return chars;
		} catch (CharacterCodingException e) { // not with REPLACE
			return new char[0];
		}
	}

	/** new String(bytes) */
	public static char[] decode(byte[] b, String enc) {
		return decode(b, 0, b.length, enc);
	}

	/** new String(bytes, offset, length) */
	public static char[] decode(byte[] b, int off, int len, String enc) {
		if (off < 0 || len < 0 || off > b.length - len) throw new StringIndexOutOfBoundsException();
		return decode(b, off, len, pick(b, off, len, enc));
	}

	/** ByteArrayOutputStream.toString() */
	public static String toString(ByteArrayOutputStream out, String enc) {
		return new String(decode(out.toByteArray(), enc));
	}

	/** Text of a manifest / JAD file. */
	public static String text(byte[] b) {
		return new String(decode(b, null));
	}

	/**
	 * new InputStreamReader(in): the reader is then made with UTF-8, and this stream turns
	 * the game's bytes into UTF-8 (decided with the first non-ASCII bytes it reads).
	 */
	public static InputStream stream(InputStream in, String enc) {
		if (in == null || !guesses(enc)) return in;
		return new Utf8Stream(in, latin(enc));
	}

	/** Reader charset to use with {@link #stream}. */
	public static String readerCharset(InputStream wrapped, String enc) {
		return wrapped instanceof Utf8Stream ? "UTF-8" : enc;
	}

	private static final class Utf8Stream extends InputStream {
		private final InputStream in;
		private final Charset single;
		private int mode; // 0 not known yet (only ASCII so far), 1 UTF-8, 2 single byte
		private char[] table;
		private byte[] buf = new byte[0];
		private int pos;

		Utf8Stream(InputStream in, Charset single) {
			this.in = in;
			this.single = single;
		}

		private boolean fill() throws IOException {
			byte[] raw = new byte[8192];
			int n = in.read(raw);
			if (n <= 0) return false;
			// read what is ready, so the guess sees as much text as possible
			while (n < raw.length && in.available() > 0) {
				int r = in.read(raw, n, raw.length - n);
				if (r <= 0) break;
				n += r;
			}
			if (mode == 0) {
				for (int i = 0; i < n; i++) {
					if (raw[i] < 0) {
						mode = isUtf8(raw, 0, n, true) ? 1 : 2;
						break;
					}
				}
			}
			if (mode != 2) {
				buf = raw;
				pos = 0;
				limit = n;
				return true;
			}
			if (table == null) {
				byte[] all = new byte[128];
				for (int i = 0; i < 128; i++) all[i] = (byte) (128 + i);
				table = decode(all, 0, 128, single);
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream(n + n / 2);
			for (int i = 0; i < n; i++) {
				int c = raw[i] & 0xFF;
				if (c < 0x80) {
					out.write(c);
					continue;
				}
				char ch = c - 128 < table.length ? table[c - 128] : '�';
				if (ch < 0x800) {
					out.write(0xC0 | ch >> 6);
					out.write(0x80 | ch & 0x3F);
				} else {
					out.write(0xE0 | ch >> 12);
					out.write(0x80 | ch >> 6 & 0x3F);
					out.write(0x80 | ch & 0x3F);
				}
			}
			buf = out.toByteArray();
			pos = 0;
			limit = buf.length;
			return true;
		}

		private int limit;

		@Override
		public int read() throws IOException {
			if (pos >= limit && !fill()) return -1;
			return buf[pos++] & 0xFF;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			if (len == 0) return 0;
			if (pos >= limit && !fill()) return -1;
			int n = Math.min(len, limit - pos);
			System.arraycopy(buf, pos, b, off, n);
			pos += n;
			return n;
		}

		@Override
		public int available() throws IOException {
			return limit - pos;
		}

		@Override
		public void close() throws IOException {
			in.close();
		}
	}
}
