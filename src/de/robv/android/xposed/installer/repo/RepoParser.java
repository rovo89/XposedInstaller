package de.robv.android.xposed.installer.repo;

import java.io.IOException;
import java.io.InputStream;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.Log;
import android.util.Pair;

public class RepoParser {
	public final static String TAG = "XposedRepoParser";
	protected final static String NS = null;
	protected final XmlPullParser parser;
	protected RepoParserCallback mCallback;
	private boolean mRepoEventTriggered = false;

	public interface RepoParserCallback {
		public void onRepositoryMetadata(Repository repository);
		public void onNewModule(Module module);
		public void onRemoveModule(String packageName);
		public void onCompleted(Repository repository);
	}

	public static void parse(InputStream is, RepoParserCallback callback) throws XmlPullParserException, IOException {
		new RepoParser(is, callback).readRepo();
	}

	protected RepoParser(InputStream is, RepoParserCallback callback) throws XmlPullParserException, IOException {
		XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
		parser = factory.newPullParser();
		parser.setInput(is, null);
		parser.nextTag();
		mCallback = callback;
	}

	protected void readRepo() throws XmlPullParserException, IOException {
		parser.require(XmlPullParser.START_TAG, NS, "repository");
		Repository repository = new Repository();
		repository.isPartial = "true".equals(parser.getAttributeValue(NS, "partial"));
		repository.partialUrl = parser.getAttributeValue(NS, "partial-url");
		repository.version = parser.getAttributeValue(NS, "version");

		while (parser.nextTag() == XmlPullParser.START_TAG) {
			String tagName = parser.getName();
			if (tagName.equals("name")) {
				repository.name = parser.nextText();
			} else if (tagName.equals("module")) {
				triggerRepoEvent(repository);
				Module module = readModule(repository);
				if (module != null)
					mCallback.onNewModule(module);
			} else if (tagName.equals("remove-module")) {
				triggerRepoEvent(repository);
				String packageName = readRemoveModule();
				if (packageName != null)
					mCallback.onRemoveModule(packageName);
			} else {
				skip(true);
			}
		}

		mCallback.onCompleted(repository);
	}

	private void triggerRepoEvent(Repository repository) {
		if (mRepoEventTriggered)
			return;

		mCallback.onRepositoryMetadata(repository);
		mRepoEventTriggered = true;
	}

	protected Module readModule(Repository repository) throws XmlPullParserException, IOException {
		parser.require(XmlPullParser.START_TAG, NS, "module");
		final int startDepth = parser.getDepth();

		Module module = new Module(repository);
		module.packageName = parser.getAttributeValue(NS, "package");
		if (module.packageName == null) {
			logError("no package name defined");
			leave(startDepth);
			return null;
		}

		module.created = parseTimestamp("created");
		module.updated = parseTimestamp("updated");

		while (parser.nextTag() == XmlPullParser.START_TAG) {
			String tagName = parser.getName();
			if (tagName.equals("name")) {
				module.name = parser.nextText();
			} else if (tagName.equals("author")) {
				module.author = parser.nextText();
			} else if (tagName.equals("summary")) {
				module.summary = parser.nextText();
			} else if (tagName.equals("description")) {
				String isHtml = parser.getAttributeValue(NS, "html");
				if (isHtml != null && isHtml.equals("true"))
					module.descriptionIsHtml = true;
				module.description = parser.nextText();
			} else if (tagName.equals("screenshot")) {
				module.screenshots.add(parser.nextText());
			} else if (tagName.equals("moreinfo")) {
				String label = parser.getAttributeValue(NS, "label");
				String role = parser.getAttributeValue(NS, "role");
				String value = parser.nextText();
				module.moreInfo.add(new Pair<String, String>(label, value));

				if (role != null && role.contains("support"))
					module.support = value;
			} else if (tagName.equals("version")) {
				ModuleVersion version = readModuleVersion(module);
				if (version != null)
					module.versions.add(version);
			} else {
				skip(true);
			}
		}

		if (module.name == null) {
			logError("packages need at least a name");
			return null;
		}

		return module;
	}

	private long parseTimestamp(String attName) {
		String value = parser.getAttributeValue(NS, attName);
		if (value == null)
			return -1;
		try {
			return Long.parseLong(value) * 1000L;
		} catch (NumberFormatException ex) {
			return -1;
		}
	}

	public static Spanned parseSimpleHtml(String source) {
		source = source.replaceAll("<li>", "\t\u0095 ");
		source = source.replaceAll("</li>", "<br>");
		Spanned html = Html.fromHtml(source);

		// trim trailing newlines
		int len = html.length();
		int end = len;
		for (int i = len - 1; i >= 0; i--) {
			if (html.charAt(i) != '\n')
				break;
			end = i;
		}

		if (end == len)
			return html;
		else
			return new SpannableStringBuilder(html, 0, end);
	}

	protected ModuleVersion readModuleVersion(Module module) throws XmlPullParserException, IOException {
		parser.require(XmlPullParser.START_TAG, NS, "version");
		final int startDepth = parser.getDepth();
		ModuleVersion version = new ModuleVersion(module);

		version.uploaded = parseTimestamp("uploaded");

		while (parser.nextTag() == XmlPullParser.START_TAG) {
			String tagName = parser.getName();
			if (tagName.equals("name")) {
				version.name = parser.nextText();
			} else if (tagName.equals("code")) {
				try {
					version.code = Integer.parseInt(parser.nextText());
				} catch (NumberFormatException nfe) {
					logError(nfe.getMessage());
					leave(startDepth);
					return null;
				}
			} else if (tagName.equals("reltype")) {
				version.relType = ReleaseType.fromString(parser.nextText());
			} else if (tagName.equals("download")) {
				version.downloadLink = parser.nextText();
			} else if (tagName.equals("md5sum")) {
				version.md5sum = parser.nextText();
			} else if (tagName.equals("changelog")) {
				String isHtml = parser.getAttributeValue(NS, "html");
				if (isHtml != null && isHtml.equals("true"))
					version.changelogIsHtml = true;
				version.changelog = parser.nextText();
			} else if (tagName.equals("branch")) {
				// obsolete
				skip(false);
			} else {
				skip(true);
			}
		}

		return version;
	}

	protected String readRemoveModule() throws XmlPullParserException, IOException {
		parser.require(XmlPullParser.START_TAG, NS, "remove-module");
		final int startDepth = parser.getDepth();

		String packageName = parser.getAttributeValue(NS, "package");
		if (packageName == null) {
			logError("no package name defined");
			leave(startDepth);
			return null;
		}

		return packageName;
	}

	protected void skip(boolean showWarning) throws XmlPullParserException, IOException {
		parser.require(XmlPullParser.START_TAG, null, null);
		if (showWarning)
			Log.w(TAG, "skipping unknown/erronous tag: " + parser.getPositionDescription());
		int level = 1;
		while (level > 0) {
			int eventType = parser.next();
			if (eventType == XmlPullParser.END_TAG) {
				level--;
			} else if (eventType == XmlPullParser.START_TAG) {
				level++;
			}
		}
	}

	protected void leave(int targetDepth) throws XmlPullParserException, IOException {
		Log.w(TAG, "leaving up to level " + targetDepth + ": " + parser.getPositionDescription());
		while (parser.getDepth() > targetDepth) {
			while (parser.next() != XmlPullParser.END_TAG) {
				// do nothing
			}
		}
	}

	protected void logError(String error) {
		Log.e(TAG, parser.getPositionDescription() + ": " + error);
	}
}
