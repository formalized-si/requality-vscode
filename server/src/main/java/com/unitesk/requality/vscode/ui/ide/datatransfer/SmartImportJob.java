package com.unitesk.requality.vscode.ui.ide.datatransfer;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.core.ServerContext;
import com.unitesk.requality.vscode.core.Workspace;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.resources.ICommand;
import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IFolder;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IProjectDescription;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.IWorkspace;
import com.unitesk.requality.vscode.ui.resources.IWorkspaceRoot;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Job;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/**
 * {@code org.eclipse.ui.internal.wizards.datatransfer.SmartImportJob}: a job that imports a folder (or the folder an archive
 * was expanded to) as a project, detects the nested projects in it, and configures the projects with the
 * {@link ProjectConfigurator}s it finds. The existing projects of the workspace are skipped.
 *
 * <p>
 * VSCODE: the nested folders are crawled one after the other (Eclipse crawls them in a job group), the projects are
 * created without a batched resource delta, and the auto-build is switched off through the workspace emulation.
 * </p>
 */
public class SmartImportJob extends Job {
  private static final String IDE_WORKBENCH = "org.eclipse.ui.ide"; //$NON-NLS-1$

  /** The folder could not be imported as a project ({@code CouldNotImportProjectException}). */
  static final class CouldNotImportProjectException extends Exception {
    private static final long serialVersionUID = 1L;

    CouldNotImportProjectException(File directory, String message) {
      super(message == null ? directory.getAbsolutePath() : directory.getAbsolutePath() + ": " + message);
    }

    CouldNotImportProjectException(File directory, Throwable cause) {
      super(directory.getAbsolutePath(), cause);
    }
  }

  // Input parameters
  private final File rootDirectory;
  private Set<File> directoriesToImport;
  private Set<File> excludedDirectories;
  private boolean discardRootProject;
  private boolean deepChildrenDetection;
  private final boolean configureProjects;
  private boolean closeProjectsAfterImport;
  private boolean skipDotFolders = true;
  private boolean reconfigureEclipseProjects;
  private IWorkingSet[] workingSets;

  // working fields
  private IProject rootProject;
  private final IWorkspaceRoot workspaceRoot;
  private ProjectConfiguratorExtensionManager configurationManager;

  protected Map<File, List<ProjectConfigurator>> importProposals;
  private final Map<IProject, List<ProjectConfigurator>> report;
  private final Map<IPath, Exception> errors;

  /**
   * Builds a new instance of the job.
   *
   * @param rootDirectory the root directory to import and analyze
   * @param workingSets working sets to assign to imported projects
   * @param configureProjects whether projects are configured (natures etc.) according to their metadata
   * @param recuriveChildrenDetection whether to recurse for detection of nested projects
   */
  public SmartImportJob(File rootDirectory, Set<IWorkingSet> workingSets, boolean configureProjects,
      boolean recuriveChildrenDetection) {
    super(rootDirectory.getAbsolutePath());
    this.workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
    this.rootDirectory = rootDirectory;
    setWorkingSets(workingSets);
    this.configureProjects = configureProjects;
    this.deepChildrenDetection = recuriveChildrenDetection;
    this.report = Collections.synchronizedMap(new HashMap<>());
    this.errors = Collections.synchronizedMap(new HashMap<>());
  }

  /** Sets the working sets to assign to newly imported projects. */
  public void setWorkingSets(Set<IWorkingSet> workingSets) {
    if (workingSets != null) {
      this.workingSets = workingSets.toArray(new IWorkingSet[workingSets.size()]);
    } else {
      this.workingSets = new IWorkingSet[0];
    }
  }

  /** The root directory for the import operation. */
  public File getRoot() {
    return this.rootDirectory;
  }

  /**
   * Sets the directories that have been detected by preliminary detection and that the user has selected to import. Those
   * are imported and configured in any case.
   */
  public void setDirectoriesToImport(Set<File> directories) {
    this.directoriesToImport = directories;
  }

  /**
   * Sets the directories the user configured as NOT to import. Projects under those directories may be imported, but never
   * a project directly in one of them.
   */
  public void setExcludedDirectories(Set<File> directories) {
    this.excludedDirectories = directories;
  }

