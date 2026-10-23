package com.unitesk.requality.vscode.ui.ide.markers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.unitesk.requality.vscode.ui.resources.IMarker;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.CoreException;

/**
 * {@code org.eclipse.ui.internal.views.markers.MarkerFieldFilterGroup}: a named configuration of a markers view (the
 * Show menu and the Filters dialog). It shows the markers of its scope whose description, completion and priority
 * match. The scope paths are the resources selected when the configuration was made.
 */
public class MarkerFilterConfiguration {
  /** {@code filtersDialog_anyResource}: the markers of any resource. */
  public static final int SCOPE_ANY = 0;
  /** {@code filtersDialog_selectedResource}: the markers of the selected resources only. */
  public static final int SCOPE_SELECTED = 1;
  /** {@code filtersDialog_selectedAndChildren}: the markers of the selected resources and their children. */
  public static final int SCOPE_SELECTED_AND_CHILDREN = 2;
  /** {@code filtersDialog_anyResourceInSameProject}: the markers of the projects of the selected resources. */
  public static final int SCOPE_SAME_PROJECT = 3;

  private String name;
  private boolean enabled;
  private int scope = SCOPE_ANY;
  private final List<String> scopePaths = new ArrayList<>();
  private String text = "";
  /** {@code filtersDialog_contains} (true) or {@code filtersDialog_doesNotContain} (false). */
  private boolean containsText = true;
  private boolean completed = true;
  private boolean notCompleted = true;
  private boolean high = true;
  private boolean normal = true;
  private boolean low = true;
  /** {@code SeverityAndDescriptionFieldFilter}: the severities shown (the problems; markers without one are infos). */
  private boolean errors = true;
  private boolean warnings = true;
  private boolean infos = true;

  public MarkerFilterConfiguration(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  /** Whether the configuration is checked (applied) in its view. */
  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getScope() {
    return scope;
  }

  public void setScope(int scope) {
    this.scope = scope;
  }

  /** The full paths of the resources of the scope (for the selected scopes). */
  public List<String> getScopePaths() {
    return scopePaths;
  }

  public void setScopePaths(List<String> paths) {
    scopePaths.clear();
    scopePaths.addAll(paths);
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text == null ? "" : text;
  }

  public boolean isContainsText() {
    return containsText;
  }

  public void setContainsText(boolean containsText) {
    this.containsText = containsText;
  }

  public boolean isCompleted() {
    return completed;
  }

  public void setCompleted(boolean completed) {
    this.completed = completed;
  }

  public boolean isNotCompleted() {
    return notCompleted;
  }

  public void setNotCompleted(boolean notCompleted) {
    this.notCompleted = notCompleted;
  }

  /** The priority checked for {@code priority}: {@link IMarker#PRIORITY_HIGH}, NORMAL or LOW. */
  public boolean isPriorityChecked(int priority) {
    switch (priority) {
      case IMarker.PRIORITY_HIGH:
        return high;
      case IMarker.PRIORITY_LOW:
        return low;
      default:
        return normal;
    }
  }

  public void setPriorityChecked(int priority, boolean checked) {
    switch (priority) {
      case IMarker.PRIORITY_HIGH:
        high = checked;
        break;
      case IMarker.PRIORITY_LOW:
        low = checked;
        break;
      default:
        normal = checked;
    }
  }

  /** The severity checked for {@code severity}: {@link IMarker#SEVERITY_ERROR}, WARNING or INFO. */
  public boolean isSeverityChecked(int severity) {
    switch (severity) {
      case IMarker.SEVERITY_ERROR:
        return errors;
      case IMarker.SEVERITY_WARNING:
        return warnings;
      default:
        return infos;
    }
  }

  public void setSeverityChecked(int severity, boolean checked) {
    switch (severity) {
      case IMarker.SEVERITY_ERROR:
        errors = checked;
        break;
      case IMarker.SEVERITY_WARNING:
        warnings = checked;
        break;
      default:
        infos = checked;
    }
  }

  /** Copies the settings of another configuration (the Filters dialog applies its edits this way). */
  public void copyFrom(MarkerFilterConfiguration o) {
    scope = o.scope;
    setScopePaths(o.scopePaths);
    text = o.text;
    containsText = o.containsText;
    completed = o.completed;
    notCompleted = o.notCompleted;
    high = o.high;
    normal = o.normal;
    low = o.low;
    errors = o.errors;
    warnings = o.warnings;
    infos = o.infos;
  }

  /** A copy of the settings under another name, not enabled. */
  public MarkerFilterConfiguration copy(String newName) {
    MarkerFilterConfiguration c = new MarkerFilterConfiguration(newName);
    c.copyFrom(this);
    return c;
  }

  /** Whether the marker is shown by this configuration. */
  public boolean matches(IMarker m) {
    if (!inScope(m))
      return false;
    String description = attributeText(m, IMarker.MESSAGE);
    if (!text.isEmpty()) {
      boolean contains = description.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT));
      if (contains != containsText)
        return false;
    }
    boolean done = attributeBoolean(m, IMarker.DONE, false);
    if (done ? !completed : !notCompleted)
      return false;
    if (!isSeverityChecked(attributeInt(m, IMarker.SEVERITY, IMarker.SEVERITY_INFO)))
      return false;
    return isPriorityChecked(attributeInt(m, IMarker.PRIORITY, IMarker.PRIORITY_NORMAL));
  }

  private boolean inScope(IMarker m) {
    if (scope == SCOPE_ANY)
      return true;
    IResource r = m.getResource();
    if (r == null)
      return false;
    String path = r.getFullPath().toString();
    for (String selected : scopePaths) {
      if (scope == SCOPE_SAME_PROJECT && projectOf(path).equals(projectOf(selected)))
        return true;
      if (path.equals(selected))
        return true;
      if (scope == SCOPE_SELECTED_AND_CHILDREN && path.startsWith(selected.endsWith("/") ? selected : selected + "/"))
        return true;
    }
    return false;
  }

  /** The name of the project of a full path ({@code /Project/folder/file}: Project). */
  private static String projectOf(String fullPath) {
    String p = fullPath.startsWith("/") ? fullPath.substring(1) : fullPath;
    int slash = p.indexOf('/');
    return slash < 0 ? p : p.substring(0, slash);
  }

  private static String attributeText(IMarker m, String name) {
    try {
      Object v = m.getAttribute(name);
      return v == null ? "" : v.toString();
    } catch (CoreException | RuntimeException e) {
      return "";
    }
  }

  private static boolean attributeBoolean(IMarker m, String name, boolean def) {
    try {
      return m.getAttribute(name, def);
    } catch (RuntimeException e) {
      return def;
    }
  }

  private static int attributeInt(IMarker m, String name, int def) {
    try {
      return m.getAttribute(name, def);
    } catch (RuntimeException e) {
      return def;
    }
  }
}
