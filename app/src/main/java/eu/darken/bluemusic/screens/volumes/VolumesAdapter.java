package eu.darken.bluemusic.screens.volumes;

import android.content.Context;
import android.support.annotation.StringRes;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.core.database.ManagedDevice;

import static android.graphics.Typeface.BOLD;
import static android.graphics.Typeface.NORMAL;


class VolumesAdapter extends RecyclerView.Adapter<VolumesAdapter.DeviceVH> {
    private final List<ManagedDevice> data;
    private final Callback callback;

    VolumesAdapter(List<ManagedDevice> devices, Callback callback) {
        this.data = devices;
        this.callback = callback;
    }

    @Override
    public DeviceVH onCreateViewHolder(ViewGroup parent, int viewType) {
        return new DeviceVH(LayoutInflater.from(parent.getContext()).inflate(R.layout.adapter_volumes_line, parent, false));
    }

    @Override
    public void onBindViewHolder(DeviceVH holder, int position) {
        holder.bind(data.get(position), callback);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    interface Callback {
        void onMusicVolumeAdjusted(ManagedDevice device, float percentage);

        void onVoiceVolumeAdjusted(ManagedDevice device, float percentage);
    }

    static class DeviceVH extends RecyclerView.ViewHolder {
        private final Context context;
        @BindView(R.id.name) TextView name;
        @BindView(R.id.caption) TextView caption;
        @BindView(R.id.music_seekbar) SeekBar musicSeekbar;
        @BindView(R.id.music_counter) TextView musicCounter;
        @BindView(R.id.voice_seekbar) SeekBar voiceSeekbar;
        @BindView(R.id.voice_counter) TextView voiceCounter;

        DeviceVH(View itemView) {
            super(itemView);
            context = itemView.getContext();
            ButterKnife.bind(this, itemView);
        }

        void bind(ManagedDevice item, Callback callback) {
            name.setText(item.getName());
            name.setTypeface(null, item.isActive() ? BOLD : NORMAL);

            String timeString;
            if (item.getLastConnected() > 0) {
                timeString = DateUtils.getRelativeDateTimeString(
                        getContext(),
                        item.getLastConnected(),
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.WEEK_IN_MILLIS,
                        0
                ).toString();
            } else {
                timeString = getString(R.string.time_tag_never);
            }
            caption.setText(
                    item.isActive() ?
                            getString(R.string.state_connected) :
                            getString(R.string.last_seen_x, timeString)
            );

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

            voiceSeekbar.setMax(item.getMaxVoiceVolume());
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
                    callback.onVoiceVolumeAdjusted(item, (float) seekBar.getProgress() / seekBar.getMax());
                }
            });
            voiceSeekbar.setProgress(item.getRealVoiceVolume());
        }

        Context getContext() {
            return itemView.getContext();
        }

        String getString(@StringRes int stringRes) {
            return context.getString(stringRes);
        }

        String getString(@StringRes int stringRes, Object... objects) {
            return context.getString(stringRes, objects);
        }
    }
}
