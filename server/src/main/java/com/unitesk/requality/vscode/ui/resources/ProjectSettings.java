package com.unitesk.requality.vscode.ui.resources;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.runtime.IPath;

/**
 * The project preference files of the workspace ({@code <project>/.settings/*.prefs}, the Eclipse instance scope of a
 * project: {@code org.eclipse.core.resources.prefs} for the encodings of the resources, {@code
 * org.eclipse.core.runtime.prefs} for the line delimiter). Keys are read and written as Eclipse writes them, with
 * the {@code eclipse.preferences.version=1} header.
 */
public final class ProjectSettings {
  /** The resource preferences file: {@code encoding/<project relative path>} and {@code encoding/<project>}. */
  public static final String RESOURCES_PREFS = "org.eclipse.core.resources.prefs";
  /** The runtime preferences file: {@code line.separator}. */
  public static final String RUNTIME_PREFS = "org.eclipse.core.runtime.prefs";
  /** The key prefix of the encodings in {@link #RESOURCES_PREFS}. */
  public static final String ENCODING = "encoding/";

  private ProjectSettings() {
  }

  /** The settings file of the project, in {@code <project>/.settings}. */
  public static File file(ProjectRef project, String name) {
    return new File(new File(project.toFile(), ".settings"), name);
  }

  /** The entries of a settings file (empty when the file does not exist or cannot be read). */
  public static Map<String, String> read(ProjectRef project, String name) {
    Map<String, String> entries = new LinkedHashMap<>();
    File f = file(project, name);
    if (!f.isFile())
      return entries;
    try {
      Properties p = new Properties();
      p.load(new StringReader(Files.readString(f.toPath(), StandardCharsets.ISO_8859_1)));
      for (String key : p.stringPropertyNames())
        if (!"eclipse.preferences.version".equals(key))
          entries.put(key, p.getProperty(key));
    } catch (IOException | IllegalArgumentException e) {
      RLogger.logError(e);
    }
    return entries;
  }

  /** One entry of a settings file, or null. */
  public static String get(ProjectRef project, String name, String key) {
    return read(project, name).get(key);
  }

  /** Sets (or with a null value removes) an entry, and writes the file, deleting it when it has no entries. */
  public static void put(ProjectRef project, String name, String key, String value) throws IOException {
    Map<String, String> entries = read(project, name);
    if (value == null)
      entries.remove(key);
    else
      entries.put(key, value);
    write(project, name, entries);
  }

  /** Writes the entries of a settings file in the Eclipse format; removes the file when there are none. */
  public static void write(ProjectRef project, String name, Map<String, String> entries) throws IOException {
    File f = file(project, name);
    if (entries.isEmpty()) {
      Files.deleteIfExists(f.toPath());
      return;
    }
    f.getParentFile().mkdirs();
    StringBuilder sb = new StringBuilder("eclipse.preferences.version=1\n");
    for (Map.Entry<String, String> e : entries.entrySet())
      sb.append(escape(e.getKey(), true)).append('=').append(escape(e.getValue(), false)).append('\n');
    Files.writeString(f.toPath(), sb.toString(), StandardCharsets.ISO_8859_1);
  }

  /** The key of the encoding of a resource: its project relative path, or the project name for the project. */
  public static String encodingKey(IResource resource) {
    if (resource instanceof IProject)
      return ENCODING + resource.getName();
    IPath rel = resource.getProjectRelativePath();
    return ENCODING + (rel == null ? "" : rel.toPortableString());
  }

  /** The charset set explicitly on the resource (its encoding entry), or null when it inherits the container's. */
  public static String explicitCharset(IResource resource) {
    IProject project = resource.getProject();
    if (!(project instanceof ProjectRef))
      return null;
    return get((ProjectRef) project, RESOURCES_PREFS, encodingKey(resource));
  }

  /** Escapes a key or value as a java.util.Properties line (Eclipse writes the same escapes). */
  static String escape(String s, boolean key) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        case '=', ':', '#', '!' -> sb.append('\\').append(c);
        case ' ' -> sb.append(key ? "\\ " : " ");
        default -> {
          if (c < 0x20 || c > 0x7e)
            sb.append(String.format("\\u%04X", (int) c));
          else
            sb.append(c);
        }
      }
    }
    return sb.toString();
  }
}
