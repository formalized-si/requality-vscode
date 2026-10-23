package com.unitesk.requality.vscode.ui.ide.dialogs;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import com.unitesk.requality.vscode.core.ServerContext;
import com.unitesk.requality.vscode.ui.jface.preference.IPreferenceStore;
import com.unitesk.requality.vscode.ui.jface.preference.PreferencePage;
import com.unitesk.requality.vscode.ui.resources.IMarker;
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
import com.unitesk.requality.vscode.ui.swt.widgets.Combo;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Group;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPreferencePage;

/**
 * {@code org.eclipse.ui.internal.ide.dialogs.IDEWorkspacePreferencePage}: the Workspace page of Window &gt; Preferences
 * (the controls of the current IDE page): the refresh of external changes, the close of unrelated projects, the
 * workspace save interval, the window title, the projects opened with a project, the severities of the unknown natures
 * and of the missing encodings, the system explorer command, the text file encoding and the line delimiter of new text
 * files. The workspace settings go to the workspace description ({@link IWorkspaceDescription}); the others to the
 * {@link IDEInternalPreferences} store. The build settings are on the Build page.
 */
public class IDEWorkspacePreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
  static final String DEFAULT_ENCODING = "UTF-8";
  private static final String[] LINE_DELIMITER_NAMES = { "Unix", "Windows", "Mac" };
  private static final String[] LINE_DELIMITERS = { "\n", "\r\n", "\r" };
  private static final String[] OPEN_VALUES = { IDEInternalPreferences.PSPM_ALWAYS, IDEInternalPreferences.PSPM_NEVER,
      IDEInternalPreferences.PSPM_PROMPT };
  private static final String[] OPEN_LABELS = { "Always", "Never", "Prompt" };
  private static final int[] SEVERITIES = { IMarker.SEVERITY_INFO, IMarker.SEVERITY_WARNING, IMarker.SEVERITY_ERROR };
  private static final String[] SEVERITY_LABELS = { "Info", "Warning", "Error" };

  private Button autoRefreshButton;
  private Button lightweightRefreshButton;
  private Button closeUnrelatedProjectButton;
  private Text saveInterval;
  private Button showLocationNameInTitle;
  private Text workspaceName;
  private Button showPerspectiveNameInTitle;
  private Button showLocationPathInTitle;
  private Button showProductNameInTitle;
  private Button showActiveEditorInfoInTitle;
  private Combo openReferencesCombo;
  private Combo missingNatureCombo;
  private Combo missingEncodingCombo;
  private Text systemExplorer;
  private Button defaultEncodingButton;
  private Button otherEncodingButton;
  private Combo encodingCombo;
  private Button defaultLineDelimiterButton;
  private Button otherLineDelimiterButton;
  private Combo lineDelimiterCombo;

  @Override
  public void init(IWorkbench workbench) {
  }

  @Override
  protected Control createContents(Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout();
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    composite.setLayout(layout);
    composite.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL));
    Label related = new Label(composite, SWT.WRAP);
    related.setText("See Startup and Shutdown for workspace startup and shutdown preferences.");
    related.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    createSpace(composite);
    createAutoRefreshControls(composite);
    closeUnrelatedProjectButton = new Button(composite, SWT.CHECK);
    closeUnrelatedProjectButton.setText("Always &close unrelated projects without prompt");
    closeUnrelatedProjectButton.setToolTipText("Close unrelated projects without prompt");
    closeUnrelatedProjectButton.setSelection(store().getBoolean(IDEInternalPreferences.CLOSE_UNRELATED_PROJECTS));
    createSaveIntervalGroup(composite);
    createSpace(composite);
    createWorkspaceLocationGroup(composite);
    createSpace(composite);
    Composite comboParent = new Composite(composite, SWT.NONE);
    comboParent.setLayout(new GridLayout(2, false));
    createOpenPrefControls(comboParent);
    missingNatureCombo = createSeverityCombo(comboParent, "Report unknown project n&ature as:",
        IDEInternalPreferences.MISSING_NATURE_SEVERITY);
    missingEncodingCombo = createSeverityCombo(comboParent, "Rep&ort missing project encoding as:",
        IDEInternalPreferences.MISSING_ENCODING_SEVERITY);
    createSpace(composite);
    createSystemExplorerGroup(composite);
    createSpace(composite);
    Composite lower = new Composite(composite, SWT.NONE);
    GridLayout lowerLayout = new GridLayout();
    lowerLayout.marginWidth = 0;
    lowerLayout.numColumns = 2;
    lowerLayout.makeColumnsEqualWidth = true;
    lower.setLayout(lowerLayout);
    lower.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
    createEncodingEditorControls(lower);
    createLineSeparatorEditorControls(lower);
    return composite;
  }

  private static IPreferenceStore store() {
    return IDEInternalPreferences.getStore();
  }

  private static IWorkspaceDescription description() {
    return ResourcesPlugin.getWorkspace().getDescription();
  }

  private void createAutoRefreshControls(Composite parent) {
    autoRefreshButton = new Button(parent, SWT.CHECK);
    autoRefreshButton.setText("&Refresh using native hooks or polling");
    autoRefreshButton.setToolTipText("Automatically refresh external workspace changes using native hooks or polling");
    autoRefreshButton.setSelection(store().getBoolean(IDEInternalPreferences.PREF_AUTO_REFRESH));
    lightweightRefreshButton = new Button(parent, SWT.CHECK);
    lightweightRefreshButton.setText("Refresh on acce&ss");
    lightweightRefreshButton.setToolTipText("Automatically refresh external workspace changes on access via the workspace");
    lightweightRefreshButton.setSelection(store().getBoolean(IDEInternalPreferences.PREF_LIGHTWEIGHT_AUTO_REFRESH));
  }

  /** The workspace save interval, in minutes: an integer between 1 and {@link IDEInternalPreferences#MAX_SAVE_INTERVAL}. */
  private void createSaveIntervalGroup(Composite composite) {
    Composite groupComposite = new Composite(composite, SWT.LEFT);
    GridLayout layout = new GridLayout();
    layout.numColumns = 2;
    groupComposite.setLayout(layout);
    GridData gd = new GridData();
    gd.horizontalAlignment = GridData.FILL;
    gd.grabExcessHorizontalSpace = true;
    groupComposite.setLayoutData(gd);
    Label label = new Label(groupComposite, SWT.LEFT);
    label.setText("&Workspace save interval (in minutes):");
    saveInterval = new Text(groupComposite, SWT.BORDER);
    saveInterval.setTextLimit(Integer.toString(IDEInternalPreferences.MAX_SAVE_INTERVAL).length());
    saveInterval.setText(Long.toString(description().getSnapshotInterval() / 60000));
    saveInterval.addModifyListener(new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent e) {
        validate(saveInterval, 1, IDEInternalPreferences.MAX_SAVE_INTERVAL,
            "The workspace save interval should be between 1 and " + IDEInternalPreferences.MAX_SAVE_INTERVAL + ".");
      }
    });
  }

  /** Window title: which parts of the workspace the title of the window shows. */
  private void createWorkspaceLocationGroup(Composite composite) {
    IPreferenceStore store = store();
    Composite groupComposite = new Composite(composite, SWT.LEFT);
    groupComposite.setLayout(new GridLayout(2, false));
    groupComposite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    Group title = new Group(groupComposite, SWT.NONE);
    title.setText("Window title");
    title.setLayout(new GridLayout(1, false));
    title.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
    Composite nameComposite = new Composite(title, SWT.NONE);
    nameComposite.setLayout(new GridLayout(2, false));
    showLocationNameInTitle = new Button(nameComposite, SWT.CHECK);
    showLocationNameInTitle.setText("Show workspace nam&e:");
    showLocationNameInTitle.setSelection(store.getBoolean(IDEInternalPreferences.SHOW_LOCATION_NAME));
    workspaceName = new Text(nameComposite, SWT.BORDER);
    workspaceName.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    workspaceName.setText(store.getString(IDEInternalPreferences.WORKSPACE_NAME));
    showPerspectiveNameInTitle = new Button(title, SWT.CHECK);
    showPerspectiveNameInTitle.setText("Show perspec&tive name");
    showPerspectiveNameInTitle.setSelection(store.getBoolean(IDEInternalPreferences.SHOW_PERSPECTIVE_IN_TITLE));
    Composite pathComposite = new Composite(title, SWT.NONE);
    pathComposite.setLayout(new GridLayout(2, false));
    showLocationPathInTitle = new Button(pathComposite, SWT.CHECK);
    showLocationPathInTitle.setText("Show &full workspace path:");
    showLocationPathInTitle.setSelection(store.getBoolean(IDEInternalPreferences.SHOW_LOCATION));
    Text workspacePath = new Text(pathComposite, SWT.READ_ONLY);
    workspacePath.setText(workspaceLocation());
    workspacePath.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
    showProductNameInTitle = new Button(title, SWT.CHECK);
    showProductNameInTitle.setText("Show product name");
    showProductNameInTitle.setSelection(store.getBoolean(IDEInternalPreferences.SHOW_PRODUCT_IN_TITLE));
    showActiveEditorInfoInTitle = new Button(title, SWT.CHECK);
    showActiveEditorInfoInTitle.setText("Show active editor name");
    showActiveEditorInfoInTitle.setSelection(store.getBoolean(IDEInternalPreferences.SHOW_ACTIVE_EDITOR_INFO_IN_TITLE));
  }

  /** The location of the workspace: its first folder (the workspace root of the port). */
  private static String workspaceLocation() {
    if (ServerContext.server() == null || ServerContext.server().workspace().folders().isEmpty())
      return "";
    return ServerContext.server().workspace().folders().get(0).getAbsolutePath();
  }

  private void createOpenPrefControls(Composite parent) {
    Label label = new Label(parent, SWT.NONE);
    label.setText("Open referenced projects when a project is opened:");
    openReferencesCombo = new Combo(parent, SWT.READ_ONLY);
    openReferencesCombo.setItems(OPEN_LABELS);
    String current = store().getString(IDEInternalPreferences.OPEN_REQUIRED_PROJECTS);
    openReferencesCombo.select(Math.max(0, indexOf(OPEN_VALUES, current)));
  }

  /** A severity combo: Ignore (-1), Info, Warning or Error for the markers reported. */
  private Combo createSeverityCombo(Composite parent, String labelText, String key) {
    Label label = new Label(parent, SWT.NONE);
    label.setText(labelText);
    Combo combo = new Combo(parent, SWT.READ_ONLY);
    List<String> items = new ArrayList<>();
    items.add("Ignore");
    for (String l : SEVERITY_LABELS)
      items.add(l);
    combo.setItems(items.toArray(new String[0]));
    int severity = store().getInt(key);
    int index = severity == IDEInternalPreferences.SEVERITY_IGNORE ? 0 : 1 + indexOf(SEVERITIES, severity);
    combo.select(Math.max(0, index));
    return combo;
  }

  private void createSystemExplorerGroup(Composite composite) {
    Composite groupComposite = new Composite(composite, SWT.LEFT);
    groupComposite.setLayout(new GridLayout(2, false));
    groupComposite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    Label label = new Label(groupComposite, SWT.NONE);
    label.setText("Command for launching system e&xplorer:");
    systemExplorer = new Text(groupComposite, SWT.BORDER);
    systemExplorer.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    systemExplorer.setText(store().getString(IDEInternalPreferences.WORKBENCH_SYSTEM_EXPLORER));
  }

  /** Text file encoding: the default (UTF-8) or another charset, which is the encoding of the files without their own. */
  private void createEncodingEditorControls(Composite parent) {
    Group group = new Group(parent, SWT.NONE);
    group.setLayout(new GridLayout(2, false));
    group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    group.setText("Text file encoding");
    String current = description().getEncoding();
    List<String> names = new ArrayList<>(new TreeSet<>(Charset.availableCharsets().keySet()));
    defaultEncodingButton = new Button(group, SWT.RADIO);
    defaultEncodingButton.setText("Defa&ult (" + DEFAULT_ENCODING + ")");
    defaultEncodingButton.setLayoutData(gridSpan(2));
    otherEncodingButton = new Button(group, SWT.RADIO);
    otherEncodingButton.setText("&Other:");
    encodingCombo = new Combo(group, SWT.READ_ONLY);
    encodingCombo.setItems(names.toArray(new String[0]));
    encodingCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    encodingCombo.setVisibleItemCount(20);
    boolean isDefault = DEFAULT_ENCODING.equals(current);
    defaultEncodingButton.setSelection(isDefault);
    otherEncodingButton.setSelection(!isDefault);
    encodingCombo.select(Math.max(0, names.indexOf(isDefault ? DEFAULT_ENCODING : current)));
    SelectionAdapter radio = new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        encodingCombo.setEnabled(otherEncodingButton.getSelection());
      }
    };
    defaultEncodingButton.addSelectionListener(radio);
    otherEncodingButton.addSelectionListener(radio);
    encodingCombo.setEnabled(otherEncodingButton.getSelection());
  }

  /** New text file line delimiter: the default of the platform or one of the delimiters. */
  private void createLineSeparatorEditorControls(Composite parent) {
    Group group = new Group(parent, SWT.NONE);
    group.setLayout(new GridLayout(2, false));
    group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    group.setText("New text file line delimiter");
    String current = store().getString(IDEInternalPreferences.LINE_DELIMITER);
    defaultLineDelimiterButton = new Button(group, SWT.RADIO);
    defaultLineDelimiterButton.setText("D&efault (" + lineDelimiterName(System.lineSeparator()) + ")");
    defaultLineDelimiterButton.setLayoutData(gridSpan(2));
    otherLineDelimiterButton = new Button(group, SWT.RADIO);
    otherLineDelimiterButton.setText("Ot&her:");
    lineDelimiterCombo = new Combo(group, SWT.READ_ONLY);
    lineDelimiterCombo.setItems(LINE_DELIMITER_NAMES);
    lineDelimiterCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    int index = indexOfDelimiter(current);
    defaultLineDelimiterButton.setSelection(index < 0);
    otherLineDelimiterButton.setSelection(index >= 0);
    lineDelimiterCombo.select(Math.max(0, index));
    SelectionAdapter radio = new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        lineDelimiterCombo.setEnabled(otherLineDelimiterButton.getSelection());
      }
    };
    defaultLineDelimiterButton.addSelectionListener(radio);
    otherLineDelimiterButton.addSelectionListener(radio);
    lineDelimiterCombo.setEnabled(otherLineDelimiterButton.getSelection());
  }

  private static String lineDelimiterName(String delimiter) {
    int i = indexOfDelimiter(delimiter);
    return i < 0 ? "Unix" : LINE_DELIMITER_NAMES[i];
  }

  private static int indexOfDelimiter(String delimiter) {
    for (int i = 0; i < LINE_DELIMITERS.length; i++)
      if (LINE_DELIMITERS[i].equals(delimiter))
        return i;
    return -1;
  }

  private static int indexOf(String[] values, String value) {
    for (int i = 0; i < values.length; i++)
      if (values[i].equals(value))
        return i;
    return -1;
  }

  private static int indexOf(int[] values, int value) {
    for (int i = 0; i < values.length; i++)
      if (values[i] == value)
        return i;
    return -1;
  }

  private static GridData gridSpan(int span) {
    GridData gd = new GridData();
    gd.horizontalSpan = span;
    return gd;
  }

  /** Sets the error message and the validity of the page for an integer field in a range. */
  private boolean validate(Text field, int min, int max, String error) {
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

  private static void createSpace(Composite parent) {
    Label vfiller = new Label(parent, SWT.LEFT);
    GridData gridData = new GridData();
    gridData.horizontalAlignment = GridData.BEGINNING;
    gridData.grabExcessHorizontalSpace = false;
    gridData.verticalAlignment = GridData.CENTER;
    gridData.grabExcessVerticalSpace = false;
    vfiller.setLayoutData(gridData);
  }

  @Override
  protected void performDefaults() {
    IPreferenceStore store = store();
    autoRefreshButton.setSelection(store.getDefaultBoolean(IDEInternalPreferences.PREF_AUTO_REFRESH));
    lightweightRefreshButton.setSelection(store.getDefaultBoolean(IDEInternalPreferences.PREF_LIGHTWEIGHT_AUTO_REFRESH));
    closeUnrelatedProjectButton.setSelection(store.getDefaultBoolean(IDEInternalPreferences.CLOSE_UNRELATED_PROJECTS));
    saveInterval.setText("5");
    showLocationPathInTitle.setSelection(store.getDefaultBoolean(IDEInternalPreferences.SHOW_LOCATION));
    showLocationNameInTitle.setSelection(store.getDefaultBoolean(IDEInternalPreferences.SHOW_LOCATION_NAME));
    showPerspectiveNameInTitle.setSelection(store.getDefaultBoolean(IDEInternalPreferences.SHOW_PERSPECTIVE_IN_TITLE));
    showProductNameInTitle.setSelection(store.getDefaultBoolean(IDEInternalPreferences.SHOW_PRODUCT_IN_TITLE));
    showActiveEditorInfoInTitle.setSelection(store.getDefaultBoolean(IDEInternalPreferences.SHOW_ACTIVE_EDITOR_INFO_IN_TITLE));
    workspaceName.setText(store.getDefaultString(IDEInternalPreferences.WORKSPACE_NAME));
    openReferencesCombo.select(Math.max(0, indexOf(OPEN_VALUES, store.getDefaultString(IDEInternalPreferences.OPEN_REQUIRED_PROJECTS))));
    missingNatureCombo.select(1 + indexOf(SEVERITIES, store.getDefaultInt(IDEInternalPreferences.MISSING_NATURE_SEVERITY)));
    missingEncodingCombo.select(1 + indexOf(SEVERITIES, store.getDefaultInt(IDEInternalPreferences.MISSING_ENCODING_SEVERITY)));
    systemExplorer.setText(store.getDefaultString(IDEInternalPreferences.WORKBENCH_SYSTEM_EXPLORER));
    defaultEncodingButton.setSelection(true);
    otherEncodingButton.setSelection(false);
    encodingCombo.setEnabled(false);
    encodingCombo.select(Math.max(0, new ArrayList<>(new TreeSet<>(Charset.availableCharsets().keySet())).indexOf(DEFAULT_ENCODING)));
    defaultLineDelimiterButton.setSelection(true);
    otherLineDelimiterButton.setSelection(false);
    lineDelimiterCombo.setEnabled(false);
    super.performDefaults();
  }

  @Override
  public boolean performOk() {
    long minutes;
    try {
      minutes = Long.parseLong(saveInterval.getText().trim());
    } catch (NumberFormatException e) {
      return false;
    }
    if (minutes < 1 || minutes > IDEInternalPreferences.MAX_SAVE_INTERVAL)
      return false;
    IWorkspaceDescription description = description();
    if (minutes * 60000 != description.getSnapshotInterval())
      description.setSnapshotInterval(minutes * 60000);
    description.setEncoding(defaultEncodingButton.getSelection() ? DEFAULT_ENCODING : encodingCombo.getText());
    IPreferenceStore store = store();
    store.setValue(IDEInternalPreferences.PREF_AUTO_REFRESH, autoRefreshButton.getSelection());
    store.setValue(IDEInternalPreferences.PREF_LIGHTWEIGHT_AUTO_REFRESH, lightweightRefreshButton.getSelection());
    store.setValue(IDEInternalPreferences.CLOSE_UNRELATED_PROJECTS, closeUnrelatedProjectButton.getSelection());
    store.setValue(IDEInternalPreferences.SHOW_LOCATION, showLocationPathInTitle.getSelection());
    store.setValue(IDEInternalPreferences.SHOW_LOCATION_NAME, showLocationNameInTitle.getSelection());
    store.setValue(IDEInternalPreferences.SHOW_PERSPECTIVE_IN_TITLE, showPerspectiveNameInTitle.getSelection());
    store.setValue(IDEInternalPreferences.SHOW_PRODUCT_IN_TITLE, showProductNameInTitle.getSelection());
    store.setValue(IDEInternalPreferences.SHOW_ACTIVE_EDITOR_INFO_IN_TITLE, showActiveEditorInfoInTitle.getSelection());
    store.setValue(IDEInternalPreferences.WORKSPACE_NAME, workspaceName.getText());
    store.setValue(IDEInternalPreferences.WORKBENCH_SYSTEM_EXPLORER, systemExplorer.getText());
    store.setValue(IDEInternalPreferences.OPEN_REQUIRED_PROJECTS, OPEN_VALUES[Math.max(0, openReferencesCombo.getSelectionIndex())]);
    store.setValue(IDEInternalPreferences.MISSING_NATURE_SEVERITY, severityOf(missingNatureCombo.getSelectionIndex()));
    store.setValue(IDEInternalPreferences.MISSING_ENCODING_SEVERITY, severityOf(missingEncodingCombo.getSelectionIndex()));
    String delimiter = defaultLineDelimiterButton.getSelection() ? ""
        : LINE_DELIMITERS[Math.max(0, lineDelimiterCombo.getSelectionIndex())];
    store.setValue(IDEInternalPreferences.LINE_DELIMITER, delimiter);
    return true;
  }

  /** The severity of an item of a severity combo: the first item is Ignore (-1). */
  private static int severityOf(int index) {
    return index <= 0 ? IDEInternalPreferences.SEVERITY_IGNORE : SEVERITIES[index - 1];
  }
}
