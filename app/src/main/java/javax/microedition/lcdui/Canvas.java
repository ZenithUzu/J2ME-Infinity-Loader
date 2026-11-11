/*
 * Copyright 2012 Kulikov Dmitriy
 * Copyright 2017-2020 Nikita Shakarun
 * Copyright 2019-2023 Yury Kharchenko
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

package javax.microedition.lcdui;

import static android.opengl.GLES20.*;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.lcdui.commands.AbstractSoftKeysBar;
import javax.microedition.lcdui.event.CanvasEvent;
import javax.microedition.lcdui.event.Event;
import javax.microedition.lcdui.event.EventFilter;
import javax.microedition.lcdui.graphics.CanvasView;
import javax.microedition.lcdui.graphics.CanvasWrapper;
import javax.microedition.lcdui.graphics.GlesView;
import javax.microedition.lcdui.graphics.ShaderProgram;
import javax.microedition.lcdui.keyboard.KeyMapper;
import javax.microedition.lcdui.keyboard.VirtualKeyboard;
import javax.microedition.lcdui.overlay.FpsCounter;
import javax.microedition.lcdui.overlay.Layer;
import javax.microedition.lcdui.overlay.Overlay;
import javax.microedition.lcdui.overlay.OverlayView;
import javax.microedition.shell.MicroActivity;
import javax.microedition.util.ContextHolder;

import io.reactivex.Single;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.ShaderInfo;

@SuppressWarnings({"WeakerAccess", "unused"})
public abstract class Canvas extends Displayable {
	private static final String TAG = Canvas.class.getName();

	public static final int KEY_POUND = 35;
	public static final int KEY_STAR = 42;
	public static final int KEY_NUM0 = 48;
	public static final int KEY_NUM1 = 49;
	public static final int KEY_NUM2 = 50;
	public static final int KEY_NUM3 = 51;
	public static final int KEY_NUM4 = 52;
	public static final int KEY_NUM5 = 53;
	public static final int KEY_NUM6 = 54;
	public static final int KEY_NUM7 = 55;
	public static final int KEY_NUM8 = 56;
	public static final int KEY_NUM9 = 57;

	public static final int KEY_UP = -1;
	public static final int KEY_DOWN = -2;
	public static final int KEY_LEFT = -3;
	public static final int KEY_RIGHT = -4;
	public static final int KEY_FIRE = -5;
	public static final int KEY_SOFT_LEFT = -6;
	public static final int KEY_SOFT_RIGHT = -7;
	public static final int KEY_CLEAR = -8;
	public static final int KEY_SEND = -10;
	public static final int KEY_END = -11;

	public static final int UP = 1;
	public static final int LEFT = 2;
	public static final int RIGHT = 5;
	public static final int DOWN = 6;
	public static final int FIRE = 8;
	public static final int GAME_A = 9;
	public static final int GAME_B = 10;
	public static final int GAME_C = 11;
	public static final int GAME_D = 12;

	private static final float FULLSCREEN_HEIGHT_RATIO = 0.85f;

	/** 自动选择超采样倍率 */
	public static final int SUPERSAMPLING_AUTO = 0;
	/** 用户可选的最大倍率 */
	public static final int MAX_SUPERSAMPLING = 10;
	/** 单边上限（GLES2 纹理安全值） */
	private static final int MAX_SUPERSAMPLING_DIMENSION = 4096;
	/** 总面积上限（约 32MB ARGB_8888） */
	private static final long MAX_SUPERSAMPLING_PIXELS = 8L * 1024 * 1024;

	private static boolean filter;
	private static boolean touchInput;
	private static int graphicsMode;
	private static int supersampling;
	private static int mutableScale = 1;
	private static ShaderInfo shaderFilter;
	private static boolean parallelRedraw;
	private static boolean forceFullscreen;
	private static boolean showFps;
	private static int backgroundColor;
	private static int scaleRatio;
	private static int fpsLimit;
	private static boolean screenshotRawMode;
	private static int scaleType;
	private static int screenGravity;
	/** Image rotation in degrees: 0, 90 (clockwise) or 270 */
	private static int screenRotation;

	private final Object bufferLock = new Object();
	private final Object surfaceLock = new Object();
	private final PaintEvent paintEvent = new PaintEvent();
	private final SoftBar softBar = new SoftBar();
	private final CanvasWrapper canvasWrapper = new CanvasWrapper(filter);
	private final RectF virtualScreen = new RectF();
	private final RectF rotatedScreen = new RectF();

	protected int width, height;
	protected int maxHeight;
	private LinearLayout layout;
	private SurfaceView innerView;
	private Surface surface;
	private GLRenderer renderer;
	private int displayWidth;
	private int displayHeight;
	private boolean fullscreen;
	private boolean visible;
	private boolean sizeChangedCalled;
	private Image offscreen;
	private Image offscreenCopy;
	private static boolean frameBlend;
	private static boolean autoFit;
	/** auto fit: part of the game screen shown, in game pixels (null = everything) */
	private volatile Rect fitArea;
	private int shownL, shownT, shownW = 1, shownH = 1;
	private boolean fitDecided;
	private long fitStart, fitLastScan;
	private Rect fitLastBox;
	private int fitStable;
	private int[] fitRow;
	/** anti-flicker: last raw frame, spare buffer and the paint that mixes them 50/50 */
	private Bitmap blendLast, blendRaw;
	private android.graphics.Canvas blendLastCanvas, blendRawCanvas, blendOutCanvas;
	private Bitmap blendOut;
	private final android.graphics.Paint blendPaint = new android.graphics.Paint();
	private int onX, onY, onWidth, onHeight;
	private Handler uiHandler;
	private Overlay overlay;
	private FpsCounter fpsCounter;
	private boolean skipLeftSoft;
	private boolean skipRightSoft;
	private int[][] lastPointerPos = new int[20][2];
	/** 屏幕缓冲实际生效的超采样倍率，1 表示未开启 */
	private int supersamplingFactor = 1;

	protected Canvas() {
		this(forceFullscreen);
	}

	protected Canvas(boolean fullscreen) {
		this.fullscreen = fullscreen;
		super.softBar = softBar;
		if (graphicsMode == 1) {
			renderer = new GLRenderer();
		}
		if (parallelRedraw) {
			uiHandler = new Handler(Looper.getMainLooper(), msg -> repaintScreen());
		}
		displayWidth = ContextHolder.getDisplayWidth();
		displayHeight = ContextHolder.getDisplayHeight();
		updateSize();
	}

	public static void setShaderFilter(ShaderInfo shader) {
		Canvas.shaderFilter = shader;
	}

	public static void setAutoFit(boolean enable) {
		Canvas.autoFit = enable;
	}

	/**
	 * Auto fit (idea from JarNix): many games were made for one fixed screen (e.g. 176x208)
	 * and only draw that part of a bigger one. Pixels the game never touched stay transparent,
	 * so once the drawn area has been stable for a while, show only that area.
	 * Waits 12 s first, so a logo or a transition is not taken as the game screen.
	 * Call under bufferLock after a frame was written to offscreenCopy.
	 */
	private void autoFitCheck() {
		if (!autoFit || fitDecided) return;
		long now = System.currentTimeMillis();
		if (fitStart == 0) fitStart = now;
		if (now - fitStart > 120_000) {
			fitDecided = true; // undecided after 2 minutes: keep the whole screen
			return;
		}
		if (now - fitStart < 12_000 || now - fitLastScan < 1000) return;
		fitLastScan = now;
		Bitmap bm = offscreenCopy.getBitmap();
		int s = offscreenCopy.getPixelScale();
		int bw = bm.getWidth();
		int w = Math.min(width, bw / s), h = Math.min(height, bm.getHeight() / s);
		if (fitRow == null || fitRow.length != bw) fitRow = new int[bw];
		int l = w, t = h, r = -1, b = -1;
		for (int y = 0; y < h; y++) {
			bm.getPixels(fitRow, 0, bw, 0, y * s, bw, 1);
			for (int x = 0; x < w; x++) {
				if ((fitRow[x * s] >>> 24) != 0) {
					if (x < l) l = x;
					if (x > r) r = x;
					if (y < t) t = y;
					b = y;
				}
			}
		}
		if (r < 0) return; // nothing drawn yet
		Rect box = new Rect(l, t, r + 1, b + 1);
		fitStable = box.equals(fitLastBox) ? fitStable + 1 : 0;
		fitLastBox = box;
		if (fitStable < 5) return; // the drawn area must stay the same for ~5 s
		fitDecided = true;
		boolean smaller = box.width() <= width - 4 || box.height() <= height - 4;
		if (smaller && box.width() >= 96 && box.height() >= 64) {
			fitArea = box;
			android.app.Activity activity = ContextHolder.getActivity();
			if (activity != null) activity.runOnUiThread(this::updateSize);
		}
	}

	public static void setFrameBlend(boolean enable) {
		Canvas.frameBlend = enable;
	}

	/**
	 * Mixes the frame just written to offscreenCopy with the previous one (call under bufferLock).
	 * Fixes games that fake transparency with lines/dots that alternate every frame:
	 * old slow LCDs blended them, a modern screen shows them flickering.
	 */
	private void blendFrame() {
		if (!frameBlend) return;
		Bitmap cur = offscreenCopy.getBitmap();
		int w = cur.getWidth(), h = cur.getHeight();
		if (blendLast == null || blendLast.getWidth() != w || blendLast.getHeight() != h) {
			blendLast = cur.copy(Bitmap.Config.ARGB_8888, true);
			blendRaw = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
			blendLastCanvas = new android.graphics.Canvas(blendLast);
			blendRawCanvas = new android.graphics.Canvas(blendRaw);
			blendOutCanvas = new android.graphics.Canvas(cur);
			blendOut = cur;
			blendPaint.setAlpha(128);
			return;
		}
		if (blendOut != cur) {
			blendOutCanvas = new android.graphics.Canvas(cur);
			blendOut = cur;
		}
		blendRawCanvas.drawBitmap(cur, 0, 0, null);              // keep this raw frame
		blendOutCanvas.drawBitmap(blendLast, 0, 0, blendPaint);  // shown = 50% this + 50% last
		Bitmap t = blendLast; blendLast = blendRaw; blendRaw = t;
		android.graphics.Canvas tc = blendLastCanvas; blendLastCanvas = blendRawCanvas; blendRawCanvas = tc;
	}

	/** 0 none, 1 = 90° clockwise, 2 = 270°, 3 = automatic (see AutoRotate) */
	public static void setScreenRotation(int mode) {
		Canvas.screenRotation = mode == 1 ? 90 : mode == 2 ? 270 : 0;
		if (mode == 3) AutoRotate.begin();
	}

	public static void setScale(int screenGravity, int scaleType, int scaleRatio) {
		Canvas.screenGravity = screenGravity;
		Canvas.scaleType = scaleType;
		Canvas.scaleRatio = scaleRatio;
	}

	public static void setBackgroundColor(int color) {
		backgroundColor = color | 0xFF000000;
	}

	public static void setFilterBitmap(boolean filter) {
		Canvas.filter = filter;
	}

	public static void setHasTouchInput(boolean touchInput) {
		Canvas.touchInput = touchInput;
	}

	public static void setGraphicsMode(int mode, boolean parallel) {
		Canvas.graphicsMode = mode;
		Canvas.parallelRedraw = (mode == 0 || mode == 3) && parallel;
	}

