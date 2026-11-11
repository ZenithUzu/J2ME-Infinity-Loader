/*
 * Copyright 2012 Kulikov Dmitriy
 * Copyright 2017-2018 Nikita Shakarun
 * Copyright 2021 Yury Kharchenko
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
package javax.microedition.lcdui.keyboard;

import static javax.microedition.lcdui.keyboard.KeyMapper.SE_KEY_SPECIAL_GAMING_A;
import static javax.microedition.lcdui.keyboard.KeyMapper.SE_KEY_SPECIAL_GAMING_B;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.View;

import androidx.annotation.NonNull;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.graphics.CanvasWrapper;
import javax.microedition.lcdui.overlay.Overlay;
import javax.microedition.shell.MicroActivity;
import javax.microedition.util.ContextHolder;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.config.ProfilesManager;
import ru.playsoftware.j2meloader.util.FileUtils;

public class VirtualKeyboard implements Overlay, Runnable {
	private static final String TAG = VirtualKeyboard.class.getSimpleName();

	private static final String ARROW_LEFT = "\u2190";
	private static final String ARROW_UP = "\u2191";
	private static final String ARROW_RIGHT = "\u2192";
	private static final String ARROW_DOWN = "\u2193";
	private static final String ARROW_UP_LEFT = "\u2196";
	private static final String ARROW_UP_RIGHT = "\u2197";
	private static final String ARROW_DOWN_LEFT = "\u2199";
	private static final String ARROW_DOWN_RIGHT = "\u2198";
	// JarNix style labels for the four directions
	private static final String TRI_LEFT = "\u25C0";
	private static final String TRI_UP = "\u25B2";
	private static final String TRI_RIGHT = "\u25B6";
	private static final String TRI_DOWN = "\u25BC";

	private static final int LAYOUT_SIGNATURE = 0x564B4C00;
	private static final int LAYOUT_VERSION = 3;
	public static final int LAYOUT_EOF = -1;
	public static final int LAYOUT_KEYS = 0;
	public static final int LAYOUT_SCALES = 1;
	@SuppressWarnings("unused")
	public static final int LAYOUT_COLORS = 2;
	public static final int LAYOUT_TYPE = 3;

	private static final int OVAL_SHAPE = 0;
	private static final int RECT_SHAPE = 1;
	public static final int ROUND_RECT_SHAPE = 2;

	public static final int TYPE_CUSTOM = 0;
	private static final int TYPE_PHONE = 1;
	private static final int TYPE_PHONE_ARROWS = 2;
	private static final int TYPE_NUM_ARR = 3;
	private static final int TYPE_ARR_NUM = 4;
	private static final int TYPE_NUMBERS = 5;
	private static final int TYPE_ARROWS = 6;
	private static final int TYPE_GAMEPAD = 7;
	/** JarNix keypad: d-pad with OK on the left, numbers on the right, soft keys above (default) */
	private static final int TYPE_JARNIX = 8;

	private static final float PHONE_KEY_ROWS = 5;
	private static final float PHONE_KEY_SCALE_X = 2.0f;
	private static final float PHONE_KEY_SCALE_Y = 0.75f;
	private static final long[] REPEAT_INTERVALS = {200, 400, 128, 128, 128, 128, 128};

	private static final int SCREEN = -1;
	private static final int KEY_NUM1 = 0;
	private static final int KEY_NUM2 = 1;
	private static final int KEY_NUM3 = 2;
	private static final int KEY_NUM4 = 3;
	private static final int KEY_NUM5 = 4;
	private static final int KEY_NUM6 = 5;
	private static final int KEY_NUM7 = 6;
	private static final int KEY_NUM8 = 7;
	private static final int KEY_NUM9 = 8;
	private static final int KEY_NUM0 = 9;
	private static final int KEY_STAR = 10;
	private static final int KEY_POUND = 11;
	private static final int KEY_SOFT_LEFT = 12;
	private static final int KEY_SOFT_RIGHT = 13;
	private static final int KEY_D = 14;
	private static final int KEY_C = 15;
	private static final int KEY_UP_LEFT = 16;
	private static final int KEY_UP = 17;
	private static final int KEY_UP_RIGHT = 18;
	private static final int KEY_LEFT = 19;
	private static final int KEY_RIGHT = 20;
	private static final int KEY_DOWN_LEFT = 21;
	private static final int KEY_DOWN = 22;
	private static final int KEY_DOWN_RIGHT = 23;
	private static final int KEY_FIRE = 24;
	private static final int KEY_A = 25;
	private static final int KEY_B = 26;
	private static final int KEY_MENU = 27;
	private static final int KEY_STICK = 28;
	private static final int KEY_TOGGLE = 29;
	private static final int KEYBOARD_SIZE = 30;
	/** not a real key code: identifies the on-screen stick in saved layouts */
	private static final int STICK_KEY_CODE = -0x5354;
	private static final int TOGGLE_KEY_CODE = -0x544F;
	/** The keyboard's look before JarNix's: a game still on it gets the new one. */
	private static final int[] OLD_DEFAULT_COLORS = {0xD0D0D0, 0x000080, 0x000080, 0xFFFFFF, 0xFFFFFF};

	private static final float SCALE_SNAP_RADIUS = 0.05f;

	private static final int FEEDBACK_DURATION = 50;

	private final float[] keyScales = {
			1, 1,
			1, 1,
			1, 1,
			1, 1,
			1, 1,
			1, 1,
			1, 1,
	};
	private final int[][] keyScaleGroups = {{
			KEY_UP_LEFT,
			KEY_UP,
			KEY_UP_RIGHT,
			KEY_LEFT,
			KEY_RIGHT,
			KEY_DOWN_LEFT,
			KEY_DOWN,
			KEY_DOWN_RIGHT
	}, {
			KEY_SOFT_LEFT,
			KEY_SOFT_RIGHT
	}, {
			KEY_A,
			KEY_B,
			KEY_C,
			KEY_D,
	}, {
			KEY_NUM1,
			KEY_NUM2,
			KEY_NUM3,
			KEY_NUM4,
			KEY_NUM5,
			KEY_NUM6,
			KEY_NUM7,
			KEY_NUM8,
			KEY_NUM9,
			KEY_NUM0,
			KEY_STAR,
			KEY_POUND
	}, {
			KEY_FIRE
	}, {
			KEY_MENU,
			KEY_TOGGLE
	}, {
			KEY_STICK
	}};
	private final VirtualKey[] keypad = new VirtualKey[KEYBOARD_SIZE];
	// the average user usually has no more than 10 fingers...
	private final VirtualKey[] associatedKeys = new VirtualKey[10];
	private final int[] snapStack = new int[KEYBOARD_SIZE];

	private final Handler handler;
	private final File saveFile;
	/** exists while the layout is (or was edited from) the gamepad layout */
	private final File padLabelsFile;
	/** gamepad layout: keys are drawn with the skin pictures (PadSkin) when there are any */
	private boolean padMode;
	private final RectF skinRect = new RectF();
	private static final int[] DPAD_KEYS = {KEY_UP_LEFT, KEY_UP, KEY_UP_RIGHT, KEY_LEFT,
			KEY_RIGHT, KEY_DOWN_LEFT, KEY_DOWN, KEY_DOWN_RIGHT};
	private final ProfileModel settings;
	private final RectF virtualScreen = new RectF();

	private Canvas target;
	private View overlayView;
	private boolean obscuresVirtualScreen;
	private boolean visible = true;
	/** hidden because a game controller or physical keyboard is in use; a touch brings it back */
	private boolean hiddenByController;
	/** hidden from the quick settings: no keys at all until shown again (remembered per game) */
	private boolean userHidden;
	/** Button look (Settings / quick settings): Portemon modern or outline, game colors, or skin pictures. */
	public static final String PREF_BUTTON_STYLE = "pref_button_style";
	public static final int STYLE_MODERN = PortemonStyle.MODERN;
	public static final int STYLE_OUTLINE = PortemonStyle.OUTLINE;
	public static final int STYLE_CLASSIC = 2;
	public static final int STYLE_SKIN = 3;
	private int buttonStyle;
	private PortemonStyle portemon;
	private final File hiddenFile;
	private int layoutEditMode = LAYOUT_EOF;
	private int editedIndex;
	private float offsetX;
	private float offsetY;
	private float prevScaleX;
	private float prevScaleY;
	private RectF screen;
	private float keySize =
			Math.min(ContextHolder.getDisplayWidth(), ContextHolder.getDisplayHeight()) / 6.0f;
	private float snapRadius;
	private int layoutVariant;

	public VirtualKeyboard(ProfileModel settings) {
		this.settings = settings;
		this.saveFile = new File(settings.dir + Config.MIDLET_KEY_LAYOUT_FILE);
		this.padLabelsFile = new File(saveFile.getPath() + ".gamepad");
		this.hiddenFile = new File(saveFile.getPath() + ".hidden");
		this.userHidden = hiddenFile.exists();
		setButtonStyle(readButtonStyle());

		for (int i = KEY_NUM1; i < 9; i++) {
			keypad[i] = new VirtualKey(Canvas.KEY_NUM1 + i, Integer.toString(1 + i));
		}

		keypad[KEY_NUM0] = new VirtualKey(Canvas.KEY_NUM0, "0");
		keypad[KEY_STAR] = new VirtualKey(Canvas.KEY_STAR, "*");
		keypad[KEY_POUND] = new VirtualKey(Canvas.KEY_POUND, "#");

		keypad[KEY_SOFT_LEFT] = new VirtualKey(Canvas.KEY_SOFT_LEFT, "L");
		keypad[KEY_SOFT_RIGHT] = new VirtualKey(Canvas.KEY_SOFT_RIGHT, "R");

		keypad[KEY_A] = new VirtualKey(SE_KEY_SPECIAL_GAMING_A, "A");
		keypad[KEY_B] = new VirtualKey(SE_KEY_SPECIAL_GAMING_B, "B");
		keypad[KEY_C] = new VirtualKey(Canvas.KEY_END, "C");
		keypad[KEY_D] = new VirtualKey(Canvas.KEY_SEND, "D");

		keypad[KEY_UP_LEFT] = new DualKey(Canvas.KEY_UP, Canvas.KEY_LEFT, ARROW_UP_LEFT);
		keypad[KEY_UP] = new VirtualKey(Canvas.KEY_UP, TRI_UP);
		keypad[KEY_UP_RIGHT] = new DualKey(Canvas.KEY_UP, Canvas.KEY_RIGHT, ARROW_UP_RIGHT);

		keypad[KEY_LEFT] = new VirtualKey(Canvas.KEY_LEFT, TRI_LEFT);
		keypad[KEY_RIGHT] = new VirtualKey(Canvas.KEY_RIGHT, TRI_RIGHT);

		keypad[KEY_DOWN_LEFT] = new DualKey(Canvas.KEY_DOWN, Canvas.KEY_LEFT, ARROW_DOWN_LEFT);
		keypad[KEY_DOWN] = new VirtualKey(Canvas.KEY_DOWN, TRI_DOWN);
		keypad[KEY_DOWN_RIGHT] = new DualKey(Canvas.KEY_DOWN, Canvas.KEY_RIGHT, ARROW_DOWN_RIGHT);

		keypad[KEY_FIRE] = new VirtualKey(Canvas.KEY_FIRE, "OK");
		keypad[KEY_MENU] = new MenuKey();
		keypad[KEY_STICK] = new StickKey();
		keypad[KEY_TOGGLE] = new ToggleKey();
		useJarnixColors();

		layoutVariant = readLayoutType();

		if (layoutVariant == -1) {
			layoutVariant = settings.vkType;
			if (layoutVariant == TYPE_CUSTOM) {
				layoutVariant = TYPE_JARNIX;
			}
		}
		resetLayout(layoutVariant);
		if (layoutVariant == TYPE_CUSTOM) {
			try {
				readLayout();
			} catch (IOException e) {
				e.printStackTrace();
				resetLayout(TYPE_JARNIX);
				layoutVariant = TYPE_JARNIX;
				saveLayout();
			}
		}
		HandlerThread thread = new HandlerThread("MidletVirtualKeyboard");
		thread.start();
		handler = new Handler(thread.getLooper());
	}

	/** A game still on the old default colors gets JarNix's look (dark keys, white text and border). */
	private void useJarnixColors() {
		ProfileModel p = settings;
		int[] now = {p.vkBgColor, p.vkFgColor, p.vkBgColorSelected, p.vkFgColorSelected, p.vkOutlineColor};
		if (!Arrays.equals(now, OLD_DEFAULT_COLORS)) return;
		p.vkBgColor = ProfileModel.JARNIX_VK_BG;
		p.vkFgColor = ProfileModel.JARNIX_VK_FG;
		p.vkBgColorSelected = ProfileModel.JARNIX_VK_BG_SELECTED;
		p.vkFgColorSelected = ProfileModel.JARNIX_VK_FG_SELECTED;
		p.vkOutlineColor = ProfileModel.JARNIX_VK_OUTLINE;
	}

	/**
	 * Gamepad <-> keys (the "Mando" / "123" key and the in-game menu). Each side remembers the
	 * layout it had, edited (custom) ones too: the custom layout is kept in its own copy.
	 */
	public void togglePad() {
		File memo = new File(saveFile.getPath() + ".toggle");
		boolean toPad = !padMode;
		int next = toPad ? TYPE_GAMEPAD : TYPE_JARNIX;
		try (DataInputStream in = new DataInputStream(new FileInputStream(memo))) {
			int variant = in.readInt();
			boolean pad = in.readBoolean();
			if (pad == toPad) next = variant;
		} catch (IOException ignored) {
		}
		try (java.io.DataOutputStream out = new java.io.DataOutputStream(new java.io.FileOutputStream(memo))) {
			out.writeInt(layoutVariant);
			out.writeBoolean(padMode);
		} catch (IOException e) {
			Log.w(TAG, "togglePad: ", e);
		}
		File mine = new File(saveFile.getPath() + (padMode ? ".pad" : ".keys"));
		File theirs = new File(saveFile.getPath() + (toPad ? ".pad" : ".keys"));
		try {
			if (layoutVariant == TYPE_CUSTOM) FileUtils.copyFileUsingChannel(saveFile, mine);
			if (next == TYPE_CUSTOM) {
				if (!theirs.exists()) {
					next = toPad ? TYPE_GAMEPAD : TYPE_JARNIX;
				} else {
					FileUtils.copyFileUsingChannel(theirs, saveFile);
					// tells updateLabels which kind of custom layout it is
					if (toPad) padLabelsFile.createNewFile(); else padLabelsFile.delete();
				}
			}
		} catch (IOException e) {
			Log.w(TAG, "togglePad: ", e);
			next = toPad ? TYPE_GAMEPAD : TYPE_JARNIX;
		}
		setLayout(next);
	}

	public void onLayoutChanged(int variant) {
		if (variant == TYPE_CUSTOM && isPhone()) {
			float min = screen.width();
			float max = screen.height();
			if (min > max) {
				float tmp = max;
				max = min;
				min = tmp;
			}

			float oldSize = min / 6.0f;
			float newSize = Math.min(oldSize, max / 12.0f);
			float s = oldSize / newSize;
			for (int i = 0; i < keyScales.length; i++) {
				keyScales[i] *= s;
			}
		}
		layoutVariant = variant;
		saveLayout();
		if (target != null && target.isShown()) {
			target.updateSize();
		}
	}

	private void resetLayout(int variant) {
		updateLabels(variant);
		setSnap(KEY_STICK, SCREEN, RectSnap.INT_SOUTHWEST, false); // only the gamepad shows it
		setSnap(KEY_TOGGLE, SCREEN, RectSnap.INT_NORTH, false); // gamepad <-> keys, see below
		switch (variant) {
			case TYPE_JARNIX:
				// like JarNix: d-pad (OK in the middle) bottom left, numbers bottom right,
				// left soft key, the gamepad switch and right soft key in a row above them
				Arrays.fill(keyScales, 1);

				setSnap(KEY_DOWN_LEFT, SCREEN, RectSnap.INT_SOUTHWEST, true);
				setSnap(KEY_DOWN, KEY_DOWN_LEFT, RectSnap.EXT_EAST, true);
				setSnap(KEY_DOWN_RIGHT, KEY_DOWN, RectSnap.EXT_EAST, true);
				setSnap(KEY_LEFT, KEY_DOWN_LEFT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_RIGHT, KEY_DOWN_RIGHT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_UP_RIGHT, KEY_RIGHT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_UP, KEY_UP_RIGHT, RectSnap.EXT_WEST, true);
				setSnap(KEY_UP_LEFT, KEY_UP, RectSnap.EXT_WEST, true);
				setSnap(KEY_FIRE, KEY_DOWN_RIGHT, RectSnap.EXT_NORTHWEST, true);
				setSnap(KEY_POUND, SCREEN, RectSnap.INT_SOUTHEAST, true);
				setSnap(KEY_NUM0, KEY_POUND, RectSnap.EXT_WEST, true);
				setSnap(KEY_STAR, KEY_NUM0, RectSnap.EXT_WEST, true);
				setSnap(KEY_NUM7, KEY_STAR, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM8, KEY_NUM7, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM9, KEY_NUM8, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM4, KEY_NUM7, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM5, KEY_NUM4, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM6, KEY_NUM5, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM1, KEY_NUM4, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM2, KEY_NUM1, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM3, KEY_NUM2, RectSnap.EXT_EAST, true);
				setSnap(KEY_SOFT_LEFT, KEY_UP_LEFT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_TOGGLE, KEY_UP, RectSnap.EXT_NORTH, true);
				setSnap(KEY_SOFT_RIGHT, KEY_NUM3, RectSnap.EXT_NORTH, true);

				setSnap(KEY_D, KEY_NUM1, RectSnap.EXT_NORTH, false);
				setSnap(KEY_C, KEY_NUM2, RectSnap.EXT_NORTH, false);
				setSnap(KEY_A, SCREEN, RectSnap.INT_NORTHWEST, false);
				setSnap(KEY_B, SCREEN, RectSnap.INT_NORTHEAST, false);
				setSnap(KEY_MENU, KEY_UP_RIGHT, RectSnap.EXT_NORTH, false);
				break;
			case TYPE_GAMEPAD:
				// like a game controller: d-pad bottom left with the stick above it, A B X Y
				// diamond bottom right, L and R in the top corners, Select / menu / Start bottom center
				Arrays.fill(keyScales, 1);
				keyScales[keyScales.length - 2] = 2; // stick: 2 x 2 keys
				keyScales[keyScales.length - 1] = 2;

				setSnap(KEY_DOWN_LEFT, SCREEN, RectSnap.INT_SOUTHWEST, true);
				setSnap(KEY_DOWN, KEY_DOWN_LEFT, RectSnap.EXT_EAST, true);
				setSnap(KEY_DOWN_RIGHT, KEY_DOWN, RectSnap.EXT_EAST, true);
				setSnap(KEY_LEFT, KEY_DOWN_LEFT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_RIGHT, KEY_DOWN_RIGHT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_UP_RIGHT, KEY_RIGHT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_UP, KEY_UP_RIGHT, RectSnap.EXT_WEST, true);
				setSnap(KEY_UP_LEFT, KEY_UP, RectSnap.EXT_WEST, true);
				setSnap(KEY_STICK, KEY_UP, RectSnap.EXT_NORTH, true);
				setSnap(KEY_STAR, SCREEN, RectSnap.INT_NORTHWEST, true);       // L

				setSnap(KEY_D, SCREEN, RectSnap.INT_SOUTHEAST, false);         // hidden anchor
				setSnap(KEY_NUM7, KEY_D, RectSnap.EXT_WEST, true);              // B (bottom)
				setSnap(KEY_FIRE, KEY_D, RectSnap.EXT_NORTH, true);             // A (right)
				setSnap(KEY_NUM9, KEY_NUM7, RectSnap.EXT_NORTHWEST, true);      // X (left)
				setSnap(KEY_NUM0, KEY_FIRE, RectSnap.EXT_NORTHWEST, true);      // Y (top)
				setSnap(KEY_POUND, SCREEN, RectSnap.INT_NORTHEAST, true);      // R

				setSnap(KEY_MENU, SCREEN, RectSnap.INT_SOUTH, true);
				setSnap(KEY_SOFT_LEFT, KEY_MENU, RectSnap.EXT_WEST, true);      // Select
				setSnap(KEY_SOFT_RIGHT, KEY_MENU, RectSnap.EXT_EAST, true);     // Start
				setSnap(KEY_TOGGLE, KEY_MENU, RectSnap.EXT_NORTH, true);        // keys "123"

				setSnap(KEY_NUM2, SCREEN, RectSnap.INT_NORTH, false);
				setSnap(KEY_NUM1, KEY_NUM2, RectSnap.EXT_WEST, false);
				setSnap(KEY_NUM3, KEY_NUM2, RectSnap.EXT_EAST, false);
				setSnap(KEY_NUM4, KEY_NUM1, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM5, KEY_NUM2, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM6, KEY_NUM3, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM8, KEY_NUM5, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_A, KEY_NUM4, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_B, KEY_NUM6, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_C, KEY_NUM8, RectSnap.EXT_SOUTH, false);
				break;
			case TYPE_PHONE:
				for (int j = 0, len = keyScales.length; j < len;) {
					keyScales[j++] = PHONE_KEY_SCALE_X;
					keyScales[j++] = PHONE_KEY_SCALE_Y;
				}

				setSnap(KEY_NUM0, SCREEN, RectSnap.INT_SOUTH, true);
				setSnap(KEY_STAR, KEY_NUM0, RectSnap.EXT_WEST, true);
				setSnap(KEY_POUND, KEY_NUM0, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM7, KEY_STAR, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM8, KEY_NUM7, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM9, KEY_NUM8, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM4, KEY_NUM7, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM5, KEY_NUM4, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM6, KEY_NUM5, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM1, KEY_NUM4, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM2, KEY_NUM1, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM3, KEY_NUM2, RectSnap.EXT_EAST, true);
				setSnap(KEY_SOFT_LEFT, KEY_NUM1, RectSnap.EXT_NORTH, true);
				setSnap(KEY_FIRE, KEY_NUM2, RectSnap.EXT_NORTH, true);
				setSnap(KEY_SOFT_RIGHT, KEY_NUM3, RectSnap.EXT_NORTH, true);

				setSnap(KEY_A, SCREEN, RectSnap.INT_NORTHWEST, false);
				setSnap(KEY_B, SCREEN, RectSnap.INT_NORTHEAST, false);
				setSnap(KEY_C, KEY_A, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_D, KEY_B, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_UP_LEFT, KEY_C, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_UP, KEY_C, RectSnap.EXT_SOUTHEAST, false);
				setSnap(KEY_UP_RIGHT, KEY_D, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_LEFT, KEY_UP_LEFT, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_MENU, KEY_UP, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_RIGHT, KEY_UP_RIGHT, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_DOWN_LEFT, KEY_LEFT, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_DOWN, KEY_MENU, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_DOWN_RIGHT, KEY_RIGHT, RectSnap.EXT_SOUTH, false);
				break;
			case TYPE_PHONE_ARROWS:
				for (int j = 0, len = keyScales.length; j < len;) {
					keyScales[j++] = PHONE_KEY_SCALE_X;
					keyScales[j++] = PHONE_KEY_SCALE_Y;
				}

				setSnap(KEY_NUM0, SCREEN, RectSnap.INT_SOUTH, true);
				setSnap(KEY_STAR, KEY_NUM0, RectSnap.EXT_WEST, true);
				setSnap(KEY_POUND, KEY_NUM0, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM7, KEY_STAR, RectSnap.EXT_NORTH, true);
				setSnap(KEY_DOWN, KEY_NUM7, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM9, KEY_DOWN, RectSnap.EXT_EAST, true);
				setSnap(KEY_LEFT, KEY_NUM7, RectSnap.EXT_NORTH, true);
				setSnap(KEY_FIRE, KEY_LEFT, RectSnap.EXT_EAST, true);
				setSnap(KEY_RIGHT, KEY_FIRE, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM1, KEY_LEFT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_UP, KEY_NUM1, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM3, KEY_UP, RectSnap.EXT_EAST, true);
				setSnap(KEY_SOFT_LEFT, KEY_NUM1, RectSnap.EXT_NORTH, true);
				setSnap(KEY_MENU, KEY_UP, RectSnap.EXT_NORTH, true);
				setSnap(KEY_SOFT_RIGHT, KEY_NUM3, RectSnap.EXT_NORTH, true);

				setSnap(KEY_A, SCREEN, RectSnap.INT_NORTHWEST, false);
				setSnap(KEY_B, SCREEN, RectSnap.INT_NORTHEAST, false);
				setSnap(KEY_C, KEY_A, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_D, KEY_B, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_UP_LEFT, KEY_C, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM2, KEY_C, RectSnap.EXT_SOUTHEAST, false);
				setSnap(KEY_UP_RIGHT, KEY_D, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM4, KEY_UP_LEFT, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM5, KEY_NUM2, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM6, KEY_UP_RIGHT, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_DOWN_LEFT, KEY_NUM4, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_DOWN_RIGHT, KEY_NUM6, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM8, KEY_NUM5, RectSnap.EXT_SOUTH, false);
				break;
			case TYPE_NUM_ARR:
			default:
				Arrays.fill(keyScales, 1.0f);

				setSnap(KEY_DOWN_RIGHT, SCREEN, RectSnap.INT_SOUTHEAST, true);
				setSnap(KEY_DOWN, KEY_DOWN_RIGHT, RectSnap.EXT_WEST, true);
				setSnap(KEY_DOWN_LEFT, KEY_DOWN, RectSnap.EXT_WEST, true);
				setSnap(KEY_LEFT, KEY_DOWN_LEFT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_RIGHT, KEY_DOWN_RIGHT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_UP_RIGHT, KEY_RIGHT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_UP, KEY_UP_RIGHT, RectSnap.EXT_WEST, true);
				setSnap(KEY_UP_LEFT, KEY_UP, RectSnap.EXT_WEST, true);
				setSnap(KEY_FIRE, KEY_DOWN_RIGHT, RectSnap.EXT_NORTHWEST, true);
				setSnap(KEY_SOFT_LEFT, KEY_UP_LEFT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_SOFT_RIGHT, KEY_UP_RIGHT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_STAR, SCREEN, RectSnap.INT_SOUTHWEST, true);
				setSnap(KEY_NUM0, KEY_STAR, RectSnap.EXT_EAST, true);
				setSnap(KEY_POUND, KEY_NUM0, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM7, KEY_STAR, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM8, KEY_NUM7, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM9, KEY_NUM8, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM4, KEY_NUM7, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM5, KEY_NUM4, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM6, KEY_NUM5, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM1, KEY_NUM4, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM2, KEY_NUM1, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM3, KEY_NUM2, RectSnap.EXT_EAST, true);

				setSnap(KEY_D, KEY_NUM1, RectSnap.EXT_NORTH, false);
				setSnap(KEY_C, KEY_NUM3, RectSnap.EXT_NORTH, false);
				setSnap(KEY_A, SCREEN, RectSnap.INT_NORTHWEST, false);
				setSnap(KEY_B, SCREEN, RectSnap.INT_NORTHEAST, false);
				setSnap(KEY_MENU, KEY_UP, RectSnap.EXT_NORTH, false);
				break;
			case TYPE_ARR_NUM:
				Arrays.fill(keyScales, 1);

				setSnap(KEY_DOWN_LEFT, SCREEN, RectSnap.INT_SOUTHWEST, true);
				setSnap(KEY_DOWN, KEY_DOWN_LEFT, RectSnap.EXT_EAST, true);
				setSnap(KEY_DOWN_RIGHT, KEY_DOWN, RectSnap.EXT_EAST, true);
				setSnap(KEY_LEFT, KEY_DOWN_LEFT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_RIGHT, KEY_DOWN_RIGHT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_UP_RIGHT, KEY_RIGHT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_UP, KEY_UP_RIGHT, RectSnap.EXT_WEST, true);
				setSnap(KEY_UP_LEFT, KEY_UP, RectSnap.EXT_WEST, true);
				setSnap(KEY_FIRE, KEY_DOWN_RIGHT, RectSnap.EXT_NORTHWEST, true);
				setSnap(KEY_SOFT_LEFT, KEY_UP_LEFT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_SOFT_RIGHT, KEY_UP_RIGHT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_POUND, SCREEN, RectSnap.INT_SOUTHEAST, true);
				setSnap(KEY_NUM0, KEY_POUND, RectSnap.EXT_WEST, true);
				setSnap(KEY_STAR, KEY_NUM0, RectSnap.EXT_WEST, true);
				setSnap(KEY_NUM7, KEY_STAR, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM8, KEY_NUM7, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM9, KEY_NUM8, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM4, KEY_NUM7, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM5, KEY_NUM4, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM6, KEY_NUM5, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM1, KEY_NUM4, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM2, KEY_NUM1, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM3, KEY_NUM2, RectSnap.EXT_EAST, true);

				setSnap(KEY_D, KEY_NUM1, RectSnap.EXT_NORTH, false);
				setSnap(KEY_C, KEY_NUM3, RectSnap.EXT_NORTH, false);
				setSnap(KEY_A, SCREEN, RectSnap.INT_NORTHWEST, false);
				setSnap(KEY_B, SCREEN, RectSnap.INT_NORTHEAST, false);
				setSnap(KEY_MENU, KEY_UP, RectSnap.EXT_NORTH, false);
				break;
			case TYPE_NUMBERS:
				Arrays.fill(keyScales, 1);

				setSnap(KEY_NUM0, SCREEN, RectSnap.INT_SOUTH, true);
				setSnap(KEY_STAR, KEY_NUM0, RectSnap.EXT_WEST, true);
				setSnap(KEY_POUND, KEY_NUM0, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM7, KEY_STAR, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM8, KEY_NUM7, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM9, KEY_NUM8, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM4, KEY_NUM7, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM5, KEY_NUM4, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM6, KEY_NUM5, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM1, KEY_NUM4, RectSnap.EXT_NORTH, true);
				setSnap(KEY_NUM2, KEY_NUM1, RectSnap.EXT_EAST, true);
				setSnap(KEY_NUM3, KEY_NUM2, RectSnap.EXT_EAST, true);
				setSnap(KEY_SOFT_LEFT, KEY_NUM1, RectSnap.EXT_WEST, true);
				setSnap(KEY_SOFT_RIGHT, KEY_NUM3, RectSnap.EXT_EAST, true);

				setSnap(KEY_UP, SCREEN, RectSnap.INT_NORTH, false);
				setSnap(KEY_UP_LEFT, KEY_UP, RectSnap.EXT_WEST, false);
				setSnap(KEY_UP_RIGHT, KEY_UP, RectSnap.EXT_EAST, false);
				setSnap(KEY_FIRE, KEY_UP, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_LEFT, KEY_UP_LEFT, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_RIGHT, KEY_UP_RIGHT, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_DOWN_LEFT, KEY_LEFT, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_DOWN_RIGHT, KEY_RIGHT, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_DOWN, KEY_DOWN_LEFT, RectSnap.EXT_EAST, false);
				setSnap(KEY_A, KEY_NUM4, RectSnap.EXT_WEST, false);
				setSnap(KEY_B, KEY_NUM6, RectSnap.EXT_EAST, false);
				setSnap(KEY_C, KEY_NUM7, RectSnap.EXT_WEST, false);
				setSnap(KEY_D, KEY_NUM9, RectSnap.EXT_EAST, false);
				setSnap(KEY_MENU, SCREEN, RectSnap.INT_NORTHEAST, false);
				break;
			case TYPE_ARROWS:
				Arrays.fill(keyScales, 1);

				setSnap(KEY_DOWN, SCREEN, RectSnap.INT_SOUTH, true);
				setSnap(KEY_DOWN_RIGHT, KEY_DOWN, RectSnap.EXT_EAST, true);
				setSnap(KEY_DOWN_LEFT, KEY_DOWN, RectSnap.EXT_WEST, true);
				setSnap(KEY_LEFT, KEY_DOWN_LEFT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_RIGHT, KEY_DOWN_RIGHT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_UP_RIGHT, KEY_RIGHT, RectSnap.EXT_NORTH, true);
				setSnap(KEY_UP, KEY_UP_RIGHT, RectSnap.EXT_WEST, true);
				setSnap(KEY_UP_LEFT, KEY_UP, RectSnap.EXT_WEST, true);
				setSnap(KEY_FIRE, KEY_DOWN_RIGHT, RectSnap.EXT_NORTHWEST, true);
				setSnap(KEY_SOFT_LEFT, KEY_UP_LEFT, RectSnap.EXT_WEST, true);
				setSnap(KEY_SOFT_RIGHT, KEY_UP_RIGHT, RectSnap.EXT_EAST, true);

				setSnap(KEY_NUM1, KEY_NUM2, RectSnap.EXT_WEST, false);
				setSnap(KEY_NUM2, SCREEN, RectSnap.INT_NORTH, false);
				setSnap(KEY_NUM3, KEY_NUM2, RectSnap.EXT_EAST, false);
				setSnap(KEY_NUM4, KEY_NUM1, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM5, KEY_NUM2, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM6, KEY_NUM3, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM7, KEY_NUM4, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM8, KEY_NUM5, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM9, KEY_NUM6, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_STAR, KEY_NUM7, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_NUM0, KEY_NUM8, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_POUND, KEY_NUM9, RectSnap.EXT_SOUTH, false);
				setSnap(KEY_A, KEY_LEFT, RectSnap.EXT_WEST, false);
				setSnap(KEY_B, KEY_RIGHT, RectSnap.EXT_EAST, false);
				setSnap(KEY_C, KEY_DOWN_LEFT, RectSnap.EXT_WEST, false);
				setSnap(KEY_D, KEY_DOWN_RIGHT, RectSnap.EXT_EAST, false);
				setSnap(KEY_MENU, SCREEN, RectSnap.INT_NORTHEAST, false);
				break;
		}
	}

	/**
	 * Gamepad labels (JarNix defaults: A = OK, B = 7, X = 9, Y = 0, L = *, R = #,
	 * Select/Start = soft keys). Also kept when a gamepad layout was edited (custom).
	 */
	private void updateLabels(int variant) {
		if (variant != TYPE_CUSTOM) {
			// remember it, so an edited (custom) gamepad keeps its labels after a restart
			boolean isPad = variant == TYPE_GAMEPAD;
			if (isPad != padLabelsFile.exists()) {
				try {
					if (isPad) padLabelsFile.createNewFile(); else padLabelsFile.delete();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		boolean pad = variant == TYPE_GAMEPAD || (variant == TYPE_CUSTOM && padLabelsFile.exists());
		padMode = pad;
		for (VirtualKey key : keypad) {
			key.label = key.defaultLabel;
		}
		keypad[KEY_TOGGLE].label = pad ? "123" : ContextHolder.getAppContext().getString(R.string.pref_vk_type_gamepad);
		if (pad) {
			keypad[KEY_FIRE].label = "A";
			keypad[KEY_NUM7].label = "B";
			keypad[KEY_NUM9].label = "X";
			keypad[KEY_NUM0].label = "Y";
			keypad[KEY_STAR].label = "L";
			keypad[KEY_POUND].label = "R";
			keypad[KEY_SOFT_LEFT].label = "SEL";
			keypad[KEY_SOFT_RIGHT].label = "START";
		}
	}

	public int getLayout() {
		return layoutVariant;
	}

	public float getPhoneKeyboardHeight(float w, float h) {
		return PHONE_KEY_ROWS * getKeySize(w, h) * PHONE_KEY_SCALE_Y;
	}

	public void setLayout(int variant) {
		resetLayout(variant);
		if (variant == TYPE_CUSTOM) {
			try {
				readLayout();
			} catch (IOException ioe) {
				ioe.printStackTrace();
				resetLayout(layoutVariant);
				return;
			}
		}
		layoutVariant = variant;
		onLayoutChanged(variant);
		for (int group = 0; group < keyScaleGroups.length; group++) {
			resizeKeyGroup(group);
		}
		snapKeys();
		overlayView.postInvalidate();
		if (target != null && target.isShown()) {
			target.updateSize();
		}
	}

	private void saveLayout() {
		try (RandomAccessFile raf = new RandomAccessFile(saveFile, "rw")) {
			int variant = layoutVariant;
			if (variant != TYPE_CUSTOM && raf.length() > 16) {
				try {
					if (raf.readInt() != LAYOUT_SIGNATURE) {
						throw new IOException("file signature not found");
					}
					int version = raf.readInt();
					if (version < 1 || version > LAYOUT_VERSION) {
						throw new IOException("incompatible file version");
					}
					loop:while (true) {
						int block = raf.readInt();
						int length = raf.readInt();
						switch (block) {
							case LAYOUT_EOF:
								raf.seek(raf.getFilePointer() - 8);
								raf.writeInt(LAYOUT_TYPE);
								raf.writeInt(1);
								raf.write(variant);
								raf.writeInt(LAYOUT_EOF);
								raf.writeInt(0);
								return;
							case LAYOUT_TYPE:
								raf.write(variant);
								return;
							case LAYOUT_KEYS:
								if (version >= 2) {
									int count = raf.readInt();
									length = count * 21;
								}
							default:
								if (raf.skipBytes(length) != length) {
									break loop;
								}
								break;
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			raf.seek(0);
			raf.writeInt(LAYOUT_SIGNATURE);
			raf.writeInt(LAYOUT_VERSION);
			raf.writeInt(LAYOUT_TYPE);
			raf.writeInt(1);
			raf.write(variant);
			if (variant != TYPE_CUSTOM) {
				raf.writeInt(LAYOUT_EOF);
				raf.writeInt(0);
				raf.setLength(raf.getFilePointer());
				return;
			}
			raf.writeInt(LAYOUT_KEYS);
			raf.writeInt(keypad.length * 21 + 4);
			raf.writeInt(keypad.length);
			for (VirtualKey key : keypad) {
				raf.writeInt(key.hashCode());
				raf.writeBoolean(key.visible);
				raf.writeInt(key.snapOrigin);
				raf.writeInt(key.snapMode);
				PointF snapOffset = key.snapOffset;
				raf.writeFloat(snapOffset.x);
				raf.writeFloat(snapOffset.y);
			}
			raf.writeInt(LAYOUT_SCALES);
			raf.writeInt(keyScales.length * 4 + 4);
			raf.writeInt(keyScales.length);
			for (float keyScale : keyScales) {
				raf.writeFloat(keyScale);
			}
			raf.writeInt(LAYOUT_EOF);
			raf.writeInt(0);
			raf.setLength(raf.getFilePointer());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private int readLayoutType() {
		try (DataInputStream dis = new DataInputStream(new FileInputStream(saveFile))) {
			if (dis.readInt() != LAYOUT_SIGNATURE) {
				throw new IOException("file signature not found");
			}
			int version = dis.readInt();
			if (version < 1 || version > LAYOUT_VERSION) {
				throw new IOException("incompatible file version");
			}
			int custom = 0;
			while (true) {
				int block = dis.readInt();
				int length = dis.readInt();
				switch (block) {
					case LAYOUT_EOF:
						return custom == 3 ? 0 : -1;
					case LAYOUT_TYPE:
						return dis.read();
					case LAYOUT_KEYS:
						if (version >= 2) {
							int count = dis.readInt();
							length = count * 21;
						}
						if (dis.skipBytes(length) != length) {
							return -1;
						}
						custom |= 1;
						break;
					case LAYOUT_SCALES:
						if (dis.skipBytes(length) != length) {
							return -1;
						}
						custom |= 2;
						break;
					default:
						if (dis.skipBytes(length) != length) {
							return -1;
						}
				}
			}
		} catch (FileNotFoundException e) {
			Log.w(TAG, "readLayoutType() threw an FileNotFoundException: " + e.getMessage());
		} catch (IOException e) {
			e.printStackTrace();
		}
		return -1;
	}

	private void readLayout() throws IOException {
		try (DataInputStream dis = new DataInputStream(new FileInputStream(saveFile))) {
			if (dis.readInt() != LAYOUT_SIGNATURE) {
				throw new IOException("file signature not found");
			}
			int version = dis.readInt();
			if (version < 1 || version > LAYOUT_VERSION) {
				throw new IOException("incompatible file version");
			}
			while (true) {
				int block = dis.readInt();
				int length = dis.readInt();
				int count;
				switch (block) {
					case LAYOUT_EOF:
						return;
					case LAYOUT_KEYS:
						count = dis.readInt();
						for (int i = 0; i < count; i++) {
							int hash = dis.readInt();
							boolean found = false;
							for (VirtualKey key : keypad) {
								if (key.hashCode() == hash) {
									if (version >= 2) {
										key.visible = dis.readBoolean();
									}
									key.snapOrigin = dis.readInt();
									key.snapMode = dis.readInt();
									key.snapOffset.x = dis.readFloat();
									key.snapOffset.y = dis.readFloat();
									found = true;
									break;
								}
							}
							if (!found) {
								dis.skipBytes(version >= 2 ? 17 : 16);
							}
						}
						break;
					case LAYOUT_SCALES:
						count = dis.readInt();
						if (version >= 3) {
							for (int i = 0; i < count; i++) {
								keyScales[i] = dis.readFloat();
							}
						} else if (count * 2 <= keyScales.length) {
							for (int i = 0, len = count * 2; i < len;) {
								float v = dis.readFloat();
								keyScales[i++] = v;
								keyScales[i++] = v;
							}
						} else {
							dis.skipBytes(count * 4);
						}
						break;
					default:
						dis.skipBytes(length);
						break;
				}
			}
		}
	}

	public String[] getKeyNames() {
		String[] names = new String[KEYBOARD_SIZE];
		for (int i = 0; i < KEYBOARD_SIZE; i++) {
			names[i] = keypad[i].label;
		}
		return names;
	}

	/**
	 * The keys that belong to what is on screen now, for the hide/show list: the gamepad's
	 * buttons in gamepad mode, the phone keys (without the stick) otherwise.
	 */
	public int[] getModeKeys() {
		int[] pad = {KEY_UP, KEY_DOWN, KEY_LEFT, KEY_RIGHT, KEY_UP_LEFT, KEY_UP_RIGHT, KEY_DOWN_LEFT,
				KEY_DOWN_RIGHT, KEY_FIRE, KEY_NUM7, KEY_NUM9, KEY_NUM0, KEY_STAR, KEY_POUND, KEY_STICK,
				KEY_SOFT_LEFT, KEY_SOFT_RIGHT, KEY_MENU, KEY_TOGGLE};
		if (padMode) return pad;
		int[] keys = new int[KEYBOARD_SIZE - 1];
		for (int i = 0, n = 0; i < KEYBOARD_SIZE; i++) {
			if (i != KEY_STICK) keys[n++] = i;
		}
		return keys;
	}

	/** Name of a key in the hide/show list. */
	public String getKeyName(int index) {
		if (index == KEY_STICK) return "Stick";
		if (index == KEY_MENU) return "\u22EE";
		return keypad[index].label;
	}

	public int getAlpha() {
		return settings.vkAlpha;
	}

	/** Opacity over the game, shown at once (saved with the game's settings by the caller). */
	public void setAlpha(int alpha) {
		settings.vkAlpha = alpha;
		if (overlayView != null) overlayView.postInvalidate();
	}

	/** Back to the default gamepad or keypad layout (the edited one is lost). */
	public void resetModeLayout() {
		setLayout(padMode ? TYPE_GAMEPAD : TYPE_JARNIX);
	}

	public boolean[] getKeysVisibility() {
		boolean[] states = new boolean[KEYBOARD_SIZE];
		for (int i = 0; i < KEYBOARD_SIZE; i++) {
			states[i] = !keypad[i].visible;
		}
		return states;
	}

	public void setKeysVisibility(boolean[] states) {
		for (int i = 0; i < KEYBOARD_SIZE; i++) {
			keypad[i].visible = !states[i];
		}
		overlayView.postInvalidate();
	}

	@Override
	public void setTarget(Canvas canvas) {
		target = canvas;
		highlightGroup(-1);
	}

	private void setSnap(int key, int origin, int mode, boolean visible) {
		VirtualKey vKey = keypad[key];
		vKey.snapOrigin = origin;
		vKey.snapMode = mode;
		vKey.snapOffset.set(0, 0);
		vKey.snapValid = false;
		vKey.visible = visible;
	}

	private boolean findSnap(int target, int origin) {
		VirtualKey tk = keypad[target];
		VirtualKey ok = keypad[origin];
		tk.snapMode = RectSnap.getSnap(tk.rect, ok.rect, snapRadius, RectSnap.COARSE_MASK, true);
		if (tk.snapMode != RectSnap.NO_SNAP) {
			tk.snapOrigin = origin;
			tk.snapOffset.set(0, 0);
			for (int i = 0; i < keypad.length; i++) {
				origin = keypad[origin].snapOrigin;
				if (origin == SCREEN) {
					return true;
				}
			}
		}
		return false;
	}

	private void snapKey(int key, int level) {
		if (level >= snapStack.length) {
			Log.d(TAG, "Snap loop detected: ");
			for (int i = 1; i < snapStack.length; i++) {
				System.out.print(snapStack[i]);
				System.out.print(", ");
			}
			Log.d(TAG, String.valueOf(key));
			return;
		}
		snapStack[level] = key;
		VirtualKey vKey = keypad[key];
		if (vKey.snapOrigin == SCREEN) {
			RectSnap.snap(vKey.rect, screen, vKey.snapMode, vKey.snapOffset);
		} else {
			if (!keypad[vKey.snapOrigin].snapValid) {
				snapKey(vKey.snapOrigin, level + 1);
			}
			RectSnap.snap(vKey.rect, keypad[vKey.snapOrigin].rect,
					vKey.snapMode, vKey.snapOffset);
		}
		vKey.snapValid = true;
	}

	private void snapKeys() {
		obscuresVirtualScreen = false;
		boolean isPhone = isPhone();
		for (int i = 0; i < keypad.length; i++) {
			snapKey(i, 0);
			VirtualKey key = keypad[i];
			RectF rect = key.rect;
			key.corners = (int) (Math.min(rect.width(), rect.height()) * 0.25F);
			if (!isPhone && RectF.intersects(rect, virtualScreen)) {
				obscuresVirtualScreen = true;
				key.opaque = false;
			} else {
				key.opaque = true;
			}
		}
		boolean opaque = !obscuresVirtualScreen || settings.vkForceOpacity;
		for (VirtualKey key : keypad) {
			key.opaque &= opaque;
		}
	}

	public boolean isPhone() {
		return layoutVariant == TYPE_PHONE || layoutVariant == TYPE_PHONE_ARROWS;
	}

	private void highlightGroup(int group) {
		for (VirtualKey aKeypad : keypad) {
			aKeypad.selected = false;
		}
		if (group >= 0) {
			for (int key = 0; key < keyScaleGroups[group].length; key++) {
				keypad[keyScaleGroups[group][key]].selected = true;
			}
		}
	}

	public int getLayoutEditMode() {
		return layoutEditMode;
	}

	public void setLayoutEditMode(int mode) {
		layoutEditMode = mode;
		int group = -1;
		if (mode == LAYOUT_SCALES) {
			editedIndex = 0;
			group = 0;
		}
		highlightGroup(group);
		handler.removeCallbacks(this);
		visible = true;
		overlayView.postInvalidate();
		hide();
	}

	private void resizeKey(int key, float w, float h) {
		VirtualKey vKey = keypad[key];
		vKey.resize(w, h);
		vKey.snapValid = false;
	}

	private void resizeKeyGroup(int group) {
		float sizeX = keySize * keyScales[group * 2];
		float sizeY = keySize * keyScales[group * 2 + 1];
		for (int key = 0; key < keyScaleGroups[group].length; key++) {
			resizeKey(keyScaleGroups[group][key], sizeX, sizeY);
		}
	}

	@Override
	public void resize(RectF screen, float left, float top, float right, float bottom) {
		this.screen = screen;
		virtualScreen.set(left, top, right, bottom);
		snapRadius = keyScales[0];
		for (int i = 1; i < keyScales.length; i++) {
			if (keyScales[i] < snapRadius) {
				snapRadius = keyScales[i];
			}
		}

		float keySize = getKeySize(screen.width(), screen.height());
		snapRadius = keySize * snapRadius / 4;
		this.keySize = keySize;
		for (int group = 0; group < keyScaleGroups.length; group++) {
			resizeKeyGroup(group);
		}
		snapKeys();
		overlayView.postInvalidate();
		int delay = settings.vkHideDelay;
		if (delay > 0 && obscuresVirtualScreen && layoutEditMode == LAYOUT_EOF) {
			for (VirtualKey key : associatedKeys) {
				if (key != null) {
					return;
				}
			}
			handler.postDelayed(this, delay);
		}
	}

	private float getKeySize(float screenWidth, float screenHeight) {
		float min = screenWidth;
		float max = screenHeight;
		boolean landscape = min > max;
		if (min > max) {
			float tmp = max;
			max = min;
			min = tmp;
		}

		boolean nonWide = max / min < 2;
		float keySize;
		if (isPhone()) {
			keySize = min / 6.0F;
		} else if (nonWide || landscape) {
			keySize = max / 12F;
		} else {
			keySize = min / 6.5F;
		}
		return keySize;
	}

	private static int readButtonStyle() {
		try {
			return Integer.parseInt(androidx.preference.PreferenceManager
					.getDefaultSharedPreferences(ContextHolder.getAppContext())
					.getString(PREF_BUTTON_STYLE, String.valueOf(STYLE_MODERN)));
		} catch (Exception e) {
			return STYLE_MODERN;
		}
	}

	public int getButtonStyle() {
		return buttonStyle;
	}

	/** Changes the look right away (skin pictures without an imported skin fall back to modern). */
	public void setButtonStyle(int style) {
		if (style == STYLE_SKIN && !PadSkin.hasUserSkin()) style = STYLE_MODERN;
		buttonStyle = style;
		portemon = style <= STYLE_OUTLINE ? new PortemonStyle(ContextHolder.getAppContext(), style) : null;
		if (overlayView != null) overlayView.postInvalidate();
	}

	/** Shape of a key in the Portemon look. */
	private int shapeOf(VirtualKey key) {
		if (key == keypad[KEY_SOFT_LEFT] || key == keypad[KEY_SOFT_RIGHT] || key == keypad[KEY_MENU]
				|| key == keypad[KEY_TOGGLE]) return PortemonStyle.PILL;
		if (!padMode) return PortemonStyle.ROUND;
		if (key == keypad[KEY_STAR] || key == keypad[KEY_POUND]) return PortemonStyle.PILL;
		if (key == keypad[KEY_FIRE] || key == keypad[KEY_NUM7] || key == keypad[KEY_NUM9]
				|| key == keypad[KEY_NUM0]) return PortemonStyle.CIRCLE;
		return PortemonStyle.ROUND;
	}

	/** Portemon cross over the 8 direction keys of the gamepad. */
	private boolean paintPortemonDpad(CanvasWrapper g) {
		RectF area = new RectF(keypad[KEY_UP_LEFT].rect);
		for (int k : DPAD_KEYS) {
			if (!keypad[k].visible) return false;
			area.union(keypad[k].rect);
		}
		int dirs = 0;
		if (keypad[KEY_UP_LEFT].selected || keypad[KEY_UP].selected || keypad[KEY_UP_RIGHT].selected) dirs |= 1;
		if (keypad[KEY_DOWN_LEFT].selected || keypad[KEY_DOWN].selected || keypad[KEY_DOWN_RIGHT].selected) dirs |= 2;
		if (keypad[KEY_UP_LEFT].selected || keypad[KEY_LEFT].selected || keypad[KEY_DOWN_LEFT].selected) dirs |= 4;
		if (keypad[KEY_UP_RIGHT].selected || keypad[KEY_RIGHT].selected || keypad[KEY_DOWN_RIGHT].selected) dirs |= 8;
		portemon.dpad(g.getCanvas(), area, dirs, skinAlpha(keypad[KEY_UP]));
		return true;
	}

	/** Keys on screen right now (the game is centered when there are none). */
	public boolean isShown() {
		return !userHidden && !hiddenByController;
	}

	public boolean isUserHidden() {
		return userHidden;
	}

	/** Quick settings: hides or shows every key, and remembers it for this game. */
	public void setUserHidden(boolean hidden) {
		if (hidden == userHidden) return;
		userHidden = hidden;
		releaseAll();
		try {
			if (hidden) hiddenFile.createNewFile(); else hiddenFile.delete();
		} catch (IOException e) {
			Log.w(TAG, "setUserHidden: ", e);
		}
		relayoutTarget();
	}

	private void releaseAll() {
		for (int i = 0; i < associatedKeys.length; i++) {
			if (associatedKeys[i] != null) {
				associatedKeys[i].onUp();
				associatedKeys[i] = null;
			}
		}
	}

	/** Keys appeared or went away: the game moves to the middle or back to the top. */
	private void relayoutTarget() {
		if (overlayView != null) overlayView.postInvalidate();
		Canvas t = target;
		if (t != null && t.isShown()) t.updateSize();
	}

	/** Called when a physical game controller / keyboard is used. */
	public void hideForController() {
		if (!hiddenByController && !userHidden && layoutEditMode == LAYOUT_EOF) {
			hiddenByController = true;
			for (int i = 0; i < associatedKeys.length; i++) {
				if (associatedKeys[i] != null) {
					associatedKeys[i].onUp();
					associatedKeys[i] = null;
				}
			}
			relayoutTarget();
		}
	}

	@Override
	public void paint(CanvasWrapper g) {
		if (visible && !hiddenByController && !userHidden) {
			boolean skinDpad = padMode && layoutEditMode == LAYOUT_EOF
					&& (buttonStyle == STYLE_SKIN ? paintSkinDpad(g) : portemon != null && paintPortemonDpad(g));
			for (int i = 0; i < keypad.length; i++) {
				VirtualKey key = keypad[i];
				if (key.visible && !(skinDpad && isDpadKey(i))) {
					key.paint(g);
				}
			}
		}
	}

	private static boolean isDpadKey(int index) {
		for (int k : DPAD_KEYS) {
			if (k == index) return true;
		}
		return false;
	}

	/** Skin alpha: like the keys, see-through only where they cover the game screen. */
	private int skinAlpha(VirtualKey key) {
		return key.opaque || layoutEditMode != LAYOUT_EOF ? 0xFF : settings.vkAlpha;
	}

	/** Fits a picture inside rect keeping its proportions (centered) into skinRect. */
	private void fitSkin(Bitmap bm, RectF rect) {
		float s = Math.min(rect.width() / bm.getWidth(), rect.height() / bm.getHeight());
		float w = bm.getWidth() * s / 2, h = bm.getHeight() * s / 2;
		skinRect.set(rect.centerX() - w, rect.centerY() - h, rect.centerX() + w, rect.centerY() + h);
	}

	/**
	 * One d-pad picture over the 8 direction keys. Pressed: dpad_up, dpad_down_left... if the
	 * skin has them, else Citra style dpad_pressed_one (up) / dpad_pressed_two (up + left) turned.
	 */
	private boolean paintSkinDpad(CanvasWrapper g) {
		Bitmap idle = PadSkin.get("dpad");
		if (idle == null) return false;
		RectF area = new RectF(keypad[KEY_UP_LEFT].rect);
		boolean up = false, down = false, left = false, right = false;
		for (int k : DPAD_KEYS) {
			VirtualKey key = keypad[k];
			if (!key.visible) return false;
			area.union(key.rect);
		}
		up = keypad[KEY_UP_LEFT].selected || keypad[KEY_UP].selected || keypad[KEY_UP_RIGHT].selected;
		down = keypad[KEY_DOWN_LEFT].selected || keypad[KEY_DOWN].selected || keypad[KEY_DOWN_RIGHT].selected;
		left = keypad[KEY_UP_LEFT].selected || keypad[KEY_LEFT].selected || keypad[KEY_DOWN_LEFT].selected;
		right = keypad[KEY_UP_RIGHT].selected || keypad[KEY_RIGHT].selected || keypad[KEY_DOWN_RIGHT].selected;
		Bitmap bm = idle;
		float degrees = 0;
		if (up || down || left || right) {
			boolean diag = (up || down) && (left || right);
			String name = "dpad_" + (diag ? (up ? "up_" : "down_") + (left ? "left" : "right")
					: up ? "up" : down ? "down" : left ? "left" : "right");
			Bitmap own = PadSkin.get(name);
			if (own != null) {
				bm = own;
			} else if (diag && PadSkin.get("dpad_pressed_two") != null) {
				bm = PadSkin.get("dpad_pressed_two");
				degrees = up ? (left ? 0 : 90) : (right ? 180 : 270);
			} else if (!diag && PadSkin.get("dpad_pressed_one") != null) {
				bm = PadSkin.get("dpad_pressed_one");
				degrees = up ? 0 : right ? 90 : down ? 180 : 270;
			}
		}
		fitSkin(bm, area);
		g.drawSkin(bm, skinRect, skinAlpha(keypad[KEY_UP]), degrees);
		return true;
	}

	/** The skin picture name for a key in gamepad mode, or null. */
	private String skinName(VirtualKey key) {
		if (key == keypad[KEY_FIRE]) return "a";
		if (key == keypad[KEY_NUM7]) return "b";
		if (key == keypad[KEY_NUM9]) return "x";
		if (key == keypad[KEY_NUM0]) return "y";
		if (key == keypad[KEY_STAR]) return "l";
		if (key == keypad[KEY_POUND]) return "r";
		if (key == keypad[KEY_SOFT_LEFT]) return "select";
		if (key == keypad[KEY_SOFT_RIGHT]) return "start";
		return null;
	}

	@Override
	public boolean pointerPressed(int pointer, float x, float y) {
		switch (layoutEditMode) {
			case LAYOUT_EOF:
				if (userHidden) {
					return false;
				}
				if (hiddenByController) { // first touch only shows the keys again
					hiddenByController = false;
					relayoutTarget();
					return true;
				}
				if (pointer > associatedKeys.length) {
					return false;
				}
				for (VirtualKey key : keypad) {
					if (key.contains(x, y)) {
						vibrate();
						associatedKeys[pointer] = key;
						key.onDown();
						if (key instanceof StickKey) ((StickKey) key).move(x, y);
						overlayView.postInvalidate();
						break;
					}
				}
				break;
			case LAYOUT_KEYS:
				editedIndex = -1;
				for (int i = 0; i < keypad.length; i++) {
					if (keypad[i].contains(x, y)) {
						editedIndex = i;
						RectF rect = keypad[i].rect;
						offsetX = x - rect.left;
						offsetY = y - rect.top;
						break;
					}
				}
				break;
			case LAYOUT_SCALES:
				int index = -1;
				for (int group = 0; group < keyScaleGroups.length && index < 0; group++) {
					for (int key = 0; key < keyScaleGroups[group].length && index < 0; key++) {
						if (keypad[keyScaleGroups[group][key]].contains(x, y)) {
							index = group;
						}
					}
				}
				if (index >= 0) {
					editedIndex = index;
					highlightGroup(index);
					overlayView.postInvalidate();
				}
				offsetX = x;
				offsetY = y;
				prevScaleX = keyScales[editedIndex * 2];
				prevScaleY = keyScales[editedIndex * 2 + 1];
				break;
		}
		return false;
	}

	@Override
	public boolean pointerDragged(int pointer, float x, float y) {
		switch (layoutEditMode) {
			case LAYOUT_EOF:
				if (pointer > associatedKeys.length) {
					return false;
				}
				VirtualKey aKey = associatedKeys[pointer];
				if (aKey instanceof StickKey) {
					((StickKey) aKey).move(x, y);
					overlayView.postInvalidate();
				} else if (aKey == null) {
					pointerPressed(pointer, x, y);
				} else if (!aKey.contains(x, y)) {
					associatedKeys[pointer] = null;
					aKey.onUp();
					overlayView.postInvalidate();
					pointerPressed(pointer, x, y);
				}
				break;
			case LAYOUT_KEYS:
				if (editedIndex >= 0) {
					VirtualKey key = keypad[editedIndex];
					RectF rect = key.rect;
					rect.offsetTo(x - offsetX, y - offsetY);
					key.snapMode = RectSnap.NO_SNAP;
					for (int i = 0; i < keypad.length; i++) {
						if (i != editedIndex && findSnap(editedIndex, i)) {
							break;
						}
					}
					if (key.snapMode == RectSnap.NO_SNAP) {
						key.snapMode = RectSnap.getSnap(rect, screen, key.snapOffset);
						key.snapOrigin = SCREEN;
						if (Math.abs(key.snapOffset.x) <= snapRadius) {
							key.snapOffset.x = 0;
						}
						if (Math.abs(key.snapOffset.y) <= snapRadius) {
							key.snapOffset.y = 0;
						}
					}
					snapKey(editedIndex, 0);
					overlayView.postInvalidate();
				}
				break;
			case LAYOUT_SCALES:
				float dx = x - offsetX;
				float dy = offsetY - y;
				float scale;
				int index = this.editedIndex * 2;
				if (Math.abs(dx) > Math.abs(dy)) {
					scale = prevScaleX + dx / Math.min(screen.centerX(), screen.centerY());
				} else {
					scale = prevScaleY + dy / Math.min(screen.centerX(), screen.centerY());
					index++;
				}
				if (Math.abs(1 - scale) <= SCALE_SNAP_RADIUS) {
					scale = 1;
				} else {
					for (int i = index % 2; i < keyScales.length; i += 2) {
						if (i != index && Math.abs(keyScales[i] - scale) <= SCALE_SNAP_RADIUS) {
							scale = keyScales[i];
							break;
						}
					}
				}
				keyScales[index] = scale;
				resizeKeyGroup(this.editedIndex);
				snapKeys();
				overlayView.postInvalidate();
				break;
		}
		return false;
	}

	@Override
	public boolean pointerReleased(int pointer, float x, float y) {
		if (layoutEditMode == LAYOUT_EOF) {
			if (pointer > associatedKeys.length) {
				return false;
			}
			VirtualKey key = associatedKeys[pointer];
			if (key != null) {
				associatedKeys[pointer] = null;
				key.onUp();
				overlayView.postInvalidate();
			}
		} else if (layoutEditMode == LAYOUT_KEYS) {
			for (int key = 0; key < keypad.length; key++) {
				VirtualKey vKey = keypad[key];
				if (vKey.snapOrigin == editedIndex) {
					vKey.snapMode = RectSnap.NO_SNAP;
					for (int i = 0; i < KEYBOARD_SIZE; i++) {
						if (i != key && findSnap(key, i)) {
							break;
						}
					}
					if (vKey.snapMode == RectSnap.NO_SNAP) {
						vKey.snapMode = RectSnap.getSnap(vKey.rect, screen, vKey.snapOffset);
						vKey.snapOrigin = SCREEN;
						if (Math.abs(vKey.snapOffset.x) <= snapRadius) {
							vKey.snapOffset.x = 0;
						}
						if (Math.abs(vKey.snapOffset.y) <= snapRadius) {
							vKey.snapOffset.y = 0;
						}
					}
					snapKey(key, 0);
				}
			}
			snapKeys();
			editedIndex = -1;
		}
		return false;
	}

	@Override
	public void show() {
		if (settings.vkHideDelay > 0 && obscuresVirtualScreen) {
			handler.removeCallbacks(this);
			if (!visible) {
				visible = true;
				overlayView.postInvalidate();
			}
		}
	}

	@Override
	public void hide() {
		long delay = settings.vkHideDelay;
		if (delay > 0 && obscuresVirtualScreen && layoutEditMode == LAYOUT_EOF) {
			handler.postDelayed(this, delay);
		}
	}

	@Override
	public void cancel() {
		for (VirtualKey key : keypad) {
			key.selected = false;
			handler.removeCallbacks(key);
		}
	}

	@Override
	public void run() {
		visible = false;
		overlayView.postInvalidate();
	}

	@Override
	public boolean keyPressed(int keyCode) {
		int hashCode = 31 * (31 + keyCode);
		for (VirtualKey key : keypad) {
			if (key.hashCode() == hashCode) {
				key.selected = true;
				overlayView.postInvalidate();
				break;
			}
		}
		return false;
	}

	@Override
	public boolean keyRepeated(int keyCode) {
		return false;
	}

	@Override
	public boolean keyReleased(int keyCode) {
		int hashCode = 31 * (31 + keyCode);
		for (VirtualKey key : keypad) {
			if (key.hashCode() == hashCode) {
				key.selected = false;
				overlayView.postInvalidate();
				break;
			}
		}
		return false;
	}

	private void vibrate() {
		if (settings.vkFeedback) ContextHolder.vibrateKey(FEEDBACK_DURATION);
	}

	public void setView(View view) {
		overlayView = view;
	}

	public int getKeyStatesVodafone() {
		int keyStates = 0;
		for (int i = 0; i < keypad.length; i++) {
			VirtualKey key = keypad[i];
			if (key.selected) {
				keyStates |= getKeyBit(i);
			}
		}
		return keyStates;
	}

	private int getKeyBit(int vKey) {
		switch (vKey) {
			case KEY_NUM0      : return 1;       // 0 0 key         KEY_NUM0       = 9;
			case KEY_NUM1      : return 1 <<  1; // 1 1 key         KEY_NUM1       = 0;
			case KEY_NUM2      : return 1 <<  2; // 2 2 key         KEY_NUM2       = 1;
			case KEY_NUM3      : return 1 <<  3; // 3 3 key         KEY_NUM3       = 2;
			case KEY_NUM4      : return 1 <<  4; // 4 4 key         KEY_NUM4       = 3;
			case KEY_NUM5      : return 1 <<  5; // 5 5 key         KEY_NUM5       = 4;
			case KEY_NUM6      : return 1 <<  6; // 6 6 key         KEY_NUM6       = 5;
			case KEY_NUM7      : return 1 <<  7; // 7 7 key         KEY_NUM7       = 6;
			case KEY_NUM8      : return 1 <<  8; // 8 8 key         KEY_NUM8       = 7;
			case KEY_NUM9      : return 1 <<  9; // 9 9 key         KEY_NUM9       = 8;
			case KEY_STAR      : return 1 << 10; // 10 * key        KEY_STAR       = 10;
			case KEY_POUND     : return 1 << 11; // 11 # key        KEY_POUND      = 11;
			case KEY_UP        : return 1 << 12; // 12 Up key       KEY_UP         = 17;
			case KEY_LEFT      : return 1 << 13; // 13 Left key     KEY_LEFT       = 19;
			case KEY_RIGHT     : return 1 << 14; // 14 Right key    KEY_RIGHT      = 20;
			case KEY_DOWN      : return 1 << 15; // 15 Down key     KEY_DOWN       = 22;
			case KEY_FIRE      : return 1 << 16; // 16 Select key   KEY_FIRE       = 24;
			case KEY_SOFT_LEFT : return 1 << 17; // 17 Softkey 1    KEY_SOFT_LEFT  = 12;
			case KEY_SOFT_RIGHT: return 1 << 18; // 18 Softkey 2    KEY_SOFT_RIGHT = 13;
			// TODO: 05.08.2020 Softkey3 mapped to KEY_C
			case KEY_C         : return 1 << 19; // 19 Softkey 3    KEY_C          = 15;
			case KEY_UP_RIGHT  : return 1 << 20; // 20 Upper Right  KEY_UP_RIGHT   = 18;
			case KEY_UP_LEFT   : return 1 << 21; // 21 Upper Left   KEY_UP_LEFT    = 16;
			case KEY_DOWN_RIGHT: return 1 << 22; // 22 Lower Right  KEY_DOWN_RIGHT = 23;
			case KEY_DOWN_LEFT : return 1 << 23; // 23 Lower Left   KEY_DOWN_LEFT  = 21;
		}
		return 0;
	}

	public void saveScreenParams() {
		float scale = virtualScreen.width() / screen.width();
		settings.screenScaleRatio = Math.round(scale * 100);
		settings.screenGravity = 1;
		Canvas.setScale(settings.screenGravity, settings.screenScaleType, settings.screenScaleRatio);
		ProfilesManager.saveConfig(settings);
	}

	private class VirtualKey implements Runnable {
		final String defaultLabel;
		String label;
		final int keyCode;
		final RectF rect = new RectF();
		final PointF snapOffset = new PointF();
		int snapOrigin;
		int snapMode;
		boolean snapValid;
		boolean selected;
		boolean visible = true;
		boolean opaque = true;
		int corners;
		private final int hashCode;
		private int repeatCount;

		VirtualKey(int keyCode, String label) {
			this.keyCode = keyCode;
			this.label = label;
			this.defaultLabel = label;
			hashCode = 31 * (31 + this.keyCode);
		}

		void resize(float width, float height) {
			rect.right = rect.left + width;
			rect.bottom = rect.top + height;
		}

		boolean contains(float x, float y) {
			return visible && rect.contains(x, y);
		}

		void paint(CanvasWrapper g) {
			if (portemon != null) {
				portemon.key(g.getCanvas(), rect, label, selected, skinAlpha(this), shapeOf(this));
				return;
			}
			String skin = buttonStyle == STYLE_SKIN && padMode && layoutEditMode == LAYOUT_EOF ? skinName(this) : null;
			Bitmap bm = skin == null ? null : PadSkin.get(selected ? skin + "_pressed" : skin);
			if (bm == null && skin != null) bm = PadSkin.get(skin);
			if (bm != null) {
				fitSkin(bm, rect);
				g.drawSkin(bm, skinRect, skinAlpha(this), 0);
				return;
			}
			int bgColor;
			int fgColor;
			if (selected) {
				bgColor = settings.vkBgColorSelected;
				fgColor = settings.vkFgColorSelected;
			} else {
				bgColor = settings.vkBgColor;
				fgColor = settings.vkFgColor;
			}
			int alpha = (opaque || layoutEditMode != LAYOUT_EOF ? 0xFF : settings.vkAlpha) << 24;
			g.setFillColor(alpha | bgColor);
			g.setTextColor(alpha | fgColor);
			g.setDrawColor(alpha | settings.vkOutlineColor);

			switch (settings.vkButtonShape) {
				case ROUND_RECT_SHAPE:
					g.fillRoundRect(rect, corners, corners);
					g.drawRoundRect(rect, corners, corners);
					break;
				case RECT_SHAPE:
					g.fillRect(rect);
					g.drawRect(rect);
					break;
				case OVAL_SHAPE:
					g.fillArc(rect, 0, 360);
					g.drawArc(rect, 0, 360);
					break;
			}
			g.drawString(label, rect.centerX(), rect.centerY());
		}

		@NonNull
		public String toString() {
			return "[" + label + ": " + rect.left + ", " + rect.top + ", " + rect.right + ", " + rect.bottom + "]";
		}

		public int hashCode() {
			return hashCode;
		}

		@Override
		public void run() {
			if (target == null) {
				selected = false;
				repeatCount = 0;
				return;
			}
			if (selected) {
				onRepeat();
			} else {
				repeatCount = 0;
			}
		}

		public void onRepeat() {
			handler.postDelayed(this, repeatCount > 6 ? 80 : REPEAT_INTERVALS[repeatCount++]);
			target.postKeyRepeated(keyCode);
		}

		protected void onDown() {
			selected = true;
			target.postKeyPressed(keyCode);
			handler.postDelayed(this, 400);
		}

		public void onUp() {
			selected = false;
			handler.removeCallbacks(this);
			target.postKeyReleased(keyCode);
		}
	}

	private class DualKey extends VirtualKey {

		final int secondKeyCode;
		private final int hashCode;

		DualKey(int keyCode, int secondKeyCode, String label) {
			super(keyCode, label);
			if (secondKeyCode == 0) throw new IllegalArgumentException();
			this.secondKeyCode = secondKeyCode;
			hashCode = 31 * (31 + this.keyCode) + this.secondKeyCode;
		}

		@Override
		protected void onDown() {
			super.onDown();
			target.postKeyPressed(secondKeyCode);
		}

		@Override
		public void onUp() {
			super.onUp();
			target.postKeyReleased(secondKeyCode);
		}

		@Override
		public void onRepeat() {
			super.onRepeat();
			target.postKeyRepeated(secondKeyCode);
		}

		@Override
		public int hashCode() {
			return hashCode;
		}
	}

	/**
	 * On-screen stick (idea from JarNix): the direction from its center to the finger presses
	 * one of 8 directions, like the d-pad. Skin: "joystick" at rest, or Citra style
	 * stick_main_range (plate, here joystick_base) + stick_main (knob) / stick_main_pressed.
	 */
	private class StickKey extends VirtualKey {
		private int mask; // up 1, down 2, left 4, right 8
		private float knobX = Float.NaN, knobY;

		StickKey() {
			super(STICK_KEY_CODE, "◎");
		}

		void move(float x, float y) {
			float r = rect.width() / 2;
			float dx = x - rect.centerX(), dy = y - rect.centerY();
			float d = (float) Math.hypot(dx, dy);
			float max = r * 0.5f; // the knob never leaves the plate
			if (d > max) {
				dx = dx * max / d;
				dy = dy * max / d;
			}
			knobX = rect.centerX() + dx;
			knobY = rect.centerY() + dy;
			int m = 0;
			if (d > r * 0.25f) { // dead zone in the middle
				// 8 sectors of 45°: a direction counts when within 67.5° of its axis
				double a = Math.toDegrees(Math.atan2(-(y - rect.centerY()), x - rect.centerX()));
				if (a > 22.5 && a < 157.5) m |= 1;
				if (a < -22.5 && a > -157.5) m |= 2;
				if (a > 112.5 || a < -112.5) m |= 4;
				if (a < 67.5 && a > -67.5) m |= 8;
			}
			setMask(m);
		}

		private void setMask(int m) {
			int[] codes = {Canvas.KEY_UP, Canvas.KEY_DOWN, Canvas.KEY_LEFT, Canvas.KEY_RIGHT};
			for (int i = 0; i < 4; i++) {
				int bit = 1 << i;
				if ((m & bit) != 0 && (mask & bit) == 0) target.postKeyPressed(codes[i]);
				if ((m & bit) == 0 && (mask & bit) != 0) target.postKeyReleased(codes[i]);
			}
			mask = m;
		}

		@Override
		protected void onDown() {
			selected = true;
		}

		@Override
		public void onUp() {
			selected = false;
			knobX = Float.NaN;
			if (target != null) setMask(0);
		}

		@Override
		public void onRepeat() {
		}

		@Override
		void paint(CanvasWrapper g) {
			int alpha = skinAlpha(this);
			boolean held = !Float.isNaN(knobX);
			if (portemon != null) {
				portemon.stick(g.getCanvas(), rect, knobX, knobY, held, alpha);
				return;
			}
			if (buttonStyle != STYLE_SKIN) {
				paintPlain(g, held);
				return;
			}
			Bitmap base = PadSkin.get("joystick_base");
			Bitmap knob = held && PadSkin.get("joystick_pressed") != null
					? PadSkin.get("joystick_pressed") : PadSkin.get("joystick_knob");
			if (!held && PadSkin.get("joystick") != null && layoutEditMode == LAYOUT_EOF) {
				fitSkin(PadSkin.get("joystick"), rect);
				g.drawSkin(PadSkin.get("joystick"), skinRect, alpha, 0);
				return;
			}
			if (base != null && knob != null && layoutEditMode == LAYOUT_EOF) {
				fitSkin(base, rect);
				g.drawSkin(base, skinRect, alpha, 0);
				RectF k = new RectF(skinRect);
				// finger travel (up to half the radius) -> stick travel whose edge reaches the rim
				if (held) k.offset((knobX - rect.centerX()) * 0.68f, (knobY - rect.centerY()) * 0.68f);
				// a Citra knob picture is smaller than the plate: keep its own proportion
				if (knob.getWidth() < base.getWidth()) {
					float s = k.width() / base.getWidth();
					float w = knob.getWidth() * s / 2, h = knob.getHeight() * s / 2;
					k.set(k.centerX() - w, k.centerY() - h, k.centerX() + w, k.centerY() + h);
				}
				g.drawSkin(knob, k, alpha, 0);
				return;
			}
			paintPlain(g, held);
		}

		/** No skin: a plate and a knob in the keyboard colors. */
		private void paintPlain(CanvasWrapper g, boolean held) {
			int a = (opaque || layoutEditMode != LAYOUT_EOF ? 0xFF : settings.vkAlpha) << 24;
			g.setFillColor(a | settings.vkBgColor);
			g.setDrawColor(a | settings.vkOutlineColor);
			g.fillArc(rect, 0, 360);
			g.drawArc(rect, 0, 360);
			float kr = rect.width() * 0.22f;
			float cx = held ? knobX : rect.centerX(), cy = held ? knobY : rect.centerY();
			RectF k = new RectF(cx - kr, cy - kr, cx + kr, cy + kr);
			g.setFillColor(a | (selected ? settings.vkBgColorSelected : settings.vkFgColor));
			g.fillArc(k, 0, 360);
		}
	}

	/** Switches between the on-screen gamepad and the keys; sends nothing to the game. */
	private class ToggleKey extends VirtualKey {

		ToggleKey() {
			super(TOGGLE_KEY_CODE, "123");
		}

		@Override
		protected void onDown() {
			selected = true;
		}

		@Override
		public void onUp() {
			if (!selected) return;
			selected = false;
			// after the touch is done: the layout and its keys are rebuilt
			overlayView.post(VirtualKeyboard.this::togglePad);
		}

		@Override
		public void onRepeat() {
		}

		@Override
		public void run() {
		}
	}

	private class MenuKey extends VirtualKey {

		MenuKey() {
			super(KeyMapper.KEY_OPTIONS_MENU, "M");
		}

		@Override
		protected void onDown() {
			selected = true;
			handler.postDelayed(this, 500);
		}

		@Override
		public void onUp() {
			if (selected) {
				selected = false;
				handler.removeCallbacks(this);
				MicroActivity activity = ContextHolder.getActivity();
				if (activity != null) {
					activity.openOptionsMenu();
				}
			}
		}

		@Override
		public void run() {
			selected = false;
			MicroActivity activity = ContextHolder.getActivity();
			if (activity != null) {
				activity.runOnUiThread(activity::showExitConfirmation);
			}
		}
	}
}
