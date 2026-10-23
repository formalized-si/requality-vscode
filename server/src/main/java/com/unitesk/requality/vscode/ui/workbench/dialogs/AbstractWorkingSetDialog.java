package com.unitesk.requality.vscode.ui.workbench.dialogs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.dialogs.SelectionDialog;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardDialog;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSetManager;
import com.unitesk.requality.vscode.ui.workbench.Workbench;
import com.unitesk.requality.vscode.ui.workbench.internal.WorkingSet;
import com.unitesk.requality.vscode.ui.workbench.internal.WorkingSetRegistry;

/**
 * {@code org.eclipse.ui.internal.dialogs.AbstractWorkingSetDialog}: the base of the working set dialogs, with the buttons
 * to create, edit and remove the working sets, and to select all or none of them.
 */
public abstract class AbstractWorkingSetDialog extends SelectionDialog implements IWorkingSetSelectionDialog {
  private static final int ID_NEW = IDialogConstants.CLIENT_ID + 1;
  private static final int ID_DETAILS = ID_NEW + 1;
  private static final int ID_REMOVE = ID_DETAILS + 1;
  private static final int ID_SELECTALL = ID_REMOVE + 1;
  private static final int ID_DESELECTALL = ID_SELECTALL + 1;

  private Button newButton;
  private Button detailsButton;
  private Button removeButton;
  private Button selectAllButton;
  private Button deselectAllButton;

  private IWorkingSet[] result;
  private List<IWorkingSet> addedWorkingSets;
  private List<IWorkingSet> removedWorkingSets;
  private Map<IWorkingSet, IWorkingSet> editedWorkingSets;
  private List<IWorkingSet> removedMRUWorkingSets;
  private Set<String> workingSetIds;
  private final boolean canEdit;

  protected AbstractWorkingSetDialog(Shell parentShell, String[] workingSetIds, boolean canEdit) {
    super(parentShell);
    if (workingSetIds != null) {
      this.workingSetIds = new HashSet<>();
      this.workingSetIds.addAll(Arrays.asList(workingSetIds));
    }
    this.canEdit = canEdit;
  }

  /** The set of supported working set types, or null for all of them. */
  protected Set<String> getSupportedWorkingSetIds() {
    return workingSetIds;
  }

