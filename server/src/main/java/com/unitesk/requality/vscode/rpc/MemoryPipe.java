package com.unitesk.requality.vscode.rpc;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * An in-memory byte pipe between two JSON-RPC connections in one JVM. (A java.io piped stream breaks when the thread
 * that last wrote to it ends, e.g. a server job thread; the stdio of the real server has no such tie to threads.)
 */
public final class MemoryPipe {
  private static final byte[] EOF = new byte[0];
  private final LinkedBlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
  private byte[] current;
  private int pos;

  public final OutputStream out = new OutputStream() {
    @Override
    public void write(int b) {
      queue.add(new byte[] { (byte) b });
    }

    @Override
    public void write(byte[] b, int off, int len) {
      if (len > 0)
        queue.add(Arrays.copyOfRange(b, off, off + len));
    }

    @Override
    public void close() {
      queue.add(EOF);
    }
  };

  public final InputStream in = new InputStream() {
    @Override
    public int read() throws IOException {
      byte[] one = new byte[1];
      return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
    }

    @Override
    public synchronized int read(byte[] b, int off, int len) throws IOException {
      if (len == 0)
        return 0;
      while (current == null || pos >= current.length) {
        if (current == EOF)
          return -1;
        try {
          current = queue.take();
        } catch (InterruptedException e) {
          throw new InterruptedIOException();
        }
        pos = 0;
      }
      if (current == EOF)
        return -1;
      int n = Math.min(len, current.length - pos);
      System.arraycopy(current, pos, b, off, n);
      pos += n;
      return n;
    }
  };
}
