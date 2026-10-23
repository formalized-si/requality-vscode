package com.unitesk.requality.vscode.ui.team.history;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.compare.CompareConfiguration;
import com.unitesk.requality.vscode.ui.compare.ITypedElement;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.DiffNode;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.Differencer;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.ICompareInput;
import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.action.IMenuManager;
import com.unitesk.requality.vscode.ui.jface.action.MenuManager;
import com.unitesk.requality.vscode.ui.team.actions.OpenWithMenu;
import com.unitesk.requality.vscode.ui.team.preferences.IPreferenceIds;
import com.unitesk.requality.vscode.ui.team.preferences.TeamUIPlugin;
import com.unitesk.requality.vscode.ui.jface.preference.IPreferenceStore;
import com.unitesk.requality.vscode.ui.jface.action.Separator;
import com.unitesk.requality.vscode.ui.jface.action.ToolBarManager;
import com.unitesk.requality.vscode.ui.jface.viewers.DoubleClickEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.IDoubleClickListener;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IResourceChangeEvent;
import com.unitesk.requality.vscode.ui.resources.IResourceChangeListener;
import com.unitesk.requality.vscode.ui.resources.IResourceDelta;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.runtime.NullProgressMonitor;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Menu;
import com.unitesk.requality.vscode.ui.team.FileRevisionTypedElement;
import com.unitesk.requality.vscode.ui.team.LocalResourceTypedElement;
import com.unitesk.requality.vscode.ui.team.core.history.IFileHistory;
import com.unitesk.requality.vscode.ui.team.core.history.IFileRevision;
import com.unitesk.requality.vscode.ui.team.core.history.LocalFileHistory;
import com.unitesk.requality.vscode.ui.team.core.history.LocalFileRevision;
import com.unitesk.requality.vscode.ui.team.ui.history.HistoryPage;
import com.unitesk.requality.vscode.ui.team.ui.history.IHistoryCompareAdapter;
import com.unitesk.requality.vscode.ui.team.ui.history.IHistoryPageSite;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchActionConstants;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPartSite;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;

/**
 * {@code org.eclipse.team.internal.ui.history.LocalHistoryPage}: the history page of the local history of a file. The tree
 * shows the workspace file and its states, grouped by date ("Today", "Yesterday", "This Month", "Older than This Month")
 * when "Group Revisions by Date" is on. Open shows a revision read-only, Compare compares it with the file (or with
 * another revision), Get Contents replaces the file with it (the file's contents are kept in the local history). A double
 * click opens the revision, or compares it in Compare Mode.
 * VSCODE: the history is read synchronously (no progress job).
 */
public class LocalHistoryPage extends HistoryPage implements IHistoryCompareAdapter {
  public static final int ON = 1;
  public static final int OFF = 2;
  public static final int ALWAYS = 4;

  static final String NO_REVISIONS = "No revisions found";

  IFile file;
  private IFileRevision currentFileRevision;
  private LocalFileHistory localFileHistory;
  TreeViewer treeViewer;
  private boolean shutdown;
  private boolean groupingOn = true;
  private int compareMode = OFF;
  LocalHistoryTableProvider historyTableProvider;
  private Composite localComposite;
  private Action groupByDateMode;
  private Action collapseAll;
  private Action compareModeAction;
  private Action getContentsAction;
  private CompareRevisionAction compareAction;
  private OpenRevisionAction openAction;
  private OpenWithMenu openWithMenu;
  private IResourceChangeListener resourceListener;

  /** The tree content: the categories (grouped) or the revisions. */
  private static final class LocalHistoryContentProvider implements ITreeContentProvider {
    @Override
    public Object[] getElements(Object inputElement) {
      if (inputElement instanceof IFileHistory fileHistory) {
        return fileHistory.getFileRevisions();
      }
      if (inputElement instanceof IFileRevision[] revisions) {
        return revisions;
      }
      if (inputElement instanceof AbstractHistoryCategory[] categories) {
        return categories;
      }
      return new Object[0];
    }

    @Override
    public void dispose() {
    }

    @Override
    public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
    }

    @Override
    public Object[] getChildren(Object parentElement) {
      if (parentElement instanceof AbstractHistoryCategory category) {
        IFileRevision[] revisions = category.getRevisions();
        return revisions == null ? new Object[0] : revisions;
      }
      return new Object[0];
    }

