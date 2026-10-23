package com.unitesk.requality.vscode.ui.navigator.wizards;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.navigator.INavigatorContentService;
import com.unitesk.requality.vscode.ui.navigator.NavigatorActionService;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;
import com.unitesk.requality.vscode.ui.workbench.WorkbenchException;

/**
 * {@code org.eclipse.ui.internal.navigator.wizards.CommonWizardDescriptorManager}: the {@code commonWizard} elements of
 * the {@code org.eclipse.ui.navigator.navigatorContent} extension point, and the ones enabled for an element and a
 * type of wizard ({@code new}, {@code import}, {@code export}).
 */
public final class CommonWizardDescriptorManager {
  private static final CommonWizardDescriptorManager INSTANCE = new CommonWizardDescriptorManager();
  private static final String NAVIGATOR_CONTENT = "org.eclipse.ui.navigator.navigatorContent";
  /** Find wizards of type 'new'. */
  public static final String WIZARD_TYPE_NEW = "new";

  private CommonWizardDescriptorManager() {
  }

  /** @return the singleton instance of the manager */
  public static CommonWizardDescriptorManager getInstance() {
    return INSTANCE;
  }

  /** Reads the {@code commonWizard} elements of the registry (nested in a navigatorContent, or at top level). */
  private static List<CommonWizardDescriptor> readRegistry() {
    List<CommonWizardDescriptor> r = new ArrayList<>();
    for (IConfigurationElement e : ExtensionRegistry.get().getConfigurationElementsFor(NAVIGATOR_CONTENT)) {
      try {
        if ("navigatorContent".equals(e.getName())) {
          for (IConfigurationElement w : e.getChildren("commonWizard"))
            r.add(new CommonWizardDescriptor(w, e.getAttribute("id")));
        } else if ("commonWizard".equals(e.getName())) {
          r.add(new CommonWizardDescriptor(e, null));
        }
      } catch (WorkbenchException ex) {
        RLogger.logError(ex);
      }
    }
    return r;
  }

  /**
   * Returns all wizard descriptors which enable for the given element.
   *
   * @param anElement the element to return the wizards for (an empty list for an empty selection)
   * @param aType the type of wizards to locate ('new', 'import', or 'export')
   * @param aContentService the content service of the viewer, which decides the visibility, or null
   */
  public CommonWizardDescriptor[] getEnabledCommonWizardDescriptors(Object anElement, String aType,
      INavigatorContentService aContentService) {
    List<CommonWizardDescriptor> enabled = new ArrayList<>();
    for (CommonWizardDescriptor d : readRegistry())
      if (aType.equals(d.getType()) && isVisible(aContentService, d) && d.isEnabledFor(anElement))
        enabled.add(d);
    return enabled.toArray(new CommonWizardDescriptor[0]);
  }

  /** The wizard is visible when its navigator content extension is bound to the viewer. */
  // VSCODE: the activities of the workbench (isActive) are not supported; the bindings of the viewer decide.
  private static boolean isVisible(INavigatorContentService aContentService, CommonWizardDescriptor d) {
    if (aContentService == null || d.getId() == null)
      return true;
    List<Pattern> content = NavigatorActionService.patterns(aContentService.getViewerId(), "viewerContentBinding",
        "contentExtension");
    return NavigatorActionService.matches(content, d.getId());
  }
}
