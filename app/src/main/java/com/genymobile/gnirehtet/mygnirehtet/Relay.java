package com.genymobile.gnirehtet.mygnirehtet;

import com.genymobile.gnirehtet.relay.Log;
import com.genymobile.gnirehtet.relay.SelectionHandler;
import com.genymobile.gnirehtet.relay.UDPConnection;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Set;

public class Relay {
    private static final String TAG = Relay.class.getSimpleName();
    private static final int CLEANING_INTERVAL = 60 * 1000;
    private final int port;
    private boolean run;
    public Relay(int port) {
        this.port = port;
        this.run = false;
    }

    public void run() throws IOException {
        this.run = true;
        Selector selector = Selector.open();

        // will register the socket on the selector
        TunnelServer tunnelServer = new TunnelServer(port, selector);

        Log.i(TAG, "Relay server started");

        long nextCleaningDeadline = System.currentTimeMillis() + UDPConnection.IDLE_TIMEOUT;
        while (this.run) {
            long timeout = Math.max(0, nextCleaningDeadline - System.currentTimeMillis());
            selector.select(timeout);
            Set<SelectionKey> selectedKeys = selector.selectedKeys();

            long now = System.currentTimeMillis();
            if (now >= nextCleaningDeadline || selectedKeys.isEmpty()) {
                tunnelServer.cleanUp();
                nextCleaningDeadline = now + CLEANING_INTERVAL;
            }

            for (SelectionKey selectedKey : selectedKeys) {
                SelectionHandler selectionHandler = (SelectionHandler) selectedKey.attachment();
                selectionHandler.onReady(selectedKey);
            }
            // by design, we handled everything
            selectedKeys.clear();
        }
        try {
            tunnelServer.cleanUp();
            tunnelServer.close();
            Log.i(TAG, "Relay server stopped");
        } catch (IOException e) {
            Log.e(TAG, "Failed to stop relay server: ", e);
        }
    }

    public void stop() {
        Log.i(TAG, "Stopping relay server...");
        this.run = false;
    }
}
