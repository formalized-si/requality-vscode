package com.unitesk.requality.vscode.ui.ide.markers;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.dialogs.InputDialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.ArrayContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.CheckboxTableViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.CheckStateChangedEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.ICheckStateListener;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionChangedListener;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.SelectionChangedEvent;
import com.unitesk.requality.vscode.ui.resources.IMarker;
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
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;

/**
 * {@code org.eclipse.ui.internal.views.markers.FiltersConfigurationDialog} (Filters...): the configurations of a markers
 * view. The checked configurations are shown (any or all of them, as chosen), or all items when "Show all items" is
 * checked. The selected configuration is edited in the scope, description, completed and priority areas; the limit of
 * the items per group is set for the view. The dialog works on copies: the view takes them after OK.
 */
public class FiltersConfigurationDialog extends Dialog {
  private static final int ALL_ID = IDialogConstants.CLIENT_ID + 1;
  private static final int NEW_ID = IDialogConstants.CLIENT_ID + 2;
  private static final int REMOVE_ID = IDialogConstants.CLIENT_ID + 3;
  private static final int RENAME_ID = IDialogConstants.CLIENT_ID + 4;

  private final List<MarkerFilterConfiguration> configurations = new ArrayList<>();
  private final List<String> selectionPaths;
  private final boolean hasCompletion;
  private final boolean hasPriority;
  private boolean matchAll;
  private boolean limitsEnabled;
  private int limit;

  private Button showAllButton, matchAllButton, matchAnyButton, limitButton;
  private Button addButton, removeButton, renameButton;
  private CheckboxTableViewer configsTable;
  private Button anyScope, sameProjectScope, selectedScope, childrenScope;
  private Text textField;
  private Combo textCombo;
  private Button completedButton, notCompletedButton, highButton, normalButton, lowButton;
  private Button errorButton, warningButton, infoButton;
  /** The severity area of the problems ({@code SeverityAndDescriptionConfigurationArea}). */
  private final boolean hasSeverity;
  private Text limitText;
  private MarkerFilterConfiguration current;

  /**
   * @param markerType the marker type of the view (the completed and priority areas are the ones of the tasks)
   * @param configurations the configurations of the view (copied)
   * @param selectionPaths the full paths of the resources selected in the view: the scope of the selected areas
   */
  public FiltersConfigurationDialog(Shell parent, String markerType, List<MarkerFilterConfiguration> configurations,
      boolean matchAll, boolean limitsEnabled, int limit, List<String> selectionPaths) {
    super(parent);
    for (MarkerFilterConfiguration c : configurations) {
      MarkerFilterConfiguration copy = c.copy(c.getName());
      copy.setEnabled(c.isEnabled());
      this.configurations.add(copy);
    }
    this.hasCompletion = IMarker.TASK.equals(markerType);
    this.hasPriority = IMarker.TASK.equals(markerType);
    this.hasSeverity = IMarker.PROBLEM.equals(markerType);
    this.matchAll = matchAll;
    this.limitsEnabled = limitsEnabled;
    this.limit = limit;
    this.selectionPaths = new ArrayList<>(selectionPaths);
  }

  @Override
  protected void configureShell(Shell newShell) {
    super.configureShell(newShell);
    newShell.setText("Filters");
  }

  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    createButton(parent, IDialogConstants.OK_ID, "&Apply and Close", true);
    createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite area = (Composite) super.createDialogArea(parent);
    Composite composite = new Composite(area, SWT.NONE);
    composite.setLayout(new GridLayout(1, false));
    composite.setLayoutData(new GridData(GridData.FILL_BOTH));

