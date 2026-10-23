package com.unitesk.requality.vscode.ui.ide.dialogs;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.preference.PreferencePage;
import com.unitesk.requality.vscode.ui.resources.IWorkspaceDescription;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.ModifyEvent;
import com.unitesk.requality.vscode.ui.swt.events.ModifyListener;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPreferencePage;

/**
 * {@code org.eclipse.ui.internal.ide.dialogs.FileStatesPage}: the Local History page of Window &gt; Preferences: the
 * limits of the states of the files (the days to keep them, the entries per file, the size of the files) with the
 * validation messages of Eclipse. The values are the ones of the workspace description
 * ({@link IWorkspaceDescription}), which the local history of the files applies.
 */
public class FileStatesPage extends PreferencePage implements IWorkbenchPreferencePage {
  private static final int FAILED_VALUE = -1;
  private static final long DAY_LENGTH = 86400000;
  private static final long MEGABYTES = 1024 * 1024;
  private static final int FILE_STATES_MAXIMUM = 10000;
  private static final long STATE_SIZE_MAXIMUM = 100;
  private static final int INDENT = 20;

  private Text longevityText;
  private Text maxStatesText;
  private Text maxStateSizeText;
  private Button applyPolicyButton;
  private Button keepDerivedStateButton;
  private final List<Control> dependentControls = new ArrayList<>();

  @Override
  public void init(IWorkbench workbench) {
  }

  private static IWorkspaceDescription description() {
    return ResourcesPlugin.getWorkspace().getDescription();
  }

  @Override
  protected Control createContents(Composite parent) {
    IWorkspaceDescription description = description();
    long days = Math.max(1, description.getFileStateLongevity() / DAY_LENGTH);
    long megabytes = Math.max(1, description.getMaxFileStateSize() / MEGABYTES);
    Composite composite = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout();
    layout.numColumns = 2;
    layout.marginLeft = INDENT;
    layout.marginWidth = 0;
    composite.setLayout(layout);
    keepDerivedStateButton = addCheckBox("History for derived files", description.isKeepDerivedState(), composite);
    applyPolicyButton = addCheckBox("&Limit history size", description.isApplyFileStatePolicy(), composite);
    longevityText = addDependentLabelAndText("Days to &keep files:", String.valueOf(days), composite);
    maxStatesText = addDependentLabelAndText("Maximum &entries per file:",
        String.valueOf(description.getMaxFileStates()), composite);
    maxStateSizeText = addDependentLabelAndText("&Maximum file size (MB):", String.valueOf(megabytes), composite);
    checkState();
    Label spacer = new Label(composite, SWT.NONE);
    GridData spacerData = new GridData();
    spacerData.horizontalSpan = 2;
    spacer.setLayoutData(spacerData);
    Label note = new Label(composite, SWT.WRAP);
    note.setText("Note: The 'Maximum entries per file' and the 'Days to keep files' values\n"
        + "are only applied when compacting the local history on shutdown.");
    GridData noteData = new GridData();
    noteData.horizontalSpan = 2;
    note.setLayoutData(noteData);
    dependentControls.add(note);
    return composite;
  }

