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

package com.unitesk.requality.vscode.port.eclipse.editors.browser;

import java.util.concurrent.atomic.AtomicReference;
import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.core.tree.ITreeLogic.IdContainsWrongChars;
import com.unitesk.requality.core.tree.ITreeLogic.IdEmpty;
import com.unitesk.requality.core.tree.ITreeLogic.PastingIntoLeafException;
import com.unitesk.requality.core.tree.ITreeLogic.ProjectNotLoadedException;
import com.unitesk.requality.core.tree.ITreeLogic.SameIdExistsInChildren;
import com.unitesk.requality.core.tree.TreeLogic;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;
import com.unitesk.requality.vscode.port.eclipse.handlers.create.CreateRequirementHandler;
import com.unitesk.requality.vscode.port.eclipse.ui.dialogs.SelectNodeDialog;
import com.unitesk.requality.vscode.port.eclipse.ui.dialogs.SelectReqDialog;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.CoreUtils;

public class AddSelectionToRequirement extends Action {
  protected ReqMarker reqMarker;
  private Requirement target;
  private String nextId;
  private String newName;

  public AddSelectionToRequirement(ReqMarker reqMarker) {
    super("Add to Requirement...");
    this.reqMarker = reqMarker;
  }

  public AddSelectionToRequirement(ReqMarker reqMarker, Requirement target) {
    super("Add to Requirement: " + target.getNameOrId());
    this.reqMarker = reqMarker;
    this.target = target;
    //validateTarget(reqMarker.getSelectedDocument());
  }


  public AddSelectionToRequirement(ReqMarker reqMarker, Requirement target, String nextId) {
    super(
        "Create new Requirement: "
            + (!target.getQualifiedId().equals(Requirement.getTypeRootQId())
                ? target.getNameOrId() + "/"
                : "")
            + nextId);
    this.reqMarker = reqMarker;
    this.nextId = nextId;
    this.target = target;
  }

  public AddSelectionToRequirement(
      ReqMarker reqMarker, Requirement req, String id, String newName) {
    this(reqMarker, req, id);
    this.newName = newName;
    // TODO: fix hack
    String label =
        "Create new Requirement: "
            + (!target.getQualifiedId().equals(Requirement.getTypeRootQId())
                ? target.getNameOrId() + "/"
                : "")
            + newName;
    setText(label);
  }

