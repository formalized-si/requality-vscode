package com.unitesk.requality.core.attribute.valueprovider;

import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.TypedValue;

public interface IValueProvider extends IGenericValueProvider{
  TypedValue getTypedValue(String key, AttributeStorage as);
  TypedValue getRawValue();
}