  /** Adds the modify buttons (New, Edit, Remove) to the dialog. */
  protected void addModifyButtons(Composite composite) {
    GridLayout layout = new GridLayout();
    layout.marginHeight = layout.marginWidth = 0;
    layout.verticalSpacing = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_SPACING);
    GridData data = new GridData(GridData.VERTICAL_ALIGN_BEGINNING | GridData.GRAB_VERTICAL);
    Composite buttonComposite = new Composite(composite, SWT.RIGHT);
    buttonComposite.setLayout(layout);
    buttonComposite.setLayoutData(data);
    newButton = createButton(buttonComposite, ID_NEW, "&New...", false);
    newButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(com.unitesk.requality.vscode.ui.swt.events.SelectionEvent e) {
        createWorkingSet();
      }
    });
    if (canEdit) {
      detailsButton = createButton(buttonComposite, ID_DETAILS, "&Edit...", false);
      detailsButton.setEnabled(false);
      detailsButton.addSelectionListener(new SelectionAdapter() {
        @Override
        public void widgetSelected(com.unitesk.requality.vscode.ui.swt.events.SelectionEvent e) {
          editSelectedWorkingSet();
        }
      });
      removeButton = createButton(buttonComposite, ID_REMOVE, "&Remove", false);
      removeButton.setEnabled(false);
      removeButton.addSelectionListener(new SelectionAdapter() {
        @Override
        public void widgetSelected(com.unitesk.requality.vscode.ui.swt.events.SelectionEvent e) {
          removeSelectedWorkingSets();
        }
      });
    }
    // the buttons are laid out vertically: createButton counts the columns, so they are reset here
    layout.numColumns = 1;
  }

  /** Adds the Select All and Deselect All buttons. */
  protected void addSelectionButtons(Composite composite) {
    GridLayout layout = new GridLayout(2, false);
    layout.marginHeight = layout.marginWidth = 0;
    layout.horizontalSpacing = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_SPACING);
    Composite buttonComposite = new Composite(composite, SWT.NONE);
    buttonComposite.setLayout(layout);
    buttonComposite.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING));
    selectAllButton = createButton(buttonComposite, ID_SELECTALL, "Select &All", false);
    selectAllButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(com.unitesk.requality.vscode.ui.swt.events.SelectionEvent e) {
        selectAllSets();
      }
    });
    deselectAllButton = createButton(buttonComposite, ID_DESELECTALL, "Dese&lect All", false);
    deselectAllButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(com.unitesk.requality.vscode.ui.swt.events.SelectionEvent e) {
        deselectAllSets();
      }
    });
  }

  /** Selects all working sets. */
  protected abstract void selectAllSets();

  /** Deselects all working sets. */
  protected abstract void deselectAllSets();

  /** Opens a working set wizard for editing the selected working set. */
  void editSelectedWorkingSet() {
    IWorkingSetManager manager = Workbench.INSTANCE.getWorkingSetManager();
    IWorkingSet editWorkingSet = getSelectedWorkingSets().get(0);
    IWorkingSetEditWizard wizard = manager.createWorkingSetEditWizard(editWorkingSet);
    if (wizard == null) {
      return;
    }
    WizardDialog dialog = new WizardDialog(getShell(), wizard);
    IWorkingSet originalWorkingSet = editedWorkingSets.get(editWorkingSet);
    boolean firstEdit = originalWorkingSet == null;
    // save the original working set values for restoration when selection dialog is cancelled.
    if (firstEdit) {
      originalWorkingSet = new WorkingSet(editWorkingSet.getName(), editWorkingSet.getLabel(),
          editWorkingSet.getElements());
    } else {
      editedWorkingSets.remove(editWorkingSet);
    }
    dialog.create();
    if (dialog.open() == com.unitesk.requality.vscode.ui.jface.window.Window.OK) {
      editWorkingSet = wizard.getSelection();
      availableWorkingSetsChanged();
      // make sure ok button is enabled when the selected working set is edited (bug 33386)
      updateButtonAvailability();
    }
    editedWorkingSets.put(editWorkingSet, originalWorkingSet);
  }

  /** Opens a working set wizard for creating a new working set. */
  void createWorkingSet() {
    IWorkingSetManager manager = Workbench.INSTANCE.getWorkingSetManager();
    String[] ids = null;
    if (workingSetIds != null) {
      ids = workingSetIds.toArray(new String[workingSetIds.size()]);
    }
    IWorkingSetNewWizard wizard = manager.createWorkingSetNewWizard(ids);
    // the wizard can never be null since we have at least a resource working set creation page
    WizardDialog dialog = new WizardDialog(getShell(), wizard);
    dialog.create();
    if (dialog.open() == com.unitesk.requality.vscode.ui.jface.window.Window.OK) {
      IWorkingSet workingSet = wizard.getSelection();
      manager.addWorkingSet(workingSet);
      addedWorkingSets.add(workingSet);
      availableWorkingSetsChanged();
      workingSetAdded(workingSet);
    }
  }

  /** The selected working sets of the list. */
  protected abstract List<IWorkingSet> getSelectedWorkingSets();

  /**
   * Notifies the dialog that there has been a change to the sets available for use (a set was added, deleted or
   * renamed). Subclasses should call this to update the selection button enablements.
   */
  protected void availableWorkingSetsChanged() {
    boolean enable = Workbench.INSTANCE.getWorkingSetManager().getWorkingSets().length > 0;
    if (!(selectAllButton == null || selectAllButton.isDisposed())) {
      selectAllButton.setEnabled(enable);
    }
    if (!(deselectAllButton == null || deselectAllButton.isDisposed())) {
      deselectAllButton.setEnabled(enable);
    }
  }

  /** Notifies the dialog that the given working set was added. Subclasses can select it. */
  protected void workingSetAdded(IWorkingSet addedSet) {
  }

  @Override
  public IWorkingSet[] getSelection() {
    return result;
  }

  @Override
  public void setSelection(IWorkingSet[] selection) {
    result = selection;
  }

  @Override
  public int open() {
    addedWorkingSets = new ArrayList<>();
    removedWorkingSets = new ArrayList<>();
    editedWorkingSets = new HashMap<>();
    removedMRUWorkingSets = new ArrayList<>();
    return super.open();
  }

  /** The working sets that were added during the life of this dialog. */
  protected final List<IWorkingSet> getAddedWorkingSets() {
    return addedWorkingSets;
  }

  /** The working sets that were edited during the life of this dialog, with their original values. */
  protected final Map<IWorkingSet, IWorkingSet> getEditedWorkingSets() {
    return editedWorkingSets;
  }

  /** The working sets that were removed from the most recently used list during the life of this dialog. */
  protected final List<IWorkingSet> getRemovedMRUWorkingSets() {
    return removedMRUWorkingSets;
  }

  /** The working sets that were removed during the life of this dialog. */
  protected final List<IWorkingSet> getRemovedWorkingSets() {
    return removedWorkingSets;
  }

  /** Updates the enabled state of the modify buttons from the selection. */
  protected void updateButtonAvailability() {
    List<IWorkingSet> selection = getSelectedWorkingSets();
    boolean hasSelection = !selection.isEmpty();
    boolean hasSingleSelection = hasSelection;
    WorkingSetRegistry registry = WorkingSetRegistry.get();
    newButton.setEnabled(registry.hasNewPageWorkingSetDescriptor());
    if (canEdit) {
      removeButton.setEnabled(hasSelection);
    }
    IWorkingSet selectedWorkingSet = null;
    if (hasSelection) {
      hasSingleSelection = selection.size() == 1;
      if (hasSingleSelection) {
        selectedWorkingSet = selection.get(0);
      }
    }
    if (canEdit) {
      detailsButton.setEnabled(hasSingleSelection && selectedWorkingSet.isEditable());
    }
    getButton(IDialogConstants.OK_ID).setEnabled(true);
  }

  /** Removes the selected working sets from the workbench. */
  protected void removeSelectedWorkingSets() {
    removeSelectedWorkingSets(getSelectedWorkingSets());
  }

  /** Removes the working sets of the selection from the working set manager. */
  protected void removeSelectedWorkingSets(List<IWorkingSet> selection) {
    IWorkingSetManager manager = Workbench.INSTANCE.getWorkingSetManager();
    for (IWorkingSet workingSet : selection) {
      if (getAddedWorkingSets().contains(workingSet)) {
        getAddedWorkingSets().remove(workingSet);
      } else {
        for (IWorkingSet recentWorkingSet : manager.getRecentWorkingSets()) {
          if (workingSet.equals(recentWorkingSet)) {
            getRemovedMRUWorkingSets().add(workingSet);
            break;
          }
        }
        getRemovedWorkingSets().add(workingSet);
      }
      manager.removeWorkingSet(workingSet);
    }
    availableWorkingSetsChanged();
  }
}
