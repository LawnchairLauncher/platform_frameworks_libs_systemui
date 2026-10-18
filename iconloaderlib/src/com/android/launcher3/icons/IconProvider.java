/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.icons;

import static android.content.Intent.ACTION_DATE_CHANGED;
import static android.content.Intent.ACTION_TIMEZONE_CHANGED;
import static android.content.Intent.ACTION_TIME_CHANGED;
import static android.content.res.Resources.ID_NULL;
import static android.graphics.drawable.AdaptiveIconDrawable.getExtraInsetFraction;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.BuildCompat;

import com.android.launcher3.icons.cache.CachingLogic;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.SafeCloseable;

import java.io.File;
import java.util.Calendar;
import java.util.Objects;

import app.lawnchair.icons.CustomAdaptiveIconDrawable;
import app.lawnchair.icons.IconPreferencesKt;

/**
 * Class to handle icon loading from different packages
 */
public class IconProvider {

    private static final String TAG = "IconProvider";
    private static final boolean DEBUG = false;
    public static final boolean ATLEAST_T = BuildCompat.isAtLeastT();
    public static final boolean ATLEAST_OREO = BuildCompat.isAtLeastO();

    private static final String ICON_METADATA_KEY_PREFIX = ".dynamic_icons";

    private static final String SYSTEM_STATE_SEPARATOR = " ";

    // Lawnchair: One UI applies Galaxy Themes / Theme Park icon themes inside
    // PackageManager#loadUnbadgedItemIcon, never through the app's own resources.
    private static final boolean IS_SAMSUNG = "samsung".equalsIgnoreCase(Build.MANUFACTURER);
    private static final String SAMSUNG_ICON_THEME_SETTING = "current_sec_appicon_theme_package";
    private static final String SAMSUNG_THEME_PARK_ICON_DIR = "/data/overlays/themepark/icons/";

    protected final Context mContext;
    protected final ComponentName mCalendar;
    protected final ComponentName mClock;

    @NonNull
    protected String mSystemState = "";

    /** Id of the active Samsung icon theme, or null when icons come from app resources. */
    @Nullable
    private volatile String mSamsungIconTheme;
    private volatile boolean mSamsungIconThemeLoaded;
    private boolean mLoggedIconThemeSettingDenied;

    public IconProvider(Context context) {
        mContext = context;
        mCalendar = parseComponentOrNull(context, R.string.calendar_component_name);
        mClock = parseComponentOrNull(context, R.string.clock_component_name);
    }

    /**
     * Returns a string representing the current state of the app icon. It can be used as a
     * identifier to invalidate any resources loaded from the app.
     * It also incorporated ay system state, that can affect the loaded resource
     *
     * @see #updateSystemState()
     */
    public String getStateForApp(@Nullable ApplicationInfo appInfo) {
        if (appInfo == null) {
            return mSystemState;
        }

        if (mCalendar != null && mCalendar.getPackageName().equals(appInfo.packageName)) {
            return mSystemState + SYSTEM_STATE_SEPARATOR + getDay() + SYSTEM_STATE_SEPARATOR
                    + getApplicationInfoHash(appInfo);
        } else {
            return mSystemState + SYSTEM_STATE_SEPARATOR + getApplicationInfoHash(appInfo);
        }
    }

    /**
     * Returns a hash to uniquely identify a particular version of appInfo
     */
    protected String getApplicationInfoHash(@NonNull ApplicationInfo appInfo) {
        // The hashString in source dir changes with every install
        return appInfo.sourceDir;
    }

    /**
     * Loads the icon for the provided activity info
     */
    public Drawable getIcon(ComponentInfo info) {
        return getIcon(info, mContext.getResources().getConfiguration().densityDpi);
    }

    /**
     * Loads the icon for the provided component info
     */
    public Drawable getIcon(ComponentInfo info, int iconDpi) {
        return getIcon(info, info.applicationInfo, iconDpi);
    }

    /**
     * Loads the icon for the provided application info
     */
    public Drawable getIcon(ApplicationInfo info) {
        return getIcon(info, mContext.getResources().getConfiguration().densityDpi);
    }

    /**
     * Loads the icon for the provided application info
     */
    public Drawable getIcon(ApplicationInfo info, int iconDpi) {
        return getIcon(info, info, iconDpi);
    }

