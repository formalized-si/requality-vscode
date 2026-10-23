package com.unitesk.requality.vscode.ui.workbench;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.port.eclipse.Activator;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogSettings;
import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;

/**
 * {@code org.eclipse.ui.internal.registry.EditorRegistry}: the editors declared by the plug-in ({@code
 * org.eclipse.ui.editors}, with their {@code contentTypeBinding}s from {@code org.eclipse.core.contenttype.contentTypes})
 * and the built-in editors (text, system) and the editors of VS Code, registered by the {@code initialize} request
 * ({@code editors}: id, label, filenamePatterns).
 */
public final class EditorRegistry implements IEditorRegistry {
  public static final EditorRegistry INSTANCE = new EditorRegistry();

  private static final String EDITORS = "org.eclipse.ui.editors";
  private static final String CONTENT_TYPES = "org.eclipse.core.contenttype.contentTypes";
  private static final String TEXT_EDITOR_LABEL = "Text Editor";
  private static final String SYSTEM_EDITOR_LABEL = "System Editor";
  private static final String ASSOCIATIONS_SECTION = "EditorRegistry";
  private static final String ASSOCIATIONS = "associations";

  /** An editor of VS Code: its view type, label and the file name patterns (glob) it opens. */
  private static final class VSCodeEditor {
    final String id;
    final String label;
    final List<Pattern> patterns = new ArrayList<>();

    VSCodeEditor(String id, String label, List<String> globs) {
      this.id = id;
      this.label = label;
      for (String g : globs)
        patterns.add(glob(g));
    }

    boolean matches(String fileName) {
      for (Pattern p : patterns)
        if (fileName != null && p.matcher(fileName).matches())
          return true;
      return false;
    }
  }

  private static final List<VSCodeEditor> VSCODE_EDITORS = new ArrayList<>();

  private EditorRegistry() {
  }

  /**
   * Registers the editors of VS Code (the {@code editors} of the {@code initialize} request, absent or null: none).
   * They replace the editors registered before.
   */
  public static synchronized void registerVSCodeEditors(JsonElement editors) {
    VSCODE_EDITORS.clear();
    if (editors == null || !editors.isJsonArray())
      return;
    for (JsonElement e : editors.getAsJsonArray()) {
      if (!e.isJsonObject())
        continue;
      JsonObject o = e.getAsJsonObject();
      if (!o.has("id") || o.get("id").isJsonNull())
        continue;
      String id = o.get("id").getAsString();
      String label = o.has("label") && !o.get("label").isJsonNull() ? o.get("label").getAsString() : id;
      List<String> globs = new ArrayList<>();
      if (o.has("filenamePatterns") && o.get("filenamePatterns").isJsonArray())
        for (JsonElement g : o.getAsJsonArray("filenamePatterns"))
          globs.add(g.getAsString());
      VSCODE_EDITORS.add(new VSCodeEditor(id, label, globs));
    }
  }

  /** True when the editor is shown by VS Code (the text editor, the system editor or an editor of VS Code). */
  public static synchronized boolean opensInVSCode(String editorId) {
    return editorId != null && (DEFAULT_TEXT_EDITOR_ID.equals(editorId) || SYSTEM_EXTERNAL_EDITOR_ID.equals(editorId)
        || SYSTEM_INPLACE_EDITOR_ID.equals(editorId) || vscodeEditor(editorId) != null);
  }

  /** The editor id as the VS Code extension names it in {@code client/openFile}: "default", "system" or the view type. */
  public static String vscodeEditorName(String editorId) {
    if (SYSTEM_EXTERNAL_EDITOR_ID.equals(editorId))
      return "system";
    if (DEFAULT_TEXT_EDITOR_ID.equals(editorId) || SYSTEM_INPLACE_EDITOR_ID.equals(editorId) || editorId == null)
      return "default";
    return editorId;
  }

  private static synchronized VSCodeEditor vscodeEditor(String id) {
    for (VSCodeEditor e : VSCODE_EDITORS)
      if (e.id.equals(id))
        return e;
    return null;
  }

  @Override
  public IEditorDescriptor findEditor(String editorId) {
    if (editorId == null)
      return null;
    if (DEFAULT_TEXT_EDITOR_ID.equals(editorId))
      return new Descriptor(editorId, TEXT_EDITOR_LABEL, false);
    if (SYSTEM_EXTERNAL_EDITOR_ID.equals(editorId))
      return new Descriptor(editorId, SYSTEM_EDITOR_LABEL, true);
    VSCodeEditor v = vscodeEditor(editorId);
    if (v != null)
      return new Descriptor(v.id, v.label, false);
    if (isExternalProgram(editorId))
      return createForProgram(editorId);
    IConfigurationElement e = pluginEditor(editorId);
    return e == null ? null : new Descriptor(editorId, e.getAttribute("name"), false);
  }

