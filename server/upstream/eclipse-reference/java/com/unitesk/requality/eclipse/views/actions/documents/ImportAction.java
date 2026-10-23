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

package com.unitesk.requality.eclipse.views.actions.documents;

import java.util.Collection;

import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.eclipse.editors.NodeEditorInput;
import com.unitesk.requality.eclipse.editors.browser.ReqMarker;
import com.unitesk.requality.eclipse.ui.cnf.CNFAction;
import com.unitesk.requality.eclipse.wizards.DocumentImportWizard;
import com.unitesk.requality.nodetype.DocFolder;
import com.unitesk.requality.nodetype.Document;

public class ImportAction extends CNFAction {
  private static final String IMPORT = "Import Document...";

  public static String initalPath; // stores last open path

  public ImportAction(ISelectionProvider provider) {
    setText(IMPORT);
    setToolTipText(IMPORT);
    String IMG_ETOOL_IMPORT_WIZ = "IMG_ETOOL_IMPORT_WIZ"; // This is hack.
    setImageDescriptor(
        PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(IMG_ETOOL_IMPORT_WIZ));
  }

  public void run() {
    DocumentImportWizard wizard = new DocumentImportWizard();
    wizard.init(PlatformUI.getWorkbench(), null);
    WizardDialog dialog =
        new WizardDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), wizard);
    dialog.open();
  }

  public static void openDocument(String docId, TreeDB db) {
    Document doc = (Document) db.getNode(docId, Path.QID);
    IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
    if (page != null) {
      try {
        page.openEditor(
            new NodeEditorInput(doc, ReqMarker.ID, null), "com.unitesk.requality.editors.browser.ReqMarker", true);
      } catch (PartInitException e) {
        RLogger.logError(e);
      }
    }
  }

  private DocFolder getDocFolder() {
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (nodes == null || nodes.fullSelection.size() != 1) return null;
    Collection<TreeNode>nds = nodes.get(DocFolder.TYPE_NAME);
    if(nds == null || nds.size()<1)
      return null;
    TreeNode obj = nds.iterator().next();

    if (obj instanceof DocFolder) {
      return (DocFolder) obj;
    }
    return null;
  }
  @Override
  public boolean isEnabled() {
    DocFolder df = getDocFolder();
    if(df == null)
      return false;
    TreeDB db = df.getTreeDB();
    TreeStateManager tsm = TreeStateManager.getOne(ResManager.getManager().getTracker());
    return tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db));
  }
}
