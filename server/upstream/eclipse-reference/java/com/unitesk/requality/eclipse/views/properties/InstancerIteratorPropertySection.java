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
package com.unitesk.requality.eclipse.views.properties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.progress.UIJob;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetPage;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.events.INodeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.filter.ITreeNodeFilter;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.nodeiterator.RequalityChildIterator;
import com.unitesk.requality.core.nodeiterator.RequalityChildIterator.RequalityChildIteratorType;
import com.unitesk.requality.core.nodeiterator.ReuseRequalityChildIterator;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.eclipse.ui.dialogs.SelectNodeByTypesDialog;
import com.unitesk.requality.eclipse.ui.dialogs.SelectNodeDialog;
import com.unitesk.requality.nodetype.Comment;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TestPurpose;
import com.unitesk.requality.nodetype.VirtualNode;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.message.RMessages;

public class InstancerIteratorPropertySection extends TreeNodePropertiesTab {
  private VirtualNode instancer;
  private Composite parent;

  private static final String BUT_SEL = "Select...";
  private static String TITLE_SEL_BASE = "Select target node";
  private static final String BUT_CLEAR = "Clear...";
  private static String TITLE_CLEAR_BASE = "Clear target node";

  private Text target;
  private Font specFont;
  private Button selBaseButton, clearBaseButton;

  private List<Button> childGenVarsButtons = new ArrayList<>();
  private List<Combo> childGenVarsCombos = new ArrayList<>();
  private Combo childGenList;
  private Button addchildGen;
  private Composite childGenVars;
  private Composite specPanel;

  private void updateSpecPanel() {
    if (isDisposed || specPanel.isDisposed()) return;
    if (instancer == null || instancer.isDisposed()) return;

    for (Control c : specPanel.getChildren()) c.dispose();
    Composite childGenVarsBase = null;
    RequalityChildIterator ci = instancer.getChildIterator();
    if (ci!=null && ci.getChildIteratorType()
        == RequalityChildIteratorType.REUSE_CARTESIAN) {
      Label l = new Label(specPanel, SWT.NONE);
      l.setText("It. vars:");
      l.setLayoutData(new GridData(SWT.RIGHT, SWT.TOP, false, false));
      childGenVarsBase = new Composite(specPanel, SWT.NONE);
      childGenVarsBase.setLayoutData(new GridData(GridData.FILL_BOTH));
      childGenVarsBase.setLayout(new GridLayout(1, true));
      childGenVars = new Composite(childGenVarsBase, SWT.NONE);
      childGenList.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
      //childGenVars.setBackground(null);
      childGenVars.setLayout(new GridLayout(2, false));
    }
    updateChildGenList(instancer);
    parent.layout();
    if (ci!=null && ci.getChildIteratorType()
        == RequalityChildIteratorType.REUSE_CARTESIAN) {
      addchildGen = new Button(childGenVarsBase, SWT.NONE);
      addchildGen.setText("+");
      addchildGen.setEnabled(
          ((ReuseRequalityChildIterator) instancer.getChildIterator())
                  .getAttributeNames(instancer, instancer.getTreeDB())
                  .size()
              > 0);
      addchildGen.setFont(specFont);
      GridData ld = new GridData();
      ld.heightHint = ld.widthHint = childGenList.getSize().y;
      addchildGen.setLayoutData(ld);
    }

    if (childGenVars != null && !childGenVars.isDisposed()) childGenVars.layout();
    specPanel.layout();
  }

  private void updateTargetUFName(Composite parent, Text target, VirtualNode instancer, final TreeNode nde) {

    UIJob job =
        new UIJob("update requality cnf") {//TODO rewrite to display job?
          @Override
          public IStatus runInUIThread(IProgressMonitor monitor) {
            if(isDisposed ||  parent.isDisposed())
              return Status.CANCEL_STATUS;
            if (instancer == null || instancer.getTarget() == null)
              target.setText("<Target node is not selected>");
            TreeNode node = nde;
            if (node == null && instancer!=null) node = instancer.getTarget();
            if ((node == null || node.isDisposed()) && !target.isDisposed())
              target.setText("<Target node is not selected>");
            if (!target.isDisposed() && node != null) target.setText(node.getUserVisibleName(true));
            return Status.CANCEL_STATUS;
          }
        };
    job.schedule();
  }

