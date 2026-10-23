package com.unitesk.requality.vscode.ui.ide;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.core.ServerContext;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IMarker;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;
import com.unitesk.requality.vscode.ui.runtime.QualifiedName;
import com.unitesk.requality.vscode.ui.workbench.EditorRegistry;
import com.unitesk.requality.vscode.ui.workbench.FileEditorInput;
import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.core.ServerContext;
import com.unitesk.requality.vscode.ui.workbench.IEditorInput;
import com.unitesk.requality.vscode.ui.workbench.IEditorPart;
import com.unitesk.requality.vscode.ui.workbench.IEditorRegistry;
import com.unitesk.requality.vscode.ui.workbench.ISharedImages;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;
import com.unitesk.requality.vscode.ui.workbench.PartInitException;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;

/** {@code org.eclipse.ui.ide.IDE}: the IDE shared images used by Requality. */
public final class IDE {
  /** {@code IDE.RESOURCE_EDITOR_KEY}-like key: the editor chosen for a file (Open With) is a persistent property. */
  public static final QualifiedName EDITOR_KEY = new QualifiedName("org.eclipse.ui.internal.registry.ResourceEditorRegistry",
      "EditorProperty");

  /**
   * {@code IDE.getDefaultEditor(IFile)}: the editor remembered for the file (chosen in Open With), or null when the
   * registry default applies.
   */
  public static IEditorRegistry.IEditorDescriptor getDefaultEditor(IFile file) {
    String id = rememberedEditorId(file);
    return id == null ? null : PlatformUI.getWorkbench().getEditorRegistry().findEditor(id);
  }

  /** {@code IDE.setDefaultEditor(IFile, String)}: remembers the editor for the file; null forgets it. */
  public static void setDefaultEditor(IFile file, String editorId) {
    try {
      file.setPersistentProperty(EDITOR_KEY, editorId);
    } catch (com.unitesk.requality.vscode.ui.runtime.CoreException e) {
      throw new IllegalStateException(e);
    }
  }

  /** The editor id of the file: the remembered one, else the registry default, else the text editor. */
  public static String editorIdOf(IFile file) {
    String id = rememberedEditorId(file);
    if (id != null && PlatformUI.getWorkbench().getEditorRegistry().findEditor(id) != null)
      return id;
    IEditorRegistry.IEditorDescriptor d = PlatformUI.getWorkbench().getEditorRegistry().getDefaultEditor(file.getName());
    return d == null ? IEditorRegistry.DEFAULT_TEXT_EDITOR_ID : d.getId();
  }

  private static String rememberedEditorId(IFile file) {
    try {
      return file.getPersistentProperty(EDITOR_KEY);
    } catch (com.unitesk.requality.vscode.ui.runtime.CoreException e) {
      return null;
    }
  }

  /**
   * {@code IDE.openEditor(IWorkbenchPage, IFile, boolean)}: opens the file in the editor it is remembered for, else
   * the registry default editor, else the text editor. Editors of VS Code are opened by the extension.
   */
  public static IEditorPart openEditor(IWorkbenchPage page, IFile file, boolean activate) throws PartInitException {
    return openEditor(page, file, editorIdOf(file), activate);
  }

  /**
   * {@code IDE.openEditor(IWorkbenchPage, IFile, String, boolean)}: an editor hosted by the server opens in the page;
   * the text editor, the system editor and the editors of VS Code are opened by the extension ({@code client/openFile},
   * returns null).
   */
  public static IEditorPart openEditor(IWorkbenchPage page, IFile file, String editorId, boolean activate)
      throws PartInitException {
    if (EditorRegistry.opensInVSCode(editorId)) {
      openFileInVSCode(file, EditorRegistry.vscodeEditorName(editorId));
      return null;
    }
    if (EditorRegistry.isExternalProgram(editorId)) {
      openFileWithProgram(file, editorId);
      return null;
    }
    return page.openEditor(new FileEditorInput(file), editorId, activate);
  }

  /**
   * The file opens with a program chosen in Open With &gt; Other... &gt; Browse... (the program is run with the path of the
   * file as argument). VSCODE: a macOS application bundle is opened with {@code open -a}.
   */
  public static void openFileWithProgram(IFile file, String program) throws PartInitException {
    openPathWithProgram(file.getLocation().toOSString(), program);
  }

