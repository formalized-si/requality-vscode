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
package com.unitesk.requality.vscode.port.eclipse.views.properties;

import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.unitesk.requality.vscode.ui.jface.resource.FontRegistry;
import com.unitesk.requality.vscode.ui.jface.text.Document;
import com.unitesk.requality.vscode.ui.jface.text.IDocument;
import com.unitesk.requality.vscode.ui.jface.text.ITextInputListener;
import com.unitesk.requality.vscode.ui.jface.text.TextViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.graphics.FontData;
import com.unitesk.requality.vscode.ui.swt.layout.FillLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPart;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.tabbed.AbstractPropertySection;
import com.unitesk.requality.vscode.ui.tabbed.TabbedPropertySheetPage;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributesVersioningRule;
import com.unitesk.requality.core.attribute.VersionsStorage;
import com.unitesk.requality.core.attribute.reference.IReferenceVersionRule;
import com.unitesk.requality.core.events.INodeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.storage.LinkResolver;
import com.unitesk.requality.core.transaction.ITransactionListener;
import com.unitesk.requality.core.transaction.TransactionStorage;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;

public class DebugPropertySection extends AbstractPropertySection {
  private TextViewer source;
  private FontRegistry fontRegistry;
  private TabbedPropertySheetPage page;
  private Lock l = new ReentrantLock();

  public DebugPropertySection() {
    super();
    l.lock();
    EclipseAPI.getDisplay()
        .syncExec(
            new Runnable() {

              @Override
              public void run() {
                try{
                  fontRegistry = new FontRegistry(PlatformUI.getWorkbench().getDisplay());
                  fontRegistry.put(
                    "debug", new FontData[] {new FontData("Monospaced", 12, SWT.NORMAL)});
                }
                finally{
                  l.unlock();
                }
              }
            });
  }

