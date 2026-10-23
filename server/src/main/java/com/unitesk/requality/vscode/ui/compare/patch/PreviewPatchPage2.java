/*******************************************************************************
 * Copyright (c) 2000, 2021 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.unitesk.requality.vscode.ui.compare.patch;

import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;
import java.util.regex.Pattern;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.compare.CompareConfiguration;
import com.unitesk.requality.vscode.ui.compare.CompareUIPlugin;
import com.unitesk.requality.vscode.ui.compare.ComparePreferencePage;
import com.unitesk.requality.vscode.ui.compare.patch.api.IHunk;
import com.unitesk.requality.vscode.ui.compare.patch.core.Hunk;
import com.unitesk.requality.vscode.ui.jface.preference.IPreferenceStore;
import com.unitesk.requality.vscode.ui.compare.patch.core.FilePatch2;
import com.unitesk.requality.vscode.ui.forms.events.ExpansionAdapter;
import com.unitesk.requality.vscode.ui.forms.events.ExpansionEvent;
import com.unitesk.requality.vscode.ui.forms.widgets.ExpandableComposite;
import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.port.eclipse.Activator;
import com.unitesk.requality.vscode.ui.jface.action.IMenuManager;
import com.unitesk.requality.vscode.ui.jface.action.Separator;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogSettings;
import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeSelection;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardPage;
import com.unitesk.requality.vscode.ui.runtime.Assert;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.NullProgressMonitor;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Combo;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;

/**
 * {@code org.eclipse.compare.internal.patch.PreviewPatchPage2}: the review page of the wizard: the file patches and their
 * hunks in the tree of {@link PatchCompareEditorInput}, the contents of the selected entry in the compare pane, and the
 * patch options (strip prefix segments, fuzz factor, reverse, ignore whitespace, rejects, excluded entries).
 */
public class PreviewPatchPage2 extends WizardPage {
  protected final static String PREVIEWPATCHPAGE_NAME = "PreviewPatchPage";

  final WorkspacePatcher fPatcher;
  private final CompareConfiguration fConfiguration;
  private PatchCompareEditorInput fInput;
  private Combo fStripPrefixSegments;
  private Text fFuzzField;
  private Label addedRemovedLines;
  private Action fExcludeAction;
  private Action fIncludeAction;
  private Action fIgnoreWhiteSpace;
  private Action fReversePatch;
  private Action fMoveAction;
  protected boolean pageRecalculate = true;
  /** {@code PreviewPatchPage2.EXPAND_PATCH_OPTIONS}: the dialog settings key of the expanded patch options. */
  private static final String EXPAND_PATCH_OPTIONS = "expandPatchOptions";
  /** {@code PreviewPatchPage2.GENERATE_REJECTS}: the dialog settings key of the generate reject file option. */
  private static final String GENERATE_REJECTS = "generateRejects";
  private Button generateRejects;
  private ExpandableComposite patchOptions;
  private IDialogSettings settings;

  public PreviewPatchPage2(WorkspacePatcher patcher, CompareConfiguration configuration) {
    super(PREVIEWPATCHPAGE_NAME, PatchMessages.PreviewPatchPage_title, null);
    setDescription(PatchMessages.PreviewPatchPage2_8);
    Assert.isNotNull(patcher);
    Assert.isNotNull(configuration);
    this.fPatcher = patcher;
    this.fConfiguration = configuration;
    this.fConfiguration.addPropertyChangeListener(event -> {
      if (event.getProperty().equals(CompareConfiguration.IGNORE_WHITESPACE)) {
        rebuildTree();
      }
    });
  }

  @Override
  public void createControl(Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);
    composite.setLayout(new GridLayout());
    composite.setLayoutData(new GridData(GridData.FILL_BOTH));

    fInput = new PatchCompareEditorInput(getPatcher(), getCompareConfiguration()) {
      @Override
      protected void fillContextMenu(IMenuManager manager) {
        if (isShowAll()) {
          manager.add(fIncludeAction);
        }
        manager.add(fExcludeAction);
        manager.add(new Separator());
        manager.add(fMoveAction);
      }
    };
    buildPatchOptionsGroup(composite);

