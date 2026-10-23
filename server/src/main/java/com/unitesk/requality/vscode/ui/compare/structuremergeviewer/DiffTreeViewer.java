package com.unitesk.requality.vscode.ui.compare.structuremergeviewer;

import com.unitesk.requality.vscode.ui.compare.CompareConfiguration;
import com.unitesk.requality.vscode.ui.compare.CompareUI;
import com.unitesk.requality.vscode.ui.compare.ITypedElement;
import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.action.IMenuManager;
import com.unitesk.requality.vscode.ui.jface.action.MenuManager;
import com.unitesk.requality.vscode.ui.jface.preference.IPropertyChangeListener;
import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ViewerComparator;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;

/**
 * {@code org.eclipse.compare.structuremergeviewer.DiffTreeViewer}: the tree of the differences of a structure
 * compare ("Structure Compare"); each element shows its name and the image of its type with the overlay of its kind of
 * difference. The first change is selected when the input is set; double-click expands or collapses a container.
 */
public class DiffTreeViewer extends TreeViewer {
  class DiffViewerContentProvider implements ITreeContentProvider {
    @Override
    public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
    }

    @Override
    public void dispose() {
    }

    @Override
    public Object getParent(Object element) {
      return element instanceof IDiffElement ? ((IDiffElement) element).getParent() : null;
    }

    @Override
    public boolean hasChildren(Object element) {
      return element instanceof IDiffContainer && ((IDiffContainer) element).hasChildren();
    }

    @Override
    public Object[] getChildren(Object element) {
      return element instanceof IDiffContainer ? ((IDiffContainer) element).getChildren() : new Object[0];
    }

