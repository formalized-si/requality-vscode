package com.unitesk.requality.vscode.ui.navigator;

import com.unitesk.requality.vscode.ui.swt.dnd.DND;
import com.unitesk.requality.vscode.ui.swt.dnd.TransferData;

/**
 * {@code org.eclipse.ui.navigator.CommonDropAdapter}: the drop state of a navigator view. The explorer tree of VS
 * Code passes the current target, operation and transfer to the assistants.
 */
public class CommonDropAdapter {
  private Object currentTarget;
  private int currentOperation = DND.DROP_NONE;
  private TransferData currentTransfer;
  private int feedback = DND.FEEDBACK_NONE;

  public CommonDropAdapter() {
  }

  public Object getCurrentTarget() {
    return currentTarget;
  }

  public void setCurrentTarget(Object target) {
    this.currentTarget = target;
  }

  public int getCurrentOperation() {
    return currentOperation;
  }

  public void setCurrentOperation(int operation) {
    this.currentOperation = operation;
  }

  public TransferData getCurrentTransfer() {
    return currentTransfer;
  }

  public void setCurrentTransfer(TransferData transfer) {
    this.currentTransfer = transfer;
  }

  public int getCurrentLocation() {
    return feedback;
  }

  public void overrideOperation(int operation) {
    this.currentOperation = operation;
  }

  public void setFeedback(int feedback) {
    this.feedback = feedback;
  }
}
