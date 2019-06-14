package eu.darken.bluemusic.main.ui.managed;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import butterknife.BindView;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.main.core.audio.AudioStream;
import eu.darken.bluemusic.main.core.database.ManagedDevice;
import eu.darken.bluemusic.util.ApiHelper;
import eu.darken.bluemusic.util.AppTool;
import eu.darken.bluemusic.util.Check;
import eu.darken.bluemusic.util.DeviceHelper;
import eu.darken.bluemusic.util.ui.BasicAdapter;
import eu.darken.bluemusic.util.ui.BasicViewHolder;
import timber.log.Timber;

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
        void onStreamAdjusted(ManagedDevice device, AudioStream.Type type, float percentage);

        void onShowConfigScreen(ManagedDevice device);
    }

    static class ManagedDeviceVH extends BasicViewHolder<ManagedDevice> {
        @BindView(R.id.device_icon) ImageView icon;
        @BindView(R.id.name) TextView nameView;
        @BindView(R.id.lastseen) TextView lastSeen;

        @BindView(R.id.launch_icon) ImageView launchIcon;
        @BindView(R.id.autoplay_icon) ImageView autoPlayIcon;
        @BindView(R.id.volumelock_icon) ImageView volumeLockIcon;
        @BindView(R.id.keepawake_icon) ImageView keepAwakeIcon;
        @BindView(R.id.extras_container) ViewGroup extrasContainer;

        @BindView(R.id.config_icon) View config;

        @BindView(R.id.music_container) View musicContainer;
        @BindView(R.id.music_seekbar) SeekBar musicSeekbar;
        @BindView(R.id.music_counter) TextView musicCounter;

        @BindView(R.id.call_container) View voiceContainer;
        @BindView(R.id.call_seekbar) SeekBar voiceSeekbar;
        @BindView(R.id.call_counter) TextView voiceCounter;

        @BindView(R.id.ring_container) View ringContainer;
        @BindView(R.id.ring_seekbar) SeekBar ringSeekbar;
        @BindView(R.id.ring_counter) TextView ringCounter;
        @BindView(R.id.ring_permission_label) TextView ringPermissionLabel;
        @BindView(R.id.ring_permission_action) TextView ringPermissionAction;

        @BindView(R.id.notification_container) View notificationContainer;
        @BindView(R.id.notification_seekbar) SeekBar notificationSeekbar;
        @BindView(R.id.notification_counter) TextView notificationCounter;
        @BindView(R.id.notification_permission_label) TextView notificationPermissionLabel;
        @BindView(R.id.notification_permission_action) TextView notificationPermissionAction;
        private Callback callback;

        ManagedDeviceVH(ViewGroup parent, Callback callback) {
            super(parent, R.layout.viewholder_managed_device);
            this.callback = callback;
        }

        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void bind(ManagedDevice item) {
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

            autoPlayIcon.setVisibility(item.getAutoPlay() ? View.VISIBLE : View.GONE);

            volumeLockIcon.setVisibility(item.getVolumeLock() ? View.VISIBLE : View.GONE);
            keepAwakeIcon.setVisibility(item.getKeepAwake() ? View.VISIBLE : View.GONE);

            if (item.getLaunchPkg() != null) {
                try {
                    launchIcon.setImageDrawable(AppTool.getIcon(getContext(), item.getLaunchPkg()));
                } catch (PackageManager.NameNotFoundException e) {
                    Timber.e(e);
                }
            }
            launchIcon.setVisibility(item.getLaunchPkg() != null ? View.VISIBLE : View.GONE);

            extrasContainer.setVisibility(item.getLaunchPkg() != null || item.getAutoPlay() || item.getVolumeLock() ? View.VISIBLE : View.GONE);

            config.setOnClickListener(v -> callback.onShowConfigScreen(item));

            musicContainer.setVisibility(item.getVolume(AudioStream.Type.MUSIC) != null ? View.VISIBLE : View.GONE);
            if (item.getVolume(AudioStream.Type.MUSIC) != null) {
                musicSeekbar.setMax(item.getMaxVolume(AudioStream.Type.MUSIC));
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
                        callback.onStreamAdjusted(item, AudioStream.Type.MUSIC, (float) seekBar.getProgress() / seekBar.getMax());
                    }
                });
                musicSeekbar.setProgress(item.getRealVolume(AudioStream.Type.MUSIC));
                musicCounter.setText(String.valueOf(musicSeekbar.getProgress()));
            }

            voiceContainer.setVisibility(item.getVolume(AudioStream.Type.CALL) != null ? View.VISIBLE : View.GONE);
            if (item.getVolume(AudioStream.Type.CALL) != null) {
                voiceSeekbar.setMax(item.getMaxVolume(AudioStream.Type.CALL));
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
                        callback.onStreamAdjusted(item, AudioStream.Type.CALL, (float) seekBar.getProgress() / seekBar.getMax());
                    }
                });
                voiceSeekbar.setProgress(item.getRealVolume(AudioStream.Type.CALL));
                voiceCounter.setText(String.valueOf(voiceSeekbar.getProgress()));
            }

            ringContainer.setVisibility(item.getVolume(AudioStream.Type.RINGTONE) != null ? View.VISIBLE : View.GONE);
            if (item.getVolume(AudioStream.Type.RINGTONE) != null) {
                ringSeekbar.setMax(item.getMaxVolume(AudioStream.Type.RINGTONE));
                ringSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        ringCounter.setText(String.valueOf(progress));
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {

                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        callback.onStreamAdjusted(item, AudioStream.Type.RINGTONE, (float) seekBar.getProgress() / seekBar.getMax());
                    }
                });
                ringSeekbar.setProgress(item.getRealVolume(AudioStream.Type.RINGTONE));
                ringCounter.setText(String.valueOf(ringSeekbar.getProgress()));
            }

            ringPermissionAction.setOnClickListener(v -> {
                Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                getContext().startActivity(intent);
            });

            final NotificationManager notifMan = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
            Check.notNull(notifMan);
            boolean needsPermission = ApiHelper.hasMarshmallow() && item.getVolume(AudioStream.Type.RINGTONE) != null && !notifMan.isNotificationPolicyAccessGranted();
            ringPermissionLabel.setVisibility(needsPermission ? View.VISIBLE : View.GONE);
            ringPermissionAction.setVisibility(needsPermission ? View.VISIBLE : View.GONE);
            ringSeekbar.setVisibility(needsPermission ? View.GONE : View.VISIBLE);
            ringCounter.setVisibility(needsPermission ? View.GONE : View.VISIBLE);


            notificationContainer.setVisibility(item.getVolume(AudioStream.Type.NOTIFICATION) != null ? View.VISIBLE : View.GONE);
            if (item.getVolume(AudioStream.Type.NOTIFICATION) != null) {
                notificationSeekbar.setMax(item.getMaxVolume(AudioStream.Type.NOTIFICATION));
                notificationSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        notificationCounter.setText(String.valueOf(progress));
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {

                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        callback.onStreamAdjusted(item, AudioStream.Type.NOTIFICATION,
                                (float) seekBar.getProgress() / seekBar.getMax());
                    }
                });
                notificationSeekbar.setProgress(item.getRealVolume(AudioStream.Type.NOTIFICATION));
                notificationCounter.setText(String.valueOf(notificationSeekbar.getProgress()));
            }

            notificationPermissionAction.setOnClickListener(v -> {
                Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                getContext().startActivity(intent);
            });

            notificationPermissionLabel.setVisibility(needsPermission ? View.VISIBLE : View.GONE);
            notificationPermissionAction.setVisibility(needsPermission ? View.VISIBLE : View.GONE);
            notificationSeekbar.setVisibility(needsPermission ? View.GONE : View.VISIBLE);
            notificationCounter.setVisibility(needsPermission ? View.GONE : View.VISIBLE);
        }
    }
}
