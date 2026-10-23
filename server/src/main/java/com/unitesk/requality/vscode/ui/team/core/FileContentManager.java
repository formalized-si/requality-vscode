package com.unitesk.requality.vscode.ui.team.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.unitesk.requality.vscode.ui.team.preferences.TeamUIPlugin;

/**
 * {@code org.eclipse.team.core.FileContentManager}: the content type (text or binary) of the files by their extension
 * or name. The defaults are the {@code fileTypes} extensions of org.eclipse.team.core; the user mappings are kept in the
 * Team UI preference store ({@code file_types}: an extension and its type per line, {@code
 * cvs_mode_for_file_without_extensions}: a file name and its type) and override the defaults.
 */
public class FileContentManager {
  static final String PREF_TEAM_EXTENSION_TYPES = "file_types";
  static final String PREF_TEAM_FILENAME_TYPES = "cvs_mode_for_file_without_extensions";

  /** The default extension mappings of the {@code org.eclipse.team.core.fileTypes} extension point. */
  private static final String[][] PLUGIN_EXTENSIONS = {
      { "text", "txt" }, { "text", "properties" }, { "text", "xml" }, { "text", "xsl" }, { "text", "html" },
      { "text", "htm" }, { "binary", "jpg" }, { "binary", "jpeg" }, { "binary", "gif" }, { "binary", "png" },
      { "binary", "ico" }, { "binary", "bmp" }, { "binary", "tif" }, { "binary", "tiff" }, { "binary", "doc" },
      { "binary", "xls" }, { "binary", "pdf" }, { "binary", "ppt" }, { "binary", "zip" }, { "binary", "exe" },
      { "binary", "dll" }, { "binary", "so" }, { "text", "project" }, { "text", "options" }, { "text", "exsd" },
      { "text", "mxsd" }, { "text", "MF" }, { "text", "prefs" }, { "text", "ini" } };

  /** {@code IStringMapping}: a file name or extension and its content type. */
  public static final class Mapping {
    private final String string;
    private final int type;

    public Mapping(String string, int type) {
      this.string = string;
      this.type = type;
    }

    public String getString() {
      return string;
    }

    public int getType() {
      return type;
    }
  }

  /** The default extension mappings of the platform (extension to type). */
  private static Map<String, Integer> pluginExtensions() {
    Map<String, Integer> r = new LinkedHashMap<>();
    for (String[] e : PLUGIN_EXTENSIONS)
      r.put(e[1], "text".equals(e[0]) ? Team.TEXT : Team.BINARY);
    return r;
  }

  /** {@code FileContentManager.getTypeForExtension}: the type of the extension, or UNKNOWN. */
  public int getTypeForExtension(String extension) {
    Integer t = userMappings(PREF_TEAM_EXTENSION_TYPES).get(extension);
    if (t == null)
      t = pluginExtensions().get(extension);
    return t == null ? Team.UNKNOWN : t;
  }

  /** {@code FileContentManager.getTypeForName}: the type of the file name, or UNKNOWN. */
  public int getTypeForName(String filename) {
    Integer t = userMappings(PREF_TEAM_FILENAME_TYPES).get(filename);
    return t == null ? Team.UNKNOWN : t;
  }

  /** {@code FileContentManager.getExtensionMappings}: the defaults and the user mappings (the user ones win). */
  public Mapping[] getExtensionMappings() {
    Map<String, Integer> all = new LinkedHashMap<>(pluginExtensions());
    all.putAll(userMappings(PREF_TEAM_EXTENSION_TYPES));
    return toMappings(all);
  }

  /** {@code FileContentManager.getNameMappings}: the user mappings of the file names. */
  public Mapping[] getNameMappings() {
    return toMappings(userMappings(PREF_TEAM_FILENAME_TYPES));
  }

  /** {@code FileContentManager.getDefaultExtensionMappings}: the mappings of the platform. */
  public Mapping[] getDefaultExtensionMappings() {
    return toMappings(pluginExtensions());
  }

  /** {@code FileContentManager.getDefaultNameMappings}: no name mappings are contributed. */
  public Mapping[] getDefaultNameMappings() {
    return new Mapping[0];
  }

  /** {@code FileContentManager.setExtensionMappings}: replaces the user extension mappings. */
  public void setExtensionMappings(String[] extensions, int[] types) {
    saveUserMappings(PREF_TEAM_EXTENSION_TYPES, extensions, types);
  }

  /** {@code FileContentManager.setNameMappings}: replaces the user file name mappings. */
  public void setNameMappings(String[] names, int[] types) {
    saveUserMappings(PREF_TEAM_FILENAME_TYPES, names, types);
  }

  /** The user mappings of the key: a name and its type per line. */
  private static Map<String, Integer> userMappings(String key) {
    Map<String, Integer> r = new LinkedHashMap<>();
    String value = TeamUIPlugin.getPreferenceStore().getString(key);
    if (value == null || value.isEmpty())
      return r;
    for (String line : value.split("\n")) {
      int tab = line.lastIndexOf('\t');
      if (tab <= 0)
        continue;
      try {
        r.put(line.substring(0, tab), Integer.valueOf(line.substring(tab + 1)));
      } catch (NumberFormatException e) {
        // a corrupt line is ignored
      }
    }
    return r;
  }

  private static void saveUserMappings(String key, String[] names, int[] types) {
    StringBuilder buf = new StringBuilder();
    for (int i = 0; i < names.length; i++)
      buf.append(names[i]).append('\t').append(types[i]).append('\n');
    TeamUIPlugin.getPreferenceStore().setValue(key, buf.toString());
  }

  private static Mapping[] toMappings(Map<String, Integer> map) {
    List<Mapping> r = new ArrayList<>();
    for (Map.Entry<String, Integer> e : map.entrySet())
      r.add(new Mapping(e.getKey(), e.getValue()));
    return r.toArray(new Mapping[0]);
  }
}
