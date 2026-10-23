package com.unitesk.requality.vscode.ui.ide.datatransfer;

import com.unitesk.requality.vscode.port.eclipse.Activator;
import com.unitesk.requality.vscode.ui.workbench.dialogs.WorkbenchPreferences;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogSettings;
import com.unitesk.requality.vscode.ui.jface.operation.IRunnableWithProgress;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.wizard.Wizard;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.Adapters;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.IPath;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.workbench.IImportWizard;
import com.unitesk.requality.vscode.ui.workbench.IWorkbench;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;

/**
 * {@code org.eclipse.ui.internal.wizards.datatransfer.SmartImportWizard}: allows the user to control an import operation. It
 * takes a directory or an archive as input, and proposes what and how to import, relying on the strategies contributed as
 * {@link ProjectConfigurator}s.
 *
 * <p>
 * VSCODE: the archives are expanded with the java.util.zip reader (zip, jar) and a minimal tar reader (tar, tar.gz); the
 * wizard banner image is not shown.
 * </p>
 */
public class SmartImportWizard extends Wizard implements IImportWizard {

  /** Expands an archive onto a file system directory. */
  private static final class ExpandArchiveIntoFilesystemOperation implements IRunnableWithProgress {
    private final File archive;
    private final File destination;

    private ExpandArchiveIntoFilesystemOperation(File archive, File destination) {
      this.archive = archive;
      this.destination = destination;
    }

    @Override
    public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
      try {
        destination.mkdirs();
        if (isTarFile(archive)) {
          expandTar(archive, destination);
        } else {
          expandZip(archive, destination);
        }
      } catch (IOException | RuntimeException ex) {
        throw new InvocationTargetException(ex);
      }
    }

