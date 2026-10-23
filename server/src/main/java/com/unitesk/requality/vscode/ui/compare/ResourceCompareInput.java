package com.unitesk.requality.vscode.ui.compare;

import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.DiffNode;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.DiffTreeViewer;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.Differencer;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.IDiffContainer;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.IDiffElement;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.IStructureComparator;
import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.action.IMenuManager;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.jface.window.Window;
import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.mapping.ResourceMapping;
import com.unitesk.requality.vscode.ui.resources.mapping.ResourceMappingContext;
import com.unitesk.requality.vscode.ui.resources.mapping.ResourceTraversal;
import com.unitesk.requality.vscode.ui.runtime.Adapters;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.NullProgressMonitor;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.compare.internal.ResourceCompareInput}: the compare editor input of two or three resources. Files
 * are compared with their contents; folders with their members as a structure (the tree of differences). The resources
 * are compared through buffers, which are written to the files when the input is saved.
 */
class ResourceCompareInput extends CompareEditorInput {
  private static final boolean NORMALIZE_CASE = true;

  private boolean fThreeWay = false;
  private Action fOpenAction;
  private Object fRoot;
  private IStructureComparator fAncestor;
  private IStructureComparator fLeft;
  private IStructureComparator fRight;
  private IResource fAncestorResource;
  private IResource fLeftResource;
  private IResource fRightResource;
  private DiffTreeViewer fDiffViewer;

  /** {@code ResourceCompareInput.MyDiffNode}: a difference node whose name is marked while its contents are dirty. */
  class MyDiffNode extends DiffNode {
    private boolean fDirty = false;
    private ITypedElement fLastId;
    private String fLastName;

    MyDiffNode(IDiffContainer parent, int description, ITypedElement ancestor, ITypedElement left, ITypedElement right) {
      super(parent, description, ancestor, left, right);
    }

    @Override
    public void fireChange() {
      super.fireChange();
      setDirty(true);
      fDirty = true;
      if (fDiffViewer != null)
        fDiffViewer.refresh(this);
    }

    void clearDirty() {
      fDirty = false;
    }

    @Override
    public String getName() {
      if (fLastName == null)
        fLastName = super.getName();
      if (fDirty)
        return '<' + fLastName + '>';
      return fLastName;
    }

    @Override
    public ITypedElement getId() {
      ITypedElement id = super.getId();
      if (id == null)
        return fLastId;
      fLastId = id;
      return id;
    }
  }

  /** {@code ResourceCompareInput.FilteredBufferedResourceNode}: the members of a folder, as buffered nodes. */
  static class FilteredBufferedResourceNode extends BufferedResourceNode {
    FilteredBufferedResourceNode(IResource resource) {
      super(resource);
    }

    @Override
    protected IStructureComparator createChild(IResource child) {
      if (filtered(child.getName(), child instanceof IContainer))
        return null;
      return new FilteredBufferedResourceNode(child);
    }

    /** Whether the member is excluded by the "Filtered Members" of the Compare/Patch preferences. */
    static boolean filtered(String name, boolean isFolder) {
      return CompareUIPlugin.filter(name, isFolder, false);
    }
  }

  /** {@code ResourceCompareInput.SelectAncestorDialog}: the radio buttons choosing the common ancestor of three resources. */
  static class SelectAncestorDialog extends MessageDialog {
    private final IResource[] theResources;
    IResource ancestorResource;
    IResource leftResource;
    IResource rightResource;
    private Button[] buttons;

    SelectAncestorDialog(Shell parentShell, IResource[] theResources) {
      super(parentShell, "Select Common Ancestor",
          null, "Which resource would you like to use as the common ancestor in the three-way compare?",
          MessageDialog.QUESTION, new String[] { IDialogConstants.OK_LABEL, IDialogConstants.CANCEL_LABEL }, 0);
      this.theResources = theResources;
    }

    @Override
    protected Control createCustomArea(Composite parent) {
      Composite composite = new Composite(parent, SWT.NONE);
      composite.setLayout(new GridLayout());
      buttons = new Button[3];
      for (int i = 0; i < 3; i++) {
        buttons[i] = new Button(composite, SWT.RADIO);
        buttons[i].addSelectionListener(selectionListener);
        buttons[i].setText(MessageFormat.format("''{0}''", theResources[i].getFullPath().toPortableString()));
        buttons[i].setFont(parent.getFont());
        // set initial state
        buttons[i].setSelection(i == 0);
      }
      pickAncestor(0);
      return composite;
    }

    private void pickAncestor(int i) {
      ancestorResource = theResources[i];
      leftResource = theResources[i == 0 ? 1 : 0];
      rightResource = theResources[i == 2 ? 1 : 2];
    }

