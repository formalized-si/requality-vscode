package com.unitesk.requality.core.attribute;

import java.util.HashSet;
import java.util.Set;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.tool.DeepFirstTreeWalker;

public class GetterAttributeNames {

  public static Set<String> getFromRoot(TreeNode root, IAttributeFilter filter) {
    Set<String> names = new HashSet<>();
    DeepFirstTreeWalker walker = new DeepFirstTreeWalker(root, true, true, false);
    for (TreeNode node : walker) {
      for (Attribute a : node.attrs()) {
        if (filter.filter(a)) names.add(a.getKey());
      }
    }
    return names;
  }
}
