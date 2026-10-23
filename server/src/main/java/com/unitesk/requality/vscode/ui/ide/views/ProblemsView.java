package com.unitesk.requality.vscode.ui.ide.views;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.unitesk.requality.vscode.ui.ide.markers.MarkerFilterConfiguration;
import com.unitesk.requality.vscode.ui.resources.IMarker;
import com.unitesk.requality.vscode.ui.runtime.CoreException;

/**
 * {@code org.eclipse.ui.internal.views.markers.ProblemsView}: the problems of the workspace (the problem markers with
 * their severity: Description, Resource, Path, Location, Type), grouped by severity, shown in the Requality panel. Its
 * Show menu has the configurations of the problems generator (the errors and warnings of the selection, the project,
 * or the workspace), disabled at first.
 */
public class ProblemsView extends MarkerView {
  public static final String ID = "org.eclipse.ui.views.ProblemView";
  /** {@code com.unitesk.requality.marker}: the markers of the Requality checkers (a problem marker subtype). */
  private static final String REQUALITY_MARKER = "com.unitesk.requality.marker";
  /** The labels of the marker types of the problems ({@code name} of the marker type declarations). */
  private static final Map<String, String> TYPE_LABELS = Map.of(IMarker.PROBLEM, "Problem", REQUALITY_MARKER,
      "Requality Problem");

  public ProblemsView() {
    super(IMarker.PROBLEM);
  }

  @Override
  protected String getViewId() {
    return ID;
  }

  @Override
  protected String getDefaultTitle() {
    return "Problems";
  }

  @Override
  protected boolean hasSeverity() {
    return true;
  }

  @Override
  protected boolean hasQuickFix() {
    return true;
  }

  @Override
  protected boolean groupsByType() {
    return true;
  }

  /** {@code org.eclipse.ui.ide.severity}: the default grouping of the problems generator. */
  @Override
  protected String defaultGroupBy() {
    return GROUP_SEVERITY;
  }

  @Override
  protected String markerName() {
    return "Problem";
  }

  /** The type label of the marker ({@code MarkerTypeField}): the marker type's name, the subtypes by their ids. */
  @Override
  protected String typeLabel(IMarker m) {
    try {
      String type = m.getType();
      return TYPE_LABELS.getOrDefault(type, "Problem");
    } catch (CoreException e) {
      return "Problem";
    }
  }

  /**
   * The configurations of the problems generator ({@code markerFieldConfiguration} of org.eclipse.ui.ide), in the order
   * of its plugin.xml, with the names of plugin.properties.
   */
  @Override
  protected List<MarkerFilterConfiguration> defaultConfigurations() {
    List<MarkerFilterConfiguration> r = new ArrayList<>();
    int scopeProject = MarkerFilterConfiguration.SCOPE_SAME_PROJECT;
    int scopeSelected = MarkerFilterConfiguration.SCOPE_SELECTED_AND_CHILDREN;
    r.add(new SeverityConfiguration("Errors/Warnings on Project", scopeProject, true, true, false));
    r.add(new SeverityConfiguration("Errors/Warnings on Selection", scopeSelected, true, true, false));
    r.add(new SeverityConfiguration("Warnings on Selection", scopeSelected, false, true, false));
    r.add(new SeverityConfiguration("All Errors on Workspace", MarkerFilterConfiguration.SCOPE_ANY, true, false,
        false));
    return r;
  }

  /**
   * A configuration of the Show menu of the problems: the markers of its scope whose severities are checked
   * ({@code ErrorsWarningsParameters}, {@code AllErrorsParameters}, {@code SelectedWarningsParameters}).
   */
  static final class SeverityConfiguration extends MarkerFilterConfiguration {
    SeverityConfiguration(String name, int scope, boolean errors, boolean warnings, boolean infos) {
      super(name);
      setScope(scope);
      setSeverityChecked(IMarker.SEVERITY_ERROR, errors);
      setSeverityChecked(IMarker.SEVERITY_WARNING, warnings);
      setSeverityChecked(IMarker.SEVERITY_INFO, infos);
    }
  }
}
