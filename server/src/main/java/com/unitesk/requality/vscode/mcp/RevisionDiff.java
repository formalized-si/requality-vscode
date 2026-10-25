package com.unitesk.requality.vscode.mcp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.unitesk.requality.vscode.mcp.McpServer.InvalidParams;
import com.unitesk.requality.vscode.mcp.McpServer.ToolError;

/**
 * compare_with_revision: the nodes of a catalog now (its files on disk) against a Git revision, by their UUIDs, so that
 * a person can review what changed since a commit (also the changes of a colleague or of VS Code). A catalog keeps a
 * node in a file {@code <id>.json} of the folder of its parent ({@code root/} for the top nodes), with its UUID and its
 * attributes; a moved or renamed node has the same UUID in another file.
 */
final class RevisionDiff {
  private RevisionDiff() {
  }

  /** A revision Git accepts and no option: a commit, a branch, a tag, HEAD~2, main@{1}. */
  private static final java.util.regex.Pattern REVISION = java.util.regex.Pattern.compile("[A-Za-z0-9._/~^@{}+-]{1,200}");
  private static final long GIT_TIMEOUT_S = 120;
  /** An attribute value is shown with at most this many characters. */
  private static final int SHOWN = 300;

  /** A node as a file has it: its path and the values of its attributes as text. */
  private static final class Node {
    final String path;
    final Map<String, String> attributes;

    Node(String path, Map<String, String> attributes) {
      this.path = path;
      this.attributes = attributes;
    }

    String attr(String key) {
      return attributes.get(key);
    }
  }

  static JsonObject compare(File catalog, String revision, String under, int limit) throws Exception {
    if (!REVISION.matcher(revision).matches() || revision.startsWith("-"))
      throw new InvalidParams("Invalid revision " + revision + " (a commit, a branch, a tag or HEAD~1)");
    String top = git(catalog, null, "rev-parse", "--show-toplevel").trim();
    if (top.isEmpty())
      throw new ToolError("The catalog " + catalog + " is not in a Git repository");
    String prefix = git(catalog, null, "rev-parse", "--show-prefix").trim();
    String commit = git(catalog, null, "rev-parse", "--verify", "--quiet", revision + "^{commit}").trim();
    if (commit.isEmpty())
      throw new ToolError("No revision " + revision + " in the Git repository " + top);
    // the node files that differ: tracked ones (changed, added, deleted) and new ones Git does not track yet
    Set<String> changed = new TreeSet<>();
    for (String e : git(catalog, null, "diff", "--no-ext-diff", "--no-textconv", "--name-only", "-z", "--no-renames", "--relative", commit, "--", ".").split("\0"))
      if (isNode(e))
        changed.add(e);
    for (String e : git(catalog, null, "ls-files", "--others", "--exclude-standard", "-z", "--", ".").split("\0"))
      if (isNode(e))
        changed.add(e);
    // the old files of the revision, by one cat-file process (its requests end at new lines: a name with one is left out)
    List<String> unreadable = new ArrayList<>();
    for (String f : new ArrayList<>(changed))
      if (f.indexOf('\n') >= 0 || f.indexOf('\r') >= 0) {
        changed.remove(f);
        unreadable.add(f.replaceAll("[\r\n]", "?") + " (a new line in the name)");
      }
    StringBuilder q = new StringBuilder();
    List<String> asked = new ArrayList<>(changed);
    for (String f : asked)
      q.append(commit).append(':').append(prefix).append(f).append('\n');
    Map<String, byte[]> old = catFile(catalog, q.toString(), asked);
    Map<String, Node> before = new LinkedHashMap<>();
    Map<String, Node> after = new LinkedHashMap<>();
    for (String f : asked) {
      byte[] o = old.get(f);
      if (o != null)
        read(f, o, before, unreadable, "in " + revision);
      File now = new File(catalog, f);
      if (now.isFile())
        read(f, Files.readAllBytes(now.toPath()), after, unreadable, "now");
    }
    String filter = under == null || under.equals("/") ? null : (under.endsWith("/") ? under : under + "/");
    JsonArray added = new JsonArray();
    JsonArray deleted = new JsonArray();
    JsonArray modified = new JsonArray();
    int[] counts = new int[3];
    for (Map.Entry<String, Node> e : after.entrySet()) {
      Node n = e.getValue();
      Node b = before.get(e.getKey());
      if (b == null) {
        if (!inside(n.path, filter))
          continue;
        if (counts[0]++ < limit)
          added.add(brief(e.getKey(), n));
        continue;
      }
      JsonObject d = difference(b, n);
      if (d == null || !inside(n.path, filter) && !inside(b.path, filter))
        continue;
      if (counts[2]++ < limit) {
        JsonObject m = brief(e.getKey(), n);
        if (!b.path.equals(n.path))
          m.addProperty("was", b.path);
        if (d.size() > 0)
          m.add("attributes", d);
        modified.add(m);
      }
    }
    // a node that moved has its old file and its new file among the changed ones: it is in both sets
    for (Map.Entry<String, Node> e : before.entrySet())
      if (!after.containsKey(e.getKey()) && inside(e.getValue().path, filter) && counts[1]++ < limit)
        deleted.add(brief(e.getKey(), e.getValue()));
    JsonObject r = new JsonObject();
    r.addProperty("revision", revision);
    r.addProperty("commit", commit.length() > 12 ? commit.substring(0, 12) : commit);
    JsonObject c = new JsonObject();
    c.addProperty("added", counts[0]);
    c.addProperty("deleted", counts[1]);
    c.addProperty("changed", counts[2]);
    r.add("counts", c);
    r.add("added", added);
    r.add("deleted", deleted);
    r.add("changed", modified);
    if (counts[0] > limit || counts[1] > limit || counts[2] > limit)
      r.addProperty("more", "Some lists have more than " + limit + " nodes: give a larger limit or 'under'");
    if (!unreadable.isEmpty())
      r.add("unreadable", strings(unreadable));
    return r;
  }

