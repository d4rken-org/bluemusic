package eu.darken.bluemusic.main.ui.managed

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.annotation.RequiresApi
import eu.darken.bluemusic.R
import eu.darken.bluemusic.databinding.ViewholderManagedDeviceBinding
import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.database.ManagedDevice
import eu.darken.bluemusic.main.ui.managed.ManagedDevicesAdapter.ManagedDeviceVH
import eu.darken.bluemusic.util.*
import eu.darken.bluemusic.util.ui.BasicAdapter
import eu.darken.bluemusic.util.ui.BasicViewHolder
import timber.log.Timber

internal class ManagedDevicesAdapter(private val callback: Callback) : BasicAdapter<ManagedDeviceVH, ManagedDevice>() {
    override fun onCreateBaseViewHolder(inflater: LayoutInflater, parent: ViewGroup, viewType: Int): ManagedDeviceVH {
        return ManagedDeviceVH(parent, callback)
    }

    internal interface Callback {
        fun onStreamAdjusted(device: ManagedDevice, type: AudioStream.Type, percentage: Float)
        fun onShowConfigScreen(device: ManagedDevice)
    }

    internal class ManagedDeviceVH(parent: ViewGroup, private val callback: Callback) : BasicViewHolder<ManagedDevice>(parent, R.layout.viewholder_managed_device) {

        private val ui: ViewholderManagedDeviceBinding by lazy { ViewholderManagedDeviceBinding.bind(itemView) }

