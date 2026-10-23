package com.unitesk.requality.tool.reqif;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.model.TreeNode;

public abstract class AttributeConverter {
  public ReqIFModel model;

  public AttributeConverter(ReqIFModel model) {
    this.model = model;
  }

  abstract void convertRequalityToReqIF(TreeNode node, Attribute attr);

  abstract void convertReqIFToRequality(TreeNode node, AbstractAttributeValue attr);
}
