package com.aarongarbut.droidindole;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Pair;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public class IndoleVpnService extends VpnService {
    static Thread thread;
    int mtu;
    String hostname;
    int port;

    List<Pair<String, Integer>> address = new ArrayList<Pair<String, Integer>>() {{
        add(new Pair<>("10.8.0.2", 32));
    }};
    List<Pair<String, Integer>> route = new ArrayList<Pair<String, Integer>>() {{
        add(new Pair<>("0.0.0.0", 0));
    }};
    List<String> dns;
    List<String> application;

    @Override
    public void onCreate() {
        SharedPreferences sharedPreferences = getSharedPreferences("", MODE_PRIVATE);
        mtu = sharedPreferences.getInt("MTU", 1400);
        hostname = sharedPreferences.getString("HostName", "localhost");
        port = sharedPreferences.getInt("Port", 3023);
        dns = new ArrayList<>(sharedPreferences.getStringSet("DNS", new HashSet<String>() {{
            add("8.8.8.8");
        }}));
        application = new ArrayList<>(sharedPreferences.getStringSet("Application", new HashSet<String>() {{
            add("com.android.chrome");
        }}));
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
            switch (Objects.requireNonNull(intent.getAction())) {
                case "T":
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
                            channel.connect(new InetSocketAddress(hostname, port));
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
                    return START_STICKY;
                case "F":
                    stopSelf();
                    return START_NOT_STICKY;
            }
            return START_NOT_STICKY;
        }
    }
}
