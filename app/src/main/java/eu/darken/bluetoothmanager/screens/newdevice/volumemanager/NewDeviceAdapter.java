package eu.darken.bluetoothmanager.screens.newdevice.volumemanager;

import android.bluetooth.BluetoothDevice;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import eu.darken.bluetoothmanager.R;


public class NewDeviceAdapter extends RecyclerView.Adapter<NewDeviceAdapter.DeviceVH> {
    private final List<BluetoothDevice> data;
    private final Callback callback;

    public NewDeviceAdapter(List<BluetoothDevice> devices, Callback callback) {
        this.data = devices;
        this.callback = callback;
    }

    @Override
    public DeviceVH onCreateViewHolder(ViewGroup parent, int viewType) {
        return new DeviceVH(LayoutInflater.from(parent.getContext()).inflate(R.layout.adapter_device_line, parent, false));
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
        void onDeviceSelected(BluetoothDevice bluetoothDevice);
    }

    public static class DeviceVH extends RecyclerView.ViewHolder {
        @BindView(R.id.name) TextView name;
        @BindView(R.id.caption) TextView caption;

        public DeviceVH(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }

        public void bind(BluetoothDevice item, Callback callback) {
            name.setText(item.getName());
            caption.setText(item.getAddress());
            this.itemView.setOnClickListener(view -> callback.onDeviceSelected(item));
        }
    }
}
