package de.robv.android.xposed.installer.installation;

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.MaterialDialog.Builder;
import com.afollestad.materialdialogs.simplelist.MaterialSimpleListAdapter;
import com.afollestad.materialdialogs.simplelist.MaterialSimpleListItem;

import java.io.File;
import java.util.Set;

import de.robv.android.xposed.installer.R;
import de.robv.android.xposed.installer.XposedApp;
import de.robv.android.xposed.installer.util.DownloadsUtil;
import de.robv.android.xposed.installer.util.DownloadsUtil.DownloadFinishedCallback;
import de.robv.android.xposed.installer.util.DownloadsUtil.DownloadInfo;
import de.robv.android.xposed.installer.util.FrameworkZips;
import de.robv.android.xposed.installer.util.FrameworkZips.FrameworkZip;
import de.robv.android.xposed.installer.util.FrameworkZips.LocalFrameworkZip;
import de.robv.android.xposed.installer.util.FrameworkZips.OnlineFrameworkZip;
import de.robv.android.xposed.installer.util.RunnableWithParam;

public class FrameworkDownloader extends Fragment {
    private boolean mShowOutdated = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mShowOutdated = XposedApp.getPreferences().getBoolean("framework_download_show_outdated", false);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.framework_download, container, false);
        triggerRefresh(true, true);
        return v;
    }

    private void triggerRefresh(final boolean online, final boolean local) {
        new Thread("FrameworkZipsRefresh") {
            @Override
            public void run() {
                if (online) {
                    XposedApp.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showLoading();
                        }
                    });
                    FrameworkZips.refreshOnline();
                }
                if (local) {
                    FrameworkZips.refreshLocal();
                }
                XposedApp.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        refreshZipViews();
                    }
                });
            }
        }.start();
    }

    @UiThread
    private void showLoading() {
        LayoutInflater inflater = getActivity().getLayoutInflater();
        LinearLayout zips = (LinearLayout) getView().findViewById(R.id.zips);

        // TODO add a proper layout, spinner or something like that
        TextView tv = new TextView(getActivity());
        tv.setText("loading...");
        tv.setTextSize(20);

        zips.removeAllViews();
        zips.addView(tv);
    }

    @UiThread
    private void refreshZipViews() {
        LayoutInflater inflater = getActivity().getLayoutInflater();
        LinearLayout zips = (LinearLayout) getView().findViewById(R.id.zips);

        zips.removeAllViews();
        synchronized (FrameworkZip.class) {
            Set<String> allTitles = FrameworkZips.getAllTitles();
            // TODO handle "no ZIPs" case
            addZipViews(inflater, zips, false, allTitles);
            addZipViews(inflater, zips, true, allTitles);
        }
    }

    private void addZipViews(LayoutInflater inflater, ViewGroup container, boolean uninstaller, Set<String> allTitles) {
        for (String title : allTitles) {
            OnlineFrameworkZip online = FrameworkZips.getOnline(title);
            LocalFrameworkZip local = FrameworkZips.getLocal(title);
            if (online != null) {
                if (online.uninstaller == uninstaller && (mShowOutdated || online.current)) {
                    addZipView(inflater, container, online, true, local != null);
                }
            } else if (local != null) {
                if (local.uninstaller == uninstaller) {
                    addZipView(inflater, container, local, false, true);
                }
            }
        }
    }

    public void addZipView(LayoutInflater inflater, ViewGroup container, final FrameworkZip zip, boolean hasOnline, boolean hasLocal) {
        View view = inflater.inflate(R.layout.list_item_framework_zip, container, false);

        TextView tvTitle = (TextView) view.findViewById(R.id.title);
        tvTitle.setText(zip.title);

        ImageView ivStatus = (ImageView) view.findViewById(R.id.framework_zip_status);
        if (!hasLocal) {
            ivStatus.setImageResource(R.drawable.ic_cloud);
        } else if (hasOnline) {
            ivStatus.setImageResource(R.drawable.ic_cloud_download);
        } else {
            ivStatus.setImageResource(R.drawable.ic_cloud_off);
        }

        if (zip instanceof OnlineFrameworkZip && !((OnlineFrameworkZip) zip).current) {
            int gray = Color.parseColor("#A0A0A0");
            tvTitle.setTextColor(gray);
            ivStatus.setColorFilter(gray);
        } else if (zip.uninstaller) {
            tvTitle.setTextColor(Color.RED);
        }

        view.setClickable(true);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showActionDialog(getActivity(), zip.title);
            }
        });
        container.addView(view);
    }


    private void showActionDialog(final Context context, final String title) {
        final long ACTION_INSTALL = 0;
        final long ACTION_INSTALL_RECOVERY = 1;
        final long ACTION_SAVE = 2;
        final long ACTION_DELETE = 3;

        // TODO this is a work-around as the callback can only access final variables
        // see also: https://github.com/afollestad/material-dialogs/issues/1175
        final MaterialDialog[] dialogRef = new MaterialDialog[1];

        final MaterialSimpleListAdapter adapter = new MaterialSimpleListAdapter(new MaterialSimpleListAdapter.Callback() {
            @Override
            public void onMaterialListItemSelected(int index, MaterialSimpleListItem item) {
                MaterialDialog dialog = dialogRef[0];
                dialog.dismiss();
                long action = item.getId();

                // Handle delete simple actions.
                if (action == ACTION_DELETE) {
                    FrameworkZips.delete(context, title);
                    triggerRefresh(false, true);
                    return;
                }

                // Handle actions that need a download first.
                RunnableWithParam<File> runAfterDownload = null;
                if (action == ACTION_INSTALL) {
                    runAfterDownload = new RunnableWithParam<File>() {
                        @Override
                        public void run(File file) {
                            flash(context, new FlashDirectly(file, false));
                        }
                    };
                } else if (action == ACTION_INSTALL_RECOVERY) {
                    runAfterDownload = new RunnableWithParam<File>() {
                        @Override
                        public void run(File file) {
                            flash(context, new FlashRecoveryAuto(file));
                        }
                    };
                } else if (action == ACTION_SAVE) {
                    runAfterDownload = new RunnableWithParam<File>() {
                        @Override
                        public void run(File file) {
                            saveTo(context, file);
                        }
                    };
                }

                LocalFrameworkZip local = FrameworkZips.getLocal(title);
                if (local != null) {
                    runAfterDownload.run(local.path);
                } else {
                    download(context, title, runAfterDownload);
                }
            }
        });

        // TODO Adjust texts for uninstaller (e.g. "execute")
        adapter.add(new MaterialSimpleListItem.Builder(context)
                .content("Install")
                .id(ACTION_INSTALL)
                .icon(R.drawable.ic_check_circle)
                .build());

        adapter.add(new MaterialSimpleListItem.Builder(context)
                .content("Install via recovery")
                .id(ACTION_INSTALL_RECOVERY)
                .icon(R.drawable.ic_check_circle)
                .build());

        adapter.add(new MaterialSimpleListItem.Builder(context)
                .content("Save to...")
                .id(ACTION_SAVE)
                .icon(R.drawable.ic_save)
                .build());

        if (FrameworkZips.hasLocal(title)) {
            adapter.add(new MaterialSimpleListItem.Builder(context)
                    .content("Delete downloaded file")
                    .id(ACTION_DELETE)
                    .icon(R.drawable.ic_delete)
                    .build());
        }

        MaterialDialog dialog = new Builder(context)
                .title(title)
                .adapter(adapter, null)
                .build();

        dialog.show();
        dialogRef[0] = dialog;
    }

    private void download(Context context, String title, final RunnableWithParam<File> callback) {
        OnlineFrameworkZip zip = FrameworkZips.getOnline(title);
        new DownloadsUtil.Builder(context)
                .setTitle(zip.title)
                .setUrl(zip.url)
                .setDestinationFromUrl(DownloadsUtil.DOWNLOAD_FRAMEWORK)
                .setCallback(new DownloadFinishedCallback() {
                    @Override
                    public void onDownloadFinished(Context context, DownloadInfo info) {
                        triggerRefresh(false, true);
                        callback.run(new File(info.localFilename));
                    }
                })
                .setMimeType(DownloadsUtil.MIME_TYPES.ZIP)
                .setDialog(true)
                .download();
    }

    private static void flash(Context context, Flashable flashable) {
        Intent install = new Intent(context, InstallationActivity.class);
        install.putExtra(Flashable.KEY, flashable);
        context.startActivity(install);
    }

    private static void installRecovery(Context context, File file) {
        Toast.makeText(context, "Not implemented yet", Toast.LENGTH_SHORT).show();
    }

    private static void saveTo(Context context, File file) {
        Toast.makeText(context, "Not implemented yet", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_framework_download, menu);
        menu.findItem(R.id.show_outdated).setChecked(mShowOutdated);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.show_outdated) {
            mShowOutdated = !item.isChecked();
            XposedApp.getPreferences().edit().putBoolean("framework_download_show_outdated", mShowOutdated).apply();
            item.setChecked(mShowOutdated);
            refreshZipViews();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
