package com.unitesk.requality.eclipse.core;

import org.eclipse.jface.viewers.IFilter;
import com.unitesk.requality.tool.CoreUtils;

public class DebugEnabledFilter implements IFilter {

  @Override
  public boolean select(Object toTest) {
    return CoreUtils.DEBUG_LEVEL>0;
  }
}
