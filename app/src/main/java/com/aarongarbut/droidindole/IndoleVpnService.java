package com.aarongarbut.droidindole;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.util.Pair;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.List;

public class IndoleVpnService extends VpnService {
    static Thread thread;
    static String TAG = "DROID_INDOLE_DEBUG";
    int mtu = 1400;
    List<Pair<String, Integer>> address = new ArrayList<Pair<String, Integer>>() {{
        add(new Pair<>("10.8.0.2", 32));
    }};
    List<Pair<String, Integer>> route = new ArrayList<Pair<String, Integer>>() {{
        add(new Pair<>("0.0.0.0", 0));
    }};
    List<String> dns = new ArrayList<String>() {{
        add("8.8.8.8");
    }};
    List<String> application = new ArrayList<String>() {{
        add("com.android.chrome");
    }};

    @Override
    public void onCreate() {
//        final PackageManager pm = getPackageManager();
//        List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
//
//        for (ApplicationInfo packageInfo : packages) {
//            Log.d(TAG, "Installed package :" + packageInfo.packageName);
//            Log.d(TAG, "Source dir : " + packageInfo.sourceDir);
//            Log.d(TAG, "Launch Activity :" + pm.getLaunchIntentForPackage(packageInfo.packageName));
//        }
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        synchronized (this) {
            if (thread != null) {
                thread.interrupt();
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            thread = new Thread(() -> {
                Builder builder = new Builder();
                builder.setBlocking(true);
                builder.setMtu(mtu);
                application.forEach(app -> {
                    try {
                        builder.addAllowedApplication(app);
                    } catch (PackageManager.NameNotFoundException e) {
                        e.printStackTrace();
                    }
                });
                address.forEach(pair -> builder.addAddress(pair.first, pair.second));
                route.forEach(pair -> builder.addRoute(pair.first, pair.second));
                dns.forEach(builder::addDnsServer);
                ParcelFileDescriptor descriptor = builder.establish();
                ParcelFileDescriptor.AutoCloseInputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor);
                ParcelFileDescriptor.AutoCloseOutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(descriptor);
                try (DatagramChannel channel = DatagramChannel.open()) {
                    channel.configureBlocking(true);
                    channel.connect(new InetSocketAddress("127.0.0.1", 54345));
                    Thread in = new Thread(() -> {
                        ByteBuffer packet = ByteBuffer.allocate(mtu);
                        //noinspection InfiniteLoopStatement
                        while (true) {
                            try {
                                int length = input.read(packet.array());
                                packet.limit(length);
                                channel.write(packet);
                                packet.clear();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }) {{
                        start();
                    }};
                    Thread out = new Thread(() -> {
                        ByteBuffer packet = ByteBuffer.allocate(mtu);
                        //noinspection InfiniteLoopStatement
                        while (true) {
                            try {
                                packet.clear();
                                int length = channel.read(packet);
                                output.write(packet.array(), 0, length);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }) {{
                        start();
                    }};

                    try {
                        in.join();
                        out.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            input.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        try {
                            output.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        channel.close();
                        in.interrupt();
                        out.interrupt();
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    thread = null;
                }
            }) {{
                start();
            }};
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: ");
    }
}
