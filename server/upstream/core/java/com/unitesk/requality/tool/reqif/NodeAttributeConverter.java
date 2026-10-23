package com.unitesk.requality.tool.reqif;

import java.util.Map;
import com.unitesk.requality.core.model.TreeNode;

public interface NodeAttributeConverter {
  public boolean doors2Requality(SpecObject so, TreeNode node, Map<String, String> attrrenames, ReqIFModel model);

  public boolean requality2Doors(TreeNode node, SpecObject so, ReqIFModel model);
}
