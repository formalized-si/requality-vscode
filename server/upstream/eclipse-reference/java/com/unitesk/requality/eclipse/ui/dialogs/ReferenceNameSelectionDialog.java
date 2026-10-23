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

package com.unitesk.requality.eclipse.ui.dialogs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.eclipse.ui.cnf.RequalitySorter;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

public class ReferenceNameSelectionDialog extends Dialog {

  protected TreeDB db;
  private TreeViewer nodeTree = null;
  private Map<String, Object> attrMap = null;
//  private static Map<TreeDB, String[]> attrSel = new HashMap<>();
  private ISelectionChangedListener selListener = 
      new ISelectionChangedListener() {
    @Override
    public void selectionChanged(SelectionChangedEvent event) {
      if(nodeTree.getControl().isDisposed())
        return;
      StructuredSelection ss = (StructuredSelection) nodeTree.getSelection();
      if (ss.isEmpty()) return;
      @SuppressWarnings("unchecked")
      Iterator<Object> i = ss.iterator();
      List<String> ts = new ArrayList<>();
      boolean clear = false;
      while (i.hasNext()) {
        Object o = i.next();
        if (o instanceof Attribute) ts.add(((Attribute) o).getKey());
        else clear = true;
      }
      if (clear) {
        db.registerMetaObject(META_SAVED_REVERT_NAMES, null);
      } else saveState(db, ts.toArray(new String[0]));
    }
  };
  public ReferenceNameSelectionDialog(TreeDB tn, Shell shell) {
    super(shell);
    db = tn;
  }

  protected void configureShell(Shell shell) {
    super.configureShell(shell);
    shell.setText("Select reference name(s)");
  }

  protected ILabelProvider getLabelProvider() {
    return new ILabelProvider() {

      @Override
      public void removeListener(ILabelProviderListener listener) {
        // TODO Auto-generated method stub

      }

      @Override
      public boolean isLabelProperty(Object element, String property) {
        // TODO Auto-generated method stub
        return false;
      }

      @Override
      public void dispose() {
        // TODO Auto-generated method stub

      }

      @Override
      public void addListener(ILabelProviderListener listener) {
        // TODO Auto-generated method stub

      }

      @Override
      public String getText(Object element) {
        if (element instanceof Attribute) return ((Attribute) element).getKey();
        return element.toString();
      }

      @Override
      public Image getImage(Object element) {
        // TODO Auto-generated method stub
        return null;
      }
    };
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    //setPageComplete(true);
    parent.setLayout(new GridLayout(1, true));
    Composite container = new Composite(parent, SWT.NULL);
    GridData gd = new GridData(GridData.FILL_BOTH);
    gd.heightHint = 350;
    gd.widthHint = 100;
    container.setLayoutData(gd);
    GridLayout layout = new GridLayout();
    container.setLayout(layout);
    layout.verticalSpacing = 9;

    gd = new GridData(GridData.FILL_BOTH);

    nodeTree = new TreeViewer(container, SWT.MULTI);
    nodeTree.getControl().setLayoutData(gd);
    nodeTree.setLabelProvider(getLabelProvider());
    nodeTree.setAutoExpandLevel(2);
    nodeTree.setComparator(new RequalitySorter());
    nodeTree.getControl().setEnabled(false);
    if (db != null) {
	  final TreeViewer nodeTree = this.nodeTree;
      final TreeDB db = this.db;
      final String[] attrSel = getSavedState(db);
      final Map<String, Object> attrMap = this.attrMap;
      final ISelectionChangedListener selListener = this.selListener;
      WorkspaceJob j = new WorkspaceJob("init data for reference name selection dialog"){

        @Override
        public IStatus runInWorkspace(IProgressMonitor monitor) {
          if(db==null)
            return Status.CANCEL_STATUS;
          getAMap();
          EclipseAPI.getDisplay().asyncExec(new Runnable(){
            @Override
            public void run() {
              if(nodeTree.getControl().isDisposed())
                return;
              nodeTree.setContentProvider(getContentProvider());
              nodeTree.setInput(db);
              nodeTree.getControl().setEnabled(true);
              if (attrSel!=null) {
                if (attrMap != null) {
                  List<Object> tosel = new ArrayList<>();
                  for (String s : attrSel) if (attrMap.containsKey(s)) tosel.add(attrMap.get(s));
                  nodeTree.setSelection(new StructuredSelection(tosel.toArray()));
                }
              }
              nodeTree.addSelectionChangedListener(selListener);
            }
          });
          return Status.OK_STATUS;
        }
      };
      j.schedule();
    } else {
      nodeTree.setInput(null);
      nodeTree.setContentProvider(getContentProvider());
      nodeTree.addSelectionChangedListener(selListener);
    }
    
    return container;

    //setControl( container );

  }
  /** Ensures that both text fields are set. */
  protected ITreeContentProvider getContentProvider() {
    return new ITreeContentProvider() {
      @Override
      public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {}

      @Override
      public void dispose() {}

      @Override
      public boolean hasChildren(Object element) {
        return element instanceof TreeNode;
      }

      @Override
      public Object getParent(Object element) {
        if (element instanceof Attribute) {
          return db;
        }
        return null;
      }

      @Override
      public Object[] getElements(Object inputElement) {
        if (inputElement instanceof TreeDB) {
          Object[] ret = null;
          ret = getAMap().values().toArray(new Object[attrMap.size() + 1]);
          ret[ret.length - 1] = "~Use all references~";
          return ret;
        }
        return null;
      }

      @Override
      public Object[] getChildren(Object parentElement) {
        return getElements(parentElement);
      }
    };
  }

  protected Map<String, Object> getAMap() {
    synchronized (this) {
      if(attrMap == null){
        attrMap = new HashMap<>();
        if(db==null)
          return attrMap;
        Set<TreeNode> nodes = new HashSet<>();
        DeepFirstTreeWalker dftw =
            new DeepFirstTreeWalker(
                db.getNode(Requirement.getTypeRootQId(), Path.QID), true, true, false);
        Iterator<TreeNode> it = dftw.iterator();
        while (it.hasNext()) nodes.add(it.next());
        for (TreeNode n : nodes) {
          for (Attribute a : n.attrs())
            if (a.getType().equals(AttributeType.REFERENCE)
                && !attrMap.containsKey(a.getKey())
                && a.getValue() != null 
                && !a.getValue().toString().replace("\n", "").trim().equals(""))
              attrMap.put(a.getKey(), a);
        }
      }
    }
    return attrMap;
  }
  private static String META_SAVED_REVERT_NAMES="SAVED_REVERT_REFERENCE_NAMES";
  public static String[] getSavedState(TreeDB db) {
    return db.getMetaObject(META_SAVED_REVERT_NAMES);
  }

  public static void saveState(TreeDB db,  String[] back) {
    db.registerMetaObject(META_SAVED_REVERT_NAMES, back);
  }
}
