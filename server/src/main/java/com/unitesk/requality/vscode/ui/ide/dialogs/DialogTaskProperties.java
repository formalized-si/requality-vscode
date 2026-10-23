/*******************************************************************************
 * Copyright (c) 2000, 2020 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.unitesk.requality.vscode.ui.ide.dialogs;

import java.util.Map;

import com.unitesk.requality.vscode.ui.resources.IMarker;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Combo;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.ui.views.markers.internal.DialogTaskProperties}: the properties of a task (its priority and
 * whether it is completed, in addition to the marker's properties).
 */
public class DialogTaskProperties extends DialogMarkerProperties {
  private static final String PRIORITY_HIGH = "High";
  private static final String PRIORITY_NORMAL = "Normal";
  private static final String PRIORITY_LOW = "Low";
  static final String PRIORITY_LABEL = "&Priority:";
  static final String COMPLETED_LABEL = "&Completed";
  static final String CREATE_TASK = "Add Task";
  static final String MODIFY_TASK = "Modify Task";

  protected Combo priorityCombo;
  protected Button completedCheckbox;

  public DialogTaskProperties(Shell parentShell) {
    super(parentShell);
    setType(IMarker.TASK);
  }

  public DialogTaskProperties(Shell parentShell, String title) {
    super(parentShell, title);
    setType(IMarker.TASK);
  }

  @Override
  protected void createAttributesArea(Composite parent) {
    createSeperator(parent);
    super.createAttributesArea(parent);

    Label label = new Label(parent, SWT.NONE);
    label.setText(PRIORITY_LABEL);

    Composite composite = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout();
    layout.numColumns = 2;
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    composite.setLayout(layout);

    priorityCombo = new Combo(composite, SWT.READ_ONLY);
    priorityCombo.setItems(PRIORITY_HIGH, PRIORITY_NORMAL, PRIORITY_LOW);
    priorityCombo.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        if (getMarker() == null)
          getInitialAttributes().put(IMarker.PRIORITY, getPriorityFromDialog());
        markDirty();
      }
    });

    completedCheckbox = new Button(composite, SWT.CHECK);
    completedCheckbox.setText(COMPLETED_LABEL);
    GridData gridData = new GridData();
    gridData.horizontalIndent = 20;
    completedCheckbox.setLayoutData(gridData);
    completedCheckbox.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        if (getMarker() == null)
          getInitialAttributes().put(IMarker.DONE, completedCheckbox.getSelection());
        markDirty();
      }
    });
  }

  protected boolean getCompleted() {
    IMarker marker = getMarker();
    if (marker == null) {
      Object done = getInitialAttributes().get(IMarker.DONE);
      return done instanceof Boolean && (Boolean) done;
    }
    return marker.getAttribute(IMarker.DONE, false);
  }

  protected int getPriority() {
    IMarker marker = getMarker();
    if (marker == null) {
      Object priority = getInitialAttributes().get(IMarker.PRIORITY);
      return priority instanceof Integer ? (Integer) priority : IMarker.PRIORITY_NORMAL;
    }
    return marker.getAttribute(IMarker.PRIORITY, IMarker.PRIORITY_NORMAL);
  }

  @Override
  protected void updateEnablement() {
    super.updateEnablement();
    priorityCombo.setEnabled(isEditable());
    completedCheckbox.setEnabled(isEditable());
  }

  @Override
  protected void updateDialogForNewMarker() {
    selectPriority(getPriority());
    boolean completed = getCompleted();
    getInitialAttributes().put(IMarker.DONE, completed);
    completedCheckbox.setSelection(completed);
    super.updateDialogForNewMarker();
  }

  @Override
  protected void updateDialogFromMarker() {
    selectPriority(getPriority());
    boolean completed = getCompleted();
    getInitialAttributes().put(IMarker.DONE, completed);
    completedCheckbox.setSelection(completed);
    super.updateDialogFromMarker();
  }

  private void selectPriority(int priority) {
    if (priority == IMarker.PRIORITY_HIGH)
      priorityCombo.select(0);
    else if (priority == IMarker.PRIORITY_LOW)
      priorityCombo.select(2);
    else
      priorityCombo.select(1);
  }

  private int getPriorityFromDialog() {
    switch (priorityCombo.getSelectionIndex()) {
      case 0:
        return IMarker.PRIORITY_HIGH;
      case 2:
        return IMarker.PRIORITY_LOW;
      default:
        return IMarker.PRIORITY_NORMAL;
    }
  }

  @Override
  protected Map<String, Object> getMarkerAttributes() {
    Map<String, Object> attrs = super.getMarkerAttributes();
    attrs.put(IMarker.PRIORITY, getPriorityFromDialog());
    attrs.put(IMarker.DONE, completedCheckbox.getSelection());
    Object userEditable = attrs.get(IMarker.USER_EDITABLE);
    if (!(userEditable instanceof Boolean))
      attrs.put(IMarker.USER_EDITABLE, Boolean.TRUE);
    return attrs;
  }

  @Override
  protected String getModifyOperationTitle() {
    return MODIFY_TASK;
  }

  @Override
  protected String getCreateOperationTitle() {
    return CREATE_TASK;
  }
}
