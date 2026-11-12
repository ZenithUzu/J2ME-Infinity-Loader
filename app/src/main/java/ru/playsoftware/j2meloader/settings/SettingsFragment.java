/*
 * Copyright 2017-2019 Nikita Shakarun
 * Copyright 2019-2024 Yury Kharchenko
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

package ru.playsoftware.j2meloader.settings;

import static ru.playsoftware.j2meloader.util.Constants.PREF_ADD_CUTOUT_AREA;
import static ru.playsoftware.j2meloader.util.Constants.PREF_EMULATOR_DIR;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.XmlResourceParser;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.config.ProfilesActivity;
import ru.playsoftware.j2meloader.util.FileUtils;
import ru.playsoftware.j2meloader.util.PickDirResultContract;

public class SettingsFragment extends PreferenceFragmentCompat {
	private static final String TAG = "SettingsFragment";

	private Preference prefFolder;
	private final ActivityResultLauncher<String> openDirLauncher = registerForActivityResult(
			new PickDirResultContract(),
			this::onPickDirResult);

	/** gamepad skin: a folder of PNG pictures, also Citra style (see PadSkin) */
	private final ActivityResultLauncher<Uri> openSkinLauncher = registerForActivityResult(
			new androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
			this::onPickSkinResult);

	private final ActivityResultLauncher<String[]> openSkinZipLauncher = registerForActivityResult(
			new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
			zip -> onPickSkinResult(zip, true));

	private void onPickSkinResult(Uri tree) {
		onPickSkinResult(tree, false);
	}

	private void onPickSkinResult(Uri uri, boolean zip) {
		if (uri == null) {
			return;
		}
		int count;
		try {
			count = zip ? javax.microedition.lcdui.keyboard.PadSkin.importZip(uri)
					: javax.microedition.lcdui.keyboard.PadSkin.importFrom(uri);
		} catch (Exception e) {
			Log.e(TAG, "onPickSkinResult: ", e);
			count = 0;
		}
		if (count > 0) setButtonStyle(javax.microedition.lcdui.keyboard.VirtualKeyboard.STYLE_SKIN);
		android.widget.Toast.makeText(requireContext(), count > 0
				? getString(R.string.pad_skin_imported, count)
				: getString(R.string.pad_skin_empty), android.widget.Toast.LENGTH_LONG).show();
	}

	private final ActivityResultLauncher<Uri> openFiltersLauncher = registerForActivityResult(
			new androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
			uri -> onPickFilters(uri, false));
	private final ActivityResultLauncher<String[]> openFiltersZipLauncher = registerForActivityResult(
			new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
			uri -> onPickFilters(uri, true));

	private void onPickFilters(Uri uri, boolean zip) {
		if (uri == null) return;
		int count;
		try {
			count = zip ? ru.playsoftware.j2meloader.config.Filters.importZip(requireContext(), uri)
					: ru.playsoftware.j2meloader.config.Filters.importFolder(requireContext(), uri);
		} catch (Exception e) {
			Log.e(TAG, "onPickFilters: ", e);
			count = 0;
		}
		android.widget.Toast.makeText(requireContext(), count > 0
				? getString(R.string.filters_imported, count)
				: getString(R.string.filters_empty), android.widget.Toast.LENGTH_LONG).show();
	}

	private void showFiltersDialog() {
		new AlertDialog.Builder(requireContext())
				.setTitle(R.string.pref_import_filters)
				.setItems(R.array.filters_import_actions, (d, which) -> {
					if (which == 0) {
						openFiltersLauncher.launch(null);
					} else {
						openFiltersZipLauncher.launch(new String[]{"application/zip",
								"application/x-zip-compressed", "application/octet-stream"});
					}
				})
				.show();
	}

	/** An imported skin is used right away; going back to default returns to the Portemon look. */
	private void setButtonStyle(int style) {
		androidx.preference.ListPreference p = findPreference(
				javax.microedition.lcdui.keyboard.VirtualKeyboard.PREF_BUTTON_STYLE);
		if (p != null) p.setValue(String.valueOf(style));
	}

	private void showSkinDialog() {
		new AlertDialog.Builder(requireContext())
				.setTitle(R.string.pref_pad_skin)
				.setItems(R.array.pad_skin_actions, (d, which) -> {
					if (which == 0) {
						openSkinLauncher.launch(null);
					} else if (which == 1) {
						openSkinZipLauncher.launch(new String[]{"application/zip",
								"application/x-zip-compressed", "application/octet-stream"});
					} else {
						javax.microedition.lcdui.keyboard.PadSkin.reset();
						setButtonStyle(javax.microedition.lcdui.keyboard.VirtualKeyboard.STYLE_MODERN);
						android.widget.Toast.makeText(requireContext(), R.string.pad_skin_reset,
								android.widget.Toast.LENGTH_SHORT).show();
					}
				})
				.show();
	}

	/** Minimal look: every icon in the same neutral color instead of its own colors. */
	private static void tintIcons(androidx.preference.PreferenceGroup group, int color) {
		for (int i = 0; i < group.getPreferenceCount(); i++) {
			Preference p = group.getPreference(i);
			android.graphics.drawable.Drawable icon = p.getIcon();
			if (icon != null) {
				icon = icon.mutate();
				icon.setTint(color);
				p.setIcon(icon);
			}
			if (p instanceof androidx.preference.PreferenceGroup) {
				tintIcons((androidx.preference.PreferenceGroup) p, color);
			}
		}
	}

	/** What was in the ⋮ menu of the library (Collections has its own tab). */
	private void initAppActions() {
		onClick("pref_import_game", () -> runInLibrary(
				ru.playsoftware.j2meloader.applist.AppsListFragment.ACTION_IMPORT_GAME));
		onClick("pref_install_folder", () -> runInLibrary(
				ru.playsoftware.j2meloader.applist.AppsListFragment.ACTION_INSTALL_FOLDER));
		onClick("pref_check_updates_now", () -> runInLibrary(
				ru.playsoftware.j2meloader.applist.AppsListFragment.ACTION_CHECK_UPDATES));
		onClick("pref_help", () -> new ru.playsoftware.j2meloader.info.HelpDialogFragment()
				.show(getChildFragmentManager(), "help"));
		onClick("pref_about", () -> new ru.playsoftware.j2meloader.info.AboutDialogFragment()
				.show(getChildFragmentManager(), "about"));
		onClick("pref_save_log", () -> {
			try {
				ru.playsoftware.j2meloader.util.LogUtils.writeLog();
				android.widget.Toast.makeText(requireContext(), R.string.log_saved, android.widget.Toast.LENGTH_SHORT).show();
			} catch (java.io.IOException e) {
				Log.e(TAG, "save log: ", e);
				android.widget.Toast.makeText(requireContext(), R.string.error, android.widget.Toast.LENGTH_SHORT).show();
			}
		});
		onClick("pref_exit", () -> requireActivity().finishAffinity());
	}

	private void onClick(String key, Runnable action) {
		Preference p = findPreference(key);
		if (p != null) p.setOnPreferenceClickListener(x -> {
			action.run();
			return true;
		});
	}

	/** Back to the games screen, which runs the action (its pickers and dialogs live there). */
	private void runInLibrary(String action) {
		startActivity(new Intent(requireContext(), ru.playsoftware.j2meloader.MainActivity.class)
				.putExtra(ru.playsoftware.j2meloader.MainActivity.EXTRA_RUN, action));
		requireActivity().finish();
	}

	@Override
	public void onCreatePreferences(Bundle bundle, String s) {
		addPreferencesFromResource(R.xml.preferences);
		// where games and saves live; a public folder survives uninstalling (like JL-Mod)
		androidx.preference.Preference folder = findPreference("pref_data_folder");
		if (folder != null) {
			folder.setSummary(ru.playsoftware.j2meloader.config.Config.getEmulatorDir());
			folder.setOnPreferenceClickListener(p -> {
				ru.playsoftware.j2meloader.util.StorageChoice.ask(requireActivity());
				return true;
			});
		}
		// new colors are seen at once
		androidx.preference.Preference dyn = findPreference("pref_dynamic_colors");
		if (dyn != null) dyn.setOnPreferenceChangeListener((p, v) -> {
			requireView().post(() -> requireActivity().recreate());
			return true;
		});
		initLanguages();
		initAppActions();
		tintIcons(getPreferenceScreen(), com.google.android.material.color.MaterialColors.getColor(
				requireContext(), com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF606060));
		findPreference("pref_default_settings").setIntent(new Intent(requireActivity(), ProfilesActivity.class));
		findPreference("pref_backup").setOnPreferenceClickListener(preference -> {
			Intent intent = new Intent(requireActivity(), BackupActivity.class);
			startActivity(intent);
			return true;
		});
		findPreference("pref_import_filters").setOnPreferenceClickListener(preference -> {
			showFiltersDialog();
			return true;
		});
		findPreference("pref_pad_skin").setOnPreferenceClickListener(preference -> {
			showSkinDialog();
			return true;
		});
		prefFolder = findPreference(PREF_EMULATOR_DIR);
		prefFolder.setSummary(Config.getEmulatorDir());
		prefFolder.setOnPreferenceClickListener(preference -> {
			if (FileUtils.usesOldPicker()) {
				openDirLauncher.launch(null);
			} else {
				openPicker();
			}
			return true;
		});
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			findPreference(PREF_ADD_CUTOUT_AREA).setVisible(true);
		}
	}

	private void initLanguages() {
		ListPreference prefLanguage = findPreference("pref_language");
		if (prefLanguage == null) {
			return;
		}
		Context context = requireContext();
		List<String> tags = new ArrayList<>();
		tags.add("");
		@SuppressLint("DiscouragedApi")
		int id = context.getResources()
				.getIdentifier("_generated_res_locale_config", "xml", context.getPackageName());
		try (XmlResourceParser parser = context.getResources().getXml(id)) {
			for (int type = parser.next(); type != XmlPullParser.END_DOCUMENT; type = parser.next()) {
				if (type != XmlPullParser.START_TAG || !"locale".equals(parser.getName())) {
					continue;
				}
				for (int i = 0; i < parser.getAttributeCount(); i++) {
					if ("name".equals(parser.getAttributeName(i))) {
						tags.add(parser.getAttributeValue(i));
						break;
					}
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "initLanguages: ", e);
		}
		String[] languageTags = tags.toArray(new String[0]);
		String[] languageNames = new String[languageTags.length];
		languageNames[0] = context.getString(R.string.pref_theme_system);
		for (int i = 1; i < languageTags.length; i++) {
			Locale locale = Locale.forLanguageTag(languageTags[i]);
			languageNames[i] = locale.getDisplayName(locale);
		}
		prefLanguage.setEntryValues(languageTags);
		prefLanguage.setEntries(languageNames);
		LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
		Locale locale = locales.isEmpty() ? null : locales.get(0);
		prefLanguage.setValue(locale == null ? "" : locale.toLanguageTag());
		prefLanguage.setOnPreferenceChangeListener((preference, value) -> {
			AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags((String) value));
			return true;
		});
	}

	private void openPicker() {
		try {
			startActivity(getFileManagerIntentOnDocumentProvider(Intent.ACTION_VIEW));
			return;
		} catch (ActivityNotFoundException ignored) {}

		try {
			startActivity(getFileManagerIntentOnDocumentProvider("android.provider.action.BROWSE"));
			return;
		} catch (ActivityNotFoundException ignored) {}

		try {
			// Just try to open the file manager, try the package name used on "normal" phones
			startActivity(getFileManagerIntent("com.google.android.documentsui"));
			return;
		} catch (ActivityNotFoundException ignored) {}

		try {
			// Next, try the AOSP package name
			startActivity(getFileManagerIntent("com.android.documentsui"));
		} catch (ActivityNotFoundException ignored) {}
	}

	private Intent getFileManagerIntent(String packageName) {
		// Fragile, but some phones don't expose the system file manager in any better way
		Intent intent = new Intent(Intent.ACTION_MAIN);
		intent.setClassName(packageName, "com.android.documentsui.files.FilesActivity");
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		return intent;
	}

	private Intent getFileManagerIntentOnDocumentProvider(String action) {
		String authority = requireContext().getPackageName() + ".documentProvider";
		String root = new File(Config.getEmulatorDir()).getAbsolutePath();
		Intent intent = new Intent(action);
		intent.addCategory(Intent.CATEGORY_DEFAULT);
		intent.setData(DocumentsContract.buildRootUri(authority, root));
		intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
				| Intent.FLAG_GRANT_PREFIX_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
		return intent;
	}

	private void onPickDirResult(Uri uri) {
		if (uri == null) {
			return;
		}
		File file = new File(uri.getPath());
		String path = file.getAbsolutePath();
		if (!FileUtils.initWorkDir(file)) {
			new AlertDialog.Builder(requireActivity())
					.setTitle(R.string.error)
					.setCancelable(false)
					.setMessage(getString(R.string.create_apps_dir_failed, path))
					.setNegativeButton(android.R.string.cancel, null)
					.setPositiveButton(R.string.choose, (d, w) -> openDirLauncher.launch(null))
					.show();
			return;
		}
		getPreferenceManager().getSharedPreferences().edit()
				.putString(PREF_EMULATOR_DIR, path)
				.apply();
		prefFolder.setSummary(path);
	}
}
