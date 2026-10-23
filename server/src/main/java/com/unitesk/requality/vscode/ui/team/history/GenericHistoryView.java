package com.unitesk.requality.vscode.ui.team.history;

import com.unitesk.requality.vscode.ui.swt.dnd.DND;
import com.unitesk.requality.vscode.ui.swt.dnd.DropTarget;
import com.unitesk.requality.vscode.ui.swt.dnd.Transfer;
import com.unitesk.requality.vscode.ui.workbench.ResourceTransfer;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.action.IAction;
import com.unitesk.requality.vscode.ui.jface.action.IMenuCreator;
import com.unitesk.requality.vscode.ui.jface.action.MenuManager;
import com.unitesk.requality.vscode.ui.jface.action.Separator;
import com.unitesk.requality.vscode.ui.jface.action.ToolBarManager;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.preference.IPropertyChangeListener;
import com.unitesk.requality.vscode.ui.jface.preference.PropertyChangeEvent;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.Adapters;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Menu;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.team.RepositoryProvider;
import com.unitesk.requality.vscode.ui.team.preferences.TeamUIPlugin;
import com.unitesk.requality.vscode.ui.team.ui.history.HistoryPage;
import com.unitesk.requality.vscode.ui.team.ui.history.IHistoryPage;
import com.unitesk.requality.vscode.ui.team.ui.history.IHistoryPageSite;
import com.unitesk.requality.vscode.ui.team.ui.history.IHistoryPageSource;
import com.unitesk.requality.vscode.ui.team.ui.history.IHistoryView;
import com.unitesk.requality.vscode.ui.workbench.IPageSite;
import com.unitesk.requality.vscode.ui.workbench.IViewPart;
import com.unitesk.requality.vscode.ui.workbench.IViewReference;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;
import com.unitesk.requality.vscode.ui.workbench.ISelectionListener;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPart;
import com.unitesk.requality.vscode.ui.workbench.IActionBars;
import com.unitesk.requality.vscode.ui.workbench.Page;
import com.unitesk.requality.vscode.ui.workbench.PartInitException;
import com.unitesk.requality.vscode.ui.workbench.ViewPart;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionProvider;

/**
 * {@code org.eclipse.team.internal.ui.history.GenericHistoryView}: the History view. It shows the history page of the
 * page source of the object (the local history page of a file), refreshes it, and can be pinned so that another object
 * is shown in another instance of the view.
 * The navigation history ("Show Previous Histories") lists the histories shown before, and the view can be linked with the
 * selection of the workbench ("Link with Editor and Selection"): it then shows the history of the selected resource.
 * Resources dropped on the view show their history (VSCODE: files are dragged from the VS Code views with Shift held).
 */
public class GenericHistoryView extends ViewPart implements IHistoryView, IPropertyChangeListener {
  public static final String ID = IHistoryView.VIEW_ID;
  private static final String HISTORY_VIEW_GROUP = "org.eclipse.team.ui.historyView";
  private static final String NAVIGATION_GROUP = "org.eclipse.team.ui.navigation";
  /** {@code GenericHistoryView.MAX_NAVIGATION_HISTORY_ENTRIES}. */
  private static final int MAX_NAVIGATION_HISTORY_ENTRIES = 20;
  /** {@code IFileHistoryConstants.PREF_GENERIC_HISTORYVIEW_EDITOR_LINKING}: whether the view is linked. */
  static final String PREF_GENERIC_HISTORYVIEW_EDITOR_LINKING = "pref_generichistory_view_linking";

  private Composite pageParent;
  private Composite pageHolder;
  private Label defaultLabel;
  private IHistoryPage currentPage;
  private IHistoryPageSource currentSource;
  private Action refreshAction;
  private Action pinAction;
  private Action linkWithEditorAction;
  private NavigationHistoryAction navigateAction;
  private boolean viewPinned;
  private boolean linkingEnabled;
  /** The last selected element, shown when the view is linked (or activated). */
  private Object lastSelectedElement;
  private final NavigationHistory navigationHistory = new NavigationHistory();

  /** The selection of the workbench: the view shows the history of the selected element when it is linked. */
  private final ISelectionListener selectionListener = new ISelectionListener() {
    private boolean isUpdatingSelection = false;

    @Override
    public void selectionChanged(IWorkbenchPart part, ISelection selection) {
      if (isUpdatingSelection)
        return;
      try {
        isUpdatingSelection = true;
        if (GenericHistoryView.this == part)
          return;
        if (selection instanceof IStructuredSelection structSelection) {
          lastSelectedElement = structSelection.getFirstElement();
          if (!isLinkingEnabled() || !checkIfPageIsVisible())
            return;
          showLastSelectedElement();
        }
      } finally {
        isUpdatingSelection = false;
      }
    }
  };

