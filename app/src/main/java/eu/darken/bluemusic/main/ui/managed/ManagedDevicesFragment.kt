package eu.darken.bluemusic.main.ui.managed

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import eu.darken.bluemusic.R
import eu.darken.bluemusic.bluetooth.ui.BluetoothActivity
import eu.darken.bluemusic.databinding.FragmentLayoutManagedDevicesBinding
import eu.darken.bluemusic.main.core.audio.AudioStream
import eu.darken.bluemusic.main.core.database.ManagedDevice
import eu.darken.bluemusic.main.ui.MainActivity
import eu.darken.bluemusic.main.ui.config.ConfigFragment.Companion.instantiate
import eu.darken.bluemusic.settings.ui.SettingsActivity
import eu.darken.bluemusic.util.ActivityUtil
import eu.darken.bluemusic.util.Check
import eu.darken.bluemusic.util.viewBinding
import eu.darken.mvpbakery.base.MVPBakery.Companion.builder
import eu.darken.mvpbakery.base.StateForwarder
import eu.darken.mvpbakery.base.ViewModelRetainer
import eu.darken.mvpbakery.injection.InjectedPresenter
import eu.darken.mvpbakery.injection.PresenterInjectionCallback
import javax.inject.Inject

class ManagedDevicesFragment : Fragment(), ManagedDevicesPresenter.View, ManagedDevicesAdapter.Callback {
    private val ui: FragmentLayoutManagedDevicesBinding by viewBinding()

    @Inject lateinit var presenter: ManagedDevicesPresenter
    private lateinit var adapter: ManagedDevicesAdapter
    private var isProVersion = false
    private var bluetoothEnabled = false
    private val stateForwarder = StateForwarder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        stateForwarder.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_layout_managed_devices, container, false)
    }

    @SuppressLint("InlinedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.recyclerview.layoutManager = LinearLayoutManager(activity)
        adapter = ManagedDevicesAdapter(this)
        ui.recyclerview.adapter = adapter
        ViewCompat.setNestedScrollingEnabled(ui.recyclerview, false)
        ui.bluetoothDisabledContainer.setOnClickListener { v: View? -> presenter.showBluetoothSettingsScreen() }

        ui.fab.setOnClickListener {
            ActivityUtil.tryStartActivity(this, Intent(context, BluetoothActivity::class.java))
        }

        super.onViewCreated(view, savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        stateForwarder.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        builder<ManagedDevicesPresenter.View, ManagedDevicesPresenter>()
                .presenterFactory(InjectedPresenter(this))
                .presenterRetainer(ViewModelRetainer(this))
                .stateForwarder(stateForwarder)
                .addPresenterCallback(PresenterInjectionCallback(this))
                .attach(this)
        super.onActivityCreated(savedInstanceState)
        Check.notNull(activity)
        val actionBar = (requireActivity() as MainActivity).supportActionBar!!
        actionBar.setDisplayHomeAsUpEnabled(false)
        actionBar.setTitle(R.string.app_name)
        actionBar.subtitle = null
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_main, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.upgrade).isVisible = !isProVersion
        val btIcon = DrawableCompat.wrap(menu.findItem(R.id.bluetooth_settings).icon!!)
        val btStateColor = if (bluetoothEnabled) android.R.color.white else R.color.state_m3
        DrawableCompat.setTint(btIcon, ContextCompat.getColor(requireContext(), btStateColor))
        menu.findItem(R.id.bluetooth_settings).icon = btIcon
        super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.upgrade -> {
                AlertDialog.Builder(requireContext())
                        .setTitle(R.string.label_premium_version)
                        .setMessage(R.string.description_premium_upgrade_explanation)
                        .setIcon(R.drawable.ic_stars_white_24dp)
                        .setPositiveButton(R.string.action_upgrade) { dialogInterface: DialogInterface?, i: Int -> presenter.onUpgradeClicked(activity) }
                        .setNegativeButton(R.string.action_cancel) { dialogInterface: DialogInterface?, i: Int -> }
                        .show()
                true
            }
            R.id.bluetooth_settings -> {
                presenter.showBluetoothSettingsScreen()
                true
            }
            R.id.settings -> {
                ActivityUtil.tryStartActivity(requireActivity(), Intent(context, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @SuppressLint("RestrictedApi") override fun displayBluetoothState(enabled: Boolean) {
        bluetoothEnabled = enabled
        ui.bluetoothDisabledContainer.visibility = if (enabled) View.GONE else View.VISIBLE
        ui.bluetoothEnabledContainer.visibility = if (enabled) View.VISIBLE else View.GONE
        ui.fab.visibility = if (enabled) View.VISIBLE else View.GONE
        requireActivity().invalidateOptionsMenu()
    }

    override fun updateUpgradeState(isProVersion: Boolean) {
        this.isProVersion = isProVersion
        requireActivity().invalidateOptionsMenu()
    }

    override fun displayDevices(managedDevices: List<ManagedDevice>) {
        adapter.data = managedDevices
        adapter.notifyDataSetChanged()
        ui.recyclerview.visibility = if (managedDevices.isEmpty()) View.GONE else View.VISIBLE
        ui.emptyInfo.visibility = if (managedDevices.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onStreamAdjusted(device: ManagedDevice, type: AudioStream.Type, percentage: Float) {
        when (type) {
            AudioStream.Type.MUSIC -> presenter.onUpdateMusicVolume(device, percentage)
            AudioStream.Type.CALL -> presenter.onUpdateCallVolume(device, percentage)
            AudioStream.Type.RINGTONE -> presenter.onUpdateRingVolume(device, percentage)
            AudioStream.Type.NOTIFICATION -> presenter.onUpdateNotificationVolume(device, percentage)
            AudioStream.Type.ALARM -> presenter.onUpdateAlarmVolume(device, percentage)
        }
    }

    override fun onShowConfigScreen(device: ManagedDevice) {
        requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.frame_content, instantiate(device))
                .addToBackStack(null)
                .commit()
    }

    override fun displayBatteryOptimizationHint(display: Boolean, intent: Intent) {
        ui.batterySavingHintDismissAction.setOnClickListener { v: View? -> presenter.onBatterySavingDismissed() }
        ui.batterySavingHintShowAction.setOnClickListener { v: View? -> ActivityUtil.tryStartActivity(this, intent) }
        ui.batterySavingHintContainer.visibility = if (display) View.VISIBLE else View.GONE
    }

    override fun displayAndroid10AppLaunchHint(display: Boolean, intent: Intent) {
        ui.android10ApplaunchHintDismissAction.setOnClickListener { v: View? -> presenter.onAppLaunchHintDismissed() }
        ui.android10ApplaunchHintShowAction.setOnClickListener { v: View? -> ActivityUtil.tryStartActivity(this, intent) }
        ui.android10ApplaunchHintContainer.visibility = if (display) View.VISIBLE else View.GONE
    }

    companion object {
        @JvmStatic fun newInstance(): Fragment {
            return ManagedDevicesFragment()
        }
    }
}