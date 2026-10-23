/*
 * Copyright 2015 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
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
package com.unitesk.requality.vscode.port.eclipse.ui.cnf;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.workbench.IEditorInput;
import com.unitesk.requality.vscode.ui.workbench.IEditorPart;
import com.unitesk.requality.vscode.ui.workbench.IEditorReference;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;
import com.unitesk.requality.vscode.ui.workbench.PartInitException;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.navigator.ILinkHelper;

import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.vscode.port.eclipse.RequalityPlugin;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker;
import com.unitesk.requality.vscode.port.eclipse.editors.NodeEditorInput;
import com.unitesk.requality.vscode.port.eclipse.editors.browser.ReqMarker;
import com.unitesk.requality.vscode.port.eclipse.ui.editors.JSEditor;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.CoreUtils;

public class CNFLinkHelper implements ILinkHelper {
 // private boolean ignoreCallForReqMarker = false;

  @Override
  public IStructuredSelection findSelection(IEditorInput anInput) {
    if (anInput instanceof NodeEditorInput) {
      NodeEditorInput input = (NodeEditorInput) anInput;
      try {
        TreeNode node = input.getSelectedNode();//change to selection for more proper link
        if(node == null)
          node = input.getStorage().getNode();
        // UniEditor
        if (node instanceof Requirement) {
          IEditorReference eref = null;
          try {
            eref =
                RequalityPlugin.findJSEditorForNode(
                    node.getQualifiedId(),
                    EclipseTracker.getTracker(DBNamesManager.DEFAULT).getProject(node.getTreeDB()));
          } catch (PartInitException e) {
            e.printStackTrace();
          }
          if (eref != null) {
            IEditorPart epart = eref.getEditor(true);
            if (epart instanceof JSEditor) {
              JSEditor jsEditor = (JSEditor) epart;
              if (jsEditor.getCurrentSelection() != null) {
                return new StructuredSelection(jsEditor.getCurrentSelection());
              }
            }
          }
        } else
        // ReqMarker
        if (node instanceof Document) {
          Document doc = (Document) node;
          IEditorReference eref = null;
          try {
            TreeDB db = doc.getTreeDB();
            if (db.isVirtual()) db = Instantiator.getRootTree(db);
            if (db != null) eref = RequalityPlugin.findMarkEditorForDocument(doc.getQualifiedId());
          } catch (PartInitException e) {
            e.printStackTrace();
          }
          if (eref != null) {
            IEditorPart epart = eref.getEditor(true);
            if (epart instanceof ReqMarker) {
              //TODO mb something move to selmanager
//              ReqMarker reqMarker = (ReqMarker) epart;
//              Location loc = reqMarker.getSelectedLocation();
//              if (loc == null) {
//                return null;
//              }
//              Requirement[] reqs = loc.getRequirements();
//              if (reqs.length > 0) {
//                List<TreeNode> tn = RequalityCNF.getSelectedNodes();
//                List<TreeNode> lreqs = new ArrayList<>();
//                lreqs.addAll(Arrays.asList(reqs));
//                if (tn != null && lreqs.removeAll(tn)) return null;
//                return new StructuredSelection(reqs[0]);
//              }
//              return new StructuredSelection();
            }
          }
        }
        return node!=null?new SelectionFromSource(node,input.getFSId()):StructuredSelection.EMPTY;
      } catch (CoreException e) {
        e.printStackTrace();
      }
    }
    //ignoreCallForReqMarker = false;
    return null;
  }

  @Override
  public void activateEditor(IWorkbenchPage aPage, IStructuredSelection selection) {

    if (!(selection.getFirstElement() instanceof TreeNode)) {
      return; // find editor only for tree nodes
    }
    long time = CoreUtils.getTime();
    TreeNode node = (TreeNode) selection.getFirstElement();
    TreeNode sel = null;
    if (selection instanceof NodeEditorInput
        && ((NodeEditorInput) selection).getData() instanceof TreeNode)
      sel = (TreeNode) ((NodeEditorInput) selection).getData();
    try {
      IEditorPart activeEditor =
          PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor();
      IEditorReference[] editorRefs = findRelevantEditors(node);
      IEditorPart curEditor = null;
      boolean findActiveEditor = false; // do not change active editor, if
      // it's listen selection change
      if(editorRefs.length == 0 && activeEditor!=null){
        if (activeEditor instanceof ReqMarker) {
//          ReqMarker marker = (ReqMarker) activeEditor;
//          SelectionManager.getOne().setActiveNodes(Collections.emptyList(), Collections.emptyList(),EventSource.OUTLINE);
//          marker.setTargetLocation(time, null, false);
        }else
          if (activeEditor instanceof JSEditor) {
            JSEditor jse = (JSEditor) activeEditor;
            //jse.setSelectedNode(null);
          }
      }else
      for (IEditorReference eRef : editorRefs) {
        curEditor = eRef.getEditor(false);
        if (curEditor == null) continue;
        if (activeEditor != null && activeEditor.equals(curEditor)) {
          findActiveEditor = true;
        }
        if (curEditor instanceof JSEditor) {
          //ignoreCallForReqMarker = true; //don't go to findSelection()!
          if (sel != null) node = sel;
          ((JSEditor) curEditor).setSelectedNode(null, node);
        }
      }
      if (curEditor != null && !findActiveEditor) {
        PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().bringToTop(curEditor);
      }
    } catch (CoreException e1) {
      e1.printStackTrace();
    }
  }

  private IEditorReference[] findRelevantEditors(TreeNode node) throws CoreException {
    List<IEditorReference> result = new ArrayList<>();
    IEditorReference[] eRefs =
        PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getEditorReferences();

    for (IEditorReference eRef : eRefs) {
      IEditorPart editor = eRef.getEditor(false);

      // ReqMarker
      if (editor instanceof ReqMarker) {
        ReqMarker marker = (ReqMarker) editor;

        if (node.getType().equals(Requirement.TYPE_NAME)) {
          Requirement req = (Requirement) node;
          List<Location> qids = req.getLocations(true);
          for (Location loc : qids) {
            if (loc != null
                && loc.getWorkParentUUID() != null)
              for(TreeNode doc:marker.getDocuments())
                if(loc.getWorkParentUUID().equals(doc.getUUId())) {
              result.add(eRef);
              break;
            }
          }
        }
        if (node.getType().equals(Location.TYPE_NAME)
            || node.getType().equals(Document.TYPE_NAME)) {
          // for document and locations
          for(TreeNode doc:marker.getDocuments()) {
            if(doc == null)
              continue;
            if (node.getQualifiedId().startsWith(doc.getQualifiedId())) {
              result.add(eRef);
            }
          }
        }
      }

      // JSEditor
      if (editor instanceof JSEditor) {
        JSEditor jsEditor = (JSEditor) editor;

        if (node.getQualifiedId()
            .startsWith(
                ((NodeEditorInput) jsEditor.getEditorInput())
                    .getStorage()
                    .getNode()
                    .getQualifiedId())) {
          result.add(eRef);
        }
      }
    }
    return result.toArray(new IEditorReference[0]);
  }
}
