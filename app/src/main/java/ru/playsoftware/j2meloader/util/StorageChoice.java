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

package ru.playsoftware.j2meloader.util;

import static ru.playsoftware.j2meloader.util.Constants.PREF_EMULATOR_DIR;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.Config;

/**
 * Where games and saves live (Android 11+). By default they are in the app's own folder, which
 * Android deletes on uninstall. Like JL-Mod, the user can choose a public folder
 * (/J2ME-Infinity) that survives: only then "all files access" is asked, and the data is moved.
 */
public final class StorageChoice {
	private static final String TAG = "StorageChoice";
	private static final String PENDING = "storage_public_pending";

	private StorageChoice() {
	}

	public static File publicDir() {
		return new File(Environment.getExternalStorageDirectory(), Config.PUBLIC_DIR);
	}

	/** First start: explains it and lets the user choose. */
	public static void ask(Activity a) {
		new AlertDialog.Builder(a)
				.setTitle(R.string.storage_choice_title)
				.setMessage(a.getString(R.string.storage_choice_message, Config.getEmulatorDir(),
						publicDir().getPath()))
				.setPositiveButton(R.string.storage_use_public, (d, w) -> request(a))
				.setNegativeButton(R.string.storage_keep_here, null)
				.show();
	}

	/** Opens the system screen to allow "all files access"; the move happens on return. */
	public static void request(Activity a) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || FileUtils.hasAllFilesAccess()) {
			Toast.makeText(a, a.getString(R.string.storage_public_already, Config.getEmulatorDir()),
					Toast.LENGTH_LONG).show();
			return;
		}
		PreferenceManager.getDefaultSharedPreferences(a).edit().putBoolean(PENDING, true).apply();
		try {
			a.startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
					Uri.parse("package:" + a.getPackageName())));
		} catch (Exception e) {
			a.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
		}
	}

	/** Back from the system screen: if allowed, moves the data and restarts the app. */
	public static void finishIfGranted(Activity a) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(a);
		if (!prefs.getBoolean(PENDING, false)) return;
		prefs.edit().remove(PENDING).apply();
		if (!FileUtils.hasAllFilesAccess()) {
			Toast.makeText(a, R.string.storage_not_granted, Toast.LENGTH_LONG).show();
			return;
		}
		File from = a.getExternalFilesDir(null);
		File to = publicDir();
		AlertDialog progress = new AlertDialog.Builder(a)
				.setMessage(R.string.storage_moving)
				.setCancelable(false)
				.show();
		new Thread(() -> {
			boolean ok = true;
			try {
				if (from != null && from.isDirectory()) copyTree(from, to);
				else ok = to.mkdirs() || to.isDirectory();
			} catch (IOException e) {
				Log.e(TAG, "move data: ", e);
				ok = false;
			}
			boolean done = ok;
			if (done) {
				prefs.edit().putString(PREF_EMULATOR_DIR, to.getPath()).commit();
				if (from != null) deleteTree(from, false);
			}
			a.runOnUiThread(() -> {
				progress.dismiss();
				Toast.makeText(a, done ? a.getString(R.string.storage_moved, to.getPath())
						: a.getString(R.string.storage_move_failed), Toast.LENGTH_LONG).show();
				if (done) restart(a);
			});
		}, "MoveData").start();
	}

	/** Copies a folder into another, keeping files that are already there. */
	private static void copyTree(File from, File to) throws IOException {
		if (!to.isDirectory() && !to.mkdirs()) throw new IOException("Cannot create " + to);
		File[] files = from.listFiles();
		if (files == null) return;
		for (File f : files) {
			File out = new File(to, f.getName());
			if (f.isDirectory()) {
				copyTree(f, out);
			} else if (!out.exists()) {
				try (InputStream in = new FileInputStream(f); OutputStream os = new FileOutputStream(out)) {
					byte[] buf = new byte[65536];
					for (int r; (r = in.read(buf)) > 0; ) os.write(buf, 0, r);
				}
			}
		}
	}

	private static void deleteTree(File dir, boolean self) {
		File[] files = dir.listFiles();
		if (files != null) {
			for (File f : files) {
				if (f.isDirectory()) deleteTree(f, true); else f.delete();
			}
		}
		if (self) dir.delete();
	}

	/** The folders are read when the app starts, so it starts again. */
	private static void restart(Activity a) {
		Intent intent = a.getPackageManager().getLaunchIntentForPackage(a.getPackageName());
		if (intent != null) {
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
			a.startActivity(intent);
		}
		Runtime.getRuntime().exit(0);
	}
}
