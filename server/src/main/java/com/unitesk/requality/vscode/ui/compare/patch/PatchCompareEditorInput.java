/*******************************************************************************
 * Copyright (c) 2008, 2011 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.unitesk.requality.vscode.ui.compare.patch;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import com.unitesk.requality.vscode.core.Icons;

import com.unitesk.requality.vscode.ui.compare.CompareEditorInput;
import com.unitesk.requality.vscode.ui.compare.CompareUI;
import com.unitesk.requality.vscode.ui.compare.CompareConfiguration;
import com.unitesk.requality.vscode.ui.compare.CompareViewerPane;
import com.unitesk.requality.vscode.ui.compare.patch.core.DiffProject;
import com.unitesk.requality.vscode.ui.compare.patch.core.FileDiffResult;
import com.unitesk.requality.vscode.ui.compare.patch.core.FilePatch2;
import com.unitesk.requality.vscode.ui.compare.patch.core.HunkResult;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.DiffNode;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.ICompareInput;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.DiffTreeViewer;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.Differencer;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.IDiffElement;
import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.action.IMenuManager;
import com.unitesk.requality.vscode.ui.jface.action.Separator;
import com.unitesk.requality.vscode.ui.jface.action.ToolBarManager;
import com.unitesk.requality.vscode.ui.jface.viewers.ILabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ViewerFilter;
import com.unitesk.requality.vscode.ui.runtime.Assert;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;

/**
 * {@code org.eclipse.compare.internal.patch.PatchCompareEditorInput}: the review tree of the patch: the projects, the files
 * and the hunks of the patch (the hunks that match, and those that do not), with the contents of the selected entry in the
 * compare pane. The entries that are excluded are hidden unless {@link #setShowAll} is set.
 */
public abstract class PatchCompareEditorInput extends CompareEditorInput {

  /** The label provider of the review tree: the excluded and the fuzzy entries are labelled as in Eclipse. */
  class PatcherCompareEditorLabelProvider extends LabelProvider {
    private final ILabelProvider wrappedProvider;

    public PatcherCompareEditorLabelProvider(ILabelProvider labelProvider) {
      wrappedProvider = labelProvider;
    }

    @Override
    public String getText(Object element) {
      String text = wrappedProvider.getText(element);
      if (element instanceof PatchDiffNode node) {
        if (node instanceof PatchProjectDiffNode projectNode) {
          if (!Utilities.getProject(projectNode.getDiffProject()).exists()) {
            text = PatchMessages.bind(PatchMessages.Diff_2Args, text,
                PatchMessages.PreviewPatchLabelDecorator_ProjectDoesNotExist);
          }
        }
        if (!node.isEnabled()) {
          return PatchMessages.bind(PatchMessages.Diff_2Args, text, PatchMessages.PatcherCompareEditorInput_NotIncluded);
        }
        if (node instanceof PatchFileDiffNode fileNode) {
          if (getPatcher().hasCachedContents(fileNode.getDiffResult().getDiff())) {
            text = PatchMessages.bind(PatchMessages.Diff_2Args, text, PatchMessages.HunkMergePage_Merged);
          }
          if (!fileNode.fileExists()) {
            text = PatchMessages.bind(PatchMessages.Diff_2Args, text, PatchMessages.PatchCompareEditorInput_0);
          }
        }
        if (node instanceof HunkDiffNode hunkNode) {
          if (hunkNode.isManuallyMerged()) {
            text = PatchMessages.bind(PatchMessages.Diff_2Args, text, PatchMessages.HunkMergePage_Merged);
          }
          if (hunkNode.isFuzzUsed()) {
            text = PatchMessages.bind(PatchMessages.Diff_2Args, text,
                PatchMessages.bind(hunkNode.isAllContextIgnored() ? PatchMessages.PreviewPatchPage_AllContextIgnored
                    : PatchMessages.PreviewPatchPage_FuzzUsed, hunkNode.getHunkResult().getFuzz() + ""));
          }
        }
        if (getPatcher().isRetargeted(node.getPatchElement())) {
          return PatchMessages.bind(PatchMessages.Diff_2Args,
              getPatcher().getOriginalPath(node.getPatchElement()).toString(),
              PatchMessages.bind(PatchMessages.PreviewPatchPage_Target, node.getName()));
        }
      }
      return text;
    }

    @Override
    public Image getImage(Object element) {
      Image image = wrappedProvider.getImage(element);
      if (element instanceof PatchDiffNode node) {
        if (!node.isEnabled() && image != null) {
          return createOverlay(image, HunkTypedElement.REMOVED_OVERLAY);
        }
      }
      if (element instanceof HunkDiffNode node) {
        if (node.isManuallyMerged() && image != null) {
          return createOverlay(image, HunkTypedElement.IS_MERGED_OVERLAY);
        }
      }
      return image;
    }

