package com.unitesk.requality.vscode.ui.workbench.dialogs;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.workbench.IWizardCategory;
import com.unitesk.requality.vscode.ui.workbench.IWizardDescriptor;

/**
 * {@code org.eclipse.ui.internal.dialogs.WizardContentProvider}: the categories and wizards of the wizard selection tree;
 * a category without wizards is not shown.
 */
public class WizardContentProvider implements ITreeContentProvider {
  @Override
  public void dispose() {
  }

  @Override
  public Object[] getChildren(Object parentElement) {
    if (!(parentElement instanceof IWizardCategory category))
      return new Object[0];
    List<Object> list = new ArrayList<>();
    for (IWizardCategory child : category.getCategories())
      if (hasChildren(child))
        list.add(child);
    for (IWizardDescriptor wizard : category.getWizards())
      list.add(wizard);
    return list.toArray();
  }

  @Override
  public Object[] getElements(Object inputElement) {
    return getChildren(inputElement);
  }

  @Override
  public Object getParent(Object element) {
    if (element instanceof IWizardCategory category)
      return category.getParent();
    if (element instanceof IWizardDescriptor wizard)
      return wizard.getCategory();
    return null;
  }

  @Override
  public boolean hasChildren(Object element) {
    return element instanceof IWizardCategory category
        && (category.getCategories().length > 0 || category.getWizards().length > 0);
  }

  @Override
  public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
  }
}
