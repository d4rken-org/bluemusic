package eu.darken.bluemusic.util.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;


public class BasicViewHolder<ITEM> extends RecyclerView.ViewHolder {
    private ITEM item;

    public BasicViewHolder(View itemView) {
        super(itemView);
    }

    public BasicViewHolder(ViewGroup parent, @LayoutRes int layoutRes) {
        this(LayoutInflater.from(parent.getContext()).inflate(layoutRes, parent, false));
    }

    public void bind(ITEM item) {
        this.item = item;
    }

    public ITEM getItem() {
        return item;
    }

    public void post(Runnable runnable) {
        getRootView().post(runnable);
    }

    public View getRootView() {
        return itemView;
    }

    public Context getContext() {
        return this.itemView.getContext();
    }

    public String getString(@StringRes int string) {
        return getResources().getString(string);
    }

    public String getString(@StringRes int string, Object... args) {
        return getResources().getString(string, args);
    }

    public String getQuantityString(int id, int quantity, Object... formatArgs) throws Resources.NotFoundException {
        return getResources().getQuantityString(id, quantity, formatArgs);
    }

    public Resources getResources() {
        return getContext().getResources();
    }

    public int getColor(@ColorRes int colorRes) {
        return ContextCompat.getColor(getContext(), colorRes);
    }

    public Drawable getDrawable(@DrawableRes int drawableRes) {
        return ContextCompat.getDrawable(getContext(), drawableRes);
    }

    public LayoutInflater getLayoutInflater() {
        return LayoutInflater.from(getContext());
    }

}