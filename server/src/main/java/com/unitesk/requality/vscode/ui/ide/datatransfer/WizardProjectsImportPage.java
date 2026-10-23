package com.unitesk.requality.vscode.ui.ide.datatransfer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.unitesk.requality.vscode.core.ServerContext;
import com.unitesk.requality.vscode.core.Workspace;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.ArrayContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.CheckboxTableViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardPage;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.ModifyEvent;
import com.unitesk.requality.vscode.ui.swt.events.ModifyListener;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.DirectoryDialog;
import com.unitesk.requality.vscode.ui.swt.widgets.FileDialog;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Table;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;

/**
 * {@code org.eclipse.ui.internal.wizards.datatransfer.WizardProjectsImportPage}: the projects of a root directory or of an
 * archive file, as a checked list. The projects are added to the workspace as they are, or copied into it when "Copy
 * projects into workspace" is set (an archive is always copied).
 */
public class WizardProjectsImportPage extends WizardPage {
  private static final String DIRECTORY = "directory"; //$NON-NLS-1$

  /** A project found in the root directory or the archive. */
  public static final class ProjectRecord {
    /** The name of the project: the name of its folder. */
    public final String name;
    /** The folder of the project in the root directory, or null for an archive. */
    public final File projectDir;
    /** The path of the project's folder inside the archive, or null for a directory. */
    public final String archivePath;

    ProjectRecord(String name, File projectDir, String archivePath) {
      this.name = name;
      this.projectDir = projectDir;
      this.archivePath = archivePath;
    }

    /** The location shown for the project. */
    String location() {
      return projectDir != null ? projectDir.getAbsolutePath() : archivePath;
    }
  }

  private Button rootDirectoryRadio;
  private Button archiveRadio;
  private Text rootField;
  private Text archiveField;
  private Button browseRootButton;
  private Button browseArchiveButton;
  private Button refreshButton;
  private CheckboxTableViewer projectsList;
  private Button selectAllButton;
  private Button deselectAllButton;
  private Button copyProjectsButton;
  private Button hideExistingButton;
  private List<ProjectRecord> records = new ArrayList<>();
  private List<ProjectRecord> shown = new ArrayList<>();

  public WizardProjectsImportPage(String name) {
    super(name);
    setTitle("Import Projects");
    setDescription("Select a directory to search for existing Eclipse projects.");
    setPageComplete(false);
  }

