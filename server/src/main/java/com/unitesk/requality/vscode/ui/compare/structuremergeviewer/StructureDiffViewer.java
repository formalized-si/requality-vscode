package com.unitesk.requality.vscode.ui.compare.structuremergeviewer;

import com.unitesk.requality.vscode.ui.compare.IContentChangeNotifier;
import com.unitesk.requality.vscode.ui.compare.IContentChangeListener;
import com.unitesk.requality.vscode.ui.compare.CompareConfiguration;
import com.unitesk.requality.vscode.ui.compare.CompareUI;
import com.unitesk.requality.vscode.ui.compare.CompareViewerSwitchingPane;
import com.unitesk.requality.vscode.ui.compare.ITypedElement;
import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;

/**
 * {@code org.eclipse.compare.structuremergeviewer.StructureDiffViewer}: the structure compare of two inputs whose
 * structure a {@link IStructureCreator} creates (archives: the entries of a zip file). The tree shows the differences
 * of the two structures; the text compare of a difference is shown by the content pane of the compare editor.
 * When a side of the input changes (IContentChangeNotifier), the structures are compared again.
 * VSCODE: the structures are created on the UI thread when the input is set (Eclipse uses a background job).
 */
public class StructureDiffViewer extends DiffTreeViewer {
  /** {@code StructureDiffViewer.NO_STRUCTURAL_DIFFERENCES}: the title argument of an equal structure. */
  private static final String NO_STRUCTURAL_DIFFERENCES = "No Structural Differences";
  /** {@code CompareMessages.StructureDiffViewer_StructureError}: the title argument of a structure that cannot be read. */
  private static final String STRUCTURE_ERROR = "Cannot Compare Structures";

  private Differencer fDifferencer;
  private IStructureCreator fStructureCreator;
  private IDiffContainer fRoot;
  private final CompareViewerSwitchingPane fParent;

  /** The content of the tree: the differences of the structures are its top level elements. */
  private class StructureContentProvider implements ITreeContentProvider {
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
    public Object[] getElements(Object input) {
      return fRoot == null ? new Object[0] : fRoot.getChildren();
    }
  }

  public StructureDiffViewer(Composite parent, CompareConfiguration configuration) {
    super(parent, configuration);
    fParent = parent instanceof CompareViewerSwitchingPane ? (CompareViewerSwitchingPane) parent : null;
    setContentProvider(new StructureContentProvider());
    setAutoExpandLevel(3);
  }

  /** Sets the creator of the structures of the inputs. */
  public void setStructureCreator(IStructureCreator structureCreator) {
    if (fStructureCreator != structureCreator) {
      fStructureCreator = structureCreator;
      setData(CompareUI.COMPARE_VIEWER_TITLE, getTitle());
    }
  }

  public IStructureCreator getStructureCreator() {
    return fStructureCreator;
  }

  @Override
  public String getTitle() {
    return fStructureCreator != null ? fStructureCreator.getName() : super.getTitle();
  }

  /** {@code StructureDiffViewer.fContentChangedListener}: the sides of the input changed: the structures are compared again. */
  private final IContentChangeListener fContentChangedListener = this::contentChanged;

  /** The sides of a compare input that notify their content changes. */
  private static java.util.List<IContentChangeNotifier> notifiers(Object input) {
    java.util.List<IContentChangeNotifier> r = new java.util.ArrayList<>();
    if (input instanceof ICompareInput ci)
      for (ITypedElement side : new ITypedElement[] { ci.getAncestor(), ci.getLeft(), ci.getRight() })
        if (side instanceof IContentChangeNotifier n)
          r.add(n);
    return r;
  }

  /** {@code StructureDiffViewer.contentChanged}: a side changed: the structures and their differences are refreshed. */
  protected void contentChanged(IContentChangeNotifier changed) {
    if (fStructureCreator == null || getControl() == null || getControl().isDisposed())
      return;
    getControl().getDisplay().asyncExec(() -> {
      if (getControl().isDisposed() || !(getInput() instanceof ICompareInput input))
        return;
      String message = diff(input);
      refresh();
      if (fParent != null)
        fParent.setTitleArgument(message);
    });
  }

  @Override
  protected void inputChanged(Object input, Object oldInput) {
    for (IContentChangeNotifier n : notifiers(oldInput))
      n.removeContentChangeListener(fContentChangedListener);
    for (IContentChangeNotifier n : notifiers(input))
      n.addContentChangeListener(fContentChangedListener);
    fRoot = null;
    String message = null;
    if (input instanceof ICompareInput && fStructureCreator != null) {
      message = diff((ICompareInput) input);
    }
    super.inputChanged(input, oldInput);
    if (fParent != null)
      fParent.setTitleArgument(message);
  }

  @Override
  protected void initialSelection() {
    expandToLevel(2);
  }

  /** Computes the differences of the structures of the input; returns the message of the title, or null. */
  private String diff(ICompareInput input) {
    ITypedElement ancestor = input.getAncestor();
    boolean threeWay = ancestor != null;
    IStructureComparator ancestorComparator = threeWay ? structure(ancestor) : null;
    IStructureComparator leftComparator = structure(input.getLeft());
    IStructureComparator rightComparator = structure(input.getRight());
    if ((threeWay && ancestorComparator == null) || leftComparator == null || rightComparator == null)
      return STRUCTURE_ERROR;
    if (fDifferencer == null) {
      fDifferencer = new Differencer() {
        @Override
        protected boolean contentsEqual(Object o1, char contributor1, Object o2, char contributor2) {
          return StructureDiffViewer.this.contentsEqual(o1, contributor1, o2, contributor2);
        }

        @Override
        protected Object visit(Object data, int result, Object ancestor, Object left, Object right) {
          Object o = super.visit(data, result, ancestor, left, right);
          if (!getCompareConfiguration().isMirrored() && o instanceof DiffNode)
            ((DiffNode) o).swapSides(true);
          return o;
        }
      };
    }
    Object root = fDifferencer.findDifferences(threeWay, null, null, ancestorComparator, leftComparator,
        rightComparator);
    fRoot = root instanceof IDiffContainer ? (IDiffContainer) root : null;
    if (fRoot == null || fRoot.getChildren().length == 0)
      return NO_STRUCTURAL_DIFFERENCES;
    return null;
  }

  private IStructureComparator structure(ITypedElement element) {
    if (element == null)
      return null;
    if (fStructureCreator instanceof IStructureCreator2) {
      try {
        return ((IStructureCreator2) fStructureCreator).createStructure(element, null);
      } catch (CoreException e) {
        RLogger.logError(e);
        return null;
      }
    }
    return fStructureCreator.getStructure(element);
  }

  private boolean contentsEqual(Object o1, char contributor1, Object o2, char contributor2) {
    if (fStructureCreator == null)
      return false;
    CompareConfiguration cc = getCompareConfiguration();
    boolean ignoreWhiteSpace = cc != null && Boolean.TRUE.equals(cc.getProperty(CompareConfiguration.IGNORE_WHITESPACE));
    if (fStructureCreator instanceof StructureCreator sc)
      return sc.contentsEquals(o1, contributor1, o2, contributor2, ignoreWhiteSpace);
    String s1 = fStructureCreator.getContents(o1, ignoreWhiteSpace);
    String s2 = fStructureCreator.getContents(o2, ignoreWhiteSpace);
    return s1 != null && s1.equals(s2);
  }
}
