package com.sefure.vpn;

import android.app.*;
import android.content.*;
import android.net.VpnService;
import android.os.*;
import android.provider.Settings;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import java.io.*;
import java.nio.*;

public class SeFuReVPNService extends VpnService {
    private static final String TAG = "SeFuReVPN";
    private static final String VPN_ADDRESS = "10.0.0.2";
    private static final String VPN_ROUTE = "0.0.0.0";
    private static final int VPN_MTU = 1500;
    private static final String CHANNEL_ID = "vpn_channel";

    private ParcelFileDescriptor vpnInterface;
    private Thread vpnThread;
    private volatile boolean running = false;
    private long uploadBytes = 0;
    private long downloadBytes = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if ("CONNECT".equals(action)) {
                startVPN();
            } else if ("DISCONNECT".equals(action)) {
                stopVPN();
            }
        }
        return START_STICKY;
    }

    private void startVPN() {
        startForeground(1, createNotification());
        try {
            Builder builder = new Builder();
            builder.setSession("SeFuRe VPN");
            builder.addAddress(VPN_ADDRESS, 32);
            builder.addRoute(VPN_ROUTE, 0);
            builder.addDnsServer("8.8.8.8");
            builder.addDnsServer("8.8.4.4");
            builder.setMtu(VPN_MTU);
            vpnInterface = builder.establish();
            if (vpnInterface != null) {
                running = true;
                vpnThread = new Thread(new VPNRunnable());
                vpnThread.start();
                FirebaseManager.logVPNStatus(true, fetchDeviceId());
            }
        } catch (Exception e) {
            Log.e(TAG, "VPN start error", e);
        }
    }

    private void stopVPN() {
        running = false;
        try {
            if (vpnInterface != null) { vpnInterface.close(); vpnInterface = null; }
            if (vpnThread != null) { vpnThread.interrupt(); vpnThread = null; }
        } catch (IOException e) {
            Log.e(TAG, "VPN stop error", e);
        }
        FirebaseManager.logVPNStatus(false, fetchDeviceId());
        stopForeground(true);
        stopSelf();
    }

    private Notification createNotification() {
        PendingIntent pi = PendingIntent.getActivity(this, 0,
            new Intent(this, MainActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SeFuRe VPN Connected")
            .setContentText("Your connection is secure")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID, "VPN Status", NotificationManager.IMPORTANCE_LOW);
        NotificationManager mgr = getSystemService(NotificationManager.class);
        if (mgr != null) mgr.createNotificationChannel(channel);
    }

    private class VPNRunnable implements Runnable {
        @Override
        public void run() {
            if (vpnInterface == null) return;
            try {
                FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
                FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor());
                byte[] buffer = new byte[VPN_MTU];
                while (running) {
                    int length = in.read(buffer);
                    if (length > 0) {
                        downloadBytes += length;
                        out.write(buffer, 0, length);
                        monitorAppUsage();
                    }
                }
                in.close();
                out.close();
            } catch (IOException e) {
                if (running) Log.e(TAG, "Tunnel error", e);
            }
        }

        private void monitorAppUsage() {
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (am == null) return;
            java.util.List<ActivityManager.RunningAppProcessInfo> apps =
                am.getRunningAppProcesses();
            if (apps == null) return;
            for (ActivityManager.RunningAppProcessInfo p : apps) {
                if (p.importance ==
                        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    if (p.pkgList != null && p.pkgList.length > 0) {
                        FirebaseManager.logAppUsage(fetchDeviceId(), p.pkgList[0],
                            downloadBytes, uploadBytes, System.currentTimeMillis());
                    }
                }
            }
        }
    }

    private String fetchDeviceId() {
        return Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    public long[] getDataUsage() { return new long[]{uploadBytes, downloadBytes}; }

    @Override
    public IBinder onBind(Intent intent) { return new VPNBinder(); }

    public class VPNBinder extends Binder {
        public SeFuReVPNService getService() { return SeFuReVPNService.this; }
    }
}
