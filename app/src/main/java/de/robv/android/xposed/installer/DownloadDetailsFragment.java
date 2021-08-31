package de.robv.android.xposed.installer;

import android.app.Activity;
import android.app.Fragment;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import de.robv.android.xposed.installer.repo.Module;
import de.robv.android.xposed.installer.repo.RepoParser;
import de.robv.android.xposed.installer.util.NavUtil;
import de.robv.android.xposed.installer.util.chrome.LinkTransformationMethod;

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

        TextView description = (TextView) view
                .findViewById(R.id.download_description);
        if (module.description != null) {
            if (module.descriptionIsHtml) {
                description.setText(RepoParser.parseSimpleHtml(getActivity(), module.description, description));
                description.setTransformationMethod(new LinkTransformationMethod(getActivity()));
                description.setMovementMethod(LinkMovementMethod.getInstance());
            } else {
                description.setText(module.description);
            }
        } else {
            description.setVisibility(View.GONE);
        }

        ViewGroup moreInfoContainer = (ViewGroup) view.findViewById(R.id.download_moreinfo_container);
        for (Pair<String, String> moreInfoEntry : module.moreInfo) {
            View moreInfoView = inflater.inflate(R.layout.download_moreinfo, moreInfoContainer, false);
            TextView txtTitle = (TextView) moreInfoView.findViewById(android.R.id.title);
            TextView txtValue = (TextView) moreInfoView.findViewById(android.R.id.message);

            txtTitle.setText(moreInfoEntry.first + ":");
            txtValue.setText(moreInfoEntry.second);

            final Uri link = NavUtil.parseURL(moreInfoEntry.second);
            if (link != null) {
                txtValue.setTextColor(txtValue.getLinkTextColors());
                moreInfoView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        NavUtil.startURL(getActivity(), link);
                    }
                });
            }

            moreInfoContainer.addView(moreInfoView);
        }

        return view;
    }
}