  private static JsonArray strings(List<String> l) {
    JsonArray a = new JsonArray();
    l.forEach(a::add);
    return a;
  }

  /** A node file of the catalog: root.json, or a .json file under root/. */
  private static boolean isNode(String f) {
    return f.equals("root.json") || f.startsWith("root/") && f.endsWith(".json");
  }

  /** The path of a node from the path of its file: root/Requirements/A.json is /Requirements/A, root.json is /. */
  private static String nodePath(String file) {
    if (file.equals("root.json"))
      return "/";
    return file.substring(4, file.length() - 5);
  }

  private static boolean inside(String path, String filter) {
    return filter == null || (path + "/").startsWith(filter);
  }

  /** Reads a node file as the storage of the catalog reads it (each format of the files). */
  private static void read(String file, byte[] content, Map<String, Node> into, List<String> unreadable, String when) {
    try {
      JsonObject o = JsonParser.parseString(new String(content, StandardCharsets.UTF_8)).getAsJsonObject();
      java.util.UUID uuid = java.util.UUID.fromString(o.get("uuid").getAsString());
      Map<String, com.unitesk.requality.core.attribute.AttributeDefinition> m = com.unitesk.requality.core.storage.NodeMapper
          .getAttributes(uuid, new java.io.ByteArrayInputStream(content),
              com.unitesk.requality.core.attribute.valueprovider.TempFolderFilesProvider.EMPTY);
      Map<String, String> attrs = new LinkedHashMap<>();
      if (m != null)
        for (Map.Entry<String, com.unitesk.requality.core.attribute.AttributeDefinition> e : m.entrySet()) {
          Object v = e.getValue().getRawValue() == null ? null : e.getValue().getRawValue().getValue();
          if (v != null)
            attrs.put(e.getKey(), text(v));
        }
      into.put(uuid.toString(), new Node(nodePath(file), attrs));
    } catch (Exception e) {
      // a merge conflict, a file that is no node
      unreadable.add(file + " (" + when + ")");
    }
  }

  /** A value as text: a list as its items, a link as its target, the others as they print. */
  private static String text(Object v) {
    if (v instanceof com.unitesk.requality.core.attribute.ListValue) {
      List<String> items = new ArrayList<>();
      for (Object x : ((com.unitesk.requality.core.attribute.ListValue) v).getValues())
        items.add(x == null ? "" : text(x));
      return items.toString();
    }
    if (v instanceof com.unitesk.requality.core.attribute.ReferenceValue)
      return ((com.unitesk.requality.core.attribute.ReferenceValue) v).getRaw();
    if (v instanceof com.unitesk.requality.core.attribute.EnumValue)
      return String.valueOf(((com.unitesk.requality.core.attribute.EnumValue) v).getValue());
    return String.valueOf(v);
  }

  private static JsonObject brief(String uuid, Node n) {
    JsonObject o = new JsonObject();
    o.addProperty("path", n.path);
    o.addProperty("uuid", uuid);
    String type = n.attr("_type");
    if (type != null)
      o.addProperty("type", type);
    String name = n.attr("_name");
    if (name != null && !name.isEmpty())
      o.addProperty("name", name);
    if ("Location".equals(type)) {
      String d = n.attr("_description");
      if (d != null)
        o.addProperty("text", shown(Core.text(d)));
    }
    return o;
  }

  /** The attributes that differ, by their names in the editor: {name: {before, after}}; null when nothing differs. */
  private static JsonObject difference(Node b, Node a) {
    JsonObject d = new JsonObject();
    Set<String> keys = new TreeSet<>(b.attributes.keySet());
    keys.addAll(a.attributes.keySet());
    for (String k : keys) {
      // the order and the generated data of Requality are no change of the person
      if (k.equals("_index") || k.equals("_children") || k.startsWith("_%") || k.equals("_last_generated"))
        continue;
      String x = b.attributes.get(k);
      String y = a.attributes.get(k);
      if (x == null ? y == null : x.equals(y))
        continue;
      JsonObject v = new JsonObject();
      boolean html = k.equals("_description");
      if (x != null)
        v.addProperty("before", shown(html ? Core.text(x) : x));
      if (y != null)
        v.addProperty("after", shown(html ? Core.text(y) : y));
      d.add(k.equals("_name") ? "name" : k.equals("_description") ? "description" : k.equals("_locations") ? "locations" : k, v);
    }
    if (d.size() == 0 && b.path.equals(a.path))
      return null;
    return d;
  }

