package com.unitesk.requality.core.attribute.valueprovider;

import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.TypedValue;

public interface IPagedValueProvider extends IGenericValueProvider{
  int getPages(int valueByPage, AttributeStorage as);
  TypedValue getTypedValue(int valueByPage,int page, String key, AttributeStorage as);
  TypedValue getRawValue(int valueByPage,int page);
}
