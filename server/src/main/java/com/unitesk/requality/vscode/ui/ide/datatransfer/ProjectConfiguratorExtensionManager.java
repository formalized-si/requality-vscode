package com.unitesk.requality.vscode.ui.ide.datatransfer;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.commands.EvaluationContext;
import com.unitesk.requality.vscode.ui.commands.Expressions;
import com.unitesk.requality.vscode.ui.commands.IEvaluationContext;
import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;
import com.unitesk.requality.vscode.ui.runtime.Platform;

/**
 * {@code org.eclipse.ui.internal.wizards.datatransfer.ProjectConfiguratorExtensionManager}: manages and requests the active
 * {@link ProjectConfigurator} extensions of the {@code org.eclipse.ui.ide.projectConfigurators} extension point for a
 * {@link SmartImportJob}.
 */
public class ProjectConfiguratorExtensionManager {
  private static final String EXTENSION_POINT_ID = "org.eclipse.ui.ide.projectConfigurators"; //$NON-NLS-1$

  private final IConfigurationElement[] extensions;
  private final Map<IConfigurationElement, ProjectConfigurator> configuratorsByExtension = new HashMap<>();

  /** Each instance has its own internal registry of the configurators it has created (at most once each). */
  public ProjectConfiguratorExtensionManager() {
    this.extensions = Platform.getExtensionRegistry().getConfigurationElementsFor(EXTENSION_POINT_ID).clone();
    // Force Eclipse configurator to be 1st
    int eclipseConfiguratorIndex = 0;
    while (eclipseConfiguratorIndex < this.extensions.length
        && !isClass(this.extensions[eclipseConfiguratorIndex], EclipseProjectConfigurator.class)) {
      eclipseConfiguratorIndex++;
    }
    if (eclipseConfiguratorIndex != 0 && eclipseConfiguratorIndex < this.extensions.length) {
      IConfigurationElement tmp = this.extensions[eclipseConfiguratorIndex];
      this.extensions[eclipseConfiguratorIndex] = this.extensions[0];
      this.extensions[0] = tmp;
    }
  }

  private static boolean isClass(IConfigurationElement extension, Class<?> cls) {
    String name = extension.getAttribute("class"); //$NON-NLS-1$
    return name != null && cls.getName().equals(com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry.portedClassName(name));
  }

  /** The active configurators for the container, the top priority first. */
  private List<ProjectConfigurator> getAllActiveProjectConfiguratorsUntyped(Object container) {
    List<ProjectConfigurator> res = new ArrayList<>();
    for (IConfigurationElement extension : this.extensions) {
      boolean addIt = false;
      // VSCODE: all the configurators come from the registry of this port, so the contributing bundle is always active
      // and only the activeWhen condition decides.
      IConfigurationElement[] activeWhenElements = extension.getChildren("activeWhen"); //$NON-NLS-1$
      switch (activeWhenElements.length) {
      case 0:
        addIt = true;
        break;
      case 1:
        IConfigurationElement activeWhen = activeWhenElements[0];
        IConfigurationElement[] activeWhenChildren = activeWhen.getChildren();
        if (activeWhenChildren.length == 1) {
          try {
            addIt = evaluate(activeWhenChildren[0], container);
          } catch (RuntimeException ex) {
            RLogger.logError(ex);
          }
        } else {
          RLogger.logInfo("Could not evaluate expression for " + extension.getAttribute("class")
              + ": there must be exactly one child of 'activeWhen'");
        }
        break;
      default:
        RLogger.logInfo("Only one 'activeWhen' is authorized on extension point " + EXTENSION_POINT_ID
            + ", for extension " + extension.getAttribute("class"));
        break;
      }
      if (addIt) {
        ProjectConfigurator configurator = getConfigurator(extension);
        if (configurator == null)
          continue;
        if (configurator instanceof EclipseProjectConfigurator) {
          // give priority
          res.add(0, configurator);
        } else {
          res.add(configurator);
        }
      }
    }
    return res;
  }

  /** The activeWhen expression: the hasFolder test on the container's location, or the expression of the workbench. */
  private static boolean evaluate(IConfigurationElement expression, Object container) {
    if ("hasFolder".equals(expression.getName())) { //$NON-NLS-1$
      File base = container instanceof File ? (File) container
          : ((IResource) container).getLocation() == null ? null : ((IResource) container).getLocation().toFile();
      return base != null && new File(base, expression.getAttribute("path")).isDirectory(); //$NON-NLS-1$
    }
    IEvaluationContext ctx = new EvaluationContext(null, container);
    IConfigurationElement wrapper = expression.getParent() instanceof IConfigurationElement
        ? (IConfigurationElement) expression.getParent() : null;
    return wrapper != null ? Expressions.evaluateContainer(wrapper, ctx) : false;
  }

  /** The active configurators for the container, the top priority first. */
  public List<ProjectConfigurator> getAllActiveProjectConfigurators(IContainer container) {
    return getAllActiveProjectConfiguratorsUntyped(container);
  }

  /** The active configurators for the folder, the top priority first. */
  public List<ProjectConfigurator> getAllActiveProjectConfigurators(File folder) {
    if (!folder.isDirectory())
      throw new IllegalArgumentException(folder.getAbsolutePath());
    return getAllActiveProjectConfiguratorsUntyped(folder);
  }

  private ProjectConfigurator getConfigurator(IConfigurationElement extension) {
    if (!this.configuratorsByExtension.containsKey(extension)) {
      try {
        ProjectConfigurator configurator = (ProjectConfigurator) extension.createExecutableExtension("class"); //$NON-NLS-1$
        this.configuratorsByExtension.put(extension, configurator);
        return configurator;
      } catch (Exception ex) {
        RLogger.logError(ex);
        return null;
      }
    }
    return this.configuratorsByExtension.get(extension);
  }

  /** The labels of all the known configurators. */
  public static List<String> getAllExtensionLabels() {
    IConfigurationElement[] extensions = ExtensionRegistry.get().getConfigurationElementsFor(EXTENSION_POINT_ID);
    List<String> res = new ArrayList<>(extensions.length);
    for (IConfigurationElement extension : extensions)
      res.add(extension.getAttribute("label")); //$NON-NLS-1$
    return res;
  }

  /** The label of the configurator (its extension's label). */
  public static String getLabel(ProjectConfigurator configurator) {
    IConfigurationElement[] extensions = ExtensionRegistry.get().getConfigurationElementsFor(EXTENSION_POINT_ID);
    for (IConfigurationElement extension : extensions) {
      String cls = extension.getAttribute("class"); //$NON-NLS-1$
      if (cls != null && configurator.getClass().getName().equals(ExtensionRegistry.portedClassName(cls)))
        return extension.getAttribute("label"); //$NON-NLS-1$
    }
    return "Missing label for " + configurator.getClass().getName(); //$NON-NLS-1$
  }
}
