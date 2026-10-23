package com.unitesk.requality.vscode.ui.team.wizards;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.wizard.IWizard;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardPage;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Table;
import com.unitesk.requality.vscode.ui.swt.widgets.TableItem;
import com.unitesk.requality.vscode.ui.team.preferences.TeamUIPlugin;

/**
 * {@code org.eclipse.team.internal.ui.wizards.GlobalRefreshWizardSelectionPage}: the page of the Synchronize wizard that lists
 * the synchronize types, the {@code synchronizeWizards} extensions of org.eclipse.team.ui. The selected type creates its wizard,
 * whose first page is the next page; a double click goes to it. The last type chosen is selected by default.
 */
public class GlobalRefreshWizardSelectionPage extends WizardPage {
  /** The preference of the last synchronize type chosen. */
  static final String DEFAULT_SELECTION = "org.eclipse.team.ui.GlobalRefreshWizardSelectionPage.default_selection";
  static final String PT_SYNCHRONIZE_WIZARDS = "synchronizeWizards";

  private final List<IConfigurationElement> descriptors = getDescriptors();
  private Table table;
  private IWizard wizard;

  public GlobalRefreshWizardSelectionPage() {
    super("globalRefreshWizardSelectionPage");
    setTitle("Synchronize");
    setDescription("Select the type of synchronization");
  }

  /** The {@code wizard} elements of the synchronize wizards, sorted by name. */
  static List<IConfigurationElement> getDescriptors() {
    List<IConfigurationElement> result = new ArrayList<>();
    for (IConfigurationElement e : ExtensionRegistry.get().getConfigurationElementsFor("org.eclipse.team.ui",
        PT_SYNCHRONIZE_WIZARDS))
      if ("wizard".equals(e.getName()) && e.getAttribute("name") != null)
        result.add(e);
    result.sort(Comparator.comparing(e -> e.getAttribute("name").toLowerCase()));
    return result;
  }

  @Override
  public void createControl(Composite parent) {
    Composite top = new Composite(parent, SWT.NONE);
    top.setLayout(new GridLayout(1, false));
    setControl(top);

    Label l = new Label(top, SWT.NONE);
    l.setText("Available synchronization types:");
    table = new Table(top, SWT.BORDER | SWT.SINGLE);
    table.setLayoutData(new GridData(GridData.FILL_BOTH));
    for (IConfigurationElement d : descriptors)
      new TableItem(table, SWT.NONE).setText(d.getAttribute("name"));
    table.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        selectionChanged();
      }

      @Override
      public void widgetDefaultSelected(SelectionEvent e) {
        selectionChanged();
        if (getNextPage() != null)
          getContainer().showPage(getNextPage());
      }
    });
    int selected = indexOfDefault();
    if (selected >= 0) {
      table.select(selected);
      selectionChanged();
    }
    table.setFocus();
  }

  private int indexOfDefault() {
    String last = TeamUIPlugin.getPreferenceStore().getString(DEFAULT_SELECTION);
    for (int i = 0; i < descriptors.size(); i++)
      if (descriptors.get(i).getAttribute("id") != null && descriptors.get(i).getAttribute("id").equals(last))
        return i;
    return descriptors.isEmpty() ? -1 : 0;
  }

  /** Creates the wizard of the selected type, the page is complete when it is created. */
  private void selectionChanged() {
    int index = table.getSelectionIndex();
    wizard = null;
    if (index < 0 || index >= descriptors.size()) {
      setPageComplete(false);
      return;
    }
    IConfigurationElement d = descriptors.get(index);
    try {
      wizard = (IWizard) d.createExecutableExtension("class");
      wizard.addPages();
      setPageComplete(true);
      setDescription(d.getAttribute("description") != null ? d.getAttribute("description")
          : "Select the type of synchronization");
    } catch (CoreException | ClassCastException e) {
      setPageComplete(false);
    }
  }

  /** The wizard of the selected synchronize type, or null. */
  public IWizard getSelectedWizard() {
    return wizard;
  }

  /** Remembers the synchronize type chosen. */
  public void savePageSettings() {
    int index = table.getSelectionIndex();
    if (index >= 0 && index < descriptors.size() && descriptors.get(index).getAttribute("id") != null)
      TeamUIPlugin.getPreferenceStore().setValue(DEFAULT_SELECTION, descriptors.get(index).getAttribute("id"));
  }

  @Override
  public com.unitesk.requality.vscode.ui.jface.wizard.IWizardPage getNextPage() {
    return wizard == null ? null : wizard.getStartingPage();
  }
}