  @Override
  public void createControl(Composite parent) {
    Composite composite = new Composite(parent, SWT.NONE);
    composite.setLayout(new GridLayout());
    composite.setLayoutData(new GridData(GridData.FILL_BOTH));
    setControl(composite);

    rootDirectoryRadio = new Button(composite, SWT.RADIO);
    rootDirectoryRadio.setText("Select roo&t directory:");
    rootDirectoryRadio.setSelection(true);
    Composite rootRow = new Composite(composite, SWT.NONE);
    rootRow.setLayout(new GridLayout(2, false));
    rootRow.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    rootField = new Text(rootRow, SWT.BORDER);
    rootField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    browseRootButton = new Button(rootRow, SWT.PUSH);
    browseRootButton.setText("B&rowse...");
    browseRootButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        DirectoryDialog dialog = new DirectoryDialog(getShell());
        dialog.setMessage("Select root directory of the projects to import");
        String path = dialog.open();
        if (path != null) {
          rootField.setText(path);
          updateProjectsList();
        }
      }
    });

    archiveRadio = new Button(composite, SWT.RADIO);
    archiveRadio.setText("Select &archive file:");
    Composite archiveRow = new Composite(composite, SWT.NONE);
    archiveRow.setLayout(new GridLayout(2, false));
    archiveRow.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    archiveField = new Text(archiveRow, SWT.BORDER);
    archiveField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    browseArchiveButton = new Button(archiveRow, SWT.PUSH);
    browseArchiveButton.setText("Bro&wse...");
    browseArchiveButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        FileDialog dialog = new FileDialog(getShell(), SWT.OPEN);
        dialog.setFilterExtensions(new String[] { "*.zip;*.jar", "*.*" });
        String path = dialog.open();
        if (path != null) {
          archiveField.setText(path);
          updateProjectsList();
        }
      }
    });
    rootField.addModifyListener(new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent e) {
        updateProjectsList();
      }
    });
    archiveField.addModifyListener(new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent e) {
        updateProjectsList();
      }
    });
    rootDirectoryRadio.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        updateProjectsList();
      }
    });
    archiveRadio.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        updateProjectsList();
      }
    });

    refreshButton = new Button(composite, SWT.PUSH);
    refreshButton.setText("R&efresh");
    refreshButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        updateProjectsList();
      }
    });

    Label label = new Label(composite, SWT.NONE);
    label.setText("&Projects:");
    projectsList = new CheckboxTableViewer(new Table(composite, SWT.CHECK | SWT.BORDER | SWT.V_SCROLL));
    projectsList.getControl().setLayoutData(new GridData(GridData.FILL_BOTH));
    projectsList.setContentProvider(new ArrayContentProvider());
    projectsList.setLabelProvider(new LabelProvider() {
      @Override
      public String getText(Object element) {
        ProjectRecord r = (ProjectRecord) element;
        return r.name + " (" + r.location() + ")";
      }
    });
    projectsList.addCheckStateListener(event -> updatePageCompletion());

    Composite buttons = new Composite(composite, SWT.NONE);
    buttons.setLayout(new GridLayout(2, true));
    selectAllButton = new Button(buttons, SWT.PUSH);
    selectAllButton.setText("&Select All");
    selectAllButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        projectsList.setAllChecked(true);
        updatePageCompletion();
      }
    });
    deselectAllButton = new Button(buttons, SWT.PUSH);
    deselectAllButton.setText("&Deselect All");
    deselectAllButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        projectsList.setAllChecked(false);
        updatePageCompletion();
      }
    });

    copyProjectsButton = new Button(composite, SWT.CHECK);
    copyProjectsButton.setText("&Copy projects into workspace");
    hideExistingButton = new Button(composite, SWT.CHECK);
    hideExistingButton.setText("H&ide projects that already exist in the workspace");
    hideExistingButton.setSelection(true);
    hideExistingButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        updateProjectsList();
      }
    });
    updateProjectsList();
  }

  /** Searches the root directory or the archive for the projects (folders with a .project file). */
  void updateProjectsList() {
    if (projectsList == null)
      return;
    records = new ArrayList<>();
    if (rootDirectoryRadio.getSelection()) {
      File root = new File(rootField.getText());
      if (root.isDirectory()) {
        for (File dir : ServerContext.server().workspace().findProjects(root))
          records.add(new ProjectRecord(dir.getName(), dir, null));
      }
    } else if (new File(archiveField.getText()).isFile()) {
      try (ZipFile zip = new ZipFile(archiveField.getText())) {
        Enumeration<? extends ZipEntry> all = zip.entries();
        while (all.hasMoreElements()) {
          String name = all.nextElement().getName();
          if (name.endsWith("/.project")) {
            String folder = name.substring(0, name.length() - "/.project".length());
            records.add(new ProjectRecord(folder.substring(folder.lastIndexOf('/') + 1), null, folder));
          }
        }
      } catch (IOException e) {
        records = new ArrayList<>();
      }
    }
    shown = new ArrayList<>();
    for (ProjectRecord r : records)
      if (!hideExistingButton.getSelection() || !existsInWorkspace(r))
        shown.add(r);
    projectsList.setInput(shown);
    projectsList.setAllChecked(true);
    updatePageCompletion();
  }

  private boolean existsInWorkspace(ProjectRecord r) {
    if (r.projectDir == null)
      return ServerContext.server().workspace().anyProject(r.name) != null;
    return ServerContext.server().workspace().project(r.projectDir) != null
        || ServerContext.server().workspace().anyProject(r.name) != null;
  }

  /** The checked projects. */
  List<ProjectRecord> selectedProjects() {
    List<ProjectRecord> r = new ArrayList<>();
    if (projectsList == null)
      return r;
    for (Object o : projectsList.getCheckedElements())
      r.add((ProjectRecord) o);
    return r;
  }

  void updatePageCompletion() {
    setPageComplete(!selectedProjects().isEmpty());
  }

  /** Adds the checked projects to the workspace; returns true if the wizard may close. */
  public boolean finish() {
    List<ProjectRecord> selected = selectedProjects();
    boolean copy = copyProjectsButton.getSelection() || archiveRadio.getSelection();
    List<String> problems = new ArrayList<>();
    for (ProjectRecord r : selected) {
      try {
        importProject(r, copy);
      } catch (IOException | RuntimeException e) {
        problems.add(r.name + ": " + e.getMessage());
      }
    }
    if (!problems.isEmpty())
      MessageDialog.openError(getShell(), "Import Projects", String.join("\n", problems));
    return problems.isEmpty();
  }

  private void importProject(ProjectRecord r, boolean copy) throws IOException {
    Workspace ws = ServerContext.server().workspace();
    if (!copy) {
      // the project stays where it is (an external project of the workspace)
      if (ws.project(r.projectDir) == null)
        ws.addProject(r.projectDir);
      return;
    }
    File target = ws.defaultLocation(r.name);
    if (target == null)
      throw new IOException("No workspace folder");
    if (target.exists())
      throw new IOException("The folder already exists: " + target);
    if (r.projectDir != null)
      copyTree(r.projectDir.toPath(), target.toPath());
    else
      extract(r.archivePath, target);
    ws.addProject(target);
  }

  private static void copyTree(java.nio.file.Path from, java.nio.file.Path to) throws IOException {
    try (java.util.stream.Stream<java.nio.file.Path> s = Files.walk(from)) {
      for (java.nio.file.Path p : (Iterable<java.nio.file.Path>) s::iterator) {
        java.nio.file.Path dest = to.resolve(from.relativize(p).toString());
        if (Files.isDirectory(p))
          Files.createDirectories(dest);
        else
          Files.copy(p, dest);
      }
    }
  }

  /** Extracts the entries below the folder of the archive into the target folder. */
  private void extract(String folder, File target) throws IOException {
    String prefix = folder + "/";
    try (ZipFile zip = new ZipFile(archiveField.getText())) {
      Enumeration<? extends ZipEntry> all = zip.entries();
      while (all.hasMoreElements()) {
        ZipEntry e = all.nextElement();
        if (!e.getName().startsWith(prefix))
          continue;
        File out = new File(target, e.getName().substring(prefix.length()));
        if (e.isDirectory()) {
          out.mkdirs();
          continue;
        }
        out.getParentFile().mkdirs();
        try (InputStream in = zip.getInputStream(e)) {
          Files.copy(in, out.toPath());
        }
      }
    }
  }
}
