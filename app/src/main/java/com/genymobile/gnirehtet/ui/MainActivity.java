package com.genymobile.gnirehtet.ui;

import static com.genymobile.gnirehtet.usbdadb.Message.CONNECTING;
import static com.genymobile.gnirehtet.usbdadb.Message.DEVICE_FOUND;
import static com.genymobile.gnirehtet.usbdadb.Message.DEVICE_NOT_FOUND;
import static com.genymobile.gnirehtet.usbdadb.Message.DISCONNECTING;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;

import com.cgutman.adblib.AdbCrypto;
import com.genymobile.gnirehtet.GnirehtetActivity;
import com.genymobile.gnirehtet.data.UIViewModel;
import com.genymobile.gnirehtet.mydadb.AdbConnection;
import com.genymobile.gnirehtet.usbdadb.Message;
import com.genymobile.gnirehtet.usbdadb.MyAdbBase64;
import com.genymobile.gnirehtet.R;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.widget.Toast;

import com.genymobile.gnirehtet.databinding.ActivityMainBinding;

import com.genymobile.gnirehtet.myadb.UsbChannel;
import com.cgutman.adblib.AdbBase64;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "Gnirehtet";
    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    private UsbManager mManager;
    private UsbDevice mDevice;
    private Handler handler;
    private AdbCrypto adbCrypto;
    private AdbConnection adbConnection;
    private boolean receiversRegistered = false;
    private UIViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(this).get(UIViewModel.class);

        mManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        mManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull android.os.Message msg) {
                switch (msg.what) {
                    case DEVICE_FOUND:
                        Toast.makeText(MainActivity.this, getResources().getString(R.string.handler_device_found), Toast.LENGTH_SHORT).show();
                        break;

                    case CONNECTING:
                        Toast.makeText(MainActivity.this, getResources().getString(R.string.handler_connecting), Toast.LENGTH_SHORT).show();
                        break;

                    case DEVICE_NOT_FOUND:
                        Toast.makeText(MainActivity.this, getResources().getString(R.string.handler_device_not_found), Toast.LENGTH_SHORT).show();
                        cleanViewModel();
                        resetFragmentConnections();
                        break;

                    case DISCONNECTING:
                        Toast.makeText(MainActivity.this, getResources().getString(R.string.handler_disconnecting), Toast.LENGTH_SHORT).show();
                        cleanViewModel();
                        resetFragmentConnections();
                        break;
                }
            }
        };

        // Creamos el adbCrypto
        AdbBase64 base64 = new MyAdbBase64();
        try {
            adbCrypto = AdbCrypto.loadAdbKeyPair(base64, new File(getFilesDir(), "adbKey"),
                    new File(getFilesDir(), "adbKey.pub"));
        } catch (Exception e) {
            Log.e(TAG, "onCreate: ", e);
        }

        if (adbCrypto == null) {
            try {
                adbCrypto = AdbCrypto.generateAdbKeyPair(base64);
                adbCrypto.saveAdbKeyPair(new File(getFilesDir(), "adbKey"), new File(getFilesDir(), "adbKey.pub"));
            } catch (Exception e) {
                Log.w(TAG, "failed to generate and save key-pair", e);
            }
        }
        viewModel.setAdbCrypto(adbCrypto);

        registerReceivers();

        //Check USB
        UsbDevice device = getIntent().getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (device != null) {
            asyncRefreshAdbConnection(device);
        } else {
            for (String k : mManager.getDeviceList().keySet()) {
                UsbDevice usbDevice = mManager.getDeviceList().get(k);
                handler.sendEmptyMessage(CONNECTING);
                if (mManager.hasPermission(usbDevice)) { ;
                    asyncRefreshAdbConnection(usbDevice);
                } else {
                    mManager.requestPermission(
                            usbDevice,
                            PendingIntent.getBroadcast(getApplicationContext(),
                                    0,
                                    new Intent(Message.USB_PERMISSION),
                                    PendingIntent.FLAG_IMMUTABLE));
                }
            }
        }


        binding.fab.setOnClickListener(view ->
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAnchorView(R.id.fab)
                .setAction("Action", null).show());
    }

    BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                Log.d(TAG, "USB device detached.");
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                String deviceName = device.getDeviceName();
                if (mDevice != null && mDevice.getDeviceName().equals(deviceName)) {
                    try {
                        Log.d(TAG, "setAdbInterface(null, null)");
                        setAdbInterface(null, null);
                    } catch (Exception e) {
                        Log.w(TAG, "setAdbInterface(null,null) failed", e);
                    }
                }
            } else if (Message.USB_PERMISSION.equals(action) || UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                Log.d(TAG, "USB device attached and USB permission granted!");
                UsbDevice usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                handler.sendEmptyMessage(CONNECTING);
                if (usbDevice != null) {
                    if (mManager.hasPermission(usbDevice))
                        asyncRefreshAdbConnection(usbDevice);
                    else
                        mManager.requestPermission(usbDevice,PendingIntent.getBroadcast(getApplicationContext(), 0, new Intent(Message.USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE));
                }
            } else {
                Log.d(TAG, "USB permission denied or action different! getAction = [".concat(action).concat("], action = [").concat(Message.USB_PERMISSION).concat("]"));
            }
        }
    };

    public void registerReceivers() {
        // Only register if not already registered
        if (!receiversRegistered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
            filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
            filter.addAction(Message.USB_PERMISSION);
            //ContextCompat.registerReceiver(this, mUsbReceiver, filter, RECEIVER_NOT_EXPORTED);
            registerReceiver(mUsbReceiver, filter);
            receiversRegistered = true;
        }
    }

    public void asyncRefreshAdbConnection(final UsbDevice device) {
        if (device != null) {
            new Thread() {
                @Override
                public void run() {
                    final UsbInterface intf = findAdbInterface(device);
                    try {
                        setAdbInterface(device, intf);
                    } catch (Exception e) {
                        Log.w(TAG, "setAdbInterface(device, intf) fail", e);
                    }
                }
            }.start();
        }
    }

    // searches for an adb interface on the given USB device
    private UsbInterface findAdbInterface(UsbDevice device) {
        int count = device.getInterfaceCount();
        for (int i = 0; i < count; i++) {
            UsbInterface intf = device.getInterface(i);
            if (intf.getInterfaceClass() == 255 && intf.getInterfaceSubclass() == 66 &&
                    intf.getInterfaceProtocol() == 1) {
                return intf;
            }
        }
        return null;
    }

    // Sets the current USB device and interface
    private synchronized boolean setAdbInterface(UsbDevice device, UsbInterface intf) throws IOException, InterruptedException {
        if (adbConnection != null) {
            adbConnection.close();
            adbConnection = null;
            mDevice = null;
        }

        if (device != null && intf != null) {
            UsbDeviceConnection connection = mManager.openDevice(device);
            if (connection != null) {
                if (connection.claimInterface(intf, false)) {
                    handler.sendEmptyMessage(CONNECTING);
                    UsbChannel usbChannel = new UsbChannel(connection, intf);
                    viewModel.setUsbChannel(usbChannel);

                    mDevice = device;
                    handler.sendEmptyMessage(DEVICE_FOUND);
                    return true;
                } else {
                    handler.sendEmptyMessage(DISCONNECTING);
                    connection.close();
                }
            }
        }

        handler.sendEmptyMessage(DEVICE_NOT_FOUND);
        Log.d(TAG, "Device not found: device= ".concat(device.toString()).concat("; intf=").concat(intf.toString()).concat(";"));
        Toast.makeText(MainActivity.this, "Device not found: device= ".concat(device.toString()).concat("; intf=").concat(intf.toString()).concat(";"), Toast.LENGTH_SHORT).show();

        mDevice = null;
        return false;
    }

    public void cleanViewModel() {
        viewModel.setDadb(null);
        viewModel.setActiveConnection(false);
        viewModel.setAdbConnection(null);
        viewModel.setReverser(null);
        viewModel.setForwarder(null);
        viewModel.setUsbChannel(null);
    }

    public void resetFragmentConnections() {
        try {
            if (viewModel.getEnableForwardConnection().getValue() != null)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    viewModel.getEnableForwardConnection().getValue().accept(false);
                }
            if (viewModel.getEnableReverseConnection().getValue() != null)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    viewModel.getEnableReverseConnection().getValue().accept(false);
                }
            stopLocalVPN();
        } catch (Exception e) {
            // Ignore it
            Log.e(TAG, "Ignored Exception when closing connections: ", e);
        }
    }

    public void stopLocalVPN() {
        Intent vpnIntent = new Intent(this, GnirehtetActivity.class);
        vpnIntent.setAction(GnirehtetActivity.ACTION_GNIREHTET_STOP);
        startActivity(vpnIntent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceivers();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        registerReceivers();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (receiversRegistered) {
            unregisterReceiver(mUsbReceiver);
            receiversRegistered = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (receiversRegistered) {
            unregisterReceiver(mUsbReceiver);
            receiversRegistered = false;
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }
}