protected int getSupersamplingFactor() {
		return supersamplingFactor;
	}

	static int mutableImageScale(int width, int height) {
		int f = Math.max(1, mutableScale);
		while (f > 1 && (long) width * height * f * f > MAX_SUPERSAMPLING_PIXELS) {
			f--;
		}
		return f;
	}

	public static void setSupersampling(int factor) {
		Canvas.supersampling = Math.max(SUPERSAMPLING_AUTO,
				Math.min(factor, MAX_SUPERSAMPLING));
	}

	public static void setForceFullscreen(boolean forceFullscreen) {
		Canvas.forceFullscreen = forceFullscreen;
	}

	public static void setShowFps(boolean showFps) {
		Canvas.showFps = showFps;
	}

	public static void setLimitFps(int fpsLimit) {
		if (fpsLimit == 0 && (graphicsMode == 1 || graphicsMode == 2)) {
			// hack for async redraw
			fpsLimit = 1000;
		}
		Canvas.fpsLimit = fpsLimit;
	}

	public static void setScreenshotRawMode(boolean enable) {
		screenshotRawMode = enable;
	}

	public int getKeyCode(int gameAction) {
		int res = KeyMapper.getKeyCode(gameAction);
		if (res != Integer.MAX_VALUE) {
			return res;
		} else {
			throw new IllegalArgumentException("unknown game action " + gameAction);
		}
	}

	public int getGameAction(int keyCode) {
		return KeyMapper.getGameAction(keyCode);
	}

	public String getKeyName(int keyCode) {
		String res = KeyMapper.getKeyName(keyCode);
		if (res != null) {
			return res;
		} else {
			throw new IllegalArgumentException("unknown keycode " + keyCode);
		}
	}

	public void postKeyPressed(int keyCode) {
		if (keyCode == KEY_SOFT_LEFT && softBar.fireLeftSoft()) {
			skipLeftSoft = true;
			return;
		} else if (keyCode == KEY_SOFT_RIGHT && softBar.fireRightSoft()) {
			skipRightSoft = true;
			return;
		}
		Display.postEvent(CanvasEvent.getInstance(this,
				CanvasEvent.KEY_PRESSED,
				KeyMapper.convertKeyCode(keyCode)));
	}

	public void postKeyReleased(int keyCode) {
		if (keyCode == KEY_SOFT_LEFT && skipLeftSoft) {
			skipLeftSoft = false;
			return;
		} else if (keyCode == KEY_SOFT_RIGHT && skipRightSoft) {
			skipRightSoft = false;
			return;
		}
		Display.postEvent(CanvasEvent.getInstance(this,
				CanvasEvent.KEY_RELEASED,
				KeyMapper.convertKeyCode(keyCode)));
	}

	public void postKeyRepeated(int keyCode) {
		if (keyCode == KEY_SOFT_LEFT && skipLeftSoft) {
			return;
		} else if (keyCode == KEY_SOFT_RIGHT && skipRightSoft) {
			return;
		}
		Display.postEvent(CanvasEvent.getInstance(this,
				CanvasEvent.KEY_REPEATED,
				KeyMapper.convertKeyCode(keyCode)));
	}

	public void doShowNotify() {
		visible = true;
		showNotify();
	}

	public void doHideNotify() {
		hideNotify();
		visible = false;
	}

	public void onDraw(android.graphics.Canvas canvas) {
		if (graphicsMode != 2) return; // Fix for Android Pie
		CanvasWrapper g = canvasWrapper;
		g.bind(canvas);
		g.clear(backgroundColor);
		synchronized (bufferLock) {
			offscreenCopy.getBitmap().prepareToDraw();
			drawScreen(g, canvas);
		}
		if (fpsCounter != null) {
			fpsCounter.increment();
		}
		frameRateTick();
	}

	/**
	 * Screenshot of what is shown (from JarNix): only the auto fit area, turned like the screen,
	 * and enlarged by a whole number with sharp pixels (no blur) to about the size on screen.
	 * "Raw" mode keeps the game's own resolution.
	 */
	public Single<Bitmap> getScreenShot() {
		return Single.create(emitter -> {
			Bitmap bitmap;
			synchronized (bufferLock) {
				int s = offscreenCopy.getPixelScale();
				Bitmap src = Bitmap.createBitmap(offscreenCopy.getBitmap(),
						shownL * s, shownT * s, shownW * s, shownH * s);
				int k = screenshotRawMode ? 1 : Math.max(1, Math.round(
						Math.max(onWidth, onHeight) / (float) Math.max(shownW, shownH)));
				android.graphics.Matrix m = new android.graphics.Matrix();
				m.postScale((float) k / s, (float) k / s);
				m.postRotate(screenRotation);
				// supersampled: smooth downscale keeps text sharp; otherwise exact pixel blocks
				bitmap = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, s > 1);
			}
			emitter.onSuccess(bitmap);
		});
	}


	/**
	 * 选择超采样倍率：AUTO 模式按"显示区是逻辑尺寸的几倍"来定，做到接近 1:1 映射。
	 */
	private int calculateSupersampling(int width, int height) {
		if (width <= 0 || height <= 0) {
			return 1;
		}
		int factor;
		if (supersampling == SUPERSAMPLING_AUTO) {
			// with a rotated image, the on-screen width shows the game height
			int shownW = screenRotation != 0 ? onHeight : onWidth;
			int shownH = screenRotation != 0 ? onWidth : onHeight;
			float ratio = Math.max((float) shownW / width, (float) shownH / height);
			factor = Math.round(ratio);
		} else {
			factor = supersampling;
		}
		factor = Math.max(1, Math.min(factor, MAX_SUPERSAMPLING));
		while (factor > 1 && (width * factor > MAX_SUPERSAMPLING_DIMENSION
				|| height * factor > MAX_SUPERSAMPLING_DIMENSION
				|| (long) width * height * factor * factor > MAX_SUPERSAMPLING_PIXELS)) {
			factor--;
		}
		return factor;
	}

	private boolean checkSizeChanged() {
		int tmpWidth = width;
		int tmpHeight = height;
		updateSize();
		return width != tmpWidth || height != tmpHeight;
	}

	/**
	 * Update the size and position of the virtual screen relative to the real one.
	 */
	public void updateSize() {
		/*
		 * We turn the sizes of the virtual screen into the sizes of the visible canvas.
		 *
		 * At the same time, we take into account that one or both virtual sizes can be less
		 * than zero, which means auto-selection of this size so that the resulting canvas
		 * has the same aspect ratio as the actual screen of the device.
		 */
		int scaledDisplayHeight;
		VirtualKeyboard vk = ContextHolder.getVk();
		boolean isPhoneSkin = vk != null && vk.isPhone();

		// if phone keyboard layout is active, then scale down the virtual screen
		if (isPhoneSkin) {
			float vkHeight = vk.getPhoneKeyboardHeight(displayWidth, displayHeight);
			scaledDisplayHeight = (int) (displayHeight - vkHeight - 1);
		} else {
			scaledDisplayHeight = displayHeight;
		}
		boolean rotated = screenRotation != 0;
		// when the image is rotated, the game sees the screen sideways
		int fitDisplayWidth = rotated ? scaledDisplayHeight : displayWidth;
		int fitDisplayHeight = rotated ? displayWidth : scaledDisplayHeight;
		if (virtualWidth > 0) {
			if (virtualHeight > 0) {
				/*
				 * the width and height of the canvas are strictly set
				 */
				width = virtualWidth;
				height = virtualHeight;
			} else {
				/*
				 * only the canvas width is set
				 * height is selected by the ratio of the real screen
				 */
				width = virtualWidth;
				height = fitDisplayHeight * virtualWidth / fitDisplayWidth;
			}
		} else {
			if (virtualHeight > 0) {
				/*
				 * only the canvas height is set
				 * width is selected by the ratio of the real screen
				 */
				width = fitDisplayWidth * virtualHeight / fitDisplayHeight;
				height = virtualHeight;
			} else {
				/*
				 * nothing is set - screen-sized canvas
				 */
				width = displayWidth;
				height = scaledDisplayHeight;
			}
		}

		/*
		 * We turn the size of the canvas into the size of the image
		 * that will be displayed on the screen of the device.
		 */
		int scaleRatio = Canvas.scaleRatio;
		Rect fa = fitArea;
		if (fa != null && fa.right <= width && fa.bottom <= height) {
			shownL = fa.left;
			shownT = fa.top;
			shownW = fa.width();
			shownH = fa.height();
		} else {
			fa = null;
			shownL = 0;
			shownT = 0;
			shownW = width;
			shownH = height;
		}
		int imgWidth = rotated ? shownH : shownW;
		int imgHeight = rotated ? shownW : shownH;
		switch (scaleType) {
			case 0:
				// without scaling
				onWidth = imgWidth;
				onHeight = imgHeight;
				break;
			case 1:
				// try to fit in width
				onWidth = displayWidth;
				onHeight = imgHeight * displayWidth / imgWidth;
				if (onHeight > scaledDisplayHeight) {
					// if height is too big, then fit in height
					onHeight = scaledDisplayHeight;
					onWidth = imgWidth * scaledDisplayHeight / imgHeight;
				}
				if (scaleRatio > 100) {
					scaleRatio = 100;
				}
				break;
			case 2:
				// scaling without preserving the aspect ratio:
				// just stretch the picture to full screen
				onWidth = displayWidth;
				onHeight = scaledDisplayHeight;
				if (scaleRatio > 100) {
					scaleRatio = 100;
				}
				break;
			case 3:
				// integer scaling: each game pixel becomes exactly N x N screen pixels
				int intFactor = Math.max(1, Math.min(displayWidth / imgWidth, scaledDisplayHeight / imgHeight));
				onWidth = imgWidth * intFactor;
				onHeight = imgHeight * intFactor;
				scaleRatio = 100;
				break;
			case 4: {
				// 16:9 (from JarNix): widen the game toward the screen, but never past 16:9
				// (or 9:16 on a tall screen): fills almost everything and distorts little
				float game = (float) imgWidth / imgHeight;
				float screen = (float) displayWidth / scaledDisplayHeight;
				if (screen >= game) {
					float target = Math.min(screen, Math.max(game, 16f / 9f));
					onHeight = scaledDisplayHeight;
					onWidth = Math.round(scaledDisplayHeight * target);
				} else {
					float target = Math.max(screen, Math.min(game, 9f / 16f));
					onWidth = displayWidth;
					onHeight = Math.round(displayWidth / target);
				}
				if (scaleRatio > 100) {
					scaleRatio = 100;
				}
				break;
			}
		}

		onWidth = onWidth * scaleRatio / 100;
		onHeight = onHeight * scaleRatio / 100;

		switch (Canvas.screenGravity) {
			case 0: // left
				onX = 0;
				onY = (scaledDisplayHeight - onHeight) / 2;
				break;
			case 1: // top
				onX = (displayWidth - onWidth) / 2;
				onY = 0;
				break;
			case 2: // center
				onX = (displayWidth - onWidth) / 2;
				onY = (scaledDisplayHeight - onHeight) / 2;
				break;
			case 3: // right
				onX = displayWidth - onWidth;
				onY = (scaledDisplayHeight - onHeight) / 2;
				break;
			case 4: // bottom
				onX = (displayWidth - onWidth) / 2;
				onY = scaledDisplayHeight - onHeight;
				break;
		}


		/*
		 * calculate the maximum height
		 */
		maxHeight = height;

		softBar.resize();
		/*
		 * calculate the current height
		 */
		float softBarHeight = softBar.bounds.height();
		if (softBarHeight > 0 && !rotated && fa == null) {
			float scaleY = (float) onHeight / height;
			height = (int) (height - softBarHeight / scaleY);
			onHeight -= softBarHeight;
		}

		RectF screen = new RectF(0, 0, displayWidth, displayHeight);
		virtualScreen.set(onX, onY, onX + onWidth, onY + onHeight);

		int newSupersampling = calculateSupersampling(width, maxHeight);
		synchronized (bufferLock) {
			if (offscreen == null || offscreen.getPixelScale() != newSupersampling) {
				while (true) {
					try {
						offscreen = Image.createImage(width, maxHeight, 0, newSupersampling);
						offscreenCopy = Image.createImage(width, maxHeight, 0, newSupersampling);
						supersamplingFactor = newSupersampling;
						mutableScale = Math.min(newSupersampling, 3);
						break;
					} catch (OutOfMemoryError e) {
						offscreen = null;
						offscreenCopy = null;
						if (newSupersampling <= 1) {
							throw e;
						}
						newSupersampling--;
						Log.w(TAG, "内存不足，超采样降级为 x" + newSupersampling);
					}
				}
			} else if (offscreen.getWidth() != width || offscreen.getHeight() != height) {
				offscreen.setSize(width, height);
				offscreenCopy.setSize(width, height);
			}
			// 超采样缓冲必须用双线性缩小，否则会有摩尔纹
			canvasWrapper.setFilterBitmap(supersamplingFactor > 1 || filter);
		}
		AutoRotate.setScreenSize(width, height);
		if (overlay != null) {
			overlay.resize(screen, onX, onY, onX + onWidth, onY + onHeight + softBarHeight);
		}

		if (rotated) {
			float cx = virtualScreen.centerX(), cy = virtualScreen.centerY();
			float hw = virtualScreen.height() / 2, hh = virtualScreen.width() / 2;
			rotatedScreen.set(cx - hw, cy - hh, cx + hw, cy + hh);
		}

		if (graphicsMode == 1) {
			float gl = 2.0f * virtualScreen.left / displayWidth - 1.0f;
			float gt = 1.0f - 2.0f * virtualScreen.top / displayHeight;
			float gr = 2.0f * virtualScreen.right / displayWidth - 1.0f;
			float gb = 1.0f - 2.0f * virtualScreen.bottom / displayHeight;
			float bh = offscreen.getBitmap().getHeight(), bw = offscreen.getBitmap().getWidth();
			float tx = shownL * supersamplingFactor / bw;
			float ty = shownT * supersamplingFactor / bh;
			float th = (float) (shownT + shownH) * supersamplingFactor / bh;
			float tw = (float) (shownL + shownW) * supersamplingFactor / bw;
			renderer.updateSize(gl, gt, gr, gb, th, tw, tx, ty);
		}
		repaintInternal();
	}

	/**
	 * Convert the screen coordinates of the pointer into the virtual ones.
	 *
	 * @param x the pointer coordinate on the real screen
	 * @return the corresponding pointer coordinate on the virtual screen
	 */
	private float convertPointerX(float x, float y) {
		if (screenRotation == 90) return shownL + (y - onY) * shownW / onHeight;
		if (screenRotation == 270) return shownL + (1 - (y - onY) / onHeight) * shownW;
		return shownL + (x - onX) * shownW / onWidth;
	}

	/**
	 * Convert the screen coordinates of the pointer into the virtual ones.
	 *
	 * @param y the pointer coordinate on the real screen
	 * @return the corresponding pointer coordinate on the virtual screen
	 */
	private float convertPointerY(float x, float y) {
		if (screenRotation == 90) return shownT + (1 - (x - onX) / onWidth) * shownH;
		if (screenRotation == 270) return shownT + (x - onX) * shownH / onWidth;
		return shownT + (y - onY) * shownH / onHeight;
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public View getDisplayableView() {
		if (layout == null) {
			layout = (LinearLayout) super.getDisplayableView();
			MicroActivity activity = ContextHolder.getActivity();
			if (graphicsMode == 1) {
				GlesView glesView = new GlesView(activity);
				glesView.setRenderer(renderer);
				glesView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
				renderer.setView(glesView);
				innerView = glesView;
			} else {
				CanvasView canvasView = new CanvasView(this, activity);
				if (graphicsMode == 2) {
					canvasView.setWillNotDraw(false);
				}
				canvasView.getHolder().setFormat(PixelFormat.RGBA_8888);
				innerView = canvasView;
			}
			ViewCallbacks callback = new ViewCallbacks(innerView);
			innerView.getHolder().addCallback(callback);
			innerView.setOnTouchListener(callback);
			innerView.setOnKeyListener(callback);
			innerView.setOnGenericMotionListener(callback);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
				// from JarNix: touches reach the game as they happen, not batched per frame
				innerView.requestUnbufferedDispatch(InputDevice.SOURCE_CLASS_POINTER);
			}
			innerView.setFocusableInTouchMode(true);
			layout.addView(innerView);
			innerView.requestFocus();
		}
		return layout;
	}

	@Override
	public void clearDisplayableView() {
		super.clearDisplayableView();
		layout = null;
		innerView = null;
	}

	public void getVirtualScreenBounds(RectF out) {
		out.set(virtualScreen);
	}

	public void setFullScreenMode(boolean flag) {
		if (fullscreen == flag) {
			return;
		}
		fullscreen = flag;
		updateSize();
		softBar.notifyChanged();
		if (!visible) {
			return;
		}
		Display.postEvent(CanvasEvent.getInstance(this, CanvasEvent.SIZE_CHANGED, width, height));
		repaintInternal();
	}

	public boolean hasPointerEvents() {
		return touchInput;
	}

	public boolean hasPointerMotionEvents() {
		return touchInput;
	}

	public boolean hasRepeatEvents() {
		return true;
	}

	public boolean isDoubleBuffered() {
		return true;
	}

	@Override
	public int getWidth() {
		return width;
	}

	@Override
	public int getHeight() {
		return height;
	}

	protected abstract void paint(Graphics g);

	public final void repaint() {
		repaint(0, 0, width, height);
	}

	public final void repaint(int x, int y, int width, int height) {
		limitFps();
		boolean post;
		synchronized (paintEvent.clip) {
			post = paintEvent.invalidateClip(this, x, y, x + width, y + height) && !paintEvent.isPending;
			if (post) {
				paintEvent.isPending = true;
			}
		}
		if (post) {
			Display.postEvent(paintEvent);
		}
	}

	private void repaintInternal() {
		synchronized (paintEvent.clip) {
			paintEvent.invalidateClip(this, 0, 0, width, height);
		}
		Display.postEvent(paintEvent);
	}

	// GameCanvas
	protected void flushBuffer(Image image, int x, int y, int width, int height) {
		limitFps();
		if (width <= 0 || height <= 0 ||
				x + width < 0 || y + height < 0 ||
				x >= this.width || y >= this.height) {
			return;
		}
		synchronized (bufferLock) {
			if (Thread.holdsLock(paintEvent)) {
				offscreen.getSingleGraphics().flush(image, x, y, width, height);
				return;
			}
			offscreenCopy.getSingleGraphics().flush(image, x, y, width, height);
			blendFrame();
			autoFitCheck();
			AutoRotate.frameShown(this);
		}
		requestFlushToScreen();
	}

	// ExtendedImage
	public void flushBuffer(Image image, int x, int y) {
		limitFps();
		synchronized (bufferLock) {
			image.copyTo(offscreenCopy, x, y);
			blendFrame();
			autoFitCheck();
			AutoRotate.frameShown(this);
		}
		requestFlushToScreen();
	}

	/** when the next frame may start (nanoTime), for a fixed cadence */
	private long nextFrameNs;

	/**
	 * Fixed-cadence FPS limit (from JarNix): 30 gives 30, not ~27. Each frame has its own
	 * deadline, so rounding and sleep overshoot do not add up; a frame that is late by more
	 * than one period resets the schedule instead of rushing to catch up.
	 * At 2x-16x game speed the limit rises with it.
	 */
	private void limitFps() {
		if (fpsLimit <= 0) return;
		long period = (long) (1_000_000_000L / (fpsLimit * javax.microedition.util.VirtualClock.getSpeed()));
		long now = System.nanoTime();
		if (nextFrameNs == 0 || now - nextFrameNs > period) {
			nextFrameNs = now;
		}
		long wait = nextFrameNs - now;
		if (wait > 0) {
			try {
				Thread.sleep(wait / 1_000_000, (int) (wait % 1_000_000));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		nextFrameNs += period;
	}

	private final Rect shownSrc = new Rect();

	private void drawScreen(CanvasWrapper g, android.graphics.Canvas canvas) {
		RectF dst = virtualScreen;
		if (screenRotation != 0) {
			canvas.save();
			canvas.rotate(screenRotation, virtualScreen.centerX(), virtualScreen.centerY());
			dst = rotatedScreen;
		}
		if (fitArea == null) {
			g.drawImage(offscreenCopy, dst);
		} else {
			int s = offscreenCopy.getPixelScale();
			shownSrc.set(shownL * s, shownT * s, (shownL + shownW) * s, (shownT + shownH) * s);
			g.drawBitmap(offscreenCopy.getBitmap(), shownSrc, dst);
		}
		if (screenRotation != 0) {
			canvas.restore();
		}
	}


	private int rateFrames;
	private long rateWindowStart;
	private float screenRate;

	/**
	 * Dynamic refresh rate (from JarNix, Android 11+): ask the display for 60, 90 or 120 Hz
	 * depending on the game's FPS (never below 60). A 30 FPS game on a 120 Hz phone no longer
	 * keeps the screen at 120 Hz, which saves battery; a fast game gets the higher rate.
	 */
	private void frameRateTick() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
		rateFrames++;
		long now = android.os.SystemClock.uptimeMillis();
		if (rateWindowStart == 0) {
			rateWindowStart = now;
			return;
		}
		long dt = now - rateWindowStart;
		if (dt < 2000) return;
		float fps = rateFrames * 1000f / dt;
		rateFrames = 0;
		rateWindowStart = now;
		float rate = fps > 95 ? 120 : fps > 65 ? 90 : 60;
		Surface s = surface;
		if (rate == screenRate || s == null || !s.isValid()) return;
		try {
			s.setFrameRate(rate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
			screenRate = rate;
		} catch (Throwable ignored) {
		}
	}

	@SuppressLint("NewApi")
	private boolean repaintScreen() {
		Surface surface = this.surface;
		if (surface == null || !surface.isValid()) {
			return true;
		}
		try {
			synchronized (surfaceLock) {
				android.graphics.Canvas canvas = graphicsMode == 3 ?
						surface.lockHardwareCanvas() : surface.lockCanvas(null);
				if (canvas == null) {
					return true;
				}
				CanvasWrapper g = this.canvasWrapper;
				g.bind(canvas);
				g.clear(backgroundColor);
				synchronized (bufferLock) {
					drawScreen(g, canvas);
				}
				surface.unlockCanvasAndPost(canvas);
			}
			if (fpsCounter != null) {
				fpsCounter.increment();
			}
			frameRateTick();
			if (parallelRedraw) uiHandler.removeMessages(0);
		} catch (Exception e) {
			Log.w(TAG, "repaintScreen: " + e);
		}
		return true;
	}

	/**
	 * After calling this method, an immediate redraw is guaranteed to occur,
	 * and the calling thread is blocked until it is completed.
	 */
	public final void serviceRepaints() {
		Display.getEventQueue().serviceRepaints(paintEvent);
	}

	@Override
	public boolean isShown() {
		return visible;
	}

	protected void showNotify() {
	}

	protected void hideNotify() {
	}

	protected void keyPressed(int keyCode) {
	}

	protected void keyRepeated(int keyCode) {
	}

	protected void keyReleased(int keyCode) {
	}

	public void pointerPressed(int pointer, float x, float y) {
		if (Display.isMultiTouchSupported()) {
			Display.setPointerNumber(pointer);
			pointerPressed(Math.round(x), Math.round(y));
			Display.resetPointerNumber();
		} else if (pointer == 0) {
			pointerPressed(Math.round(x), Math.round(y));
		}
	}

	public void pointerDragged(int pointer, float x, float y) {
		if (Display.isMultiTouchSupported()) {
			Display.setPointerNumber(pointer);
			pointerDragged(Math.round(x), Math.round(y));
			Display.resetPointerNumber();
		} else if (pointer == 0) {
			pointerDragged(Math.round(x), Math.round(y));
		}
	}

	public void pointerReleased(int pointer, float x, float y) {
		if (Display.isMultiTouchSupported()) {
			Display.setPointerNumber(pointer);
			pointerReleased(Math.round(x), Math.round(y));
			Display.resetPointerNumber();
		} else if (pointer == 0) {
			pointerReleased(Math.round(x), Math.round(y));
		}
	}

	protected void pointerPressed(int x, int y) {
	}

	protected void pointerDragged(int x, int y) {
	}

	protected void pointerReleased(int x, int y) {
	}

	void setInvisible() {
		this.visible = false;
	}

	public void doKeyPressed(int keyCode) {
		keyPressed(keyCode);
	}

	public void doKeyRepeated(int keyCode) {
		keyRepeated(keyCode);
	}

	public void doKeyReleased(int keyCode) {
		keyReleased(keyCode);
	}

	private class GLRenderer implements GLSurfaceView.Renderer {
		private final FloatBuffer vbo = ByteBuffer.allocateDirect(8 * 2 * 4)
				.order(ByteOrder.nativeOrder()).asFloatBuffer();
		private GLSurfaceView mView;
		private final int[] bgTextureId = new int[1];
		private ShaderProgram program;
		private boolean isStarted;

		@Override
		public void onSurfaceCreated(GL10 gl, EGLConfig config) {
			program = new ShaderProgram(shaderFilter);
			int c = Canvas.backgroundColor;
			glClearColor((c >> 16 & 0xff) / 255.0f, (c >> 8 & 0xff) / 255.0f, (c & 0xff) / 255.0f, 1.0f);
			glDisable(GL_BLEND);
			glDisable(GL_DEPTH_TEST);
			glDepthMask(false);
			initTex();
			Bitmap bitmap = offscreenCopy.getBitmap();
			program.loadVbo(vbo, bitmap.getWidth(), bitmap.getHeight());
			if (shaderFilter != null && shaderFilter.values != null && program.uSetting >= 0) {
				glUniform4fv(program.uSetting, 1, shaderFilter.values, 0);
			}
			isStarted = true;
		}

		@Override
		public void onSurfaceChanged(GL10 gl, int width, int height) {
			glViewport(0, 0, width, height);
			if (program.uPixelDelta >= 0) {
				glUniform2f(program.uPixelDelta, 1.0f / width, 1.0f / height);
			}
		}

		@Override
		public void onDrawFrame(GL10 gl) {
			glClear(GL_COLOR_BUFFER_BIT);
			synchronized (bufferLock) {
				GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, offscreenCopy.getBitmap(), 0);
			}
			glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
			if (fpsCounter != null) {
				fpsCounter.increment();
			}
			frameRateTick();
		}

		private void initTex() {
			glGenTextures(1, bgTextureId, 0);
			glActiveTexture(GL_TEXTURE0);
			glBindTexture(GL_TEXTURE_2D, bgTextureId[0]);
			glTexParameteri(GLES20.GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter ? GL_LINEAR : GL_NEAREST);
			glTexParameteri(GLES20.GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter ? GL_LINEAR : GL_NEAREST);
			glTexParameteri(GLES20.GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
			glTexParameteri(GLES20.GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

			// юнит текстуры
			glUniform1i(program.uTextureUnit, 0);
		}

		public void updateSize(float gl, float gt, float gr, float gb, float th, float tw, float tx, float ty) {
			synchronized (vbo) {
				FloatBuffer vertex_bg = vbo;
				vertex_bg.rewind();
				// texture corners of the game image: lt, lb, rt, rb
				float[][] t = {{tx, ty}, {tx, th}, {tw, ty}, {tw, th}};
				int[] o;
				if (screenRotation == 90) {
					o = new int[]{1, 3, 0, 2}; // screen lt shows game lb, ...
				} else if (screenRotation == 270) {
					o = new int[]{2, 0, 3, 1};
				} else {
					o = new int[]{0, 1, 2, 3};
				}
				vertex_bg.put(gl).put(gt).put(t[o[0]][0]).put(t[o[0]][1]);// lt
				vertex_bg.put(gl).put(gb).put(t[o[1]][0]).put(t[o[1]][1]);// lb
				vertex_bg.put(gr).put(gt).put(t[o[2]][0]).put(t[o[2]][1]);// rt
				vertex_bg.put(gr).put(gb).put(t[o[3]][0]).put(t[o[3]][1]);// rb
			}
			if (isStarted) {
				mView.queueEvent(() -> {
					Bitmap bitmap = offscreenCopy.getBitmap();
					synchronized (vbo) {
						program.loadVbo(vbo, bitmap.getWidth(), bitmap.getHeight());
					}
				});
			}
		}

		public void requestRender() {
			mView.requestRender();
		}

		public void setView(GLSurfaceView mView) {
			this.mView = mView;
		}

		public void stop() {
			isStarted = false;
			mView.onPause();
		}

		public void start() {
			mView.onResume();
		}

	}

	private class PaintEvent extends Event implements EventFilter {
		final Rect clip = new Rect();

		boolean isPending;

		private int enqueued = 0;

		@Override
		public synchronized void process() {
			if (!visible) {
				return;
			}
			int l, t, r, b;
			synchronized (clip) {
				isPending = false;
				l = clip.left;
				t = clip.top;
				r = clip.right;
				b = clip.bottom;
				clip.setEmpty();
			}
			if (l >= r || t >= b) {
				return;
			}
			Graphics g = offscreen.getSingleGraphics();
			g.reset(l, t, r, b);
			try {
				paint(g);
			} catch (Throwable e) {
				Log.e(TAG, "Error in paint()", e);
			}
			synchronized (bufferLock) {
				offscreen.copyTo(offscreenCopy);
				blendFrame();
				autoFitCheck();
				AutoRotate.frameShown(Canvas.this);
			}
			if (surface == null || !surface.isValid()) {
				return;
			}
			requestFlushToScreen();
		}

		@Override
		public void recycle() {
		}

		@Override
		public void enterQueue() {
			enqueued++;
		}

		@Override
		public void leaveQueue() {
			enqueued--;
		}

		/**
		 * The queue should contain no more than two repaint events
		 * <p>
		 * One won't be smooth enough, and if you add more than two,
		 * then how to determine exactly how many of them need to be added?
		 */
		@Override
		public boolean placeableAfter(Event event) {
			return event != this;
		}

		@Override
		public boolean accept(Event event) {
			return event == this;
		}

		private boolean invalidateClip(Canvas canvas, int l, int t, int r, int b) {
			boolean empty = clip.left >= clip.right || clip.top >= clip.bottom;
			if (empty) {
				clip.left = l;
				clip.top = t;
				clip.right = r;
				clip.bottom = b;
			} else {
				if (clip.left > l) clip.left = l;
				if (clip.top > t) clip.top = t;
				if (clip.right < r) clip.right = r;
				if (clip.bottom < b) clip.bottom = b;
			}
			if (clip.left < 0) clip.left = 0;
			if (clip.top < 0) clip.top = 0;
			if (clip.right > width) clip.right = width;
			if (clip.bottom > height) clip.bottom = height;
			return empty;
		}
	}

	private class ViewCallbacks implements View.OnTouchListener, SurfaceHolder.Callback, View.OnKeyListener,
			View.OnGenericMotionListener {
		private final View mView;
		OverlayView overlayView;

		public ViewCallbacks(View view) {
			mView = view;
			overlayView = ContextHolder.getActivity().binding.overlayView;
		}

		@Override
		public boolean onKey(View v, int keyCode, KeyEvent event) {
			switch (event.getAction()) {
				case KeyEvent.ACTION_DOWN:
					return onKeyDown(keyCode, event);
				case KeyEvent.ACTION_UP:
					return onKeyUp(keyCode, event);
				case KeyEvent.ACTION_MULTIPLE:
					if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
						String characters = event.getCharacters();
						for (int i = 0; i < characters.length(); i++) {
							int cp = characters.codePointAt(i);
							postKeyPressed(cp);
							postKeyReleased(cp);
						}
						return true;
					} else {
						return onKeyDown(keyCode, event);
					}
			}
			return false;
		}

		/** directions currently held by the controller's stick/hat: left, right, up, down */
		private final int[] padKeys = KeyMapper.AXIS_KEYS;
		private final boolean[] padDir = new boolean[padKeys.length];
		/** the controller sends L2/R2 as key presses: do not also read its trigger axes */
		private boolean triggerKeysSeen;

		/**
		 * Game controller left stick and hat (d-pad reported as axes) become d-pad key presses,
		 * so they follow the same mapping as the d-pad keys.
		 */
		@Override
		public boolean onGenericMotion(View v, MotionEvent event) {
			if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK
					|| event.getActionMasked() != MotionEvent.ACTION_MOVE) {
				return false;
			}
			boolean[] now = KeyMapper.axesToKeys(event, triggerKeysSeen);
			for (boolean b : now) {
				if (b) {
					hideVirtualKeyboard();
					break;
				}
			}
			for (int i = 0; i < padKeys.length; i++) {
				if (now[i] != padDir[i]) {
					padDir[i] = now[i];
					KeyEvent key = new KeyEvent(now[i] ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP, padKeys[i]);
					if (now[i]) {
						onKeyDown(padKeys[i], key);
					} else {
						onKeyUp(padKeys[i], key);
					}
				}
			}
			return true;
		}

		/** A real game controller or physical keyboard (not the nav bar, not volume keys). */
		private boolean isPhysicalController(KeyEvent event) {
			InputDevice device = event.getDevice();
			if (device == null || device.isVirtual()) return false;
			int source = event.getSource();
			if ((source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
					|| (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
				return true;
			}
			return (source & InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD
					&& device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC;
		}

		private void hideVirtualKeyboard() {
			VirtualKeyboard vk = ContextHolder.getVk();
			if (vk != null) vk.hideForController();
		}

		public boolean onKeyDown(int keyCode, KeyEvent event) {
			if (isPhysicalController(event)) hideVirtualKeyboard();
			if ((keyCode == KeyEvent.KEYCODE_BUTTON_L2 || keyCode == KeyEvent.KEYCODE_BUTTON_R2)
					&& event.getDevice() != null) {
				triggerKeysSeen = true;
			}
			keyCode = KeyMapper.convertAndroidKeyCode(keyCode, event);
			if (keyCode == 0) {
				return false;
			}
			if (event.getRepeatCount() == 0) {
				if (overlay == null || !overlay.keyPressed(keyCode)) {
					postKeyPressed(keyCode);
				}
			} else {
				if (overlay == null || !overlay.keyRepeated(keyCode)) {
					postKeyRepeated(keyCode);
				}
			}
			return true;
		}

		public boolean onKeyUp(int keyCode, KeyEvent event) {
			int midpKeyCode = KeyMapper.convertAndroidKeyCode(keyCode, event);
			if (midpKeyCode == 0) {
				return false;
			}
			if (overlay == null || !overlay.keyReleased(midpKeyCode)) {
				postKeyReleased(midpKeyCode);
			}
			return true;
		}

		@Override
		@SuppressLint("ClickableViewAccessibility")
		public boolean onTouch(View v, MotionEvent event) {
			switch (event.getActionMasked()) {
				case MotionEvent.ACTION_DOWN:
					if (overlay != null) {
						overlay.show();
					}
				case MotionEvent.ACTION_POINTER_DOWN:
					int index = event.getActionIndex();
					int id = event.getPointerId(index);
					float x = event.getX(index);
					float y = event.getY(index);
					if (overlay != null) {
						overlay.pointerPressed(id, x, y);
					}
					if (touchInput && virtualScreen.contains(x, y)) {
						int cX = Math.round(convertPointerX(x, y));
						int cY = Math.round(convertPointerY(x, y));
						if (id < 20) {
							lastPointerPos[id][0] = cX;
							lastPointerPos[id][1] = cY;
						}
						Display.postEvent(CanvasEvent.getInstance(Canvas.this,
								CanvasEvent.POINTER_PRESSED,
								id,
								cX,
								cY));
					}
					break;
				case MotionEvent.ACTION_MOVE:
					int pointerCount = event.getPointerCount();
					int historySize = event.getHistorySize();
					for (int h = 0; h < historySize; h++) {
						for (int p = 0; p < pointerCount; p++) {
							id = event.getPointerId(p);
							x = event.getHistoricalX(p, h);
							y = event.getHistoricalY(p, h);
							if (overlay != null) {
								overlay.pointerDragged(id, x, y);
							}
							if (touchInput && virtualScreen.contains(x, y)) {
								int cX = Math.round(convertPointerX(x, y));
								int cY = Math.round(convertPointerY(x, y));
								if (id < 20) {
									int oX = lastPointerPos[id][0];
									int oY = lastPointerPos[id][1];
									if (oX == cX && oY == cY) {
										continue;
									}
									lastPointerPos[id][0] = cX;
									lastPointerPos[id][1] = cY;
								}
								Display.postEvent(CanvasEvent.getInstance(Canvas.this,
										CanvasEvent.POINTER_DRAGGED,
										id,
										cX,
										cY));
							}
						}
					}
					for (int p = 0; p < pointerCount; p++) {
						id = event.getPointerId(p);
						x = event.getX(p);
						y = event.getY(p);
						if (overlay != null) {
							overlay.pointerDragged(id, x, y);
						}
						if (touchInput && virtualScreen.contains(x, y)) {
							int cX = Math.round(convertPointerX(x, y));
							int cY = Math.round(convertPointerY(x, y));
							if (id < 20) {
								int oX = lastPointerPos[id][0];
								int oY = lastPointerPos[id][1];
								if (oX == cX && oY == cY) {
									continue;
								}
								lastPointerPos[id][0] = cX;
								lastPointerPos[id][1] = cY;
							}
							Display.postEvent(CanvasEvent.getInstance(Canvas.this,
									CanvasEvent.POINTER_DRAGGED,
									id,
									cX,
									cY));
						}
					}
					break;
				case MotionEvent.ACTION_UP:
					if (overlay != null) {
						overlay.hide();
					}
				case MotionEvent.ACTION_POINTER_UP:
					index = event.getActionIndex();
					id = event.getPointerId(index);
					x = event.getX(index);
					y = event.getY(index);
					if (overlay != null) {
						overlay.pointerReleased(id, x, y);
					}
					if (touchInput && virtualScreen.contains(x, y)) {
						int cX = Math.round(convertPointerX(x, y));
						int cY = Math.round(convertPointerY(x, y));
						lastPointerPos[id][0] = cX;
						lastPointerPos[id][1] = cY;
						Display.postEvent(CanvasEvent.getInstance(Canvas.this,
								CanvasEvent.POINTER_RELEASED,
								id,
								cX,
								cY));
					}
					break;
				case MotionEvent.ACTION_CANCEL:
					if (overlay != null) {
						overlay.cancel();
					}
					break;
				default:
					return false;
			}
			return true;
		}

		@Override
		public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int newWidth, int newHeight) {
			if (displayWidth > displayHeight) {
				if (newWidth < newHeight) {
					softBar.closeMenu();
				}
			} else if (newWidth > newHeight) {
				softBar.closeMenu();
			}
			displayWidth = newWidth;
			displayHeight = newHeight;
			if (checkSizeChanged() || !sizeChangedCalled) {
				Display.postEvent(CanvasEvent.getInstance(Canvas.this,
						CanvasEvent.SIZE_CHANGED,
						width,
						height));
				repaintInternal();
				sizeChangedCalled = true;
			}
		}

		@Override
		public void surfaceCreated(@NonNull SurfaceHolder holder) {
			if (renderer != null) {
				renderer.start();
			}
			surface = holder.getSurface();
			screenRate = 0;
			Display.postEvent(CanvasEvent.getInstance(Canvas.this, CanvasEvent.SHOW_NOTIFY));
			repaintInternal();
			if (showFps) {
				fpsCounter = new FpsCounter(overlayView);
				overlayView.addLayer(fpsCounter);
			}
			overlayView.addLayer(softBar, 0);
			overlayView.setVisibility(true);
			overlay = ContextHolder.getVk();
			if (overlay != null) {
				overlay.setTarget(Canvas.this);
			}
		}

		@Override
		public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
			if (renderer != null) {
				renderer.stop();
			}
			synchronized (surfaceLock) {
				surface = null;
			}
			Display.postEvent(CanvasEvent.getInstance(Canvas.this, CanvasEvent.HIDE_NOTIFY));
			if (fpsCounter != null) {
				fpsCounter.stop();
				overlayView.removeLayer(fpsCounter);
				fpsCounter = null;
			}
			overlayView.removeLayer(softBar);
			softBar.closeMenu();
			overlayView.setVisibility(false);
			if (overlay != null) {
				overlay.setTarget(null);
				overlay.cancel();
				overlay = null;
			}
		}

	}

	private void requestFlushToScreen() {
		if (graphicsMode == 1) {
			if (innerView != null) {
				renderer.requestRender();
			}
		} else if (graphicsMode == 2) {
			if (innerView != null) {
				innerView.postInvalidate();
			}
		} else if (!parallelRedraw) {
			repaintScreen();
		} else if (!uiHandler.hasMessages(0)) {
			uiHandler.sendEmptyMessage(0);
		}
	}

	private class SoftBar extends AbstractSoftKeysBar implements Layer {
		private final OverlayView overlayView;
		private final float padding;
		private final int textColor;
		private final int bgColor;
		private final RectF bounds = new RectF();

		private String leftLabel;
		private String rightLabel;
		private float textScale = 1.0f;

		private SoftBar() {
			super(Canvas.this, false);
			MicroActivity activity = ContextHolder.getActivity();
			this.overlayView = activity.binding.overlayView;
			DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
			padding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 5, metrics);
			textColor = ContextCompat.getColor(activity, R.color.accent);
			bgColor = ContextCompat.getColor(activity, R.color.background);
			notifyChanged();
		}

		private void showPopup() {
			PopupWindow popup = prepareMenu(fullscreen ? 0 : 1);
			popup.setWidth(Math.min(displayWidth, displayHeight) / 2);
			popup.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
			int x = (int) (displayWidth - bounds.right);
			int y = (int) (displayHeight - bounds.top);
			popup.showAtLocation(overlayView, Gravity.RIGHT | Gravity.BOTTOM, x, y);
		}

		@Override
		protected void onCommandsChanged() {
			super.onCommandsChanged();
			if (!fullscreen) {
				int size = commands.size();
				switch (size) {
					case 0:
						break;
					case 1:
						leftLabel = commands.get(0).getAndroidLabel();
						rightLabel = null;
						break;
					case 2:
						leftLabel = commands.get(1).getAndroidLabel();
						rightLabel = commands.get(0).getAndroidLabel();
						break;
					default:
						leftLabel = overlayView.getResources().getString(R.string.cmd_menu);
						rightLabel = commands.get(0).getAndroidLabel();
				}
			}
			overlayView.postInvalidate();
		}

		private boolean fireLeftSoft() {
			int size = commands.size();
			if (size == 0) {
				return false;
			}
			if (fullscreen) {
				if (listener != null) {
					showPopup();
					return true;
				}
				return false;
			}
			if (size > 2) {
				showPopup();
				return true;
			}
			if (listener != null) {
				fireCommandAction(commands.get(size > 1 ? 1 : 0));
			}
			return true;
		}

		private boolean fireRightSoft() {
			int size = commands.size();
			if (size == 0) {
				return false;
			}
			if (fullscreen) {
				if (size == 1) {
					return false;
				}
				if (listener != null) {
					showPopup();
					return true;
				}
				return false;
			}
			if (listener != null) {
				fireCommandAction(commands.get(0));
			}
			return true;
		}

		@Override
		public void paint(CanvasWrapper g) {
			if (bounds.isEmpty() || commands.size() == 0) {
				return;
			}
			g.setFillColor(bgColor);
			g.fillRect(bounds);

			if (leftLabel == null) {
				return;
			}
			g.setTextAlign(Paint.Align.LEFT);
			g.setTextScale(textScale);
			g.setTextColor(textColor);
			float y = bounds.centerY();
			g.drawString(leftLabel, bounds.left + padding * textScale, y);
			if (rightLabel != null) {
				g.setTextAlign(Paint.Align.RIGHT);
				g.drawString(rightLabel, bounds.right - padding * textScale, y);
			}

			g.setTextAlign(Paint.Align.CENTER);
			g.setTextScale(1.0f);
		}

		public void resize() {
			float left;
			float right;
			float bottom;
			VirtualKeyboard vk = ContextHolder.getVk();
			if (vk != null && vk.isPhone()) {
				float vkTop = displayHeight - vk.getPhoneKeyboardHeight(displayWidth, displayHeight) - 1;
				if (onWidth < displayWidth / 2.0f || onWidth > displayWidth) {
					textScale = 1.0f;
					left = 0;
					right = displayWidth;
					bottom = vkTop;
				} else {
					textScale = (float) onWidth / displayWidth;
					canvasWrapper.setTextScale(textScale);
					left = onX;
					right = onX + onWidth;
					bottom = onY + onHeight;
					if (bottom > vkTop) {
						bottom = vkTop;
					}
				}
			} else {
				float width = onWidth;
				float minSide = Math.min(displayWidth, displayHeight);
				if (width <= minSide) {
					textScale = width / minSide;
					canvasWrapper.setTextScale(textScale);
					left = onX;
				} else {
					left = (onX + onWidth) / 2.0f - minSide / 2.0f;
					if (left + minSide > displayWidth) {
						left = displayWidth / 2.0f - minSide / 2.0f;
					}
					width = minSide;
				}
				bottom = onY + onHeight;
				right = left + width;
				if (bottom > displayHeight) {
					bottom = displayHeight;
				}
			}
			float top = fullscreen ? bottom : bottom - canvasWrapper.getTextHeight();
			bounds.set(left, top, right, bottom);
			canvasWrapper.setTextScale(1.0f);
		}
	}
}
