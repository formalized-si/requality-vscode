package com.unitesk.requality.core.attribute;

import com.unitesk.requality.core.attribute.valueprovider.IValueProvider;

public class FVPDetectResult {
  public IValueProvider valueProvider;
  public int flag;
  
  public FVPDetectResult(IValueProvider vp, byte f) {
    this.valueProvider = vp;
    this.flag = f;
  }
}
