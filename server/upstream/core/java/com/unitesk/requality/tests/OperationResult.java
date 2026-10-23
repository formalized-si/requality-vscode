package com.unitesk.requality.tests;

public class OperationResult {
  private boolean succeed = false;
  private String message = "";

  public String getMessage() {
    return message;
  }

  public boolean getSucceedFlag() {
    return succeed;
  }

  public void setMessage(String err) {
    message = err;
  }

  public void setSuceeed(boolean s) {
    succeed = s;
  }

  public OperationResult(String message, boolean st) {
    this.message = message;
    succeed = st;
  }
}
