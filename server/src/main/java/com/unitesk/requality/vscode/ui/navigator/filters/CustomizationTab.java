package com.unitesk.requality.vscode.ui.navigator.filters;

import java.util.HashSet;
import java.util.Set;

import com.unitesk.requality.vscode.ui.jface.viewers.CheckStateChangedEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.CheckboxTableViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ICheckStateListener;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionChangedListener;
import com.unitesk.requality.vscode.ui.navigator.INavigatorContentService;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Table;

/** {@code org.eclipse.ui.internal.navigator.filters.CustomizationTab}: a tab with an instruction and a checked table. */
public class CustomizationTab extends Composite {
  private final INavigatorContentService contentService;
  private CheckboxTableViewer tableViewer;
  private final Set<Object> checkedItems = new HashSet<>();
  private final ICheckStateListener checkListener = this::checkStateChanged;

  protected CustomizationTab(Composite parent, INavigatorContentService aContentService) {
    super(parent, SWT.RESIZE);
    contentService = aContentService;
    setLayout(new GridLayout());
    setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
  }

  protected Table getTable() {
    return tableViewer.getTable();
  }

  protected void addSelectionChangedListener(ISelectionChangedListener selectionListener) {
    if (tableViewer != null)
      tableViewer.addSelectionChangedListener(selectionListener);
  }

  protected void createTable() {
    tableViewer = CheckboxTableViewer.newCheckList(this, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
    tableViewer.addCheckStateListener(checkListener);
    tableViewer.getTable().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
  }

  protected void createInstructionsLabel(String labelText) {
    Label extensionsInstructionLabel = new Label(this, SWT.BOLD | SWT.WRAP);
    extensionsInstructionLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.GRAB_HORIZONTAL
        | GridData.HORIZONTAL_ALIGN_FILL | GridData.VERTICAL_ALIGN_FILL));
    extensionsInstructionLabel.setText(labelText);
  }

  protected final INavigatorContentService getContentService() {
    return contentService;
  }

  protected final CheckboxTableViewer getTableViewer() {
    return tableViewer;
  }

  protected Set<Object> getCheckedItems() {
    return checkedItems;
  }

  protected void checkStateChanged(CheckStateChangedEvent event) {
    if (event.getChecked())
      checkedItems.add(event.getElement());
    else
      checkedItems.remove(event.getElement());
  }
}
