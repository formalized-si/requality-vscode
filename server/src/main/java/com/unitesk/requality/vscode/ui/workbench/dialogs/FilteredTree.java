package com.unitesk.requality.vscode.ui.workbench.dialogs;

import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;

/**
 * {@code org.eclipse.ui.dialogs.FilteredTree}: a tree with a filter text above it. The tree shows the elements that
 * the {@link PatternFilter} matches with the text; the matching parents are expanded.
 */
public class FilteredTree extends Composite {
  private final PatternFilter patternFilter;
  private final TreeViewer treeViewer;
  private final Text filterText;

  /** The filter text and the tree (of the tree style) filtered by the pattern filter. */
  public FilteredTree(Composite parent, int treeStyle, PatternFilter filter, boolean useNewLook) {
    super(parent, SWT.NONE);
    // VSCODE: the "new look" of Eclipse (the filter in a toolbar) is the filter text above the tree
    patternFilter = filter;
    GridLayout layout = new GridLayout();
    layout.marginHeight = 0;
    layout.marginWidth = 0;
    setLayout(layout);
    filterText = new Text(this, SWT.SINGLE | SWT.BORDER | SWT.SEARCH);
    filterText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    filterText.addListener(SWT.Modify, event -> textChanged());
    treeViewer = new TreeViewer(this, treeStyle);
    treeViewer.getTree().setLayoutData(new GridData(GridData.FILL_BOTH));
    treeViewer.addFilter(patternFilter);
  }

  private void textChanged() {
    patternFilter.setPattern(filterText.getText());
    treeViewer.refresh();
    if (patternFilter.hasPattern())
      treeViewer.expandAll();
  }

  /** The viewer of the tree. */
  public TreeViewer getViewer() {
    return treeViewer;
  }

  /** The text of the filter. */
  public Text getFilterControl() {
    return filterText;
  }

  /** The filter of the tree. */
  public PatternFilter getPatternFilter() {
    return patternFilter;
  }

  /** The focus goes to the filter text, as in Eclipse. */
  @Override
  public boolean setFocus() {
    return filterText.setFocus();
  }
}
