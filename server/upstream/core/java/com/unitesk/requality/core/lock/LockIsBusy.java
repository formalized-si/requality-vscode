package com.unitesk.requality.core.lock;

public class LockIsBusy extends Exception {

  /**
   * 
   */
  private static final long serialVersionUID = -8898483584275898055L;

  public LockIsBusy(String string) {
    super(string);
  }
}
