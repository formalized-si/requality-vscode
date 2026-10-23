package com.unitesk.requality.core.attribute.valueprovider;

import java.util.Set;
import com.unitesk.requality.core.model.TreeNode;

public abstract class TypeFilteredProvider implements IValueProvider {
  protected abstract Set<String> getSuitableTypes();
  public boolean testIsAvailiableOn(TreeNode node){
    if(getSuitableTypes().contains(node.getType()))
      return true;
    return false;
  }
}
