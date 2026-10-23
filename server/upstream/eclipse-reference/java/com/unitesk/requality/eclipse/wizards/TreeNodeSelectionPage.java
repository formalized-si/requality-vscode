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

package com.unitesk.requality.eclipse.wizards;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.dialogs.DialogSettings;
import org.eclipse.jface.dialogs.IDialogPage;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.model.WorkbenchLabelProvider;

import com.unitesk.requality.core.filter.ITreeNodeFilter;
import com.unitesk.requality.core.instancer.Instantiator;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.IDMechanics;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.eclipse.core.EclipseTracker;
import com.unitesk.requality.eclipse.editors.EditorUtils;
import com.unitesk.requality.eclipse.ui.cnf.RequalitySorter;
import com.unitesk.requality.eclipse.views.RequalityContentProvider;
import com.unitesk.requality.eclipse.wizards.massops.MassOppsWizard;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

public class TreeNodeSelectionPage extends WizardPage {
  private List<String> filters = null;
  private ISelectionChangedListener listener;

  public void setNodeTypesFilters(String[] f) {
    filters = new ArrayList<>();
    for (String filt : f) filters.add(filt);
  }

  private static Map<String, String> fids =
        new HashMap<>();
  protected TreeDB db;
  private Text containerText;
  private TreeViewer nodeTree = null;
  private boolean allowMulti = false;
  private TreeNodeIDMode mode;
  private NodeSelectionListener nsl;
  public static final String ROOT_ID = "Root id";

  /**
   * Constructor for SampleNewWizardPage.
   *
   * @param multi
   * @param pageName
   */
  public TreeNodeSelectionPage(ISelection selection, TreeNodeIDMode mode) {
    this(selection, "/", SWT.SINGLE, "/Requirements", mode);//TODO not only requirements
  }


  private void detectFIDs() {
    if(db == null)
      return;
    DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(
      db.getNode(Requirement.getTypeRootQId()), false, true, false);
    Iterator<TreeNode> it = dftw.iterator();
    while(it.hasNext()) {
      TreeNode n = it.next();
      String f = n.getExternalForeignID();
      if(f!=null)
          fids.put(f, n.getQualifiedId(true));
    }
  }
  
  public TreeNodeSelectionPage(ISelection selection, String root, int params, String string, TreeNodeIDMode mode) {
    super("reqElementSelection");
    this.mode = mode;
    rootQID = root;
    setTitle("Select Element");
    setDescription("Please select element from listed tree.");
    if ((params & SWT.MULTI) == SWT.MULTI) allowMulti = true;
    if (selection != null && !selection.isEmpty()) {
      Object o = ((StructuredSelection) selection).getFirstElement();
      if (o instanceof Object[]) {
        Object[] arr =(Object[])o;
        if(arr!=null && arr.length>0)
    	  o = arr[0];
      }
      if (o instanceof Collection) {
        Collection c = (Collection)o; 
      	if(c!=null && !c.isEmpty())
          o = c.iterator().next();
      }
      if (o instanceof TreeNode) db = ((TreeNode) o).getTreeDB();
      if (o instanceof TreeDB) db = (TreeDB) o;
      if (db != null && db.isVirtual()) db = Instantiator.getRootTree(db); //TODO look into
    }
    setPageComplete(false);
    setTargetQIDs(string, true, true);
    if(db!=null && mode == TreeNodeIDMode.ProjectSettings){
      if(db.getFullNameMode() == 3)//TODO more proper enums
        detectFIDs();
    }
  }

  protected ILabelProvider getLabelProvider() {
    return WorkbenchLabelProvider.getDecoratingWorkbenchLabelProvider();
  }
  boolean modEnabled = false;
  ModifyListener idModifier = new ModifyListener() {
    @Override
    public void modifyText(ModifyEvent e) {
      boolean dis = false;
      if(modEnabled) {
        containerText.removeModifyListener(this);
        modEnabled = false;
        dis = true;
      }
      try {
        String text = containerText.getText();
        setTargetQIDs(containerText.getText(), true, false);
        if(containerText.getText().isEmpty())
          containerText.setText(text);
      }finally {
        if(dis) {
          containerText.addModifyListener(this);
          modEnabled = true;
        }
      }
    }
  };

