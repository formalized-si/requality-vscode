package com.unitesk.requality.vscode.ui.workbench.dialogs;

import java.util.Locale;

import com.unitesk.requality.vscode.ui.jface.viewers.ViewerFilter;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.workbench.IWizardCategory;
import com.unitesk.requality.vscode.ui.workbench.IWizardDescriptor;

/**
 * {@code org.eclipse.ui.internal.dialogs.WizardPatternFilter}: keeps the wizards whose label, description or category
 * contain every word of the filter text, and the categories that contain such wizards. An empty text keeps everything.
 */
public class WizardPatternFilter extends ViewerFilter {
  private String[] words = new String[0];

  /** Sets the filter text: the words are matched case-insensitively. */
  public void setPattern(String text) {
    String t = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    words = t.isEmpty() ? new String[0] : t.split("\\s+");
  }

  /** Whether a filter text is set. */
  public boolean hasPattern() {
    return words.length > 0;
  }

  @Override
  public boolean select(Viewer viewer, Object parentElement, Object element) {
    if (words.length == 0)
      return true;
    if (element instanceof IWizardDescriptor wizard)
      return matches(wizard);
    if (element instanceof IWizardCategory category)
      return hasMatch(category);
    return true;
  }

  private boolean hasMatch(IWizardCategory category) {
    for (IWizardDescriptor w : category.getWizards())
      if (matches(w))
        return true;
    for (IWizardCategory c : category.getCategories())
      if (hasMatch(c))
        return true;
    return false;
  }

  private boolean matches(IWizardDescriptor wizard) {
    StringBuilder text = new StringBuilder();
    text.append(wizard.getLabel()).append(' ');
    if (wizard.getDescription() != null)
      text.append(wizard.getDescription()).append(' ');
    for (IWizardCategory c = wizard.getCategory(); c != null && c.getParent() != null; c = c.getParent())
      text.append(c.getLabel()).append(' ');
    String s = text.toString().toLowerCase(Locale.ROOT);
    for (String w : words)
      if (!s.contains(w))
        return false;
    return true;
  }
}
