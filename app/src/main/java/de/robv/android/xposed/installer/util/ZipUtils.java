package de.robv.android.xposed.installer.util;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author dvdandroid
 */
public class ZipUtils {

    private static String sLatestVersion;
    private static ArrayList<String> sAvailableSdks;
    private static ArrayList<String> sAvailableSdksSystemless;
    private static String[] ARCHS = new String[]{
            "arm",
            "arm64",
            "x86"
    };

    public static void init() throws IOException {
        getLatestVersion(); // Get the latest version of the framework (eg: 86)
        getAvailableSdk(); // Load all available SDKs (eg: [21, 22, 23]
        getAvailableSdkSystemless();
    }

    public static List<XposedZip.Installer> getInstallers() throws IOException {
        List<XposedZip.Installer> zips = new ArrayList<>();

        zips.addAll(getInstallers(false));
        if (sAvailableSdksSystemless.size() != 0)
            zips.addAll(getInstallers());

        return zips;
    }

    private static List<XposedZip.Installer> getInstallers(boolean systemless) throws IOException {
        List<XposedZip.Installer> zips = new ArrayList<>();

        if (systemless) {
            sLatestVersion += "-systemless";
            /*
                If checking for systemless version, add a "-systemless" string
                eg: xposed-v86-systemless-sdk23-arm.zip
             */
        }

        List<String> sdks = systemless ? sAvailableSdksSystemless : sAvailableSdks;

        for (String sdk : sdks) { // Foreach SDKs loaded previously,
            for (String arch : ARCHS) { // And foreach architecture
                String name = "xposed-v" + sLatestVersion + "-sdk" + sdk + "-" + arch; // Generate the name eg: xposed-v86-sdk23-arm.zip or xposed-v86-systemless-sdk23-arm.zip

                if (systemless) sdk += "/systemless";
                /*
                    If the zip is systemless-support, then the url is:
                    eg: http://dl-xda.xposed.info/framework/sdk23/systemless/arm/xposed-v86-systemless-sdk23-arm.zip
                    else
                    eg: http://dl-xda.xposed.info/framework/sdk23/arm/xposed-v86-sdk23-arm.zip
                 */
                String link = "http://dl-xda.xposed.info/framework/sdk" + sdk + "/" + arch + "/" + name + ".zip";

                zips.add(new XposedZip.Installer(link, name, arch, sdk, sLatestVersion));
            }
        }

        return zips;
    }

    public static List<XposedZip.Uninstaller> getUninstallers(Context context) throws IOException, java.text.ParseException {
        String site = getFileContent("http://dl-xda.xposed.info/framework/uninstaller/");
        List<XposedZip.Uninstaller> listUninstaller = new ArrayList<>();

        Pattern pattern = Pattern.compile("(href=\")([^\\?\"]*)\\.zip");
        Matcher matcher = pattern.matcher(site);
        String filename;
        int counter = 0;
        while (matcher.find()) {
            counter++;
            if (counter % 2 != 0) continue;

            filename = matcher.group();
            filename = filename.replace("href=\"", "");
            filename = filename.replace(".zip", "");

            String[] array = filename.split("-");

            String date = array[2];
            String architecture = array[3];
            String link = "http://dl-xda.xposed.info/framework/uninstaller/" + filename + ".zip";

            listUninstaller.add(new XposedZip.Uninstaller(context, link, filename, architecture, date));
        }

        return listUninstaller;
    }

    private static void getAvailableSdk() throws IOException {
        String site = getFileContent("http://dl-xda.xposed.info/framework/");

        Pattern pattern = Pattern.compile("(href=\"sdk)([^\\?\"]*)");
        Matcher matcher = pattern.matcher(site);
        sAvailableSdks = new ArrayList<>();

        while (matcher.find()) {
            String sdk = matcher.group();
            sdk = sdk.replace("href=\"", "");
            sdk = sdk.replace("sdk", "");
            sdk = sdk.replace("/", "");

            sAvailableSdks.add(sdk);
        }
    }

    private static void getAvailableSdkSystemless() throws IOException {
        String baseUrl = "http://dl-xda.xposed.info/framework/sdk%s/arm/systemless";
        sAvailableSdksSystemless = new ArrayList<>();

        for (String sdk : sAvailableSdks) {
            if (!is404(String.format(baseUrl, sdk))) {
                sAvailableSdksSystemless.add(sdk);
            }
        }
    }

    private static boolean is404(String link) throws IOException {
        URL url = new URL(link);
        HttpURLConnection huc = (HttpURLConnection) url.openConnection();
        int responseCode = huc.getResponseCode();

        return responseCode == 404;
    }

    private static void getLatestVersion() throws IOException {
        String site = getFileContent("http://dl-xda.xposed.info/framework/sdk23/arm/");

        Pattern pattern = Pattern.compile("(href=\")([^\\?\"]*)\\.zip");
        Matcher matcher = pattern.matcher(site);
        String last = "";
        while (matcher.find()) {
            last = matcher.group();
        }
        last = last.replace("href=\"", "");
        String[] file = last.split("-");

        sLatestVersion = file[1].replace("v", "");
    }

    public static String getFileContent(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("GET");
        c.setInstanceFollowRedirects(false);
        c.setDoOutput(false);
        c.connect();

        BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }
        br.close();

        return sb.toString();
    }

}
