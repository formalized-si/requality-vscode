package com.unitesk.requality.core.attribute.valueprovider;


import java.util.HashSet;
import java.util.Set;

import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.events.INodeChangeListener;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.nodetype.Location;

public class LocationLabelProvider extends CommonLabelProvider implements INodeChangeListener {
  private static String TYPE = "LocationLabelProvider";
  private static Set<String> labelDeps = new HashSet<>(); 
  static{
    labelDeps.add(TreeNode.ATTR_REVERT_LINKS);
  }
  @Override
  public Set<String> getDependencies(AttributeStorage as) {
    return labelDeps;
  }
  
  @Override
  public TypedValue getTypedValue(String key, AttributeStorage as) {
    return new TypedValue(AttributeType.STRING, ((Location)as.getNode()).formLabel());
  }
  @Override
  public void detach(TreeNode node) {
    node.removeGuiChangeListener(this);
  }
  
  @Override
  public void attach(TreeNode node) {
    node.addGuiChangeListener(this);
  }
  
  private static LocationLabelProvider one = new LocationLabelProvider();
  public static IValueProvider getOne(Location location) {
    return one;
  }

  @Override
  public void attributeChange(TreeNode source, String what, Attribute old) {
  }

  @Override
  public void attributesChange(TreeNode source, ChangesSet<Attribute> what) {
  }

  @Override
  public void deleted(TreeNode source) {
    
  }

  @Override
  public void moved(TreeNode node, TreeNode oldParent) {
  }

  @Override
  public void fireNodeResourceChange(TreeNode treeNode, String resName, Moment m) {
  }
}
