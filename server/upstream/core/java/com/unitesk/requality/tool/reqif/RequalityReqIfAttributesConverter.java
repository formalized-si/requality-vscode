package com.unitesk.requality.tool.reqif;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import com.unitesk.requality.core.model.TreeNode;

public class RequalityReqIfAttributesConverter {
  List<NodeAttributeConverter> converters = new LinkedList<>();
  SingleAttributeConverter sac;

  public RequalityReqIfAttributesConverter(
      List<NodeAttributeConverter> convs, SingleAttributeConverter def) {
    converters.addAll(convs);
    sac = def;
  }

  private void registerConverter(NodeAttributeConverter sac) {
    converters.add(sac);
  }

  public void convertReqToDoors(TreeNode node, SpecObject object, ReqIFModel model) {
    for (NodeAttributeConverter sac2 : converters) sac2.requality2Doors(node, object, model);
  }

  public void convertDoorsToReq(SpecObject object, TreeNode node, Map<String, String> attrrenames, ReqIFModel model) {
    for (NodeAttributeConverter sac2 : converters) sac2.doors2Requality(object, node, attrrenames, model);
  }
}
