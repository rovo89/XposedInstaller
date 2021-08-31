package de.robv.android.xposed.installer.repo;

import de.robv.android.xposed.installer.R;

public enum ReleaseType {
	STABLE(R.string.reltype_stable),
	BETA(R.string.reltype_beta),
	EXPERIMENTAL(R.string.reltype_experimental);

	private static final ReleaseType[] sValuesCache = values();
	private final int mTitleId;

	ReleaseType(int titleId) {
		mTitleId = titleId;
	}

	public static ReleaseType fromString(String value) {
		if (value == null) {
			return STABLE;
		}
		switch (value) {
			case "stable":
			default:
				return STABLE;
			case "beta":
				return BETA;
			case "experimental":
				return EXPERIMENTAL;
		}
	}

	public static ReleaseType fromOrdinal(int ordinal) {
		return sValuesCache[ordinal];
	}

	public int getTitleId() {
		return mTitleId;
	}
}
