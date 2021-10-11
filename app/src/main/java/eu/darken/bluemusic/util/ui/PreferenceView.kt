package eu.darken.bluemusic.util.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.annotation.DrawableRes
import eu.darken.bluemusic.R
import eu.darken.bluemusic.databinding.ViewPreferenceBinding

open class PreferenceView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {

    private val ui = ViewPreferenceBinding.inflate(LayoutInflater.from(context), this)

    init {
        getContext().theme.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground)).run {
            background = getDrawable(0)
            recycle()
        }

        getContext().obtainStyledAttributes(attrs, R.styleable.PreferenceView).run {
            ui.icon.setImageResource(getResourceId(R.styleable.PreferenceView_pvIcon, 0))
            ui.title.setText(getResourceId(R.styleable.PreferenceView_pvTitle, 0))
            val descId = getResourceId(R.styleable.PreferenceView_pvDescription, 0)
            if (descId != 0) ui.description.setText(descId) else ui.description.visibility = GONE
            recycle()
        }
    }

    override fun onFinishInflate() {
        val padding = resources.getDimensionPixelOffset(R.dimen.default_16dp_padding)
        setPadding(padding, padding, padding, padding)
        super.onFinishInflate()
    }

    fun addExtra(view: View?) {
        ui.extra.addView(view)
        ui.extra.visibility = if (view != null) VISIBLE else GONE
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        setEnabledRecursion(this, enabled)
    }

    fun setIcon(@DrawableRes iconRes: Int) {
        ui.icon.setImageResource(iconRes)
    }

    fun setDescription(desc: String?) {
        ui.description.text = desc
    }

    companion object {
        private fun setEnabledRecursion(vg: ViewGroup, enabled: Boolean) {
            for (i in 0 until vg.childCount) {
                val child = vg.getChildAt(i)
                if (child is ViewGroup) setEnabledRecursion(child, enabled) else child.isEnabled = enabled
            }
        }
    }
}