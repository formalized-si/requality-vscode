package com.unitesk.requality.eclipse.handlers;

import java.io.File;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.DialogSettings;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

import com.unitesk.requality.core.api.API;
import com.unitesk.requality.core.api.MassOppDescriptor;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.eclipse.wizards.massops.MassOppsWizard;

public class MassOpsWizardHandler extends AbstractHandler {

  private static final String SECTION_NAME = "MassOppWizard's Settings";
  private static DialogSettings dialogSettings = new DialogSettings(SECTION_NAME);

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {

    
    MassOppDescriptor[] objs = API.getMassOppDescriptors();

    Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
    /*	GridLayout layout = new GridLayout();
    shell.setLayout(layout);*/

    TreeNode root = SelectionManager.getOne().getSelection(EventSource.EXPLORER).getFirstNode();

    MassOppsWizard wizard = new MassOppsWizard(root, objs);
    wizard.setForcePreviousAndNextButtons(true);
    wizard.setHelpAvailable(false);

    try {
      File file = new File(MassOppsWizard.FILE_NAME);
      if (file.exists() && file.canRead()) {
        dialogSettings.load(MassOppsWizard.FILE_NAME);
      }

      wizard.setDialogSettings(dialogSettings);

      WizardDialog wizardDialog = new WizardDialog(shell, wizard);
      WizardDialog.setDialogHelpAvailable(false);
      if (wizardDialog.open() == Window.OK) {
        RLogger.logDebug("OK pressed");
      } else {
        RLogger.logDebug("Cancel pressed");
      }
    } catch (Exception e) {
      RLogger.logError("File Exception in DialogSettings: " + e);
      RLogger.logError(e);
    }
    return true;
  }

  @Override
  public boolean isEnabled() {
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (nodes == null) {
      return false;
    }
    if(nodes.fullSelection.size()!=1)
      return false;
    TreeNode node = nodes.getFirstNode();
    TreeDB db = node.getTreeDB();
    TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
    return tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db));
  }
}
