package com.unitesk.requality.vscode.ui.ide.datatransfer;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

import com.unitesk.requality.vscode.ui.ide.wizards.WizardResourceImportPage;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.ProgressMonitorDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.CheckboxTreeViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.ModifyEvent;
import com.unitesk.requality.vscode.ui.swt.events.ModifyListener;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.FileDialog;
import com.unitesk.requality.vscode.ui.swt.widgets.Group;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;

/**
 * {@code org.eclipse.ui.internal.wizards.datatransfer.WizardArchiveFileResourceImportPage1}: imports the checked entries of
 * a zip archive into the destination container, with the options of the File System import.
 */
public class WizardArchiveFileResourceImportPage1 extends WizardResourceImportPage implements IOverwriteQuery {
  private Text archiveNameField;
  private Button selectAllButton;
  private Button deselectAllButton;
  private CheckboxTreeViewer treeViewer;
  private Button overwriteExistingResourcesCheckbox;
  private Button createTopLevelFolderCheckbox;
  private ZipFile zipFile;
  private ZipLeveledStructureProvider provider;
  private final List<ZipFile> openArchives = new ArrayList<>();

  public WizardArchiveFileResourceImportPage1(String name, IWorkbench aWorkbench, IStructuredSelection selection) {
    super(name, aWorkbench, selection);
    setTitle("Archive File");
    setDescription("Import resources from an archive file into an existing project.");
    setPageComplete(false);
  }

  @Override
  public void createControl(Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);
    composite.setLayout(new GridLayout());
    composite.setLayoutData(new GridData(GridData.FILL_BOTH));
    setControl(composite);

