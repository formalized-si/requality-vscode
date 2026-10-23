package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionProvider;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/** Site of a view or editor hosted by the server (IViewSite and IEditorSite). */
public class PartSite implements IViewSite, IEditorSite {
  private final String id;
  private final IWorkbenchPart part;
  private final ActionBars actionBars;
  private ISelectionProvider selectionProvider;
  private Shell shell;

  public PartSite(String id, IWorkbenchPart part) {
    this.id = id;
    this.part = part;
    this.actionBars = new ActionBars(id);
  }

  /** The shell of the part (VSCODE: public for the parts the services create, as the Requality Explorer). */
  public void setShell(Shell shell) {
    this.shell = shell;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public IWorkbenchPart getPart() {
    return part;
  }

  @Override
  public IWorkbenchPage getPage() {
    return Workbench.PAGE;
  }

  @Override
  public IWorkbenchWindow getWorkbenchWindow() {
    return Workbench.WINDOW;
  }

  @Override
  public Shell getShell() {
    return shell;
  }

  @Override
  public ISelectionProvider getSelectionProvider() {
    return selectionProvider;
  }

  @Override
  public void setSelectionProvider(ISelectionProvider provider) {
    selectionProvider = provider;
  }

  /** The handler service of the site: its activations are those of the part (active while the part is active). */
  private final com.unitesk.requality.vscode.ui.commands.IHandlerService handlerService = new com.unitesk.requality.vscode.ui.commands.IHandlerService() {
    private final com.unitesk.requality.vscode.ui.commands.IHandlerService global = Workbench.INSTANCE
        .getService(com.unitesk.requality.vscode.ui.commands.IHandlerService.class);

    @Override
    public Object executeCommand(String commandId, com.unitesk.requality.vscode.ui.swt.widgets.Event event)
        throws com.unitesk.requality.vscode.ui.commands.ExecutionException, com.unitesk.requality.vscode.ui.commands.NotDefinedException,
        com.unitesk.requality.vscode.ui.commands.NotEnabledException, com.unitesk.requality.vscode.ui.commands.NotHandledException {
      return global.executeCommand(commandId, event);
    }

    @Override
    public com.unitesk.requality.vscode.ui.commands.IHandlerActivation activateHandler(String commandId,
        com.unitesk.requality.vscode.ui.commands.IHandler handler) {
      return com.unitesk.requality.vscode.ui.commands.CommandSupport.get().activateHandler(commandId, handler, part);
    }

    @Override
    public void deactivateHandler(com.unitesk.requality.vscode.ui.commands.IHandlerActivation activation) {
      global.deactivateHandler(activation);
    }

    @Override
    public com.unitesk.requality.vscode.ui.commands.IEvaluationContext getCurrentState() {
      return global.getCurrentState();
    }

    @Override
    public com.unitesk.requality.vscode.ui.commands.IEvaluationContext createContextSnapshot(boolean includeSelection) {
      return global.createContextSnapshot(includeSelection);
    }
  };

  @Override
  @SuppressWarnings("unchecked")
  public <T> T getService(Class<T> api) {
    if (api == com.unitesk.requality.vscode.ui.commands.IHandlerService.class)
      return (T) handlerService;
    // The E4 model part of an editor (its editor group is the editor stack of the editor area).
    if (api == com.unitesk.requality.vscode.ui.e4.model.application.ui.basic.MPart.class)
      return (T) com.unitesk.requality.vscode.ui.e4.workbench.modeling.EditorModel.partOf(part);
    return Workbench.INSTANCE.getService(api);
  }

  /** The part was disposed: the activations of its site end. */
  public void disposeServices() {
    if (com.unitesk.requality.vscode.ui.commands.CommandSupport.get() != null)
      com.unitesk.requality.vscode.ui.commands.CommandSupport.get().deactivateHandlers(part);
  }

  @Override
  public IActionBars getActionBars() {
    return actionBars;
  }

  private volatile Runnable actionBarsUpdater;

  /** How the part fills its action bars for its current selection (a navigator: its action providers). */
  public void setActionBarsUpdater(Runnable updater) {
    actionBarsUpdater = updater;
  }

  /** Brings the global action handlers up to date with the part's selection. */
  public void updateActionBars() {
    Runnable r = actionBarsUpdater;
    if (r != null)
      r.run();
  }

  /** The context menu of the part gets the plugin.xml contributions of {@code popup:<part id>}. */
  @Override
  public void registerContextMenu(com.unitesk.requality.vscode.ui.jface.action.MenuManager menuManager,
      ISelectionProvider selectionProvider) {
    registerContextMenu(id, menuManager, selectionProvider);
  }

  @Override
  public void registerContextMenu(String menuId, com.unitesk.requality.vscode.ui.jface.action.MenuManager menuManager,
      ISelectionProvider selectionProvider) {
    PopupMenuExtender.register(menuId, menuManager, selectionProvider, part);
  }
}
