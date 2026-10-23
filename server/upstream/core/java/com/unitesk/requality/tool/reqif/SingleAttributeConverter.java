package com.unitesk.requality.tool.reqif;

import java.util.Map;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.model.TreeNode;

public interface SingleAttributeConverter {

  boolean requality2Doors(TreeNode node, Attribute a, SpecObject so, ReqIFModel model);

  boolean doors2Requality(
      SpecObject so, AbstractAttributeValue val, TreeNode node, Map<String, String> attrrenames, ReqIFModel model);
}
