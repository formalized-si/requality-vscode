package com.unitesk.requality.core.checker;

public interface INodeMark extends IMark {
  /**
   * Get node where problem appears
   *
   * @return tree node
   */
  Object getTarget();
  String[] getAdditionalData();
}
