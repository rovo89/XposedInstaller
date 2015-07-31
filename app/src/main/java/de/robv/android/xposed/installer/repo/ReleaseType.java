package de.robv.android.xposed.installer.repo;

import de.robv.android.xposed.installer.R;

public enum ReleaseType {
	STABLE (R.string.reltype_stable, R.string.reltype_stable_summary),
	BETA (R.string.reltype_beta, R.string.reltype_beta_summary),
	EXPERIMENTAL (R.string.reltype_experimental, R.string.reltype_experimental_summary);

	private static final ReleaseType[] sValuesCache = values();

	public static ReleaseType fromString(String value) {
		if (value == null || value.equals("stable"))
			return STABLE;
		else if (value.equals("beta"))
			return BETA;
		else if (value.equals("experimental"))
			return EXPERIMENTAL;
		else
			return STABLE;
	}

	public static ReleaseType fromOrdinal(int ordinal) {
		return sValuesCache[ordinal];
	}

	private final int mTitleId;
	private final int mSummaryId;

	private ReleaseType(int titleId, int summaryId) {
		mTitleId = titleId;
		mSummaryId = summaryId;
	}

	public int getTitleId() {
		return mTitleId;
	}

	public int getSummaryId() {
		return mSummaryId;
	}
}
