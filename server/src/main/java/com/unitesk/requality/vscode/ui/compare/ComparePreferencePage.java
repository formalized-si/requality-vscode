package com.unitesk.requality.vscode.ui.compare;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.unitesk.requality.vscode.ui.compare.contentmergeviewer.TextMergeViewer;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.DiffNode;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.Differencer;
import com.unitesk.requality.vscode.ui.jface.preference.IPreferenceStore;
import com.unitesk.requality.vscode.ui.jface.preference.PreferencePage;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.CTabFolder;
import com.unitesk.requality.vscode.ui.swt.widgets.CTabItem;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Group;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPreferencePage;

/**
 * {@code org.eclipse.compare.internal.ComparePreferencePage}: the Compare/Patch page of Window &gt; Preferences, with the
 * General tab (structure compare, the unified diff, the patch options, the filtered members) and the Text Compare tab
 * (the options of the text viewers, the navigation at the end of a change, the previews of the 2-way and 3-way
 * compare). The settings are kept in the {@link CompareUIPlugin} store; Restore Defaults and OK apply them.
 */
public class ComparePreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
  private static final String PREFIX = CompareUIPlugin.PLUGIN_ID + ".";
  public static final String OPEN_STRUCTURE_COMPARE = PREFIX + "OpenStructureCompare";
  public static final String USE_OUTLINE_VIEW = PREFIX + "UseOutlineView";
  public static final String SYNCHRONIZE_SCROLLING = PREFIX + "SynchronizeScrolling";
  public static final String SHOW_PSEUDO_CONFLICTS = PREFIX + "ShowPseudoConflicts";
  public static final String INITIALLY_SHOW_ANCESTOR_PANE = PREFIX + "InitiallyShowAncestorPane";
  public static final String PREF_SAVE_ALL_EDITORS = PREFIX + "SaveAllEditors";
  public static final String IGNORE_WHITESPACE = PREFIX + "IgnoreWhitespace";
  public static final String USE_SINGLE_LINE = PREFIX + "UseSingleLine";
  public static final String HIGHLIGHT_TOKEN_CHANGES = PREFIX + "HighlightTokenChanges";
  public static final String CAPPING_DISABLED = PREFIX + "CappingDisable";
  public static final String PATH_FILTER = PREFIX + "PathFilter";
  public static final String ADDED_LINES_REGEX = PREFIX + "AddedLinesRegex";
  public static final String REMOVED_LINES_REGEX = PREFIX + "RemovedLinesRegex";
  public static final String SWAPPED = PREFIX + "Swapped";
  public static final String UNIFIED_DIFF = PREFIX + "UnifiedDiff";
  public static final String UNIFIED_DIFF_FOLD_UNCHANGED = PREFIX + "UnifiedDiffFoldUnchanged";
  /** The navigation at the end of the changes ({@code ICompareUIConstants.PREF_NAVIGATION_END_ACTION}). */
  public static final String NAVIGATION_END_ACTION = PREFIX + "NavigationEndAction";
  public static final String VALUE_PROMPT = "prompt";
  public static final String VALUE_LOOP = "loop";
  public static final String VALUE_NEXT = "next";
  public static final String VALUE_DO_NOTHING = "donothing";

  private static final String[] GENERAL_CHECKS = { OPEN_STRUCTURE_COMPARE, USE_OUTLINE_VIEW, IGNORE_WHITESPACE,
      UNIFIED_DIFF, UNIFIED_DIFF_FOLD_UNCHANGED, PREF_SAVE_ALL_EDITORS };
  private static final String[] TEXT_CHECKS = { SYNCHRONIZE_SCROLLING, INITIALLY_SHOW_ANCESTOR_PANE,
      SHOW_PSEUDO_CONFLICTS, USE_SINGLE_LINE, HIGHLIGHT_TOKEN_CHANGES, CAPPING_DISABLED, SWAPPED };

  private final Map<Button, String> checkBoxes = new LinkedHashMap<>();
  private final List<CompareConfiguration> previews = new ArrayList<>();
  private Text filters;
  private Text addedLinesRegex;
  private Text removedLinesRegex;
  private Button[] navigationButtons;
  private static final String[] NAVIGATION_VALUES = { VALUE_PROMPT, VALUE_LOOP, VALUE_NEXT, VALUE_DO_NOTHING };

  /** The store of the page: the one of the compare plug-in. */
  @Override
  protected IPreferenceStore doGetPreferenceStore() {
    return CompareUIPlugin.getPreferenceStore();
  }

  /** The defaults of the Compare preferences (the ones of {@code ComparePreferencePage.initDefaults}). */
  public static void initDefaults(IPreferenceStore store) {
    store.setDefault(OPEN_STRUCTURE_COMPARE, true);
    store.setDefault(USE_OUTLINE_VIEW, true);
    store.setDefault(SYNCHRONIZE_SCROLLING, true);
    store.setDefault(SHOW_PSEUDO_CONFLICTS, false);
    store.setDefault(INITIALLY_SHOW_ANCESTOR_PANE, false);
    store.setDefault(IGNORE_WHITESPACE, false);
    store.setDefault(PREF_SAVE_ALL_EDITORS, false);
    store.setDefault(ADDED_LINES_REGEX, "");
    store.setDefault(REMOVED_LINES_REGEX, "");
    store.setDefault(USE_SINGLE_LINE, false);
    store.setDefault(HIGHLIGHT_TOKEN_CHANGES, true);
    store.setDefault(CAPPING_DISABLED, false);
    store.setDefault(PATH_FILTER, "");
    store.setDefault(NAVIGATION_END_ACTION, VALUE_PROMPT);
    store.setDefault(SWAPPED, true);
    store.setDefault(UNIFIED_DIFF, false);
    store.setDefault(UNIFIED_DIFF_FOLD_UNCHANGED, true);
  }

  /** Whether the text compares ignore the white space (the Compare preference, unless the configuration says so). */
  public static boolean isIgnoreWhitespace() {
    return CompareUIPlugin.getPreferenceStore().getBoolean(IGNORE_WHITESPACE);
  }

  /** Whether the saved editors are saved before patches are browsed. */
  public static boolean getSaveAllEditors() {
    return CompareUIPlugin.getPreferenceStore().getBoolean(PREF_SAVE_ALL_EDITORS);
  }

  @Override
  public void init(IWorkbench workbench) {
  }

  @Override
  protected Control createContents(Composite parent) {
    CTabFolder folder = new CTabFolder(parent, SWT.NONE);
    folder.setLayoutData(new GridData(GridData.FILL_BOTH));
    CTabItem item = new CTabItem(folder, SWT.NONE);
    item.setText("General");
    item.setControl(createGeneralPage(folder));
    folder.setSelection(0);
    item = new CTabItem(folder, SWT.NONE);
    item.setText("Text Compare");
    item.setControl(createTextComparePage(folder));
    return folder;
  }

  private Control createGeneralPage(Composite parent) {
    Composite composite = new Composite(parent, SWT.NULL);
    composite.setLayout(new GridLayout(1, false));
    addCheckBox(composite, "&Open structure compare automatically", OPEN_STRUCTURE_COMPARE);
    addCheckBox(composite, "Show structure compare in Outline &view when possible", USE_OUTLINE_VIEW);
    addCheckBox(composite, "Ignore &white space", IGNORE_WHITESPACE);
    Group unified = new Group(composite, SWT.NONE);
    unified.setText("Unified Diff");
    unified.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    unified.setLayout(new GridLayout(1, false));
    addCheckBox(unified, "EXPERIMENTAL: Use Unified Diff instead of 2-way compare when possible", UNIFIED_DIFF);
    addCheckBox(unified, "&Fold unchanged regions, keeping a few lines of context around each change",
        UNIFIED_DIFF_FOLD_UNCHANGED);
    new Label(composite, SWT.NONE);
    addCheckBox(composite, "A&utomatically save editors with unsaved changes before browsing patches",
        PREF_SAVE_ALL_EDITORS);
    new Label(composite, SWT.NONE);
    Label l = new Label(composite, SWT.WRAP);
    l.setText("Enter regular expressions used to identify added or removed lines in a patch\n"
        + "(e.g. '^\\+\\s*\\S' for an added line with at least one word character).");
    Composite regex = new Composite(composite, SWT.NONE);
    regex.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    regex.setLayout(twoColumns(false));
    new Label(regex, SWT.NONE).setText("Added lines");
    addedLinesRegex = new Text(regex, SWT.BORDER);
    addedLinesRegex.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    addedLinesRegex.setText(getPreferenceStore().getString(ADDED_LINES_REGEX));
    new Label(regex, SWT.NONE).setText("Removed lines");
    removedLinesRegex = new Text(regex, SWT.BORDER);
    removedLinesRegex.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    removedLinesRegex.setText(getPreferenceStore().getString(REMOVED_LINES_REGEX));
    new Label(composite, SWT.NONE);
    l = new Label(composite, SWT.WRAP);
    l.setText(" Enter member names that should be excluded from 'Compare With Each Other'.\n"
        + "List is comma separated (e.g. '*.class, .project, bin/')");
    Composite filter = new Composite(composite, SWT.NONE);
    filter.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    filter.setLayout(twoColumns(false));
    new Label(filter, SWT.NONE).setText(" &Filtered Members:");
    filters = new Text(filter, SWT.BORDER);
    filters.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    filters.setText(getPreferenceStore().getString(PATH_FILTER));
    return composite;
  }

  private Control createTextComparePage(Composite parent) {
    Composite composite = new Composite(parent, SWT.NULL);
    composite.setLayout(new GridLayout(1, false));
    addCheckBox(composite, "Synchronize &scrolling between panes in compare viewers", SYNCHRONIZE_SCROLLING);
    addCheckBox(composite, "&Initially show ancestor pane", INITIALLY_SHOW_ANCESTOR_PANE);
    addCheckBox(composite, "Show &pseudo conflicts", SHOW_PSEUDO_CONFLICTS);
    addCheckBox(composite, "Connect &ranges with single line", USE_SINGLE_LINE);
    addCheckBox(composite, "&Highlight individual changes", HIGHLIGHT_TOKEN_CHANGES);
    addCheckBox(composite, "Disable &capping when comparing large documents", CAPPING_DISABLED);
    addCheckBox(composite, "S&wap left and right", SWAPPED);
    Group group = new Group(composite, SWT.NONE);
    group.setText("When the end/beginning is reached while navigating an element");
    group.setLayout(new GridLayout(1, false));
    group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    String[] labels = { "Pro&mpt", "G&o to the beginning/end of the element", "Disp&lay the next/previous element",
        "Do &nothing" };
    navigationButtons = new Button[labels.length];
    String current = getPreferenceStore().getString(NAVIGATION_END_ACTION);
    for (int i = 0; i < labels.length; i++) {
      navigationButtons[i] = new Button(group, SWT.RADIO | SWT.LEFT);
      navigationButtons[i].setText(labels[i]);
      navigationButtons[i].setSelection(NAVIGATION_VALUES[i].equals(current));
    }
    new Label(composite, SWT.NONE).setText("Pre&view 2-way compare:");
    Control two = create2WayPreviewer(composite);
    two.setLayoutData(previewData(12));
    new Label(composite, SWT.NONE).setText("Pre&view 3-way compare:");
    Control three = create3WayPreviewer(composite);
    three.setLayoutData(previewData(9));
    return composite;
  }

  private static GridData previewData(int heightChars) {
    GridData gd = new GridData(GridData.FILL_BOTH);
    gd.widthHint = 480;
    gd.heightHint = heightChars * 16;
    return gd;
  }

  private static GridLayout twoColumns(boolean margins) {
    GridLayout layout = new GridLayout(2, false);
    layout.marginWidth = margins ? 5 : 0;
    return layout;
  }

  private Button addCheckBox(Composite parent, String label, String key) {
    Button checkBox = new Button(parent, SWT.CHECK);
    checkBox.setText(label);
    checkBox.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    checkBox.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        // the values are applied when the page is confirmed (performOk)
      }
    });
    checkBoxes.put(checkBox, key);
    return checkBox;
  }

  private Control create2WayPreviewer(Composite parent) {
    CompareConfiguration cc = new CompareConfiguration();
    cc.setLeftLabel("Working Copy");
    cc.setRightLabel("Baseline");
    cc.setRightEditable(false);
    TextMergeViewer viewer = new TextMergeViewer(parent, SWT.BORDER, cc);
    viewer.setInput(new DiffNode(new FakeInput(WORKING_COPY), new FakeInput(BASELINE)));
    previews.add(cc);
    return viewer.getControl();
  }

  private Control create3WayPreviewer(Composite parent) {
    CompareConfiguration cc = new CompareConfiguration();
    cc.setAncestorLabel("Common Ancestor");
    cc.setLeftLabel("Local");
    cc.setLeftEditable(false);
    cc.setRightLabel("Remote");
    cc.setRightEditable(false);
    TextMergeViewer viewer = new TextMergeViewer(parent, SWT.BORDER, cc);
    viewer.setInput(new DiffNode(Differencer.CONFLICTING, new FakeInput(PREVIEW_ANCESTOR),
        new FakeInput(PREVIEW_LEFT), new FakeInput(PREVIEW_RIGHT)));
    previews.add(cc);
    return viewer.getControl();
  }

  static final String WORKING_COPY = "line 1\nline 2\nline 3\nadded words in line 4\nline 5\nadded line 5a\nadded line 6b\n"
      + "line 6\nmodified line 7\nline 8\nline 9";
  static final String BASELINE = "\nline 1\nline 2\nremoved line 2a\nremoved line 2b\nline 3\nline 4\nline 5\nline 6\n"
      + "line to modify 7\nline 8\nremoved words in line 9\n";
  static final String PREVIEW_ANCESTOR = "1\n2\n3\nconflicting change\n4\n5";
  static final String PREVIEW_LEFT = "1\n2\noutgoing addition\n3\noutgoing change\n4\npseudo conflict\n5";
  static final String PREVIEW_RIGHT = "1\nincoming addition\nincoming addition\n2\n3\nincoming change\n4\npseudo conflict\n5";

  /** A text of the preview: its contents are the text (the preview is not editable). */
  private static final class FakeInput implements com.unitesk.requality.vscode.ui.compare.ITypedElement,
      com.unitesk.requality.vscode.ui.compare.IEncodedStreamContentAccessor {
    private final String content;

    FakeInput(String content) {
      this.content = content;
    }

    @Override
    public com.unitesk.requality.vscode.ui.swt.graphics.Image getImage() {
      return null;
    }

    @Override
    public String getName() {
      return "no name";
    }

    @Override
    public String getType() {
      return "no type";
    }

    @Override
    public InputStream getContents() {
      return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_16));
    }

    @Override
    public String getCharset() {
      return StandardCharsets.UTF_16.name();
    }
  }

  @Override
  protected void performDefaults() {
    IPreferenceStore store = getPreferenceStore();
    for (Map.Entry<Button, String> e : checkBoxes.entrySet())
      e.getKey().setSelection(store.getDefaultBoolean(e.getValue()));
    filters.setText(store.getDefaultString(PATH_FILTER));
    addedLinesRegex.setText(store.getDefaultString(ADDED_LINES_REGEX));
    removedLinesRegex.setText(store.getDefaultString(REMOVED_LINES_REGEX));
    String def = store.getDefaultString(NAVIGATION_END_ACTION);
    for (int i = 0; i < navigationButtons.length; i++)
      navigationButtons[i].setSelection(NAVIGATION_VALUES[i].equals(def));
    super.performDefaults();
  }

  @Override
  public boolean performOk() {
    IPreferenceStore store = getPreferenceStore();
    for (Map.Entry<Button, String> e : checkBoxes.entrySet())
      store.setValue(e.getValue(), e.getKey().getSelection());
    store.setValue(PATH_FILTER, filters.getText());
    store.setValue(ADDED_LINES_REGEX, addedLinesRegex.getText());
    store.setValue(REMOVED_LINES_REGEX, removedLinesRegex.getText());
    for (int i = 0; i < navigationButtons.length; i++)
      if (navigationButtons[i].getSelection())
        store.setValue(NAVIGATION_END_ACTION, NAVIGATION_VALUES[i]);
    return true;
  }

  @Override
  public void dispose() {
    for (CompareConfiguration cc : previews)
      cc.dispose();
    previews.clear();
    super.dispose();
  }
}
