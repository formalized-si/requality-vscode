package com.unitesk.requality.core.api;

import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.operation.IProgressMonitor;

public interface IParamValueProvider {

  public AttributeType getAttributeType();
  public String getType();
  public TypedValue getTypedValue(String key, AttributeStorage as, IProgressMonitor mon, Object ... meta);
  
}
