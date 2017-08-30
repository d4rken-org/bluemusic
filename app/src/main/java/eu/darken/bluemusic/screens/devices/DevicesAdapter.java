package eu.darken.bluemusic.screens.devices;

import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.List;

import butterknife.BindView;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.core.database.ManagedDevice;
import eu.darken.bluemusic.util.ui.BasicViewHolder;
import eu.darken.bluemusic.util.ui.ClickableAdapter;


class DevicesAdapter extends ClickableAdapter<DevicesAdapter.DeviceVH, ManagedDevice> {
    private final List<ManagedDevice> data;

    DevicesAdapter(List<ManagedDevice> devices) {
        this.data = devices;
    }

    @Override
    public DeviceVH onCreateBaseViewHolder(LayoutInflater inflater, ViewGroup parent, int viewType) {
        return new DeviceVH(parent);
    }

    @Override
    public void onBindViewHolder(DeviceVH holder, int position) {
        holder.bind(data.get(position));
    }

    @Override
    public int getItemCount() {
        return data.size();
    }


    static class DeviceVH extends BasicViewHolder<ManagedDevice> {
        @BindView(R.id.name) TextView name;
        @BindView(R.id.caption) TextView caption;
        @BindView(R.id.music_seekbar) SeekBar musicSeekbar;
        @BindView(R.id.music_counter) TextView musicCounter;
        @BindView(R.id.voice_seekbar) SeekBar voiceSeekbar;
        @BindView(R.id.voice_counter) TextView voiceCounter;

        public DeviceVH(@NonNull ViewGroup parent) {
            super(parent, R.layout.viewholder_devicevolumes);
        }

        @Override
        public void bind(@NonNull ManagedDevice managedDevice) {
            super.bind(managedDevice);
        }
    }
}
