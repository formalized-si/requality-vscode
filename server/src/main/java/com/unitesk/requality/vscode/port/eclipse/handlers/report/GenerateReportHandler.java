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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.commands.IHandler;
import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IFolder;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.WorkspaceJob;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchWindow;

import com.unitesk.requality.core.exception.WorkspaceOperationPerforming;
import com.unitesk.requality.core.lock.LockIsBusy;
import com.unitesk.requality.core.lock.LockType;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IGenerateReportHandler;
import com.unitesk.requality.core.operation.IGenerateReportHandlerProvider;
import com.unitesk.requality.core.operation.ReportGenResult;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.storage.DBNamesManager;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.vscode.port.eclipse.Activator;
import com.unitesk.requality.vscode.port.eclipse.api.EclipseAPI;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseProgressMonitor;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker;
import com.unitesk.requality.nodetype.ReportData;
import com.unitesk.requality.nodetype.ReportSettings;
import com.unitesk.requality.report.template.Template;
import com.unitesk.requality.tool.CoreUtils;
// TODO report undo
public class GenerateReportHandler extends AbstractHandler
    implements IHandler, IGenerateReportHandler {
  private static TreeNode[]NO_NODES = new TreeNode[0];

  private com.unitesk.requality.core.operation.GenerateReportHandler base =
      new com.unitesk.requality.core.operation.GenerateReportHandler() {
        @Override
        public void openInformation(String title, String message) {
          Shell s = getShell();
          if (s == null) return;
          MessageDialog.openInformation(s, title, message);
        }

        @Override
        public void openError(String title, String message) {
          Shell s = getShell();
          if (s == null) return;
          MessageDialog.openError(s, title, message);
        }

        @Override
        public void openReport(ReportData res) {
          OpenReportHandler.openReport(res);
        }
      };

  public static IContainer getReportFolder(TreeNode node) {
    IFile nodeFile = null;
    try {
      nodeFile = (IFile) EclipseTracker.getResourceByNode(node, LockType.LOCK_WAIT);
    } catch (LockIsBusy e) {
      RLogger.logDebug(CoreUtils.getTrace(e),7);
    }
    if(nodeFile == null)
      return null;
    IContainer res = nodeFile.getParent().getFolder(Path.fromOSString(node.getId() + ResManager.RESOURCES_SUFFIX));
    return res;
  }
  public static com.unitesk.requality.core.operation.IProgressMonitor wrapMonitor(IProgressMonitor monitor){
    if (monitor != null)
      return
          new EclipseProgressMonitor(monitor);
    return null;
  }

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    SelectionRecord mnodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    Collection<TreeNode> nodes = mnodes.getOrDefault(ReportSettings.TYPE_NAME, Collections.emptyList());
    StringBuilder sb = new StringBuilder("Generate report ");
    for(TreeNode nd:nodes)
      if(nd.getType().equals(ReportSettings.TYPE_NAME)){
        if(sb.length()>16)
          sb.append(", ");
        sb.append("(");
        sb.append(nd.getTreeDB().getProjectName());
        sb.append(") ");
        sb.append(nd.getUserVisibleName());
      }
    String wo = CoreUtils.getWorkspaceOperation();
    if(!wo.isEmpty())
      EclipseAPI.getDisplay().asyncExec(new Runnable(){
        
        @Override
        public void run() {
          MessageDialog.openError(EclipseAPI.getDisplay().getActiveShell(),
              "Report generation error","Cannot generate report: operation '"+wo+"' is performing now.");
        }
      });
    else{
      WorkspaceJob wj = getReportJob(mnodes, sb.toString(), event.getParameters(), true);
      wj.schedule();
    }
    //nodes.get(0).getTreeDB().commit();
    return null;
  }
  public WorkspaceJob getReportJob(SelectionRecord nodes, String sb, Map<?, ?> par, boolean openAfter){
    final com.unitesk.requality.core.operation.GenerateReportHandler base =
        GenerateReportHandler.this.base;
    return new WorkspaceJob(sb){
      int it = 0;
      @Override
      public boolean shouldRun() {
        if(super.shouldRun()) {
          if(CoreUtils.busyCondition(nodes) || !CoreUtils.getWorkspaceOperation().isEmpty())
          {
            if(it++<5) 
              this.schedule(500);//try to resheldule
            return false;
          }
          return true;
        }
        return false;
      }
      @Override
      public boolean shouldSchedule() {
        for(TreeNode nd:nodes.fullSelection)
          if(nd.getTreeDB().isDisposed())
            return false;
        return true;
      }
      @Override
      public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
        Collection<TreeNode> nds = nodes.getOrDefault(ReportSettings.TYPE_NAME, Collections.emptyList());
        Collection<ReportSettings>reps = toReportSetts(nds);
        CoreUtils.lockWorkspace(sb.toString());
        try {
          base.execute(false, reps, par, wrapMonitor(monitor), openAfter);
        }catch(Exception e){
          RLogger.logError(e);
          EclipseAPI.getDisplay().asyncExec(new Runnable(){
          
            @Override
            public void run() {
              MessageDialog.openError(EclipseAPI.getDisplay().getActiveShell(),
                  "Report generation error","Cannot generate report: "+e.getMessage());
            }
          });
        }
        finally{
          CoreUtils.unlockWorkspace();//seems to be locked for a moment
        }
        return Status.OK_STATUS;
      }
    };
  }
  private static Shell getShell() {
    if (Activator.getDefault() == null) return null;
    IWorkbench w = Activator.getDefault().getWorkbench();
    if (w == null) return null;
    IWorkbenchWindow win = w.getActiveWorkbenchWindow();
    if (win == null) return null;
    return win.getShell();
  }

  @Override
  public ReportGenResult[] execute(boolean lockWorkspace, TreeDB db,
      com.unitesk.requality.core.operation.IProgressMonitor monitor, boolean openAfter, ReportSettings... reportS)
      throws WorkspaceOperationPerforming {
    ReportGenResult[] res = base.execute(lockWorkspace, db, monitor, openAfter, reportS);
    for(ReportGenResult r:res) {
      if (r == null) continue;
      if (r.savePath != null) {
        IProject project = EclipseTracker.getTracker(DBNamesManager.DEFAULT).getProject(db);
      //TODO check
        IPath path = Path.fromOSString(r.savePath.toString()).makeRelativeTo(project.getLocation());

        IFolder folder = project.getFolder(path);
        try {
          folder.refreshLocal(IResource.DEPTH_INFINITE, null);
        } catch (CoreException e) {
          RLogger.logError(e);
        }
      }
    }
    return res;
  }
  public ReportGenResult[] generateReport(boolean lockWork, ReportSettings reportS, com.unitesk.requality.core.operation.IProgressMonitor mon, boolean openAfter) throws WorkspaceOperationPerforming {
    return execute(lockWork, reportS.getTreeDB(), mon, openAfter, reportS);
  }

  public Template getTemplate(ReportSettings report) {
    return base.getTemplate(report);
  }

  public Template getTemplate(TreeDB db, String tid) {
    return base.getTemplate(db, tid);
  }
  //remove old image from report before new generation
  @Override
  public void clearOldImages(ReportSettings nde) {
    base.clearOldImages(nde);
  }

  @Override
  public boolean isEnabled() {
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if(CoreUtils.busyCondition(nodes))
      return false;
    Collection<ReportSettings> reports = toReportSetts(nodes.getOrDefault(ReportSettings.TYPE_NAME, Collections.emptyList()));
    return base.isEnabled(true, reports);
  }

  private static Collection<ReportSettings> toReportSetts(Collection<TreeNode> tnd) {
    if(tnd == null || tnd.isEmpty())
      return Collections.emptyList();
    List<ReportSettings> ret = new LinkedList<>();
    for(TreeNode node: tnd)
      ret.add((ReportSettings) node);
    return ret;
  }
  @Override
  public Object execute(boolean lockWork, Collection<ReportSettings> nodes, Map<?, ?> par, com.unitesk.requality.core.operation.IProgressMonitor monitor, boolean openAfter) throws Exception {
    String wo = CoreUtils.getWorkspaceOperation();
    if(!wo.isEmpty())
      EclipseAPI.getDisplay().asyncExec(new Runnable(){
        
        @Override
        public void run() {
          MessageDialog.openError(EclipseAPI.getDisplay().getActiveShell(),
              "Report generation error","Cannot generate report: operation '"+wo+"' is performing now.");
        }
      });
    else{
      if(lockWork)
        CoreUtils.lockWorkspace("Generate report for "+Arrays.toString(nodes.toArray()));
      try{
        return base.execute(false, nodes, par, monitor, openAfter);
      }finally{
        if(lockWork)
          CoreUtils.unlockWorkspace();
      }
    }
    return false;

  }

  @Override
  public boolean isEnabled(boolean lockWork, Collection<ReportSettings> nodes) {
    return base.isEnabled(lockWork, nodes);
  }
  @Override
  public void openError(String string, String string2) {
    base.openError(string, string2);
    
  }
  @Override
  public void openInformation(String string, String string2) {
    base.openInformation(string, string2);
  }
  @Override
  public void openReport(ReportData rd) {
    base.openReport(rd);
  }
  @Override
  public String getLastError() {
    return base.getLastError();
  }
  private static IGenerateReportHandlerProvider provider = null;
  public static IGenerateReportHandlerProvider getProvider() {
    if(provider == null)
      provider = new IGenerateReportHandlerProvider() {
      
        @Override
        public IGenerateReportHandler getReportHandler() {
          return new GenerateReportHandler();
        }
      };
    return provider;
  }
}
