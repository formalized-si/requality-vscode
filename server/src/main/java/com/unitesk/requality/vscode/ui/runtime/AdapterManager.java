package com.unitesk.requality.vscode.ui.runtime;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.core.log.RLogger;

/**
 * {@code org.eclipse.core.runtime.IAdapterManager} with the adapter factories of plugin.xml
 * ({@code org.eclipse.core.runtime.adapters}): an element is adapted by the factories registered for its type.
 */
public final class AdapterManager implements Platform.IAdapterManager {
  static final AdapterManager INSTANCE = new AdapterManager();

  private static final class Factory {
    final String adaptableType;
    final List<String> adapterTypes = new ArrayList<>();
    final IConfigurationElement element;
    IAdapterFactory instance;
    boolean failed;

    Factory(IConfigurationElement e) {
      element = e;
      adaptableType = e.getAttribute("adaptableType");
      for (IConfigurationElement a : e.getChildren("adapter"))
        adapterTypes.add(ExtensionRegistry.portedClassName(a.getAttribute("type")));
    }

    synchronized IAdapterFactory get() {
      if (instance == null && !failed) {
        try {
          instance = (IAdapterFactory) element.createExecutableExtension("class");
        } catch (CoreException | ClassCastException e) {
          failed = true;
          RLogger.logInfo("Adapter factory " + element.getAttribute("class") + " is not available");
        }
      }
      return instance;
    }
  }

  private List<Factory> factories;

  private AdapterManager() {
  }

  private synchronized List<Factory> factories() {
    if (factories == null) {
      factories = new ArrayList<>();
      for (IConfigurationElement e : ExtensionRegistry.get().getConfigurationElementsFor("org.eclipse.core.runtime.adapters"))
        if ("factory".equals(e.getName()))
          factories.add(new Factory(e));
    }
    return factories;
  }

  private static boolean isInstance(Object o, String type) {
    try {
      return Class.forName(ExtensionRegistry.portedClassName(type)).isInstance(o);
    } catch (ClassNotFoundException | LinkageError e) {
      return false;
    }
  }

  @Override
  public Object getAdapter(Object adaptable, Class<?> adapterType) {
    if (adaptable == null)
      return null;
    if (adapterType.isInstance(adaptable))
      return adaptable;
    if (adaptable instanceof IAdaptable) {
      Object a = ((IAdaptable) adaptable).getAdapter(adapterType);
      if (a != null)
        return a;
    }
    for (Factory f : factories()) {
      if (!f.adapterTypes.contains(adapterType.getName()) || !isInstance(adaptable, f.adaptableType))
        continue;
      IAdapterFactory factory = f.get();
      if (factory == null)
        continue;
      @SuppressWarnings("unchecked")
      Object a = factory.getAdapter(adaptable, (Class) adapterType);
      if (a != null)
        return a;
    }
    return null;
  }

  @Override
  public boolean hasAdapter(Object adaptable, String adapterTypeName) {
    for (Factory f : factories())
      if (f.adapterTypes.contains(ExtensionRegistry.portedClassName(adapterTypeName)) && isInstance(adaptable, f.adaptableType))
        return true;
    return false;
  }
}