  @Override
  public void createControls(Composite parent, TabbedPropertySheetPage aTabbedPropertySheetPage) {
    l.lock();
    try{
      super.createControls(parent, aTabbedPropertySheetPage);
      page = aTabbedPropertySheetPage;
      parent.setLayout(new FillLayout());
      source = new TextViewer(parent, SWT.MULTI | SWT.READ_ONLY);
      source.getControl().setFont(fontRegistry.get("debug"));
      source.addTextInputListener(
          new ITextInputListener() {
            @Override
            public void inputDocumentChanged(IDocument oldInput, IDocument newInput) {
              if (source.getControl().isDisposed()) return;
              source.getControl().pack();
              source.getControl().redraw();
              source.getControl().getParent().pack();
              source.getControl().getParent().layout();
              if (!page.getControl().isDisposed()) {
                page.resizeScrolledComposite();
              }
            }
  
            @Override
            public void inputDocumentAboutToBeChanged(IDocument oldInput, IDocument newInput) {
              // TODO Auto-generated method stub
  
            }
          });
    }
    finally{
      l.unlock();
    }
    //source.getControl().setLayoutData(new Fi);
  }
  private boolean transupdate = false;
  INodeChangeListener ncl =
      new INodeChangeListener() {
        @Override
        public void deleted(TreeNode source) {
          if (target != null && source.getUUId().equals(target.getUUId())) {
            if (page.getControl().isDisposed()) {
              return;
            }
            if (page.getCurrentTab() != null) {
              TreeNode par = target.getShownParent();
              if (par == null) {
                setInput(getPart(), null); //TODO del autoup
                return;
              }
              if (par.isDisposed()) par = par.getTreeDB().getRootNode();
              setInput(getPart(), new StructuredSelection(par)); //TODO del autoup
            }
          }
        }

        @Override
        public void attributeChange(final TreeNode source, String what, Attribute old) {
          if (page.getControl().isDisposed()&&target == source) {
            return;
          }
          TreeDB db = source.getTreeDB();
          TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
          if(!tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db))) {
            transupdate = true;
          } else {
            openNode(source);
          }
        }

        @Override
        public void moved(TreeNode source, TreeNode oldParent) {
          // TODO Auto-generated method stub

        }

        @Override
        public void attributesChange(TreeNode source, ChangesSet<Attribute> what) {
          if (page.getControl().isDisposed()&&target == source) {
            return;
          }
          TreeDB db = source.getTreeDB();
          TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
          if(!tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db))) {
            transupdate = true;
          } else {
            openNode(source);
          }
        }

        @Override
        public void fireNodeResourceChange(
            TreeNode treeNode, String resName, Moment m) {
          // TODO Auto-generated method stub

        }
      };
  ITransactionListener itl =
      new ITransactionListener() {

        @Override
        public void undo(TransactionStorage tStorage) {
          if (transupdate) {
            openNode(target);
            transupdate = false;
          }
        }

        @Override
        public void redo(TransactionStorage tStorage) {
          if (transupdate) {
            openNode(target);
            transupdate = false;
          }
        }

        @Override
        public void execute(TransactionStorage tStorage) {
          if (transupdate) {
            openNode(target);
            transupdate = false;
          }
        }

        @Override
        public void _dispose() {
          // TODO Auto-generated method stub

        }
      };

  @Override
  public void setInput(IWorkbenchPart part, ISelection selection) {
    super.setInput(part, selection);

    TreeNode node = TreeNodePropertiesTab.toTreeNode(selection);
    if (node == null) {
      return;
    }
    openNode(node);
  }

  private TreeNode target;

  private void openNode(final TreeNode node) {
    final TextViewer source = this.source;
    EclipseAPI.getDisplay()
        .asyncExec(
            new Runnable() {
              @Override
              public void run() {
                if(source.getControl().isDisposed())
                  return;
                openNodeSafe(TreeNodePropertiesTab.toRequirementOrNode(node));
              }
            });
  }

  private void openNodeSafe(TreeNode node) {
    if (target != null) {
      target.removeGuiChangeListener(ncl);
      target.getTreeDB().removeTransactionListener(itl);
    }
    if (node == null) return;
    target = node;
    target.addGuiChangeListener(ncl);
    target.getTreeDB().addTransactionListener(itl);
    try {
      StringBuilder addtext = new StringBuilder();
      addtext.append(node.getClass().getName());
      addtext.append("\n");
      addtext.append(node.hashCode());
      addtext.append("\n");
      addtext.append(node.getType());
      addtext.append("\n");
      addtext.append(node.isDisposed() ? "DISPOSED " : "not disposed");
      addtext.append(node.isVirtual() ? "" : "non");
      addtext.append("virt node");
      addtext.append(", ");
      addtext.append(node.getTreeDB().isVirtual() ? "" : "non");
      addtext.append("virt TreeDB(");
      addtext.append(node.getTreeDB().getVisibleName());
      addtext.append("), uid: ");
      addtext.append(node.getSUUId());
      addtext.append("\nparent: ");
      if (node.getWorkParent() == null) addtext.append("NONE");
      else {
        addtext.append(node.getWorkParent().toString());
        addtext.append("(");
        addtext.append(node.getWorkParentUUID());
        addtext.append(", ");
        addtext.append(node.getWorkParent().isVirtual() ? "" : "non");
        addtext.append("virt node");
        addtext.append(", ");
        addtext.append(node.getWorkParent().getTreeDB().isVirtual() ? "" : "non");
        addtext.append("virt TreeDB");
        addtext.append(")");
      }
      addtext.append("\nwchilds: ");
      addtext.append(node.getWorkChildrenUUIDs().size());
      addtext.append(", schilds: ");
      addtext.append(node.sizeShownChildren());
      addtext.append("\ncloneParent: ");
      if (node.getCloneParent() == null) addtext.append("NONE");
      else {
        addtext.append(node.getCloneParent().toString());
        addtext.append("(");
        addtext.append(node.getCloneParentUUId());
        addtext.append(", ");
        addtext.append(node.getCloneParent().isVirtual() ? "" : "non");
        addtext.append("virt node");
        addtext.append(", ");
        addtext.append(node.getCloneParent().getTreeDB().isVirtual() ? "" : "non");
        addtext.append("virt TreeDB");
        addtext.append(")");
      }
      addtext.append("\n");
      VersionsStorage vs = node.attrs().getVersionsStorage();
      for (String s : node.attrs().getVersionsStorage().getKeys()) {
        addtext.append("\n");
        addtext.append(s);
        addtext.append(": ");
        addtext.append(vs.getVersion(s).toString());
      }
      addtext.append("\nrev:\n");
      Map<String, Map<TreeNode, String>> set = node.getTreeDB().getLinkResolver().getLinksToNode(node,  true);
      if(set!=null)
        for(Entry<String, Map<TreeNode, String>> en: set.entrySet()){
          addtext.append(en.getKey()+":\n");
          Map<TreeNode,String> nv = en.getValue()!=null?en.getValue():Collections.emptyMap();
          for(TreeNode m:nv.keySet())
            addtext.append("\t"+m.getQualifiedId()+(!AttributesVersioningRule.isLegal(node.getType())?
                "":IReferenceVersionRule.getVersionString(nv.get(m), m.getNodeVersion()," "))+"\n");
        }
      addtext.append("\nrelated:\n");
      LinkResolver lr = node.getTreeDB().getLinkResolver();
      Map<String, Map<TreeNode, String>>map = lr.getLinkedNodes(node);
      if(map!=null)
        for(String s:map.keySet()){
          addtext.append(s+":\n");
          Map<TreeNode,String> smap = map.get(s);
          for(TreeNode n:smap.keySet())
            addtext.append("\t"+n.getQualifiedId()+"(v"+smap.get(n)+")"+"\n");
        }
      source.setInput(new Document(addtext.toString()));

    } catch (Exception e) {
      RLogger.logError(e);
    }
    //page.getControl().pack();
    //source.getControl().forceFocus();
  }
}