  /**
   * GenericHistoryView.editorActivated: a linked view shows the history of the file of the active editor (VSCODE: the
   * text editors of files are VS Code editors; their activation comes from the extension, see ActiveTextEditor).
   */
  private final java.util.function.Consumer<com.unitesk.requality.vscode.ui.resources.IFile> editorListener = file -> {
    if (!isLinkingEnabled() || !checkIfPageIsVisible())
      return;
    lastSelectedElement = file;
    showLastSelectedElement();
  };

  /** The site of the pages of this view (the view's action bars, selection provider and shell). */
  private final class WorkbenchHistoryPageSite implements IHistoryPageSite {
    private ISelectionProvider selectionProvider;

    @Override
    public Shell getShell() {
      return getViewSite().getShell();
    }

    @Override
    public IWorkbenchPart getPart() {
      return GenericHistoryView.this;
    }

    @Override
    public IPageSite getWorkbenchPageSite() {
      return null;
    }

    @Override
    public ToolBarManager getToolBarManager() {
      return getViewSite().getActionBars().getToolBarManager();
    }

    @Override
    public void setSelectionProvider(ISelectionProvider provider) {
      this.selectionProvider = provider;
      getViewSite().setSelectionProvider(provider);
    }

    @Override
    public ISelectionProvider getSelectionProvider() {
      return selectionProvider;
    }

    @Override
    public boolean isModal() {
      return false;
    }
  }

  /** Adds drag and drop support to the history view (resources dropped on the view show their history). */
  void initDragAndDrop() {
    int ops = DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_LINK;
    Transfer[] transfers = new Transfer[] { ResourceTransfer.getInstance() };
    dropTarget = new DropTarget(pageParent, ops);
    dropTarget.setTransfer(transfers);
    dropAdapter = new GenericHistoryDropAdapter(this);
    dropTarget.addDropListener(dropAdapter);
  }

  DropTarget dropTarget;
  GenericHistoryDropAdapter dropAdapter;

  @Override
  public void createPartControl(Composite parent) {
    this.pageParent = parent;
    parent.setLayout(new GridLayout(1, false));
    refreshAction = new Action("Refresh") {
      @Override
      public void run() {
        if (currentPage != null) {
          currentPage.refresh();
        }
      }
    };
    refreshAction.setToolTipText("Refresh");
    pinAction = new Action("Pin this History View") {
      @Override
      public void run() {
        if (isChecked()) {
          linkWithEditorAction.setChecked(false);
          setLinkingEnabled(false);
        }
        setViewPinned(isChecked());
      }
    };
    pinAction.setChecked(viewPinned);
    pinAction.setToolTipText("Pin this History View");
    linkingEnabled = TeamUIPlugin.getPreferenceStore().getBoolean(PREF_GENERIC_HISTORYVIEW_EDITOR_LINKING);
    linkWithEditorAction = new Action("Link with Editor") {
      @Override
      public void run() {
        if (isChecked()) {
          pinAction.setChecked(false);
          setViewPinned(false);
        }
        setLinkingEnabled(isViewPinned() ? false : isChecked());
      }
    };
    linkWithEditorAction.setChecked(isLinkingEnabled());
    linkWithEditorAction.setToolTipText("Link with Editor and Selection");
    navigateAction = new NavigationHistoryAction();
    ISelection selection = getSite().getPage().getSelection();
    if (selection instanceof IStructuredSelection structSelection)
      lastSelectedElement = structSelection.getFirstElement();
    configureToolbars(getViewSite().getActionBars());
    initDragAndDrop();
    getSite().getPage().addSelectionListener(selectionListener);
    com.unitesk.requality.vscode.ui.workbench.ActiveTextEditor.addListener(editorListener);
    showDefaultPage();
  }

  /** The toolbar of the view: Refresh, the link and the pin (the page's actions follow them), then the navigation. */
  private void configureToolbars(IActionBars actionBars) {
    ToolBarManager tbm = actionBars.getToolBarManager();
    tbm.add(new Separator(HISTORY_VIEW_GROUP));
    tbm.add(refreshAction);
    tbm.add(linkWithEditorAction);
    tbm.add(pinAction);
    tbm.add(new Separator(NAVIGATION_GROUP));
    tbm.add(navigateAction);
    tbm.update(false);
  }

  private void showDefaultPage() {
    defaultLabel = new Label(pageParent, SWT.WRAP);
    defaultLabel.setText("There are no history pages to show.");
    defaultLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    pageParent.layout(true, true);
  }