  private void updateTargetUFName() {
    updateTargetUFName(parent, target, instancer, null);
  }

  private INodeChangeListener
      iteratorChange =
          new INodeChangeListener() {
            @Override
            public void deleted(TreeNode source) {
              //		dispose();
            }

            @Override
            public void attributeChange(TreeNode source, String what,Attribute old) {
              final VirtualNode in = (VirtualNode) source;
              final Composite specPanel = InstancerIteratorPropertySection.this.specPanel;
              final Combo childGenList = InstancerIteratorPropertySection.this.childGenList;
              if (what.equals(VirtualNode.ATTR_CHILD_ITERATOR)
                  || what.equals(TreeNode.ATTR_UNDEFINED)) {
                EclipseAPI.getDisplay().asyncExec(
                        new Runnable() {
                          @Override
                          public void run() {
                            if (!isDisposed && !specPanel.isDisposed()) {
                              RequalityChildIterator it = in.getChildIterator();
                              if(it!=null)
                                childGenList.select(
                                  it.getChildIteratorType().ordinal());
                              updateChildGenList(in);
                            }
                          }
                        });
              } else if (what.equals(VirtualNode.ATTR_TARGET)
                  || what.equals(TreeNode.ATTR_UNDEFINED)) {
                updateTargetUFName();
              }
            }

            @Override
            public void moved(TreeNode source, TreeNode oldParent) {
              // TODO Auto-generated method stub

            }

            @Override
            public void attributesChange(TreeNode source, ChangesSet<Attribute> what) {
              if (what.contains(VirtualNode.ATTR_CHILD_ITERATOR)
                  || what.contains(TreeNode.ATTR_UNDEFINED))
                attributeChange(source, VirtualNode.ATTR_CHILD_ITERATOR, what.getOld(VirtualNode.ATTR_CHILD_ITERATOR));

              if (what.contains(VirtualNode.ATTR_TARGET) || what.contains(TreeNode.ATTR_UNDEFINED))
                attributeChange(source, VirtualNode.ATTR_TARGET, what.getOld(VirtualNode.ATTR_TARGET));
            }

            @Override
            public void fireNodeResourceChange(
                TreeNode treeNode, String resName, Moment m) {
              // TODO Auto-generated method stub

            }
          },
      targetChange =
          new INodeChangeListener() {
            @Override
            public void deleted(TreeNode source) {
              if (instancer != null) updateTargetUFName(childGenVars, target, instancer, source);
            }

            @Override
            public void attributeChange(final TreeNode source, String what,Attribute old) {
              if (instancer == null) return;
              if (what.equals(TreeNode.ATTR_NAME)
                  || what.equals(TreeNode.ATTR_UNDEFINED)
                  || what.equals(TreeNode.ATTR_LABEL)) {
                final Composite childGenVars = InstancerIteratorPropertySection.this.childGenVars;
                final Text target = InstancerIteratorPropertySection.this.target;
                final VirtualNode instancer = InstancerIteratorPropertySection.this.instancer;
                WorkspaceJob wj =
                    new WorkspaceJob("refresh name") {//TODO rewrite to display job?
                      @Override
                      public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
                        if(isDisposed)
                          return Status.CANCEL_STATUS;
                        updateTargetUFName(childGenVars, target, instancer, source);
                        return Status.OK_STATUS;
                      }
                    };
                wj.schedule();
              }
            }

            @Override
            public void moved(TreeNode source, TreeNode oldParent) {
              updateTargetUFName(childGenVars, target, instancer, source);
            }

