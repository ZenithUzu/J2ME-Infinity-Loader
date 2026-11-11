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

package javax.microedition.lcdui;

import javax.microedition.shell.MicroActivity;
import javax.microedition.util.ContextHolder;

/**
 * Automatic image rotation (idea from JarNix). Games made to be played sideways on a portrait
 * phone draw (almost) every image rotated. Graphics reports the area of each image drawn on a
 * screen-sized target; once rotated images clearly dominate and add up to ~20 screens, the image
 * is turned back, the app goes landscape, and the choice is saved in the game's settings.
 */
final class AutoRotate {
	static volatile boolean tracking;
	private static int screenW, screenH;
	private static long upright, cw, ccw;
	private static long start;
	private static int frames;

	private AutoRotate() {
	}

	static void begin() {
		tracking = true;
		upright = cw = ccw = 0;
		start = 0;
		frames = 0;
	}

	static void setScreenSize(int w, int h) {
		screenW = w;
		screenH = h;
	}

	/** An image drawn on target: turn 0 upright, 90 = TRANS_ROT90 (clockwise), 270 = TRANS_ROT270. */
	static void imageDrawn(Image target, int turn, int area) {
		if (target == null || target.getWidth() != screenW || target.getHeight() != screenH) return;
		synchronized (AutoRotate.class) {
			if (turn == 90) cw += area;
			else if (turn == 270) ccw += area;
			else upright += area;
		}
	}

	/** Called once per frame shown; decides after 12 s and 300 frames, gives up after 2 min. */
	static void frameShown(Canvas canvas) {
		if (!tracking) return;
		long now = System.currentTimeMillis();
		if (start == 0) start = now;
		frames++;
		if (now - start > 120_000) {
			tracking = false;
			return;
		}
		if (frames < 300 || now - start < 12_000) return;
		long up, c, cc;
		synchronized (AutoRotate.class) {
			up = upright;
			c = cw;
			cc = ccw;
		}
		long screens = 20L * screenW * screenH;
		int mode; // as in the settings: 1 = 90° clockwise, 2 = 270°
		if (c > 2 * (up + cc) && c > screens) mode = 2;         // drawn clockwise -> turn back
		else if (cc > 2 * (up + c) && cc > screens) mode = 1;
		else if (up + c + cc < screens) return;                 // not enough evidence yet
		else mode = 0;
		tracking = false;
		if (mode == 0) return;
		Canvas.setScreenRotation(mode);
		MicroActivity activity = ContextHolder.getActivity();
		if (activity != null) {
			activity.runOnUiThread(() -> {
				activity.onAutoRotation(mode);
				canvas.updateSize();
			});
		}
	}
}
