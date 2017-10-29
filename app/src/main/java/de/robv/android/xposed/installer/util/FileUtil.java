/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.robv.android.xposed.installer.util;

import android.util.Log;

import java.io.File;

import de.robv.android.xposed.installer.XposedApp;

public class FileUtil {
    public static boolean setPermissions(String path, int owner, int other) {
        return setPermissions(new File(path), owner, other);
    }

    public static boolean setPermissions(File path, int owner, int other) {
        if (!setPermissions(path, other, false) || !setPermissions(path, owner, true)) {
            Log.w(XposedApp.TAG, "Failed to set permissions " +
                    owner + "/" + other + " for " + path.getPath());
            return false;
        }
        return true;
    }

    private static boolean setPermissions(File path, int perm, boolean ownerOnly) {
        return path.setReadable((perm & 4) != 0, ownerOnly)
            && path.setWritable((perm & 2) != 0, ownerOnly)
            && path.setExecutable((perm & 1) != 0, ownerOnly);
    }

    public static boolean deleteContentsAndDir(File dir) {
        if (deleteContents(dir)) {
            return dir.delete();
        } else {
            return false;
        }
    }

    public static boolean deleteContents(File dir) {
        File[] files = dir.listFiles();
        boolean success = true;
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    success &= deleteContents(file);
                }
                if (!file.delete()) {
                    Log.w(XposedApp.TAG, "Failed to delete " + file);
                    success = false;
                }
            }
        }
        return success;
    }
}
