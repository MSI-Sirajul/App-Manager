package com.app.manager;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.ArrayList;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class MainActivity extends Activity {

    private static final int REQ_UNINSTALL = 9001;

    private PackageManager pm;
    private final List<ApplicationInfo> installedApps = new ArrayList<>();
    private final List<ApplicationInfo> systemApps = new ArrayList<>();

    private List<ApplicationInfo> visibleApps = new ArrayList<>(); // current tab list passed to adapter

    private ListView listView;
    private EditText searchBar;
    private TextView tabInstalled, tabSystem;
    private ImageView topAppIcon, topDeleteIcon, topListIcon;

    private AppAdapter adapter;

    // selection and action state
    private boolean selectionMode = false;
    private final Set<String> selectedPkgs = new HashSet<>();
    private final Queue<String> uninstallQueue = new ArrayDeque<>();

    // which tab is active: true = installed, false = system
    private boolean showingInstalled = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        pm = getPackageManager();

        // Root layout
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));
        root.setBackgroundColor(Color.WHITE);

        // Title bar
        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setPadding(dp(8), dp(10), dp(8), dp(10));
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setBackgroundColor(Color.parseColor("#263238"));

        topAppIcon = new ImageView(this);
        try {
            Drawable ic = pm.getApplicationIcon(getPackageName());
            topAppIcon.setImageDrawable(ic);
        } catch (Exception ignored) {}
        titleBar.addView(topAppIcon, new LinearLayout.LayoutParams(dp(32), dp(32)));

        TextView title = new TextView(this);
        title.setText("App Manager");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18f);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.leftMargin = dp(12);
        titleBar.addView(title, titleLp);

        topDeleteIcon = new ImageView(this);
        topDeleteIcon.setImageResource(R.mipmap.delete);
        topDeleteIcon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        topDeleteIcon.setVisibility(View.GONE);
        topDeleteIcon.setOnClickListener(v -> startBatchUninstall());
        titleBar.addView(topDeleteIcon, new LinearLayout.LayoutParams(dp(36), dp(36)));

        topListIcon = new ImageView(this);
        topListIcon.setImageResource(R.mipmap.list_ic);
        topListIcon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        topListIcon.setOnClickListener(v -> Toast.makeText(this, "List icon (no-op)", Toast.LENGTH_SHORT).show());
        titleBar.addView(topListIcon, new LinearLayout.LayoutParams(dp(36), dp(36)));

        root.addView(titleBar);

        // Search bar
        searchBar = new EditText(this);
        searchBar.setHint("Search apps...");
        searchBar.setPadding(dp(10), dp(10), dp(10), dp(10));
        searchBar.setBackgroundResource(android.R.drawable.editbox_background);
        root.addView(searchBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        // Tabs
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);

        tabInstalled = makeTab("Installed Apps");
        tabSystem = makeTab("System Apps");
        tabs.addView(tabInstalled, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tabs.addView(tabSystem, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(tabs);

        // ListView
        listView = new ListView(this);
        root.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        setContentView(root);

        // load apps
        loadAllApps();

        // adapter with default installed apps
        showingInstalled = true;
        visibleApps.clear();
        visibleApps.addAll(installedApps);
        adapter = new AppAdapter(this, visibleApps);
        listView.setAdapter(adapter);
        highlightActiveTab();

        // tab clicks
        tabInstalled.setOnClickListener(v -> switchToInstalled());
        tabSystem.setOnClickListener(v -> switchToSystem());

        // search
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                if (adapter != null) adapter.getFilter().filter(s.toString());
            }
        });
    }

    private TextView makeTab(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        t.setGravity(Gravity.CENTER);
        t.setTextSize(14f);
        t.setBackgroundColor(Color.TRANSPARENT);
        return t;
    }

    private void loadAllApps() {
        installedApps.clear();
        systemApps.clear();
        List<ApplicationInfo> all = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo ai : all) {
            boolean isSystem = (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
            if (isSystem) systemApps.add(ai);
            else installedApps.add(ai);
        }
        Comparator<ApplicationInfo> cmp = new Comparator<ApplicationInfo>() {
            @Override public int compare(ApplicationInfo a, ApplicationInfo b) {
                String la = String.valueOf(a.loadLabel(pm));
                String lb = String.valueOf(b.loadLabel(pm));
                return la.compareToIgnoreCase(lb);
            }
        };
        Collections.sort(installedApps, cmp);
        Collections.sort(systemApps, cmp);
    }

    private void switchToInstalled() {
        showingInstalled = true;
        loadAllApps();
        visibleApps.clear();
        visibleApps.addAll(installedApps);
        if (adapter != null) adapter.updateAppList(visibleApps);
        highlightActiveTab();
        exitSelectionMode();
    }

    private void switchToSystem() {
        showingInstalled = false;
        loadAllApps();
        visibleApps.clear();
        visibleApps.addAll(systemApps);
        if (adapter != null) adapter.updateAppList(visibleApps);
        highlightActiveTab();
        exitSelectionMode();
    }

    private void highlightActiveTab() {
        tabInstalled.setBackgroundColor(showingInstalled ? Color.LTGRAY : Color.TRANSPARENT);
        tabSystem.setBackgroundColor(!showingInstalled ? Color.LTGRAY : Color.TRANSPARENT);
    }

    private void enterSelectionMode() {
        selectionMode = true;
        topDeleteIcon.setVisibility(selectedPkgs.isEmpty() ? View.GONE : View.VISIBLE);
        if (adapter != null) {
            adapter.clearActionShown();
            adapter.notifyDataSetChanged();
        }
    }

    private void exitSelectionMode() {
        selectionMode = false;
        selectedPkgs.clear();
        topDeleteIcon.setVisibility(View.GONE);
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void startBatchUninstall() {
        if (selectedPkgs.isEmpty()) {
            Toast.makeText(this, "No apps selected", Toast.LENGTH_SHORT).show();
            return;
        }
        uninstallQueue.clear();
        uninstallQueue.addAll(selectedPkgs);
        topDeleteIcon.setVisibility(View.GONE);
        launchNextUninstall();
    }

    private void launchNextUninstall() {
        if (uninstallQueue.isEmpty()) {
            exitSelectionMode();
            loadAllApps();
            // refresh current view
            if (showingInstalled) {
                visibleApps.clear();
                visibleApps.addAll(installedApps);
            } else {
                visibleApps.clear();
                visibleApps.addAll(systemApps);
            }
            if (adapter != null) adapter.updateAppList(visibleApps);
            return;
        }
        String pkg = uninstallQueue.poll();
        try {
            Intent i = new Intent(Intent.ACTION_DELETE);
            i.setData(Uri.parse("package:" + pkg));
            startActivityForResult(i, REQ_UNINSTALL);
        } catch (Exception e) {
            // skip and continue
            launchNextUninstall();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_UNINSTALL) {
            // refresh and continue
            loadAllApps();
            if (showingInstalled) {
                visibleApps.clear();
                visibleApps.addAll(installedApps);
            } else {
                visibleApps.clear();
                visibleApps.addAll(systemApps);
            }
            if (adapter != null) adapter.updateAppList(visibleApps);
            launchNextUninstall();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    // ===== Adapter =====
    private static class ViewHolder {
        ImageView icon;
        TextView title;
        TextView subtitle;
        ImageView action;
        ImageView del;
        ImageView info;
        ImageView select;
    }

    private class AppAdapter extends BaseAdapter implements Filterable {
        private final Context ctx;
        private final PackageManager pkgManager;
        private final List<ApplicationInfo> original = new ArrayList<>();
        private final List<ApplicationInfo> filtered = new ArrayList<>();
        private String actionShownPkg = null;

        AppAdapter(Context c, List<ApplicationInfo> initial) {
            ctx = c;
            pkgManager = c.getPackageManager();
            updateAppList(initial);
        }

        void updateAppList(List<ApplicationInfo> list) {
            original.clear();
            filtered.clear();
            if (list != null) {
                original.addAll(list);
                filtered.addAll(list);
            }
            actionShownPkg = null;
            notifyDataSetChanged();
        }

        void clearActionShown() { actionShownPkg = null; }

        @Override public int getCount() { return filtered.size(); }
        @Override public Object getItem(int position) { return filtered.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            final ViewHolder h;
            if (convertView == null) {
                h = new ViewHolder();
                LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(dp(12), dp(10), dp(12), dp(10));
                row.setGravity(Gravity.CENTER_VERTICAL);

                h.icon = new ImageView(ctx);
                row.addView(h.icon, new LinearLayout.LayoutParams(dp(48), dp(48)));

                LinearLayout texts = new LinearLayout(ctx);
                texts.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                tLp.leftMargin = dp(10);
                row.addView(texts, tLp);

                h.title = new TextView(ctx);
                h.title.setTextSize(16f);
                texts.addView(h.title);

                h.subtitle = new TextView(ctx);
                h.subtitle.setTextSize(12f);
                h.subtitle.setTextColor(Color.GRAY);
                texts.addView(h.subtitle);

                h.action = new ImageView(ctx);
                h.action.setImageResource(R.mipmap.action);
                LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(dp(36), dp(36));
                aLp.leftMargin = dp(6);
                row.addView(h.action, aLp);

                h.del = new ImageView(ctx);
                h.del.setImageResource(R.mipmap.delete);
                row.addView(h.del, aLp);
                h.del.setVisibility(View.GONE);

                h.info = new ImageView(ctx);
                h.info.setImageResource(R.mipmap.info);
                LinearLayout.LayoutParams iLp = new LinearLayout.LayoutParams(dp(36), dp(36));
                iLp.leftMargin = dp(6);
                row.addView(h.info, iLp);
                h.info.setVisibility(View.GONE);

                h.select = new ImageView(ctx);
                h.select.setImageResource(R.mipmap.select);
                row.addView(h.select, aLp);
                h.select.setVisibility(View.GONE);

                convertView = row;
                convertView.setTag(h);
            } else {
                h = (ViewHolder) convertView.getTag();
            }

            final ApplicationInfo ai = filtered.get(position);
            final String pkg = ai.packageName;
            h.title.setText(String.valueOf(ai.loadLabel(pkgManager)));
            h.subtitle.setText(pkg + ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0 ? " • system" : ""));
            try { h.icon.setImageDrawable(ai.loadIcon(pkgManager)); } catch (Exception ignored) {}

            // selection icon visibility
            h.select.setVisibility(selectionMode && selectedPkgs.contains(pkg) ? View.VISIBLE : View.GONE);

            // action vs del/info visible
            if (actionShownPkg != null && actionShownPkg.equals(pkg)) {
                h.action.setVisibility(View.GONE);
                h.del.setVisibility(View.VISIBLE);
                h.info.setVisibility(View.VISIBLE);
            } else {
                h.action.setVisibility(View.VISIBLE);
                h.del.setVisibility(View.GONE);
                h.info.setVisibility(View.GONE);
            }

            h.action.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // toggle action panel; ensure only one shown
                    if (actionShownPkg != null && actionShownPkg.equals(pkg)) {
                        actionShownPkg = null;
                    } else {
                        actionShownPkg = pkg;
                        // leave selection mode when showing action
                        selectionMode = false;
                        selectedPkgs.clear();
                        topDeleteIcon.setVisibility(View.GONE);
                    }
                    notifyDataSetChanged();
                }
            });

            h.del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // single uninstall
                    try {
                        Intent i = new Intent(Intent.ACTION_DELETE);
                        i.setData(Uri.parse("package:" + pkg));
                        startActivityForResult(i, REQ_UNINSTALL);
                    } catch (Exception e) {
                        Toast.makeText(ctx, "Cannot uninstall", Toast.LENGTH_SHORT).show();
                        // collapse panel
                        actionShownPkg = null;
                        notifyDataSetChanged();
                    }
                }
            });

            h.info.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        i.setData(Uri.parse("package:" + pkg));
                        startActivity(i);
                    } catch (Exception e) {
                        Toast.makeText(ctx, "Cannot open app info", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            // long click enters selection mode and selects item
            convertView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    selectionMode = true;
                    selectedPkgs.add(pkg);
                    topDeleteIcon.setVisibility(selectedPkgs.isEmpty() ? View.GONE : View.VISIBLE);
                    actionShownPkg = null;
                    notifyDataSetChanged();
                    return true;
                }
            });

            // click toggles selection if in selection mode; otherwise launch app
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (selectionMode) {
                        if (selectedPkgs.contains(pkg)) selectedPkgs.remove(pkg);
                        else selectedPkgs.add(pkg);
                        if (selectedPkgs.isEmpty()) {
                            selectionMode = false;
                            topDeleteIcon.setVisibility(View.GONE);
                        } else {
                            topDeleteIcon.setVisibility(View.VISIBLE);
                        }
                        notifyDataSetChanged();
                    } else {
                        Intent launch = pkgManager.getLaunchIntentForPackage(pkg);
                        if (launch != null) startActivity(launch);
                        else Toast.makeText(ctx, "Cannot launch this app", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            // fade animation for del/info
            if (actionShownPkg != null && actionShownPkg.equals(pkg)) {
                AlphaAnimation a = new AlphaAnimation(0f, 1f);
                a.setDuration(180);
                h.del.startAnimation(a);
                h.info.startAnimation(a);
            }

            return convertView;
        }

        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults res = new FilterResults();
                    List<ApplicationInfo> out = new ArrayList<>();
                    if (TextUtils.isEmpty(constraint)) {
                        out.addAll(original);
                    } else {
                        String q = constraint.toString().toLowerCase();
                        for (ApplicationInfo a : original) {
                            String label = String.valueOf(a.loadLabel(pkgManager)).toLowerCase();
                            String pkg = a.packageName.toLowerCase();
                            if (label.contains(q) || pkg.contains(q)) out.add(a);
                        }
                    }
                    res.values = out;
                    res.count = out.size();
                    return res;
                }

                @SuppressWarnings("unchecked")
                @Override protected void publishResults(CharSequence constraint, FilterResults results) {
                    filtered.clear();
                    if (results.values != null) filtered.addAll((List<ApplicationInfo>) results.values);
                    notifyDataSetChanged();
                }
            };
        }
    }
}
