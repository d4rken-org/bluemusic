package eu.darken.bluemusic.util.ui;

import android.view.View;


public abstract class ClickableAdapter<VIEWHOLDER extends BasicViewHolder<DATA>, DATA> extends BasicAdapter<VIEWHOLDER, DATA> {
    private OnItemClickListener<DATA> itemClickListener;
    private OnItemLongClickListener<DATA> itemLongClickListener;

    public interface OnItemClickListener<DATA> {

        void onItemClick(View view, int position, DATA item);
    }

    public interface OnItemLongClickListener<DATA> {

        boolean onItemLongClick(View view, int position, DATA item);
    }

    public void setItemClickListener(OnItemClickListener<DATA> itemClickListener) {
        this.itemClickListener = itemClickListener;
    }

    public void setItemLongClickListener(OnItemLongClickListener<DATA> itemLongClickListener) {
        this.itemLongClickListener = itemLongClickListener;
    }

    @Override
    public void onBindViewHolder(VIEWHOLDER holder, int position) {
        super.onBindViewHolder(holder, position);
        if (itemClickListener != null) {
            holder.itemView.setOnClickListener(view -> itemClickListener.onItemClick(holder.itemView, position, getItem(position)));
        }
        if (itemLongClickListener != null) {
            holder.itemView.setOnLongClickListener(view -> itemLongClickListener.onItemLongClick(holder.itemView, position, getItem(position)));
        }
    }
}
