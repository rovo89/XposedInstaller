package de.robv.android.xposed.installer.util.chrome;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.support.customtabs.CustomTabsClient;
import android.support.customtabs.CustomTabsIntent;
import android.support.customtabs.CustomTabsServiceConnection;
import android.support.customtabs.CustomTabsSession;

import java.util.List;

/**
 * This is a helper class to manage the connection to the Custom Tabs Service.
 */
public class CustomTabActivityHelper implements ServiceConnectionCallback {
    private CustomTabsSession mCustomTabsSession;
    private CustomTabsClient mClient;
    private CustomTabsServiceConnection mConnection;
    private ConnectionCallback mConnectionCallback;

    /**
     * Opens the URL on a Custom Tab if possible. Otherwise fallsback to opening
     * it on a WebView.
     *
     * @param activity         The host activity.
     * @param customTabsIntent a CustomTabsIntent to be used if Custom Tabs is available.
     * @param uri              the Uri to be opened.
     * @param fallback         a CustomTabFallback to be used if Custom Tabs is not
     *                         available.
     */
    public static void openCustomTab(Activity activity, CustomTabsIntent customTabsIntent, Uri uri, CustomTabFallback fallback) {
        String packageName = CustomTabsHelper.getPackageNameToUse(activity);

        // If we cant find a package name, it means theres no browser that
        // supports
        // Chrome Custom Tabs installed. So, we fallback to the webview
        if (packageName == null) {
            if (fallback != null) {
                fallback.openUri(activity, uri);
            }
        } else {
            customTabsIntent.intent.setPackage(packageName);
            customTabsIntent.launchUrl(activity, uri);
        }
    }

    /**
     * Unbinds the Activity from the Custom Tabs Service.
     *
     * @param activity the activity that is connected to the service.
     */
    public void unbindCustomTabsService(Activity activity) {
        if (mConnection == null)
            return;
        activity.unbindService(mConnection);
        mClient = null;
        mCustomTabsSession = null;
        mConnection = null;
    }

    /**
     * Creates or retrieves an exiting CustomTabsSession.
     *
     * @return a CustomTabsSession.
     */
    public CustomTabsSession getSession() {
        if (mClient == null) {
            mCustomTabsSession = null;
        } else if (mCustomTabsSession == null) {
            mCustomTabsSession = mClient.newSession(null);
        }
        return mCustomTabsSession;
    }

    /**
     * Register a Callback to be called when connected or disconnected from the
     * Custom Tabs Service.
     *
     * @param connectionCallback
     */
    public void setConnectionCallback(ConnectionCallback connectionCallback) {
        this.mConnectionCallback = connectionCallback;
    }

    /**
     * Binds the Activity to the Custom Tabs Service.
     *
     * @param activity
     *            the activity to be binded to the service.
     */
    public void bindCustomTabsService(Activity activity) {
        if (mClient != null)
            return;

        String packageName = CustomTabsHelper.getPackageNameToUse(activity);
        if (packageName == null)
            return;

        mConnection = new ServiceConnection(this);
        CustomTabsClient.bindCustomTabsService(activity, packageName, mConnection);
    }

    /**
     * @return true if call to mayLaunchUrl was accepted.
     * @see {@link CustomTabsSession#mayLaunchUrl(Uri, Bundle, List)}.
     */
    public boolean mayLaunchUrl(Uri uri, Bundle extras,
                                List<Bundle> otherLikelyBundles) {
        if (mClient == null)
            return false;

        CustomTabsSession session = getSession();
        if (session == null)
            return false;

        return session.mayLaunchUrl(uri, extras, otherLikelyBundles);
    }

    @Override
    public void onServiceConnected(CustomTabsClient client) {
        mClient = client;
        mClient.warmup(0L);
        if (mConnectionCallback != null)
            mConnectionCallback.onCustomTabsConnected();
    }

    @Override
    public void onServiceDisconnected() {
        mClient = null;
        mCustomTabsSession = null;
        if (mConnectionCallback != null)
            mConnectionCallback.onCustomTabsDisconnected();
    }

    /**
     * A Callback for when the service is connected or disconnected. Use those
     * callbacks to handle UI changes when the service is connected or
     * disconnected.
     */
    public interface ConnectionCallback {
        /**
         * Called when the service is connected.
         */
        void onCustomTabsConnected();

        /**
         * Called when the service is disconnected.
         */
        void onCustomTabsDisconnected();
    }

    /**
     * To be used as a fallback to open the Uri when Custom Tabs is not
     * available.
     */
    public interface CustomTabFallback {
        /**
         * @param activity The Activity that wants to open the Uri.
         * @param uri      The uri to be opened by the fallback.
         */
        void openUri(Activity activity, Uri uri);
    }

}