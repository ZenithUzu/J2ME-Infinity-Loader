/*
 * Copyright 2018 Nikita Shakarun
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

package ru.playsoftware.j2meloader.filepicker;

import static ru.playsoftware.j2meloader.util.Constants.PREF_BLACK_BACKGROUND;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.nononsenseapps.filepicker.AbstractFilePickerActivity;
import com.nononsenseapps.filepicker.AbstractFilePickerFragment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import ru.playsoftware.j2meloader.R;

public class FilteredFilePickerActivity extends AbstractFilePickerActivity<File> {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		if (sp.getBoolean(ru.playsoftware.j2meloader.base.BaseActivity.PREF_DYNAMIC_COLORS, true)) {
			com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this);
		}
		if (sp.getBoolean(PREF_BLACK_BACKGROUND, false)) {
			getTheme().applyStyle(R.style.ThemeOverlay_App_Dark, true);
		}
		super.onCreate(savedInstanceState);
		// a sheet from the bottom (like the quick menu): the games stay visible behind it
		android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
		boolean portrait = dm.heightPixels >= dm.widthPixels;
		getWindow().setLayout(portrait ? ViewGroup.LayoutParams.MATCH_PARENT : (int) (dm.widthPixels * 0.6f),
				(int) (dm.heightPixels * (portrait ? 0.62f : 0.9f)));
		getWindow().setGravity(android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
	}

	@Override
	protected AbstractFilePickerFragment<File> getFragment(@Nullable String startPath, int mode, boolean allowMultiple,
														   boolean allowCreateDir, boolean allowExistingFile, boolean singleClick) {
		// a missing or unreadable start (e.g. "/") showed an empty list: start in the storage
		File start = startPath == null ? null : new File(startPath);
		if (start != null && start.isFile()) start = start.getParentFile();
		if (start == null || !start.isDirectory() || !start.canRead()) {
			start = android.os.Environment.getExternalStorageDirectory();
		}
		startPath = start.getPath();
		FilteredFilePickerFragment fragment = new FilteredFilePickerFragment();
		fragment.setArgs(startPath, mode, allowMultiple, allowCreateDir, allowExistingFile, singleClick);
		return fragment;
	}

	@Override
	public void onFilesPicked(@NonNull List<Uri> files) {
		// Don't put uris into ClipData: it causes FileUriExposedException
		Intent i = new Intent();
		i.putExtra(EXTRA_ALLOW_MULTIPLE, true);
		ArrayList<String> paths = new ArrayList<>();
		for (Uri file : files) {
			paths.add(file.toString());
		}
		i.putStringArrayListExtra(EXTRA_PATHS, paths);
		setResult(Activity.RESULT_OK, i);
		finish();
	}
}
