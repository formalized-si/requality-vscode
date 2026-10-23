package com.unitesk.requality.vscode.ui.workbench;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.Platform;

/**
 * {@code org.eclipse.ui.BasicWorkingSetElementAdapter}: a declarative element adapter. The working set type gives the
 * classes the elements are kept as, {@code elementAdapterClass="org.eclipse.ui.BasicWorkingSetElementAdapter:class1;adapt=true,class2"}:
 * an element of such a class is kept as it is, and with {@code adapt=true} an element that adapts to the class is kept as the
 * adapter. VSCODE: the class of the element is checked by name (through the ported names); the OSGi package check of Eclipse
 * is replaced by the adapter manager.
 */
public final class BasicWorkingSetElementAdapter implements IWorkingSetElementAdapter {
  private static final class Type {
    String className;
    boolean adapt;
  }

  private final Type[] preferredTypes;

  /** The adapter of the element adapter class data (the part after the colon of elementAdapterClass). */
  public BasicWorkingSetElementAdapter(String data) {
    List<Type> types = new ArrayList<>();
    for (StringTokenizer toker = new StringTokenizer(data == null ? "" : data, ","); toker.hasMoreTokens();) {
      Type record = new Type();
      for (StringTokenizer opts = new StringTokenizer(toker.nextToken(), ";"); opts.hasMoreTokens();) {
        String token = opts.nextToken();
        if (record.className == null) {
          record.className = token;
        } else {
          String[] pair = token.split("=", 2);
          if (pair.length == 2 && "adapt".equals(pair[0])) {
            record.adapt = "true".equals(pair[1]);
          }
        }
      }
      types.add(record);
    }
    preferredTypes = types.toArray(new Type[0]);
  }

  @Override
  public IAdaptable[] adaptElements(IWorkingSet ws, IAdaptable[] elements) {
    List<IAdaptable> adaptedElements = new ArrayList<>();
    for (IAdaptable element : elements) {
      IAdaptable adaptable = adapt(element);
      if (adaptable != null) {
        adaptedElements.add(adaptable);
      }
    }
    return adaptedElements.toArray(new IAdaptable[adaptedElements.size()]);
  }

  /** The first of the preferred types the element is (or adapts to). */
  private IAdaptable adapt(IAdaptable adaptable) {
    for (Type preferredType : preferredTypes) {
      IAdaptable adaptedAdaptable = adapt(preferredType, adaptable);
      if (adaptedAdaptable != null) {
        return adaptedAdaptable;
      }
    }
    return null;
  }

  private IAdaptable adapt(Type type, IAdaptable adaptable) {
    Class<?> clazz = load(type.className);
    if (clazz != null && clazz.isInstance(adaptable)) {
      return adaptable;
    }
    if (type.adapt && clazz != null) {
      Object adapted = Platform.getAdapterManager().getAdapter(adaptable, clazz);
      if (adapted instanceof IAdaptable) {
        return (IAdaptable) adapted;
      }
    }
    return null;
  }

  private static Class<?> load(String name) {
    try {
      return Class.forName(ExtensionRegistry.portedClassName(name));
    } catch (ClassNotFoundException | LinkageError e) {
      return null;
    }
  }

  @Override
  public void dispose() {
  }
}
