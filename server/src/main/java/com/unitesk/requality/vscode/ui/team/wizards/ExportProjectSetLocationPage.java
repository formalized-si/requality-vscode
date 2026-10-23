package com.unitesk.requality.vscode.ui.team.wizards;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.dialogs.TitleAreaDialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.TreeViewer;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardPage;
import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.IWorkspace;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Combo;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.FileDialog;
import com.unitesk.requality.vscode.ui.swt.widgets.Group;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.vscode.ui.workbench.model.BaseWorkbenchContentProvider;
import com.unitesk.requality.vscode.ui.workbench.model.WorkbenchLabelProvider;

/**
 * {@code org.eclipse.team.internal.ui.wizards.ExportProjectSetLocationPage}: the page of the Team Project Set export that
 * names the file to write. The destination is a file of the file system (File, with Browse...) or a file of the workspace
 * (Workspace, with Browse... to choose the folder and the file name in the workspace).
 * VSCODE: the workspace file is written to its location on the file system.
 */
public class ExportProjectSetLocationPage extends WizardPage {
  private Combo fileCombo;
  private Button fileRadio;
  private Button workspaceRadio;
  private Text workspaceText;
  private IFile workspaceFile;
  private String file = "";
  private boolean saveToFileSystem;

  public ExportProjectSetLocationPage(String pageName, String title, Object titleImage) {
    super(pageName, title, titleImage);
    setDescription("Export selected resources into the team project file.");
  }

  @Override
  public void createControl(Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);
    composite.setLayout(new GridLayout(1, false));
    composite.setLayoutData(new GridData(GridData.FILL_BOTH));

    Group locationGroup = new Group(composite, SWT.NONE);
    locationGroup.setLayout(new GridLayout(1, false));
    locationGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    locationGroup.setText("Select the export destination:");

    createExportToFile(locationGroup);
    createExportToWorkspace(locationGroup);
    saveToFileSystem = true;

