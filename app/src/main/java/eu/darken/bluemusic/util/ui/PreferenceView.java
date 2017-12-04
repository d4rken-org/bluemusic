package eu.darken.bluemusic.util.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.DrawableRes;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import butterknife.BindView;
import butterknife.ButterKnife;
import eu.darken.bluemusic.R;


public class PreferenceView extends RelativeLayout {
    @BindView(R.id.icon) ImageView icon;
    @BindView(R.id.title) TextView title;
    @BindView(R.id.description) TextView description;
    @BindView(R.id.extra) ViewGroup extraContainer;

    public PreferenceView(Context context) {
        this(context, null);
    }

    public PreferenceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PreferenceView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        View.inflate(getContext(), R.layout.view_preference, this);
        ButterKnife.bind(this);

        int padding = getResources().getDimensionPixelOffset(R.dimen.default_16dp_padding);
        setPadding(padding, padding, padding, padding);

        TypedArray typedArray = null;
        try {
            typedArray = getContext().getTheme().obtainStyledAttributes(new int[]{android.R.attr.selectableItemBackground});
            setBackground(typedArray.getDrawable(0));
        } finally {
            if (typedArray != null) typedArray.recycle();
            typedArray = null;
        }

        try {
            typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.PreferenceView);
            icon.setImageResource(typedArray.getResourceId(R.styleable.PreferenceView_pvIcon, 0));
            title.setText(typedArray.getResourceId(R.styleable.PreferenceView_pvTitle, 0));

            final int descId = typedArray.getResourceId(R.styleable.PreferenceView_pvDescription, 0);
            if (descId != 0) description.setText(descId);
            else description.setVisibility(GONE);
        } finally {
            if (typedArray != null) typedArray.recycle();
        }
    }

    public void addExtra(View view) {
        extraContainer.addView(view);
        extraContainer.setVisibility(view != null ? VISIBLE : GONE);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setEnabledRecursion(this, enabled);
    }

    private static void setEnabledRecursion(ViewGroup vg, boolean enabled) {
        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);
            if (child instanceof ViewGroup) setEnabledRecursion((ViewGroup) child, enabled);
            else child.setEnabled(enabled);
        }
    }

    public void setIcon(@DrawableRes int iconRes) {
        icon.setImageResource(iconRes);
    }

    public void setDescription(String desc) {
        description.setText(desc);
    }
}
