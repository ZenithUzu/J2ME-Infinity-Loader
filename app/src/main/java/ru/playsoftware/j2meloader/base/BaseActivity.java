package ru.playsoftware.j2meloader.base;

import static ru.playsoftware.j2meloader.util.Constants.PREF_BLACK_BACKGROUND;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;

import ru.playsoftware.j2meloader.R;

public class BaseActivity extends AppCompatActivity {
	public static final String PREF_DYNAMIC_COLORS = "pref_dynamic_colors";

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		// Material You: the app takes its colors from the wallpaper (Android 12+)
		if (sp.getBoolean(PREF_DYNAMIC_COLORS, true)) {
			com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this);
		}
		if (sp.getBoolean(PREF_BLACK_BACKGROUND, false)) {
			getTheme().applyStyle(R.style.ThemeOverlay_App_Dark, true);
		}
		super.onCreate(savedInstanceState);
		// see-through bars over the app's own surface color, light or dark icons as needed
		EdgeToEdge.enable(this,
				SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
				SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT));
		View view = findViewById(android.R.id.content);
		ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
			Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
			v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
			return WindowInsetsCompat.CONSUMED;
		});
	}
}
