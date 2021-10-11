package eu.darken.bluemusic.bluetooth.ui.discover

import android.view.LayoutInflater
import android.view.ViewGroup
import eu.darken.bluemusic.R
import eu.darken.bluemusic.bluetooth.core.FakeSpeakerDevice
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.bluetooth.ui.discover.DiscoverAdapter.DeviceVH
import eu.darken.bluemusic.databinding.ViewholderDeviceBinding
import eu.darken.bluemusic.util.DeviceHelper
import eu.darken.bluemusic.util.ui.BasicViewHolder
import eu.darken.bluemusic.util.ui.ClickableAdapter

class DiscoverAdapter : ClickableAdapter<DeviceVH, SourceDevice>() {
    override fun onCreateBaseViewHolder(inflater: LayoutInflater, parent: ViewGroup, viewType: Int): DeviceVH {
        return DeviceVH(parent)
    }

    class DeviceVH(parent: ViewGroup) : BasicViewHolder<SourceDevice>(parent, R.layout.viewholder_device) {

        private val ui: ViewholderDeviceBinding by lazy { ViewholderDeviceBinding.bind(itemView) }

        override fun bind(item: SourceDevice) {
            super.bind(item)
            ui.name.text = DeviceHelper.getAliasAndName(item)
            ui.caption.text = item.address
            ui.icon.setImageResource(DeviceHelper.getIconForDevice(item))
            if (item.address == FakeSpeakerDevice.ADDR) {
                ui.name.setTextColor(getColor(R.color.colorAccent))
            } else {
                ui.name.setTextColor(getColor(android.R.color.primary_text_dark))
            }
        }
    }
}