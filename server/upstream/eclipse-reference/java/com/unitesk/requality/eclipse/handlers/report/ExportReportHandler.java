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

package com.unitesk.requality.eclipse.handlers.report;

import java.io.File;
import java.util.Collection;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.ui.PlatformUI;

import com.unitesk.requality.core.jobs.JobManager;
import com.unitesk.requality.core.jobs.RunnableWithMonitor;
import com.unitesk.requality.core.lock.LockIsBusy;
import com.unitesk.requality.core.lock.LockType;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.operation.IReportExportOperation;
import com.unitesk.requality.core.operation.ReportExportHandler;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.eclipse.api.EclipseAPI;
import com.unitesk.requality.eclipse.core.EclipseTracker;
import com.unitesk.requality.nodetype.ReportData;
import com.unitesk.requality.tool.CoreUtils;

// TODO report undo
public class ExportReportHandler extends AbstractHandler implements IHandler,IReportExportOperation {
  static IReportExportOperation base = new ReportExportHandler();
  private static RLogger ls = new RLogger(){
    @Override
    public void lE(String s) {
      MessageDialog.openError(
          PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
          "Report export error",
          s);
    }
    @Override
    public void lD(String s) {
      System.out.println("debug: "+s); 
    }
    @Override
    public void lI(String s) {
      System.out.println("info: "+s); 
    }
  };
  static{
    base.setLogger(ls);
  }
  
  private static com.unitesk.requality.core.operation.ReportExportHandler one = new ReportExportHandler();
  public static ReportExportHandler getOne(){
    return one;
  }
  private static String DIALOG_TITLE = "Report Export",
      DIALOG_MESSAGE = "Select folder for report(s) to be exported to";

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

  /* (non-Javadoc)
   * @see com.unitesk.requality.eclipse.handlers.report.IReportExportOperation#execute(org.eclipse.core.commands.ExecutionEvent)
   */
  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (nodes == null) return null;
    Collection<TreeNode>nds = nodes.get(ReportData.TYPE_NAME);
    if(nds==null || nds.size()<1)
      return null;

    EclipseAPI.getDisplay().asyncExec(new Runnable() {

      @Override
      public void run() {
        DirectoryDialog dd =
            new DirectoryDialog(EclipseAPI.getDisplay().getActiveShell());
        dd.setText(DIALOG_TITLE);
        dd.setMessage(DIALOG_MESSAGE);
        String exportDir = dd.open();
        if (exportDir == null) 
          return ;
        File f = new File(exportDir);
        if (!f.exists()) {
          MessageDialog.openError(
              EclipseAPI.getDisplay().getActiveShell(),
              "Report export",
              "Target folder does not exists.");
          return ;
        }
        JobManager.jm.startJob("Export report "+nds.iterator().next().getUserVisibleName(), new RunnableWithMonitor() {
          
          @Override
          public void run(IProgressMonitor pm) {
            
            exportReport(nds, exportDir, true);
            String s = nds.size()>1?"s":"";
            StringBuilder sb = new StringBuilder();
            for(TreeNode node:nds){
              if(sb.length()>0)
                sb.append(", ");
              sb.append("'");
              sb.append(node.getUserVisibleName());
              sb.append("'");
            }
            EclipseAPI.getDisplay().asyncExec(new Runnable() {
    
              @Override
              public void run() {
                MessageDialog.openInformation(EclipseAPI.getDisplay().getActiveShell(), "Export Report", "Export of report"+s+" "+sb.toString()+" to '"+exportDir+"' is completed."); 
              }
              
            });
          }
        },true);
      }
    });
    return null;
  }
  @Override
  public void handleError(String string, Exception e) {
    MessageDialog.openError(
        PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
        "Report export",
        (!string.isEmpty()?string+"\n":"")+CoreUtils.getTrace(e));
  }

  /* (non-Javadoc)
   * @see com.unitesk.requality.eclipse.handlers.report.IReportExportOperation#isEnabled()
   */
  @Override
  public boolean isEnabled() {
    SelectionRecord selected = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (selected == null || selected.fullSelection.size() == 0) return false;
    Collection<TreeNode> reps = selected.get(ReportData.TYPE_NAME);
    if(reps == null || reps.size() == 0)
      return false;
    TreeDB db = null;
    TreeStateManager tsm = null;
    for (TreeNode node : reps) {
      if(db == null || node.getTreeDB()!=db) {
    	db = node.getTreeDB();
    	if(tsm == null)
    	  tsm = TreeStateManager.getOne(db.getTracker());
    	if(!tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db)))
    	  return false;
      }
      if (node.attrs().getAttribute(ReportData.ATTR_LAST_GENERATED) == null) return false;
      if (!((ReportData) node).reportExists()) return false;
    }
    return super.isEnabled();
  }

  @Override
  public void exportReport(Collection<TreeNode> nodes, String exportDir, boolean sepFolder) {
    one.exportReport(nodes, exportDir, sepFolder);
  }

  @Override
  public void setLogger(RLogger ls) {
  }
}
