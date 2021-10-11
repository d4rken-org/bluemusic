package eu.darken.bluemusic.main.ui.config

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import eu.darken.bluemusic.R
import eu.darken.bluemusic.util.AppTool

class LaunchAppAdapter(private val apps: List<AppTool.Item>) : BaseAdapter() {
    override fun getCount(): Int = apps.size

    override fun getItem(i: Int): AppTool.Item = apps[i]

    override fun getItemId(i: Int): Long = apps[i].hashCode().toLong()

    @SuppressLint("InflateParams") override fun getView(position: Int, _convertView: View?, parent: ViewGroup): View {
        var convertView = _convertView
        val holder: ViewHolder
        if (convertView != null) {
            holder = convertView.tag as ViewHolder
        } else {
            convertView = LayoutInflater.from(parent.context).inflate(R.layout.adapter_app_line, null)
            holder = ViewHolder(convertView)
            convertView.tag = holder
        }
        holder.bind(getItem(position))
        return convertView!!
    }

    class ViewHolder(layout: View) {
        val icon = layout.findViewById<ImageView>(R.id.icon)
        val appName = layout.findViewById<TextView>(R.id.name)
        val packageName = layout.findViewById<TextView>(R.id.pkg)

        fun bind(item: AppTool.Item) {
            appName.text = item.appName
            packageName.text = item.packageName
            icon.setImageDrawable(item.appIcon)
        }
    }
}