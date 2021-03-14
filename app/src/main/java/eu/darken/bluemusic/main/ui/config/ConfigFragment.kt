package eu.darken.bluemusic.main.ui.config

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.Unbinder
import com.google.android.material.snackbar.Snackbar
import eu.darken.bluemusic.R
import eu.darken.bluemusic.bluetooth.core.FakeSpeakerDevice
import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.database.ManagedDevice
import eu.darken.bluemusic.main.ui.MainActivity
import eu.darken.bluemusic.util.ActivityUtil
import eu.darken.bluemusic.util.AppTool
import eu.darken.bluemusic.util.Check
import eu.darken.bluemusic.util.ui.PreferenceView
import eu.darken.bluemusic.util.ui.SwitchPreferenceView
import eu.darken.mvpbakery.base.MVPBakery
import eu.darken.mvpbakery.base.PresenterRetainer
import eu.darken.mvpbakery.base.ViewModelRetainer
import eu.darken.mvpbakery.injection.InjectedPresenter
import eu.darken.mvpbakery.injection.PresenterInjectionCallback
import timber.log.Timber
import javax.inject.Inject

class ConfigFragment : Fragment(), ConfigPresenter.View {

    private lateinit var actionBar: ActionBar
    private var unbinder: Unbinder? = null

    @BindView(R.id.pref_music_volume) lateinit var prefMusicVolume: SwitchPreferenceView
    @BindView(R.id.pref_call_volume) lateinit var prefCallVolume: SwitchPreferenceView
    @BindView(R.id.pref_ring_volume) lateinit var prefRingVolume: SwitchPreferenceView
    @BindView(R.id.pref_notification_volume) lateinit var prefNotificationVolume: SwitchPreferenceView
    @BindView(R.id.pref_alarm_volume) lateinit var prefAlarmVolume: SwitchPreferenceView
    @BindView(R.id.pref_autoplay_enabled) lateinit var prefAutoPlay: SwitchPreferenceView
    @BindView(R.id.pref_launch_app) lateinit var prefLaunchApp: PreferenceView
    @BindView(R.id.pref_volume_lock) lateinit var prefVolumeLock: SwitchPreferenceView
    @BindView(R.id.pref_volume_lock_box) lateinit var prefVolumeLockBox: ViewGroup
    @BindView(R.id.pref_keep_awake) lateinit var prefKeepAwake: SwitchPreferenceView
    @BindView(R.id.pref_keep_awake_box) lateinit var prefKeepAwakeBox: ViewGroup
    @BindView(R.id.pref_volume_nudge) lateinit var prefNudgeVolume: SwitchPreferenceView
    @BindView(R.id.pref_monitoring_duration) lateinit var prefMonitoringDuration: PreferenceView
    @BindView(R.id.pref_reaction_delay) lateinit var prefReactionDelay: PreferenceView
    @BindView(R.id.pref_adjustment_delay) lateinit var prefAdjustmentDelay: PreferenceView
    @BindView(R.id.pref_rename) lateinit var prefRename: PreferenceView
    @BindView(R.id.pref_delete) lateinit var prefDelete: PreferenceView

