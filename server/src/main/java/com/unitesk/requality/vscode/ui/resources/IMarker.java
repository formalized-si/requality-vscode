package com.unitesk.requality.vscode.ui.resources;

import java.util.Map;

import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;

/**
 * {@code org.eclipse.core.resources.IMarker}. Markers are kept by the {@link MarkerStore}; problem markers are shown
 * as VS Code diagnostics and explorer decorations.
 */
public interface IMarker extends IAdaptable {
  String MARKER = "org.eclipse.core.resources.marker";
  String PROBLEM = "org.eclipse.core.resources.problemmarker";
  String TASK = "org.eclipse.core.resources.taskmarker";
  String TEXT = "org.eclipse.core.resources.textmarker";
  String BOOKMARK = "org.eclipse.core.resources.bookmark";
  String SEVERITY = "severity";
  String MESSAGE = "message";
  String LOCATION = "location";
  String PRIORITY = "priority";
  String DONE = "done";
  String CHAR_START = "charStart";
  String CHAR_END = "charEnd";
  String LINE_NUMBER = "lineNumber";
  String TRANSIENT = "transient";
  String USER_EDITABLE = "userEditable";
  String SOURCE_ID = "sourceId";
  int PRIORITY_HIGH = 2;
  int PRIORITY_LOW = 0;
  int PRIORITY_NORMAL = 1;
  int SEVERITY_ERROR = 2;
  int SEVERITY_WARNING = 1;
  int SEVERITY_INFO = 0;

  void delete() throws CoreException;

  boolean exists();

  Object getAttribute(String attributeName) throws CoreException;

  int getAttribute(String attributeName, int defaultValue);

  String getAttribute(String attributeName, String defaultValue);

  boolean getAttribute(String attributeName, boolean defaultValue);

  Map<String, Object> getAttributes() throws CoreException;

  Object[] getAttributes(String[] attributeNames) throws CoreException;

  long getCreationTime() throws CoreException;

  long getId();

  IResource getResource();

  String getType() throws CoreException;

  boolean isSubtypeOf(String superType) throws CoreException;

  void setAttribute(String attributeName, int value) throws CoreException;

  void setAttribute(String attributeName, Object value) throws CoreException;

  void setAttribute(String attributeName, boolean value) throws CoreException;

  void setAttributes(String[] attributeNames, Object[] values) throws CoreException;

  void setAttributes(Map<String, ? extends Object> attributes) throws CoreException;
}
