package com.unitesk.requality.vscode.services;

import com.unitesk.requality.vscode.RequalityServer;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.FillLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.tabbed.PropertyRegistry;
import com.unitesk.requality.vscode.ui.tabbed.TabbedPropertySheetPage;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPart;
import com.unitesk.requality.vscode.ui.resources.ProjectRef;

/**
 * The Properties view (Eclipse PropertySheet with the Requality tabbed property contributor), rendered in the
 * {@code requality.properties} webview view.
 */
public final class PropertiesService implements Services.Service {
  public static final String CONTRIBUTOR_ID = "com.unitesk.requality.views.RequalityPropertyContributor";
  public static final String VIEW_ID = "requality.properties";


  /** The Eclipse id of the Properties view (PropertySheet). */
  public static final String PART_ID = "org.eclipse.ui.views.PropertySheet";

  /**
   * The Properties view as a workbench part (org.eclipse.ui.views.PropertySheet) showing the tabbed property page of
   * the Requality contributor, so that sections find it with findView and register their context menus with its
   * site, and handlers see it as the active part (RemoveLocationFromRequirementHandler).
   */
  public static final class PropertiesView extends com.unitesk.requality.vscode.ui.workbench.PropertySheet
      implements com.unitesk.requality.vscode.ui.workbench.ISelectionListener {
    private boolean pinned;
    private IWorkbenchPart currentPart;
    private ISelection currentSelection;
    private boolean needsUpdate;

    /**
     * PropertySheet.selectionChanged: the page shows the selection of the other parts; while the sheet is pinned
     * (Pin to Selection) the selection is kept for when it is unpinned.
     */
    @Override
    public void selectionChanged(IWorkbenchPart part, ISelection selection) {
      if (part == null || part == this || getCurrentPage() == null)
        return;
      if (pinned) {
        if (part == currentPart) {
          currentSelection = selection;
          needsUpdate = true;
        }
        return;
      }
      currentPart = part;
      currentSelection = selection;
      needsUpdate = false;
      ((TabbedPropertySheetPage) getCurrentPage()).selectionChanged(part, selection);
    }

    @Override
    public boolean isPinned() {
      return pinned;
    }

    /**
     * PinPropertySheetAction ("Pin to Selection"): pins the sheet to the current selection; the content description
     * says which part it comes from ("Selection from {0}"). Unpinning shows the current selection of that part.
     */
    public void setPinned(boolean pin) {
      if (pin == pinned)
        return;
      pinned = pin;
      if (com.unitesk.requality.vscode.core.ServerContext.server() != null) {
        com.google.gson.JsonObject p = new com.google.gson.JsonObject();
        p.addProperty("key", "requality.properties.pinned");
        p.addProperty("value", pinned);
        com.unitesk.requality.vscode.core.ServerContext.client().notify("client/setContext", p);
      }
      setContentDescription(pinned && currentPart != null
          ? java.text.MessageFormat.format("Selection from {0}", currentPart.getTitle()) : "");
      if (!pinned && currentPart != null && currentSelection != null && needsUpdate) {
        needsUpdate = false;
        ((TabbedPropertySheetPage) getCurrentPage()).selectionChanged(currentPart, currentSelection);
      }
    }

    /** PropertySheet.partClosed: the part of a pinned selection closed, the sheet is unpinned. */
    public void partClosed(IWorkbenchPart part) {
      if (part == currentPart) {
        if (pinned)
          setPinned(false);
        currentPart = null;
        currentSelection = null;
      }
    }

    @Override
    public void dispose() {
      getSite().getPage().removeSelectionListener(this);
      getSite().getPage().removePartListener(partListener);
      super.dispose();
    }

    private final com.unitesk.requality.vscode.ui.workbench.IPartListener partListener = new com.unitesk.requality.vscode.ui.workbench.IPartListener() {
      @Override
      public void partActivated(IWorkbenchPart part) {
      }

      @Override
      public void partBroughtToTop(IWorkbenchPart part) {
      }

      @Override
      public void partClosed(IWorkbenchPart part) {
        PropertiesView.this.partClosed(part);
      }

      @Override
      public void partDeactivated(IWorkbenchPart part) {
      }

      @Override
      public void partOpened(IWorkbenchPart part) {
      }
    };

    @Override
    public void createPartControl(com.unitesk.requality.vscode.ui.swt.widgets.Composite parent) {
      getSite().getPage().addSelectionListener(this);
      getSite().getPage().addPartListener(partListener);
      // The tabs and sections of the Requality property contributor (plugin.xml).
      PropertyRegistry.loadFromExtensions(CONTRIBUTOR_ID);
      parent.getShell().setText("Properties");
      TabbedPropertySheetPage page = new TabbedPropertySheetPage(() -> CONTRIBUTOR_ID) {
        @Override
        protected String titleOf(Object element) {
          if (element instanceof ProjectRef)
            return ((ProjectRef) element).getName();
          if (element instanceof com.unitesk.requality.core.model.TreeNode)
            return ExplorerService.label((com.unitesk.requality.core.model.TreeNode) element);
          return super.titleOf(element);
        }
      };
      // PageBookView: the view's selection is the one of its page's site, which the tabbed page does not set
      // (an empty selection while the Properties view is the active part).
      getSite().setSelectionProvider(new com.unitesk.requality.vscode.ui.jface.viewers.ISelectionProvider() {
        @Override
        public void addSelectionChangedListener(com.unitesk.requality.vscode.ui.jface.viewers.ISelectionChangedListener l) {
        }

        @Override
        public com.unitesk.requality.vscode.ui.jface.viewers.ISelection getSelection() {
          return com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection.EMPTY;
        }

        @Override
        public void removeSelectionChangedListener(com.unitesk.requality.vscode.ui.jface.viewers.ISelectionChangedListener l) {
        }

        @Override
        public void setSelection(com.unitesk.requality.vscode.ui.jface.viewers.ISelection selection) {
        }
      });
      // PageBookView.initPage: the page site is the one of the Properties view.
      page.init(new com.unitesk.requality.vscode.ui.workbench.PageSite(getSite()));
      page.createControl(parent);
      setCurrentPage(page);
      // PropertySheet: a new sheet shows the current selection of the active part.
      IWorkbenchPart active = com.unitesk.requality.vscode.ui.workbench.Workbench.activePart();
      ISelection current = com.unitesk.requality.vscode.ui.workbench.Workbench.currentSelection();
      if (active != null && current != null)
        selectionChanged(active, current);
    }

    @Override
    public String getTitle() {
      return "Properties";
    }

    @Override
    public void setFocus() {
    }
  }

