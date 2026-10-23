package com.unitesk.requality.vscode.ui.ide.dialogs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.jface.dialogs.Dialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.ErrorDialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.dialogs.IMessageProvider;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.CheckStateChangedEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.ICheckStateListener;
import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.ITreeViewerListener;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeExpansionEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.CheckboxTreeViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ViewerComparator;
import com.unitesk.requality.vscode.ui.runtime.Adapters;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.IWorkspace;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.BusyIndicator;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.vscode.ui.swt.widgets.TreeItem;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSetManager;
import com.unitesk.requality.vscode.ui.workbench.Workbench;
import com.unitesk.requality.vscode.ui.workbench.dialogs.IWorkingSetPage;
import com.unitesk.requality.vscode.ui.workbench.dialogs.WorkingSetWizardPage;
import com.unitesk.requality.vscode.ui.workbench.model.WorkbenchContentProvider;
import com.unitesk.requality.vscode.ui.workbench.model.WorkbenchLabelProvider;
import com.unitesk.requality.vscode.ui.workbench.internal.WorkingSet;

/**
 * {@code org.eclipse.ui.internal.ide.dialogs.ResourceWorkingSetPage}: creates and edits a resource working set: a name and
 * the checked resources of the workspace tree (a checked container checks its members as they are expanded).
 * VSCODE: the grayed state is kept by the page (the compat CheckboxTreeViewer has no getter for it); the resource
 * tree is sorted by name.
 */
public class ResourceWorkingSetPage extends WorkingSetWizardPage implements IWorkingSetPage {
  private static final int SIZING_SELECTION_WIDGET_WIDTH = 50;
  private static final int SIZING_SELECTION_WIDGET_HEIGHT = 200;

  private Text text;
  private CheckboxTreeViewer tree;
  private IWorkingSet workingSet;
  private boolean firstCheck = true;
  /** The grayed resources of the tree (see the class comment). */
  private final Set<Object> grayed = new HashSet<>();

  /** Creates the page. */
  public ResourceWorkingSetPage() {
    super("resourceWorkingSetPage", "Resource Working Set", null);
    setDescription("Enter a working set name and select the working set resources.");
  }

  /** The horizontal dialog units in pixels (the formula of the compat Dialog, which has the font metrics). */
  private static int convertHorizontalDLUsToPixels(int dlus) {
    return (dlus * 6 + 2) / 4;
  }

  /** Adds the working set elements contained in the container to the list of checked resources. */
  private void addWorkingSetElements(List<IResource> collectedResources, IContainer container) {
    IPath containerPath = container.getFullPath();
    for (IAdaptable adaptable : workingSet.getElements()) {
      IResource resource = Adapters.adapt(adaptable, IResource.class);
      if (resource != null) {
        IPath resourcePath = resource.getFullPath();
        if (containerPath.isPrefixOf(resourcePath)) {
          collectedResources.add(resource);
        }
      }
    }
  }

