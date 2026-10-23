package com.unitesk.requality.vscode.ui.workbench;

/** {@code org.eclipse.ui.wizards.IWizardRegistry}. */
public interface IWizardRegistry {
  /** The wizard with the given id, or null if no wizard of this registry has it. */
  IWizardDescriptor findWizard(String id);

  /** The category with the given id, or null. */
  IWizardCategory findCategory(String id);

  /** The root category: its subcategories are the top-level categories, its wizards are outside any category. */
  IWizardCategory getRootCategory();
}