  /**
   * {@code EditorDescriptor.createForProgram(String)}: an external editor for a program chosen by the user (Open With &gt;
   * Other... &gt; Browse...). Its id is the absolute path of the program, its label the name of the file.
   */
  public static IEditorDescriptor createForProgram(String path) {
    return new Descriptor(path, new File(path).getName(), true);
  }

  /**
   * True when the id is the absolute path of an existing program (an executable, or an application bundle on macOS):
   * an external editor that opens the file with the program, not an editor of VS Code.
   */
  public static boolean isExternalProgram(String editorId) {
    if (editorId == null || !new File(editorId).isAbsolute())
      return false;
    File f = new File(editorId);
    return f.isFile() || f.isDirectory() && editorId.endsWith(".app");
  }

  /**
   * The editor the user chose to remember for a file name, or for a file type given as {@code *.ext}, in Open With.
   * Persisted with the dialog settings of the workbench (section {@code EditorRegistry}).
   */
  public static synchronized void setDefaultEditor(String key, String editorId) {
    IDialogSettings section = associationSection();
    if (section == null)
      return;
    Map<String, String> associations = associations(section);
    if (editorId == null)
      associations.remove(key);
    else
      associations.put(key, editorId);
    List<String> lines = new ArrayList<>();
    associations.forEach((k, v) -> lines.add(k + "\t" + v));
    section.put(ASSOCIATIONS, lines.toArray(new String[0]));
    Activator.getDefault().saveDialogSettings();
  }

  /** The editor id remembered for the file name, else for its file type; null when none. */
  private static synchronized String rememberedEditorId(String fileName) {
    Activator activator = Activator.getDefault();
    IDialogSettings section = activator == null ? null : activator.getDialogSettings().getSection(ASSOCIATIONS_SECTION);
    if (section == null)
      return null;
    Map<String, String> associations = associations(section);
    String id = associations.get(fileName);
    if (id == null && fileName.lastIndexOf('.') > 0)
      id = associations.get("*" + fileName.substring(fileName.lastIndexOf('.')));
    return id;
  }

  private static Map<String, String> associations(IDialogSettings section) {
    Map<String, String> r = new LinkedHashMap<>();
    String[] lines = section.getArray(ASSOCIATIONS);
    if (lines != null)
      for (String line : lines) {
        int tab = line.indexOf('\t');
        if (tab > 0)
          r.put(line.substring(0, tab), line.substring(tab + 1));
      }
    return r;
  }

  /** The section of the remembered editors, or null when the workbench has no dialog settings (no plug-in). */
  private static IDialogSettings associationSection() {
    Activator activator = Activator.getDefault();
    if (activator == null)
      return null;
    IDialogSettings settings = activator.getDialogSettings();
    IDialogSettings section = settings.getSection(ASSOCIATIONS_SECTION);
    return section != null ? section : settings.addNewSection(ASSOCIATIONS_SECTION);
  }

  @Override
  public IEditorDescriptor getDefaultEditor(String fileName) {
    String remembered = fileName == null ? null : rememberedEditorId(fileName);
    IEditorDescriptor chosen = remembered == null ? null : findEditor(remembered);
    if (chosen != null)
      return chosen;
    IEditorDescriptor[] editors = getEditors(fileName);
    if (editors.length > 0)
      return editors[0];
    return findEditor(DEFAULT_TEXT_EDITOR_ID);
  }

  @Override
  public IEditorDescriptor[] getEditors(String fileName) {
    return getEditors(fileName, null);
  }

  @Override
  public IEditorDescriptor[] getEditors(String fileName, String contentTypeId) {
    List<String> types = contentTypesOf(fileName);
    if (contentTypeId != null && !types.contains(contentTypeId))
      types.add(contentTypeId);
    List<IEditorDescriptor> r = new ArrayList<>();
    // the editors of the plug-in first: the first one is the default editor of the file
    for (IConfigurationElement e : ExtensionRegistry.get().getConfigurationElementsFor(EDITORS)) {
      if (!"editor".equals(e.getName()))
        continue;
      if (matchesBinding(e, types, fileName))
        r.add(new Descriptor(e.getAttribute("id"), e.getAttribute("name"), false));
    }
    synchronized (EditorRegistry.class) {
      for (VSCodeEditor v : VSCODE_EDITORS)
        if (v.matches(fileName))
          r.add(new Descriptor(v.id, v.label, false));
    }
    return r.toArray(new IEditorDescriptor[0]);
  }

