package com.unitesk.requality.vscode.ui.workbench.dialogs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.IDoubleClickListener;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionChangedListener;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.SelectionChangedEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ViewerFilter;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.vscode.ui.workbench.IWizardCategory;
import com.unitesk.requality.vscode.ui.workbench.IWizardDescriptor;

/**
 * {@code org.eclipse.ui.internal.dialogs.NewWizardNewPage}: the tree of the new wizard categories and wizards, with a
 * filter text ({@code Wizards:}) that keeps the wizards whose name matches and their categories, and the description
 * of the selected wizard. A double click on a wizard advances to its pages.
 */
class NewWizardNewPage implements ISelectionChangedListener {
  private static final int SIZING_LISTS_HEIGHT = 200;
  private static final int SIZING_VIEWER_WIDTH = 300;
  private static final String PROJECT_TAG = "project";

  private final NewWizardSelectionPage page;
  private final IWizardCategory wizardCategories;
  private final boolean projectsOnly;
  private TreeViewer treeViewer;
  private Text filterText;
  private Label descriptionLabel;
  private final ViewerFilter wizardFilter = new ViewerFilter() {
    @Override
    public boolean select(Viewer viewer, Object parentElement, Object element) {
      return matches(element);
    }
  };

  NewWizardNewPage(NewWizardSelectionPage mainPage, IWizardCategory wizardCategories, boolean projectsOnly) {
    this.page = mainPage;
    this.wizardCategories = wizardCategories;
    this.projectsOnly = projectsOnly;
  }

  /** The control of the page: the filter, the tree and the description of the selected wizard. */
  Control createControl(Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);
    composite.setLayout(new GridLayout());
    composite.setLayoutData(new GridData(GridData.FILL_BOTH));

    Label label = new Label(composite, SWT.NONE);
    label.setText("Wizards:");
    filterText = new Text(composite, SWT.SINGLE | SWT.BORDER | SWT.SEARCH);
    filterText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    filterText.addListener(SWT.Modify, event -> {
      if (treeViewer != null) {
        treeViewer.refresh();
        treeViewer.expandAll();
      }
    });

    treeViewer = new TreeViewer(composite, SWT.BORDER | SWT.SINGLE);
    GridData data = new GridData(GridData.FILL_BOTH);
    data.heightHint = SIZING_LISTS_HEIGHT;
    data.widthHint = SIZING_VIEWER_WIDTH;
    treeViewer.getTree().setLayoutData(data);
    treeViewer.setContentProvider(new WizardContentProvider());
    treeViewer.setLabelProvider(new LabelProvider() {
      @Override
      public String getText(Object element) {
        return element instanceof IWizardCategory ? ((IWizardCategory) element).getName()
            : ((IWizardDescriptor) element).getLabel();
      }
    });
    treeViewer.addFilter(wizardFilter);
    treeViewer.setInput(wizardCategories);
    treeViewer.expandAll();
    treeViewer.addSelectionChangedListener(this);
    treeViewer.addDoubleClickListener(event -> {
      if (page.getSelectedWizard() != null)
        page.advanceToNextPageOrFinish();
    });

    descriptionLabel = new Label(composite, SWT.WRAP);
    descriptionLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    return composite;
  }

  /** The filter of the wizards: the project wizards only for projects, and the name must match the filter text. */
  private boolean matches(Object element) {
    if (element instanceof IWizardDescriptor) {
      IWizardDescriptor d = (IWizardDescriptor) element;
      if (projectsOnly && !Arrays.asList(d.getTags()).contains(PROJECT_TAG))
        return false;
      return containsFilter(d.getLabel());
    }
    if (element instanceof IWizardCategory) {
      IWizardCategory c = (IWizardCategory) element;
      for (IWizardCategory s : c.getCategories())
        if (matches(s))
          return true;
      for (IWizardDescriptor d : c.getWizards())
        if (matches(d))
          return true;
      return false;
    }
    return true;
  }

  private boolean containsFilter(String name) {
    String filter = filterText == null ? "" : filterText.getText().trim();
    return filter.isEmpty() || name != null && name.toLowerCase().contains(filter.toLowerCase());
  }

  @Override
  public void selectionChanged(SelectionChangedEvent selectionEvent) {
    Object selectedObject = getSingleSelection(selectionEvent.getSelection());
    IWizardDescriptor descriptor = selectedObject instanceof IWizardDescriptor ? (IWizardDescriptor) selectedObject
        : null;
    updateDescription(descriptor);
    page.updateWizardSelection(descriptor);
  }

  private static Object getSingleSelection(ISelection selection) {
    if (selection instanceof IStructuredSelection && !selection.isEmpty())
      return ((IStructuredSelection) selection).getFirstElement();
    return null;
  }

  private void updateDescription(IWizardDescriptor selectedObject) {
    String text = selectedObject == null || selectedObject.getDescription() == null ? "" : selectedObject.getDescription();
    descriptionLabel.setText(text);
  }

  /** The children of the categories: the subcategories, then the wizards of the category. */
  private static final class WizardContentProvider implements ITreeContentProvider {
    @Override
    public Object[] getElements(Object inputElement) {
      return getChildren(inputElement);
    }

    @Override
    public Object[] getChildren(Object parentElement) {
      if (!(parentElement instanceof IWizardCategory))
        return new Object[0];
      IWizardCategory c = (IWizardCategory) parentElement;
      List<Object> r = new ArrayList<>(Arrays.asList(c.getCategories()));
      r.addAll(Arrays.asList(c.getWizards()));
      return r.toArray();
    }

    @Override
    public Object getParent(Object element) {
      return element instanceof IWizardCategory ? ((IWizardCategory) element).getParent() : null;
    }

    @Override
    public boolean hasChildren(Object element) {
      return element instanceof IWizardCategory
          && (((IWizardCategory) element).getCategories().length > 0 || ((IWizardCategory) element).getWizards().length > 0);
    }
  }
}
