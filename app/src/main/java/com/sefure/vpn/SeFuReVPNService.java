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
import java.nio.channels.*;

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
    private PendingIntent pendingIntent;

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
        Notification notification = createNotification();
        startForeground(1, notification);

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
                FirebaseManager.logVPNStatus(true, getDeviceId());
            }
        } catch (Exception e) {
            Log.e(TAG, "VPN start error", e);
        }
    }

    private void stopVPN() {
        running = false;
        try {
            if (vpnInterface != null) {
                vpnInterface.close();
                vpnInterface = null;
            }
            if (vpnThread != null) {
                vpnThread.interrupt();
                vpnThread = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "VPN stop error", e);
        }
        FirebaseManager.logVPNStatus(false, getDeviceId());
        stopForeground(true);
        stopSelf();
    }

    private Notification createNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        pendingIntent = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SeFuRe VPN Connected")
            .setContentText("Your connection is secure")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID,
            "VPN Status",
            NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private class VPNRunnable implements Runnable {
        @Override
        public void run() {
            if (vpnInterface == null) return;

            FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
            FileOutputStream out = new FileOutputStream(vpnInterface.getFileDescriptor());
            ByteBuffer buffer = ByteBuffer.allocate(VPN_MTU);

            while (running) {
                try {
                    buffer.clear();
                    int length = in.read(buffer.array());
                    if (length > 0) {
                        downloadBytes += length;
                        buffer.limit(length);
                        out.write(buffer.array(), 0, length);
                        monitorAppUsage();
                    }
                } catch (IOException e) {
                    if (running) {
                        Log.e(TAG, "VPN tunnel error", e);
                    }
                    break;
                }
            }

            try {
                in.close();
                out.close();
            } catch (IOException e) {
                Log.e(TAG, "Stream close error", e);
            }
        }

        private void monitorAppUsage() {
            ActivityManager activityManager =
                (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            if (activityManager == null) return;

            java.util.List<ActivityManager.RunningAppProcessInfo> runningApps =
                activityManager.getRunningAppProcesses();
            if (runningApps == null) return;

            for (ActivityManager.RunningAppProcessInfo processInfo : runningApps) {
                if (processInfo.importance ==
                        ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    String[] packages = processInfo.pkgList;
                    if (packages != null && packages.length > 0) {
                        FirebaseManager.logAppUsage(
                            getDeviceId(),
                            packages[0],
                            downloadBytes,
                            uploadBytes,
                            System.currentTimeMillis()
                        );
                    }
                }
            }
        }
    }

    private String getDeviceId() {
        return Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    public long[] getDataUsage() {
        return new long[]{uploadBytes, downloadBytes};
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new VPNBinder();
    }

    public class VPNBinder extends Binder {
        public SeFuReVPNService getService() {
            return SeFuReVPNService.this;
        }
    }
}
