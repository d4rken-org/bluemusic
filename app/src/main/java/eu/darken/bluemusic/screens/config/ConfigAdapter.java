package eu.darken.bluemusic.screens.config;

import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import butterknife.BindView;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.core.bluetooth.SourceDevice;
import eu.darken.bluemusic.util.DeviceHelper;
import eu.darken.bluemusic.util.ui.BasicViewHolder;
import eu.darken.bluemusic.util.ui.ClickableAdapter;


class ConfigAdapter extends ClickableAdapter<ConfigAdapter.DeviceVH, SourceDevice> {

    @Override
    public DeviceVH onCreateBaseViewHolder(LayoutInflater inflater, ViewGroup parent, int viewType) {
        return new DeviceVH(parent);
    }

    static class DeviceVH extends BasicViewHolder<SourceDevice> {
        @BindView(R.id.name) TextView name;
        @BindView(R.id.caption) TextView caption;
        @BindView(R.id.icon) ImageView icon;

        public DeviceVH(@NonNull ViewGroup parent) {
            super(parent, R.layout.viewholder_device);
        }

        @Override
        public void bind(@NonNull SourceDevice item) {
            super.bind(item);
            name.setText(DeviceHelper.getAliasAndName(item));
            caption.setText(item.getAddress());

            icon.setImageResource(DeviceHelper.getIconForDevice(item));
        }
    }
}
