package eu.darken.bluemusic.main.ui.managed;

import android.support.annotation.NonNull;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import butterknife.BindView;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.main.core.database.ManagedDevice;
import eu.darken.bluemusic.util.DeviceHelper;
import eu.darken.bluemusic.util.ui.BasicAdapter;
import eu.darken.bluemusic.util.ui.BasicViewHolder;

import static android.graphics.Typeface.BOLD;
import static android.graphics.Typeface.NORMAL;


class ManagedDevicesAdapter extends BasicAdapter<ManagedDevicesAdapter.ManagedDeviceVH, ManagedDevice> {
    private final Callback callback;

    ManagedDevicesAdapter(Callback callback) {
        this.callback = callback;
    }

    @Override
    public ManagedDeviceVH onCreateBaseViewHolder(LayoutInflater inflater, ViewGroup parent, int viewType) {
        return new ManagedDeviceVH(parent, callback);
    }

    interface Callback {
        void onMusicVolumeAdjusted(ManagedDevice device, float percentage);

        void onCallVolumeAdjusted(ManagedDevice device, float percentage);

        void onShowConfigScreen(ManagedDevice device);
    }

    static class ManagedDeviceVH extends BasicViewHolder<ManagedDevice> {
        @BindView(R.id.device_icon) ImageView icon;
        @BindView(R.id.name) TextView nameView;
        @BindView(R.id.lastseen) TextView lastSeen;
        @BindView(R.id.flags) TextView flags;
        @BindView(R.id.config_icon) View config;

        @BindView(R.id.music_container) View musicContainer;
        @BindView(R.id.music_seekbar) SeekBar musicSeekbar;
        @BindView(R.id.music_counter) TextView musicCounter;

        @BindView(R.id.call_container) View voiceContainer;
        @BindView(R.id.call_seekbar) SeekBar voiceSeekbar;
        @BindView(R.id.call_counter) TextView voiceCounter;
        private Callback callback;

        ManagedDeviceVH(@NonNull ViewGroup parent, Callback callback) {
            super(parent, R.layout.viewholder_managed_device);
            this.callback = callback;
        }

        @Override
        public void bind(@NonNull ManagedDevice item) {
            super.bind(item);
            icon.setImageResource(DeviceHelper.getIconForDevice(item.getSourceDevice()));

            nameView.setText(DeviceHelper.getAliasAndName(item.getSourceDevice()));
            nameView.setTypeface(null, item.isActive() ? BOLD : NORMAL);

            String timeString;
            if (item.getLastConnected() > 0) {
                timeString = DateUtils.getRelativeDateTimeString(getContext(), item.getLastConnected(), DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0).toString();
            } else {
                timeString = getString(R.string.label_neverseen);
            }
            lastSeen.setText(item.isActive() ? getString(R.string.label_state_connected) : timeString);

            if (item.isAutoPlayEnabled()) flags.setText(R.string.label_autoplay);
            flags.setVisibility(item.isAutoPlayEnabled() ? View.VISIBLE : View.GONE);

            config.setOnClickListener(v -> callback.onShowConfigScreen(item));

            musicContainer.setVisibility(item.getMusicVolume() != null ? View.VISIBLE : View.GONE);
            if (item.getMusicVolume() != null) {
                musicSeekbar.setMax(item.getMaxMusicVolume());
                musicSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        musicCounter.setText(String.valueOf(progress));
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {

                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        callback.onMusicVolumeAdjusted(item, (float) seekBar.getProgress() / seekBar.getMax());
                    }
                });
                musicSeekbar.setProgress(item.getRealMusicVolume());
            }

            voiceContainer.setVisibility(item.getCallVolume() != null ? View.VISIBLE : View.GONE);
            if (item.getCallVolume() != null) {
                voiceSeekbar.setMax(item.getMaxCallVolume());
                voiceSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        voiceCounter.setText(String.valueOf(progress));
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {

                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        callback.onCallVolumeAdjusted(item, (float) seekBar.getProgress() / seekBar.getMax());
                    }
                });
                voiceSeekbar.setProgress(item.getRealCallVolume());
            }
        }
    }
}