    @Inject lateinit var presenter: ConfigPresenter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val layout = inflater.inflate(R.layout.fragment_layout_config, container, false)
        unbinder = ButterKnife.bind(this, layout)
        return layout
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefMusicVolume.setOnCheckedChangedListener { _: SwitchPreferenceView?, _: Boolean -> presenter.onToggleMusicVolume() }
        prefCallVolume.setOnCheckedChangedListener { _: SwitchPreferenceView?, _: Boolean -> presenter.onToggleCallVolume() }
        prefRingVolume.setOnCheckedChangedListener { v: SwitchPreferenceView, _: Boolean -> v.isChecked = presenter.onToggleRingVolume() }
        prefNotificationVolume.setOnCheckedChangedListener { v: SwitchPreferenceView, _: Boolean -> v.isChecked = presenter.onToggleNotificationVolume() }
        prefAlarmVolume.setOnCheckedChangedListener { v: SwitchPreferenceView, _: Boolean -> v.isChecked = presenter.onToggleAlarmVolume() }
        prefAutoPlay.setOnCheckedChangedListener { v: SwitchPreferenceView, _: Boolean -> v.isChecked = presenter.onToggleAutoPlay() }
        prefLaunchApp.setOnClickListener {
            presenter.onLaunchAppClicked()
            Snackbar.make(requireView(), R.string.label_just_a_moment_please, Snackbar.LENGTH_SHORT).show()
        }
        prefLaunchApp.setOnLongClickListener {
            presenter.onClearLaunchApp()
            true
        }
        prefVolumeLock.setOnCheckedChangedListener { v: SwitchPreferenceView, _: Boolean -> v.isChecked = presenter.onToggleVolumeLock() }
        prefKeepAwake.setOnCheckedChangedListener { v: SwitchPreferenceView, _: Boolean -> v.isChecked = presenter.onToggleKeepAwake() }
        prefNudgeVolume.setOnCheckedChangedListener { v, _ -> v.isChecked = presenter.onToggleNudgeVolume() }
        prefMonitoringDuration.setOnClickListener { presenter.onEditMonitoringDurationClicked() }
        prefReactionDelay.setOnClickListener { presenter.onEditReactionDelayClicked() }
        prefAdjustmentDelay.setOnClickListener { presenter.onEditAdjustmentDelayClicked() }
        prefRename.setOnClickListener { presenter.onRenameClicked() }
        prefDelete.setOnClickListener { presenter.onDeleteDevice() }
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        MVPBakery.builder<ConfigPresenter.View, ConfigPresenter>()
                .addPresenterCallback(object : PresenterRetainer.Callback<ConfigPresenter.View, ConfigPresenter> {
                    override fun onPresenterAvailable(presenter: ConfigPresenter) {
                        val address = requireArguments().getString(ARG_ADDRESS)
                        presenter.setDevice(address!!)
                    }
                })
                .addPresenterCallback(PresenterInjectionCallback(this))
                .presenterRetainer(ViewModelRetainer(this))
                .presenterFactory(InjectedPresenter(this))
                .attach(this)
        super.onActivityCreated(savedInstanceState)

        actionBar = (requireActivity() as MainActivity).supportActionBar!!

