package com.unitesk.requality.eclipse.handlers.copypaste;

public class EclipsePasteHandler extends EclipseCopyPasteBaseHandler {
  public static String commandId = "org.eclipse.ui.edit.paste";

  @Override
  public String getCommandId() {
    return commandId;
  }
}
