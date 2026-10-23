package com.unitesk.requality.vscode.port.eclipse.handlers.location;

import java.util.List;
import java.util.Map;
import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.runtime.WorkspaceJob;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPart;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.commands.IElementUpdater;
import com.unitesk.requality.vscode.ui.commands.UIElement;
import com.unitesk.requality.vscode.ui.workbench.IPage;
import com.unitesk.requality.vscode.ui.workbench.PropertySheet;
import com.unitesk.requality.vscode.ui.tabbed.ISection;
import com.unitesk.requality.vscode.ui.tabbed.TabbedPropertySheetPage;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.storage.OFlag;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.Requirement;

public class RemoveLocationFromRequirementHandler extends AbstractHandler
    implements IElementUpdater {

  private ILocationTab getTab() {
    IWorkbenchPart part =
        PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActivePart();
    if (part instanceof PropertySheet) {
      IPage page = ((PropertySheet) part).getCurrentPage();
      if (page instanceof TabbedPropertySheetPage) {
        if (((TabbedPropertySheetPage) page).getCurrentTab() == null) return null;
        ISection[] sections = ((TabbedPropertySheetPage) page).getCurrentTab().getSections();
        if (sections != null && sections.length == 1 && sections[0] instanceof ILocationTab) {
          return (ILocationTab) sections[0];
        }
      }
    }
    return null;
  }

  @SuppressWarnings("rawtypes")
  @Override
  public void updateElement(UIElement element, Map parameters) {
    ILocationTab tab = getTab();
    if (tab == null) {
      return;
    }
    Map<Location, List<Requirement>> map = tab.getLocationsAndRequirements();
    element.setText(
        "Delete Location"
            + ((map != null
                    && (map.keySet().size() > 1
                        || (map.keySet().size() > 0 && map.values().iterator().next().size() > 1)))
                ? "s"
                : ""));
  }

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    ILocationTab tab = getTab();
    if (tab == null) {
      return null;
    }
    Map<Location, List<Requirement>> map = tab.getLocationsAndRequirements();
    if (map == null || map.size() == 0) {
      return null;
    }
    //req == null - delete all links
    String s = map.size() > 1 ? "s" : "";
    if (!MessageDialog.openConfirm(
        PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
        "Delete Location" + s,
        "Delete selected Location" + s + "?")) {
      return null;
    }
    Location l = map.keySet().iterator().next();
    WorkspaceJob j = new WorkspaceJob("Delete locations"){

      @Override
      public IStatus runInWorkspace(IProgressMonitor monitor) {
        if(l == null)
          return Status.CANCEL_STATUS;
        boolean started = l.getTreeDB().startTransaction("Delete Location" + s, true);
        if (started) {
          try {
            CommonRemoveLocation.getOne().removeLocations(map, OFlag.NONE, true);
            if(started){
              l.getTreeDB().commit();
              started = false;
            }
          }finally{
            if(started)
              l.getTreeDB().rollback();
          }
        }
        EclipseAPI.getDisplay().asyncExec(new Runnable(){

          @Override
          public void run() {
            if(tab.getControl().isDisposed())
              return;
            tab.refreshLocations();
          }
          
        });
        return Status.OK_STATUS;
      }
      
    };
    j.schedule();
    return null;
  }

  @Override
  public boolean isEnabled() {
    ILocationTab tab = getTab();
    if (tab == null) return false;
    Map<Location, List<Requirement>> map = tab.getLocationsAndRequirements();
    if (map == null || map.size() == 0) {
      return false;
    }
    TreeDB db = null;
    TreeStateManager tsm = null;
    for(Location l:map.keySet()) {
      if(db == null || l.getTreeDB()!=db) {
        db = l.getTreeDB();
        if(tsm == null)
          tsm = TreeStateManager.getOne(db.getTracker());
        if(!tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db)))
          return false;
      }
    }
    return true;
  }
}