  /** @see IDialogPage#createControl(Composite) */
  public void createControl(Composite parent) {
    Composite container = new Composite(parent, SWT.NULL);
    GridLayout layout = new GridLayout();
    container.setLayout(layout);
    layout.numColumns = 2;
    layout.verticalSpacing = 9;
    Label label = new Label(container, SWT.NULL);
    label.setText("&Element id:");

    containerText = new Text(container, SWT.BORDER | SWT.SINGLE);
    if (selectedNodes != null) select(selectedNodes);
    GridData gd = new GridData(GridData.FILL_HORIZONTAL);
    containerText.setLayoutData(gd);
    
    containerText.addModifyListener(idModifier);
    modEnabled = true;

    gd = new GridData(GridData.FILL_BOTH);
    gd.horizontalSpan = 2;

    nodeTree = new TreeViewer(container, allowMulti ? SWT.MULTI : SWT.NONE);
    nodeTree.getControl().setLayoutData(gd);
    nodeTree.setLabelProvider(getLabelProvider());
    nodeTree.setComparator(new RequalitySorter());
    nodeTree.setAutoExpandLevel(2);
    nodeTree.setComparator(new RequalitySorter());
    if (db == null) {
      nodeTree.setContentProvider(getContentProvider());
      nodeTree.setInput(ResourcesPlugin.getWorkspace().getRoot());
    } else {
      nodeTree.setContentProvider(getContentProvider());
      nodeTree.setInput(
          db.getNode(
              getRootQId(),
              com.unitesk.requality.core.model.Path.QID | com.unitesk.requality.core.model.Path.UV));
    }
    nsl = new NodeSelectionListener();
    nodeTree.addSelectionChangedListener(nsl);

    nodeTree.getTree().addKeyListener(keyList);

    setPageComplete(false);
    if (targetQIDs != null) setTargetQIDs(CoreUtils.listToString(targetQIDs, "| ", true), true, true);

    containerText.setFocus();
    setControl(container);
  }

  public boolean checkCanSelectNode() {
    updateStatus(null);
    if (selectedNodes == null || selectedNodes.length == 0) {
      updateStatus("None selected");
      return false;
    }
    for (TreeNode node : selectedNodes)
      if (getRootQId().equals(node.getUserVisibleName(true))) {
        updateStatus("Root cannot be selected");
        return false;
      }
    return true;
  }

  private KeyListener keyList =
      new KeyListener() {
        public void keyPressed(KeyEvent e) {
          if (e.keyCode == ' ') {
            TreeItem[] items = nodeTree.getTree().getSelection();
            if (items.length > 0) {
              TreeItem item = items[0];
              if (item.getExpanded() == false) {
                item.setExpanded(true);
                Event e1 = new Event();
                e1.item = item;
                nodeTree.getTree().notifyListeners(SWT.Expand, e1);
              } else item.setExpanded(false);
            }
          }
        }

        public void keyReleased(KeyEvent e) {}
      };
  private String[] targetQIDs;
  private String rootQID = "/";
  private TreeNode[] selectedNodes;

  private final class NodeSelectionListener implements ISelectionChangedListener {
    @Override
    public void selectionChanged(SelectionChangedEvent event) {
      boolean enBack = false;
      nodeTree.removeSelectionChangedListener(this);
      try {
	      if(modEnabled) {
	        containerText.removeModifyListener(idModifier);
	        enBack = true;
	        modEnabled = false;
	      }
	      try {
	        TreeSelection ts = (TreeSelection) event.getSelection();
	        Object[] sel = ts.toArray();
	        if (sel == null) return;
	        if (!isSelectedSame(selectedNodes)) {
	          for (int i = 0; i < sel.length; i++) {
	            if (sel[i] instanceof TreeDB)
	              sel[i] =
	                  ((TreeDB) sel[i])
	                      .getNode(
	                          getRootQId(),
	                          com.unitesk.requality.core.model.Path.QID | com.unitesk.requality.core.model.Path.UV);
	            if (sel[i] instanceof IProject) {
	              IProject p = (IProject) sel[i];
	              sel[i] = null;
	              try {
	                  EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
	                sel[i] =
	                 (et
	                  .getTreeDB(p, null, et.getLocalName(p))
	                  .getNode(
	                      getRootQId(),
	                      com.unitesk.requality.core.model.Path.QID | com.unitesk.requality.core.model.Path.UV));
	              } catch (IOException e) {
	                RLogger.logError(e);
	              }
	            }
	          }
	          setPageComplete(true);
	          select(sel);
	          setTargetObjects(sel);
	          checkCanSelectNode();
	          if (getSelectedNodes() != null && getSelectedNodes().length > 0) {
	            DialogSettings dialogSettings = (DialogSettings) getDialogSettings();
	            if (dialogSettings != null) {
	              dialogSettings.put(ROOT_ID, getSelectedNodes()[0].getSUUId());
	              try {
	                dialogSettings.save(MassOppsWizard.FILE_NAME);
	              } catch (IOException e) {
	                RLogger.logInfo("DialogSettings saving Exception in TreeNodeSelectionPage\ns");
	              }
	            }
	          }
	          //updateUI();
	        }
	      }
	      finally {
	        if(enBack) {
	          containerText.addModifyListener(idModifier);
	          modEnabled = true;
	        }
	      }
      }finally {
        nodeTree.addSelectionChangedListener(this);
      }
    }
  }

