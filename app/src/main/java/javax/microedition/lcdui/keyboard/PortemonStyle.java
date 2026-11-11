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

package javax.microedition.lcdui.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;

/**
 * On-screen buttons drawn like Portemon's touch controls (no pictures needed):
 * <ul>
 * <li>Modern: flat see-through slate fill, a faint border, white label.</li>
 * <li>Outline: only a light border and the label.</li>
 * </ul>
 * Pressed keys take the system accent color (Material You on Android 12+).
 */
final class PortemonStyle {
	static final int MODERN = 0;
	static final int OUTLINE = 1;

	static final int ROUND = 0;  // rounded square
	static final int CIRCLE = 1; // A B X Y
	static final int PILL = 2;   // L R Select Start, wide keys

	static final String ARROW_UP = "▲";
	static final String ARROW_DOWN = "▼";
	static final String ARROW_LEFT = "◀";
	static final String ARROW_RIGHT = "▶";

	private final int style;
	private final float dp;
	private final int slate, accent, ink;
	private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final RectF r = new RectF();
	private final Path path = new Path();

	PortemonStyle(Context context, int style) {
		this.style = style;
		dp = context.getResources().getDisplayMetrics().density;
		if (Build.VERSION.SDK_INT >= 31) {
			slate = context.getColor(android.R.color.system_neutral2_800) & 0xFFFFFF;
			accent = context.getColor(android.R.color.system_accent1_200) & 0xFFFFFF;
			ink = context.getColor(android.R.color.system_neutral1_900) & 0xFFFFFF;
		} else {
			slate = 0x2A2D3A;
			accent = 0xB9C3FF;
			ink = 0x15161C;
		}
		stroke.setStyle(Paint.Style.STROKE);
		text.setTextAlign(Paint.Align.CENTER);
		text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
	}

	/** alpha: 0..255, the keyboard opacity over the game (255 elsewhere). */
	private void paints(boolean pressed, int alpha) {
		float a = alpha / 255f;
		stroke.setStrokeWidth(style == MODERN ? 1.5f * dp : 2 * dp);
		if (style == MODERN) {
			fill.setColor(argb(pressed ? 0xE6 : 0xC0, a, pressed ? accent : slate));
			stroke.setColor(argb(pressed ? 0xFF : 0x30, a, pressed ? accent : 0xFFFFFF));
		} else {
			fill.setColor(argb(pressed ? 0xB3 : 0x50, a, pressed ? accent : 0x101010));
			stroke.setColor(argb(0xF0, a, 0xFFFFFF));
		}
		text.setColor(argb(0xFF, a, pressed ? ink : 0xFFFFFF));
		text.setShadowLayer(pressed ? 0 : (style == MODERN ? 1.5f : 2.5f) * dp, 0, 0,
				argb(style == MODERN ? 0x66 : 0xAA, a, 0));
	}

	private static int argb(int alpha, float factor, int rgb) {
		return Math.round(alpha * factor) << 24 | rgb & 0xFFFFFF;
	}

	void key(Canvas c, RectF rect, String label, boolean pressed, int alpha, int shape) {
		paints(pressed, alpha);
		float sw = stroke.getStrokeWidth();
		r.set(rect);
		r.inset(sw / 2 + dp, sw / 2 + dp);
		if (shape == CIRCLE) {
			float d = Math.min(r.width(), r.height()) / 2;
			c.drawCircle(r.centerX(), r.centerY(), d, fill);
			c.drawCircle(r.centerX(), r.centerY(), d, stroke);
		} else {
			float rad = shape == PILL ? r.height() / 2 : Math.min(r.width(), r.height()) * 0.3f;
			c.drawRoundRect(r, rad, rad, fill);
			c.drawRoundRect(r, rad, rad, stroke);
		}
		if (label == null || label.isEmpty()) return;
		float s = Math.min(r.width(), r.height()) * 0.17f;
		switch (label) {
			case ARROW_UP: arrow(c, r.centerX(), r.centerY(), 0, -s); return;
			case ARROW_DOWN: arrow(c, r.centerX(), r.centerY(), 0, s); return;
			case ARROW_LEFT: arrow(c, r.centerX(), r.centerY(), -s, 0); return;
			case ARROW_RIGHT: arrow(c, r.centerX(), r.centerY(), s, 0); return;
		}
		text.setTextSize(Math.min(r.height() * 0.42f, r.width() * 0.8f / Math.max(1, label.length() * 0.6f)));
		c.drawText(label, r.centerX(), r.centerY() - (text.descent() + text.ascent()) / 2, text);
	}

	/**
	 * The gamepad cross: four rounded squares with arrows, lit in the pressed directions.
	 * dirs bits: 1 up, 2 down, 4 left, 8 right.
	 */
	void dpad(Canvas c, RectF area, int dirs, int alpha) {
		float size = Math.min(area.width(), area.height());
		float t = size / 3, gap = 2.5f * dp, rad = t * 0.28f;
		float x0 = area.centerX() - size / 2, y0 = area.centerY() - size / 2;
		float[][] cells = {{t, 0}, {t, 2 * t}, {0, t}, {2 * t, t}};
		float s = t * 0.16f;
		float[][] arrows = {{0, -s}, {0, s}, {-s, 0}, {s, 0}};
		for (int i = 0; i < 4; i++) {
			boolean on = (dirs & (1 << i)) != 0;
			paints(on, alpha);
			r.set(x0 + cells[i][0] + gap, y0 + cells[i][1] + gap, x0 + cells[i][0] + t - gap, y0 + cells[i][1] + t - gap);
			c.drawRoundRect(r, rad, rad, fill);
			c.drawRoundRect(r, rad, rad, stroke);
			arrow(c, r.centerX(), r.centerY(), arrows[i][0], arrows[i][1]);
		}
		// the middle square joins the arms, without arrow
		paints(false, alpha);
		r.set(x0 + t - gap, y0 + t - gap, x0 + 2 * t + gap, y0 + 2 * t + gap);
		c.drawRect(r, fill);
	}

	/** The analog stick: a plate and a knob that follows the finger. */
	void stick(Canvas c, RectF rect, float knobX, float knobY, boolean held, int alpha) {
		paints(false, alpha);
		float rad = Math.min(rect.width(), rect.height()) / 2 - stroke.getStrokeWidth();
		c.drawCircle(rect.centerX(), rect.centerY(), rad, fill);
		c.drawCircle(rect.centerX(), rect.centerY(), rad, stroke);
		float kr = rad * 0.42f;
		float cx = held ? knobX : rect.centerX(), cy = held ? knobY : rect.centerY();
		float a = alpha / 255f;
		fill.setColor(argb(held ? 0xF0 : 0xD0, a, held ? accent : style == MODERN ? 0xFFFFFF : 0x101010));
		c.drawCircle(cx, cy, kr, fill);
		if (style == OUTLINE) c.drawCircle(cx, cy, kr, stroke);
	}

	private void arrow(Canvas c, float x, float y, float dx, float dy) {
		path.reset();
		path.moveTo(x + dx, y + dy);
		path.lineTo(x - dx - dy, y - dy + dx);
		path.lineTo(x - dx + dy, y - dy - dx);
		path.close();
		int old = fill.getColor();
		fill.setColor(text.getColor());
		fill.setShadowLayer(0, 0, 0, 0);
		c.drawPath(path, fill);
		fill.setColor(old);
	}
}