    private final SelectionListener selectionListener = new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        Button selectedButton = (Button) e.widget;
        if (!selectedButton.getSelection())
          return;
        for (int i = 0; i < 3; i++) {
          if (selectedButton == buttons[i])
            pickAncestor(i);
        }
      }
    };
  }

  ResourceCompareInput(CompareConfiguration config) {
    super(config);
  }

  @Override
  public Viewer createDiffViewer(Composite parent) {
    fDiffViewer = new DiffTreeViewer(parent, getCompareConfiguration()) {
      @Override
      protected void fillContextMenu(IMenuManager manager) {
        if (fOpenAction == null) {
          fOpenAction = new Action() {
            @Override
            public void run() {
              handleOpen(null);
            }
          };
          // Utilities.initAction(fOpenAction, getBundle(), "action.CompareContents.")
          fOpenAction.setText("Show Content Comparison");
          fOpenAction.setToolTipText("Show Content Comparison");
        }
        boolean enable = false;
        ISelection selection = getSelection();
        if (selection instanceof IStructuredSelection ss) {
          if (ss.size() == 1) {
            Object element = ss.getFirstElement();
            if (element instanceof MyDiffNode) {
              ITypedElement te = ((MyDiffNode) element).getId();
              if (te != null)
                enable = !ITypedElement.FOLDER_TYPE.equals(te.getType());
            } else {
              enable = true;
            }
          }
        }
        fOpenAction.setEnabled(enable);
        manager.add(fOpenAction);
        super.fillContextMenu(manager);
      }
    };
    return fDiffViewer;
  }

  /**
   * If the compare is three-way, asks the user which resource to use as the ancestor. Returns false if the user cancels
   * the prompt, true otherwise.
   */
  boolean setSelection(ISelection s, Shell shell, boolean showSelectAncestorDialog) {
    IResource[] selection = getResources(s);
    fThreeWay = selection.length == 3;
    if (fThreeWay) {
      SelectAncestorDialog dialog = new SelectAncestorDialog(shell, selection);
      int code = dialog.open();
      if (code != Window.OK)
        return false;
      fAncestorResource = dialog.ancestorResource;
      fAncestor = getStructure(fAncestorResource);
      fLeftResource = dialog.leftResource;
      fRightResource = dialog.rightResource;
    } else {
      fAncestorResource = null;
      fAncestor = null;
      fLeftResource = selection[0];
      fRightResource = selection[1];
    }
    fLeft = getStructure(fLeftResource);
    fRight = getStructure(fRightResource);
    return true;
  }

  /** Sets the resources to compare, as chosen in the "Compare With Other Resource" dialog: the ancestor, left, right. */
  boolean setResources(IResource[] selection) {
    fThreeWay = selection.length == 3;
    if (fThreeWay) {
      fAncestorResource = selection[0];
      fAncestor = getStructure(fAncestorResource);
      fLeftResource = selection[1];
      fRightResource = selection[2];
    } else {
      fAncestorResource = null;
      fAncestor = null;
      fLeftResource = selection[0];
      fRightResource = selection[1];
    }
    fLeft = getStructure(fLeftResource);
    fRight = getStructure(fRightResource);
    return true;
  }

  /** Whether compare can be executed for the selection: two or three comparable resources. */
  public boolean isEnabled(ISelection s) {
    IResource[] selection = getResources(s);
    if (selection.length < 2 || selection.length > 3)
      return false;
    boolean threeWay = selection.length == 3;
    if (threeWay) {
      // It only makes sense if they're all mutually comparable. If not, the user should compare two of them.
      return comparable(selection[0], selection[1]) && comparable(selection[0], selection[2])
          && comparable(selection[1], selection[2]);
    }
    return comparable(selection[0], selection[1]);
  }

  /** Initializes the labels and the images of the sides in the compare configuration. */
  void initializeCompareConfiguration() {
    CompareConfiguration cc = getCompareConfiguration();
    if (fLeftResource != null) {
      cc.setLeftLabel(buildLabel(fLeftResource));
      cc.setLeftImage(CompareUI.getImage(fLeftResource));
    }
    if (fRightResource != null) {
      cc.setRightLabel(buildLabel(fRightResource));
      cc.setRightImage(CompareUI.getImage(fRightResource));
    }
    if (fThreeWay && fAncestorResource != null) {
      cc.setAncestorLabel(buildLabel(fAncestorResource));
      cc.setAncestorImage(CompareUI.getImage(fAncestorResource));
    }
  }

  /** Both resources are either structured (folders, archives) or unstructured (files). */
  private boolean comparable(IResource c1, IResource c2) {
    return hasStructure(c1) == hasStructure(c2);
  }

  private boolean hasStructure(IResource input) {
    if (input instanceof IContainer)
      return true;
    if (input instanceof IFile) {
      String type = ((IFile) input).getFileExtension();
      if (type != null) {
        type = normalizeCase(type);
        return "JAR".equals(type) || "ZIP".equals(type);
      }
    }
    return false;
  }

  /** The structure of a resource for the compare, or null when it has none. */
  private IStructureComparator getStructure(IResource input) {
    if (input instanceof IContainer || input instanceof IFile)
      return new FilteredBufferedResourceNode(input);
    return null;
  }

  /** Performs the two-way or three-way diff of the selected resources. */
  @Override
  public Object prepareInput(IProgressMonitor pm) throws InvocationTargetException {
    try {
      // fix for PR 1GFMLFB: files that are out of sync with the file system appear as empty
      fLeftResource.refreshLocal(IResource.DEPTH_INFINITE, pm);
      fRightResource.refreshLocal(IResource.DEPTH_INFINITE, pm);
      if (fThreeWay && fAncestorResource != null)
        fAncestorResource.refreshLocal(IResource.DEPTH_INFINITE, pm);
      pm.beginTask("Operation in Progress...", IProgressMonitor.UNKNOWN);
      String leftLabel = fLeftResource.getName();
      String rightLabel = fRightResource.getName();
      String title;
      if (fThreeWay) {
        String ancestorLabel = fAncestorResource.getName();
        title = MessageFormat.format("Compare (''{0}'' - ''{1}'' - ''{2}'')", ancestorLabel, leftLabel, rightLabel);
      } else {
        title = MessageFormat.format("Compare (''{0}'' - ''{1}'')", leftLabel, rightLabel);
      }
      setTitle(title);
      Differencer d = new Differencer() {
        @Override
        protected Object visit(Object parent, int description, Object ancestor, Object left, Object right) {
          return new MyDiffNode((IDiffContainer) parent, description, (ITypedElement) ancestor, (ITypedElement) left,
              (ITypedElement) right);
        }
      };
      fRoot = d.findDifferences(fThreeWay, pm, null, fAncestor, fLeft, fRight);
      return fRoot;
    } catch (CoreException ex) {
      throw new InvocationTargetException(ex);
    } finally {
      pm.done();
    }
  }

  @Override
  public String getToolTipText() {
    if (fLeftResource != null && fRightResource != null) {
      String leftLabel = fLeftResource.getFullPath().makeRelative().toString();
      String rightLabel = fRightResource.getFullPath().makeRelative().toString();
      if (fThreeWay) {
        String ancestorLabel = fAncestorResource.getFullPath().makeRelative().toString();
        return MessageFormat.format("Three-way compare of ''{1}'' and ''{2}'' relative to common ancestor ''{0}''",
            ancestorLabel, leftLabel, rightLabel);
      }
      return MessageFormat.format("Two-way compare of ''{0}'' with ''{1}''", leftLabel, rightLabel);
    }
    return super.getToolTipText();
  }

  private String buildLabel(IResource r) {
    String n = r.getFullPath().toString();
    if (n.charAt(0) == IPath.SEPARATOR)
      return n.substring(1);
    return n;
  }

  /** Commits the buffers of the changed nodes to their files. */
  @Override
  public void saveChanges(IProgressMonitor pm) throws CoreException {
    super.saveChanges(pm);
    if (fRoot instanceof DiffNode) {
      try {
        commit(pm, (DiffNode) fRoot);
      } finally {
        if (fDiffViewer != null)
          fDiffViewer.refresh();
        setDirty(false);
      }
    }
  }

  /** Recursively walks the diff tree and commits all changes. */
  private static void commit(IProgressMonitor pm, DiffNode node) throws CoreException {
    if (node instanceof MyDiffNode)
      ((MyDiffNode) node).clearDirty();
    ITypedElement left = node.getLeft();
    if (left instanceof BufferedResourceNode)
      ((BufferedResourceNode) left).commit(pm);
    ITypedElement right = node.getRight();
    if (right instanceof BufferedResourceNode)
      ((BufferedResourceNode) right).commit(pm);
    IDiffElement[] children = node.getChildren();
    if (children != null)
      for (IDiffElement element : children)
        if (element instanceof DiffNode)
          commit(pm, (DiffNode) element);
  }

  private static String normalizeCase(String s) {
    if (NORMALIZE_CASE && s != null)
      return s.toUpperCase();
    return s;
  }

  @Override
  public boolean canRunAsJob() {
    return true;
  }

  /** {@code Utilities.getResources}: the accessible resources of the selection (the traversals of mappings included). */
  static IResource[] getResources(ISelection selection) {
    List<IResource> result = new ArrayList<>();
    if (selection instanceof IStructuredSelection)
      for (Object o : ((IStructuredSelection) selection).toList()) {
        IResource resource = Adapters.adapt(o, IResource.class);
        if (resource != null) {
          if (resource.isAccessible())
            result.add(resource);
          continue;
        }
        ResourceMapping mapping = Adapters.adapt(o, ResourceMapping.class);
        if (mapping == null)
          continue;
        try {
          for (ResourceTraversal t : mapping.getTraversals(ResourceMappingContext.LOCAL_CONTEXT, new NullProgressMonitor()))
            for (IResource r : t.getResources())
              if (r != null && r.isAccessible())
                result.add(r);
        } catch (CoreException e) {
          RLogger.logError(e);
        }
      }
    return result.toArray(new IResource[0]);
  }
}
