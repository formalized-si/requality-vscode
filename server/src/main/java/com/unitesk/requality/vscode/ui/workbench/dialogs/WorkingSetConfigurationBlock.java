package com.unitesk.requality.vscode.ui.workbench.dialogs;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogSettings;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.window.Window;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardDialog;
import com.unitesk.requality.vscode.ui.runtime.Assert;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Combo;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSetManager;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/**
 * {@code org.eclipse.ui.dialogs.WorkingSetConfigurationBlock}: the block of the working sets of a new project. A check box
 * enables the working sets, a combo shows the recently selected working sets, and the "Select..." and "New..." buttons
 * choose or create them.
 *
 */
public class WorkingSetConfigurationBlock {

  /**
   * Filters the given working sets such that the following is true: for each IWorkingSet s in result: s.getId() is element of
   * workingSetIds.
   *
   * @param workingSets the array to filter
   * @param workingSetIds the acceptable working set ids
   * @return the filtered elements
   */
  public static IWorkingSet[] filter(IWorkingSet[] workingSets, String[] workingSetIds) {
    String[] workingSetIdsCopy = Arrays.copyOf(workingSetIds, workingSetIds.length);
    Arrays.sort(workingSetIdsCopy);
    List<IWorkingSet> result = new ArrayList<>();
    for (IWorkingSet workingSet : workingSets) {
      if (Arrays.binarySearch(workingSetIdsCopy, workingSet.getId()) >= 0) {
        result.add(workingSet);
      }
    }
    return result.toArray(new IWorkingSet[result.size()]);
  }

  private static final IWorkingSet[] EMPTY_WORKING_SET_ARRAY = new IWorkingSet[0];
  private static final String WORKINGSET_SELECTION_HISTORY = "workingset_selection_history"; //$NON-NLS-1$
  private static final int MAX_HISTORY_SIZE = 5;

  private Label workingSetLabel;
  private Combo workingSetCombo;
  private Button selectButton;
  private Button enableButton;
  private Button newButton;
  private IWorkingSet[] selectedWorkingSets;
  private final List<String> selectionHistory;
  private final IDialogSettings dialogSettings;
  private final String[] workingSetTypeIds;
  private final String enableButtonLabel;
  private final String newButtonLabel;
  private final String comboLabel;
  private final String selectLabel;

  /**
   * Create a new instance of this working set block using default labels.
   *
   * @param settings to store/load the selection history
   * @param workingSetIds working set ids from which the user can choose
   */
  public WorkingSetConfigurationBlock(IDialogSettings settings, String... workingSetIds) {
    this(settings, null, null, null, null, workingSetIds);
  }

  /**
   * Create a new instance of this working set block using custom labels.
   *
   * @param settings to store/load the selection history
   * @param enableButtonLabel the label of the enablement check box, or null for the default
   * @param newButtonLabel the label of the new button, or null for the default
   * @param comboLabel the label of the recent working set combo, or null for the default
   * @param selectLabel the label of the select button, or null for the default
   * @param workingSetIds working set ids from which the user can choose
   */
  public WorkingSetConfigurationBlock(IDialogSettings settings, String enableButtonLabel, String newButtonLabel,
      String comboLabel, String selectLabel, String... workingSetIds) {
    Assert.isNotNull(workingSetIds);
    Assert.isNotNull(settings);
    workingSetTypeIds = workingSetIds;
    Arrays.sort(workingSetIds);
    selectedWorkingSets = EMPTY_WORKING_SET_ARRAY;
    dialogSettings = settings;
    selectionHistory = loadSelectionHistory(settings, workingSetIds);
    this.enableButtonLabel = enableButtonLabel == null ? WorkingSetGroup.ENABLE_BUTTON_LABEL : enableButtonLabel;
    this.newButtonLabel = newButtonLabel == null ? "Ne&w..." : newButtonLabel; //$NON-NLS-1$
    this.comboLabel = comboLabel == null ? "W&orking sets:" : comboLabel; //$NON-NLS-1$
    this.selectLabel = selectLabel == null ? "S&elect..." : selectLabel; //$NON-NLS-1$
  }

  /**
   * Set the current selection of working sets. This array will be filtered to contain only working sets that are applicable to
   * this instance.
   *
   * @param workingSets the working sets
   */
  public void setWorkingSets(IWorkingSet... workingSets) {
    selectedWorkingSets = filterWorkingSets(Arrays.asList(workingSets));
    if (workingSetCombo != null) {
      updateSelectedWorkingSets();
    }
  }

