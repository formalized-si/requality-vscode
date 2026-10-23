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
package com.unitesk.requality.eclipse.views.actions;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.PlatformUI;

import com.unitesk.requality.core.filter.ITreeNodeFilter;
import com.unitesk.requality.core.filter.ITreeNodesFilter;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.eclipse.ui.cnf.CNFAction;

public class NodeProcessingAction extends CNFAction {
  private ITreeNodeFilter filter;
  private ITreeNodesFilter enfilter;
  private ITreeNodeFilter actions;
  private String transactionName;
//TODO mb delete?
  public NodeProcessingAction(
      String name, String transactionName, ITreeNodeFilter filter, ITreeNodeFilter actions) {
    this(name, transactionName, filter, null, actions);
  }

  public NodeProcessingAction(
      String name, ITreeNodeFilter filter, ITreeNodesFilter enfilter, ITreeNodeFilter actions) {
    this(name, name, filter, enfilter, actions);
  }

  public NodeProcessingAction(
      String name,
      String transactionName,
      ITreeNodeFilter filter,
      ITreeNodesFilter enfilter,
      ITreeNodeFilter actions) {
    // TODO:
    // super(provider);
    this.enfilter = enfilter;
    this.filter = filter;
    this.actions = actions;
    this.transactionName = transactionName;
    setText(name);
  }

  @Override
  public void run() {
    final TreeDB db = getTreeDB();
    if (db == null) return;
    SelectionRecord nodes = SelectionManager.getOne().getSelection();
    boolean started = false;
    if (transactionName != null)
      started = db.startTransaction(transactionName, true);
      if (!started) {
        MessageDialog.openError(
            PlatformUI.getWorkbench().getDisplay().getActiveShell(),
            "Processing Error",
            "Some actions are already being performed");
        return;
      }
    try {
      for(TreeNode node: nodes.fullSelection) {
        if(node!=null){
            //            db.getNode(Requirement
            //                    .getTypeRootQId(),Path.QID);
            try {
              if (!filter.select(node)) continue;
              try {
                if (!actions.select(node)) {
                  if (started) {
                    db.rollback();
                    started = false;
                  }
                  return;
                }
              } catch (final Exception e) {
                if (started) {
                  db.rollback();
                  started = false;
                }
                PlatformUI.getWorkbench()
                    .getDisplay()
                    .asyncExec(
                        new Runnable() {
                          public void run() {
                            //			            	PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
                            MessageDialog.openError(
                                PlatformUI.getWorkbench().getDisplay().getActiveShell(),
                                "Processing Error",
                                e.getClass().getName() + ": " + e.getMessage());
                            e.printStackTrace();
                          }
                        });
                return;
              }
            } catch (final Exception e) {
              PlatformUI.getWorkbench()
                  .getDisplay()
                  .asyncExec(
                      new Runnable() {
                        public void run() {
                          //            		    PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
      
                          MessageDialog.openError(
                              PlatformUI.getWorkbench().getDisplay().getActiveShell(),
                              "Processing Error",
                              e.getClass().getName() + ": " + e.getMessage());
                          e.printStackTrace();
                        }
                      });
            }
          }
      }
    }finally{
      if(started)
        db.commit();
      //TODO also rollback on errors?
    }
  }

  private TreeDB getTreeDB() {
    SelectionRecord nodes = SelectionManager.getOne().getSelection();
    if (nodes != null && nodes.fullSelection.size()>0) {
      for(TreeNode node:nodes.fullSelection){
        if(node!=null)
          return node.getTreeDB();
      }
    }
    return null;
  }

  @Override
  public boolean isEnabled() {
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (nodes == null || nodes.fullSelection.size() < 1) return false;
    TreeDB db = null;
    for(TreeNode de:nodes.fullSelection) {
      if(de!=null) {
        if(db == null)
          db = de.getTreeDB();
        if(db!=de.getTreeDB())
          return false;
        break;
      }
    }
    if(db==null)
      return false;
    return (enfilter == null || enfilter.select(nodes).fullSelection.size() > 0);
  }
}
