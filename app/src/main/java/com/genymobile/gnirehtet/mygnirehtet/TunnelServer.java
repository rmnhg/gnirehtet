package com.genymobile.gnirehtet.mygnirehtet;

import com.genymobile.gnirehtet.relay.Client;
import com.genymobile.gnirehtet.relay.Log;
import com.genymobile.gnirehtet.relay.SelectionHandler;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * Handle the connections from the clients.
 */
public class TunnelServer {

    private static final String TAG = com.genymobile.gnirehtet.relay.TunnelServer.class.getSimpleName();

    private final List<Client> clients = new ArrayList<>();
    private ServerSocketChannel serverSocketChannel;

    public TunnelServer(int port, Selector selector) throws IOException {
        this.serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        // ServerSocketChannel.bind() requires API 24
        serverSocketChannel.socket().bind(new InetSocketAddress(port));

        SelectionHandler socketChannelHandler = (selectionKey) -> {
            try {
                ServerSocketChannel channel = (ServerSocketChannel) selectionKey.channel();
                acceptClient(selector, channel);
            } catch (IOException e) {
                Log.e(TAG, "Cannot accept client", e);
            }
        };
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT, socketChannelHandler);
    }

    private void acceptClient(Selector selector, ServerSocketChannel serverSocketChannel) throws IOException {
        SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.configureBlocking(false);
        // will register the socket on the selector
        Client client = new Client(selector, socketChannel, this::removeClient);
        clients.add(client);
        Log.i(TAG, "Client #".concat(String.valueOf(client.getId())).concat(" connected"));
    }

    private void removeClient(Client client) {
        clients.remove(client);
        Log.i(TAG, "Client #".concat(String.valueOf(client.getId())).concat(" disconnected"));
    }

    public void cleanUp() {
        for (Client client : clients) {
            client.cleanExpiredConnections();
        }
    }

    public void close() throws IOException {
        serverSocketChannel.close();
    }
}
