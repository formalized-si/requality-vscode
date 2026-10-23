package com.unitesk.requality.vscode.ui.workbench;

import com.unitesk.requality.vscode.ui.jface.preference.IPropertyChangeListener;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.dialogs.IWorkingSetEditWizard;
import com.unitesk.requality.vscode.ui.workbench.dialogs.IWorkingSetNewWizard;
import com.unitesk.requality.vscode.ui.workbench.dialogs.IWorkingSetSelectionDialog;

/** {@code org.eclipse.ui.IWorkingSetManager}: stores the working sets and notifies the changes of them. */
public interface IWorkingSetManager {
  /** Change event id when a working set is added (newValue: the added working set, oldValue: null). */
  String CHANGE_WORKING_SET_ADD = "workingSetAdd";

  /** Change event id when a working set is removed (newValue: null, oldValue: the removed working set). */
  String CHANGE_WORKING_SET_REMOVE = "workingSetRemove";

  /** Change event id when the contents of a working set changed (newValue: the changed working set). */
  String CHANGE_WORKING_SET_CONTENT_CHANGE = "workingSetContentChange";

  /** Change event id when the name of a working set changed (newValue: the changed working set). */
  String CHANGE_WORKING_SET_NAME_CHANGE = "workingSetNameChange";

  /** Change event id when the label of a working set changed (newValue: the changed working set). */
  String CHANGE_WORKING_SET_LABEL_CHANGE = "workingSetLabelChange";

  /** Change event id when a working set updater got installed (newValue: the updater). */
  String CHANGE_WORKING_SET_UPDATER_INSTALLED = "workingSetUpdaterInstalled";

  /** Change event id when a working set updater got uninstalled (oldValue: the updater). */
  String CHANGE_WORKING_SET_UPDATER_UNINSTALLED = "workingSetUpdaterUninstalled";

  void addPropertyChangeListener(IPropertyChangeListener listener);

  /** Adds the working set to the top of the most recently used working sets. */
  void addRecentWorkingSet(IWorkingSet workingSet);

  /** Adds a working set to the manager. The working set must not exist yet. */
  void addWorkingSet(IWorkingSet workingSet);

  /** Creates a working set; it is not added to the manager. */
  IWorkingSet createWorkingSet(String name, IAdaptable[] elements);

  /** Creates the union of the components; it is updated when the components change. */
  IWorkingSet createAggregateWorkingSet(String name, String label, IWorkingSet[] components);

  /** Re-creates a working set from the memento, or null when it cannot be created. */
  IWorkingSet createWorkingSet(IMemento memento);

  /** The most recently used working sets, the most recent first. */
  IWorkingSet[] getRecentWorkingSets();

  /** The working set of the name, or null. */
  IWorkingSet getWorkingSet(String name);

  /** The visible working sets, sorted by name. */
  IWorkingSet[] getWorkingSets();

  /** All working sets, including the invisible ones. */
  IWorkingSet[] getAllWorkingSets();

  void removePropertyChangeListener(IPropertyChangeListener listener);

  void removeWorkingSet(IWorkingSet workingSet);

  /** Disposes the manager. */
  void dispose();

  /** Adds the element, adapted by each working set, to the working sets. */
  void addToWorkingSets(IAdaptable element, IWorkingSet[] workingSets);

  /** Creates the wizard that edits the working set, or null when its type has no page. */
  IWorkingSetEditWizard createWorkingSetEditWizard(IWorkingSet workingSet);

  /** Creates the wizard that creates a working set of the given types, or null when none can be created. */
  IWorkingSetNewWizard createWorkingSetNewWizard(String[] workingSetIds);

  /** Creates the dialog that selects the working sets (all types). */
  IWorkingSetSelectionDialog createWorkingSetSelectionDialog(Shell parentShell, boolean multi);

  /** Creates the dialog that selects the working sets of the given types. */
  IWorkingSetSelectionDialog createWorkingSetSelectionDialog(Shell parentShell, boolean multi,
      String[] workingSetIds);

  /** Sets the maximum length of the recent working sets list (1 to 99). */
  void setRecentWorkingSetsLength(int length);

  /** The maximum length of the recent working sets list. */
  int getRecentWorkingSetsLength();
}
