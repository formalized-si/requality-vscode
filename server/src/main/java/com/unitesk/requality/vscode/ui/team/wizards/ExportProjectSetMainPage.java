package com.unitesk.requality.vscode.ui.team.wizards;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.unitesk.requality.vscode.ui.jface.action.IContributionItem;
import com.unitesk.requality.vscode.ui.jface.dialogs.IMessageProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.CheckboxTableViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.TableViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardPage;
import com.unitesk.requality.vscode.ui.navigator.ResourceComparator;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.IWorkspaceRoot;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.resources.mapping.ResourceMapping;
import com.unitesk.requality.vscode.ui.runtime.Adapters;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.FillLayout;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Table;
import com.unitesk.requality.vscode.ui.team.RepositoryProvider;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSetManager;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.dialogs.IWorkingSetSelectionDialog;
import com.unitesk.requality.vscode.ui.workbench.model.WorkbenchLabelProvider;

/**
 * {@code org.eclipse.team.internal.ui.wizards.ExportProjectSetMainPage}: the page of the Team Project Set export that selects
 * the projects or the working sets to include. The Export working sets check box switches between the project page (the
 * projects with a repository provider) and the working set page (the working sets with resources, and the projects of the
 * checked ones). The Requality product has no repository provider, so no project can be exported.
 * VSCODE: the page is shown by the visibility of its two pages (Eclipse uses a PageBook); the gray text of the projects
 * that cannot be exported is not ported.
 */
public class ExportProjectSetMainPage extends WizardPage {
  private final List<IProject> passedInSelectedProjects = new ArrayList<>();
  private Button exportWorkingSets;
  private Composite book;
  private ProjectPage projectPage;
  private WorkingSetPage workingSetPage;
  private IExportProjectSetPage selectedPage;

  public ExportProjectSetMainPage(String pageName, String title, Object titleImage) {
    super(pageName, title, titleImage);
    setDescription("Define which projects or working sets should be exported into the team project file.");
  }

  /** {@code ExportProjectSetMainPage.isProjectExportable}: the project has a repository provider. */
  static boolean isProjectExportable(IProject project) {
    return RepositoryProvider.getProvider(project) != null;
  }

  /** The projects of an object: those of its resource mapping, or the project of its resource (none for the root). */
  static IProject[] getProjectsForObject(Object object) {
    ResourceMapping resourceMapping = Adapters.adapt(object, ResourceMapping.class);
    if (resourceMapping != null)
      return resourceMapping.getProjects();
    IResource resource = Adapters.adapt(object, IResource.class);
    if (resource != null && resource.getType() != IResource.ROOT)
      return new IProject[] { resource.getProject() };
    return null;
  }

  /** {@code ExportProjectSetMainPage.isWorkingSetSupported}: a working set with at least one resource. */
  static boolean isWorkingSetSupported(IWorkingSet workingSet) {
    if (!workingSet.isEmpty() && !workingSet.isAggregateWorkingSet())
      for (IAdaptable element : workingSet.getElements())
        if (Adapters.adapt(element, IResource.class) != null)
          return true;
    return false;
  }

  @Override
  public void createControl(Composite parent) {
    Composite c = new Composite(parent, SWT.NONE);
    c.setLayout(new GridLayout(1, false));
    c.setLayoutData(new GridData(GridData.FILL_BOTH));

    exportWorkingSets(c);

    book = new Composite(c, SWT.NONE);
    book.setLayout(new FillLayout());
    book.setLayoutData(new GridData(GridData.FILL_BOTH));

    workingSetPage = new WorkingSetPage();
    workingSetPage.createControl(book);
    projectPage = new ProjectPage();
    // pass in the selected, exportable projects
    for (IProject project : passedInSelectedProjects)
      if (isProjectExportable(project)) {
        projectPage.selectedProjects.add(project);
        projectPage.referenceCountProjects.add(project);
      }
    projectPage.createControl(book);

    setControl(c);
    showPage(projectPage);
    selectedPage = projectPage;
  }