  @Override
  public IEditorDescriptor[] getSortedEditorsFromPlugins() {
    List<IEditorDescriptor> r = new ArrayList<>();
    for (IConfigurationElement e : ExtensionRegistry.get().getConfigurationElementsFor(EDITORS))
      if ("editor".equals(e.getName()))
        r.add(new Descriptor(e.getAttribute("id"), e.getAttribute("name"), false));
    synchronized (EditorRegistry.class) {
      for (VSCodeEditor v : VSCODE_EDITORS)
        r.add(new Descriptor(v.id, v.label, false));
    }
    r.add(findEditor(DEFAULT_TEXT_EDITOR_ID));
    r.add(findEditor(SYSTEM_EXTERNAL_EDITOR_ID));
    return r.toArray(new IEditorDescriptor[0]);
  }

  @Override
  public ImageDescriptor getImageDescriptor(String filename) {
    return ImageDescriptor.getMissingImageDescriptor();
  }

  /** The editor of plugin.xml with the id, or null. */
  static IConfigurationElement pluginEditor(String editorId) {
    for (IConfigurationElement e : ExtensionRegistry.get().getConfigurationElementsFor(EDITORS))
      if ("editor".equals(e.getName()) && editorId.equals(e.getAttribute("id")))
        return e;
    return null;
  }

  /** True when the editor of plugin.xml is hosted by the server (its class is ported and available). */
  static boolean isHosted(String editorId) {
    IConfigurationElement e = pluginEditor(editorId);
    if (e == null)
      return false;
    try {
      Class.forName(ExtensionRegistry.portedClassName(e.getAttribute("class")));
      return true;
    } catch (ClassNotFoundException | LinkageError ex) {
      return false;
    }
  }

  private static boolean matchesBinding(IConfigurationElement editor, List<String> types, String fileName) {
    for (IConfigurationElement b : editor.getChildren("contentTypeBinding"))
      if (types.contains(b.getAttribute("contentTypeId")))
        return true;
    if (fileName == null)
      return false;
    for (String ext : listAttribute(editor, "extensions"))
      if (fileName.toLowerCase(Locale.ROOT).endsWith("." + ext.toLowerCase(Locale.ROOT)))
        return true;
    for (String name : listAttribute(editor, "filenames"))
      if (name.equalsIgnoreCase(fileName))
        return true;
    return false;
  }

  /** The ids of the content types of plugin.xml the file name belongs to. */
  private static List<String> contentTypesOf(String fileName) {
    List<String> r = new ArrayList<>();
    if (fileName == null)
      return r;
    String ext = fileName.lastIndexOf('.') < 0 ? "" : fileName.substring(fileName.lastIndexOf('.') + 1);
    for (IConfigurationElement t : ExtensionRegistry.get().getConfigurationElementsFor(CONTENT_TYPES)) {
      if (!"content-type".equals(t.getName()))
        continue;
      if (listAttribute(t, "file-names").stream().anyMatch(fileName::equalsIgnoreCase)
          || !ext.isEmpty() && listAttribute(t, "file-extensions").stream().anyMatch(ext::equalsIgnoreCase))
        r.add(t.getAttribute("id"));
    }
    return r;
  }

  private static List<String> listAttribute(IConfigurationElement e, String name) {
    String v = e.getAttribute(name);
    if (v == null)
      return new ArrayList<>();
    List<String> r = new ArrayList<>();
    Arrays.stream(v.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(r::add);
    return r;
  }

  /** A glob of the file name patterns ({@code *.md}) as a regular expression, case-insensitive. */
  static Pattern glob(String glob) {
    StringBuilder sb = new StringBuilder();
    for (char c : glob.toCharArray()) {
      if (c == '*')
        sb.append(".*");
      else if (c == '?')
        sb.append('.');
      else
        sb.append(Pattern.quote(String.valueOf(c)));
    }
    return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
  }

  /** {@code IEditorDescriptor} of the registry. */
  static final class Descriptor implements IEditorDescriptor {
    private final String id;
    private final String label;
    private final boolean external;

    Descriptor(String id, String label, boolean external) {
      this.id = id;
      this.label = label == null ? id : label;
      this.external = external;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public String getLabel() {
      return label;
    }

    @Override
    public boolean isOpenExternal() {
      return external;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Descriptor && ((Descriptor) o).id.equals(id);
    }

    @Override
    public int hashCode() {
      return id.hashCode();
    }
  }
}
