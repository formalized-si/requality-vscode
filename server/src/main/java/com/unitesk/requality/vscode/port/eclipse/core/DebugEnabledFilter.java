package com.unitesk.requality.vscode.port.eclipse.core;

import com.unitesk.requality.vscode.ui.jface.viewers.IFilter;
import com.unitesk.requality.tool.CoreUtils;

public class DebugEnabledFilter implements IFilter {

  @Override
  public boolean select(Object toTest) {
    return CoreUtils.DEBUG_LEVEL>0;
  }
}
