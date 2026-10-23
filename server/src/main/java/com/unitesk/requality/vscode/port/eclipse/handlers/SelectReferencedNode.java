package com.unitesk.requality.vscode.port.eclipse.handlers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.jface.action.ContributionItem;
import com.unitesk.requality.vscode.ui.jface.action.ToolBarManager;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.ToolItem;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.events.ITreeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.events.TreeNodeChangeEvent;
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.FSId;
import com.unitesk.requality.core.selection.IUIStateChangeListener;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.core.tree.IStateChangeHandler;
import com.unitesk.requality.core.tree.TreeState;
import com.unitesk.requality.vscode.port.eclipse.Activator;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;
import com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF;
import com.unitesk.requality.repo.RevisionInfo;

public class SelectReferencedNode extends AbstractHandler implements ITreeChangeListener, IStateChangeHandler {
//  private TreeDB oldDb = null;
//  private Map<TreeDB, TreeNode> refMap = new HashMap<>(),
//      selMap = new HashMap<>();
  private Map<String, UUID> selectedNodes = new HashMap<>();
  private static TreeState[] READY_STATES = new TreeState[] {TreeState.READY};
  private  RequalityCNF view;//TODO from event?
  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    final TreeNode node = SelectionManager.getFirstNode(
        SelectionManager.getOne().getSelection(EventSource.EXPLORER));
    if(node == null)
      return null;
    final TreeNode
        old = node.getTreeDB().getLinkResolver().getRefTarget();
    selectedNodes.put(node.getTreeDB().getLocalName(), node.getUUId());
    node.getTreeDB().getLinkResolver().setRefTarget(node);

    updateButtonLabel(node);
    view = 
            (RequalityCNF)
            PlatformUI.getWorkbench()
                .getActiveWorkbenchWindow()
                .getActivePage()
                .findView(RequalityCNF.ID);

