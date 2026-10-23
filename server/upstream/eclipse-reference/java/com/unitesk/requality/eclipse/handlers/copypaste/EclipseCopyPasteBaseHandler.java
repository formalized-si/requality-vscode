package com.unitesk.requality.eclipse.handlers.copypaste;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;

public abstract class EclipseCopyPasteBaseHandler extends AbstractHandler {
  public abstract String getCommandId();

  @Override
  public boolean isEnabled() {
    IExtensionRegistry reg = Platform.getExtensionRegistry();
    IConfigurationElement[] configurationElements = reg.getConfigurationElementsFor(getCommandId());
    for (IConfigurationElement conf : configurationElements) {
      try {
        try {
          AbstractHandler ah = (AbstractHandler) conf.createExecutableExtension("class");
          if (ah.isEnabled()) return false;
        } catch (ClassCastException cce) {
          //so, this is bad handlers(
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    return true;
  }

  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    return null;
  }
}
