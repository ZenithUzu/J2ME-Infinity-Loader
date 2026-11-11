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
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.databinding.HomeHeaderBinding;
import ru.playsoftware.j2meloader.databinding.ItemHomeCoverBinding;

/**
 * Top of the library (console style home): "Continue playing" with the last game, then the
 * Recent and Favorites rows, then the "All games" title. Shown only on the whole library,
 * without a search; the games grid comes right after it (ConcatAdapter).
 */
class HomeHeaderAdapter extends RecyclerView.Adapter<HomeHeaderAdapter.Holder> {
	private final View.OnCreateContextMenuListener contextMenuListener;
	private final List<AppItem> recent = new ArrayList<>();
	private final List<AppItem> favorites = new ArrayList<>();
	private int total;
	/** first game of the library, for the big card before anything was played */
	private AppItem first;
	private boolean shown;

	HomeHeaderAdapter(View.OnCreateContextMenuListener contextMenuListener) {
		this.contextMenuListener = contextMenuListener;
	}

	/** Fills the rows from the installed games and the collections. */
	void update(List<AppItem> all, GameCollections collections, boolean show) {
		Map<String, AppItem> byPath = new HashMap<>();
		for (AppItem item : all) byPath.put(item.getPath(), item);
		recent.clear();
		for (String path : collections.recent()) {
			AppItem item = byPath.get(path);
			if (item != null) recent.add(item);
		}
		favorites.clear();
		Set<String> fav = collections.games(GameCollections.FAVORITES);
		for (AppItem item : all) {
			if (fav.contains(item.getPath())) favorites.add(item);
		}
		total = all.size();
		first = all.isEmpty() ? null : all.get(0);
		boolean was = shown;
		shown = show && total > 0;
		if (was && shown) notifyItemChanged(0);
		else if (was) notifyItemRemoved(0);
		else if (shown) notifyItemInserted(0);
	}

	@Override
	public int getItemCount() {
		return shown ? 1 : 0;
	}

	@Override
	public int getItemViewType(int position) {
		return R.layout.home_header; // never the same as the grid's types
	}

	@NonNull
	@Override
	public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		return new Holder(HomeHeaderBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
	}

	@Override
	public void onBindViewHolder(@NonNull Holder h, int position) {
		Context c = h.itemView.getContext();
		AppItem last = recent.isEmpty() ? first : recent.get(0);
		h.b.heroLabel.setText(recent.isEmpty() ? R.string.home_start : R.string.home_continue);
		h.b.hero.setVisibility(last == null ? View.GONE : View.VISIBLE);
		if (last != null) {
			h.b.heroTitle.setText(last.getTitle());
			setIcon(h.b.heroIcon, last);
			View.OnClickListener play = v -> start(v.getContext(), last);
			h.b.heroPlay.setOnClickListener(play);
			h.b.hero.setOnClickListener(play);
		}
		boolean hasRecent = recent.size() > 1; // the first one is already in the card
		h.b.recentTitle.setVisibility(hasRecent ? View.VISIBLE : View.GONE);
		h.b.recentRow.setVisibility(hasRecent ? View.VISIBLE : View.GONE);
		h.b.recentRow.setAdapter(new RowAdapter(hasRecent ? recent.subList(1, recent.size()) : new ArrayList<>()));
		boolean hasFav = !favorites.isEmpty();
		h.b.favoritesTitle.setVisibility(hasFav ? View.VISIBLE : View.GONE);
		h.b.favoritesRow.setVisibility(hasFav ? View.VISIBLE : View.GONE);
		h.b.favoritesRow.setAdapter(new RowAdapter(favorites));
		h.b.allTitle.setText(c.getString(R.string.home_all_games, total));
	}

	static void setIcon(ImageView view, AppItem item) {
		Drawable icon = Drawable.createFromPath(item.getImagePathExt());
		if (icon != null) {
			icon.setFilterBitmap(false); // pixel art stays sharp when enlarged
			view.setImageDrawable(icon);
		} else {
			view.setImageResource(R.mipmap.ic_launcher);
		}
	}

	static void start(Context context, AppItem item) {
		new GameCollections(context).addRecent(item.getPath());
		Config.startApp(context, item.getTitle(), item.getPathExt(), false);
	}

	static class Holder extends RecyclerView.ViewHolder {
		final HomeHeaderBinding b;

		Holder(HomeHeaderBinding b) {
			super(b.getRoot());
			this.b = b;
		}
	}

	/** One horizontal row of game tiles. */
	private class RowAdapter extends RecyclerView.Adapter<RowAdapter.Tile> {
		private final List<AppItem> items;

		RowAdapter(List<AppItem> items) {
			this.items = new ArrayList<>(items);
		}

		@Override
		public int getItemCount() {
			return items.size();
		}

		@NonNull
		@Override
		public Tile onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			return new Tile(ItemHomeCoverBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
		}

		@Override
		public void onBindViewHolder(@NonNull Tile t, int position) {
			AppItem item = items.get(position);
			t.b.coverTitle.setText(item.getTitle());
			setIcon(t.b.coverIcon, item);
			t.itemView.setTag(item); // for the long-press menu (AppItemLayout)
			t.itemView.setOnClickListener(v -> start(v.getContext(), item));
			t.itemView.setOnCreateContextMenuListener(contextMenuListener);
		}

		class Tile extends RecyclerView.ViewHolder {
			final ItemHomeCoverBinding b;

			Tile(ItemHomeCoverBinding b) {
				super(b.getRoot());
				this.b = b;
			}
		}
	}
}
