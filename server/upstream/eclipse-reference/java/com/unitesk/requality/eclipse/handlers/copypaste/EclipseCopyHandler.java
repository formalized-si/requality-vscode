package com.unitesk.requality.eclipse.handlers.copypaste;

public class EclipseCopyHandler extends EclipseCopyPasteBaseHandler {
  public static String commandId = "org.eclipse.ui.edit.copy";

  @Override
  public String getCommandId() {
    return commandId;
  }
}
