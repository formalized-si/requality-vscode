package com.unitesk.requality.vscode.ui.workbench;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.unitesk.requality.core.log.RLogger;

/**
 * {@code org.eclipse.ui.internal.PageLayout}: the {@link IPageLayout} of the VS Code workbench.
 *
 * <p>VS Code places the views itself, so the relationship, ratio and reference of the placements are only recorded
 * (see {@link #getViews()}). Each view of the layout is created like an Eclipse perspective creates it; when the
 * layout is applied to show the perspective ({@link #PageLayout(boolean)} with true), the view is also shown in VS
 * Code ({@link IWorkbenchPage#VIEW_VISIBLE}, no activation). A view that VS Code does not know is recorded as
 * unavailable and logged; Eclipse would show an error placeholder in its place. Placeholders, folders' placeholders
 * and the shortcuts are recorded only.
 */
public class PageLayout implements IPageLayout {

  /** A view placed by the layout: a view of {@link IPageLayout#addView} or of an {@link IFolderLayout}. */
  public static final class ViewPlacement {
    private final String viewId;
    private final int relationship;
    private final float ratio;
    private final String refId;
    private final boolean available;
    private final boolean placeholder;

    ViewPlacement(String viewId, int relationship, float ratio, String refId, boolean available, boolean placeholder) {
      this.viewId = viewId;
      this.relationship = relationship;
      this.ratio = ratio;
      this.refId = refId;
      this.available = available;
      this.placeholder = placeholder;
    }

    /** The id of the requested view. */
    public String getViewId() {
      return viewId;
    }

    /**
     * One of {@link IPageLayout#LEFT}, {@link IPageLayout#RIGHT}, {@link IPageLayout#TOP}, {@link IPageLayout#BOTTOM};
     * 0 for a view of a folder (its position is the folder's, see {@link #getRefId()}).
     */
    public int getRelationship() {
      return relationship;
    }

    /** The requested ratio of the new view (0 for a view of a folder). */
    public float getRatio() {
      return ratio;
    }

    /** The id of the reference part (the editor area or another view), or of the folder of a folder's view. */
    public String getRefId() {
      return refId;
    }

    /** False if VS Code has no such view; the view is not shown. */
    public boolean isAvailable() {
      return available;
    }

    /** True for a placeholder: the view is recorded, not shown. */
    public boolean isPlaceholder() {
      return placeholder;
    }
  }

  /** {@code org.eclipse.ui.internal.FolderLayout}: a folder of the layout. */
  private final class FolderLayout implements IFolderLayout {
    private final String folderId;

    FolderLayout(String folderId) {
      this.folderId = folderId;
    }

    @Override
    public void addView(String viewId) {
      place(viewId, 0, 0f, folderId, false);
    }

    @Override
    public void addPlaceholder(String viewId) {
      place(viewId, 0, 0f, folderId, true);
    }

    @Override
    public String getId() {
      return folderId;
    }
  }

  private final boolean show;
  private final List<ViewPlacement> views = new ArrayList<>();
  private final List<String> showViewShortcuts = new ArrayList<>();
  private final List<String> newWizardShortcuts = new ArrayList<>();
  private final List<String> perspectiveShortcuts = new ArrayList<>();
  private final List<String> actionSets = new ArrayList<>();
  private boolean editorAreaVisible = true;

  /** A layout that creates its views without showing them (the workbench at start). */
  public PageLayout() {
    this(false);
  }

  /**
   * @param show true if the views are shown in VS Code as the layout is applied ({@code setPerspective}), false if
   *          they are only created
   */
  public PageLayout(boolean show) {
    this.show = show;
  }

  @Override
  public String getEditorArea() {
    return ID_EDITOR_AREA;
  }

  @Override
  public void addView(String viewId, int relationship, float ratio, String refId) {
    place(viewId, relationship, ratio, refId, false);
  }

  @Override
  public void addPlaceholder(String viewId, int relationship, float ratio, String refId) {
    place(viewId, relationship, ratio, refId, true);
  }

