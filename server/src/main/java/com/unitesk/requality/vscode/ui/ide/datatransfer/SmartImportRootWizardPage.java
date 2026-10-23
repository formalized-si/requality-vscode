package com.unitesk.requality.vscode.ui.ide.datatransfer;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.port.eclipse.Activator;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogSettings;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.fieldassist.ControlDecoration;
import com.unitesk.requality.vscode.ui.jface.viewers.CheckboxTreeViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ITableColorProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.ITableLabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.ITreeContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ViewerComparator;
import com.unitesk.requality.vscode.ui.jface.viewers.ViewerFilter;
import com.unitesk.requality.vscode.ui.jface.wizard.IWizard;
import com.unitesk.requality.vscode.ui.jface.wizard.ProgressMonitorPart;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardPage;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.Assert;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Job;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.ModifyEvent;
import com.unitesk.requality.vscode.ui.swt.events.ModifyListener;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.graphics.Color;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Combo;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.DirectoryDialog;
import com.unitesk.requality.vscode.ui.swt.widgets.Event;
import com.unitesk.requality.vscode.ui.swt.widgets.FileDialog;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Link;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.vscode.ui.swt.widgets.TreeColumn;
import com.unitesk.requality.vscode.ui.swt.widgets.TreeItem;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.dialogs.ImportExportWizard;
import com.unitesk.requality.vscode.ui.workbench.dialogs.WorkingSetGroup;
import com.unitesk.requality.vscode.ui.workbench.internal.WorkingSetRegistry;
import com.unitesk.requality.vscode.ui.workbench.internal.WorkingSetDescriptor;

/**
 * {@code org.eclipse.ui.internal.wizards.datatransfer.SmartImportRootWizardPage}: the "Import Projects from File System or
 * Archive" page. This page simply asks for what to import, and asks the user a basic choice of how to import (import raw,
 * infer sub-projects...). The proposals are computed in a job, and listed in a check box tree with a filter text.
 *
 * <p>
 * The proposals are computed by a {@link Job} whose progress is shown in the progress monitor part of the wizard
 * container, as Eclipse: its Stop button cancels the job (the list then shows "inspection canceled"). The archive is
 * expanded in the progress of the wizard container.
 * </p>
 */
public class SmartImportRootWizardPage extends WizardPage {

  static final String IMPORTED_SOURCES = SmartImportRootWizardPage.class.getName() + ".knownSources"; //$NON-NLS-1$

  private static final String STORE_HIDE_ALREADY_OPEN = "SmartImportRootWizardPage.STORE_HIDE_ALREADY_OPEN"; //$NON-NLS-1$
  private static final String STORE_NESTED_PROJECTS = "SmartImportRootWizardPage.STORE_NESTED_PROJECTS"; //$NON-NLS-1$
  private static final String STORE_CLOSE_IMPORTED = "SmartImportRootWizardPage.STORE_CLOSE_IMPORTED"; //$NON-NLS-1$
  private static final String STORE_CONFIGURE_NATURES = "SmartImportRootWizardPage.STORE_CONFIGURE_NATURES"; //$NON-NLS-1$
  private static final String STORE_SKIP_DOT_FOLDERS = "SmartImportRootWizardPage.STORE_SKIP_DOT_FOLDERS"; //$NON-NLS-1$

  // Root
  private File selection;
  private Combo rootDirectoryText;
  private ControlDecoration rootDirectoryTextDecorator;

  // Proposal part
  private CheckboxTreeViewer tree;
  private Text filterText;
  private final ProposalPatternFilter patternFilter = new ProposalPatternFilter();
  private final ViewerFilter existingProjectsFilter = new ViewerFilter() {
    @Override
    public boolean select(Viewer viewer, Object parentElement, Object element) {
      return !isExistingProject((File) element);
    }
  };
  private boolean hideAlreadyOpen = false;
  private ControlDecoration proposalSelectionDecorator;
  private Set<File> directoriesToImport;
  private Label selectionSummary;
  protected Map<File, List<ProjectConfigurator>> potentialProjects = Collections.emptyMap();
  /** The progress monitor part of the wizard container, grabbed by a run of the container (Eclipse's supplier). */
  protected Supplier<ProgressMonitorPart> wizardProgressMonitor = new Supplier<>() {
    private ProgressMonitorPart progressMonitorPart;

    @Override
    public ProgressMonitorPart get() {
      if (progressMonitorPart == null && getWizard().getContainer() != null) {
        try {
          getWizard().getContainer().run(false, true, monitor -> {
            if (monitor instanceof ProgressMonitorPart) {
              progressMonitorPart = (ProgressMonitorPart) monitor;
            }
          });
        } catch (InvocationTargetException ite) {
          RLogger.logError(ite.getCause() instanceof Exception ? (Exception) ite.getCause() : ite);
        } catch (InterruptedException operationCanceled) {
          Thread.interrupted();
        }
      }
      return progressMonitorPart;
    }
  };

  /** Stops the proposals job: the Stop button of the wizard progress part. */
  private final SelectionAdapter cancelWorkListener = new SelectionAdapter() {
    @Override
    public void widgetSelected(SelectionEvent e) {
      stopAndDisconnectCurrentWork();
    }
  };