  @Override
  public IStatus run(IProgressMonitor monitor) {
    IWorkspace workspace = ResourcesPlugin.getWorkspace();
    Workspace ws = ServerContext.server().workspace();
    boolean isAutoBuilding = ws.isAutoBuilding();
    try {
      if (isAutoBuilding) {
        ws.setAutoBuilding(false);
      }

      if (directoriesToImport != null) {
        this.deepChildrenDetection = false;
        Comparator<File> rootToLeafComparator = (arg0, arg1) -> {
          int lengthDiff = arg0.getAbsolutePath().length() - arg1.getAbsolutePath().length();
          if (lengthDiff != 0) {
            return lengthDiff;
          }
          return arg0.compareTo(arg1);
        };
        SortedSet<File> directories = new TreeSet<>(rootToLeafComparator);
        directories.addAll(this.directoriesToImport);
        SortedMap<File, IProject> leafToRootProjects = new TreeMap<>(Collections.reverseOrder(rootToLeafComparator));
        final Set<IProject> alreadyConfiguredProjects = new HashSet<>();
        // Create all projects in one workspace operation, so listeners see a single resource delta instead of one per
        // project. No configurator runs here. AVOID_UPDATE would defer that delta to the workspace notification job.
        workspace.run(workspaceMonitor -> {
          for (final File directoryToImport : directories) {
            final boolean alreadyAnEclipseProject = new File(directoryToImport, IProjectDescription.DESCRIPTION_FILE_NAME)
                .isFile();
            try {
              IProject newProject = toExistingOrNewProject(directoryToImport, workspaceMonitor, IResource.BACKGROUND_REFRESH);
              if (alreadyAnEclipseProject) {
                alreadyConfiguredProjects.add(newProject);
              }
              leafToRootProjects.put(directoryToImport, newProject);
            } catch (CouldNotImportProjectException ex) {
              IPath path = Path.fromOSString(directoryToImport.getAbsolutePath());
              this.errors.put(path, ex);
            }
          }
        }, this.workspaceRoot, IResource.NONE, null);
        if (configureProjects) {
          // VSCODE: the configuration of the directories runs one after the other (Eclipse runs them as jobs).
          for (final IProject newProject : leafToRootProjects.values()) {
            try {
              importProjectAndChildrenRecursively(newProject, !alreadyConfiguredProjects.contains(newProject), monitor);
            } catch (Exception ex) {
              RLogger.logError(ex);
            }
          }
        }
      } else { // no specific projects included, consider only root
        File rootProjectFile = new File(this.rootDirectory, IProjectDescription.DESCRIPTION_FILE_NAME);
        boolean isRootANewProject = !rootProjectFile.isFile();
        this.rootProject = toExistingOrNewProject(this.rootDirectory, monitor, IResource.NONE);

        if (this.configureProjects) {
          importProjectAndChildrenRecursively(this.rootProject, isRootANewProject, monitor);

          if (isRootANewProject && rootProjectWorthBeingRemoved()) {
            this.discardRootProject = MessageDialog.openQuestion(
                PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                DataTransferMessages.SmartImportJob_discardRootProject_title,
                DataTransferMessages.SmartImportJob_discardRootProject_description);
            if (this.discardRootProject) {
              this.rootProject.delete(false, true, monitor);
              if (isRootANewProject) {
                rootProjectFile.delete();
              }
              this.report.remove(this.rootProject);
            }
          }
        }
      }
      if (closeProjectsAfterImport) {
        for (IProject project : new ArrayList<>(this.report.keySet())) {
          try {
            project.close(monitor);
          } catch (Exception e) {
            RLogger.logError(e);
          }
        }
      }
    } catch (Exception ex) {
      return new Status(IStatus.ERROR, IDE_WORKBENCH, ex.getMessage(), ex);
    } finally {
      if (isAutoBuilding) {
        ws.setAutoBuilding(true);
      }
    }
    return Status.OK_STATUS;
  }

  protected boolean rootProjectWorthBeingRemoved() {
    if (this.report.size() == 1) {
      return false;
    }
    List<ProjectConfigurator> rootProjectConfigurators = this.report.get(this.rootProject);
    if (rootProjectConfigurators.isEmpty()) {
      return true;
    }
    boolean areOnlyDummyConfigurators = true;
    for (ProjectConfigurator configurator : rootProjectConfigurators) {
      // TODO: semantics whether configurator is "strong enough" for a root project should be put inside configurator
      areOnlyDummyConfigurators &= (configurator instanceof EclipseProjectConfigurator
          || configurator instanceof EclipseWorkspaceConfigurator);
    }
    return areOnlyDummyConfigurators;
  }

