package com.unitesk.requality.core.storage;

public class OFlag {
  public static final byte NONE = 0;
  public static final byte NO_TRANSACTION = 4;
  public static final byte NO_GUI_UPDATE=1,
               NO_UPDATE=2,
               NO_LATES=8;
  public static boolean isNoGuiUpdate(byte flag) {
      return (flag&NO_GUI_UPDATE)==NO_GUI_UPDATE;
  }
  public static boolean isNoUpdate(byte flag) {
    return (flag&NO_UPDATE)==NO_UPDATE;
  }
  public static boolean isNoLates(byte flag) {
    return (flag&NO_LATES)==NO_LATES;
  }
}
