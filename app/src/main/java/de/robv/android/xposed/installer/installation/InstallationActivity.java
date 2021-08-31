package de.robv.android.xposed.installer.installation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Fragment;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.text.Spannable;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;

import de.robv.android.xposed.installer.R;
import de.robv.android.xposed.installer.XposedApp;
import de.robv.android.xposed.installer.XposedBaseActivity;
import de.robv.android.xposed.installer.util.RootUtil;

public class InstallationActivity extends XposedBaseActivity {
    private static final int REBOOT_COUNTDOWN = 15000;

    private static final int MEDIUM_ANIM_TIME = XposedApp.getInstance().getResources()
            .getInteger(android.R.integer.config_mediumAnimTime);
    private static final int LONG_ANIM_TIME = XposedApp.getInstance().getResources()
            .getInteger(android.R.integer.config_longAnimTime);

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
            ab.setTitle(flashable.getType().title);
            ab.setSubtitle(flashable.getTitle());
            ab.setDisplayHomeAsUpEnabled(true);
        }

        setFloating(toolbar, flashable.getType().title);

        if (savedInstanceState == null) {
            InstallationFragment logFragment = new InstallationFragment();
            getFragmentManager().beginTransaction().replace(R.id.container, logFragment).commit();
            logFragment.startInstallation(this, flashable);
        }
    }

    public static class InstallationFragment extends Fragment implements FlashCallback {
        private Flashable mFlashable;
        private static final int TYPE_NONE = 0;
        private static final int TYPE_ERROR = -1;
        private static final int TYPE_OK = 1;
        private TextView mLogText;
        private ProgressBar mProgress;
        private ImageView mConsoleResult;
        private Button mBtnReboot;
        private Button mBtnCancel;

        public void startInstallation(final Context context, final Flashable flashable) {
            mFlashable = flashable;
            new Thread("FlashZip") {
                @Override
                public void run() {
                    mFlashable.flash(context, InstallationFragment.this);
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
            mBtnCancel = (Button) view.findViewById(R.id.cancel);

            return view;
        }

        @Override
        public void onStarted() {
            try {
                Thread.sleep(LONG_ANIM_TIME * 3);
            } catch (InterruptedException ignored) {
            }
        }

        @Override
        public void onLine(final String line) {
            try {
                Thread.sleep(60);
            } catch (InterruptedException ignored) {
            }
            XposedApp.postOnUiThread(new Runnable() {
                @Override
                public void run() {
                    appendText(line, TYPE_NONE);
                }
            });
        }

        @Override
        public void onErrorLine(final String line) {
            try {
                Thread.sleep(60);
            } catch (InterruptedException ignored) {
            }
            XposedApp.postOnUiThread(new Runnable() {
                @Override
                public void run() {
                    appendText(line, TYPE_ERROR);
                }
            });
        }

        private static ValueAnimator createExpandCollapseAnimator(final View view, final boolean expand) {
            ValueAnimator animator = new ValueAnimator() {
                @Override
                public void start() {
                    view.measure(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    int height = view.getMeasuredHeight();

                    int start = 0, end = 0;
                    if (expand) {
                        start = -height;
                    } else {
                        end = -height;
                    }

                    setIntValues(start, end);

                    super.start();
                }
            };

            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                final ViewGroup.MarginLayoutParams layoutParams = ((ViewGroup.MarginLayoutParams) view.getLayoutParams());

                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    layoutParams.bottomMargin = (Integer) animation.getAnimatedValue();
                    view.requestLayout();
                }
            });

            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    view.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!expand) {
                        view.setVisibility(View.GONE);
                    }
                }
            });

            return animator;
        }

        @Override
        public void onDone() {
            XposedApp.getInstance().reloadXposedProp();
            try {
                Thread.sleep(LONG_ANIM_TIME);
            } catch (InterruptedException ignored) {
            }
            XposedApp.postOnUiThread(new Runnable() {
                @Override
                public void run() {
                    appendText("\n" + getString(R.string.file_done), TYPE_OK);

                    // Fade in the result image.
                    mConsoleResult.setImageResource(R.drawable.ic_check_circle);
                    mConsoleResult.setVisibility(View.VISIBLE);
                    ObjectAnimator fadeInResult = ObjectAnimator.ofFloat(mConsoleResult, "alpha", 0.0f, 0.03f);
                    fadeInResult.setDuration(MEDIUM_ANIM_TIME * 2);

                    // Collapse the whole bottom bar.
                    View buttomBar = getView().findViewById(R.id.buttonPanel);
                    Animator collapseBottomBar = createExpandCollapseAnimator(buttomBar, false);
                    collapseBottomBar.setDuration(MEDIUM_ANIM_TIME);
                    collapseBottomBar.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mProgress.setIndeterminate(false);
                            mProgress.setRotation(180);
                            mProgress.setMax(REBOOT_COUNTDOWN);
                            mProgress.setProgress(REBOOT_COUNTDOWN);

                            mBtnReboot.setVisibility(View.VISIBLE);
                            mBtnCancel.setVisibility(View.VISIBLE);
                        }
                    });

                    Animator expandBottomBar = createExpandCollapseAnimator(buttomBar, true);
                    expandBottomBar.setDuration(MEDIUM_ANIM_TIME * 2);
                    expandBottomBar.setStartDelay(LONG_ANIM_TIME * 4);

                    final ObjectAnimator countdownProgress = ObjectAnimator.ofInt(mProgress, "progress", REBOOT_COUNTDOWN, 0);
                    countdownProgress.setDuration(REBOOT_COUNTDOWN);
                    countdownProgress.setInterpolator(new LinearInterpolator());

                    final ValueAnimator countdownButton = ValueAnimator.ofInt(REBOOT_COUNTDOWN / 1000, 0);
                    countdownButton.setDuration(REBOOT_COUNTDOWN);
                    countdownButton.setInterpolator(new LinearInterpolator());

                    final String format = getString(R.string.countdown);
                    final RootUtil.RebootMode rebootMode = mFlashable.getRebootMode();
                    final String action = getString(rebootMode.titleRes);
                    mBtnReboot.setText(String.format(format, action, REBOOT_COUNTDOWN / 1000));

                    countdownButton.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        private int minWidth = 0;

                        @Override
                        public void onAnimationUpdate(ValueAnimator animation) {
                            mBtnReboot.setText(String.format(format, action, animation.getAnimatedValue()));

                            // Make sure that the button width doesn't shrink.
                            if (mBtnReboot.getWidth() > minWidth) {
                                minWidth = mBtnReboot.getWidth();
                                mBtnReboot.setMinimumWidth(minWidth);
                            }
                        }
                    });

                    countdownButton.addListener(new AnimatorListenerAdapter() {
                        private boolean canceled = false;

                        @Override
                        public void onAnimationCancel(Animator animation) {
                            canceled = true;
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (!canceled) {
                                mBtnReboot.callOnClick();
                            }
                        }
                    });

                    mBtnReboot.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            countdownProgress.cancel();
                            countdownButton.cancel();

                            RootUtil rootUtil = new RootUtil();
                            if (!rootUtil.startShell(InstallationFragment.this)
                                    || !rootUtil.reboot(rebootMode, InstallationFragment.this)) {
                                onError(FlashCallback.ERROR_GENERIC, getString(R.string.reboot_failed));
                            }
                        }
                    });

                    mBtnCancel.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            countdownProgress.cancel();
                            countdownButton.cancel();

                            getActivity().finish();
                        }
                    });

                    AnimatorSet as = new AnimatorSet();
                    as.play(fadeInResult);
                    as.play(collapseBottomBar).with(fadeInResult);
                    as.play(expandBottomBar).after(collapseBottomBar);
                    as.play(countdownProgress).after(expandBottomBar);
                    as.play(countdownButton).after(expandBottomBar);
                    as.start();
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
                    ObjectAnimator fadeInResult = ObjectAnimator.ofFloat(mConsoleResult, "alpha", 0.0f, 0.03f);
                    fadeInResult.setDuration(MEDIUM_ANIM_TIME * 2);

                    View buttomBar = getView().findViewById(R.id.buttonPanel);
                    Animator collapseBottomBar = createExpandCollapseAnimator(buttomBar, false);
                    collapseBottomBar.setDuration(MEDIUM_ANIM_TIME);

                    AnimatorSet as = new AnimatorSet();
                    as.play(fadeInResult);
                    as.play(collapseBottomBar).with(fadeInResult);
                    as.start();
                }
            });
        }

        @SuppressLint("SetTextI18n")
        private void appendText(String text, int type) {
            int color;
            switch (type) {
                case TYPE_ERROR:
                    color = ContextCompat.getColor(getActivity(), R.color.red_500);
                    break;
                case TYPE_OK:
                    color = ContextCompat.getColor(getActivity(), R.color.darker_green);
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