    /** {@code PatchCompareEditorInput.createOverlay}: the base image with the overlay in its top left quadrant. */
    private Image createOverlay(Image image, String overlay) {
      String path = Icons.compose(image.getPath(),
          List.of(new Icons.Overlay(overlay, Icons.Quadrant.TOP_LEFT)));
      return path == null ? image : new Image(null, path);
    }
  }

  private final DiffNode root;
  private final WorkspacePatcher patcher;
  private TreeViewer viewer;
  private boolean fShowAll;
  private boolean showMatched = false;

  public PatchCompareEditorInput(WorkspacePatcher patcher, CompareConfiguration configuration) {
    super(configuration);
    Assert.isNotNull(patcher);
    this.patcher = patcher;
    root = new DiffNode(Differencer.NO_CHANGE) {
      @Override
      public boolean hasChildren() {
        return true;
      }
    };
  }

  @Override
  protected Object prepareInput(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
    initLabels();
    return root;
  }

  private void initLabels() {
    CompareConfiguration cc = getCompareConfiguration();
    cc.setLeftEditable(true);
    cc.setRightEditable(false);
    cc.setLeftLabel(getCompareConfiguration().getLeftLabel(root));
    cc.setLeftImage(getCompareConfiguration().getLeftImage(root));
    cc.setRightLabel(getCompareConfiguration().getRightLabel(root));
    cc.setRightImage(getCompareConfiguration().getRightImage(root));
  }

  /** Refreshes the review tree (the labels of its entries). */
  protected void updateTree() {
    if (getViewer() != null && !getViewer().getControl().isDisposed()) {
      getViewer().refresh(true);
    }
  }

  /** Builds the review tree from the diffs of the patcher (the projects of a workspace patch, else the file diffs). */
  protected void buildTree() {
    if (getRoot().hasChildren()) {
      resetRoot();
    }
    getViewer().setInput(getRoot());
    getPatcher().refresh();
    if (getPatcher().isWorkspacePatch()) {
      processProjects(getPatcher().getDiffProjects());
    } else {
      processDiffs(getPatcher().getDiffs());
    }
    getViewer().refresh();
  }

  private void processDiffs(FilePatch2[] diffs) {
    for (FilePatch2 diff : diffs) {
      processDiff(diff, getRoot());
    }
  }

  private void processProjects(DiffProject[] diffProjects) {
    for (DiffProject diffProject : diffProjects) {
      PatchProjectDiffNode projectNode = new PatchProjectDiffNode(getRoot(), diffProject, getPatcher().getConfiguration());
      FilePatch2[] diffs = diffProject.getFileDiffs();
      for (FilePatch2 fileDiff : diffs) {
        processDiff(fileDiff, projectNode);
      }
    }
  }

  private void processDiff(FilePatch2 diff, DiffNode parent) {
    FileDiffResult diffResult = getPatcher().getDiffResult(diff);
    PatchFileDiffNode node = PatchFileDiffNode.createDiffNode(parent, diffResult);
    HunkResult[] hunkResults = diffResult.getHunkResults();
    for (HunkResult hunkResult : hunkResults) {
      if (!hunkResult.isOK()) {
        HunkDiffNode hunkNode = HunkDiffNode.createDiffNode(node, hunkResult, true);
        Object left = hunkNode.getLeft();
        if (left instanceof UnmatchedHunkTypedElement element) {
          element.addContentChangeListener(source -> {
            if (getViewer() == null || getViewer().getControl().isDisposed()) {
              return;
            }
            getViewer().refresh(true);
          });
        }
      } else if (showMatched) {
        HunkDiffNode.createDiffNode(node, hunkResult, false, true, false);
      }
    }
  }

  @Override
  public Viewer createDiffViewer(Composite parent) {
    viewer = new DiffTreeViewer(parent, getCompareConfiguration()) {
      @Override
      protected void fillContextMenu(IMenuManager manager) {
        PatchCompareEditorInput.this.fillContextMenu(manager);
      }
    };
    viewer.setLabelProvider(new PatcherCompareEditorLabelProvider((ILabelProvider) viewer.getLabelProvider()));
    viewer.getTree().setData(CompareUI.COMPARE_VIEWER_TITLE, PatchMessages.PatcherCompareEditorInput_PatchContents);
    viewer.addOpenListener(event -> {
      IStructuredSelection sel = (IStructuredSelection) event.getSelection();
      Object obj = sel.getFirstElement();
      if (obj instanceof HunkDiffNode) {
        if (((HunkDiffNode) obj).getHunkResult().isOK()) {
          getCompareConfiguration().setLeftLabel(PatchMessages.PatcherCompareEditorInput_LocalCopy);
          getCompareConfiguration().setRightLabel(PatchMessages.PreviewPatchPage2_MatchedHunk);
        } else {
          getCompareConfiguration().setLeftLabel(PatchMessages.PreviewPatchPage2_PatchedLocalFile);
          getCompareConfiguration().setRightLabel(PatchMessages.PreviewPatchPage2_OrphanedHunk);
        }
      } else {
        getCompareConfiguration().setLeftLabel(PatchMessages.PatcherCompareEditorInput_LocalCopy);
        getCompareConfiguration().setRightLabel(PatchMessages.PatcherCompareEditorInput_AfterPatch);
      }
    });
    viewer.setFilters(getFilters());
    viewer.setInput(root);
    return viewer;
  }

