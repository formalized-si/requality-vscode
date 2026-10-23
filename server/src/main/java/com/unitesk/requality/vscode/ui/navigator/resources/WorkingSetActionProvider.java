package com.unitesk.requality.vscode.ui.navigator.resources;

import com.unitesk.requality.vscode.ui.jface.preference.IPropertyChangeListener;
import com.unitesk.requality.vscode.ui.jface.preference.PropertyChangeEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.ViewerFilter;
import com.unitesk.requality.vscode.ui.navigator.CommonActionProvider;
import com.unitesk.requality.vscode.ui.navigator.CommonViewer;
import com.unitesk.requality.vscode.ui.navigator.ICommonActionExtensionSite;
import com.unitesk.requality.vscode.ui.navigator.IExtensionActivationListener;
import com.unitesk.requality.vscode.ui.navigator.IExtensionStateModel;
import com.unitesk.requality.vscode.ui.navigator.INavigatorContentService;
import com.unitesk.requality.vscode.ui.navigator.INavigatorFilterService;
import com.unitesk.requality.vscode.ui.navigator.workingsets.WorkingSetsContentProvider;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.workbench.IActionBars;
import com.unitesk.requality.vscode.ui.workbench.IAggregateWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.IMemento;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSetManager;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.ResourceWorkingSetFilter;
import com.unitesk.requality.vscode.ui.workbench.actions.WorkingSetFilterActionGroup;

/**
 * {@code org.eclipse.ui.internal.navigator.resources.actions.WorkingSetActionProvider}: the working set actions of
 * the view menu (Select Working Set..., Deselect Working Set, Edit Active Working Set..., the recently used working
 * sets and "Top Level Elements"); the active working set filters the viewer through the common filter
 * {@code org.eclipse.ui.navigator.resources.filters.workingSet}, and with working sets as the top level elements the
 * input of the viewer is the (aggregate) working set.
 */
public class WorkingSetActionProvider extends CommonActionProvider {
  private static final String TAG_CURRENT_WORKING_SET_NAME = "currentWorkingSetName";
  private static final String WORKING_SET_FILTER_ID = "org.eclipse.ui.navigator.resources.filters.workingSet";

  private boolean contributedToViewMenu = false;
  private CommonViewer viewer;
  private INavigatorContentService contentService;
  private INavigatorFilterService filterService;
  private WorkingSetFilterActionGroup workingSetActionGroup;
  private WorkingSetRootModeActionGroup workingSetRootModeActionGroup;
  private final Object originalViewerInput = ResourcesPlugin.getWorkspace().getRoot();
  private IExtensionStateModel extensionStateModel;
  private boolean emptyWorkingSet;
  private IWorkingSet workingSet;
  private IPropertyChangeListener topLevelModeListener;
  private boolean ignoreFilterChangeEvents;

  /** Listens to the working set manager: the active working set is removed, renamed or changed. */
  public class WorkingSetManagerListener implements IPropertyChangeListener {
    private boolean listening = false;

    @Override
    public void propertyChange(PropertyChangeEvent event) {
      String property = event.getProperty();
      Object newValue = event.getNewValue();
      Object oldValue = event.getOldValue();
      String newLabel = null;
      if (IWorkingSetManager.CHANGE_WORKING_SET_REMOVE.equals(property) && oldValue == workingSet) {
        newLabel = "";
        setWorkingSet(null);
      } else if (IWorkingSetManager.CHANGE_WORKING_SET_NAME_CHANGE.equals(property) && newValue == workingSet) {
        newLabel = workingSet.getLabel();
      } else if (IWorkingSetManager.CHANGE_WORKING_SET_CONTENT_CHANGE.equals(property) && newValue == workingSet) {
        if (workingSet.isAggregateWorkingSet() && workingSet.isEmpty()) {
          // act as if the working set has been made null
          if (!emptyWorkingSet) {
            emptyWorkingSet = true;
            setWorkingSetFilter(null);
            newLabel = null;
          }
        } else if (emptyWorkingSet) {
          // the set went from empty to non-empty: restore it
          emptyWorkingSet = false;
          setWorkingSetFilter(workingSet);
          newLabel = workingSet.getLabel();
        }
      }
      if (viewer != null) {
        if (newLabel != null)
          viewer.getCommonNavigator().setWorkingSetLabel(newLabel);
        viewer.getFrameList().reset();
        viewer.refresh();
      }
      saveViewState();
    }

    public synchronized void listen() {
      if (!listening) {
        PlatformUI.getWorkbench().getWorkingSetManager().addPropertyChangeListener(managerChangeListener);
        listening = true;
      }
    }

    public synchronized void ignore() {
      if (listening) {
        PlatformUI.getWorkbench().getWorkingSetManager().removePropertyChangeListener(managerChangeListener);
        listening = false;
      }
    }
  }

