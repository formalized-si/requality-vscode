package com.unitesk.requality.vscode.ui.ide.wizards;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.unitesk.requality.vscode.ui.ide.dialogs.FileSystemElement;
import com.unitesk.requality.vscode.ui.jface.viewers.CheckStateChangedEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.CheckboxTableViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ICheckStateProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.SelectionChangedEvent;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;

/**
 * The two-pane folder/file selection of {@link WizardFileSystemResourceImportPage1}: the folders of the source tree on
 * the left, the files of the selected folder on the right with check boxes. The checked files are kept for all the
 * folders, not only the displayed one.
 */
public class FileSystemSelectionGroup {
  private final ITreeContentProvider folderProvider;
  private final ITreeContentProvider fileProvider;
  private final Set<Object> checked = new LinkedHashSet<>();
  private final TreeViewer folderViewer;
  private final CheckboxTableViewer fileViewer;
  private FileSystemElement root;
  private FileSystemElement currentFolder;

  public FileSystemSelectionGroup(Composite parent, ITreeContentProvider folderProvider,
      ITreeContentProvider fileProvider) {
    this.folderProvider = folderProvider;
    this.fileProvider = fileProvider;

    Composite group = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout();
    layout.numColumns = 2;
    layout.makeColumnsEqualWidth = true;
    group.setLayout(layout);
    group.setFont(parent.getFont());
    group.setLayoutData(new GridData(GridData.FILL_BOTH));

    folderViewer = new TreeViewer(group, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
    GridData folderData = new GridData(GridData.FILL_BOTH);
    folderData.heightHint = 250;
    folderData.widthHint = 200;
    folderViewer.getTree().setLayoutData(folderData);
    folderViewer.setContentProvider(folderProvider);
    folderViewer.setLabelProvider(new ElementLabelProvider());
    folderViewer.addSelectionChangedListener(this::folderSelectionChanged);

    fileViewer = CheckboxTableViewer.newCheckList(group, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
    GridData fileData = new GridData(GridData.FILL_BOTH);
    fileData.heightHint = 250;
    fileData.widthHint = 200;
    fileViewer.getTable().setLayoutData(fileData);
    fileViewer.setContentProvider(fileProvider);
    fileViewer.setLabelProvider(new ElementLabelProvider());
    fileViewer.setCheckStateProvider(new ICheckStateProvider() {
      @Override
      public boolean isChecked(Object element) {
        return checked.contains(element);
      }

      @Override
      public boolean isGrayed(Object element) {
        return false;
      }
    });
    fileViewer.addCheckStateListener(this::fileCheckStateChanged);
  }

  private void folderSelectionChanged(SelectionChangedEvent event) {
    ISelection selection = event.getSelection();
    if (selection instanceof IStructuredSelection && !selection.isEmpty())
      showFolder((FileSystemElement) ((IStructuredSelection) selection).getFirstElement());
    else
      showFolder(null);
  }

  private void fileCheckStateChanged(CheckStateChangedEvent event) {
    if (event.getChecked())
      checked.add(event.getElement());
    else
      checked.remove(event.getElement());
  }

  private void showFolder(FileSystemElement folder) {
    currentFolder = folder;
    fileViewer.setInput(folder);
    fileViewer.refresh();
  }

  /** Shows the given tree (the root element is not shown, its top-level folders are). Clears the checked files. */
  public void setRoot(FileSystemElement newRoot) {
    root = newRoot;
    checked.clear();
    folderViewer.setInput(newRoot);
    Object[] topLevel = newRoot == null ? new Object[0] : folderProvider.getChildren(newRoot);
    if (topLevel.length > 0) {
      folderViewer.setSelection(new StructuredSelection(topLevel[0]), true);
      showFolder((FileSystemElement) topLevel[0]);
    } else {
      showFolder(null);
    }
  }

  public FileSystemElement getRoot() {
    return root;
  }

  /** The checked files, in the order they were checked. */
  public List<Object> getCheckedElements() {
    return new ArrayList<>(checked);
  }

  /** Replaces the checked files by the elements of the given lists (folder to selected files). */
  public void updateSelections(Map<?, ?> selectionMap) {
    checked.clear();
    for (Object elements : selectionMap.values())
      checked.addAll((Collection<?>) elements);
    fileViewer.refresh();
  }

  /** Checks or unchecks all the files of the displayed folder. */
  public void setAllSelections(boolean state) {
    if (currentFolder == null)
      return;
    for (Object element : fileProvider.getChildren(currentFolder)) {
      if (state)
        checked.add(element);
      else
        checked.remove(element);
    }
    fileViewer.refresh();
  }

  /** The label of a file system element. */
  private static final class ElementLabelProvider extends LabelProvider {
    @Override
    public String getText(Object element) {
      return ((FileSystemElement) element).getLabel();
    }
  }
}
