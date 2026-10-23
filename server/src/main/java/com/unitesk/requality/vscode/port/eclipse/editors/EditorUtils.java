package com.unitesk.requality.vscode.port.eclipse.editors;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;

import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IWorkspaceRoot;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.jface.viewers.IContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.TreePath;
import com.unitesk.requality.vscode.ui.workbench.IEditorPart;
import com.unitesk.requality.vscode.ui.workbench.IEditorReference;
import com.unitesk.requality.vscode.ui.workbench.IViewPart;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;
import com.unitesk.requality.vscode.ui.workbench.PartInitException;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.ide.IDE;
import com.unitesk.requality.vscode.ui.navigator.CommonNavigator;

import com.unitesk.requality.core.IFSIdProvider;
import com.unitesk.requality.core.lock.LockIsBusy;
import com.unitesk.requality.core.lock.LockType;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.FSId;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.document.ILinkedStateProvider;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker;
import com.unitesk.requality.vscode.port.eclipse.editors.browser.ReqMarker;
import com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF;
import com.unitesk.requality.vscode.port.eclipse.ui.editors.IEditorWithId;
import com.unitesk.requality.vscode.port.eclipse.ui.editors.ModuleEditor;
import com.unitesk.requality.vscode.port.eclipse.ui.editors.ReportViewer;
import com.unitesk.requality.vscode.port.eclipse.ui.editors.UniEditor;
import com.unitesk.requality.vscode.port.eclipse.views.RequalityContentProvider;
import com.unitesk.requality.nodetype.Comment;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.ReportData;
import com.unitesk.requality.nodetype.TestPurpose;

public class EditorUtils {
  public static ILinkedStateProvider LINKED_PROVIDER = new ILinkedStateProvider() {
    
    @Override
    public boolean isLinked() {
      IViewPart view = RequalityCNF.activeNavigator;
      if (view instanceof CommonNavigator) {
        CommonNavigator navigator = (CommonNavigator) view;
        return navigator.isLinkingEnabled();
      }
      return false;
    }
  };

  public static Object formPathOrElement(IContentProvider cp, Object input, TreeNode parentElement) {
    if(cp instanceof RequalityContentProvider){
      RequalityContentProvider rcp = (RequalityContentProvider) cp;
      int i = 2;
      TreeNode node = parentElement;
      LinkedList<TreeNode> path = new LinkedList<>();
      while(node!=null)
      {
        path.addFirst(node);
        i++;
        node = node.getWorkParent();
      }
      int n = i;
      Object[]ret = new Object[i];
      Object root = input;
      ret[0] = root;
      TreeDB db = parentElement.getTreeDB();
      
      if (root instanceof TreeNode) {
        TreeNode nroot = (TreeNode) root;
        Iterator<TreeNode> itPath = path.iterator();
        boolean found = false;
        while(itPath.hasNext()){
          TreeNode nde = itPath.next();
          if(nde.getSUUId().equals(nroot.getSUUId())){
            found = true;
            break;
          }
        }
        if(!found)
          return parentElement;
        i = 1;
        while(itPath.hasNext()) 
          ret[i++] = itPath.next();
        if(i<n)
          ret = Arrays.copyOf(ret, i);
        return ret;
      } else if (root instanceof IWorkspaceRoot) {
        ret[1] = ResManager.getManager().getTracker().getProjectEntity(db.getLocalName(), db.getProjectName(), true);

        Iterator<TreeNode> itPath = path.iterator();
        i = 2;
        while(itPath.hasNext())
          ret[i++] = itPath.next();
        return ret;
//      } else if (newInput instanceof Object[]) {//TODO exit?
//        dbs.add(((TreeNode) ((Object[]) newInput)[0]).getTreeDB());
      } else if (root instanceof TreeDB) {
        Iterator<TreeNode> itPath = path.iterator();
        i = 1;
        while(itPath.hasNext())
          ret[i++] = itPath.next();
        if(i<n)
          ret = Arrays.copyOf(ret, i);
        return ret;
      } else {
        //TODO?
      }
    }else {
      if(cp instanceof ITreeContentProvider) {
        ITreeContentProvider tcp = (ITreeContentProvider) cp;
        Object par = tcp.getParent(parentElement), node = parentElement;
        LinkedList<Object> ret = new LinkedList<>();
        while(par!=null) {
          ret.addFirst(node);
          node = par;
          par = tcp.getParent(node);
        }
        return new TreePath(ret.toArray());
      }
    }
    return parentElement;
  }