  private final IPropertyChangeListener filterChangeListener = event -> {
    if (ignoreFilterChangeEvents)
      return;
    IWorkingSet newWorkingSet = (IWorkingSet) event.getNewValue();
    setWorkingSet(newWorkingSet);
    if (newWorkingSet != null) {
      if (!contentService.isActive(WorkingSetsContentProvider.EXTENSION_ID)) {
        contentService.getActivationService().activateExtensions(new String[] { WorkingSetsContentProvider.EXTENSION_ID }, false);
        contentService.getActivationService().persistExtensionActivations();
      }
      if (newWorkingSet.isAggregateWorkingSet()) {
        IAggregateWorkingSet agWs = (IAggregateWorkingSet) newWorkingSet;
        IWorkingSet[] comps = agWs.getComponents();
        if (comps.length > 1)
          viewer.getCommonNavigator().setWorkingSetLabel("Multiple Working Sets");
        else if (comps.length > 0)
          viewer.getCommonNavigator().setWorkingSetLabel(comps[0].getLabel());
        else
          viewer.getCommonNavigator().setWorkingSetLabel(null);
      } else {
        viewer.getCommonNavigator().setWorkingSetLabel(workingSet.getLabel());
      }
    } else {
      viewer.getCommonNavigator().setWorkingSetLabel(null);
    }
    viewer.getFrameList().reset();
    saveViewState();
  };

  private final WorkingSetManagerListener managerChangeListener = new WorkingSetManagerListener();

  private final IExtensionActivationListener activationListener = new IExtensionActivationListener() {
    private IWorkingSet savedWorkingSet;

    @Override
    public void onExtensionActivation(String aViewerId, String[] theNavigatorExtensionIds, boolean isActive) {
      for (String theNavigatorExtensionId : theNavigatorExtensionIds) {
        if (WorkingSetsContentProvider.EXTENSION_ID.equals(theNavigatorExtensionId)) {
          if (isActive) {
            extensionStateModel = contentService.findStateModel(WorkingSetsContentProvider.EXTENSION_ID);
            workingSetRootModeActionGroup.setStateModel(extensionStateModel);
            extensionStateModel.addPropertyChangeListener(topLevelModeListener);
            if (savedWorkingSet != null)
              setWorkingSet(savedWorkingSet);
            managerChangeListener.listen();
          } else {
            savedWorkingSet = workingSet;
            setWorkingSet(null);
            viewer.getCommonNavigator().setWorkingSetLabel(null);
            managerChangeListener.ignore();
            workingSetRootModeActionGroup.setShowTopLevelWorkingSets(false);
            extensionStateModel.removePropertyChangeListener(topLevelModeListener);
          }
        }
      }
    }
  };

  @Override
  public void init(ICommonActionExtensionSite aSite) {
    viewer = (CommonViewer) aSite.getStructuredViewer();
    contentService = aSite.getContentService();
    filterService = contentService.getFilterService();
    extensionStateModel = contentService.findStateModel(WorkingSetsContentProvider.EXTENSION_ID);
    workingSetActionGroup = new WorkingSetFilterActionGroup(aSite.getViewSite().getShell(), filterChangeListener);
    workingSetRootModeActionGroup = new WorkingSetRootModeActionGroup(viewer, extensionStateModel);
    topLevelModeListener = event -> {
      setWorkingSet(workingSet);
      viewer.getFrameList().reset();
      saveViewState();
    };
    if (contentService.isActive(WorkingSetsContentProvider.EXTENSION_ID)) {
      managerChangeListener.listen();
      extensionStateModel.addPropertyChangeListener(topLevelModeListener);
    }
    contentService.getActivationService().addExtensionActivationListener(activationListener);
  }

  protected void initWorkingSetFilter(String workingSetName) {
    IWorkingSet ws = null;
    if (workingSetName != null && workingSetName.length() > 0) {
      IWorkingSetManager workingSetManager = PlatformUI.getWorkbench().getWorkingSetManager();
      ws = workingSetManager.getWorkingSet(workingSetName);
    }
    // VSCODE: the preference "use the window working set by default" (USE_WINDOW_WORKING_SET_BY_DEFAULT) has no
    // setting here; it is false by default in Eclipse.
    if (ws != null)
      setWorkingSet(ws);
  }

  private void setWorkingSetFilter(IWorkingSet ws) {
    setWorkingSetFilter(ws, true);
  }