    try {
      fInput.run(null);
    } catch (InterruptedException | InvocationTargetException e) {
      // ignore
    }

    Label label = new Label(composite, SWT.NONE);
    label.setText(PatchMessages.PreviewPatchPage2_9);

    Control c = fInput.createContents(composite);
    initializeActions();
    fInput.contributeDiffViewerToolbarItems(getContributedActions(), getPatcher().isWorkspacePatch());
    fInput.getViewer().addSelectionChangedListener(event -> {
      ISelection s = event.getSelection();
      if (s != null && !s.isEmpty()) {
        if (s instanceof IStructuredSelection ss) {
          updateActions(ss);
        }
      }
    });
    c.setLayoutData(new GridData(GridData.FILL_BOTH));

    addedRemovedLines = new Label(composite, SWT.NONE);
    addedRemovedLines.setLayoutData(new GridData(GridData.FILL_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING));

    setControl(composite);

    restoreWidgetValues();
  }

  /** {@code PreviewPatchPage2.restoreWidgetValues}: the expanded options and the reject file option of the last wizard. */
  private void restoreWidgetValues() {
    IDialogSettings dialogSettings = Activator.getDefault().getDialogSettings();
    settings = dialogSettings.getSection(PREVIEWPATCHPAGE_NAME);
    if (settings == null) {
      settings = dialogSettings.addNewSection(PREVIEWPATCHPAGE_NAME);
    }
    if (settings != null) {
      if (settings.get(EXPAND_PATCH_OPTIONS) != null) {
        patchOptions.setExpanded(settings.getBoolean(EXPAND_PATCH_OPTIONS));
      }
      if (settings.get(GENERATE_REJECTS) != null) {
        generateRejects.setSelection(settings.getBoolean(GENERATE_REJECTS));
        getPatcher().setGenerateRejectFile(generateRejects.getSelection());
      }
    }
  }

  /** {@code PreviewPatchPage2.saveWidgetValues}: called when the wizard finishes. */
  void saveWidgetValues() {
    if (settings != null) {
      settings.put(EXPAND_PATCH_OPTIONS, patchOptions.isExpanded());
      settings.put(GENERATE_REJECTS, generateRejects.getSelection());
    }
  }

  private void updateActions(IStructuredSelection ss) {
    fExcludeAction.setEnabled(false);
    fIncludeAction.setEnabled(false);
    for (Object element : ss) {
      if (element instanceof PatchDiffNode) {
        if (((PatchDiffNode) element).isEnabled()) {
          fExcludeAction.setEnabled(true);
        } else {
          fIncludeAction.setEnabled(true);
        }
      }
    }
  }

  private void updateEnablements() {
    boolean atLeastOneIsEnabled = false;
    if (fInput != null) {
      atLeastOneIsEnabled = fInput.hasResultToApply();
    }
    setPageComplete(atLeastOneIsEnabled);
  }

  private Action[] getContributedActions() {
    return new Action[] { fIgnoreWhiteSpace };
  }

  private void initializeActions() {
    fMoveAction = new Action(PatchMessages.PreviewPatchPage2_RetargetAction, null) {
      @Override
      public void run() {
        Shell shell = getShell();
        ISelection selection = fInput.getViewer().getSelection();
        PatchDiffNode node = null;
        if (selection instanceof IStructuredSelection ss) {
          if (ss.getFirstElement() instanceof PatchDiffNode) {
            node = (PatchDiffNode) ss.getFirstElement();
          }
        }
        if (node == null) {
          return;
        }
        final RetargetPatchElementDialog dialog = new RetargetPatchElementDialog(shell, fPatcher, node);
        int returnCode = dialog.open();
        if (returnCode == com.unitesk.requality.vscode.ui.jface.window.Window.OK) {
          rebuildTree();
        }
      }
    };
    fMoveAction.setToolTipText(PatchMessages.PreviewPatchPage2_RetargetTooltip);
    fMoveAction.setEnabled(true);

    fInput.getViewer().addSelectionChangedListener(event -> {
      IStructuredSelection sel = event.getStructuredSelection();
      Object obj = sel.getFirstElement();
      boolean enable = false;
      if (obj instanceof PatchProjectDiffNode) {
        enable = true;
      } else if (obj instanceof PatchFileDiffNode node) {
        enable = node.getDiffResult().getDiffProblem();
      } else if (obj instanceof HunkDiffNode) {
        enable = true;
      }
      fMoveAction.setEnabled(enable);
    });

    fExcludeAction = new Action(PatchMessages.PreviewPatchPage2_0) {
      @Override
      public void run() {
        ISelection selection = fInput.getViewer().getSelection();
        if (selection instanceof TreeSelection treeSelection) {
          Iterator<?> iter = treeSelection.iterator();
          while (iter.hasNext()) {
            Object obj = iter.next();
            if (obj instanceof PatchDiffNode node) {
              node.setEnabled(false);
            }
          }
          updateActions(treeSelection);
        }
        fInput.getViewer().refresh();
      }
    };
    fExcludeAction.setEnabled(true);

    fIncludeAction = new Action(PatchMessages.PreviewPatchPage2_1) {
      @Override
      public void run() {
        ISelection selection = fInput.getViewer().getSelection();
        if (selection instanceof TreeSelection treeSelection) {
          Iterator<?> iter = treeSelection.iterator();
          while (iter.hasNext()) {
            Object obj = iter.next();
            if (obj instanceof PatchDiffNode node) {
              node.setEnabled(true);
            }
          }
          updateActions(treeSelection);
        }
        fInput.getViewer().refresh();
      }
    };
    fIncludeAction.setEnabled(true);

    fIgnoreWhiteSpace = new Action(PatchMessages.PreviewPatchPage2_IgnoreWSAction,
        ImageDescriptor.fromResource("icons/compare/etool16/ignorews_edit.png")) {
      @Override
      public void run() {
        try {
          getContainer().run(false, true, monitor -> {
            monitor.beginTask(PatchMessages.PreviewPatchPage2_IgnoreWhitespace, IProgressMonitor.UNKNOWN);
            if (isChecked() != getPatcher().isIgnoreWhitespace()) {
              if (promptToRebuild(PatchMessages.PreviewPatchPage2_2)) {
                if (getPatcher().setIgnoreWhitespace(isChecked())) {
                  getCompareConfiguration().setProperty(CompareConfiguration.IGNORE_WHITESPACE,
                      Boolean.valueOf(isChecked()));
                }
              } else {
                fIgnoreWhiteSpace.setChecked(!isChecked());
              }
            }
            monitor.done();
          });
        } catch (InvocationTargetException | InterruptedException e) {
          // ignore
        }
      }
    };
    fIgnoreWhiteSpace.setChecked(false);
    fIgnoreWhiteSpace.setToolTipText(PatchMessages.PreviewPatchPage2_IgnoreWSTooltip);

    fReversePatch = new Action(PatchMessages.PreviewPatchPage_ReversePatch_text) {
      @Override
      public void run() {
        try {
          getContainer().run(true, true, monitor -> {
            monitor.beginTask(PatchMessages.PreviewPatchPage2_CalculateReverse, IProgressMonitor.UNKNOWN);
            if (isChecked() != getPatcher().isReversed()) {
              if (promptToRebuild(PatchMessages.PreviewPatchPage2_3)) {
                if (getPatcher().setReversed(isChecked())) {
                  rebuildTree();
                }
              } else {
                fReversePatch.setChecked(!isChecked());
              }
            }
            monitor.done();
          });
        } catch (InvocationTargetException | InterruptedException e) {
          // ignore
        }
      }
    };
    fReversePatch.setChecked(false);
    fReversePatch.setToolTipText(PatchMessages.PreviewPatchPage_ReversePatch_text);
  }

  @Override
  public void setVisible(boolean visible) {
    super.setVisible(visible);
    if (visible) {
      fillSegmentCombo();
      if (getPatcher().isGitPatch()) {
        int ignore = getPatcher().calculateStripGitPrefixSegments();
        fStripPrefixSegments.select(ignore);
        getPatcher().setStripPrefixSegments(ignore);
      }
      rebuildTree();
      updateEnablements();
      addedRemovedLines.setText(countLines());
      // expand the first tree item i.e. change
      fInput.selectFirstChange();
      getContainer().updateButtons();
      Button defaultButton = getShell().getDefaultButton();
      if (defaultButton != null) {
        defaultButton.setFocus();
      }
    }
  }

  /** Asks whether the manual changes may be discarded for the rebuild of the patch (VSCODE: the prompt is modal). */
  private boolean promptToRebuild(final String promptToConfirm) {
    final Control ctrl = getControl();
    final boolean[] result = new boolean[] { false };
    if (ctrl != null && !ctrl.isDisposed()) {
      try {
        fInput.saveChanges(null);
      } catch (CoreException e) {
        RLogger.logError(e);
      }
      result[0] = fInput.confirmRebuild(promptToConfirm);
    }
    return result[0];
  }

  private void rebuildTree() {
    final Control ctrl = getControl();
    if (ctrl != null && !ctrl.isDisposed()) {
      fInput.buildTree();
      updateEnablements();
    }
  }

  private void fillSegmentCombo() {
    if (getPatcher().isWorkspacePatch()) {
      fStripPrefixSegments.setEnabled(false);
    } else {
      fStripPrefixSegments.setEnabled(true);
      int length = 99;
      if (fStripPrefixSegments != null && pageRecalculate) {
        if (fStripPrefixSegments.getItemCount() > 1) {
          fStripPrefixSegments.remove(1, fStripPrefixSegments.getItemCount() - 1);
        }
        length = getPatcher().calculatePrefixSegmentCount();
        if (length != 99) {
          for (int k = 1; k < length; k++) {
            fStripPrefixSegments.add(Integer.toString(k));
          }
          fStripPrefixSegments.select(0);
          getPatcher().setStripPrefixSegments(0);
          pageRecalculate = false;
        }
      }
    }
  }

  private void buildPatchOptionsGroup(Composite parent) {
    patchOptions = new ExpandableComposite(parent, SWT.NONE,
        ExpandableComposite.TWISTIE | ExpandableComposite.CLIENT_INDENT);
    patchOptions.setText(PatchMessages.PreviewPatchPage_PatchOptions_title);
    patchOptions.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false, 3, 1));
    patchOptions.addExpansionListener(new ExpansionAdapter() {
      @Override
      public void expansionStateChanged(ExpansionEvent e) {
        parent.layout(true, true);
      }
    });

    Composite c = new Composite(patchOptions, SWT.NONE);
    patchOptions.setClient(c);
    patchOptions.setExpanded(true);
    GridLayout gl = new GridLayout();
    gl.numColumns = 3;
    c.setLayout(gl);
    c.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));

    createStripSegmentCombo(c);
    createShowMatchedToggle(c);
    createFuzzFactorChooser(c);
    createReversePatchToggle(c);
    createShowRemovedToggle(c);
    createGenerateRejectsToggle(c);

    final WorkspacePatcher patcher = getPatcher();
    if (fStripPrefixSegments != null) {
      fStripPrefixSegments.addSelectionListener(new SelectionAdapter() {
        @Override
        public void widgetSelected(SelectionEvent e) {
          if (patcher.getStripPrefixSegments() != getStripPrefixSegments()) {
            if (promptToRebuild(PatchMessages.PreviewPatchPage2_4)) {
              if (patcher.setStripPrefixSegments(getStripPrefixSegments())) {
                rebuildTree();
              }
            }
          }
        }
      });
    }

    fFuzzField.addModifyListener(e -> {
      if (patcher.getFuzz() != getFuzzFactor()) {
        if (promptToRebuild(PatchMessages.PreviewPatchPage2_5)) {
          if (patcher.setFuzz(getFuzzFactor())) {
            rebuildTree();
          }
        } else {
          fFuzzField.setText(Integer.toString(patcher.getFuzz()));
        }
      }
    });
  }

  private void createFuzzFactorChooser(Composite parent) {
    final WorkspacePatcher patcher = getPatcher();
    Composite pair = new Composite(parent, SWT.NONE);
    GridLayout gl = new GridLayout();
    gl.numColumns = 3;
    gl.marginHeight = gl.marginWidth = 0;
    pair.setLayout(gl);
    pair.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));

    Label l = new Label(pair, SWT.NONE);
    l.setText(PatchMessages.PreviewPatchPage_FuzzFactor_text);
    l.setToolTipText(PatchMessages.PreviewPatchPage_FuzzFactor_tooltip);
    l.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_CENTER | GridData.HORIZONTAL_ALIGN_BEGINNING
        | GridData.GRAB_HORIZONTAL));

    fFuzzField = new Text(pair, SWT.BORDER);
    fFuzzField.setText("0");
    GridData gd = new GridData(GridData.VERTICAL_ALIGN_CENTER | GridData.HORIZONTAL_ALIGN_END);
    gd.widthHint = 30;
    fFuzzField.setLayoutData(gd);

    Button b = new Button(pair, SWT.PUSH);
    b.setText(PatchMessages.PreviewPatchPage_GuessFuzz_text);
    b.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        if (promptToRebuild(PatchMessages.PreviewPatchPage2_6)) {
          patcher.setFuzz(-1);
          int fuzz = guessFuzzFactor(patcher);
          if (fuzz >= 0) {
            fFuzzField.setText(Integer.toString(fuzz));
          }
        }
      }
    });
    b.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_CENTER));
  }

  private void createGenerateRejectsToggle(Composite pair) {
    generateRejects = new Button(pair, SWT.CHECK);
    generateRejects.setText(PatchMessages.HunkMergePage_GenerateRejectFile);
    GridData gd = new GridData(GridData.VERTICAL_ALIGN_CENTER | GridData.HORIZONTAL_ALIGN_BEGINNING
        | GridData.GRAB_HORIZONTAL);
    generateRejects.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        getPatcher().setGenerateRejectFile(generateRejects.getSelection());
      }
    });
    generateRejects.setSelection(false);
    generateRejects.setLayoutData(gd);
  }

  private void createShowRemovedToggle(Composite pair) {
    final Button showRemoved = new Button(pair, SWT.CHECK);
    showRemoved.setText(PatchMessages.PreviewPatchPage2_7);
    GridData gd = new GridData(GridData.VERTICAL_ALIGN_CENTER | GridData.HORIZONTAL_ALIGN_BEGINNING
        | GridData.GRAB_HORIZONTAL);
    showRemoved.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        fInput.setShowAll(showRemoved.getSelection());
        fInput.updateTree();
      }
    });
    showRemoved.setSelection(fInput.isShowAll());
    showRemoved.setLayoutData(gd);
  }

  private void createReversePatchToggle(Composite pair) {
    final Button reversePatch = new Button(pair, SWT.CHECK);
    reversePatch.setText(PatchMessages.PreviewPatchPage_ReversePatch_text);
    GridData gd = new GridData(GridData.VERTICAL_ALIGN_CENTER | GridData.HORIZONTAL_ALIGN_BEGINNING
        | GridData.GRAB_HORIZONTAL);
    reversePatch.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        if (fReversePatch != null) {
          fReversePatch.setChecked(reversePatch.getSelection());
          fReversePatch.run();
          if (fReversePatch.isChecked() != reversePatch.getSelection()) {
            reversePatch.setSelection(fReversePatch.isChecked());
          }
        }
      }
    });
    reversePatch.setSelection(getPatcher().isReversed());
    reversePatch.setLayoutData(gd);
  }

  private void createStripSegmentCombo(Composite parent) {
    final WorkspacePatcher patcher = getPatcher();
    Composite pair = new Composite(parent, SWT.NONE);
    GridLayout gl = new GridLayout();
    gl.numColumns = 2;
    gl.marginHeight = gl.marginWidth = 0;
    pair.setLayout(gl);
    pair.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));

    Label l = new Label(pair, SWT.NONE);
    l.setText(PatchMessages.PreviewPatchPage_IgnoreSegments_text);
    l.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_CENTER | GridData.HORIZONTAL_ALIGN_BEGINNING));

    fStripPrefixSegments = new Combo(pair, SWT.DROP_DOWN | SWT.READ_ONLY);
    int prefixCnt = patcher.getStripPrefixSegments();
    String prefix = Integer.toString(prefixCnt);
    fStripPrefixSegments.add(prefix);
    fStripPrefixSegments.setText(prefix);
    fStripPrefixSegments.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_CENTER | GridData.HORIZONTAL_ALIGN_BEGINNING
        | GridData.GRAB_HORIZONTAL));
  }

  private void createShowMatchedToggle(Composite parent) {
    final Button showMatched = new Button(parent, SWT.CHECK);
    showMatched.setText(PatchMessages.PreviewPatchPage2_ShowMatched);
    GridData gd = new GridData(GridData.VERTICAL_ALIGN_CENTER | GridData.HORIZONTAL_ALIGN_BEGINNING
        | GridData.GRAB_HORIZONTAL);
    showMatched.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        fInput.setShowMatched(showMatched.getSelection());
        rebuildTree();
      }
    });
    showMatched.setSelection(fInput.isShowMatched());
    showMatched.setLayoutData(gd);
  }

  public int getFuzzFactor() {
    int fuzzFactor = 0;
    if (fFuzzField != null) {
      String s = fFuzzField.getText();
      try {
        fuzzFactor = Integer.parseInt(s);
      } catch (NumberFormatException ex) {
        // keep 0
      }
    }
    return fuzzFactor;
  }

  public int getStripPrefixSegments() {
    int stripPrefixSegments = 0;
    if (fStripPrefixSegments != null) {
      String s = fStripPrefixSegments.getText();
      try {
        stripPrefixSegments = Integer.parseInt(s);
      } catch (NumberFormatException ex) {
        // keep 0
      }
    }
    return stripPrefixSegments;
  }

  private int guessFuzzFactor(final WorkspacePatcher patcher) {
    return patcher.guessFuzzFactor(new NullProgressMonitor());
  }

  /** Saves the contents merged by hand in the review tree (before the patch is applied). */
  public void ensureContentsSaved() {
    try {
      fInput.saveChanges(new NullProgressMonitor());
    } catch (CoreException e) {
      RLogger.logError(e);
    }
  }

  public WorkspacePatcher getPatcher() {
    return fPatcher;
  }

  public CompareConfiguration getCompareConfiguration() {
    return fConfiguration;
  }

  /** The count of the added and removed lines of the patch. */
  /** {@code PreviewPatchPage2.countLines}: the added and removed lines, by the regular expressions of the preferences. */
  private String countLines() {
    int added = 0, removed = 0;
    IPreferenceStore store = CompareUIPlugin.getPreferenceStore();
    String addedLinesRegex = store.getString(ComparePreferencePage.ADDED_LINES_REGEX);
    String removedLinesRegex = store.getString(ComparePreferencePage.REMOVED_LINES_REGEX);
    if ((addedLinesRegex == null || "".equals(addedLinesRegex))
        && (removedLinesRegex == null || "".equals(removedLinesRegex))) {
      fPatcher.countLines();
      FilePatch2[] fileDiffs = fPatcher.getDiffs();
      for (FilePatch2 fileDiff : fileDiffs) {
        added += fileDiff.getAddedLines();
        removed += fileDiff.getRemovedLines();
      }
    } else {
      Pattern addedPattern = Pattern.compile(addedLinesRegex == null ? "" : addedLinesRegex);
      Pattern removedPattern = Pattern.compile(removedLinesRegex == null ? "" : removedLinesRegex);
      for (FilePatch2 fileDiff : fPatcher.getDiffs()) {
        for (IHunk hunk : fileDiff.getHunks()) {
          for (String line : ((Hunk) hunk).getLines()) {
            if (addedPattern.matcher(line).find()) {
              added++;
            }
            if (removedPattern.matcher(line).find()) {
              removed++;
            }
          }
        }
      }
    }
    return PatchMessages.bind(PatchMessages.PreviewPatchPage2_AddedRemovedLines, added + "", removed + "");
  }
}
