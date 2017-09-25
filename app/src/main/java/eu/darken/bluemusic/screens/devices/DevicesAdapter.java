package eu.darken.bluemusic.screens.devices;

import android.bluetooth.BluetoothClass;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import butterknife.BindView;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.core.bluetooth.SourceDevice;
import eu.darken.bluemusic.util.ui.BasicViewHolder;
import eu.darken.bluemusic.util.ui.ClickableAdapter;


class DevicesAdapter extends ClickableAdapter<DevicesAdapter.DeviceVH, SourceDevice> {

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
            name.setText(item.getName());
            caption.setText(item.getAddress());
            int devClass;

            if (item.getBluetoothClass() == null) devClass = BluetoothClass.Device.Major.UNCATEGORIZED;
            else devClass = item.getBluetoothClass().getMajorDeviceClass();
            if (devClass == BluetoothClass.Device.Major.AUDIO_VIDEO) {
                icon.setImageResource(R.drawable.ic_headset_white_36dp);
            } else {
                icon.setImageResource(R.drawable.ic_devices_other_white_36dp);
            }
        }
    }
}
