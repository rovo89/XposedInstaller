package de.robv.android.xposed.installer;

import android.animation.Animator;
import android.app.Activity;
import android.app.Fragment;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.StyleSpan;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import de.robv.android.xposed.installer.repo.Module;
import de.robv.android.xposed.installer.repo.RepoParser;
import de.robv.android.xposed.installer.util.AnimatorUtil;

public class DownloadDetailsFragment extends Fragment {
	private DownloadDetailsActivity mActivity;

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		mActivity = (DownloadDetailsActivity) activity;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final Module module = mActivity.getModule();
		if (module == null)
			return null;

		final View view = inflater.inflate(R.layout.download_details, container, false);

		TextView title = (TextView) view.findViewById(R.id.download_title);
		title.setText(module.name);

		TextView author = (TextView) view.findViewById(R.id.download_author);
		if (module.author != null && !module.author.isEmpty())
			author.setText(getString(R.string.download_author, module.author));
		else
			author.setText(R.string.download_unknown_author);

		TextView description = (TextView) view.findViewById(R.id.download_description);
		if (module.description != null) {
			if (module.descriptionIsHtml) {
				description.setText(RepoParser.parseSimpleHtml(module.description));
				description.setMovementMethod(LinkMovementMethod.getInstance());
			} else {
				description.setText(module.description);
			}
		} else {
			description.setVisibility(View.GONE);
		}

		ViewGroup moreInfoContainer = (ViewGroup) view.findViewById(R.id.download_moreinfo_container);
		for (Pair<String,String> moreInfoEntry : module.moreInfo) {
			TextView moreInfoView = (TextView) inflater.inflate(R.layout.download_moreinfo, moreInfoContainer, false);

			SpannableStringBuilder ssb = new SpannableStringBuilder(moreInfoEntry.first);
			ssb.append(": ");
			ssb.setSpan(new StyleSpan(Typeface.BOLD), 0, ssb.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
			ssb.append(moreInfoEntry.second);
			moreInfoView.setText(ssb);

			moreInfoContainer.addView(moreInfoView);
		}

		return view;
	}

	@Override
	public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
		return AnimatorUtil.createSlideAnimation(this, nextAnim);
	}
}