    public Drawable getIcon(PackageItemInfo info, ApplicationInfo appInfo, int iconDpi) {
        String packageName = info.packageName;
        ThemeData td = getThemeDataForPackage(packageName);

        Drawable icon = null;
        // Lawnchair: a Samsung icon theme replaces the calendar's dynamic date icon, as in One UI
        if (mCalendar != null && mCalendar.getPackageName().equals(packageName)
                && !isSamsungIconThemeActive()) {
            icon = loadCalendarDrawable(iconDpi, td);
        } else if (mClock != null && mClock.getPackageName().equals(packageName)) {
            icon = ClockDrawableWrapper.forPackage(mContext, mClock.getPackageName(), iconDpi);
        }
        if (icon == null) {
            icon = loadPackageIconWithFallback(info, appInfo, iconDpi);
            if (ATLEAST_T && icon instanceof AdaptiveIconDrawable && td != null) {
                CustomAdaptiveIconDrawable aid = (CustomAdaptiveIconDrawable) CustomAdaptiveIconDrawable.wrapNonNull(
                    icon
                );
                if  (aid.getMonochrome() == null) {
                    icon = new CustomAdaptiveIconDrawable(aid.getBackground(),
                            aid.getForeground(), td.loadPaddedDrawable());
                }
            }
        }
        return icon;
    }

    protected ThemeData getThemeDataForPackage(String packageName) {
        return null;
    }

    protected Drawable loadPackageIconWithFallback(
            PackageItemInfo info, ApplicationInfo appInfo, int density) {
        Drawable icon = null;
        if (BuildCompat.isAtLeastV() && info.isArchived) {
            // Icons for archived apps com from system service, let the default impl handle that
            icon = info.loadIcon(mContext.getPackageManager());
        }
        if (icon == null && density != 0 && (info.icon != 0 || appInfo.icon != 0)) {
            icon = loadPackageIcon(info, appInfo, density);
        }
        return icon != null ? icon : getFullResDefaultActivityIcon(density);
    }

    @Nullable
    protected Drawable loadPackageIcon(
            @NonNull PackageItemInfo info, @NonNull ApplicationInfo appInfo, int density) {
        if (isSamsungIconThemeActive()) {
            Drawable themed = loadSamsungThemedIcon(info);
            if (themed != null) return themed;
        }
        try {
            final Resources resources = mContext.getPackageManager()
                    .getResourcesForApplication(appInfo);
            // Try to load the package item icon first
            if (info != appInfo && info.icon != 0) {
                try {
                    Drawable icon = resources.getDrawableForDensity(info.icon, density);
                    if (icon != null) return icon;
                } catch (Resources.NotFoundException exc) { }
            }
            if (appInfo.icon != 0) {
                // Load the fallback app icon
                try {
                    return resources.getDrawableForDensity(appInfo.icon, density);
                } catch (Resources.NotFoundException exc) { }
            }
        } catch (NameNotFoundException | Resources.NotFoundException exc) { }
        return null;
    }

