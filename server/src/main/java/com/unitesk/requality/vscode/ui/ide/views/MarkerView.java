package com.unitesk.requality.vscode.ui.ide.views;

import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.core.ServerContext;
import com.unitesk.requality.vscode.services.MenuService;
import com.unitesk.requality.vscode.ui.ide.IDE;
import com.unitesk.requality.vscode.ui.ide.dialogs.DialogMarkerProperties;
import com.unitesk.requality.vscode.ui.ide.dialogs.DialogTaskProperties;
import com.unitesk.requality.vscode.ui.ide.markers.FiltersConfigurationDialog;
import com.unitesk.requality.vscode.ui.ide.markers.MarkerFields;
import com.unitesk.requality.vscode.ui.ide.markers.MarkerFilterConfiguration;
import com.unitesk.requality.vscode.ui.ide.markers.MarkersViewColumnsDialog;
import com.unitesk.requality.vscode.ui.ide.undo.DeleteMarkersOperation;
import com.unitesk.requality.vscode.ui.ide.undo.UpdateMarkersOperation;
import com.unitesk.requality.vscode.ui.ide.undo.WorkspaceUndoUtil;
import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.action.ActionContributionItem;
import com.unitesk.requality.vscode.ui.jface.action.IAction;
import com.unitesk.requality.vscode.ui.jface.action.IMenuManager;
import com.unitesk.requality.vscode.ui.jface.action.MenuManager;
import com.unitesk.requality.vscode.ui.jface.action.Separator;
import com.unitesk.requality.vscode.ui.jface.action.ToolBarManager;
import com.unitesk.requality.vscode.ui.jface.dialogs.ElementListSelectionDialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.ErrorDialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.dialogs.InputDialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;
import com.unitesk.requality.vscode.ui.jface.viewers.DoubleClickEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.IDoubleClickListener;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionChangedListener;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.ITableLabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.ITreeViewerListener;
import com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.SelectionChangedEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeExpansionEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IMarker;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.MarkerStore;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.dnd.Clipboard;
import com.unitesk.requality.vscode.ui.swt.dnd.TextTransfer;
import com.unitesk.requality.vscode.ui.swt.dnd.Transfer;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Menu;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.vscode.ui.swt.widgets.Tree;
import com.unitesk.requality.vscode.ui.swt.widgets.TreeColumn;
import com.unitesk.requality.vscode.ui.swt.widgets.TreeItem;
import com.unitesk.requality.vscode.ui.workbench.FileEditorInput;
import com.unitesk.requality.vscode.ui.workbench.IActionBars;
import com.unitesk.requality.vscode.ui.workbench.IEditorPart;
import com.unitesk.requality.vscode.ui.workbench.ISelectionListener;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPart;
import com.unitesk.requality.vscode.ui.workbench.PartInitException;
import com.unitesk.requality.vscode.ui.workbench.PartSite;
import com.unitesk.requality.vscode.ui.workbench.ViewPart;
import com.unitesk.requality.vscode.ui.workbench.Workbench;
import com.unitesk.requality.vscode.ui.workbench.actions.ActionFactory;

/**
 * {@code org.eclipse.ui.internal.views.markers.ExtendedMarkersView}: the tree of the markers of one marker type of the
 * workspace (the Bookmarks and Tasks views). The columns are the fields of the content generator of the type, shown
 * or hidden with Configure Columns...; the markers are shown by the configurations of the Show menu and the Filters
 * dialog, filtered by the text of the type filter text, limited to the items per group, sorted by a field (Sort By or
 * the column header) and grouped by type (Group By: the group rows are the parents of the markers, collapsed until
 * expanded). The scope of the configurations is the selection of the workbench. The view menu and the context menu
 * are the ones of Eclipse ({@code menu:} and {@code popup:} contributions of org.eclipse.ui.ide).
 */
public abstract class MarkerView extends ViewPart {
  /** {@code MarkerGroupMenu.GroupBy}: no grouping, the grouping by the type of the markers, by their severity. */
  private static final String GROUP_NONE = "none";
  private static final String GROUP_TYPE = "type";
  static final String GROUP_SEVERITY = "severity";
  private static final int DEFAULT_LIMIT = 100;
  /** The severities of the groups of Group By Severity, in their order (errors, warnings, infos). */
  private static final int[] SEVERITY_ORDER = { IMarker.SEVERITY_ERROR, IMarker.SEVERITY_WARNING,
      IMarker.SEVERITY_INFO };
  /** {@code ExtendedMarkersView.SUMMARY_BREAKDOWN} (errorsAndWarningsSummaryBreakdown of the messages). */
  private static final String SUMMARY_BREAKDOWN = "{0,choice,0#0 errors|1#{0,number,integer} error"
      + "|1<{0,number,integer} errors}, {1,choice,0#0 warnings|1#{1,number,integer} warning"
      + "|1<{1,number,integer} warnings}, {2,choice,0#0 others|1#{2,number,integer} other|1<{2,number,integer} others}";
  /** {@code problem_filter_matchedMessage}: the summary of the problems and the items matched by the filters. */
  private static final String PROBLEM_FILTER_MATCHED = "{0} (Filter matched {1} of {2} items)";
  /** {@code MarkerSeverityAndDescriptionField}: the severity images of Eclipse (icons/full/obj16). */
  private static final String ERROR_ICON = "icons/ide/error_tsk.png";
  private static final String WARNING_ICON = "icons/ide/warn_tsk.png";
  private static final String INFO_ICON = "icons/ide/info_tsk.png";
  /** {@code MarkerView.ImageDescriptor}s of the icons of org.eclipse.ui.ide (icons/full/obj16). */
  private static final String COMPLETE_ICON = "icons/ide/complete_tsk.png";
  private static final String INCOMPLETE_ICON = "icons/ide/incomplete_tsk.png";
  private static final String HIGH_PRIORITY_ICON = "icons/ide/hprio_tsk.png";
  private static final String LOW_PRIORITY_ICON = "icons/ide/lprio_tsk.png";
  private static final String COMPLETE_HEADER_ICON = "icons/ide/header_complete.png";
  private static final String PRIORITY_HEADER_ICON = "icons/ide/header_priority.png";
  /** The numbers of the views opened by Open Another (the secondary ids of the views, from 1). */
  private static final Set<Integer> INSTANCES = new HashSet<>();