  private ViewerFilter[] getFilters() {
    return new ViewerFilter[] { new ViewerFilter() {
      @Override
      public boolean select(Viewer v, Object parentElement, Object element) {
        if (element instanceof PatchDiffNode node) {
          return node.isEnabled() || isShowAll();
        }
        return false;
      }
    } };
  }

  protected boolean isShowAll() {
    return fShowAll;
  }

  protected void setShowAll(boolean show) {
    fShowAll = show;
  }

  public boolean isShowMatched() {
    return showMatched;
  }

  protected void setShowMatched(boolean show) {
    showMatched = show;
  }

  /** The actions of the page in the toolbar of the review tree (the contributed group of the tree's pane). */
  public void contributeDiffViewerToolbarItems(Action[] actions, boolean workspacePatch) {
    ToolBarManager tbm = CompareViewerPane.getToolBarManager(viewer.getControl().getParent());
    if (tbm != null) {
      tbm.removeAll();
      tbm.add(new Separator("contributed"));
      for (Action action : actions) {
        tbm.add(action);
      }
      tbm.update(true);
    }
  }

  /**
   * {@code CompareEditorInput.getNavigator().selectChange(true)}: selects and reveals the first change of the review
   * tree (the first leaf that is not expanded by default), as the structure viewer does when its input is set.
   */
  public void selectFirstChange() {
    if (viewer == null || viewer.getControl().isDisposed()) {
      return;
    }
    Object first = firstChange(root);
    if (first != null) {
      viewer.setSelection(new StructuredSelection(first), true);
    }
  }

  private Object firstChange(Object element) {
    Object[] children = ((ITreeContentProvider) viewer.getContentProvider()).getChildren(element);
    if (viewer.getComparator() != null) {
      viewer.getComparator().sort(viewer, children);
    }
    if (children.length == 0) {
      return element == root ? null : element;
    }
    if (element != root && element instanceof DiffNode node && node.dontExpand()) {
      return element;
    }
    return firstChange(children[0]);
  }

  public TreeViewer getViewer() {
    return viewer;
  }

  public DiffNode getRoot() {
    return root;
  }

  public void resetRoot() {
    IDiffElement[] children = root.getChildren();
    for (IDiffElement child : children) {
      root.remove(child);
    }
  }

  public WorkspacePatcher getPatcher() {
    return patcher;
  }

  /** Whether the patch can be rebuilt; the manual changes are discarded when the user confirms it. */
  public boolean confirmRebuild(String message) {
    if (getPatcher().hasCachedContents()) {
      if (promptToDiscardCachedChanges(message)) {
        getPatcher().clearCachedContents();
        return true;
      }
      return false;
    }
    return true;
  }

  private boolean promptToDiscardCachedChanges(String message) {
    return com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog.openConfirm(viewer.getControl().getShell(),
        PatchMessages.PatcherCompareEditorInput_0, message);
  }

  /** Whether at least one file or hunk of the patch is included. */
  public boolean hasResultToApply() {
    boolean atLeastOneIsEnabled = false;
    if (getViewer() != null) {
      IDiffElement[] elements = getRoot().getChildren();
      for (IDiffElement element : elements) {
        if (isEnabled(element)) {
          atLeastOneIsEnabled = true;
          break;
        }
      }
    }
    return atLeastOneIsEnabled;
  }

  private boolean isEnabled(IDiffElement element) {
    if (element instanceof PatchDiffNode node) {
      return node.isEnabled();
    }
    return false;
  }

  /** The actions of the review tree's context menu (the page fills it). */
  protected abstract void fillContextMenu(IMenuManager manager);

  @Override
  public Viewer findStructureViewer(Viewer oldViewer, ICompareInput input, Composite parent) {
    if (Utilities.isHunk(input)) {
      return null;
    }
    return super.findStructureViewer(oldViewer, input, parent);
  }
}