    EclipseAPI.getDisplay()
        .syncExec(
            new Runnable() {

              @Override
              public void run() {
                if (view != null && !view.getCommonViewer().getControl().isDisposed()) {
                  view.refresh(old);
                  view.refresh(node);
                  view.getNavigatorActionService().updateActionBars();
                }
              }
            });
    SelectionManager.getOne().addSourceListener(EventSource.EXPLORER, null, list);
    ResManager.getManager().getTracker().getTreeStateManager().addStateHook("*", READY_STATES, this);
    return null;
  }
  

  private IUIStateChangeListener list = new IUIStateChangeListener() {
    @Override
    public void newActiveNodes(FSId source, SelectionRecord nodes) {
      TreeNode node = nodes.getFirstNode();
      if (node == null) return;
      String lname = node.getTreeDB().getLocalName();
      UUID oldRTarg = selectedNodes.get(lname);
      TreeNode ntarg = node.getTreeDB().getLinkResolver().getRefTarget();
      UUID newRTarg = ntarg == null?null:ntarg.getUUId();
      
      if(newRTarg!=null)
        selectedNodes.put(lname, newRTarg);
      else
        selectedNodes.remove(lname, newRTarg);
      if(!node.getTreeDB().getGUIListeners().contains(SelectReferencedNode.this))
        node.getTreeDB().addGUIListener(SelectReferencedNode.this);
      updateButtonLabel(node);
      final TreeNode fnode = node, foldNode = oldRTarg == null?null:node.getTreeDB().getNode(oldRTarg);
      if (oldRTarg==null && newRTarg!=null || oldRTarg!=null && !oldRTarg.equals(newRTarg)) {
        if(newRTarg!=null || oldRTarg!=null){
          EclipseAPI.getDisplay()
              .syncExec(
                  new Runnable() {
                    @Override
                    public void run() {
                      if (view != null && !view.getCommonViewer().getControl().isDisposed()) {
                        if(fnode!=null)
                          view.refresh(fnode);
                        if(foldNode!=null)
                          view.refresh(foldNode);
                        view.getNavigatorActionService().updateActionBars();
                      }
                    }
                  });
        }
      }
    }
  };

  @Override
  public void dispose() {
    super.dispose();
    SelectionManager.getOne().removeSourceListener(EventSource.EXPLORER, null, list);
    ResManager.getManager().getTracker().getTreeStateManager().removeStateHook("*", READY_STATES, this);
  }
  
  @Override
  public boolean isEnabled() {
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (nodes.fullSelection.size() != 1)
      return false;
    return true;
  }

  @Override
  public void changed(
      TreeNode node, ChangesSet<Attribute> params, Moment m, byte flag) {
    if(m == Moment.AFTER)
    //TODO filter by changes
      if (node.getUUId().equals(selectedNodes.get(node.getTreeDB().getLocalName()))) updateButtonLabel(node);
  }
  private Image oldImage = null;
  private void updateButtonLabel(TreeNode node) {
    Display d = EclipseAPI.getDisplay();
    if (d != null)
      d.syncExec(
          new Runnable() {
            @Override
            public void run() {
              if(d.isDisposed())
                return;
              if (view != null && !view.getCommonViewer().getControl().isDisposed()) {
                ToolBarManager manager =
                    (ToolBarManager) view.getViewSite().getActionBars().getToolBarManager();
                ToolItem ti = null; //((ToolItem)ci.getWidget());
                for (ToolItem iti : manager.getControl().getItems()) {
                  Object o = iti.getData();
                  if (o != null && o instanceof ContributionItem) {
                    if ("com.unitesk.requality.commands.RequilityCNFRefSel"
                        .equals(((ContributionItem) o).getId())) ti = iti;
                  }
                }
                Image ni = null;
                if (ti != null) {
                  int ref = 0;
                  if (node != null) ref = node.getTreeDB().getLinkResolver().getGlobalRefTargetState();
                  switch (ref) {
                    case 0:
                      {
                      if(oldImage!=null) oldImage.dispose();
                        ni = (Image)
                                Activator.getImageDescriptor("/icons/zerolink.png")
                                    .createResource(d);
                        oldImage = ti.getImage();
                      }
                      break;
                    case 1:
                      {
                      if(oldImage!=null) oldImage.dispose();
                        ni = (Image)
                                Activator.getImageDescriptor("/icons/onelink.png")
                                    .createResource(d);
                        oldImage = ti.getImage();
                      }
                      break;
                    case 2:
                      {
                      if(oldImage!=null) oldImage.dispose();
                        ni = (Image)
                                Activator.getImageDescriptor("/icons/multilink.png")
                                    .createResource(d);
                        oldImage = ti.getImage();
                      }
                      break;
                  }
                }
                if(ni!=null && ti!=null) {
                  ti.setImage(ni);
                  final Image fni = ni;
                  final ToolItem fti = ti;
                  ti.addDisposeListener(one -> {
                    fni.dispose();
                  });
                  oldImage = ni;
                }
                //((HandledContributionItem)ci).widget.update();
                //view.getNavigatorActionService().updateActionBars();
              }
            }
          });
  }

  @Override
  public void created(TreeNode node, byte flag) {}

  @Override
  public void beforeCreated(NodeDesc node, byte flag) {}
  
  @Override
  public void deleted(TreeNode nodeDesc, byte flag, Moment m) {}

  @Override
  public void moved(TreeNode node, TreeNode oldParent, TreeNode newParent, Moment m, byte flag) {}

  @SuppressWarnings("unchecked")
  @Override
  public void changed(List<TreeNodeChangeEvent> changes) {
    //TODO remade
    for (TreeNodeChangeEvent ce : changes) {
      switch (ce.getType()) {
        case CHANGE:
          if(ce.getMoment() == Moment.AFTER)
            changed(
              ce.getTreeNodeTarget(),
              (ChangesSet<Attribute>) ce.getArguments()[0],
              ce.getMoment(),
              ce.getFlag());
          break;
        default:
          break;
      }
    }
  }


  @Override
  public void childrenOrderChanged(TreeNode node, byte flag) {}

@Override
public void stateChanges(TreeDB db, String lname, String pname, RevisionInfo ri, TreeState oldState, TreeState state) {
	// TODO Auto-generated method stub
	
}

@Override
public boolean filterTreeDB(TreeDB db) {
	// TODO Auto-generated method stub
	return false;
}
}
