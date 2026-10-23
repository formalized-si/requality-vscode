package com.unitesk.requality.vscode.ui.workbench;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.commands.EvaluationContext;
import com.unitesk.requality.vscode.ui.commands.Expressions;
import com.unitesk.requality.vscode.ui.jface.viewers.Decoration;
import com.unitesk.requality.vscode.ui.jface.viewers.IDecoration;
import com.unitesk.requality.vscode.ui.jface.viewers.ILightweightLabelDecorator;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;

/**
 * The lightweight label decorators of plugin.xml ({@code org.eclipse.ui.decorators}), applied to an element in their
 * contribution order like the Eclipse decorator manager: each decorator adds overlays (by default in its
 * {@code location} quadrant), prefixes, suffixes, colors and fonts to one shared decoration.
 */
public final class LightweightDecorators {
  private static final class Definition {
    final IConfigurationElement element;
    final int quadrant;
    ILightweightLabelDecorator instance;
    boolean failed;

    Definition(IConfigurationElement element) {
      this.element = element;
      this.quadrant = quadrant(element.getAttribute("location"));
    }

    synchronized ILightweightLabelDecorator get() {
      if (instance == null && !failed) {
        try {
          instance = (ILightweightLabelDecorator) element.createExecutableExtension("class");
        } catch (CoreException | ClassCastException e) {
          failed = true;
          RLogger.logInfo("Decorator " + element.getAttribute("id") + " is not available: " + e.getMessage());
        }
      }
      return instance;
    }

    /** objectClass attribute or enablement expression. */
    boolean appliesTo(Object o) {
      String objectClass = element.getAttribute("objectClass");
      if (objectClass != null && !Expressions.isInstance(o, objectClass))
        return false;
      IConfigurationElement[] en = element.getChildren("enablement");
      if (en.length == 0)
        return true;
      return Expressions.evaluateContainer(en[0], new EvaluationContext(null, o));
    }
  }

  private static List<Definition> definitions;

  private LightweightDecorators() {
  }

  static int quadrant(String location) {
    if (location == null)
      return IDecoration.BOTTOM_RIGHT;
    switch (location) {
      case "TOP_LEFT":
        return IDecoration.TOP_LEFT;
      case "TOP_RIGHT":
        return IDecoration.TOP_RIGHT;
      case "BOTTOM_LEFT":
        return IDecoration.BOTTOM_LEFT;
      case "UNDERLAY":
        return IDecoration.UNDERLAY;
      case "REPLACE":
        return IDecoration.REPLACE;
      default:
        return IDecoration.BOTTOM_RIGHT;
    }
  }

  private static synchronized List<Definition> definitions() {
    if (definitions == null) {
      definitions = new ArrayList<>();
      for (IConfigurationElement e : ExtensionRegistry.get().getConfigurationElementsFor("org.eclipse.ui.decorators"))
        if ("decorator".equals(e.getName()) && "true".equals(e.getAttribute("lightweight"))
            && !"false".equals(e.getAttribute("state")) && e.getAttribute("class") != null)
          definitions.add(new Definition(e));
      // The decorator manager creates the enabled decorators when the workbench starts.
      for (Definition d : definitions)
        d.get();
    }
    return definitions;
  }

  /** Creates the enabled decorators (they register themselves, e.g. for RequalityDecorator.getAll()). */
  public static void ensureCreated() {
    definitions();
  }

  /** The decoration of an element by every enabled lightweight decorator. */
  public static Decoration decorate(Object element) {
    Decoration d = new Decoration();
    for (Definition def : definitions()) {
      try {
        if (!def.appliesTo(element))
          continue;
        ILightweightLabelDecorator dec = def.get();
        if (dec == null)
          continue;
        d.setDefaultQuadrant(def.quadrant);
        dec.decorate(element, d);
      } catch (RuntimeException e) {
        // Eclipse disables nothing but logs the failing decorator (SafeRunner).
        RLogger.logError(e);
      }
    }
    return d;
  }
}
