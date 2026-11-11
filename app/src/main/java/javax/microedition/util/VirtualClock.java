/*
 * Copyright 2026 fuyijian (J2ME-Loader-HD), ZenithUzu
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

package javax.microedition.util;

/**
 * Game time that can run 2x/3x/4x faster than real time.
 * <p>
 * When a game is installed, dexlib rewrites its calls to System.currentTimeMillis(),
 * System.nanoTime() and Thread.sleep() to this class, so the game's own timing speeds up.
 * Games installed before that rewrite existed must be reinstalled for the speed to apply.
 */
public final class VirtualClock {
	private static volatile double speed = 1.0;
	/** virtual time baseVirt matched real time baseReal at the last speed change */
	private static long baseVirt;
	private static long baseReal;
	/** last value returned, so game time never goes backwards */
	private static volatile long lastIssued;

	static {
		baseReal = System.currentTimeMillis();
		baseVirt = baseReal;
		lastIssued = baseVirt;
	}

	private VirtualClock() {
	}

	public static double getSpeed() {
		return speed;
	}

	public static void setSpeed(double value) {
		if (value <= 0) {
			value = 1.0;
		}
		synchronized (VirtualClock.class) {
			long now = System.currentTimeMillis();
			baseVirt = baseVirt + (long) ((now - baseReal) * speed);
			baseReal = now;
			speed = value;
		}
	}

	/**
	 * Replaces System.currentTimeMillis(). Always advances from the accumulated base,
	 * even at 1x: after running faster, game time is ahead of real time, and jumping
	 * back would make the game wait a long time for its next frame.
	 */
	public static long currentTimeMillis() {
		long virt;
		synchronized (VirtualClock.class) {
			virt = baseVirt + (long) ((System.currentTimeMillis() - baseReal) * speed);
		}
		long last = lastIssued;
		if (virt < last) {
			virt = last;
		}
		lastIssued = virt;
		return virt;
	}

	/** Replaces System.nanoTime() (millisecond precision is enough for games). */
	public static long nanoTime() {
		return currentTimeMillis() * 1_000_000L;
	}

	/** Converts a game delay into the real time to wait. */
	public static long scaleDelay(long millis) {
		if (millis <= 0) {
			return 0;
		}
		double s = speed;
		if (s == 1.0) {
			return millis;
		}
		long real = (long) (millis / s);
		return real < 1 ? 1 : real;
	}

	/** Replaces Thread.sleep(long). */
	public static void sleep(long millis) throws InterruptedException {
		if (millis > 0) {
			Thread.sleep(scaleDelay(millis));
		}
	}

	/** Replaces Thread.sleep(long, int). */
	public static void sleep(long millis, int nanos) throws InterruptedException {
		sleep(millis);
	}
}