  /** Runs the program with the path of a file as argument (a macOS application bundle with {@code open -a}). */
  public static void openPathWithProgram(String path, String program) throws PartInitException {
    boolean bundle = program.endsWith(".app") && System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("mac");
    java.util.List<String> command = bundle ? java.util.List.of("open", "-a", program, path) : java.util.List.of(program, path);
    try {
      new ProcessBuilder(command).start();
    } catch (java.io.IOException e) {
      throw new PartInitException("Cannot start " + program + ": " + e.getMessage(), e);
    }
  }

  /** The file opens in a VS Code editor: {@code client/openFile} with the path and the editor ("default", "system" or a view type). */
  public static void openFileInVSCode(IFile file, String editor) {
    if (ServerContext.server() == null)
      return;
    JsonObject p = new JsonObject();
    p.addProperty("path", file.getLocation().toOSString());
    p.addProperty("editor", editor);
    ServerContext.client().notify("client/openFile", p);
  }

  private IDE() {
  }

  /** {@code org.eclipse.ui.ide.IDE.openEditor(IWorkbenchPage, IEditorInput, String)}: activates the editor. */
  public static IEditorPart openEditor(IWorkbenchPage page, IEditorInput input, String editorID)
      throws PartInitException {
    return openEditor(page, input, editorID, true);
  }

  /** {@code org.eclipse.ui.ide.IDE.openEditor(IWorkbenchPage, IEditorInput, String, boolean)}. */
  public static IEditorPart openEditor(IWorkbenchPage page, IEditorInput input, String editorID, boolean activate)
      throws PartInitException {
    return page.openEditor(input, editorID, activate);
  }

  /** The workspace resource of an editor input (ResourceUtil.getResource), or null. */
  private static com.unitesk.requality.vscode.ui.resources.IResource resourceOf(IEditorInput input) {
    if (input == null)
      return null;
    if (input instanceof com.unitesk.requality.vscode.ui.workbench.FileEditorInput)
      return ((com.unitesk.requality.vscode.ui.workbench.FileEditorInput) input).getFile();
    Object o = input.getAdapter(com.unitesk.requality.vscode.ui.resources.IResource.class);
    return o instanceof com.unitesk.requality.vscode.ui.resources.IResource
        ? (com.unitesk.requality.vscode.ui.resources.IResource) o : null;
  }