  @Override
  public void createControl(Composite parent) {
    Composite composite = new Composite(parent, SWT.NULL);
    composite.setLayout(new GridLayout());
    composite.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));
    setControl(composite);

    Label label = new Label(composite, SWT.WRAP);
    label.setText("&Working set name:");
    label.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL
        | GridData.VERTICAL_ALIGN_CENTER));

    text = new Text(composite, SWT.SINGLE | SWT.BORDER);
    text.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL));
    text.addModifyListener(e -> validateInput());
    text.setFocus();

    label = new Label(composite, SWT.WRAP);
    label.setText("Working set &contents:");
    label.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL
        | GridData.VERTICAL_ALIGN_CENTER));

    tree = new CheckboxTreeViewer(composite);
    final ITreeContentProvider treeContentProvider = new WorkbenchContentProvider();
    tree.setContentProvider(treeContentProvider);
    tree.setLabelProvider(new WorkbenchLabelProvider());
    tree.setInput(ResourcesPlugin.getWorkspace().getRoot());
    tree.setComparator(new ViewerComparator());
    GridData data = new GridData(GridData.FILL_BOTH | GridData.GRAB_VERTICAL);
    data.heightHint = SIZING_SELECTION_WIDGET_HEIGHT;
    data.widthHint = SIZING_SELECTION_WIDGET_WIDTH;
    tree.getControl().setLayoutData(data);

    tree.addCheckStateListener(new ICheckStateListener() {
      @Override
      public void checkStateChanged(CheckStateChangedEvent event) {
        handleCheckStateChange(event);
      }
    });
    tree.addTreeListener(new ITreeViewerListener() {
      @Override
      public void treeCollapsed(TreeExpansionEvent event) {
      }

      @Override
      public void treeExpanded(TreeExpansionEvent event) {
        final Object element = event.getElement();
        if (!isGrayed(element)) {
          BusyIndicator.showWhile(getShell().getDisplay(),
              () -> setSubtreeChecked((IContainer) element, tree.getChecked(element), false));
        }
      }
    });

    // Select All / Deselect All buttons
    Composite buttonComposite = new Composite(composite, SWT.NONE);
    GridLayout layout = new GridLayout(2, false);
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    layout.horizontalSpacing = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_SPACING);
    buttonComposite.setLayout(layout);
    buttonComposite.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));

    Button selectAllButton = new Button(buttonComposite, SWT.PUSH);
    selectAllButton.setText("Select &All");
    selectAllButton.setToolTipText("Select all of theses resource for this working set.");
    selectAllButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent selectionEvent) {
        BusyIndicator.showWhile(getShell().getDisplay(), () -> {
          tree.setCheckedElements(treeContentProvider.getElements(tree.getInput()));
          setSubtreeChecked((IContainer) tree.getInput(), true, false);
        });
        validateInput();
      }
    });
    selectAllButton.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));

    Button deselectAllButton = new Button(buttonComposite, SWT.PUSH);
    deselectAllButton.setText("Dese&lect All");
    deselectAllButton.setToolTipText("Deselect all of these resources for this working set.");
    deselectAllButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent selectionEvent) {
        BusyIndicator.showWhile(getShell().getDisplay(), () -> {
          tree.setCheckedElements(treeContentProvider.getElements(tree.getInput()));
          setSubtreeChecked((IContainer) tree.getInput(), false, false);
        });
        validateInput();
      }
    });
    deselectAllButton.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));

    initializeCheckedState();
    if (workingSet != null) {
      text.setText(workingSet.getName());
    }
    validateInput();
  }

  /** Collects the checked resources of the container. */
  private void findCheckedResources(List<IResource> checkedResources, IContainer container) {
    IResource[] resources = null;
    try {
      resources = container.members();
    } catch (CoreException ex) {
      handleCoreException(ex, getShell(), "Error", "Error during update of checked state");
    }
    if (resources == null) {
      return;
    }
    for (IResource resource : resources) {
      if (isGrayed(resource)) {
        if (resource.isAccessible()) {
          findCheckedResources(checkedResources, (IContainer) resource);
        } else {
          addWorkingSetElements(checkedResources, (IContainer) resource);
        }
      } else if (tree.getChecked(resource)) {
        checkedResources.add(resource);
      }
    }
  }

  @Override
  public void finish() {
    ArrayList<IResource> resources = new ArrayList<>(10);
    findCheckedResources(resources, (IContainer) tree.getInput());
    IAdaptable[] elements = resources.toArray(new IAdaptable[resources.size()]);
    if (workingSet == null) {
      IWorkingSetManager workingSetManager = Workbench.INSTANCE.getWorkingSetManager();
      workingSet = workingSetManager.createWorkingSet(getWorkingSetName(), elements);
    } else {
      workingSet.setName(getWorkingSetName());
      workingSet.setElements(elements);
    }
  }

  @Override
  public IWorkingSet getSelection() {
    return workingSet;
  }

  private String getWorkingSetName() {
    return text.getText();
  }

  private void handleCheckStateChange(final CheckStateChangedEvent event) {
    BusyIndicator.showWhile(getShell().getDisplay(), () -> {
      IResource resource = (IResource) event.getElement();
      boolean state = event.getChecked();
      setGrayed(resource, false);
      if (resource instanceof IContainer) {
        setSubtreeChecked((IContainer) resource, state, true);
      }
      updateParentState(resource);
      validateInput();
    });
  }

  private void handleCoreException(CoreException exception, com.unitesk.requality.vscode.ui.swt.widgets.Shell shell,
      String title, String message) {
    IStatus status = exception.getStatus();
    if (status != null) {
      ErrorDialog.openError(shell, title, message, status);
    } else {
      MessageDialog.openError(shell, "Internal Error", exception.getLocalizedMessage());
    }
  }

  /** Sets the checked state of the tree items from the working set, or from the selection when there is none. */
  private void initializeCheckedState() {
    BusyIndicator.showWhile(getShell().getDisplay(), () -> {
      Object[] items = null;
      if (workingSet == null) {
        com.unitesk.requality.vscode.ui.jface.viewers.ISelection selection = Workbench.currentSelection();
        if (selection instanceof com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection) {
          items = ((com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection) selection).toArray();
        }
      } else {
        items = workingSet.getElements();
      }
      if (items == null) {
        return;
      }
      tree.setCheckedElements(items);
      for (Object i : items) {
        if (!(i instanceof IAdaptable)) {
          continue;
        }
        IAdaptable item = (IAdaptable) i;
        IContainer container = Adapters.adapt(item, IContainer.class);
        if (container != null) {
          setSubtreeChecked(container, true, true);
        }
        IResource resource = Adapters.adapt(item, IResource.class);
        if (resource != null && !resource.isAccessible()) {
          IProject project = resource.getProject();
          if (!tree.getChecked(project)) {
            setGrayChecked(project, true);
          }
        } else {
          updateParentState(resource);
        }
      }
    });
  }

  @Override
  public void setSelection(IWorkingSet workingSet) {
    if (workingSet == null) {
      throw new IllegalArgumentException("Working set must not be null");
    }
    this.workingSet = workingSet;
    if (getShell() != null && text != null) {
      firstCheck = false;
      text.setText(workingSet.getName());
      initializeCheckedState();
      validateInput();
    }
  }

  /**
   * Sets the checked state of the container's members.
   *
   * @param container          the container whose children are checked or unchecked
   * @param state              true: check all members; false: uncheck them
   * @param checkExpandedState true: recurse into the sub-containers; false: only the members of this container
   */
  private void setSubtreeChecked(IContainer container, boolean state, boolean checkExpandedState) {
    // checked state is set lazily on expand, don't set it if container is collapsed
    if (!container.isAccessible() || (!tree.getExpandedState(container) && state && checkExpandedState)) {
      return;
    }
    IResource[] members = null;
    try {
      members = container.members();
    } catch (CoreException ex) {
      handleCoreException(ex, getShell(), "Error", "Error during update of checked state");
    }
    if (members == null) {
      return;
    }
    for (int i = members.length - 1; i >= 0; i--) {
      IResource element = members[i];
      boolean elementGrayChecked = isGrayed(element) || tree.getChecked(element);
      if (state) {
        tree.setChecked(element, true);
        setGrayed(element, false);
      } else {
        setGrayChecked(element, false);
      }
      // unchecked state only needs to be set when the container is checked or grayed
      if (element instanceof IContainer && (state || elementGrayChecked)) {
        setSubtreeChecked((IContainer) element, state, true);
      }
    }
  }

  /** Checks and grays the parent of the resource if some of its members are checked. */
  private void updateParentState(IResource child) {
    if (child == null || child.getParent() == null) {
      return;
    }
    IContainer parent = child.getParent();
    boolean childChecked = false;
    IResource[] members = null;
    try {
      members = parent.members();
    } catch (CoreException ex) {
      handleCoreException(ex, getShell(), "Error", "Error during update of checked state");
    }
    if (members != null) {
      for (int i = members.length - 1; i >= 0; i--) {
        if (tree.getChecked(members[i]) || isGrayed(members[i])) {
          childChecked = true;
          break;
        }
      }
    }
    setGrayChecked(parent, childChecked);
    updateParentState(parent);
  }

  /** Validates the working set name and the checked state of the resource tree. */
  private void validateInput() {
    String errorMessage = null;
    String infoMessage = null;
    String newText = text.getText();
    if (!newText.equals(newText.trim())) {
      errorMessage = "The name must not have a leading or trailing whitespace.";
    }
    if (newText.isEmpty()) {
      if (firstCheck) {
        setPageComplete(false);
        firstCheck = false;
        return;
      }
      errorMessage = "The name must not be empty.";
    }
    firstCheck = false;
    if (errorMessage == null && (workingSet == null || !newText.equals(workingSet.getName()))) {
      for (IWorkingSet set : Workbench.INSTANCE.getWorkingSetManager().getWorkingSets()) {
        if (newText.equals(set.getName())) {
          errorMessage = "A working set with the same name already exists.";
        }
      }
    }
    if (infoMessage == null && tree.getCheckedElements().length == 0) {
      infoMessage = "No resources selected.";
    }
    setMessage(infoMessage, IMessageProvider.INFORMATION);
    setErrorMessage(errorMessage);
    setPageComplete(errorMessage == null);
  }

  private boolean isGrayed(Object element) {
    return grayed.contains(element);
  }

  private void setGrayed(Object element, boolean state) {
    tree.setGrayed(element, state);
    if (state) {
      grayed.add(element);
    } else {
      grayed.remove(element);
    }
  }

  private void setGrayChecked(Object element, boolean state) {
    tree.setGrayChecked(element, state);
    if (state) {
      grayed.add(element);
    } else {
      grayed.remove(element);
    }
  }
}
