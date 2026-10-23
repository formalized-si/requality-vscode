package com.unitesk.requality.core.attribute.valueprovider;

import java.util.HashSet;
import java.util.Set;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.model.TreeNode;

public class DescModeValueProvider extends CommonLabelProvider implements IValueProvider {
  private static final String TYPE = "DescriptionModeLabelProvider";
  public static final String LABEL_IS_SIMPLE_PARAM="_labelIsSimple";
  private static final Set<String> listen = new HashSet<>();
  {
    listen.addAll(Attribute.getHTMLNames());
    listen.add(TreeNode.ATTR_NAMEORID);
  }
  @Override
  public TypedValue getTypedValue(String key, AttributeStorage as) {
    String desc = as.getNode().getDescription();
    String nid = as.getStringAttributeValue(TreeNode.ATTR_NAMEORID);
    if (desc != null && desc.length() > 5){
      StringBuilder sb = new StringBuilder();
      sb.append(desc.substring(0, Math.min(desc.length(), 40)));
      sb.append("\t[");
      sb.append(nid);
      sb.append("]");
      return new TypedValue(AttributeType.STRING,  sb.toString());
    }
    return new TypedValue(AttributeType.STRING, nid);
  }
  @Override
  public Set<String> getDependencies(AttributeStorage as) {
    return listen;
  }
  @Override
  public String getType() {
    return TYPE;
  }
  
}