  /** Disposes the current page and its control; the toolbar keeps the actions of the view only. */
  private void disposePage() {
    if (currentPage != null) {
      currentPage.removePropertyChangeListener(this);
      currentPage.dispose();
      currentPage = null;
      currentSource = null;
    }
    if (pageHolder != null && !pageHolder.isDisposed()) {
      pageHolder.dispose();
    }
    pageHolder = null;
    if (defaultLabel != null && !defaultLabel.isDisposed()) {
      defaultLabel.dispose();
    }
    defaultLabel = null;
    if (refreshAction != null) {
      ToolBarManager tbm = getViewSite().getActionBars().getToolBarManager();
      tbm.removeAll();
      configureToolbars(getViewSite().getActionBars());
    }
  }

  @Override
  public IHistoryPage showHistoryFor(Object object) {
    return showHistoryFor(object, false);
  }

  @Override
  public IHistoryPage showHistoryFor(Object object, boolean force) {
    return showHistoryPageFor(object, true, force, null);
  }

  /**
   * Shows the history of the object with the page of the page source (the source of the object when none is given). The
   * page of the view is refreshed when it shows the object already; a pinned view shows the object in another view.
   */
  public IHistoryPage showHistoryPageFor(Object object, boolean refresh, boolean force, IHistoryPageSource pageSource) {
    lastSelectedElement = null;
    if (pageParent == null || pageParent.isDisposed()) {
      return null;
    }
    pageSource = getPageSourceFor(object, pageSource);
    if (pageSource == null || !pageSource.canShowHistoryFor(object)) {
      return null;
    }
    IHistoryPage existingPage = checkForExistingPage(object, refresh, pageSource);
    if (existingPage != null) {
      return existingPage;
    }
    if (viewPinned && !force) {
      return handlePinnedView(object, refresh, pageSource);
    }
    Page page = pageSource.createPage(object);
    if (!(page instanceof IHistoryPage historyPage)) {
      return null;
    }
    disposePage();
    pageHolder = new Composite(pageParent, SWT.NONE);
    pageHolder.setLayout(new GridLayout(1, false));
    pageHolder.setLayoutData(new GridData(GridData.FILL_BOTH));
    historyPage.addPropertyChangeListener(this);
    historyPage.setSite(new WorkbenchHistoryPageSite());
    if (page instanceof HistoryPage hp) {
      hp.setHistoryView(this);
    }
    page.createControl(pageHolder);
    historyPage.setInput(object);
    currentPage = historyPage;
    currentSource = pageSource;
    setContentDescription(historyPage.getName());
    pageParent.layout(true, true);
    addNavigationHistoryEntry();
    return historyPage;
  }

  /** The page of the view when it shows the object with the source, refreshed when asked. */
  private IHistoryPage checkForExistingPage(Object object, boolean refresh, IHistoryPageSource pageSource) {
    if (currentPage != null && matches(currentPage, object, pageSource)) {
      if (refresh) {
        currentPage.refresh();
      }
      return currentPage;
    }
    return null;
  }

  private boolean matches(IHistoryPage page, Object object, IHistoryPageSource pageSource) {
    Object input = page.getInput();
    return input != null && input.equals(object) && Objects.equals(currentSource, pageSource);
  }

  /** A pinned view shows the object in an unpinned view, or in a new instance of the view. */
  private IHistoryPage handlePinnedView(Object object, boolean refresh, IHistoryPageSource source) {
    GenericHistoryView unpinned = findUnpinnedHistoryView();
    if (unpinned != null) {
      getSite().getPage().activate(unpinned);
      return unpinned.showHistoryPageFor(object, refresh, true, source);
    }
    try {
      String id = ID + System.currentTimeMillis();
      IViewPart view = getSite().getPage().showView(ID, id, IWorkbenchPage.VIEW_CREATE);
      getSite().getPage().activate(view);
      if (view instanceof GenericHistoryView ghv) {
        return ghv.showHistoryPageFor(object, refresh, true, source);
      }
    } catch (PartInitException e) {
      RLogger.logError(e);
    }
    return null;
  }

  /** An unpinned History view of the workbench, or null. */
  public GenericHistoryView findUnpinnedHistoryView() {
    for (IViewReference h : getSite().getPage().getViewReferences()) {
      if (ID.equals(h.getId())) {
        IViewPart view = h.getView(false);
        if (view instanceof GenericHistoryView ghv && !ghv.isViewPinned()) {
          return ghv;
        }
      }
    }
    return null;
  }

  boolean isViewPinned() {
    return viewPinned;
  }

  void setViewPinned(boolean pinned) {
    this.viewPinned = pinned;
  }

