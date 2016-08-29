package de.robv.android.xposed.installer.installation;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.MaterialDialog.Builder;
import com.afollestad.materialdialogs.MaterialDialog.ListCallback;
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
        TextView tv = new TextView(getContext());
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
                if (online.uninstaller == uninstaller && online.current) {
                    addZipView(inflater, container, online, local != null);
                }
            } else if (local != null) {
                if (local.uninstaller == uninstaller) {
                    addZipView(inflater, container, local, true);
                }
            }
        }
    }

    public void addZipView(LayoutInflater inflater, ViewGroup container, final FrameworkZip zip, boolean hasLocal) {
        View view = inflater.inflate(R.layout.list_item_framework_zip, container, false);
        TextView tvTitle = (TextView) view.findViewById(R.id.title);
        tvTitle.setText(zip.title);
        if (zip.uninstaller) {
            tvTitle.setTextColor(Color.RED);
        }
        if (!hasLocal) {
            view.findViewById(R.id.framework_zip_downloaded).setVisibility(View.GONE);
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

        final MaterialSimpleListAdapter adapter = new MaterialSimpleListAdapter(context);

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
                .adapter(adapter, new ListCallback() {
                    @Override
                    public void onSelection(MaterialDialog dialog, View itemView, int which, CharSequence text) {
                        int numHeaders = dialog.getListView().getHeaderViewsCount();
                        if (which < numHeaders) {
                            // TODO if we really want to add a description, this could expand it
                            return;
                        }

                        dialog.dismiss();
                        MaterialSimpleListItem item = adapter.getItem(which - numHeaders);
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
                })
                .build();

        TextView header = new TextView(context);
        header.setText("Here we could add a description, changelog, ...");
        header.setTextSize(16);
        header.setPadding(20, 0, 20, 0);
        dialog.getListView().addHeaderView(header);

        dialog.show();
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
}
