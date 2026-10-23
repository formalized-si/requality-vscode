package com.unitesk.requality.eclipse.views;

import org.eclipse.ui.views.properties.tabbed.ITypeMapper;
import com.unitesk.requality.core.model.ITreeNodeProvider;

public class TypeMapper implements ITypeMapper {

  @Override
  public Class mapType(Object object) {
    if(ITreeNodeProvider.class.isInstance(object))
      return ((ITreeNodeProvider)object).getNode().getClass();
    return object.getClass();
  }
}
