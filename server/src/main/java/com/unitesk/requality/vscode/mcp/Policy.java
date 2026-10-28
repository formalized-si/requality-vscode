package com.unitesk.requality.vscode.mcp;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.unitesk.requality.vscode.mcp.McpServer.ToolError;

/**
 * What the agent may reach outside the catalogs: the folders whose files it may import and where it may write reports
 * (the folders of the catalogs and the folders given with --allow, and the folders the user allows when the agent needs
 * them and the client can ask), and the file types it may import.
 */
final class Policy {
  /**
   * Asks the user whether the server may use a folder (for the rest of the session); null when the client cannot ask.
   * The argument is the folder and the reason.
   */
  interface Asker {
    /** True: allowed; false: the user declined; null: the client cannot ask. */
    Boolean allow(File folder, String reason);
  }

  private volatile Asker asker;

  void asker(Asker a) {
    asker = a;
  }
  /** The document types the core imports (HTML through its own importer, the others through Apache Tika). */
  static final Set<String> DOCUMENT_TYPES = new HashSet<>(Arrays.asList("html", "htm", "xhtml", "xml", "doc", "docx", "odt",
      "rtf", "pdf", "txt"));

  private final List<Path> roots = new java.util.concurrent.CopyOnWriteArrayList<>();

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
    return allowed(path, what, true);
  }

  /** {@link #allowed(String, String)}; {@code ask}: the user may allow the folder (else it is refused). */
  File allowed(String path, String what, boolean ask) {
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
    // the folder of the file (or the folder itself), when the user allows it; never the home or a top folder
    File folder = p.toFile().isDirectory() ? p.toFile() : p.toFile().getParentFile();
    Asker a = ask ? asker : null;
    Boolean answer = a != null && folder != null && askable(folder) ? a.allow(folder, what.toLowerCase(Locale.ROOT) + " " + p)
        : null;
    if (Boolean.TRUE.equals(answer)) {
      try {
        roots.add(folder.getCanonicalFile().toPath());
      } catch (IOException e) {
        roots.add(folder.getAbsoluteFile().toPath().normalize());
      }
      return p.toFile();
    }
    if (Boolean.FALSE.equals(answer))
      throw new ToolError("The user did not allow the folder " + folder + ". Copy the file into a folder of the server "
          + roots + ", or ask the user to start requality-mcp with --allow <folder>.");
    throw new ToolError(what + " " + path + " is outside the folders this server may use: " + roots
        + ". Copy it into one of them, or start requality-mcp with --allow <folder> to add a folder.");
  }

  /** A folder the user may be asked to allow: not the home folder, not one of its parents, not a top folder. */
  static boolean askable(File folder) {
    try {
      Path f = folder.getCanonicalFile().toPath();
      Path home = new File(System.getProperty("user.home")).getCanonicalFile().toPath();
      if (f.getParent() == null || f.getNameCount() < 2 || home.startsWith(f))
        return false;
      // the folders of the system and of the settings of the user (keys, credentials)
      String name = f.getFileName().toString();
      if (name.startsWith("."))
        return false;
      for (Path x = f; x != null; x = x.getParent())
        if (x.getFileName() != null && x.getFileName().toString().startsWith("."))
          return false;
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  /** True when the file (an allowed, canonical path) is the folder or in it. */
  static boolean inside(File file, File folder) {
    Path f;
    Path d;
    try {
      f = file.getCanonicalFile().toPath();
      d = folder.getCanonicalFile().toPath();
    } catch (IOException e) {
      f = file.getAbsoluteFile().toPath().normalize();
      d = folder.getAbsoluteFile().toPath().normalize();
    }
    return f.startsWith(d);
  }

  /** A document file the agent may import: in an allowed folder, of a document type. */
  File document(String path) {
    return file(path, "document", DOCUMENT_TYPES);
  }

  /** The attributes of the elements that the importer follows (tag@attribute). */
  private static final Set<String> REFERENCES = new HashSet<>(Arrays.asList("img@src", "img@alt", "object@data", "script@src",
      "link@href", "embed@src", "frame@src"));

  /** The HTML documents: the importer copies the files their references name (and downloads the CSS of a URL). */
  private static final Set<String> HTML_TYPES = new HashSet<>(Arrays.asList("html", "htm", "xhtml", "xml"));

  /**
   * The references of an HTML document that the importer would copy into the catalog or download: a URL (other than
   * data:), or an existing file outside the allowed folders. Empty when the import is safe.
   */
  List<String> unsafeReferences(File html) throws IOException {
    String name = html.getName().toLowerCase(Locale.ROOT);
    int dot = name.lastIndexOf('.');
    if (dot < 0 || !HTML_TYPES.contains(name.substring(dot + 1)))
      return new ArrayList<>();
    // the references as the importer reads them: its parser (HtmlCleaner) and its charset
    List<String> refs = new ArrayList<>();
    org.htmlcleaner.TagNode root = com.unitesk.requality.document.OmnivorousImporter.getCharset(() -> {
      try {
        return new java.io.FileInputStream(html);
      } catch (IOException e) {
        throw new java.io.UncheckedIOException(e);
      }
    }).getSecond();
    if (root != null)
      for (org.htmlcleaner.TagNode e : root.getAllElements(true)) {
        String tag = e.getName() == null ? "" : e.getName().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> a : e.getAttributes().entrySet()) {
          String attr = a.getKey().toLowerCase(Locale.ROOT);
          if (REFERENCES.contains(tag + "@" + attr) && a.getValue() != null)
            refs.add(a.getValue().trim());
        }
      }
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
        // the files the importer may read for the reference: it decodes a reference in two ways (an image or a script
        // with URLDecoder, a style sheet as a URI path) and takes a path in the folder of the document
        for (File c : candidates(html.getParentFile(), r))
          if (c.exists()) {
            try {
              allowed(c.getAbsolutePath(), "The reference", false);
            } catch (ToolError e) {
              bad.add(r);
              break;
            }
          }
        continue;
      }
      // a reference to no file copies nothing (an alt text, for example)
      if (!f.exists())
        continue;
      try {
        allowed(f.getAbsolutePath(), "The reference", false);
      } catch (ToolError e) {
        bad.add(r);
      }
    }
    return bad;
  }

  /** The files a relative or absolute reference of a document in the folder may name, as the importer reads it. */
  static List<File> candidates(File folder, String ref) {
    java.util.Set<String> paths = new java.util.LinkedHashSet<>();
    for (String r : new String[] { ref, cut(ref) }) {
      paths.add(r);
      try {
        paths.add(java.net.URLDecoder.decode(r, "UTF-8"));
      } catch (IllegalArgumentException | java.io.UnsupportedEncodingException e) {
        // not encoded
      }
      try {
        paths.add(java.net.URLDecoder.decode(r.replace("+", "%2B"), "UTF-8"));
      } catch (IllegalArgumentException | java.io.UnsupportedEncodingException e) {
        // not encoded
      }
      try {
        paths.add(com.unitesk.requality.tool.CoreUtils.URLDecode(r));
      } catch (RuntimeException e) {
        // not encoded
      }
    }
    List<File> out = new ArrayList<>();
    for (String p : paths) {
      if (p.isEmpty())
        continue;
      File f = new File(p);
      if (f.isAbsolute()) {
        out.add(f);
      } else {
        out.add(new File(folder, p));
        // the current folder of the process (the importer of older versions read it)
        out.add(f.getAbsoluteFile());
      }
    }
    return out;
  }

  /** The reference without its query and fragment. */
  private static String cut(String r) {
    int c = r.indexOf('#');
    if (c >= 0)
      r = r.substring(0, c);
    c = r.indexOf('?');
    if (c >= 0)
      r = r.substring(0, c);
    return r;
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
