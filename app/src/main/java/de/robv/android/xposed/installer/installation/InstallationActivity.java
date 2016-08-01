package de.robv.android.xposed.installer.installation;

import android.animation.Animator;
import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.CardView;
import android.support.v7.widget.Toolbar;
import android.text.Spannable;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;

import de.robv.android.xposed.installer.R;
import de.robv.android.xposed.installer.XposedBaseActivity;
import de.robv.android.xposed.installer.util.ThemeUtil;

public class InstallationActivity extends XposedBaseActivity {

    private static String mPath;

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

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setRetainInstance(true);

            if (savedInstanceState == null) {
                new android.os.Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        InstallDirect.install(mPath, InstallationFragment.this, isOkSystemless());
                    }
                }, 1500); // wait 1.5 secs. installation is so fast
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
            appendText(getString(R.string.file_done), TYPE_OK);

            mProgress.setIndeterminate(false);
            mConsoleResult.setImageResource(R.drawable.ic_check_circle);
            //noinspection deprecation
            mResultContainer.setCardBackgroundColor(getResources().getColor(R.color.darker_green));
            animateResult();
        }

        @Override
        public void onError(int exitCode, String error) {
            appendText(error, TYPE_ERROR);

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
            // TODO colors should probably be defined in resources
            int color;
            switch (type) {
                case TYPE_ERROR:
                    color = 0xFFF44336;
                    break;
                case TYPE_OK:
                    color = 0xFF4CAF50;
                    break;
                default:
                    mLogText.append(text);
                    mLogText.append("\n");
                    return;
            }

            int start = mLogText.length();
            mLogText.append(text);
            int end = mLogText.length();
            ((Spannable) mLogText.getText()).setSpan(new ForegroundColorSpan(color), start, end, 0);
            mLogText.append("\n");
        }

        private boolean isOkSystemless() {
            boolean suPartition = new File("/su").exists() && new File("/data/su.img").exists();
            boolean m = Build.VERSION.SDK_INT >= 23;

            /*
            TODO: Add toggle for user to force system installation
             */

            return m && suPartition;
        }
    }
}
