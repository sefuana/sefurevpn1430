package com.sefure.vpn;

import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import com.google.firebase.database.*;
import java.util.*;

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
                for (DataSnapshot deviceSnapshot : snapshot.getChildren()) {
                    DeviceInfo device = deviceSnapshot.getValue(DeviceInfo.class);
                    if (device != null) {
                        deviceList.add(device);
                    }
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
        // Load and display device usage details
        loadDeviceUsage(device.getDeviceId());
    }
    
    private void loadDeviceUsage(String deviceId) {
        DatabaseReference usageRef = FirebaseManager.getUsageReference(deviceId);
        usageRef.limitToLast(50).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                TextView usageText = findViewById(R.id.usage_details);
                StringBuilder details = new StringBuilder();
                
                for (DataSnapshot usageSnapshot : snapshot.getChildren()) {
                    Map<String, Object> usage = (Map<String, Object>) usageSnapshot.getValue();
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
    
    // Device model class
    public static class DeviceInfo {
        private String deviceId;
        private String deviceName;
        private boolean connected;
        private boolean vpnActive;
        private long lastOnline;
        
        public DeviceInfo() {}
        
        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
        public String getDeviceName() { return deviceName; }
        public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
        public boolean isConnected() { return connected; }
        public void setConnected(boolean connected) { this.connected = connected; }
        public boolean isVpnActive() { return vpnActive; }
        public void setVpnActive(boolean vpnActive) { this.vpnActive = vpnActive; }
        public long getLastOnline() { return lastOnline; }
        public void setLastOnline(long lastOnline) { this.lastOnline = lastOnline; }
    }
    
    // Simple adapter
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