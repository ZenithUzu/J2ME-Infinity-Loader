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

package javax.microedition.lcdui.keyboard;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Locale;

import javax.microedition.util.ContextHolder;

/**
 * Pictures for the on-screen gamepad (idea from JarNix).
 * <p>
 * A skin is a set of PNG files: a, b, x, y, l, r, select, start, dpad, each with a
 * "_pressed" picture, and the d-pad pressed either one picture per direction
 * (dpad_up, dpad_down_left...) or Citra style (dpad_pressed_one = up,
 * dpad_pressed_two = up + left), which is turned for the other directions.
 * Citra file names (button_a.png, dpad_pressed_one_direction.png...) are renamed on import.
 * <p>
 * Imported skins live in the app's private files/skin folder; without one,
 * the skin bundled in assets/skin is used.
 */
public final class PadSkin {
	private static final String DIR = "skin";
	private static final HashMap<String, Bitmap> cache = new HashMap<>();

	private PadSkin() {
	}

	private static Context context() {
		return ContextHolder.getAppContext();
	}

	private static File userDir() {
		return new File(context().getFilesDir(), DIR);
	}

	public static boolean hasUserSkin() {
		File[] files = userDir().listFiles();
		return files != null && files.length > 0;
	}

	/** The picture called name, or null if the skin does not have it. */
	public static synchronized Bitmap get(String name) {
		if (cache.containsKey(name)) return cache.get(name);
		Bitmap b = null;
		if (hasUserSkin()) {
			File f = new File(userDir(), name + ".png");
			if (f.isFile()) b = BitmapFactory.decodeFile(f.getPath());
		} else {
			try (InputStream in = context().getAssets().open(DIR + "/" + name + ".png")) {
				b = BitmapFactory.decodeStream(in);
			} catch (Exception ignored) {
			}
		}
		cache.put(name, b);
		return b;
	}

	/** Citra / other emulator file name -> the name used here (null = not a gamepad picture). */
	static String normalize(String fileName) {
		String n = fileName.toLowerCase(Locale.ROOT);
		if (!n.endsWith(".png")) return null;
		n = n.substring(0, n.length() - 4);
		if (n.startsWith("button_")) n = n.substring(7);
		n = n.replace("_directions", "").replace("_direction", "");
		if (n.equals("dpad_idle") || n.equals("dpad_standard")) n = "dpad";
		return n;
	}

	/**
	 * Copies the PNG files of a folder chosen with the system picker into the app.
	 * @return how many pictures were copied
	 */
	public static synchronized int importFrom(Uri tree) throws Exception {
		Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree,
				DocumentsContract.getTreeDocumentId(tree));
		String[] cols = {DocumentsContract.Document.COLUMN_DOCUMENT_ID,
				DocumentsContract.Document.COLUMN_DISPLAY_NAME};
		File tmp = new File(context().getFilesDir(), DIR + ".tmp");
		deleteDir(tmp);
		tmp.mkdirs();
		int count = 0;
		try (Cursor c = context().getContentResolver().query(children, cols, null, null, null)) {
			while (c != null && c.moveToNext()) {
				String name = c.getString(1) == null ? null : normalize(c.getString(1));
				if (name == null) continue;
				Uri doc = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0));
				try (InputStream in = context().getContentResolver().openInputStream(doc);
					 OutputStream out = new FileOutputStream(new File(tmp, name + ".png"))) {
					byte[] buf = new byte[16384];
					for (int r; (r = in.read(buf)) > 0; ) out.write(buf, 0, r);
				}
				count++;
			}
		}
		if (count == 0) {
			deleteDir(tmp);
			return 0;
		}
		deleteDir(userDir());
		tmp.renameTo(userDir());
		cache.clear();
		return count;
	}

	/** Back to the skin bundled with the app. */
	public static synchronized void reset() {
		deleteDir(userDir());
		cache.clear();
	}

	private static void deleteDir(File dir) {
		File[] files = dir.listFiles();
		if (files != null) {
			for (File f : files) f.delete();
		}
		dir.delete();
	}
}
