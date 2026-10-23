package com.unitesk.requality.vscode.ui.team.synchronize.actions;

import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.action.IContributionItem;
import com.unitesk.requality.vscode.ui.jface.action.IMenuCreator;
import com.unitesk.requality.vscode.ui.jface.action.MenuManager;
import com.unitesk.requality.vscode.ui.jface.action.Separator;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardDialog;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Menu;
import com.unitesk.requality.vscode.ui.team.synchronize.SynchronizeManager;
import com.unitesk.requality.vscode.ui.team.synchronize.SynchronizeView;
import com.unitesk.requality.vscode.ui.team.ui.synchronize.ISynchronizeParticipant;
import com.unitesk.requality.vscode.ui.team.wizards.GlobalSynchronizeWizard;

/**
 * {@code org.eclipse.team.internal.ui.synchronize.actions.SynchronizePageDropDownAction}: the drop-down of the Synchronize
 * view that switches between the synchronize participants and starts a synchronization. Its menu lists one radio item per
 * participant (the current one checked), a separator and Synchronize...; its run shows the next participant, or opens the
 * Synchronize wizard without a participant.
 */
public class SynchronizePageDropDownAction extends Action implements IMenuCreator {
  /** {@code GlobalRefreshAction_4}: the label without a current participant. */
  static final String SYNCHRONIZE_LABEL = "Synchronize...";

  private final SynchronizeView view;
  private final Action synchronizeAction;
  private MenuManager menuManager;

  public SynchronizePageDropDownAction(SynchronizeView view) {
    this.view = view;
    setText("Synchronizations");
    setDescription("Synchronizations");
    setToolTipText("Synchronizations");
    setMenuCreator(this);
    synchronizeAction = new Action(SYNCHRONIZE_LABEL) {
      @Override
      public void run() {
        new WizardDialog(view.getViewSite().getShell(), new GlobalSynchronizeWizard()).open();
      }
    };
    synchronizeAction.setActionDefinitionId("org.eclipse.team.ui.synchronizeAll");
    update();
  }

  @Override
  public void dispose() {
    menuManager = null;
  }

  @Override
  public Menu getMenu(Menu parent) {
    return null;
  }

  @Override
  public Menu getMenu(Control parent) {
    if (menuManager == null) {
      menuManager = new MenuManager();
      menuManager.createContextMenu(parent);
    }
    fillMenu();
    return menuManager.getMenu();
  }

  /** The items of the menu, for the server ({@code menu/items}). */
  public IContributionItem[] getMenuItems() {
    if (menuManager == null)
      menuManager = new MenuManager();
    fillMenu();
    return menuManager.getItems();
  }

  /** Fills the menu with the participants and the Synchronize... action. */
  private void fillMenu() {
    menuManager.removeAll();
    for (ISynchronizeParticipant p : SynchronizeManager.getInstance().getSynchronizeParticipants())
      menuManager.add(new ShowSynchronizeParticipantAction(view, p));
    menuManager.add(new Separator());
    menuManager.add(synchronizeAction);
    menuManager.update(true);
  }

  @Override
  public void run() {
    ISynchronizeParticipant[] all = SynchronizeManager.getInstance().getSynchronizeParticipants();
    if (all.length == 0) {
      synchronizeAction.run();
      return;
    }
    // the next participant after the current one (the first one after the last)
    int next = 0;
    for (int i = 0; i < all.length; i++)
      if (all[i] == view.getParticipant())
        next = (i + 1) % all.length;
    view.display(all[next]);
  }

  /** Updates the label of the drop-down ({@code SynchronizePageDropDownAction.update}). */
  public void update() {
    setText(SYNCHRONIZE_LABEL);
    setToolTipText(SYNCHRONIZE_LABEL);
  }
}
