/*
 * Copyright 2018-2019 Nikita Shakarun
 * Copyright 2020-2022 Yury Kharchenko
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

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;

import java.io.File;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.base.BaseActivity;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.config.ProfilesManager;

/** Controller and keyboard mapping of one game (from its settings), see PadMapper. */
public class KeyMapperActivity extends BaseActivity {
	private PadMapper mapper;

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		String path = getIntent().getDataString();
		if (path == null) {
			Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
			finish();
			return;
		}
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
			actionBar.setTitle(R.string.pad_map_title);
		}
		ProfileModel params = ProfilesManager.loadConfig(new File(path));
		mapper = new PadMapper(this, params.keyMappings, map -> {
			params.keyMappings = map;
			ProfilesManager.saveConfig(params);
		});
		LinearLayout root = new LinearLayout(this);
		int pad = (int) (16 * getResources().getDisplayMetrics().density);
		root.setPadding(pad, pad / 2, pad, 0);
		root.addView(mapper.view(), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.MATCH_PARENT));
		setContentView(root);
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		if (mapper != null && mapper.onKey(event)) return true;
		return super.dispatchKeyEvent(event);
	}

	@Override
	public boolean dispatchGenericMotionEvent(MotionEvent event) {
		if (mapper != null && mapper.onMotion(event)) return true;
		return super.dispatchGenericMotionEvent(event);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
}
