package de.robv.android.xposed.installer.repo;

import java.io.IOException;
import java.io.InputStream;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.util.Log;

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
        		Module module = readModule();
        		if (module != null)
        			repository.modules.put(module.packageName, module);
        	} else {
        		skip();
        	}
        }
        
        return repository;
	}
	
	protected Module readModule() throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, NS, "module");
        Module module = new Module();
        module.packageName = parser.getAttributeValue(NS, "package");
        if (module.packageName == null) {
        	logError("no package name defined");
        	return null;
        }

        while (parser.nextTag() == XmlPullParser.START_TAG) {
        	String tagName = parser.getName();
        	if (tagName.equals("name")) {
        		module.name = parser.nextText();
        	} else if (tagName.equals("author")) {
        		module.author = parser.nextText();
        	} else if (tagName.equals("contact")) {
        		module.contact = parser.nextText();
        	} else if (tagName.equals("description")) {
        		module.description = parser.nextText();
        	} else if (tagName.equals("screenshot")) {
        		module.screenshots.add(parser.nextText());
        	} else if (tagName.equals("version")) {
        		ModuleVersion version = readModuleVersion();
        		if (version != null) {
        			module.versions.add(version);
        			if (module.latestVersion == null)
        				module.latestVersion = version;
        		}
        	} else {
        		skip();
        	}
        }
        
        return module;
	}
	
	protected ModuleVersion readModuleVersion() throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, NS, "version");
        ModuleVersion version= new ModuleVersion();

        while (parser.nextTag() == XmlPullParser.START_TAG) {
        	String tagName = parser.getName();
        	if (tagName.equals("name")) {
        		version.name = parser.nextText();
        	} else if (tagName.equals("code")) {
        		try {
        			version.code = Integer.parseInt(parser.nextText());
        		} catch (NumberFormatException nfe) {
        			logError(nfe.getMessage());
        			return null;
        		}
        	} else if (tagName.equals("download")) {
        		version.downloadLink = parser.nextText();
        	} else if (tagName.equals("md5sum")) {
        		version.md5sum = parser.nextText();
        	} else if (tagName.equals("changelog")) {
        		version.changelog.add(parser.nextText());
        	} else {
        		skip();
        	}
        }
        
        return version;
	}

	protected void skip() throws XmlPullParserException, IOException {
		parser.require(XmlPullParser.START_TAG, null, null);
		Log.w(TAG, "skipping unknown tag: " + parser.getPositionDescription());
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
	
	protected void logError(String error) {
		Log.e(TAG, parser.getPositionDescription() + ": " + error);
	}
}