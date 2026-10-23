package com.unitesk.requality.vscode.ui.team.synchronize.patch;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.compare.CompareConfiguration;
import com.unitesk.requality.vscode.ui.compare.CompareLinks;
import com.unitesk.requality.vscode.ui.compare.CompareUI;
import com.unitesk.requality.vscode.ui.compare.patch.HunkDiffNode;
import com.unitesk.requality.vscode.ui.compare.patch.PatchCompareEditorInput;
import com.unitesk.requality.vscode.ui.compare.patch.PatchDiffNode;
import com.unitesk.requality.vscode.ui.compare.patch.PatchFileDiffNode;
import com.unitesk.requality.vscode.ui.compare.patch.PatchMessages;
import com.unitesk.requality.vscode.ui.compare.patch.WorkspacePatcher;
import com.unitesk.requality.vscode.ui.compare.patch.core.FilePatch2;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.IDiffContainer;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.IDiffElement;
import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.action.IMenuManager;
import com.unitesk.requality.vscode.ui.jface.action.Separator;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.resources.IStorage;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.NullProgressMonitor;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.team.ui.synchronize.AbstractSynchronizeParticipant;
import com.unitesk.requality.vscode.ui.team.ui.synchronize.ISynchronizeParticipant;

/**
 * {@code org.eclipse.team.internal.ui.synchronize.patch.ApplyPatchModelSynchronizeParticipant}: the synchronize participant of
 * Synchronize with Patch. Its page is the review tree of the patch (the projects, the files and the hunks, with the compare
 * pane of the selected entry). The context menu has Open In Compare Editor, Merge and Mark as Merged, as the merge action
 * group of Eclipse does without the Overwrite action (ApplyPatchModelSynchronizeParticipantActionGroup); Merge and Mark as
 * Merged are disabled for hunks (ApplyPatchSynchronizationActionProvider). Double click opens the compare editor of the file.
 * VSCODE: the participant is not a model synchronization: the page is the review tree of the Apply Patch wizard, and the
 * entries are not decorated with their incoming changes beyond the kind of difference. The Reverse Patch, Fuzz Factor,
 * Ignore Leading Path Segments and Generate Rej File actions of the view menu are not ported. The unsaved changes of the
 * participant are the hunks merged by hand in the review tree (the cached contents); they are saved by applying the files.
 */
public class ApplyPatchModelSynchronizeParticipant extends AbstractSynchronizeParticipant {
  private final WorkspacePatcher patcher;
  private final CompareConfiguration configuration;
  private PatchPageInput input;
  /** Whether the review tree selects an element for the editor (the selection then does not bring its editor to the top). */
  private boolean selectingForEditor;

  /** The participant of the patch (the patch file, when it is a workspace file, names the participant). */
  public ApplyPatchModelSynchronizeParticipant(IStorage patch, WorkspacePatcher patcher, CompareConfiguration configuration) {
    super(patch == null ? "Patch" : patch.getName());
    this.patcher = patcher;
    this.configuration = configuration;
    setDescription("Synchronize with Patch: " + getName());
  }

  /** The patcher of the patch: its file patches, the target and the options of the patch. */
  public WorkspacePatcher getPatcher() {
    return patcher;
  }

  @Override
  public Control createPage(Composite parent) {
    Composite page = new Composite(parent, SWT.NONE);
    page.setLayout(new GridLayout());
    input = new PatchPageInput();
    try {
      input.run(null);
    } catch (InterruptedException | InvocationTargetException e) {
      RLogger.logError(e);
    }
    Control c = input.createContents(page);
    c.setLayoutData(new GridData(GridData.FILL_BOTH));
    input.rebuild();
    return page;
  }

  @Override
  public void dispose() {
    input = null;
    super.dispose();
  }

  /** The unsaved changes of the participant: the file patches with the contents merged by hand in the review tree. */
  @Override
  public boolean isDirty() {
    return patcher.hasCachedContents();
  }

  /** Save: applies the file patches whose contents were merged by hand (the unsaved changes) to the workspace. */
  @Override
  public void save() {
    for (FilePatch2 diff : patcher.getDiffs())
      patcher.setEnabled(diff, patcher.hasCachedContents(diff));
    applyEnabled(true);
  }

  /** Merge: applies the selected entries of the review tree to the workspace, the applied file patches leave the view. */
  void merge() {
    applySelected(true);
  }

  /** Mark as Merged: the selected entries leave the view without being applied to the workspace. */
  void markAsMerged() {
    applySelected(false);
  }

  /** The entries selected in the review tree (their patch elements). */
  private Set<Object> selectedElements() {
    Set<Object> selected = new LinkedHashSet<>();
    if (input == null)
      return selected;
    if (input.getViewer().getSelection() instanceof IStructuredSelection ss)
      for (Object o : ss.toArray())
        if (o instanceof PatchDiffNode node)
          selected.add(node.getPatchElement());
    return selected;
  }

  /** Applies (or only marks as merged) the selected entries: only the selected ones are enabled for the merge. */
  private void applySelected(boolean apply) {
    if (input == null)
      return;
    Set<Object> selected = selectedElements();
    if (selected.isEmpty())
      return;
    for (FilePatch2 diff : patcher.getDiffs())
      patcher.setEnabled(diff, false);
    for (Object element : selected)
      patcher.setEnabled(element, true);
    applyEnabled(apply);
  }

