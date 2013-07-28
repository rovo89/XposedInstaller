package de.robv.android.xposed.installer;

import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBar.Tab;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.os.Bundle;

public class TabListener<T extends Fragment> implements ActionBar.TabListener {
    private final FragmentActivity mActivity;
    private final String mTag;
    private final Class<T> mClass;
    private final Bundle mArgs;
    private final boolean mAlwaysReload;
    private Fragment mFragment;

    public TabListener(FragmentActivity activity, String tag, Class<T> clz, boolean alwaysReload) {
        this(activity, tag, clz, alwaysReload, null);
    }

    public TabListener(FragmentActivity activity, String tag, Class<T> clz, boolean alwaysReload, Bundle args) {
        mActivity = activity;
        mTag = tag;
        mClass = clz;
        mArgs = args;
        mAlwaysReload = alwaysReload;

        // Check to see if we already have a fragment for this tab, probably
        // from a previously saved state.  If so, deactivate it, because our
        // initial state is that a tab isn't shown.
        mFragment = mActivity.getSupportFragmentManager().findFragmentByTag(mTag);
        if (mFragment != null && !mFragment.isDetached()) {
            FragmentTransaction ft = mActivity.getSupportFragmentManager().beginTransaction();
            ft.detach(mFragment);
            ft.commit();
        }
    }

    public void onTabSelected(Tab tab, FragmentTransaction ft) {
        if (mFragment == null) {
            mFragment = Fragment.instantiate(mActivity, mClass.getName(), mArgs);
            ft.add(R.id.action_bar_activity_content, mFragment, mTag);
        } else {
            ft.attach(mFragment);
        }
    }

    public void onTabUnselected(Tab tab, FragmentTransaction ft) {
        if (mFragment != null) {
        	if (mAlwaysReload) {
                ft.remove(mFragment);
                mFragment = null;
        	} else {
        		ft.detach(mFragment);
        	}
        }
    }

    public void onTabReselected(Tab tab, FragmentTransaction ft) {}
}