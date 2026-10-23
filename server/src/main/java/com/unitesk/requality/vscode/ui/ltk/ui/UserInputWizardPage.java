package com.unitesk.requality.vscode.ui.ltk.ui;

import com.unitesk.requality.vscode.ui.ltk.Change;

import com.unitesk.requality.vscode.ui.jface.wizard.IWizardPage;

/**
 * {@code org.eclipse.ltk.ui.refactoring.UserInputWizardPage}: a page asking for the input of the refactoring. Its next
 * page is the preview, which shows the change created from the input.
 */
public abstract class UserInputWizardPage extends RefactoringWizardPage {

  protected UserInputWizardPage(String name) {
    super(name);
  }

  @Override
  public boolean canFlipToNextPage() {
    return isPageComplete() && getRefactoringWizard().hasPreviewPage();
  }

  /**
   * {@code UserInputWizardPage#computeSuccessorPage()}: the preview page, with the change of the current input once the
   * final conditions passed (null: the page stays, the problems were shown).
   */
  @Override
  public IWizardPage getNextPage() {
    IWizardPage preview = getWizard().getPage(PreviewWizardPage.PAGE_NAME);
    if (preview instanceof PreviewWizardPage) {
      Change change = getRefactoringWizard().computePreviewChange();
      if (change == null)
        return null;
      ((PreviewWizardPage) preview).setChange(change);
    }
    return preview;
  }
}
