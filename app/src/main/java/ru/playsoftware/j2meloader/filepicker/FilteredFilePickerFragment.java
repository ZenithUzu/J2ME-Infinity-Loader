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

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nononsenseapps.filepicker.FilePickerFragment;
import com.nononsenseapps.filepicker.LogicHandler;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

import ru.playsoftware.j2meloader.R;

public class FilteredFilePickerFragment extends FilePickerFragment {
	private static final List<String> extList = Arrays.asList(".jad", ".jar", ".kjx", ".zip");
	private static final Stack<File> history = new Stack<>();

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		if (history.empty()) {
			history.push(Environment.getExternalStorageDirectory());
		}
		requireActivity().getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
			@Override
			public void handleOnBackPressed() {
				if (isBackTop()) {
					setEnabled(false);
					requireActivity().onBackPressed();
				} else {
					goBack();
				}
			}
		});
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		LayoutInflater li = getLayoutInflater();
		switch (viewType) {
			case LogicHandler.VIEWTYPE_HEADER:
				return new HeaderViewHolder(li.inflate(R.layout.listitem_dir, parent, false));
			case LogicHandler.VIEWTYPE_CHECKABLE:
				return new CheckableViewHolder(li.inflate(R.layout.listitem_checkable, parent, false));
			case LogicHandler.VIEWTYPE_DIR:
			default:
				return new DirViewHolder(li.inflate(R.layout.listitem_dir, parent, false));
		}
	}

	private String getExtension(@NonNull File file) {
		String name = file.getName();
		int i = name.lastIndexOf('.');
		if (i < 0) {
			return null;
		} else {
			return name.substring(i).toLowerCase();
		}
	}

	@Override
	protected boolean isItemVisible(final File file) {
		if (isDir(file)) {
			return true;
		}
		if (mode != MODE_FILE && mode != MODE_FILE_AND_DIR) {
			return false;
		}
		String ext = getExtension(file);
		return ext != null && extList.contains(ext);
	}

	/**
	 * Android 11+: with "all files access" the folders can be read without the old storage
	 * permission (which those versions never give, the picker said "permission denied").
	 */
	@Override
	protected boolean hasPermission(@NonNull File path) {
		return ru.playsoftware.j2meloader.util.FileUtils.hasAllFilesAccess() || super.hasPermission(path);
	}

	private static final int MENU_SELECT_ALL = 0x5A11;

	/** "Select all": every game in this folder, to install them all at once. */
	@Override
	public void onCreateOptionsMenu(@NonNull android.view.Menu menu, @NonNull android.view.MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		if (allowMultiple) {
			menu.add(0, MENU_SELECT_ALL, 0, android.R.string.selectAll)
					.setIcon(R.drawable.ic_select_all)
					.setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);
		}
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull android.view.MenuItem item) {
		if (item.getItemId() != MENU_SELECT_ALL) return super.onOptionsItemSelected(item);
		if (mFiles != null) {
			for (int i = 0; i < mFiles.size(); i++) {
				File f = mFiles.get(i);
				if (!isDir(f)) mCheckedItems.add(f);
			}
			if (mAdapter != null) mAdapter.notifyDataSetChanged();
		}
		return true;
	}

	/** ".." stops at the storage folder; "/" above it can't be read and looked empty. */
	@NonNull
	@Override
	public File getRoot() {
		return Environment.getExternalStorageDirectory();
	}

	@Override
	public void goToDir(@NonNull File file) {
		history.add(mCurrentPath);
		super.goToDir(file);
	}

	private boolean isBackTop() {
		return history.empty();
	}

	private void goBack() {
		File last = history.pop();
		super.goToDir(last);
	}

	@Override
	public void onBindHeaderViewHolder(@NonNull HeaderViewHolder viewHolder) {
		viewHolder.itemView.setEnabled(!getRoot().equals(mCurrentPath));
		super.onBindHeaderViewHolder(viewHolder);
	}

	@NonNull
	@Override
	public Uri toUri(@NonNull File file) {
		return Uri.fromFile(file);
	}
}