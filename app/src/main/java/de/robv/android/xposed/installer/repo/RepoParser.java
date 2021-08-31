package de.robv.android.xposed.installer.repo;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LevelListDrawable;
import android.os.AsyncTask;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.Log;
import android.util.Pair;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;

import de.robv.android.xposed.installer.R;

public class RepoParser {
    public final static String TAG = "XposedRepoParser";
    protected final static String NS = null;
    protected final XmlPullParser parser;
    protected RepoParserCallback mCallback;
    private boolean mRepoEventTriggered = false;

    protected RepoParser(InputStream is, RepoParserCallback callback) throws XmlPullParserException, IOException {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        parser = factory.newPullParser();
        parser.setInput(is, null);
        parser.nextTag();
        mCallback = callback;
    }

    public static void parse(InputStream is, RepoParserCallback callback) throws XmlPullParserException, IOException {
        new RepoParser(is, callback).readRepo();
    }

    public static Spanned parseSimpleHtml(final Context c, String source, final TextView textView) {
        source = source.replaceAll("<li>", "\t\u0095 ");
        source = source.replaceAll("</li>", "<br>");
        Spanned html = Html.fromHtml(source, new Html.ImageGetter() {
            @Override
            public Drawable getDrawable(String source) {
                LevelListDrawable d = new LevelListDrawable();
                @SuppressWarnings("deprecation")
                Drawable empty = c.getResources().getDrawable(R.drawable.ic_no_image);
                d.addLevel(0, 0, empty);
                assert empty != null;
                d.setBounds(0, 0, empty.getIntrinsicWidth(), empty.getIntrinsicHeight());
                new ImageGetterAsyncTask(c, source, d).execute(textView);

                return d;
            }
        }, null);

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

    protected void readRepo() throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, NS, "repository");
        Repository repository = new Repository();
        repository.isPartial = "true".equals(parser.getAttributeValue(NS, "partial"));
        repository.partialUrl = parser.getAttributeValue(NS, "partial-url");
        repository.version = parser.getAttributeValue(NS, "version");

        while (parser.nextTag() == XmlPullParser.START_TAG) {
            String tagName = parser.getName();
            switch (tagName) {
                case "name":
                    repository.name = parser.nextText();
                    break;
                case "module":
                    triggerRepoEvent(repository);
                    Module module = readModule(repository);
                    if (module != null)
                        mCallback.onNewModule(module);
                    break;
                case "remove-module":
                    triggerRepoEvent(repository);
                    String packageName = readRemoveModule();
                    if (packageName != null)
                        mCallback.onRemoveModule(packageName);
                    break;
                default:
                    skip(true);
                    break;
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
            switch (tagName) {
                case "name":
                    module.name = parser.nextText();
                    break;
                case "author":
                    module.author = parser.nextText();
                    break;
                case "summary":
                    module.summary = parser.nextText();
                    break;
                case "description":
                    String isHtml = parser.getAttributeValue(NS, "html");
                    if (isHtml != null && isHtml.equals("true"))
                        module.descriptionIsHtml = true;
                    module.description = parser.nextText();
                    break;
                case "screenshot":
                    module.screenshots.add(parser.nextText());
                    break;
                case "moreinfo":
                    String label = parser.getAttributeValue(NS, "label");
                    String role = parser.getAttributeValue(NS, "role");
                    String value = parser.nextText();
                    module.moreInfo.add(new Pair<>(label, value));

                    if (role != null && role.contains("support"))
                        module.support = value;
                    break;
                case "version":
                    ModuleVersion version = readModuleVersion(module);
                    if (version != null)
                        module.versions.add(version);
                    break;
                default:
                    skip(true);
                    break;
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

        version.uploaded = parseTimestamp("uploaded");

        while (parser.nextTag() == XmlPullParser.START_TAG) {
            String tagName = parser.getName();
            switch (tagName) {
                case "name":
                    version.name = parser.nextText();
                    break;
                case "code":
                    try {
                        version.code = Integer.parseInt(parser.nextText());
                    } catch (NumberFormatException nfe) {
                        logError(nfe.getMessage());
                        leave(startDepth);
                        return null;
                    }
                    break;
                case "reltype":
                    version.relType = ReleaseType.fromString(parser.nextText());
                    break;
                case "download":
                    version.downloadLink = parser.nextText();
                    break;
                case "md5sum":
                    version.md5sum = parser.nextText();
                    break;
                case "changelog":
                    String isHtml = parser.getAttributeValue(NS, "html");
                    if (isHtml != null && isHtml.equals("true"))
                        version.changelogIsHtml = true;
                    version.changelog = parser.nextText();
                    break;
                case "branch":
                    // obsolete
                    skip(false);
                    break;
                default:
                    skip(true);
                    break;
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
            //noinspection StatementWithEmptyBody
            while (parser.next() != XmlPullParser.END_TAG) {
                // do nothing
            }
        }
    }

    protected void logError(String error) {
        Log.e(TAG, parser.getPositionDescription() + ": " + error);
    }

    public interface RepoParserCallback {
        void onRepositoryMetadata(Repository repository);
        void onNewModule(Module module);
        void onRemoveModule(String packageName);
        void onCompleted(Repository repository);
    }

    static class ImageGetterAsyncTask extends AsyncTask<TextView, Void, Bitmap> {
        private LevelListDrawable levelListDrawable;
        private Context context;
        private String source;
        private TextView t;

        public ImageGetterAsyncTask(Context context, String source, LevelListDrawable levelListDrawable) {
            this.context = context;
            this.source = source;
            this.levelListDrawable = levelListDrawable;
        }

        @Override
        protected Bitmap doInBackground(TextView... params) {
            t = params[0];
            try {
                return Picasso.with(context).load(source).get();
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(final Bitmap bitmap) {
            try {
                Drawable d = new BitmapDrawable(context.getResources(), bitmap);
                Point size = new Point();
                ((Activity) context).getWindowManager().getDefaultDisplay().getSize(size);
                int multiplier = size.x / bitmap.getWidth();
                if (multiplier <= 0) multiplier = 1;
                levelListDrawable.addLevel(1, 1, d);
                levelListDrawable.setBounds(0, 0, bitmap.getWidth() * multiplier, bitmap.getHeight() * multiplier);
                levelListDrawable.setLevel(1);
                t.setText(t.getText());
            } catch (Exception ignored) { /* Like a null bitmap, etc. */
            }
        }
    }

}