  /** The marker type of the view (org.eclipse.core.resources.bookmark or taskmarker). */
  private final String markerType;
  /** The fields of the generator of the type, in the order of the generator. */
  private final List<String> generatorFields;
  /** The shown columns, in their order. */
  private final List<String> shownFields = new ArrayList<>();
  private final Map<String, Integer> widths = new HashMap<>();
  private final List<MarkerFilterConfiguration> configurations = new ArrayList<>();
  private boolean matchAll;
  private boolean limitEnabled = true;
  private int limit = DEFAULT_LIMIT;
  private String groupBy;
  private String sortField;
  private boolean ascending = true;
  /** The text of the type filter text (Show Text Filter); the markers whose fields contain all its words. */
  private String filterPattern = "";
  private boolean showFilterText;
  /** The names of the group rows expanded by the user (kept over the refreshes, as Eclipse's categoriesToExpand). */
  private final Set<String> categoriesToExpand = new HashSet<>();
  /** The full paths of the resources selected in the workbench: the scope of the configurations. */
  private List<String> selectedPaths = new ArrayList<>();
  /** The number of the view of Open Another (0 for the view of the workbench). */
  private int instance;
  private String customTitle;

  private Text filterText;
  private Tree tree;
  private TreeViewer viewer;
  private final List<TreeColumn> columns = new ArrayList<>();
  private Action goToAction;
  private Action copyAction;
  private Action copyDescriptionAction;
  private Action copyQualifiedNameAction;
  private Action deleteAction;
  private Action selectAllAction;
  private Action propertiesAction;
  private Action markCompletedAction;
  private Action deleteCompletedAction;
  private Action addTaskAction;
  private Action filterAction;
  private Action openAnotherAction;
  private Action quickFixAction;
  private final Runnable refresher = this::refresh;
  private final Consumer<List<IResource>> changeListener = changes -> refreshLater();
  private final ISelectionListener selectionListener = this::workbenchSelectionChanged;
  private volatile boolean disposed;
  /** The markers shown by the view (the items of the group rows, or the items of the view), as last shown. */
  private List<IMarker> shownMarkers = new ArrayList<>();
  /** The number of the markers shown by the view, as last shown. */
  private int shownCount;
  /** The errors, warnings and others (infos and markers without severity) of the markers matched, as last shown. */
  private int[] summaryCounts = new int[3];

  protected MarkerView(String markerType) {
    this.markerType = markerType;
    this.generatorFields = MarkerFields.generatorFields(markerType);
    for (String f : generatorFields)
      if (MarkerFields.visibleByDefault(markerType, f))
        shownFields.add(f);
    for (String f : generatorFields)
      widths.put(f, MarkerFields.width(f));
    this.groupBy = defaultGroupBy();
    this.configurations.addAll(defaultConfigurations());
  }

  /** The marker type of the view. */
  protected String getMarkerType() {
    return markerType;
  }

  /** Whether the view's marker type is tasks (a completed column, priority, and the task actions). */
  protected boolean isTasks() {
    return IMarker.TASK.equals(markerType);
  }

  /** The id of the view (set by the subclasses: {@code org.eclipse.ui.views.BookmarkView} or TaskList). */
  protected abstract String getViewId();

  /** The name of the view as Eclipse registers it ({@code Bookmarks}, {@code Tasks} or {@code Problems}). */
  protected abstract String getDefaultTitle();

  /** Whether the view shows the severities of its markers (the problems): the images, the sort and the summary. */
  protected boolean hasSeverity() {
    return false;
  }

  /** Whether the view offers Quick Fix (the marker resolutions of {@link IDE#getMarkerHelpRegistry()}). */
  protected boolean hasQuickFix() {
    return false;
  }

  /** Whether the view offers Group By Type (the markers grouped by the label of their marker type). */
  protected boolean groupsByType() {
    return isTasks();
  }

  /** The grouping of the view when it is opened: the severity for the problems, no grouping otherwise. */
  protected String defaultGroupBy() {
    return GROUP_NONE;
  }

  /** The configurations of the Show menu of the view when it is opened (not enabled). */
  protected List<MarkerFilterConfiguration> defaultConfigurations() {
    return new ArrayList<>();
  }

  /** The name of the markers of the view in its messages: Task, Bookmark or Problem. */
  protected String markerName() {
    return isTasks() ? "Task" : "Bookmark";
  }

  /** The label of the marker type of a marker: the Type column, and the group of Group By Type. */
  protected String typeLabel(IMarker m) {
    return markerName();
  }

  /** The id of the view instance for its menus: the view id, and the number of an Open Another view. */
  private String menuId() {
    return instance == 0 ? getViewId() : getViewId() + "#" + instance;
  }

  /** The id of the view menu of the view ({@code menu:} of the view id, as the Requality views name theirs). */
  String viewMenuId() {
    return menuId() + ".viewMenu";
  }

  /** The id of the context menu of the view ({@code popup:org.eclipse.ui.ide.MarkersView}). */
  String popupMenuId() {
    return menuId() + ".popup";
  }

  @Override
  public String getTitle() {
    return customTitle != null ? customTitle : getDefaultTitle();
  }

