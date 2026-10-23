package com.unitesk.requality.vscode.port.tests;

import com.unitesk.requality.vscode.ui.jface.wizard.IWizard;
import com.unitesk.requality.core.model.TreeNode;
/**
 * Wizard used to alter settings of coverage source. It tends to load settings on
 * setCoverageStorageSettings call and save changed settings on performFinish().
 *
 * @author kildishev
 */
public interface ICoverageSourceWizard extends IWizard {
  /**
   * Define current settings that were stored in past.s
   *
   * @param split settings over coverage source
   */
  void setCoverageStorageSettings(TreeNode node, String[] split);
}
