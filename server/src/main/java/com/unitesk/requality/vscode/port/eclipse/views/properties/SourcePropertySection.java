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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import com.unitesk.requality.vscode.ui.runtime.WorkspaceJob;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
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

import com.unitesk.requality.core.IFSIdProvider;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.events.INodeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.exception.NodeNotFoundException;
import com.unitesk.requality.core.lock.LockIsBusy;
import com.unitesk.requality.core.lock.LockType;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.FSId;
import com.unitesk.requality.core.selection.IUIStateChangeListener;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionManager.SourcePriority;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.transaction.ITransactionListener;
import com.unitesk.requality.core.transaction.TransactionStorage;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;

public class SourcePropertySection extends AbstractPropertySection implements IFSIdProvider {
  private TextViewer source;
  private FontRegistry fontRegistry;
  private TabbedPropertySheetPage page;
  private FSId fsid;

  public SourcePropertySection() {
    super();
    fsid = SelectionManager.getOne().registerNewFSId(EventSource.PROPERTIES, this, true);//TODO true?
    fontRegistry = new FontRegistry(PlatformUI.getWorkbench().getDisplay());
    fontRegistry.put("source", new FontData[] {new FontData("Monospaced", 12, SWT.NORMAL)});
  }

  @Override
  public void createControls(Composite parent, TabbedPropertySheetPage aTabbedPropertySheetPage) {
    super.createControls(parent, aTabbedPropertySheetPage);
    page = aTabbedPropertySheetPage;
    parent.setLayout(new FillLayout());
    source = new TextViewer(parent, SWT.MULTI | SWT.READ_ONLY);
    source.getControl().setFont(fontRegistry.get("source"));
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
    SelectionManager.getOne().addSourceListener(EventSource.PROPERTIES, fsid, list );
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
              try{
                TreeNode par = target.getShownParentExc();
                if (par == null) {
                  setInput(getPart(), null); //TODO del autoup
                  return;
                }
                if (par.isDisposed()) par = par.getTreeDB().getRootNode();
                setInput(getPart(), new StructuredSelection(par)); //TODO del autoup
              }catch(NodeNotFoundException e){
              }
            }
          }
        }

        @Override
        public void attributeChange(final TreeNode source, String what, Attribute old) {
          if (page.getControl().isDisposed()) {
            return;
          }
          TreeDB db = source.getTreeDB();
          if (db.getStorage() instanceof TransactionStorage) {
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
          if (page.getControl().isDisposed()) {
            return;
          }
          TreeDB db = source.getTreeDB();
          if (db.getStorage() instanceof TransactionStorage) {
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
  private IUIStateChangeListener list = new IUIStateChangeListener() {

    @Override
    public void newActiveNodes(FSId source, SelectionRecord nodes) {
      if(source.getEventSource() == EventSource.PROPERTIES)
        return;
      //TODO seems not to get here, test it
      TreeNode node = nodes.getFirstNode();
      if(node!=null)//TODO what if no any?
        openNode(node);
    }
  };

  private void openNode(final TreeNode node) {
    final TextViewer source = SourcePropertySection.this.source;
    EclipseAPI
        .getDisplay()
        .asyncExec(
            new Runnable() {
              @Override
              public void run() {
                if(source.getControl().isDisposed()||node == null || node.getTreeDB().isDisposed())
                  return;
                openNodeSafe(TreeNodePropertiesTab.toRequirementOrNode(node));
              }
            });
  }
  
  @Override
  public void aboutToBeShown() {
    super.aboutToBeShown();
    SelectionManager sman = SelectionManager.getOne();
    //get selection of last active properties
    if(sman.getActiveSource() == EventSource.PROPERTIES) {//if props is active
      FSId id = sman.getFirstActiveSource(EventSource.PROPERTIES);
      if(id!=fsid)
        sman.changeActiveSource(SourcePriority.PROPERTIES, EventSource.PROPERTIES, fsid, isSelectionSource(), true);
    }
    SelectionRecord sr = sman.getSelection();
//  sman.registerInstance(fsid.getEventSource(), fsid.getObject(), fsid);
  //sman.activateSource(SourcePriority.SOURCE_CHANGE, EventSource.PROPERTIES, fsid, isSelectionSource(), false);
    if(sr != null) {
      TreeNode sel = sr.getFirstNode();
      if(sel != null)
        if(target == null || !sel.getUUId().equals(target.getUUId())) {
          openNodeSafe(sel);
        }
    }
  }

  private void openNodeSafe(TreeNode node) {
    if (target != null) {
      target.removeGuiChangeListener(ncl);
      target.getTreeDB().removeTransactionListener(itl);
    }
    if (node == null) return;
    target = node;
    SelectionManager.getOne().updateSelection(SourcePriority.PROPERTIES, fsid, target);
    target.addGuiChangeListener(ncl);
    target.getTreeDB().addTransactionListener(itl);
    File f = null;
    try {
      f = (File) target.getTreeDB().getStorage().getJSONResource(node.getUUId(), LockType.NO_LOCK);
    } catch (LockIsBusy e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    if (f == null || !f.exists()) {
      source.setInput(new Document(""));
      return;
    }
    File ff = f;
    final TextViewer source = SourcePropertySection.this.source;
    WorkspaceJob j = new WorkspaceJob("File content loading for "+f){

      @Override
      public IStatus runInWorkspace(IProgressMonitor monitor) {
        if(!ff.exists())
          return Status.CANCEL_STATUS;
        Writer writer = new StringWriter();
        char[] buffer = new char[1024];
        try {
          InputStream in = new FileInputStream(ff);
          Reader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
          try{
            int n;
            while ((n = reader.read(buffer)) != -1) {
              writer.write(buffer, 0, n);
            }
            EclipseAPI.getDisplay().asyncExec(new Runnable(){
              @Override
              public void run() {
                if(source.getControl().isDisposed() || writer == null)
                  return;
                source.setInput(new Document(writer.toString()));
              }
            });
          }finally{
            try{
              reader.close();
            }catch(IOException e){
              RLogger.logError(e);
            }
            in.close();
          }

        } catch (Exception e) {
          RLogger.logError(e);
        }
        return Status.OK_STATUS;
      }
      
    };
    j.schedule();
    //page.getControl().pack();
    //source.getControl().forceFocus();
  }
  @Override
  public void dispose() {
    super.dispose();
    SelectionManager.getOne().unregisterInstance(fsid);
    SelectionManager.getOne().removeSourceListener(EventSource.PROPERTIES, fsid, list );
  }

  @Override
  public FSId getFSId() {
    return fsid;
  }

  @Override
  public boolean isSelectionSource() {
    return false;
  }
}
