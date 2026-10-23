package com.unitesk.requality.core.storage.cache;

import java.io.File;
import java.nio.file.Path;
import java.util.Iterator;

import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.service.ResourcesServlet;
import com.unitesk.requality.core.storage.OperationResult;
import com.unitesk.requality.nodetype.ReportData;
import com.unitesk.requality.report.ReportOutProcessor;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

public class ReportProcessedCache extends CacheManipulator<TreeDB>{
  private static final String NAME = "ReportProcessedFilesCache";
  private String[] SETTS_NAME = new String[] {"_reportProcessedCache"};
  private String[] SETTS_VERSION = new String[] {"1.4.1"};
  @Override
  protected String[] getSettingsNames(TreeDB db) {
    return SETTS_NAME;//no caches
  }

  @Override
  protected String[] getVersions() {
    return SETTS_VERSION;
  }

  @Override
  protected boolean isCrossProjectCache() {
    return false;
  }
  @Override
  public void clearNodeCache(TreeDB db, SettingsManipulator settingsStore, TreeNode node) {
    super.clearNodeCache(db, settingsStore, node);
    Path p = CoreUtils.getCacheDir(db);
    if(p!=null) {
      java.io.File f = p.resolve("reports").toFile();
      if(f.exists()) {
        f = f.toPath().resolve(node.getSUUId()).toFile();
        if(f.exists())
          CoreUtils.delete(f, true, false);
      }
    }
  }
  @Override
  protected OperationResult innerDropCache(TreeDB db, SettingsManipulator cman) {
    Path p = CoreUtils.getCacheDir(db);
    if(p!=null) {
      java.io.File f = p.resolve("reports").toFile();
      if(f.exists())
        CoreUtils.delete(f, true, false);
    }
    return OperationResult.SUCEEDED;
  }

  @Override
  protected OperationResult innerInitFirst(TreeDB db, SettingsManipulator cman, IProgressMonitor mon) {
    DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(db.getNode("/Reports"),true,false,true);
    Iterator<TreeNode> it = dftw.iterator();
    while(it.hasNext()) {
      TreeNode fnode = it.next();
      if(fnode.getType().equals(ReportData.TYPE_NAME)) {////////////////////////////////////////////////////
        ReportData rd = (ReportData) fnode;
        for(String resName:rd.listResources()) {
          String lowName = resName.toLowerCase();
          if(lowName.endsWith("htm") || lowName.endsWith("html")){
            File proceedReportFile = ResourcesServlet.getProcessedReportFile(fnode, resName);
            java.nio.file.Path tempIdFile = proceedReportFile.getParentFile().toPath()
                .resolve(proceedReportFile.getName() + "_tempId");
            if(!proceedReportFile.exists() || ReportOutProcessor.isOutdated(tempIdFile, resName)) {
              ReportOutProcessor.shelduleReportUpdate(fnode, resName, null, null, null, null);
            }
          }
        }
      }
    }
    return OperationResult.SUCEEDED;
  }

  @Override
  public void close() {
  }

  @Override
  public OperationResult innerInitNotFirst(TreeDB db, SettingsManipulator cman, IProgressMonitor mon) {
    return innerInitFirst(db,cman,mon);
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  protected boolean isNonRevision() {
    return true;
  }

}
