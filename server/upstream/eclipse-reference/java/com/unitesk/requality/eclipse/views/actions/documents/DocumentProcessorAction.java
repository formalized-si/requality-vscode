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
package com.unitesk.requality.eclipse.views.actions.documents;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import com.unitesk.requality.core.descriptors.DocumentProcessorDescriptor;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.document.IDocProcessorWithWizard;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.document.IDocumentProcessor;
import com.unitesk.requality.eclipse.RequalityPlugin;
import com.unitesk.requality.eclipse.core.EclipseProgressMonitor;
import com.unitesk.requality.eclipse.core.ReqSettings;
import com.unitesk.requality.eclipse.editors.NodeEditorInput;
import com.unitesk.requality.eclipse.editors.browser.ReqMarker;
import com.unitesk.requality.eclipse.ui.cnf.CNFAction;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.message.RMessages;

public class DocumentProcessorAction extends CNFAction {
  private DocumentProcessorDescriptor desc;
  private PrintStream pw;
  
  public DocumentProcessorAction(DocumentProcessorDescriptor desc, ISelectionProvider provider, PrintStream pw) {
    // TODO:
    // super(provider);
    this.desc = desc;
    this.pw = pw;
    setText(desc.getName());
  }

  @Override
  public void run() {
    final TreeDB db = getTreeDB();
    if (db == null)
      return;
    final Collection<TreeNode> docs = getSelectedDocuments();
    final IDocumentProcessor proc = desc.getProcessor();
    final PrintStream pw = this.pw;
    final WorkspaceJob wj =
        new WorkspaceJob("Processing " + Arrays.toString(docs.toArray())) {
          @Override
          public IStatus runInWorkspace(final IProgressMonitor monitor) throws CoreException {
            try {
              final Requirement rootReq =
                  (Requirement) db.getNode(Requirement.getTypeRootQId(), Path.QID);
              for (final TreeNode tr : docs) {
                Document doc = (Document) tr;
                //TODO call wizard
                ReqSettings rs = new ReqSettings(proc.getClass().getCanonicalName());
                rs.load(proc.getClass().getCanonicalName(), doc.getTreeDB());
                Map<String,Object> sw = null;
                if(proc instanceof IDocProcessorWithWizard)
                  sw = ((IDocProcessorWithWizard)proc).showWizard(doc);
                if(sw!=null)
                  rs.setMap(sw);//TODO move to wizard
                if (!proc.configure(doc, rs.getMap(), pw)) {
                  continue;
                }
                com.unitesk.requality.core.operation.IProgressMonitor mon = null;
                if (monitor != null)
                  mon =
                      new EclipseProgressMonitor(monitor);
                proc.process(doc, rootReq, pw, null, mon);
                if (mon != null) 
                	mon.doneAll();
                //doc.resetCache();
                //TODO check
              }

            } catch (IOException ie) {
              RLogger.logError(ie);
              if(monitor!=null)
                monitor.done();
              return Status.CANCEL_STATUS;
            } catch (final Exception e) {
              RLogger.logError(e);
              RMessages.openError(
                  "Document Processing Error",
                  e.getClass().getName() + ": " + e.getMessage(),null, false);
              if(monitor!=null)
                monitor.done();
              return Status.CANCEL_STATUS;
            }
            PlatformUI.getWorkbench()
                .getDisplay()
                .asyncExec(
                    new Runnable() {
                      public void run() {
                        for (TreeNode tr : docs) {
                          Document d = (Document) tr;
                          if (closeEditor(d)) {
                            IWorkbenchPage page =
                                PlatformUI.getWorkbench()
                                    .getActiveWorkbenchWindow()
                                    .getActivePage();
                            if (page != null) {
                              try {
                                page.openEditor(
                                    new NodeEditorInput(d, ReqMarker.ID, null),
                                    "com.unitesk.requality.editors.browser.ReqMarker",
                                    false);
                              } catch (PartInitException e) {
                                RLogger.logError(e);
                              }
                            }
                          }
                        }
                        if(monitor!=null)
                          monitor.done();
                      }
                    });
            return Status.OK_STATUS;
          };
        };
    wj.setRule(ResourcesPlugin.getWorkspace().getRoot());
    wj.schedule();
  }

  private boolean closeEditor(Document doc) {
    try {
      IEditorReference eRef = RequalityPlugin.findMarkEditorForDocument(doc.getQualifiedId(true));
      if (eRef == null) return false;
      ((ReqMarker) eRef.getEditor(false)).close(false);
      return true;
    } catch (PartInitException e) {
      RLogger.logError(e);
    }
    return false;
  }

  private TreeDB getTreeDB() {
    Collection<TreeNode> docs = getSelectedDocuments();
    if (docs != null && docs.size()>0) {
      return docs.iterator().next().getTreeDB();
    }
    return null;
  }

  @Override
  public boolean isEnabled() {
    return getSelectedDocuments() != null;
  }
 
  private Collection<TreeNode> getSelectedDocuments() {
    //<Document> docs = new ArrayList<>();
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if(nodes.fullSelection.size()!=1)
      return null;//more non document nodes
    Collection<TreeNode> docs = nodes.get(Document.TYPE_NAME);
    if(docs == null || docs.size() == 0)
      return null;
    TreeDB db = null;
    for (TreeNode node : docs) {
      if (db == null || db!=node.getTreeDB()){
        db = node.getTreeDB();
        TreeStateManager tsm = TreeStateManager.getOne(ResManager.getManager().getTracker());
        if(!tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db)))
          return null;
      }
      if (db != node.getTreeDB()) return null;
    }
    return docs;
  }
}
