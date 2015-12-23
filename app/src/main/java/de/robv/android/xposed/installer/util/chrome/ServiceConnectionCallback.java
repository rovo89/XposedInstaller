package de.robv.android.xposed.installer.util.chrome;

import android.support.customtabs.CustomTabsClient;

/**
 * Callback for events when connecting and disconnecting from Custom Tabs
 * Service.
 */
public interface ServiceConnectionCallback {
	/**
	 * Called when the service is connected.
	 * 
	 * @param client
	 *            a CustomTabsClient
	 */
	void onServiceConnected(CustomTabsClient client);

	/**
	 * Called when the service is disconnected.
	 */
	void onServiceDisconnected();
}