  /**
   * Retrieves the working sets of the given selection, filtered on the criteria used to construct this instance.
   *
   * @param selection the selection to retrieve the working sets from
   * @return the applicable working sets or an empty array
   */
  public IWorkingSet[] findApplicableWorkingSets(IStructuredSelection selection) {
    if (selection == null) {
      return EMPTY_WORKING_SET_ARRAY;
    }
    return filterWorkingSets(selection.toList());
  }

  private IWorkingSet[] filterWorkingSets(Collection<?> elements) {
    List<IWorkingSet> result = new ArrayList<>();
    for (Object element : elements) {
      if (element instanceof IWorkingSet && verifyWorkingSet((IWorkingSet) element)) {
        result.add((IWorkingSet) element);
      }
    }
    return result.toArray(new IWorkingSet[result.size()]);
  }

  private boolean verifyWorkingSet(IWorkingSet workingSetCandidate) {
    return !workingSetCandidate.isAggregateWorkingSet()
        && Arrays.binarySearch(workingSetTypeIds, workingSetCandidate.getId()) >= 0;
  }

  /**
   * The currently selected working sets. If the controls of this block are disabled this array is empty.
   *
   * @return the selected working sets
   */
  public IWorkingSet[] getSelectedWorkingSets() {
    if (enableButton.getSelection()) {
      return selectedWorkingSets;
    }
    return EMPTY_WORKING_SET_ARRAY;
  }

  /**
   * Adds this block to the parent.
   *
   * @param parent the parent to add the block to
   */
  public void createContent(final Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);
    composite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
    composite.setLayout(new GridLayout(3, false));

    enableButton = new Button(composite, SWT.CHECK);
    enableButton.setText(enableButtonLabel);
    GridData enableData = new GridData(SWT.FILL, SWT.CENTER, true, false);
    enableData.horizontalSpan = 2;
    enableButton.setLayoutData(enableData);
    enableButton.setSelection(selectedWorkingSets.length > 0);

