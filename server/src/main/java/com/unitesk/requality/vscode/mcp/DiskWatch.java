package com.unitesk.requality.vscode.mcp;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.ITreeStorage;

/**
 * The node files of a catalog that changed on disk after the server loaded them (git pull, another editor): a tool
 * would read the tree the server has in memory, which is out of date. Before a call the server compares the node files
 * ({@code root.json} and the {@code .json} files under {@code root/}) with the ones it saw last; a file whose content
 * is not the node the tree has (a new, changed or deleted node) makes the server load the project again. The files the
 * server wrote itself have the content of the tree.
 */
final class DiskWatch {
  /** A catalog is looked at, at most, once in this time ({@code -Drequality.diskWatch=<ms>}; the tests change it). */
  static long period() {
    return Long.getLong("requality.diskWatch", 2_000);
  }

  /** The node files of a catalog: path relative to the catalog -> {modified time, size}. */
  private static final class Snapshot {
    final Map<String, long[]> files = new HashMap<>();
    final long time = System.currentTimeMillis();
  }

  private final Map<String, Snapshot> last = new HashMap<>();

  /** Forgets a catalog (it was loaded again: the next look starts over). */
  synchronized void forget(File catalog) {
    last.remove(catalog.getAbsolutePath());
  }

  /** Looks at the files of a catalog that was loaded now: the next look compares with them. */
  synchronized void loaded(File catalog) {
    last.put(catalog.getAbsolutePath(), scan(catalog));
  }

  /**
   * The first node file of the catalog (relative to it) that changed on disk in a way the tree of the server does not
   * have, or null. Called when no transaction of the tree is open.
   */
  synchronized String outdated(File catalog, TreeDB db, long loadedAt) {
    // a transaction of the core (a job of its own) shows states that are not on disk yet: the next call looks
    if (db.getStorage() instanceof com.unitesk.requality.core.transaction.TransactionStorage)
      return null;
    String key = catalog.getAbsolutePath();
    Snapshot prev = last.get(key);
    if (prev != null && System.currentTimeMillis() - prev.time < period())
      return null;
    Snapshot now = scan(catalog);
    last.put(key, now);
    ITreeStorage storage = db.getStorage();
    if (prev == null) {
      // no look since the load (it ended before the server watched): the files newer than the start of the load
      for (Map.Entry<String, long[]> e : now.files.entrySet())
        if (e.getValue()[0] >= loadedAt - 2_000 && !inTree(catalog, e.getKey(), db, storage))
          return e.getKey();
      return null;
    }
    for (Map.Entry<String, long[]> e : now.files.entrySet()) {
      long[] p = prev.files.get(e.getKey());
      if (p != null && p[0] == e.getValue()[0] && p[1] == e.getValue()[1])
        continue;
      // a new or changed file: the tree must have its node with this content
      if (!inTree(catalog, e.getKey(), db, storage))
        return e.getKey();
    }
    for (String f : prev.files.keySet())
      if (!now.files.containsKey(f)) {
        // a deleted file: the tree must not have its node any more
        TreeNode n = node(db, f);
        if (n != null && !n.isDisposed())
          return f;
      }
    return null;
  }

  /** True when the tree has the node of the file with the content of the file. */
  private static boolean inTree(File catalog, String rel, TreeDB db, ITreeStorage storage) {
    try {
      byte[] content = Files.readAllBytes(new File(catalog, rel).toPath());
      JsonObject o = JsonParser.parseString(new String(content, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
      UUID u = UUID.fromString(o.get("uuid").getAsString());
      TreeNode n = db.getNode(u);
      if (n == null || n.isDisposed())
        return false;
      JsonObject state = History.state(storage, u, null);
      return state != null && History.onDisk(storage, u, state);
    } catch (IOException | RuntimeException e) {
      // a file being written, or not a node: looked at again later
      return true;
    }
  }

  /** The node of a node file path: root/Requirements/A.json is /Requirements/A. */
  private static TreeNode node(TreeDB db, String rel) {
    String path = rel.equals("root.json") ? "/" : rel.substring(4, rel.length() - 5);
    try {
      return db.getNode(path, com.unitesk.requality.core.model.Path.QID);
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static Snapshot scan(File catalog) {
    Snapshot s = new Snapshot();
    File rootJson = new File(catalog, "root.json");
    if (rootJson.isFile())
      s.files.put("root.json", new long[] { rootJson.lastModified(), rootJson.length() });
    Path root = new File(catalog, "root").toPath();
    if (!Files.isDirectory(root))
      return s;
    Path base = catalog.toPath();
    try {
      Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
          String n = f.getFileName().toString();
          if (n.endsWith(".json") && a.isRegularFile())
            s.files.put(base.relativize(f).toString().replace(File.separatorChar, '/'),
                new long[] { a.lastModifiedTime().toMillis(), a.size() });
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path f, IOException e) {
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      // a folder that cannot be read: its files are not looked at
    }
    return s;
  }
}
