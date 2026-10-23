package com.unitesk.requality.vscode.ui.navigator;

import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.FillLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.workbench.IMemento;
import com.unitesk.requality.vscode.ui.workbench.IViewSite;
import com.unitesk.requality.vscode.ui.workbench.PartInitException;
import com.unitesk.requality.vscode.ui.workbench.ViewPart;

/**
 * {@code org.eclipse.ui.navigator.CommonNavigator}: a view with a {@link CommonViewer} whose input is the workspace
 * root (rendered by the server in the VS Code view that hosts the part).
 */
public class CommonNavigator extends ViewPart {
  /** {@code CommonNavigator.IS_LINKING_ENABLED_PROPERTY}: fired when the linking with the editors changes. */
  public static final int IS_LINKING_ENABLED_PROPERTY = 0x10000;
  private CommonViewer commonViewer;
  private NavigatorActionService actionService;
  private boolean linking;
  private IMemento memento;

  @Override
  public void init(IViewSite site) throws PartInitException {
    super.init(site);
  }

  public void init(IViewSite site, IMemento aMemento) throws PartInitException {
    this.memento = aMemento;
    init(site);
  }

  @Override
  public void createPartControl(Composite aParent) {
    aParent.setLayout(new FillLayout());
    // VSCODE: the viewer of a navigator whose tree is the VS Code view is set before the part control (setCommonViewer)
    if (commonViewer == null)
      setCommonViewer(createCommonViewerObject(aParent));
    initListeners(commonViewer);
    commonViewer.setInput(getInitialInput());
    if (commonViewer.getFrameList() == null)
      commonViewer.createFrameList();
    if (getSite() != null)
      getSite().setSelectionProvider(commonViewer);
    if (actionService == null)
      actionService = new NavigatorActionService();
    if (getViewSite() != null)
      actionService.fillActionBars(getViewSite().getActionBars());
  }

  /** Sets the viewer of the navigator (its tree); the viewer is then the one of {@link #getCommonViewer()}. */
  public void setCommonViewer(CommonViewer viewer) {
    commonViewer = viewer;
    viewer.setCommonNavigator(this);
  }

  protected CommonViewer createCommonViewerObject(Composite aParent) {
    return new CommonViewer(getViewSite() == null ? getClass().getName() : getViewSite().getId(), aParent,
        SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
  }

  protected void initListeners(com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer viewer) {
    viewer.addDoubleClickListener(event -> handleDoubleClick(event));
  }

  /**
   * {@code CommonNavigator.handleDoubleClick} with the open of {@code CommonNavigatorManager}
   * (OpenAndLinkWithEditorHelper.open on the open event of a double click): the OPEN handler of the action providers
   * for the selection opens the element; without an OPEN handler an expandable element is expanded or collapsed.
   */
  protected void handleDoubleClick(com.unitesk.requality.vscode.ui.jface.viewers.DoubleClickEvent anEvent) {
    if (getViewSite() == null)
      return;
    // CommonNavigatorManager: the action bars are those of the action providers for the selection
    if (actionService != null)
      actionService.fillActionBars(getViewSite().getActionBars());
    com.unitesk.requality.vscode.ui.jface.action.IAction openHandler = getViewSite().getActionBars()
        .getGlobalActionHandler(ICommonActionConstants.OPEN);
    if (openHandler == null) {
      com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection selection =
          (com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection) anEvent.getSelection();
      Object element = selection.getFirstElement();
      com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer viewer = getCommonViewer();
      if (element != null && viewer.isExpandable(element))
        viewer.setExpandedState(element, !viewer.getExpandedState(element));
    } else if (openHandler.isEnabled())
      openHandler.run();
  }

  protected Object getInitialInput() {
    return ResourcesPlugin.getWorkspace().getRoot();
  }

  public CommonViewer getCommonViewer() {
    return commonViewer;
  }

  public INavigatorContentService getNavigatorContentService() {
    return commonViewer == null ? null : commonViewer.getNavigatorContentService();
  }

  public NavigatorActionService getNavigatorActionService() {
    return actionService;
  }

  /** Sets the action providers of the navigator (the explorer's are bound to its viewer and content). */
  // VSCODE: the navigator of the Requality Explorer sets its action service once its viewer is set (ExplorerService).
  public void setNavigatorActionService(NavigatorActionService service) {
    actionService = service;
  }

  public void selectReveal(ISelection selection) {
    if (commonViewer != null)
      commonViewer.setSelection(selection, true);
  }

  /** The title tool tip shows the input of the viewer (the frame the navigator went into). */
  public void updateTitle() {
    Object input = getCommonViewer() == null ? null : getCommonViewer().getInput();
    if (input == null)
      setTitleToolTip("");
    else
      setTitleToolTip(getFrameToolTipText(input));
  }

  /** The tool tip of a frame and of the view title: the label of the element. */
  public String getFrameToolTipText(Object anElement) {
    CommonViewer v = getCommonViewer();
    if (v == null)
      return "";
    return ((com.unitesk.requality.vscode.ui.jface.viewers.ILabelProvider) v.getLabelProvider()).getText(anElement);
  }

  /** For subclasses (ProjectExplorer shows the working set); the CommonNavigator itself shows nothing. */
  public void setWorkingSetLabel(String label) {
  }

  public String getWorkingSetLabel() {
    return null;
  }

  public boolean isLinkingEnabled() {
    return linking;
  }

  public void setLinkingEnabled(boolean toEnableLinking) {
    linking = toEnableLinking;
    firePropertyChange(IS_LINKING_ENABLED_PROPERTY);
  }

  public void saveState(IMemento aMemento) {
  }

  protected IMemento getMemento() {
    return memento;
  }

  @Override
  public void setFocus() {
    if (commonViewer != null && !commonViewer.getControl().isDisposed())
      commonViewer.getControl().setFocus();
  }

  @Override
  public void dispose() {
    if (actionService != null)
      actionService.dispose();
    super.dispose();
  }
}