    newButton = new Button(composite, SWT.PUSH);
    newButton.setText(newButtonLabel);
    newButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
    newButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        createNewWorkingSet(newButton.getShell());
      }
    });

    workingSetLabel = new Label(composite, SWT.NONE);
    workingSetLabel.setText(comboLabel);

    workingSetCombo = new Combo(composite, SWT.READ_ONLY | SWT.BORDER);
    workingSetCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    selectButton = new Button(composite, SWT.PUSH);
    selectButton.setText(selectLabel);
    selectButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false));
    selectButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        SimpleWorkingSetSelectionDialog dialog = new SimpleWorkingSetSelectionDialog(parent.getShell(), workingSetTypeIds,
            selectedWorkingSets, false);
        dialog.setMessage("The new project will be added to the selected working sets:"); //$NON-NLS-1$
        if (dialog.open() == Window.OK) {
          IWorkingSet[] result = dialog.getSelection();
          if (result != null && result.length > 0) {
            selectedWorkingSets = result;
            PlatformUI.getWorkbench().getWorkingSetManager().addRecentWorkingSet(result[0]);
          } else {
            selectedWorkingSets = EMPTY_WORKING_SET_ARRAY;
          }
          updateWorkingSetSelection();
        }
      }
    });

    enableButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        updateEnableState(enableButton.getSelection());
      }
    });
    updateEnableState(enableButton.getSelection());

    workingSetCombo.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        updateSelectedWorkingSets();
      }
    });
    workingSetCombo.setItems(getHistoryEntries());
    if (selectedWorkingSets.length == 0 && selectionHistory.size() > 0) {
      workingSetCombo.select(historyIndex(selectionHistory.get(0)));
      updateSelectedWorkingSets();
    } else {
      updateWorkingSetSelection();
    }
  }

  private void createNewWorkingSet(com.unitesk.requality.vscode.ui.swt.widgets.Shell shell) {
    IWorkingSetManager manager = PlatformUI.getWorkbench().getWorkingSetManager();
    com.unitesk.requality.vscode.ui.workbench.dialogs.IWorkingSetNewWizard wizard = manager
        .createWorkingSetNewWizard(workingSetTypeIds);
    WizardDialog dialog = new WizardDialog(shell, wizard);
    dialog.create();
    if (dialog.open() == Window.OK) {
      IWorkingSet workingSet = wizard.getSelection();
      if (workingSet != null) {
        manager.addWorkingSet(workingSet);
        selectedWorkingSets = new IWorkingSet[] { workingSet };
        manager.addRecentWorkingSet(workingSet);
      }
      enableButton.setSelection(true);
      updateEnableState(true);
      updateWorkingSetSelection();
    }
  }

  private void updateEnableState(boolean enabled) {
    workingSetLabel.setEnabled(enabled);
    workingSetCombo.setEnabled(enabled && (selectedWorkingSets.length > 0 || getHistoryEntries().length > 0));
    selectButton.setEnabled(enabled);
  }

  private void updateWorkingSetSelection() {
    if (selectedWorkingSets.length > 0) {
      workingSetCombo.setEnabled(true);
      StringBuilder buf = new StringBuilder();
      buf.append(selectedWorkingSets[0].getLabel());
      for (int i = 1; i < selectedWorkingSets.length; i++) {
        buf.append(',').append(' ');
        buf.append(selectedWorkingSets[i].getLabel());
      }
      String currentSelection = buf.toString();
      int index = historyIndex(currentSelection);
      historyInsert(currentSelection);
      if (index >= 0) {
        workingSetCombo.select(index);
      } else {
        workingSetCombo.setItems(getHistoryEntries());
        workingSetCombo.select(historyIndex(currentSelection));
      }
    } else {
      enableButton.setSelection(false);
      updateEnableState(false);
    }
  }

  private String[] getHistoryEntries() {
    String[] history = selectionHistory.toArray(new String[selectionHistory.size()]);
    Arrays.sort(history, (o1, o2) -> Collator.getInstance().compare(o1, o2));
    return history;
  }

  private void historyInsert(String entry) {
    selectionHistory.remove(entry);
    selectionHistory.add(0, entry);
    storeSelectionHistory(dialogSettings);
  }

  private int historyIndex(String entry) {
    for (int i = 0; i < workingSetCombo.getItemCount(); i++) {
      if (workingSetCombo.getItem(i).equals(entry)) {
        return i;
      }
    }
    return -1;
  }

  private static String[] split(String value, String delimiters) {
    StringTokenizer tokenizer = new StringTokenizer(value, delimiters);
    List<String> tokens = new ArrayList<>();
    while (tokenizer.hasMoreTokens()) {
      tokens.add(tokenizer.nextToken());
    }
    return tokens.toArray(new String[tokens.size()]);
  }

  private void updateSelectedWorkingSets() {
    String item = workingSetCombo.getItem(workingSetCombo.getSelectionIndex());
    String[] workingSetNames = split(item, ", "); //$NON-NLS-1$
    IWorkingSetManager workingSetManager = PlatformUI.getWorkbench().getWorkingSetManager();
    selectedWorkingSets = new IWorkingSet[workingSetNames.length];
    for (int i = 0; i < workingSetNames.length; i++) {
      IWorkingSet set = workingSetManager.getWorkingSet(workingSetNames[i]);
      Assert.isNotNull(set);
      selectedWorkingSets[i] = set;
    }
  }

  private void storeSelectionHistory(IDialogSettings settings) {
    List<String> history = selectionHistory.size() > MAX_HISTORY_SIZE
        ? selectionHistory.subList(0, MAX_HISTORY_SIZE)
        : selectionHistory;
    settings.put(WORKINGSET_SELECTION_HISTORY, history.toArray(new String[history.size()]));
  }

  private List<String> loadSelectionHistory(IDialogSettings settings, String... workingSetIds) {
    String[] strings = settings.getArray(WORKINGSET_SELECTION_HISTORY);
    List<String> result = new ArrayList<>();
    if (strings == null || strings.length == 0) {
      return result;
    }
    Set<String> workingSetIdsSet = new HashSet<>(Arrays.asList(workingSetIds));
    IWorkingSetManager workingSetManager = PlatformUI.getWorkbench().getWorkingSetManager();
    for (String string : strings) {
      boolean valid = true;
      for (String name : split(string, ", ")) { //$NON-NLS-1$
        IWorkingSet workingSet = workingSetManager.getWorkingSet(name);
        if (workingSet == null || !workingSetIdsSet.contains(workingSet.getId())) {
          valid = false;
          break;
        }
      }
      if (valid) {
        result.add(string);
      }
    }
    return result;
  }
}