        actionBar.setDisplayHomeAsUpEnabled(true)
        actionBar.title = requireArguments().getString(ARG_NAME)
    }

    override fun onDestroyView() {
        unbinder?.unbind()
        super.onDestroyView()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Check.notNull(activity)
        if (item.itemId == android.R.id.home) {
            activity?.supportFragmentManager?.popBackStack()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun updateProState(isPro: Boolean) {
        prefRingVolume.setDescription(getString(R.string.description_ring_volume) + if (isPro) "" else "\n[${getString(R.string.label_premium_version_required)}]     ")
        prefNotificationVolume.setDescription(getString(R.string.description_notification_volume) + if (isPro) "" else "\n[${getString(R.string.label_premium_version_required)}]")
        prefAutoPlay.setDescription(getString(R.string.description_autoplay) + if (isPro) "" else "\n[${getString(R.string.label_premium_version_required)}]     ")
        prefVolumeLock.setDescription(getString(R.string.description_volume_lock) + if (isPro) "" else "\n[${getString(R.string.label_premium_version_required)}]")
        prefKeepAwake.setDescription(getString(R.string.description_keep_awake) + if (isPro) "" else "\n[${getString(R.string.label_premium_version_required)}]")
        if (!isPro) {
            prefLaunchApp.setDescription("[" + getString(R.string.label_premium_version_required) + "]")
        }
        prefRename.setDescription(getString(R.string.description_rename_device) + if (isPro) "" else "\n[${getString(R.string.label_premium_version_required)}]")
    }

    override fun updateDevice(device: ManagedDevice) {
        Check.notNull(arguments)
        val alias = device.label
        actionBar.title = alias
        val name = device.name
        if (alias != name) actionBar.subtitle = name
        prefMusicVolume.isChecked = device.getVolume(AudioStream.Type.MUSIC) != null
        prefMusicVolume.visibility = View.VISIBLE
        prefCallVolume.isChecked = device.getVolume(AudioStream.Type.CALL) != null
        prefCallVolume.visibility = View.VISIBLE
        prefRingVolume.isChecked = device.getVolume(AudioStream.Type.RINGTONE) != null
        prefRingVolume.visibility = View.VISIBLE
        prefNotificationVolume.isChecked = device.getVolume(AudioStream.Type.NOTIFICATION) != null
        prefNotificationVolume.visibility = View.VISIBLE
        prefAlarmVolume.isChecked = device.getVolume(AudioStream.Type.ALARM) != null
        prefAlarmVolume.visibility = View.VISIBLE
        prefAutoPlay.isChecked = device.autoPlay
        prefAutoPlay.visibility = View.VISIBLE
        prefVolumeLock.isChecked = device.volumeLock
        prefVolumeLockBox.visibility = if (device.address == FakeSpeakerDevice.ADDR) View.GONE else View.VISIBLE
        prefKeepAwake.isChecked = device.keepAwake
        prefKeepAwakeBox.visibility = if (device.address == FakeSpeakerDevice.ADDR) View.GONE else View.VISIBLE

        prefNudgeVolume.isChecked = device.nudgeVolume

        prefLaunchApp.visibility = View.VISIBLE
        val launchLabel = try {
            device.launchPkg?.let { AppTool.getLabel(requireContext(), it) }
        } catch (e: PackageManager.NameNotFoundException) {
            Timber.e(e)
            null
        }
        prefLaunchApp.setDescription(launchLabel ?: getString(R.string.description_launch_app))

        prefReactionDelay.visibility = View.VISIBLE
        prefAdjustmentDelay.visibility = View.VISIBLE
        prefMonitoringDuration.visibility = View.VISIBLE
        prefRename.visibility = if (device.address == FakeSpeakerDevice.ADDR) View.GONE else View.VISIBLE
        prefDelete.visibility = View.VISIBLE
    }

    override fun showRequiresPro() {
        AlertDialog.Builder(requireContext())
                .setTitle(R.string.label_premium_version)
                .setMessage(R.string.description_premium_required_this_extra_option)
                .setIcon(R.drawable.ic_stars_white_24dp)
                .setPositiveButton(R.string.action_upgrade) { _: DialogInterface?, _: Int -> presenter.onPurchaseUpgrade(requireActivity()) }
                .setNegativeButton(R.string.action_cancel) { _: DialogInterface?, _: Int -> }
                .show()
    }

    @SuppressLint("InflateParams") override fun showMonitoringDurationDialog(duration: Long) {
        val container = layoutInflater.inflate(R.layout.view_dialog_delay, null)
        val input = container.findViewById<EditText>(R.id.input)
        input.setText(duration.toString())
        AlertDialog.Builder(requireContext())
                .setTitle(R.string.label_monitoring_duration)
                .setMessage(R.string.description_monitoring_duration)
                .setIcon(R.drawable.ic_timer_white_24dp)
                .setView(container)
                .setPositiveButton(R.string.action_set) { _: DialogInterface?, _: Int ->
                    try {
                        val newDuration = input.text.toString().toLong()
                        presenter.onEditMonitoringDuration(newDuration)
                    } catch (e: NumberFormatException) {
                        Timber.e(e)
                        Snackbar.make(requireView(), R.string.label_invalid_input, Snackbar.LENGTH_SHORT).show()
                    }
                }
                .setNeutralButton(R.string.action_reset) { _: DialogInterface?, _: Int -> presenter.onEditReactionDelay(-1) }
                .setNegativeButton(R.string.action_cancel) { _: DialogInterface?, _: Int -> }
                .show()
    }

    @SuppressLint("InflateParams") override fun showReactionDelayDialog(delay: Long) {
        val container = layoutInflater.inflate(R.layout.view_dialog_delay, null)
        val input = container.findViewById<EditText>(R.id.input)
        input.setText(delay.toString())
        AlertDialog.Builder(requireContext())
                .setTitle(R.string.label_reaction_delay)
                .setMessage(R.string.description_reaction_delay)
                .setIcon(R.drawable.ic_timer_white_24dp)
                .setView(container)
                .setPositiveButton(R.string.action_set) { _: DialogInterface?, _: Int ->
                    try {
                        val newDelay = input.text.toString().toLong()
                        presenter.onEditReactionDelay(newDelay)
                    } catch (e: NumberFormatException) {
                        Timber.e(e)
                        Snackbar.make(requireView(), R.string.label_invalid_input, Snackbar.LENGTH_SHORT).show()
                    }
                }
                .setNeutralButton(R.string.action_reset) { _: DialogInterface?, i: Int -> presenter.onEditReactionDelay(-1) }
                .setNegativeButton(R.string.action_cancel) { _: DialogInterface?, _: Int -> }
                .show()
    }

    @SuppressLint("InflateParams") override fun showAdjustmentDelayDialog(delay: Long) {
        val container = layoutInflater.inflate(R.layout.view_dialog_delay, null)
        val input = container.findViewById<EditText>(R.id.input)
        input.setText(delay.toString())
        AlertDialog.Builder(requireContext())
                .setTitle(R.string.label_adjustment_delay)
                .setMessage(R.string.description_adjustment_delay)
                .setIcon(R.drawable.ic_av_timer_white_24dp)
                .setView(container)
                .setPositiveButton(R.string.action_set) { _: DialogInterface?, _: Int ->
                    try {
                        val newDelay = input.text.toString().toLong()
                        presenter.onEditAdjustmentDelay(newDelay)
                    } catch (e: NumberFormatException) {
                        Timber.e(e)
                        Snackbar.make(requireView(), R.string.label_invalid_input, Snackbar.LENGTH_SHORT).show()
                    }
                }
                .setNeutralButton(R.string.action_reset) { _: DialogInterface?, _: Int -> presenter.onEditAdjustmentDelay(-1) }
                .setNegativeButton(R.string.action_cancel) { _: DialogInterface?, _: Int -> }
                .show()
    }

    @SuppressLint("InflateParams")
    override fun showRenameDialog(current: String?) {
        val container = layoutInflater.inflate(R.layout.view_dialog_rename, null)
        val input = container.findViewById<EditText>(R.id.input)
        input.setText(current)
        AlertDialog.Builder(requireContext())
                .setTitle(R.string.label_rename_device)
                .setView(container)
                .setPositiveButton(R.string.action_set) { _: DialogInterface?, _: Int -> presenter.onRenameDevice(input.text.toString()) }
                .setNeutralButton(R.string.action_reset) { _: DialogInterface?, _: Int -> presenter.onRenameDevice(null) }
                .setNegativeButton(R.string.action_cancel) { _: DialogInterface?, _: Int -> }
                .show()
    }

    override fun showAppSelectionDialog(items: List<AppTool.Item>) {
        val adapter = LaunchAppAdapter(items)
        AlertDialog.Builder(requireContext())
                .setAdapter(adapter) { _: DialogInterface?, pos: Int -> presenter.onLaunchAppSelected(adapter.getItem(pos)) }
                .show()
    }

    @RequiresApi(api = Build.VERSION_CODES.M) override fun showNotificationPermissionView() {
        Toast.makeText(context, R.string.description_ring_volume_additional_permission, Toast.LENGTH_LONG).show()
        ActivityUtil.tryStartActivity(this, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    }

    override fun showUndoDeletion(undoAction: Runnable) {
        Snackbar.make(requireActivity().findViewById(R.id.frame_content), R.string.label_forget_device, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_undo) { undoAction.run() }
                .show()
    }

    override fun finishScreen() {
        requireActivity().supportFragmentManager.popBackStack()
    }

    companion object {
        private const val ARG_ADDRESS = "device.address"
        private const val ARG_NAME = "device.aliasorname"
        @JvmStatic fun instantiate(device: ManagedDevice): Fragment {
            val bundle = Bundle()
            bundle.putString(ARG_ADDRESS, device.address)
            bundle.putString(ARG_NAME, device.label)
            val fragment = ConfigFragment()
            fragment.arguments = bundle
            return fragment
        }
    }
}