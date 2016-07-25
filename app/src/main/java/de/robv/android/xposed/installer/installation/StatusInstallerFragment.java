package de.robv.android.xposed.installer.installation;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import de.robv.android.xposed.installer.R;
import de.robv.android.xposed.installer.XposedApp;
import de.robv.android.xposed.installer.util.NavUtil;

public class StatusInstallerFragment extends Fragment {

    public static final File DISABLE_FILE = new File(XposedApp.BASE_DIR + "conf/disabled");
    private static Activity sActivity;
    private static ImageView mErrorIcon;
    private static TextView mErrorTv;
    private static TextView txtKnownIssue;
    private static TextView txtInstallError;
    private static View txtInstallContainer;
    private static ImageView txtInstallIcon;
    private static String installedXposedVersion;
    private static View disableView;
    private static SwitchCompat xposedDisable;

    public static void setError(boolean connectionFailed, boolean noSdks) {
        if (!connectionFailed && !noSdks) return;

        mErrorTv.setVisibility(View.VISIBLE);
        mErrorIcon.setVisibility(View.VISIBLE);
        if (noSdks) {
            mErrorIcon.setImageDrawable(sActivity.getResources().getDrawable(R.drawable.ic_warning_grey));
            mErrorTv.setText(String.format(sActivity.getString(R.string.phone_not_compatible), Build.VERSION.SDK_INT, Build.CPU_ABI));
        }
        if (connectionFailed) {
            mErrorIcon.setImageDrawable(sActivity.getResources().getDrawable(R.drawable.ic_no_connection));
            mErrorTv.setText(sActivity.getString(R.string.loadingError));
        }
    }

