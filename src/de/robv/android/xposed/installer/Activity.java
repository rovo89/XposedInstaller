package de.robv.android.xposed.installer;

import android.os.Bundle;

public abstract class Activity extends android.app.Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (getApplication() instanceof Application) {
			((Application) getApplication()).dispatchActivityCreated(this, savedInstanceState);
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		if (getApplication() instanceof Application) {
			((Application) getApplication()).dispatchActivityStarted(this);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (getApplication() instanceof Application) {
			((Application) getApplication()).dispatchActivityResumed(this);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (getApplication() instanceof Application) {
			((Application) getApplication()).dispatchActivityPaused(this);
		}
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (getApplication() instanceof Application) {
			((Application) getApplication()).dispatchActivityStopped(this);
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (getApplication() instanceof Application) {
			((Application) getApplication()).dispatchActivitySaveInstanceState(this, outState);
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (getApplication() instanceof Application) {
			((Application) getApplication()).dispatchActivityDestroyed(this);
		}
	}
}