  static {
    com.unitesk.requality.vscode.ui.workbench.Workbench.setHostView(PART_ID, VIEW_ID);
    com.unitesk.requality.vscode.ui.workbench.Workbench.registerView(PART_ID, PropertiesView::new);
  }

  /** The page of the Properties view (the view part is created on first use). */
  public static TabbedPropertySheetPage page() {
    return Display.getDefault().syncCall(() -> {
      try {
        com.unitesk.requality.vscode.ui.workbench.IViewPart v = com.unitesk.requality.vscode.ui.workbench.PlatformUI
            .getWorkbench().getActiveWorkbenchWindow().getActivePage()
            .showView(PART_ID, null, com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage.VIEW_CREATE);
        return (TabbedPropertySheetPage) ((PropertiesView) v).getCurrentPage();
      } catch (com.unitesk.requality.vscode.ui.workbench.PartInitException e) {
        throw new IllegalStateException(e);
      }
    });
  }

  /** Workbench selection changed (called on the UI thread). */
  public static void selectionChanged(IWorkbenchPart part, ISelection selection) {
    page().selectionChanged(part, selection);
  }

  /** TreeNodePropertiesTab refreshed the page after every operation history change. */
  private static final com.unitesk.requality.vscode.ui.commands.operations.IOperationHistoryListener HISTORY = e -> {
    int t = e.getEventType();
    if (t == com.unitesk.requality.vscode.ui.commands.operations.OperationHistoryEvent.UNDONE
        || t == com.unitesk.requality.vscode.ui.commands.operations.OperationHistoryEvent.REDONE)
      Display.getDefault().asyncExec(() -> {
        page().refresh();
      });
  };

  @Override
  public void register(RequalityServer server) {
    com.unitesk.requality.vscode.ui.commands.operations.OperationHistory.INSTANCE.addOperationHistoryListener(HISTORY);
    server.rpc().onRequest("properties/open", p -> {
      server.requireInitialized();
      page();
      return null;
    });
    // Pin to Selection of the Properties view (the toggle of its toolbar in VS Code).
    server.rpc().onRequest("properties/pin", p -> {
      server.requireInitialized();
      boolean pin = p.has("pinned") && p.get("pinned").getAsBoolean();
      page();
      return Display.getDefault().syncCall(() -> {
        for (com.unitesk.requality.vscode.ui.workbench.IViewReference r : com.unitesk.requality.vscode.ui.workbench.PlatformUI
            .getWorkbench().getActiveWorkbenchWindow().getActivePage().getViewReferences())
          if (PART_ID.equals(r.getId()) && r.getView(false) instanceof PropertiesView)
            ((PropertiesView) r.getView(false)).setPinned(pin);
        return pin;
      });
    });
  }
}
