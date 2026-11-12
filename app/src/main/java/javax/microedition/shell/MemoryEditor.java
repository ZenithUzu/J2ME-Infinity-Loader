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

package javax.microedition.shell;

import android.app.Activity;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import javax.microedition.midlet.MIDlet;

import ru.playsoftware.j2meloader.R;

/**
 * A simple memory editor (idea from JL-Mod Plus, done without changing the game's code):
 * walks the running game's objects from the MIDlet (fields, static fields of its classes and
 * arrays), finds whole numbers equal to a value or that changed in a direction, and lets you
 * edit or freeze them. Results are candidates: check in the game which one is the right one.
 */
final class MemoryEditor {
	private static final int MAX_OBJECTS = 200_000;
	private static final int MAX_SLOTS = 300_000;
	private static final int MAX_ARRAY = 65_536;
	private static final int SHOWN = 100;

	/** One number in the game: a field (static if owner is null) or an array element. */
	private static final class Slot {
		final Object owner;
		final Field field;
		final int index;
		long last;
		boolean frozen;
		long frozenValue;

		Slot(Object owner, Field field, int index) {
			this.owner = owner;
			this.field = field;
			this.index = index;
		}

		long read() throws IllegalAccessException {
			return field != null ? field.getLong(owner) : Array.getLong(owner, index);
		}

		void write(long v) throws IllegalAccessException {
			Class<?> t = field != null ? field.getType() : owner.getClass().getComponentType();
			Object box = t == int.class ? (Object) (int) v : t == long.class ? (Object) v
					: t == short.class ? (Object) (short) v : (Object) (byte) v;
			if (field != null) field.set(owner, box); else Array.set(owner, index, box);
		}

		String name() {
			if (field != null) {
				return field.getDeclaringClass().getSimpleName() + "." + field.getName();
			}
			return owner.getClass().getComponentType().getName() + "[" + index + "]";
		}
	}

	private static List<Slot> results;
	private static final List<Slot> frozen = new ArrayList<>();
	private static Thread freezer;

	private MemoryEditor() {
	}

	private static boolean isNumber(Class<?> t) {
		return t == int.class || t == long.class || t == short.class || t == byte.class;
	}

	/** Every whole number reachable from the MIDlet, inside the game's own classes. */
	private static List<Slot> collect(MIDlet midlet) {
		ClassLoader game = midlet.getClass().getClassLoader();
		List<Slot> slots = new ArrayList<>();
		IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
		IdentityHashMap<Class<?>, Boolean> statics = new IdentityHashMap<>();
		ArrayDeque<Object> queue = new ArrayDeque<>();
		queue.add(midlet);
		seen.put(midlet, Boolean.TRUE);
		while (!queue.isEmpty() && seen.size() < MAX_OBJECTS && slots.size() < MAX_SLOTS) {
			Object o = queue.poll();
			Class<?> c = o.getClass();
			if (c.isArray()) {
				Class<?> t = c.getComponentType();
				int len = Array.getLength(o);
				if (isNumber(t)) {
					if (len <= MAX_ARRAY) for (int i = 0; i < len; i++) slots.add(new Slot(o, null, i));
				} else if (!t.isPrimitive()) {
					for (int i = 0; i < len; i++) enqueue(Array.get(o, i), game, seen, queue);
				}
				continue;
			}
			for (Class<?> k = c; k != null && k.getClassLoader() == game; k = k.getSuperclass()) {
				boolean doStatics = statics.put(k, Boolean.TRUE) == null;
				for (Field f : k.getDeclaredFields()) {
					boolean isStatic = Modifier.isStatic(f.getModifiers());
					if (isStatic && !doStatics) continue;
					try {
						f.setAccessible(true);
						Object owner = isStatic ? null : o;
						if (isNumber(f.getType())) {
							if (!Modifier.isFinal(f.getModifiers())) slots.add(new Slot(owner, f, -1));
						} else if (!f.getType().isPrimitive()) {
							enqueue(f.get(owner), game, seen, queue);
						}
					} catch (Throwable ignored) {
					}
				}
			}
		}
		return slots;
	}

	private static void enqueue(Object v, ClassLoader game, IdentityHashMap<Object, Boolean> seen,
								ArrayDeque<Object> queue) {
		if (v == null || seen.containsKey(v)) return;
		Class<?> c = v.getClass();
		if (c.isArray() || c.getClassLoader() == game) {
			seen.put(v, Boolean.TRUE);
			queue.add(v);
		}
	}

	private interface Test {
		boolean keep(long now, long before);
	}

	private interface Search {
		void run(Test test);
	}

	/** Compare tests need a "before": the first time they only take a snapshot of everything. */
	private static final Test SNAPSHOT = (now, before) -> true;

	/** First search over everything, or a refinement of the previous results. */
	private static void search(MIDlet midlet, Test test) {
		List<Slot> from = results != null ? results : collect(midlet);
		List<Slot> out = new ArrayList<>();
		for (Slot s : from) {
			try {
				long v = s.read();
				if (test.keep(v, s.last)) out.add(s);
				s.last = v;
			} catch (Throwable ignored) {
			}
		}
		results = out;
	}

