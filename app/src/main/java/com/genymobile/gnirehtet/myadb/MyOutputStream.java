package com.genymobile.gnirehtet.myadb;

import java.io.IOException;
import java.io.OutputStream;

import okio.BufferedSink;
import okio.Okio;

public class MyOutputStream extends OutputStream {
    private AdbStream stream;
    public MyOutputStream(AdbStream stream) {
        this.stream = stream;
        BufferedSink sink = Okio.buffer(Okio.sink(this));
    }

    @Override
    public void write(int i) throws IOException {
        try {
            stream.write(String.valueOf((char) i));
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }
}
