/*
 * Copyright 2009-2026 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a cop
import com.unitesk.requality.vscode.port.eclipse.views.RequalityContentProvider;
y of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.unitesk.requality.vscode.port.eclipse.ui.dialogs;

import java.io.IOException;
import java.util.HashSet;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.WorkspaceJob;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.ILabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionChangedListener;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.SelectionChangedEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.ISharedImages;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.model.WorkbenchLabelProvider;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.events.DefaultTreeChangeListener;
import com.unitesk.requality.core.events.ITreeChangeListener;
import com.unitesk.requality.core.filter.ITreeNodeFilter;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.tree.ITreeLogic.PastingIntoLeafException;
import com.unitesk.requality.core.tree.ITreeLogic.ProjectNotLoadedException;
import com.unitesk.requality.core.tree.TreeLogic;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker;
import com.unitesk.requality.vscode.port.eclipse.editors.EditorUtils;
import com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalitySorter;
import com.unitesk.requality.vscode.port.eclipse.views.RequalityContentProvider;
import com.unitesk.requality.nodetype.TestPurpose;
import com.unitesk.requality.nodetype.VirtualNode;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.cli.RequalityCLI;

public abstract class SelectNodeDialog extends TreeViewerDialog<TreeNode> {
  public static final String NAME = "SelectNodeDialog";
  private HashSet<TreeNode> newNodes = new HashSet<>();

  private final class NodeSelectionListener implements ISelectionChangedListener {
    @Override
    public void selectionChanged(SelectionChangedEvent event) {
      TreeSelection ts = (TreeSelection) event.getSelection();
      if (!isSelectedSame(selectedNode)) {
        Object o = ts.getFirstElement(); 
        if (o instanceof IProject) {
          IProject p = (IProject)o;
          try {
            EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
			select(et.getTreeDB(p, null, et.getLocalName(p)).getRootNode());
		  } catch (IOException e) {
			RLogger.logError(e);
		  }
        }else
          if (ts.getFirstElement() instanceof TreeNode) {
            select(ts.getFirstElement());
          } else if (ts.getFirstElement() instanceof TreeDB) {
            TreeDB db = (TreeDB) ts.getFirstElement();
            select(db.getNode(getRootQId(), Path.QID | Path.UV));
          }
        updateUI();
      }
    }
  }

  private ITreeChangeListener catalogChangeListener =
      new DefaultTreeChangeListener() {

        private boolean dirty = false;

        @Override
        public void changed(TreeNode node, ChangesSet<Attribute> what) {
          callChanged(node);
        }

        private void callChanged(TreeNode node) {
          if (dirty) {
            return;
          }
          dirty = true;
          Shell shell = getShell();
          if (shell == null) return;

          Display display = shell.getDisplay();
          if (display.isDisposed()) return;
          final TreeViewer nodeTree = SelectNodeDialog.this.nodeTree;
          display.asyncExec(
              new Runnable() {
                @Override
                public void run() {
                  if(nodeTree.getControl().isDisposed())
                    return;
                  refresh();
                  dirty = false;
                }
              });
        }

        @Override
        public void created(TreeNode node) {
          callChanged(node.getShownParent());
        }
      };

  private static final String TEXT_CREATE_REQUIREMENT = "Create New";


  private static int TREE_WIDTH = 250;
  private static int TREE_HEIGHT = 300;

  public String getTitle() {
    return "Element selection";
  }

  protected TreeDB db = null;

  private boolean canSelectRoot = false;

  protected String newNodeType = null;
  protected boolean ignoretrans = false;

  protected String childtype = null;

  public void setTypeOfCreatedChilden(String t) {
    childtype = t;
  }

  private final SelectionListener createButtonListener =
      new SelectionListener() {
        @Override
        public void widgetSelected(SelectionEvent e) {
          TreeLogic l = selectedNode.getTreeDB().getTreeLogic();
          try {
            if ((childtype != null && !l.canBeCreatedInExc(childtype, selectedNode))
                || (childtype == null
                    && !l.canBeCreatedInExc(selectedNode.getType(), selectedNode))) {
              MessageDialog.openError(
                  PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                  "Create Element",
                  "Element of type '" + (childtype != null
                      ? CoreUtils.splitWords(childtype)
                      : selectedNode.getTypeLabel())
                          + "' cannot be created in "
                          + selectedNode.getUserVisibleName(true)
                          + "("
                          + selectedNode.getTypeLabel()
                          + ")");
              return;
            }
          } catch (PastingIntoLeafException e1) {
            String type = (childtype != null ? childtype : selectedNode.getType());
            MessageDialog.openError(
                PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                "Create Element",
                "Can't add "
                    + CoreUtils.splitWords(type)
                    + " to "
                    + selectedNode.getTypeLabel()
                    + " with "
                    + (type.equals(TestPurpose.TYPE_NAME) ? "non-" : "")
                    + "Test Purpose child");
            return;
          } catch (ProjectNotLoadedException e1) {
            MessageDialog.openError(
                PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                "Create Element",
                "Project "
                    + selectedNode.getTreeDB().getVisibleName()
                    + " is not loaded");
            return;
          }
          GetNodeIdDialog dialog =
              new GetNodeIdDialog(
                  SelectNodeDialog.this.getShell(),
                  selectedNode,
                  RequalityCLI.getNextId(
                      selectedNode,
                      newNodeType != null ? newNodeType : selectedNode.getType(),
                      defaultIdPrefix()));
          final boolean ignoretrans = SelectNodeDialog.this.ignoretrans;
          final TreeNode selectedNode = SelectNodeDialog.this.selectedNode;
          final String childtype = SelectNodeDialog.this.childtype;
          final HashSet<TreeNode> newNodes = SelectNodeDialog.this.newNodes;
          
          if ((dialog.open() == GetNodeIdDialog.OK) && (selectedNode.getTreeDB() != null)) {
            String id = dialog.getResult().trim();
            WorkspaceJob j = new WorkspaceJob("Create element on "+selectedNode.getTreeDB().getVisibleName()){

              @Override
              public IStatus runInWorkspace(IProgressMonitor monitor) {
                TreeNode newNode = null;
                boolean cont = ignoretrans; 
                boolean started = !cont && selectedNode.getTreeDB().startTransaction("Create Element", true);
                cont = started || cont;
                if (cont) {
                  try {
                    if (childtype != null) {
                      newNode = selectedNode.createChild(id, childtype, null);
                    } else {
                      newNode =
                          selectedNode.createChild(
                              id, selectedNode.getType(), null);
                    }
                    saveAction(newNode);
                    selectedNode.commitAttributes();
                    newNodes.add(newNode);
                    TreeNode fnewNode = newNode;
                    EclipseAPI.getDisplay().asyncExec(new Runnable(){
                    
                      @Override
                      public void run() {
                        if(SelectNodeDialog.this.getContents().isDisposed())
                          return;
                        select(fnewNode);
                      }
                    });
                    if(started) {
                      started = false;
                      selectedNode.getTreeDB().commit();
                    }
                  }finally {
                    if(started) {
                      started = false;
                      selectedNode.getTreeDB().rollback();
                    }
                  }
                  //    ReqTreeView.setSelected((TreeNode) selectedNode);
                }
                return Status.OK_STATUS;
              }
              
            };
            j.schedule();
          }
        }

        @Override
        public void widgetDefaultSelected(SelectionEvent e) {
          widgetDefaultSelected(e);
        }
      };

  protected String defaultIdPrefix() {
    return null;
  }

  protected void saveAction(TreeNode n) {};

  @Override
  public int open() {
    if(db!=null)
      db.addListener(catalogChangeListener); //GUI tends to be useless during single transaction
    return super.open();
  }

  @Override
  protected void buttonPressed(int buttonId) {
    super.buttonPressed(buttonId);
    if(db!=null)
      db.removeGUIListener(catalogChangeListener);
  }

  public boolean canSelectNode() {
    return canSelectNode(selectedNode);
  }

  public boolean canSelectNode(TreeNode selectedNode) {
    if (filter != null && !filter.select(selectedNode)) {
      return false;
    }
    if (enablementfilter != null && !enablementfilter.select(selectedNode)) return false;
    if (super.canSelectNode()) return true;
    if (canSelectRoot) {
      return true; // we already select something
    }
    boolean result = !getRootQId().equals(selectedNode.getQualifiedId());
    return result;
  }

  public SelectNodeDialog(Shell shell, TreeDB treedb, boolean ignoretrans) {
    super(shell);
    this.db = treedb;
    this.ignoretrans = ignoretrans;
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite container = createClientContainer(parent, 1);
    header = new Composite(container, SWT.NONE);
    header.setLayout(new GridLayout(2, false));
    Label commentLabel = new Label(header, SWT.NONE);
    String labelString = "Select an element";
    if (canCreateNodes()) {
      labelString += " or ";
      createButton = new Button(header, SWT.PUSH);
      createButton.setText(TEXT_CREATE_REQUIREMENT);
      createButton.addSelectionListener(createButtonListener);
    }
    commentLabel.setText(labelString);
    commentLabel.setLayoutData(new GridData(GridData.BEGINNING, GridData.CENTER, true, true));
    nodeTree = new TreeViewer(container);
    nodeTree.getControl().setLayoutData(new GridData(TREE_WIDTH, TREE_HEIGHT));
    nodeTree.setLabelProvider(getLabelProvider());
    nodeTree.setComparator(new RequalitySorter());
    nodeTree.setAutoExpandLevel(2);
    nodeTree.setComparator(new RequalitySorter());
    if (db == null) {
      nodeTree.setContentProvider(getContentProvider());
      nodeTree.setInput(ResourcesPlugin.getWorkspace().getRoot());
    } else {
      nodeTree.setContentProvider(getContentProvider());
      nodeTree.setInput(db.getNode(getRootQId(), Path.QID | Path.UV));
    }

    nodeTree.addSelectionChangedListener(new NodeSelectionListener());

    nodeTree.getTree().addMouseListener(mouseList);
    nodeTree.getTree().addKeyListener(keyList);

    this.getShell().setText(getTitle());
    this.getShell()
        .setImage(
            PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_ELEMENT));

    refresh();

    return container;
  }

  protected ILabelProvider getLabelProvider() {
    return WorkbenchLabelProvider.getDecoratingWorkbenchLabelProvider();
  }

  public boolean canCreateNodes() {
    return false;
  }

  RequalityContentProvider cnp = null;

  protected ITreeContentProvider getContentProvider() {
    if (cnp == null) cnp = new RequalityContentProvider(false);
    return cnp;
  }

  protected RequalityContentProvider getRequalityContentProvider() {
    ITreeContentProvider content = getContentProvider();
    if (content instanceof RequalityContentProvider) return (RequalityContentProvider) content;
    return null;
  }

  protected Composite createClientContainer(Composite parent, int span) {
    Composite container = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout();
    layout.marginWidth = layout.marginHeight = 10;
    layout.verticalSpacing = 2;
    layout.numColumns = span;
    container.setLayout(layout);
    return container;
  }

  public void setCanSelectRoot(boolean canSelectRoot) {
    this.canSelectRoot = canSelectRoot;
  }

  @Override
  public boolean canCreateNode() {
    return super.canCreateNode()
        && !selectedNode.isVirtual()
        && canSelectNode(selectedNode)
        && !VirtualNode.TYPE_NAME.equals(selectedNode.getType());
  };

  public void select(Object obj) {
    if (obj != null) {
      selectedNode = (TreeNode) obj;
      if (nodeTree != null && !nodeTree.getControl().isDisposed()) {
        if (createButton != null) createButton.setEnabled(canCreateNode());
        if(obj instanceof TreeNode){
          TreeNode nd = (TreeNode)obj;
          if(nd.isRoot())
          {
            IProject p = EclipseTracker.getTracker(DBNamesManager.DEFAULT).getProject(nd.getTreeDB());
              obj = p;
          }
        }
        if(obj instanceof TreeNode)
          nodeTree.expandToLevel(EditorUtils.formPathOrElement(nodeTree.getContentProvider(),nodeTree.getInput(), (TreeNode)obj), 0);
        else          
          nodeTree.expandToLevel(obj, 0);
        nodeTree.setSelection(new StructuredSelection(obj), true);
        updateUI();
      }
    }
  }

  private boolean isSelectedSame(TreeNode selectedReq) {
    IStructuredSelection selection = (IStructuredSelection) nodeTree.getSelection();
    Object o = selection.getFirstElement(); 
    if (o == null) return false;
    if (!o.equals(selectedReq)){
      if (o instanceof TreeDB) {
        return ((TreeDB)o).getRootNodeUUID().equals(selectedReq.getUUId()); 
      }else
        if(o instanceof IProject) {
          IProject p = (IProject)o;
          TreeDB db;
		  try {
		    EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
			db = et.getTreeDB(p, null, et.getLocalName(p));
			if(db!=null && selectedReq!=null)
              return db.getRootNodeUUID().equals(selectedReq.getUUId());
		  } catch (IOException e) {
			// TODO Auto-generated catch block
			RLogger.logError(e);
		  }
        }
      return false;
    }
    return true;
  }

  @Override
  protected void refresh() {
    if (nodeTree != null && !nodeTree.getControl().isDisposed()) {
      nodeTree.refresh();

      if (selectedNode != null) {
        // Because base can be reloaded
        selectedNode = selectedNode.getTreeDB().getNode(selectedNode.getUUId());
        if (!isSelectedSame(selectedNode)) {
          select(selectedNode);
        }
      }
    }
  }

  public boolean isNewNodeSelected() {
    return newNodes.contains(selectedNode);
  }

  private ITreeNodeFilter filter;
  private ITreeNodeFilter contentfilter;
  private ITreeNodeFilter enablementfilter;

  public void setContentFilter(ITreeNodeFilter fl) {
    RequalityContentProvider rcp = getRequalityContentProvider();
    if (contentfilter != null && rcp != null) rcp.removeFilter(contentfilter);
    contentfilter = fl;
    if (rcp != null) rcp.addFilter(contentfilter);
  }

  public void setFilter(ITreeNodeFilter f) {
    RequalityContentProvider rcp = getRequalityContentProvider();
    if (contentfilter == null && rcp != null && filter != null) rcp.removeFilter(filter);
    filter = f;
    if (rcp != null && contentfilter == null) rcp.addFilter(f);
  }

  public ITreeNodeFilter getEnablementFilter() {
    return enablementfilter;
  }

  public void setEnablementFilter(ITreeNodeFilter f) {
    //getRequalityContentProvider();
    enablementfilter = f;
  }
}