            @Override
            public void attributesChange(TreeNode source, ChangesSet<Attribute> what) {
              Map<String,Attribute> old = what.getOldMap();
              for (Entry<String, Attribute> s: old.entrySet())
                attributeChange(source, s.getKey(), s.getValue());
            }

            @Override
            public void fireNodeResourceChange(
                TreeNode treeNode, String resName, Moment m) {
              // TODO Auto-generated method stub

            }
          };


  private Listener
      selBaseLink =
          new Listener() {
	       boolean started = false;
           public void handleEvent(Event event) {
    	     try {
                if ((event.type == SWT.KeyDown) && (event.keyCode == SWT.INSERT)
                    || (event.type == SWT.Selection)) {
                  try {
                    final Set<String> types = new HashSet<>();
                    types.add(Requirement.TYPE_NAME);
                    types.add(TestPurpose.TYPE_NAME);
                    types.add(Comment.TYPE_NAME);
                    types.add(VirtualNode.TYPE_NAME);
                    SelectNodeDialog selDialog =
                        new SelectNodeByTypesDialog(
                            parent.getShell(), instancer.getTreeDB(), types);
                    selDialog.setFilter(
                        new ITreeNodeFilter() {

                          @Override
                          public boolean select(TreeNode node) {
                            if (!types.contains(node.getType())) return false;
                            RequalityChildIterator it = instancer.getChildIterator();
                            return it != null && it.checkTarget(instancer, node) == null;
                          }
                        });
                    if (selDialog.open() == SelectNodeDialog.OK) {
                      TreeNode selected = selDialog.getResult();
                      if (selected != null) {
                	  final TreeNode node = InstancerIteratorPropertySection.this.node;
                        WorkspaceJob j = new WorkspaceJob("Change target node for "+node.getUserVisibleName()){

                          @Override
                          public IStatus runInWorkspace(IProgressMonitor monitor) {
                             if(node == null)
                               return Status.CANCEL_STATUS;
                             boolean started = node.getTreeDB().startTransaction("Select target node");
                             if (!started) 
                               return Status.CANCEL_STATUS;
                             try{
                               //setTarget(null);
                               setTarget(selected);
                               node.commitAttributes();
                               if(started){
                                 node.getTreeDB().commit();
                                 started = false;
                               }
                             }
                             finally{
                               if(started)
                                 node.getTreeDB().rollback();
                             }
                             return Status.OK_STATUS;
                           }
                           
                         };
                         j.schedule();
                         return;
                       }
                     }
                  } catch (Exception e) {
                    RLogger.logError(e);
                  }
                }
              } catch (Exception e) {
                RLogger.logError(e);
              }
              //errored roollback
              if (started) node.getTreeDB().rollback();
            }
          },
      clearBaseLink =
          new Listener() {
            public void handleEvent(Event event) {
        	  final TreeNode node = InstancerIteratorPropertySection.this.node;
              WorkspaceJob j = new WorkspaceJob("Clear target node"){

                @Override
                public IStatus runInWorkspace(IProgressMonitor monitor) {
                  boolean started = node.getTreeDB().startTransaction("Clear target node");
                  if (started) {
                    try{
                      setTarget(null);
                      node.commitAttributes();
                      if(started){
                        node.getTreeDB().commit();
                        started = false;
                      }
                    }
                    finally{
                      if(started)
                        node.getTreeDB().rollback();
                    }
                  }
                  return Status.OK_STATUS;
                }
                
              };
              j.schedule();
            }
          };
  private Map<Integer, FocusListener> lastComboSelectionListeners =
      new HashMap<>();
  private Map<Integer, Listener> lastDelPressListeners = new HashMap<>();
  private void setTarget(TreeNode selected) {
	TreeDB started = null;
    boolean stTrans = false;
    try {
      TreeNode old = instancer.getTarget();
      if (old != null) old.removeGuiChangeListener(targetChange);
      instancer.hide = true;
      String val = null;
      RequalityChildIterator it = instancer.getChildIterator();
      if (selected != null && it != null)
        val = (it.checkTarget(instancer, selected));
      if (val != null) {
        instancer.hide = false;
        RMessages.openError(
                "Target selection",
                "Selected node of type "
                    + (selected != null ? selected.getTypeLabel():"Node")
                    + " cannot be child of "
                    + instancer.getUserVisibleName(true)
                    + " because:\n"
                    , val, false);
          return;
        }
        instancer.hide = false;
        stTrans = instancer.getTreeDB().startTransaction("Change target of reuse",true);
        	started = instancer.getTreeDB();
	        try {
	          instancer.setTarget(selected);
	          instancer.commitAttributes();
	          if(stTrans){
	            started.commit();
	            stTrans = false;
	          }
	        } catch (RuntimeException e) {
	          RLogger.logError(e);
	          RMessages.openError(
	              "Target selection",
	              "Selected node cannot be target of "
	                    + instancer.getUserVisibleName(true)
	                    + " because ",
	                    e.toString()+" "+CoreUtils.getTrace(e.getStackTrace()), false);
	        }
	        if (selected != null) selected.addGuiChangeListener(targetChange);
    } catch (Exception e) {
      RLogger.logError(e);
    }finally{
      if(stTrans && started!=null)
        started.rollback();
    }
    final Text target = this.target;
    EclipseAPI.getDisplay().asyncExec(new Runnable(){
      @Override
      public void run() {
        if(isDisposed ||target.isDisposed())
          return ;
        if (selected != null) target.setText(selected.getUserVisibleName(true));
        else target.setText("");
      }
    });
  }

  @Override
  public void disposedActions(boolean realDispose) {
    super.disposedActions(realDispose);
    if (instancer != null && realDispose) {
      instancer.removeGuiChangeListener(iteratorChange);
      TreeNode nd = instancer.getTarget();
      if (nd != null) nd.removeGuiChangeListener(targetChange);
    }
  }
  

  public Composite createComposite(
      Composite par, TabbedPropertySheetPage aTabbedPropertySheetPage) {
    parent = aTabbedPropertySheetPage.getWidgetFactory().createComposite(par);
    parent.setLayout(new GridLayout(1, true));
    parent.setLayoutData(new GridData(GridData.FILL_BOTH));
    parent.setBackgroundMode(SWT.INHERIT_FORCE);//fixes wrong colors
    
    Composite panel = new Composite(parent, SWT.NONE);
    panel.setLayout(new GridLayout(3, false));
    GridData gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.horizontalSpan = 2;
    panel.setLayoutData(gd);

    Label l = new Label(panel, SWT.NONE);
    l.setText("Target:");
    l.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
    selBaseButton = new Button(panel, SWT.PUSH);
    selBaseButton.setText(BUT_SEL);
    selBaseButton.setToolTipText(TITLE_SEL_BASE);
    selBaseButton.addListener(SWT.Selection, selBaseLink);

    clearBaseButton = new Button(panel, SWT.PUSH);
    clearBaseButton.setText(BUT_CLEAR);
    clearBaseButton.setToolTipText(TITLE_CLEAR_BASE);
    clearBaseButton.addListener(SWT.Selection, clearBaseLink);

    GridLayout layout = new GridLayout(3, false);
    layout.marginHeight = 0;
    layout.marginWidth = 0;

    target = new Text(parent, SWT.READ_ONLY);
    target.setText("");
    //target.setBackground(null);
    target.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
   

    Composite c = new Composite(parent, SWT.NONE);
    c.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    c.setLayout(new GridLayout(2, false));
    l = new Label(c, SWT.NONE);
    l.setText("Iteration method:");

    childGenList = new Combo(c, SWT.READ_ONLY);
    childGenList.add("Reuse");
    childGenList.add("Base element");
    childGenList.addModifyListener(
        new ModifyListener() {

          @Override
          public void modifyText(ModifyEvent e) {
        	TreeDB db = instancer.getTreeDB();
        	TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
            if (isDisposed || !tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db)))
              return;
            RequalityChildIterator ci = instancer.getChildIterator();
            if (ci == null
                || childGenList.getSelectionIndex() != -1
                    && ci.getChildIteratorType().ordinal() != childGenList.getSelectionIndex()) {
              int sel = childGenList.getSelectionIndex();
              if (sel < 0) sel = 0;
              int fsel = sel;
              final VirtualNode instancer = InstancerIteratorPropertySection.this.instancer;
              final Combo childGenList = InstancerIteratorPropertySection.this.childGenList; 
              WorkspaceJob j = new WorkspaceJob("Update child iterator type for "+node.getUserVisibleName()){
                @Override
                public IStatus runInWorkspace(IProgressMonitor monitor) {
                  if(instancer == null)
                    return Status.CANCEL_STATUS;
                  RequalityChildIteratorType tp = RequalityChildIteratorType.values()[fsel];
                  boolean started = instancer.getTreeDB().startTransaction("Change iterator type", true);
                  if (started) {
                    try {
                      TreeNode targ = instancer.getTarget();
                      try {
                        instancer.setTarget(null);
                        instancer.commitAttributes();
                        instancer.setTarget(targ);
                        instancer.setChildIterator(
                            RequalityChildIterator.fromAttributeString(
                                null, instancer, tp.ordinal() + "|UUID:|" + Integer.toString(fsel)));
                        instancer.commitAttributes();
  
                      } catch (Exception e1) {
                        e1.printStackTrace();
                      }
                      if(started) {
                        instancer.getTreeDB().commit();
                        started = false;
                      }
                    }finally {
                      if(started)
                        instancer.getTreeDB().rollback();
                    }
                  } else
                    EclipseAPI.getDisplay().asyncExec(new Runnable(){
                    
                      @Override
                      public void run() {
                        if(isDisposed || childGenList.isDisposed() || instancer == null)
                          return;
                        RequalityChildIterator it = instancer.getChildIterator();
                        if(it!=null)
                          childGenList.select(it.getType().ordinal());
                      }
                    });
                  EclipseAPI.getDisplay().asyncExec(new Runnable(){
                    @Override
                    public void run() {
                      if(isDisposed || childGenList.isDisposed())
                        return;
                      updateSpecPanel();
                    }
                  });
                  return Status.OK_STATUS;
                }
                
              };
              j.schedule();
            }
          }
        });

    specPanel = new Composite(parent, SWT.NONE);
    specPanel.setLayoutData(new GridData(GridData.FILL_BOTH));
    specPanel.setLayout(new GridLayout(2, false));
    updateSpecPanel();
    return parent;
  }

  private void updateChildGenList(VirtualNode instancer) {
    //childGenVarsCombos.clear();
    //String newChildGenDesctiption = instancer.attrs().getStringAttributeValue(VirtualNode.ATTR_CHILD_ITERATOR);
    //		if(newChildGenDesctiption == null) {
    //		}
    RequalityChildIterator rci = instancer.getChildIterator();
    if (rci != null) {
      childGenList.select(rci.getChildIteratorType().ordinal());
    } else {
      childGenList.select(0);
      rci = instancer.getChildIterator();
    }
    if (rci != null && rci.getChildIteratorType() == RequalityChildIteratorType.REUSE_CARTESIAN) {
      ReuseRequalityChildIterator ci = (ReuseRequalityChildIterator) rci;
      List<String> possibleVars = ci.getAttributeNames(instancer, instancer.getTreeDB());
      int i = 0;

      final List<String> vars = ci.getAttributeVars();
      while (vars.size() < childGenVarsCombos.size()) {
        int ind = childGenVarsCombos.size();
        childGenVarsCombos.get(ind - 1).dispose();
        childGenVarsCombos.remove(ind - 1);
        childGenVarsButtons.get(ind - 1).dispose();
        childGenVarsButtons.remove(ind - 1);
      }
      for (String s : vars) {
        final int j = i;
        Combo c2 = null;
        Button b2 = null;
        if (j < childGenVarsCombos.size() && !childGenVarsCombos.get(j).isDisposed()) {
          c2 = childGenVarsCombos.get(j);
          b2 = childGenVarsButtons.get(j);
          if (lastComboSelectionListeners.containsKey(j)) {
            c2.removeFocusListener(lastComboSelectionListeners.get(j));
            b2.removeListener(SWT.MouseDown, lastDelPressListeners.get(j));
            c2.removeAll();
          }
        } else {
          c2 = new Combo(childGenVars, SWT.READ_ONLY);
          childGenVarsCombos.add(c2);
          //TODO check it
          //if(s.equals("") && !c2.getText().equals(""))
          //instancer.getChildIterator().setAttributeVar(i, c2.getText());
          b2 = new Button(childGenVars, SWT.NONE);
          b2.setFont(specFont);
          childGenVarsButtons.add(b2);

          GridData ld = new GridData();
          ld.heightHint = ld.widthHint = childGenList.getSize().y;
          b2.setLayoutData(ld);
          b2.setText("⊗");
        }
        final Combo c = c2;
        final Button b = b2;
        lastComboSelectionListeners.put(
            j,
            new FocusListener() {

              @Override
              public void focusLost(FocusEvent e) {
                RequalityChildIterator it = instancer.getChildIterator();
                if(it == null)
                  return;
            	TreeDB db = instancer.getTreeDB();
            	TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
                if (e.widget.isDisposed()
                    || !tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db)))
                	return;
                if (((ReuseRequalityChildIterator) it).getAttributeVar(j)
                    == c.getText()) return;
                ((Combo) e.widget).removeFocusListener(lastComboSelectionListeners.get(j));
                String ctext = c.getText();
                final Map<Integer, FocusListener> lastComboSelectionListeners =
                        InstancerIteratorPropertySection.this.lastComboSelectionListeners;
                WorkspaceJob jb = new WorkspaceJob("Set parameter in iterator for "+node.getUserVisibleName()){

                  @Override
                  public IStatus runInWorkspace(IProgressMonitor monitor) {
                    if (instancer!=null){
                      boolean started = db.startTransaction("Set parameter in iterator", true);
                      if(started){
                        try{
                          ((ReuseRequalityChildIterator) it)
                            .setAttributeVar(instancer, db, j, ctext);
                          instancer.commitAttributes();
                          if(started){
                            db.commit();
                            started = false;
                          }
                      }finally{
                        if(started)
                          db.rollback();
                      }
                    } else
                      EclipseAPI.getDisplay().asyncExec(new Runnable(){
                      
                        @Override
                        public void run() {
                          if(isDisposed || e.widget.isDisposed()||instancer == null)
                            return;
                          ((Combo) e.widget)
                          .select(
                              ((Combo) e.widget)
                                  .indexOf(
                                      ((ReuseRequalityChildIterator) it)
                                          .getAttributeVar(j))); 
                        }
                      });
                    }
                    EclipseAPI.getDisplay().asyncExec(new Runnable(){
                      @Override
                      public void run() {
                        if(isDisposed || e.widget.isDisposed())
                          return;
                        ((Combo) e.widget).addFocusListener(lastComboSelectionListeners.get(j));
                      }
                    });
                    return Status.OK_STATUS;
                  }
                  
                };
                jb.schedule();
              }

              @Override
              public void focusGained(FocusEvent e) {
                // TODO Auto-generated method stub

              }
            });
        lastDelPressListeners.put(
            j,
            new Listener() {
              @Override
              public void handleEvent(Event event) {
            	TreeDB db = instancer.getTreeDB();
            	TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
                if (event.widget.isDisposed()
                    || !tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db)))
                	return;
                final VirtualNode instancer = InstancerIteratorPropertySection.this.instancer;
                final List<Button>childGenVarsButtons= InstancerIteratorPropertySection.this.childGenVarsButtons;
                final List<Combo> childGenVarsCombos = InstancerIteratorPropertySection.this.childGenVarsCombos;
                final Map<Integer, FocusListener> lastComboSelectionListeners = 
                    InstancerIteratorPropertySection.this.lastComboSelectionListeners;
                final Map <Integer, Listener> lastDelPressListeners = 
                    InstancerIteratorPropertySection.this.lastDelPressListeners;
                final Composite parent = InstancerIteratorPropertySection.this.parent;
                final Composite childGenVars = InstancerIteratorPropertySection.this.childGenVars;
                WorkspaceJob j = new WorkspaceJob("Remove parameter from iterator"){

                  @Override
                  public IStatus runInWorkspace(IProgressMonitor monitor) {
                      if (instancer!=null) {
                        RequalityChildIterator it = instancer.getChildIterator();
                        if(it == null)
                          return Status.CANCEL_STATUS;
                        boolean started = instancer
                          .getTreeDB()
                          .startTransaction("Remove parameter from iterator", true);
                        if(started){
                          try{
                            int id = childGenVarsButtons.indexOf(event.widget);
                            ((ReuseRequalityChildIterator) it)
                                .removeAttributeVar(instancer, instancer.getTreeDB(), id);
                            EclipseAPI.getDisplay().syncExec(new Runnable() {
                              
                              @Override
                              public void run() {
                                event.widget.dispose();
                                childGenVarsCombos.get(id).dispose();
                              }
                            });
                            childGenVarsCombos.remove(id);
                            childGenVarsButtons.remove(id);
                            for (int i = id; i < childGenVarsCombos.size(); i++) {
                              lastComboSelectionListeners.put(i, lastComboSelectionListeners.get(i + 1));
                              //  lastComboSelectionListeners.remove(i);
                              lastDelPressListeners.put(i, lastDelPressListeners.get(i + 1));
                              //lastDelPressListeners.remove(i);
                            }
                            lastComboSelectionListeners.remove(childGenVarsCombos.size());
                            lastDelPressListeners.remove(childGenVarsCombos.size());
                            if(started){
                              started = false;
                              instancer.getTreeDB().commit();
                            }
                          }finally{
                            if(started)
                              instancer.getTreeDB().rollback();
                          }
                        }
                      }
                      EclipseAPI.getDisplay().asyncExec(new Runnable(){
                      
                        @Override
                        public void run() {
                          if(isDisposed || instancer == null || parent.isDisposed())
                            return;
                          updateChildGenList(instancer);
                          childGenVars.layout();
                          childGenVars.getParent().layout();
                        }
                      });
                      return Status.OK_STATUS;
                    }
                    
                  };
                  j.schedule();
                }
              });
        int tosel = 0, k = 0;
        for (String s2 : possibleVars) {
          if (s2.startsWith("_")) continue;
          c.add(s2);
          if (s2.equals(s)) tosel = k;
          k++;
        }
        c.select(tosel);
        c.addFocusListener(lastComboSelectionListeners.get(j));
        b.addListener(SWT.MouseDown, lastDelPressListeners.get(j));
        i++;
      }
    }
    //parent.layout();
    //specPanel.layout();

    parent.layout();
    parent.redraw();
    //if(childGenVars!=null && !childGenVars.isDisposed())
    //childGenVars.layout();

  }

  @Override
  public void createControls(
      final Composite par, TabbedPropertySheetPage aTabbedPropertySheetPage) {
    super.createControls(parent, aTabbedPropertySheetPage);
    par.setLayout(new GridLayout(1, true));
    par.setLayoutData(new GridData(GridData.FILL_BOTH));
    parent = createComposite(par, aTabbedPropertySheetPage);
    specFont =
        new Font(
            aTabbedPropertySheetPage.getSite().getShell().getDisplay(),
            java.awt.Font.MONOSPACED,
            14,
            0);
  }

  @Override
  protected void setTreeNode(TreeNode node) {
    if (!(node instanceof VirtualNode) || isDisposed || target.isDisposed()) {
      return;
    }

    instancer = (VirtualNode) node;
    if (isDisposed) return;

    if (!target.isDisposed()) target.setText("");
    childGenVarsCombos.clear();
    childGenVarsButtons.clear();
    lastComboSelectionListeners.clear();
    lastDelPressListeners.clear();
    updateSpecPanel();

    if (instancer.getTarget() != null) {
      TreeNode targ = instancer.getTarget();
      targ.removeGuiChangeListener(targetChange);
      target.setText(targ.getUserVisibleName(true));
      targ.addGuiChangeListener(targetChange);
    }

    if (addchildGen != null && !addchildGen.isDisposed()) {
      addchildGen.addListener(
          SWT.MouseDown,
          new Listener() {
            @Override
            public void handleEvent(Event event) {
        	   final VirtualNode instancer = InstancerIteratorPropertySection.this.instancer;
               final Composite childGenVars = InstancerIteratorPropertySection.this.childGenVars;
               final Composite parent = InstancerIteratorPropertySection.this.parent;
               WorkspaceJob j = new WorkspaceJob("Add parameter to iterator"){
                 @Override
                 public IStatus runInWorkspace(IProgressMonitor monitor) {
                   boolean started = instancer!=null && instancer.getTreeDB().startTransaction("Add parameter to iterator", true);
                   if (started && instancer!=null) {
                    RequalityChildIterator it = instancer.getChildIterator();
                    if(it == null)
                      return Status.CANCEL_STATUS;
                    try {
                       ((ReuseRequalityChildIterator) it)
                        .addAttributeVar(instancer, instancer.getTreeDB());
                      instancer.commitAttributes();
                      if(started){
                        instancer.getTreeDB().commit();
                        started = false;
                      }
                    }finally {
                      if(started)
                        instancer.getTreeDB().rollback();
                    }
                  }
                  EclipseAPI.getDisplay().asyncExec(new Runnable(){
                    
                    @Override
                    public void run() {
                      if(isDisposed || instancer == null || parent.isDisposed())
                        return;
                      updateChildGenList(instancer);
                      
                      Composite childGenVars = InstancerIteratorPropertySection.this.childGenVars;
                      childGenVars.layout();
                      childGenVars.getParent().layout();
                    }
                  }); 
                  return Status.OK_STATUS;
                }
                
              };
              j.schedule();
            }
          });
    }

    node.addGuiChangeListener(iteratorChange);

    parent.layout();
    parent.redraw();
  }

  @Override
  protected boolean affectedByAttribute(String a) {
    return TreeNode.ATTR_ID.equals(a)
        || TreeNode.ATTR_NAME.equals(a)
        || (VirtualNode.ATTR_TARGET.equals(a))
        || (TreeNode.ATTR_UNDEFINED.equals(a) || VirtualNode.ATTR_CHILD_ITERATOR.equals(a));
  };

  private static Set<String> ids = new HashSet<>();
  static{
    ids.add(TreeNode.ATTR_ID);
    ids.add(TreeNode.ATTR_NAME);
    ids.add(TreeNode.ATTR_UNDEFINED);
    ids.add(VirtualNode.ATTR_TARGET);
    ids.add(VirtualNode.ATTR_CHILD_ITERATOR);
  }
  @Override
  public boolean requireUpdate(TreeNode source, ChangesSet<Attribute> what) {
    for(String s:what.getAll())
      if(ids.contains(s) || !s.startsWith("_"))//TODO iterator check
        return true;
    return false;
  };
}
