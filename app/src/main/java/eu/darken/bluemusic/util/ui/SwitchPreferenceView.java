package eu.darken.bluemusic.util.ui;

import android.content.Context;
import android.util.AttributeSet;

import androidx.appcompat.widget.SwitchCompat;


public class SwitchPreferenceView extends PreferenceView {
    private SwitchCompat toggle;

    public SwitchPreferenceView(Context context) {
        super(context);
    }

    public SwitchPreferenceView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SwitchPreferenceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        toggle = new SwitchCompat(getContext());
        toggle.setClickable(false);
        addExtra(toggle);
        setOnClickListener(view -> performClick());
        super.onFinishInflate();
    }

    public boolean isChecked() {
        return toggle.isChecked();
    }

    public void setChecked(boolean checked) {
        toggle.setChecked(checked);
    }

    public void setOnCheckedChangedListener(Listener listener) {
        setOnClickListener(view -> {
            toggle.setChecked(!toggle.isChecked());
            listener.onCheckedChanged(SwitchPreferenceView.this, isChecked());
        });
    }

    public interface Listener {
        void onCheckedChanged(SwitchPreferenceView view, boolean checked);
    }
}
