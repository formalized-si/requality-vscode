package com.unitesk.requality.vscode.ui.workbench.progress;

import java.util.concurrent.atomic.AtomicBoolean;

import com.unitesk.requality.vscode.services.MenuService;
import com.unitesk.requality.vscode.ui.commands.CommandSupport;
import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.action.IMenuManager;
import com.unitesk.requality.vscode.ui.jface.action.MenuManager;
import com.unitesk.requality.vscode.ui.jface.action.Separator;
import com.unitesk.requality.vscode.ui.jface.action.ToolBarManager;
import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Menu;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchActionConstants;
import com.unitesk.requality.vscode.ui.workbench.ViewPart;

/**
 * {@code org.eclipse.ui.internal.progress.ProgressView} ({@code org.eclipse.ui.views.ProgressView}): the operations of
 * the workbench, one row each with its progress, its task and its stop button; the finished operations are kept until
 * they are removed, by their buttons or by Remove All Finished Operations. The view menu has Remove All Finished
 * Operations and Preferences (the {@link JobsViewPreferenceDialog}); the context menu of a row has Cancel.
 */
public class ProgressView extends ViewPart {
  /** The id of the view ({@code org.eclipse.ui.views.ProgressView}). */
  public static final String ID = "org.eclipse.ui.views.ProgressView";
  /** {@code ProgressView_ClearAllAction}. */
  static final String CLEAR_ALL_TEXT = "Remove All Finished Operations";
  /** {@code ProgressView_CancelAction}. */
  static final String CANCEL_TEXT = "&Cancel";
  /** {@code OpenPreferences_text}: the view menu item that opens the {@link JobsViewPreferenceDialog}. */
  static final String PREFERENCES_TEXT = "&Preferences...";

  private final Runnable changeListener = this::refreshLater;
  private final AtomicBoolean refreshQueued = new AtomicBoolean();
  private ProgressViewer viewer;
  private Action clearAllAction;
  private Action cancelAction;

  public ProgressView() {
  }

  @Override
  public void createPartControl(Composite parent) {
    setPartName("Progress");
    setTitleImage(ImageDescriptor.fromResource("icons/shared/pview.png").createImage());
    parent.setLayout(new GridLayout(1, false));
    viewer = new ProgressViewer(parent, SWT.NONE);
    viewer.setLayoutData(new GridData(GridData.FILL_BOTH));

    clearAllAction = new Action(CLEAR_ALL_TEXT, ImageDescriptor.fromResource("icons/shared/progress_remall.png")) {
      @Override
      public void run() {
        ProgressManager.getInstance().removeAllFinished();
      }
    };
    clearAllAction.setToolTipText(CLEAR_ALL_TEXT);
    cancelAction = new Action(CANCEL_TEXT) {
      @Override
      public void run() {
        viewer.cancelSelection();
      }
    };

    initContextMenu();
    // ProgressView.initPulldownMenu: Remove All Finished Operations, then the preferences of the view
    IMenuManager menu = getViewSite().getActionBars().getMenuManager();
    menu.add(clearAllAction);
    menu.add(new Action(PREFERENCES_TEXT) {
      @Override
      public void run() {
        new JobsViewPreferenceDialog(viewer.getShell()).open();
      }
    });
    ToolBarManager toolBar = getViewSite().getActionBars().getToolBarManager();
    toolBar.add(clearAllAction);
    toolBar.update(false);
    // the view menu and the row menu, listed by the server for the client (menu/items)
    MenuService.register(ID + ".viewMenu", menu::getItems);
    MenuService.register(ID + ".popup", () -> {
      MenuManager m = new MenuManager();
      fillContextMenu(m);
      return m.getItems();
    });

    ProgressManager.getInstance().addListener(changeListener);
    refresh();
  }

  /**
   * {@code ProgressView.initContextMenu}: Cancel for the selected row, then the additions group. The menu is opened on
   * the viewer; Cancel is enabled while the selected job is active.
   */
  private void initContextMenu() {
    MenuManager menuMgr = new MenuManager("#PopupMenu"); //$NON-NLS-1$
    menuMgr.setRemoveAllWhenShown(true);
    menuMgr.addMenuListener(this::fillContextMenu);
    Menu menu = menuMgr.createContextMenu(viewer);
    viewer.setMenu(menu);
  }

  private void fillContextMenu(IMenuManager manager) {
    cancelAction.setEnabled(viewer != null && viewer.canCancelSelection());
    manager.add(cancelAction);
    manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
  }

  /** The Remove All Finished Operations action of the view toolbar (enabled while a finished job is kept). */
  public Action getClearAllAction() {
    return clearAllAction;
  }

  /** The Cancel action of the row menu. */
  public Action getCancelAction() {
    return cancelAction;
  }

  @Override
  public void setFocus() {
    if (viewer != null)
      viewer.setFocus();
  }

  @Override
  public void dispose() {
    ProgressManager.getInstance().removeListener(changeListener);
    super.dispose();
  }

  /** The jobs change on their threads: the view is refreshed once on the display thread. */
  private void refreshLater() {
    if (refreshQueued.compareAndSet(false, true)) {
      Display.getDefault().asyncExec(() -> {
        refreshQueued.set(false);
        refresh();
      });
    }
  }

  private void refresh() {
    if (viewer == null || viewer.isDisposed())
      return;
    viewer.refresh();
    boolean enabled = ProgressManager.getInstance().hasFinished();
    if (enabled != clearAllAction.isEnabled()) {
      clearAllAction.setEnabled(enabled);
      // VSCODE: the toolbar of the view is the VS Code view title, which reads the state of the action again when told
      if (CommandSupport.get() != null)
        CommandSupport.get().refreshElements(ID);
    }
  }
}
