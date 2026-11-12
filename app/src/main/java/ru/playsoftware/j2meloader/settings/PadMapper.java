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

package ru.playsoftware.j2meloader.settings;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.keyboard.KeyMapper;

import ru.playsoftware.j2meloader.R;

/**
 * Controller and keyboard mapping like Portemon's "Gamepad and keyboard" panel: the buttons of
 * a controller (Buttons tab), the d-pad and right stick (Sticks tab) and keyboard keys
 * (Keyboard tab), each row "physical button → game key". Touch a row to choose its game key;
 * "Detect another input" adds any other button or key. Starts from JarNix's mapping.
 * Used in the game (quick menu) and in the game settings (KeyMapperActivity).
 */
public final class PadMapper {
	public interface Listener {
		/** The whole map changed (it is never null). */
		void onChanged(SparseIntArray map);
	}

	/** Controller buttons shown even when not assigned, in this order. */
	private static final int[] BUTTONS = {
			KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
			KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
			KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
			KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
			KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT,
			KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR,
			KeyEvent.KEYCODE_BUTTON_MODE, KeyEvent.KEYCODE_BACK,
			KeyEvent.KEYCODE_BUTTON_C, KeyEvent.KEYCODE_BUTTON_Z};
	private static final int[] STICKS = {
			KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
			KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
			KeyMapper.KEYCODE_RSTICK_UP, KeyMapper.KEYCODE_RSTICK_DOWN,
			KeyMapper.KEYCODE_RSTICK_LEFT, KeyMapper.KEYCODE_RSTICK_RIGHT};
	/** Game keys a button can send, in the chooser's order. */
	private static final int[] GAME_KEYS = {
			Canvas.KEY_UP, Canvas.KEY_DOWN, Canvas.KEY_LEFT, Canvas.KEY_RIGHT, Canvas.KEY_FIRE,
			Canvas.KEY_SOFT_LEFT, Canvas.KEY_SOFT_RIGHT,
			Canvas.KEY_NUM1, Canvas.KEY_NUM2, Canvas.KEY_NUM3, Canvas.KEY_NUM4, Canvas.KEY_NUM5,
			Canvas.KEY_NUM6, Canvas.KEY_NUM7, Canvas.KEY_NUM8, Canvas.KEY_NUM9, Canvas.KEY_STAR,
			Canvas.KEY_NUM0, Canvas.KEY_POUND,
			KeyMapper.SE_KEY_SPECIAL_GAMING_A, KeyMapper.SE_KEY_SPECIAL_GAMING_B,
			Canvas.KEY_CLEAR, Canvas.KEY_SEND, Canvas.KEY_END, KeyMapper.KEY_OPTIONS_MENU};
	private static final int NONE = Integer.MIN_VALUE;

	private final Context context;
	private final Listener listener;
	private final float dp;
	private SparseIntArray map;
	private int tab;
	private boolean detecting;
	private LinearLayout list;
	private TextView hint;
	private final MaterialButton[] tabs = new MaterialButton[3];

	public PadMapper(Context context, SparseIntArray start, Listener listener) {
		this.context = context;
		this.listener = listener;
		this.dp = context.getResources().getDisplayMetrics().density;
		this.map = start != null ? start.clone() : KeyMapper.getDefaultKeyMap();
	}

	private int color(int attr) {
		return MaterialColors.getColor(context, attr, 0xFF808080);
	}

