package com.unitesk.requality.vscode.port.eclipse.views;

import com.unitesk.requality.vscode.ui.tabbed.ITypeMapper;
import com.unitesk.requality.core.model.ITreeNodeProvider;

public class TypeMapper implements ITypeMapper {

  @Override
  public Class mapType(Object object) {
    if(ITreeNodeProvider.class.isInstance(object))
      return ((ITreeNodeProvider)object).getNode().getClass();
    return object.getClass();
  }
}
