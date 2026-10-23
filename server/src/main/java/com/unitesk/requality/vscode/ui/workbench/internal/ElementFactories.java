package com.unitesk.requality.vscode.ui.workbench.internal;

import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;
import com.unitesk.requality.vscode.ui.workbench.IElementFactory;

/**
 * {@code org.eclipse.ui.internal.Workbench.getElementFactory}: the element factories by id, the built-in working set
 * factory and the ones of the {@code org.eclipse.ui.elementFactories} extension point.
 */
public final class ElementFactories {
  private static final String PL_ELEMENT_FACTORIES = "org.eclipse.ui.elementFactories";

  private static final java.util.Map<String, IElementFactory> REGISTERED = new java.util.concurrent.ConcurrentHashMap<>();

  private ElementFactories() {
  }

  /** Registers a factory by id (the factories of the server's own elements, and of the tests). */
  public static void register(String factoryId, IElementFactory factory) {
    REGISTERED.put(factoryId, factory);
  }

  /** The element factory of the id, or null. */
  public static IElementFactory get(String factoryId) {
    if (AbstractWorkingSet.FACTORY_ID.equals(factoryId))
      return new WorkingSetFactory();
    if (REGISTERED.containsKey(factoryId))
      return REGISTERED.get(factoryId);
    for (IConfigurationElement e : ExtensionRegistry.get().getConfigurationElementsFor(PL_ELEMENT_FACTORIES))
      if (factoryId.equals(e.getAttribute("id")))
        try {
          return (IElementFactory) e.createExecutableExtension("class");
        } catch (Exception ex) {
          com.unitesk.requality.core.log.RLogger.logError(ex);
          return null;
        }
    return null;
  }
}
