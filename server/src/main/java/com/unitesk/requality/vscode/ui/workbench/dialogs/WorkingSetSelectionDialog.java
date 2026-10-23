package com.unitesk.requality.vscode.ui.workbench.dialogs;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.viewers.ArrayContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.CheckboxTableViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ILabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.ViewerComparator;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.IAggregateWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSetManager;
import com.unitesk.requality.vscode.ui.workbench.Workbench;

/**
 * {@code org.eclipse.ui.internal.dialogs.WorkingSetSelectionDialog}: lists the working sets of the workbench, checked
 * ones are the selection; creates, edits and removes them.
 * The choices are the window working set, no working set, or the selected working sets.
 */
public class WorkingSetSelectionDialog extends AbstractWorkingSetDialog {
  private static final int SIZING_SELECTION_WIDGET_HEIGHT = 200;
  private static final int SIZING_SELECTION_WIDGET_WIDTH = 50;

  private final ILabelProvider labelProvider;
  private final IStructuredContentProvider contentProvider;
  private CheckboxTableViewer listViewer;
  private final boolean multiSelect;

  private Button buttonWindowSet;
  private Button buttonNoSet;
  private Button buttonSelectedSets;

  /**
   * Creates a working set selection dialog.
   *
   * @param parentShell   the parent shell
   * @param multi         true: the working sets chosen are returned as they are; false: as one aggregate working set
   * @param workingSetIds the valid working set types, or null for all of them
   */
  public WorkingSetSelectionDialog(Shell parentShell, boolean multi, String[] workingSetIds) {
    super(parentShell, workingSetIds, true);
    contentProvider = ArrayContentProvider.getInstance();
    labelProvider = new WorkingSetLabelProvider();
    multiSelect = multi;
    if (multiSelect) {
      setTitle("Select Working Sets");
      setMessage("&Select working sets:");
    } else {
      setTitle("Select Working Set");
      setMessage("Selec&t a working set:");
    }
  }

