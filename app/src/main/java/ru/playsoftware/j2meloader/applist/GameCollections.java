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

package ru.playsoftware.j2meloader.applist;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User collections of games (idea from JL-Mod Plus), kept apart from the games themselves:
 * a collection is a name and the set of installed game folders in it. Removing a game from
 * a collection never uninstalls it.
 */
public final class GameCollections {
	private static final String FILE = "game_collections";
	private static final String NAMES = "names";
	private static final String PREFIX = "c:";
	private static final String CURRENT = "current";
	/** built-in lists, not in names(): Favorites (a collection) and Recent (last launched first) */
	public static final String FAVORITES = "\u0001favorites";
	public static final String RECENT = "\u0001recent";
	private static final int RECENT_MAX = 20;

	private final SharedPreferences prefs;

	public GameCollections(Context context) {
		prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
	}

	/** Collection names, sorted. */
	public List<String> names() {
		List<String> list = new ArrayList<>(prefs.getStringSet(NAMES, Collections.emptySet()));
		Collections.sort(list, String.CASE_INSENSITIVE_ORDER);
		return list;
	}

	public void create(String name) {
		Set<String> names = new HashSet<>(prefs.getStringSet(NAMES, Collections.emptySet()));
		names.add(name);
		prefs.edit().putStringSet(NAMES, names).apply();
	}

	public void delete(String name) {
		Set<String> names = new HashSet<>(prefs.getStringSet(NAMES, Collections.emptySet()));
		names.remove(name);
		SharedPreferences.Editor e = prefs.edit().putStringSet(NAMES, names).remove(PREFIX + name);
		if (name.equals(getCurrent())) e.remove(CURRENT);
		e.apply();
	}

	public Set<String> games(String name) {
		return prefs.getStringSet(PREFIX + name, Collections.emptySet());
	}

	public boolean contains(String name, String gamePath) {
		return games(name).contains(gamePath);
	}

	public void setMember(String name, String gamePath, boolean member) {
		Set<String> games = new HashSet<>(games(name));
		if (member) games.add(gamePath); else games.remove(gamePath);
		prefs.edit().putStringSet(PREFIX + name, games).apply();
	}

	/** The collection the list is filtered by, or null for all games. */
	public String getCurrent() {
		return prefs.getString(CURRENT, null);
	}

	public void setCurrent(String name) {
		prefs.edit().putString(CURRENT, name).apply();
	}

	/** Remembers a launched game for Recent. */
	public void addRecent(String gamePath) {
		List<String> recent = recent();
		recent.remove(gamePath);
		recent.add(0, gamePath);
		while (recent.size() > RECENT_MAX) recent.remove(recent.size() - 1);
		prefs.edit().putString(RECENT, android.text.TextUtils.join("\n", recent)).apply();
	}

	/** Last games opened, newest first. */
	public List<String> recent() {
		String s = prefs.getString(RECENT, "");
		List<String> list = new ArrayList<>();
		for (String p : s.split("\n")) {
			if (!p.isEmpty()) list.add(p);
		}
		return list;
	}

	/** Only the games of the current collection (all of them if there is none). */
	public List<AppItem> filter(List<AppItem> items) {
		String current = getCurrent();
		if (current == null || items == null) return items;
		if (RECENT.equals(current)) {
			List<AppItem> out = new ArrayList<>();
			for (String path : recent()) {
				for (AppItem item : items) {
					if (item.getPath().equals(path)) out.add(item);
				}
			}
			return out;
		}
		Set<String> games = games(current);
		List<AppItem> out = new ArrayList<>();
		for (AppItem item : items) {
			if (games.contains(item.getPath())) out.add(item);
		}
		return out;
	}
}
