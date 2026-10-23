package com.unitesk.requality.vscode.port.eclipse.views.properties;

import com.unitesk.requality.vscode.ui.jface.viewers.IFilter;
import com.unitesk.requality.core.api.Global;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.DocFolder;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.ReportData;
import com.unitesk.requality.nodetype.ReportFolder;
import com.unitesk.requality.nodetype.ReportSettings;

public class RepositoryFilter implements IFilter {
  public static String[] ALLOWED = new String[]{DocFolder.TYPE_NAME, ReportFolder.TYPE_NAME};
  public static String[] SHOWED = new String[]{DocFolder.TYPE_NAME, ReportFolder.TYPE_NAME, ReportData.TYPE_NAME, Document.TYPE_NAME, ReportSettings.TYPE_NAME};
  @Override
  public boolean select(Object toTest) {
    if(!(toTest instanceof TreeNode))
      return false;
    TreeNode node = (TreeNode)toTest;
    String tp = node.getType();
    try{
      for(String s:SHOWED)
        if(s.equals(tp))
          return Global.getRepoManager(node.getTreeDB()).getCVSClient()!=null;
    } catch (Exception e) {
    }
    return false;
  }

}
