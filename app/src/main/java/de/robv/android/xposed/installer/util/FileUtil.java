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

import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import java.io.File;

import de.robv.android.xposed.installer.XposedApp;

public class FileUtil {
    /**
     * Set owner and mode of of given {@link File}.
     *
     * @param mode to apply through {@code chmod}
     * @param uid to apply through {@code chown}, or -1 to leave unchanged
     * @param gid to apply through {@code chown}, or -1 to leave unchanged
     * @return 0 on success, otherwise errno.
     */
    public static int setPermissions(File path, int mode, int uid, int gid) {
        return setPermissions(path.getAbsolutePath(), mode, uid, gid);
    }

    /**
     * Set owner and mode of of given path.
     *
     * @param mode to apply through {@code chmod}
     * @param uid to apply through {@code chown}, or -1 to leave unchanged
     * @param gid to apply through {@code chown}, or -1 to leave unchanged
     * @return 0 on success, otherwise errno.
     */
    public static int setPermissions(String path, int mode, int uid, int gid) {
        try {
            Os.chmod(path, mode);
        } catch (ErrnoException e) {
            Log.w(XposedApp.TAG, "Failed to chmod(" + path + "): " + e);
            return e.errno;
        }

        if (uid >= 0 || gid >= 0) {
            try {
                Os.chown(path, uid, gid);
            } catch (ErrnoException e) {
                Log.w(XposedApp.TAG, "Failed to chown(" + path + "): " + e);
                return e.errno;
            }
        }

        return 0;
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
