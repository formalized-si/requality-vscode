package com.unitesk.requality.vscode.core;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One Requality server per catalog: a server (of VS Code or the headless one of the MCP server) holds an operating
 * system lock of every catalog it loads, so two processes do not write the files and caches of one catalog. The lock
 * files are in {@code ~/.requality/locks} (one per canonical catalog path), outside the catalog; the system releases a
 * lock when its process ends.
 * <p>
 * A process that waits for a catalog asks the holder to give it up: it touches {@code <lock>.want}, and a holder that
 * can (the MCP server, between calls) releases the catalog ({@link #wanted}).
 * <p>
 * A file lock of the system belongs to the process, and closing any channel of the file releases it (POSIX). So the
 * locks of the process are kept in one table of the JVM, and a lock file is opened only while no lock of it is held.
 * The lock files are deleted when released; a global lock file (never deleted) is held while a lock file is opened and
 * locked, and while it is deleted, so that no process locks a lock file that another one deletes.
 */
public final class CatalogLocks {
  /**
   * How long a load waits for the lock: a restarted server waits for the previous one to exit, which closes its catalogs
   * in at most 30 seconds, and an idle MCP server gives a wanted catalog up in less.
   */
  static final long WAIT_MS = 45_000;
  /** A wish older than this is not a wish any more (the process that waited is gone). */
  static final long WANT_MS = 10_000;
  private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");

  /** The locks of this process by lock file; the monitor of the table is held while a lock file is opened and locked. */
  private static final Map<String, FileLock> HELD = new HashMap<>();
  /** The channels of the global lock files by folder (open while the process runs). */
  private static final Map<String, FileChannel> GLOBAL = new HashMap<>();
  /** Channels that must stay open: closing them would release a lock of this process. */
  private static final List<FileChannel> KEPT = new ArrayList<>();

  private final File dir;
  private final long waitMs;

  public CatalogLocks(File dir) {
    this(dir, WAIT_MS);
  }

  CatalogLocks(File dir, long waitMs) {
    this.dir = dir;
    this.waitMs = waitMs;
  }

  /**
   * The locks of the user ({@code -Drequality.locks=<dir>} puts them elsewhere and {@code -Drequality.lockWait=<ms>}
   * changes the wait, as the tests do).
   */
  public static CatalogLocks forUser() {
    String d = System.getProperty("requality.locks");
    return new CatalogLocks(d != null && !d.isEmpty() ? new File(d)
        : new File(new File(System.getProperty("user.home"), ".requality"), "locks"), Long.getLong("requality.lockWait", WAIT_MS));
  }

  /** The folder of the lock files. */
  public File folder() {
    return dir;
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

  private File file(File catalog, String suffix) throws IOException {
    return new File(dir, key(catalog) + suffix).getAbsoluteFile();
  }

  /** True when this process holds the lock of the catalog. */
  public boolean holds(File catalog) {
    try {
      String k = file(catalog, ".lock").getPath();
      synchronized (HELD) {
        return HELD.containsKey(k);
      }
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Takes the lock of the catalog; false when another process holds it after waiting a while. The wait asks the holder
   * to give the catalog up.
   */
  public boolean acquire(File catalog) throws IOException {
    return acquire(catalog, waitMs);
  }

  /** Takes the lock of the catalog if no other process holds it now. */
  public boolean tryAcquire(File catalog) throws IOException {
    return acquire(catalog, 0);
  }

  private boolean acquire(File catalog, long waitMs) throws IOException {
    File f = file(catalog, ".lock");
    String k = f.getPath();
    long end = System.currentTimeMillis() + waitMs;
    long asked = 0;
    while (true) {
      synchronized (HELD) {
        if (HELD.containsKey(k))
          return true;
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory())
          throw new IOException("Cannot create the folder of the catalog locks " + dir);
        FileLock global = global();
        try {
          FileChannel ch = FileChannel.open(f.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
          FileLock lock;
          try {
            lock = ch.tryLock();
          } catch (OverlappingFileLockException e) {
            // a lock of this JVM that is not in the table (cannot happen): the channel stays open, the lock stays
            KEPT.add(ch);
            return true;
          } catch (IOException | RuntimeException e) {
            ch.close();
            throw e;
          }
          if (lock != null) {
            try {
              // the process that holds the lock, for a person who looks for it
              ch.truncate(0);
              ch.write(java.nio.ByteBuffer.wrap((ProcessHandle.current().pid() + " " + catalog.getCanonicalPath() + "\n")
                  .getBytes(StandardCharsets.UTF_8)));
            } catch (IOException e) {
              // the lock holds without the note
            }
            HELD.put(k, lock);
            // the wish of this process is fulfilled (another waiter asks again within a second)
            file(catalog, ".want").delete();
            return true;
          }
          // no lock of this process is on the file: closing the channel releases nothing
          ch.close();
        } finally {
          global.release();
        }
      }
      long now = System.currentTimeMillis();
      if (now > end)
        return false;
      if (now - asked >= 1000) {
        want(catalog);
        asked = now;
      }
      try {
        Thread.sleep(250);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
  }

  /** Locks the global lock file of the folder (called with the monitor of the table held). */
  private FileLock global() throws IOException {
    String g = new File(dir, "locks.lock").getAbsolutePath();
    FileChannel ch = GLOBAL.get(g);
    if (ch == null || !ch.isOpen()) {
      ch = FileChannel.open(new File(g).toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
      GLOBAL.put(g, ch);
    }
    // other processes hold it only while they open, lock or delete a lock file
    return ch.lock();
  }

  /** Asks the process that holds the catalog to give it up. */
  public void want(File catalog) {
    try {
      File w = file(catalog, ".want");
      if (!w.exists())
        w.createNewFile();
      w.setLastModified(System.currentTimeMillis());
    } catch (IOException e) {
      // no folder of the locks: nobody can be asked
    }
  }

  /** True when another process asked for the catalog in the last seconds ({@link #want}). */
  public boolean wanted(File catalog) {
    try {
      File w = file(catalog, ".want");
      long t = w.lastModified();
      return t != 0 && System.currentTimeMillis() - t < WANT_MS;
    } catch (IOException e) {
      return false;
    }
  }

  /** Releases the lock of the catalog, if this process holds it; its lock file goes. */
  public void release(File catalog) {
    try {
      File f = file(catalog, ".lock");
      synchronized (HELD) {
        FileLock lock = HELD.remove(f.getPath());
        if (lock == null)
          return;
        close(f, lock);
      }
    } catch (IOException e) {
      // no key: nothing is held
    }
  }

  /** Releases the locks of the catalogs of this folder of locks. */
  public void releaseAll() {
    synchronized (HELD) {
      String prefix = dir.getAbsolutePath() + File.separator;
      for (java.util.Iterator<Map.Entry<String, FileLock>> it = HELD.entrySet().iterator(); it.hasNext();) {
        Map.Entry<String, FileLock> e = it.next();
        if (!e.getKey().startsWith(prefix))
          continue;
        it.remove();
        close(new File(e.getKey()), e.getValue());
      }
    }
  }

  /** Deletes the lock file and releases its lock (called with the monitor of the table held). */
  private void close(File f, FileLock lock) {
    FileLock global = null;
    try {
      global = global();
      // Windows does not delete an open file: the file stays (and is used again)
      if (!WINDOWS)
        f.delete();
    } catch (IOException e) {
      // the file stays
    }
    try {
      lock.release();
      lock.channel().close();
    } catch (IOException e) {
      // released with the channel
    } finally {
      if (global != null)
        try {
          global.release();
        } catch (IOException e) {
          // released when the process ends
        }
    }
  }
}