  private Set<IProject> searchAndImportChildrenProjectsRecursively(final IContainer parentContainer,
      Set<IPath> directoriesToExclude, final IProgressMonitor progressMonitor) throws Exception {
    IPath parentLocation = parentContainer.getLocation();
    if (parentLocation == null) {
      return Collections.emptySet();
    }
    for (IProject processedProjects : new ArrayList<>(this.report.keySet())) {
      if (parentLocation.equals(processedProjects.getLocation())) {
        return Collections.emptySet();
      }
    }
    parentContainer.refreshLocal(IResource.DEPTH_ONE, progressMonitor); // make sure we know all children
    Set<IFolder> childrenToProcess = new HashSet<>();
    final Set<IProject> res = new HashSet<>();
    for (IResource childResource : parentContainer.members()) {
      if (childResource.getType() == IResource.FOLDER && !childResource.isDerived()) {
        if (skipDotFolders && childResource.getName().startsWith(".")) { //$NON-NLS-1$
          continue;
        }
        IPath location = childResource.getLocation();
        if (location == null) {
          continue;
        }
        boolean excluded = false;
        if (directoriesToExclude != null) {
          for (IPath excludedPath : directoriesToExclude) {
            if (!excludedPath.isPrefixOf(parentLocation) && excludedPath.isPrefixOf(location)) {
              excluded = true;
            }
          }
        }
        if (!excluded) {
          childrenToProcess.add((IFolder) childResource);
        }
      }
    }

    for (final IFolder childFolder : childrenToProcess) {
      try {
        res.addAll(importProjectAndChildrenRecursively(childFolder, false, progressMonitor));
      } catch (Exception ex) {
        // VSCODE: a crawl that fails is logged, the other folders are still crawled
        RLogger.logError(ex);
      }
    }
    return res;
  }

