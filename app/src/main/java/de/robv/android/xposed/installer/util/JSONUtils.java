package de.robv.android.xposed.installer.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JSONUtils {

    public static final String JSON_LINK = "https://raw.githubusercontent.com/DVDAndroid/XposedInstaller/material/app/xposed_list.json";

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

    private static String getLatestVersion() throws IOException {
        String site = getFileContent("http://dl-xda.xposed.info/framework/sdk23/arm/");

        Pattern pattern = Pattern.compile("(href=\")([^\\?\"]*)\\.zip");
        Matcher matcher = pattern.matcher(site);
        String last = "";
        while (matcher.find()) {
            last = matcher.group();
        }
        last = last.replace("href=\"", "");
        String[] file = last.split("-");

        return file[1].replace("v", "");
    }

    public static String listZip() throws IOException {
        String latest = getLatestVersion();
        String newJson = "";
        String[] arch = new String[]{
                "arm",
                "arm64",
                "x86"
        };

        for (int sdk = 21; sdk <= 23; sdk++) {
            for (String a : arch) {
                newJson += installerToString("xposed-v" + latest + "-sdk" + sdk + "-" + a) + ",";
            }
        }

        return newJson;
    }

    private static String installerToString(String filename) {
        String[] array = filename.split("-");

        String version = array[1].replace("v", "");
        int sdk = Integer.parseInt(array[2].replace("sdk", ""));
        String architecture = array[3];
        String link = "http://dl-xda.xposed.info/framework/sdk" + sdk + "/" + architecture + "/" + filename + ".zip";

        return "{\"name\": \"" + filename + "\",\"version\": \"" + version + "\",\"link\": \"" + link + "\",\"sdk\": " + sdk + ",\"architecture\": \"" + architecture + "\"    }";
    }

}
