package com.unitesk.requality.vscode.ui.workbench;

import java.util.Collection;

/** {@code org.eclipse.ui.contexts.IContextService}. */
public interface IContextService {
  /** {@code IContextService.activateContext(String)}: activates a context and returns its activation. */
  IContextActivation activateContext(String contextId);

  /** {@code IContextService.deactivateContext(IContextActivation)}. */
  void deactivateContext(IContextActivation activation);

  /** {@code IContextService.getActiveContextIds()}. */
  Collection<String> getActiveContextIds();
}
