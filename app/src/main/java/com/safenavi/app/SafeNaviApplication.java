package com.safenavi.app;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import org.maplibre.android.MapLibre;

public class SafeNaviApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        com.safenavi.app.product.ServerConnection.initialize(this);
        com.safenavi.app.product.SessionRecovery.install(this);
        String language = getSharedPreferences("AppPreferences", MODE_PRIVATE)
                .getString("app_language", "en");
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language));
        MapLibre.getInstance(this);
    }
}