  protected String getRootQId() {
    return rootQID;
  }

  public void select(Object[] obj) {
    if (obj != null) {
      selectedNodes = new TreeNode[obj.length];
      for (int i = 0; i < obj.length; i++) selectedNodes[i] = (TreeNode) obj[i];
      if (nodeTree != null && selectedNodes.length > 0 && !nodeTree.getControl().isDisposed()) {
        ISelection s = new StructuredSelection(obj);
        if (obj.length == 1) s = new StructuredSelection(obj[0]);
        nodeTree.expandToLevel(EditorUtils.formPathOrElement(nodeTree.getContentProvider(),nodeTree.getInput(), selectedNodes[0]), 0);
        nodeTree.setSelection(s, true);
      }
      String s = "";
      Integer alteredMode = null;
      if(mode == TreeNodeIDMode.QualifiedId)
        alteredMode = 1;
      if (selectedNodes.length > 0)
        s = "\"" + selectedNodes[0].getTreeDB().getFullName(selectedNodes[0], alteredMode, false) + "\"";
      for (int i = 1; i < selectedNodes.length; i++)
        s += "| " + "\"" + selectedNodes[0].getTreeDB().getFullName(selectedNodes[0], alteredMode, false) + "\"";
      containerText.setText(s);
    }
  }

  private boolean isSelectedSame(Object[] sel) {
    IStructuredSelection selection = (IStructuredSelection) nodeTree.getSelection();
    Object[] sels = selection.toArray();
    if (sels == null) return false;
    return Arrays.deepEquals(sels, sel);
  }

  /** Ensures that both text fields are set. */
  protected ITreeContentProvider getContentProvider() {
    RequalityContentProvider rcp = new RequalityContentProvider();
    rcp.addFilter(
        new ITreeNodeFilter() {
          @Override
          public boolean select(TreeNode node) {
            return filters == null || filters.contains(node.getType());
          }
        });
    return rcp;
  }

  protected void updateStatus(String message) {
    //Set node id nodeId to node.getQualifiedId()
    setErrorMessage(message);
    setPageComplete(message == null);
  }

  public String getContainerName() {
    return containerText.getText();
  }

  public IResource getModifiedResource() {
    return ResourcesPlugin.getWorkspace().getRoot().findMember(new Path(getContainerName()));
  }

  private void setTargetObjects(Object[] input) {
    String[] res = new String[input.length];
    int i = 0;
    for (Object o : input) {
      TreeNode node = (TreeNode)o;
      db = node.getTreeDB();//initiate tree db
      res[i++] = 
          mode == mode.ProjectSettings?
              IDMechanics.getNodeId(node):
              node.getTreeDB().getFullName(node, mode == TreeNodeIDMode.QualifiedId?1:null, false);
    }
    setTargetQIDs(res, true);
  }

  public void setTargetQIDs(String input, boolean inner) {
    setTargetQIDs(input, inner, true);
  }
  public void setTargetQIDs(String input, boolean inner, boolean changeText) {
    ISelectionChangedListener sel = null;
    if (inner) {
      sel = listener;
      listener = null;
    }
    if (input == null) targetQIDs = new String[0];
    else targetQIDs = input.split("\\| (?=(?:[^\']*[\'][^\']*[\'])*[^\']*$)");
    for (int i = 0; i < targetQIDs.length; i++) {
      String s = targetQIDs[i];
      if (s.startsWith("'") && s.endsWith("'") && s.length() > 1)
        s = s.substring(1, s.length() - 1);
      targetQIDs[i] = s;
    }
    setTargetQIDs(targetQIDs, changeText);
    if (inner) listener = sel;
  }

