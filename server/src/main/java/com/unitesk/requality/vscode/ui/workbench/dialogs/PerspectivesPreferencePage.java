package com.unitesk.requality.vscode.ui.workbench.dialogs;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.unitesk.requality.vscode.ui.jface.preference.IPreferenceStore;
import com.unitesk.requality.vscode.ui.jface.preference.PreferencePage;
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
import com.unitesk.requality.vscode.ui.swt.widgets.Table;
import com.unitesk.requality.vscode.ui.swt.widgets.TableItem;
import com.unitesk.requality.vscode.ui.workbench.IPerspectiveRegistry.IPerspectiveDescriptor;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPreferencePage;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchWindow;
import com.unitesk.requality.vscode.ui.workbench.internal.PerspectiveRegistry;
import com.unitesk.requality.vscode.ui.workbench.internal.PerspectiveRegistry.PerspectiveDescriptor;

/**
 * {@code org.eclipse.ui.internal.dialogs.PerspectivesPreferencePage} (Window &gt; Preferences &gt; General &gt;
 * Perspectives): where a perspective opens, the available perspectives with the default one marked "(default)", and
 * Make Default, Revert (the custom definition of a predefined perspective) and Delete (a saved perspective). Revert and
 * Delete take effect when the page is confirmed, as in Eclipse. VSCODE: the perspectives open in the one window, so
 * the open mode is kept but does not change where a perspective opens.
 */