  @Override
  protected void cancelPressed() {
    restoreAddedWorkingSets();
    restoreChangedWorkingSets();
    restoreRemovedWorkingSets();
    setSelection(null);
    super.cancelPressed();
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite composite = (Composite) super.createDialogArea(parent);
    createMessageArea(composite);

    com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter onSelect = new com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter() {
      @Override
      public void widgetSelected(com.unitesk.requality.vscode.ui.swt.events.SelectionEvent e) {
        updateButtonAvailability();
      }
    };
    buttonWindowSet = new Button(composite, SWT.RADIO);
    buttonWindowSet.setText("&Window Working Sets");
    buttonWindowSet.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    buttonWindowSet.addSelectionListener(onSelect);
    buttonNoSet = new Button(composite, SWT.RADIO);
    buttonNoSet.setText("N&o Working Sets");
    buttonNoSet.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    buttonNoSet.addSelectionListener(onSelect);
    buttonSelectedSets = new Button(composite, SWT.RADIO);
    buttonSelectedSets.setText("Se&lected Working Sets");
    buttonSelectedSets.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    buttonSelectedSets.addSelectionListener(onSelect);
    switch (getInitialRadioSelection()) {
      case 0:
        buttonWindowSet.setSelection(true);
        break;
      case 1:
        buttonNoSet.setSelection(true);
        break;
      default:
        buttonSelectedSets.setSelection(true);
        break;
    }

    GridLayout layout = new GridLayout(2, false);
    layout.marginHeight = layout.marginWidth = 0;
    layout.horizontalSpacing = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_SPACING);
    layout.verticalSpacing = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_SPACING);
    GridData data = new GridData(GridData.FILL_BOTH);
    data.heightHint = SIZING_SELECTION_WIDGET_HEIGHT;
    data.widthHint = SIZING_SELECTION_WIDGET_WIDTH + 300;
    Composite viewerComposite = new Composite(composite, SWT.NONE);
    viewerComposite.setLayout(layout);
    viewerComposite.setLayoutData(data);

    listViewer = CheckboxTableViewer.newCheckList(viewerComposite, SWT.BORDER | SWT.MULTI);
    data = new GridData(GridData.FILL_BOTH);
    data.heightHint = SIZING_SELECTION_WIDGET_HEIGHT;
    data.widthHint = SIZING_SELECTION_WIDGET_WIDTH;
    listViewer.getTable().setLayoutData(data);
    listViewer.setLabelProvider(labelProvider);
    listViewer.setContentProvider(contentProvider);
    listViewer.setComparator(new ViewerComparator());
    listViewer.addFilter(new WorkingSetFilter(getSupportedWorkingSetIds()));
    listViewer.addSelectionChangedListener(event -> updateButtonAvailability());
    listViewer.addDoubleClickListener(event -> {
      Object obj = listViewer.getStructuredSelection().getFirstElement();
      listViewer.setCheckedElements(new Object[] { obj });
      buttonWindowSet.setSelection(false);
      buttonNoSet.setSelection(false);
      buttonSelectedSets.setSelection(true);
      okPressed();
    });
    listViewer.addCheckStateListener(event -> {
      // implicitly select the "Selected Working Sets" choice
      buttonWindowSet.setSelection(false);
      buttonNoSet.setSelection(false);
      buttonSelectedSets.setSelection(true);
    });

    addModifyButtons(viewerComposite);
    addSelectionButtons(composite);

    listViewer.setInput(Arrays.asList(Workbench.INSTANCE.getWorkingSetManager().getWorkingSets()));
    List<?> initialElementSelections = getInitialElementSelections();
    if (multiSelect) {
      listViewer.setCheckedElements(initialElementSelections.toArray());
    } else if (!initialElementSelections.isEmpty()) {
      IWorkingSet set = (IWorkingSet) initialElementSelections.get(0);
      if (set instanceof IAggregateWorkingSet aggregate) {
        listViewer.setCheckedElements(aggregate.getComponents());
      } else {
        listViewer.setCheckedElements(initialElementSelections.toArray());
      }
    }
    availableWorkingSetsChanged();
    applyDialogFont(composite);
    return composite;
  }

  /** The radio button to select first: 0 the window working set, 1 no working set, 2 the selected working sets. */
  private int getInitialRadioSelection() {
    IWorkingSet windowSet = Workbench.INSTANCE.getActiveWorkbenchWindow().getActivePage().getAggregateWorkingSet();
    if (getSelection() != null && getSelection().length > 0) {
      return windowSet.equals(getSelection()[0]) ? 0 : 2;
    }
    return 1;
  }

  @Override
  protected Control createContents(Composite parent) {
    Control control = super.createContents(parent);
    List<?> selections = getInitialElementSelections();
    if (!selections.isEmpty()) {
      listViewer.setSelection(new StructuredSelection(selections), true);
    }
    updateButtonAvailability();
    return control;
  }

  @Override
  protected List<IWorkingSet> getSelectedWorkingSets() {
    return listViewer.getStructuredSelection().toList();
  }

  @Override
  protected void okPressed() {
    if (buttonWindowSet.getSelection()) {
      IWorkingSet[] windowSet = new IWorkingSet[] {
          Workbench.INSTANCE.getActiveWorkbenchWindow().getActivePage().getAggregateWorkingSet() };
      setSelection(windowSet);
      setResult(Arrays.asList(getSelection()));
    } else if (buttonNoSet.getSelection()) {
      setSelection(new IWorkingSet[0]);
      setResult(Arrays.asList(getSelection()));
    } else if (buttonSelectedSets.getSelection()) {
      Object[] untypedResult = listViewer.getCheckedElements();
      IWorkingSet[] typedResult = new IWorkingSet[untypedResult.length];
      System.arraycopy(untypedResult, 0, typedResult, 0, untypedResult.length);
      // if multiselect is allowed or there was only one selected then dont create an aggregate
      if (multiSelect || typedResult.length <= 1) {
        setSelection(typedResult);
        setResult(Arrays.asList(typedResult));
      } else {
        String setId = getAggregateIdForSets(typedResult);
        IWorkingSetManager workingSetManager = Workbench.INSTANCE.getWorkingSetManager();
        IWorkingSet aggregate = workingSetManager.getWorkingSet(setId);
        if (aggregate != null) {
          workingSetManager.removeWorkingSet(aggregate);
        }
        aggregate = workingSetManager.createAggregateWorkingSet(setId, "Multiple Working Sets", typedResult);
        workingSetManager.addWorkingSet(aggregate);
        setSelection(new IWorkingSet[] { aggregate });
        setResult(Collections.singletonList(aggregate));
      }
    }
    super.okPressed();
  }

  /** The name of the aggregate set made of the sets (long, not for display). */
  private String getAggregateIdForSets(IWorkingSet[] typedResult) {
    StringBuilder buffer = new StringBuilder();
    buffer.append("Aggregate:");
    for (IWorkingSet element : typedResult) {
      buffer.append(element.getName()).append(':');
    }
    return buffer.toString();
  }

  /** Removes the newly created working sets from the working set manager. */
  private void restoreAddedWorkingSets() {
    IWorkingSetManager manager = Workbench.INSTANCE.getWorkingSetManager();
    Iterator<IWorkingSet> iterator = getAddedWorkingSets().iterator();
    while (iterator.hasNext()) {
      manager.removeWorkingSet(iterator.next());
    }
  }

  /** Rolls back the changes to the working sets. */
  private void restoreChangedWorkingSets() {
    for (IWorkingSet editedWorkingSet : getEditedWorkingSets().keySet()) {
      IWorkingSet originalWorkingSet = getEditedWorkingSets().get(editedWorkingSet);
      if (!editedWorkingSet.getName().equals(originalWorkingSet.getName())) {
        editedWorkingSet.setName(originalWorkingSet.getName());
      }
      if (!Arrays.equals(editedWorkingSet.getElements(), originalWorkingSet.getElements())) {
        editedWorkingSet.setElements(originalWorkingSet.getElements());
      }
    }
  }

  /** Adds back the removed working sets to the working set manager. */
  private void restoreRemovedWorkingSets() {
    IWorkingSetManager manager = Workbench.INSTANCE.getWorkingSetManager();
    for (IWorkingSet workingSet : getRemovedWorkingSets()) {
      manager.addWorkingSet(workingSet);
    }
    for (IWorkingSet workingSet : getRemovedMRUWorkingSets()) {
      manager.addRecentWorkingSet(workingSet);
    }
  }

  @Override
  public void setSelection(IWorkingSet[] workingSets) {
    super.setSelection(workingSets);
    setInitialSelections(workingSets == null ? new Object[0] : workingSets);
  }

  @Override
  protected void availableWorkingSetsChanged() {
    listViewer.setInput(Workbench.INSTANCE.getWorkingSetManager().getWorkingSets());
    super.availableWorkingSetsChanged();
  }

  @Override
  protected void selectAllSets() {
    listViewer.setCheckedElements(Workbench.INSTANCE.getWorkingSetManager().getWorkingSets());
    buttonNoSet.setSelection(false);
    buttonSelectedSets.setSelection(true);
    updateButtonAvailability();
  }

  @Override
  protected void deselectAllSets() {
    listViewer.setCheckedElements(new Object[0]);
    buttonNoSet.setSelection(false);
    buttonSelectedSets.setSelection(true);
    updateButtonAvailability();
  }
}
