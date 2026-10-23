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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.ArrayList;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.compare.patch.api.IFilePatch2;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogSettings;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.jface.wizard.IWizardPage;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardPage;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IFolder;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.dnd.Clipboard;
import com.unitesk.requality.vscode.ui.swt.dnd.TextTransfer;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.events.ShellAdapter;
import com.unitesk.requality.vscode.ui.swt.events.ShellEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Combo;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.FileDialog;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.navigator.ResourceComparator;
import com.unitesk.requality.vscode.ui.workbench.model.WorkbenchContentProvider;
import com.unitesk.requality.vscode.ui.workbench.model.WorkbenchLabelProvider;

/**
 * {@code org.eclipse.compare.internal.patch.InputPatchPage}: the patch input of the wizard: the clipboard, a patch file
 * (with its Browse... button), a URL, or a file of the workspace. The patch is read and parsed by the wizard's patcher.
 */
public class InputPatchPage extends WizardPage {
  protected static final int SIZING_TEXT_FIELD_WIDTH = 250;
  protected static final int COMBO_HISTORY_LENGTH = 5;

  private final static String PAGE_NAME = "PatchWizardPage1";

  protected final static int CLIPBOARD = 1;
  protected final static int FILE = 2;
  protected final static int WORKSPACE = 3;
  protected final static int URL = 4;

  protected final static String INPUTPATCHPAGE_NAME = "InputPatchPage";

  /** {@code InputPatchPage.STORE_PATCH_FILES_ID}: the dialog settings keys of the page. */
  private final static String STORE_PATCH_FILES_ID = PAGE_NAME + ".PATCH_FILES";
  private final static String STORE_PATCH_URLS_ID = PAGE_NAME + ".PATCH_URLS";
  private final static String STORE_INPUT_METHOD_ID = PAGE_NAME + ".INPUT_METHOD";
  private final static String STORE_WORKSPACE_PATH_ID = PAGE_NAME + ".WORKSPACE_PATH";

  private boolean fShowError = false;
  private String fPatchSource;
  private boolean fPatchRead = false;
  private final PatchWizard fPatchWizard;
  private final ActivationListener fActivationListener = new ActivationListener();

  private Button fUseClipboardButton;
  private Combo fPatchFileNameField;
  private Button fPatchFileBrowseButton;
  private Button fUsePatchFileButton;
  private Button fUseWorkspaceButton;
  private Button fUseURLButton;
  private Combo fPatchURLField;
  private Label fWorkspaceSelectLabel;
  private TreeViewer fTreeViewer;

  class ActivationListener extends ShellAdapter {
    @Override
    public void shellActivated(ShellEvent e) {
      fShowError = true;
      switch (getInputMethod()) {
        case FILE:
          fShowError = (!fPatchFileNameField.getText().equals(""));
          break;
        case URL:
          fShowError = (!fPatchURLField.getText().equals(""));
          break;
        case WORKSPACE:
          fShowError = (!fTreeViewer.getSelection().isEmpty());
          break;
        default:
          break;
      }
      updateWidgetEnablements();
    }
  }

  public InputPatchPage(PatchWizard pw) {
    super(INPUTPATCHPAGE_NAME, PatchMessages.InputPatchPage_title, null);
    fPatchWizard = pw;
    setMessage(PatchMessages.InputPatchPage_message);
  }

