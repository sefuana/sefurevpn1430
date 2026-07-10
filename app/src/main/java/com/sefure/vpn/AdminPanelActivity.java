package com.sefure.vpn;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AdminPanelActivity extends AppCompatActivity {
    private RecyclerView devicesRecyclerView;
    private LinearLayout detailPanel;
    private DatabaseReference devicesRef;
    private List<DeviceInfo> deviceList = new ArrayList<>();
    private DeviceAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        devicesRecyclerView = findViewById(R.id.devices_list);
        detailPanel = findViewById(R.id.detail_panel);

        devicesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DeviceAdapter(deviceList, this::showDeviceDetails);
        devicesRecyclerView.setAdapter(adapter);

        devicesRef = FirebaseManager.getAllDevices();
        loadDevices();
    }

    private void loadDevices() {
        devicesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                deviceList.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    DeviceInfo device = child.getValue(DeviceInfo.class);
                    if (device != null) deviceList.add(device);
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(AdminPanelActivity.this,
                    "Error loading devices", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showDeviceDetails(DeviceInfo device) {
        detailPanel.setVisibility(View.VISIBLE);
        loadDeviceUsage(device.getDeviceId());
    }

    private void loadDeviceUsage(String deviceId) {
        DatabaseReference usageRef = FirebaseManager.getUsageReference(deviceId);
        usageRef.limitToLast(50).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                TextView usageText = findViewById(R.id.usage_details);
                StringBuilder details = new StringBuilder();
                for (DataSnapshot snap : snapshot.getChildren()) {
                    Map<String, Object> usage = (Map<String, Object>) snap.getValue();
                    if (usage != null) {
                        details.append("App: ").append(usage.get("packageName"))
                               .append("\nData: ").append(usage.get("downloadBytes"))
                               .append(" bytes\n\n");
                    }
                }
                usageText.setText(details.toString());
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    public static class DeviceInfo {
        private String deviceId, deviceName;
        private boolean connected, vpnActive;
        private long lastOnline;

        public DeviceInfo() {}
        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String d) { this.deviceId = d; }
        public String getDeviceName() { return deviceName; }
        public void setDeviceName(String d) { this.deviceName = d; }
        public boolean isConnected() { return connected; }
        public void setConnected(boolean c) { this.connected = c; }
        public boolean isVpnActive() { return vpnActive; }
        public void setVpnActive(boolean v) { this.vpnActive = v; }
        public long getLastOnline() { return lastOnline; }
        public void setLastOnline(long l) { this.lastOnline = l; }
    }

    class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {
        private List<DeviceInfo> devices;
        private OnDeviceClickListener listener;

        DeviceAdapter(List<DeviceInfo> devices, OnDeviceClickListener listener) {
            this.devices = devices;
            this.listener = listener;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_device, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            DeviceInfo device = devices.get(position);
            holder.deviceName.setText(device.getDeviceName());
            holder.statusIcon.setImageResource(
                device.isConnected() ? R.drawable.ic_online : R.drawable.ic_offline);
            holder.itemView.setOnClickListener(v -> listener.onDeviceClick(device));
        }

        @Override
        public int getItemCount() { return devices.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView deviceName;
            ImageView statusIcon;

            ViewHolder(View itemView) {
                super(itemView);
                deviceName = itemView.findViewById(R.id.device_name);
                statusIcon = itemView.findViewById(R.id.status_icon);
            }
        }
    }

    interface OnDeviceClickListener {
        void onDeviceClick(DeviceInfo device);
    }
}
