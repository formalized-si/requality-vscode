package com.unitesk.requality.vscode.ui.workbench.dialogs;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.IWorkspaceRoot;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Event;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Listener;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.vscode.ui.swt.widgets.Tree;

/**
 * {@code org.eclipse.ui.dialogs.ResourceAndContainerGroup}: the group of a wizard page that asks for the parent
 * container of a resource (the "Enter or select the parent folder:" text with the tree of the workspace containers)
 * and for the name of the resource. The page is notified of the changes of both through its listener.
 */
public class ResourceAndContainerGroup {
  private static final String CONTAINER_MESSAGE = "Enter or select the parent folder:";
  private static final String SELECT_FOLDER = "Select the folder:";

  private final Listener listener;
  private final String resourceFieldLabel;
  private final String resourceLabel;
  private final boolean allowNewContainerName;
  private Text containerField;
  private Text resourceField;
  private TreeViewer containerTree;
  private boolean allowExistingResources = true;

  /**
   * @param parent the composite of the group
   * @param listener notified of the changes of the container and the resource fields
   * @param resourceFieldLabel the label of the resource name field, such as "Folder name:"
   * @param resourceLabel the name of the kind of resource in the messages, such as "folder"
   * @param allowNewContainerName true if the container may be a new (missing) folder
   * @param heightHint the height of the container tree
   */
  public ResourceAndContainerGroup(Composite parent, Listener listener, String resourceFieldLabel,
      String resourceLabel, boolean allowNewContainerName, int heightHint) {
    this.listener = listener;
    this.resourceFieldLabel = resourceFieldLabel;
    this.resourceLabel = resourceLabel;
    this.allowNewContainerName = allowNewContainerName;

    Composite group = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout();
    layout.numColumns = 1;
    group.setLayout(layout);
    group.setLayoutData(new GridData(GridData.FILL_BOTH));

    Label label = new Label(group, SWT.NONE);
    label.setText(CONTAINER_MESSAGE);
    containerField = new Text(group, SWT.BORDER | SWT.SINGLE);
    containerField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    containerField.addListener(SWT.Modify, this::fire);

    Label selectLabel = new Label(group, SWT.NONE);
    selectLabel.setText(SELECT_FOLDER);
    containerTree = new TreeViewer(new Tree(group, SWT.BORDER | SWT.SINGLE));
    GridData treeData = new GridData(GridData.FILL_BOTH);
    treeData.heightHint = heightHint;
    containerTree.getTree().setLayoutData(treeData);
    containerTree.setContentProvider(new ContainerContentProvider());
    containerTree.setLabelProvider(new LabelProvider() {
      @Override
      public String getText(Object element) {
        return ((IResource) element).getName();
      }
    });
    containerTree.setInput(ResourcesPlugin.getWorkspace().getRoot());
    containerTree.addSelectionChangedListener(event -> {
      ISelection selection = event.getSelection();
      if (selection instanceof IStructuredSelection && !selection.isEmpty()) {
        Object o = ((IStructuredSelection) selection).getFirstElement();
        if (o instanceof IResource)
          containerField.setText(((IResource) o).getFullPath().toPortableString());
      }
    });

    Label nameLabel = new Label(group, SWT.NONE);
    nameLabel.setText(resourceFieldLabel);
    resourceField = new Text(group, SWT.BORDER | SWT.SINGLE);
    resourceField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    resourceField.addListener(SWT.Modify, this::fire);
  }

  private void fire(Event event) {
    if (listener != null)
      listener.handleEvent(event);
  }

  /** Whether an existing resource with the name may be used (else the name must be a new one). */
  public void setAllowExistingResources(boolean allow) {
    allowExistingResources = allow;
  }

  public boolean isAllowExistingResources() {
    return allowExistingResources;
  }

  /** The name of the resource as typed by the user. */
  public String getResource() {
    return resourceField.getText();
  }

  public void setResource(String name) {
    resourceField.setText(name);
  }

  /** The full path of the parent container, or null when the container field is not a container of the workspace. */
  public IPath getContainerFullPath() {
    String text = containerField.getText().trim();
    if (text.isEmpty())
      return null;
    return new Path(text).makeAbsolute();
  }

  public void setContainerFullPath(IPath path) {
    containerField.setText(path == null ? "" : path.toPortableString());
  }

  /** The container of the path, or null if the path is not a container of the workspace. */
  public IContainer getContainer() {
    IPath path = getContainerFullPath();
    if (path == null)
      return null;
    IResource r = ResourcesPlugin.getWorkspace().getRoot().findMember(path);
    return r instanceof IContainer ? (IContainer) r : null;
  }

  /**
   * The message of the container field, or null if it names a container of the workspace (or a missing one when new
   * containers are allowed).
   */
  public String validateContainer(String missingMessage, String notContainerMessage) {
    IPath path = getContainerFullPath();
    if (path == null)
      return missingMessage;
    IResource r = ResourcesPlugin.getWorkspace().getRoot().findMember(path);
    if (r == null)
      return allowNewContainerName ? null : missingMessage;
    return r instanceof IContainer ? null : notContainerMessage;
  }

  /** The message of the resource name, or null if it is valid for the container. */
  public String validateResource(String emptyMessage, String invalidMessage, String existsMessage) {
    String name = getResource();
    if (name == null || name.trim().isEmpty())
      return emptyMessage;
    IStatus status = ResourcesPlugin.getWorkspace().validateName(name, IResource.FOLDER);
    if (!status.isOK())
      return invalidMessage;
    IContainer container = getContainer();
    if (container != null) {
      IResource existing = container.findMember(name);
      if (existing != null && !allowExistingResources)
        return existsMessage;
    }
    return null;
  }

  public String getResourceFieldLabel() {
    return resourceFieldLabel;
  }

  public String getResourceLabel() {
    return resourceLabel;
  }

  /** The containers of the workspace: its projects, and the folders of the open projects. */
  private static final class ContainerContentProvider implements ITreeContentProvider {
    @Override
    public Object[] getElements(Object inputElement) {
      return getChildren(inputElement);
    }

    @Override
    public Object[] getChildren(Object parentElement) {
      List<Object> r = new ArrayList<>();
      if (parentElement instanceof IWorkspaceRoot) {
        for (Object p : ((IWorkspaceRoot) parentElement).getProjects())
          if (((IResource) p).exists())
            r.add(p);
      } else if (parentElement instanceof IContainer) {
        try {
          for (IResource m : ((IContainer) parentElement).members())
            if (m instanceof IContainer)
              r.add(m);
        } catch (Exception e) {
          // a container that cannot be listed has no children
        }
      }
      return r.toArray();
    }

    @Override
    public Object getParent(Object element) {
      return element instanceof IResource ? ((IResource) element).getParent() : null;
    }

    @Override
    public boolean hasChildren(Object element) {
      return getChildren(element).length > 0;
    }
  }
}
