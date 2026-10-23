package com.unitesk.requality.core.selection;

import java.util.List;
import java.util.Map;
import com.unitesk.requality.core.model.TreeNode;

public interface ISelectionProvider {
  public Map<String, List<TreeNode>> getSelection();

  public String getGroupId();
}