public class PerspectivesPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
  private IWorkbench workbench;
  private PerspectiveRegistry perspectiveRegistry;
  private List<IPerspectiveDescriptor> perspectives;
  private String defaultPerspectiveId;
  private final List<IPerspectiveDescriptor> perspToDelete = new ArrayList<>();
  private final List<IPerspectiveDescriptor> perspToRevert = new ArrayList<>();
  private Table perspectivesTable;
  private Button revertButton;
  private Button deleteButton;
  private Button setDefaultButton;
  private Button openSameWindowButton;
  private Button openNewWindowButton;
  private int openPerspMode;

  private final Comparator<IPerspectiveDescriptor> comparator = new Comparator<IPerspectiveDescriptor>() {
    private final Collator collator = Collator.getInstance();

    @Override
    public int compare(IPerspectiveDescriptor d1, IPerspectiveDescriptor d2) {
      return collator.compare(d1.getLabel(), d2.getLabel());
    }
  };

  @Override
  protected Control createContents(Composite parent) {
    Composite composite = createComposite(parent);
    createOpenPerspButtonGroup(composite);
    createCustomizePerspective(composite);
    return composite;
  }

  protected Composite createComposite(Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);
    composite.setLayoutData(new GridData(GridData.FILL_BOTH));
    composite.setFont(parent.getFont());
    GridLayout layout = new GridLayout();
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    layout.verticalSpacing = 10;
    composite.setLayout(layout);
    return composite;
  }

  protected void createOpenPerspButtonGroup(Composite composite) {
    Group buttonComposite = new Group(composite, SWT.LEFT);
    buttonComposite.setText("Open a new perspective");
    buttonComposite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    buttonComposite.setFont(composite.getFont());
    GridLayout layout = new GridLayout();
    layout.numColumns = 2;
    buttonComposite.setLayout(layout);

    openSameWindowButton = new Button(buttonComposite, SWT.RADIO);
    openSameWindowButton.setText("In the &same window");
    openSameWindowButton.setSelection(WorkbenchPreferences.OPM_ACTIVE_PAGE == openPerspMode);
    openSameWindowButton.setFont(composite.getFont());
    openSameWindowButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        openPerspMode = WorkbenchPreferences.OPM_ACTIVE_PAGE;
      }
    });

    openNewWindowButton = new Button(buttonComposite, SWT.RADIO);
    openNewWindowButton.setText("In a &new window");
    openNewWindowButton.setSelection(WorkbenchPreferences.OPM_NEW_WINDOW == openPerspMode);
    openNewWindowButton.setFont(composite.getFont());
    openNewWindowButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        openPerspMode = WorkbenchPreferences.OPM_NEW_WINDOW;
      }
    });
  }

  protected Composite createCustomizePerspective(Composite parent) {
    Composite perspectivesComponent = new Composite(parent, SWT.NONE);
    perspectivesComponent.setLayoutData(new GridData(GridData.FILL_BOTH));
    perspectivesComponent.setFont(parent.getFont());
    GridLayout layout = new GridLayout();
    layout.numColumns = 2;
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    perspectivesComponent.setLayout(layout);

    Label label = new Label(perspectivesComponent, SWT.LEFT);
    label.setText("Available &perspectives:");
    GridData data = new GridData();
    data.horizontalSpan = 2;
    label.setLayoutData(data);
    label.setFont(parent.getFont());

    perspectivesTable = new Table(perspectivesComponent, SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
    perspectivesTable.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        updateButtons();
      }
    });
    perspectivesTable.setFont(parent.getFont());
    data = new GridData(GridData.FILL_BOTH);
    data.grabExcessHorizontalSpace = true;
    data.grabExcessVerticalSpace = true;
    perspectivesTable.setLayoutData(data);

    IPerspectiveDescriptor[] persps = perspectiveRegistry.getPerspectives();
    perspectives = new ArrayList<>(persps.length);
    for (IPerspectiveDescriptor p : persps)
      perspectives.add(p);
    perspectives.sort(comparator);
    defaultPerspectiveId = perspectiveRegistry.getDefaultPerspective();
    updatePerspectivesTable();

    Composite buttonBar = (Composite) createVerticalButtonBar(perspectivesComponent);
    data = new GridData(GridData.FILL_VERTICAL);
    buttonBar.setLayoutData(data);

    Composite noteComposite = new Composite(parent, SWT.NONE);
    GridLayout noteLayout = new GridLayout();
    noteLayout.marginWidth = 0;
    noteLayout.marginHeight = 0;
    noteComposite.setLayout(noteLayout);
    Label note = new Label(noteComposite, SWT.WRAP);
    note.setText("Note:");
    Label revertNote = new Label(noteComposite, SWT.WRAP);
    revertNote.setText("'Revert' removes the customization from the selected perspective.\n"
        + "This only applies to newly opened perspectives.");
    GridData noteData = new GridData();
    noteData.horizontalSpan = 2;
    noteComposite.setLayoutData(noteData);
    return perspectivesComponent;
  }

  protected Button createVerticalButton(Composite parent, String label, String tooltip) {
    Button button = new Button(parent, SWT.PUSH);
    button.setText(label);
    GridData data = new GridData(GridData.FILL_HORIZONTAL);
    button.setLayoutData(data);
    button.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent event) {
        verticalButtonPressed(event.widget);
      }
    });
    button.setToolTipText(tooltip);
    button.setFont(parent.getFont());
    return button;
  }

  protected Control createVerticalButtonBar(Composite parent) {
    Composite composite = new Composite(parent, SWT.NULL);
    GridLayout layout = new GridLayout();
    layout.numColumns = 1;
    layout.marginWidth = 5;
    layout.marginHeight = 0;
    composite.setLayout(layout);
    composite.setFont(parent.getFont());

    setDefaultButton = createVerticalButton(composite, "Ma&ke Default", "Make the Current Selection the Default Perspective");
    revertButton = createVerticalButton(composite, "&Revert", "Reverts the Current Selection to its Original Value");
    deleteButton = createVerticalButton(composite, "D&elete", "Delete a User Defined Perspective");
    updateButtons();
    return composite;
  }

  @Override
  public void init(IWorkbench aWorkbench) {
    this.workbench = aWorkbench;
    this.perspectiveRegistry = PerspectiveRegistry.INSTANCE;
    IPreferenceStore store = getPreferenceStore();
    openPerspMode = store.getInt(WorkbenchPreferences.OPEN_PERSP_MODE);
  }

  @Override
  protected IPreferenceStore doGetPreferenceStore() {
    return WorkbenchPreferences.getStore();
  }

  /** Restore Defaults: the open mode and the default perspective of the product; the list keeps its perspectives. */
  @Override
  protected void performDefaults() {
    IPreferenceStore store = getPreferenceStore();
    openPerspMode = store.getDefaultInt(WorkbenchPreferences.OPEN_PERSP_MODE);
    openSameWindowButton.setSelection(WorkbenchPreferences.OPM_ACTIVE_PAGE == openPerspMode);
    openNewWindowButton.setSelection(WorkbenchPreferences.OPM_NEW_WINDOW == openPerspMode);
    defaultPerspectiveId = PerspectiveRegistry.productDefaultPerspectiveId();
    updatePerspectivesTable();
    updateButtons();
    super.performDefaults();
  }

  private int indexOf(String perspectiveId) {
    if (perspectiveId == null)
      return -1;
    for (int i = 0; i < perspectives.size(); i++)
      if (perspectives.get(i).getId().equals(perspectiveId))
        return i;
    return -1;
  }

  /** A predefined perspective is never deleted; a saved one with open instances is confirmed first. */
  private boolean canDeletePerspective(IPerspectiveDescriptor desc) {
    boolean open = false;
    for (IWorkbenchWindow window : workbench.getWorkbenchWindows())
      for (IWorkbenchPage page : window.getPages())
        for (IPerspectiveDescriptor d : page.getOpenPerspectives())
          if (d.getId().equals(desc.getId()))
            open = true;
    if (!open)
      return true;
    com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog d = new com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog(
        getShell(), "Delete Perspective", null,
        "Delete the '" + desc.getLabel() + "' perspective? It has open instances.",
        com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog.QUESTION, 0, "Yes", "No");
    return d.open() == 0;
  }

  @Override
  public boolean performOk() {
    if (!Objects.equals(defaultPerspectiveId, perspectiveRegistry.getDefaultPerspective()))
      perspectiveRegistry.setDefaultPerspective(defaultPerspectiveId);
    for (IPerspectiveDescriptor perspective : perspToDelete)
      for (IWorkbenchWindow window : workbench.getWorkbenchWindows())
        for (IWorkbenchPage page : window.getPages())
          page.closePerspective(perspective, true, false);
    perspectiveRegistry.deletePerspectives(perspToDelete);
    for (IPerspectiveDescriptor perspective : perspToRevert)
      perspectiveRegistry.revertPerspective(perspective);
    IPreferenceStore store = getPreferenceStore();
    store.setValue(WorkbenchPreferences.OPEN_PERSP_MODE, openPerspMode);
    return true;
  }

  protected void updateButtons() {
    int index = perspectivesTable.getSelectionIndex();
    PerspectiveDescriptor desc = null;
    if (index > -1)
      desc = (PerspectiveDescriptor) perspectives.get(index);
    if (desc != null) {
      revertButton.setEnabled(desc.isPredefined() && desc.hasCustomDefinition() && !perspToRevert.contains(desc));
      deleteButton.setEnabled(!desc.isPredefined());
      setDefaultButton.setEnabled(true);
    } else {
      revertButton.setEnabled(false);
      deleteButton.setEnabled(false);
      setDefaultButton.setEnabled(false);
    }
  }

  protected void updatePerspectivesTable() {
    perspectivesTable.removeAll();
    for (int i = 0; i < perspectives.size(); i++) {
      IPerspectiveDescriptor persp = perspectives.get(i);
      String label = persp.getLabel();
      if (persp.getId().equals(defaultPerspectiveId))
        label = label + " (default)";
      TableItem item = new TableItem(perspectivesTable, SWT.NULL, i);
      item.setText(label);
      item.setData(persp);
    }
  }

  protected void verticalButtonPressed(Object button) {
    int index = perspectivesTable.getSelectionIndex();
    if (index <= -1)
      return;
    PerspectiveDescriptor desc = (PerspectiveDescriptor) perspectives.get(index);
    if (button == revertButton) {
      if (!perspToRevert.contains(desc))
        perspToRevert.add(desc);
    } else if (button == deleteButton) {
      if (!perspToDelete.contains(desc) && canDeletePerspective(desc)) {
        perspToDelete.add(desc);
        perspToRevert.remove(desc);
        perspectives.remove(desc);
        updatePerspectivesTable();
      }
    } else if (button == setDefaultButton) {
      defaultPerspectiveId = desc.getId();
      updatePerspectivesTable();
      perspectivesTable.setSelection(index);
    }
    updateButtons();
  }
}
