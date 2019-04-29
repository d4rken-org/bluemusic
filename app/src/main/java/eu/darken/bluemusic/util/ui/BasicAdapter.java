package eu.darken.bluemusic.util.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import androidx.recyclerview.widget.RecyclerView;


public abstract class BasicAdapter<VIEWHOLDER extends BasicViewHolder<? super DATA>, DATA> extends RecyclerView.Adapter<VIEWHOLDER> {
    private final List<DATA> data = new ArrayList<>();

    public void setData(List<DATA> newData) {
        data.clear();
        if (newData != null) data.addAll(newData);
    }

    public List<DATA> getData() {
        return data;
    }

    public DATA getItem(int position) {
        return data.get(position);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    @Override
    public void onBindViewHolder(VIEWHOLDER holder, int position) {
        holder.bind(getItem(position));
    }

    @Override
    public VIEWHOLDER onCreateViewHolder(ViewGroup parent, int viewType) {
        return onCreateBaseViewHolder(LayoutInflater.from(parent.getContext()), parent, viewType);
    }

    public abstract VIEWHOLDER onCreateBaseViewHolder(LayoutInflater inflater, ViewGroup parent, int viewType);

    @Override
    public void onViewRecycled(VIEWHOLDER holder) {
        super.onViewRecycled(holder);
    }

}