  private Job refreshProposalsJob;
  private Button selectAllButton;
  private Button deselectAllButton;

  // Configuration part
  private boolean closeProjectsAfterImport = false;
  private boolean detectNestedProjects = true;
  private boolean configureProjects = true;
  private boolean skipDotFolders = true;

  // Working sets
  private Set<IWorkingSet> workingSets;
  private WorkingSetGroup workingSetsGroup;

  /** The label of the folder column and the "Import as" column (the configurator that would import the folder). */
  private final class ProposalLabelProvider extends LabelProvider implements ITableLabelProvider, ITableColorProvider {
    @Override
    public String getColumnText(Object element, int columnIndex) {
      if (columnIndex == 0) {
        return getFolderText((File) element);
      } else if (columnIndex == 1) {
        return getImportAsText((File) element);
      }
      return ""; //$NON-NLS-1$
    }

    @Override
    public com.unitesk.requality.vscode.ui.swt.graphics.Image getColumnImage(Object element, int columnIndex) {
      return null;
    }

    @Override
    public Color getForeground(Object element, int columnIndex) {
      if (isExistingProject((File) element) || isExistingProjectName((File) element)) {
        return Display.getDefault().getSystemColor(SWT.COLOR_GRAY);
      }
      return null;
    }

    @Override
    public Color getBackground(Object element, int columnIndex) {
      return null;
    }
  }

  /** The label of the folder column: the path relative to the parent of the root. */
  private String getFolderText(File file) {
    Path filePath = file.toPath();
    Path rootPath = getWizard().createOrGetConfiguredImportJob().getRoot().toPath();
    if (filePath.startsWith(rootPath)) {
      Path parent = rootPath.getParent();
      if (parent != null) {
        Path relative = parent.relativize(filePath);
        if (relative.getNameCount() > 0) {
          return relative.toString();
        }
      }
    }
    return filePath.toString();
  }

  /** The label of the "Import as" column. */
  private String getImportAsText(File file) {
    if (isExistingProject(file)) {
      return DataTransferMessages.SmartImportProposals_alreadyImportedAsProject_title;
    } else if (isExistingProjectName(file)) {
      return DataTransferMessages.SmartImportProposals_anotherProjectWithSameNameExists_title;
    }
    List<ProjectConfigurator> configurators = this.potentialProjects.get(file);
    if (configurators == null || configurators.isEmpty()) {
      return ""; //$NON-NLS-1$
    }
    return ProjectConfiguratorExtensionManager.getLabel(configurators.get(0));
  }

  /**
   * Constructs a new instance of that page.
   *
   * @param wizard the container wizard (most likely an {@link SmartImportWizard})
   * @param initialSelection initial selection (directory or archive)
   * @param initialWorkingSets initial working sets
   */
  public SmartImportRootWizardPage(SmartImportWizard wizard, File initialSelection, Set<IWorkingSet> initialWorkingSets) {
    super(SmartImportRootWizardPage.class.getName());
    this.selection = initialSelection;
    this.workingSets = initialWorkingSets;
    if (this.workingSets == null) {
      this.workingSets = new HashSet<>();
    }
    setWizard(wizard);
  }

  @Override
  public void setWizard(IWizard easymportWizard) {
    Assert.isTrue(easymportWizard instanceof SmartImportWizard, "wizard"); //$NON-NLS-1$
    super.setWizard(easymportWizard);
  }

  @Override
  public SmartImportWizard getWizard() {
    return (SmartImportWizard) super.getWizard();
  }

  @Override
  public void createControl(Composite parent) {
    setTitle(DataTransferMessages.SmartImportWizardPage_importProjectsInFolderTitle);
    setDescription(DataTransferMessages.SmartImportWizardPage_importProjectsInFolderDescription);
    loadWidgetStates();

    Composite res = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout(4, false);
    layout.marginWidth = 10;
    layout.marginHeight = 10;
    res.setLayout(layout);
    res.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

    createInputSelectionOptions(res);

    GridData proposalsGroupLayoutData = new GridData(SWT.FILL, SWT.FILL, true, true, 4, 1);
    proposalsGroupLayoutData.verticalIndent = 12;
    createProposalsGroup(res).setLayoutData(proposalsGroupLayoutData);

    createConfigurationOptions(res);

    createWorkingSetsGroup(res);

    createLink(res);

    if (this.selection != null) {
      rootDirectoryText.setText(this.selection.getAbsolutePath());
      validatePage();
    }

    setControl(res);
  }

