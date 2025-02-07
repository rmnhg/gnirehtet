package com.genymobile.gnirehtet.myadb;

public class AdbTCPForward {
    private AdbConnection connection;
    private AdbStream stream;
    private String host;
    private String target;
    private TcpForwarder forwarder;

    public AdbTCPForward(AdbConnection connection, String host, String target) {
        this.connection = connection;
        this.host = host;
        this.target = target;
        this.forwarder = new TcpForwarder(connection, host, target);
    }
}
