package de.robv.android.xposed.installer.repo;

import java.io.IOException;
import java.io.InputStream;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.util.Log;
import android.util.Pair;

public class RepoParser {
	public final static String TAG = "XposedRepoParser";
	protected final static String NS = null;
	protected final XmlPullParser parser;

	public RepoParser(InputStream is) throws XmlPullParserException, IOException {
		XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
		parser = factory.newPullParser();
		parser.setInput(is, null);
	}

	public Repository parse() throws XmlPullParserException, IOException {
		parser.nextTag();
		return readRepo();
	}

	protected Repository readRepo() throws XmlPullParserException, IOException {
		parser.require(XmlPullParser.START_TAG, NS, "repository");
		Repository repository = new Repository();

		while (parser.nextTag() == XmlPullParser.START_TAG) {
			String tagName = parser.getName();
			if (tagName.equals("name")) {
				repository.name = parser.nextText(); 
			} else if (tagName.equals("module")) {
				Module module = readModule(repository);
				if (module != null)
					repository.modules.put(module.packageName, module);
			} else {
				skip();
			}
		}

		return repository;
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
				skip();
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

	protected ModuleVersion readModuleVersion(Module module) throws XmlPullParserException, IOException {
		parser.require(XmlPullParser.START_TAG, NS, "version");
		final int startDepth = parser.getDepth();
		ModuleVersion version = new ModuleVersion(module);

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
			} else if (tagName.equals("branch")) {
				version.branch = parser.nextText();
			} else if (tagName.equals("download")) {
				version.downloadLink = parser.nextText();
			} else if (tagName.equals("md5sum")) {
				version.md5sum = parser.nextText();
			} else if (tagName.equals("changelog")) {
				String isHtml = parser.getAttributeValue(NS, "html");
				if (isHtml != null && isHtml.equals("true"))
					version.changelogIsHtml = true;
				version.changelog = parser.nextText();
			} else {
				skip();
			}
		}

		return version;
	}

	protected void skip() throws XmlPullParserException, IOException {
		parser.require(XmlPullParser.START_TAG, null, null);
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