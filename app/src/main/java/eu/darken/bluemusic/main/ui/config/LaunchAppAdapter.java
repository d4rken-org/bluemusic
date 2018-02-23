package eu.darken.bluemusic.main.ui.config;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.util.AppTool;

public class LaunchAppAdapter extends BaseAdapter {
    private final Context context;
    private final List<AppTool.Item> apps;

    public LaunchAppAdapter(Context context, List<AppTool.Item> apps) {
        this.context = context;
        this.apps = apps;
    }

    @Override
    public int getCount() {
        return apps.size();
    }

    @Override
    public AppTool.Item getItem(int i) {
        return apps.get(i);
    }

    @Override
    public long getItemId(int i) {
        return apps.get(i).hashCode();
    }

    @SuppressLint("InflateParams")
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.adapter_app_line, null);
            holder = new ViewHolder();
            ButterKnife.bind(holder, convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.bind(getItem(position));
        return convertView;
    }


    static class ViewHolder {
        @BindView(R.id.icon) ImageView icon;
        @BindView(R.id.name) TextView appName;
        @BindView(R.id.pkg) TextView packageName;

        public void bind(AppTool.Item item) {
            appName.setText(item.getAppName());
            packageName.setText(item.getPackageName());
            icon.setImageDrawable(item.getAppIcon());
        }
    }
}