  @Override
  public void createPartControl(Composite parent) {
    parent.setLayout(new GridLayout(1, false));
    // the type filter text of the view (Show Text Filter): hidden until shown
    filterText = new Text(parent, SWT.SEARCH | SWT.ICON_SEARCH | SWT.BORDER);
    filterText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    filterText.setMessage("type filter text");
    filterText.addModifyListener(e -> {
      filterPattern = filterText.getText();
      refresh();
    });
    filterText.setVisible(showFilterText);

    viewer = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
    tree = viewer.getTree();
    tree.setLayoutData(new GridData(GridData.FILL_BOTH));
    tree.setHeaderVisible(true);
    tree.setLinesVisible(true);
    createColumns();
    // CompletionEditingSupport: a click on the completion cell of a task toggles its completion (the check box image
    // of the cell); VSCODE: the cell editor is the click event of the cell (Tree MouseDown with the column index)
    tree.addListener(SWT.MouseDown, e -> {
      if (!(e.item instanceof TreeItem) || !(((TreeItem) e.item).getData() instanceof IMarker))
        return;
      if (e.index < 0 || e.index >= shownFields.size() || !MarkerFields.COMPLETED.equals(shownFields.get(e.index)))
        return;
      IMarker marker = (IMarker) ((TreeItem) e.item).getData();
      if (!editable(marker))
        return;
      setCompleted(marker, !attribute(marker, IMarker.DONE, false));
    });
    // the check event of a task item changes its completion too
    tree.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        if (e.detail == SWT.CHECK && e.item instanceof TreeItem && ((TreeItem) e.item).getData() instanceof IMarker) {
          TreeItem item = (TreeItem) e.item;
          setCompleted((IMarker) item.getData(), item.getChecked());
        }
      }
    });

    viewer.setContentProvider(new MarkerContentProvider());
    viewer.setLabelProvider(new MarkerLabelProvider());
    viewer.addDoubleClickListener(new IDoubleClickListener() {
      @Override
      public void doubleClick(DoubleClickEvent event) {
        if (firstSelected() instanceof GroupRow) {
          GroupRow group = (GroupRow) firstSelected();
          viewer.setExpandedState(group, !viewer.getExpandedState(group));
          return;
        }
        goTo(selectedMarkers());
      }
    });
    viewer.addSelectionChangedListener(new ISelectionChangedListener() {
      @Override
      public void selectionChanged(SelectionChangedEvent event) {
        updateActions();
      }
    });
    // the group rows stay expanded as the user expanded them (Eclipse's categoriesToExpand)
    viewer.addTreeListener(new ITreeViewerListener() {
      @Override
      public void treeExpanded(TreeExpansionEvent event) {
        if (event.getElement() instanceof GroupRow)
          categoriesToExpand.add(((GroupRow) event.getElement()).name);
      }

      @Override
      public void treeCollapsed(TreeExpansionEvent event) {
        if (event.getElement() instanceof GroupRow)
          categoriesToExpand.remove(((GroupRow) event.getElement()).name);
      }
    });

    createActions();
    MenuManager menuManager = new MenuManager();
    menuManager.setRemoveAllWhenShown(true);
    menuManager.addMenuListener(this::fillContextMenu);
    Menu menu = menuManager.createContextMenu(tree);
    tree.setMenu(menu);
    getSite().registerContextMenu(menuManager, viewer);
    getSite().setSelectionProvider(viewer);
    ((PartSite) getSite()).setActionBarsUpdater(this::updateActions);

    IActionBars bars = getViewSite().getActionBars();
    bars.setGlobalActionHandler(ActionFactory.COPY.getId(), copyAction);
    bars.setGlobalActionHandler(ActionFactory.DELETE.getId(), deleteAction);
    bars.setGlobalActionHandler(ActionFactory.SELECT_ALL.getId(), selectAllAction);
    bars.setGlobalActionHandler(ActionFactory.PROPERTIES.getId(), propertiesAction);
    if (isTasks())
      bars.setGlobalActionHandler(ActionFactory.ADD_TASK.getId(), addTaskAction);
    initToolBar(bars.getToolBarManager());

    // the view menu and the context menu, listed by the server for the client (menu/items)
    MenuService.register(viewMenuId(), () -> {
      MenuManager m = new MenuManager();
      fillViewMenu(m);
      return m.getItems();
    });
    MenuService.register(popupMenuId(), () -> {
      MenuManager m = new MenuManager();
      fillContextMenu(m);
      return m.getItems();
    });

    MarkerStore.addListener(changeListener);
    // the scope of the configurations is the selection of the workbench (ViewerPageSelectionListener of Eclipse)
    getSite().getPage().addSelectionListener(selectionListener);
    workbenchSelectionChanged(Workbench.activePart(), Workbench.currentSelection());
    refresh();
  }

  @Override
  public void setFocus() {
    if (tree != null && !tree.isDisposed())
      tree.setFocus();
  }

  @Override
  public void dispose() {
    disposed = true;
    MarkerStore.removeListener(changeListener);
    try {
      getSite().getPage().removeSelectionListener(selectionListener);
    } catch (RuntimeException e) {
      // the page is gone with the workbench
    }
    super.dispose();
  }

  /** The columns of the shown fields, in their order (rebuilt when the columns change). */
  private void createColumns() {
    for (TreeColumn c : columns)
      c.dispose();
    columns.clear();
    for (int i = 0; i < shownFields.size(); i++) {
      String field = shownFields.get(i);
      TreeColumn column = new TreeColumn(tree, SWT.LEFT, i);
      column.setText(MarkerFields.header(field));
      column.setWidth(widths.get(field));
      // VSCODE: the header images of the completion and priority columns (Eclipse's column header images)
      if (MarkerFields.COMPLETED.equals(field))
        column.setImage(ImageDescriptor.fromResource(COMPLETE_HEADER_ICON).createImage());
      else if (MarkerFields.PRIORITY.equals(field))
        column.setImage(ImageDescriptor.fromResource(PRIORITY_HEADER_ICON).createImage());
      column.addSelectionListener(new SelectionAdapter() {
        @Override
        public void widgetSelected(SelectionEvent e) {
          sortBy(field);
        }
      });
      columns.add(column);
    }
    // the rows are made again for the columns: the cells of the hidden columns are not kept
    tree.removeAll();
  }

  // ---- contents

  /** The markers of the view: the markers of the type in the workspace. */
  List<IMarker> markers() {
    List<IMarker> r = new ArrayList<>();
    try {
      r.addAll(Arrays.asList(ResourcesPlugin.getWorkspace().getRoot().findMarkers(markerType, true,
          IResource.DEPTH_INFINITE)));
    } catch (CoreException e) {
      ErrorDialog.openError(getSite().getShell(), "Error", null, e.getStatus());
    }
    return r;
  }

  /** Whether a marker is shown by the configurations: any of the checked ones, or all of them when matching all. */
  private boolean passesConfigurations(IMarker m) {
    boolean any = false;
    for (MarkerFilterConfiguration c : configurations) {
      if (!c.isEnabled())
        continue;
      any = true;
      boolean match = c.matches(m);
      if (matchAll && !match)
        return false;
      if (!matchAll && match)
        return true;
    }
    return !any || matchAll;
  }

  /**
   * {@code MarkerPatternFilter}: a marker matches the type filter text when one of its fields contains all the words
   * of the text (case ignored).
   */
  private boolean passesFilterText(IMarker m) {
    if (filterPattern.trim().isEmpty())
      return true;
    String[] words = filterPattern.trim().toLowerCase(Locale.ROOT).split("\\s+");
    for (String field : shownFields) {
      if (MarkerFields.COMPLETED.equals(field))
        continue;
      String value = text(m, field).toLowerCase(Locale.ROOT);
      boolean all = true;
      for (String w : words)
        all &= value.contains(w);
      if (all)
        return true;
    }
    return false;
  }

  /**
   * The rows of the viewer: the group rows of the grouping, with their markers in their sort order, limited per group;
   * without a grouping, the markers themselves.
   */
  private List<Object> rows(List<IMarker> all) {
    List<IMarker> matched = new ArrayList<>();
    for (IMarker m : all)
      if (passesConfigurations(m) && passesFilterText(m))
        matched.add(m);
    if (sortField != null)
      matched.sort(this::compare);
    summaryCounts = new int[3];
    for (IMarker m : matched) {
      int severity = severityGroupOf(m);
      summaryCounts[severity == IMarker.SEVERITY_ERROR ? 0 : severity == IMarker.SEVERITY_WARNING ? 1 : 2]++;
    }
    int max = limitEnabled ? limit : Integer.MAX_VALUE;
    List<Object> r = new ArrayList<>();
    List<IMarker> shown = new ArrayList<>();
    if (GROUP_SEVERITY.equals(groupBy)) {
      for (int severity : SEVERITY_ORDER) {
        List<IMarker> items = new ArrayList<>();
        for (IMarker m : matched)
          if (severityGroupOf(m) == severity)
            items.add(m);
        addGroup(r, shown, severityGroupName(severity), severity, items, max);
      }
    } else if (GROUP_TYPE.equals(groupBy)) {
      // the groups of the types, by their names (a view of one type has one group)
      TreeMap<String, List<IMarker>> byType = new TreeMap<>();
      for (IMarker m : matched)
        byType.computeIfAbsent(typeLabel(m), k -> new ArrayList<>()).add(m);
      for (Map.Entry<String, List<IMarker>> e : byType.entrySet())
        addGroup(r, shown, e.getKey(), -1, e.getValue(), max);
    } else {
      List<IMarker> items = matched.subList(0, Math.min(max, matched.size()));
      r.addAll(items);
      shown.addAll(items);
    }
    shownMarkers = shown;
    shownCount = shown.size();
    return r;
  }

  /** A group row of the markers, with the items of the group shown (none for an empty group). */
  private void addGroup(List<Object> rows, List<IMarker> shown, String name, int severity, List<IMarker> items,
      int max) {
    if (items.isEmpty())
      return;
    int n = Math.min(max, items.size());
    GroupRow group = new GroupRow(name, items.size(), n, limitEnabled, limit, items.subList(0, n), severity);
    rows.add(group);
    shown.addAll(group.items);
  }

  /** The severity of a marker as the groups and the images of the problems take it (infos for the others). */
  static int severityGroupOf(IMarker m) {
    int severity = attribute(m, IMarker.SEVERITY, IMarker.SEVERITY_INFO);
    return severity == IMarker.SEVERITY_ERROR || severity == IMarker.SEVERITY_WARNING ? severity
        : IMarker.SEVERITY_INFO;
  }

  /** The name of the group of a severity: Errors, Warnings or Infos ({@code markerGrouping.*}). */
  private static String severityGroupName(int severity) {
    switch (severity) {
      case IMarker.SEVERITY_ERROR:
        return "Errors";
      case IMarker.SEVERITY_WARNING:
        return "Warnings";
      default:
        return "Infos";
    }
  }

  /** The image of a severity ({@code error_tsk}, {@code warn_tsk} or {@code info_tsk}). */
  private static String severityIcon(int severity) {
    switch (severity) {
      case IMarker.SEVERITY_ERROR:
        return ERROR_ICON;
      case IMarker.SEVERITY_WARNING:
        return WARNING_ICON;
      default:
        return INFO_ICON;
    }
  }

  /** The full paths of the selected resources (the scope of the configurations). */
  private List<String> selectedPaths() {
    return selectedPaths;
  }

  /** Shows the markers of the workspace (the selection is kept for the markers still there). */
  public void refresh() {
    if (disposed || tree == null || tree.isDisposed())
      return;
    ISelection before = viewer.getSelection();
    List<IMarker> all = markers();
    for (MarkerFilterConfiguration c : configurations)
      c.setScopePaths(selectedPaths());
    List<Object> shown = rows(all);
    viewer.setInput(shown);
    for (Object o : shown)
      if (o instanceof GroupRow && categoriesToExpand.contains(((GroupRow) o).name))
        viewer.setExpandedState(o, true);
    List<IMarker> kept = new ArrayList<>();
    if (before instanceof IStructuredSelection)
      for (Object o : ((IStructuredSelection) before).toList())
        if (o instanceof IMarker && all.contains(o))
          kept.add((IMarker) o);
    viewer.setSelection(new StructuredSelection(kept));
    setContentDescription(statusMessage(all.size()));
    updateActions();
  }

  /**
   * {@code ExtendedMarkersView.getStatusMessage}: the number of the items, or the items matched of the total when the
   * configurations, the text or the limit hide some.
   */
  private String statusMessage(int total) {
    // ExtendedMarkersView.getStatusMessage: the errors and warnings of the problems, then the matched items
    if (hasSeverity() && (summaryCounts[0] > 0 || summaryCounts[1] > 0)) {
      String message = new MessageFormat(SUMMARY_BREAKDOWN).format(
          new Object[] { summaryCounts[0], summaryCounts[1], summaryCounts[2] });
      if (shownCount >= total)
        return message;
      return MessageFormat.format(PROBLEM_FILTER_MATCHED, message, shownCount, total);
    }
    if (shownCount >= total)
      return total == 1 ? "1 item" : total + " items";
    return "Filter matched " + shownCount + " of " + total + " items";
  }

  private void refreshLater() {
    if (disposed)
      return;
    Display d = Display.getDefault();
    if (d != null)
      d.asyncExec(refresher);
  }

  /**
   * {@code ViewerPageSelectionListener}: the resources of the selection of another part are the scope of the
   * configurations; a change of them refreshes the view.
   */
  private void workbenchSelectionChanged(IWorkbenchPart part, ISelection selection) {
    if (part == this || disposed)
      return;
    List<String> paths = new ArrayList<>();
    if (part instanceof IEditorPart) {
      IResource r = resourceOf(((IEditorPart) part).getEditorInput());
      if (r != null)
        paths.add(r.getFullPath().toString());
    } else if (selection instanceof IStructuredSelection) {
      for (Object o : ((IStructuredSelection) selection).toList()) {
        IResource r = resourceOf(o);
        if (r != null && !paths.contains(r.getFullPath().toString()))
          paths.add(r.getFullPath().toString());
      }
    }
    if (paths.equals(selectedPaths))
      return;
    selectedPaths = paths;
    refreshLater();
  }

  private static IResource resourceOf(Object o) {
    if (o instanceof IResource)
      return (IResource) o;
    if (o instanceof FileEditorInput)
      return ((FileEditorInput) o).getFile();
    if (o instanceof IAdaptable) {
      Object r = ((IAdaptable) o).getAdapter(IResource.class);
      return r instanceof IResource ? (IResource) r : null;
    }
    return null;
  }

  /** The text of a field of a marker (the column of the view). */
  String text(IMarker m, String field) {
    switch (field) {
      case MarkerFields.PRIORITY:
        switch (attribute(m, IMarker.PRIORITY, IMarker.PRIORITY_NORMAL)) {
          case IMarker.PRIORITY_HIGH:
            return "High";
          case IMarker.PRIORITY_LOW:
            return "Low";
          default:
            return "Normal";
        }
      case MarkerFields.DESCRIPTION:
        return attributeText(m, IMarker.MESSAGE, "Unknown");
      case MarkerFields.RESOURCE:
        return m.getResource().getName();
      case MarkerFields.PATH:
        return containerPath(m.getResource());
      case MarkerFields.LOCATION: {
        String location = attributeText(m, IMarker.LOCATION, "");
        if (!location.isEmpty())
          return location;
        int line = attribute(m, IMarker.LINE_NUMBER, -1);
        return line < 0 ? "Unknown" : "line " + line;
      }
      case MarkerFields.CREATION_TIME: {
        long time = creationTime(m);
        return time <= 0 ? "" : DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(new Date(time));
      }
      case MarkerFields.ID:
        return Long.toString(m.getId());
      case MarkerFields.TYPE:
        return typeLabel(m);
      default:
        return "";
    }
  }

  /** {@code MarkerPathField}: the container of the resource, as {@code /Project/folder}; empty for a project. */
  private static String containerPath(IResource r) {
    if (r.getFullPath().segmentCount() <= 1)
      return "";
    return r.getFullPath().removeLastSegments(1).toString();
  }

  static String attributeText(IMarker m, String name, String def) {
    Object v = attributeObject(m, name);
    return v == null ? def : v.toString();
  }

  static Object attributeObject(IMarker m, String name) {
    try {
      return m.getAttribute(name);
    } catch (CoreException e) {
      return null;
    }
  }

  private static long creationTime(IMarker m) {
    try {
      return m.getCreationTime();
    } catch (CoreException e) {
      return -1;
    }
  }

  static boolean attribute(IMarker m, String name, boolean def) {
    try {
      return m.getAttribute(name, def);
    } catch (RuntimeException e) {
      return def;
    }
  }

  static int attribute(IMarker m, String name, int def) {
    try {
      return m.getAttribute(name, def);
    } catch (RuntimeException e) {
      return def;
    }
  }

  // ---- sorting

  /** A click on a column header: sorts by the field, and reverses the order when it is already the sort field. */
  private void sortBy(String field) {
    if (field.equals(sortField))
      ascending = !ascending;
    else {
      sortField = field;
      ascending = true;
    }
    refresh();
  }

  private int compare(IMarker a, IMarker b) {
    int r;
    if (MarkerFields.COMPLETED.equals(sortField))
      r = Boolean.compare(attribute(a, IMarker.DONE, false), attribute(b, IMarker.DONE, false));
    else if (hasSeverity() && MarkerFields.DESCRIPTION.equals(sortField)) {
      // MarkerSeverityAndDescriptionField: the higher severities first, then the descriptions
      r = Integer.compare(severityGroupOf(b), severityGroupOf(a));
      if (r == 0)
        r = text(a, sortField).compareToIgnoreCase(text(b, sortField));
    } else if (MarkerFields.PRIORITY.equals(sortField))
      r = Integer.compare(attribute(b, IMarker.PRIORITY, IMarker.PRIORITY_NORMAL),
          attribute(a, IMarker.PRIORITY, IMarker.PRIORITY_NORMAL));
    else if (MarkerFields.CREATION_TIME.equals(sortField))
      r = Long.compare(creationTime(a), creationTime(b));
    else if (MarkerFields.ID.equals(sortField))
      r = Long.compare(a.getId(), b.getId());
    else
      r = text(a, sortField).compareToIgnoreCase(text(b, sortField));
    return ascending ? r : -r;
  }

  /**
   * A group row of the Group By type ({@code MarkerCategory}): the name of the type, the number of its items and the
   * items shown (the limit of the items per group applies to them). Its label is the one of Eclipse's
   * {@code Category_*} messages.
   */
  static final class GroupRow {
    final String name;
    final List<IMarker> items;
    final String label;
    /** The severity of the group of Group By Severity (its image: errors and warnings), -1 for the other groups. */
    final int severity;

    GroupRow(String name, int total, int shown, boolean limitEnabled, int limit, List<IMarker> items, int severity) {
      this.name = name;
      this.items = new ArrayList<>(items);
      this.severity = severity;
      if (limitEnabled && total > limit)
        label = name + " (" + shown + " of " + total + " items)";
      else if (shown == 1)
        label = name + " (1 item)";
      else
        label = name + " (" + shown + " items)";
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof GroupRow && ((GroupRow) o).name.equals(name);
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }

    @Override
    public String toString() {
      return label;
    }
  }

  /** {@code MarkerContentProvider}: the group rows of the grouping, or the markers, are the roots. */
  private static final class MarkerContentProvider implements ITreeContentProvider {
    @Override
    public Object[] getElements(Object inputElement) {
      return inputElement instanceof List ? ((List<?>) inputElement).toArray() : new Object[0];
    }

    @Override
    public Object[] getChildren(Object parentElement) {
      return parentElement instanceof GroupRow ? ((GroupRow) parentElement).items.toArray() : new Object[0];
    }

    @Override
    public Object getParent(Object element) {
      return null;
    }

    @Override
    public boolean hasChildren(Object element) {
      return element instanceof GroupRow && !((GroupRow) element).items.isEmpty();
    }
  }

  private final class MarkerLabelProvider extends LabelProvider implements ITableLabelProvider {
    @Override
    public Image getColumnImage(Object element, int columnIndex) {
      // the group rows of the severities: the image of the errors and the warnings (the highest severity of Eclipse)
      if (element instanceof GroupRow)
        return columnIndex == 0 && ((GroupRow) element).severity >= IMarker.SEVERITY_WARNING
            ? ImageDescriptor.fromResource(severityIcon(((GroupRow) element).severity)).createImage()
            : null;
      if (!(element instanceof IMarker) || columnIndex >= shownFields.size())
        return null;
      IMarker m = (IMarker) element;
      String field = shownFields.get(columnIndex);
      // VSCODE: the description of the problems has the image of its severity (MarkerSeverityAndDescriptionField)
      if (hasSeverity() && MarkerFields.DESCRIPTION.equals(field))
        return ImageDescriptor.fromResource(severityIcon(severityGroupOf(m))).createImage();
      // VSCODE: the completion and priority columns are the icons of Eclipse (MarkerCompletionField, MarkerPriorityField)
      if (MarkerFields.COMPLETED.equals(field)) {
        if (!attribute(m, IMarker.USER_EDITABLE, true))
          return null;
        return ImageDescriptor.fromResource(attribute(m, IMarker.DONE, false) ? COMPLETE_ICON : INCOMPLETE_ICON)
            .createImage();
      }
      if (MarkerFields.PRIORITY.equals(field)) {
        switch (attribute(m, IMarker.PRIORITY, IMarker.PRIORITY_NORMAL)) {
          case IMarker.PRIORITY_HIGH:
            return ImageDescriptor.fromResource(HIGH_PRIORITY_ICON).createImage();
          case IMarker.PRIORITY_LOW:
            return ImageDescriptor.fromResource(LOW_PRIORITY_ICON).createImage();
          default:
            return null;
        }
      }
      return null;
    }

    @Override
    public String getColumnText(Object element, int columnIndex) {
      if (element instanceof GroupRow)
        return columnIndex == 0 ? ((GroupRow) element).label : "";
      String field = shownFields.get(columnIndex);
      return MarkerFields.COMPLETED.equals(field) || MarkerFields.PRIORITY.equals(field) ? ""
          : text((IMarker) element, field);
    }
  }

  // ---- selection and actions

  private Object firstSelected() {
    ISelection s = viewer == null ? null : viewer.getSelection();
    if (s instanceof IStructuredSelection && !((IStructuredSelection) s).isEmpty())
      return ((IStructuredSelection) s).getFirstElement();
    return null;
  }

  List<IMarker> selectedMarkers() {
    List<IMarker> r = new ArrayList<>();
    if (viewer == null)
      return r;
    ISelection s = viewer.getSelection();
    if (s instanceof IStructuredSelection)
      for (Object o : ((IStructuredSelection) s).toList())
        if (o instanceof IMarker)
          r.add((IMarker) o);
    return r;
  }

  private void createActions() {
    goToAction = new Action("Go to Resource") {
      @Override
      public void run() {
        goTo(selectedMarkers());
      }
    };
    goToAction.setToolTipText("Go to Resource");
    copyAction = new Action("&Copy") {
      @Override
      public void run() {
        copy(selectedMarkers());
      }
    };
    copyAction.setToolTipText("Copy");
    copyDescriptionAction = new Action("&Description") {
      @Override
      public void run() {
        copyText(selectedMarkers(), false);
      }
    };
    copyQualifiedNameAction = new Action("Resource &Qualified Name") {
      @Override
      public void run() {
        copyText(selectedMarkers(), true);
      }
    };
    deleteAction = new Action("&Delete") {
      @Override
      public void run() {
        delete(selectedMarkers());
      }
    };
    deleteAction.setToolTipText("Delete");
    selectAllAction = new Action("Select A&ll") {
      @Override
      public void run() {
        viewer.setSelection(new StructuredSelection(selectable()));
        updateActions();
      }
    };
    selectAllAction.setToolTipText("Select All");
    propertiesAction = new Action("P&roperties") {
      @Override
      public void run() {
        properties(selectedMarkers());
      }
    };
    propertiesAction.setToolTipText("Properties");
    markCompletedAction = new Action("&Mark Completed") {
      @Override
      public void run() {
        markCompleted(selectedMarkers());
      }
    };
    deleteCompletedAction = new Action("Delete Completed &Tasks") {
      @Override
      public void run() {
        deleteCompletedTasks();
      }
    };
    addTaskAction = new Action("Add &Task...") {
      @Override
      public void run() {
        // VSCODE: the Add Task of the view adds the task to the workspace (the workspace root), as Eclipse does
        new DialogTaskProperties(getShell(), "Add Task").open();
      }
    };
    addTaskAction.setToolTipText("Add Task");
    filterAction = new Action("&Filters...") {
      @Override
      public void run() {
        configureFilters();
      }
    };
    filterAction.setToolTipText("Configure the filters to apply to the markers view");
    openAnotherAction = new Action("New " + getDefaultTitle() + " View") {
      @Override
      public void run() {
        openAnother();
      }
    };
    quickFixAction = new Action("&Quick Fix") {
      @Override
      public void run() {
        quickFix(selectedMarkers());
      }
    };
    quickFixAction.setToolTipText("Quick Fix");
  }

  /** The markers of the view that can be selected (the items, not the group rows). */
  private List<IMarker> selectable() {
    return new ArrayList<>(shownMarkers);
  }

  private void initToolBar(ToolBarManager toolbar) {
    toolbar.add(filterAction);
  }

  private Shell getShell() {
    return getSite().getShell();
  }

  private void fillContextMenu(IMenuManager menu) {
    menu.add(goToAction);
    menu.add(new Separator());
    menu.add(copyAction);
    MenuManager copyDetails = new MenuManager("Cop&y Details");
    copyDetails.add(copyDescriptionAction);
    copyDetails.add(copyQualifiedNameAction);
    menu.add(copyDetails);
    menu.add(deleteAction);
    menu.add(selectAllAction);
    menu.add(new Separator());
    menu.add(showInMenu());
    if (hasQuickFix())
      menu.add(quickFixAction);
    if (isTasks()) {
      menu.add(addTaskAction);
      menu.add(deleteCompletedAction);
      menu.add(markCompletedAction);
    }
    menu.add(new Separator());
    menu.add(propertiesAction);
    updateActions();
  }

  /** The View menu: the Show menu of the configurations, Group By, Sort By, Open Another and the Filters actions. */
  private void fillViewMenu(IMenuManager menu) {
    if (!configurations.isEmpty()) {
      MenuManager show = new MenuManager("&Show");
      for (MarkerFilterConfiguration c : configurations)
        show.add(checkAction(c.getName(), c.isEnabled(), () -> toggleConfiguration(c)));
      show.add(new Separator());
      show.add(checkAction("Sho&w All", noConfigurationEnabled(), this::showAll));
      menu.add(show);
    }
    if (hasSeverity() || groupsByType()) {
      MenuManager group = new MenuManager("&Group By");
      if (hasSeverity())
        group.add(radioAction("&Severity", GROUP_SEVERITY.equals(groupBy), () -> setGroupBy(GROUP_SEVERITY)));
      if (groupsByType())
        group.add(radioAction("&Type", GROUP_TYPE.equals(groupBy), () -> setGroupBy(GROUP_TYPE)));
      group.add(radioAction("&None", GROUP_NONE.equals(groupBy), () -> setGroupBy(GROUP_NONE)));
      menu.add(group);
    }
    MenuManager sort = new MenuManager("&Sort By");
    for (String f : shownFields)
      sort.add(radioAction(MarkerFields.header(f), f.equals(sortField), () -> setSortField(f)));
    sort.add(new Separator());
    sort.add(checkAction("&Ascending", ascending, () -> setAscending(!ascending)));
    menu.add(sort);
    menu.add(openAnotherAction);
    menu.add(new Separator());
    menu.add(filterAction);
    menu.add(columnsAction());
    menu.add(new Separator());
    menu.add(checkAction("&Show Text Filter", showFilterText, this::toggleFilterText));
  }

  private Action columnsAction() {
    Action columns = new Action("&Configure Columns...") {
      @Override
      public void run() {
        configureColumns();
      }
    };
    return columns;
  }

  private boolean noConfigurationEnabled() {
    for (MarkerFilterConfiguration c : configurations)
      if (c.isEnabled())
        return false;
    return true;
  }

  private Action checkAction(String text, boolean checked, Runnable run) {
    Action a = new Action(text, IAction.AS_CHECK_BOX) {
      @Override
      public void run() {
        run.run();
      }
    };
    a.setChecked(checked);
    return a;
  }

  private Action radioAction(String text, boolean checked, Runnable run) {
    Action a = new Action(text, IAction.AS_RADIO_BUTTON) {
      @Override
      public void run() {
        run.run();
      }
    };
    a.setChecked(checked);
    return a;
  }

  private void toggleConfiguration(MarkerFilterConfiguration c) {
    c.setEnabled(!c.isEnabled());
    refresh();
  }

  private void showAll() {
    for (MarkerFilterConfiguration c : configurations)
      c.setEnabled(false);
    refresh();
  }

  private void setGroupBy(String group) {
    groupBy = group;
    refresh();
  }

  private void setSortField(String field) {
    sortField = field;
    refresh();
  }

  private void setAscending(boolean value) {
    ascending = value;
    refresh();
  }

  /**
   * Show Text Filter ({@code ExtendedMarkersView.showSearchFilterText}): the text box is shown and focused, or hidden
   * with its text cleared and the view focused.
   */
  private void toggleFilterText() {
    showFilterText = !showFilterText;
    filterText.setVisible(showFilterText);
    if (showFilterText) {
      filterText.setFocus();
    } else {
      filterText.setText("");
      setFocus();
    }
  }

  /** The Show In menu of the selected marker: the targets of the Show In menu, for the resource of the marker. */
  private MenuManager showInMenu() {
    MenuManager showIn = new MenuManager("Show In");
    List<IMarker> sel = selectedMarkers();
    if (sel.isEmpty()) {
      Action none = new Action("<No Applicable Views>") {
        @Override
        public void run() {
        }
      };
      none.setEnabled(false);
      showIn.add(none);
      return showIn;
    }
    IResource resource = sel.get(0).getResource();
    // VSCODE: the targets of ShowInMenu (ui/workbench/actions), for the resource of the marker
    String[][] targets = { { "Project Explorer", "explorer" }, { "Properties", "properties" },
        { "System Explorer", "system" }, { "Terminal", "terminal" } };
    for (String[] t : targets) {
      Action a = new Action(t[0]) {
        @Override
        public void run() {
          IResource r = "terminal".equals(t[1]) && resource instanceof IFile ? resource.getParent() : resource;
          JsonObject p = new JsonObject();
          p.addProperty("target", t[1]);
          p.addProperty("path", r.getLocation().toOSString());
          if (ServerContext.server() != null)
            ServerContext.client().notify("client/showIn", p);
        }
      };
      showIn.add(new ActionContributionItem(a));
    }
    return showIn;
  }

  /** Configure Columns...: the shown columns and their widths. */
  void configureColumns() {
    MarkersViewColumnsDialog dialog = new MarkersViewColumnsDialog(getShell(), markerType, shownFields, widths);
    if (dialog.open() == IDialogConstants.OK_ID) {
      shownFields.clear();
      shownFields.addAll(dialog.getShown());
      widths.putAll(dialog.getWidths());
      createColumns();
      refresh();
    }
  }

  /** Filters...: the configurations of the view (Show menu) and the items per group. */
  void configureFilters() {
    List<String> paths = new ArrayList<>(selectedPaths());
    FiltersConfigurationDialog dialog = new FiltersConfigurationDialog(getShell(), markerType, configurations,
        matchAll, limitEnabled, limit, paths);
    if (dialog.open() == IDialogConstants.OK_ID) {
      configurations.clear();
      configurations.addAll(dialog.getConfigurations());
      matchAll = dialog.isMatchAll();
      limitEnabled = dialog.isLimitsEnabled();
      limit = dialog.getLimit();
      refresh();
    }
  }

  /**
   * Open Another ({@code OpenMarkersViewHandler}): asks the name of the new view, and opens a new instance of the view
   * in a panel of the workbench, with the menus of its own ids.
   */
  // VSCODE: the new view is a panel of the Requality views (VS Code declares the views of the extension)
  void openAnother() {
    int count = newSecondaryId();
    String defaultName = getDefaultTitle() + " " + count;
    InputDialog dialog = new InputDialog(getShell(), "New " + getDefaultTitle() + " View",
        "&Enter a name for the new view:", defaultName, newText -> newText.isEmpty()
            ? "Name must not be empty" : null);
    if (dialog.open() != IDialogConstants.OK_ID) {
      INSTANCES.remove(count);
      return;
    }
    MarkerView part = newInstance();
    part.instance = count;
    PanelSite site = new PanelSite(getViewId(), part);
    try {
      part.init(site);
    } catch (PartInitException e) {
      INSTANCES.remove(count);
      MessageDialog.openError(getShell(), "New " + getDefaultTitle() + " View", e.getMessage());
      return;
    }
    Shell shell = new Shell(SWT.NONE);
    site.panel = shell;
    // the title bar of the view in its panel: the toolbar of the view and the view menu (the triangle of Eclipse)
    GridLayout gl = new GridLayout(1, false);
    gl.marginWidth = gl.marginHeight = 0;
    shell.setLayout(gl);
    Composite bar = new Composite(shell, SWT.NONE);
    GridLayout bl = new GridLayout(2, false);
    bl.marginWidth = bl.marginHeight = 0;
    bar.setLayout(bl);
    bar.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
    Composite content = new Composite(shell, SWT.NONE);
    content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    part.createPartControl(content);
    part.getViewSite().getActionBars().getToolBarManager().createControl(bar);
    Button viewMenu = new Button(bar, SWT.ARROW | SWT.DOWN);
    viewMenu.setToolTipText("View Menu");
    viewMenu.addListener(SWT.Selection, e -> {
      MenuManager mm = new MenuManager();
      part.fillViewMenu(mm);
      mm.createContextMenu(viewMenu).setVisible(true);
    });
    part.customTitle = dialog.getValue();
    part.setPartName(dialog.getValue());
    shell.addListener(SWT.Dispose, e -> {
      INSTANCES.remove(count);
      part.dispose();
    });
    shell.open();
  }

  /**
   * The site of a view opened in a panel by Open Another: its shell is the panel ({@code PartSite} sets the shell of the
   * views of the workbench only).
   */
  private static final class PanelSite extends PartSite {
    Shell panel;

    PanelSite(String id, IWorkbenchPart part) {
      super(id, part);
    }

    @Override
    public Shell getShell() {
      return panel;
    }
  }

  /** The number of a new view of Open Another (the lowest one not used by an open view). */
  private static synchronized int newSecondaryId() {
    int n = 1;
    while (INSTANCES.contains(n))
      n++;
    INSTANCES.add(n);
    return n;
  }

  /** A new instance of the view of this class ({@code showView} with a secondary id). */
  private MarkerView newInstance() {
    try {
      return (MarkerView) getClass().getDeclaredConstructor().newInstance();
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("The view " + getViewId() + " cannot be opened again", e);
    }
  }

  /** The actions follow the selection of the view. */
  void updateActions() {
    if (disposed || viewer == null || goToAction == null)
      return;
    List<IMarker> sel = selectedMarkers();
    boolean any = !sel.isEmpty();
    goToAction.setEnabled(any);
    copyAction.setEnabled(any);
    copyDescriptionAction.setEnabled(any);
    copyQualifiedNameAction.setEnabled(any);
    deleteAction.setEnabled(any && sel.stream().allMatch(m -> editable(m)));
    quickFixAction.setEnabled(hasQuickFix() && sel.stream().anyMatch(m -> resolutionsOf(m).length > 0));
    selectAllAction.setEnabled(!selectable().isEmpty());
    propertiesAction.setEnabled(any);
    markCompletedAction.setEnabled(sel.stream().anyMatch(m -> editable(m) && !attribute(m, IMarker.DONE, false)));
    deleteCompletedAction.setEnabled(!completedTasks().isEmpty());
    addTaskAction.setEnabled(true);
  }

  private static boolean editable(IMarker m) {
    return attribute(m, IMarker.USER_EDITABLE, true);
  }

  // ---- the actions

  /**
   * Go To: opens the file of the marker in the editor at its line; a folder or a project is shown in the explorer. VS
   * Code opens the file: the {@code client/openFile} notification.
   */
  void goTo(List<IMarker> markers) {
    if (markers.isEmpty())
      return;
    IMarker m = markers.get(0);
    IResource r = m.getResource();
    if (!(r instanceof IFile))
      return; // ExtendedMarkersView.openMarkerInEditor opens the markers of files only
    JsonObject p = new JsonObject();
    p.addProperty("path", r.getLocation().toOSString());
    p.addProperty("editor", "default");
    int line = attribute(m, IMarker.LINE_NUMBER, -1);
    if (line >= 0)
      p.addProperty("line", line);
    ServerContext.client().notify("client/openFile", p);
  }

  /** Copy: the descriptions of the markers on the clipboard, one per line. */
  void copy(List<IMarker> markers) {
    copyText(markers, false);
  }

  /** Copy Details: the descriptions or the resource qualified names of the markers, one per line, on the clipboard. */
  void copyText(List<IMarker> markers, boolean qualifiedName) {
    if (markers.isEmpty())
      return;
    StringBuilder sb = new StringBuilder();
    for (IMarker m : markers) {
      if (sb.length() > 0)
        sb.append(System.lineSeparator());
      sb.append(qualifiedName ? m.getResource().getFullPath().toString() : text(m, MarkerFields.DESCRIPTION));
    }
    Clipboard clipboard = new Clipboard(null);
    try {
      clipboard.setContents(new Object[] { sb.toString() }, new Transfer[] { TextTransfer.getInstance() });
    } finally {
      clipboard.dispose();
    }
  }

  void delete(List<IMarker> markers) {
    if (markers.isEmpty())
      return;
    IMarker[] array = markers.toArray(new IMarker[0]);
    String name = array.length == 1 ? "Delete " + markerName() : "Delete " + markerName() + "s";
    try {
      WorkspaceUndoUtil.execute(new DeleteMarkersOperation(array, name), null, getShell());
    } catch (CoreException e) {
      ErrorDialog.openError(getShell(), name, null, e.getStatus());
    }
  }

  /**
   * {@code QuickFixHandler}: the fixes of the first selected marker that apply to all the selected markers, one of
   * them (asked when there are several) applied to the markers. Without a fix, the message of Eclipse.
   */
  void quickFix(List<IMarker> markers) {
    if (markers.isEmpty())
      return;
    IMarker first = markers.get(0);
    List<IDE.IMarkerResolution> fixes = new ArrayList<>(Arrays.asList(resolutionsOf(first)));
    for (IMarker m : markers.subList(1, markers.size()))
      fixes.removeIf(f -> !Arrays.asList(resolutionsOf(m)).contains(f));
    String description = attributeText(first, IMarker.MESSAGE, "Unknown");
    if (fixes.isEmpty()) {
      String message = markers.size() == 1 ? "No fixes available for '" + description + "'."
          : "The selected problems do not have a common applicable quick fix.";
      MessageDialog.openInformation(getShell(), "Quick Fix", message);
      return;
    }
    IDE.IMarkerResolution fix = fixes.get(0);
    if (fixes.size() > 1) {
      ElementListSelectionDialog dialog = new ElementListSelectionDialog(getShell(), new LabelProvider() {
        @Override
        public String getText(Object element) {
          return ((IDE.IMarkerResolution) element).getLabel();
        }
      });
      dialog.setTitle("Quick Fix");
      dialog.setMessage("Select the fix for '" + description + "'.");
      dialog.setElements(fixes.toArray());
      if (dialog.open() != IDialogConstants.OK_ID)
        return;
      fix = (IDE.IMarkerResolution) dialog.getFirstResult();
    }
    for (IMarker m : markers)
      fix.run(m);
  }

  /** The fixes of a marker: the resolutions of its marker type ({@link IDE#getMarkerHelpRegistry()}). */
  static IDE.IMarkerResolution[] resolutionsOf(IMarker m) {
    return IDE.getMarkerHelpRegistry().getResolutions(m);
  }

  void properties(List<IMarker> markers) {
    if (markers.isEmpty())
      return;
    DialogMarkerProperties dialog = isTasks() ? new DialogTaskProperties(getShell())
        : new DialogMarkerProperties(getShell());
    dialog.setMarker(markers.get(0));
    dialog.open();
  }

  void markCompleted(List<IMarker> markers) {
    List<IMarker> todo = new ArrayList<>();
    for (IMarker m : markers)
      if (editable(m) && !attribute(m, IMarker.DONE, false))
        todo.add(m);
    if (todo.isEmpty())
      return;
    Map<String, Object> attrs = new HashMap<>();
    attrs.put(IMarker.DONE, Boolean.TRUE);
    try {
      WorkspaceUndoUtil.execute(new UpdateMarkersOperation(todo.toArray(new IMarker[0]), attrs, "Mark Completed",
          true), null, getShell());
    } catch (CoreException e) {
      ErrorDialog.openError(getShell(), "Mark Completed", null, e.getStatus());
    }
  }

  private void setCompleted(IMarker m, boolean completed) {
    if (!editable(m))
      return;
    Map<String, Object> attrs = new HashMap<>();
    attrs.put(IMarker.DONE, completed);
    try {
      WorkspaceUndoUtil.execute(new UpdateMarkersOperation(m, attrs, "Modify Task", true), null, getShell());
    } catch (CoreException e) {
      ErrorDialog.openError(getShell(), "Modify Task", null, e.getStatus());
    }
  }

  private List<IMarker> completedTasks() {
    List<IMarker> r = new ArrayList<>();
    if (!isTasks())
      return r;
    for (IMarker m : markers())
      if (editable(m) && attribute(m, IMarker.DONE, false))
        r.add(m);
    return r;
  }

  /** Delete Completed Tasks: the user-editable completed tasks after a confirmation. */
  void deleteCompletedTasks() {
    List<IMarker> done = completedTasks();
    if (done.isEmpty()) {
      MessageDialog.openInformation(getShell(), "Delete Completed Tasks",
          "No user-editable completed tasks to delete.");
      return;
    }
    String message = done.size() == 1 ? "Do you want to permanently delete the completed task?"
        : "Do you want to permanently delete the " + done.size() + " completed tasks?";
    if (!MessageDialog.openQuestion(getShell(), "Delete Completed Tasks", message))
      return;
    try {
      WorkspaceUndoUtil.execute(new DeleteMarkersOperation(done.toArray(new IMarker[0]), "Delete Tasks"), null,
          getShell());
    } catch (CoreException e) {
      ErrorDialog.openError(getShell(), "Delete Completed Tasks", null, e.getStatus());
    }
  }
}
