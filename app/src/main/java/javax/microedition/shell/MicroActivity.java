/*
 * Copyright 2015-2016 Nickolay Savchenko
 * Copyright 2017-2021 Nikita Shakarun
 * Copyright 2019-2023 Yury Kharchenko
 * Copyright 2022-2024 Arman Jussupgaliyev
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

package javax.microedition.shell;

import static ru.playsoftware.j2meloader.util.Constants.*;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.DigitsKeyListener;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.preference.PreferenceManager;

import org.acra.ACRA;
import org.acra.ErrorReporter;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Objects;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.ViewHandler;
import javax.microedition.lcdui.event.SimpleEvent;
import javax.microedition.lcdui.keyboard.VirtualKeyboard;
import javax.microedition.location.LocationProviderImpl;
import javax.microedition.util.ContextHolder;
import javax.microedition.util.VirtualClock;

import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;
import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.config.Filters;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.config.ProfilesManager;
import ru.playsoftware.j2meloader.config.ShaderInfo;
import ru.playsoftware.j2meloader.databinding.ActivityMicroBinding;
import ru.playsoftware.j2meloader.util.Constants;
import ru.playsoftware.j2meloader.util.LogUtils;

public class MicroActivity extends AppCompatActivity {
	private static final int ORIENTATION_DEFAULT = 0;
	private static final int ORIENTATION_AUTO = 1;
	private static final int ORIENTATION_PORTRAIT = 2;
	private static final int ORIENTATION_LANDSCAPE = 3;

	private Displayable current;
	private boolean visible;
	private boolean pauseSuppressed;
	private boolean pauseDisabled;
	private boolean actionBarEnabled;
	private boolean statusBarEnabled;
	private boolean cutoutsEnabled;
	private MicroLoader microLoader;
	private String appName;
	private InputMethodManager inputMethodManager;
	private boolean imeVisible;
	private int statusBarInset;
	private int toolbarHeight;
	private int menuKey;
	private String appPath;

	public ActivityMicroBinding binding;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		// in-game dialogs (quick settings...) in the same colors as the app
		if (sp.getBoolean(ru.playsoftware.j2meloader.base.BaseActivity.PREF_DYNAMIC_COLORS, true)) {
			com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this);
		}
		if (sp.getBoolean(PREF_BLACK_BACKGROUND, false)) {
			getTheme().applyStyle(R.style.ThemeOverlay_App_Dark, true);
		}
		lockNightMode();

		super.onCreate(savedInstanceState);
		ContextHolder.setCurrentActivity(this);

		binding = ActivityMicroBinding.inflate(getLayoutInflater());
		View view = binding.getRoot();
		setContentView(view);
		setSupportActionBar(binding.toolbar);

		actionBarEnabled = sp.getBoolean(PREF_TOOLBAR, false);
		statusBarEnabled = sp.getBoolean(PREF_STATUSBAR, false);
		cutoutsEnabled = sp.getBoolean(PREF_ADD_CUTOUT_AREA, true) &&
				Build.VERSION.SDK_INT >= Build.VERSION_CODES.P;
		setupInsets();
		if (sp.getBoolean(PREF_KEEP_SCREEN, false)) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
		ContextHolder.setVibration(sp.getBoolean(PREF_VIBRATION, true));
		Canvas.setScreenshotRawMode(sp.getBoolean(PREF_SCREENSHOT_SWITCH, false));
		Intent intent = getIntent();
		if (BuildConfig.FULL_EMULATOR) {
			appName = intent.getStringExtra(KEY_MIDLET_NAME);
			Uri data = intent.getData();
			if (data == null) {
				showErrorDialog("Invalid intent: app path is null");
				return;
			}
			appPath = data.toString();
		} else {
			appName = getTitle().toString();
			appPath = getApplicationInfo().dataDir + "/files/converted/midlet";
			File dir = new File(appPath);
			if (!dir.exists() && !dir.mkdirs()) {
				throw new RuntimeException("Can't access file system");
			}
		}
		String arguments = intent.getStringExtra(KEY_START_ARGUMENTS);
		if (arguments != null) {
			MidletSystem.setProperty("com.nokia.mid.cmdline", arguments);
			String[] arr = arguments.split(";");
			for (String s: arr) {
				if (s.length() == 0) {
					continue;
				}
				if (s.contains("=")) {
					int i = s.indexOf('=');
					String k = s.substring(0, i);
					String v = s.substring(i + 1);
					MidletSystem.setProperty(k, v);
				} else {
					MidletSystem.setProperty(s, "");
				}
			}
		}
		MidletSystem.setProperty("com.nokia.mid.cmdline.instance", "1");
		microLoader = new MicroLoader(this, appPath);
		if (!microLoader.init()) {
			Config.startApp(this, appName, appPath, true, arguments);
			finish();
			return;
		}
		microLoader.applyConfiguration();
		// from JarNix: the volume keys change the game's sound, not the ringer
		setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
		pauseDisabled = microLoader.isPauseDisabled();
		menuKey = microLoader.getMenuKeyCode();
		inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
		if (microLoader.shouldAskKeyboard()) {
			new AlertDialog.Builder(this)
					.setTitle(appName)
					.setMessage(R.string.ask_show_keyboard)
					.setCancelable(false)
					.setPositiveButton(android.R.string.yes, (d, w) -> {
						microLoader.setKeyboardChoice(true);
						startGame();
					})
					.setNegativeButton(android.R.string.no, (d, w) -> {
						microLoader.setKeyboardChoice(false);
						startGame();
					})
					.show();
		} else {
			startGame();
		}
	}

	/**
	 * From JarNix: on a phone, an exception in a game thread only ends that thread.
	 * Here it closed the whole app; now the thread ends and the error is shown on screen,
	 * so an endless "loading" is not left unexplained. Errors on the UI thread still crash.
	 */
	/** Called by auto rotation (UI thread): go landscape and remember it for this game. */
	public void onAutoRotation(int mode) {
		setOrientation(ORIENTATION_LANDSCAPE);
		microLoader.saveDetectedRotation(mode, ORIENTATION_LANDSCAPE);
	}

	private void installThreadErrorHandler() {
		Thread.UncaughtExceptionHandler crash = Thread.getDefaultUncaughtExceptionHandler();
		Thread uiThread = android.os.Looper.getMainLooper().getThread();
		Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
			if (t == uiThread && crash != null) {
				crash.uncaughtException(t, e);
				return;
			}
			android.util.Log.e("MicroActivity", "Error in game thread \"" + t.getName() + "\"", e);
			runOnUiThread(() -> Toast.makeText(this,
					getString(R.string.game_thread_error, t.getName(), String.valueOf(e)),
					Toast.LENGTH_LONG).show());
		});
	}

	private void startGame() {
		VirtualKeyboard vk = ContextHolder.getVk();
		int orientation = microLoader.getOrientation();
		if (vk != null) {
			vk.setView(binding.overlayView);
			binding.overlayView.addLayer(vk);
			if (vk.isPhone()) {
				orientation = ORIENTATION_PORTRAIT;
			}
		}
		setOrientation(orientation);
		installThreadErrorHandler();

		try {
			loadMIDlet();
		} catch (Exception e) {
			e.printStackTrace();
			showErrorDialog(e.toString());
		}
	}

	public void lockNightMode() {
		int current = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
		if (current == Configuration.UI_MODE_NIGHT_YES) {
			AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
		} else {
			AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		visible = true;
		// clear first: games often restart their music inside resumeApp()
		javax.microedition.media.Manager.setBackground(false);
		if (!pauseSuppressed) {
			MidletThread.resumeApp();
		}
	}

	@Override
	public void onPause() {
		hideSoftInput();
		if (!pauseSuppressed && !pauseDisabled) {
			visible = false;
			MidletThread.pauseApp();
			// some games ignore pauseApp() and keep playing: silence them here
			javax.microedition.media.Manager.setBackground(true);
		}
		super.onPause();
	}

	private void hideSoftInput() {
		if (inputMethodManager != null) {
			IBinder windowToken = binding.displayableContainer.getWindowToken();
			inputMethodManager.hideSoftInputFromWindow(windowToken, 0);
		}
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus && current instanceof Canvas) {
			hideSystemUI();
		}
	}

	@SuppressLint("SourceLockedOrientationActivity")
	private void setOrientation(int orientation) {
		switch (orientation) {
			case ORIENTATION_AUTO:
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
				break;
			case ORIENTATION_PORTRAIT:
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
				break;
			case ORIENTATION_LANDSCAPE:
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
				break;
			case ORIENTATION_DEFAULT:
			default:
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
				break;
		}
	}

	private void loadMIDlet() throws Exception {
		LinkedHashMap<String, String> midlets = microLoader.loadMIDletList();
		int size = midlets.size();
		String[] midletsNameArray = midlets.values().toArray(new String[0]);
		String[] midletsClassArray = midlets.keySet().toArray(new String[0]);
		if (size == 0) {
			throw new Exception("No MIDlets found");
		} else if (size == 1) {
			MidletThread.create(microLoader, midletsClassArray[0]);
		} else {
			showMidletDialog(midletsNameArray, midletsClassArray);
		}
	}

	private void showMidletDialog(String[] names, final String[] classes) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this)
				.setTitle(R.string.select_dialog_title)
				.setItems(names, (d, n) -> {
					String clazz = classes[n];
					ErrorReporter errorReporter = ACRA.getErrorReporter();
					String report = errorReporter.getCustomData(Constants.KEY_APPCENTER_ATTACHMENT);
					StringBuilder sb = new StringBuilder();
					if (report != null) {
						sb.append(report).append("\n");
					}
					sb.append("Begin app: ").append(names[n]).append(", ").append(clazz);
					errorReporter.putCustomData(Constants.KEY_APPCENTER_ATTACHMENT, sb.toString());
					MidletThread.create(microLoader, clazz);
					MidletThread.resumeApp();
				})
				.setOnCancelListener(d -> {
					d.dismiss();
					MidletThread.notifyDestroyed();
				});
		builder.show();
	}

	void showErrorDialog(String message) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setTitle(R.string.error)
				.setMessage(message)
				.setPositiveButton(android.R.string.ok, (d, w) -> MidletThread.notifyDestroyed());
		builder.setOnCancelListener(dialogInterface -> MidletThread.notifyDestroyed());
		builder.show();
	}

	private int getToolBarHeight() {
		int[] attrs = new int[]{androidx.appcompat.R.attr.actionBarSize};
		TypedArray ta = obtainStyledAttributes(attrs);
		int toolBarHeight = ta.getDimensionPixelSize(0, -1);
		ta.recycle();
		return toolBarHeight;
	}

	private void setupInsets() {
		EdgeToEdge.enable(
				this,
				SystemBarStyle.dark(ContextCompat.getColor(this, R.color.primary)),
				SystemBarStyle.dark(Color.BLACK)
		);

		ViewCompat.setOnApplyWindowInsetsListener(binding.midletFrame, (v, windowInsets) -> {
			int typeMask = WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime();
			if (!cutoutsEnabled) {
				typeMask |= WindowInsetsCompat.Type.displayCutout();
			}
			Insets insets = windowInsets.getInsets(typeMask);
			statusBarInset = insets.top;

			ViewGroup.LayoutParams params = binding.statusbarView.getLayoutParams();
			params.height = insets.top;
			binding.statusbarView.setLayoutParams(params);

			v.setPadding(insets.left, 0, insets.right, insets.bottom);
			updateOverlayLocation();
			return WindowInsetsCompat.CONSUMED;
		});

		// On API < 30 the IME insets are not dispatched
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
			getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(() -> {
				WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(binding.midletFrame);
				boolean visible = rootInsets != null
						&& rootInsets.isVisible(WindowInsetsCompat.Type.ime());
				if (visible == imeVisible) {
					return;
				}
				imeVisible = visible;
				if (current instanceof Canvas) {
					if (visible) {
						showSystemUI();
					} else {
						hideSystemUI();
					}
				} else { ViewCompat.requestApplyInsets(binding.midletFrame); }
			});
		}
	}

	private void updateOverlayLocation() {
		binding.overlayView.setLocation(0, statusBarInset + toolbarHeight);
	}

	private WindowInsetsControllerCompat getWindowInsetsController() {
		Window window = getWindow();
		return WindowCompat.getInsetsController(window, window.getDecorView());
	}

	private void hideSystemUI() {
		int typeMask = WindowInsetsCompat.Type.navigationBars();
		if (!statusBarEnabled) {
			typeMask |= WindowInsetsCompat.Type.statusBars();
		}

		WindowInsetsControllerCompat windowInsetsController = getWindowInsetsController();
		windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
		windowInsetsController.hide(typeMask);
		// For API < 30
		ViewCompat.requestApplyInsets(binding.midletFrame);
	}

	private void showSystemUI() {
		WindowInsetsControllerCompat windowInsetsController = getWindowInsetsController();
		windowInsetsController.show(WindowInsetsCompat.Type.systemBars());
		// For API < 30
		ViewCompat.requestApplyInsets(binding.midletFrame);
	}

	public void setCurrent(Displayable displayable) {
		ViewHandler.postEvent(new SetCurrentEvent(current, displayable));
		current = displayable;
	}

	public Displayable getCurrent() {
		return current;
	}

	public boolean isVisible() {
		return visible;
	}

	public void setPauseSuppressed(boolean pauseSuppressed) {
		this.pauseSuppressed = pauseSuppressed;
	}

	public void showExitConfirmation() {
		AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
		alertBuilder.setTitle(R.string.CONFIRMATION_REQUIRED)
				.setMessage(R.string.FORCE_CLOSE_CONFIRMATION)
				.setPositiveButton(android.R.string.ok, (d, w) -> {
					hideSoftInput();
					MidletThread.destroyApp();
				})
				.setNeutralButton(R.string.action_settings, (d, w) -> {
					hideSoftInput();
					Config.startApp(this, appName, appPath, true);
					MidletThread.destroyApp();
				})
				.setNegativeButton(android.R.string.cancel, null);
		alertBuilder.create().show();
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		if (event.getKeyCode() == KeyEvent.KEYCODE_MENU)
			if (current instanceof Canvas && binding.displayableContainer.dispatchKeyEvent(event)) {
				return true;
			} else if (event.getAction() == KeyEvent.ACTION_DOWN) {
				if (event.getRepeatCount() == 0) {
					event.startTracking();
					return true;
				} else if (event.isLongPress()) {
					return onKeyLongPress(event.getKeyCode(), event);
				}
			} else if (event.getAction() == KeyEvent.ACTION_UP) {
				return onKeyUp(event.getKeyCode(), event);
			}
		return super.dispatchKeyEvent(event);
	}

	@Override
	public void openOptionsMenu() {
		if (!actionBarEnabled && current instanceof Canvas) {
			showSystemUI();
		}
		super.openOptionsMenu();
	}

	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent event) {
		if (keyCode == menuKey || keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
			showExitConfirmation();
			return true;
		}
		return super.onKeyLongPress(keyCode, event);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_MENU) {
			return false;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if ((keyCode == menuKey || keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU)
				&& (event.getFlags() & (KeyEvent.FLAG_LONG_PRESS | KeyEvent.FLAG_CANCELED)) == 0) {
			showQuickPanel(); // Back and the menu key open the quick menu (it has everything)
			return true;
		}
		return super.onKeyUp(keyCode, event);
	}

	@Override
	public void onBackPressed() {
		// Intentionally overridden by empty due to support for back-key remapping.
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.midlet_displayable, menu);
		// everything else is in the quick menu now; the bar keeps only its button
		for (int i = 0; i < menu.size(); i++) menu.getItem(i).setVisible(false);
		MenuItem quick = menu.findItem(R.id.action_quick_settings);
		quick.setVisible(true);
		quick.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_exit_midlet) {
			showExitConfirmation();
		} else if (id == R.id.action_save_log) {
			saveLog();
		} else if (id == R.id.action_lock_orientation) {
			if (item.isChecked()) {
				VirtualKeyboard vk = ContextHolder.getVk();
				int orientation = vk != null && vk.isPhone() ? ORIENTATION_PORTRAIT : microLoader.getOrientation();
				setOrientation(orientation);
				item.setChecked(false);
			} else {
				item.setChecked(true);
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
			}
		} else if (id == R.id.action_ime_keyboard) {
			inputMethodManager.toggleSoftInputFromWindow(binding.displayableContainer.getWindowToken(),
					InputMethodManager.SHOW_FORCED, 0);
		} else if (id == R.id.action_take_screenshot) {
			takeScreenshot();
		} else if (id == R.id.action_limit_fps) {
			showLimitFpsDialog();
		} else if (id == R.id.action_game_speed) {
			showGameSpeedDialog();
		} else if (id == R.id.action_memory_editor) {
			MemoryEditor.show(this);
		} else if (id == R.id.action_quick_settings) {
			showQuickPanel();
		} else if (ContextHolder.getVk() != null) {
			// Handled only when virtual keyboard is enabled
			handleVkOptions(id);
		}
		return true;
	}

	private void handleVkOptions(int id) {
		VirtualKeyboard vk = ContextHolder.getVk();
		if (id == R.id.action_layout_edit_mode) {
			vk.setLayoutEditMode(VirtualKeyboard.LAYOUT_KEYS);
			Toast.makeText(this, R.string.layout_edit_mode, Toast.LENGTH_SHORT).show();
		} else if (id == R.id.action_layout_scale_mode) {
			vk.setLayoutEditMode(VirtualKeyboard.LAYOUT_SCALES);
			Toast.makeText(this, R.string.layout_scale_mode, Toast.LENGTH_SHORT).show();
		} else if (id == R.id.action_layout_edit_finish) {
			vk.setLayoutEditMode(VirtualKeyboard.LAYOUT_EOF);
			Toast.makeText(this, R.string.layout_edit_finished, Toast.LENGTH_SHORT).show();
			showSaveVkAlert(false);
		} else if (id == R.id.action_toggle_pad) {
			vk.togglePad();
			setOrientation(vk.isPhone() ? ORIENTATION_PORTRAIT : microLoader.getOrientation());
		} else if (id == R.id.action_layout_switch) {
			showSetLayoutDialog();
		} else if (id == R.id.action_hide_buttons) {
			showHideButtonDialog();
		}
	}

	@SuppressLint("CheckResult")
	private void takeScreenshot() {
		microLoader.takeScreenshot((Canvas) current, new SingleObserver<String>() {
			@Override
			public void onSubscribe(@NonNull Disposable d) {
			}

			@Override
			public void onSuccess(@NonNull String s) {
				Toast.makeText(MicroActivity.this, getString(R.string.screenshot_saved)
						+ " " + s, Toast.LENGTH_LONG).show();
				MediaScannerConnection.scanFile(MicroActivity.this, new String[]{s}, null, null);
			}

			@Override
			public void onError(@NonNull Throwable e) {
				e.printStackTrace();
				Toast.makeText(MicroActivity.this, R.string.error, Toast.LENGTH_SHORT).show();
			}
		});
	}

	private void saveLog() {
		try {
			LogUtils.writeLog();
			Toast.makeText(this, R.string.log_saved, Toast.LENGTH_SHORT).show();
		} catch (IOException e) {
			e.printStackTrace();
			Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show();
		}
	}

	private void showHideButtonDialog() {
		final VirtualKeyboard vk = ContextHolder.getVk();
		boolean[] states = vk.getKeysVisibility();
		boolean[] changed = states.clone();
		// only the keys of what is on screen (gamepad or keypad), each mode keeps its own
		int[] keys = vk.getModeKeys();
		String[] names = new String[keys.length];
		boolean[] checked = new boolean[keys.length];
		for (int i = 0; i < keys.length; i++) {
			names[i] = vk.getKeyName(keys[i]);
			checked[i] = states[keys[i]];
		}
		new AlertDialog.Builder(this)
				.setTitle(R.string.hide_buttons)
				.setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> changed[keys[which]] = isChecked)
				.setPositiveButton(android.R.string.ok, (dialog, which) -> {
					if (!Arrays.equals(states, changed)) {
						vk.setKeysVisibility(changed);
						showSaveVkAlert(true);
					}
				}).show();
	}

	private void showSaveVkAlert(boolean keepScreenPreferred) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.CONFIRMATION_REQUIRED);
		builder.setMessage(R.string.pref_vk_save_alert);
		builder.setNegativeButton(android.R.string.no, null);
		AlertDialog dialog = builder.create();

		final VirtualKeyboard vk = ContextHolder.getVk();
		if (vk.isPhone()) {
			AppCompatCheckBox cb = new AppCompatCheckBox(this);
			cb.setText(R.string.opt_save_screen_params);
			cb.setChecked(keepScreenPreferred);

			TypedValue out = new TypedValue();
			getTheme().resolveAttribute(androidx.appcompat.R.attr.dialogPreferredPadding, out, true);
			int paddingH = getResources().getDimensionPixelOffset(out.resourceId);
			int paddingT = getResources().getDimensionPixelOffset(androidx.appcompat.R.dimen.abc_dialog_padding_top_material);
			dialog.setView(cb, paddingH, paddingT, paddingH, 0);

			dialog.setButton(dialog.BUTTON_POSITIVE, getText(android.R.string.yes), (d, w) -> {
				if (cb.isChecked()) {
					vk.saveScreenParams();
				}
				vk.onLayoutChanged(VirtualKeyboard.TYPE_CUSTOM);
			});
		} else {
			dialog.setButton(dialog.BUTTON_POSITIVE, getText(android.R.string.yes), (d, w) ->
					ContextHolder.getVk().onLayoutChanged(VirtualKeyboard.TYPE_CUSTOM));
		}
		dialog.show();
	}

	private static final String PREF_PANEL_SECTION = "quick_panel_section";
	private View quickPanel;
	private boolean orientationLocked;

	/**
	 * Quick menu, opened with Back or the bar button. Portrait: a sheet from the bottom that
	 * leaves the game visible on top (drag its handle to make it taller, or down to close it).
	 * Landscape: a panel from the right. Screen / Controls / Game tabs; changes apply at once.
	 */
	private void showQuickPanel() {
		if (closeQuickPanel()) return;
		ViewGroup host = findViewById(android.R.id.content);
		float dp = getResources().getDisplayMetrics().density;
		boolean portrait = host.getHeight() >= host.getWidth();
		FrameLayout veil = new FrameLayout(this);
		veil.setBackgroundColor(0x33000000);
		veil.setOnClickListener(v -> closeQuickPanel()); // touching the game closes it

		LinearLayout panel = new LinearLayout(this);
		panel.setOrientation(LinearLayout.VERTICAL);
		panel.setClickable(true);
		panel.setElevation(8 * dp);
		float r = 28 * dp;
		android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
		bg.setColor(themeColor(com.google.android.material.R.attr.colorSurfaceContainer));
		bg.setCornerRadii(portrait ? new float[]{r, r, r, r, 0, 0, 0, 0} : new float[]{r, r, 0, 0, 0, 0, r, r});
		panel.setBackground(bg);
		int pad = (int) (16 * dp);
		panel.setPadding(pad, portrait ? 0 : pad, pad, 0);
		FrameLayout.LayoutParams plp;
		if (portrait) {
			plp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
					(int) (host.getHeight() * 0.62f), android.view.Gravity.BOTTOM);
			panel.addView(sheetHandle(panel, host, dp), new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT, (int) (24 * dp)));
		} else {
			plp = new FrameLayout.LayoutParams((int) Math.min(340 * dp, host.getWidth() * 0.55f),
					ViewGroup.LayoutParams.MATCH_PARENT, android.view.Gravity.END);
		}
		veil.addView(panel, plp);

		// header: game name, screenshot and Continue
		LinearLayout header = new LinearLayout(this);
		header.setGravity(android.view.Gravity.CENTER_VERTICAL);
		TextView title = new TextView(this);
		title.setText(appName);
		title.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);
		title.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface));
		title.setMaxLines(1);
		title.setEllipsize(TextUtils.TruncateAt.END);
		header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		if (current instanceof Canvas) {
			ImageView shot = new ImageView(this);
			shot.setImageResource(R.drawable.ic_q_shot);
			shot.setColorFilter(themeColor(com.google.android.material.R.attr.colorOnSurface));
			shot.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
			int ip = (int) (9 * dp);
			shot.setPadding(ip, ip, ip, ip);
			shot.setBackground(round(themeColor(com.google.android.material.R.attr.colorSurfaceContainerHighest), 20 * dp));
			shot.setContentDescription(getString(R.string.take_screenshot));
			shot.setOnClickListener(v -> {
				closeQuickPanel();
				takeScreenshot();
			});
			LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams((int) (40 * dp), (int) (40 * dp));
			slp.rightMargin = (int) (8 * dp);
			header.addView(shot, slp);
		}
		com.google.android.material.button.MaterialButton resume = new com.google.android.material.button.MaterialButton(this);
		resume.setText(R.string.quick_resume);
		resume.setIconResource(R.drawable.ic_home_play);
		resume.setOnClickListener(v -> closeQuickPanel());
		header.addView(resume);
		panel.addView(header);

		// tabs: Screen / Controls / Game, as one rounded segmented bar
		LinearLayout tabs = new LinearLayout(this);
		tabs.setBackground(round(themeColor(com.google.android.material.R.attr.colorSurfaceContainerHighest), 22 * dp));
		int tp = (int) (4 * dp);
		tabs.setPadding(tp, tp, tp, tp);
		int[] tabNames = {R.string.quick_section_screen, R.string.quick_section_controls, R.string.quick_section_game};
		int[] tabIcons = {R.drawable.ic_q_fit, R.drawable.ic_q_pad, R.drawable.ic_nav_games};
		TextView[] tabViews = new TextView[3];
		for (int i = 0; i < 3; i++) {
			TextView t = new TextView(this);
			t.setText(tabNames[i]);
			t.setGravity(android.view.Gravity.CENTER);
			t.setTextSize(13);
			t.setMaxLines(1);
			t.setEllipsize(TextUtils.TruncateAt.END);
			t.setCompoundDrawablePadding((int) (6 * dp));
			t.setPadding((int) (6 * dp), 0, (int) (6 * dp), 0);
			tabViews[i] = t;
			tabs.addView(t, new LinearLayout.LayoutParams(0, (int) (36 * dp), 1));
		}
		LinearLayout.LayoutParams tabsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT);
		tabsLp.topMargin = (int) (10 * dp);
		tabsLp.bottomMargin = (int) (10 * dp);
		panel.addView(tabs, tabsLp);

		android.widget.ScrollView scroll = new android.widget.ScrollView(this);
		scroll.setVerticalScrollBarEnabled(false);
		LinearLayout content = new LinearLayout(this);
		content.setOrientation(LinearLayout.VERTICAL);
		scroll.addView(content);
		panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

		com.google.android.material.button.MaterialButton exit = new com.google.android.material.button.MaterialButton(
				this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
		exit.setText(R.string.quick_exit_game);
		exit.setOnClickListener(v -> {
			closeQuickPanel();
			showExitConfirmation();
		});
		LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT);
		elp.topMargin = (int) (6 * dp);
		elp.bottomMargin = (int) (10 * dp);
		panel.addView(exit, elp);

		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
		int onColor = themeColor(com.google.android.material.R.attr.colorOnSurface);
		int offColor = themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant);
		int selBg = themeColor(com.google.android.material.R.attr.colorSurface);
		Pick select = which -> {
			for (int i = 0; i < 3; i++) {
				boolean on = i == which;
				TextView t = tabViews[i];
				t.setBackground(on ? round(selBg, 18 * dp) : null);
				t.setTextColor(on ? onColor : offColor);
				t.setTypeface(null, on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
				android.graphics.drawable.Drawable d = ContextCompat.getDrawable(this, tabIcons[i]);
				if (d != null) {
					d = d.mutate();
					d.setTint(on ? onColor : offColor);
					d.setBounds(0, 0, (int) (18 * dp), (int) (18 * dp));
				}
				t.setCompoundDrawablesRelative(d, null, null, null);
			}
			sp.edit().putInt(PREF_PANEL_SECTION, which).apply();
			fillSection(content, which, dp);
			scroll.scrollTo(0, 0);
		};
		for (int i = 0; i < 3; i++) {
			int which = i;
			tabViews[i].setOnClickListener(v -> select.pick(which));
		}
		select.pick(Math.max(0, Math.min(2, sp.getInt(PREF_PANEL_SECTION, 0))));

		quickPanel = veil;
		host.addView(veil, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));
		veil.setAlpha(0);
		veil.animate().alpha(1).setDuration(160).start();
		if (portrait) panel.setTranslationY(plp.height);
		else panel.setTranslationX(plp.width);
		panel.animate().translationX(0).translationY(0).setDuration(220)
				.setInterpolator(new android.view.animation.DecelerateInterpolator(2)).start();
	}

	/** The bar on top of the portrait sheet: drag it up to make the sheet taller, down to close it. */
	@SuppressLint("ClickableViewAccessibility")
	private View sheetHandle(LinearLayout panel, ViewGroup host, float dp) {
		FrameLayout area = new FrameLayout(this);
		View bar = new View(this);
		bar.setBackground(round(themeColor(com.google.android.material.R.attr.colorOutline), 2 * dp));
		area.addView(bar, new FrameLayout.LayoutParams((int) (32 * dp), (int) (4 * dp), android.view.Gravity.CENTER));
		area.setOnTouchListener(new View.OnTouchListener() {
			private float startY;
			private int startH;

			@Override
			public boolean onTouch(View v, android.view.MotionEvent e) {
				ViewGroup.LayoutParams lp = panel.getLayoutParams();
				int h = host.getHeight();
				switch (e.getActionMasked()) {
					case android.view.MotionEvent.ACTION_DOWN:
						startY = e.getRawY();
						startH = lp.height;
						break;
					case android.view.MotionEvent.ACTION_MOVE:
						lp.height = (int) Math.max(h * 0.2f, Math.min(h * 0.94f, startH + startY - e.getRawY()));
						panel.setLayoutParams(lp);
						break;
					case android.view.MotionEvent.ACTION_UP:
					case android.view.MotionEvent.ACTION_CANCEL:
						if (lp.height < h * 0.35f) closeQuickPanel();
						break;
				}
				return true;
			}
		});
		return area;
	}

	/** @return true if the panel was open */
	private boolean closeQuickPanel() {
		if (quickPanel == null) return false;
		ViewGroup parent = (ViewGroup) quickPanel.getParent();
		if (parent != null) parent.removeView(quickPanel);
		quickPanel = null;
		ProfilesManager.saveConfig(microLoader.params);
		return true;
	}

	private int themeColor(int attr) {
		return com.google.android.material.color.MaterialColors.getColor(this, attr, 0xFF808080);
	}

	private static android.graphics.drawable.GradientDrawable round(int color, float radius) {
		android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
		d.setColor(color);
		d.setCornerRadius(radius);
		return d;
	}

	/** The cards of one tab; every change is applied at once and saved for the game. */
	private void fillSection(LinearLayout box, int section, float dp) {
		box.removeAllViews();
		VirtualKeyboard vk = ContextHolder.getVk();
		ProfileModel p = microLoader.params;
		if (section == 0) {
			LinearLayout g = group(box, R.string.quick_group_size, dp);
			// the game's screen size: games read it once at start, so it needs a restart
			final int[][] sizes = {{128, 128}, {128, 160}, {176, 208}, {176, 220}, {240, 320},
					{320, 240}, {240, 400}, {360, 640}, {640, 360}};
			String[] sizeNames = new String[sizes.length];
			int sizeNow = -1;
			for (int i = 0; i < sizes.length; i++) {
				sizeNames[i] = sizes[i][0] + "×" + sizes[i][1];
				if (sizes[i][0] == p.screenWidth && sizes[i][1] == p.screenHeight) sizeNow = i;
			}
			label(g, R.string.quick_resolution, dp);
			int sizeWas = sizeNow;
			g.addView(chips(sizeNames, sizeNow, w -> {
				if (w == sizeWas) return;
				p.screenWidth = sizes[w][0];
				p.screenHeight = sizes[w][1];
				ProfilesManager.saveConfig(p);
				new AlertDialog.Builder(this)
						.setTitle(R.string.quick_resolution)
						.setMessage(R.string.quick_resolution_restart)
						.setPositiveButton(R.string.quick_restart_now, (d, x) -> {
							closeQuickPanel();
							hideSoftInput();
							// the settings screen (another process) starts the game again
							startActivity(new Intent(Intent.ACTION_DEFAULT, Uri.parse(appPath), this,
									ru.playsoftware.j2meloader.config.ConfigActivity.class)
									.putExtra(Constants.KEY_MIDLET_NAME, appName)
									.putExtra(ru.playsoftware.j2meloader.config.ConfigActivity.KEY_RESTART, true));
							MidletThread.destroyApp();
						})
						.setNegativeButton(R.string.quick_restart_later, null)
						.show();
			}, dp));
			label(g, R.string.quick_scale, dp);
			g.addView(chips(getResources().getStringArray(R.array.pref_scale_type_entries), p.screenScaleType, w -> {
				p.screenScaleType = w;
				p.screenScaleRatio = 100; // the whole screen, without an old zoom
				Canvas.setScale(p.screenGravity, w, p.screenScaleRatio);
				applyQuickChange();
			}, dp));
			label(g, R.string.pref_screen_gravity, dp);
			g.addView(chips(getResources().getStringArray(R.array.pref_screen_gravity_entries), p.screenGravity, w -> {
				p.screenGravity = w;
				Canvas.setScale(w, p.screenScaleType, p.screenScaleRatio);
				applyQuickChange();
			}, dp));

			g = group(box, R.string.quick_group_image, dp);
			label(g, R.string.quick_filter, dp);
			if (p.graphicsMode == 1) {
				// the game runs in its own process: use its work folder, like when the game starts
				File dir = new File(microLoader.getShadersDir());
				Filters.installBundled(this, dir);
				java.util.ArrayList<ShaderInfo> list = Filters.list(dir);
				list.add(0, null); // no filter
				String[] names = new String[list.size()];
				names[0] = getString(R.string.identity_filter);
				for (int i = 1; i < names.length; i++) names[i] = list.get(i).toString();
				g.addView(chips(names, Math.max(0, list.indexOf(Canvas.getShaderFilter())), w -> {
					ShaderInfo shader = list.get(w);
					if (shader != null) {
						shader.dir = dir.getPath() + "/";
						shader.values = Filters.defaultValues(shader);
					}
					p.shader = shader;
					Canvas.setShaderFilter(shader);
					applyQuickChange();
				}, dp));
			} else {
				TextView note = new TextView(this);
				note.setText(R.string.quick_filter_gl_only);
				note.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
				note.setPadding((int) (14 * dp), 0, (int) (14 * dp), (int) (8 * dp));
				g.addView(note);
			}
			rowSwitch(g, R.string.quick_smooth, p.screenFilter, dp, on -> {
				p.screenFilter = on;
				Canvas.setFilterBitmap(on);
				applyQuickChange();
			});
			rowSwitch(g, R.string.PREF_FRAME_BLEND, p.frameBlend, dp, on -> {
				p.frameBlend = on;
				Canvas.setFrameBlend(on);
				applyQuickChange();
			});
			rowSwitch(g, R.string.PREF_AUTO_FIT, p.autoFit, dp, on -> {
				p.autoFit = on;
				Canvas.setAutoFit(on);
				applyQuickChange();
			});

			g = group(box, R.string.quick_group_rotation, dp);
			label(g, R.string.PREF_SCREEN_ROTATION, dp);
			g.addView(chips(getResources().getStringArray(R.array.PREF_SCREEN_ROTATION_ENTRIES), p.screenRotation, w -> {
				p.screenRotation = w;
				Canvas.setScreenRotation(w);
				applyQuickChange();
			}, dp));
			label(g, R.string.PREF_ORIENTATION, dp);
			g.addView(chips(getResources().getStringArray(R.array.PREF_ORIENTATION_ENTRIES), p.orientation, w -> {
				p.orientation = w;
				ProfilesManager.saveConfig(p);
				if (!orientationLocked) setOrientation(vk != null && vk.isPhone() ? ORIENTATION_PORTRAIT : w);
			}, dp));
			rowSwitch(g, R.string.action_lock_orientation, orientationLocked, dp, on -> {
				orientationLocked = on;
				if (on) {
					setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
				} else {
					VirtualKeyboard k = ContextHolder.getVk();
					setOrientation(k != null && k.isPhone() ? ORIENTATION_PORTRAIT : microLoader.getOrientation());
				}
			});

			g = group(box, R.string.quick_group_bars, dp);
			rowSwitch(g, R.string.quick_top_bar, actionBarEnabled, dp, this::setActionBarEnabled);
			rowSwitch(g, R.string.PREF_SHOW_FPS, p.showFps, dp, on -> {
				p.showFps = on;
				Canvas.setShowFps(on);
				applyQuickChange();
			});
		} else if (section == 1) {
			LinearLayout g = group(box, R.string.quick_group_keys, dp);
			rowSwitch(g, R.string.quick_keys_on_screen, vk != null && !vk.isUserHidden(), dp, on -> {
				if (on) showKeyboardNow();
				else if (ContextHolder.getVk() != null) ContextHolder.getVk().setUserHidden(true);
			});
			if (vk != null) {
				LinearLayout row = new LinearLayout(this);
				int rp = (int) (10 * dp);
				row.setPadding(rp, (int) (4 * dp), rp, rp);
				tile(row, R.drawable.ic_q_pad, R.string.vk_toggle_pad, dp, () -> {
					vk.setUserHidden(false);
					vk.togglePad();
				});
				tile(row, R.drawable.ic_q_move, R.string.quick_edit_controls, dp, () -> {
					closeQuickPanel();
					startControlsEditor();
				});
				tile(row, R.drawable.ic_q_hide, R.string.hide_buttons, dp, this::showHideButtonDialog);
				g.addView(row);
				rowAction(g, R.string.layout_switch, dp, () -> {
					closeQuickPanel();
					showSetLayoutDialog();
				});
			}
			if (inputMethodManager != null) {
				rowAction(g, R.string.action_keyboard_ime, dp, () -> {
					closeQuickPanel();
					inputMethodManager.toggleSoftInputFromWindow(binding.displayableContainer.getWindowToken(),
							InputMethodManager.SHOW_FORCED, 0);
				});
			}
			if (vk == null) return;

			g = group(box, R.string.quick_group_style, dp);
			g.addView(chips(getResources().getStringArray(R.array.button_style_entries), vk.getButtonStyle(), w -> {
				vk.setButtonStyle(w);
				if (vk.getButtonStyle() != w) {
					Toast.makeText(this, R.string.button_style_no_skin, Toast.LENGTH_LONG).show();
				}
				PreferenceManager.getDefaultSharedPreferences(this).edit()
						.putString(VirtualKeyboard.PREF_BUTTON_STYLE, String.valueOf(vk.getButtonStyle())).apply();
			}, dp));
			label(g, R.string.quick_opacity, dp);
			com.google.android.material.slider.Slider slider = new com.google.android.material.slider.Slider(this);
			slider.setValueFrom(0);
			slider.setValueTo(255);
			slider.setStepSize(1);
			slider.setValue(vk.getAlpha());
			slider.setLabelBehavior(com.google.android.material.slider.LabelFormatter.LABEL_GONE);
			slider.addOnChangeListener((s, value, user) -> vk.setAlpha((int) value));
			g.addView(slider);
			rowSwitch(g, R.string.PREF_VK_FEEDBACK, p.vkFeedback, dp, on -> {
				p.vkFeedback = on;
				ProfilesManager.saveConfig(p);
			});
			rowAction(g, R.string.quick_reset_layout, dp, vk::resetModeLayout);
		} else {
			LinearLayout g = group(box, R.string.quick_group_speed, dp);
			label(g, R.string.PREF_GAME_SPEED, dp);
			final double[] speeds = {0.25, 0.5, 1, 2, 3, 4, 8, 16};
			final String[] labels = {"0.25×", "0.5×", "1×", "2×", "3×", "4×", "8×", "16×"};
			int speed = 2;
			for (int i = 0; i < speeds.length; i++) {
				if (speeds[i] == VirtualClock.getSpeed()) speed = i;
			}
			g.addView(chips(labels, speed, w -> {
				VirtualClock.setSpeed(speeds[w]);
				if (speeds[w] != 1) Toast.makeText(this, R.string.game_speed_hint, Toast.LENGTH_SHORT).show();
			}, dp));
			label(g, R.string.PREF_LIMIT_FPS, dp);
			final int[] limits = {0, 15, 30, 60};
			int limit = Canvas.getLimitFps();
			int checked = 4; // "Other..."
			for (int i = 0; i < limits.length; i++) {
				if (limits[i] == limit) checked = i;
			}
			g.addView(chips(new String[]{getString(R.string.unlimited), "15", "30", "60", getString(R.string.quick_other)},
					checked, w -> {
						if (w < limits.length) {
							microLoader.setLimitFps(limits[w]);
						} else {
							closeQuickPanel();
							showLimitFpsDialog();
						}
					}, dp));

			g = group(box, R.string.quick_group_tools, dp);
			rowAction(g, R.string.memory_editor, dp, () -> {
				closeQuickPanel();
				MemoryEditor.show(this);
			});
			rowAction(g, R.string.save_log, dp, this::saveLog);
		}
	}

	/** A rounded card with a small colored title, like the groups of the mockup. */
	private LinearLayout group(LinearLayout box, int title, float dp) {
		LinearLayout g = new LinearLayout(this);
		g.setOrientation(LinearLayout.VERTICAL);
		g.setBackground(round(themeColor(com.google.android.material.R.attr.colorSurface), 18 * dp));
		g.setPadding(0, 0, 0, (int) (4 * dp));
		TextView t = new TextView(this);
		t.setText(title);
		t.setAllCaps(true);
		t.setTextSize(12);
		t.setLetterSpacing(0.04f);
		t.setTypeface(null, android.graphics.Typeface.BOLD);
		t.setTextColor(themeColor(androidx.appcompat.R.attr.colorPrimary));
		t.setPadding((int) (14 * dp), (int) (12 * dp), (int) (14 * dp), (int) (2 * dp));
		g.addView(t);
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT);
		lp.bottomMargin = (int) (8 * dp);
		box.addView(g, lp);
		return g;
	}

	private void label(LinearLayout g, int text, float dp) {
		TextView t = new TextView(this);
		t.setText(text);
		t.setTextSize(15);
		t.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface));
		t.setPadding((int) (14 * dp), (int) (10 * dp), (int) (14 * dp), (int) (2 * dp));
		g.addView(t);
	}

	/** A row that does something when touched. */
	private void rowAction(LinearLayout g, int text, float dp, Runnable action) {
		TextView t = new TextView(this);
		t.setText(text);
		t.setTextSize(15);
		t.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface));
		t.setGravity(android.view.Gravity.CENTER_VERTICAL);
		t.setPadding((int) (14 * dp), 0, (int) (14 * dp), 0);
		t.setMinHeight((int) (48 * dp));
		TypedValue out = new TypedValue();
		getTheme().resolveAttribute(android.R.attr.selectableItemBackground, out, true);
		t.setBackgroundResource(out.resourceId);
		t.setOnClickListener(v -> action.run());
		g.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT));
	}

	/** A small square button with an icon over its name (the 3 big actions of Controls). */
	private void tile(LinearLayout row, int icon, int text, float dp, Runnable action) {
		LinearLayout t = new LinearLayout(this);
		t.setOrientation(LinearLayout.VERTICAL);
		int p = (int) (10 * dp);
		t.setPadding(p, p, p, p);
		t.setMinimumHeight((int) (72 * dp));
		t.setBackground(round(themeColor(com.google.android.material.R.attr.colorSecondaryContainer), 14 * dp));
		int fg = themeColor(com.google.android.material.R.attr.colorOnSecondaryContainer);
		ImageView i = new ImageView(this);
		i.setImageResource(icon);
		i.setColorFilter(fg);
		t.addView(i, new LinearLayout.LayoutParams((int) (22 * dp), (int) (22 * dp)));
		TextView l = new TextView(this);
		l.setText(text);
		l.setTextColor(fg);
		l.setTextSize(12);
		l.setMaxLines(2);
		l.setEllipsize(TextUtils.TruncateAt.END);
		l.setPadding(0, (int) (6 * dp), 0, 0);
		t.addView(l);
		t.setOnClickListener(v -> action.run());
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
		lp.leftMargin = lp.rightMargin = (int) (3 * dp);
		row.addView(t, lp);
	}

	private interface OnOff {
		void set(boolean on);
	}

	private void rowSwitch(LinearLayout box, int text, boolean checked, float dp, OnOff action) {
		com.google.android.material.materialswitch.MaterialSwitch sw =
				new com.google.android.material.materialswitch.MaterialSwitch(this);
		sw.setText(text);
		sw.setChecked(checked);
		sw.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface));
		sw.setMinHeight((int) (48 * dp));
		sw.setPadding((int) (14 * dp), 0, (int) (14 * dp), 0);
		sw.setOnCheckedChangeListener((v, on) -> action.set(on));
		box.addView(sw, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT));
	}

	private interface Pick {
		void pick(int which);
	}

	/** One-choice filter chips that wrap to the next line (fit any width). */
	private View chips(String[] names, int selected, Pick pick, float dp) {
		com.google.android.material.chip.ChipGroup group = new com.google.android.material.chip.ChipGroup(this);
		group.setSingleSelection(true);
		group.setSelectionRequired(true);
		group.setChipSpacingVertical((int) (2 * dp));
		group.setPadding((int) (14 * dp), 0, (int) (14 * dp), (int) (6 * dp));
		for (int i = 0; i < names.length; i++) {
			com.google.android.material.chip.Chip c = (com.google.android.material.chip.Chip)
					getLayoutInflater().inflate(R.layout.quick_chip, group, false);
			c.setText(names[i]);
			c.setChecked(i == selected);
			int which = i;
			c.setOnClickListener(v -> pick.pick(which));
			group.addView(c);
		}
		return group;
	}

	private View editorBar;

	/**
	 * Controls editor like Portemon: a bar on top (drag it from ⠿), touch a piece of the
	 * controls to pick it (the d-pad, the 4 buttons, L, R...), drag it to move it whole,
	 * - / + to resize it, Hide, Reset and Done.
	 */
	private void startControlsEditor() {
		VirtualKeyboard vk = ContextHolder.getVk();
		if (vk == null) return;
		vk.setUserHidden(false);
		vk.setLayoutEditMode(VirtualKeyboard.LAYOUT_GROUPS);
		Toast.makeText(this, R.string.quick_editor_hint, Toast.LENGTH_LONG).show();
		ViewGroup host = findViewById(android.R.id.content);
		float dp = getResources().getDisplayMetrics().density;
		LinearLayout bar = new LinearLayout(this);
		bar.setGravity(android.view.Gravity.CENTER_VERTICAL);
		android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
		bg.setColor(0xE6000000 | (themeColor(com.google.android.material.R.attr.colorSurfaceContainerHigh) & 0xFFFFFF));
		bg.setCornerRadius(24 * dp);
		bar.setBackground(bg);
		int pad = (int) (6 * dp);
		bar.setPadding(pad, pad, pad, pad);
		TextView handle = new TextView(this);
		handle.setText("⠿");
		handle.setTextSize(22);
		handle.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSurface));
		handle.setGravity(android.view.Gravity.CENTER);
		bar.addView(handle, new LinearLayout.LayoutParams((int) (36 * dp), (int) (44 * dp)));
		handle.setOnTouchListener(new View.OnTouchListener() {
			private float sx, sy, bx, by;

			@Override
			public boolean onTouch(View v, android.view.MotionEvent e) {
				if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
					sx = e.getRawX();
					sy = e.getRawY();
					bx = bar.getX();
					by = bar.getY();
				} else if (e.getActionMasked() == android.view.MotionEvent.ACTION_MOVE) {
					bar.setX(Math.max(0, Math.min(host.getWidth() - bar.getWidth(), bx + e.getRawX() - sx)));
					bar.setY(Math.max(0, Math.min(host.getHeight() - bar.getHeight(), by + e.getRawY() - sy)));
				}
				return true;
			}
		});
		// Piece: moves the whole d-pad / the 4 buttons...; Free: moves one button on its own
		com.google.android.material.button.MaterialButton mode = editorButton(bar,
				getString(vk.isFreeMove() ? R.string.quick_move_free : R.string.quick_move_piece), dp, null);
		mode.setOnClickListener(v -> {
			vk.setFreeMove(!vk.isFreeMove());
			mode.setText(vk.isFreeMove() ? R.string.quick_move_free : R.string.quick_move_piece);
		});
		editorButton(bar, "−", dp, () -> vk.scaleEditedGroup(-0.1f));
		editorButton(bar, "+", dp, () -> vk.scaleEditedGroup(0.1f));
		editorButton(bar, getString(R.string.quick_hide_piece), dp, vk::hideEditedGroup);
		editorButton(bar, getString(R.string.quick_reset_short), dp, vk::resetModeLayout);
		com.google.android.material.button.MaterialButton done = editorButton(bar, getString(R.string.quick_done), dp, () -> {
			vk.finishGroupEditing();
			ViewGroup parent = (ViewGroup) editorBar.getParent();
			if (parent != null) parent.removeView(editorBar);
			editorBar = null;
		});
		done.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
				themeColor(androidx.appcompat.R.attr.colorPrimary)));
		done.setTextColor(themeColor(com.google.android.material.R.attr.colorOnPrimary));
		editorBar = bar;
		FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
				ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL);
		lp.topMargin = (int) (8 * dp);
		host.addView(bar, lp);
	}

	/** Filled button in the secondary container color (Material 3 "tonal"). */
	private com.google.android.material.button.MaterialButton tonalButton() {
		com.google.android.material.button.MaterialButton b = new com.google.android.material.button.MaterialButton(this);
		b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
				themeColor(com.google.android.material.R.attr.colorSecondaryContainer)));
		b.setTextColor(themeColor(com.google.android.material.R.attr.colorOnSecondaryContainer));
		return b;
	}

	private com.google.android.material.button.MaterialButton editorButton(LinearLayout bar, String text,
																			  float dp, Runnable action) {
		com.google.android.material.button.MaterialButton b = tonalButton();
		b.setText(text);
		b.setAllCaps(false);
		b.setMinWidth(0);
		b.setMinimumWidth((int) (44 * dp));
		b.setPadding((int) (12 * dp), 0, (int) (12 * dp), 0);
		if (action != null) b.setOnClickListener(v -> action.run());
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
				(int) (44 * dp));
		lp.leftMargin = lp.rightMargin = (int) (3 * dp);
		bar.addView(b, lp);
		return b;
	}

	/** Shows the keys again, or turns the on-screen keyboard on if this game had it off. */
	private void showKeyboardNow() {
		VirtualKeyboard vk = ContextHolder.getVk();
		if (vk != null) {
			vk.setUserHidden(false);
			return;
		}
		microLoader.params.showKeyboard = true;
		ProfilesManager.saveConfig(microLoader.params);
		vk = new VirtualKeyboard(microLoader.params);
		vk.setUserHidden(false);
		ContextHolder.setVk(vk);
		vk.setView(binding.overlayView);
		binding.overlayView.addLayer(vk);
		if (current instanceof Canvas) {
			((Canvas) current).attachOverlay();
		}
		invalidateOptionsMenu();
	}

	/** Shows a quick settings change on the running game and saves it for next time. */
	private void applyQuickChange() {
		if (current instanceof Canvas) {
			((Canvas) current).refreshDisplay();
		}
		ProfilesManager.saveConfig(microLoader.params);
	}

	/** Top bar on or off right away (it is the same option as in Settings). */
	private void setActionBarEnabled(boolean enabled) {
		actionBarEnabled = enabled;
		PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean(PREF_TOOLBAR, enabled).apply();
		if (!(current instanceof Canvas)) return;
		ActionBar actionBar = Objects.requireNonNull(getSupportActionBar());
		LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) binding.toolbar.getLayoutParams();
		if (enabled) {
			String title = current.getTitle();
			actionBar.setTitle(title == null ? appName : title);
			actionBar.show();
			toolbarHeight = (int) (getToolBarHeight() / 1.5);
			layoutParams.height = toolbarHeight;
		} else {
			actionBar.hide();
			toolbarHeight = 0;
			hideSystemUI();
		}
		binding.toolbar.setLayoutParams(layoutParams);
		updateOverlayLocation();
		invalidateOptionsMenu();
	}

	private void showSetLayoutDialog() {
		final VirtualKeyboard vk = ContextHolder.getVk();
		AlertDialog.Builder builder = new AlertDialog.Builder(this)
				.setTitle(R.string.layout_switch)
				.setSingleChoiceItems(R.array.PREF_VK_TYPE_ENTRIES, vk.getLayout(), null)
				.setPositiveButton(android.R.string.ok, (d, w) -> {
					vk.setLayout(((AlertDialog) d).getListView().getCheckedItemPosition());
					if (vk.isPhone()) {
						setOrientation(ORIENTATION_PORTRAIT);
					} else {
						setOrientation(microLoader.getOrientation());
					}
				});
		builder.show();
	}

	private void showGameSpeedDialog() {
		// like JL-Mod Plus: slow motion too
		final double[] speeds = {0.25, 0.5, 1, 2, 3, 4, 8, 16};
		final String[] labels = {"0.25×", "0.5×", "1×", "2×", "3×", "4×", "8×", "16×"};
		int checked = 2;
		for (int i = 0; i < speeds.length; i++) {
			if (speeds[i] == VirtualClock.getSpeed()) {
				checked = i;
			}
		}
		new AlertDialog.Builder(this)
				.setTitle(R.string.PREF_GAME_SPEED)
				.setSingleChoiceItems(labels, checked, (dialog, which) -> {
					VirtualClock.setSpeed(speeds[which]);
					dialog.dismiss();
					if (speeds[which] != 1) {
						Toast.makeText(this, R.string.game_speed_hint, Toast.LENGTH_LONG).show();
					}
				})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	private void showLimitFpsDialog() {
		EditText editText = new EditText(this);
		editText.setHint(R.string.unlimited);
		editText.setInputType(InputType.TYPE_CLASS_NUMBER);
		editText.setKeyListener(DigitsKeyListener.getInstance("0123456789"));
		editText.setMaxLines(1);
		editText.setSingleLine(true);
		float density = getResources().getDisplayMetrics().density;
		LinearLayout linearLayout = new LinearLayout(this);
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT);
		int margin = (int) (density * 20);
		params.setMargins(margin, 0, margin, 0);
		linearLayout.addView(editText, params);
		int paddingVertical = (int) (density * 16);
		int paddingHorizontal = (int) (density * 8);
		editText.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);
		new AlertDialog.Builder(this)
				.setTitle(R.string.PREF_LIMIT_FPS)
				.setView(linearLayout)
				.setPositiveButton(android.R.string.ok, (d, w) -> {
					Editable text = editText.getText();
					int fps = 0;
					try {
						fps = TextUtils.isEmpty(text) ? 0 : Integer.parseInt(text.toString().trim());
					} catch (NumberFormatException ignored) {
					}
					microLoader.setLimitFps(fps);
				})
				.setNegativeButton(android.R.string.cancel, null)
				.setNeutralButton(R.string.reset, ((d, which) -> microLoader.setLimitFps(-1)))
				.show();
	}

	@Override
	public boolean onContextItemSelected(@NonNull MenuItem item) {
		if (current instanceof Form) {
			((Form) current).contextMenuItemSelected(item);
		}

		return super.onContextItemSelected(item);
	}

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		ContextHolder.notifyOnActivityResult(requestCode, resultCode, data);
	}

	public String getAppName() {
		return appName;
	}

	public void toast(@StringRes int message) {
		runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
	}

	private class SetCurrentEvent extends SimpleEvent {
		private final Displayable current;
		private final Displayable next;

		private SetCurrentEvent(Displayable current, Displayable next) {
			this.current = current;
			this.next = next;
		}

		@Override
		public void process() {
			closeOptionsMenu();
			if (current != null) {
				current.clearDisplayableView();
			}
			if (next instanceof Alert) {
				return;
			}
			binding.displayableContainer.removeAllViews();
			ActionBar actionBar = Objects.requireNonNull(getSupportActionBar());
			LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) binding.toolbar.getLayoutParams();
			int toolbarPadding = binding.toolbar.getPaddingTop();
			toolbarHeight = 0;
			if (next instanceof Canvas) {
				hideSystemUI();
				if (!actionBarEnabled) {
					actionBar.hide();
				} else {
					final String title = next.getTitle();
					actionBar.setTitle(title == null ? appName : title);
					toolbarHeight = (int) (getToolBarHeight() / 1.5);
					layoutParams.height = toolbarHeight;
				}
			} else {
				showSystemUI();
				actionBar.show();
				final String title = next != null ? next.getTitle() : null;
				actionBar.setTitle(title == null ? appName : title);
				toolbarHeight = getToolBarHeight();
				layoutParams.height = toolbarHeight;
			}
			updateOverlayLocation();
			binding.toolbar.setLayoutParams(layoutParams);
			binding.toolbar.setPadding(0, toolbarPadding, 0, 0);
			invalidateOptionsMenu();
			if (next != null) {
				binding.displayableContainer.addView(next.getDisplayableView());
			}
		}
	}

	@Override
	protected void onDestroy() {
		// Stop the MIDlet thread explicitly (fixes stuck audio)
		MidletThread.destroyApp();
		binding = null;
		super.onDestroy();
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == 1) {
			synchronized (LocationProviderImpl.permissionLock) {
				LocationProviderImpl.permissionLock.notify();
			}
			LocationProviderImpl.permissionResult = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
		}
	}
}
