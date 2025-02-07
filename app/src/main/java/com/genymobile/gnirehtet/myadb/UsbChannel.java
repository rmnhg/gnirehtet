package com.genymobile.gnirehtet.myadb;

import static java.lang.System.currentTimeMillis;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbRequest;
import android.util.Log;

import com.genymobile.gnirehtet.myadb.AdbChannel;
import com.genymobile.gnirehtet.myadb.AdbMessage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.LinkedList;

/**
 * Created by xudong on 2/21/14.
 */
public class UsbChannel implements AdbChannel {
    private static final String TAG = "Gnirehtet USB";
    private final UsbDeviceConnection mDeviceConnection;
    private final UsbEndpoint mEndpointOut;
    private final UsbEndpoint mEndpointIn;
    private final UsbInterface mInterface;

    private final int defaultTimeout = 1000;
    private final int waitMsBeforeWrite = 100;

    private final LinkedList<UsbRequest> mInRequestPool = new LinkedList<UsbRequest>();

    private static int CMD_AUTH = 0x48545541;
    private static int CMD_CNXN = 0x4e584e43;
    private static int CMD_OPEN = 0x4e45504f;
    private static int CMD_OKAY = 0x59414b4f;
    private static int CMD_CLSE = 0x45534c43;
    private static int CMD_WRTE = 0x45545257;

    // return an IN request to the pool
    public void releaseInRequest(UsbRequest request) {
        synchronized (mInRequestPool) {
            mInRequestPool.add(request);
        }
    }


    // get an IN request from the pool
    public UsbRequest getInRequest() {
        synchronized (mInRequestPool) {
            if (mInRequestPool.isEmpty()) {
                UsbRequest request = new UsbRequest();
                request.initialize(mDeviceConnection, mEndpointIn);
                return request;
            } else {
                return mInRequestPool.removeFirst();
            }
        }
    }


    @Override
    public void readx(byte[] buffer, int length) throws IOException {
        /*String stacktrace = "";
        try {
            throw new IOException("Stacktrace que nos interesa :)");
        } catch (IOException e) {
            stacktrace = stacktrace.concat(Arrays.toString(e.getStackTrace()));
        }
        Log.d(TAG, "readx: stacktrace (length=".concat(String.valueOf(length)).concat("): ").concat(stacktrace));*/

        UsbRequest usbRequest = getInRequest();

        ByteBuffer expected = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
        usbRequest.setClientData(expected);

        if (!usbRequest.queue(expected, length)) {
            throw new IOException("fail to queue read UsbRequest");
        }

        while (true) {
            UsbRequest wait = mDeviceConnection.requestWait();

            if (wait == null) {
                throw new IOException("Connection.requestWait return null");
            }

            ByteBuffer clientData = (ByteBuffer) wait.getClientData();
            wait.setClientData(null);

            if (wait.getEndpoint() == mEndpointOut) {
                // a write UsbRequest complete, just ignore
            } else if (expected == clientData) {
                releaseInRequest(wait);
                break;

            } else {
                throw new IOException("unexpected behavior");
            }
        }
        expected.flip();
        expected.get(buffer);
        //Log.d(TAG, "USB_IO readx: buffer=".concat(getBytesAsString(buffer)));
        //Log.d(TAG, "USB_IO_hex readx: buffer=".concat(getBytesAsByteString(buffer)));
    }

    public String getBytesAsString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            char c = (char) bytes[i] <= '~'? (char) bytes[i] : '.';
            sb.append(c);
        }
        return sb.toString();
    }

    public String getBytesAsByteString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i == 0)
                sb.append('[');
            sb.append(String.format("%02X", bytes[i]));
            if (i != bytes.length-1)
                sb.append(", ");
            else
                sb.append(']');
        }
        return sb.toString();
    }

    // API LEVEL 18 is needed to invoke bulkTransfer(mEndpointOut, buffer, offset, buffer.length - offset, defaultTimeout)
