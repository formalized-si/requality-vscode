package com.unitesk.requality.report;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.unitesk.requality.core.IMultiPartDataProvider;
import com.unitesk.requality.core.MultiPart;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.DocFolder;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

public class DocumentMultiPart implements IMultiPartDataProvider{
  MultiPart mp = null;
  @Override
  public MultiPart getMultiPart(TreeNode root, ReportUtils utils, ReportGenerationContext context) {
    if(mp == null)
      mp = new MultiPart() {
        @Override
        public boolean isEnabled(TreeNode initialRootReq) {
          return true;
        }
        @Override
        public List<TreeNode> getMultipartRoots(ReportGenerationContext context, Requirement initialRootReq,
        		boolean allowRelated) {
          List<TreeNode> ret = new LinkedList<>();
          TreeDB db = initialRootReq.getTreeDB();
          ret.add(db.getNode(Requirement.getTypeRootQId()));
          DocFolder df = (DocFolder) db.getNode(Document.getTypeRootQId());
          DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(df, false, true, true);
          Iterator<TreeNode> it = dftw.iterator();
          while(it.hasNext()) {
            TreeNode node = it.next();
            if(node.getType().equals(Document.TYPE_NAME) && 
                (((Document)node).haveMarkup() || !context.reportData.attrs().getStringAttributeValue("markupOnly", "true").equalsIgnoreCase("true")))
              ret.add(node);
          }
          return ret;
        }
      };
    return mp;
  }

}