  @Override
  public boolean isEnabled() {
    try {
//      boolean s = reqMarker.isSelectable();
//      boolean b = reqMarker.isRemovable();
//      Map<String, TreeNode[]> selectedRequirements =
//          SelectionManager.getOne().getSelection(EventSource.EXPLORER);
//      if(selectedRequirements==null)
//        selectedRequirements = new LinkedList<>();
//      if(selectedRequirements.size() == 0)
//        selectedRequirements.add(reqMarker.getTreeDB().getNode(Requirement.getTypeRootQId()));
//      if ((selectedRequirements.size() != 1)) {
//        return false;
//      }
//      TreeNode node = selectedRequirements.get(0);
//      if(node.getTreeDB().isBusy())
//        return false;
//      Location loc = reqMarker.getSelectedLocation();
//      if (loc != null
//          && 
//            (nextId == null
//             && target != null
//             && target.getLocation(loc.getQualifiedId()) != null
//             ))
//        return false;
//      return s || b;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
    return false;//outdated
  }

  @Override
  public void run() {
    long time = CoreUtils.getTime();
    Display cur = Display.findDisplay(Thread.currentThread());
    final ReqMarker reqMarker = this.reqMarker;
//    WorkspaceJob wj = new WorkspaceJob("Add selection to requirement "){
//    
//      @Override
//      public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
//        TreeDB db = reqMarker.getTreeDB();
//        //Multi-requirement locations support
//        boolean els = true;
//        String oid = null;
//        AtomicBoolean isRem = new AtomicBoolean(false),isSel = new AtomicBoolean(false);
//        cur.syncExec(new Runnable(){
//        
//          @Override
//          public void run() {
//            if(reqMarker.getBrowser().isDisposed())
//              return;
////            isRem.set(reqMarker.isRemovable());
////            isSel.set(reqMarker.isSelectable());
//          }
//        });
//        if (isRem.get()) {
//          //Location loc = reqMarker.getSelectedLocation();
//          oid = reqMarker.getSelectedLocationId();
////          if (loc != null) {
////            els = false;
////            if (db.startTransaction("Add Selection and Create Requirement", true)) {
////              Requirement targetReq = createTargetRequirement(loc.getQualifiedId(), true);
////              if (targetReq != null && targetReq.getLocation(loc.getQualifiedId()) == null) {
////                targetReq.addLocation(loc.getUUId());
////                targetReq.commitAttributes();
////              }
////              cur.syncExec(new Runnable(){
////                @Override
////                public void run() {
////                  if(reqMarker.getBrowser().isDisposed())
////                    return;
////                  reqMarker.fireSelectionChange(); 
////                }
////              });
////              db.commit();
////            }
////          }
//        }
//        if (els)
//          //Create new location
////          if (isSel.get() || oid != null) {
////            boolean rb = false;
////            if (db.startTransaction("Add Selection and Create Requirement", true)) {
////              cur.syncExec(new Runnable(){
////                @Override
////                public void run() {
////                  if(reqMarker.getBrowser().isDisposed())
////                    return;
////                  reqMarker.getBrowser().setEnabled(false); 
////                }
////              });
////              try {
////                Requirement targetReq = createTargetRequirement(null, true);
////                cur.syncExec(new Runnable(){
////                  @Override
////                  public void run() {
////                    if(reqMarker.getBrowser().isDisposed())
////                      return;
////                    reqMarker.getBrowser().setEnabled(true); 
////                  }
////                });
////                if (targetReq != null) {
////                  try {
////                    target = targetReq;
////                    Object[] jsData;
////                    jsData = reqMarker.addSelectionTo(cur, targetReq, oid);
////                    //if location is not added, then rollback
////                    if (jsData == null) {
////                      db.rollback();rb = true;
////                      return Status.CANCEL_STATUS;
////                    }
////                    db.commit(); // Add Selection to Requirement
////                    rb = true;
////                    cur.asyncExec(new Runnable(){
////                    
////                      @Override
////                      public void run() {
////                        if(reqMarker.getBrowser().getWebBrowser().isDisposed())
////                            return;
////                        reqMarker.setTargetLocation(time, (Location) jsData[1], true);
////                        reqMarker.fireSelectionChange(); 
////                      }
////                    });
////                  } catch (Exception e) {
////                    e.printStackTrace();
////                    if(!rb){if(db.inTransaction())db.rollback();rb = true;}
////                  }
////                } else if(!rb){db.rollback();rb = true;}
////              } catch (Exception e) {
////                RLogger.logError(e);
////                if(!rb){if(db.inTransaction())db.rollback();rb = true;}
////              }
////            }
////          }
//        return Status.OK_STATUS;
//      }
//    };
//    wj.schedule();
  }

  private Requirement createTargetRequirement(final String existingLocationId, boolean started) {
    Display d = Display.findDisplay(Thread.currentThread());
    if(d == null)
      d = EclipseAPI.getDisplay();
    if (target == null) {
     
      AtomicReference<Requirement> jo = new AtomicReference<>(null);
      d.syncExec(new Runnable() {
        
        @Override
        public void run() {
          if(reqMarker.getBrowser().isDisposed())
            return;
            SelectNodeDialog reqSelectDlg =
                      new SelectReqDialog(
                          PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                          reqMarker.getTreeDB(),
                          started) {
                        @Override
                        public boolean canSelect() {
                          if (!super.canSelect()) return false;
                          if (selectedNode == null || !selectedNode.getType().equals(Requirement.TYPE_NAME))
                            return false;
                          Requirement req = (Requirement) selectedNode;
                          if (existingLocationId != null && req.getLocation(existingLocationId) != null)
                            return false;
                          return true;
                        }
                      };

//          List<TreeNode> selectedRequirements =
//              SelectionManager.getOne().getSelection(EventSource.EXPLORER);
//          if(selectedRequirements==null)
//            selectedRequirements = new LinkedList<>();
//          if(selectedRequirements.size() == 0)
//            selectedRequirements.add(reqMarker.getTreeDB().getNode(Requirement.getTypeRootQId()));
//          if (selectedRequirements.get(0).equals(Requirement.TYPE_NAME)) {
//            reqSelectDlg.select(selectedRequirements.get(0));
//          }
          reqSelectDlg.setCanSelectRoot(false);
          if (reqSelectDlg.open() == SelectNodeDialog.OK) {
            if (reqSelectDlg.isNewNodeSelected()) {}
            // Activator.getDefault().setActiveNode(reqSelectDlg.getResult(),
            //       reqSelectDlg.getResult().getType());
            jo.set((Requirement) reqSelectDlg.getResult());
            return;
          }
          jo.set(null);
        }
      });
      return jo.get();
    }
    if (nextId != null) {
      //add check to possibility of adding
      //new Requirement to target
      try {
        TreeLogic l = target.getTreeDB().getTreeLogic();
        if (l.canBeChildOfExc(Requirement.TYPE_NAME, target)
            && l.isChildIdValidExc(target, nextId)) {
          boolean st = false;
          if (!started)
            st = target.getTreeDB().startTransaction("Create Requirement");
          try {
            CreateRequirementHandler crh = new CreateRequirementHandler();
            Requirement req = crh.execute(true, target,nextId, null, false, Requirement.TYPE_NAME);
            
            //Requirement req = (Requirement) target.createChild(nextId, Requirement.TYPE_NAME, null);
            if (newName != null) {
              req.setName(newName);
              req.commitAttributes();
            }
            if (st) {
              target.getTreeDB().commit();
              st = false;
            }
            return req;
          }
          finally {
            if(st)
              target.getTreeDB().rollback();
          }
        } else {
          d.asyncExec(new Runnable() {
            
            @Override
            public void run() {
                MessageDialog.openError(
                          PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                          "Add Element",
                          "Element of type '"
                              + Requirement.TYPE_NAME
                              + "' cannot be created in "
                              + target.getUserVisibleName(true));
            }
        });
          
          return null;
        }
      } catch (PastingIntoLeafException e) {
        d.asyncExec(new Runnable() {
            
            @Override
            public void run() {
              MessageDialog.openError(
                PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                "Add Element",
                "Can't add "
                    + Requirement.TYPE_NAME
                    + " to "
                    + target.getTypeLabel()
                    + " with "
                    + "Test Purpose child");
            }
        });
        return null;
      } catch (IllegalStateException e) {
        d.asyncExec(new Runnable() {
          @Override
          public void run() {
            MessageDialog.openError(
              PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
              "Add Element",
              "Some actions are already performing");
          }
        });
        return null;
      } catch (SameIdExistsInChildren e) {
        d.asyncExec(new Runnable() {
          @Override
          public void run() {
            
            MessageDialog.openError(
                PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                "Add Element",
                "Element with same id exists in " + target.getUserVisibleName(true));
          }
        });
        return null;
      } catch (IdEmpty e) {
        d.asyncExec(new Runnable() {
        @Override
        public void run() {  
          MessageDialog.openError(
            PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
            "Add Element",
            "Element id is empty");
        }
        });
        return null;
      } catch (IdContainsWrongChars e) {
        d.asyncExec(new Runnable() {
        @Override
        public void run() {
          MessageDialog.openError(
            PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
            "Add Element",
            "Element id contains wrong character - " + e.getWrongCharacter());
        }
        });
        return null;
      } catch (ProjectNotLoadedException e) {
        d.asyncExec(new Runnable() {
        @Override
        public void run() {
          MessageDialog.openError(
            PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
            "Add Element",
            "Project is not loaded");
        }
        });
        return null;
      }
    }
    return target;
  }
}
