package com.unitesk.requality.vscode.ui.ide.dialogs;

import java.util.Arrays;
import java.util.TreeSet;

import com.unitesk.requality.vscode.ui.jface.dialogs.ListSelectionDialog;
import com.unitesk.requality.vscode.ui.jface.preference.IPreferenceStore;
import com.unitesk.requality.vscode.ui.jface.preference.PreferencePage;
import com.unitesk.requality.vscode.ui.jface.viewers.ArrayContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.ILabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider;
import com.unitesk.requality.vscode.ui.jface.window.Window;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IWorkspaceDescription;
import com.unitesk.requality.vscode.ui.resources.IncrementalProjectBuilder;
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
import com.unitesk.requality.vscode.ui.swt.widgets.List;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPreferencePage;
import com.unitesk.requality.vscode.ui.workbench.actions.BuildAction;

/**
 * {@code org.eclipse.ui.internal.ide.dialogs.BuildOrderPreferencePage}: the Build page of General &gt; Workspace: build
 * automatically, save the modified resources before a manual build, the project build order (the default one, or the
 * list of the projects with Up, Down, Add Project... and Remove Project), the maximum number of iterations of the builds
 * of projects with cycles and the maximum number of simultaneous project builds. The settings go to the workspace
 * description ({@link IWorkspaceDescription}); the save before a build to the {@link IDEInternalPreferences} store.
 */
