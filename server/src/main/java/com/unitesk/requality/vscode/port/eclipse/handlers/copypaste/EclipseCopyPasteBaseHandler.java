package com.unitesk.requality.vscode.port.eclipse.handlers.copypaste;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;
import com.unitesk.requality.vscode.ui.runtime.IExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.Platform;

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