    public static void loadXposedStatus() {
        if (Build.VERSION.SDK_INT >= 21) {
            if (installedXposedVersion != null) {
                int installedXposedVersionInt = extractIntPart(installedXposedVersion);
                if (installedXposedVersionInt == XposedApp.getXposedVersion()) {
                    txtInstallError.setText(sActivity.getString(R.string.installed_lollipop, installedXposedVersion));
                    txtInstallError.setTextColor(sActivity.getResources().getColor(R.color.darker_green));
                    txtInstallContainer.setBackgroundColor(sActivity.getResources().getColor(R.color.darker_green));
                    txtInstallIcon.setImageDrawable(sActivity.getResources().getDrawable(R.drawable.ic_check_circle));
                } else {
                    txtInstallError.setText(sActivity.getString(R.string.installed_lollipop_inactive, installedXposedVersion));
                    txtInstallError.setTextColor(sActivity.getResources().getColor(R.color.amber_500));
                    txtInstallContainer.setBackgroundColor(sActivity.getResources().getColor(R.color.amber_500));
                    txtInstallIcon.setImageDrawable(sActivity.getResources().getDrawable(R.drawable.ic_warning));
                }
            } else {
                txtInstallError.setText(R.string.not_installed_no_lollipop);
                txtInstallError.setTextColor(sActivity.getResources().getColor(R.color.warning));
                txtInstallContainer.setBackgroundColor(sActivity.getResources().getColor(R.color.warning));
                txtInstallIcon.setImageDrawable(sActivity.getResources().getDrawable(R.drawable.ic_error));
                xposedDisable.setVisibility(View.GONE);
                disableView.setVisibility(View.GONE);
            }
        } else {
            int installedXposedVersionInt = XposedApp.getXposedVersion();
            if (installedXposedVersionInt != 0) {
                txtInstallError.setText(sActivity.getString(R.string.installed_lollipop, installedXposedVersionInt));
                txtInstallError.setTextColor(sActivity.getResources().getColor(R.color.darker_green));
                txtInstallContainer.setBackgroundColor(sActivity.getResources().getColor(R.color.darker_green));
                txtInstallIcon.setImageDrawable(sActivity.getResources().getDrawable(R.drawable.ic_check_circle));
                if (DISABLE_FILE.exists()) {
                    txtInstallError.setText(sActivity.getString(R.string.installed_lollipop_inactive, installedXposedVersionInt));
                    txtInstallError.setTextColor(sActivity.getResources().getColor(R.color.amber_500));
                    txtInstallContainer.setBackgroundColor(sActivity.getResources().getColor(R.color.amber_500));
                    txtInstallIcon.setImageDrawable(sActivity.getResources().getDrawable(R.drawable.ic_warning));
                }
            } else {
                txtInstallError.setText(sActivity.getString(R.string.not_installed_no_lollipop));
                txtInstallError.setTextColor(sActivity.getResources().getColor(R.color.warning));
                txtInstallContainer.setBackgroundColor(sActivity.getResources().getColor(R.color.warning));
                txtInstallIcon.setImageDrawable(sActivity.getResources().getDrawable(R.drawable.ic_error));
                xposedDisable.setVisibility(View.GONE);
                disableView.setVisibility(View.GONE);
            }
        }

        xposedDisable.setChecked(!DISABLE_FILE.exists());

        xposedDisable.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (DISABLE_FILE.exists()) {
                    DISABLE_FILE.delete();
                    Snackbar.make(xposedDisable, R.string.xposed_on_next_reboot, Snackbar.LENGTH_LONG).show();
                } else {
                    try {
                        DISABLE_FILE.createNewFile();
                        Snackbar.make(xposedDisable, R.string.xposed_off_next_reboot, Snackbar.LENGTH_LONG).show();
                    } catch (IOException e) {
                        Log.e(XposedApp.TAG, "StatusInstallerFragment -> " + e.getMessage());
                    }
                }
            }
        });
    }

    private static boolean checkClassExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static int extractIntPart(String str) {
        int result = 0, length = str.length();
        for (int offset = 0; offset < length; offset++) {
            char c = str.charAt(offset);
            if ('0' <= c && c <= '9')
                result = result * 10 + (c - '0');
            else
                break;
        }
        return result;
    }

    public static String getArch() {
        String info = "";

        try {
            FileReader fr = new FileReader("/proc/cpuinfo");
            BufferedReader br = new BufferedReader(fr);
            String text;
            while ((text = br.readLine()) != null) {
                if (!text.startsWith("processor")) break;
            }
            br.close();
            String[] array = text != null ? text.split(":\\s+", 2) : new String[0];
            if (array.length >= 2) {
                info += array[1] + " ";
            }
        } catch (IOException ignored) {
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            info += Build.SUPPORTED_ABIS[0];
        } else {
            String arch = System.getenv("os.arch");
            if (arch != null) info += arch;
        }
        info += " (";
        if (info.contains("x86")) {
            info += "x86";
        } else if (info.contains("x86_64")) {
            info += "x86_64";
        } else if (info.contains("arm64")) {
            info += "arm64";
        } else {
            info += "arm";
        }
        return info + ")";
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.status_installer, container, false);

        mErrorIcon = (ImageView) v.findViewById(R.id.errorIcon);
        mErrorTv = (TextView) v.findViewById(R.id.errorTv);

        txtKnownIssue = (TextView) v.findViewById(R.id.framework_known_issue);

        txtInstallError = (TextView) v.findViewById(R.id.framework_install_errors);
        txtInstallContainer = v.findViewById(R.id.status_container);
        txtInstallIcon = (ImageView) v.findViewById(R.id.status_icon);

        installedXposedVersion = XposedApp.getXposedProp().get("version");
        disableView = v.findViewById(R.id.disableView);
        xposedDisable = (SwitchCompat) v.findViewById(R.id.disableSwitch);

        TextView androidSdk = (TextView) v.findViewById(R.id.android_version);
        TextView manufacturer = (TextView) v.findViewById(R.id.ic_manufacturer);
        TextView cpu = (TextView) v.findViewById(R.id.cpu);

        loadXposedStatus();

        androidSdk.setText(getString(R.string.android_sdk, getAndroidVersion(), Build.VERSION.RELEASE, Build.VERSION.SDK_INT));
        manufacturer.setText(getUIFramework());
        cpu.setText(getArch());

        refreshKnownIssue();
        return v;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sActivity = getActivity();
    }

    @SuppressLint("StringFormatInvalid")
    private void refreshKnownIssue() {
        String issueName = null;
        String issueLink = null;

        if (new File("/system/framework/core.jar.jex").exists()) {
            issueName = "Aliyun OS";
            issueLink = "http://forum.xda-developers.com/showpost.php?p=52289793&postcount=5";

        } else if (new File("/data/miui/DexspyInstaller.jar").exists() || checkClassExists("miui.dexspy.DexspyInstaller")) {
            issueName = "MIUI/Dexspy";
            issueLink = "http://forum.xda-developers.com/showpost.php?p=52291098&postcount=6";

        } else if (checkClassExists("com.huawei.android.content.res.ResourcesEx")
                || checkClassExists("android.content.res.NubiaResources")) {
            issueName = "Resources subclass";
            issueLink = "http://forum.xda-developers.com/showpost.php?p=52801382&postcount=8";
        }

        if (issueName != null) {
            final String issueLinkFinal = issueLink;
            txtKnownIssue.setText(getString(R.string.install_known_issue, issueName));
            txtKnownIssue.setVisibility(View.VISIBLE);
            txtKnownIssue.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    NavUtil.startURL(getActivity(), issueLinkFinal);
                }
            });
        } else {
            txtKnownIssue.setVisibility(View.GONE);
        }
    }

    private String getAndroidVersion() {
        switch (Build.VERSION.SDK_INT) {
            case 16:
            case 17:
            case 18:
                return "Jelly Bean";
            case 19:
                return "KitKat";
            case 21:
            case 22:
                return "Lollipop";
            case 23:
                return "Marshmallow";
            case 24:
                return "Nougat";
        }
        return "";
    }

    private String getUIFramework() {
        String manufacturer = Character.toUpperCase(Build.MANUFACTURER.charAt(0)) + Build.MANUFACTURER.substring(1);
        if (!Build.BRAND.equals(Build.MANUFACTURER)) {
            manufacturer += " " + Character.toUpperCase(Build.BRAND.charAt(0)) + Build.BRAND.substring(1);
        }
        manufacturer += " " + Build.MODEL + " ";
        if (manufacturer.contains("Samsung")) {
            manufacturer += new File("/system/framework/twframework.jar").exists() ? "(TouchWiz)" : "(AOSP-based ROM)";
        } else if (manufacturer.contains("Xioami")) {
            manufacturer += new File("/system/framework/framework-miui-res.apk").exists() ? "(MIUI)" : "(AOSP-based ROM)";
        }
        return manufacturer;
    }
}