  private Set<IProject> importProjectAndChildrenRecursively(final IContainer container, boolean forceFullProjectCheck,
      IProgressMonitor progressMonitor) throws Exception {
    Set<IProject> projectFromCurrentContainer = new HashSet<>();
    final IPath containerLocation = container.getLocation();
    if (containerLocation == null) {
      return projectFromCurrentContainer;
    }
    boolean isAlreadyAnEclipseProject = false;
    Set<ProjectConfigurator> mainProjectConfigurators = new HashSet<>();
    Set<IPath> excludedPaths = new HashSet<>();
    if (this.excludedDirectories != null) {
      for (File excludedDirectory : this.excludedDirectories) {
        excludedPaths.add(Path.fromOSString(excludedDirectory.getAbsolutePath()));
      }
    }
    container.refreshLocal(IResource.DEPTH_INFINITE, progressMonitor);
    if (!forceFullProjectCheck) {
      EclipseProjectConfigurator eclipseProjectConfigurator = new EclipseProjectConfigurator();
      if (eclipseProjectConfigurator.shouldBeAnEclipseProject(container, progressMonitor)) {
        isAlreadyAnEclipseProject = true;
      }
    }

    if (this.configurationManager == null) {
      this.configurationManager = new ProjectConfiguratorExtensionManager();
    }
    Collection<ProjectConfigurator> activeConfigurators = this.configurationManager
        .getAllActiveProjectConfigurators(container);
    Set<ProjectConfigurator> potentialSecondaryConfigurators = new HashSet<>();
    IProject project = null;
    for (ProjectConfigurator configurator : activeConfigurators) {
      // exclude Eclipse project configurator for root project if is new
      if (configurator instanceof EclipseProjectConfigurator && forceFullProjectCheck) {
        continue;
      }
      if (configurator.shouldBeAnEclipseProject(container, progressMonitor)) {
        mainProjectConfigurators.add(configurator);
        if (project == null) {
          // Create project
          try {
            project = toExistingOrNewProject(containerLocation.toFile(), progressMonitor, IResource.BACKGROUND_REFRESH);
          } catch (CouldNotImportProjectException ex) {
            this.errors.put(containerLocation, ex);
            return projectFromCurrentContainer;
          }
          projectFromCurrentContainer.add(project);
        }
      } else {
        potentialSecondaryConfigurators.add(configurator);
      }
    }

    // The container was already refreshed above, so refresh again only if the project is a different resource (nested
    // project created for a child folder).
    if (!mainProjectConfigurators.isEmpty() && !project.equals(container)) {
      project.refreshLocal(IResource.DEPTH_INFINITE, progressMonitor);
    }
    for (ProjectConfigurator configurator : mainProjectConfigurators) {
      if (configurator instanceof EclipseProjectConfigurator || !isAlreadyAnEclipseProject
          || this.reconfigureEclipseProjects) {
        configurator.configure(project, excludedPaths, progressMonitor);
        this.report.get(project).add(configurator);
      }
      excludedPaths.addAll(toPathSet(configurator.getFoldersToIgnore(project, progressMonitor)));
    }

    if (deepChildrenDetection) {
      Set<IProject> allNestedProjects = searchAndImportChildrenProjectsRecursively(container, excludedPaths,
          progressMonitor);
      projectFromCurrentContainer.addAll(allNestedProjects);
    }
    // exclude all known children projects
    for (IProject other : container.getWorkspace().getRoot().getProjects()) {
      IPath otherLocation = other.getLocation();
      if (otherLocation != null && !containerLocation.equals(otherLocation)
          && containerLocation.isPrefixOf(otherLocation)) {
        excludedPaths.add(otherLocation);
      }
    }

    if (mainProjectConfigurators.isEmpty() && (!isAlreadyAnEclipseProject || forceFullProjectCheck)) {
      // Apply secondary configurators
      if (project == null) {
        // Create project
        try {
          project = toExistingOrNewProject(containerLocation.toFile(), progressMonitor, IResource.BACKGROUND_REFRESH);
        } catch (CouldNotImportProjectException ex) {
          this.errors.put(containerLocation, ex);
          return projectFromCurrentContainer;
        }
        projectFromCurrentContainer.add(project);
      }
      project.refreshLocal(IResource.DEPTH_ONE, progressMonitor);
      for (ProjectConfigurator additionalConfigurator : potentialSecondaryConfigurators) {
        if (additionalConfigurator.canConfigure(project, excludedPaths, progressMonitor)) {
          additionalConfigurator.configure(project, excludedPaths, progressMonitor);
          this.report.get(project).add(additionalConfigurator);
          excludedPaths.addAll(toPathSet(additionalConfigurator.getFoldersToIgnore(project, progressMonitor)));
        }
      }
    }
    if (project != null) {
      // make sure this folder isn't going to be processed again
      excludedPaths.add(project.getLocation());
    }
    return projectFromCurrentContainer;
  }

  private Set<IPath> toPathSet(Set<? extends IContainer> resources) {
    if (resources == null || resources.isEmpty()) {
      return Collections.emptySet();
    }
    Set<IPath> res = new HashSet<>();
    for (IContainer container : resources) {
      IPath location = container.getLocation();
      if (location != null) {
        res.add(location);
      }
    }
    return res;
  }

  private static boolean isInsideDotFolder(File file) {
    File current = file;
    while (current != null) {
      String name = current.getName();
      if (!name.isEmpty() && name.startsWith(".")) { //$NON-NLS-1$
        return true;
      }
      current = current.getParentFile();
    }
    return false;
  }

  /**
   * The project of the directory, existing or created and opened.
   *
   * @param refreshMode {@link IResource#BACKGROUND_REFRESH} for background refresh, or {@link IResource#NONE} for immediate
   */
  private IProject toExistingOrNewProject(File directory, IProgressMonitor progressMonitor, int refreshMode)
      throws CouldNotImportProjectException {
    try {
      IProject project = projectAlreadyExistsInWorkspace(directory);
      if (project == null) {
        project = createOrImportProject(directory, progressMonitor);
      }
      project.open(refreshMode, progressMonitor);
      if (!this.report.containsKey(project)) {
        this.report.put(project, new ArrayList<>());
      }
      return project;
    } catch (Exception ex) {
      throw new CouldNotImportProjectException(directory, ex);
    }
  }

