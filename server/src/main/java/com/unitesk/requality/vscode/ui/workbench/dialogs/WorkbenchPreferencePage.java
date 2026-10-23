package com.unitesk.requality.vscode.ui.workbench.dialogs;

import com.unitesk.requality.vscode.core.ServerContext;
import com.unitesk.requality.vscode.ui.jface.preference.IPreferenceStore;
import com.unitesk.requality.vscode.ui.jface.preference.PreferencePage;
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
import com.unitesk.requality.vscode.ui.swt.widgets.Group;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPreferencePage;

/**
 * {@code org.eclipse.ui.internal.dialogs.WorkbenchPreferencePage}: the General page of Window &gt; Preferences (the
 * workbench settings: running in the background, the editor cycling, the view limit, the open mode of the lists).
 * The open mode is the setting {@code workbench.list.openMode} of VS Code: a change is sent to the extension
 * ({@code client/updateSetting}); the other settings are kept in the {@link WorkbenchPreferences} store.
 */
public class WorkbenchPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
  /** The setting of VS Code for the open mode of the lists. */
  public static final String OPEN_MODE_SETTING = "workbench.list.openMode";
  static final int MAX_SAVE_INTERVAL = 9999;
  static final int MAX_VIEW_LIMIT = 1_000_000;

  private Button showUserDialogButton;
  private Button stickyCycleButton;
  private Button showHeapStatusButton;
  private Text largeViewLimit;
  private Text saveInterval;
  private Button doubleClickButton;
  private Button singleClickButton;
  private Button selectOnHoverButton;
  private Button openAfterDelayButton;
  private Button showInlineRenameButton;
  private Button showFilterTextInMarkerViewsButton;
  private boolean openOnSingleClick;
  private boolean initialOpenOnSingleClick;
  private boolean selectOnHover;
  private boolean openAfterDelay;
  private boolean renameModeInline;

  @Override
  protected Control createContents(Composite parent) {
    Composite composite = createComposite(parent);
    createSettings(composite);
    createSpace(composite);
    createOpenModeGroup(composite);
    return composite;
  }

  protected void createSettings(Composite composite) {
    createShowUserDialogPref(composite);
    createStickyCyclePref(composite);
    createHeapStatusPref(composite);
    createLargeViewLimitPref(composite);
  }

  protected void createShowUserDialogPref(Composite composite) {
    showUserDialogButton = new Button(composite, SWT.CHECK);
    showUserDialogButton.setText("Always r&un in background");
    showUserDialogButton.setToolTipText("Run long operations in the background where possible");
    showUserDialogButton.setSelection(getPreferenceStore().getBoolean(WorkbenchPreferences.RUN_IN_BACKGROUND));
  }

  protected void createHeapStatusPref(Composite composite) {
    showHeapStatusButton = new Button(composite, SWT.CHECK);
    showHeapStatusButton.setText("Sho&w heap status");
    showHeapStatusButton.setToolTipText("Show the heap status area on the bottom of the window");
    showHeapStatusButton.setSelection(getPreferenceStore().getBoolean(WorkbenchPreferences.SHOW_MEMORY_MONITOR));
  }

  protected void createStickyCyclePref(Composite composite) {
    stickyCycleButton = new Button(composite, SWT.CHECK);
    stickyCycleButton.setText("Keep &next/previous editor, view and perspectives dialog open");
    stickyCycleButton.setSelection(getPreferenceStore().getBoolean(WorkbenchPreferences.STICKY_CYCLE));
  }

  protected void createLargeViewLimitPref(Composite composite) {
    Composite groupComposite = new Composite(composite, SWT.LEFT);
    GridLayout layout = new GridLayout();
    layout.numColumns = 2;
    groupComposite.setLayout(layout);
    GridData gd = new GridData();
    gd.horizontalAlignment = GridData.FILL;
    gd.grabExcessHorizontalSpace = true;
    groupComposite.setLayoutData(gd);
    Label label = new Label(groupComposite, SWT.LEFT);
    label.setText("Initial maximum number of elements shown in views:");
    label.setToolTipText(LARGE_VIEW_LIMIT_TOOLTIP);
    largeViewLimit = new Text(groupComposite, SWT.BORDER);
    largeViewLimit.setToolTipText(LARGE_VIEW_LIMIT_TOOLTIP);
    largeViewLimit.setTextLimit(7);
    largeViewLimit.setText(Integer.toString(getPreferenceStore().getInt(WorkbenchPreferences.LARGE_VIEW_LIMIT)));
    largeViewLimit.addModifyListener(new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent e) {
        validateInteger(largeViewLimit, 0, MAX_VIEW_LIMIT,
            "The limit should be an integer between 0 and " + MAX_VIEW_LIMIT + ".");
      }
    });
  }

  static final String LARGE_VIEW_LIMIT_TOOLTIP = "Use 0 for no limit. If the number of elements exceeds given limit, "
      + "user interaction is required to show more elements. This limit is a hint: not all views support this limit "
      + "or may use different one.";

  /** The integer of a field, or an error message set on the page when the text is not an integer in the range. */
  protected final boolean validateInteger(Text field, int min, int max, String error) {
    boolean ok;
    try {
      int v = Integer.parseInt(field.getText().trim());
      ok = v >= min && v <= max;
    } catch (NumberFormatException e) {
      ok = false;
    }
    setErrorMessage(ok ? null : error);
    setValid(ok);
    return ok;
  }

  protected Composite createComposite(Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout();
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    composite.setLayout(layout);
    composite.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL));
    return composite;
  }

  /** The workbench save interval (in minutes) of the General page: an integer between 0 and {@link #MAX_SAVE_INTERVAL}. */
  protected void createSaveIntervalGroup(Composite composite) {
    Composite groupComposite = new Composite(composite, SWT.LEFT);
    GridLayout layout = new GridLayout();
    layout.numColumns = 2;
    groupComposite.setLayout(layout);
    GridData gd = new GridData();
    gd.horizontalAlignment = GridData.FILL;
    gd.grabExcessHorizontalSpace = true;
    groupComposite.setLayoutData(gd);
    Label label = new Label(groupComposite, SWT.LEFT);
    label.setText("Workbench save &interval (in minutes):");
    saveInterval = new Text(groupComposite, SWT.BORDER);
    saveInterval.setTextLimit(Integer.toString(MAX_SAVE_INTERVAL).length());
    saveInterval.setText(Integer.toString(getPreferenceStore().getInt(WorkbenchPreferences.WORKBENCH_SAVE_INTERVAL)));
    saveInterval.addModifyListener(new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent e) {
        validateInteger(saveInterval, 0, MAX_SAVE_INTERVAL,
            "The workbench save interval should be an integer between 0 and " + MAX_SAVE_INTERVAL + ".");
      }
    });
  }

  /** The Open mode group: double click or single click (with its two options, kept in the workbench store). */
  protected void createOpenModeGroup(Composite composite) {
    Group buttonComposite = new Group(composite, SWT.LEFT);
    buttonComposite.setLayout(new GridLayout());
    buttonComposite.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
    buttonComposite.setText("Open mode");
    doubleClickButton = createRadioButton(buttonComposite, "D&ouble click");
    doubleClickButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        selectClickMode(singleClickButton.getSelection());
      }
    });
    doubleClickButton.setSelection(!openOnSingleClick);
    singleClickButton = createRadioButton(buttonComposite, "&Single click");
    singleClickButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        selectClickMode(singleClickButton.getSelection());
      }
    });
    singleClickButton.setSelection(openOnSingleClick);
    selectOnHoverButton = new Button(buttonComposite, SWT.CHECK | SWT.LEFT);
    selectOnHoverButton.setText("Select on &hover");
    selectOnHoverButton.setEnabled(openOnSingleClick);
    selectOnHoverButton.setSelection(selectOnHover);
    selectOnHoverButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        selectOnHover = selectOnHoverButton.getSelection();
      }
    });
    GridData data = new GridData();
    data.horizontalIndent = 20;
    selectOnHoverButton.setLayoutData(data);
    openAfterDelayButton = new Button(buttonComposite, SWT.CHECK | SWT.LEFT);
    openAfterDelayButton.setText("Open when using arrow &keys");
    openAfterDelayButton.setEnabled(openOnSingleClick);
    openAfterDelayButton.setSelection(openAfterDelay);
    openAfterDelayButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        openAfterDelay = openAfterDelayButton.getSelection();
      }
    });
    data = new GridData();
    data.horizontalIndent = 20;
    openAfterDelayButton.setLayoutData(data);
    Label note = new Label(buttonComposite, SWT.WRAP);
    note.setText("Note: This preference may not take effect on all views");
  }

  protected void createInlineRenamePref(Composite composite) {
    showInlineRenameButton = new Button(composite, SWT.CHECK);
    showInlineRenameButton.setText("&Rename resource inline if available");
    showInlineRenameButton.setToolTipText("&Rename resource inline if available");
    showInlineRenameButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        renameModeInline = showInlineRenameButton.getSelection();
      }
    });
    showInlineRenameButton.setSelection(renameModeInline);
  }

  protected void createShowFilterTextInMarkerViewsPref(Composite composite) {
    showFilterTextInMarkerViewsButton = new Button(composite, SWT.CHECK);
    showFilterTextInMarkerViewsButton.setText("&Initially show text filter in marker views");
    showFilterTextInMarkerViewsButton.setToolTipText("This preference is used to show/hide the text filter in the "
        + "Problems, Tasks, Bookmarks views. This preference has effect only if the user has not explicitly set the "
        + "visibility of the text filter using the corresponding toggle in the view menu.");
    showFilterTextInMarkerViewsButton.setSelection(
        getPreferenceStore().getBoolean(WorkbenchPreferences.INITIALLY_SHOW_FILTER_TEXT_IN_MARKER_VIEWS));
  }

  private void selectClickMode(boolean singleClick) {
    openOnSingleClick = singleClick;
    selectOnHoverButton.setEnabled(openOnSingleClick);
    openAfterDelayButton.setEnabled(openOnSingleClick);
  }

  protected static Button createRadioButton(Composite parent, String label) {
    Button button = new Button(parent, SWT.RADIO | SWT.LEFT);
    button.setText(label);
    return button;
  }

  protected static void createSpace(Composite parent) {
    Label vfiller = new Label(parent, SWT.LEFT);
    GridData gridData = new GridData();
    gridData.horizontalAlignment = GridData.BEGINNING;
    gridData.grabExcessHorizontalSpace = false;
    gridData.verticalAlignment = GridData.CENTER;
    gridData.grabExcessVerticalSpace = false;
    vfiller.setLayoutData(gridData);
  }

  @Override
  protected IPreferenceStore doGetPreferenceStore() {
    return WorkbenchPreferences.getStore();
  }

  @Override
  public void init(IWorkbench workbench) {
    IPreferenceStore store = getPreferenceStore();
    openOnSingleClick = store.getBoolean(WorkbenchPreferences.OPEN_ON_SINGLE_CLICK);
    initialOpenOnSingleClick = openOnSingleClick;
    selectOnHover = store.getBoolean(WorkbenchPreferences.SELECT_ON_HOVER);
    openAfterDelay = store.getBoolean(WorkbenchPreferences.OPEN_AFTER_DELAY);
    renameModeInline = !WorkbenchPreferences.RESOURCE_RENAME_MODE_DIALOG.equals(
        store.getString(WorkbenchPreferences.RESOURCE_RENAME_MODE));
  }

  @Override
  protected void performDefaults() {
    IPreferenceStore store = getPreferenceStore();
    showUserDialogButton.setSelection(store.getDefaultBoolean(WorkbenchPreferences.RUN_IN_BACKGROUND));
    stickyCycleButton.setSelection(store.getDefaultBoolean(WorkbenchPreferences.STICKY_CYCLE));
    showHeapStatusButton.setSelection(store.getDefaultBoolean(WorkbenchPreferences.SHOW_MEMORY_MONITOR));
    largeViewLimit.setText(Integer.toString(store.getDefaultInt(WorkbenchPreferences.LARGE_VIEW_LIMIT)));
    if (saveInterval != null)
      saveInterval.setText(Integer.toString(store.getDefaultInt(WorkbenchPreferences.WORKBENCH_SAVE_INTERVAL)));
    openOnSingleClick = store.getDefaultBoolean(WorkbenchPreferences.OPEN_ON_SINGLE_CLICK);
    selectOnHover = store.getDefaultBoolean(WorkbenchPreferences.SELECT_ON_HOVER);
    openAfterDelay = store.getDefaultBoolean(WorkbenchPreferences.OPEN_AFTER_DELAY);
    singleClickButton.setSelection(openOnSingleClick);
    doubleClickButton.setSelection(!openOnSingleClick);
    selectOnHoverButton.setSelection(selectOnHover);
    openAfterDelayButton.setSelection(openAfterDelay);
    selectOnHoverButton.setEnabled(openOnSingleClick);
    openAfterDelayButton.setEnabled(openOnSingleClick);
    renameModeInline = !WorkbenchPreferences.RESOURCE_RENAME_MODE_DIALOG.equals(
        store.getDefaultString(WorkbenchPreferences.RESOURCE_RENAME_MODE));
    if (showInlineRenameButton != null)
      showInlineRenameButton.setSelection(renameModeInline);
    if (showFilterTextInMarkerViewsButton != null)
      showFilterTextInMarkerViewsButton.setSelection(
          store.getDefaultBoolean(WorkbenchPreferences.INITIALLY_SHOW_FILTER_TEXT_IN_MARKER_VIEWS));
    super.performDefaults();
  }

  @Override
  public boolean performOk() {
    IPreferenceStore store = getPreferenceStore();
    store.setValue(WorkbenchPreferences.STICKY_CYCLE, stickyCycleButton.getSelection());
    store.setValue(WorkbenchPreferences.OPEN_ON_SINGLE_CLICK, openOnSingleClick);
    store.setValue(WorkbenchPreferences.SELECT_ON_HOVER, selectOnHover);
    store.setValue(WorkbenchPreferences.OPEN_AFTER_DELAY, openAfterDelay);
    store.setValue(WorkbenchPreferences.RUN_IN_BACKGROUND, showUserDialogButton.getSelection());
    if (saveInterval != null)
      store.setValue(WorkbenchPreferences.WORKBENCH_SAVE_INTERVAL, Integer.parseInt(saveInterval.getText().trim()));
    store.setValue(WorkbenchPreferences.LARGE_VIEW_LIMIT, Integer.parseInt(largeViewLimit.getText().trim()));
    store.setValue(WorkbenchPreferences.RESOURCE_RENAME_MODE, renameModeInline
        ? WorkbenchPreferences.RESOURCE_RENAME_MODE_INLINE : WorkbenchPreferences.RESOURCE_RENAME_MODE_DIALOG);
    if (showFilterTextInMarkerViewsButton != null)
      store.setValue(WorkbenchPreferences.INITIALLY_SHOW_FILTER_TEXT_IN_MARKER_VIEWS,
          showFilterTextInMarkerViewsButton.getSelection());
    store.setValue(WorkbenchPreferences.SHOW_MEMORY_MONITOR, showHeapStatusButton.getSelection());
    if (openOnSingleClick != initialOpenOnSingleClick)
      sendOpenMode(openOnSingleClick);
    initialOpenOnSingleClick = openOnSingleClick;
    return true;
  }

  /** The open mode of the lists is a setting of VS Code: the extension writes it. */
  private static void sendOpenMode(boolean singleClick) {
    if (ServerContext.server() == null)
      return;
    com.google.gson.JsonObject p = new com.google.gson.JsonObject();
    p.addProperty("key", OPEN_MODE_SETTING);
    p.addProperty("value", singleClick ? "singleClick" : "doubleClick");
    ServerContext.client().notify("client/updateSetting", p);
  }
}
