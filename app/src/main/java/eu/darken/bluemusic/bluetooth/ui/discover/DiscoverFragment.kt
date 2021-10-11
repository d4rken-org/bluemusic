package eu.darken.bluemusic.bluetooth.ui.discover

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import eu.darken.bluemusic.R
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.databinding.FragmentLayoutDiscoverBinding
import eu.darken.bluemusic.util.ui.ClickableAdapter
import eu.darken.bluemusic.util.viewBinding
import eu.darken.mvpbakery.base.MVPBakery.Companion.builder
import eu.darken.mvpbakery.base.ViewModelRetainer
import eu.darken.mvpbakery.injection.InjectedPresenter
import eu.darken.mvpbakery.injection.PresenterInjectionCallback
import javax.inject.Inject

class DiscoverFragment : Fragment(), DiscoverPresenter.View, ClickableAdapter.OnItemClickListener<SourceDevice> {

    @Inject lateinit var presenter: DiscoverPresenter
    val ui: FragmentLayoutDiscoverBinding by viewBinding()
    val adapter: DiscoverAdapter = DiscoverAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_layout_discover, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ui.recyclerview.layoutManager = LinearLayoutManager(context)
        ui.recyclerview.adapter = adapter
        adapter.setItemClickListener(this)
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        builder<DiscoverPresenter.View, DiscoverPresenter>()
                .presenterFactory(InjectedPresenter(this))
                .presenterRetainer(ViewModelRetainer(this))
                .addPresenterCallback(PresenterInjectionCallback(this))
                .attach(this)
        super.onActivityCreated(savedInstanceState)
        val actionBar = (requireActivity() as AppCompatActivity).supportActionBar!!
        actionBar.setDisplayHomeAsUpEnabled(true)
        actionBar.setTitle(R.string.label_add_device)
        actionBar.subtitle = null
    }

    override fun showDevices(devices: List<SourceDevice>) {
        ui.recyclerview.visibility = View.VISIBLE
        ui.progressbar.visibility = View.INVISIBLE
        adapter.data = devices
        adapter.notifyDataSetChanged()
    }

    override fun onItemClick(view: View, position: Int, item: SourceDevice) {
        presenter.onAddDevice(item)
    }

    override fun showError(error: Throwable) {
        Snackbar.make(requireView(), error.message?.toString() ?: error.toString(), Snackbar.LENGTH_SHORT).show()
    }

    override fun showProgress() {
        ui.recyclerview.visibility = View.INVISIBLE
        ui.progressbar.visibility = View.VISIBLE
    }

    override fun closeScreen() {
        requireActivity().finish()
    }

    override fun showUpgradeDialog() {
        AlertDialog.Builder(requireContext())
                .setTitle(R.string.label_premium_version)
                .setMessage(R.string.description_premium_required_additional_devices)
                .setIcon(R.drawable.ic_stars_white_24dp)
                .setPositiveButton(R.string.action_upgrade) { dialogInterface: DialogInterface?, i: Int -> presenter.onPurchaseUpgrade(activity) }
                .setNegativeButton(R.string.action_cancel) { dialogInterface: DialogInterface?, i: Int -> }
                .show()
    }

    companion object {
        @JvmStatic fun newInstance(): Fragment {
            return DiscoverFragment()
        }
    }
}