  private IProject projectAlreadyExistsInWorkspace(File directory) {
    for (IProject project : workspaceRoot.getProjects()) {
      File file = SmartImportWizard.toFile(project);
      if (file == null) {
        continue;
      }
      if (file.getAbsoluteFile().equals(directory.getAbsoluteFile())) {
        return project;
      }
    }
    return null;
  }

  private IProject createOrImportProject(File directory, IProgressMonitor progressMonitor) throws Exception {
    IProjectDescription desc = null;
    File expectedProjectDescriptionFile = new File(directory, IProjectDescription.DESCRIPTION_FILE_NAME);
    if (expectedProjectDescriptionFile.exists()) {
      desc = loadProjectDescription(expectedProjectDescriptionFile);
      String expectedName = desc.getName();
      IProject projectWithSameName = this.workspaceRoot.getProject(expectedName);
      if (projectWithSameName.exists()) {
        if (directory.equals(SmartImportWizard.toFile(projectWithSameName))) {
          // project seems already there
          return projectWithSameName;
        }
        throw new CouldNotImportProjectException(directory, DataTransferMessages.bind(
            DataTransferMessages.SmartImportProposals_anotherProjectWithSameNameExists_description, expectedName));
      }
    } else {
      String projectName = generateNewProjectName(directory, this.workspaceRoot);
      desc = ResourcesPlugin.getWorkspace().newProjectDescription(projectName);
    }
    desc.setLocation(Path.fromOSString(directory.getAbsolutePath()));
    IProject res = this.workspaceRoot.getProject(desc.getName());
    res.create(desc, progressMonitor);
    if (this.workingSets != null && this.workingSets.length > 0) {
      PlatformUI.getWorkbench().getWorkingSetManager().addToWorkingSets(res, this.workingSets);
    }
    return res;
  }

