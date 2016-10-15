package de.robv.android.xposed.installer.installation;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import java.io.File;

import de.robv.android.xposed.installer.util.FrameworkZips;
import de.robv.android.xposed.installer.util.RootUtil;

public abstract class Flashable implements Parcelable {
    public static final String KEY = "flash";

    protected final File mZipPath;
    protected final FrameworkZips.Type mType;
    protected final String mTitle;

    public Flashable(File zipPath, FrameworkZips.Type type, String title) {
        mZipPath = zipPath;
        mType = type;
        mTitle = title;
    }

    protected Flashable(Parcel in) {
        mZipPath = (File) in.readSerializable();
        mType = (FrameworkZips.Type) in.readSerializable();
        mTitle = in.readString();
    }

    public abstract void flash(Context context, FlashCallback callback);

    public FrameworkZips.Type getType() {
        return mType;
    }

    public String getTitle() {
        return mTitle;
    }

    public RootUtil.RebootMode getRebootMode() {
        return RootUtil.RebootMode.NORMAL;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeSerializable(mZipPath);
        dest.writeSerializable(mType);
        dest.writeString(mTitle);
    }
}
