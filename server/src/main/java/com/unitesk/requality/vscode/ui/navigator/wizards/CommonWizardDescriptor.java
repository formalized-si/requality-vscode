package com.unitesk.requality.vscode.ui.navigator.wizards;

import com.unitesk.requality.vscode.ui.commands.EvaluationContext;
import com.unitesk.requality.vscode.ui.commands.Expressions;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;
import com.unitesk.requality.vscode.ui.workbench.WorkbenchException;

/**
 * {@code org.eclipse.ui.internal.navigator.wizards.CommonWizardDescriptor}: a {@code commonWizard} element of a
 * navigator content extension (or of the navigator's {@code commonWizard} extension point): a wizard shortcut in the
 * context menu of the navigator, for the elements its enablement accepts.
 */
public class CommonWizardDescriptor {
  /** The default menu group id for commonWizards without a menuGroupId attribute. */
  public static final String DEFAULT_MENU_GROUP_ID = "all-uncategorized";

  private final String id;
  private final String wizardId;
  private final String menuGroupId;
  private final String type;
  private final IConfigurationElement enablement;

  /**
   * @param configElement the {@code commonWizard} element
   * @param anId the id of the enclosing navigator content extension, or null
   * @throws WorkbenchException if a required attribute is missing
   */
  public CommonWizardDescriptor(IConfigurationElement configElement, String anId) throws WorkbenchException {
    String associated = configElement.getAttribute("associatedExtensionId");
    id = anId != null ? anId : associated;
    wizardId = configElement.getAttribute("wizardId");
    type = configElement.getAttribute("type");
    String group = configElement.getAttribute("menuGroupId");
    menuGroupId = group == null ? DEFAULT_MENU_GROUP_ID : group;
    if (wizardId == null || wizardId.isEmpty())
      throw new WorkbenchException("Missing attribute: wizardId in common wizard extension");
    if (type == null || type.isEmpty())
      throw new WorkbenchException("Missing attribute: type in common wizard extension");
    IConfigurationElement[] children = configElement.getChildren("enablement");
    if (children.length > 1)
      throw new WorkbenchException("More than one element: enablement in common wizard extension");
    enablement = children.length == 1 ? children[0] : null;
  }

  /** The id of the navigator content extension the wizard belongs to (its visibility), or null. */
  public String getId() {
    return id;
  }

  public String getWizardId() {
    return wizardId;
  }

  public String getMenuGroupId() {
    return menuGroupId;
  }

  /** The type attribute: {@code new}, {@code import} or {@code export}. */
  public String getType() {
    return type;
  }

  /** Determines if the wizard is enabled for the element (no enablement: never). */
  public boolean isEnabledFor(Object anElement) {
    if (enablement == null)
      return false;
    return Expressions.evaluateContainer(enablement, new EvaluationContext(null, anElement));
  }
}