  /**
   * {@code IWorkspace.loadProjectDescription}: the name, comment, natures and build spec of an existing .project file.
   * VSCODE: read here, the description class of the workspace emulation does not read files.
   */
  static IProjectDescription loadProjectDescription(File dotProject) throws Exception {
    DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
    f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); //$NON-NLS-1$
    f.setExpandEntityReferences(false);
    org.w3c.dom.Document doc = f.newDocumentBuilder().parse(dotProject);
    Element root = doc.getDocumentElement();
    IProjectDescription desc = ResourcesPlugin.getWorkspace().newProjectDescription(text(root, "name")); //$NON-NLS-1$
    desc.setComment(text(root, "comment")); //$NON-NLS-1$
    List<String> natures = new ArrayList<>();
    for (Element n : elements(root, "natures", "nature")) //$NON-NLS-1$ //$NON-NLS-2$
      natures.add(n.getTextContent().trim());
    desc.setNatureIds(natures.toArray(new String[0]));
    List<ICommand> commands = new ArrayList<>();
    for (Element c : elements(root, "buildSpec", "buildCommand")) { //$NON-NLS-1$ //$NON-NLS-2$
      ICommand command = desc.newCommand();
      command.setBuilderName(text(c, "name")); //$NON-NLS-1$
      Map<String, String> args = new LinkedHashMap<>();
      for (Element entry : elements(c, "arguments", "dictionary")) { //$NON-NLS-1$ //$NON-NLS-2$
        args.put(text(entry, "key"), text(entry, "value")); //$NON-NLS-1$ //$NON-NLS-2$
      }
      command.setArguments(args);
      commands.add(command);
    }
    desc.setBuildSpec(commands.toArray(new ICommand[0]));
    return desc;
  }

  private static String text(Element parent, String child) {
    NodeList nodes = parent.getElementsByTagName(child);
    return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim(); //$NON-NLS-1$
  }

  private static List<Element> elements(Element root, String parent, String child) {
    List<Element> r = new ArrayList<>();
    NodeList parents = root.getElementsByTagName(parent);
    for (int i = 0; i < parents.getLength(); i++) {
      NodeList children = ((Element) parents.item(i)).getElementsByTagName(child);
      for (int j = 0; j < children.getLength(); j++)
        r.add((Element) children.item(j));
    }
    return r;
  }

  private static String generateNewProjectName(File directory, IWorkspaceRoot root) {
    String projectName = directory.getName();
    if (root.getProject(projectName).exists()) {
      StringBuilder projectNameBuilder = new StringBuilder(projectName);
      File currentDirectory = directory;
      while (currentDirectory.canRead() && directory.getParentFile() != null
          && root.getProject(projectNameBuilder.toString()).exists()) {
        projectNameBuilder.insert(0, "_"); //$NON-NLS-1$
        projectNameBuilder.insert(0, currentDirectory.getParentFile().getName());
        currentDirectory = currentDirectory.getParentFile();
      }
      if (!root.getProject(projectNameBuilder.toString()).exists()) {
        projectName = projectNameBuilder.toString();
      } else {
        int i = 1;
        do {
          projectName = directory.getName() + '(' + i + ')';
          i++;
        } while (root.getProject(projectName).exists());
      }
    }
    return projectName;
  }

  /** The project found or created for the root folder. */
  public IProject getRootProject() {
    return this.rootProject;
  }

  /** The projects found or imported, and the configurators used to configure them. */
  public Map<IProject, List<ProjectConfigurator>> getConfiguredProjects() {
    return this.report;
  }

  /** The import errors that happened. */
  public Map<IPath, Exception> getErrors() {
    return this.errors;
  }

  /** The proposals for the import operation. */
  public Map<File, List<ProjectConfigurator>> getImportProposals(IProgressMonitor monitor) {
    if (!this.deepChildrenDetection) {
      Map<File, List<ProjectConfigurator>> res = new HashMap<>();
      res.put(rootDirectory, Collections.emptyList());
      return res;
    }
    if (this.importProposals == null) {
      Map<File, List<ProjectConfigurator>> res = new HashMap<>();
      if (this.configurationManager == null) {
        this.configurationManager = new ProjectConfiguratorExtensionManager();
      }
      List<ProjectConfigurator> activeConfigurators = configurationManager
          .getAllActiveProjectConfigurators(this.rootDirectory);
      for (ProjectConfigurator configurator : activeConfigurators) {
        Set<File> supportedDirectories = configurator.findConfigurableLocations(SmartImportJob.this.rootDirectory,
            this.skipDotFolders, monitor);
        if (supportedDirectories != null) {
          for (File supportedDirectory : supportedDirectories) {
            if (supportedDirectory.isDirectory()) {
              if (!res.containsKey(supportedDirectory)) {
                res.put(supportedDirectory, new ArrayList<>());
              }
              res.get(supportedDirectory).add(configurator);
            } else {
              RLogger.logInfo("Project detection must return only directories.\n" + supportedDirectory
                  + " is not a directory.\nContributed by " + configurator.getClass().getName());
            }
          }
        }
      }
      for (ProjectConfigurator configurator : activeConfigurators) {
        configurator.removeDirtyDirectories(res);
      }
      if (this.skipDotFolders) {
        res.keySet().removeIf(SmartImportJob::isInsideDotFolder);
      }
      this.importProposals = res;
    }
    return this.importProposals;
  }

  /** Whether the job is set to configure projects (natures and other). */
  public boolean isConfigureProjects() {
    return this.configureProjects;
  }

  /** Whether the job looks for nested projects when no directory is passed to {@link #setDirectoriesToImport(Set)}. */
  public boolean isDetectNestedProjects() {
    return this.deepChildrenDetection;
  }

  /** Sets whether the job should look for nested projects (ignored when directories to import are given). */
  public void setDetectNestedProjects(boolean detectNestedProjects) {
    this.deepChildrenDetection = detectNestedProjects;
  }

  /** If true, imported projects are closed in the workspace after the import. */
  void setCloseProjectsAfterImport(boolean closeProjectsAfterImport) {
    this.closeProjectsAfterImport = closeProjectsAfterImport;
  }

  /** If true, folders starting with '.' (e.g. .git) are skipped during project scanning. */
  void setSkipDotFolders(boolean skipDotFolders) {
    this.skipDotFolders = skipDotFolders;
  }

  /** Whether folders starting with '.' are skipped during scanning. */
  public boolean isSkipDotFolders() {
    return this.skipDotFolders;
  }

  /** Forgets the initial import proposals. */
  public void resetProposals() {
    this.importProposals = null;
  }

  /** The directories that will be crawled for import. */
  public Set<File> getDirectoriesToImport() {
    return this.directoriesToImport;
  }

  public boolean belongsTo(Object family) {
    return family == SmartImportJob.class;
  }
}