    setControl(composite);
    updateEnablement();
  }

  private void createExportToFile(Composite composite) {
    fileRadio = new Button(composite, SWT.RADIO);
    fileRadio.setText("F&ile");
    fileRadio.addListener(SWT.Selection, e -> {
      saveToFileSystem = true;
      file = fileCombo.getText();
      updateEnablement();
    });

    Composite inner = new Composite(composite, SWT.NONE);
    inner.setLayout(new GridLayout(2, false));
    inner.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

    fileCombo = new Combo(inner, SWT.DROP_DOWN | SWT.BORDER);
    fileCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    String[] history = PsfHistory.get(PsfHistory.FILENAMES);
    file = history.length > 0 ? history[0] : "projectSet.psf";
    fileCombo.setItems(history);
    fileCombo.setText(file);
    fileCombo.addListener(SWT.Modify, e -> {
      file = fileCombo.getText();
      updateEnablement();
    });

    Button browseButton = new Button(inner, SWT.PUSH);
    browseButton.setText("B&rowse...");
    GridData data = new GridData();
    data.horizontalAlignment = GridData.FILL;
    data.widthHint = Math.max(browseButton.computeSize(SWT.DEFAULT, SWT.DEFAULT, true).x, 80);
    browseButton.setLayoutData(data);
    browseButton.addListener(SWT.Selection, e -> {
      if (!isSaveToFileSystem())
        saveToFileSystem = true;
      FileDialog d = new FileDialog(getShell(), SWT.SAVE);
      d.setFilterExtensions(new String[] { "*.psf" });
      d.setFilterNames(new String[] { "Team Project Set Files (*.psf)" });
      d.setFileName("projectSet.psf");
      String fileName = getFileName();
      if (fileName != null) {
        int separator = fileName.lastIndexOf(File.separatorChar);
        if (separator != -1)
          fileName = fileName.substring(0, separator);
      }
      d.setFilterPath(fileName);
      String f = d.open();
      if (f != null) {
        fileCombo.setText(f);
        file = f;
      }
    });
  }

  private void createExportToWorkspace(Composite composite) {
    workspaceRadio = new Button(composite, SWT.RADIO);
    workspaceRadio.setText("W&orkspace");
    workspaceRadio.addListener(SWT.Selection, e -> {
      saveToFileSystem = false;
      updateEnablement();
    });

    Composite nameGroup = new Composite(composite, SWT.NONE);
    nameGroup.setLayout(new GridLayout(2, false));
    nameGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

    workspaceText = new Text(nameGroup, SWT.BORDER);
    workspaceText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    workspaceText.setEditable(false);
    workspaceText.addListener(SWT.Modify, e -> {
      file = workspaceFile.getLocation().toString();
      updateEnablement();
    });

    Button wsBrowseButton = new Button(nameGroup, SWT.PUSH);
    GridData gd = new GridData();
    gd.horizontalAlignment = GridData.FILL;
    gd.widthHint = Math.max(wsBrowseButton.computeSize(SWT.DEFAULT, SWT.DEFAULT, true).x, 80);
    wsBrowseButton.setLayoutData(gd);
    wsBrowseButton.setText("Bro&wse ...");
    wsBrowseButton.addListener(SWT.Selection, e -> {
      if (isSaveToFileSystem())
        saveToFileSystem = false;
      WorkspaceDialog d = new WorkspaceDialog(getShell());
      d.open();
    });
  }

  private void updateEnablement() {
    boolean complete;
    fileRadio.setSelection(saveToFileSystem);
    workspaceRadio.setSelection(!saveToFileSystem);
    if (file.isEmpty()) {
      setErrorMessage("Please specify the destination file.");
      complete = false;
    } else if (new File(file).isDirectory()) {
      setErrorMessage("You have specified a folder");
      complete = false;
    } else if (!isSaveToFileSystem() && workspaceFile == null) {
      setErrorMessage("Please specify the destination file.");
      complete = false;
    } else {
      complete = true;
    }
    if (!isSaveToFileSystem() && workspaceFile != null)
      complete = true;
    if (complete) {
      setErrorMessage(null);
      setDescription("Export selected resources into the team project file.");
    }
    setPageComplete(complete);
  }

  @Override
  public void setVisible(boolean visible) {
    super.setVisible(visible);
    if (visible && fileCombo != null)
      fileCombo.setFocus();
  }

  /** Whether the file is written to the file system (File) rather than to the workspace. */
  public boolean isSaveToFileSystem() {
    return saveToFileSystem;
  }

  /** The name of the file to write. */
  public String getFileName() {
    return file;
  }

  /** Sets the name of the file to write (kept when it is null). */
  public void setFileName(String name) {
    if (name != null)
      file = name;
  }

  /** The Team Project Set Save Location dialog: the folder and the file name in the workspace. */
  private final class WorkspaceDialog extends TitleAreaDialog {
    private TreeViewer wsTreeViewer;
    private Text wsFilenameText;
    private IContainer wsContainer;
    private Button okButton;

    WorkspaceDialog(Shell shell) {
      super(shell);
    }

    @Override
    protected Control createContents(Composite parent) {
      Control control = super.createContents(parent);
      setTitle("Team Project Set Save Location");
      setMessage("Select a folder in the workspace and enter a filename.");
      return control;
    }

    @Override
    protected Control createDialogArea(Composite parent) {
      Composite composite = (Composite) super.createDialogArea(parent);
      composite.setLayout(new GridLayout(1, false));
      composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
      getShell().setText("Export Team Project Set to Workspace");

      wsTreeViewer = new TreeViewer(composite, SWT.BORDER);
      GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
      gd.widthHint = 550;
      gd.heightHint = 250;
      wsTreeViewer.getTree().setLayoutData(gd);
      wsTreeViewer.setContentProvider(new LocationPageContentProvider());
      wsTreeViewer.setLabelProvider(new WorkbenchLabelProvider());
      wsTreeViewer.setInput(ResourcesPlugin.getWorkspace());

      Composite group = new Composite(composite, SWT.NONE);
      GridLayout layout = new GridLayout(2, false);
      layout.marginWidth = 0;
      group.setLayout(layout);
      group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

      Label label = new Label(group, SWT.NONE);
      label.setLayoutData(new GridData());
      label.setText("&Filename");
      wsFilenameText = new Text(group, SWT.BORDER);
      wsFilenameText.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
      wsFilenameText.setText("projectSet.psf");

      setupListeners();
      return parent;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
      super.createButtonsForButtonBar(parent);
      okButton = getButton(IDialogConstants.OK_ID);
    }

    @Override
    protected void okPressed() {
      if (wsContainer == null)
        getSelectedContainer();
      workspaceFile = wsContainer.getFile(Path.fromOSString(wsFilenameText.getText()));
      if (workspaceFile != null)
        workspaceText.setText(workspaceFile.getFullPath().toString());
      super.okPressed();
    }

    private void getSelectedContainer() {
      Object obj = wsTreeViewer.getStructuredSelection().getFirstElement();
      if (obj instanceof IContainer container)
        wsContainer = container;
      else if (obj instanceof IFile f)
        wsContainer = f.getParent();
    }

    @Override
    protected void cancelPressed() {
      getSelectedContainer();
      super.cancelPressed();
    }

    private void setupListeners() {
      wsTreeViewer.addSelectionChangedListener(event -> {
        IStructuredSelection s = event.getStructuredSelection();
        Object obj = s.getFirstElement();
        if (obj instanceof IContainer container) {
          wsContainer = container;
        } else if (obj instanceof IFile tempFile) {
          wsContainer = tempFile.getParent();
          wsFilenameText.setText(tempFile.getName());
        }
      });
      wsTreeViewer.addDoubleClickListener(event -> {
        if (event.getSelection() instanceof IStructuredSelection s) {
          Object item = s.getFirstElement();
          if (wsTreeViewer.getExpandedState(item))
            wsTreeViewer.collapseToLevel(item, 1);
          else
            wsTreeViewer.expandToLevel(item, 1);
        }
      });
      wsFilenameText.addModifyListener(e -> {
        String patchName = wsFilenameText.getText();
        if (patchName.trim().isEmpty()) {
          okButton.setEnabled(false);
          setErrorMessage("Enter a filename.");
        } else if (!ResourcesPlugin.getWorkspace().validateName(patchName, IResource.FILE).isOK()) {
          okButton.setEnabled(false);
          setErrorMessage("Filename cannot contain any segments.");
        } else {
          okButton.setEnabled(true);
          setErrorMessage(null);
        }
      });
    }
  }

  /** The content of the workspace tree: the workspace with its open projects. */
  private static final class LocationPageContentProvider extends BaseWorkbenchContentProvider {
    @Override
    public Object[] getChildren(Object element) {
      if (element instanceof IWorkspace workspace) {
        List<Object> accessible = new ArrayList<>();
        for (IProject project : workspace.getRoot().getProjects())
          if (project.isOpen())
            accessible.add(project);
        return accessible.toArray();
      }
      return super.getChildren(element);
    }
  }
}
