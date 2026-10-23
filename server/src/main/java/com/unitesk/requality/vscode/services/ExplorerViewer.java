package com.unitesk.requality.vscode.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.jface.preference.PropertyChangeEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.ILabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ViewerFilter;
import com.unitesk.requality.vscode.ui.navigator.CommonNavigator;
import com.unitesk.requality.vscode.ui.navigator.CommonViewer;
import com.unitesk.requality.vscode.ui.navigator.framelist.BackAction;
import com.unitesk.requality.vscode.ui.navigator.framelist.ForwardAction;
import com.unitesk.requality.vscode.ui.navigator.framelist.FrameList;
import com.unitesk.requality.vscode.ui.navigator.framelist.UpAction;
import com.unitesk.requality.vscode.ui.resources.IWorkspaceRoot;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * The {@code CommonViewer} of the Requality Explorer. The tree is the VS Code view {@code requality.explorer}
 * ({@link ExplorerService}), so the viewer's input, selection, expansion and filters are those of the VS Code tree:
 * the input is the parent of the VS Code roots (the workspace root, or the project or folder the explorer went
 * into), the viewer filters (e.g. the working set filter) hide items of the VS Code tree, a selection set on the
 * viewer selects and reveals the VS Code items. Frames (Go Into, Back, Forward, Up) work on this viewer like in
 * Eclipse; the frame actions of the view toolbar (CommonNavigatorActionGroup) are shown by the VS Code view title
 * while one of them is enabled.
 */
public final class ExplorerViewer extends CommonViewer {
  /** Items of the VS Code tree that are expanded (reported by the extension). */
  private final Set<String> expanded = ConcurrentHashMap.newKeySet();
  private BackAction backAction;
  private ForwardAction forwardAction;
  private UpAction upAction;
  private ExplorerContentService contentService;
  /** CommonNavigatorActionGroup: "Filters and Customization..." and "Recent Filters" of the view menu. */
  private com.unitesk.requality.vscode.ui.navigator.filters.FilterActionGroup filterGroup;
  private boolean viewMenuFilled;

  /** The content of the VS Code tree: the items of ExplorerService as elements. */
  private static final class ContentProvider implements ITreeContentProvider {
    @Override
    public Object[] getElements(Object inputElement) {
      return getChildren(inputElement);
    }

    @Override
    public Object[] getChildren(Object parentElement) {
      JsonArray items = parentElement == null || parentElement instanceof IWorkspaceRoot ? ExplorerService.projectItems()
          : ExplorerService.childItems(ExplorerService.idOf(parentElement));
      List<Object> r = new ArrayList<>();
      for (JsonElement e : items) {
        Object o = ExplorerService.element(e.getAsJsonObject().get("id").getAsString());
        if (o != null)
          r.add(o);
      }
      return r.toArray();
    }

    @Override
    public Object getParent(Object element) {
      return ExplorerService.parentElement(element);
    }

    @Override
    public boolean hasChildren(Object element) {
      return ExplorerService.hasChildren(element);
    }
  }

  /** The labels of the VS Code tree (decorated, as the NavigatorDecoratingLabelProvider). */
  private static final class LabelProvider implements ILabelProvider {
    private final boolean images;

    LabelProvider(boolean images) {
      this.images = images;
    }

    /** The image of the item (its decorated icon), drawn by the tree of the view; the VS Code tree draws its own. */
    @Override
    public Image getImage(Object element) {
      if (!images || element == null || element instanceof IWorkspaceRoot)
        return null;
      JsonObject item = ExplorerService.itemOf(element);
      String icon = item == null || !item.has("icon") ? null : item.get("icon").getAsString();
      if (icon == null)
        return null;
      // an undecorated file or folder: the workbench image of files and folders (IMG_OBJ_FILE / IMG_OBJ_FOLDER)
      if ("file".equals(icon) || "folder".equals(icon))
        icon = ExplorerService.sharedIconPath("folder".equals(icon));
      return new Image(null, icon);
    }

    @Override
    public String getText(Object element) {
      if (element == null || element instanceof IWorkspaceRoot)
        return "";
      JsonObject item = ExplorerService.itemOf(element);
      return item == null || !item.has("label") ? String.valueOf(element) : item.get("label").getAsString();
    }
  }

  /**
   * The viewer of the explorer part ({@code CommonNavigator.getCommonViewer()} of the Requality Explorer). The
   * navigator is the explorer part; the viewer's tree is the VS Code view, so the viewer has no widget of its own.
   */
  public static ExplorerViewer create(CommonNavigator navigator) {
    return new ExplorerViewer(navigator, new Shell(), false);
  }