  private void createLink(Composite res) {
    Link showOtherImportWizards = new Link(res, SWT.NONE);
    showOtherImportWizards
        .setText("<A>" + DataTransferMessages.SmartImportWizardPage_showOtherSpecializedImportWizard + "</A>"); //$NON-NLS-1$ //$NON-NLS-2$
    showOtherImportWizards.setLayoutData(new GridData(SWT.END, SWT.END, true, false, 4, 1));
    showOtherImportWizards.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        ImportExportWizard importWizard = new ImportExportWizard(ImportExportWizard.IMPORT);
        IStructuredSelection sel = selection != null ? new StructuredSelection(selection) : new StructuredSelection();
        importWizard.init(PlatformUI.getWorkbench(), sel);
        IDialogSettings workbenchSettings = Activator.getDefault().getDialogSettings();
        IDialogSettings wizardSettings = workbenchSettings.getSection("ImportExportAction"); //$NON-NLS-1$
        if (wizardSettings == null) {
          wizardSettings = workbenchSettings.addNewSection("ImportExportAction"); //$NON-NLS-1$
        }
        importWizard.setDialogSettings(wizardSettings);
        importWizard.addPages();
        getWizard().getContainer().showPage(importWizard.getPages()[0]);
      }
    });
  }

  private void createWorkingSetsGroup(Composite parent) {
    Composite workingSetComposite = new Composite(parent, SWT.NONE);
    GridData layoutData = new GridData(SWT.FILL, SWT.TOP, true, false, 4, 1);
    layoutData.verticalIndent = 10;
    workingSetComposite.setLayoutData(layoutData);
    workingSetComposite.setLayout(new GridLayout());
    String[] workingSetIds = Arrays.stream(WorkingSetRegistry.get().getNewPageWorkingSetDescriptors())
        .map(WorkingSetDescriptor::getId).toArray(String[]::new);
    IStructuredSelection wsSel = new StructuredSelection(this.workingSets.toArray());
    this.workingSetsGroup = new WorkingSetGroup(workingSetComposite, wsSel, workingSetIds,
        Activator.getDefault().getDialogSettings());
  }
  private void createInputSelectionOptions(Composite parent) {
    Label rootDirectoryLabel = new Label(parent, SWT.NONE);
    rootDirectoryLabel.setText(DataTransferMessages.SmartImportWizardPage_selectRootDirectory);
    rootDirectoryText = new Combo(parent, SWT.BORDER);
    String[] knownSources = getWizard().getDialogSettings().getArray(IMPORTED_SOURCES);
    if (knownSources != null) {
      rootDirectoryText.setItems(knownSources);
    }
    GridData rootDirectoryTextLayoutData = new GridData(SWT.FILL, SWT.CENTER, true, false);
    rootDirectoryText.setLayoutData(rootDirectoryTextLayoutData);
    rootDirectoryText.addModifyListener(new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent e) {
        SmartImportRootWizardPage.this.selection = new File(((Combo) e.widget).getText());
        if (SmartImportWizard.isValidArchive(selection)) {
          if (SmartImportWizard.getExpandDirectory(selection).isDirectory()) {
            if (MessageDialog.openConfirm(getShell(),
                DataTransferMessages.SmartImportWizardPage_overwriteArchiveDirectory_title,
                DataTransferMessages.bind(DataTransferMessages.SmartImportWizardPage_overwriteArchiveDirectory_message,
                    SmartImportWizard.getExpandDirectory(selection)))) {
              expandSelectedArchive();
            }
          } else {
            expandSelectedArchive();
          }
        }
        validatePage();
        refreshProposals();
      }

      private void expandSelectedArchive() {
        try {
          getContainer().run(true, true, monitor -> {
            try {
              getWizard().expandArchive(selection, monitor);
            } catch (InterruptedException ex) {
              throw ex;
            } catch (Exception ex) {
              throw new InvocationTargetException(ex);
            }
            if (monitor.isCanceled()) {
              throw new InterruptedException();
            }
          });
        } catch (Exception ex) {
          MessageDialog.openWarning(getShell(), DataTransferMessages.SmartImportWizardPage_incompleteExpand_title,
              DataTransferMessages.bind(DataTransferMessages.SmartImportWizardPage_incompleteExpand_message,
                  SmartImportWizard.getExpandDirectory(selection)));
        }
      }
    });
    this.rootDirectoryTextDecorator = new ControlDecoration(rootDirectoryText, SWT.TOP | SWT.LEFT);
    this.rootDirectoryTextDecorator.setDescriptionText(DataTransferMessages.SmartImportWizardPage_incorrectRootDirectory);
    this.rootDirectoryTextDecorator.hide();

    Button directoryButton = new Button(parent, SWT.PUSH);
    directoryButton.setText(DataTransferMessages.SmartImportWizardPage_browse);
    directoryButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        DirectoryDialog dialog = new DirectoryDialog(getShell(), SWT.NONE);
        dialog.setText(DataTransferMessages.SmartImportWizardPage_browseForFolder);
        dialog.setMessage(DataTransferMessages.SmartImportWizardPage_selectFolderOrArchiveToImport);
        if (rootDirectoryText.getText() != null) {
          File current = new File(rootDirectoryText.getText());
          if (current.isDirectory()) {
            dialog.setFilterPath(current.getAbsolutePath());
          } else if (current.isFile()) {
            dialog.setFilterPath(current.getParentFile().getAbsolutePath());
          }
        }
        if (dialog.getFilterPath() == null) {
          dialog.setFilterPath(ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile().toString());
        }
        String directory = dialog.open();
        if (directory != null) {
          rootDirectoryText.setText(directory);
        }
      }
    });

    Button browseArchiveButton = new Button(parent, SWT.PUSH);
    browseArchiveButton.setText(DataTransferMessages.SmartImportWizardPage_selectArchiveButton);
    browseArchiveButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        FileDialog dialog = new FileDialog(getShell(), SWT.NONE);
        dialog.setText(DataTransferMessages.SmartImportWizardPage_selectArchiveTitle);
        dialog.setFilterExtensions(new String[] { "*.zip;*.tar;*.tar.gz" }); //$NON-NLS-1$
        dialog.setFilterNames(new String[] { DataTransferMessages.SmartImportWizardPage_allSupportedArchives });
        if (rootDirectoryText.getText() != null) {
          File current = new File(rootDirectoryText.getText());
          if (current.isDirectory()) {
            dialog.setFilterPath(current.getAbsolutePath());
          } else if (current.isFile()) {
            dialog.setFilterPath(current.getParentFile().getAbsolutePath());
            dialog.setFileName(current.getName());
          }
        }
        if (dialog.getFilterPath() == null) {
          dialog.setFilterPath(ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile().toString());
        }
        String archive = dialog.open();
        if (archive != null) {
          rootDirectoryText.setText(archive);
        }
      }
    });
  }

  private void createConfigurationOptions(Composite parent) {
    final Button skipDotFoldersCheckbox = new Button(parent, SWT.CHECK);
    skipDotFoldersCheckbox.setText(DataTransferMessages.SmartImportWizardPage_skipDotFolders);
    skipDotFoldersCheckbox.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 4, 1));
    skipDotFoldersCheckbox.setSelection(this.skipDotFolders);
    skipDotFoldersCheckbox.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        SmartImportRootWizardPage.this.skipDotFolders = skipDotFoldersCheckbox.getSelection();
        refreshProposals();
      }
    });

    final Button closeProjectsCheckbox = new Button(parent, SWT.CHECK);
    closeProjectsCheckbox.setText(DataTransferMessages.SmartImportWizardPage_closeProjectsAfterImport);
    closeProjectsCheckbox.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 4, 1));
    closeProjectsCheckbox.setSelection(closeProjectsAfterImport);
    closeProjectsCheckbox.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        closeProjectsAfterImport = closeProjectsCheckbox.getSelection();
      }
    });

    Link showDetectorsLink = new Link(parent, SWT.NONE);
    showDetectorsLink.setText(DataTransferMessages.SmartImportWizardPage_showAvailableDetectors);
    GridData gridData = new GridData();
    gridData.horizontalAlignment = GridData.FILL;
    gridData.grabExcessHorizontalSpace = true;
    gridData.horizontalSpan = 4;
    showDetectorsLink.setLayoutData(gridData);
    showDetectorsLink.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        StringBuilder message = new StringBuilder();
        message.append(DataTransferMessages.SmartImportWizardPage_availableDetectors_description);
        message.append('\n');
        message.append('\n');
        List<String> extensionsLabels = new ArrayList<>(ProjectConfiguratorExtensionManager.getAllExtensionLabels());
        extensionsLabels.sort(null);
        for (String extensionLabel : extensionsLabels) {
          message.append("* "); //$NON-NLS-1$
          message.append(extensionLabel);
          message.append('\n');
        }
        MessageDialog.openInformation(getShell(), DataTransferMessages.SmartImportWizardPage_availableDetectors_title,
            message.toString());
      }
    });

    final Button detectNestedProjectsCheckbox = new Button(parent, SWT.CHECK);
    detectNestedProjectsCheckbox.setText(DataTransferMessages.SmartImportWizardPage_detectNestedProjects);
    detectNestedProjectsCheckbox.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 4, 1));
    detectNestedProjectsCheckbox.setSelection(this.detectNestedProjects);
    detectNestedProjectsCheckbox.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        SmartImportRootWizardPage.this.detectNestedProjects = detectNestedProjectsCheckbox.getSelection();
        refreshProposals();
      }
    });

    final Button configureProjectsCheckbox = new Button(parent, SWT.CHECK);
    configureProjectsCheckbox.setText(DataTransferMessages.SmartImportWizardPage_configureProjects);
    configureProjectsCheckbox.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 4, 1));
    configureProjectsCheckbox.setSelection(this.configureProjects);
    configureProjectsCheckbox.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        SmartImportRootWizardPage.this.configureProjects = configureProjectsCheckbox.getSelection();
        refreshProposals();
      }
    });
  }

  private Composite createProposalsGroup(Composite parent) {
    Composite res = new Composite(parent, SWT.NONE);
    res.setLayout(new GridLayout(2, false));

    Composite treeComposite = new Composite(res, SWT.NONE);
    treeComposite.setLayout(new GridLayout(1, false));
    treeComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    // the filter text of the FilteredTree of Eclipse, filtering the proposals as the user types
    filterText = new Text(treeComposite, SWT.BORDER);
    filterText.setMessage("type filter text"); //$NON-NLS-1$
    filterText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    tree = new CheckboxTreeViewer(treeComposite, SWT.CHECK | SWT.BORDER | SWT.FULL_SELECTION);
    tree.setUseHashlookup(true);
    GridData treeGridData = new GridData(SWT.FILL, SWT.FILL, true, true);
    treeGridData.heightHint = 150;
    tree.getControl().setLayoutData(treeGridData);
    tree.addFilter(patternFilter);
    filterText.addModifyListener(new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent e) {
        patternFilter.setPattern(filterText.getText());
        tree.refresh();
      }
    });

    tree.setContentProvider(new ITreeContentProvider() {
      @Override
      public Object[] getElements(Object inputElement) {
        return ((Map<File, ?>) inputElement).keySet().toArray(File[]::new);
      }

      @Override
      public Object[] getChildren(Object parentElement) {
        return null;
      }

      @Override
      public Object getParent(Object element) {
        return null;
      }

      @Override
      public boolean hasChildren(Object element) {
        return false;
      }
    });

    ProposalLabelProvider projectLP = new ProposalLabelProvider();
    tree.setLabelProvider(projectLP);
    ImportsComparator comparator = new ImportsComparator(projectLP);
    tree.setComparator(comparator);

    tree.addCheckStateListener(event -> {
      File dir = (File) event.getElement();
      if (isExistingProject(dir) || isExistingProjectName(dir)) {
        tree.setChecked(dir, false);
        return;
      }
      if (event.getChecked()) {
        SmartImportRootWizardPage.this.directoriesToImport.add(dir);
      } else {
        SmartImportRootWizardPage.this.directoriesToImport.remove(dir);
      }
      proposalsSelectionChanged();
    });

    tree.getTree().setHeaderVisible(true);
    TreeColumn folderCol = new TreeColumn(tree.getTree(), SWT.NONE);
    folderCol.setText(DataTransferMessages.SmartImportProposals_folder);
    folderCol.setWidth(500);
    TreeColumn impAsCol = new TreeColumn(tree.getTree(), SWT.NONE);
    impAsCol.setText(DataTransferMessages.SmartImportProposals_importAs);
    impAsCol.setWidth(150);
    folderCol.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        comparator.setColumn(0);
        tree.getTree().setSortColumn(folderCol);
        tree.getTree().setSortDirection(comparator.getDirection());
        tree.refresh();
      }
    });
    impAsCol.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        comparator.setColumn(1);
        tree.getTree().setSortColumn(impAsCol);
        tree.getTree().setSortDirection(comparator.getDirection());
        tree.refresh();
      }
    });
    comparator.setColumn(0);
    tree.getTree().setSortColumn(folderCol);
    tree.getTree().setSortDirection(SWT.UP);

    this.proposalSelectionDecorator = new ControlDecoration(tree.getTree(), SWT.TOP | SWT.LEFT);
    this.proposalSelectionDecorator
        .setDescriptionText(DataTransferMessages.SmartImportWizardPage_selectAtLeastOneFolderToOpenAsProject);
    this.proposalSelectionDecorator.hide();

    Composite selectionButtonsGroup = new Composite(res, SWT.NONE);
    GridLayout buttonsLayout = new GridLayout();
    buttonsLayout.marginWidth = 0;
    buttonsLayout.marginHeight = 0;
    selectionButtonsGroup.setLayout(buttonsLayout);
    selectionButtonsGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true));
    selectAllButton = new Button(selectionButtonsGroup, SWT.PUSH);
    selectAllButton.setText(DataTransferMessages.DataTransfer_selectAll);
    selectAllButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        for (TreeItem item : tree.getTree().getItems()) {
          File dir = (File) item.getData();
          if (isExistingProject(dir) || isExistingProjectName(dir)) {
            tree.setChecked(dir, false);
          } else {
            tree.setChecked(dir, true);
            SmartImportRootWizardPage.this.directoriesToImport.add(dir);
          }
        }
        proposalsSelectionChanged();
      }
    });
    deselectAllButton = new Button(selectionButtonsGroup, SWT.PUSH);
    deselectAllButton.setText(DataTransferMessages.DataTransfer_deselectAll);
    deselectAllButton.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        for (Object item : tree.getCheckedElements()) {
          tree.setChecked(item, false);
          SmartImportRootWizardPage.this.directoriesToImport.remove(item);
        }
        proposalsSelectionChanged();
      }
    });

    selectionSummary = new Label(selectionButtonsGroup, SWT.NONE);
    selectionSummary.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, true, 1, 1));
    selectionSummary.setText(DataTransferMessages.bind(DataTransferMessages.SmartImportProposals_selectionSummary, 0, 0));
    Button hideProjectsAlreadyInWorkspace = new Button(selectionButtonsGroup, SWT.CHECK);
    hideProjectsAlreadyInWorkspace.setText(DataTransferMessages.SmartImportProposals_hideExistingProjects);
    hideProjectsAlreadyInWorkspace.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        ViewerFilter[] currentFilters = tree.getFilters();
        ViewerFilter[] newFilters = null;
        hideAlreadyOpen = ((Button) e.widget).getSelection();
        if (hideAlreadyOpen) {
          newFilters = new ViewerFilter[currentFilters.length + 1];
          System.arraycopy(currentFilters, 0, newFilters, 0, currentFilters.length);
          newFilters[newFilters.length - 1] = existingProjectsFilter;
        } else {
          List<ViewerFilter> filters = new ArrayList<>(currentFilters.length > 0 ? currentFilters.length - 1 : 0);
          for (ViewerFilter filter : currentFilters) {
            if (filter != existingProjectsFilter) {
              filters.add(filter);
            }
          }
          newFilters = filters.toArray(new ViewerFilter[filters.size()]);
        }
        tree.setFilters(newFilters);
      }
    });
    hideProjectsAlreadyInWorkspace.setSelection(hideAlreadyOpen);
    // to trigger initial selection -> adds viewerfilter when hideAlreadyOpen = true
    hideProjectsAlreadyInWorkspace.notifyListeners(SWT.Selection, new Event());
    tree.setInput(Collections.emptyMap());
    return res;
  }

  /**
   * The filter of the proposals: the folders whose label contains the text of the filter (its words are the patterns, with
   * the wildcards {@code *} and {@code ?}), as the pattern filter of the filtered tree of Eclipse.
   */
  private final class ProposalPatternFilter extends ViewerFilter {
    private Pattern pattern;

    void setPattern(String text) {
      String trimmed = text.trim().toLowerCase(Locale.ROOT);
      if (trimmed.isEmpty()) {
        pattern = null;
        return;
      }
      StringBuilder regex = new StringBuilder(".*"); //$NON-NLS-1$
      for (char c : trimmed.toCharArray()) {
        if (c == '*') {
          regex.append(".*"); //$NON-NLS-1$
        } else if (c == '?') {
          regex.append('.');
        } else {
          regex.append(Pattern.quote(String.valueOf(c)));
        }
      }
      regex.append(".*"); //$NON-NLS-1$
      pattern = Pattern.compile(regex.toString(), Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    }

    @Override
    public boolean select(Viewer viewer, Object parentElement, Object element) {
      if (pattern == null) {
        return true;
      }
      return pattern.matcher(getFolderText((File) element)).matches();
    }
  }

  /** The table sorter: by folder or by the "Import as" label, ascending or descending. */
  private static final class ImportsComparator extends ViewerComparator {
    private int column = -1;
    private int direction = SWT.UP;
    private final ProposalLabelProvider projectLP;

    public ImportsComparator(ProposalLabelProvider projectLP) {
      this.projectLP = projectLP;
    }

    public void setColumn(int column) {
      if (this.column == column) {
        direction = (direction == SWT.UP) ? SWT.DOWN : SWT.UP;
      } else {
        this.column = column;
        direction = SWT.UP;
      }
    }

    public int getDirection() {
      return direction;
    }

    @Override
    public int compare(Viewer viewer, Object e1, Object e2) {
      File f1 = (File) e1;
      File f2 = (File) e2;
      int res = 0;
      switch (column) {
      case 0:
        res = f1.getAbsolutePath().compareToIgnoreCase(f2.getAbsolutePath());
        break;
      case 1:
        String t1 = getValue(projectLP.getColumnText(f1, 1));
        String t2 = getValue(projectLP.getColumnText(f2, 1));
        res = t1.compareToIgnoreCase(t2);
        break;
      default:
        break;
      }
      return direction == SWT.UP ? res : -res;
    }

    private String getValue(String s) {
      return s == null ? "" : s; //$NON-NLS-1$
    }
  }

  private IProject findExistingProject(File element) {
    // VSCODE: the locations of the projects are canonical (/private/var on macOS), the folders may not be (/var)
    File canonicalElement = canonical(element);
    for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
      IPath location = project.getLocation();
      if (location != null && canonicalElement.equals(canonical(location.toFile()))) {
        return project;
      }
    }
    return null;
  }

  private static File canonical(File file) {
    try {
      return file.getCanonicalFile();
    } catch (IOException e) {
      return file.getAbsoluteFile();
    }
  }

  protected boolean isExistingProject(File element) {
    return findExistingProject(element) != null;
  }

  protected boolean isExistingProjectName(File element) {
    String name = element.getName();
    return !name.isEmpty() && ResourcesPlugin.getWorkspace().getRoot().getProject(name).exists();
  }

  protected void validatePage() {
    // reset error message
    setErrorMessage(null);
    // order of invocation of setErrorMessage == reverse order of priority
    // ie: most important one must call setErrorMessage last
    if (tree.getCheckedElements().length == 0) {
      this.proposalSelectionDecorator.show();
      setErrorMessage(this.proposalSelectionDecorator.getDescriptionText());
    } else {
      this.proposalSelectionDecorator.hide();
    }

    if (!sourceIsValid()) {
      this.rootDirectoryTextDecorator.show();
      setErrorMessage(this.rootDirectoryTextDecorator.getDescriptionText());
    } else {
      this.rootDirectoryTextDecorator.hide();
    }
    setPageComplete(isPageComplete());
  }

  @Override
  public boolean isPageComplete() {
    return sourceIsValid() && getWizard().createOrGetConfiguredImportJob() != null
        && (getWizard().createOrGetConfiguredImportJob().getDirectoriesToImport() == null
            || !getWizard().createOrGetConfiguredImportJob().getDirectoriesToImport().isEmpty());
  }

  private boolean sourceIsValid() {
    return this.selection != null && (this.selection.isDirectory() || SmartImportWizard.isValidArchive(this.selection));
  }

  /**
   * @return The selected source of import (can be a directory or an archive)
   */
  public File getSelectedRoot() {
    return this.selection;
  }

  /** Sets the initial source to import. */
  public void setInitialImportRoot(File directoryOrArchive) {
    this.selection = directoryOrArchive;
    this.rootDirectoryText.setText(directoryOrArchive.getAbsolutePath());
    refreshProposals();
  }

  /**
   * @return The user selected working sets to assign to imported projects.
   */
  public Set<IWorkingSet> getSelectedWorkingSets() {
    this.workingSets = new HashSet<>();
    Collections.addAll(this.workingSets, this.workingSetsGroup.getSelectedWorkingSets());
    return this.workingSets;
  }

  private void proposalsSelectionChanged() {
    if (getWizard().createOrGetConfiguredImportJob() != null) {
      if (potentialProjects.size() == 1 && potentialProjects.values().iterator().next().isEmpty()) {
        getWizard().createOrGetConfiguredImportJob().setDirectoriesToImport(null);
        getWizard().createOrGetConfiguredImportJob().setExcludedDirectories(null);
        selectionSummary.setText(
            DataTransferMessages.bind(DataTransferMessages.SmartImportProposals_selectionSummary, directoriesToImport.size(), 1));
      } else {
        Set<File> excludedDirectories = new HashSet<>(potentialProjects.keySet());
        for (File directory : this.directoriesToImport) {
          excludedDirectories.remove(directory);
        }
        getWizard().createOrGetConfiguredImportJob().setDirectoriesToImport(directoriesToImport);
        getWizard().createOrGetConfiguredImportJob().setExcludedDirectories(excludedDirectories);
        selectionSummary.setText(DataTransferMessages.bind(DataTransferMessages.SmartImportProposals_selectionSummary,
            directoriesToImport.size(), potentialProjects.size()));
      }
    }
    setPageComplete(isPageComplete());
  }

  /** @return whether the job should recurse to find nested projects */
  public boolean isDetectNestedProject() {
    return this.detectNestedProjects;
  }

  /** @return whether the job should configure projects (add natures etc...) */
  public boolean isConfigureProjects() {
    return this.configureProjects;
  }

  boolean isCloseProjectsAfterImport() {
    return closeProjectsAfterImport;
  }

  /** Public for tests. */
  public void setCloseProjectsAfterImport(boolean closeProjectsAfterImport) {
    this.closeProjectsAfterImport = closeProjectsAfterImport;
  }

  /** @return whether folders starting with '.' should be skipped during scanning */
  boolean isSkipDotFolders() {
    return skipDotFolders;
  }

  /**
   * Computes the proposals of the source in a job: the tree shows the "Inspecting" item until the job is done, and the page
   * stays usable meanwhile.
   */
  private void refreshProposals() {
    stopAndDisconnectCurrentWork();
    this.potentialProjects = Collections.emptyMap();
    proposalsUpdated();
    // compute new state
    if (sourceIsValid()) {
      tree.getControl().setEnabled(false);
      selectAllButton.setEnabled(false);
      deselectAllButton.setEnabled(false);
      TreeItem computingItem = new TreeItem(tree.getTree(), SWT.DEFAULT);
      computingItem.setText(DataTransferMessages.bind(DataTransferMessages.SmartImportJob_inspecting, selection.getAbsolutePath()));
      final SmartImportJob importJob = getWizard().createOrGetConfiguredImportJob();
      final ProgressMonitorPart progressPart = wizardProgressMonitor.get();
      final DelegateProgressMonitorInUIThread jobMonitor = new DelegateProgressMonitorInUIThread(progressPart);
      refreshProposalsJob = new Job(DataTransferMessages.bind(DataTransferMessages.SmartImportJob_inspecting,
          selection.getAbsolutePath())) {
        @Override
        protected IProgressMonitor createMonitor() {
          // VSCODE: the progress of the job is shown in the wizard progress part, not in a progress notification
          return jobMonitor;
        }

        @Override
        protected IStatus run(IProgressMonitor monitor) {
          IStatus result;
          Map<File, List<ProjectConfigurator>> found = null;
          try {
            found = new HashMap<>(importJob.getImportProposals(monitor));
            if (!found.containsKey(importJob.getRoot())) {
              found.put(importJob.getRoot(), Collections.emptyList());
            }
            result = monitor.isCanceled() ? Status.CANCEL_STATUS : Status.OK_STATUS;
          } catch (RuntimeException ex) {
            result = new Status(IStatus.ERROR, "org.eclipse.ui.ide", ex.getMessage(), ex); //$NON-NLS-1$
          }
          final IStatus status = result;
          final Map<File, List<ProjectConfigurator>> proposals = found;
          Display.getDefault().asyncExec(() -> {
            if (refreshProposalsJob == this) {
              proposalsComputed(importJob, status, proposals, computingItem);
              if (progressPart != null && !progressPart.isDisposed()) {
                progressPart.setVisible(false);
              }
            }
          });
          return status;
        }
      };
      refreshProposalsJob.setPriority(Job.INTERACTIVE);
      refreshProposalsJob.setUser(true);
      if (progressPart != null) {
        progressPart.attachToCancelComponent(null);
        progressPart.setVisible(true);
        Button stopButton = progressPart.getStopButton();
        stopButton.removeSelectionListener(cancelWorkListener);
        stopButton.addSelectionListener(cancelWorkListener);
      }
      refreshProposalsJob.schedule(0);
    }
  }

  /**
   * The monitor of the proposals job, the progress is forwarded to the wizard progress part (its updates are made on the
   * display thread by the part). Cancel is the job's: the Stop button of the part cancels the job.
   * VSCODE: the job monitor of Eclipse's delegate progress monitor of the wizard progress bar.
   */
  private static final class DelegateProgressMonitorInUIThread implements IProgressMonitor {
    private final ProgressMonitorPart part;
    private volatile boolean canceled;

    DelegateProgressMonitorInUIThread(ProgressMonitorPart part) {
      this.part = part;
    }

    @Override
    public void beginTask(String name, int totalWork) {
      if (part != null) {
        part.beginTask(name, totalWork);
      }
    }

    @Override
    public void done() {
      if (part != null) {
        part.done();
      }
    }

    @Override
    public void internalWorked(double work) {
      if (part != null) {
        part.internalWorked(work);
      }
    }

    @Override
    public boolean isCanceled() {
      return canceled;
    }

    @Override
    public void setCanceled(boolean value) {
      canceled = value;
    }

    @Override
    public void setTaskName(String name) {
      if (part != null) {
        part.setTaskName(name);
      }
    }

    @Override
    public void subTask(String name) {
      if (part != null) {
        part.subTask(name);
      }
    }

    @Override
    public void worked(int work) {
      internalWorked(work);
    }
  }

  /** Shows the proposals of a finished job (on the display thread). */
  private void proposalsComputed(SmartImportJob importJob, IStatus result, Map<File, List<ProjectConfigurator>> found,
      TreeItem computingItem) {
    if (tree.getControl().isDisposed()) {
      return;
    }
    if (result.isOK()) {
      computingItem.dispose();
      if (sourceIsValid() && getWizard().createOrGetConfiguredImportJob() == importJob) {
        this.potentialProjects = found;
        proposalsUpdated();
      }
      tree.getTree().setEnabled(potentialProjects.size() > 1);
      selectAllButton.setEnabled(potentialProjects.size() > 1);
      deselectAllButton.setEnabled(potentialProjects.size() > 1);
    } else if (result.getCode() == IStatus.CANCEL) {
      computingItem.setText(DataTransferMessages.SmartImportProposals_inspecitionCanceled);
    } else if (result.getCode() == IStatus.ERROR) {
      computingItem.setText(DataTransferMessages.bind(DataTransferMessages.SmartImportProposals_errorWhileInspecting,
          result.getMessage()));
    }
  }

  private void stopAndDisconnectCurrentWork() {
    if (refreshProposalsJob != null) {
      refreshProposalsJob.cancel();
    }
  }

  private void proposalsUpdated() {
    tree.setInput(potentialProjects);
    this.directoriesToImport = new HashSet<>();
    for (File dir : potentialProjects.keySet()) {
      if (!(isExistingProject(dir) || isExistingProjectName(dir))) {
        directoriesToImport.add(dir);
      }
    }
    tree.setCheckedElements(directoriesToImport.toArray(new Object[directoriesToImport.size()]));
    proposalsSelectionChanged();
    validatePage();
  }

  @Override
  public void dispose() {
    saveWidgetStates();
    stopAndDisconnectCurrentWork();
    super.dispose();
  }

  /** Load widget states from DialogSettings. */
  private void loadWidgetStates() {
    IDialogSettings dialogSettings = getDialogSettings();
    // only load settings if there is a key available to preserve default values
    if (dialogSettings != null && dialogSettings.get(STORE_HIDE_ALREADY_OPEN) != null) {
      hideAlreadyOpen = dialogSettings.getBoolean(STORE_HIDE_ALREADY_OPEN);
      closeProjectsAfterImport = dialogSettings.getBoolean(STORE_CLOSE_IMPORTED);
      detectNestedProjects = dialogSettings.getBoolean(STORE_NESTED_PROJECTS);
      configureProjects = dialogSettings.getBoolean(STORE_CONFIGURE_NATURES);
      if (dialogSettings.get(STORE_SKIP_DOT_FOLDERS) != null) {
        skipDotFolders = dialogSettings.getBoolean(STORE_SKIP_DOT_FOLDERS);
      }
    }
  }

  /** Save widget states to DialogSettings. */
  private void saveWidgetStates() {
    IDialogSettings dialogSettings = getDialogSettings();
    if (dialogSettings != null) {
      dialogSettings.put(STORE_HIDE_ALREADY_OPEN, hideAlreadyOpen);
      dialogSettings.put(STORE_CLOSE_IMPORTED, closeProjectsAfterImport);
      dialogSettings.put(STORE_NESTED_PROJECTS, detectNestedProjects);
      dialogSettings.put(STORE_CONFIGURE_NATURES, configureProjects);
      dialogSettings.put(STORE_SKIP_DOT_FOLDERS, skipDotFolders);
    }
  }
}
