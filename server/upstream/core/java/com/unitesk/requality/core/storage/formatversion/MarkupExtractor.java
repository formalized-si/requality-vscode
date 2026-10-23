package com.unitesk.requality.core.storage.formatversion;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.document.OldMarkupProcessor;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Requirement;

public class MarkupExtractor {
  public static void extract(TreeNode node, Map<String, List<NodeDesc>> newLoc) {
    OldMarkupProcessor omp = new OldMarkupProcessor();
    omp.setNoTransactionsFlag(true);
    omp.setShowMessages(false, false);
    omp.newLocations = newLoc;
    omp.isLocExternal = true;
    try {
      omp.process((Document) node, node.getTreeDB().getNode(Requirement.getTypeRootQId(),Path.QID), null, null, null);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }
}
