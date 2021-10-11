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
import com.google.android.material.snackbar.Snackbar
import eu.darken.bluemusic.R
import eu.darken.bluemusic.bluetooth.core.FakeSpeakerDevice
import eu.darken.bluemusic.databinding.FragmentLayoutConfigBinding
import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.database.ManagedDevice
import eu.darken.bluemusic.main.ui.MainActivity
import eu.darken.bluemusic.util.ActivityUtil
import eu.darken.bluemusic.util.AppTool
import eu.darken.bluemusic.util.Check
import eu.darken.bluemusic.util.viewBinding
import eu.darken.mvpbakery.base.MVPBakery
import eu.darken.mvpbakery.base.PresenterRetainer
import eu.darken.mvpbakery.base.ViewModelRetainer
import eu.darken.mvpbakery.injection.InjectedPresenter
import eu.darken.mvpbakery.injection.PresenterInjectionCallback
import timber.log.Timber
import javax.inject.Inject

class ConfigFragment : Fragment(), ConfigPresenter.View {

    private lateinit var actionBar: ActionBar
    val ui: FragmentLayoutConfigBinding by viewBinding()

    @Inject lateinit var presenter: ConfigPresenter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_layout_config, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.prefMusicVolume.setOnCheckedChangedListener { _, _ -> presenter.onToggleMusicVolume() }
        ui.prefCallVolume.setOnCheckedChangedListener { _, _ -> presenter.onToggleCallVolume() }
        ui.prefRingVolume.setOnCheckedChangedListener { v, _ -> v.isChecked = presenter.onToggleRingVolume() }
        ui.prefNotificationVolume.setOnCheckedChangedListener { v, _ -> v.isChecked = presenter.onToggleNotificationVolume() }
        ui.prefAlarmVolume.setOnCheckedChangedListener { v, _ -> v.isChecked = presenter.onToggleAlarmVolume() }
        ui.prefAutoplayEnabled.setOnCheckedChangedListener { v, _lean -> v.isChecked = presenter.onToggleAutoPlay() }
        ui.prefLaunchApp.setOnClickListener {
            presenter.onLaunchAppClicked()
            Snackbar.make(requireView(), R.string.label_just_a_moment_please, Snackbar.LENGTH_SHORT).show()
        }
        ui.prefLaunchApp.setOnLongClickListener {
            presenter.onClearLaunchApp()
            true
        }
        ui.prefVolumeLock.setOnCheckedChangedListener { v, _ -> v.isChecked = presenter.onToggleVolumeLock() }
        ui.prefKeepAwake.setOnCheckedChangedListener { v, _ -> v.isChecked = presenter.onToggleKeepAwake() }
        ui.prefVolumeNudge.setOnCheckedChangedListener { v, _ -> v.isChecked = presenter.onToggleNudgeVolume() }
        ui.prefMonitoringDuration.setOnClickListener { presenter.onEditMonitoringDurationClicked() }
        ui.prefReactionDelay.setOnClickListener { presenter.onEditReactionDelayClicked() }
        ui.prefAdjustmentDelay.setOnClickListener { presenter.onEditAdjustmentDelayClicked() }
        ui.prefRename.setOnClickListener { presenter.onRenameClicked() }
        ui.prefDelete.setOnClickListener { presenter.onDeleteDevice() }
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Check.notNull(activity)
        if (item.itemId == android.R.id.home) {
            activity?.supportFragmentManager?.popBackStack()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun updateProState(isPro: Boolean) {
        ui.prefRingVolume.setDescription(getString(R.string.description_ring_volume) + if (isPro) "" else "\n[${getString(R.string.label_premium_version_required)}]     ")
        ui.prefNotificationVolume.setDescription(getString(R.string.description_notification_volume) + if (isPro) "" else "\n[${getString(R.string.label_premium_version_required)}]")
        ui.prefAutoplayEnabled.setDescription(getString(R.string.description_autoplay) + if (isPro) "" else "\n[${getString(R.string.label_premium_version_required)}]     ")
        ui.prefVolumeLock.setDescription(getString(R.string.description_volume_lock) + if (isPro) "" else "\n[${getString(R.string.label_premium_version_required)}]")
        ui.prefKeepAwake.setDescription(getString(R.string.description_keep_awake) + if (isPro) "" else "\n[${getString(R.string.label_premium_version_required)}]")
        if (!isPro) {
            ui.prefLaunchApp.setDescription("[" + getString(R.string.label_premium_version_required) + "]")
        }
        ui.prefRename.setDescription(getString(R.string.description_rename_device) + if (isPro) "" else "\n[${getString(R.string.label_premium_version_required)}]")
    }

    override fun updateDevice(device: ManagedDevice) {
        Check.notNull(arguments)
        val alias = device.label
        actionBar.title = alias
        val name = device.name
        if (alias != name) actionBar.subtitle = name
        ui.prefMusicVolume.isChecked = device.getVolume(AudioStream.Type.MUSIC) != null
        ui.prefMusicVolume.visibility = View.VISIBLE
        ui.prefCallVolume.isChecked = device.getVolume(AudioStream.Type.CALL) != null
        ui.prefCallVolume.visibility = View.VISIBLE
        ui.prefRingVolume.isChecked = device.getVolume(AudioStream.Type.RINGTONE) != null
        ui.prefRingVolume.visibility = View.VISIBLE
        ui.prefNotificationVolume.isChecked = device.getVolume(AudioStream.Type.NOTIFICATION) != null
        ui.prefNotificationVolume.visibility = View.VISIBLE
        ui.prefAlarmVolume.isChecked = device.getVolume(AudioStream.Type.ALARM) != null
        ui.prefAlarmVolume.visibility = View.VISIBLE
        ui.prefAutoplayEnabled.isChecked = device.autoPlay
        ui.prefAutoplayEnabled.visibility = View.VISIBLE
        ui.prefVolumeLock.isChecked = device.volumeLock
        ui.prefVolumeLockBox.visibility = if (device.address == FakeSpeakerDevice.ADDR) View.GONE else View.VISIBLE
        ui.prefKeepAwake.isChecked = device.keepAwake
        ui.prefKeepAwakeBox.visibility = if (device.address == FakeSpeakerDevice.ADDR) View.GONE else View.VISIBLE

        ui.prefVolumeNudge.isChecked = device.nudgeVolume

        ui.prefLaunchApp.visibility = View.VISIBLE
        val launchLabel = try {
            device.launchPkg?.let { AppTool.getLabel(requireContext(), it) }
        } catch (e: PackageManager.NameNotFoundException) {
            Timber.e(e)
            null
        }
        ui.prefLaunchApp.setDescription(launchLabel ?: getString(R.string.description_launch_app))

        ui.prefReactionDelay.visibility = View.VISIBLE
        ui.prefAdjustmentDelay.visibility = View.VISIBLE
        ui.prefMonitoringDuration.visibility = View.VISIBLE
        ui.prefRename.visibility = if (device.address == FakeSpeakerDevice.ADDR) View.GONE else View.VISIBLE
        ui.prefDelete.visibility = View.VISIBLE
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