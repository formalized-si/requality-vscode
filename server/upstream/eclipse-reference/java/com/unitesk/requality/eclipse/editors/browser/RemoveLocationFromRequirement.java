/*
 * Copyright 2009-2026 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.unitesk.requality.eclipse.editors.browser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.FSId;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.storage.OFlag;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.eclipse.handlers.requirement.DeleteRequirementHandler;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.Requirement;

final class RemoveLocationFromRequirement extends Action {
  private final TreeDB db;
  private final FSId source;
//TODO move to js
  RemoveLocationFromRequirement(TreeDB tree, FSId source) {
    super("Remove from Requirements");
    this.source = source;
    this.db = tree;
  }

  public boolean isEnabled() {
    try {
      SelectionRecord nds = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
      TreeDB db = null;
      TreeStateManager tsm = null;
      Collection<TreeNode> locs = nds.get(Location.TYPE_NAME);
      if(nds!=null && locs!=null && locs.size()>0)
      {
         Location l = (Location) locs.iterator().next();
         if(l!=null && l.getTreeDB()!=db) {
           db = l.getTreeDB();
           if(tsm ==null)
             tsm = TreeStateManager.getOne(db.getTracker());
           return tsm.isFullReady(l.getTreeDB().getLocalName(),
        		   tsm.getRevisionInfo(l.getTreeDB()));
         }
      }
      return true;
    } catch (Exception e) {
      RLogger.logError(e);
      return false;
    }
  }

  @Override
  public void run() { 
    Display d = Display.findDisplay(Thread.currentThread());
    final FSId s = this.source;
    if(s == null)
      return;
    WorkspaceJob wj = new WorkspaceJob("Remove location"){

      @Override
      public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {       
        SelectionRecord nds = SelectionManager.getOne().getSelection(s);
        Collection<TreeNode> locs = nds.get(Location.TYPE_NAME);
        Location loc = null;//TODO move code to core 
        if(nds!=null&& locs!=null && locs.size()>0)
          loc = (Location) locs.iterator().next();
        if(loc == null) {
          RLogger.logDebug("Location not found after removeLocation call");
          return Status.CANCEL_STATUS;
        }
        //Location loc = (Location) reqMarker.getSelectedDocument().findSingleWorkChildPlusName(id);
        Requirement[] req4check = null;
        if (loc != null) 
          req4check = loc.getRequirements();
        AtomicBoolean dia = new AtomicBoolean(false);
        d.syncExec(new Runnable(){
        
          @Override
          public void run() {
            if (!MessageDialog.openConfirm(
                PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                "Delete Location",
                "Delete selected Location?")) {
              dia.set(true);
            }
                        
          }
        });
        if(dia.get())
          return Status.CANCEL_STATUS;
        boolean started = db.startTransaction("Delete Location", true); 
        if (!started) {
          d.asyncExec(new Runnable(){
          
            @Override
            public void run() {
              MessageDialog.openError(
              PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
              "Delete Location Error",
              "Some actions are already performing");
            }
          });
          return Status.CANCEL_STATUS;
        }
        try {
          if (loc != null) 
            loc.delete();
          else{
            //TODO not supported in now model
          }
          //       try{
          //          if(req4check.length>0){
          //               CommonRemoveLocation.RemoveLocation(loc, Arrays.asList(req4check), reqMarker, true);
          //
          //          }
          //          else if(req4check.length==0)
          //              CommonRemoveLocation.RemoveLocation(loc, null, reqMarker, true,locQId,reqMarker.getDocument().getTreeDB());
          //          loc.getTreeDB().commit();
          //     }
          //  catch(Exception e){
          //      RLogger.logError(e);
          //      loc.getTreeDB().rollback();
          //  }

          if(started) {
            db.commit();
            started = false;
          }
        }finally {
          if(started)
            db.rollback();
        }
        TreeNode[] reqs = new TreeNode[req4check.length];
        int cnt = 0;
        String strList = "";
        if (req4check != null)
          for (Requirement req : req4check) {
            if (req.getLocations(false).size() == 0 && req.sizeWorkChildren() == 0) {
              reqs[cnt++] = req;
              if (!strList.equals("")) {
                strList += ", ";
              }
              strList += req.getNameOrId();
            }
          }
        if (cnt > 0) {
          //TODO message
          TreeDB db = reqs[0].getTreeDB();
          TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
          if (!tsm.canStartTransaction(db.getLocalName(),tsm.getRevisionInfo(db))) return Status.CANCEL_STATUS;
          dia.set(false);
          final String fstrList = strList;
          final int fnc = cnt;
          d.syncExec(new Runnable(){
          
            @Override
            public void run() {
              dia.set(MessageDialog.openQuestion(
              PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
              "Delete Requirement",
              (fnc == 1
                      ? "Requirement \"" + reqs[0].getUserVisibleName(true) + "\" has "
                      : "Requirements " + fstrList + " have ")
                  + "no locations, delete?"));
            }
          });
          if (dia.get()) {
            List<UUID> proc = new ArrayList<>();
            try {
              started = db.startTransaction("Delete Requirement" + (cnt > 1 ? "s" : ""), true);
              if (started) {
                try {
                  new DeleteRequirementHandler().process(reqs, proc, OFlag.NO_TRANSACTION);
                } catch (ExecutionException e) {
                  RLogger.logError(e);
                }
                finally {
                  if(started) {
                    started = false;
                    db.commit();
                  }
                }
              }
            }catch(Exception e) {
              if(started)
                db.rollback();
            }
          }
        }
        return Status.OK_STATUS;
      }
    };
    wj.schedule();
    
  }
}
