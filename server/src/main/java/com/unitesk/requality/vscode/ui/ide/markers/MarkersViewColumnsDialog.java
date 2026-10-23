package com.unitesk.requality.vscode.ui.ide.markers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Group;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.swt.widgets.Spinner;

/**
 * {@code org.eclipse.ui.internal.views.markers.MarkersViewColumnsDialog} (Configure Columns...): the columns of a markers
 * view that are shown, in their order, and the width of the selected shown column. Restore Defaults shows the default
 * columns again.
 */
public class MarkersViewColumnsDialog extends Dialog {
  private static final int RESTORE_DEFAULTS_ID = IDialogConstants.CLIENT_ID + 1;
  private static final int MIN_WIDTH = 20;
  private static final int MAX_WIDTH = 1000;

  private final String markerType;
  /** The columns in their order, shown and hidden: the result of the dialog after OK. */
  private final List<String> shown = new ArrayList<>();
  private final List<String> hidden = new ArrayList<>();
  private final Map<String, Integer> widths = new LinkedHashMap<>();
  private com.unitesk.requality.vscode.ui.swt.widgets.List shownList, hiddenList;
  private Button hideButton, showButton, upButton, downButton;
  private Spinner widthSpinner;
  private Label widthLabel;

  /**
   * @param markerType the marker type of the view
   * @param shown the columns shown, in order
   * @param widths the widths of the columns by field
   */
  public MarkersViewColumnsDialog(Shell parent, String markerType, List<String> shown, Map<String, Integer> widths) {
    super(parent);
    this.markerType = markerType;
    this.shown.addAll(shown);
    this.widths.putAll(widths);
    for (String f : MarkerFields.generatorFields(markerType))
      if (!shown.contains(f))
        hidden.add(f);
  }

  @Override
  protected void configureShell(Shell newShell) {
    super.configureShell(newShell);
    newShell.setText("Configure Columns");
  }

  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    createButton(parent, RESTORE_DEFAULTS_ID, "Restore De&faults", false);
    createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
    createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite area = (Composite) super.createDialogArea(parent);
    Group group = new Group(area, SWT.NONE);
    group.setText("Hide/Show Columns");
    group.setLayout(new GridLayout(3, false));
    group.setLayoutData(new GridData(GridData.FILL_BOTH));

    Label shownLabel = new Label(group, SWT.NONE);
    shownLabel.setText("Sh&own:");
    new Label(group, SWT.NONE);
    Label hiddenLabel = new Label(group, SWT.NONE);
    hiddenLabel.setText("H&idden:");

    shownList = new com.unitesk.requality.vscode.ui.swt.widgets.List(group, SWT.BORDER | SWT.V_SCROLL | SWT.SINGLE);
    shownList.setLayoutData(new GridData(GridData.FILL_BOTH));
    Composite buttons = new Composite(group, SWT.NONE);
    buttons.setLayout(new GridLayout(1, false));
    buttons.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_CENTER));
    hideButton = new Button(buttons, SWT.PUSH);
    hideButton.setText("<- &Hide ");
    showButton = new Button(buttons, SWT.PUSH);
    showButton.setText("&Show ->");
    upButton = new Button(buttons, SWT.PUSH);
    upButton.setText("Move &Up");
    downButton = new Button(buttons, SWT.PUSH);
    downButton.setText("Move Do&wn");
    hiddenList = new com.unitesk.requality.vscode.ui.swt.widgets.List(group, SWT.BORDER | SWT.V_SCROLL | SWT.SINGLE);
    hiddenList.setLayoutData(new GridData(GridData.FILL_BOTH));

    Composite width = new Composite(area, SWT.NONE);
    width.setLayout(new GridLayout(2, false));
    width.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    widthLabel = new Label(width, SWT.NONE);
    widthLabel.setText("&Width of the selected shown column:");
    widthSpinner = new Spinner(width, SWT.BORDER);
    widthSpinner.setMinimum(MIN_WIDTH);
    widthSpinner.setMaximum(MAX_WIDTH);

    hideButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        moveSelected(false);
      }
    });
    showButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        moveSelected(true);
      }
    });
    upButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        moveShown(-1);
      }
    });
    downButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        moveShown(1);
      }
    });
    shownList.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        hiddenList.deselectAll();
        updateEnablement();
      }
    });
    hiddenList.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        shownList.deselectAll();
        updateEnablement();
      }
    });
    widthSpinner.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        String field = selectedShown();
        if (field != null)
          widths.put(field, widthSpinner.getSelection());
      }
    });
    refreshLists();
    return area;
  }

  private String selectedShown() {
    int i = shownList.getSelectionIndex();
    return i < 0 || i >= shown.size() ? null : shown.get(i);
  }

  private void refreshLists() {
    shownList.removeAll();
    for (String f : shown)
      shownList.add(MarkerFields.header(f));
    hiddenList.removeAll();
    for (String f : hidden)
      hiddenList.add(MarkerFields.header(f));
    updateEnablement();
  }

  /** Hides the selected shown column, or shows the selected hidden column (at the end). */
  private void moveSelected(boolean show) {
    if (show) {
      int i = hiddenList.getSelectionIndex();
      if (i < 0)
        return;
      String f = hidden.remove(i);
      shown.add(f);
      shownList.deselectAll();
      hiddenList.setSelection(i < hidden.size() ? i : hidden.size() - 1);
    } else {
      String f = selectedShown();
      if (f == null || shown.size() <= 1)
        return; // VSCODE: at least one column stays visible, as the preferences of Eclipse
      shown.remove(f);
      hidden.add(f);
      shownList.deselectAll();
    }
    refreshLists();
  }

  private void moveShown(int delta) {
    int i = shownList.getSelectionIndex();
    int j = i + delta;
    if (i < 0 || j < 0 || j >= shown.size())
      return;
    String f = shown.remove(i);
    shown.add(j, f);
    refreshLists();
    shownList.setSelection(j);
    updateEnablement();
  }

  private void updateEnablement() {
    String f = selectedShown();
    boolean hasShown = f != null;
    boolean hasHidden = hiddenList.getSelectionIndex() >= 0;
    hideButton.setEnabled(hasShown && shown.size() > 1);
    showButton.setEnabled(hasHidden);
    upButton.setEnabled(hasShown && shownList.getSelectionIndex() > 0);
    downButton.setEnabled(hasShown && shownList.getSelectionIndex() < shown.size() - 1);
    widthLabel.setEnabled(hasShown);
    widthSpinner.setEnabled(hasShown);
    if (hasShown) {
      Integer w = widths.get(f);
      widthSpinner.setSelection(w == null ? MarkerFields.width(f) : w);
    }
  }

  @Override
  protected void buttonPressed(int buttonId) {
    if (buttonId == RESTORE_DEFAULTS_ID) {
      shown.clear();
      hidden.clear();
      for (String f : MarkerFields.generatorFields(markerType))
        if (MarkerFields.visibleByDefault(markerType, f))
          shown.add(f);
        else
          hidden.add(f);
      refreshLists();
      return;
    }
    super.buttonPressed(buttonId);
  }

  /** The shown columns in their order (the result after OK). */
  public List<String> getShown() {
    return new ArrayList<>(shown);
  }

  /** The widths of the columns by field (the result after OK). */
  public Map<String, Integer> getWidths() {
    return new LinkedHashMap<>(widths);
  }
}
