package com.unitesk.requality.vscode.ui.workbench;

/**
 * {@code org.eclipse.ui.wizards.IWizardCategory}: a category of the {@code org.eclipse.ui.newWizards} extension point
 * (a {@code category} element), with its subcategories and the wizards declared in it.
 */
public interface IWizardCategory {
  /** The id of the category; null for the root category. */
  String getId();

  /** The name of the category shown in the wizard selection tree. */
  String getLabel();

  /** The name of the category (the label). */
  default String getName() {
    return getLabel();
  }

  /** The parent category, or null for the root category. */
  IWizardCategory getParent();

  /** The path of the category from the root: the ids of the categories. */
  com.unitesk.requality.vscode.ui.runtime.IPath getPath();

  /** The subcategories, sorted by name. */
  IWizardCategory[] getCategories();

  /** The wizards declared in this category, sorted by name. */
  IWizardDescriptor[] getWizards();

  /** The category with the path relative to this one, or null. */
  IWizardCategory findCategory(com.unitesk.requality.vscode.ui.runtime.IPath path);

  /** The wizard with the id in this category or its subcategories, or null. */
  IWizardDescriptor findWizard(String id);
}
