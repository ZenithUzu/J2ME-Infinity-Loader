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

package ru.playsoftware.j2meloader.applist;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.util.FileUtils;

/**
 * One game in one .zip (idea from JL-Mod Plus app bundles), to move it to another phone:
 * game/ = the installed game, config/ = its settings and key layout, data/ = its saves (RMS).
 */
public final class GamePackage {
	private static final String GAME = "game/", CONFIG = "config/", DATA = "data/";
	/** the game's folder name, so settings and saves keep matching it */
	private static final String FOLDER = "folder.txt";

	private GamePackage() {
	}

	public static void export(Context context, AppItem item, Uri target) throws IOException {
		String name = item.getPath();
		try (OutputStream os = context.getContentResolver().openOutputStream(target);
			 ZipOutputStream zip = new ZipOutputStream(os)) {
			zip.putNextEntry(new ZipEntry(FOLDER));
			zip.write(name.getBytes("UTF-8"));
			zip.closeEntry();
			add(zip, new File(Config.getAppDir(), name), GAME);
			add(zip, new File(Config.getConfigsDir(), name), CONFIG);
			add(zip, new File(Config.getDataDir(), name), DATA);
		}
	}

	private static void add(ZipOutputStream zip, File dir, String prefix) throws IOException {
		File[] files = dir.listFiles();
		if (files == null) return;
		for (File f : files) {
			if (f.isDirectory()) {
				add(zip, f, prefix + f.getName() + "/");
				continue;
			}
			zip.putNextEntry(new ZipEntry(prefix + f.getName()));
			try (InputStream in = new FileInputStream(f)) {
				copy(in, zip);
			}
			zip.closeEntry();
		}
	}

	/**
	 * Unpacks a game package; a game already installed with the same folder is replaced.
	 * @return the game folder name, or null if the file is not a game package
	 */
	public static String importFrom(Context context, Uri source) throws IOException {
		File tmp = new File(Config.getAppDir(), ".import");
		FileUtils.deleteDirectory(tmp);
		boolean hasGame = false;
		try (ZipInputStream zip = new ZipInputStream(context.getContentResolver().openInputStream(source))) {
			for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
				if (e.isDirectory()) continue;
				File out = new File(tmp, e.getName());
				// never write outside the folder (zip slip)
				if (!out.getCanonicalPath().startsWith(tmp.getCanonicalPath() + File.separator)) continue;
				out.getParentFile().mkdirs();
				try (OutputStream os = new FileOutputStream(out)) {
					copy(zip, os);
				}
				if (e.getName().equals(GAME + Config.MIDLET_DEX_FILE.substring(1))) hasGame = true;
			}
		}
		if (!hasGame) {
			FileUtils.deleteDirectory(tmp);
			return null;
		}
		String name = folderName(new File(tmp, FOLDER));
		move(new File(tmp, GAME), new File(Config.getAppDir(), name));
		move(new File(tmp, CONFIG), new File(Config.getConfigsDir(), name));
		move(new File(tmp, DATA), new File(Config.getDataDir(), name));
		FileUtils.deleteDirectory(tmp);
		return name;
	}

	private static String folderName(File file) {
		String n = file.isFile() ? FileUtils.getText(file.getPath()).trim() : "";
		n = n.replaceAll(FileUtils.ILLEGAL_FILENAME_CHARS, "");
		return n.isEmpty() || n.startsWith(".") ? "imported_" + System.currentTimeMillis() : n;
	}

	/** Everything lives in the same work folder, so a rename is enough. */
	private static void move(File from, File to) throws IOException {
		if (!from.exists()) return;
		FileUtils.deleteDirectory(to);
		to.getParentFile().mkdirs();
		if (!from.renameTo(to)) {
			throw new IOException("Can't move " + from + " to " + to);
		}
	}

	private static void copy(InputStream in, OutputStream out) throws IOException {
		byte[] buf = new byte[65536];
		for (int r; (r = in.read(buf)) > 0; ) out.write(buf, 0, r);
	}
}
