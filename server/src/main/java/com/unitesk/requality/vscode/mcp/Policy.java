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
