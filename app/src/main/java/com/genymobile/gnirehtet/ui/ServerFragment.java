package com.genymobile.gnirehtet.ui;

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

import com.cgutman.adblib.AdbCrypto;
import com.genymobile.gnirehtet.R;
import com.genymobile.gnirehtet.data.UIViewModel;
import com.genymobile.gnirehtet.databinding.FragmentServerBinding;
import com.genymobile.gnirehtet.myadb.AdbConnection;
import com.genymobile.gnirehtet.myadb.TcpForwarder;
import com.genymobile.gnirehtet.myadb.UsbChannel;
import com.genymobile.gnirehtet.mydadb.UsbDadb;
import com.genymobile.gnirehtet.mygnirehtet.Relay;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;

public class ServerFragment extends Fragment {
    private FragmentServerBinding binding;
    private UIViewModel viewModel;
    private static final String TAG = "GnirehtetServer";
    private static final int PORT = 31416;
    private static final String LOCAL_ABSTRACT_NAME = "gnirehtet";

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        viewModel = new ViewModelProvider(requireActivity()).get(UIViewModel.class);
        // Se establece el fragmento actual
        viewModel.changeFragment("ServerFragment");
        // Se carga la vista
        binding = FragmentServerBinding.inflate(inflater, container, false);

        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel.setEnableReverseConnection(active -> setReverseConnectionActive(active));

        binding.buttonToClientScreen.setOnClickListener(v -> {
                    NavHostFragment.findNavController(ServerFragment.this)
                            .navigate(R.id.action_ServerFragment_to_ClientFragment);
                }
        );

        binding.buttonServerStatus.setOnClickListener(v -> {
                    if (binding.buttonServerStatus.getText() == getResources().getString(R.string.stop)) {
                        stopRelay();
                        setConnectionActive(false);
                    } else {
                        startRelay();
                        setConnectionActive(true);
                    }
                }
        );

        binding.buttonReverseClientStatus.setOnClickListener(v -> {
                    if (binding.buttonReverseClientStatus.getText() == getResources().getString(R.string.stop)) {
                        try {
                            stopReversing();
                        } catch (Exception e) {
                            Log.e(TAG, "Error when trying to close the TCP Reversing of TCP port 31416: ", e);
                        }
                        setReverseConnectionActive(false);
                    } else {
                        try {
                            Toast.makeText(getActivity(), "Trying to reverse!", Toast.LENGTH_SHORT).show();
                            AdbCrypto adbCrypto = viewModel.getAdbCrypto().getValue();
                            UsbChannel usbChannel = viewModel.getUsbChannel().getValue();
                            if (usbChannel != null && adbCrypto != null) {
                                try {
                                    setupReversing();
                                    Toast.makeText(getActivity(), "Reversing! :)", Toast.LENGTH_SHORT).show();
                                    setReverseConnectionActive(true);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error when trying to enable TCP Reversing of TCP port 31416: ", e);
                                }
                            } else {
                                Log.w(TAG, "Error when trying to enable TCP Reversing of TCP port 31416: AdbCrypto and/or UsbChannel were null.");
                            }
                        } catch (Exception e) {
                            Toast.makeText(getActivity(), "Could not forward :(", Toast.LENGTH_SHORT).show();
                            Log.e(TAG, "Error when trying to enable TCP Reversing of TCP port 31416: ", e);
                        }
                    }
                }
        );
    }

    public void setConnectionActive(boolean active) {
        binding.buttonServerStatus.setText(active? R.string.stop : R.string.start);
        viewModel.setActiveConnection(active);
        binding.buttonServerStatus.setClickable(!active);
    }

    public void setupReversing() {
        AdbCrypto adbCrypto = viewModel.getAdbCrypto().getValue();
        UsbChannel usbChannel = viewModel.getUsbChannel().getValue();
        if (usbChannel != null && adbCrypto != null) {
            UsbDadb dadb = viewModel.getDadb().getValue() == null? new UsbDadb(adbCrypto, usbChannel) : viewModel.getDadb().getValue();
            try {
                AutoCloseable startRemoteVPN = dadb.open("shell:exec am start -a com.genymobile.gnirehtet.START " +
                        "-n com.genymobile.gnirehtet/.GnirehtetActivity");
                Thread.sleep(5000);
                startRemoteVPN.close();
            } catch (Exception e) {
                Log.e(TAG, "Error when trying to close the ADB Stream for the remote VPN intent: ", e);
            }

            AutoCloseable reverser = dadb.reverse(PORT, LOCAL_ABSTRACT_NAME);
            viewModel.setReverser(reverser);
            viewModel.setDadb(dadb);
        } else {
            Log.w(TAG, "Error when trying to enable TCP Forwarding of localabstract gnirehtet: AdbCrypto and/or UsbChannel were null.");
        }
    }

    public void stopReversing() {
        UsbDadb dadb = viewModel.getDadb().getValue();
        AutoCloseable reverser = viewModel.getReverser().getValue();
        if (reverser != null) {
            try {
                reverser.close();
            } catch (Exception e) {
                Log.e(TAG, "Error when trying to close the TCP Reversing of TCP port 31416: ", e);
            }
        }
        if (dadb != null)
            dadb.close();
    }

    public void startRelay() {
        Relay relay = new Relay(PORT);
        Thread relayThread = new Thread(() -> {
            try {
                relay.run();
            } catch (IOException e) {
                Log.e(TAG, "Failed to start relay server: ", e);
            }
        });
        relayThread.start();
        if (relayThread.isAlive()) {
            viewModel.setRelayThread(relayThread);
            viewModel.setRelay(relay);
            Log.i(TAG, "The relay thread is alive, so we are " +
                    "going to store it with its relay instance.");
        } else {
            Log.w(TAG, "The relay thread is not alive, so we are not storing it!");
        }
    }
    public void setReverseConnectionActive(boolean active) {
        binding.buttonReverseClientStatus.setText(active? R.string.stop : R.string.start);
    }

    public void stopRelay() {
        try {
            if (viewModel.getRelayThread().getValue() != null) {
                viewModel.getRelay().getValue().stop(); // Is it still necessary?
                Thread relayThread = viewModel.getRelayThread().getValue();
                viewModel.getRelayThread().getValue().interrupt();
                //viewModel.getRelayThread().getValue().join();
                viewModel.setRelayThread(null);
                viewModel.setRelay(null);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop the relay server: ", e);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}