  /**
   * {@code IDE.saveAllEditors(IResource[], boolean)}: the dirty editors of resources under the roots are saved, after
   * the "Save Resources" selection when {@code confirm}; false when the user canceled.
   */
  public static boolean saveAllEditors(com.unitesk.requality.vscode.ui.resources.IResource[] resourceRoots, boolean confirm) {
    java.util.List<IEditorPart> dirty = new java.util.ArrayList<>();
    IWorkbenchPage page = com.unitesk.requality.vscode.ui.workbench.PlatformUI.getWorkbench().getActiveWorkbenchWindow()
        .getActivePage();
    for (IEditorPart e : page.getEditors()) {
      if (!e.isDirty())
        continue;
      com.unitesk.requality.vscode.ui.resources.IResource r = resourceOf(e.getEditorInput());
      if (r == null)
        continue;
      for (com.unitesk.requality.vscode.ui.resources.IResource root : resourceRoots)
        if (root.getFullPath().isPrefixOf(r.getFullPath())) {
          dirty.add(e);
          break;
        }
    }
    if (dirty.isEmpty())
      return true;
    java.util.List<IEditorPart> toSave = dirty;
    if (confirm) {
      com.unitesk.requality.vscode.ui.jface.dialogs.ListSelectionDialog d = new com.unitesk.requality.vscode.ui.jface.dialogs.ListSelectionDialog(
          com.unitesk.requality.vscode.ui.workbench.PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), dirty,
          new com.unitesk.requality.vscode.ui.jface.viewers.ArrayContentProvider(),
          new com.unitesk.requality.vscode.ui.jface.viewers.LabelProvider() {
            @Override
            public String getText(Object element) {
              return ((IEditorPart) element).getTitle();
            }
          }, "Select the resources to save:");
      d.setTitle("Save Resources");
      d.setInitialSelections(dirty.toArray());
      if (d.open() != com.unitesk.requality.vscode.ui.jface.window.Window.OK)
        return false;
      toSave = new java.util.ArrayList<>();
      for (Object o : d.getResult())
        toSave.add((IEditorPart) o);
    }
    for (IEditorPart e : toSave)
      e.doSave(null);
    return true;
  }

  /**
   * {@code IDE.openEditor(IWorkbenchPage, IFile, boolean)}: opens the file in its default editor. VS Code opens the
   * editor on the client: the {@code client/openFile} notification with the path of the file.
   */
  public static void openFile(com.unitesk.requality.vscode.ui.resources.IFile file) {
    if (ServerContext.server() == null)
      return;
    JsonObject p = new JsonObject();
    p.addProperty("path", file.getLocation().toOSString());
    p.addProperty("editor", "default");
    ServerContext.client().notify("client/openFile", p);
  }

  /** {@code org.eclipse.ui.ide.IDE.SharedImages}. */
  public interface SharedImages {
    String IMG_OBJ_PROJECT = ISharedImages.IMG_OBJ_PROJECT;

    String IMG_OBJ_PROJECT_CLOSED = ISharedImages.IMG_OBJ_PROJECT_CLOSED;
  }

  /**
   * {@code org.eclipse.ui.ide.IDE.computeSelectedResources}: the resources of the selection, the elements that are
   * resources or adapt to one, in the order of the selection.
   */
  public static java.util.List<com.unitesk.requality.vscode.ui.resources.IResource> computeSelectedResources(
      com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection selection) {
    java.util.List<com.unitesk.requality.vscode.ui.resources.IResource> r = new java.util.ArrayList<>();
    if (selection == null)
      return r;
    for (Object o : selection.toList()) {
      com.unitesk.requality.vscode.ui.resources.IResource res = null;
      if (o instanceof com.unitesk.requality.vscode.ui.resources.IResource x)
        res = x;
      else if (o instanceof com.unitesk.requality.vscode.ui.runtime.IAdaptable a)
        res = a.getAdapter(com.unitesk.requality.vscode.ui.resources.IResource.class);
      if (res != null)
        r.add(res);
    }
    return r;
  }

  /** The extension point of the marker resolutions (Quick Fix): {@code org.eclipse.ui.ide.markerResolution}. */
  private static final String MARKER_RESOLUTION_POINT = "org.eclipse.ui.ide.markerResolution";
  private static IMarkerHelpRegistry markerHelpRegistry;

  /** {@code org.eclipse.ui.IMarkerResolution}: a fix of a marker, applied by Quick Fix. */
  public interface IMarkerResolution {
    /** The label of the fix in the Quick Fix dialog. */
    String getLabel();

    /** Applies the fix to the marker. */
    void run(IMarker marker);
  }

  /** {@code org.eclipse.ui.IMarkerResolutionGenerator}: the fixes of the markers (markerResolutionGenerator). */
  public interface IMarkerResolutionGenerator {
    IMarkerResolution[] getResolutions(IMarker marker);
  }

  /** {@code org.eclipse.ui.IMarkerHelpRegistry}: the fixes (resolutions) of the markers. */
  public interface IMarkerHelpRegistry {
    IMarkerResolution[] getResolutions(IMarker marker);
  }

  /**
   * {@code IDE.getMarkerHelpRegistry}: the registry of the marker resolutions of the markerResolutionGenerator elements
   * of the {@code org.eclipse.ui.ide.markerResolution} extension point, asked for the resolutions of a marker of their
   * marker type (the Requality plug-in contributes none).
   */
  public static synchronized IMarkerHelpRegistry getMarkerHelpRegistry() {
    if (markerHelpRegistry == null)
      markerHelpRegistry = IDE::resolutionsOf;
    return markerHelpRegistry;
  }

  private static IMarkerResolution[] resolutionsOf(IMarker marker) {
    java.util.List<IMarkerResolution> r = new java.util.ArrayList<>();
    for (IConfigurationElement e : ExtensionRegistry.get().getConfigurationElementsFor(MARKER_RESOLUTION_POINT)) {
      if (!"markerResolutionGenerator".equals(e.getName()))
        continue;
      try {
        String type = e.getAttribute("markerType");
        if (type != null && !marker.isSubtypeOf(type))
          continue;
        IMarkerResolutionGenerator generator = (IMarkerResolutionGenerator) e.createExecutableExtension("class");
        IMarkerResolution[] found = generator.getResolutions(marker);
        if (found != null)
          for (IMarkerResolution f : found)
            if (f != null)
              r.add(f);
      } catch (Exception ex) {
        // a generator that fails contributes no fixes (Eclipse logs its error and goes on)
      }
    }
    return r.toArray(new IMarkerResolution[0]);
  }
}