  public static String detectEditorId(Object node) {
    String ret = ModuleEditor.ID;
    if(node != null)
    {
      if(node instanceof TreeNode){
        TreeNode tn = (TreeNode) node;
        String type = tn.getType();
        switch (type) {
          case Document.TYPE_NAME:
          case Location.TYPE_NAME:
            ret = ReqMarker.ID;
            break;
          case Comment.TYPE_NAME:
          case TestPurpose.TYPE_NAME:
            ret = UniEditor.ID;
            break;
          case ReportData.TYPE_NAME:
            ret = ReportViewer.ID;
            break;
          default:
            ret = UniEditor.ID;//testdb-related editors may be here
            break;
        }
      }
    }
    return ret;
  }
  public static IEditorPart openEditor(TreeNode node) {
    return openEditor(node, null, null, null);
  }
  public static IEditorPart openEditor(TreeNode node, TreeNode sel, String editorId, FSId fsid) {
    String type = node.getType();
    //String editorid = Eclipse;
    //NodeEditorInput input = new NodeEditorInput(node);
    if (type.equals(TestPurpose.TYPE_NAME)
        || type.equals(Comment.TYPE_NAME)
        || type.equals("TestStep")
        || type.equals(Location.TYPE_NAME)) {
      sel = node;
      node = node.getWorkParent();
    }
    if(editorId == null)
      editorId = detectEditorId(node);;
    try {
      NodeEditorInput input = null;
      IEditorReference eref = null;
      input = new NodeEditorInput(node, sel, editorId, fsid);
      try {
        eref =
          EditorUtils.findEditorForNode(
            node.getQualifiedId(),
            fsid == null ? EventSource.SYSTEM : fsid.getEventSource(), editorId, EclipseTracker.getResourceByNode(node, LockType.LOCK_WAIT).getProject());
      } catch (LockIsBusy e) {
        RLogger.logError(e);
      }
      IEditorPart part;
      if (eref != null) 
        part = eref.getEditor(true);
      else {
        IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
        part = IDE.openEditor(page, input, editorId, false);
      }
      if (editorId == UniEditor.ID) 
        ((UniEditor) part).setSelectedNode(null, sel);
      part.getSite().getPage().bringToTop(part);
//      if(node instanceof Requirement)
//        docservice.updateTargetRequirement(time, (Requirement)node);
      return part;
      // IDE.openEditor(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(), input,
      // editorid, false);
    } catch (PartInitException e) {
      e.printStackTrace();
    } catch (CoreException e) {
      e.printStackTrace();
    }
    return null;
  }
  public static IEditorReference findEditorForNode(String nodeQId, EventSource editorId, String id, IProject project)
      throws PartInitException, CoreException {
    IEditorReference[] eRefs =
        PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getEditorReferences();
    for (IEditorReference eRef : eRefs) {
      IEditorPart jsEditor = eRef.getEditor(true);
      //TODO disposed check
      if(jsEditor instanceof IFSIdProvider && jsEditor instanceof IEditorWithId) {
        String jeditorId = ((IEditorWithId)jsEditor).getEditorKindId();
        EventSource src = ((IFSIdProvider)jsEditor).getFSId().getEventSource();
        if(editorId!=null && editorId.equals(src) &&
           id.equals(jeditorId)) {
          if (!(jsEditor.getEditorInput() instanceof NodeEditorInput)) 
            continue;
          NodeEditorInput editorInput = (NodeEditorInput) jsEditor.getEditorInput();
          if (editorInput.getStorage().getNode().getQualifiedId().equals(nodeQId)) {
            return eRef;
          }
        }
      }
    }
    return null;
  }
}