  private void setWorkingSetFilter(IWorkingSet ws, boolean firstTime) {
    ResourceWorkingSetFilter workingSetFilter = null;
    for (ViewerFilter filter : viewer.getFilters())
      if (filter instanceof ResourceWorkingSetFilter) {
        workingSetFilter = (ResourceWorkingSetFilter) filter;
        break;
      }
    if (workingSetFilter == null) {
      if (firstTime) {
        filterService.addActiveFilterIds(new String[] { WORKING_SET_FILTER_ID });
        filterService.updateViewer();
        setWorkingSetFilter(ws, false);
        return;
      }
      com.unitesk.requality.core.log.RLogger.logError(new IllegalStateException("Required filter " + WORKING_SET_FILTER_ID
          + " is not present. Working set support will not function correctly."));
      return;
    }
    if (extensionStateModel.getBooleanProperty(WorkingSetsContentProvider.SHOW_TOP_LEVEL_WORKING_SETS)) {
      // a filter would hide the "Others" working set content
      workingSetFilter.setWorkingSet(null);
    } else {
      workingSetFilter.setWorkingSet(emptyWorkingSet ? null : ws);
    }
  }

  protected void setWorkingSet(IWorkingSet ws) {
    workingSet = ws;
    emptyWorkingSet = ws != null && ws.isAggregateWorkingSet() && ws.isEmpty();
    ignoreFilterChangeEvents = true;
    try {
      workingSetActionGroup.setWorkingSet(ws);
    } finally {
      ignoreFilterChangeEvents = false;
    }
    if (viewer != null) {
      setWorkingSetFilter(ws);
      if (ws == null || emptyWorkingSet
          || !extensionStateModel.getBooleanProperty(WorkingSetsContentProvider.SHOW_TOP_LEVEL_WORKING_SETS)) {
        if (viewer.getInput() != originalViewerInput)
          viewer.setInput(originalViewerInput);
        else
          viewer.refresh();
      } else if (!ws.isAggregateWorkingSet()) {
        IWorkingSetManager workingSetManager = PlatformUI.getWorkbench().getWorkingSetManager();
        viewer.setInput(workingSetManager.createAggregateWorkingSet("", "", new IWorkingSet[] { ws }));
      } else {
        viewer.setInput(ws);
      }
    }
  }

  @Override
  public void restoreState(final IMemento aMemento) {
    super.restoreState(aMemento);
    // Eclipse runs this asynchronously to avoid being reentered while a selection change is processed
    viewer.getControl().getShell().getDisplay().asyncExec(() -> {
      if (viewer.getControl().isDisposed())
        return;
      boolean showWorkingSets = true;
      if (aMemento != null) {
        Integer showWorkingSetsInt = aMemento.getInteger(WorkingSetsContentProvider.SHOW_TOP_LEVEL_WORKING_SETS);
        showWorkingSets = showWorkingSetsInt == null || showWorkingSetsInt.intValue() == 1;
        extensionStateModel.setBooleanProperty(WorkingSetsContentProvider.SHOW_TOP_LEVEL_WORKING_SETS, showWorkingSets);
        workingSetRootModeActionGroup.setShowTopLevelWorkingSets(showWorkingSets);
        String lastWorkingSetName = aMemento.getString(TAG_CURRENT_WORKING_SET_NAME);
        initWorkingSetFilter(lastWorkingSetName);
      } else {
        showWorkingSets = false;
        extensionStateModel.setBooleanProperty(WorkingSetsContentProvider.SHOW_TOP_LEVEL_WORKING_SETS, showWorkingSets);
        workingSetRootModeActionGroup.setShowTopLevelWorkingSets(showWorkingSets);
      }
    });
  }

  @Override
  public void saveState(IMemento aMemento) {
    super.saveState(aMemento);
    if (aMemento != null) {
      int showWorkingSets = extensionStateModel.getBooleanProperty(WorkingSetsContentProvider.SHOW_TOP_LEVEL_WORKING_SETS) ? 1 : 0;
      aMemento.putInteger(WorkingSetsContentProvider.SHOW_TOP_LEVEL_WORKING_SETS, showWorkingSets);
      if (workingSet != null)
        aMemento.putString(TAG_CURRENT_WORKING_SET_NAME, workingSet.getName());
    }
  }

  @Override
  public void fillActionBars(IActionBars actionBars) {
    if (!contributedToViewMenu) {
      try {
        super.fillActionBars(actionBars);
        workingSetActionGroup.fillActionBars(actionBars);
        if (workingSetRootModeActionGroup != null)
          workingSetRootModeActionGroup.fillActionBars(actionBars);
      } finally {
        contributedToViewMenu = true;
      }
    }
  }

  @Override
  public void dispose() {
    super.dispose();
    workingSetActionGroup.dispose();
    if (workingSetRootModeActionGroup != null)
      workingSetRootModeActionGroup.dispose();
    managerChangeListener.ignore();
    extensionStateModel.removePropertyChangeListener(topLevelModeListener);
    contentService.getActivationService().removeExtensionActivationListener(activationListener);
  }

  public IPropertyChangeListener getFilterChangeListener() {
    return filterChangeListener;
  }

  /** VSCODE: the view memento is saved when the state changes (see services/ExplorerViewState). */
  private static void saveViewState() {
    com.unitesk.requality.vscode.services.ExplorerService.saveViewState();
  }
}