	private static synchronized void startFreezer() {
		if (freezer != null) return;
		freezer = new Thread(() -> {
			while (true) {
				synchronized (frozen) {
					for (Slot s : frozen) {
						try {
							s.write(s.frozenValue);
						} catch (Throwable ignored) {
						}
					}
				}
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					return;
				}
			}
		}, "MemoryFreezer");
		freezer.setDaemon(true);
		freezer.start();
	}

	/** What the list shows: the frozen numbers first, then the results (if few enough). */
	private static List<Slot> shown() {
		List<Slot> out;
		synchronized (frozen) {
			out = new ArrayList<>(frozen);
		}
		if (results != null && results.size() <= SHOWN) {
			for (Slot s : results) if (!s.frozen) out.add(s);
		}
		return out;
	}

	static void show(Activity activity) {
		MIDlet midlet = MidletThread.getMidlet();
		if (midlet == null) return;
		android.view.View root = activity.getLayoutInflater().inflate(R.layout.memory_editor, null);
		EditText value = root.findViewById(R.id.memory_value);
		TextView status = root.findViewById(R.id.memory_status);
		ListView list = root.findViewById(R.id.memory_list);
		com.google.android.material.chip.ChipGroup compareRow = root.findViewById(R.id.memory_compare);
		List<Slot> rows = new ArrayList<>();
		ArrayAdapter<Slot> adapter = new ArrayAdapter<Slot>(activity, R.layout.item_memory_slot, rows) {
			@Override
			public android.view.View getView(int pos, android.view.View v, ViewGroup parent) {
				if (v == null) v = activity.getLayoutInflater().inflate(R.layout.item_memory_slot, parent, false);
				Slot s = rows.get(pos);
				((TextView) v.findViewById(R.id.slot_name)).setText(s.name());
				String n;
				try {
					n = String.valueOf(s.read());
				} catch (Throwable e) {
					n = "?";
				}
				((TextView) v.findViewById(R.id.slot_value)).setText(n);
				v.findViewById(R.id.slot_frozen).setVisibility(s.frozen ? android.view.View.VISIBLE : android.view.View.GONE);
				return v;
			}
		};
		list.setAdapter(adapter);

		Runnable refresh = () -> {
			rows.clear();
			rows.addAll(shown());
			adapter.notifyDataSetChanged();
			if (results == null) {
				status.setText(R.string.memory_hint);
			} else {
				status.setText(activity.getString(R.string.memory_count, results.size()));
			}
		};
		// runs a search away from the screen thread, then shows the results
		Search run = test -> {
			status.setText(R.string.memory_searching);
			boolean first = results == null && test == SNAPSHOT;
			new Thread(() -> {
				search(midlet, test);
				activity.runOnUiThread(() -> {
					refresh.run();
					// unknown value: everything was remembered, now compare as the game changes it
					if (first) status.setText(R.string.memory_snapshot);
				});
			}, "MemorySearch").start();
		};
		// changed / unchanged / up / down: the first time, remember every value
		Search compare = test -> run.run(results == null ? SNAPSHOT : test);
		Runnable find = () -> {
			long n;
			try {
				n = Long.parseLong(value.getText().toString().trim());
			} catch (NumberFormatException e) {
				return;
			}
			run.run((now, before) -> now == n);
		};
		root.findViewById(R.id.memory_find).setOnClickListener(v -> find.run());
		value.setOnEditorActionListener((v, id, e) -> {
			find.run();
			return true;
		});
		addChip(activity, compareRow, R.string.memory_changed, v -> compare.run((now, before) -> now != before));
		addChip(activity, compareRow, R.string.memory_unchanged, v -> compare.run((now, before) -> now == before));
		addChip(activity, compareRow, R.string.memory_increased, v -> compare.run((now, before) -> now > before));
		addChip(activity, compareRow, R.string.memory_decreased, v -> compare.run((now, before) -> now < before));
		addChip(activity, compareRow, R.string.memory_new, v -> {
			results = null;
			refresh.run();
		});
		list.setOnItemClickListener((p, v, pos, id) -> editSlot(activity, rows.get(pos), refresh));
		refresh.run();
		// the values in the list follow the game live while the editor is open
		android.os.Handler ticker = new android.os.Handler(android.os.Looper.getMainLooper());
		Runnable tick = new Runnable() {
			@Override
			public void run() {
				adapter.notifyDataSetChanged();
				ticker.postDelayed(this, 500);
			}
		};
		ticker.postDelayed(tick, 500);
		new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
				.setTitle(R.string.memory_editor)
				.setView(root)
				.setPositiveButton(R.string.quick_done, null)
				.setOnDismissListener(d -> ticker.removeCallbacks(tick))
				.show();
	}

	private static void addChip(Activity a, com.google.android.material.chip.ChipGroup row, int text,
								android.view.View.OnClickListener l) {
		com.google.android.material.chip.Chip c = new com.google.android.material.chip.Chip(a);
		c.setText(text);
		c.setOnClickListener(l);
		row.addView(c);
	}

	private static void editSlot(Activity activity, Slot s, Runnable refresh) {
		EditText edit = new EditText(activity);
		edit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
		try {
			edit.setText(String.valueOf(s.read()));
		} catch (Throwable ignored) {
		}
		new AlertDialog.Builder(activity)
				.setTitle(s.name())
				.setView(edit)
				.setPositiveButton(R.string.memory_set, (d, w) -> {
					try {
						long v = Long.parseLong(edit.getText().toString().trim());
						s.write(v);
						s.frozenValue = v;
					} catch (Throwable ignored) {
					}
					refresh.run();
				})
				.setNeutralButton(s.frozen ? R.string.memory_unfreeze : R.string.memory_freeze, (d, w) -> {
					synchronized (frozen) {
						if (s.frozen) {
							frozen.remove(s);
							s.frozen = false;
						} else {
							try {
								s.frozenValue = Long.parseLong(edit.getText().toString().trim());
							} catch (NumberFormatException e) {
								return;
							}
							s.frozen = true;
							frozen.add(s);
						}
					}
					startFreezer();
					refresh.run();
				})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}
}