    @Override
    public Object getParent(Object element) {
      return null;
    }

    @Override
    public boolean hasChildren(Object element) {
      return element instanceof AbstractHistoryCategory category && category.hasRevisions();
    }
  }

  /** The table provider of the page: the current file is the revision it is based on. */
  private class LocalFileHistoryTableProvider extends LocalHistoryTableProvider {
    @Override
    protected long getModificationDate(Object element) {
      IFileRevision entry = adaptToFileRevision(element);
      return entry != null ? entry.getTimestamp() : -1;
    }

    @Override
    protected boolean isCurrentEdition(Object element) {
      IFileRevision entry = adaptToFileRevision(element);
      return entry != null && file != null && file.getLocalTimeStamp() == entry.getTimestamp();
    }
  }

  /** Refreshes the page when the file changes in the workspace. */
  private class HistoryResourceListener implements IResourceChangeListener {
    @Override
    public void resourceChanged(IResourceChangeEvent event) {
      IResourceDelta root = event.getDelta();
      if (file == null || root == null) {
        return;
      }
      if (root.findMember(file.getFullPath()) != null) {
        com.unitesk.requality.vscode.ui.swt.widgets.Display.getDefault().asyncExec(this::refreshFromListener);
      }
    }

    private void refreshFromListener() {
      if (!shutdown) {
        refresh();
      }
    }
  }

  public LocalHistoryPage(int compareMode) {
    super();
    this.compareMode = compareMode;
  }

  public LocalHistoryPage() {
    super();
  }

  @Override
  public boolean inputSet() {
    currentFileRevision = null;
    IFile tempFile = getFile();
    this.file = tempFile;
    if (tempFile == null) {
      return false;
    }
    if (treeViewer != null) {
      treeViewer.setInput(null);
    }
    localFileHistory = new LocalFileHistory(file, !getHistoryPageSite().isModal());
    refreshHistory();
    return true;
  }

  /** The file whose history the page shows, or null. */
  protected IFile getFile() {
    return LocalHistoryPageSource.getFile(getInput());
  }

  /** Reads the history of the file and shows it in the tree. */
  private void refreshHistory() {
    if (localFileHistory == null || shutdown) {
      return;
    }
    IProgressMonitor monitor = new NullProgressMonitor();
    try {
      localFileHistory.refresh(monitor);
    } catch (CoreException ex) {
      RLogger.logError(ex);
    }
    update(localFileHistory.getFileRevisions(), monitor);
  }

  /** Creates the page's control: the tree of the revisions and the actions. */
  public final void createControl(Composite parent, boolean allowMultiSelection) {
    localComposite = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout(1, false);
    layout.marginHeight = 0;
    layout.marginWidth = 0;
    localComposite.setLayout(layout);
    localComposite.setLayoutData(new GridData(GridData.FILL_BOTH));
    treeViewer = createTree(localComposite, allowMultiSelection);
    contributeActions();
    IHistoryPageSite parentSite = getHistoryPageSite();
    if (parentSite != null && treeViewer != null) {
      parentSite.setSelectionProvider(treeViewer);
    }
    resourceListener = new HistoryResourceListener();
    ResourcesPlugin.getWorkspace().addResourceChangeListener(resourceListener, IResourceChangeEvent.POST_CHANGE);
    if (getFile() != null && treeViewer != null && getInput() != null) {
      refreshHistory();
    }
  }

  @Override
  public void createControl(Composite parent) {
    createControl(parent, true);
  }

  private void contributeActions() {
    final IPreferenceStore store = TeamUIPlugin.getPreferenceStore();
    groupByDateMode = new Action("Group Revisions by Date") {
      @Override
      public void run() {
        groupingOn = !groupingOn;
        store.setValue(IPreferenceIds.PREF_GROUPBYDATE_MODE, groupingOn);
        refreshHistory();
      }
    };
    groupingOn = store.getBoolean(IPreferenceIds.PREF_GROUPBYDATE_MODE);
    groupByDateMode.setChecked(groupingOn);
    groupByDateMode.setToolTipText("Group Revisions by Date");

    collapseAll = new Action("Collapse All") {
      @Override
      public void run() {
        treeViewer.collapseAll();
      }
    };
    collapseAll.setToolTipText("Collapse All");

    IHistoryPageSite historyPageSite = getHistoryPageSite();
    if (!historyPageSite.isModal()) {
      if ((compareMode & ALWAYS) == 0) {
        compareModeAction = new Action("Compare Mode") {
          @Override
          public void run() {
            compareMode = compareMode == ON ? OFF : ON;
            compareModeAction.setChecked(compareMode == ON);
          }
        };
        compareModeAction.setToolTipText("Compare Mode");
        compareModeAction.setChecked(compareMode == ON);
        getContentsAction = getContextMenuAction("Get Contents", monitor -> {
          if (confirmOverwrite() && currentSelection instanceof IFileRevision revision) {
            try (InputStream in = revision.getStorage(monitor).getContents()) {
              file.setContents(in, false, true, monitor);
            } catch (java.io.IOException e) {
              throw new CoreException(new Status(IStatus.ERROR, "org.eclipse.team.ui", 0, e.getMessage(), e));
            }
          }
        });
      }
      compareAction = createCompareAction();
      compareAction.setEnabled(!treeViewer.getSelection().isEmpty());
      treeViewer.getTree().addSelectionListener(new SelectionAdapter() {
        @Override
        public void widgetSelected(SelectionEvent e) {
          compareAction.setCurrentFileRevision(getCurrentFileRevision());
          compareAction.selectionChanged(treeViewer.getStructuredSelection());
        }
      });

      if (!((compareMode & (ALWAYS | ON)) == (ALWAYS | ON))) {
        openAction = new OpenRevisionAction("&Open", this);
        openAction.setEnabled(!treeViewer.getSelection().isEmpty());
        treeViewer.getTree().addSelectionListener(new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            openAction.selectionChanged(treeViewer.getStructuredSelection());
          }
        });

        // Add 'Open With...' sub-menu
        openWithMenu = new OpenWithMenu(this);
        treeViewer.getTree().addSelectionListener(new SelectionAdapter() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            openWithMenu.selectionChanged(treeViewer.getStructuredSelection());
          }
        });
      }

      treeViewer.addDoubleClickListener(new IDoubleClickListener() {
        @Override
        public void doubleClick(DoubleClickEvent event) {
          if (event.getSelection() instanceof IStructuredSelection structuredSelection
              && !structuredSelection.isEmpty()) {
            if ((compareMode & ON) > 0) {
              CompareRevisionAction compare = createCompareAction();
              compare.setCurrentFileRevision(getCurrentFileRevision());
              compare.selectionChanged(new com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection(
                  new Object[] { getCurrentFileRevision(), structuredSelection.getFirstElement() }));
              compare.run();
            } else if (openAction != null) {
              openAction.selectionChanged(structuredSelection);
              openAction.run();
            }
          }
        }
      });

      MenuManager menuMgr = new MenuManager();
      Menu menu = menuMgr.createContextMenu(treeViewer.getTree());
      menuMgr.addMenuListener(this::fillTableMenu);
      menuMgr.setRemoveAllWhenShown(true);
      treeViewer.getTree().setMenu(menu);
      IWorkbenchPartSite partSite = historyPageSite.getPart() == null ? null : historyPageSite.getPart().getSite();
      if (partSite != null) {
        partSite.registerContextMenu(menuMgr, treeViewer);
      }
    }

    ToolBarManager tbm = historyPageSite.getToolBarManager();
    if (tbm != null) {
      tbm.add(new Separator(getFileNameQualifier() + "grouping"));
      tbm.add(groupByDateMode);
      tbm.add(new Separator(getFileNameQualifier() + "collapse"));
      tbm.add(collapseAll);
      if (compareModeAction != null) {
        tbm.add(compareModeAction);
      }
      tbm.update(false);
    }
  }

  protected CompareRevisionAction createCompareAction() {
    return new CompareRevisionAction(this);
  }

  private String getFileNameQualifier() {
    return file != null ? file.getFullPath().toString() : "";
  }

  /** The actions of the context menu of the revisions. */
  protected void fillTableMenu(IMenuManager manager) {
    IHistoryPageSite parentSite = getHistoryPageSite();
    manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
    if (file != null && !parentSite.isModal()) {
      if (openAction != null) {
        manager.add(openAction);
      }
      if (openWithMenu != null) {
        MenuManager openWithSubmenu = new MenuManager("Open &With");
        openWithSubmenu.add(openWithMenu);
        manager.add(openWithSubmenu);
      }
      if (compareAction != null) {
        manager.add(compareAction);
      }
      if (getContentsAction != null && showGetContentsAction(treeViewer.getStructuredSelection())) {
        manager.add(new Separator("getContents"));
        manager.add(getContentsAction);
      }
    }
  }

  /** Get Contents is shown for a single selected revision, not for the current file (which would truncate it). */
  private boolean showGetContentsAction(IStructuredSelection sel) {
    if (sel.size() == 1) {
      Object selectedElem = sel.getFirstElement();
      return !(selectedElem instanceof LocalFileRevision local && local.isCurrentState());
    }
    return false;
  }

  protected final TreeViewer createTree(Composite parent, boolean allowMultiSelection) {
    historyTableProvider = new LocalFileHistoryTableProvider();
    TreeViewer viewer = historyTableProvider.createTree(parent, allowMultiSelection);
    viewer.setContentProvider(new LocalHistoryContentProvider());
    return viewer;
  }

  @Override
  public Control getControl() {
    return localComposite;
  }

  @Override
  public void setFocus() {
    if (localComposite != null) {
      localComposite.setFocus();
    }
  }

  @Override
  public String getDescription() {
    return getFile() != null ? getFile().getFullPath().toString() : null;
  }

  @Override
  public String getName() {
    return getFile() != null ? getFile().getName() : "";
  }

  @Override
  public boolean isValidInput(Object object) {
    return object instanceof IFile;
  }

  @Override
  public void refresh() {
    refreshHistory();
  }

  @Override
  public void dispose() {
    shutdown = true;
    if (resourceListener != null) {
      ResourcesPlugin.getWorkspace().removeResourceChangeListener(resourceListener);
      resourceListener = null;
    }
  }

  /** The revision of the workspace file (the current state of the file). */
  public IFileRevision getCurrentFileRevision() {
    if (currentFileRevision != null) {
      return currentFileRevision;
    }
    if (file != null) {
      currentFileRevision = new LocalFileRevision(file);
    }
    return currentFileRevision;
  }

  /** The revision selected in the tree for the Get Contents action (set when it runs). */
  private Object currentSelection;

  /** Runs an action on the selected revision of the tree (not on a category). */
  private Action getContextMenuAction(String title, LocalAction action) {
    return new Action(title) {
      @Override
      public void run() {
        if (file == null || treeViewer.getSelection() == null || !(treeViewer.getSelection() instanceof IStructuredSelection ss)) {
          return;
        }
        Object o = ss.getFirstElement();
        if (o instanceof AbstractHistoryCategory) {
          return;
        }
        currentSelection = o;
        try {
          action.run(new NullProgressMonitor());
        } catch (CoreException e) {
          RLogger.logError(e);
          MessageDialog.openError(getHistoryPageSite().getShell(), title, e.getMessage());
        }
      }

      @Override
      public boolean isEnabled() {
        return treeViewer.getSelection() instanceof IStructuredSelection ss && ss.size() == 1;
      }
    };
  }

  /** An action of a selected revision: the work of Get Contents. */
  @FunctionalInterface
  interface LocalAction {
    void run(IProgressMonitor monitor) throws CoreException;
  }

  /** Asks whether the current revision of the file may be overwritten. */
  private boolean confirmOverwrite() {
    if (file != null && file.exists()) {
      return MessageDialog.openConfirm(getHistoryPageSite().getShell(), "Overwrite Current Revision?",
          "Do you wish to overwrite the current revision with the selected revision?");
    }
    return true;
  }

  public void setClickAction(boolean compare) {
    compareMode = compare ? ON : OFF;
    if (compareModeAction != null) {
      compareModeAction.setChecked(compareMode == ON);
    }
  }

  @Override
  public ICompareInput getCompareInput(Object object) {
    if (object instanceof IFileRevision selectedFileRevision) {
      ITypedElement fileElement = new LocalResourceTypedElement(file);
      FileRevisionTypedElement right = new FileRevisionTypedElement(selectedFileRevision, null);
      return new DiffNode(null, Differencer.CHANGE, null, fileElement, right);
    }
    return null;
  }

  @Override
  public void prepareInput(ICompareInput input, CompareConfiguration configuration, IProgressMonitor monitor) {
    Object right = input.getRight();
    if (right != null) {
      String label = getLabel(right);
      if (label != null) {
        configuration.setRightLabel(label);
      }
    }
    Object left = input.getLeft();
    if (left != null) {
      String label = getLabel(left);
      if (label != null) {
        configuration.setLeftLabel(label);
      }
    }
  }

  /** The label of a side of a comparison: the date of the revision, or "Workspace File" for the file. */
  protected String getLabel(Object object) {
    if (object instanceof IFileRevision revision) {
      return revisionLabel(revision);
    }
    if (object instanceof FileRevisionTypedElement e && e.getRevision() != null) {
      return revisionLabel(e.getRevision());
    }
    if (object instanceof LocalResourceTypedElement) {
      return "Workspace File";
    }
    return null;
  }

  /** The label of a revision: "Local History (date)" for a state, "Workspace File" for the file. */
  public static String revisionLabel(IFileRevision revision) {
    if (revision instanceof LocalFileRevision local && local.isCurrentState()) {
      return "Workspace File";
    }
    long timestamp = revision.getTimestamp();
    if (timestamp > 0) {
      return "Local History (" + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,
          java.text.DateFormat.MEDIUM).format(new Date(timestamp)) + ")";
    }
    return revision.getName();
  }

  /** Shows the revisions in the tree (called from the page's refresh); categories are built when grouping is on. */
  protected void update(final IFileRevision[] revisions, IProgressMonitor monitor) {
    final AbstractHistoryCategory[] categories = groupingOn ? sortRevisions(revisions) : null;
    com.unitesk.requality.vscode.ui.swt.widgets.Display.getDefault().asyncExec(() -> {
      if (treeViewer == null || treeViewer.getControl() == null || treeViewer.getControl().isDisposed()) {
        return;
      }
      if (categories != null) {
        Object[] elementsToExpand = mapExpandedElements(categories, treeViewer.getExpandedElements());
        treeViewer.setInput(categories);
        if (elementsToExpand.length > 0) {
          treeViewer.setExpandedElements(elementsToExpand);
        } else {
          treeViewer.expandAll();
        }
      } else if (revisions.length > 0) {
        treeViewer.setInput(revisions);
      } else {
        treeViewer.setInput(new AbstractHistoryCategory[] { getErrorMessage() });
      }
    });
  }

  /** The categories that were expanded (the same names are expanded again after a refresh). */
  private Object[] mapExpandedElements(AbstractHistoryCategory[] categories, Object[] expandedElements) {
    Set<String> names = new HashSet<>();
    for (Object expandedElement : expandedElements) {
      if (expandedElement instanceof AbstractHistoryCategory c)
        names.add(c.getName());
    }
    ArrayList<Object> expandable = new ArrayList<>();
    for (AbstractHistoryCategory category : categories) {
      if (names.contains(category.getName())) {
        expandable.add(category);
      }
    }
    return expandable.toArray();
  }

  /** Groups the revisions by date: today, yesterday, this month and older. */
  private AbstractHistoryCategory[] sortRevisions(IFileRevision[] revisions) {
    DateHistoryCategory[] tempCategories = new DateHistoryCategory[4];
    Calendar currentCal = Calendar.getInstance();
    tempCategories[0] = new DateHistoryCategory("Today", currentCal, null);
    Calendar yesterdayCal = Calendar.getInstance();
    yesterdayCal.roll(Calendar.DAY_OF_YEAR, -1);
    tempCategories[1] = new DateHistoryCategory("Yesterday", yesterdayCal, null);
    Calendar monthCal = Calendar.getInstance();
    monthCal.set(Calendar.DAY_OF_MONTH, 1);
    tempCategories[2] = new DateHistoryCategory("This Month", monthCal, yesterdayCal);
    tempCategories[3] = new DateHistoryCategory("Older than This Month", null, monthCal);
    ArrayList<AbstractHistoryCategory> finalCategories = new ArrayList<>();
    for (DateHistoryCategory tempCategory : tempCategories) {
      tempCategory.collectFileRevisions(revisions, false);
      if (tempCategory.hasRevisions()) {
        finalCategories.add(tempCategory);
      }
    }
    if (finalCategories.isEmpty()) {
      finalCategories.add(getErrorMessage());
    }
    return finalCategories.toArray(new AbstractHistoryCategory[0]);
  }

  private MessageHistoryCategory getErrorMessage() {
    return new MessageHistoryCategory(NO_REVISIONS);
  }
}
