package de.robv.android.xposed.installer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.app.Application.ActivityLifecycleCallbacks;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileUtils;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.robv.android.xposed.installer.util.AssetUtil;
import de.robv.android.xposed.installer.util.ModuleUtil;
import de.robv.android.xposed.installer.util.NotificationUtil;
import de.robv.android.xposed.installer.util.RepoLoader;

public class XposedApp extends Application implements ActivityLifecycleCallbacks {
    public static final String TAG = "XposedInstaller";

    @SuppressLint("SdCardPath")
    public static final String BASE_DIR = "/data/data/de.robv.android.xposed.installer/";
    public static final String ENABLED_MODULES_LIST_FILE = XposedApp.BASE_DIR + "conf/enabled_modules.list";
    private static final File XPOSED_PROP_FILE_SYSTEMLESS = new File("/xposed/xposed.prop");
    private static final File XPOSED_PROP_FILE_SYSTEMLESS_2 = new File("/vendor/xposed.prop");
    private static final File XPOSED_PROP_FILE = new File("/system/xposed.prop");
    public static int WRITE_EXTERNAL_PERMISSION = 69;
    public static String THIS_APK_VERSION = "1465218000000";
    public static int[] iconsValues = new int[]{R.mipmap.ic_launcher, R.mipmap.ic_launcher_hjmodi, R.mipmap.ic_launcher_rovo, R.mipmap.ic_launcher_rovo_old, R.mipmap.ic_launcher_staol};
    private static Pattern PATTERN_APP_PROCESS_VERSION = Pattern.compile(".*with Xposed support \\(version (.+)\\).*");
    private static XposedApp mInstance = null;
    private static Thread mUiThread;
    private static Handler mMainHandler;
    private boolean mIsUiLoaded = false;
    private Activity mCurrentActivity = null;
    private SharedPreferences mPref;
    private Map<String, String> mXposedProp;

    public static XposedApp getInstance() {
        return mInstance;
    }

    public static void runOnUiThread(Runnable action) {
        if (Thread.currentThread() != mUiThread) {
            mMainHandler.post(action);
        } else {
            action.run();
        }
    }

    public static Integer getXposedVersion() {
        if (Build.VERSION.SDK_INT >= 21) {
            return getActiveXposedVersion();
        } else {
            return getInstalledAppProcessVersion();
        }
    }

    private static int getInstalledAppProcessVersion() {
        try {
            return getAppProcessVersion(new FileInputStream("/system/bin/app_process"));
        } catch (IOException e) {
            return 0;
        }
    }

