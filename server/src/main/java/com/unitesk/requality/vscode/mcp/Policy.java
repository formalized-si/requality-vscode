package com.unitesk.requality.vscode.mcp;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.unitesk.requality.vscode.mcp.McpServer.ToolError;

/**
 * What the agent may reach outside the catalogs: the folders whose files it may import and where it may write reports
 * (the folders of the catalogs and the folders given with --allow), and the file types it may import.
 */
final class Policy {
  /** The document types the core imports (HTML through its own importer, the others through Apache Tika). */
  static final Set<String> DOCUMENT_TYPES = new HashSet<>(Arrays.asList("html", "htm", "xhtml", "xml", "doc", "docx", "odt",
      "rtf", "pdf", "txt"));

  private final List<Path> roots = new ArrayList<>();

  Policy(List<File> folders) {
    for (File f : folders)
      try {
        roots.add(f.getCanonicalFile().toPath());
      } catch (IOException e) {
        roots.add(f.getAbsoluteFile().toPath().normalize());
      }
  }

  List<Path> roots() {
    return roots;
  }

  /** The canonical path of a file or folder in an allowed folder; an error that tells how to allow it otherwise. */
  File allowed(String path, String what) {
    File f = new File(path);
    if (!f.isAbsolute())
      throw new ToolError(what + " " + path + ": give an absolute path");
    // "." and ".." are resolved first: the file used is the one of the normalized path
    f = f.toPath().normalize().toFile();
    Path p;
    try {
      // the canonical path of the deepest existing parent: links and ".." are resolved before the check
      File existing = f;
      List<String> rest = new ArrayList<>();
      while (existing != null && !existing.exists()) {
        rest.add(0, existing.getName());
        existing = existing.getParentFile();
      }
      p = existing == null ? f.toPath().normalize() : existing.getCanonicalFile().toPath();
      for (String r : rest) {
        if (r.equals("..") || r.equals("."))
          throw new ToolError(what + " " + path + ": the path has '" + r + "'");
        p = p.resolve(r);
      }
    } catch (IOException e) {
      throw new ToolError(what + " " + path + ": " + e.getMessage());
    }
    for (Path root : roots)
      if (p.startsWith(root))
        return p.toFile();
    throw new ToolError(what + " " + path + " is outside the folders this server may use: " + roots
        + ". Start requality-mcp with --allow <folder> to add a folder.");
  }

  /** A document file the agent may import: in an allowed folder, of a document type. */
  File document(String path) {
    return file(path, "document", DOCUMENT_TYPES);
  }

  /** The HTML documents: the importer copies the files their references name (and downloads the CSS of a URL). */
  private static final Set<String> HTML_TYPES = new HashSet<>(Arrays.asList("html", "htm", "xhtml"));

  /**
   * The references of an HTML document that the importer would copy into the catalog or download: a URL (other than
   * data:), or an existing file outside the allowed folders. Empty when the import is safe.
   */
  List<String> unsafeReferences(File html) throws IOException {
    String name = html.getName().toLowerCase(Locale.ROOT);
    int dot = name.lastIndexOf('.');
    if (dot < 0 || !HTML_TYPES.contains(name.substring(dot + 1)))
      return new ArrayList<>();
    org.jsoup.nodes.Document d = org.jsoup.Jsoup.parse(html, null);
    List<String> refs = new ArrayList<>();
    for (String[] q : new String[][] { { "img", "src" }, { "img", "alt" }, { "object", "data" }, { "script", "src" },
        { "link", "href" }, { "embed", "src" } })
      for (org.jsoup.nodes.Element e : d.select(q[0] + "[" + q[1] + "]"))
        refs.add(e.attr(q[1]).trim());
    List<String> bad = new ArrayList<>();
    for (String r : refs) {
      if (r.isEmpty() || r.regionMatches(true, 0, "data:", 0, 5) || bad.contains(r))
        continue;
      File f;
      if (r.matches("(?i)^file:.*")) {
        try {
          f = new File(java.net.URI.create(r));
        } catch (RuntimeException e) {
          bad.add(r);
          continue;
        }
      } else if (r.matches("^[a-zA-Z][a-zA-Z0-9+.-]+:.*") && !r.matches("^[a-zA-Z]:[\\\\/].*")) {
        bad.add(r); // a URL: the importer downloads it (CSS) or the reference is copied as text
        continue;
      } else {
        String path = r;
        int cut = path.indexOf('#');
        if (cut >= 0)
          path = path.substring(0, cut);
        cut = path.indexOf('?');
        if (cut >= 0)
          path = path.substring(0, cut);
        if (path.isEmpty())
          continue;
        try {
          path = java.net.URLDecoder.decode(path.replace("+", "%2B"), "UTF-8");
        } catch (IllegalArgumentException e) {
          // not encoded
        }
        File p = new File(path);
        f = p.isAbsolute() ? p : new File(html.getParentFile(), path);
      }
      // a reference to no file copies nothing (an alt text, for example)
      if (!f.exists())
        continue;
      try {
        allowed(f.getAbsolutePath(), "The reference");
      } catch (ToolError e) {
        bad.add(r);
      }
    }
    return bad;
  }

  /** A file the agent may read: in an allowed folder, with one of the extensions. */
  File file(String path, String kind, Set<String> types) {
    File f = allowed(path, "The file");
    if (!f.isFile())
      throw new ToolError("No file " + f);
    String name = f.getName();
    int dot = name.lastIndexOf('.');
    String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    if (!types.contains(ext))
      throw new ToolError("The file " + f + " is no " + kind + ": the server reads " + new java.util.TreeSet<>(types) + " files");
    return f;
  }
}
