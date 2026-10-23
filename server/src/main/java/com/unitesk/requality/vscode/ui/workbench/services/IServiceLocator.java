package com.unitesk.requality.vscode.ui.workbench.services;

/** {@code org.eclipse.ui.services.IServiceLocator}: looks up services by their API class. */
public interface IServiceLocator {
  /** {@code org.eclipse.ui.services.IServiceLocator#getService}. */
  Object getService(Class<?> api);

  /** {@code org.eclipse.ui.services.IServiceLocator#hasService}. */
  boolean hasService(Class<?> api);
}