  /** Applies the enabled file patches (when asked) and removes them from the patch, so that they leave the view. */
  private void applyEnabled(boolean apply) {
    List<FilePatch2> applied = new ArrayList<>();
    for (FilePatch2 diff : patcher.getDiffs())
      if (patcher.isEnabled(diff))
        applied.add(diff);
    if (apply) {
      try {
        patcher.applyAll(new NullProgressMonitor(),
            resources -> com.unitesk.requality.vscode.ui.compare.Utilities.validateResources(resources,
                Display.getDefault().getActiveShell(), PatchMessages.PatchWizard_title));
      } catch (CoreException e) {
        RLogger.logError(e);
      }
    }
    for (FilePatch2 diff : applied)
      patcher.removeDiff(diff);
    if (input != null)
      input.rebuild();
  }

  /**
   * Open In Compare Editor: opens the compare editor of the file of the selected entry (a hunk opens the editor of its file),
   * as {@code OpenInCompareAction} does on the double click. Project entries have no compare input. The editor is linked
   * with the file (see {@link CompareLinks}).
   */
  void openInCompareEditor(Object element) {
    PatchFileDiffNode file = fileOf(element);
    if (file == null)
      return;
    CompareUI.openCompareEditor(file.getName(), file.getLeft(), file.getRight(),
        PatchMessages.PatcherCompareEditorInput_LocalCopy, PatchMessages.PatcherCompareEditorInput_AfterPatch,
        file.getPatchElement(), this::selectForEditor);
  }

  /** The file of an entry of the review tree (a hunk belongs to its file), or null for a project. */
  private static PatchFileDiffNode fileOf(Object element) {
    return element instanceof PatchFileDiffNode f ? f
        : element instanceof HunkDiffNode h && h.getParent() instanceof PatchFileDiffNode p ? p : null;
  }

  /**
   * Selects the file of the patch element in the review tree, as the view does when the compare editor of the file becomes
   * active ({@code SynchronizeView.partActivated}). Does nothing while the review tree is not shown.
   */
  void selectForEditor(Object patchElement) {
    if (input == null || input.getViewer() == null || input.getViewer().getControl().isDisposed())
      return;
    PatchFileDiffNode file = findFile(input.getRoot(), patchElement);
    if (file == null)
      return;
    selectingForEditor = true;
    try {
      input.getViewer().setSelection(new StructuredSelection(file), true);
    } finally {
      selectingForEditor = false;
    }
  }

  private static PatchFileDiffNode findFile(IDiffContainer container, Object patchElement) {
    for (IDiffElement child : container.getChildren()) {
      if (child instanceof PatchFileDiffNode f && patchElement.equals(f.getPatchElement()))
        return f;
      if (child instanceof IDiffContainer c) {
        PatchFileDiffNode found = findFile(c, patchElement);
        if (found != null)
          return found;
      }
    }
    return null;
  }

  /** The review tree of the patch, with the actions of its context menu. */
  private class PatchPageInput extends PatchCompareEditorInput {
    private final Action openAction = new Action("Open In Compare &Editor") {
      @Override
      public void run() {
        if (getViewer() != null && getViewer().getSelection() instanceof IStructuredSelection ss)
          for (Object o : ss.toArray())
            openInCompareEditor(o);
      }
    };
    private final Action mergeAction = new Action("&Merge") {
      @Override
      public void run() {
        merge();
      }
    };
    private final Action markAsMergedAction = new Action("Mark &as Merged") {
      @Override
      public void run() {
        markAsMerged();
      }
    };

    PatchPageInput() {
      super(patcher, configuration);
    }

    @Override
    public Viewer createDiffViewer(Composite parent) {
      Viewer v = super.createDiffViewer(parent);
      if (v instanceof StructuredViewer sv)
        sv.addOpenListener(e -> {
          if (e.getSelection() instanceof IStructuredSelection ss)
            for (Object o : ss.toArray())
              openInCompareEditor(o);
        });
      // Link with Editor: the compare editor of the selected file is brought to the top (when the view is linked)
      v.addSelectionChangedListener(e -> {
        if (selectingForEditor || !(e.getSelection() instanceof IStructuredSelection ss) || ss.isEmpty())
          return;
        PatchFileDiffNode file = fileOf(ss.getFirstElement());
        if (file != null)
          CompareLinks.bringToTop(file.getPatchElement());
      });
      return v;
    }

    @Override
    protected void fillContextMenu(IMenuManager manager) {
      Object[] elements = getViewer() != null && getViewer().getSelection() instanceof IStructuredSelection ss
          ? ss.toArray() : new Object[0];
      boolean hunks = false;
      boolean allWithCompareInput = elements.length > 0;
      for (Object o : elements) {
        hunks |= o instanceof HunkDiffNode;
        allWithCompareInput &= o instanceof PatchFileDiffNode || o instanceof HunkDiffNode;
      }
      openAction.setEnabled(allWithCompareInput);
      mergeAction.setEnabled(elements.length > 0 && !hunks);
      markAsMergedAction.setEnabled(elements.length > 0 && !hunks);
      manager.add(openAction);
      manager.add(new Separator());
      manager.add(mergeAction);
      manager.add(markAsMergedAction);
    }

    /** Rebuilds the review tree from the diffs that remain in the patcher. */
    void rebuild() {
      resetRoot();
      buildTree();
      updateTree();
    }
  }
}
