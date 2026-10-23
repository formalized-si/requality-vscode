package com.unitesk.requality.vscode.ui.navigator.framelist;

/** {@code org.eclipse.ui.internal.navigator.framelist.FrameListMessages} (messages.properties). */
final class FrameListMessages {
  static final String Back_text = "&Back";
  static final String Back_toolTip = "Back";
  static final String Back_toolTipOneArg = "Back to {0}";
  static final String Forward_text = "&Forward";
  static final String Forward_toolTip = "Forward";
  static final String Forward_toolTipOneArg = "Forward to {0}";
  static final String GoInto_text = "Go &Into";
  static final String GoInto_toolTip = "Go Into";
  static final String Up_text = "&Up One Level";
  static final String Up_toolTip = "Up";
  static final String Up_toolTipOneArg = "Up to {0}";

  private FrameListMessages() {
  }

  /** {@code NLS.bind} with one argument. */
  static String bind(String message, String arg) {
    return message.replace("{0}", arg);
  }
}
