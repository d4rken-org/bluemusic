package eu.darken.bluemusic.screens.volumes;

import android.content.Context;
import android.support.annotation.StringRes;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import eu.darken.bluemusic.R;
import eu.darken.bluemusic.core.database.ManagedDevice;

import static android.graphics.Typeface.BOLD;
import static android.graphics.Typeface.NORMAL;


public class VolumesAdapter extends RecyclerView.Adapter<VolumesAdapter.DeviceVH> {
    private final List<ManagedDevice> data;
    private final Callback callback;

    public VolumesAdapter(List<ManagedDevice> devices, Callback callback) {
        this.data = devices;
        this.callback = callback;
    }

    @Override
    public DeviceVH onCreateViewHolder(ViewGroup parent, int viewType) {
        return new DeviceVH(LayoutInflater.from(parent.getContext()).inflate(R.layout.adapter_volumes_line, parent, false));
    }

    @Override
    public void onBindViewHolder(DeviceVH holder, int position) {
        holder.bind(data.get(position), callback);
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    public interface Callback {
    }

    public static class DeviceVH extends RecyclerView.ViewHolder {
        private final Context context;
        @BindView(R.id.name) TextView name;
        @BindView(R.id.caption) TextView caption;
        @BindView(R.id.seekbar) SeekBar seekbar;

        public DeviceVH(View itemView) {
            super(itemView);
            context = itemView.getContext();
            ButterKnife.bind(this, itemView);
        }

        public void bind(ManagedDevice item, Callback callback) {
            name.setText(item.getName());
            name.setTypeface(null, item.isActive() ? BOLD : NORMAL);
            caption.setText(
                    item.isActive() ?
                            getString(R.string.state_connected) :
                            getString(R.string.last_seen_x, item.getLastConnected())
            );

            seekbar.setMax(15);
            seekbar.setProgress(Math.round(item.getVolumePercentage() * 15));
        }

        public String getString(@StringRes int stringRes) {
            return context.getString(stringRes);
        }

        public String getString(@StringRes int stringRes, Object... objects) {
            return context.getString(stringRes, objects);
        }
    }
}
