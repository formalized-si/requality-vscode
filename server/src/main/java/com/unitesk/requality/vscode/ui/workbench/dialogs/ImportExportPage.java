package com.unitesk.requality.vscode.ui.workbench.dialogs;

import java.util.Comparator;

import com.unitesk.requality.vscode.ui.jface.viewers.DoubleClickEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ViewerComparator;
import com.unitesk.requality.vscode.ui.jface.wizard.IWizard;
import com.unitesk.requality.vscode.ui.jface.wizard.IWizardPage;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardPage;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.ModifyEvent;
import com.unitesk.requality.vscode.ui.swt.events.ModifyListener;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.vscode.ui.workbench.IWizardCategory;
import com.unitesk.requality.vscode.ui.workbench.IWizardDescriptor;
import com.unitesk.requality.vscode.ui.workbench.IWizardRegistry;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchWizard;

/**
 * {@code org.eclipse.ui.internal.dialogs.ImportExportPage}: the page that selects the import or export wizard from a
 * categorized, filterable tree. The description of the selected wizard is the message of the page; Next shows the
 * pages of the selected wizard, and a double click on a wizard does the same.
 */
public abstract class ImportExportPage extends WizardPage {
  protected static final String DIALOG_SETTING_SECTION_NAME = "ImportExportPage."; //$NON-NLS-1$

  protected final IWorkbench workbench;
  protected final IStructuredSelection currentSelection;
  // tree viewer of wizard selections
  private TreeViewer treeViewer;
  // the wizard selected in the tree, created by getNextPage
  private IWizardDescriptor selectedWizard;
  private IWizard selectedWizardInstance;

  /**
   * {@code org.eclipse.ui.internal.dialogs.ImportExportPage.CategorizedWizardSelectionTree}: a filter text and the tree
   * of the categories and wizards.
   */
  protected static class CategorizedWizardSelectionTree {
    private static final int SIZING_LISTS_HEIGHT = 200;
    private final IWizardCategory wizardCategories;
    private final String message;
    private final WizardPatternFilter filter = new WizardPatternFilter();
    private TreeViewer viewer;

    protected CategorizedWizardSelectionTree(IWizardCategory categories, String msg) {
      this.wizardCategories = categories;
      this.message = msg;
    }

    /** Creates the message, the filter text and the tree viewer. */
    protected Composite createControl(Composite parent) {
      Composite outerContainer = new Composite(parent, SWT.NONE);
      outerContainer.setLayout(new GridLayout());
      outerContainer.setLayoutData(new GridData(GridData.FILL_BOTH));
      outerContainer.setFont(parent.getFont());

      Label messageLabel = new Label(outerContainer, SWT.NONE);
      if (message != null)
        messageLabel.setText(message);
      messageLabel.setFont(parent.getFont());

      Text filterText = new Text(outerContainer, SWT.SINGLE | SWT.BORDER);
      filterText.setMessage("type filter text");
      filterText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
      filterText.addModifyListener(new ModifyListener() {
        @Override
        public void modifyText(ModifyEvent e) {
          filter.setPattern(filterText.getText());
          viewer.refresh();
          if (filter.hasPattern())
            viewer.expandAll();
        }
      });

      viewer = new TreeViewer(outerContainer, SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
      viewer.getControl().setFont(parent.getFont());
      viewer.addFilter(filter);
      viewer.setContentProvider(new WizardContentProvider());
      viewer.setLabelProvider(new LabelProvider() {
        @Override
        public String getText(Object element) {
          if (element instanceof IWizardCategory c)
            return c.getLabel();
          if (element instanceof IWizardDescriptor w)
            return w.getLabel();
          return super.getText(element);
        }
      });
      viewer.setComparator(new ViewerComparator(
          (Comparator<Object>) (a, b) -> String.valueOf(labelOf(a)).compareToIgnoreCase(String.valueOf(labelOf(b)))));
      viewer.setInput(wizardCategories);
      // the categories are shown open: the wizards of a category are what the user chooses from
      if (wizardCategories != null)
        for (IWizardCategory c : wizardCategories.getCategories())
          viewer.setExpandedState(c, true);
      viewer.getControl().setLayoutData(new GridData(GridData.FILL_BOTH));
      layoutTopControl(viewer.getControl());
      return outerContainer;
    }

    private static String labelOf(Object o) {
      if (o instanceof IWizardCategory c)
        return c.getLabel();
      if (o instanceof IWizardDescriptor w)
        return w.getLabel();
      return "";
    }

    /** The categorized tree viewer. */
    protected TreeViewer getViewer() {
      return viewer;
    }

    private void layoutTopControl(com.unitesk.requality.vscode.ui.swt.widgets.Control control) {
      GridData data = new GridData(GridData.FILL_BOTH);
      data.heightHint = SIZING_LISTS_HEIGHT;
      control.setLayoutData(data);
    }
  }

