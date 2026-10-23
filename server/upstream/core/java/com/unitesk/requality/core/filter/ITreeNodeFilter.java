package com.unitesk.requality.core.filter;

import com.unitesk.requality.core.model.TreeNode;

public interface ITreeNodeFilter {
  /**
   * @return <code>true</code> if element is included in the filtered set, and <code>false</code> if
   *     excluded
   */
  boolean select(TreeNode node);
}
