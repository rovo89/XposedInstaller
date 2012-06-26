package de.robv.android.xposed.installer;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.Bundle;

public class TabListener<T extends Fragment> implements ActionBar.TabListener {
    private final Activity mActivity;
    private final String mTag;
    private final Class<T> mClass;
    private final Bundle mArgs;
    private final boolean mAlwaysReload;
    private Fragment mFragment;

    public TabListener(Activity activity, String tag, Class<T> clz, boolean alwaysReload) {
        this(activity, tag, clz, alwaysReload, null);
    }

    public TabListener(Activity activity, String tag, Class<T> clz, boolean alwaysReload, Bundle args) {
        mActivity = activity;
        mTag = tag;
        mClass = clz;
        mArgs = args;
        mAlwaysReload = alwaysReload;

        // Check to see if we already have a fragment for this tab, probably
        // from a previously saved state.  If so, deactivate it, because our
        // initial state is that a tab isn't shown.
        mFragment = mActivity.getFragmentManager().findFragmentByTag(mTag);
        if (mFragment != null && !mFragment.isDetached()) {
            FragmentTransaction ft = mActivity.getFragmentManager().beginTransaction();
            ft.detach(mFragment);
            ft.commit();
        }
    }

    public void onTabSelected(Tab tab, FragmentTransaction ft) {
        if (mFragment == null) {
            mFragment = Fragment.instantiate(mActivity, mClass.getName(), mArgs);
            ft.add(android.R.id.content, mFragment, mTag);
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