/*
 * Copyright 2015-2016 Nickolay Savchenko
 * Copyright 2017-2020 Nikita Shakarun
 * Copyright 2018-2022 Yury Kharchenko
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

import static ru.playsoftware.j2meloader.util.Constants.KEY_APP_URI;
import static ru.playsoftware.j2meloader.util.Constants.KEY_MIDLET_NAME;
import static ru.playsoftware.j2meloader.util.Constants.PREF_APPS_VIEW;
import static ru.playsoftware.j2meloader.util.Constants.PREF_APP_SORT;
import static ru.playsoftware.j2meloader.util.Constants.PREF_LAST_PATH;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDiskIOException;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.GridLayoutManager;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.appsdb.AppRepository;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.config.ConfigActivity;
import ru.playsoftware.j2meloader.config.ProfilesActivity;
import ru.playsoftware.j2meloader.databinding.FragmentAppsListBinding;
import ru.playsoftware.j2meloader.info.AboutDialogFragment;
import ru.playsoftware.j2meloader.info.HelpDialogFragment;
import ru.playsoftware.j2meloader.settings.SettingsActivity;
import ru.playsoftware.j2meloader.util.AppUtils;
import ru.playsoftware.j2meloader.util.FileUtils;
import ru.playsoftware.j2meloader.util.LogUtils;
import ru.woesss.j2me.installer.BatchInstallerDialog;
import ru.woesss.j2me.installer.InstallerDialog;

public class AppsListFragment extends Fragment implements MenuProvider {
	private static final String TAG = AppsListFragment.class.getSimpleName();

	private final ActivityResultLauncher<String> openFileLauncher = registerForActivityResult(
			FileUtils.getFilePicker(),
			this::onPickFileResult);
	/** install every game in a folder (and its subfolders), like JL-Mod Plus */
	private final ActivityResultLauncher<Uri> openFolderLauncher = registerForActivityResult(
			new androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
			this::onPickFolderResult);
	private final AppsListAdapter adapter = new AppsListAdapter(this);
	/** game being exported while the "save as" picker is open */
	private AppItem exportItem;
	/** game whose icon is being changed while the image picker is open */
	private AppItem iconItem;
	private final ActivityResultLauncher<String[]> pickIconLauncher = registerForActivityResult(
			new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
			this::onPickIcon);
	private final ActivityResultLauncher<String> exportGameLauncher = registerForActivityResult(
			new androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip"),
			this::onExportGame);
	private final ActivityResultLauncher<String[]> importGameLauncher = registerForActivityResult(
			new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
			this::onImportGame);
	private GameCollections collections;
	/** every installed game, before the collection filter */
	private List<AppItem> allItems;
	private Uri appUri;
	private SharedPreferences preferences;
	private AppRepository appRepository;
	private Disposable searchViewDisposable;
	private GridLayoutManager layoutManager;
	private FragmentAppsListBinding binding;
	private DividerItemDecoration itemDecoration;

	public static AppsListFragment newInstance(Uri data) {
		AppsListFragment fragment = new AppsListFragment();
		Bundle args = new Bundle();
		args.putParcelable(KEY_APP_URI, data);
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Bundle args = requireArguments();
		appUri = args.getParcelable(KEY_APP_URI);
		args.remove(KEY_APP_URI);
		FragmentActivity activity = requireActivity();
		preferences = PreferenceManager.getDefaultSharedPreferences(activity);
		AppListModel appListModel = new ViewModelProvider(activity).get(AppListModel.class);
		appRepository = appListModel.getAppRepository();
		appRepository.observeErrors(this, this::alertDbError);
		appRepository.observeApps(this, this::onDbUpdated);
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		binding = FragmentAppsListBinding.inflate(inflater, container, false);
		return binding.getRoot();
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		MenuHost menuHost = requireActivity();
		menuHost.addMenuProvider(this, getViewLifecycleOwner());

		adapter.setEmptyView(binding.empty);
		int viewType = preferences.getInt(PREF_APPS_VIEW, AppsListAdapter.LAYOUT_TYPE_GRID);
		int spanCount;
		if (viewType == AppsListAdapter.LAYOUT_TYPE_GRID) {
			spanCount = gridColumns(getResources().getConfiguration().screenWidthDp);
		} else {
			spanCount = 1;
			itemDecoration = new DividerItemDecoration(view.getContext(), DividerItemDecoration.VERTICAL);
			binding.list.addItemDecoration(itemDecoration);
		}
		adapter.setLayout(viewType);
		layoutManager = new GridLayoutManager(requireContext(), spanCount);
		requireActivity().addOnConfigurationChangedListener(configuration -> {
			if (adapter.getItemViewType(0) == AppsListAdapter.LAYOUT_TYPE_GRID) {
				layoutManager.setSpanCount(gridColumns(configuration.screenWidthDp));
			}
		});
		binding.list.setLayoutManager(layoutManager);
		binding.list.setAdapter(adapter);
		binding.floatingActionButton.setOnClickListener(v -> {
			String path = preferences.getString(PREF_LAST_PATH, null);
			if (path == null) {
				File dir = Environment.getExternalStorageDirectory();
				if (dir.canRead()) {
					path = dir.getAbsolutePath();
				}
			}
			try {
				openFileLauncher.launch(path);
			} catch (ActivityNotFoundException e) {
				Toast.makeText(getContext(), R.string.error_no_picker, Toast.LENGTH_SHORT).show();
				e.printStackTrace();
			}
		});
	}

	private void alertDbError(Throwable throwable) {
		Activity activity = getActivity();
		if (activity == null) {
			Log.e(TAG, "Db error detected", throwable);
			return;
		}
		if (throwable instanceof SQLiteDiskIOException) {
			Toast.makeText(activity, R.string.error_disk_io, Toast.LENGTH_SHORT).show();
		} else {
			String msg = activity.getString(R.string.error) + ": " + throwable.getMessage();
			Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
		}
	}

	private static final String RELEASES_API =
			"https://api.github.com/repos/ZenithUzu/J2ME-Infinity-Loader/releases/latest";

	/**
	 * Like JL-Mod Plus updates: asks GitHub for the latest release and, if it is newer,
	 * offers to open its page to download the APK (nothing is installed silently).
	 */
	private void checkForUpdates() {
		FragmentActivity activity = requireActivity();
		Toast.makeText(activity, R.string.update_checking, Toast.LENGTH_SHORT).show();
		new Thread(() -> {
			String tag = null, url = null;
			int code = -1;
			try {
				java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(RELEASES_API)
						.openConnection();
				c.setRequestProperty("Accept", "application/vnd.github+json");
				c.setConnectTimeout(10000);
				c.setReadTimeout(10000);
				code = c.getResponseCode();
				if (code == 200) {
					try (java.io.InputStream in = c.getInputStream()) {
						org.json.JSONObject json = new org.json.JSONObject(
								new String(ru.playsoftware.j2meloader.util.IOUtils.toByteArray(in), "UTF-8"));
						tag = json.optString("tag_name", null);
						url = json.optString("html_url", null);
					}
				}
				c.disconnect();
			} catch (Exception e) {
				Log.w(TAG, "checkForUpdates: " + e);
			}
			final String fTag = tag, fUrl = url;
			final int fCode = code;
			activity.runOnUiThread(() -> showUpdateResult(activity, fCode, fTag, fUrl));
		}, "UpdateCheck").start();
	}

	private void showUpdateResult(FragmentActivity activity, int code, String tag, String url) {
		if (activity.isFinishing()) return;
		String current = ru.playsoftware.j2meloader.BuildConfig.VERSION_NAME;
		AlertDialog.Builder b = new AlertDialog.Builder(activity).setTitle(R.string.update_title);
		if (code == 404) {
			b.setMessage(R.string.update_no_releases);
		} else if (tag == null || url == null) {
			b.setMessage(R.string.update_error);
		} else if (compareVersions(tag, current) > 0) {
			b.setMessage(getString(R.string.update_available, tag, current))
					.setPositiveButton(R.string.update_open, (d, w) -> {
						try {
							startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
						} catch (Exception e) {
							Toast.makeText(activity, R.string.error, Toast.LENGTH_SHORT).show();
						}
					});
		} else {
			b.setMessage(getString(R.string.update_latest, current));
		}
		b.setNegativeButton(android.R.string.cancel, null).show();
	}

	/** "v1.10.0" vs "1.9.2-open": compares the numbers only. */
	static int compareVersions(String a, String b) {
		String[] x = a.replaceAll("^[^0-9]*", "").split("[^0-9]+");
		String[] y = b.replaceAll("^[^0-9]*", "").split("[^0-9]+");
		for (int i = 0; i < Math.max(x.length, y.length); i++) {
			int p = i < x.length && !x[i].isEmpty() ? Integer.parseInt(x[i]) : 0;
			int q = i < y.length && !y[i].isEmpty() ? Integer.parseInt(y[i]) : 0;
			if (p != q) return Integer.compare(p, q);
		}
		return 0;
	}

	/** Like JL-Mod Plus metadata editing: the list icon changes, the game's JAR does not. */
	private void showIconDialog(AppItem item) {
		new AlertDialog.Builder(requireActivity())
				.setTitle(item.getTitle())
				.setItems(R.array.icon_actions, (d, which) -> {
					if (which == 0) {
						iconItem = item;
						pickIconLauncher.launch(new String[]{"image/*"});
					} else {
						saveIcon(item, null);
					}
				})
				.show();
	}

	private void onPickIcon(Uri uri) {
		AppItem item = iconItem;
		iconItem = null;
		if (uri == null || item == null) return;
		android.graphics.Bitmap picture = null;
		try (java.io.InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
			picture = android.graphics.BitmapFactory.decodeStream(in);
		} catch (Exception e) {
			Log.e(TAG, "onPickIcon: ", e);
		}
		if (picture == null) {
			Toast.makeText(requireContext(), R.string.error, Toast.LENGTH_SHORT).show();
			return;
		}
		saveIcon(item, picture);
	}

	private void saveIcon(AppItem item, android.graphics.Bitmap picture) {
		try {
			ru.playsoftware.j2meloader.util.AppUtils.setCustomIcon(item, picture);
			appRepository.update(item);
		} catch (Exception e) {
			Log.e(TAG, "saveIcon: ", e);
			Toast.makeText(requireContext(), R.string.error, Toast.LENGTH_SHORT).show();
		}
	}

	private void onExportGame(Uri target) {
		AppItem item = exportItem;
		exportItem = null;
		if (target == null || item == null) return;
		FragmentActivity activity = requireActivity();
		new Thread(() -> {
			boolean ok;
			try {
				GamePackage.export(activity, item, target);
				ok = true;
			} catch (Exception e) {
				Log.e(TAG, "onExportGame: ", e);
				ok = false;
			}
			boolean done = ok;
			activity.runOnUiThread(() -> Toast.makeText(activity,
					done ? R.string.game_exported : R.string.error, Toast.LENGTH_SHORT).show());
		}, "ExportGame").start();
	}

	private void onImportGame(Uri source) {
		if (source == null) return;
		FragmentActivity activity = requireActivity();
		List<AppItem> current = allItems == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(allItems);
		new Thread(() -> {
			String name;
			try {
				name = GamePackage.importFrom(activity, source);
				if (name != null) ru.playsoftware.j2meloader.util.AppUtils.updateDb(appRepository, current);
			} catch (Exception e) {
				Log.e(TAG, "onImportGame: ", e);
				name = null;
			}
			boolean ok = name != null;
			activity.runOnUiThread(() -> Toast.makeText(activity,
					ok ? R.string.game_imported : R.string.game_import_invalid, Toast.LENGTH_LONG).show());
		}, "ImportGame").start();
	}

	/** Grid columns for the chosen icon size (Settings > Library grid). */
	private int gridColumns(int screenWidthDp) {
		return Math.max(1, screenWidthDp / (AppsListAdapter.gridIconDp(requireContext()) + 42));
	}

	@Override
	public void onResume() {
		super.onResume();
		// grid settings may have changed in Settings
		if (layoutManager != null && adapter.getItemViewType(0) == AppsListAdapter.LAYOUT_TYPE_GRID) {
			layoutManager.setSpanCount(gridColumns(getResources().getConfiguration().screenWidthDp));
			adapter.notifyDataSetChanged();
		}
	}

	private GameCollections collections() {
		if (collections == null) collections = new GameCollections(requireContext());
		return collections;
	}

	/** Shows only the games of the chosen collection, and its name under the title. */
	private void showCollection() {
		adapter.setItems(collections().filter(allItems));
		androidx.appcompat.app.ActionBar bar =
				((androidx.appcompat.app.AppCompatActivity) requireActivity()).getSupportActionBar();
		if (bar != null) bar.setSubtitle(collectionTitle(collections().getCurrent()));
	}

	private String collectionTitle(String name) {
		if (GameCollections.FAVORITES.equals(name)) return getString(R.string.collection_favorites);
		if (GameCollections.RECENT.equals(name)) return getString(R.string.collection_recent);
		return name;
	}

	/** Main menu > Collections: pick which games to show, create or delete collections. */
	private void showCollectionsDialog() {
		List<String> names = collections().names();
		names.add(0, GameCollections.RECENT);
		names.add(0, GameCollections.FAVORITES);
		String[] items = new String[names.size() + 2];
		items[0] = getString(R.string.collection_all_games);
		for (int i = 0; i < names.size(); i++) items[i + 1] = collectionTitle(names.get(i));
		items[items.length - 1] = getString(R.string.collection_new);
		AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity())
				.setTitle(R.string.collections)
				.setItems(items, (d, which) -> {
					if (which == 0) {
						collections().setCurrent(null);
						showCollection();
					} else if (which == items.length - 1) {
						askCollectionName(name -> {
							collections().setCurrent(name);
							showCollection();
						});
					} else {
						collections().setCurrent(names.get(which - 1));
						showCollection();
					}
				});
		String current = collections().getCurrent();
		if (current != null && !current.startsWith("\u0001")) {
			builder.setNeutralButton(getString(R.string.collection_delete, current), (d, w) -> {
				collections().delete(current);
				showCollection();
			});
		}
		builder.show();
	}

	/** Game menu > Collections: tick the collections this game belongs to. */
	private void showGameCollectionsDialog(AppItem item) {
		List<String> names = collections().names();
		if (names.isEmpty()) {
			askCollectionName(name -> collections().setMember(name, item.getPath(), true));
			return;
		}
		String[] items = names.toArray(new String[0]);
		boolean[] checked = new boolean[items.length];
		for (int i = 0; i < items.length; i++) {
			checked[i] = collections().contains(items[i], item.getPath());
		}
		new AlertDialog.Builder(requireActivity())
				.setTitle(item.getTitle())
				.setMultiChoiceItems(items, checked, (d, which, isChecked) -> {
					collections().setMember(items[which], item.getPath(), isChecked);
					showCollection();
				})
				.setNeutralButton(R.string.collection_new, (d, w) -> askCollectionName(name -> {
					collections().setMember(name, item.getPath(), true);
					showCollection();
				}))
				.setPositiveButton(android.R.string.ok, null)
				.show();
	}

	private interface NameCallback {
		void accept(String name);
	}

	private void askCollectionName(NameCallback onCreated) {
		FragmentActivity activity = requireActivity();
		EditText editText = new EditText(activity);
		editText.setSingleLine();
		int pad = (int) (20 * getResources().getDisplayMetrics().density);
		LinearLayout layout = new LinearLayout(activity);
		layout.setPadding(pad, 0, pad, 0);
		layout.addView(editText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT));
		new AlertDialog.Builder(activity)
				.setTitle(R.string.collection_new)
				.setView(layout)
				.setPositiveButton(android.R.string.ok, (d, w) -> {
					String name = editText.getText().toString().trim();
					if (name.isEmpty()) return;
					collections().create(name);
					onCreated.accept(name);
				})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	private void onPickFolderResult(Uri tree) {
		if (tree == null) {
			return;
		}
		List<Uri> uris = new java.util.ArrayList<>();
		try {
			collectGames(tree, android.provider.DocumentsContract.getTreeDocumentId(tree), uris, 0);
		} catch (Exception e) {
			Log.e(TAG, "onPickFolderResult: ", e);
		}
		if (uris.isEmpty()) {
			Toast.makeText(requireContext(), R.string.install_folder_empty, Toast.LENGTH_SHORT).show();
			return;
		}
		onPickFileResult(uris);
	}

	/** Adds the .jar/.jad/.kjx files of a folder tree (max 3 subfolder levels). */
	private void collectGames(Uri tree, String docId, List<Uri> out, int depth) {
		Uri children = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
		String[] cols = {android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
				android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
				android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE};
		try (android.database.Cursor c = requireContext().getContentResolver()
				.query(children, cols, null, null, null)) {
			if (c == null) return;
			while (c.moveToNext()) {
				String id = c.getString(0);
				String name = c.getString(1);
				String mime = c.getString(2);
				if (android.provider.DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
					if (depth < 3) collectGames(tree, id, out, depth + 1);
				} else if (name != null) {
					String n = name.toLowerCase(java.util.Locale.ROOT);
					if (n.endsWith(".jar") || n.endsWith(".jad") || n.endsWith(".kjx") || n.endsWith(".zip")) {
						out.add(android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, id));
					}
				}
			}
		}
	}

	/**
	 * Install from ZIP (like JL-Mod Plus): the .jar / .jad / .kjx files inside a .zip are
	 * unpacked to the cache and installed like files picked one by one.
	 * A .zip that is itself a game (a renamed .jar, it has META-INF/MANIFEST.MF) stays as is.
	 */
	private static List<Uri> expandZips(android.content.Context context, List<Uri> uris) {
		List<Uri> out = new java.util.ArrayList<>();
		File base = new File(context.getCacheDir(), "zip-games");
		ru.playsoftware.j2meloader.util.FileUtils.deleteDirectory(base);
		int n = 0;
		for (Uri uri : uris) {
			String name = ru.playsoftware.j2meloader.util.FileUtils.getDisplayName(context, uri);
			if (name == null || !name.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
				out.add(uri);
				continue;
			}
			File dir = new File(base, String.valueOf(n++));
			List<Uri> games = new java.util.ArrayList<>();
			boolean isJar = false;
			try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(
					context.getContentResolver().openInputStream(uri))) {
				for (java.util.zip.ZipEntry e; (e = zip.getNextEntry()) != null; ) {
					String entry = e.getName();
					if (entry.equalsIgnoreCase("META-INF/MANIFEST.MF")) isJar = true;
					String file = entry.substring(entry.lastIndexOf('/') + 1);
					String low = file.toLowerCase(java.util.Locale.ROOT);
					if (e.isDirectory() || file.startsWith(".")
							|| !(low.endsWith(".jar") || low.endsWith(".jad") || low.endsWith(".kjx"))) {
						continue;
					}
					dir.mkdirs();
					File target = new File(dir, file);
					try (java.io.OutputStream os = new java.io.FileOutputStream(target)) {
						byte[] buf = new byte[65536];
						for (int r; (r = zip.read(buf)) > 0; ) os.write(buf, 0, r);
					}
					games.add(Uri.fromFile(target));
				}
			} catch (Exception e) {
				Log.w(TAG, "expandZips: " + uri, e);
			}
			if (isJar || games.isEmpty()) out.add(uri); else out.addAll(games);
		}
		return out;
	}

	private void onPickFileResult(List<Uri> picked) {
		if (picked == null || picked.isEmpty()) {
			return;
		}
		preferences.edit()
				.putString(PREF_LAST_PATH, picked.get(0).getPath())
				.apply();
		android.content.Context context = requireContext().getApplicationContext();
		new Thread(() -> {
			List<Uri> uris = expandZips(context, picked);
			FragmentActivity activity = getActivity();
			if (activity != null && !uris.isEmpty()) activity.runOnUiThread(() -> installUris(uris));
		}, "ExpandZips").start();
	}

	private void installUris(List<Uri> uris) {
		if (!isAdded()) {
			return;
		}
		if (uris.size() == 1) {
			InstallerDialog.newInstance(uris.get(0)).show(getParentFragmentManager(), "installer");
		} else {
			BatchInstallerDialog.newInstance(uris).show(getParentFragmentManager(), "installer");
		}
	}

	private void alertRename(AppItem item) {
		FragmentActivity activity = requireActivity();
		EditText editText = new EditText(activity);
		editText.setText(item.getTitle());
		float density = getResources().getDisplayMetrics().density;
		LinearLayout linearLayout = new LinearLayout(activity);
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT);
		int margin = (int) (density * 20);
		params.setMargins(margin, 0, margin, 0);
		linearLayout.addView(editText, params);
		int paddingVertical = (int) (density * 16);
		int paddingHorizontal = (int) (density * 8);
		editText.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);
		AlertDialog.Builder builder = new AlertDialog.Builder(activity)
				.setTitle(R.string.action_context_rename)
				.setView(linearLayout)
				.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
					String title = editText.getText().toString().trim();
					if (title.equals("")) {
						Toast.makeText(getActivity(), R.string.error, Toast.LENGTH_SHORT).show();
					} else {
						item.setTitle(title);
						appRepository.update(item);
					}
				})
				.setNegativeButton(android.R.string.cancel, null);
		builder.show();
	}

	private void alertDelete(AppItem item) {
		AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity())
				.setTitle(android.R.string.dialog_alert_title)
				.setMessage(R.string.message_delete)
				.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
					AppUtils.deleteApp(item);
					appRepository.delete(item);
				})
				.setNegativeButton(android.R.string.cancel, null);
		builder.show();
	}

	@Override
	public void onCreateContextMenu(@NonNull ContextMenu menu, @NonNull View v,
									ContextMenu.ContextMenuInfo menuInfo) {
		MenuInflater inflater = requireActivity().getMenuInflater();
		inflater.inflate(R.menu.context_main, menu);
		if (!ShortcutManagerCompat.isRequestPinShortcutSupported(requireContext())) {
			menu.findItem(R.id.action_context_shortcut).setVisible(false);
		}

		AppItem appItem = ((AppItemLayout.AppItemMenuInfo) menuInfo).appItem;
		menu.findItem(R.id.action_context_favorite).setTitle(
				collections().contains(GameCollections.FAVORITES, appItem.getPath())
						? R.string.favorite_remove : R.string.favorite_add);
		if (!new File(appItem.getPathExt() + Config.MIDLET_RES_FILE).exists()) {
			menu.findItem(R.id.action_context_reinstall).setVisible(false);
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AppItem appItem = ((AppItemLayout.AppItemMenuInfo) item.getMenuInfo()).appItem;
		int itemId = item.getItemId();
		if (itemId == R.id.action_context_shortcut) {
			requestAddShortcut(appItem);
		} else if (itemId == R.id.action_context_rename) {
			alertRename(appItem);
		} else if (itemId == R.id.action_context_icon) {
			showIconDialog(appItem);
		} else if (itemId == R.id.action_context_export) {
			exportItem = appItem;
			exportGameLauncher.launch(appItem.getTitle().replaceAll(
					ru.playsoftware.j2meloader.util.FileUtils.ILLEGAL_FILENAME_CHARS, "") + ".zip");
		} else if (itemId == R.id.action_context_collections) {
			showGameCollectionsDialog(appItem);
		} else if (itemId == R.id.action_context_favorite) {
			boolean fav = collections().contains(GameCollections.FAVORITES, appItem.getPath());
			collections().setMember(GameCollections.FAVORITES, appItem.getPath(), !fav);
			showCollection();
		} else if (itemId == R.id.action_context_settings) {
			Config.startApp(requireActivity(), appItem.getTitle(), appItem.getPathExt(), true);
		} else if (itemId == R.id.action_context_reinstall) {
			InstallerDialog.newInstance(appItem.getId()).show(getParentFragmentManager(), "installer");
		} else if (itemId == R.id.action_context_delete) {
			alertDelete(appItem);
		} else {
			return super.onContextItemSelected(item);
		}
		return true;
	}

	private void requestAddShortcut(AppItem appItem) {
		FragmentActivity activity = requireActivity();
		Bitmap bitmap = AppUtils.getIconBitmap(appItem);
		IconCompat icon;
		if (bitmap == null) {
			icon = IconCompat.createWithResource(activity, R.mipmap.ic_launcher);
		} else {
			int width = bitmap.getWidth();
			int height = bitmap.getHeight();
			ActivityManager am = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
			int iconSize = am.getLauncherLargeIconSize();
			Rect src;
			if (width > height) {
				int left = (width - height) / 2;
				src = new Rect(left, 0, left + height, height);
			} else if (width < height) {
				int top = (height - width) / 2;
				src = new Rect(0, top, width, top + width);
			} else {
				src = null;
			}
			Bitmap scaled = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888);
			Canvas canvas = new Canvas(scaled);
			canvas.drawBitmap(bitmap, src, new RectF(0, 0, iconSize, iconSize), null);
			icon = IconCompat.createWithBitmap(scaled);
		}
		String title = appItem.getTitle();
		Intent launchIntent = new Intent(Intent.ACTION_DEFAULT, Uri.parse(appItem.getPathExt()),
				activity, ConfigActivity.class);
		launchIntent.putExtra(KEY_MIDLET_NAME, title);
		ShortcutInfoCompat shortcut = new ShortcutInfoCompat.Builder(activity, title)
				.setIntent(launchIntent)
				.setShortLabel(title)
				.setIcon(icon)
				.build();
		ShortcutManagerCompat.requestPinShortcut(activity, shortcut, null);
	}

	@Override
	public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
		menuInflater.inflate(R.menu.main, menu);
		final MenuItem searchItem = menu.findItem(R.id.action_search);
		SearchView searchView = (SearchView) searchItem.getActionView();
		searchViewDisposable = Observable.create((ObservableOnSubscribe<String>) emitter ->
						searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
							@Override
							public boolean onQueryTextSubmit(String query) {
								emitter.onNext(query);
								return true;
							}

							@Override
							public boolean onQueryTextChange(String newText) {
								emitter.onNext(newText);
								return true;
							}
						})).debounce(300, TimeUnit.MILLISECONDS)
				.map(String::toLowerCase)
				.distinctUntilChanged()
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(charSequence -> adapter.getFilter().filter(charSequence));
		int type = preferences.getInt(PREF_APPS_VIEW, AppsListAdapter.LAYOUT_TYPE_GRID);
		if (type == AppsListAdapter.LAYOUT_TYPE_LIST) {
			menu.findItem(R.id.action_view).setIcon(R.drawable.ic_action_apps_view_grid);
		}
	}

	@SuppressLint("NotifyDataSetChanged")
	@Override
	public boolean onMenuItemSelected(MenuItem item) {
		FragmentActivity activity = requireActivity();
		int itemId = item.getItemId();
		if (itemId == R.id.action_import_game) {
			importGameLauncher.launch(new String[]{"application/zip",
					"application/x-zip-compressed", "application/octet-stream"});
		} else if (itemId == R.id.action_check_updates) {
			checkForUpdates();
		} else if (itemId == R.id.action_collections) {
			showCollectionsDialog();
		} else if (itemId == R.id.action_install_folder) {
			openFolderLauncher.launch(null);
		} else if (itemId == R.id.action_about) {
			AboutDialogFragment aboutDialogFragment = new AboutDialogFragment();
			aboutDialogFragment.show(getChildFragmentManager(), "about");
		} else if (itemId == R.id.action_profiles) {
			Intent intentProfiles = new Intent(activity, ProfilesActivity.class);
			startActivity(intentProfiles);
		} else if (item.getItemId() == R.id.action_settings) {
			startActivity(new Intent(activity, SettingsActivity.class));
		} else if (itemId == R.id.action_help) {
			HelpDialogFragment helpDialogFragment = new HelpDialogFragment();
			helpDialogFragment.show(getChildFragmentManager(), "help");
		} else if (itemId == R.id.action_save_log) {
			try {
				LogUtils.writeLog();
				Toast.makeText(activity, R.string.log_saved, Toast.LENGTH_SHORT).show();
			} catch (IOException e) {
				e.printStackTrace();
				Toast.makeText(activity, R.string.error, Toast.LENGTH_SHORT).show();
			}
		} else if (itemId == R.id.action_exit_app) {
			activity.finish();
		} else if (itemId == R.id.action_sort) {
			showSortDialog();
		} else if (itemId == R.id.action_view) {
			int viewType = adapter.getItemViewType(0);
			viewType = (viewType + 1) % 2;
			if (viewType == AppsListAdapter.LAYOUT_TYPE_LIST) {
				item.setIcon(R.drawable.ic_action_apps_view_grid);
			} else {
				item.setIcon(R.drawable.ic_action_apps_view_list);
			}
			int spanCount;
			if (viewType == AppsListAdapter.LAYOUT_TYPE_GRID) {
				spanCount = gridColumns(getResources().getConfiguration().screenWidthDp);
				if (itemDecoration != null) {
					binding.list.removeItemDecoration(itemDecoration);
				}
			} else {
				spanCount = 1;
				if (itemDecoration == null) {
					itemDecoration = new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL);
				}
				binding.list.addItemDecoration(itemDecoration);
			}
			layoutManager.setSpanCount(spanCount);
			adapter.setLayout(viewType);
			adapter.notifyDataSetChanged();
			preferences.edit().putInt(PREF_APPS_VIEW, viewType).apply();
		} else {
			return false;
		}
		return true;
	}

	private void showSortDialog() {
		int variant = preferences.getInt(PREF_APP_SORT, 0);
		FragmentActivity activity = requireActivity();
		AlertDialog.Builder builder = new AlertDialog.Builder(activity)
				.setTitle(R.string.pref_app_sort_title)
				.setAdapter(new SortAdapter(activity, variant), (d, v) -> setSort(v));
		builder.show();
	}

	private void setSort(int sortVariant) {
		if (preferences.getInt(PREF_APP_SORT, 0) == sortVariant) {
			sortVariant |= 0x80000000;
		}
		preferences.edit().putInt(PREF_APP_SORT, sortVariant).apply();
	}

	private void onDbUpdated(List<AppItem> items) {
		allItems = items;
		showCollection();
		if (appUri != null) {
			InstallerDialog.newInstance(appUri).show(getParentFragmentManager(), "installer");
			appUri = null;
		}
	}

	private static class SortAdapter extends ArrayAdapter<String> {
		private final int variant;
		private final Drawable drawableArrowDown;
		private final Drawable drawableArrowUp;

		public SortAdapter(Context context, int variant) {
			super(context,
					android.R.layout.simple_list_item_1,
					context.getResources().getStringArray(R.array.pref_app_sort_entries));
			this.variant = variant;
			drawableArrowDown = AppCompatResources.getDrawable(context, R.drawable.ic_arrow_down);
			drawableArrowUp = AppCompatResources.getDrawable(context, R.drawable.ic_arrow_up);
		}

		@NonNull
		@Override
		public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
			TextView tv = (TextView) super.getView(position, convertView, parent);
			if ((variant & 0x7FFFFFFF) == position) {
				TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(tv, null, null,
						variant >= 0 ? drawableArrowDown : drawableArrowUp, null);
			} else {
				tv.setCompoundDrawables(null, null, null, null);
			}
			return tv;
		}
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		binding = null;
	}

	@Override
	public void onDestroy() {
		if (searchViewDisposable != null) {
			searchViewDisposable.dispose();
		}
		super.onDestroy();
	}
}