  private static String shown(String s) {
    String t = s.replaceAll("\\s+", " ").trim();
    return t.length() > SHOWN ? t.substring(0, SHOWN) + " …" : t;
  }

  // ---- git

  private static String git(File dir, String input, String... args) throws IOException, InterruptedException {
    byte[] out = run(dir, input == null ? null : input.getBytes(StandardCharsets.UTF_8), args);
    return new String(out, StandardCharsets.UTF_8);
  }

  private static byte[] run(File dir, byte[] input, String... args) throws IOException, InterruptedException {
    List<String> cmd = new ArrayList<>();
    cmd.add("git");
    cmd.add("-C");
    cmd.add(dir.getAbsolutePath());
    // no file system monitor program of the configuration of the repository, no prompt for a password
    cmd.add("-c");
    cmd.add("core.fsmonitor=false");
    for (String a : args)
      cmd.add(a);
    ProcessBuilder pb = new ProcessBuilder(cmd);
    pb.environment().put("GIT_TERMINAL_PROMPT", "0");
    // the messages of git in English: the server reads them
    pb.environment().put("LC_ALL", "C");
    pb.environment().put("LANGUAGE", "C");
    pb.environment().put("GIT_PAGER", "cat");
    pb.redirectErrorStream(false);
    Process p;
    try {
      p = pb.start();
    } catch (IOException e) {
      throw new ToolError("Git is not installed (or not on the PATH of the server): " + e.getMessage());
    }
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    Thread errReader = new Thread(() -> copy(p.getErrorStream(), err));
    errReader.setDaemon(true);
    errReader.start();
    if (input != null) {
      Thread writer = new Thread(() -> {
        try (OutputStream o = p.getOutputStream()) {
          o.write(input);
        } catch (IOException e) {
          // git ended
        }
      });
      writer.setDaemon(true);
      writer.start();
    } else {
      p.getOutputStream().close();
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Thread outReader = new Thread(() -> copy(p.getInputStream(), out));
    outReader.setDaemon(true);
    outReader.start();
    if (!p.waitFor(GIT_TIMEOUT_S, TimeUnit.SECONDS)) {
      p.destroyForcibly();
      throw new ToolError("Git did not answer in " + GIT_TIMEOUT_S + " seconds");
    }
    outReader.join(5000);
    errReader.join(5000);
    if (p.exitValue() != 0 && !(args.length > 2 && args[0].equals("rev-parse") && args[1].equals("--verify"))) {
      String e = new String(err.toByteArray(), StandardCharsets.UTF_8).trim();
      if (e.contains("not a git repository"))
        throw new ToolError("The catalog " + dir + " is not in a Git repository");
      throw new ToolError("git " + args[0] + " failed: " + (e.length() > 500 ? e.substring(0, 500) : e));
    }
    return out.toByteArray();
  }

  private static void copy(InputStream in, ByteArrayOutputStream out) {
    byte[] b = new byte[1 << 16];
    try {
      int n;
      while ((n = in.read(b)) > 0)
        out.write(b, 0, n);
    } catch (IOException e) {
      // the process ended
    }
  }

  /** The contents of the files of a revision ("<sha> blob <size>\n<content>\n" or "<object> missing\n" each). */
  private static Map<String, byte[]> catFile(File dir, String requests, List<String> files)
      throws IOException, InterruptedException {
    Map<String, byte[]> out = new LinkedHashMap<>();
    if (files.isEmpty())
      return out;
    byte[] all = run(dir, requests.getBytes(StandardCharsets.UTF_8), "cat-file", "--batch");
    int pos = 0;
    for (String f : files) {
      int nl = indexOf(all, (byte) '\n', pos);
      if (nl < 0)
        break;
      String header = new String(all, pos, nl - pos, StandardCharsets.UTF_8);
      pos = nl + 1;
      if (header.endsWith(" missing") || header.endsWith(" ambiguous"))
        continue;
      String[] h = header.split(" ");
      int size = Integer.parseInt(h[h.length - 1]);
      byte[] c = new byte[size];
      System.arraycopy(all, pos, c, 0, size);
      pos += size + 1;
      if (h.length >= 2 && h[1].equals("blob"))
        out.put(f, c);
    }
    return out;
  }

  private static int indexOf(byte[] a, byte b, int from) {
    for (int i = from; i < a.length; i++)
      if (a[i] == b)
        return i;
    return -1;
  }
}
