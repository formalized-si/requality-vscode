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

package com.unitesk.requality.vscode.port.eclipse.handlers.report;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;

import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.runtime.WorkspaceJob;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.workbench.IEditorPart;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;
import com.unitesk.requality.vscode.ui.workbench.PartInitException;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.ide.IDE;

import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.lock.LockIsBusy;
import com.unitesk.requality.core.lock.LockType;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.vscode.port.eclipse.Activator;
import com.unitesk.requality.vscode.port.eclipse.RequalityPlugin;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker;
import com.unitesk.requality.vscode.port.eclipse.editors.NodeEditorInput;
import com.unitesk.requality.vscode.port.eclipse.handlers.OpenEditorHandler;
import com.unitesk.requality.vscode.port.eclipse.ui.editors.ReportViewer;
import com.unitesk.requality.vscode.port.eclipse.views.RequalityPreferences;
import com.unitesk.requality.nodetype.ReportData;
import com.unitesk.requality.report.template.Template;
import com.unitesk.requality.report.template.TemplatesManager;
import com.unitesk.requality.tool.CoreUtils;

public class OpenReportHandler extends OpenEditorHandler {
  public Object openOperation(ExecutionEvent event) throws ExecutionException {
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (nodes == null) return null;
    Collection<TreeNode>rds = nodes.get(ReportData.TYPE_NAME);
    if(rds == null||rds.size() == 0)
      return null;
    for (TreeNode node : rds) {
      ReportData rdata = (ReportData) node;
      openReport(rdata);
    }
    return null;
  }

  public static void openReport(ReportData rdata) {
    
    WorkspaceJob openr = new WorkspaceJob("Open report "+rdata.getUserVisibleName()){
      @Override
      public IStatus runInWorkspace(IProgressMonitor m) {
        Activator a = Activator.getDefault();
        if (a != null
            && a.getPreferenceStore().getBoolean(RequalityPreferences.NOT_OPEN_EDITORS)
            && RequalityPlugin.getBrowserType() == SWT.NONE) return Status.CANCEL_STATUS;
        String templateId = rdata.getTemplateId();
        Template tpl = null;
        TreeDB db = rdata!=null?rdata.getTreeDB():null;
        TemplatesManager tm = TemplatesManager.getOne(db); 
        if (templateId == null) {
          tpl = tm.getTemplate(Template.DEFAULT_ID);
          rdata.attrs().setConstAttribute(ReportData.ATTR_TEMPLATE, tpl.getId(), Flag.HIDDEN);
          rdata.commitAttributes();
        } else {
          tpl = tm.getTemplate(templateId);
        }
        URI savePath = rdata.getSaveDir();
        String href = null;
        if (rdata.getTemplateId() != null) {
            String lastGen = rdata.attrs().getStringAttributeValue(ReportData.ATTR_LAST_GENERATED);
            if (lastGen != null)
              try {
                href = new URI(savePath + CoreUtils.URLEncode(lastGen)).toString();
              } catch (URISyntaxException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
              }
          } 
        if (tpl != null && href == null) {
          //            IResource res = OpenReportHandler.getReportFolder(rdata)
          //                .getFile(
          //                        Path.fromOSString(tpl.getTemplates().get(0)
          //                                .getOutputFileName()));
          //if (!res.exists()) {
          //  GenerateReportHandler.generateReport(rdata);
          //}
          href =
              savePath
                  + "/"
                  + tm.getTplFileName(rdata,false, null);
          if ((href.length() == 0)) return Status.CANCEL_STATUS;
        }
        EclipseAPI.getDisplay().asyncExec(new Runnable(){
        
          @Override
          public void run() {
            IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
            if (page != null) {
              try {
                NodeEditorInput input = new NodeEditorInput(rdata, ReportViewer.ID, null);
                try {
                  IEditorPart e =
                      page.findEditor(input);
                  if (e != null && ((NodeEditorInput) e.getEditorInput()).getStorage().getNode() == rdata) {
                    page.bringToTop(e);
                    e.setFocus();
                    return;//tends to be more proper behaviour
                  }
                } catch (CoreException e) {
                  // TODO Auto-generated catch block
                  e.printStackTrace();
                }
                IDE.openEditor(
                    PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(),
                    input,
                    ReportViewer.ID,
                    true);
              } catch (PartInitException e) {
                e.printStackTrace();
              }
            }
          }
        });
        return Status.OK_STATUS;
      }
    };
    openr.schedule();
    
  }

  public static IContainer getReportFolder(ReportData rdata) {
    IFile nodeFile = null;
    try {
      nodeFile = (IFile) EclipseTracker.getResourceByNode(rdata, LockType.LOCK_WAIT);
    } catch (LockIsBusy e) {
      RLogger.logDebug(CoreUtils.getTrace(e),7);
    }
    if(nodeFile == null)
      return null;
    IContainer res =
        nodeFile.getParent().getFolder(Path.fromOSString(rdata.getId() + ResManager.RESOURCES_SUFFIX));
    return res;
  }

  @Override
  public String getEditorId() {
    return ReportViewer.ID;
  }
}
