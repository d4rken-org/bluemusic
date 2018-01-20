package eu.darken.bluemusic.util.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorRes;
import android.support.annotation.DrawableRes;
import android.support.annotation.LayoutRes;
import android.support.annotation.StringRes;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import butterknife.ButterKnife;


public class BasicViewHolder<ITEM> extends RecyclerView.ViewHolder {
    private ITEM item;

    public BasicViewHolder(View itemView) {
        super(itemView);
    }

    public BasicViewHolder(ViewGroup parent, @LayoutRes int layoutRes) {
        this(LayoutInflater.from(parent.getContext()).inflate(layoutRes, parent, false));
        ButterKnife.bind(this, itemView);
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