  /**
   * The viewer of the explorer part drawn as the tree of the Eclipse view (the SWT tree of the part, in its shell hosted
   * by a VS Code view): its items, selection, expansion and reveal are the tree's.
   */
  public static ExplorerViewer createRendered(CommonNavigator navigator, com.unitesk.requality.vscode.ui.swt.widgets.Composite parent) {
    return new ExplorerViewer(navigator, parent, true);
  }

  /** True when the viewer draws its tree (the Eclipse view); false when the VS Code tree shows its items. */
  private final boolean rendered;

  public boolean isRendered() {
    return rendered;
  }

  private ExplorerViewer(CommonNavigator navigator, com.unitesk.requality.vscode.ui.swt.widgets.Composite parent, boolean rendered) {
    super(com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF.ID, parent,
        rendered ? com.unitesk.requality.vscode.ui.swt.SWT.MULTI | com.unitesk.requality.vscode.ui.swt.SWT.H_SCROLL
            | com.unitesk.requality.vscode.ui.swt.SWT.V_SCROLL : 0);
    this.rendered = rendered;
    contentService = new ExplorerContentService(this);
    setContentProvider(new ContentProvider());
    setLabelProvider(new LabelProvider(rendered));
    navigator.setCommonViewer(this);
    super.setInput(ResourcesPlugin.getWorkspace().getRoot());
    createFrameList();
    // CommonNavigatorActionGroup: Back, Forward and Up in the view toolbar while one of them is enabled.
    FrameList frameList = getFrameList();
    backAction = new BackAction(frameList);
    forwardAction = new ForwardAction(frameList);
    upAction = new UpAction(frameList);
    frameList.addPropertyChangeListener(this::frameListChanged);
  }

  @Override
  public com.unitesk.requality.vscode.ui.navigator.INavigatorContentService getNavigatorContentService() {
    return contentService;
  }

  ExplorerContentService contentService() {
    return contentService;
  }

  /**
   * The view menu of the explorer: after the action providers, the filter actions of the CommonNavigatorActionGroup
   * (added once; "Recent Filters" is filled each time the menu shows, as its menu listener does).
   */
  public synchronized void fillViewMenu(com.unitesk.requality.vscode.ui.workbench.IActionBars bars) {
    if (filterGroup == null)
      filterGroup = new com.unitesk.requality.vscode.ui.navigator.filters.FilterActionGroup(this);
    if (!viewMenuFilled) {
      filterGroup.fillActionBars(bars);
      viewMenuFilled = true;
    }
    filterGroup.updateRecentFilters();
  }

  /** Sets viewer data without the reactions of {@link #setData} (the settings of the extension). */
  void setDataQuietly(String key, Object value) {
    super.setData(key, value);
  }

  /** The user filters set by the "User filters" tab are kept in the settings of the extension. */
  @Override
  @SuppressWarnings("unchecked")
  public void setData(String key, Object value) {
    super.setData(key, value);
    if (com.unitesk.requality.vscode.ui.navigator.filters.UserFiltersTab.RESOURCE_REGEXP_FILTER_DATA.equals(key) && value instanceof List)
      contentService.userFiltersChanged((List<com.unitesk.requality.vscode.ui.navigator.filters.UserFilter>) value);
  }

  private void frameListChanged(PropertyChangeEvent event) {
    if (FrameList.P_RESET.equals(event.getProperty())) {
      upAction.setEnabled(false);
      backAction.setEnabled(false);
      forwardAction.setEnabled(false);
      upAction.update();
    }
    getCommonNavigator().updateTitle();
    ExplorerService.framesChanged();
  }

  public BackAction getBackAction() {
    return backAction;
  }

  public ForwardAction getForwardAction() {
    return forwardAction;
  }

  public UpAction getUpAction() {
    return upAction;
  }

  @Override
  public void setInput(Object newInput) {
    Object old = getInput();
    if (newInput == null)
      newInput = ResourcesPlugin.getWorkspace().getRoot();
    super.setInput(newInput);
    if (old != newInput && (old == null || !old.equals(newInput)))
      ExplorerService.refreshAllNodes();
  }

  @Override
  protected void inputChanged(Object input, Object oldInput) {
    // VSCODE: the VS Code tree is refreshed by setInput.
    if (rendered)
      super.inputChanged(input, oldInput);
  }

  @Override
  public void refresh() {
    ExplorerService.refreshAllNodes();
  }

  @Override
  public void refresh(Object element) {
    if (element == null || element.equals(getInput()) || element instanceof IWorkspaceRoot)
      ExplorerService.refreshAllNodes();
    else
      ExplorerService.refreshElement(element);
  }

