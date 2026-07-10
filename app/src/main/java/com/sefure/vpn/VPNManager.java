package com.sefure.vpn;

import android.content.Context;
import java.util.*;

public class VPNManager {
    private Context context;
    private long lastUploadBytes = 0;
    private long lastDownloadBytes = 0;
    private long lastUpdateTime = System.currentTimeMillis();
    
    public VPNManager(Context context) {
        this.context = context;
    }
    
    public float[] getCurrentSpeed() {
        long currentTime = System.currentTimeMillis();
        long timeDiff = (currentTime - lastUpdateTime) / 1000; // seconds
        
        if (timeDiff == 0) timeDiff = 1;
        
        // Simulated speed for demo
        Random random = new Random();
        float uploadSpeed = random.nextFloat() * 100 + 50; // 50-150 KB/s
        float downloadSpeed = random.nextFloat() * 500 + 200; // 200-700 KB/s
        
        lastUpdateTime = currentTime;
        
        return new float[]{uploadSpeed, downloadSpeed};
    }
    
    public String getTotalDataUsage() {
        // Simulated data usage
        long totalMB = new Random().nextInt(100) + 50;
        return String.format("%d MB", totalMB);
    }
}