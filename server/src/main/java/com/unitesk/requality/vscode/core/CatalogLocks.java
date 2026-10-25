package com.unitesk.requality.vscode.core;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * One Requality server per catalog: a server (of VS Code or the headless one of the MCP server) holds an operating
 * system lock of every catalog it loads, so two processes do not write the files and caches of one catalog. The lock
 * files are in {@code ~/.requality/locks} (one per canonical catalog path), outside the catalog; the system releases a
 * lock when its process ends.
 */
public final class CatalogLocks {
  /** How long a load waits for the lock (a restarted server waits for the previous one to exit). */
  static final long WAIT_MS = 10_000;

  private final File dir;
  private final long waitMs;
  private final Map<String, FileLock> held = new HashMap<>();

  public CatalogLocks(File dir) {
    this(dir, WAIT_MS);
  }

  CatalogLocks(File dir, long waitMs) {
    this.dir = dir;
    this.waitMs = waitMs;
  }

  /** The locks of the user ({@code -Drequality.locks=<dir>} puts them elsewhere, as the tests do). */
  public static CatalogLocks forUser() {
    String d = System.getProperty("requality.locks");
    return new CatalogLocks(d != null && !d.isEmpty() ? new File(d)
        : new File(new File(System.getProperty("user.home"), ".requality"), "locks"));
  }

  private static String key(File catalog) throws IOException {
    try {
      byte[] h = MessageDigest.getInstance("SHA-256").digest(catalog.getCanonicalPath().getBytes(StandardCharsets.UTF_8));
      StringBuilder b = new StringBuilder();
      for (int i = 0; i < 16; i++)
        b.append(String.format("%02x", h[i]));
      return b.toString();
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IOException(e);
    }
  }

  /** Takes the lock of the catalog; false when another process holds it (after waiting a while). */
  public boolean acquire(File catalog) throws IOException {
    String k = key(catalog);
    synchronized (held) {
      if (held.containsKey(k))
        return true;
    }
    dir.mkdirs();
    File f = new File(dir, k + ".lock");
    long end = System.currentTimeMillis() + waitMs;
    while (true) {
      FileChannel ch = FileChannel.open(f.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
      FileLock lock;
      try {
        lock = ch.tryLock();
      } catch (OverlappingFileLockException e) {
        lock = null;
      }
      if (lock != null) {
        ch.truncate(0);
        ch.write(java.nio.ByteBuffer.wrap((ProcessHandle.current().pid() + " " + catalog.getCanonicalPath() + "\n")
            .getBytes(StandardCharsets.UTF_8)));
        synchronized (held) {
          held.put(k, lock);
        }
        return true;
      }
      ch.close();
      if (System.currentTimeMillis() > end)
        return false;
      try {
        Thread.sleep(250);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
  }

  /** Releases the lock of the catalog, if this process holds it. */
  public void release(File catalog) {
    FileLock lock;
    try {
      String k = key(catalog);
      synchronized (held) {
        lock = held.remove(k);
      }
    } catch (IOException e) {
      return;
    }
    close(lock);
  }

  public void releaseAll() {
    synchronized (held) {
      for (FileLock l : held.values())
        close(l);
      held.clear();
    }
  }

  private static void close(FileLock lock) {
    if (lock == null)
      return;
    try {
      lock.release();
      lock.channel().close();
    } catch (IOException e) {
      // released with the channel
    }
  }
}
