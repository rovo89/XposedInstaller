package de.robv.android.xposed.installer.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ExpandableListAdapter;
import android.widget.LinearLayout;
import de.robv.android.xposed.installer.R;

/** An ExpandableListView that can be used inside a ScrollView */
public class ExpandableStaticListView extends LinearLayout {
	ExpandableListAdapter mAdapter;
	final LayoutInflater mInflater;
	boolean mExpandedGroups[];
	View mGroups[];
	View mChildren[][];

	private final Drawable mIndicatorExpanded;
	private final Drawable mIndicatorCollapsed;
	private final int mIndicatorMarginLeft;
	private final int mIndicatorMarginTop;

	public ExpandableStaticListView(Context context) {
		this(context, null);
	}

	public ExpandableStaticListView(Context context, AttributeSet attrs) {
		super(context, attrs);
		setOrientation(VERTICAL);
		mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		mIndicatorExpanded = getResources().getDrawable(android.R.drawable.arrow_up_float);
		mIndicatorExpanded.setBounds(0, 0, mIndicatorExpanded.getIntrinsicWidth(), mIndicatorExpanded.getIntrinsicHeight());
		mIndicatorCollapsed = getResources().getDrawable(android.R.drawable.arrow_down_float);
		mIndicatorCollapsed.setBounds(0, 0, mIndicatorCollapsed.getIntrinsicWidth(), mIndicatorCollapsed.getIntrinsicHeight());
		DisplayMetrics metrics = getResources().getDisplayMetrics();
		mIndicatorMarginLeft = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, metrics);
		mIndicatorMarginTop = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 15, metrics);

		_reloadViewsOnUiThread();
	}

	public void reloadViews() {
		post(new Runnable() {
			@Override
			public void run() {
				_reloadViewsOnUiThread();
			}
		});
	}

	void _reloadViewsOnUiThread() {
		removeAllViews();
		if (mAdapter == null)
			return;

		final int groupCount = mAdapter.getGroupCount();
		mExpandedGroups = new boolean[groupCount];
		mGroups = new View[groupCount];
		mChildren = new View[groupCount][];

		for (int groupPosition = 0; groupPosition < groupCount; groupPosition++) {
			// add divider
			if (groupPosition != 0)
				addView(getDivider());

			// add group header
			View groupView = mAdapter.getGroupView(groupPosition, true, null, this);
			mGroups[groupPosition] = groupView;
			groupView.setClickable(true);
			final int finalGroupPosition = groupPosition;
			groupView.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					toggleGroup(finalGroupPosition);
				}
			});
			addView(groupView);
		}
	}

	void loadChildViews(int groupPosition) {
		// add children
		final int childCount = mAdapter.getChildrenCount(groupPosition);
		int viewIndex = indexOfChild(mGroups[groupPosition]);
		mChildren[groupPosition] = (childCount > 0) ? new View[childCount * 2 - 1] : new View[0];
		for (int childPosition = 0; childPosition < childCount; childPosition++) {
			if (childPosition != 0) {
				View divider = getDivider();
				mChildren[groupPosition][childPosition*2 - 1] = divider;
				addView(divider, ++viewIndex);
			}

			View childView = mAdapter.getChildView(groupPosition, childPosition, childPosition == childCount - 1, null, this);
			mChildren[groupPosition][childPosition*2] = childView;
			addView(childView, ++viewIndex);
		}
	}


	View getDivider() {
		View divider = new View(getContext());
		divider.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 1));
		divider.setBackgroundColor(getResources().getColor(R.color.list_divider));
		return divider;
	}

	@Override
	protected void dispatchDraw(Canvas canvas) {
		super.dispatchDraw(canvas);
		if (mGroups == null)
			return;

		for (int i = 0; i < mGroups.length; i++) {
			Drawable indicator = mExpandedGroups[i] ? mIndicatorExpanded : mIndicatorCollapsed;
			View v = mGroups[i];
			Rect newBounds = indicator.copyBounds();
			newBounds.offsetTo(v.getLeft() + mIndicatorMarginLeft, v.getTop() + mIndicatorMarginTop);
			indicator.setBounds(newBounds);
			indicator.draw(canvas);
		}
	}

	public void setAdapter(ExpandableListAdapter adapter) {
		mAdapter = adapter;
		reloadViews();
	}

	public ExpandableListAdapter getAdapter() {
		return mAdapter;
	}

	public void toggleGroup(int groupPosition) {
		if (mAdapter == null)
			return;

		if (mExpandedGroups[groupPosition])
			collapseGroup(groupPosition);
		else
			expandGroup(groupPosition);
	}

	public void expandGroup(final int groupPosition) {
		post(new Runnable() {
			@Override
			public void run() {
				if (mAdapter == null)
					return;

				mExpandedGroups[groupPosition] = true;
				if (mChildren[groupPosition] == null) {
					loadChildViews(groupPosition);
				} else {
					for (int i = 0; i < mChildren[groupPosition].length; i++) {
						mChildren[groupPosition][i].setVisibility(View.VISIBLE);
					}
				}
			}
		});
	}

	public void collapseGroup(final int groupPosition) {
		post(new Runnable() {
			@Override
			public void run() {
				if (mAdapter == null)
					return;

				mExpandedGroups[groupPosition] = false;
				if (mChildren[groupPosition] == null)
					return;

				for (int i = 0; i < mChildren[groupPosition].length; i++) {
					mChildren[groupPosition][i].setVisibility(View.GONE);
				}
			}
		});
	}
}