	/** The whole panel content (scrolls by itself). */
	public View view() {
		LinearLayout root = new LinearLayout(context);
		root.setOrientation(LinearLayout.VERTICAL);

		MaterialButton auto = tonal(context.getString(R.string.pad_map_auto));
		auto.setOnClickListener(v -> {
			map = KeyMapper.getDefaultKeyMap();
			changed();
		});
		root.addView(auto, full(48));

		LinearLayout tabRow = new LinearLayout(context);
		int[] names = {R.string.pad_tab_buttons, R.string.pad_tab_sticks, R.string.pad_tab_keyboard};
		for (int i = 0; i < 3; i++) {
			MaterialButton b = tonal(context.getString(names[i]));
			int which = i;
			b.setOnClickListener(v -> {
				tab = which;
				fill();
			});
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) (48 * dp), 1);
			lp.leftMargin = lp.rightMargin = (int) (3 * dp);
			tabRow.addView(b, lp);
			tabs[i] = b;
		}
		LinearLayout.LayoutParams tlp = full(48);
		tlp.topMargin = (int) (8 * dp);
		root.addView(tabRow, tlp);

		hint = new TextView(context);
		hint.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant));
		hint.setPadding((int) (4 * dp), (int) (10 * dp), (int) (4 * dp), (int) (6 * dp));
		root.addView(hint);

		ScrollView scroll = new ScrollView(context);
		scroll.setVerticalScrollBarEnabled(false);
		list = new LinearLayout(context);
		list.setOrientation(LinearLayout.VERTICAL);
		scroll.addView(list);
		root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

		LinearLayout bottom = new LinearLayout(context);
		MaterialButton detect = tonal(context.getString(R.string.pad_detect));
		detect.setOnClickListener(v -> {
			detecting = true;
			hint.setText(R.string.pad_detecting);
		});
		MaterialButton reset = tonal(context.getString(R.string.quick_reset_short));
		reset.setOnClickListener(v -> {
			map = KeyMapper.getDefaultKeyMap();
			changed();
		});
		LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, (int) (48 * dp), 2);
		dlp.rightMargin = (int) (6 * dp);
		bottom.addView(detect, dlp);
		bottom.addView(reset, new LinearLayout.LayoutParams(0, (int) (48 * dp), 1));
		LinearLayout.LayoutParams blp = full(48);
		blp.topMargin = (int) (8 * dp);
		blp.bottomMargin = (int) (8 * dp);
		root.addView(bottom, blp);
		fill();
		return root;
	}

	private LinearLayout.LayoutParams full(int heightDp) {
		return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (heightDp * dp));
	}

	private MaterialButton tonal(String text) {
		MaterialButton b = new MaterialButton(context);
		b.setText(text);
		b.setAllCaps(false);
		b.setInsetTop(0);
		b.setInsetBottom(0);
		b.setMaxLines(1);
		b.setEllipsize(android.text.TextUtils.TruncateAt.END);
		b.setPadding((int) (8 * dp), 0, (int) (8 * dp), 0);
		return b;
	}

	private void changed() {
		listener.onChanged(map.clone());
		fill();
	}

	/** Rows of the current tab, two per line. */
	private void fill() {
		detecting = false;
		for (int i = 0; i < 3; i++) {
			boolean on = i == tab;
			tabs[i].setBackgroundTintList(android.content.res.ColorStateList.valueOf(color(on
					? androidx.appcompat.R.attr.colorPrimary
					: com.google.android.material.R.attr.colorSurfaceContainerHighest)));
			tabs[i].setTextColor(color(on ? com.google.android.material.R.attr.colorOnPrimary
					: com.google.android.material.R.attr.colorOnSurface));
		}
		hint.setText(tab == 1 ? R.string.pad_stick_note : R.string.pad_map_hint);
		list.removeAllViews();
		List<Integer> keys = new ArrayList<>();
		if (tab == 0) {
			for (int k : BUTTONS) keys.add(k);
			for (int i = 0; i < map.size(); i++) { // other controller buttons that were detected
				int k = map.keyAt(i);
				if (KeyEvent.isGamepadButton(k) && !keys.contains(k)) keys.add(k);
			}
		} else if (tab == 1) {
			for (int k : STICKS) keys.add(k);
		} else {
			for (int i = 0; i < map.size(); i++) {
				int k = map.keyAt(i);
				if (isKeyboard(k)) keys.add(k);
			}
			if (keys.isEmpty()) {
				hint.setText(R.string.pad_keyboard_empty);
			}
		}
		LinearLayout line = null;
		for (int i = 0; i < keys.size(); i++) {
			if (i % 2 == 0) {
				line = new LinearLayout(context);
				LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
						ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
				llp.bottomMargin = (int) (8 * dp);
				list.addView(line, llp);
			}
			LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, (int) (56 * dp), 1);
			rlp.leftMargin = rlp.rightMargin = (int) (3 * dp);
			line.addView(row(keys.get(i)), rlp);
		}
		if (keys.size() % 2 == 1) {
			line.addView(new View(context), new LinearLayout.LayoutParams(0, 1, 1));
		}
	}

	private static boolean isKeyboard(int k) {
		if (KeyMapper.isControllerKey(k) || k == KeyEvent.KEYCODE_BACK) return false;
		for (int s : STICKS) if (s == k) return false;
		return true;
	}

	/** One "button → game key" card. */
	private View row(int key) {
		LinearLayout r = new LinearLayout(context);
		r.setGravity(Gravity.CENTER_VERTICAL);
		int p = (int) (8 * dp);
		r.setPadding(p, 0, p, 0);
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(color(com.google.android.material.R.attr.colorSurfaceContainerHigh));
		bg.setCornerRadius(16 * dp);
		r.setBackground(bg);

		TextView badge = new TextView(context);
		badge.setText(buttonName(key));
		badge.setGravity(Gravity.CENTER);
		badge.setTextSize(13);
		badge.setMaxLines(1);
		int c = faceColor(key);
		badge.setTextColor(c != 0 ? c : color(com.google.android.material.R.attr.colorOnSurface));
		GradientDrawable ring = new GradientDrawable();
		boolean round = c != 0 || key == KeyEvent.KEYCODE_BUTTON_THUMBL || key == KeyEvent.KEYCODE_BUTTON_THUMBR
				|| key == KeyEvent.KEYCODE_BUTTON_C || key == KeyEvent.KEYCODE_BUTTON_Z;
		ring.setShape(round ? GradientDrawable.OVAL : GradientDrawable.RECTANGLE);
		if (!round) ring.setCornerRadius(10 * dp);
		ring.setStroke((int) (1.5f * dp), c != 0 ? c : color(com.google.android.material.R.attr.colorOutline));
		badge.setBackground(ring);
		badge.setPadding((int) (4 * dp), 0, (int) (4 * dp), 0);
		badge.setMinWidth((int) (40 * dp));
		r.addView(badge, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (40 * dp)));

		TextView arrow = new TextView(context);
		arrow.setText("→");
		arrow.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant));
		arrow.setPadding((int) (6 * dp), 0, (int) (2 * dp), 0);
		r.addView(arrow);

		TextView target = new TextView(context);
		int game = map.get(key, NONE);
		target.setText(game == NONE ? context.getString(R.string.pad_unassigned) : gameKeyName(game));
		target.setGravity(Gravity.CENTER);
		target.setMaxLines(1);
		target.setEllipsize(android.text.TextUtils.TruncateAt.END);
		target.setTextSize(15);
		target.setTextColor(color(game == NONE ? com.google.android.material.R.attr.colorOnSurfaceVariant
				: com.google.android.material.R.attr.colorOnSurface));
		r.addView(target, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		r.setOnClickListener(v -> choose(key));
		return r;
	}

	/** A, B, X, Y in the usual controller colors. */
	private static int faceColor(int key) {
		switch (key) {
			case KeyEvent.KEYCODE_BUTTON_A: return 0xFF6CC06C;
			case KeyEvent.KEYCODE_BUTTON_B: return 0xFFE0605A;
			case KeyEvent.KEYCODE_BUTTON_X: return 0xFF5A9BE0;
			case KeyEvent.KEYCODE_BUTTON_Y: return 0xFFE8C04A;
			default: return 0;
		}
	}

	private static String buttonName(int key) {
		switch (key) {
			case KeyEvent.KEYCODE_BUTTON_A: return "A";
			case KeyEvent.KEYCODE_BUTTON_B: return "B";
			case KeyEvent.KEYCODE_BUTTON_X: return "X";
			case KeyEvent.KEYCODE_BUTTON_Y: return "Y";
			case KeyEvent.KEYCODE_BUTTON_C: return "C";
			case KeyEvent.KEYCODE_BUTTON_Z: return "Z";
			case KeyEvent.KEYCODE_BUTTON_L1: return "LB";
			case KeyEvent.KEYCODE_BUTTON_R1: return "RB";
			case KeyEvent.KEYCODE_BUTTON_L2: return "LT";
			case KeyEvent.KEYCODE_BUTTON_R2: return "RT";
			case KeyEvent.KEYCODE_BUTTON_THUMBL: return "L3";
			case KeyEvent.KEYCODE_BUTTON_THUMBR: return "R3";
			case KeyEvent.KEYCODE_BUTTON_START: return "Start";
			case KeyEvent.KEYCODE_BUTTON_SELECT: return "Select";
			case KeyEvent.KEYCODE_BUTTON_MODE: return "Home";
			case KeyEvent.KEYCODE_BACK: return "Back";
			case KeyEvent.KEYCODE_DPAD_UP: return "✚ ↑";
			case KeyEvent.KEYCODE_DPAD_DOWN: return "✚ ↓";
			case KeyEvent.KEYCODE_DPAD_LEFT: return "✚ ←";
			case KeyEvent.KEYCODE_DPAD_RIGHT: return "✚ →";
			case KeyMapper.KEYCODE_RSTICK_UP: return "R ↑";
			case KeyMapper.KEYCODE_RSTICK_DOWN: return "R ↓";
			case KeyMapper.KEYCODE_RSTICK_LEFT: return "R ←";
			case KeyMapper.KEYCODE_RSTICK_RIGHT: return "R →";
			default:
				String n = KeyEvent.keyCodeToString(key);
				if (n.startsWith("KEYCODE_")) n = n.substring(8);
				if (n.startsWith("BUTTON_")) n = n.substring(7);
				return n.length() > 7 ? n.substring(0, 7) : n;
		}
	}

	private String gameKeyName(int game) {
		switch (game) {
			case Canvas.KEY_UP: return "↑";
			case Canvas.KEY_DOWN: return "↓";
			case Canvas.KEY_LEFT: return "←";
			case Canvas.KEY_RIGHT: return "→";
			case Canvas.KEY_FIRE: return "OK";
			case Canvas.KEY_SOFT_LEFT: return context.getString(R.string.key_soft_left);
			case Canvas.KEY_SOFT_RIGHT: return context.getString(R.string.key_soft_right);
			case Canvas.KEY_STAR: return "*";
			case Canvas.KEY_POUND: return "#";
			case KeyMapper.SE_KEY_SPECIAL_GAMING_A: return "A";
			case KeyMapper.SE_KEY_SPECIAL_GAMING_B: return "B";
			case Canvas.KEY_CLEAR: return context.getString(R.string.key_clear);
			case Canvas.KEY_SEND: return context.getString(R.string.key_send);
			case Canvas.KEY_END: return context.getString(R.string.key_end);
			case KeyMapper.KEY_OPTIONS_MENU: return context.getString(R.string.key_menu);
			default:
				if (game >= Canvas.KEY_NUM0 && game <= Canvas.KEY_NUM9) return String.valueOf((char) game);
				return String.valueOf(game);
		}
	}

	/** Which game key this button sends (or none). */
	private void choose(int key) {
		ChipGroup group = new ChipGroup(context);
		group.setSingleSelection(true);
		int pad = (int) (20 * dp);
		group.setPadding(pad, (int) (8 * dp), pad, 0);
		int now = map.get(key, NONE);
		androidx.appcompat.app.AlertDialog[] dialog = new androidx.appcompat.app.AlertDialog[1];
		List<Integer> options = new ArrayList<>();
		for (int g : GAME_KEYS) options.add(g);
		options.add(NONE);
		for (int g : options) {
			Chip chip = (Chip) android.view.LayoutInflater.from(context).inflate(R.layout.quick_chip, group, false);
			chip.setText(g == NONE ? context.getString(R.string.pad_unassigned) : gameKeyName(g));
			chip.setChecked(g == now);
			chip.setOnClickListener(v -> {
				if (g == NONE) map.delete(key); else map.put(key, g);
				changed();
				if (dialog[0] != null) dialog[0].dismiss();
			});
			group.addView(chip);
		}
		ScrollView scroll = new ScrollView(context);
		scroll.addView(group);
		dialog[0] = new MaterialAlertDialogBuilder(context)
				.setTitle(context.getString(R.string.pad_choose_key, buttonName(key)))
				.setView(scroll)
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	/** While "Detect another input" waits: the pressed key or button gets its row. */
	public boolean onKey(KeyEvent e) {
		if (!detecting) return false;
		int k = e.getKeyCode();
		if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_HOME) {
			return false;
		}
		if (e.getAction() == KeyEvent.ACTION_UP) detected(k);
		return true;
	}

	/** Sticks and analog triggers while detecting. */
	public boolean onMotion(MotionEvent e) {
		if (!detecting || (e.getSource() & android.view.InputDevice.SOURCE_JOYSTICK)
				!= android.view.InputDevice.SOURCE_JOYSTICK) {
			return false;
		}
		boolean[] held = KeyMapper.axesToKeys(e, false);
		for (int i = 0; i < held.length; i++) {
			if (held[i]) {
				detected(KeyMapper.AXIS_KEYS[i]);
				break;
			}
		}
		return true;
	}

	private void detected(int key) {
		detecting = false;
		boolean stick = false;
		for (int s : STICKS) if (s == key) stick = true;
		tab = stick ? 1 : isKeyboard(key) ? 2 : 0;
		fill();
		choose(key);
	}
}