public class BuildOrderPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
  private static final String UP_LABEL = "&Up";
  private static final String DOWN_LABEL = "Dow&n";
  private static final String ADD_LABEL = "Add &Project...";
  private static final String REMOVE_LABEL = "&Remove Project";
  private static final String PROJECT_SELECTION_MESSAGE = "Select &projects to add to build path:";
  private static final String DEFAULTS_LABEL = "Use d&efault build order";
  private static final String LIST_LABEL = "Project build &order:";
  private static final String REMOVE_MESSAGE = "A project removed from the list is still built but after those specified in the list.";
  private static final int MAX_BUILD_ITERATIONS_DEFAULT = 10;

  private IWorkbench workbench;
  private Button defaultOrderButton;
  private List buildList;
  private Composite buttonComposite;
  private Text maxIterations;
  private Text maxSimultaneousBuilds;
  private Button autoSaveAllButton;
  private Button autoBuildButton;
  private boolean defaultOrderInitiallySelected;
  private String[] customBuildOrder;

  @Override
  public void init(IWorkbench currentWorkbench) {
    this.workbench = currentWorkbench;
  }

  private static IWorkspaceDescription description() {
    return ResourcesPlugin.getWorkspace().getDescription();
  }

  private static IPreferenceStore store() {
    return IDEInternalPreferences.getStore();
  }

  @Override
  protected Control createContents(Composite parent) {
    Composite composite = new Composite(parent, SWT.NULL);
    GridLayout layout = new GridLayout();
    layout.numColumns = 2;
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    composite.setLayout(layout);
    GridData data = new GridData();
    data.verticalAlignment = GridData.FILL;
    data.horizontalAlignment = GridData.FILL;
    composite.setLayoutData(data);
    createSaveAllBeforeBuildPref(composite);
    createAutoBuildPref(composite);
    createSpacer(composite);
    String[] buildOrder = getCurrentBuildOrder();
    boolean useDefault = buildOrder == null;
    createDefaultPathButton(composite, useDefault);
    createBuildOrderList(composite);
    createListButtons(composite, !useDefault);
    Label note = new Label(composite, SWT.WRAP);
    note.setText("Note: " + REMOVE_MESSAGE);
    GridData noteData = new GridData();
    noteData.horizontalSpan = 2;
    note.setLayoutData(noteData);
    createSpacer(composite);
    Composite intFieldsComposite = new Composite(composite, SWT.NONE);
    intFieldsComposite.setLayout(new GridLayout(2, false));
    intFieldsComposite.setLayoutData(new GridData(SWT.FILL, SWT.DEFAULT, true, false, 2, 1));
    createMaxIterationsField(intFieldsComposite);
    createMaxSimultaneousBuildsGroup(intFieldsComposite);
    buildList.setItems(useDefault ? getDefaultProjectOrder() : buildOrder);
    return composite;
  }

  private void createSaveAllBeforeBuildPref(Composite composite) {
    autoSaveAllButton = new Button(composite, SWT.CHECK);
    autoSaveAllButton.setLayoutData(new GridData(SWT.FILL, SWT.DEFAULT, true, false, 2, 1));
    autoSaveAllButton.setText("Save auto&matically before manual build");
    autoSaveAllButton.setToolTipText("Save modified resources automatically before manual build");
    autoSaveAllButton.setSelection(store().getBoolean(IDEInternalPreferences.SAVE_ALL_BEFORE_BUILD));
  }

  private void createAutoBuildPref(Composite composite) {
    autoBuildButton = new Button(composite, SWT.CHECK);
    autoBuildButton.setLayoutData(new GridData(SWT.FILL, SWT.DEFAULT, true, false, 2, 1));
    autoBuildButton.setText("&Build automatically");
    autoBuildButton.setToolTipText("Build automatically on resource modification");
    autoBuildButton.setSelection(ResourcesPlugin.getWorkspace().isAutoBuilding());
  }

  private void createSpacer(Composite composite) {
    Label spacer = new Label(composite, SWT.NONE);
    GridData spacerData = new GridData();
    spacerData.horizontalSpan = 2;
    spacer.setLayoutData(spacerData);
  }

  private void createDefaultPathButton(Composite composite, boolean selected) {
    defaultOrderInitiallySelected = selected;
    defaultOrderButton = new Button(composite, SWT.LEFT | SWT.CHECK);
    defaultOrderButton.setSelection(selected);
    defaultOrderButton.setText(DEFAULTS_LABEL);
    defaultOrderButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        defaultsButtonSelected(defaultOrderButton.getSelection());
      }
    });
    GridData gridData = new GridData();
    gridData.horizontalAlignment = GridData.FILL;
    gridData.horizontalSpan = 2;
    defaultOrderButton.setLayoutData(gridData);
  }

  private void createBuildOrderList(Composite composite) {
    Label buildLabel = new Label(composite, SWT.NONE);
    buildLabel.setText(LIST_LABEL);
    GridData gridData = new GridData();
    gridData.horizontalAlignment = GridData.FILL;
    gridData.horizontalSpan = 2;
    buildLabel.setLayoutData(gridData);
    buildList = new List(composite, SWT.BORDER | SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
    GridData data = new GridData();
    data.heightHint = buildList.getItemHeight() * 8;
    data.verticalAlignment = GridData.FILL;
    data.horizontalAlignment = GridData.FILL;
    data.grabExcessHorizontalSpace = true;
    data.grabExcessVerticalSpace = true;
    buildList.setLayoutData(data);
  }

  private void createListButtons(Composite composite, boolean enableComposite) {
    buttonComposite = new Composite(composite, SWT.RIGHT);
    GridLayout layout = new GridLayout();
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    buttonComposite.setLayout(layout);
    GridData gridData = new GridData();
    gridData.verticalAlignment = GridData.FILL;
    gridData.horizontalAlignment = GridData.FILL;
    buttonComposite.setLayoutData(gridData);
    Button upButton = pushButton(buttonComposite, UP_LABEL, enableComposite, new Runnable() {
      @Override
      public void run() {
        moveSelectionUp();
      }
    });
    upButton.setEnabled(enableComposite);
    pushButton(buttonComposite, DOWN_LABEL, enableComposite, new Runnable() {
      @Override
      public void run() {
        moveSelectionDown();
      }
    });
    pushButton(buttonComposite, ADD_LABEL, enableComposite, new Runnable() {
      @Override
      public void run() {
        addProject();
      }
    });
    pushButton(buttonComposite, REMOVE_LABEL, enableComposite, new Runnable() {
      @Override
      public void run() {
        buildList.remove(buildList.getSelectionIndices());
      }
    });
  }

  private static Button pushButton(Composite parent, String text, boolean enabled, Runnable action) {
    Button button = new Button(parent, SWT.CENTER | SWT.PUSH);
    button.setText(text);
    button.setEnabled(enabled);
    button.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        action.run();
      }
    });
    return button;
  }

  private void createMaxIterationsField(Composite composite) {
    Label label = new Label(composite, SWT.NONE);
    label.setText("Max &iterations when building with cycles:");
    maxIterations = new Text(composite, SWT.BORDER);
    maxIterations.setText(Integer.toString(description().getMaxBuildIterations()));
    maxIterations.addModifyListener(new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent e) {
        validate(maxIterations, 1, Integer.MAX_VALUE, "Values must be positive");
      }
    });
  }

  private void createMaxSimultaneousBuildsGroup(Composite composite) {
    Label label = new Label(composite, SWT.NONE);
    label.setText("Max &simultaneous project builds:");
    maxSimultaneousBuilds = new Text(composite, SWT.BORDER);
    maxSimultaneousBuilds.setTextLimit(Integer.toString(IDEInternalPreferences.MAX_MAX_SIMULTANEOUS_BUILD).length());
    maxSimultaneousBuilds.setText(Integer.toString(description().getMaxConcurrentBuilds()));
    maxSimultaneousBuilds.addModifyListener(new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent e) {
        validate(maxSimultaneousBuilds, 1, IDEInternalPreferences.MAX_MAX_SIMULTANEOUS_BUILD,
            "Maximum simultaneous project builds should be between 1 and "
                + IDEInternalPreferences.MAX_MAX_SIMULTANEOUS_BUILD);
      }
    });
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

  private void defaultsButtonSelected(boolean selected) {
    if (selected) {
      setBuildOrderWidgetsEnablement(false);
      customBuildOrder = buildList.getItems();
      buildList.setItems(getDefaultProjectOrder());
    } else {
      setBuildOrderWidgetsEnablement(true);
      String[] buildOrder = getCurrentBuildOrder();
      buildList.setItems(buildOrder == null ? getDefaultProjectOrder() : buildOrder);
    }
  }

  private String[] getCurrentBuildOrder() {
    if (customBuildOrder == null)
      customBuildOrder = description().getBuildOrder();
    return customBuildOrder;
  }

  /**
   * The default order of the builds: the names of the projects in order. (The port has no reference graph of the
   * projects, so the order is the one of the names.)
   */
  private String[] getDefaultProjectOrder() {
    TreeSet<String> names = new TreeSet<>();
    for (IProject p : ResourcesPlugin.getWorkspace().getRoot().getProjects())
      names.add(p.getName());
    return names.toArray(new String[0]);
  }

  private void addProject() {
    String[] currentItems = buildList.getItems();
    IProject[] allProjects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
    ILabelProvider labelProvider = new LabelProvider() {
      @Override
      public String getText(Object element) {
        return (String) element;
      }
    };
    ListSelectionDialog dialog = new ListSelectionDialog(getShell(), sortedDifference(allProjects, currentItems),
        ArrayContentProvider.getInstance(), labelProvider, PROJECT_SELECTION_MESSAGE);
    if (dialog.open() != Window.OK)
      return;
    Object[] result = dialog.getResult();
    String[] newItems = Arrays.copyOf(currentItems, currentItems.length + result.length);
    for (int i = 0; i < result.length; i++)
      newItems[currentItems.length + i] = (String) result[i];
    buildList.setItems(newItems);
  }

  private static String[] sortedDifference(IProject[] allProjects, String[] currentlyDisplayed) {
    TreeSet<String> difference = new TreeSet<>();
    for (IProject p : allProjects)
      if (!Arrays.asList(currentlyDisplayed).contains(p.getName()))
        difference.add(p.getName());
    return difference.toArray(new String[0]);
  }

  private void moveSelectionDown() {
    if (buildList.getSelectionCount() == 1) {
      int currentIndex = buildList.getSelectionIndex();
      if (currentIndex < buildList.getItemCount() - 1) {
        String elementToMove = buildList.getItem(currentIndex);
        buildList.remove(currentIndex);
        buildList.add(elementToMove, currentIndex + 1);
        buildList.setSelection(currentIndex + 1);
      }
    }
  }

  private void moveSelectionUp() {
    int currentIndex = buildList.getSelectionIndex();
    if (currentIndex > 0 && buildList.getSelectionCount() == 1) {
      String elementToMove = buildList.getItem(currentIndex);
      buildList.remove(currentIndex);
      buildList.add(elementToMove, currentIndex - 1);
      buildList.setSelection(currentIndex - 1);
    }
  }

  private void setBuildOrderWidgetsEnablement(boolean value) {
    for (Control child : buttonComposite.getChildren())
      child.setEnabled(value);
  }

  @Override
  protected void performDefaults() {
    defaultOrderButton.setSelection(true);
    defaultsButtonSelected(true);
    maxIterations.setText(Integer.toString(MAX_BUILD_ITERATIONS_DEFAULT));
    autoBuildButton.setSelection(true);
    maxSimultaneousBuilds.setText(Integer.toString(store().getDefaultInt(IDEInternalPreferences.MAX_SIMULTANEOUS_BUILD)));
    autoSaveAllButton.setSelection(store().getDefaultBoolean(IDEInternalPreferences.SAVE_ALL_BEFORE_BUILD));
    super.performDefaults();
  }

  @Override
  public boolean performOk() {
    int iterations;
    int simultaneous;
    try {
      iterations = Integer.parseInt(maxIterations.getText().trim());
      simultaneous = Integer.parseInt(maxSimultaneousBuilds.getText().trim());
    } catch (NumberFormatException e) {
      return false;
    }
    if (iterations < 1 || simultaneous < 1 || simultaneous > IDEInternalPreferences.MAX_MAX_SIMULTANEOUS_BUILD)
      return false;
    boolean useDefault = defaultOrderButton.getSelection();
    String[] buildOrder = useDefault ? null : buildList.getItems();
    IWorkspaceDescription description = description();
    if (autoBuildButton.getSelection() != ResourcesPlugin.getWorkspace().isAutoBuilding())
      description.setAutoBuilding(autoBuildButton.getSelection());
    if (simultaneous != description.getMaxConcurrentBuilds())
      description.setMaxConcurrentBuilds(simultaneous);
    store().setValue(IDEInternalPreferences.SAVE_ALL_BEFORE_BUILD, autoSaveAllButton.getSelection());
    description.setBuildOrder(buildOrder);
    description.setMaxBuildIterations(iterations);
    if (!useDefault || useDefault != defaultOrderInitiallySelected) {
      defaultOrderInitiallySelected = useDefault;
      if (ResourcesPlugin.getWorkspace().isAutoBuilding())
        BuildAction.schedule(null, IncrementalProjectBuilder.INCREMENTAL_BUILD);
    }
    customBuildOrder = null;
    return true;
  }
}
