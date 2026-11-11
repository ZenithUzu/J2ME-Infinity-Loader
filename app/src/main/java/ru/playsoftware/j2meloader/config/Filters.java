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

package ru.playsoftware.j2meloader.config;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Screen filters (shaders) for the OpenGL ES graphics mode.
 * <ul>
 * <li>The bundled ones (assets/filters: JarNix filters in PPSSPP format and RetroArch .glsl
 * filters) are copied to the shaders folder, without touching files already there.</li>
 * <li>Import copies PPSSPP (.ini/.fsh/.vsh) or RetroArch (.glsl) files from a folder or a .zip,
 * since on Android 11+ the work folder is hard to reach with a file manager.</li>
 * </ul>
 */
public final class Filters {
	private static final String TAG = "Filters";
	private static final String ASSETS = "filters";

	private Filters() {
	}

	public static void installBundled(Context context, File dir) {
		try {
			String[] files = context.getAssets().list(ASSETS);
			if (files == null) return;
			dir.mkdirs();
			for (String name : files) {
				File out = new File(dir, name);
				if (out.exists()) continue;
				try (InputStream in = context.getAssets().open(ASSETS + "/" + name)) {
					copy(in, out);
				}
			}
		} catch (IOException e) {
			Log.e(TAG, "installBundled: ", e);
		}
	}

	private static boolean isFilterFile(String name) {
		String n = name.toLowerCase(Locale.ROOT);
		return !n.startsWith(".") && (n.endsWith(".ini") || n.endsWith(".fsh") || n.endsWith(".vsh")
				|| n.endsWith(".glsl") || n.endsWith(".png"));
	}

	/** Copies the filter files of a folder (picked with the system picker). @return files copied */
	public static int importFolder(Context context, Uri tree) throws IOException {
		File dir = new File(Config.getShadersDir());
		dir.mkdirs();
		Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree,
				DocumentsContract.getTreeDocumentId(tree));
		String[] cols = {DocumentsContract.Document.COLUMN_DOCUMENT_ID,
				DocumentsContract.Document.COLUMN_DISPLAY_NAME};
		int count = 0;
		try (Cursor c = context.getContentResolver().query(children, cols, null, null, null)) {
			while (c != null && c.moveToNext()) {
				String name = c.getString(1);
				if (name == null || !isFilterFile(name)) continue;
				Uri doc = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0));
				try (InputStream in = context.getContentResolver().openInputStream(doc)) {
					copy(in, new File(dir, name));
				}
				count++;
			}
		}
		return count;
	}

	/** Copies the filter files inside a .zip (only file names count). @return files copied */
	public static int importZip(Context context, Uri zip) throws IOException {
		File dir = new File(Config.getShadersDir());
		dir.mkdirs();
		int count = 0;
		try (ZipInputStream in = new ZipInputStream(context.getContentResolver().openInputStream(zip))) {
			for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
				if (e.isDirectory()) continue;
				String entry = e.getName();
				String name = entry.substring(entry.lastIndexOf('/') + 1);
				if (!isFilterFile(name)) continue;
				copy(in, new File(dir, name));
				count++;
			}
		}
		return count;
	}

	private static void copy(InputStream in, File to) throws IOException {
		try (OutputStream out = new FileOutputStream(to)) {
			byte[] buf = new byte[16384];
			for (int r; (r = in.read(buf)) > 0; ) out.write(buf, 0, r);
		}
	}
}
