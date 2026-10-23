package com.unitesk.requality.vscode.ui.team.preferences;

import com.unitesk.requality.vscode.ui.jface.preference.IPreferenceStore;
import com.unitesk.requality.vscode.ui.jface.preference.PreferencePage;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Combo;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Group;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPreferencePage;

/**
 * {@code org.eclipse.team.internal.ui.preferences.SyncViewerPreferencePage}: the Team page of Window &gt; Preferences
 * ("General Version Control Preferences"): the synchronize view presentation and the options of the compare editors,
 * the import and the patches. The perspective list is the one of the port (none, or the Team Synchronizing perspective).
 */
public class SyncViewerPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
  private static final String[] PERSPECTIVE_IDS = { IPreferenceIds.SYNCVIEW_DEFAULT_PERSPECTIVE_NONE,
      TeamUIPlugin.TEAM_SYNCHRONIZING_PERSPECTIVE };
  private static final String[] PERSPECTIVE_LABELS = { "None", "Team Synchronizing" };
  private static final String[] LAYOUT_IDS = { IPreferenceIds.FLAT_LAYOUT, IPreferenceIds.TREE_LAYOUT,
      IPreferenceIds.COMPRESSED_LAYOUT };
  /** The values of {@code MessageDialogWithToggle}: always, never and prompt. */
  private static final String[] SWITCH_IDS = { "always", "never", TeamUIPlugin.PROMPT };

  private final java.util.Map<Button, String> checkBoxes = new java.util.LinkedHashMap<>();
  private Button[] layoutButtons;
  private Button[] switchButtons;
  private Combo perspective;

  public SyncViewerPreferencePage() {
    setTitle("Team");
    setDescription("General Version Control Preferences:");
  }

  @Override
  protected IPreferenceStore doGetPreferenceStore() {
    return TeamUIPlugin.getPreferenceStore();
  }

  @Override
  public void init(IWorkbench workbench) {
  }

  @Override
  protected Control createContents(Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);
    composite.setLayout(new GridLayout(1, false));
    composite.setLayoutData(new GridData(GridData.FILL_BOTH));
    IPreferenceStore store = getPreferenceStore();
    check(composite, "&Show all synchronization information in a resource's text label",
        IPreferenceIds.SYNCVIEW_VIEW_SYNCINFO_IN_LABEL);
    check(composite, "Show the &file author in compare editors", IPreferenceIds.SHOW_AUTHOR_IN_COMPARE_EDITOR);
    check(composite, "When &editing file in non-shared projects, automatically make files writable if prompting is "
        + "not possible", IPreferenceIds.MAKE_FILE_WRITTABLE_IF_CONTEXT_MISSING);
    check(composite, "Re&use open compare editors when opening comparisons", IPreferenceIds.REUSE_OPEN_COMPARE_EDITOR);
    check(composite, "Run 'Project Set' import in the &background", IPreferenceIds.RUN_IMPORT_IN_BACKGROUND);
    check(composite, "&Apply patch in 'Synchronize' view", IPreferenceIds.APPLY_PATCH_IN_SYNCHRONIZE_VIEW);
    Group layoutGroup = group(composite, "Choose the presentation to be used when displaying workspace projects");
    String layout = store.getString(IPreferenceIds.SYNCVIEW_DEFAULT_LAYOUT);
    layoutButtons = radios(layoutGroup, new String[] { "&Flat", "T&ree", "&Compressed Folders" }, LAYOUT_IDS, layout);
    Group switchGroup = group(composite,
        "Open the associated perspective when a synchronize operation completes");
    String sw = store.getString(IPreferenceIds.SYNCHRONIZING_COMPLETE_PERSPECTIVE);
    switchButtons = radios(switchGroup, new String[] { "Al&ways", "&Never", "Pro&mpt" }, SWITCH_IDS, sw);
    Group perspectives = group(composite, "Perspectives");
    Label description = new Label(perspectives, SWT.WRAP);
    description.setText("This setting controls which perspective to open when resources are synchronized.\n"
        + "To indicate that a perspective should not be opened select 'None'.");
    GridData dd = new GridData();
    dd.horizontalSpan = 2;
    description.setLayoutData(dd);
    Label label = new Label(perspectives, SWT.NONE);
    label.setText("&Perspective:");
    perspective = new Combo(perspectives, SWT.READ_ONLY);
    perspective.setItems(PERSPECTIVE_LABELS);
    String id = store.getString(IPreferenceIds.SYNCVIEW_DEFAULT_PERSPECTIVE);
    int index = java.util.Arrays.asList(PERSPECTIVE_IDS).indexOf(id);
    perspective.select(Math.max(0, index));
    perspective.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    return composite;
  }

  private void check(Composite parent, String label, String key) {
    Button b = new Button(parent, SWT.CHECK);
    b.setText(label);
    b.setSelection(getPreferenceStore().getBoolean(key));
    b.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    checkBoxes.put(b, key);
  }

  private static Group group(Composite parent, String title) {
    Group g = new Group(parent, SWT.NONE);
    g.setText(title);
    g.setLayout(new GridLayout(2, false));
    g.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    return g;
  }

  private static Button[] radios(Composite parent, String[] labels, String[] values, String current) {
    Button[] r = new Button[labels.length];
    for (int i = 0; i < labels.length; i++) {
      r[i] = new Button(parent, SWT.RADIO | SWT.LEFT);
      r[i].setText(labels[i]);
      r[i].setSelection(values[i].equals(current));
    }
    return r;
  }

  @Override
  protected void performDefaults() {
    IPreferenceStore store = getPreferenceStore();
    for (java.util.Map.Entry<Button, String> e : checkBoxes.entrySet())
      e.getKey().setSelection(store.getDefaultBoolean(e.getValue()));
    select(layoutButtons, LAYOUT_IDS, store.getDefaultString(IPreferenceIds.SYNCVIEW_DEFAULT_LAYOUT));
    select(switchButtons, SWITCH_IDS, store.getDefaultString(IPreferenceIds.SYNCHRONIZING_COMPLETE_PERSPECTIVE));
    perspective.select(Math.max(0, java.util.Arrays.asList(PERSPECTIVE_IDS)
        .indexOf(store.getDefaultString(IPreferenceIds.SYNCVIEW_DEFAULT_PERSPECTIVE))));
    super.performDefaults();
  }

  private static void select(Button[] buttons, String[] values, String current) {
    for (int i = 0; i < buttons.length; i++)
      buttons[i].setSelection(values[i].equals(current));
  }

  @Override
  public boolean performOk() {
    IPreferenceStore store = getPreferenceStore();
    for (java.util.Map.Entry<Button, String> e : checkBoxes.entrySet())
      store.setValue(e.getValue(), e.getKey().getSelection());
    store.setValue(IPreferenceIds.SYNCVIEW_DEFAULT_LAYOUT, selected(layoutButtons, LAYOUT_IDS));
    store.setValue(IPreferenceIds.SYNCHRONIZING_COMPLETE_PERSPECTIVE, selected(switchButtons, SWITCH_IDS));
    int i = perspective.getSelectionIndex();
    store.setValue(IPreferenceIds.SYNCVIEW_DEFAULT_PERSPECTIVE,
        i < 0 ? IPreferenceIds.SYNCVIEW_DEFAULT_PERSPECTIVE_NONE : PERSPECTIVE_IDS[i]);
    return true;
  }

  private static String selected(Button[] buttons, String[] values) {
    for (int i = 0; i < buttons.length; i++)
      if (buttons[i].getSelection())
        return values[i];
    return values[0];
  }
}
