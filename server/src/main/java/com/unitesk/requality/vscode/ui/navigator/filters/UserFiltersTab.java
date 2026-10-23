package com.unitesk.requality.vscode.ui.navigator.filters;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.viewers.ArrayContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.CellEditor;
import com.unitesk.requality.vscode.ui.jface.viewers.CheckboxTableViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ColumnLabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.ColumnWeightData;
import com.unitesk.requality.vscode.ui.jface.viewers.EditingSupport;
import com.unitesk.requality.vscode.ui.jface.viewers.ICheckStateProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.TableLayout;
import com.unitesk.requality.vscode.ui.jface.viewers.TableViewerColumn;
import com.unitesk.requality.vscode.ui.jface.viewers.TextCellEditor;
import com.unitesk.requality.vscode.ui.navigator.CommonViewer;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;

/**
 * {@code org.eclipse.ui.internal.navigator.filters.UserFiltersTab}: "User filters": name patterns of resources to
 * hide, each enabled or not; New, Edit and Remove edit the list (in place in the table).
 */
public class UserFiltersTab extends CustomizationTab {
  /** The data key of the user filters of a viewer (NavigatorPlugin.RESOURCE_REGEXP_FILTER_DATA). */
  public static final String RESOURCE_REGEXP_FILTER_DATA = "org.eclipse.ui.navigator.resources.filters.regexp";

  private static class UserFilterRegexpEditingSupport extends EditingSupport {
    private boolean enableEdition = false;

    UserFilterRegexpEditingSupport(CheckboxTableViewer viewer) {
      super(viewer);
    }

    void setEditionEnabled(boolean enableEdition) {
      this.enableEdition = enableEdition;
    }

    @Override
    public CheckboxTableViewer getViewer() {
      return (CheckboxTableViewer) super.getViewer();
    }

    @Override
    protected void setValue(Object element, Object value) {
      ((UserFilter) element).setRegexp((String) value);
      getViewer().update(element, null);
    }

    @Override
    protected Object getValue(Object element) {
      return ((UserFilter) element).getRegexp();
    }

    @Override
    protected CellEditor getCellEditor(Object element) {
      return new TextCellEditor(getViewer().getTable());
    }

    @Override
    protected boolean canEdit(Object element) {
      return enableEdition;
    }
  }

  private final ArrayList<UserFilter> userFiltersWorkingCopy;

  @SuppressWarnings("unchecked")
  public UserFiltersTab(Composite parent, CommonViewer commonViewer) {
    super(parent, commonViewer.getNavigatorContentService());
    Object data = commonViewer.getData(RESOURCE_REGEXP_FILTER_DATA);
    userFiltersWorkingCopy = new ArrayList<>();
    if (data != null)
      for (UserFilter f : (Collection<UserFilter>) data)
        userFiltersWorkingCopy.add(new UserFilter(f.getRegexp(), f.isEnabled()));
    createControl();
  }

  private void createControl() {
    setLayout(new GridLayout(2, false));
    Label label = new Label(this, SWT.WRAP);
    label.setText("Create custom resource filters to apply. Resources with names that match a selected pattern will be hidden.");
    label.setLayoutData(new GridData(SWT.FILL, SWT.DEFAULT, true, false, 2, 1));
    final CheckboxTableViewer filtersViewer = CheckboxTableViewer.newCheckList(this, SWT.BORDER | SWT.CHECK);
    filtersViewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    TableLayout tableLayout = new TableLayout();
    tableLayout.addColumnData(new ColumnWeightData(200));
    filtersViewer.getTable().setLayout(tableLayout);
    filtersViewer.setContentProvider(ArrayContentProvider.getInstance());
    TableViewerColumn column = new TableViewerColumn(filtersViewer, SWT.FULL_SELECTION, 0);
    column.setLabelProvider(new ColumnLabelProvider() {
      @Override
      public String getText(Object o) {
        return ((UserFilter) o).getRegexp();
      }
    });
    final UserFilterRegexpEditingSupport editingSupport = new UserFilterRegexpEditingSupport(filtersViewer);
    column.setEditingSupport(editingSupport);
    filtersViewer.setCheckStateProvider(new ICheckStateProvider() {
      @Override
      public boolean isGrayed(Object element) {
        return false;
      }

      @Override
      public boolean isChecked(Object element) {
        return ((UserFilter) element).isEnabled();
      }
    });
    filtersViewer.addCheckStateListener(event -> ((UserFilter) event.getElement()).setEnabled(event.getChecked()));
    filtersViewer.setInput(userFiltersWorkingCopy);

    Composite buttonsComposite = new Composite(this, SWT.NONE);
    buttonsComposite.setLayoutData(new GridData(SWT.DEFAULT, SWT.TOP, false, false));
    GridLayout layout = new GridLayout(1, false);
    layout.marginHeight = 0;
    buttonsComposite.setLayout(layout);

    final Button addButton = new Button(buttonsComposite, SWT.PUSH);
    addButton.setLayoutData(new GridData(SWT.FILL, SWT.DEFAULT, false, false));
    addButton.setText("&New");
    addButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent event) {
        UserFilter newFilter = new UserFilter();
        userFiltersWorkingCopy.add(newFilter);
        filtersViewer.refresh();
        filtersViewer.setSelection(new StructuredSelection(newFilter));
        editingSupport.setEditionEnabled(true);
        filtersViewer.editElement(newFilter, 0);
        editingSupport.setEditionEnabled(false);
      }
    });
    final Button editButton = new Button(buttonsComposite, SWT.PUSH);
    editButton.setLayoutData(new GridData(SWT.FILL, SWT.DEFAULT, false, false));
    editButton.setText("&Edit");
    editButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent event) {
        editingSupport.setEditionEnabled(true);
        filtersViewer.editElement(((IStructuredSelection) filtersViewer.getSelection()).getFirstElement(), 0);
        editingSupport.setEditionEnabled(false);
      }
    });
    final Button removeButton = new Button(buttonsComposite, SWT.PUSH);
    removeButton.setLayoutData(new GridData(SWT.FILL, SWT.DEFAULT, false, false));
    removeButton.setText("&Remove");
    removeButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent event) {
        UserFilter filter = (UserFilter) ((IStructuredSelection) filtersViewer.getSelection()).getFirstElement();
        userFiltersWorkingCopy.remove(filter);
        filtersViewer.refresh();
      }
    });
    filtersViewer.addSelectionChangedListener(event -> {
      editButton.setEnabled(!event.getSelection().isEmpty());
      removeButton.setEnabled(!event.getSelection().isEmpty());
    });
    filtersViewer.setSelection(new StructuredSelection());
    editButton.setEnabled(false);
    removeButton.setEnabled(false);
  }

  public List<UserFilter> getUserFilters() {
    return userFiltersWorkingCopy;
  }
}