    private static void expandZip(File archive, File destination) throws IOException {
      try (ZipFile zip = new ZipFile(archive)) {
        Enumeration<? extends ZipEntry> all = zip.entries();
        while (all.hasMoreElements()) {
          ZipEntry e = all.nextElement();
          File out = target(destination, e.getName());
          if (e.isDirectory()) {
            out.mkdirs();
            continue;
          }
          out.getParentFile().mkdirs();
          try (InputStream in = zip.getInputStream(e)) {
            Files.copy(in, out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
          }
        }
      }
    }

    private static void expandTar(File archive, File destination) throws IOException {
      try (InputStream raw = new BufferedInputStream(Files.newInputStream(archive.toPath()))) {
        raw.mark(2);
        byte[] magic = new byte[2];
        int n = raw.read(magic);
        raw.reset();
        InputStream in = n == 2 && (magic[0] & 0xff) == 0x1f && (magic[1] & 0xff) == 0x8b ? new GZIPInputStream(raw) : raw;
        byte[] header = new byte[512];
        while (readFully(in, header) == 512) {
          if (header[0] == 0)
            break;
          String name = tarString(header, 0, 100);
          String prefix = tarString(header, 345, 155);
          if (!prefix.isEmpty())
            name = prefix + "/" + name;
          long size = Long.parseLong(tarString(header, 124, 12).trim(), 8);
          char type = (char) header[156];
          File out = target(destination, name);
          if (type == '5') {
            out.mkdirs();
          } else if (type == '0' || type == 0) {
            out.getParentFile().mkdirs();
            Files.copy(new java.io.ByteArrayInputStream(readBytes(in, size)), out.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
          } else {
            skip(in, size);
          }
          skip(in, (512 - size % 512) % 512);
        }
      }
    }

    private static byte[] readBytes(InputStream in, long size) throws IOException {
      byte[] b = new byte[(int) size];
      if (readFully(in, b) != b.length)
        throw new IOException("Truncated tar entry"); //$NON-NLS-1$
      return b;
    }

    private static void skip(InputStream in, long n) throws IOException {
      long left = n;
      while (left > 0) {
        long s = in.skip(left);
        if (s <= 0)
          break;
        left -= s;
      }
    }

    private static int readFully(InputStream in, byte[] b) throws IOException {
      int total = 0;
      while (total < b.length) {
        int r = in.read(b, total, b.length - total);
        if (r < 0)
          break;
        total += r;
      }
      return total;
    }

    private static String tarString(byte[] b, int off, int len) {
      int end = off;
      while (end < off + len && b[end] != 0)
        end++;
      return new String(b, off, end - off, StandardCharsets.UTF_8);
    }

    /** The file of an entry below the destination (the entries cannot leave it). */
    private static File target(File destination, String entryName) throws IOException {
      File out = new File(destination, entryName).getAbsoluteFile();
      if (!out.toPath().normalize().startsWith(destination.getAbsoluteFile().toPath().normalize()))
        throw new IOException("Invalid archive entry: " + entryName); //$NON-NLS-1$
      return out;
    }
  }

  /** The job property of Eclipse's {@code IProgressConstants.PROPERTY_IN_DIALOG} (org.eclipse.ui.progress). */
  static final String PROPERTY_IN_DIALOG = "org.eclipse.ui.progress.PROPERTY_IN_DIALOG"; //$NON-NLS-1$

  private static final String SMART_IMPORT_SECTION_NAME = SmartImportWizard.class.getSimpleName();

  private File initialSelection;
  private Set<IWorkingSet> initialWorkingSets = new HashSet<>();
  private SmartImportRootWizardPage projectRootPage;
  private SmartImportJob easymportJob;
  /** the selected directory or the directory when archive got expanded. */
  private File directoryToImport;

  public SmartImportWizard() {
    super();
    setNeedsProgressMonitor(true);
    setForcePreviousAndNextButtons(true);
    IDialogSettings dialogSettings = getDialogSettings();
    if (dialogSettings == null) {
      IDialogSettings workbenchSettings = Activator.getDefault().getDialogSettings();
      IDialogSettings section = workbenchSettings.getSection(SMART_IMPORT_SECTION_NAME);
      if (section == null) {
        section = workbenchSettings.addNewSection(SMART_IMPORT_SECTION_NAME);
      }
      dialogSettings = section;
      setDialogSettings(dialogSettings);
    }
    setWindowTitle(DataTransferMessages.SmartImportWizardPage_importProjectsInFolderTitle);
  }

  /** Sets the initial directory or archive to import in workspace. */
  public void setInitialImportSource(File directoryOrArchive) {
    this.initialSelection = directoryOrArchive;
  }

  /** Sets the initial selected working sets for the wizard. */
  public void setInitialWorkingSets(Set<IWorkingSet> workingSets) {
    this.initialWorkingSets = workingSets;
  }

  @Override
  public void init(IWorkbench workbench, IStructuredSelection selection) {
    if (selection != null) {
      for (Object item : selection) {
        File asFile = toFile(item);
        if (asFile != null && this.initialSelection == null) {
          this.initialSelection = asFile;
        } else {
          IWorkingSet asWorkingSet = Adapters.adapt(item, IWorkingSet.class);
          if (asWorkingSet != null) {
            this.initialWorkingSets.add(asWorkingSet);
          }
        }
      }
    }
  }

  /**
   * Tries to infer a file location from the object: a file, a resource, or an adaptable to a resource.
   *
   * @return a {@link File} associated to this object, or null.
   */
  public static File toFile(Object o) {
    if (o instanceof File) {
      return (File) o;
    } else if (o instanceof IResource) {
      IPath location = ((IResource) o).getLocation();
      return location == null ? null : location.toFile();
    } else if (o instanceof IAdaptable) {
      IResource resource = ((IAdaptable) o).getAdapter(IResource.class);
      if (resource != null) {
        IPath location = resource.getLocation();
        return location == null ? null : location.toFile();
      }
    }
    return null;
  }

  /** The absolute path of the object's file, or the empty string. */
  public static String toAbsolutePath(Object o) {
    File file = toFile(o);
    return file == null ? "" : file.getAbsolutePath(); //$NON-NLS-1$
  }

  @Override
  public void addPages() {
    this.projectRootPage = new SmartImportRootWizardPage(this, this.initialSelection, this.initialWorkingSets);
    addPage(this.projectRootPage);
  }

  @Override
  public boolean performFinish() {
    IDialogSettings settings = getDialogSettings();
    String[] previousProposals = settings.getArray(SmartImportRootWizardPage.IMPORTED_SOURCES);
    if (previousProposals == null) {
      previousProposals = new String[0];
    }
    if (!Arrays.asList(previousProposals).contains(this.projectRootPage.getSelectedRoot().getAbsolutePath())) {
      String[] newProposals = new String[previousProposals.length + 1];
      newProposals[0] = this.projectRootPage.getSelectedRoot().getAbsolutePath();
      System.arraycopy(previousProposals, 0, newProposals, 1, previousProposals.length);
      settings.put(SmartImportRootWizardPage.IMPORTED_SOURCES, newProposals);
    }
    SmartImportJob job = createOrGetConfiguredImportJob();
    boolean runInBackground = WorkbenchPreferences.getStore().getBoolean(WorkbenchPreferences.RUN_IN_BACKGROUND);
    job.setProperty(PROPERTY_IN_DIALOG, runInBackground);
    job.schedule();
    return true;
  }

  /**
   * Gets or creates the import job of this wizard, or returns null when the source is invalid. When the job does not match the
   * current configuration of the wizard, a new job is created. Some configurations are applied to the job in any case.
   */
  public SmartImportJob createOrGetConfiguredImportJob() {
    final File root = this.projectRootPage.getSelectedRoot();
    if (root == null) {
      return null;
    }
    if (root.isDirectory()) {
      this.directoryToImport = root;
    } else if (SmartImportWizard.isValidArchive(root)) {
      this.directoryToImport = getExpandDirectory(root);
      if (!directoryToImport.isDirectory()) {
        throw new IllegalArgumentException("Archive wasn't expanded first"); //$NON-NLS-1$
      }
    } else {
      return null;
    }

    if (this.easymportJob == null || !matchesPage(this.easymportJob, this.projectRootPage)) {
      this.easymportJob = new SmartImportJob(this.directoryToImport, projectRootPage.getSelectedWorkingSets(),
          projectRootPage.isConfigureProjects(), projectRootPage.isDetectNestedProject());
    }

    // always update working set on request as the job isn't updated on WS change automatically
    this.easymportJob.setWorkingSets(projectRootPage.getSelectedWorkingSets());
    this.easymportJob.setCloseProjectsAfterImport(projectRootPage.isCloseProjectsAfterImport());
    this.easymportJob.setSkipDotFolders(projectRootPage.isSkipDotFolders());

    return this.easymportJob;
  }

  /** The current import job, or null when it was not created yet. */
  public SmartImportJob getCurrentImportJob() {
    return this.easymportJob;
  }

  static boolean isValidArchive(File file) {
    return isZipFile(file) || isTarFile(file);
  }

  static boolean isZipFile(File file) {
    String n = file.getName().toLowerCase();
    return n.endsWith(".zip") || n.endsWith(".jar"); //$NON-NLS-1$ //$NON-NLS-2$
  }

  static boolean isTarFile(File file) {
    String n = file.getName().toLowerCase();
    return n.endsWith(".tar") || n.endsWith(".tar.gz") || n.endsWith(".tgz"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
  }

  /** Expands the archive into the expand directory of the workspace (the one of the wizard's page). */
  void expandArchive(final File archive, IProgressMonitor monitor) throws Exception {
    if (!isValidArchive(archive)) {
      throw new IllegalArgumentException("Input must be an archive"); //$NON-NLS-1$
    }
    this.directoryToImport = getExpandDirectory(archive);
    ExpandArchiveIntoFilesystemOperation expandOperation = new ExpandArchiveIntoFilesystemOperation(archive,
        directoryToImport);
    expandOperation.run(monitor);
    if (monitor.isCanceled()) {
      throw new InterruptedException();
    }
  }

  /** The folder an archive is expanded to: a folder next to the workspace root's content. */
  static File getExpandDirectory(final File archive) {
    if (!isValidArchive(archive)) {
      throw new IllegalArgumentException("Input must be an archive"); //$NON-NLS-1$
    }
    return new File(ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile(), archive.getName() + "_expanded"); //$NON-NLS-1$
  }

  private static boolean matchesPage(SmartImportJob job, SmartImportRootWizardPage page) {
    File jobRoot = job.getRoot().getAbsoluteFile();
    File pageRoot = page.getSelectedRoot().getAbsoluteFile();
    boolean sameSource = jobRoot.equals(pageRoot)
        || (isValidArchive(pageRoot) && getExpandDirectory(pageRoot).getAbsoluteFile().equals(jobRoot));
    return sameSource && job.isDetectNestedProjects() == page.isDetectNestedProject()
        && job.isConfigureProjects() == page.isConfigureProjects()
        && job.isSkipDotFolders() == page.isSkipDotFolders();
  }
}
