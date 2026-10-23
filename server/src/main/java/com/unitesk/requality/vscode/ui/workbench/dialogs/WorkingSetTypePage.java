package com.unitesk.requality.vscode.ui.workbench.dialogs;

import com.unitesk.requality.vscode.ui.jface.viewers.ArrayContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.TableViewer;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.workbench.internal.WorkingSetDescriptor;
import com.unitesk.requality.vscode.ui.workbench.internal.WorkingSetRegistry;

/**
 * {@code org.eclipse.ui.internal.dialogs.WorkingSetTypePage}: lists the working set types of the new working set wizard;
 * the page of the chosen type comes next.
 */
public class WorkingSetTypePage extends WorkingSetWizardPage {
  private static final int SIZING_SELECTION_WIDGET_WIDTH = 50;
  private static final int SIZING_SELECTION_WIDGET_HEIGHT = 200;

  private TableViewer typesListViewer;
  private final WorkingSetDescriptor[] descriptors;

  /** Creates the page with the types that have a page class. */
  public WorkingSetTypePage() {
    this(WorkingSetRegistry.get().getNewPageWorkingSetDescriptors());
  }

  /**
   * @param descriptors the working set types that can be selected on the page
   */
  public WorkingSetTypePage(WorkingSetDescriptor[] descriptors) {
    super("workingSetTypeSelectionPage", "Select a working set type", null);
    this.descriptors = descriptors;
  }

  @Override
  public boolean canFlipToNextPage() {
    return isPageComplete();
  }

  @Override
  public void createControl(Composite parent) {
    Composite composite = new Composite(parent, SWT.NULL);
    composite.setLayout(new GridLayout());
    composite.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));
    setControl(composite);

    Label typesLabel = new Label(composite, SWT.NONE);
    typesLabel.setText("&Working set type:");
    typesLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    typesListViewer = new TableViewer(composite, SWT.BORDER | SWT.SINGLE);
    GridData data = new GridData(GridData.FILL_BOTH);
    data.heightHint = SIZING_SELECTION_WIDGET_HEIGHT;
    data.widthHint = SIZING_SELECTION_WIDGET_WIDTH;
    typesListViewer.getTable().setLayoutData(data);

    typesListViewer.addSelectionChangedListener(event -> handleSelectionChanged());
    typesListViewer.addDoubleClickListener(event -> handleDoubleClick());
    typesListViewer.setContentProvider(ArrayContentProvider.getInstance());
    typesListViewer.setLabelProvider(new LabelProvider() {
      @Override
      public String getText(Object element) {
        return ((WorkingSetDescriptor) element).getName();
      }
    });
    typesListViewer.setInput(descriptors);
    setPageComplete(false);
  }

  /** The page id of the selected working set type, or null. */
  public String getSelection() {
    WorkingSetDescriptor descriptor = getSelectedWorkingSet();
    if (descriptor != null) {
      return descriptor.getId();
    }
    return null;
  }

  private WorkingSetDescriptor getSelectedWorkingSet() {
    return (WorkingSetDescriptor) typesListViewer.getStructuredSelection().getFirstElement();
  }

  private void handleDoubleClick() {
    handleSelectionChanged();
    getContainer().showPage(getNextPage());
  }

  private void handleSelectionChanged() {
    IStructuredSelection selection = typesListViewer.getStructuredSelection();
    boolean hasSelection = selection != null && !selection.isEmpty();
    WorkingSetDescriptor descriptor = getSelectedWorkingSet();
    setDescription(descriptor == null ? "" : descriptor.getDescription());
    setPageComplete(hasSelection);
  }
}