        @RequiresApi(api = Build.VERSION_CODES.M)
        override fun bind(item: ManagedDevice) {
            super.bind(item)
            ui.deviceIcon.setImageResource(DeviceHelper.getIconForDevice(item.sourceDevice))
            ui.name.text = DeviceHelper.getAliasAndName(item.sourceDevice)
            ui.name.setTypeface(null, if (item.isActive) Typeface.BOLD else Typeface.NORMAL)
            val timeString: String = if (item.lastConnected > 0) {
                DateUtils.getRelativeDateTimeString(context, item.lastConnected, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0).toString()
            } else {
                getString(R.string.label_neverseen)
            }
            ui.lastseen.text = if (item.isActive) getString(R.string.label_state_connected) else timeString
            ui.lastseen.visibility = if (item.autoPlay) View.VISIBLE else View.GONE
            ui.volumelockIcon.visibility = if (item.volumeLock) View.VISIBLE else View.GONE
            ui.keepawakeIcon.visibility = if (item.keepAwake) View.VISIBLE else View.GONE
            if (item.launchPkg != null) {
                try {
                    ui.launchIcon.setImageDrawable(AppTool.getIcon(context, item.launchPkg))
                } catch (e: PackageManager.NameNotFoundException) {
                    Timber.e(e)
                }
            }
            ui.launchIcon.visibility = if (item.launchPkg != null) View.VISIBLE else View.GONE
            val hasExtras = item.launchPkg != null || item.autoPlay || item.volumeLock || item.keepAwake
            ui.extrasContainer.visibility = if (hasExtras) View.VISIBLE else View.GONE
            ui.configIcon.setOnClickListener { v: View? -> callback.onShowConfigScreen(item) }
            ui.musicContainer.visibility = if (item.getVolume(AudioStream.Type.MUSIC) != null) View.VISIBLE else View.GONE
            if (item.getVolume(AudioStream.Type.MUSIC) != null) {
                ui.musicSeekbar.max = item.getMaxVolume(AudioStream.Type.MUSIC)
                ui.musicSeekbar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        ui.musicCounter.text = progress.toString()
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        callback.onStreamAdjusted(item, AudioStream.Type.MUSIC, seekBar.progress.toFloat() / seekBar.max)
                    }
                })
                ui.musicSeekbar.progress = item.getRealVolume(AudioStream.Type.MUSIC)
                ui.musicCounter.text = ui.musicSeekbar.progress.toString()
            }
            ui.callContainer.visibility = if (item.getVolume(AudioStream.Type.CALL) != null) View.VISIBLE else View.GONE
            if (item.getVolume(AudioStream.Type.CALL) != null) {
                ui.callSeekbar.max = item.getMaxVolume(AudioStream.Type.CALL)
                ui.callSeekbar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        ui.callCounter.text = progress.toString()
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        callback.onStreamAdjusted(item, AudioStream.Type.CALL, seekBar.progress.toFloat() / seekBar.max)
                    }
                })
                ui.callSeekbar.progress = item.getRealVolume(AudioStream.Type.CALL)
                ui.callCounter.text = ui.callSeekbar.progress.toString()
            }
            ui.ringContainer.visibility = if (item.getVolume(AudioStream.Type.RINGTONE) != null) View.VISIBLE else View.GONE
            if (item.getVolume(AudioStream.Type.RINGTONE) != null) {
                ui.ringSeekbar.max = item.getMaxVolume(AudioStream.Type.RINGTONE)
                ui.ringSeekbar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        ui.ringCounter.text = progress.toString()
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        callback.onStreamAdjusted(item, AudioStream.Type.RINGTONE, seekBar.progress.toFloat() / seekBar.max)
                    }
                })
                ui.ringSeekbar.progress = item.getRealVolume(AudioStream.Type.RINGTONE)
                ui.ringCounter.text = ui.ringSeekbar.progress.toString()
            }
            ui.ringPermissionAction.setOnClickListener { v: View? -> ActivityUtil.tryStartActivity(context as Activity, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
            val notifMan = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            Check.notNull(notifMan)
            val needsPermission = ApiHelper.hasMarshmallow() && item.getVolume(AudioStream.Type.RINGTONE) != null && !notifMan.isNotificationPolicyAccessGranted
            ui.ringPermissionLabel.visibility = if (needsPermission) View.VISIBLE else View.GONE
            ui.ringPermissionAction.visibility = if (needsPermission) View.VISIBLE else View.GONE
            ui.ringSeekbar.visibility = if (needsPermission) View.GONE else View.VISIBLE
            ui.ringCounter.visibility = if (needsPermission) View.GONE else View.VISIBLE
            ui.notificationContainer.visibility = if (item.getVolume(AudioStream.Type.NOTIFICATION) != null) View.VISIBLE else View.GONE
            if (item.getVolume(AudioStream.Type.NOTIFICATION) != null) {
                ui.notificationSeekbar.max = item.getMaxVolume(AudioStream.Type.NOTIFICATION)
                ui.notificationSeekbar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        ui.notificationCounter.text = progress.toString()
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        callback.onStreamAdjusted(item, AudioStream.Type.NOTIFICATION,
                                seekBar.progress.toFloat() / seekBar.max)
                    }
                })
                ui.notificationSeekbar.progress = item.getRealVolume(AudioStream.Type.NOTIFICATION)
                ui.notificationCounter.text = ui.notificationSeekbar.progress.toString()
            }
            ui.notificationPermissionAction.setOnClickListener { v: View? -> ActivityUtil.tryStartActivity(context as Activity, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
            ui.notificationPermissionLabel.visibility = if (needsPermission) View.VISIBLE else View.GONE
            ui.notificationPermissionAction.visibility = if (needsPermission) View.VISIBLE else View.GONE
            ui.notificationSeekbar.visibility = if (needsPermission) View.GONE else View.VISIBLE
            ui.notificationCounter.visibility = if (needsPermission) View.GONE else View.VISIBLE
            ui.alarmContainer.visibility = if (item.getVolume(AudioStream.Type.ALARM) != null) View.VISIBLE else View.GONE
            if (item.getVolume(AudioStream.Type.ALARM) != null) {
                ui.alarmSeekbar.max = item.getMaxVolume(AudioStream.Type.ALARM)
                ui.alarmSeekbar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        ui.alarmCounter.text = progress.toString()
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        callback.onStreamAdjusted(item, AudioStream.Type.ALARM, seekBar.progress.toFloat() / seekBar.max)
                    }
                })
                ui.alarmSeekbar.progress = item.getRealVolume(AudioStream.Type.ALARM)
                ui.alarmCounter.text = ui.alarmSeekbar.progress.toString()
            }
        }
    }
}