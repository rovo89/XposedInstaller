package de.robv.android.xposed.installer.installation;

import android.animation.TimeAnimator;
import android.animation.TimeAnimator.TimeListener;
import android.annotation.SuppressLint;
import android.app.Fragment;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.text.Spannable;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;

import de.robv.android.xposed.installer.R;
import de.robv.android.xposed.installer.XposedApp;
import de.robv.android.xposed.installer.XposedBaseActivity;

public class InstallationActivity extends XposedBaseActivity {
    private static final int REBOOT_COUNTDOWN = 15000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Flashable flashable = getIntent().getParcelableExtra(Flashable.KEY);
        if (flashable == null) {
            Log.e(XposedApp.TAG, InstallationActivity.class.getName() + ": Flashable is missing");
            finish();
            return;
        }

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

        if (savedInstanceState == null) {
            InstallationFragment logFragment = new InstallationFragment();
            getFragmentManager().beginTransaction().replace(R.id.container, logFragment).commit();
            logFragment.startInstallation(this, flashable);
        }
    }

    public static class InstallationFragment extends Fragment implements FlashCallback {
        private static final int TYPE_NONE = 0;
        private static final int TYPE_ERROR = -1;
        private static final int TYPE_OK = 1;
        private TextView mLogText;
        private ProgressBar mProgress;
        private ImageView mConsoleResult;
        private Button mBtnReboot;

        public void startInstallation(final Context context, final Flashable flashable) {
            new Thread("FlashZip") {
                @Override
                public void run() {
                    flashable.flash(context, InstallationFragment.this);
                }
            }.start();
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setRetainInstance(true);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.activity_installation, container, false);

            mLogText = (TextView) view.findViewById(R.id.console);
            mProgress = (ProgressBar) view.findViewById(R.id.progressBar);
            mConsoleResult = (ImageView) view.findViewById(R.id.console_result);
            mBtnReboot = (Button) view.findViewById(R.id.reboot);


            return view;
        }

        @Override
        public void onStarted() {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
            }
        }

        @Override
        public void onLine(final String line) {
            XposedApp.postOnUiThread(new Runnable() {
                @Override
                public void run() {
                    appendText(line, TYPE_NONE);
                }
            });
        }

        @Override
        public void onErrorLine(final String line) {
            XposedApp.postOnUiThread(new Runnable() {
                @Override
                public void run() {
                    appendText(line, TYPE_ERROR);
                }
            });
        }

        private static void expand(final View v, int duration) {
            v.measure(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            final int targetHeight = v.getMeasuredHeight();

            v.getLayoutParams().height = 1;
            v.setVisibility(View.VISIBLE);

            Animation a = new Animation() {
                @Override
                protected void applyTransformation(float interpolatedTime, Transformation t) {
                    v.getLayoutParams().height = (interpolatedTime == 1)
                            ? LayoutParams.WRAP_CONTENT
                            : (int) (targetHeight * interpolatedTime);
                    v.requestLayout();
                }
            };

            a.setDuration(duration);
            v.startAnimation(a);
        }

        @Override
        public void onDone() {
            XposedApp.postOnUiThread(new Runnable() {
                @Override
                public void run() {
                    appendText(getString(R.string.file_done), TYPE_OK);

                    mConsoleResult.setImageResource(R.drawable.ic_check_circle);
                    mConsoleResult.setVisibility(View.VISIBLE);

                    mProgress.setIndeterminate(false);
                    mProgress.setRotation(180);
                    mProgress.setMax(REBOOT_COUNTDOWN);

                    expand(getView().findViewById(R.id.buttonPanel),
                            getResources().getInteger(android.R.integer.config_mediumAnimTime));

                    // TODO extract to string resources
                    final String format = "%1$s (%2$d)";
                    final String action = "Reboot to recovery";

                    TimeAnimator countdown = new TimeAnimator();
                    countdown.setTimeListener(new TimeListener() {
                        private int minWidth = 0;
                        private int prevSeconds = -1;

                        @Override
                        public void onTimeUpdate(TimeAnimator animation, long totalTime, long deltaTime) {
                            int remaining = REBOOT_COUNTDOWN - (int) totalTime;
                            mProgress.setProgress(remaining);
                            if (remaining <= 0) {
                                mBtnReboot.setText(String.format(format, action, 0));
                                animation.end();

                                // TODO execute action here
                            } else {
                                int seconds = remaining / 1000 + 1;
                                if (seconds != prevSeconds) {
                                    mBtnReboot.setText(String.format(format, action, seconds));

                                    // Make sure that the button width doesn't shrink.
                                    if (mBtnReboot.getWidth() > minWidth) {
                                        minWidth = mBtnReboot.getWidth();
                                        mBtnReboot.setMinimumWidth(minWidth);
                                    }

                                    prevSeconds = seconds;
                                }
                            }
                        }
                    });
                    countdown.start();
                }
            });
        }

        @Override
        public void onError(final int exitCode, final String error) {
            XposedApp.postOnUiThread(new Runnable() {
                @Override
                public void run() {
                    appendText(error, TYPE_ERROR);
                    mConsoleResult.setImageResource(R.drawable.ic_error);
                    mConsoleResult.setVisibility(View.VISIBLE);

                    mProgress.setIndeterminate(false);
                }
            });
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