    showAllButton = new Button(composite, SWT.CHECK);
    showAllButton.setText("Sho&w all items");
    showAllButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        if (showAllButton.getSelection())
          configsTable.setAllChecked(false);
        else if (configsTable.getCheckedElements().length == 0 && !configurations.isEmpty())
          configsTable.setChecked(configurations.get(0), true);
        updateEnablement();
      }
    });

    Group configs = new Group(composite, SWT.NONE);
    configs.setText("&Configurations");
    configs.setLayout(new GridLayout(2, false));
    configs.setLayoutData(new GridData(GridData.FILL_BOTH));

    configsTable = CheckboxTableViewer.newCheckList(configs, SWT.BORDER | SWT.SINGLE);
    GridData tableData = new GridData(GridData.FILL_BOTH);
    tableData.widthHint = 160;
    tableData.heightHint = 120;
    configsTable.getControl().setLayoutData(tableData);
    configsTable.setContentProvider(ArrayContentProvider.getInstance());
    configsTable.setLabelProvider(new LabelProvider() {
      @Override
      public String getText(Object element) {
        return ((MarkerFilterConfiguration) element).getName();
      }
    });
    configsTable.setInput(configurations);
    for (MarkerFilterConfiguration c : configurations)
      if (c.isEnabled())
        configsTable.setChecked(c, true);
    configsTable.addCheckStateListener(new ICheckStateListener() {
      @Override
      public void checkStateChanged(CheckStateChangedEvent event) {
        ((MarkerFilterConfiguration) event.getElement()).setEnabled(event.getChecked());
        if (event.getChecked())
          showAllButton.setSelection(false);
        updateEnablement();
      }
    });
    configsTable.addSelectionChangedListener(new ISelectionChangedListener() {
      @Override
      public void selectionChanged(SelectionChangedEvent event) {
        Object first = ((IStructuredSelection) event.getSelection()).getFirstElement();
        select(first instanceof MarkerFilterConfiguration ? (MarkerFilterConfiguration) first : null);
      }
    });

    Composite buttons = new Composite(configs, SWT.NONE);
    buttons.setLayout(new GridLayout(1, false));
    addButton = new Button(buttons, SWT.PUSH);
    addButton.setText("&New");
    addButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        newConfiguration();
      }
    });
    removeButton = new Button(buttons, SWT.PUSH);
    removeButton.setText("Remo&ve");
    removeButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        if (current != null) {
          configurations.remove(current);
          current = null;
          configsTable.refresh();
          select(null);
          updateEnablement();
        }
      }
    });
    renameButton = new Button(buttons, SWT.PUSH);
    renameButton.setText("&Rename");
    renameButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        rename();
      }
    });

    Group scope = new Group(composite, SWT.NONE);
    scope.setText("&Scope");
    scope.setLayout(new GridLayout(1, false));
    scope.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    anyScope = radio(scope, "No filter, show all &elements", MarkerFilterConfiguration.SCOPE_ANY);
    // {@code filtersDialog_anyResourceInSameProject}: the elements of the projects of the selected resources.
    sameProjectScope = radio(scope, "On elements in selected pro&jects",
        MarkerFilterConfiguration.SCOPE_SAME_PROJECT);
    selectedScope = radio(scope, "On selected ele&ments only", MarkerFilterConfiguration.SCOPE_SELECTED);
    childrenScope = radio(scope, "On selected elements and their c&hildren",
        MarkerFilterConfiguration.SCOPE_SELECTED_AND_CHILDREN);

    Group description = new Group(composite, SWT.NONE);
    description.setText("&Description");
    description.setLayout(new GridLayout(2, false));
    description.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    Label textLabel = new Label(description, SWT.NONE);
    textLabel.setText("Te&xt: ");
    textField = new Text(description, SWT.BORDER);
    textField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    textField.addModifyListener(new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent e) {
        if (current != null)
          current.setText(textField.getText());
      }
    });
    textCombo = new Combo(description, SWT.READ_ONLY);
    textCombo.setItems(new String[] { "contains", "doesn't contain" });
    textCombo.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        if (current != null)
          current.setContainsText(textCombo.getSelectionIndex() == 0);
      }
    });

    if (hasSeverity) {
      // SeverityAndDescriptionConfigurationArea.createSeverityGroup
      Composite severityComposite = new Composite(composite, SWT.NONE);
      severityComposite.setLayout(new GridLayout(4, false));
      severityComposite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.GRAB_HORIZONTAL));
      Label label = new Label(severityComposite, SWT.NONE);
      label.setText("Where severit&y is:");
      errorButton = check(severityComposite, "E&rror", v -> severityChanged(IMarker.SEVERITY_ERROR, v));
      warningButton = check(severityComposite, "Warnin&g", v -> severityChanged(IMarker.SEVERITY_WARNING, v));
      infoButton = check(severityComposite, "Inf&o", v -> severityChanged(IMarker.SEVERITY_INFO, v));
    }
    if (hasCompletion) {
      Group completion = new Group(composite, SWT.NONE);
      completion.setText("&Completed");
      completion.setLayout(new GridLayout(1, false));
      completion.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
      Label label = new Label(completion, SWT.NONE);
      label.setText("Where stat&us is:");
      completedButton = check(completion, "C&ompleted", v -> {
        if (current != null)
          current.setCompleted(v);
      });
      notCompletedButton = check(completion, "Not Completed", v -> {
        if (current != null)
          current.setNotCompleted(v);
      });
    }
    if (hasPriority) {
      Group priority = new Group(composite, SWT.NONE);
      priority.setText("&Priority");
      priority.setLayout(new GridLayout(1, false));
      priority.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
      Label label = new Label(priority, SWT.NONE);
      label.setText("Where priorit&y is: ");
      highButton = check(priority, "Hi&gh", v -> priorityChanged(IMarker.PRIORITY_HIGH, v));
      normalButton = check(priority, "No&rmal", v -> priorityChanged(IMarker.PRIORITY_NORMAL, v));
      lowButton = check(priority, "Lo&w", v -> priorityChanged(IMarker.PRIORITY_LOW, v));
    }

    matchAnyButton = new Button(composite, SWT.RADIO);
    matchAnyButton.setText("Show items that match a&ny configuration checked below");
    matchAnyButton.setSelection(!matchAll);
    matchAllButton = new Button(composite, SWT.RADIO);
    matchAllButton.setText("Show items that match a&ll the configurations checked below");
    matchAllButton.setSelection(matchAll);
    SelectionAdapter matchListener = new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        matchAll = matchAllButton.getSelection();
      }
    };
    matchAnyButton.addSelectionListener(matchListener);
    matchAllButton.addSelectionListener(matchListener);

    Composite limits = new Composite(composite, SWT.NONE);
    limits.setLayout(new GridLayout(3, false));
    limitButton = new Button(limits, SWT.CHECK);
    limitButton.setText("&Use limits");
    limitButton.setSelection(limitsEnabled);
    Label perGroup = new Label(limits, SWT.NONE);
    perGroup.setText("Items &per group:");
    limitText = new Text(limits, SWT.BORDER);
    limitText.setText(Integer.toString(limit));
    limitButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        updateEnablement();
      }
    });

    showAllButton.setSelection(configsTable.getCheckedElements().length == 0);
    select(configurations.isEmpty() ? null : configurations.get(0));
    if (!configurations.isEmpty())
      configsTable.setSelection(new com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection(
          configurations.get(0)));
    updateEnablement();
    return area;
  }

  private Button radio(Composite parent, String text, final int scopeValue) {
    Button b = new Button(parent, SWT.RADIO);
    b.setText(text);
    b.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        if (current != null && b.getSelection()) {
          current.setScope(scopeValue);
          if (scopeValue != MarkerFilterConfiguration.SCOPE_ANY)
            current.setScopePaths(selectionPaths);
        }
      }
    });
    return b;
  }

  private Button check(Composite parent, String text, java.util.function.Consumer<Boolean> onChange) {
    Button b = new Button(parent, SWT.CHECK);
    b.setText(text);
    b.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        onChange.accept(b.getSelection());
      }
    });
    return b;
  }

  private void severityChanged(int severity, boolean checked) {
    if (current != null)
      current.setSeverityChecked(severity, checked);
  }

  private void priorityChanged(int priority, boolean checked) {
    if (current != null)
      current.setPriorityChecked(priority, checked);
  }

  /** Shows the settings of the configuration in the areas (null: none selected). */
  private void select(MarkerFilterConfiguration c) {
    current = c;
    boolean enabled = c != null;
    if (c != null) {
      anyScope.setSelection(c.getScope() == MarkerFilterConfiguration.SCOPE_ANY);
      sameProjectScope.setSelection(c.getScope() == MarkerFilterConfiguration.SCOPE_SAME_PROJECT);
      selectedScope.setSelection(c.getScope() == MarkerFilterConfiguration.SCOPE_SELECTED);
      childrenScope.setSelection(c.getScope() == MarkerFilterConfiguration.SCOPE_SELECTED_AND_CHILDREN);
      textField.setText(c.getText());
      textCombo.select(c.isContainsText() ? 0 : 1);
      if (hasCompletion) {
        completedButton.setSelection(c.isCompleted());
        notCompletedButton.setSelection(c.isNotCompleted());
      }
      if (hasSeverity) {
        errorButton.setSelection(c.isSeverityChecked(IMarker.SEVERITY_ERROR));
        warningButton.setSelection(c.isSeverityChecked(IMarker.SEVERITY_WARNING));
        infoButton.setSelection(c.isSeverityChecked(IMarker.SEVERITY_INFO));
      }
      if (hasPriority) {
        highButton.setSelection(c.isPriorityChecked(IMarker.PRIORITY_HIGH));
        normalButton.setSelection(c.isPriorityChecked(IMarker.PRIORITY_NORMAL));
        lowButton.setSelection(c.isPriorityChecked(IMarker.PRIORITY_LOW));
      }
    }
    boolean hasSelection = !selectionPaths.isEmpty();
    sameProjectScope.setEnabled(enabled && hasSelection);
    selectedScope.setEnabled(enabled && hasSelection);
    childrenScope.setEnabled(enabled && hasSelection);
    anyScope.setEnabled(enabled);
    textField.setEnabled(enabled);
    if (hasSeverity) {
      errorButton.setEnabled(enabled);
      warningButton.setEnabled(enabled);
      infoButton.setEnabled(enabled);
    }
    textCombo.setEnabled(enabled);
    if (hasCompletion) {
      completedButton.setEnabled(enabled);
      notCompletedButton.setEnabled(enabled);
    }
    if (hasPriority) {
      highButton.setEnabled(enabled);
      normalButton.setEnabled(enabled);
      lowButton.setEnabled(enabled);
    }
    updateEnablement();
  }

  private void newConfiguration() {
    String name = "New Configuration";
    int n = 2;
    while (exists(name))
      name = "New Configuration " + n++;
    MarkerFilterConfiguration c = new MarkerFilterConfiguration(name);
    c.setEnabled(true);
    configurations.add(c);
    configsTable.refresh();
    configsTable.setChecked(c, true);
    showAllButton.setSelection(false);
    configsTable.setSelection(new com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection(c));
    select(c);
  }

  private void rename() {
    if (current == null)
      return;
    InputDialog d = new InputDialog(getShell(), "Configuration Name", "Enter a name for the configuration:",
        current.getName(), text -> {
          if (text == null || text.trim().isEmpty())
            return "Name must not be empty";
          for (MarkerFilterConfiguration c : configurations)
            if (c != current && c.getName().equals(text))
              return "A filter already exists with the name " + text;
          return null;
        });
    if (d.open() == IDialogConstants.OK_ID) {
      current.setName(d.getValue());
      configsTable.refresh();
    }
  }

  private boolean exists(String name) {
    for (MarkerFilterConfiguration c : configurations)
      if (c.getName().equals(name))
        return true;
    return false;
  }

  private void updateEnablement() {
    boolean showAll = showAllButton.getSelection();
    configsTable.getControl().setEnabled(!showAll);
    addButton.setEnabled(!showAll);
    removeButton.setEnabled(!showAll && current != null);
    renameButton.setEnabled(!showAll && current != null);
    matchAnyButton.setEnabled(!showAll);
    matchAllButton.setEnabled(!showAll);
    limitText.setEnabled(limitButton.getSelection());
  }

  @Override
  protected void buttonPressed(int buttonId) {
    if (buttonId == IDialogConstants.OK_ID) {
      if (!parseLimit()) {
        MessageDialog.openError(getShell(), "Filters", "Items per group must be a number.");
        return;
      }
      if (showAllButton.getSelection())
        for (MarkerFilterConfiguration c : configurations)
          c.setEnabled(false);
      limitsEnabled = limitButton.getSelection();
    }
    super.buttonPressed(buttonId);
  }

  private boolean parseLimit() {
    try {
      limit = Integer.parseInt(limitText.getText().trim());
      return limit >= 0;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  /** The configurations of the dialog (the view takes them after OK). */
  public List<MarkerFilterConfiguration> getConfigurations() {
    return new ArrayList<>(configurations);
  }

  public boolean isMatchAll() {
    return matchAll;
  }

  public boolean isLimitsEnabled() {
    return limitsEnabled;
  }

  public int getLimit() {
    return limit;
  }
}
