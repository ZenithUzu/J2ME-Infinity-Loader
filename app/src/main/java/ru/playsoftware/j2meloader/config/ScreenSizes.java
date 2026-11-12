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

package ru.playsoftware.j2meloader.config;

/** Screen sizes of the phones J2ME games were made for, with phones that used them. */
public final class ScreenSizes {
	public static final int[][] SIZES = {
			{96, 65}, {101, 80}, {128, 128}, {128, 160}, {132, 176}, {176, 208}, {176, 220},
			{240, 320}, {320, 240}, {240, 400}, {240, 432}, {352, 416}, {416, 352},
			{320, 480}, {360, 640}, {640, 360}, {480, 800}, {800, 480}};
	public static final String[] PHONES = {
			"Nokia 3410, 3510i", "Siemens C55", "Nokia S40 (6230, 6610)",
			"Sony Ericsson K300, Samsung, Motorola", "Siemens C65, S65", "Nokia S60 (6600, 6680, N-Gage)",
			"Sony Ericsson K750, Samsung, Motorola", "Nokia S40/S60, Sony Ericsson K800, Samsung",
			"Nokia E61, E71", "Samsung Star, Nokia Asha 305/311", "Sony Ericsson Aino",
			"Nokia E70", "Nokia E70", "Samsung, LG (HVGA)",
			"Nokia 5800, N97, C6, Sony Ericsson Satio", "Nokia N97, E7, C7",
			"Samsung Wave, Jet", "Samsung Wave, Jet"};

	private ScreenSizes() {
	}

	public static String name(int i) {
		return SIZES[i][0] + "×" + SIZES[i][1];
	}
}
