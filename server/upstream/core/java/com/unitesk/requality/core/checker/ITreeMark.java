package com.unitesk.requality.core.checker;

public interface ITreeMark extends IMark{

  /**
   * Get nodes where problem appears
   *
   * @return tree nodes
   */
  Object[] getTargets();
  String[] getAdditionalData();
}
