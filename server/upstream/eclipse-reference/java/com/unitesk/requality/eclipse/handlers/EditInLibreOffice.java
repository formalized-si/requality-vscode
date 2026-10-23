package com.unitesk.requality.eclipse.handlers;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.NullProgressMonitor;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.eclipse.core.EclipseTracker;
import com.unitesk.requality.eclipse.core.ResourceStorage;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TextNode;
import com.unitesk.requality.nodetype.VirtualNode;
import com.unitesk.requality.report.ReportGenerator;
import com.unitesk.requality.report.template.Template;
import com.unitesk.requality.report.template.TemplateFile;
import com.unitesk.requality.report.template.TemplatesManager;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.cli.LibreCLI;
import com.unitesk.requality.tool.cli.RequalityCLI;

public class EditInLibreOffice extends AbstractHandler {
  private static final TreeNode[] NO_NODES = CoreUtils.NO_NODES;
  boolean cnsl = false;

  private static void recDelete(File f, boolean force) {
    if (f.isDirectory()) for (File f2 : f.listFiles()) recDelete(f2, force);
    else if (force) f.delete();
    else f.deleteOnExit();
  }

  String librepath;

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (nodes == null || nodes.fullSelection.size() == 0) return null;
    WorkspaceJob wj = new WorkspaceJob("Edit "+nodes+" in LibreOffice") {
      
      @Override
      public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException {
        Collection<TreeNode>nds1=nodes.getOrDefault(Requirement.TYPE_NAME, Collections.emptyList()),
            nds2=nodes.getOrDefault(TextNode.TYPE_NAME, Collections.emptyList());
        Collection<TreeNode> allNodes = nds1;
        if(nds2.size()>0)
          if(allNodes.size()==0)
            allNodes = nds2;
          else {
            allNodes = new LinkedList<>();//TreeNode[nds1.length+nds2.length];
            int i = 0;
            for(TreeNode node:nds1)
              allNodes.add(node);
            for(TreeNode node:nds2)
              allNodes.add(node);
          }
        Set<TreeDB> dbs = new HashSet<>();
        IWorkbench w = PlatformUI.getWorkbench();
        
        for(TreeNode node:allNodes) {
          TreeDB dba = node.getTreeDB();
          dbs.add(dba);
          ResourceStorage rs = EclipseTracker.getResourceStorage(dba);
          IFolder tempFolder = rs.getResourceFolder(dba.getRootNode().getUUId());
        
          if (!tempFolder.exists()) {
            try{
              tempFolder.refreshLocal(IResource.DEPTH_INFINITE, null);
            }catch(Exception e){
              
            }
            if (!tempFolder.exists()) {
              try {
                tempFolder.create(false, true, null);
              } catch (CoreException e) {
                // TODO Auto-generated catch block
                RLogger.logError(e);
              }
            }
          }
          cnsl = false;
          librepath = LibreCLI.getDefault().getPath();
          if (librepath == null || librepath.isEmpty())
            w.getDisplay()
                .syncExec(
                    new Runnable() {
      
                      @Override
                      public void run() {
                        Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
                        InputDialog id =
                            new InputDialog(
                                shell,
                                "Enter path to libreoffice",
                                "Please enter path to libreoffice to corresponding field.",
                                librepath,
                                null);
                        if (id.open() == InputDialog.OK) {
                          librepath = id.getValue();
                          if (librepath.startsWith("\"")) librepath = librepath.substring(1);
                          if (librepath.endsWith("\""))
                            librepath = librepath.substring(0, librepath.length() - 1);
      
                        } else cnsl = true;
                      }
                    });
          if (cnsl) {
            return null;
          }
      
          Map<String, Object> map = new HashMap<>();
          map.put(
              "attrFilter",
              new ArrayList<String>() {
                /** */
                private static final long serialVersionUID = 5462662775364582819L;
      
                {
                  add("Ref");
                }
              });
          map.put(VirtualNode.ATTR_COLLAPSEVNODE, true);
          try {
            File tempfl =
                new File(
                    EclipseTracker.getResourceStorage(dba)
                            .getResourceFolder(dba.getRootNode().getUUId())
                            .getLocationURI()
                            .toURL()
                            .getPath()
                        + IPath.SEPARATOR
                        + "tempFolder"
                        + System.currentTimeMillis());
            tempfl.mkdirs();
            if (tempfl.exists()) recDelete(tempfl, true);
            Template t =
                TemplatesManager.getOne(node.getTreeDB()).getTemplates()//in theory someone can replace the template for project
                    .get("com.unitesk.requality.reports.reqSpecification");
            try {
              TreeDB db = dba; 
              for (TemplateFile tm : t.getTemplates()) {
                File f =
                    new File(
                        tempfl.getAbsolutePath()
                            + Path.SEPARATOR
                            + tm.getOutputFileName(null, db, node, null));//generation without report
                if (f.exists()) f.delete();
              }
              boolean b = false;
              try {
                b = db.startTransaction("generate report on "+node);
                ReportGenerator.generateReport(
                  (Requirement) node, t, tempfl.toURI().toString(), map,
                        new NullProgressMonitor());
              }finally {
                if(b) 
                  db.rollback();
              }
            } catch (Exception e) {
              RLogger.logError(e);
            }
      
            try {
              TreeDB db = dba;
              String name =
                  t.getTemplates().get(0).getOutputFileName(null, db, node, null);//generation without report
              Date dt = new Date();
              DateFormat dft = SimpleDateFormat.getDateInstance();
      
              String path =  tempfl.toURI().toString() + dft.format(dt) + CoreUtils.URLEncode(name);
              //File f = new File(tempfl.getAbsolutePath()+Path.SEPARATOR+dft.format(dt)+name);
              RequalityCLI.copyfile(
                  URI.create(tempfl.toURI().toURL().toString() + CoreUtils.URLEncode(name)), URI.create(path));
              LibreCLI.getDefault().setTempPath(librepath);
              LibreCLI.getDefault().handleCommand(false,false, path);
              
              if (cnsl) {
                LibreCLI.getDefault().setTempPath(null);
                return null;
              }
              
              if (tempfl.exists()) recDelete(tempfl, false);
            } catch (IOException e) {
            }
          } catch (Exception e1) {
            e1.printStackTrace();
          }
        }
        LibreCLI.getDefault().saveSettings();
        LibreCLI.getDefault().setTempPath(null);
        return Status.OK_STATUS;
      }
    }; 
    wj.schedule();
    return null;
  }

  @Override
  public boolean isEnabled() {
    SelectionRecord nodes = SelectionManager.getOne().getSelection(EventSource.EXPLORER);
    if (nodes == null || nodes.fullSelection.size() == 0) {
      return false;
    }
    TreeStateManager tsm = null;
    TreeDB db = null;
    Collection<TreeNode>nds=nodes.getOrDefault(Requirement.TYPE_NAME,Collections.emptyList()),
    		nds2=nodes.getOrDefault(TextNode.TYPE_NAME, Collections.emptyList());
    for (TreeNode node : nds) {
    	if(db == null || node.getTreeDB()!=db) {
    		db = node.getTreeDB();
    		if(tsm == null)
    		  tsm = TreeStateManager.getOne(db.getTracker());
    		if(!tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db))) 
    			return false;
    	}
    }
    for (TreeNode node : nds2) {
    	if(db == null || node.getTreeDB()!=db) {
    		db = node.getTreeDB();
    		if(tsm == null || !tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db))) 
    			return false;
    	}
    }
    if((nds.size()+nds2.size())>4)
      return false;//Ignore calls on more than 4 nodes //TODO but why oO?
    return true;
  }
}
