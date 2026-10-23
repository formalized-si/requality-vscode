package com.unitesk.requality.eclipse.handlers.location;

import java.util.List;
import java.util.Map;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.IElementUpdater;
import org.eclipse.ui.menus.UIElement;
import org.eclipse.ui.part.IPage;
import org.eclipse.ui.views.properties.PropertySheet;
import org.eclipse.ui.views.properties.tabbed.ISection;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetPage;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.storage.OFlag;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.eclipse.api.EclipseAPI;
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
