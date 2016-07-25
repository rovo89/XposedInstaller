package de.robv.android.xposed.installer.installation;

import android.animation.Animator;
import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.CardView;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;

import java.util.LinkedList;
import java.util.List;

import de.robv.android.xposed.installer.R;
import de.robv.android.xposed.installer.XposedBaseActivity;
import de.robv.android.xposed.installer.util.AssetUtil;
import de.robv.android.xposed.installer.util.RootUtil;
import de.robv.android.xposed.installer.util.ThemeUtil;

public class InstallationActivity extends XposedBaseActivity {

    private static String mPath;
    private static RootUtil mRootUtil = new RootUtil();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeUtil.setTheme(this);
        setContentView(R.layout.activity_container);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setTitle(R.string.install);
            ab.setDisplayHomeAsUpEnabled(true);
        }

        setFloating(toolbar, R.string.install);

        if (getIntent().getExtras() != null) {
            mPath = getIntent().getExtras().getString("path");
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().replace(R.id.container, new InstallationFragment()).commit();
        }
    }

    public static class InstallationFragment extends Fragment implements InstallCallback {

        private static final int TYPE_NONE = 0;
        private static final int TYPE_ERROR = -1;
        private static final int TYPE_OK = 1;
        private TextView mLogText;
        private ProgressBar mProgress;
        private ImageView mConsoleResult;
        private CardView mResultContainer;

        private boolean startShell() {
            if (mRootUtil.startShell())
                return true;

            showAlert(getString(R.string.root_failed));
            return false;
        }

        private void showAlert(final String result) {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showAlert(result);
                    }
                });
                return;
            }

            MaterialDialog dialog = new MaterialDialog.Builder(getActivity()).content(result).positiveText(android.R.string.ok).build();
            dialog.show();

            TextView txtMessage = (TextView) dialog
                    .findViewById(android.R.id.message);
            try {
                txtMessage.setTextSize(14);
            } catch (NullPointerException ignored) {
            }
        }

        private void areYouSure(int contentTextId, MaterialDialog.ButtonCallback yesHandler) {
            new MaterialDialog.Builder(getActivity()).title(R.string.areyousure)
                    .content(contentTextId)
                    .iconAttr(android.R.attr.alertDialogIcon)
                    .positiveText(android.R.string.yes)
                    .negativeText(android.R.string.no).callback(yesHandler).show();
        }

        private void reboot(String mode) {
            if (!startShell())
                return;

            List<String> messages = new LinkedList<>();

            String command = "reboot";
            if (mode != null) {
                command += " " + mode;
                if (mode.equals("recovery"))
                    // create a flag used by some kernels to boot into recovery
                    mRootUtil.executeWithBusybox("touch /cache/recovery/boot", messages);
            }

            if (mRootUtil.executeWithBusybox(command, messages) != 0) {
                messages.add("");
                messages.add(getString(R.string.reboot_failed));
                showAlert(TextUtils.join("\n", messages).trim());
            }
            AssetUtil.removeBusybox();
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setRetainInstance(true);

            if (savedInstanceState == null) {
                InstallDirect.install(mPath, this, mPath.contains("systemless"));
            }
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.activity_installation, container, false);

            mLogText = (TextView) view.findViewById(R.id.console);
            mProgress = (ProgressBar) view.findViewById(R.id.progressBar);
            mConsoleResult = (ImageView) view.findViewById(R.id.console_result);
            mResultContainer = (CardView) view.findViewById(R.id.result_container);

            return view;
        }

        @Override
        public void onStarted() {
            appendText(getString(R.string.installation_started), TYPE_NONE);
            mProgress.setIndeterminate(true);
        }

        @Override
        public void onLine(String line) {
            appendText(line, TYPE_NONE);
        }

        @Override
        public void onErrorLine(String line) {
            appendText(line, TYPE_ERROR);
        }

        @Override
        public void onDone() {
            appendText(getString(R.string.installation_finished), TYPE_OK);

            mProgress.setIndeterminate(false);
            mConsoleResult.setImageResource(R.drawable.ic_check_circle);
            //noinspection deprecation
            mResultContainer.setCardBackgroundColor(getResources().getColor(R.color.darker_green));
            animateResult();


            areYouSure(R.string.reboot, new MaterialDialog.ButtonCallback() {
                @Override
                public void onPositive(MaterialDialog dialog) {
                    super.onPositive(dialog);
                    reboot(null);
                }
            });
        }

        @Override
        @SuppressLint("DefaultLocale")
        public void onError(int exitCode, String error) {
            appendText(String.format("%d %s", exitCode, error), TYPE_ERROR);

            mProgress.setIndeterminate(false);
            mConsoleResult.setImageResource(R.drawable.ic_error);
            //noinspection deprecation
            mResultContainer.setCardBackgroundColor(getResources().getColor(R.color.red_500));
            animateResult();
        }

        private void animateResult() {
            mProgress.setVisibility(View.GONE);

            int centerX = mResultContainer.getMeasuredWidth() / 2;
            int centerY = mResultContainer.getMeasuredHeight() / 2;

            int radius = Math.max(mResultContainer.getMeasuredWidth(), mResultContainer.getMeasuredHeight()) / 2;

            Animator anim = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                anim = ViewAnimationUtils.createCircularReveal(mResultContainer, centerX, centerY, 0, radius);
                anim.setDuration(750);
            }

            mResultContainer.setVisibility(View.VISIBLE);

            if (anim != null) anim.start();
        }

        @SuppressLint("SetTextI18n")
        private void appendText(String text, int type) {
            switch (type) {
                case TYPE_ERROR:
                    text = "<font color=\"#F44336\">" + text + "</font>";
                    break;
                case TYPE_OK:
                    text = "<font color=\"#4CAF50\">" + text + "</font>";
                    break;
            }

            if (mLogText.getText().length() != 0) {
                String msg = Html.toHtml(mLogText.getEditableText()) + text;
                mLogText.setText(Html.fromHtml(msg), TextView.BufferType.EDITABLE);
            } else {
                mLogText.setText(Html.fromHtml(text), TextView.BufferType.EDITABLE);
            }
        }
    }
}