  @Override
  public void createControl(Composite parent) {
    Composite composite = new Composite(parent, SWT.NULL);
    composite.setLayout(new GridLayout());
    composite.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_FILL | GridData.HORIZONTAL_ALIGN_FILL));
    setControl(composite);

    buildPatchFileGroup(composite);
    // by default, whatever was used last was selected or default to File if nothing has been selected
    restoreWidgetValues();
    // see if there are any better options presently selected (i.e workspace or clipboard or URL from clipboard)
    adjustToCurrentTarget();
    fShowError = false;
    clearErrorMessage();
    updateWidgetEnablements();

    Shell shell = getShell();
    shell.addShellListener(fActivationListener);
  }

  @Override
  public IWizardPage getNextPage() {
    if (!checkPageComplete()) {
      return this;
    }
    WorkspacePatcher patcher = ((PatchWizard) getWizard()).getPatcher();
    int guess = 0; // guessPrefix(diffs);
    patcher.setStripPrefixSegments(guess);

    if (patcher.isWorkspacePatch()) {
      IWizardPage page = super.getNextPage();
      if (page.getName().equals(PatchTargetPage.PATCHTARGETPAGE_NAME)) {
        return page.getNextPage();
      }
    }
    if (patcher.isGitPatch()) {
      IWizardPage page = super.getNextPage();
      if (page.getName().equals(PatchTargetPage.PATCHTARGETPAGE_NAME)) {
        patcher.setTarget(ResourcesPlugin.getWorkspace().getRoot());
        return page.getNextPage();
      }
    }
    return super.getNextPage();
  }

  boolean checkPageComplete() {
    WorkspacePatcher patcher = ((PatchWizard) getWizard()).getPatcher();
    readInPatch();
    IFilePatch2[] diffs = patcher.getDiffs();
    if (diffs == null || diffs.length == 0) {
      String format = PatchMessages.InputPatchPage_NoDiffsFound_format;
      String message = MessageFormat.format(format, fPatchSource);
      MessageDialog.openInformation(null, PatchMessages.InputPatchPage_PatchErrorDialog_title, message);
      return false;
    }
    return true;
  }

  /** Reads the patch of the selected input into the wizard's patcher (once). */
  public void readInPatch() {
    if (fPatchRead) {
      return;
    }
    WorkspacePatcher patcher = ((PatchWizard) getWizard()).getPatcher();
    try (Reader reader = createReader()) {
      if (reader != null) {
        try {
          patcher.parse(new BufferedReader(reader));
          fPatchWizard.patchReadIn();
          fPatchRead = true;
        } catch (Exception ex) {
          setPageComplete(false);
        }
      }
    } catch (IOException closeException) {
      // ignored
    }
  }

  private Reader createReader() {
    int inputMethod = getInputMethod();
    if (inputMethod == CLIPBOARD) {
      Control c = getControl();
      if (c != null) {
        Clipboard clipboard = new Clipboard(c.getDisplay());
        Object o = clipboard.getContents(TextTransfer.getInstance());
        clipboard.dispose();
        if (o instanceof String) {
          return new StringReader((String) o);
        }
      }
      fPatchSource = PatchMessages.InputPatchPage_Clipboard_title;
    } else if (inputMethod == FILE) {
      String patchFilePath = getPatchFilePath();
      if (patchFilePath != null) {
        try {
          return new FileReader(patchFilePath);
        } catch (FileNotFoundException ex) {
          MessageDialog.openError(null, PatchMessages.InputPatchPage_PatchErrorDialog_title,
              PatchMessages.InputPatchPage_PatchFileNotFound_message);
        }
      }
      fPatchSource = PatchMessages.InputPatchPage_PatchFile_title;
    } else if (inputMethod == URL) {
      String patchFileURL = fPatchURLField.getText();
      if (patchFileURL != null) {
        try {
          String contents = Utilities.getURLContents(new URL(patchFileURL));
          if (contents != null) {
            return new StringReader(contents);
          }
        } catch (MalformedURLException e) {
          // ignore
        } catch (IOException e) {
          RLogger.logError(e);
        }
      }
      fPatchSource = PatchMessages.InputPatchPage_URL_title;
    } else if (inputMethod == WORKSPACE) {
      IResource[] resources = Utilities.getResources(fTreeViewer.getSelection());
      IResource patchFile = resources.length > 0 ? resources[0] : null;
      if (patchFile != null) {
        if (patchFile.getLocation() == null) {
          MessageDialog.openError(null, PatchMessages.InputPatchPage_PatchErrorDialog_title,
              PatchMessages.InputPatchPage_PatchFileNotFound_message);
        } else {
          try {
            return new FileReader(patchFile.getLocation().toFile());
          } catch (FileNotFoundException ex) {
            MessageDialog.openError(null, PatchMessages.InputPatchPage_PatchErrorDialog_title,
                PatchMessages.InputPatchPage_PatchFileNotFound_message);
          }
        }
      }
      fPatchSource = PatchMessages.InputPatchPage_WorkspacePatch_title;
    }
    return null;
  }

  @Override
  public boolean canFlipToNextPage() {
    return isPageComplete();
  }

  private void setEnablePatchFile(boolean enable) {
    fPatchFileNameField.setEnabled(enable);
    fPatchFileBrowseButton.setEnabled(enable);
  }

  private void setEnableWorkspacePatch(boolean enable) {
    fWorkspaceSelectLabel.setEnabled(enable);
    fTreeViewer.getTree().setEnabled(enable);
  }

  private void setEnableURLPatch(boolean enable) {
    fPatchURLField.setEnabled(enable);
  }

  private void buildPatchFileGroup(Composite parent) {
    final Composite composite = new Composite(parent, SWT.NULL);
    GridLayout gridLayout = new GridLayout();
    gridLayout.numColumns = 3;
    composite.setLayout(gridLayout);
    composite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

    GridData gd = new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
    gd.horizontalSpan = 3;

    fUseClipboardButton = new Button(composite, SWT.RADIO);
    fUseClipboardButton.setText(PatchMessages.InputPatchPage_UseClipboardButton_text);
    fUseClipboardButton.setLayoutData(gd);

    fUsePatchFileButton = new Button(composite, SWT.RADIO);
    fUsePatchFileButton.setText(PatchMessages.InputPatchPage_FileButton_text);

    fPatchFileNameField = new Combo(composite, SWT.BORDER);
    gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.widthHint = SIZING_TEXT_FIELD_WIDTH;
    fPatchFileNameField.setLayoutData(gd);

    fPatchFileBrowseButton = new Button(composite, SWT.PUSH);
    fPatchFileBrowseButton.setText(PatchMessages.InputPatchPage_ChooseFileButton_text);
    GridData data = new GridData(GridData.HORIZONTAL_ALIGN_FILL);
    fPatchFileBrowseButton.setLayoutData(data);

    fUseURLButton = new Button(composite, SWT.RADIO);
    fUseURLButton.setText(PatchMessages.InputPatchPage_URLButton_text);

    fPatchURLField = new Combo(composite, SWT.BORDER);
    gd = new GridData(GridData.FILL_HORIZONTAL);
    gd.horizontalSpan = 2;
    fPatchURLField.setLayoutData(gd);

    fUseWorkspaceButton = new Button(composite, SWT.RADIO);
    fUseWorkspaceButton.setText(PatchMessages.InputPatchPage_UseWorkspaceButton_text);
    gd = new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
    fUseWorkspaceButton.setLayoutData(gd);

    addWorkspaceControls(parent);

    fUseClipboardButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        if (!fUseClipboardButton.getSelection()) {
          return;
        }
        clearErrorMessage();
        fShowError = true;
        int state = getInputMethod();
        setEnablePatchFile(state == FILE);
        setEnableURLPatch(state == URL);
        setEnableWorkspacePatch(state == WORKSPACE);
        updateWidgetEnablements();
        fPatchRead = false;
      }
    });

    fUsePatchFileButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        if (!fUsePatchFileButton.getSelection()) {
          return;
        }
        clearErrorMessage();
        fShowError = (!fPatchFileNameField.getText().equals(""));
        int state = getInputMethod();
        setEnablePatchFile(state == FILE);
        setEnableURLPatch(state == URL);
        setEnableWorkspacePatch(state == WORKSPACE);
        updateWidgetEnablements();
        fPatchRead = false;
      }
    });

    fPatchFileNameField.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        updateWidgetEnablements();
      }
    });

    fPatchFileNameField.addModifyListener(e -> {
      clearErrorMessage();
      fShowError = true;
      fPatchRead = false;
      updateWidgetEnablements();
    });

    fPatchFileBrowseButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        clearErrorMessage();
        fShowError = true;
        handlePatchFileBrowseButtonPressed();
        updateWidgetEnablements();
      }
    });

    fUseURLButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        clearErrorMessage();
        fShowError = (!fPatchURLField.getText().equals(""));
        int state = getInputMethod();
        setEnablePatchFile(state == FILE);
        setEnableURLPatch(state == URL);
        setEnableWorkspacePatch(state == WORKSPACE);
        updateWidgetEnablements();
      }
    });

    fPatchURLField.addModifyListener(e -> {
      clearErrorMessage();
      fShowError = true;
      fPatchRead = false;
      updateWidgetEnablements();
    });

    fUseWorkspaceButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        if (!fUseWorkspaceButton.getSelection()) {
          return;
        }
        clearErrorMessage();
        fShowError = (!fTreeViewer.getSelection().isEmpty());
        int state = getInputMethod();
        setEnablePatchFile(state == FILE);
        setEnableURLPatch(state == URL);
        setEnableWorkspacePatch(state == WORKSPACE);
        updateWidgetEnablements();
        fPatchRead = false;
      }
    });

    fTreeViewer.addSelectionChangedListener(e -> {
      clearErrorMessage();
      updateWidgetEnablements();
    });

    fTreeViewer.addDoubleClickListener(event -> {
      ISelection selection = event.getSelection();
      if (selection instanceof TreeSelection treeSel) {
        Object res = treeSel.getFirstElement();
        if (res != null) {
          if (res instanceof IProject || res instanceof IFolder) {
            if (fTreeViewer.getExpandedState(res)) {
              fTreeViewer.collapseToLevel(res, 1);
            } else {
              fTreeViewer.expandToLevel(res, 1);
            }
          } else if (res instanceof IFile) {
            fPatchWizard.showPage(getNextPage());
          }
        }
      }
    });
  }

  private void addWorkspaceControls(Composite composite) {
    Composite newComp = new Composite(composite, SWT.NONE);
    GridLayout layout = new GridLayout(1, false);
    layout.marginLeft = 16; // align w/ label of check button
    newComp.setLayout(layout);
    newComp.setLayoutData(new GridData(GridData.FILL_BOTH));

    fWorkspaceSelectLabel = new Label(newComp, SWT.LEFT);
    fWorkspaceSelectLabel.setText(PatchMessages.InputPatchPage_WorkspaceSelectPatch_text);

    fTreeViewer = new TreeViewer(newComp, SWT.BORDER);
    GridData layoutData = new GridData(SWT.FILL, SWT.FILL, true, true);
    layoutData.heightHint = 300;
    fTreeViewer.getTree().setLayoutData(layoutData);
    fTreeViewer.setLabelProvider(new WorkbenchLabelProvider());
    fTreeViewer.setContentProvider(new WorkbenchContentProvider());
    fTreeViewer.setComparator(new ResourceComparator(ResourceComparator.NAME));
    fTreeViewer.setInput(ResourcesPlugin.getWorkspace().getRoot());
  }

  private void updateWidgetEnablements() {
    String error = null;
    boolean gotPatch = false;
    int inputMethod = getInputMethod();
    if (inputMethod == CLIPBOARD) {
      Control c = getControl();
      if (c != null) {
        Clipboard clipboard = new Clipboard(c.getDisplay());
        Object o = clipboard.getContents(TextTransfer.getInstance());
        clipboard.dispose();
        if (o instanceof String) {
          String s = ((String) o).trim();
          if (s.length() > 0) {
            gotPatch = true;
          } else {
            error = PatchMessages.InputPatchPage_ClipboardIsEmpty_message;
          }
        } else {
          error = PatchMessages.InputPatchPage_NoTextInClipboard_message;
        }
      } else {
        error = PatchMessages.InputPatchPage_CouldNotReadClipboard_message;
      }
    } else if (inputMethod == FILE) {
      String path = fPatchFileNameField.getText();
      if (path != null && path.length() > 0) {
        File file = new File(path);
        gotPatch = file.exists() && file.isFile() && file.length() > 0;
        if (!gotPatch) {
          error = PatchMessages.InputPatchPage_CannotLocatePatch_message + path;
        }
      } else {
        error = PatchMessages.InputPatchPage_NoFileName_message;
      }
    } else if (inputMethod == URL) {
      String urlText = fPatchURLField.getText();
      if (urlText != null) {
        try {
          new URL(urlText);
          gotPatch = true;
        } catch (MalformedURLException e) {
          error = PatchMessages.InputPatchPage_MalformedURL;
        }
      } else {
        error = PatchMessages.InputPatchPage_NoURL;
      }
    } else if (inputMethod == WORKSPACE) {
      IResource[] resources = Utilities.getResources(fTreeViewer.getSelection());
      if (resources != null && resources.length > 0) {
        IResource patchFile = resources[0];
        if (patchFile != null && patchFile.getType() == IResource.FILE) {
          IPath location = patchFile.getLocation();
          if (location == null) {
            error = PatchMessages.InputPatchPage_PatchFileNotFound_message;
          } else {
            File actualFile = location.toFile();
            gotPatch = actualFile.exists() && actualFile.isFile() && actualFile.length() > 0;
            if (!gotPatch) {
              error = PatchMessages.InputPatchPage_FileSelectedNotPatch_message;
            }
          }
        }
      } else {
        error = PatchMessages.InputPatchPage_NoFileName_message;
      }
    }
    setPageComplete(gotPatch);
    if (fShowError) {
      setErrorMessage(error);
    }
  }

  protected void handlePatchFileBrowseButtonPressed() {
    FileDialog dialog = new FileDialog(getShell(), SWT.NONE);
    dialog.setText(PatchMessages.InputPatchPage_SelectPatchFileDialog_title);
    String patchFilePath = getPatchFilePath();
    if (patchFilePath != null) {
      int lastSegment = patchFilePath.lastIndexOf(File.separatorChar);
      if (lastSegment > 0) {
        patchFilePath = patchFilePath.substring(0, lastSegment);
      }
    }
    dialog.setFilterPath(patchFilePath);
    String res = dialog.open();
    if (res == null) {
      return;
    }
    patchFilePath = dialog.getFileName();
    IPath filterPath = Path.fromOSString(dialog.getFilterPath());
    IPath path = filterPath.append(patchFilePath).makeAbsolute();
    patchFilePath = path.toOSString();
    fPatchFileNameField.setText(patchFilePath);
  }

  /** {@code InputPatchPage.restoreWidgetValues}: the input and the histories of the last wizard that finished. */
  private void restoreWidgetValues() {
    int inputMethod = FILE;
    IDialogSettings settings = getDialogSettings();
    if (settings != null) {
      try {
        inputMethod = settings.getInt(STORE_INPUT_METHOD_ID);
      } catch (NumberFormatException ex) {
        // OK - no value stored in settings; just use FILE
      }
      // set filenames history
      String[] sourceNames = settings.getArray(STORE_PATCH_FILES_ID);
      if (sourceNames != null) {
        for (String sourceName : sourceNames) {
          if (sourceName != null && sourceName.length() > 0) {
            fPatchFileNameField.add(sourceName);
          }
        }
      }
      // set patch file path
      String patchFilePath = settings.get(STORE_PATCH_FILES_ID);
      if (patchFilePath != null) {
        setSourceName(patchFilePath);
      }
      // set URLs history
      String[] sourceURLs = settings.getArray(STORE_PATCH_URLS_ID);
      if (sourceURLs != null) {
        for (String sourceURL : sourceURLs) {
          if (sourceURL != null && sourceURL.length() > 0) {
            fPatchURLField.add(sourceURL);
          }
        }
      }
      // If the previous apply patch was used with a clipboard, we need to check if there is a valid patch on the
      // clipboard. This will be done in adjustToCurrentTarget() so just set it to FILE now and, if there exists a patch
      // on the clipboard, then clipboard will be selected automatically
      if (inputMethod == CLIPBOARD) {
        inputMethod = FILE;
        fPatchFileNameField.deselectAll();
      }
      // set the workspace patch selection
      String workspaceSetting = settings.get(STORE_WORKSPACE_PATH_ID);
      if (workspaceSetting != null && workspaceSetting.length() > 0) {
        // See if this resource still exists in the workspace
        try {
          IPath path = Path.fromOSString(workspaceSetting);
          IFile targetFile = ResourcesPlugin.getWorkspace().getRoot().getFile(path);
          if (fTreeViewer != null && targetFile.exists()) {
            fTreeViewer.expandToLevel(targetFile, 0);
            fTreeViewer.setSelection(new StructuredSelection(targetFile));
          }
        } catch (RuntimeException e) {
          // Ignore. The setting was invalid
        }
      } else {
        // check to see if the current input is set to workspace - if it is switch it back to file since there is no
        // corresponding element to go along with the tree viewer
        if (inputMethod == WORKSPACE) {
          inputMethod = FILE;
        }
      }
    }
    // set radio buttons state
    setInputButtonState(inputMethod);
  }

  /** {@code InputPatchPage.saveWidgetValues}: when the wizard finishes, the input and the histories are remembered. */
  void saveWidgetValues() {
    IDialogSettings settings = getDialogSettings();
    if (settings != null) {
      settings.put(STORE_INPUT_METHOD_ID, getInputMethod());
      settings.put(STORE_PATCH_FILES_ID, getPatchFilePath());
      // update source names history
      String[] sourceNames = settings.getArray(STORE_PATCH_FILES_ID);
      if (sourceNames == null) {
        sourceNames = new String[0];
      }
      sourceNames = addToHistory(sourceNames, getPatchFilePath());
      settings.put(STORE_PATCH_FILES_ID, sourceNames);
      // update source URLs history
      String[] sourceURLs = settings.getArray(STORE_PATCH_URLS_ID);
      if (sourceURLs == null) {
        sourceURLs = new String[0];
      }
      sourceURLs = addToHistory(sourceURLs, fPatchURLField.getText());
      settings.put(STORE_PATCH_URLS_ID, sourceURLs);
      // save the workspace selection
      settings.put(STORE_WORKSPACE_PATH_ID, getWorkspacePath());
    }
  }

  /** {@code InputPatchPage.getWorkspacePath}: the full path of the selected workspace file, or empty. */
  private String getWorkspacePath() {
    if (fTreeViewer != null) {
      IResource[] resources = Utilities.getResources(fTreeViewer.getSelection());
      if (resources.length > 0) {
        return resources[0].getFullPath().toString();
      }
    }
    return "";
  }

  protected void setSourceName(String path) {
    if (path.length() > 0) {
      String[] currentItems = fPatchFileNameField.getItems();
      int selectionIndex = -1;
      for (int i = 0; i < currentItems.length; i++) {
        if (currentItems[i].equals(path)) {
          selectionIndex = i;
        }
      }
      if (selectionIndex < 0) { // not found in history
        int oldLength = currentItems.length;
        String[] newItems = new String[oldLength + 1];
        System.arraycopy(currentItems, 0, newItems, 0, oldLength);
        newItems[oldLength] = path;
        fPatchFileNameField.setItems(newItems);
        selectionIndex = oldLength;
      }
      fPatchFileNameField.select(selectionIndex);
    }
  }

  /** Adjusts the input to the target of the wizard: a workspace patch file, the clipboard, or a URL. */
  private boolean adjustToCurrentTarget() {
    IResource patchTarget = fPatchWizard.getTarget();
    if (patchTarget instanceof IFile && patchTarget.getLocation() != null) {
      try (Reader reader = new FileReader(patchTarget.getLocation().toFile())) {
        if (isPatchFile(reader)) {
          setInputButtonState(WORKSPACE);
          if (fTreeViewer != null && patchTarget.exists()) {
            fTreeViewer.expandToLevel(patchTarget, 0);
            fTreeViewer.setSelection(new StructuredSelection(patchTarget));
          }
          return true;
        }
      } catch (FileNotFoundException ex) {
        // not a patch file
      } catch (IOException x) {
        // ignored
      }
    }
    Control c = getControl();
    if (c != null) {
      Clipboard clipboard = new Clipboard(c.getDisplay());
      Object o = clipboard.getContents(TextTransfer.getInstance());
      clipboard.dispose();
      if (o instanceof String s) {
        try (Reader reader = new StringReader(s)) {
          if (isPatchFile(reader)) {
            setInputButtonState(CLIPBOARD);
            return true;
          }
          try {
            new URL(s);
            setInputButtonState(URL);
            fPatchURLField.setText(s);
            return true;
          } catch (MalformedURLException e) {
            // not an URL
          }
        } catch (IOException closeException) {
          // ignored
        }
      }
    }
    return false;
  }

  private boolean isPatchFile(Reader reader) {
    WorkspacePatcher patcher = ((PatchWizard) getWizard()).getPatcher();
    try {
      patcher.parse(new BufferedReader(reader));
    } catch (Exception ex) {
      return false;
    }
    IFilePatch2[] diffs = patcher.getDiffs();
    if (diffs == null || diffs.length == 0) {
      return false;
    }
    return true;
  }

  private void clearErrorMessage() {
    setErrorMessage(null);
  }

  private void setInputButtonState(int state) {
    switch (state) {
      case CLIPBOARD:
        fUseClipboardButton.setSelection(true);
        fUsePatchFileButton.setSelection(false);
        fUseURLButton.setSelection(false);
        fUseWorkspaceButton.setSelection(false);
        break;
      case FILE:
        fUseClipboardButton.setSelection(false);
        fUsePatchFileButton.setSelection(true);
        fUseURLButton.setSelection(false);
        fUseWorkspaceButton.setSelection(false);
        break;
      case URL:
        fUseClipboardButton.setSelection(false);
        fUsePatchFileButton.setSelection(false);
        fUseURLButton.setSelection(true);
        fUseWorkspaceButton.setSelection(false);
        break;
      case WORKSPACE:
        fUseClipboardButton.setSelection(false);
        fUsePatchFileButton.setSelection(false);
        fUseURLButton.setSelection(false);
        fUseWorkspaceButton.setSelection(true);
        break;
      default:
        throw new IllegalArgumentException(Integer.toString(state));
    }
    setEnablePatchFile(state == FILE);
    setEnableWorkspacePatch(state == WORKSPACE);
    setEnableURLPatch(state == URL);
  }

  protected int getInputMethod() {
    if (fUseClipboardButton.getSelection()) {
      return CLIPBOARD;
    }
    if (fUsePatchFileButton.getSelection()) {
      return FILE;
    }
    if (fUseURLButton.getSelection()) {
      return URL;
    }
    return WORKSPACE;
  }

  private String getPatchFilePath() {
    if (fPatchFileNameField != null) {
      return fPatchFileNameField.getText();
    }
    return "";
  }

  /** The history of a combo: the new entry first, at most {@link #COMBO_HISTORY_LENGTH} entries. */
  protected static String[] addToHistory(String[] history, String newEntry) {
    ArrayList<String> l = new ArrayList<>(Arrays.asList(history));
    l.remove(newEntry);
    l.add(0, newEntry);
    if (l.size() > COMBO_HISTORY_LENGTH) {
      l.remove(COMBO_HISTORY_LENGTH);
    }
    return l.toArray(new String[l.size()]);
  }

  public boolean isPatchRead() {
    return fPatchRead;
  }
}
