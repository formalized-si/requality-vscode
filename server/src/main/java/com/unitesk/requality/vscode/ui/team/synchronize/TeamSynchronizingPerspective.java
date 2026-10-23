package com.unitesk.requality.vscode.ui.team.synchronize;

import com.unitesk.requality.vscode.ui.team.ui.history.IHistoryView;
import com.unitesk.requality.vscode.ui.workbench.IFolderLayout;
import com.unitesk.requality.vscode.ui.workbench.IPageLayout;
import com.unitesk.requality.vscode.ui.workbench.IPerspectiveFactory;

/**
 * {@code org.eclipse.team.internal.ui.synchronize.TeamSynchronizingPerspective}: the Team Synchronizing perspective
 * (Window &gt; Perspective &gt; Team Synchronizing): the Synchronize view beside the editors, the History view with
 * the Tasks and Problems views below them.
 */
public class TeamSynchronizingPerspective implements IPerspectiveFactory {
  /** {@code org.eclipse.team.ui.TeamSynchronizingPerspective}: the id of the perspective. */
  public static final String ID = "org.eclipse.team.ui.TeamSynchronizingPerspective";

  @Override
  public void createInitialLayout(IPageLayout layout) {
    defineActions(layout);
    defineLayout(layout);
  }

  /** The shortcuts and action sets of the perspective (recorded: VS Code has its own menus). */
  public void defineActions(IPageLayout layout) {
    layout.addNewWizardShortcut("org.eclipse.ui.wizards.new.project");
    layout.addNewWizardShortcut("org.eclipse.ui.wizards.new.folder");
    layout.addNewWizardShortcut("org.eclipse.ui.wizards.new.file");
    layout.addShowViewShortcut(SynchronizeView.ID);
    layout.addShowViewShortcut(IPageLayout.ID_PROJECT_EXPLORER);
    layout.addShowViewShortcut(IPageLayout.ID_OUTLINE);
    layout.addShowViewShortcut(IPageLayout.ID_TASK_LIST);
    layout.addShowViewShortcut(IPageLayout.ID_PROBLEM_VIEW);
    layout.addActionSet("org.eclipse.team.ui.actionSet");
    layout.addPerspectiveShortcut("org.eclipse.ui.resourcePerspective");
  }

  /** The views of the perspective and their places. */
  public void defineLayout(IPageLayout layout) {
    String editorArea = layout.getEditorArea();
    IFolderLayout top = layout.createFolder("top", IPageLayout.LEFT, 0.45f, editorArea);
    top.addView(SynchronizeView.ID);
    IFolderLayout top2 = layout.createFolder("top2", IPageLayout.BOTTOM, 0.80f, editorArea);
    top2.addView(IHistoryView.VIEW_ID);
    top2.addView(IPageLayout.ID_TASK_LIST);
    top2.addView(IPageLayout.ID_PROBLEM_VIEW);
    layout.setEditorAreaVisible(true);
  }
}