  /**
   * Constructor for import/export wizard page.
   *
   * @param aWorkbench current workbench
   * @param currentSelection current selection
   */
  protected ImportExportPage(IWorkbench aWorkbench, IStructuredSelection currentSelection) {
    super("importExportPage"); //$NON-NLS-1$
    this.workbench = aWorkbench;
    this.currentSelection = currentSelection;
    setTitle("Select");
  }

  @Override
  public void createControl(Composite parent) {
    Composite outerContainer = new Composite(parent, SWT.NONE);
    outerContainer.setLayout(new GridLayout());
    outerContainer.setLayoutData(new GridData(GridData.FILL_BOTH));
    outerContainer.setFont(parent.getFont());
    createTreeViewer(outerContainer);
    updateMessage();
    setControl(outerContainer);
  }

  /** Creates the tree viewer from which a wizard is selected. */
  protected abstract Composite createTreeViewer(Composite parent);

  /** The registry of the wizards of this page. */
  protected abstract IWizardRegistry getWizardRegistry();

  /** Double click on a wizard shows its first page; on a category it expands or collapses it. */
  protected void treeDoubleClicked(DoubleClickEvent event) {
    ISelection selection = event.getViewer().getSelection();
    IStructuredSelection ss = (IStructuredSelection) selection;
    listSelectionChanged(ss);
    Object element = ss.getFirstElement();
    TreeViewer v = (TreeViewer) event.getViewer();
    if (v.isExpandable(element)) {
      v.setExpandedState(element, !v.getExpandedState(element));
    } else if (element instanceof IWizardDescriptor && canFlipToNextPage()) {
      getContainer().showPage(getNextPage());
    }
  }

  /** Updates the message of the page from the selected wizard element. */
  private void updateSelectedNode(IWizardDescriptor wizardElement) {
    setErrorMessage(null);
    if (wizardElement == null) {
      selectedWizard = null;
      updateMessage();
    } else {
      if (selectedWizard != wizardElement)
        selectedWizardInstance = null;
      selectedWizard = wizardElement;
      setMessage(wizardElement.getDescription());
    }
    // the Next button follows the selection
    if (isCurrentPage())
      getContainer().updateButtons();
  }

  /** Updates the message of the page from the selected wizard (or the choose message if none). */
  protected void updateMessage() {
    if (treeViewer != null) {
      Object sel = treeViewer.getStructuredSelection().getFirstElement();
      if (sel instanceof IWizardDescriptor)
        updateSelectedNode((IWizardDescriptor) sel);
      else
        selectedWizard = null;
    } else {
      setMessage(null);
    }
  }

  /** Method to call whenever the selection in the tree has changed. */
  protected void listSelectionChanged(ISelection selection) {
    setErrorMessage(null);
    IStructuredSelection ss = (IStructuredSelection) selection;
    Object sel = ss.getFirstElement();
    if (sel instanceof IWizardDescriptor w)
      updateSelectedNode(w);
    else
      updateSelectedNode(null);
  }

  @Override
  public boolean canFlipToNextPage() {
    return selectedWizard != null;
  }

  /**
   * The wizard of the selected wizard element, created with the workbench and the selection, whose first page is the
   * next page (the wizard's pages follow this page; the wizard is finished from the dialog).
   */
  @Override
  public IWizardPage getNextPage() {
    if (selectedWizard == null)
      return null;
    if (selectedWizardInstance == null) {
      try {
        IWizard wizard = selectedWizard.createWizard();
        if (wizard instanceof IWorkbenchWizard w)
          w.init(workbench, currentSelection);
        selectedWizardInstance = wizard;
      } catch (CoreException e) {
        setErrorMessage("Cannot create the wizard " + selectedWizard.getLabel() + ": " + e.getMessage());
        return null;
      }
    }
    IWizard wizard = selectedWizardInstance;
    if (getWizard() instanceof ImportExportWizard outer)
      outer.setSelectedWizard(wizard);
    wizard.setContainer(getContainer());
    wizard.addPages();
    return wizard.getStartingPage();
  }

  /** Saves the widget values of the page; there are none kept across sessions in this port. */
  public void saveWidgetValues() {
  }

  /** The tree viewer used for the selection of the wizard. */
  protected void setTreeViewer(TreeViewer viewer) {
    treeViewer = viewer;
  }

  /** The tree viewer used for the selection of the wizard. */
  protected TreeViewer getTreeViewer() {
    return treeViewer;
  }

  /** The root category of the registry of this page. */
  protected IWizardCategory getRootCategory() {
    return getWizardRegistry().getRootCategory();
  }

}
