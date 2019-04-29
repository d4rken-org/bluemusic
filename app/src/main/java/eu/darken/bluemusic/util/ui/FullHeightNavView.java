package eu.darken.bluemusic.util.ui;

import android.content.Context;
import android.util.AttributeSet;

import com.google.android.material.navigation.NavigationView;

public class FullHeightNavView extends NavigationView {
    public FullHeightNavView(Context context) {
        super(context);
    }

    public FullHeightNavView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FullHeightNavView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, 0);
    }
}