//    @Override
//    public void writex(byte[] buffer) throws IOException{
//
//        int offset = 0;
//        int transferred = 0;
//
//        while ((transferred = mDeviceConnection.bulkTransfer(mEndpointOut, buffer, offset, buffer.length - offset, defaultTimeout)) >= 0) {
//            offset += transferred;
//            if (offset >= buffer.length) {
//                break;
//            }
//        }
//        if (transferred < 0) {
//            throw new IOException("bulk transfer fail");
//        }
//    }

    // A dirty solution, only API level 12 is needed, not 18
    private void writex(byte[] buffer) throws IOException{
        /*if (buffer.length > 16384) {
            Log.e(TAG, "writex: buffer.length > 16384!");
        } else {
            Log.d(TAG, "writex: buffer.length <= 16384 (".concat(String.valueOf(buffer.length)).concat(") B!"));
        }*/

        //Log.d(TAG, "USB_IO writex: buffer=".concat(getBytesAsString(buffer)));
        //Log.d(TAG, "USB_IO_hex writex: buffer=".concat(getBytesAsByteString(buffer)));

        int offset = 0;
        int transferred = 0;

        byte[] tmp = new byte[buffer.length];
        System.arraycopy(buffer, 0, tmp, 0, buffer.length);

        while ((transferred = mDeviceConnection.bulkTransfer(mEndpointOut, tmp, buffer.length - offset, defaultTimeout)) >= 0) {
            offset += transferred;
            if (offset >= buffer.length) {
                break;
            } else {
                System.arraycopy(buffer, offset, tmp, 0, buffer.length - offset);
            }
        }
        if (transferred < 0) {
            throw new IOException("bulk transfer fail");
        }
    }

    public String getCommand(AdbMessage message) {
        int cmd = message.getCommand();
        Log.d(TAG, "writex: cmd=".concat(String.valueOf(cmd)));
        if (cmd == CMD_CNXN)
            return "CNXN";
        else if (cmd == CMD_AUTH)
            return "AUTH";
        else if (cmd == CMD_OPEN)
            return "OPEN";
        else if (cmd == CMD_OKAY)
            return "OKAY";
        else if (cmd == CMD_CLSE)
            return "CLSE";
        else if (cmd == CMD_WRTE)
            return "WRTE";
        return "???";
    }

    // Quitado synchronized por el synchronized(sink) en AdbReader.readMessage() y en AdbWriter.write()
    @Override
    public synchronized void writex(AdbMessage message) throws IOException {
        // TODO: here is the weirdest thing
        // write (message.head + message.payload) is totally different with write(message.head) + write(head.payload)
        /*String stacktrace = "";
        try {
            throw new IOException("Stacktrace que nos interesa :)");
        } catch (IOException e) {
            stacktrace = stacktrace.concat(Arrays.toString(e.getStackTrace()));
        }
        Log.d(TAG, "writex: message.getMessage=".concat(getCommand(message)).concat(" con stacktrace: ").concat(stacktrace));*/
        writex(message.getMessage());
        if (message.getPayload() != null && message.getPayload().length > 0) { // Añadida segunda condición IMPORTANTE QUE PUEDE DAR PROBLKEMAS
            writex(message.getPayload());
        }
    }


    @Override
    public void close() throws IOException {
        mDeviceConnection.releaseInterface(mInterface);
        mDeviceConnection.close();
    }

    public UsbChannel(UsbDeviceConnection connection, UsbInterface intf) {
        mDeviceConnection = connection;
        mInterface = intf;

        UsbEndpoint epOut = null;
        UsbEndpoint epIn = null;
        // look for our bulk endpoints
        for (int i = 0; i < intf.getEndpointCount(); i++) {
            UsbEndpoint ep = intf.getEndpoint(i);
            if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                    epOut = ep;
                } else {
                    epIn = ep;
                }
            }
        }
        if (epOut == null || epIn == null) {
            throw new IllegalArgumentException("not all endpoints found");
        }
        mEndpointOut = epOut;
        mEndpointIn = epIn;
    }

}