  @Override
  public IFolderLayout createFolder(String folderId, int relationship, float ratio, String refId) {
    folders.put(folderId, new String[] { Integer.toString(relationship), refId });
    return new FolderLayout(folderId);
  }

  /** The folders of the layout: their relationship and reference. */
  private final java.util.Map<String, String[]> folders = new java.util.HashMap<>();

  /**
   * The region of the window a view or a folder is in: {@link IPageLayout#LEFT}, {@link IPageLayout#RIGHT},
   * {@link IPageLayout#TOP} or {@link IPageLayout#BOTTOM} of the editor area. A view placed relative to another view or
   * folder is in the region of that part (the Links Explorer below the Outline, right of the editor area); a view of a
   * folder is in the region of its folder. 0 when the part is not placed.
   */
  public int regionOf(String id) {
    return regionOf(id, new java.util.HashSet<>());
  }

  private int regionOf(String id, java.util.Set<String> seen) {
    if (id == null || !seen.add(id))
      return 0;
    int relationship = 0;
    String ref = null;
    boolean found = false;
    for (ViewPlacement p : views)
      if (p.getViewId().equals(id)) {
        relationship = p.getRelationship();
        ref = p.getRefId();
        found = true;
        break;
      }
    if (!found && folders.containsKey(id)) {
      relationship = Integer.parseInt(folders.get(id)[0]);
      ref = folders.get(id)[1];
      found = true;
    }
    if (!found)
      return 0;
    if (relationship != 0 && getEditorArea().equals(ref))
      return relationship;
    int region = regionOf(ref, seen);
    return region != 0 ? region : relationship;
  }

  private void place(String viewId, int relationship, float ratio, String refId, boolean placeholder) {
    boolean available = true;
    if (!placeholder) {
      try {
        Workbench.PAGE.showView(viewId, null, show ? IWorkbenchPage.VIEW_VISIBLE : IWorkbenchPage.VIEW_CREATE);
      } catch (PartInitException e) {
        available = false;
        RLogger.logError(e);
      }
    }
    views.add(new ViewPlacement(viewId, relationship, ratio, refId, available, placeholder));
  }

  @Override
  public void addShowViewShortcut(String viewId) {
    showViewShortcuts.add(viewId);
  }

  @Override
  public void addNewWizardShortcut(String wizardId) {
    newWizardShortcuts.add(wizardId);
  }

  @Override
  public void addPerspectiveShortcut(String perspectiveId) {
    perspectiveShortcuts.add(perspectiveId);
  }

  @Override
  public void addActionSet(String actionSetId) {
    actionSets.add(actionSetId);
  }

  @Override
  public void setEditorAreaVisible(boolean showEditorArea) {
    editorAreaVisible = showEditorArea;
  }

  /** Whether the editor area is visible (Eclipse default: visible). */
  public boolean isEditorAreaVisible() {
    return editorAreaVisible;
  }

  /** Whether the views of the layout are shown as the layout is applied. */
  public boolean isShowing() {
    return show;
  }

  /** The views and placeholders requested, in order, unmodifiable. */
  public List<ViewPlacement> getViews() {
    return Collections.unmodifiableList(views);
  }

  /** The show view shortcuts requested, in order, unmodifiable. */
  public List<String> getShowViewShortcuts() {
    return Collections.unmodifiableList(showViewShortcuts);
  }

  /** The new wizard shortcuts requested, in order, unmodifiable. */
  public List<String> getNewWizardShortcuts() {
    return Collections.unmodifiableList(newWizardShortcuts);
  }

  /** The perspective shortcuts requested, in order, unmodifiable. */
  public List<String> getPerspectiveShortcuts() {
    return Collections.unmodifiableList(perspectiveShortcuts);
  }

  /** The action sets requested, in order, unmodifiable. */
  public List<String> getActionSets() {
    return Collections.unmodifiableList(actionSets);
  }
}