    Composite source = new Composite(composite, SWT.NONE);
    GridLayout sourceLayout = new GridLayout(3, false);
    source.setLayout(sourceLayout);
    source.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    Label label = new Label(source, SWT.NONE);
    label.setText("From archive file:");
    archiveNameField = new Text(source, SWT.BORDER);
    archiveNameField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    archiveNameField.addModifyListener(new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent e) {
        loadArchive(new File(archiveNameField.getText()));
      }
    });
    Button browse = new Button(source, SWT.PUSH);
    browse.setText("Browse...");
    browse.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        FileDialog dialog = new FileDialog(getShell(), SWT.OPEN);
        dialog.setFilterExtensions(new String[] { "*.zip;*.jar", "*.*" });
        String path = dialog.open();
        if (path != null) {
          archiveNameField.setText(path);
          loadArchive(new File(path));
        }
      }
    });

    treeViewer = new CheckboxTreeViewer(composite, SWT.BORDER);
    treeViewer.getControl().setLayoutData(new GridData(GridData.FILL_BOTH));
    treeViewer.setContentProvider(new ITreeContentProvider() {
      @Override
      public Object[] getChildren(Object parentElement) {
        List<?> l = provider == null ? null : provider.getChildren(parentElement);
        return l == null ? new Object[0] : l.toArray();
      }

      @Override
      public Object[] getElements(Object inputElement) {
        return getChildren(inputElement);
      }

      @Override
      public Object getParent(Object element) {
        String p = String.valueOf(element);
        int i = p.lastIndexOf('/');
        return i < 0 ? ZipLeveledStructureProvider.ROOT : p.substring(0, i);
      }

      @Override
      public boolean hasChildren(Object element) {
        return provider != null && provider.isFolder(element) && getChildren(element).length > 0;
      }

      @Override
      public void dispose() {
      }

      @Override
      public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
      }
    });
    treeViewer.setLabelProvider(new LabelProvider() {
      @Override
      public String getText(Object element) {
        return provider == null ? String.valueOf(element) : provider.getLabel(element);
      }
    });
    treeViewer.addCheckStateListener(event -> updatePageCompletion());

    Composite buttons = new Composite(composite, SWT.NONE);
    buttons.setLayout(new GridLayout(2, true));
    selectAllButton = new Button(buttons, SWT.PUSH);
    selectAllButton.setText("Select &All");
    selectAllButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        checkAll(true);
      }
    });
    deselectAllButton = new Button(buttons, SWT.PUSH);
    deselectAllButton.setText("&Deselect All");
    deselectAllButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        checkAll(false);
      }
    });

    createContainerGroup(composite);
    createOptionsGroup(composite);
    updatePageCompletion();
  }

  /** Opens the archive: its entries are the tree; a missing or unreadable file empties the tree. */
  void loadArchive(File file) {
    closeArchive();
    if (file.isFile()) {
      try {
        zipFile = new ZipFile(file);
        provider = new ZipLeveledStructureProvider(zipFile);
      } catch (IOException e) {
        zipFile = null;
        provider = null;
      }
    }
    if (treeViewer != null) {
      treeViewer.setInput(ZipLeveledStructureProvider.ROOT);
      treeViewer.refresh();
    }
    updatePageCompletion();
  }

  /**
   * Forgets the archive opened before: its file stays open until the page is disposed, as an operation that is running may
   * still read the entries of it.
   */
  private void closeArchive() {
    if (zipFile != null)
      openArchives.add(zipFile);
    zipFile = null;
    provider = null;
  }

  /** Checks or unchecks every entry of the archive. */
  private void checkAll(boolean state) {
    if (provider == null)
      return;
    treeViewer.setCheckedElements(new Object[0]);
    if (state)
      checkBelow(ZipLeveledStructureProvider.ROOT);
    updatePageCompletion();
  }

  /** Checks the children of the folder, opening the folders so that their items exist to be checked. */
  private void checkBelow(Object folder) {
    List<?> children = provider.getChildren(folder);
    if (children == null)
      return;
    for (Object c : children) {
      treeViewer.setChecked(c, true);
      if (provider.isFolder(c)) {
        treeViewer.setExpandedState(c, true);
        checkBelow(c);
      }
    }
  }

  /** The checked entries that are not inside another checked folder. */
  List<Object> topmostChecked() {
    List<Object> r = new ArrayList<>();
    if (treeViewer == null)
      return r;
    Object[] checked = treeViewer.getCheckedElements();
    for (Object e : checked) {
      String p = String.valueOf(e);
      boolean covered = false;
      for (Object o : checked) {
        String q = String.valueOf(o);
        if (!q.equals(p) && p.startsWith(q + "/"))
          covered = true;
      }
      if (!covered)
        r.add(e);
    }
    return r;
  }

  @Override
  protected void createOptionsGroupButtons(Group optionsGroup) {
    overwriteExistingResourcesCheckbox = new Button(optionsGroup, SWT.CHECK);
    overwriteExistingResourcesCheckbox.setText("Overwrite existing resources without warning");
    createTopLevelFolderCheckbox = new Button(optionsGroup, SWT.CHECK);
    createTopLevelFolderCheckbox.setText("Create top-level folder");
  }

  @Override
  protected boolean validateOptionsGroup() {
    return true;
  }

  @Override
  protected boolean validateSourceGroup() {
    return provider != null && !topmostChecked().isEmpty();
  }

  /** Recomputes the completion of the page from the archive, the checked entries and the destination. */
  @Override
  protected void updatePageCompletion() {
    setPageComplete(validateSourceGroup() && validateDestinationGroup() && validateOptionsGroup());
  }

  @Override
  public boolean finish() {
    if (provider == null) {
      MessageDialog.openError(getShell(), "Import", "The archive file does not exist.");
      return false;
    }
    List<Object> selected = topmostChecked();
    if (selected.isEmpty()) {
      MessageDialog.openInformation(getShell(), "Information", "No resources selected");
      return false;
    }
    IPath destination = getContainerFullPath();
    if (createTopLevelFolderCheckbox.getSelection()) {
      String name = new File(archiveNameField.getText()).getName();
      int dot = name.lastIndexOf('.');
      destination = destination.append(dot > 0 ? name.substring(0, dot) : name);
    }
    ImportOperation op = new ImportOperation(destination, ZipLeveledStructureProvider.ROOT, provider, this, selected);
    op.setOverwriteResources(overwriteExistingResourcesCheckbox.getSelection());
    try {
      new ProgressMonitorDialog(getShell()).run(true, true, op);
    } catch (InvocationTargetException e) {
      MessageDialog.openError(getShell(), "Import", String.valueOf(e.getCause() == null ? e.getMessage() : e.getCause().getMessage()));
      return false;
    } catch (InterruptedException e) {
      return false;
    }
    IStatus status = op.getStatus();
    if (status.getSeverity() != IStatus.OK) {
      MessageDialog.openError(getShell(), "Import", status.getMessage());
      return false;
    }
    return true;
  }

  /** The answer for an existing resource: the overwrite option, or the question for the resource. */
  @Override
  public String queryOverwrite(String pathString) {
    if (overwriteExistingResourcesCheckbox.getSelection())
      return IOverwriteQuery.ALL;
    boolean yes = MessageDialog.openQuestion(getShell(), "Question",
        pathString + " already exists. Would you like to overwrite it?");
    return yes ? IOverwriteQuery.YES : IOverwriteQuery.NO;
  }

  @Override
  public void dispose() {
    closeArchive();
    for (ZipFile z : openArchives) {
      try {
        z.close();
      } catch (IOException e) {
        // closed anyway
      }
    }
    openArchives.clear();
    super.dispose();
  }

}