  /** The tree of the view shows the items again (ExplorerService batches the refreshes of the explorer). */
  void refreshTree(Object element) {
    if (!rendered || getControl() == null || getControl().isDisposed())
      return;
    if (element == null)
      super.refresh();
    else
      super.refresh(element);
  }

  @Override
  public void update(Object element, String[] properties) {
    refresh(element);
  }

  @Override
  public ISelection getSelection() {
    if (rendered)
      return super.getSelection();
    ISelection s = viewerSelection();
    return s instanceof IStructuredSelection ? s : StructuredSelection.EMPTY;
  }

  /**
   * The selection of the explorer part: the selected tree items (IProject, IFile, IFolder or TreeNode) as the part
   * set them, or the explorer selection of the SelectionManager when the part has none.
   */
  private static ISelection viewerSelection() {
    ISelection s = com.unitesk.requality.vscode.ui.workbench.PlatformUI.getWorkbench().getActiveWorkbenchWindow()
        .getActivePage().getSelection(com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF.ID);
    return s != null ? s : new StructuredSelection(ExplorerService.selectedObjects());
  }

  /** The selection of the VS Code tree changed: the selection listeners of the viewer are notified (JFace). */
  void fireSelection(ISelection selection) {
    fireSelectionChanged(new com.unitesk.requality.vscode.ui.jface.viewers.SelectionChangedEvent(this, selection));
  }

  /** Selects and reveals the elements in the VS Code tree (and makes them the explorer selection at once). */
  @Override
  public void setSelection(ISelection selection, boolean reveal) {
    if (rendered) {
      super.setSelection(selection, reveal);
      return;
    }
    List<?> elements = selection instanceof IStructuredSelection ? ((IStructuredSelection) selection).toList() : List.of();
    // a project and its root node are one item of the VS Code tree (one id)
    Set<String> idSet = new java.util.LinkedHashSet<>();
    for (Object o : elements) {
      String id = ExplorerService.idOf(o);
      if (id != null)
        idSet.add(id);
    }
    JsonArray ids = new JsonArray();
    idSet.forEach(ids::add);
    SelectionService.select("explorer", ids);
    for (int i = 0; i < ids.size(); i++)
      ExplorerService.revealItem(ids.get(i).getAsString(), i == 0, false);
  }

  @Override
  public void reveal(Object element) {
    if (rendered) {
      super.reveal(element);
      return;
    }
    String id = ExplorerService.idOf(element);
    if (id != null)
      ExplorerService.revealItem(id, false, false);
  }

  @Override
  public Object[] getExpandedElements() {
    if (rendered)
      return super.getExpandedElements();
    List<Object> r = new ArrayList<>();
    for (String id : expanded) {
      Object o = ExplorerService.element(id);
      if (o != null)
        r.add(o);
    }
    return r.toArray();
  }

  @Override
  public void setExpandedElements(Object... elements) {
    if (rendered) {
      super.setExpandedElements(elements);
      return;
    }
    for (Object o : elements) {
      String id = ExplorerService.idOf(o);
      if (id != null)
        ExplorerService.revealItem(id, false, true);
    }
  }

  @Override
  public boolean getExpandedState(Object element) {
    if (rendered)
      return super.getExpandedState(element);
    String id = ExplorerService.idOf(element);
    return id != null && expanded.contains(id);
  }

  @Override
  public void setExpandedState(Object element, boolean expand) {
    if (rendered) {
      super.setExpandedState(element, expand);
      return;
    }
    if (expand)
      setExpandedElements(element);
  }

  @Override
  public void expandToLevel(Object element, int level) {
    if (rendered) {
      super.expandToLevel(element, level);
      return;
    }
    setExpandedElements(element);
  }

  @Override
  public boolean isExpandable(Object element) {
    if (rendered)
      return super.isExpandable(element);
    return ExplorerService.hasChildren(element);
  }

  /** The VS Code tree expanded or collapsed an item. */
  void expansionChanged(String id, boolean isExpanded) {
    if (isExpanded)
      expanded.add(id);
    else
      expanded.remove(id);
  }

  /** The elements of the items the viewer filters let through (StructuredViewer.getFilteredChildren). */
  JsonArray filter(Object parentElement, JsonArray items) {
    ViewerFilter[] filters = getFilters();
    if (filters.length == 0)
      return items;
    JsonArray r = new JsonArray();
    for (JsonElement e : items) {
      Object o = ExplorerService.element(e.getAsJsonObject().get("id").getAsString());
      if (o == null || Arrays.stream(filters).allMatch(f -> f.select((Viewer) this, parentElement, o)))
        r.add(e);
    }
    return r;
  }
}