  /** The source of the pages of an object: the local history of a file, or the adapters of the object. */
  private IHistoryPageSource getPageSourceFor(Object object, IHistoryPageSource pageSource) {
    if (object == null || pageSource != null) {
      return pageSource;
    }
    IResource resource = object instanceof IResource r ? r : Adapters.adapt(object, IResource.class);
    if (resource == null) {
      return Adapters.adapt(object, IHistoryPageSource.class);
    }
    if (resource.getProject() == null) {
      return null;
    }
    RepositoryProvider provider = RepositoryProvider.getProvider(resource.getProject());
    if (provider == null) {
      return LocalHistoryPageSource.getInstance();
    }
    IHistoryPageSource source = Adapters.adapt(provider, IHistoryPageSource.class);
    return source != null ? source : LocalHistoryPageSource.getInstance();
  }

  @Override
  public IHistoryPage getHistoryPage() {
    return currentPage;
  }

  /** Whether the view follows the selection of the workbench. */
  boolean isLinkingEnabled() {
    return linkingEnabled;
  }

  /** Links the view with the selection of the workbench (and shows the last selected element when linked). */
  public void setLinkingEnabled(boolean enabled) {
    this.linkingEnabled = enabled;
    TeamUIPlugin.getPreferenceStore().setValue(PREF_GENERIC_HISTORYVIEW_EDITOR_LINKING, enabled);
    if (enabled)
      showLastSelectedElement();
  }

  /** Whether the view can show a page (it is created and not disposed). */
  private boolean checkIfPageIsVisible() {
    return pageParent != null && !pageParent.isDisposed();
  }

  /** Shows the history of the last selected element: the resource it adapts to, or the element itself. */
  private void showLastSelectedElement() {
    if (lastSelectedElement == null)
      return;
    Object element = lastSelectedElement;
    Object resource = Adapters.adapt(element, IResource.class);
    showHistoryPageFor(resource != null ? resource : element, false, false, null);
    lastSelectedElement = null;
  }

  /** Records the input of the page that is shown in the navigation history (not while navigating in it). */
  private void addNavigationHistoryEntry() {
    if (currentPage == null)
      return;
    Object input = currentPage.getInput();
    if (input != null)
      navigationHistory.addEntry(input, currentPage.getName(), currentSource);
  }

  @Override
  public void propertyChange(PropertyChangeEvent event) {
    if (event.getSource() == currentPage && IHistoryPage.P_NAME.equals(event.getProperty())) {
      navigationHistory.updateName(currentPage, currentSource);
      Display.getDefault().asyncExec(() -> {
        if (currentPage != null) {
          setContentDescription(currentPage.getName());
        }
      });
    }
  }

  @Override
  public void setFocus() {
    if (isLinkingEnabled() && lastSelectedElement != null)
      showLastSelectedElement();
    if (currentPage instanceof Page page && page.getControl() != null) {
      page.setFocus();
    }
  }

  @Override
  public void dispose() {
    getSite().getPage().removeSelectionListener(selectionListener);
    com.unitesk.requality.vscode.ui.workbench.ActiveTextEditor.removeListener(editorListener);
    disposePage();
    if (navigateAction != null)
      navigateAction.dispose();
    super.dispose();
  }

  /** {@code GenericHistoryView.NavigationHistory}: the histories shown, the current one last. */
  class NavigationHistory {
    private final List<NavigationHistoryEntry> history = new ArrayList<>();
    private int position;
    private boolean navigating;

    int size() {
      return history.size();
    }

    /** Shows the previous history of the list; the first one again when the current one is the first. */
    void gotoPreviousEntry() {
      if (position > 0)
        position--;
      else
        position = history.size() - 1;
      gotoEntry();
    }

    private void gotoEntry() {
      try {
        navigating = true;
        NavigationHistoryEntry currentEntry = getCurrentEntry();
        showHistoryPageFor(currentEntry.object, true, true, currentEntry.source);
      } finally {
        navigating = false;
      }
    }

    NavigationHistoryEntry getCurrentEntry() {
      return history.get(position);
    }

    void addEntry(Object object, String name, IHistoryPageSource source) {
      if (!navigating) {
        NavigationHistoryEntry entry = new NavigationHistoryEntry(object, name, source);
        history.remove(entry);
        history.add(entry);
        if (history.size() > MAX_NAVIGATION_HISTORY_ENTRIES)
          history.remove(0);
        position = history.size() - 1;
      }
      navigateAction.update();
    }

    NavigationHistoryEntry[] getEntries() {
      return history.toArray(new NavigationHistoryEntry[history.size()]);
    }

