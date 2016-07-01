package de.robv.android.xposed.installer.advanced;

import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;

import de.robv.android.xposed.installer.R;
import de.robv.android.xposed.installer.XposedApp;
import de.robv.android.xposed.installer.util.AssetUtil;

import static de.robv.android.xposed.installer.advanced.BaseAdvancedInstaller.APP_PROCESS_NAME;

public class StatusInstallerFragment extends Fragment {

    public static final File DISABLE_FILE = new File(XposedApp.BASE_DIR + "conf/disabled");
    private static final String BINARIES_FOLDER = AssetUtil.getBinariesFolder();
    private boolean isCompatible;
    private List<String> mCompatibilityErrors = new LinkedList<>();
    private int thisSdkCount = 0;

    public static StatusInstallerFragment newInstance(int thisSdkCount) {
        StatusInstallerFragment myFragment = new StatusInstallerFragment();

        Bundle args = new Bundle();
        args.putInt("count", thisSdkCount);
        myFragment.setArguments(args);

        return myFragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            thisSdkCount = getArguments().getInt("count", Integer.MAX_VALUE);
        }

    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.status_installer, container, false);

        ImageView errorIcon = (ImageView) v.findViewById(R.id.errorIcon);
        TextView errorTv = (TextView) v.findViewById(R.id.errorTv);
        TextView txtInstallError = (TextView) v.findViewById(R.id.framework_install_errors);

        String installedXposedVersion = XposedApp.getXposedProp().get("version");
        View disableView = v.findViewById(R.id.disableView);
        Switch xposedDisable = (Switch) v.findViewById(R.id.disableSwitch);

        if (Build.VERSION.SDK_INT >= 21) {
            if (installedXposedVersion == null) {
                txtInstallError.setText(R.string.not_installed_no_lollipop);
                txtInstallError.setTextColor(getResources().getColor(R.color.warning));
                xposedDisable.setVisibility(View.GONE);
                disableView.setVisibility(View.GONE);
            } else {
                int installedXposedVersionInt = extractIntPart(installedXposedVersion);
                if (installedXposedVersionInt == XposedApp.getXposedVersion()) {
                    txtInstallError.setText(getString(R.string.installed_lollipop, installedXposedVersion));
                    txtInstallError.setTextColor(getResources().getColor(R.color.darker_green));
                } else {
                    txtInstallError.setText(getString(R.string.installed_lollipop_inactive, installedXposedVersion));
                    txtInstallError.setTextColor(getResources().getColor(R.color.warning));
                }
            }
        } else {
            int installedXposedVersionInt = XposedApp.getXposedVersion();
            if (installedXposedVersionInt != 0) {
                txtInstallError.setText(getString(R.string.installed_lollipop, installedXposedVersionInt));
                txtInstallError.setTextColor(getResources().getColor(R.color.darker_green));

                if (DISABLE_FILE.exists()) {
                    txtInstallError.setText(getString(R.string.installed_lollipop_inactive, installedXposedVersionInt));
                    txtInstallError.setTextColor(getResources().getColor(R.color.warning));
                }
            } else {
                txtInstallError.setText(getString(R.string.not_installed_no_lollipop));
                txtInstallError.setTextColor(getResources().getColor(R.color.warning));
                xposedDisable.setVisibility(View.GONE);
                disableView.setVisibility(View.GONE);
            }
        }

        isCompatible = true;
        if (Build.VERSION.SDK_INT == 15) {
            APP_PROCESS_NAME = BINARIES_FOLDER + "app_process_xposed_sdk15";
            isCompatible = checkCompatibility();
        } else if (Build.VERSION.SDK_INT >= 16 && Build.VERSION.SDK_INT <= 18) {
            APP_PROCESS_NAME = BINARIES_FOLDER + "app_process_xposed_sdk16";
            isCompatible = checkCompatibility();
        } else if (Build.VERSION.SDK_INT == 19 || thisSdkCount == 0) {
            APP_PROCESS_NAME = BINARIES_FOLDER + "app_process_xposed_sdk19";
            isCompatible = checkCompatibility();
        }

        if (!isCompatible) {
            errorIcon.setVisibility(View.VISIBLE);
            errorTv.setVisibility(View.VISIBLE);
            errorTv.setText(String.format(getString(R.string.phone_not_compatible), Build.VERSION.SDK_INT, Build.CPU_ABI));
        }

        return v;
    }

    private boolean checkCompatibility() {
        mCompatibilityErrors.clear();
        return checkAppProcessCompatibility();
    }

    private boolean checkAppProcessCompatibility() {
        try {
            if (APP_PROCESS_NAME == null)
                return false;

            File testFile = AssetUtil.writeAssetToCacheFile(APP_PROCESS_NAME, "app_process", 00700);
            if (testFile == null) {
                mCompatibilityErrors.add("could not write app_process to cache");
                return false;
            }

            Process p = Runtime.getRuntime().exec(new String[]{testFile.getAbsolutePath(), "--xposedversion"});

            BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String result = stdout.readLine();
            stdout.close();

            BufferedReader stderr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            String errorLine;
            while ((errorLine = stderr.readLine()) != null) {
                mCompatibilityErrors.add(errorLine);
            }
            stderr.close();

            p.destroy();

            testFile.delete();
            return result != null && result.startsWith("Xposed version: ");
        } catch (IOException e) {
            mCompatibilityErrors.add(e.getMessage());
            return false;
        }
    }

    private int extractIntPart(String str) {
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
}