    @Override
    public Object[] getElements(Object element) {
      return getChildren(element);
    }
  }

  class DiffViewerLabelProvider extends LabelProvider {
    @Override
    public String getText(Object element) {
      if (element instanceof IDiffElement)
        return ((IDiffElement) element).getName();
      return "<no name>";
    }

    @Override
    public Image getImage(Object element) {
      if (element instanceof IDiffElement) {
        IDiffElement input = (IDiffElement) element;
        int kind = input.getKind();
        switch (kind & Differencer.DIRECTION_MASK) {
          case Differencer.LEFT:
            kind = kind & ~Differencer.LEFT | Differencer.RIGHT;
            break;
          case Differencer.RIGHT:
            kind = kind & ~Differencer.RIGHT | Differencer.LEFT;
            break;
          case 0:
            switch (kind & Differencer.CHANGE_TYPE_MASK) {
              case Differencer.ADDITION:
                kind = kind & ~Differencer.ADDITION | Differencer.DELETION;
                break;
              case Differencer.DELETION:
                kind = kind & ~Differencer.DELETION | Differencer.ADDITION;
                break;
              default:
                break;
            }
            break;
          default:
            break;
        }
        return fCompareConfiguration.getImage(input.getImage(), kind);
      }
      return null;
    }
  }

  /** {@code org.eclipse.compare.internal.DiffViewerComparator}: folders before files, then by name. */
  static class DiffViewerComparator extends ViewerComparator {
    @Override
    public int category(Object element) {
      if (element instanceof IDiffContainer && ((IDiffContainer) element).hasChildren())
        return 0;
      if (element instanceof ITypedElement && ITypedElement.FOLDER_TYPE.equals(((ITypedElement) element).getType()))
        return 0;
      return 1;
    }
  }

  private CompareConfiguration fCompareConfiguration;
  private IPropertyChangeListener fPropertyChangeListener;

  public DiffTreeViewer(Composite parent, CompareConfiguration configuration) {
    super(parent, SWT.MULTI);
    initialize(configuration == null ? new CompareConfiguration() : configuration);
  }

  private void initialize(CompareConfiguration configuration) {
    getControl().setData(CompareUI.COMPARE_VIEWER_TITLE, getTitle());
    setData(CompareUI.COMPARE_VIEWER_TITLE, getTitle());
    fCompareConfiguration = configuration;
    fPropertyChangeListener = e -> {
      if (CompareConfiguration.MIRRORED.equals(e.getProperty()))
        refresh(true);
    };
    fCompareConfiguration.addPropertyChangeListener(fPropertyChangeListener);
    setContentProvider(new DiffViewerContentProvider());
    setLabelProvider(new DiffViewerLabelProvider());
    setComparator(new DiffViewerComparator());
    addDoubleClickListener(e -> {
      Object o = e.getSelection() instanceof IStructuredSelection
          ? ((IStructuredSelection) e.getSelection()).getFirstElement() : null;
      if (o instanceof IDiffContainer && ((IDiffContainer) o).hasChildren())
        setExpandedState(o, !getExpandedState(o));
    });
    MenuManager mm = new MenuManager();
    mm.setRemoveAllWhenShown(true);
    mm.addMenuListener(m -> {
      fillContextMenu(m);
      if (m.getItems().length == 0) {
        Action empty = new Action("<Empty Menu>") {
        };
        empty.setEnabled(false);
        m.add(empty);
      }
    });
    getTree().setMenu(mm.createContextMenu(getTree()));
    getTree().addDisposeListener(e -> fCompareConfiguration.removePropertyChangeListener(fPropertyChangeListener));
  }

  /** The context menu of the tree: "Expand All" of the selected containers (subclasses may add their actions). */
  protected void fillContextMenu(IMenuManager manager) {
    Action expandAll = new Action("Expand All") {
      @Override
      public void run() {
        expandSelection();
      }
    };
    expandAll.setToolTipText("Expand All Nodes");
    boolean enable = false;
    if (getSelection() instanceof IStructuredSelection)
      for (Object o : ((IStructuredSelection) getSelection()).toList())
        if (o instanceof IDiffContainer && ((IDiffContainer) o).hasChildren())
          enable = true;
    expandAll.setEnabled(enable);
    manager.add(expandAll);
  }

  /** The title of the viewer. */
  public String getTitle() {
    return "Structure Compare";
  }

  @Override
  protected void inputChanged(Object in, Object oldInput) {
    super.inputChanged(in, oldInput);
    if (in != oldInput)
      initialSelection();
  }

  /** Selects (and reveals) the first change. */
  protected void initialSelection() {
    navigate(true);
  }

  /** Selects the first change (next) or the last one. */
  protected void navigate(boolean next) {
    Object first = firstLeaf(getInput(), next);
    if (first != null)
      setSelection(new StructuredSelection(first), true);
  }

  private Object firstLeaf(Object element, boolean next) {
    Object[] children = getSortedChildren(element);
    if (children.length == 0)
      return element == getInput() ? null : element;
    if (element != getInput() && element instanceof DiffNode && ((DiffNode) element).dontExpand())
      return element;
    return firstLeaf(children[next ? 0 : children.length - 1], next);
  }

  private Object[] getSortedChildren(Object element) {
    Object[] children = ((ITreeContentProvider) getContentProvider()).getChildren(element);
    if (getComparator() != null)
      getComparator().sort(this, children);
    return children;
  }

  protected void expandSelection() {
    if (getSelection() instanceof IStructuredSelection)
      for (Object next : ((IStructuredSelection) getSelection()).toList())
        expandToLevel(next, ALL_LEVELS);
  }

  /** Copies the selected differences from one side to the other. */
  protected void copySelected(boolean leftToRight) {
    if (getSelection() instanceof IStructuredSelection)
      for (Object element : ((IStructuredSelection) getSelection()).toList())
        if (element instanceof ICompareInput)
          copyOne((ICompareInput) element, leftToRight);
  }

  protected void copyOne(ICompareInput node, boolean leftToRight) {
    node.copy(leftToRight);
    update(new Object[] { node }, null);
  }

  public CompareConfiguration getCompareConfiguration() {
    return fCompareConfiguration;
  }
}
