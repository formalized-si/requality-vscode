package com.unitesk.requality.core.attribute;

public class Flag { 
  public static byte VIRTUAL = 1,//virtual flag is set for non-stored runtime attributes
      HIDDEN = 4,//hidden flag is set for system attributes that are not displayed among user attributes
      READ_ONLY = 8,
      CHANGE_READ_ONLY = 16;
}
