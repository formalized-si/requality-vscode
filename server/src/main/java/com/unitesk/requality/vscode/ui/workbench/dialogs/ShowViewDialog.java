package com.unitesk.requality.vscode.ui.workbench.dialogs;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.viewers.DoubleClickEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.IDoubleClickListener;
import com.unitesk.requality.vscode.ui.jface.viewers.ILabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelectionChangedListener;
import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.SelectionChangedEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ViewerComparator;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.views.IViewCategory;
import com.unitesk.requality.vscode.ui.workbench.views.IViewDescriptor;
import com.unitesk.requality.vscode.ui.workbench.views.IViewRegistry;

/**
 * {@code org.eclipse.ui.internal.dialogs.ShowViewDialog} (Window &gt; Show View &gt; Other...): the views of the registry in
 * their categories (a filtered tree; the "General" category first, then the labels in alphabetical order), with the
 * description of the selected view below the tree. Open, or a double click on a view, selects the views; several views
 * can be selected. A double click on a category expands or collapses it.
 *
 * <p>VSCODE: the F2 pop-up of the description is not shown; the description is always below the tree.
 */
public class ShowViewDialog extends Dialog implements ISelectionChangedListener, IDoubleClickListener {
  private static final int LIST_HEIGHT = 300;
  private static final int LIST_WIDTH = 250;

  private final IViewRegistry registry;
  private FilteredTree filteredTree;
  private Label descriptionHint;
  private Button okButton;
  private IViewDescriptor[] viewDescs = new IViewDescriptor[0];

  public ShowViewDialog(Shell parentShell, IViewRegistry registry) {
    super(parentShell);
    this.registry = registry;
  }

  @Override
  protected void configureShell(Shell shell) {
    super.configureShell(shell);
    shell.setText("Show View");
  }

  @Override
  protected void createButtonsForButtonBar(Composite parent) {
    okButton = createButton(parent, IDialogConstants.OK_ID, "Open", true);
    createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    updateButtons();
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite composite = (Composite) super.createDialogArea(parent);
    filteredTree = new FilteredTree(composite, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER,
        new PatternFilter(), true);
    GridData spec = new GridData(GridData.FILL_BOTH);
    spec.widthHint = LIST_WIDTH;
    spec.heightHint = LIST_HEIGHT;
    filteredTree.setLayoutData(spec);
    TreeViewer viewer = filteredTree.getViewer();
    viewer.setLabelProvider(new ViewLabelProvider());
    viewer.setContentProvider(new ViewContentProvider());
    viewer.setComparator(new ViewComparator());
    viewer.setInput(registry);
    viewer.addSelectionChangedListener(this);
    viewer.addDoubleClickListener(this);
    descriptionHint = new Label(composite, SWT.WRAP);
    descriptionHint.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    return composite;
  }

  /** The views chosen (the selected views of the tree, in the order of the selection); empty if cancelled. */
  public IViewDescriptor[] getSelection() {
    return viewDescs;
  }

  @Override
  public void selectionChanged(SelectionChangedEvent event) {
    List<IViewDescriptor> l = new ArrayList<>();
    for (Object o : event.getStructuredSelection())
      if (o instanceof IViewDescriptor)
        l.add((IViewDescriptor) o);
    viewDescs = l.toArray(new IViewDescriptor[0]);
    updateButtons();
    String description = viewDescs.length == 1 ? viewDescs[0].getDescription() : "";
    descriptionHint.setText(description == null ? "" : description);
  }

  @Override
  public void doubleClick(DoubleClickEvent event) {
    TreeViewer viewer = filteredTree.getViewer();
    Object element = ((IStructuredSelection) event.getSelection()).getFirstElement();
    if (element instanceof IViewCategory) {
      viewer.setExpandedState(element, !viewer.getExpandedState(element));
    } else if (viewDescs.length > 0) {
      okPressed();
    }
  }

  private void updateButtons() {
    if (okButton != null)
      okButton.setEnabled(viewDescs.length > 0);
  }

  @Override
  protected void cancelPressed() {
    viewDescs = new IViewDescriptor[0];
    super.cancelPressed();
  }

  @Override
  protected void okPressed() {
    if (viewDescs.length == 0)
      return;
    super.okPressed();
  }

  /** {@code ViewContentProvider}: the categories with views and the views outside a known category, then the views. */
  private final class ViewContentProvider implements ITreeContentProvider {
    @Override
    public Object[] getElements(Object inputElement) {
      List<Object> r = new ArrayList<>();
      List<String> categoryIds = new ArrayList<>();
      for (IViewCategory c : registry.getCategories()) {
        categoryIds.add(c.getId());
        if (c.getViews().length > 0)
          r.add(c);
      }
      for (IViewDescriptor v : registry.getViews()) {
        String[] path = v.getCategoryPath();
        if (path == null || path.length == 0 || !categoryIds.contains(path[0]))
          r.add(v);
      }
      return r.toArray();
    }

    @Override
    public Object[] getChildren(Object parentElement) {
      return parentElement instanceof IViewCategory ? ((IViewCategory) parentElement).getViews() : new Object[0];
    }

    @Override
    public Object getParent(Object element) {
      return null;
    }

    @Override
    public boolean hasChildren(Object element) {
      return element instanceof IViewCategory;
    }

    @Override
    public void dispose() {
    }

    @Override
    public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
    }
  }

  /** {@code ViewLabelProvider}: the names of the categories and the views. */
  private static final class ViewLabelProvider extends LabelProvider implements ILabelProvider {
    @Override
    public String getText(Object element) {
      if (element instanceof IViewCategory)
        return ((IViewCategory) element).getLabel();
      if (element instanceof IViewDescriptor)
        return ((IViewDescriptor) element).getLabel();
      return String.valueOf(element);
    }
  }

  /** {@code ViewComparator}: "General" first, then the labels in alphabetical order. */
  private static final class ViewComparator extends com.unitesk.requality.vscode.ui.jface.viewers.ViewerComparator {
    private final Collator collator = Collator.getInstance();

    @Override
    public int compare(Viewer viewer, Object e1, Object e2) {
      if (e1 == e2)
        return 0;
      if (isGeneral(e1))
        return -1;
      if (isGeneral(e2))
        return 1;
      return collator.compare(label(e1), label(e2));
    }

    private static boolean isGeneral(Object e) {
      return e instanceof IViewCategory && "General".equals(((IViewCategory) e).getLabel());
    }

    private static String label(Object e) {
      if (e instanceof IViewCategory)
        return ((IViewCategory) e).getLabel();
      if (e instanceof IViewDescriptor)
        return ((IViewDescriptor) e).getLabel();
      return String.valueOf(e);
    }
  }
}
