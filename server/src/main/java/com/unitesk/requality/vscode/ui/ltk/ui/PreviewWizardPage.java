package com.unitesk.requality.vscode.ui.ltk.ui;

import com.unitesk.requality.vscode.ui.jface.viewers.CheckboxTreeViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider;
import com.unitesk.requality.vscode.ui.ltk.Change;
import com.unitesk.requality.vscode.ui.ltk.CompositeChange;
import com.unitesk.requality.vscode.ui.ltk.resource.ResourceChange;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;

/**
 * {@code org.eclipse.ltk.internal.ui.refactoring.PreviewWizardPage}: the changes to be performed as a tree with check
 * boxes ("Changes to be performed"). A resource change has no text preview ("No preview available").
 */
public class PreviewWizardPage extends RefactoringWizardPage {
  /** {@code IPreviewWizardPage.PAGE_NAME}. */
  public static final String PAGE_NAME = "PreviewWizardPage";

  private Change change;
  private CheckboxTreeViewer viewer;

  public PreviewWizardPage() {
    super(PAGE_NAME);
    setTitle("Changes to be performed");
    setDescription("The following changes are necessary to perform the refactoring.");
  }

  /** {@code PreviewWizardPage#setChange(Change)}: the change shown; the tree is refreshed when it is created. */
  public void setChange(Change change) {
    this.change = change;
    if (viewer != null && !viewer.getTree().isDisposed()) {
      viewer.setInput(change);
      viewer.setAllChecked(true);
      viewer.expandAll();
    }
  }

  /** The children of a change in the preview tree. */
  static Object[] childrenOf(Object element) {
    if (element instanceof CompositeChange)
      return ((CompositeChange) element).getChildren();
    if (element instanceof ResourceChange)
      return ((ResourceChange) element).getChildren();
    return new Object[0];
  }

  @Override
  public void createControl(Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);
    composite.setLayout(new GridLayout(1, false));
    composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    viewer = new CheckboxTreeViewer(composite, SWT.BORDER | SWT.MULTI);
    viewer.getTree().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    viewer.setContentProvider(new ITreeContentProvider() {
      @Override
      public Object[] getChildren(Object parentElement) {
        return childrenOf(parentElement);
      }

      @Override
      public Object getParent(Object element) {
        return null;
      }

      @Override
      public boolean hasChildren(Object element) {
        return childrenOf(element).length > 0;
      }

      @Override
      public Object[] getElements(Object inputElement) {
        return inputElement == null ? new Object[0] : new Object[] { inputElement };
      }
    });
    viewer.setLabelProvider(new LabelProvider() {
      @Override
      public String getText(Object element) {
        return element instanceof Change ? ((Change) element).getName() : String.valueOf(element);
      }
    });
    // ChangeElement.setEnabled: a change unchecked in the tree (with its children) is not performed
    viewer.addCheckStateListener(event -> setEnabled(event.getElement(), event.getChecked()));
    Label noPreview = new Label(composite, SWT.NONE);
    noPreview.setText("No preview available");
    noPreview.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
    setControl(composite);
    setChange(change);
  }

  /** Enables or disables a change and its children, and checks them accordingly in the tree. */
  private void setEnabled(Object element, boolean enabled) {
    if (element instanceof Change c)
      c.setEnabled(enabled);
    viewer.setChecked(element, enabled);
    for (Object child : childrenOf(element))
      setEnabled(child, enabled);
  }

  /**
   * {@code PreviewWizardPage#performFinish}: the change of the preview is performed (the final conditions were checked
   * when the preview was computed); its unchecked elements are not performed.
   */
  @Override
  protected boolean performFinish() {
    if (change == null)
      return super.performFinish();
    return getRefactoringWizard().performChange(change);
  }

  @Override
  public boolean canFlipToNextPage() {
    return false;
  }
}
