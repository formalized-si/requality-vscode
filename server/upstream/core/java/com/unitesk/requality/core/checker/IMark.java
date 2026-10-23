package com.unitesk.requality.core.checker;

public interface IMark extends Comparable<Object>{

  /**
   * Get description of problem
   *
   * @return text of problems description
   */
  String getDescription();
  String getCheckerMessageId();
}