    /**
     * Lawnchair: Loads the icon through PackageManager so that the Samsung icon theme is applied.
     * This is the call One UI's LauncherActivityInfo#getIcon(int) makes while a theme is active,
     * which is what Lawnchair 15 relied on. The item's own ApplicationInfo keeps work profile apps
     * on their own user, and the unbadged variant leaves the user badge to BaseIconFactory.
     * The theme engine ignores the requested density and renders at the display density.
     */
    @Nullable
    private Drawable loadSamsungThemedIcon(@NonNull PackageItemInfo info) {
        final PackageManager pm = mContext.getPackageManager();
        try {
            Drawable icon = info.loadUnbadgedIcon(pm);
            if (icon == null || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    && pm.isDefaultApplicationIcon(icon))) {
                return null;
            }
            return icon;
        } catch (RuntimeException e) {
            // Vendor theme engine code: one bad icon must not crash the model loader, and the
            // resource path is always a safe fallback
            Log.w(TAG, "Samsung themed icon failed for " + info.packageName, e);
            return null;
        }
    }

    /**
     * Lawnchair: Whether icons should come from the active Samsung icon theme. Lawnchair themed
     * icons need the app's own adaptive icon, so they win.
     */
    private boolean isSamsungIconThemeActive() {
        return IS_SAMSUNG && getSamsungIconTheme() != null
                && !IconPreferencesKt.isThemedIconsEnabled(mContext);
    }

    /**
     * Lawnchair: Returns the Samsung icon theme, reading it on first use for providers that never
     * call {@link #updateSystemState()} (Recents, split screen, PiP).
     */
    @Nullable
    private String getSamsungIconTheme() {
        if (!mSamsungIconThemeLoaded) {
            mSamsungIconTheme = readSamsungIconTheme();
            mSamsungIconThemeLoaded = true;
        }
        return mSamsungIconTheme;
    }

    /**
     * Lawnchair: Returns an id for the icon theme One UI applies to app icons, or null if there is
     * none. Mirrors the theme engine's own check: an icon theme package in Settings.System or a
     * Theme Park icon folder, where a theme package in desk mode (e.g. DeX) turns both off.
     * The id changes with the theme package version and the night mode, since One UI themed icons
     * have a night variant.
     */
    @Nullable
    private String readSamsungIconTheme() {
        if (!IS_SAMSUNG) return null;
        final Configuration config = mContext.getResources().getConfiguration();
        String themePackage = null;
        try {
            themePackage = Settings.System.getString(
                    mContext.getContentResolver(), SAMSUNG_ICON_THEME_SETTING);
        } catch (SecurityException e) {
            // One UI catches this too, which leaves only the Theme Park check
            if (!mLoggedIconThemeSettingDenied) {
                mLoggedIconThemeSettingDenied = true;
                Log.d(TAG, "Samsung icon theme setting is not readable", e);
            }
        }

        final StringBuilder id = new StringBuilder();
        if (!TextUtils.isEmpty(themePackage)) {
            if ((config.uiMode & Configuration.UI_MODE_TYPE_MASK)
                    == Configuration.UI_MODE_TYPE_DESK) {
                return null;
            }
            id.append("secIcons=").append(themePackage);
            try {
                id.append('@').append(mContext.getPackageManager()
                        .getPackageInfo(themePackage, 0).lastUpdateTime);
            } catch (NameNotFoundException e) {
                Log.d(TAG, "Samsung icon theme package not found: " + themePackage);
            }
        }
        final File themePark = new File(SAMSUNG_THEME_PARK_ICON_DIR);
        if (themePark.exists()) {
            if (id.length() > 0) id.append(',');
            id.append("themePark=").append(themePark.lastModified());
        }
        if (id.length() == 0) return null;
        return id.append(",night=").append(config.uiMode & Configuration.UI_MODE_NIGHT_MASK)
                .toString();
    }

    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    protected Drawable loadCalendarDrawable(int iconDpi, @Nullable ThemeData td) {
        PackageManager pm = mContext.getPackageManager();
        try {
            final Bundle metadata = pm.getActivityInfo(
                    mCalendar,
                    PackageManager.GET_UNINSTALLED_PACKAGES | PackageManager.GET_META_DATA)
                    .metaData;
            final Resources resources = pm.getResourcesForApplication(mCalendar.getPackageName());
            final int id = getDynamicIconId(metadata, resources);
            if (id != ID_NULL) {
                if (DEBUG) Log.d(TAG, "Got icon #" + id);
                Drawable drawable = resources.getDrawableForDensity(id, iconDpi, null /* theme */);
                if (ATLEAST_T && drawable instanceof AdaptiveIconDrawable && td != null) {
                    AdaptiveIconDrawable aid = (AdaptiveIconDrawable) drawable;
                    if  (aid.getMonochrome() != null) {
                        return drawable;
                    }
                    if ("array".equals(td.mResources.getResourceTypeName(td.mResID))) {
                        TypedArray ta = td.mResources.obtainTypedArray(td.mResID);
                        int monoId = ta.getResourceId(IconProvider.getDay(), ID_NULL);
                        ta.recycle();
                        return monoId == ID_NULL ? drawable
                                : new AdaptiveIconDrawable(aid.getBackground(), aid.getForeground(),
                                        new ThemeData(td.mResources, monoId).loadPaddedDrawable());
                    }
                }
                return drawable;
            }
        } catch (PackageManager.NameNotFoundException e) {
            if (DEBUG) {
                Log.d(TAG, "Could not get activityinfo or resources for package: "
                        + mCalendar.getPackageName());
            }
        }
        return null;
    }

    /**
     * Returns the default activity icon
     */
    @NonNull
    public Drawable getFullResDefaultActivityIcon(final int iconDpi) {
        Drawable icon = Objects.requireNonNull(Resources.getSystem().getDrawableForDensity(
            android.R.drawable.sym_def_app_icon, iconDpi));
        
        return CustomAdaptiveIconDrawable.wrapNonNull(icon);
    }

    /**
     * @param metadata metadata of the default activity of Calendar
     * @param resources from the Calendar package
     * @return the resource id for today's Calendar icon; 0 if resources cannot be found.
     */
    private int getDynamicIconId(Bundle metadata, Resources resources) {
        if (metadata == null) {
            return ID_NULL;
        }
        String key = mCalendar.getPackageName() + ICON_METADATA_KEY_PREFIX;
        final int arrayId = metadata.getInt(key, ID_NULL);
        if (arrayId == ID_NULL) {
            return ID_NULL;
        }
        try {
            return resources.obtainTypedArray(arrayId).getResourceId(getDay(), ID_NULL);
        } catch (Resources.NotFoundException e) {
            if (DEBUG) {
                Log.d(TAG, "package defines '" + key + "' but corresponding array not found");
            }
            return ID_NULL;
        }
    }

    /**
     * Refreshes the system state definition used to check the validity of an app icon. It
     * incorporates all the properties that can affect the app icon like the list of enabled locale
     * and system-version.
     */
    public void updateSystemState() {
        mSystemState = mContext.getResources().getConfiguration().getLocales().toLanguageTags()
                + "," + Build.VERSION.SDK_INT;
        if (IS_SAMSUNG) {
            String iconTheme = readSamsungIconTheme();
            mSamsungIconTheme = iconTheme;
            mSamsungIconThemeLoaded = true;
            if (iconTheme != null) {
                // Lawnchair: re-render cached icons when the Samsung icon theme changes
                mSystemState += "," + iconTheme;
            }
        }
    }

    /**
     * @return Today's day of the month, zero-indexed.
     */
    protected static int getDay() {
        return Calendar.getInstance().get(Calendar.DAY_OF_MONTH) - 1;
    }

    private static ComponentName parseComponentOrNull(Context context, int resId) {
        String cn = context.getString(resId);
        return TextUtils.isEmpty(cn) ? null : ComponentName.unflattenFromString(cn);
    }

    /**
     * Registers a callback to listen for various system dependent icon changes.
     */
    public SafeCloseable registerIconChangeListener(IconChangeListener listener, Handler handler) {
        return new IconChangeReceiver(listener, handler);
    }

    /**
     * Notifies the provider when an icon is loaded from cache
     */
    public void notifyIconLoaded(
            @NonNull BitmapInfo icon, @NonNull ComponentKey key, @NonNull CachingLogic<?> logic) { }

    public static class ThemeData {

        final Resources mResources;
        final int mResID;

        public ThemeData(Resources resources, int resID) {
            mResources = resources;
            mResID = resID;
        }

        public Drawable loadPaddedDrawable() {
            if (!"drawable".equals(mResources.getResourceTypeName(mResID))) {
                return null;
            }
            Drawable d = mResources.getDrawable(mResID).mutate();
            d = new InsetDrawable(d, .2f);
            float inset = getExtraInsetFraction() / (1 + 2 * getExtraInsetFraction());
            Drawable fg = new InsetDrawable(d, inset);
            return fg;
        }
    }

    private class IconChangeReceiver extends BroadcastReceiver implements SafeCloseable {

        private final IconChangeListener mCallback;

        IconChangeReceiver(IconChangeListener callback, Handler handler) {
            mCallback = callback;
            if (mCalendar != null || mClock != null) {
                final IntentFilter filter = new IntentFilter(ACTION_TIMEZONE_CHANGED);
                if (mCalendar != null) {
                    filter.addAction(Intent.ACTION_TIME_CHANGED);
                    filter.addAction(ACTION_DATE_CHANGED);
                }
                mContext.registerReceiver(this, filter, null, handler);
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case ACTION_TIMEZONE_CHANGED:
                    if (mClock != null) {
                        mCallback.onAppIconChanged(mClock.getPackageName(), Process.myUserHandle());
                    }
                    // follow through
                case ACTION_DATE_CHANGED:
                case ACTION_TIME_CHANGED:
                    if (mCalendar != null) {
                        for (UserHandle user
                                : context.getSystemService(UserManager.class).getUserProfiles()) {
                            mCallback.onAppIconChanged(mCalendar.getPackageName(), user);
                        }
                    }
                    break;
            }
        }

        @Override
        public void close() {
            try {
                mContext.unregisterReceiver(this);
            } catch (Exception ignored) { }
        }
    }

    /**
     * Listener for receiving icon changes
     */
    public interface IconChangeListener {

        /**
         * Called when the icon for a particular app changes
         */
        void onAppIconChanged(String packageName, UserHandle user);
    }
}
