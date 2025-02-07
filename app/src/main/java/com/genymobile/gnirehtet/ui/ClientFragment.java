package com.genymobile.gnirehtet.ui;

import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.genymobile.gnirehtet.myadb.AdbConnection;
import com.cgutman.adblib.AdbCrypto;
import com.genymobile.gnirehtet.myadb.UsbChannel;
import com.genymobile.gnirehtet.GnirehtetActivity;
import com.genymobile.gnirehtet.R;
import com.genymobile.gnirehtet.data.UIViewModel;
import com.genymobile.gnirehtet.databinding.FragmentClientBinding;
import com.genymobile.gnirehtet.myadb.TcpForwarder;
import com.genymobile.gnirehtet.mydadb.UsbDadb;

import java.io.IOException;

public class ClientFragment extends Fragment {
    public static final String TAG = "GnirehtetClient";
    private static final int PORT = 31416;
    private static final String LOCAL_ABSTRACT_NAME = "gnirehtet";

    private FragmentClientBinding binding;
    private UsbManager mManager;
    private UIViewModel viewModel;
    private boolean useDadb = true;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        viewModel = new ViewModelProvider(requireActivity()).get(UIViewModel.class);
        viewModel.changeFragment("ClientFragment");

        binding = FragmentClientBinding.inflate(inflater, container, false);

        return binding.getRoot();

    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel.setEnableReverseConnection(active -> setConnectionActive(active));

        binding.buttonToServerScreen.setOnClickListener(v -> {
                    NavHostFragment.findNavController(ClientFragment.this)
                            .navigate(R.id.action_ClientFragment_to_ServerFragment);
                }
        );
        // Está activado si la conexión está inactiva.
        binding.buttonToServerScreen.setClickable(!viewModel.getActiveConnection().getValue());

        binding.buttonClientStatus.setOnClickListener(v -> {
                if (binding.buttonClientStatus.getText() == getResources().getString(R.string.disconnect)) {
                    stopLocalVPN();
                    try {
                        stopForwarding();
                    } catch (Exception e) {
                        Log.e(TAG, "Error when trying to close the TCP Forwarding of localabstract gnirehtet: ", e);
                    }
                    setConnectionActive(false);
                } else {
                    try {
                        Toast.makeText(getActivity(), "Trying to forward!", Toast.LENGTH_SHORT).show();
                        AdbCrypto adbCrypto = viewModel.getAdbCrypto().getValue();
                        UsbChannel usbChannel = viewModel.getUsbChannel().getValue();
                        if (usbChannel != null && adbCrypto != null) {
                            try {
                                setupForwarding();
                                startLocalVPN();
                                setConnectionActive(true);
                                Toast.makeText(getActivity(), "Forwarding! :)", Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                Log.e(TAG, "Error when trying to enable TCP Forwarding of localabstract gnirehtet: ", e);
                            }
                        } else {
                            Log.w(TAG, "Error when trying to enable TCP Forwarding of localabstract gnirehtet: AdbCrypto and/or UsbChannel were null.");
                        }
                    } catch (Exception e) {
                        Toast.makeText(getActivity(), "Could not forward :(", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Error when trying to enable TCP Forwarding of localabstract gnirehtet: ", e);
                    }
                }
            }
        );
    }

    public void startLocalVPN() {
        Intent vpnIntent = new Intent(getActivity(), GnirehtetActivity.class);
        vpnIntent.setAction(GnirehtetActivity.ACTION_GNIREHTET_START);
        startActivity(vpnIntent);
    }

    public void stopLocalVPN() {
        Intent vpnIntent = new Intent(getActivity(), GnirehtetActivity.class);
        vpnIntent.setAction(GnirehtetActivity.ACTION_GNIREHTET_STOP);
        startActivity(vpnIntent);
    }

    public void setupForwarding() throws IOException, InterruptedException {
        String host = "localabstract:".concat(LOCAL_ABSTRACT_NAME);
        String dest = "tcp:".concat(String.valueOf(PORT));

        AdbCrypto adbCrypto = viewModel.getAdbCrypto().getValue();
        UsbChannel usbChannel = viewModel.getUsbChannel().getValue();
        if (usbChannel != null && adbCrypto != null) {
            if (useDadb) {
                UsbDadb dadb = viewModel.getDadb().getValue() == null? new UsbDadb(adbCrypto, usbChannel) : viewModel.getDadb().getValue();
                AutoCloseable forwarder = dadb.forward(LOCAL_ABSTRACT_NAME, PORT);
                viewModel.setForwarder(forwarder);
                viewModel.setDadb(dadb);
            } else {
                AdbConnection adbConnection = AdbConnection.create(usbChannel, adbCrypto);
                adbConnection.connect();
                //adbConnection.open("shell:exec date");
                AutoCloseable forwarder = new TcpForwarder(adbConnection, host, dest);
                viewModel.setForwarder(forwarder);
                viewModel.setAdbConnection(adbConnection);
            }
        } else {
            Log.w(TAG, "Error when trying to enable TCP Forwarding of localabstract gnirehtet: AdbCrypto and/or UsbChannel were null.");
        }
    }

    public void stopForwarding() throws IOException {
        UsbDadb dadb = viewModel.getDadb().getValue();
        AutoCloseable forwarder = viewModel.getForwarder().getValue();
        if (forwarder != null) {
            try {
                forwarder.close();
            } catch (Exception e) {
                Log.e(TAG, "Error when trying to close the TCP Forwarding of localabstract gnirehtet: ", e);
            }
        }
        if (useDadb) {
            if (dadb != null)
                dadb.close();
        } else {
            AdbConnection adbConnection = viewModel.getAdbConnection().getValue();
            if (adbConnection != null)
                adbConnection.close();
        }
    }

    public void setConnectionActive(boolean active) {
        binding.buttonClientStatus.setText(active? R.string.disconnect : R.string.connect);
        viewModel.setActiveConnection(active);
        binding.buttonToServerScreen.setClickable(!active);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        try {
            if (viewModel.getActiveConnection().getValue()) {
                try {
                    stopForwarding();
                } catch (IOException e) {}
                viewModel.setActiveConnection(false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error when trying to close the TCP Forwarding of localabstract gnirehtet: ", e);
        }
    }

}