  private void setTargetQIDs(String[] inp, boolean chageText) {
    boolean enback = false;
	if(modEnabled) { 
      containerText.removeModifyListener(idModifier);
      enback = true;
      modEnabled = false;
	}     
    try {
      targetQIDs = inp;
      StringBuffer sb = new StringBuffer("");
      for (int i = 0; i < inp.length; i++) {
        if (!inp[i].startsWith("'")) sb.append("'");
        sb.append(inp[i]);
        if (!inp[i].endsWith("'")) sb.append("'");
        if (i < (inp.length - 1)) sb.append(",");
      }
      String input = sb.toString();
      if (chageText && containerText != null) containerText.setText(input == null ? "" : input);
      List<Object> sel = new ArrayList<>();
      for (String targetqid : targetQIDs) {
        if (targetqid != null && targetqid.trim().equals("")) targetqid = null;
        if (targetqid != null && targetqid.startsWith("'"))
          targetqid = targetqid.substring(1, targetqid.length());
        if (targetqid != null && targetqid.endsWith("'"))
          targetqid = targetqid.substring(0, targetqid.length() - 1);
        TreeNode node = null;
        if (db != null)
          try {
            node =
                findNode(targetqid);
          } catch (Exception e) {
            RLogger.logError(e);
          }
        if (node != null) sel.add(node);
        if (nodeTree != null) {
          if (listener != null)
            if (node != null)
              listener.selectionChanged(
                  new SelectionChangedEvent(nodeTree, new StructuredSelection(node)));
            else
              listener.selectionChanged(
                  new SelectionChangedEvent(nodeTree, new StructuredSelection()));
        }
      }
      if (nodeTree != null) {
        if (sel.size() != 1) nodeTree.setSelection(new StructuredSelection(sel), true);
        else nodeTree.setSelection(new StructuredSelection(sel.get(0)), true);
        updateStatus(null);
      }
      setPageComplete(false);
      if (sel.size() > 0) setPageComplete(true);
      else updateStatus("No elements were selected");
    }finally {
      if(enback) {
        containerText.addModifyListener(idModifier);
        modEnabled = true;
      }
    }
  }

  private TreeNode findNode(String targ) {
    if(fids!=null && fids.containsKey(targ))
      targ = fids.get(targ);

    return db.getNode(
        targ,
        com.unitesk.requality.core.model.Path.QID | com.unitesk.requality.core.model.Path.UV);
  }


  public void setChangeListener(ISelectionChangedListener ncl) {
    listener = ncl;
  }

  public String[] getTargetQIDs() {
    if (targetQIDs == null) return null;
    String[] targs = new String[targetQIDs.length];
    for (int i = 0; i < targs.length; i++) {
      targs[i] = targetQIDs[i];
      if (targs[i].startsWith("'")) targs[i] = targs[i].substring(1, targs[i].length());
      if (targs[i].endsWith("'")) targs[i] = targs[i].substring(0, targs[i].length() - 1);
    }
    return targs;
  }

  public TreeNode[] getSelectedNodes() {
    List<TreeNode> nodes = new ArrayList<>();
    if (nodeTree != null) {
      StructuredSelection sel = (StructuredSelection) nodeTree.getSelection();
      for (Object o : sel.toArray()) {
        if (o instanceof TreeDB)
          nodes.add(
              ((TreeDB) o)
                  .getNode(
                      getRootQId(),
                      com.unitesk.requality.core.model.Path.QID | com.unitesk.requality.core.model.Path.UV));
        if (o instanceof IProject) {
          IProject p = (IProject) o;
          try {
            EclipseTracker et = EclipseTracker.getTracker(DBNamesManager.DEFAULT);
            TreeNode node = et
                .getTreeDB(p, null, et.getLocalName(p))
                .getNode(
                    getRootQId(),
                    com.unitesk.requality.core.model.Path.QID | com.unitesk.requality.core.model.Path.UV);
            if(node!=null)
              nodes.add(node);
          } catch (IOException e) {
            // TODO Auto-generated catch block
            RLogger.logError(e);
          }
        }
        if (o instanceof TreeNode) nodes.add((TreeNode) o);
      }
    }
    return nodes.toArray(new TreeNode[0]);
  }

  public void updateSettings() {
    //TODO update settings
  }

  public void setSelection(String[] split) {
    setTargetQIDs(split, true);
    //new StructuredSelection();
  }
}