    private static int getAppProcessVersion(InputStream is) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String line;
        while ((line = br.readLine()) != null) {
            if (!line.contains("Xposed"))
                continue;

            Matcher m = PATTERN_APP_PROCESS_VERSION.matcher(line);
            if (m.find()) {
                is.close();
                return ModuleUtil.extractIntPart(m.group(1));
            }
        }
        is.close();
        return 0;
    }

    // This method is hooked by XposedBridge to return the current version
    public static Integer getActiveXposedVersion() {
        return -1;
    }

    public static Map<String, String> getXposedProp() {
        synchronized (mInstance) {
            return mInstance.mXposedProp;
        }
    }

    public static SharedPreferences getPreferences() {
        return mInstance.mPref;
    }

    public static int getColor(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(context.getPackageName() + "_preferences", MODE_PRIVATE);
        int defaultColor = context.getResources().getColor(R.color.colorPrimary);

        return prefs.getInt("colors", defaultColor);
    }

    public static void setColors(ActionBar actionBar, Object value,
                                 Activity activity) {
        int color = (int) value;
        SharedPreferences prefs = activity.getSharedPreferences(activity.getPackageName() + "_preferences", MODE_PRIVATE);

        int drawable = iconsValues[Integer.parseInt(prefs.getString("custom_icon", "0"))];

        if (actionBar != null)
            actionBar.setBackgroundDrawable(new ColorDrawable(color));

        if (Build.VERSION.SDK_INT >= 21) {

            ActivityManager.TaskDescription tDesc = new ActivityManager.TaskDescription(activity.getString(R.string.app_name),
                    drawableToBitmap(activity.getDrawable(drawable)), color);
            activity.setTaskDescription(tDesc);

            if (getPreferences().getBoolean("nav_bar", false)) {
                activity.getWindow().setNavigationBarColor(darkenColor(color, 0.85f));
            } else {
                int black = activity.getResources().getColor(android.R.color.black);
                activity.getWindow().setNavigationBarColor(black);
            }
        }
    }

    public static Bitmap drawableToBitmap(Drawable drawable) {
        Bitmap bitmap;

        if (drawable instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if (bitmapDrawable.getBitmap() != null) {
                return bitmapDrawable.getBitmap();
            }
        }

        if (drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        } else {
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        }

        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    /**
     * @author PeterCxy https://github.com/PeterCxy/Lolistat/blob/aide/app/src/
     * main/java/info/papdt/lolistat/support/Utility.java
     */
    public static int darkenColor(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= factor;
        return Color.HSVToColor(hsv);
    }

    public void onCreate() {
        super.onCreate();
        mInstance = this;
        mUiThread = Thread.currentThread();
        mMainHandler = new Handler();

        mPref = PreferenceManager.getDefaultSharedPreferences(this);
        reloadXposedProp();
        createDirectories();
        cleanup();
        NotificationUtil.init();
        AssetUtil.checkStaticBusyboxAvailability();
        AssetUtil.removeBusybox();

        registerActivityLifecycleCallbacks(this);

        @SuppressLint("SimpleDateFormat") DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
        Date date = new Date();

        if (!mPref.getString("date", "").equals(dateFormat.format(date))) {
            mPref.edit().putString("date", dateFormat.format(date)).apply();

            try {
                Log.i(TAG, String.format("XposedInstaller - %s - %s", THIS_APK_VERSION, getPackageManager().getPackageInfo(getPackageName(), 0).versionName));
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private void createDirectories() {
        mkdirAndChmod("bin", 00771);
        mkdirAndChmod("conf", 00771);
        mkdirAndChmod("log", 00777);
    }

    private void cleanup() {
        if (!mPref.getBoolean("cleaned_up_sdcard", false)) {
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                File sdcard = Environment.getExternalStorageDirectory();
                new File(sdcard, "Xposed-Disabler-CWM.zip").delete();
                new File(sdcard, "Xposed-Disabler-Recovery.zip").delete();
                new File(sdcard, "Xposed-Installer-Recovery.zip").delete();
                mPref.edit().putBoolean("cleaned_up_sdcard", true).apply();
            }
        }

        if (!mPref.getBoolean("cleaned_up_debug_log", false)) {
            new File(XposedApp.BASE_DIR + "log/debug.log").delete();
            new File(XposedApp.BASE_DIR + "log/debug.log.old").delete();
            mPref.edit().putBoolean("cleaned_up_debug_log", true).apply();
        }
    }

    private void mkdirAndChmod(String dir, int permissions) {
        dir = BASE_DIR + dir;
        new File(dir).mkdir();
        FileUtils.setPermissions(dir, permissions, -1, -1);
    }

    private void reloadXposedProp() {
        Map<String, String> map = Collections.emptyMap();
        if (XPOSED_PROP_FILE.canRead() || XPOSED_PROP_FILE_SYSTEMLESS.canRead() || XPOSED_PROP_FILE_SYSTEMLESS_2.canRead()) {
            File file = null;
            if (XPOSED_PROP_FILE.canRead()) {
                file = XPOSED_PROP_FILE;
            } else if (XPOSED_PROP_FILE_SYSTEMLESS.canRead()) {
                file = XPOSED_PROP_FILE_SYSTEMLESS;
            } else if (XPOSED_PROP_FILE_SYSTEMLESS_2.canRead()) {
                file = XPOSED_PROP_FILE_SYSTEMLESS_2;
            }

            if (file != null) {
                FileInputStream is = null;
                try {
                    is = new FileInputStream(file);
                    map = parseXposedProp(is);
                } catch (IOException e) {
                    Log.e(XposedApp.TAG, "XposedApp -> Could not read " + file.getPath(), e);
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (IOException ignored) {
                        }
                    }
                }
            }
        }

        synchronized (this) {
            mXposedProp = map;
        }
    }

    private Map<String, String> parseXposedProp(InputStream stream)
            throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        Map<String, String> map = new LinkedHashMap<String, String>();
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.split("=", 2);
            if (parts.length != 2)
                continue;

            String key = parts[0].trim();
            if (key.charAt(0) == '#')
                continue;

            map.put(key, parts[1].trim());
        }
        return Collections.unmodifiableMap(map);
    }

    public void updateProgressIndicator(final SwipeRefreshLayout refreshLayout) {
        final boolean isLoading = RepoLoader.getInstance().isLoading() || ModuleUtil.getInstance().isLoading();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                synchronized (XposedApp.this) {
                    if (mCurrentActivity != null) {
                        mCurrentActivity.setProgressBarIndeterminateVisibility(isLoading);
                        if (refreshLayout != null)
                            refreshLayout.setRefreshing(isLoading);
                    }
                }
            }
        });
    }

    @Override
    public synchronized void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        if (mIsUiLoaded)
            return;

        RepoLoader.getInstance().triggerFirstLoadIfNecessary();
        mIsUiLoaded = true;
    }

    @Override
    public synchronized void onActivityResumed(Activity activity) {
        mCurrentActivity = activity;
        updateProgressIndicator(null);
    }

    @Override
    public synchronized void onActivityPaused(Activity activity) {
        activity.setProgressBarIndeterminateVisibility(false);
        mCurrentActivity = null;
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity,
                                            Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }
}