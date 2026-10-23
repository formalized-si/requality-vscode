package com.unitesk.requality.vscode.ui.resources;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.core.ServerContext;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;

/**
 * The local history of files ({@code IResource.KEEP_HISTORY}): the states of a file saved before its contents are
 * replaced or deleted, so that an undo can restore them. Kept per full path in the storage of the server, with the
 * limits of the workspace description ({@link IWorkspaceDescription}, Local History preferences): by default states
 * older than 7 days are dropped, 50 states per file are kept and files larger than 1 MB are not saved; with the
 * limits switched off ("Limit history size") every state is kept.
 */
public final class FileHistory {
  private static final String INDEX = "index.json";
  private static final Object LOCK = new Object();

  private FileHistory() {
  }

  private static File workspaceStateFile;

  /** The history of the workspace is kept next to its state ({@code <name>-localHistory}), with its metadata. */
  public static void setWorkspaceStateFile(File stateFile) {
    workspaceStateFile = stateFile;
  }

  /** The folder of the history of the workspace, or null when there is none (states are not kept). */
  private static File root() {
    File state = workspaceStateFile;
    if (state == null)
      return null;
    String name = state.getName();
    if (name.endsWith(".json"))
      name = name.substring(0, name.length() - ".json".length());
    return new File(state.getAbsoluteFile().getParentFile(), name + "-localHistory");
  }

  private static File dirOf(File root, IPath fullPath) {
    try {
      byte[] h = MessageDigest.getInstance("SHA-1").digest(fullPath.toPortableString().getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : h)
        sb.append(String.format("%02x", b));
      return new File(root, sb.toString());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Saves the state of a file (its contents now) before they are replaced or lost. Missing and large files are not saved. */
  static void keep(File file, IPath fullPath) {
    File root = root();
    if (root == null || file == null || !file.isFile())
      return;
    IWorkspaceDescription description = ResourcesPlugin.getWorkspace().getDescription();
    boolean limited = description.isApplyFileStatePolicy();
    if (limited && file.length() > description.getMaxFileStateSize())
      return;
    synchronized (LOCK) {
      try {
        File dir = dirOf(root, fullPath);
        Files.createDirectories(dir.toPath());
        JsonArray index = readIndex(dir);
        long now = System.currentTimeMillis();
        int seq = 0;
        for (JsonElement e : index)
          seq = Math.max(seq, e.getAsJsonObject().get("seq").getAsInt() + 1);
        File data = new File(dir, "state-" + seq + ".dat");
        Files.copy(file.toPath(), data.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        JsonObject state = new JsonObject();
        state.addProperty("seq", seq);
        state.addProperty("stored", now);
        state.addProperty("modified", file.lastModified());
        state.addProperty("name", file.getName());
        state.addProperty("path", fullPath.toPortableString());
        state.addProperty("data", data.getName());
        index.add(state);
        // the states older than the longevity, and the oldest beyond the maximum per file, are dropped.
        JsonArray kept = new JsonArray();
        int n = index.size();
        for (int i = 0; i < n; i++) {
          JsonObject o = index.get(i).getAsJsonObject();
          boolean tooOld = limited && now - o.get("stored").getAsLong() > description.getFileStateLongevity();
          boolean tooMany = limited && n - i > description.getMaxFileStates();
          if (tooOld || tooMany)
            new File(dir, o.get("data").getAsString()).delete();
          else
            kept.add(o);
        }
        writeIndex(dir, kept);
      } catch (IOException | RuntimeException e) {
        RLogger.logError(e);
      }
    }
  }

  /**
   * Saves the state of a workspace file that an editor of VS Code is about to save (an Eclipse editor saves with
   * KEEP_HISTORY). Returns whether the file exists in a project of the workspace.
   */
  public static boolean keepBeforeSave(IFile file) {
    if (!file.exists() || file.getLocation() == null)
      return false;
    keep(new File(file.getLocation().toOSString()), file.getFullPath());
    return true;
  }

  /** Saves the states of the files of a deleted tree (the file or the files under the folder). */
  static void keepTree(File f, IPath fullPath) {
    if (f == null || !f.exists())
      return;
    if (f.isDirectory()) {
      File[] children = f.listFiles();
      if (children != null)
        for (File c : children)
          keepTree(c, fullPath.append(c.getName()));
    } else {
      keep(f, fullPath);
    }
  }

  /** The states of a file, the most recent first. */
  static IFileState[] states(IPath fullPath) {
    File root = root();
    if (root == null)
      return new IFileState[0];
    synchronized (LOCK) {
      File dir = dirOf(root, fullPath);
      JsonArray index = readIndex(dir);
      IFileState[] r = new IFileState[index.size()];
      int j = 0;
      for (int i = index.size() - 1; i >= 0; i--) {
        JsonObject o = index.get(i).getAsJsonObject();
        r[j++] = new State(fullPath, o.get("name").getAsString(), o.get("modified").getAsLong(),
            new File(dir, o.get("data").getAsString()));
      }
      return r;
    }
  }

  /** The full paths of the files that have states in the local history (for the files deleted from a folder). */
  static java.util.List<IPath> pathsWithStates() {
    java.util.List<IPath> r = new java.util.ArrayList<>();
    File root = root();
    if (root == null || !root.isDirectory())
      return r;
    synchronized (LOCK) {
      File[] dirs = root.listFiles(File::isDirectory);
      if (dirs == null)
        return r;
      for (File dir : dirs)
        for (JsonElement e : readIndex(dir)) {
          IPath p = com.unitesk.requality.vscode.ui.runtime.Path.fromPortableString(e.getAsJsonObject().get("path").getAsString());
          if (!r.contains(p))
            r.add(p);
        }
    }
    return r;
  }

  private static JsonArray readIndex(File dir) {
    File f = new File(dir, INDEX);
    if (!f.isFile())
      return new JsonArray();
    try {
      return JsonParser.parseString(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)).getAsJsonArray();
    } catch (IOException | RuntimeException e) {
      RLogger.logError(e);
      return new JsonArray();
    }
  }

  private static void writeIndex(File dir, JsonArray index) throws IOException {
    Files.write(new File(dir, INDEX).toPath(), index.toString().getBytes(StandardCharsets.UTF_8));
  }

  /** A saved state of a file: its contents are the saved file. */
  private static final class State implements IFileState {
    private final IPath fullPath;
    private final String name;
    private final long modified;
    private final File data;

    State(IPath fullPath, String name, long modified, File data) {
      this.fullPath = fullPath;
      this.name = name;
      this.modified = modified;
      this.data = data;
    }

    /** The contents of the state, read at once (the file of the state is not kept open by the caller). */
    @Override
    public InputStream getContents() throws CoreException {
      try {
        return new java.io.ByteArrayInputStream(java.nio.file.Files.readAllBytes(data.toPath()));
      } catch (IOException e) {
        throw new CoreException(new Status(IStatus.ERROR, ResourcesPlugin.PI_RESOURCES,
            "The contents of " + fullPath + " are no longer in the local history", e));
      }
    }

    @Override
    public long getModificationTime() {
      return modified;
    }

    @Override
    public IPath getFullPath() {
      return fullPath;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public boolean exists() {
      return data.isFile();
    }

    @Override
    public String getCharset() {
      return "UTF-8";
    }
  }
}
