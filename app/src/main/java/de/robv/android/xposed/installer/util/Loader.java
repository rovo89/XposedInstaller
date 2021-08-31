package de.robv.android.xposed.installer.util;

import android.support.v4.widget.SwipeRefreshLayout;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import de.robv.android.xposed.installer.XposedApp;

public abstract class Loader<T> implements SwipeRefreshLayout.OnRefreshListener {
    protected final String CLASS_NAME = getClass().getSimpleName();
    private boolean mIsLoading = false;
    private boolean mReloadTriggeredOnce = false;
    private final List<Listener<T>> mListeners = new CopyOnWriteArrayList<>();
    private SwipeRefreshLayout mSwipeRefreshLayout;

    public void triggerReload(final boolean force) {
        synchronized (this) {
            if (!mReloadTriggeredOnce) {
                onFirstLoad();
                mReloadTriggeredOnce = true;
            }
        }

        if (!force && !shouldUpdate()) {
            return;
        }

        synchronized (this) {
            if (mIsLoading) {
                return;
            }
            mIsLoading = true;
            updateProgressIndicator();
        }

        new Thread("Reload" + CLASS_NAME) {
            public void run() {
                boolean hasChanged = onReload();
                if (hasChanged) {
                    notifyListeners();
                }

                synchronized (this) {
                    mIsLoading = false;
                    updateProgressIndicator();
                }
            }
        }.start();
    }

    protected synchronized void onFirstLoad() {
        // Empty by default.
    }

    protected boolean shouldUpdate() {
        return true;
    }

    protected abstract boolean onReload();

    public void clear(boolean notify) {
        synchronized (this) {
            // TODO Stop reloading repository when it should be cleared
            if (mIsLoading) {
                return;
            }
            onClear();
        }

        if (notify) {
            notifyListeners();
        }
    }

    protected abstract void onClear();

    public void triggerFirstLoadIfNecessary() {
        synchronized (this) {
            if (mReloadTriggeredOnce) {
                return;
            }
        }
        triggerReload(false);
    }

    public synchronized boolean isLoading() {
        return mIsLoading;
    }

    public interface Listener<T> {
        void onReloadDone(T loader);
    }

    public void addListener(Listener<T> listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void removeListener(Listener<T> listener) {
        mListeners.remove(listener);
    }

    protected void notifyListeners() {
        for (Listener<T> listener : mListeners) {
            //noinspection unchecked
            listener.onReloadDone((T) this);
        }
    }

    public synchronized void setSwipeRefreshLayout(SwipeRefreshLayout swipeRefreshLayout) {
        this.mSwipeRefreshLayout = swipeRefreshLayout;
        if (swipeRefreshLayout == null) {
            return;
        }

        swipeRefreshLayout.setRefreshing(mIsLoading);
        swipeRefreshLayout.setOnRefreshListener(this);
    }

    @Override
    public void onRefresh() {
        triggerReload(true);
    }

    private synchronized void updateProgressIndicator() {
        if (mSwipeRefreshLayout == null) {
            return;
        }

        XposedApp.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                synchronized (Loader.this) {
                    if (mSwipeRefreshLayout != null) {
                        mSwipeRefreshLayout.setRefreshing(mIsLoading);
                    }
                }
            }
        });
    }
}
