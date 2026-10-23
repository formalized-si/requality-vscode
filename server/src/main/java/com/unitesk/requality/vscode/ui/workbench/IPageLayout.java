package com.unitesk.requality.vscode.ui.workbench;

/** {@code org.eclipse.ui.IPageLayout}: the initial arrangement of views and the editor area of a perspective. */
public interface IPageLayout {
  /** {@code org.eclipse.ui.IPageLayout#LEFT}. */
  int LEFT = 1;
  /** {@code org.eclipse.ui.IPageLayout#RIGHT}. */
  int RIGHT = 2;
  /** {@code org.eclipse.ui.IPageLayout#TOP}. */
  int TOP = 3;
  /** {@code org.eclipse.ui.IPageLayout#BOTTOM}. */
  int BOTTOM = 4;
  /** {@code org.eclipse.ui.IPageLayout#ID_EDITOR_AREA}. */
  String ID_EDITOR_AREA = "org.eclipse.ui.editorss";
  /** {@code org.eclipse.ui.IPageLayout#ID_PROJECT_EXPLORER}. */
  String ID_PROJECT_EXPLORER = "org.eclipse.ui.navigator.ProjectExplorer";
  /** {@code org.eclipse.ui.IPageLayout#ID_OUTLINE}. */
  String ID_OUTLINE = "org.eclipse.ui.views.ContentOutline";
  /** {@code org.eclipse.ui.IPageLayout#ID_TASK_LIST}. */
  String ID_TASK_LIST = "org.eclipse.ui.views.TaskList";
  /** {@code org.eclipse.ui.IPageLayout#ID_PROBLEM_VIEW}. */
  String ID_PROBLEM_VIEW = "org.eclipse.ui.views.ProblemView";

  /** {@code org.eclipse.ui.IPageLayout#getEditorArea()}. */
  String getEditorArea();

  /** {@code org.eclipse.ui.IPageLayout#addView(String, int, float, String)}. */
  void addView(String viewId, int relationship, float ratio, String refId);

  /** {@code org.eclipse.ui.IPageLayout#addPlaceholder(String, int, float, String)}: recorded, not shown. */
  void addPlaceholder(String viewId, int relationship, float ratio, String refId);

  /** {@code org.eclipse.ui.IPageLayout#createFolder(String, int, float, String)}. */
  IFolderLayout createFolder(String folderId, int relationship, float ratio, String refId);

  /** {@code org.eclipse.ui.IPageLayout#addShowViewShortcut(String)}: recorded (the Show View menu of VS Code). */
  void addShowViewShortcut(String viewId);

  /** {@code org.eclipse.ui.IPageLayout#addNewWizardShortcut(String)}: recorded (the New menu of VS Code). */
  void addNewWizardShortcut(String wizardId);

  /** {@code org.eclipse.ui.IPageLayout#addPerspectiveShortcut(String)}: recorded (the perspective bar of VS Code). */
  void addPerspectiveShortcut(String perspectiveId);

  /** {@code org.eclipse.ui.IPageLayout#addActionSet(String)}: VSCODE: action sets have no meaning here, recorded only. */
  void addActionSet(String actionSetId);

  /** {@code org.eclipse.ui.IPageLayout#setEditorAreaVisible(boolean)}. */
  void setEditorAreaVisible(boolean showEditorArea);
}
