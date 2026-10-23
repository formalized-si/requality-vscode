package com.unitesk.requality.vscode.port.eclipse.ui.cnf;

import com.unitesk.requality.vscode.port.eclipse.core.EclipseChecker;
import java.io.IOException;
import java.util.ArrayList;

import com.unitesk.requality.vscode.ui.resources.ICommand;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IProjectDescription;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.jface.action.ContributionItem;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.ITreeSelection;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;
import com.unitesk.requality.vscode.ui.swt.widgets.Menu;
import com.unitesk.requality.vscode.ui.swt.widgets.MenuItem;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchWindow;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker;
import com.unitesk.requality.vscode.port.eclipse.natures.ReqCatalogue;

public class CheckerCNFMenuBuilder extends ContributionItem {
  public CheckerCNFMenuBuilder() {
    // TODO Auto-generated constructor stub
  }

  public CheckerCNFMenuBuilder(String id) {
    super(id);
    // TODO Auto-generated constructor stub
  }

  @Override
  public void fill(Menu menu, int index) {
    Object obj = getSelectedObject();
    if (!(obj instanceof IProject)) {
      return;
    }
    final IProject proj = (IProject) obj;
    final boolean hasBuilder;
    boolean found = false;
    try {
      if (!(proj.isOpen() && proj.getNature(ReqCatalogue.ID) != null)) {
        return;
      }
      found = getCheckersEnabled(proj);
    } catch (CoreException e) {
      RLogger.logError(e);
      return;
    }
    MenuItem hasChecher = new MenuItem(menu, SWT.CHECK, index);
    hasChecher.setText("Enable Checkers");
    hasBuilder = found;
    hasChecher.setSelection(hasBuilder);
    hasChecher.addSelectionListener(
        new SelectionListener() {
          @Override
          public void widgetSelected(SelectionEvent e) {
            try {
              EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);  
              TreeDB db = null;
			  try {
				db = et.getTreeDB(proj, null, et.getLocalName(proj));
			  } catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			  }
              if(db!=null){
                et.fireDBSettingsChange(Moment.BEFORE, db);
                db.getCheckerContainer().addAllLoadedToQ();
              }
              IProjectDescription desc = proj.getDescription();
              ICommand[] commands = desc.getBuildSpec();

              if (!hasBuilder) {
                ICommand command = desc.newCommand();
                command.setBuilderName(EclipseChecker.BUILDER_ID);
                ICommand[] newCommands = new ICommand[commands.length + 1];
                System.arraycopy(commands, 0, newCommands, 1, commands.length);
                newCommands[0] = command;
                desc.setBuildSpec(newCommands);
                proj.setDescription(desc, null);
              } else {
                ArrayList<ICommand> newCommandsList = new ArrayList<>();
                for (ICommand cmd : commands) {
                  if (!cmd.getBuilderName().equals(EclipseChecker.BUILDER_ID)) {
                    newCommandsList.add(cmd);
                  }
                }
                desc.setBuildSpec(newCommandsList.toArray(new ICommand[0]));
                proj.setDescription(desc, null);
              }
              if(db!=null)
                et.fireDBSettingsChange(Moment.AFTER, db);
            } catch (CoreException e1) {
              e1.printStackTrace();
            }
          }

          @Override
          public void widgetDefaultSelected(SelectionEvent e) {}
        });
  }

  public static boolean getCheckersEnabled(IProject proj) throws CoreException {
    if(!proj.isOpen())
      return false;
    IProjectDescription desc = proj.getDescription();
    ICommand[] commands = desc.getBuildSpec();
    for (int i = 0; i < commands.length; ++i) {
      if (commands[i].getBuilderName().equals(EclipseChecker.BUILDER_ID)) {
        return true;
      }
    }
    return false;
  }

  private static Object getSelectedObject() {
    IWorkbenchWindow activeWorkbenchWindow = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
    if (activeWorkbenchWindow == null) {
      return null;
    }

    ISelection selection = activeWorkbenchWindow.getSelectionService().getSelection();

    if (!(selection instanceof ITreeSelection)) {
      return null;
    }
    ITreeSelection selected = (ITreeSelection) selection;

    if (selected.isEmpty()) {
      return null;
    }
    return selected.getFirstElement();
  }
}