  /** The Export working sets check box: the working set page is shown while it is checked. */
  private void exportWorkingSets(Composite composite) {
    exportWorkingSets = new Button(composite, SWT.CHECK | SWT.LEFT);
    exportWorkingSets.setText("E&xport working sets");
    exportWorkingSets.setSelection(false);
    exportWorkingSets.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        if (((Button) e.widget).getSelection()) {
          showPage(workingSetPage);
          selectedPage = workingSetPage;
          workingSetPage.refresh();
          workingSetPage.updateEnablement();
        } else {
          showPage(projectPage);
          selectedPage = projectPage;
          projectPage.updateEnablement();
        }
      }
    });
  }

  /** Shows one of the pages of the book and hides the other. */
  private void showPage(IExportProjectSetPage page) {
    Control shown = page.getControl();
    projectPage.getControl().setVisible(shown == projectPage.getControl());
    workingSetPage.getControl().setVisible(shown == workingSetPage.getControl());
    book.layout(true, true);
  }

  /** The working sets of the export (the working set page), or none while the project page is shown. */
  public IWorkingSet[] getSelectedWorkingSets() {
    return selectedPage.getWorkingSet().toArray(new IWorkingSet[0]);
  }

  /** The projects selected for the export. */
  public IProject[] getSelectedProjects() {
    return selectedPage.getSelectedProjects().toArray(new IProject[0]);
  }

  /** The projects that the export refers to (the projects of the checked working sets are counted once each). */
  public IProject[] getReferenceCountProjects() {
    return selectedPage.getReferenceCountProjects().toArray(new IProject[0]);
  }

  /** The projects the wizard starts with (the selection of the workbench), kept until the page is created. */
  public void setSelectedProjects(IProject[] selectedProjects) {
    passedInSelectedProjects.addAll(Arrays.asList(selectedProjects));
  }

  /** The state of the page that the export refers to: the project page or the working set page. */
  private interface IExportProjectSetPage {
    Control getControl();

    Set<IProject> getSelectedProjects();

    List<IProject> getReferenceCountProjects();

    List<IWorkingSet> getWorkingSet();
  }

  /** The content of the table of the projects and of the working sets (the input decides the rows). */
  private static final class ProjectContentProvider implements IStructuredContentProvider {
    @Override
    public Object[] getElements(Object inputElement) {
      if (inputElement instanceof IWorkspaceRoot root) {
        List<IProject> projectList = new ArrayList<>();
        for (IProject project : root.getProjects())
          if (isProjectExportable(project))
            projectList.add(project);
        return projectList.toArray(new IProject[0]);
      } else if (inputElement instanceof IWorkingSetManager manager) {
        List<IWorkingSet> resourceSets = new ArrayList<>();
        for (IWorkingSet set : manager.getAllWorkingSets())
          if (isWorkingSetSupported(set))
            resourceSets.add(set);
        return resourceSets.toArray(new IWorkingSet[0]);
      } else if (inputElement instanceof Set<?> inputElementSet) {
        Set<IProject> projects = new LinkedHashSet<>();
        for (Object element : inputElementSet) {
          IProject[] found = getProjectsForObject(element);
          if (found != null)
            projects.addAll(Arrays.asList(found));
        }
        return projects.toArray(new IProject[0]);
      }
      return new Object[0];
    }

    @Override
    public void dispose() {
    }

    @Override
    public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
    }
  }

  /** The page that selects the projects to export. */
  private final class ProjectPage implements IExportProjectSetPage {
    private Composite projectComposite;
    private CheckboxTableViewer tableViewer;
    private final Set<IProject> selectedProjects = new LinkedHashSet<>();
    private final List<IProject> referenceCountProjects = new ArrayList<>();
    private final List<IWorkingSet> selectedWorkingSet = new ArrayList<>();

    void createControl(Composite parent) {
      projectComposite = new Composite(parent, SWT.NONE);
      projectComposite.setLayout(new GridLayout(1, false));
      projectComposite.setLayoutData(new GridData(GridData.FILL_BOTH));

      Label label = new Label(projectComposite, SWT.NONE);
      label.setText("&Select the projects to include in the Team Project Set:");
      label.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

      Table table = new Table(projectComposite, SWT.CHECK | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
      GridData data = new GridData(GridData.FILL_BOTH);
      data.heightHint = 300;
      table.setLayoutData(data);
      tableViewer = new CheckboxTableViewer(table);
      tableViewer.setContentProvider(new ProjectContentProvider());
      tableViewer.setLabelProvider(new WorkbenchLabelProvider());
      tableViewer.setComparator(new ResourceComparator(ResourceComparator.NAME));
      tableViewer.addCheckStateListener(event -> {
        Object temp = event.getElement();
        if (temp instanceof IProject project) {
          if (event.getChecked()) {
            selectedProjects.add(project);
            referenceCountProjects.add(project);
          } else {
            selectedProjects.remove(project);
            referenceCountProjects.remove(project);
          }
        }
        updateEnablement();
      });

      Composite buttonComposite = new Composite(projectComposite, SWT.NONE);
      buttonComposite.setLayout(new GridLayout(2, false));
      buttonComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
      Button selectAll = new Button(buttonComposite, SWT.PUSH);
      selectAll.setText("S&elect All");
      selectAll.addListener(SWT.Selection, e -> {
        tableViewer.setAllChecked(true);
        selectedProjects.clear();
        Collections.addAll(selectedProjects, toProjects(tableViewer.getCheckedElements()));
        updateEnablement();
      });
      Button deselectAll = new Button(buttonComposite, SWT.PUSH);
      deselectAll.setText("&Deselect All");
      deselectAll.addListener(SWT.Selection, e -> {
        tableViewer.setAllChecked(false);
        selectedProjects.clear();
        updateEnablement();
      });

      tableViewer.setInput(ResourcesPlugin.getWorkspace().getRoot());
      tableViewer.setCheckedElements(selectedProjects.toArray(new IProject[0]));
      updateEnablement();
    }

    @Override
    public Control getControl() {
      return projectComposite;
    }

    void updateEnablement() {
      boolean complete = !selectedProjects.isEmpty();
      if (complete) {
        setErrorMessage(null);
        setDescription("Export selected resources into the team project file.");
      } else {
        setErrorMessage("A project must be selected.");
      }
      setPageComplete(complete);
    }

    @Override
    public Set<IProject> getSelectedProjects() {
      return selectedProjects;
    }

    @Override
    public List<IProject> getReferenceCountProjects() {
      return referenceCountProjects;
    }

    @Override
    public List<IWorkingSet> getWorkingSet() {
      return selectedWorkingSet;
    }
  }

  /** The page that selects the working sets to export, and the projects of the checked ones. */
  private final class WorkingSetPage implements IExportProjectSetPage {
    private Composite projectComposite;
    private CheckboxTableViewer wsTableViewer;
    private TableViewer tableViewer;
    private final Set<IProject> selectedProjects = new LinkedHashSet<>();
    private final List<IProject> referenceCountProjects = new ArrayList<>();
    private final List<IWorkingSet> selectedWorkingSet = new ArrayList<>();
    /** Whether the page has been displayed: no error until the first modification. */
    private boolean pageShown = false;

    void createControl(Composite parent) {
      projectComposite = new Composite(parent, SWT.NONE);
      projectComposite.setLayout(new GridLayout(1, false));
      projectComposite.setLayoutData(new GridData(GridData.FILL_BOTH));

      Label label = new Label(projectComposite, SWT.NONE);
      label.setText("Select the &working sets to include in the Team Project Set:");
      label.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

      Composite lists = new Composite(projectComposite, SWT.NONE);
      lists.setLayout(new GridLayout(2, true));
      lists.setLayoutData(new GridData(GridData.FILL_BOTH));
      addWorkingSetSection(lists);
      addProjectSection(lists);
      addButtons(projectComposite);
      setPageComplete(false);
    }

    private void addWorkingSetSection(Composite composite) {
      Table wsTable = new Table(composite, SWT.CHECK | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
      GridData data = new GridData(GridData.FILL_BOTH);
      data.heightHint = 300;
      wsTable.setLayoutData(data);
      wsTableViewer = new CheckboxTableViewer(wsTable);
      wsTableViewer.setContentProvider(new ProjectContentProvider());
      wsTableViewer.setLabelProvider(new WorkbenchLabelProvider());
      wsTableViewer.addCheckStateListener(event -> {
        if (event.getElement() instanceof IWorkingSet workingSet) {
          if (event.getChecked())
            workingSetAdded(workingSet);
          else
            workingSetRemoved(workingSet);
          tableViewer.setInput(selectedProjects);
        }
        updateEnablement();
      });
      wsTableViewer.setInput(PlatformUI.getWorkbench().getWorkingSetManager());
    }

    private void addProjectSection(Composite composite) {
      Table table = new Table(composite, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
      GridData data = new GridData(GridData.FILL_BOTH);
      data.heightHint = 300;
      table.setLayoutData(data);
      tableViewer = new TableViewer(table);
      tableViewer.setContentProvider(new ProjectContentProvider());
      tableViewer.setLabelProvider(new WorkbenchLabelProvider());
      tableViewer.setComparator(new ResourceComparator(ResourceComparator.NAME));
    }

    private void addButtons(Composite parent) {
      Composite buttonComposite = new Composite(parent, SWT.NONE);
      buttonComposite.setLayout(new GridLayout(3, false));
      buttonComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false));

      Button selectAll = new Button(buttonComposite, SWT.PUSH);
      selectAll.setText("S&elect All");
      selectAll.addListener(SWT.Selection, e -> {
        wsTableViewer.setAllChecked(true);
        selectedProjects.clear();
        selectedWorkingSet.clear();
        for (Object c : wsTableViewer.getCheckedElements()) {
          selectedWorkingSet.add((IWorkingSet) c);
          addProjects(((IWorkingSet) c).getElements());
        }
        tableViewer.setInput(selectedProjects);
        updateEnablement();
      });
      Button deselectAll = new Button(buttonComposite, SWT.PUSH);
      deselectAll.setText("&Deselect All");
      deselectAll.addListener(SWT.Selection, e -> {
        wsTableViewer.setAllChecked(false);
        selectedWorkingSet.clear();
        selectedProjects.clear();
        referenceCountProjects.clear();
        tableViewer.setInput(selectedProjects);
        updateEnablement();
      });
      Button newWorkingSet = new Button(buttonComposite, SWT.PUSH);
      newWorkingSet.setText("Edit...");
      newWorkingSet.addListener(SWT.Selection, e -> {
        IWorkingSetManager workingSetManager = PlatformUI.getWorkbench().getWorkingSetManager();
        IWorkingSetSelectionDialog wsWizard = workingSetManager.createWorkingSetSelectionDialog(getShell(), false);
        if (wsWizard != null) {
          wsWizard.open();
          // recalculate the working sets, the projects of the checked ones are selected again
          selectedWorkingSet.clear();
          referenceCountProjects.clear();
          selectedProjects.clear();
          wsTableViewer.setInput(workingSetManager);
          for (Object c : wsTableViewer.getCheckedElements()) {
            selectedWorkingSet.add((IWorkingSet) c);
            addProjects(((IWorkingSet) c).getElements());
          }
          wsTableViewer.setInput(workingSetManager);
          tableViewer.setInput(selectedProjects);
        }
      });
    }

    @Override
    public Control getControl() {
      return projectComposite;
    }

    void refresh() {
      wsTableViewer.setInput(PlatformUI.getWorkbench().getWorkingSetManager());
    }

    void updateEnablement() {
      boolean complete = !selectedProjects.isEmpty() && !selectedWorkingSet.isEmpty();
      boolean allExportable = complete;
      // check if there is at least one exportable project selected
      if (complete || !pageShown) {
        complete = false;
        for (IProject selectedProject : selectedProjects) {
          if (isProjectExportable(selectedProject))
            complete = true;
          else
            allExportable = false;
        }
        if (!complete && !pageShown) {
          setErrorMessage(null);
          setMessage("Define which projects or working sets should be exported into the team project file.");
        } else if (complete) {
          if (allExportable) {
            setErrorMessage(null);
            setMessage("Export selected resources into the team project file.");
          } else {
            setErrorMessage(null);
            setMessage("Some projects from the selected working sets cannot be exported.", IMessageProvider.WARNING);
          }
        } else {
          setErrorMessage("None of the selected working sets have an available project to export.");
        }
      } else {
        setErrorMessage("A working set must be selected.");
      }
      setPageComplete(complete);
      pageShown = true;
    }

    @Override
    public Set<IProject> getSelectedProjects() {
      return selectedProjects;
    }

    @Override
    public List<IProject> getReferenceCountProjects() {
      return referenceCountProjects;
    }

    @Override
    public List<IWorkingSet> getWorkingSet() {
      return selectedWorkingSet;
    }

    private void workingSetAdded(IWorkingSet workingSet) {
      selectedWorkingSet.add(workingSet);
      addProjects(workingSet.getElements());
    }

    private void workingSetRemoved(IWorkingSet workingSet) {
      selectedWorkingSet.remove(workingSet);
      Set<IProject> tempSet = new HashSet<>();
      for (IAdaptable element : workingSet.getElements()) {
        IProject[] projects = getProjectsForObject(element);
        if (projects != null)
          tempSet.addAll(Arrays.asList(projects));
      }
      if (!tempSet.isEmpty()) {
        selectedProjects.removeAll(tempSet);
        referenceCountProjects.removeAll(tempSet);
        selectedProjects.addAll(referenceCountProjects);
      }
    }

    private void addProjects(IAdaptable[] elements) {
      Set<IProject> tempSet = new HashSet<>();
      for (IAdaptable element : elements) {
        IProject[] projects = getProjectsForObject(element);
        if (projects != null)
          tempSet.addAll(Arrays.asList(projects));
      }
      selectedProjects.addAll(tempSet);
      referenceCountProjects.addAll(tempSet);
    }
  }

  /** The projects of the checked items of a table. */
  private static IProject[] toProjects(Object[] checked) {
    List<IProject> projects = new ArrayList<>();
    for (Object o : checked)
      if (o instanceof IProject project)
        projects.add(project);
    return projects.toArray(new IProject[0]);
  }
}