  private Button addCheckBox(String label, boolean selected, Composite parent) {
    Button button = new Button(parent, SWT.CHECK | SWT.LEFT);
    button.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        checkState();
      }
    });
    GridData data = new GridData();
    data.horizontalSpan = 2;
    data.horizontalIndent = -INDENT;
    button.setLayoutData(data);
    button.setText(label);
    button.setSelection(selected);
    return button;
  }

  private Text addDependentLabelAndText(String labelString, String textValue, Composite parent) {
    Label label = new Label(parent, SWT.LEFT);
    label.setText(labelString);
    dependentControls.add(label);
    Text text = new Text(parent, SWT.LEFT | SWT.BORDER);
    text.addModifyListener(new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent e) {
        checkState();
      }
    });
    GridData data = new GridData();
    data.horizontalAlignment = GridData.FILL;
    data.grabExcessHorizontalSpace = true;
    data.verticalAlignment = GridData.CENTER;
    data.grabExcessVerticalSpace = false;
    text.setLayoutData(data);
    text.setText(textValue);
    dependentControls.add(text);
    return text;
  }

  private void checkState() {
    if (longevityText == null || maxStatesText == null || maxStateSizeText == null || applyPolicyButton == null
        || keepDerivedStateButton == null) {
      setValid(false);
      return;
    }
    boolean enabled = applyPolicyButton.getSelection();
    for (Control c : dependentControls)
      c.setEnabled(enabled);
    if (validateLongTextEntry(longevityText, DAY_LENGTH) == FAILED_VALUE
        || validateMaxFileStates() == FAILED_VALUE
        || validateMaxFileStateSize() == FAILED_VALUE) {
      setValid(false);
      return;
    }
    setValid(true);
    setErrorMessage(null);
  }

  @Override
  protected void performDefaults() {
    super.performDefaults();
    longevityText.setText(String.valueOf(7));
    maxStatesText.setText("50");
    maxStateSizeText.setText("1");
    keepDerivedStateButton.setSelection(false);
    applyPolicyButton.setSelection(true);
    checkState();
  }

  @Override
  public boolean performOk() {
    long longevityValue = validateLongTextEntry(longevityText, DAY_LENGTH);
    int maxFileStates = validateMaxFileStates();
    long maxStateSize = validateMaxFileStateSize();
    if (longevityValue == FAILED_VALUE || maxFileStates == FAILED_VALUE || maxStateSize == FAILED_VALUE)
      return false;
    IWorkspaceDescription description = description();
    description.setFileStateLongevity(longevityValue * DAY_LENGTH);
    description.setMaxFileStates(maxFileStates);
    description.setMaxFileStateSize(maxStateSize * MEGABYTES);
    description.setKeepDerivedState(keepDerivedStateButton.getSelection());
    description.setApplyFileStatePolicy(applyPolicyButton.getSelection());
    return true;
  }

  private int validateIntegerTextEntry(Text text) {
    int value;
    try {
      value = Integer.parseInt(text.getText());
    } catch (NumberFormatException exception) {
      setErrorMessage(MessageFormat.format("Invalid value: {0}", exception.getLocalizedMessage()));
      return FAILED_VALUE;
    }
    if (value <= 0) {
      setErrorMessage("Values must be positive");
      return FAILED_VALUE;
    }
    return value;
  }

  private long validateLongTextEntry(Text text, long scale) {
    long value;
    try {
      String string = text.getText();
      value = Long.parseLong(string);
      if (value * scale / scale != value)
        throw new NumberFormatException(string);
    } catch (NumberFormatException exception) {
      setErrorMessage(MessageFormat.format("Invalid value: {0}", exception.getLocalizedMessage()));
      return FAILED_VALUE;
    }
    if (value <= 0) {
      setErrorMessage("Values must be positive");
      return FAILED_VALUE;
    }
    return value;
  }

  private int validateMaxFileStates() {
    int maxFileStates = validateIntegerTextEntry(maxStatesText);
    if (maxFileStates == FAILED_VALUE)
      return maxFileStates;
    if (maxFileStates > FILE_STATES_MAXIMUM) {
      setErrorMessage("Above maximum Entries per file: " + FILE_STATES_MAXIMUM);
      return FAILED_VALUE;
    }
    return maxFileStates;
  }

  private long validateMaxFileStateSize() {
    long maxFileStateSize = validateLongTextEntry(maxStateSizeText, MEGABYTES);
    if (maxFileStateSize == FAILED_VALUE)
      return maxFileStateSize;
    if (maxFileStateSize > STATE_SIZE_MAXIMUM) {
      setErrorMessage("Above maximum file size value: " + STATE_SIZE_MAXIMUM);
      return FAILED_VALUE;
    }
    return maxFileStateSize;
  }
}