    void gotoEntry(NavigationHistoryEntry entry) {
      position = history.indexOf(entry);
      gotoEntry();
    }

    /** The entry that {@link #gotoPreviousEntry} shows next. */
    NavigationHistoryEntry getPreviousEntry() {
      int next = position - 1;
      if (next < 0)
        next = size() - 1;
      return history.get(next);
    }

    void updateName(IHistoryPage historyPage, IHistoryPageSource pageSource) {
      for (NavigationHistoryEntry entry : getEntries())
        if (entry.matches(historyPage, pageSource))
          entry.name = historyPage.getName();
      navigateAction.update();
    }
  }

  /** {@code GenericHistoryView.NavigationHistoryEntry}: an input shown, with its name and the source of its page. */
  static class NavigationHistoryEntry {
    final Object object;
    String name;
    final IHistoryPageSource source;

    NavigationHistoryEntry(Object object, String name, IHistoryPageSource source) {
      this.object = object;
      this.name = name;
      this.source = source;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof NavigationHistoryEntry other && other.object.equals(object)
          && Objects.equals(other.source, source);
    }

    @Override
    public int hashCode() {
      return object.hashCode();
    }

    boolean matches(IHistoryPage historyPage, IHistoryPageSource pageSource) {
      return object.equals(historyPage.getInput()) && Objects.equals(source, pageSource);
    }
  }

  /**
   * {@code GenericHistoryView.NavigationHistoryAction}: "Show Previous Histories": goes back to the previous history;
   * its drop-down lists the histories shown.
   */
  class NavigationHistoryAction extends Action {
    private final MenuCreator menuCreator;
    private IAction[] actions;

    NavigationHistoryAction() {
      super("Show Previous Histories");
      menuCreator = new MenuCreator() {
        @Override
        protected IAction[] getDropDownActions() {
          return getActions();
        }
      };
      setMenuCreator(menuCreator);
      update();
    }

    private IAction[] createActions() {
      List<IAction> result = new ArrayList<>();
      for (NavigationHistoryEntry entry : navigationHistory.getEntries())
        result.add(new NavigationHistoryEntryAction(entry));
      return result.toArray(new IAction[0]);
    }

    @Override
    public void run() {
      navigationHistory.gotoPreviousEntry();
      updateCheckState();
    }

    /** Enables the action when there is a previous history, and names it in the tooltip. */
    void update() {
      setEnabled(navigationHistory.size() > 1);
      if (isEnabled())
        setToolTipText(MessageFormat.format("Show {0}", navigationHistory.getPreviousEntry().name));
      else
        setToolTipText("Show Previous Histories");
      actions = null;
      menuCreator.rebuildMenu();
      updateCheckState();
    }

    void updateCheckState() {
      for (IAction action : getActions())
        if (action instanceof NavigationHistoryEntryAction a)
          a.update();
      menuCreator.updateMenuState();
    }

    void dispose() {
      menuCreator.dispose();
    }

    private IAction[] getActions() {
      if (actions == null)
        actions = createActions();
      return actions;
    }
  }

  /** {@code GenericHistoryView.NavigationHistoryEntryAction}: an entry of the drop-down; checked when it is shown. */
  class NavigationHistoryEntryAction extends Action {
    private final NavigationHistoryEntry entry;

    NavigationHistoryEntryAction(NavigationHistoryEntry entry) {
      super(entry.name);
      this.entry = entry;
    }

    @Override
    public void run() {
      navigationHistory.gotoEntry(entry);
      navigateAction.updateCheckState();
    }

    void update() {
      setChecked(navigationHistory.getCurrentEntry() == entry);
    }
  }

  /** The drop-down menu of an action, rebuilt when the menu is shown after a change. */
  abstract static class MenuCreator implements IMenuCreator {
    private MenuManager menuManager;

    @Override
    public void dispose() {
      if (menuManager != null) {
        menuManager.dispose();
        menuManager = null;
      }
    }

    @Override
    public Menu getMenu(Control parent) {
      if (menuManager == null) {
        menuManager = new MenuManager();
        Menu menu = menuManager.createContextMenu(parent);
        IAction[] actions = getDropDownActions();
        for (int i = actions.length - 1; i >= 0; i--)
          menuManager.add(actions[i]);
        updateMenuState();
        return menu;
      }
      return menuManager.getMenu();
    }

    void updateMenuState() {
      if (menuManager != null)
        menuManager.update(true);
    }

    protected abstract IAction[] getDropDownActions();

    @Override
    public Menu getMenu(Menu parent) {
      return null;
    }

    void rebuildMenu() {
      if (menuManager != null) {
        menuManager.dispose();
        menuManager = null;
      }
    }
  }
}
