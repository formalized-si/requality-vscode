package com.unitesk.requality.vscode.ui.workbench;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Supplier;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.core.ClientApi;
import com.unitesk.requality.vscode.core.ServerContext;
import com.unitesk.requality.vscode.ui.commands.Command;
import com.unitesk.requality.vscode.ui.jface.preference.IPropertyChangeListener;
import com.unitesk.requality.vscode.ui.commands.CommandSupport;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.commands.ICommandService;
import com.unitesk.requality.vscode.ui.commands.IEvaluationContext;
import com.unitesk.requality.vscode.ui.commands.IHandler;
import com.unitesk.requality.vscode.ui.commands.IHandlerActivation;
import com.unitesk.requality.vscode.ui.commands.IHandlerService;
import com.unitesk.requality.vscode.ui.commands.NotDefinedException;
import com.unitesk.requality.vscode.ui.commands.NotEnabledException;
import com.unitesk.requality.vscode.ui.commands.NotHandledException;
import com.unitesk.requality.vscode.ui.commands.operations.IOperationHistory;
import com.unitesk.requality.vscode.ui.commands.operations.IUndoContext;
import com.unitesk.requality.vscode.ui.commands.operations.OperationHistory;
import com.unitesk.requality.vscode.ui.jface.resource.ColorRegistry;
import com.unitesk.requality.vscode.ui.jface.resource.FontRegistry;
import com.unitesk.requality.vscode.ui.jface.resource.JFaceResources;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.FillLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Event;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.workbench.internal.MainMenu;
import com.unitesk.requality.vscode.ui.workbench.internal.PerspectiveCustomization;
import com.unitesk.requality.vscode.ui.workbench.internal.PerspectiveRegistry;
import com.unitesk.requality.vscode.ui.workbench.internal.ViewRegistry;
import com.unitesk.requality.vscode.ui.workbench.views.IViewRegistry;

/**
 * The workbench: one window with one page. Views and editors of the Eclipse layer are registered here; a
 * server-hosted part is rendered in a VS Code webview (views in the webview view with the part id, editors in an
 * editor tab), parts implemented by the extension (Requality Explorer, Links Explorer, Outline, Properties, the web
 * editors) are registered as external parts.
 */
public final class Workbench implements IWorkbench {
  public static final Workbench INSTANCE = new Workbench();
  static final Window WINDOW = new Window();
  static final Page PAGE = new Page();

  private static final class ViewDescriptor {
    final String id;
    final Supplier<? extends IViewPart> factory;
    /** The VS Code webview view that shows the part. */
    String hostView;
    IViewPart instance;
    boolean external;

    ViewDescriptor(String id, Supplier<? extends IViewPart> factory) {
      this.id = id;
      this.factory = factory;
      this.hostView = id;
    }
  }

  /** VS Code view ids of the views of plugin.xml (the extension contributes them with these ids). */
  private static final Map<String, String> HOST_VIEWS = new LinkedHashMap<>();

  /** The VS Code view that shows the Eclipse view {@code viewId}. */
  public static synchronized void setHostView(String viewId, String vscodeViewId) {
    HOST_VIEWS.put(viewId, vscodeViewId);
    ViewDescriptor d = VIEWS.get(viewId);
    if (d != null && !d.external)
      d.hostView = vscodeViewId;
  }

  public static synchronized String hostView(String viewId) {
    return HOST_VIEWS.getOrDefault(viewId, viewId);
  }

  /** The Eclipse view shown in the VS Code view {@code vscodeViewId}. */
  public static synchronized String viewOfHost(String vscodeViewId) {
    for (Map.Entry<String, String> e : HOST_VIEWS.entrySet())
      if (e.getValue().equals(vscodeViewId))
        return e.getKey();
    return vscodeViewId;
  }

  /** A view of plugin.xml ({@code org.eclipse.ui.views}) whose ported class is available. */
  private static ViewDescriptor viewFromExtensions(String viewId) {
    for (com.unitesk.requality.vscode.ui.runtime.IConfigurationElement e : com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry
        .get().getConfigurationElementsFor("org.eclipse.ui.views")) {
      if (!"view".equals(e.getName()) || !viewId.equals(e.getAttribute("id")))
        continue;
      if (!classAvailable(e.getAttribute("class")))
        return null;
      ViewDescriptor d = new ViewDescriptor(viewId, () -> {
        try {
          return (IViewPart) e.createExecutableExtension("class");
        } catch (com.unitesk.requality.vscode.ui.runtime.CoreException ex) {
          RLogger.logError(ex);
          return null;
        }
      });
      d.hostView = HOST_VIEWS.getOrDefault(viewId, viewId);
      return d;
    }
    return null;
  }

  /** An editor of plugin.xml ({@code org.eclipse.ui.editors}) whose ported class is available. */
  private static Supplier<? extends IEditorPart> editorFromExtensions(String editorId) {
    for (com.unitesk.requality.vscode.ui.runtime.IConfigurationElement e : com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry
        .get().getConfigurationElementsFor("org.eclipse.ui.editors")) {
      if (!"editor".equals(e.getName()) || !editorId.equals(e.getAttribute("id")))
        continue;
      if (!classAvailable(e.getAttribute("class")))
        return null;
      return () -> {
        try {
          return (IEditorPart) e.createExecutableExtension("class");
        } catch (com.unitesk.requality.vscode.ui.runtime.CoreException ex) {
          RLogger.logError(ex);
          return null;
        }
      };
    }
    return null;
  }

  private static boolean classAvailable(String className) {
    try {
      Class.forName(com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry.portedClassName(className));
      return true;
    } catch (ClassNotFoundException | LinkageError ex) {
      return false;
    }
  }

  private static final Map<String, ViewDescriptor> VIEWS = new LinkedHashMap<>();
  private static final Map<String, Supplier<? extends IEditorPart>> EDITORS = new LinkedHashMap<>();
  /** Fallback for editors opened by the extension (web editors): opens the editor for the input. */
  private static volatile ExternalEditorOpener externalOpener;
  private static final List<EditorRef> OPEN_EDITORS = new ArrayList<>();
  private static volatile IWorkbenchPart activePart;
  private static volatile IEditorPart activeEditor;
  private static volatile ISelection selection = StructuredSelection.EMPTY;

  /** Opens an editor the server does not host (web editors). */
  public interface ExternalEditorOpener {
    IEditorPart open(IEditorInput input, String editorId, boolean activate) throws PartInitException;
  }

  private Workbench() {
  }

  // ---- registration

  /** A view rendered by the server in the VS Code webview view {@code id}. */
  public static synchronized void registerView(String id, Supplier<? extends IViewPart> factory) {
    ViewDescriptor d = new ViewDescriptor(id, factory);
    d.hostView = HOST_VIEWS.getOrDefault(id, id);
    VIEWS.put(id, d);
  }

  /** A view implemented by the extension, represented by {@code part}. */
  /**
   * Registers a part that is created on first use by {@code part} (the Requality Explorer: its viewer and site are
   * set up by the explorer service once the server is initialized).
   */
  public static synchronized void registerExternalView(String id, Supplier<? extends IViewPart> part) {
    ViewDescriptor d = new ViewDescriptor(id, part);
    d.external = true;
    VIEWS.put(id, d);
  }

  public static synchronized void registerExternalView(String id, IViewPart part) {
    ViewDescriptor d = new ViewDescriptor(id, () -> part);
    d.instance = part;
    d.external = true;
    VIEWS.put(id, d);
  }

  /** An editor rendered by the server in a VS Code editor tab. */
  public static synchronized void registerEditor(String id, Supplier<? extends IEditorPart> factory) {
    EDITORS.put(id, factory);
  }

  public static void setExternalEditorOpener(ExternalEditorOpener opener) {
    externalOpener = opener;
  }

  /** An editor opened by the extension (web editor tab) becomes known to the page. */
  public static synchronized void editorOpened(String editorId, IEditorPart part) {
    OPEN_EDITORS.add(new EditorRef(editorId, part));
    com.unitesk.requality.vscode.ui.e4.workbench.modeling.EditorModel.editorOpened(part);
  }

  public static synchronized void editorClosed(IEditorPart part) {
    if (part.getSite() instanceof PartSite)
      ((PartSite) part.getSite()).disposeServices();
    com.unitesk.requality.vscode.ui.e4.workbench.modeling.EditorModel.editorClosed(part);
    for (IPartListener l : PART_LISTENERS) {
      try {
        l.partClosed(part);
      } catch (RuntimeException e) {
        RLogger.logError(e);
      }
    }
    for (IPartListener2 l : PART_LISTENERS_2) {
      try {
        l.partClosed(partReference(part));
      } catch (RuntimeException e) {
        RLogger.logError(e);
      }
    }
    OPEN_EDITORS.removeIf(r -> r.part == part);
    if (activeEditor == part)
      activeEditor = null;
    if (activePart == part)
      activePart = null;
  }

  /** The part that has focus in VS Code changed (with its selection). */
  private static final List<IPartListener> PART_LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();
  /** The {@code IPartListener2} listeners (the Activator's part tracking), fired with the part references. */
  private static final List<IPartListener2> PART_LISTENERS_2 = new java.util.concurrent.CopyOnWriteArrayList<>();
  // VSCODE: the window and the page of VS Code exist before the plug-in starts, so the listeners of the window and the
  // page are only kept (the window and page opened events are not reported again).
  private static final List<IWindowListener> WINDOW_LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();
  private static final List<IPageListener> PAGE_LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();

  /** The part reference of a part of the workbench (the single page). */
  static IWorkbenchPartReference partReference(IWorkbenchPart part) {
    return new IWorkbenchPartReference() {
      @Override
      public IWorkbenchPart getPart(boolean restore) {
        return part;
      }

      @Override
      public String getId() {
        return part.getId();
      }

      @Override
      public String getTitle() {
        return part.getTitle();
      }

      @Override
      public String getPartName() {
        return part.getTitle();
      }

      @Override
      public IWorkbenchPage getPage() {
        return PAGE;
      }
    };
  }

  /** The workbench logger of the plug-in ({@code com.unitesk.requality.vscode.ui.e4.core.services.log.Logger}): the server's log. */
  private static final com.unitesk.requality.vscode.ui.e4.core.services.log.Logger LOGGER =
      new com.unitesk.requality.vscode.ui.e4.core.services.log.Logger() {
        @Override
        public void info(String message) {
          RLogger.baseLogger.getLogger().lD(message);
        }

        @Override
        public void error(String message) {
          RLogger.baseLogger.getLogger().lE(message);
        }
      };

  private static com.unitesk.requality.vscode.ui.jface.viewers.ISelectionProvider activeProvider;
  /** AbstractSelectionService: a selection change of the active part's provider is the workbench selection. */
  private static final com.unitesk.requality.vscode.ui.jface.viewers.ISelectionChangedListener PROVIDER_LISTENER = e -> {
    IWorkbenchPart part = activePart;
    ISelection s = e.getSelection();
    Display.getDefault().asyncExec(() -> {
      if (part != null && part == activePart)
        fireSelection(part, s);
    });
  };

  private static void hookProvider(IWorkbenchPart part) {
    com.unitesk.requality.vscode.ui.jface.viewers.ISelectionProvider old = activeProvider;
    if (old != null)
      old.removeSelectionChangedListener(PROVIDER_LISTENER);
    activeProvider = part != null && part.getSite() != null ? part.getSite().getSelectionProvider() : null;
    if (activeProvider != null)
      activeProvider.addSelectionChangedListener(PROVIDER_LISTENER);
  }

  private static void fireSelection(IWorkbenchPart part, ISelection sel) {
    selection = sel;
    if (part != null)
      PART_SELECTIONS.put(part.getId(), sel);
    for (ISelectionListener l : SELECTION_LISTENERS) {
      try {
        l.selectionChanged(part, sel);
      } catch (RuntimeException e) {
        RLogger.logError(e);
      }
    }
  }

  public static void partActivated(IWorkbenchPart part, ISelection sel) {
    boolean changed = part != activePart;
    activePart = part;
    if (changed)
      hookProvider(part);
    if (changed && part != null)
      for (IPartListener l : PART_LISTENERS) {
        try {
          l.partActivated(part);
        } catch (RuntimeException e) {
          RLogger.logError(e);
        }
      }
    if (changed && part != null)
      for (IPartListener2 l : PART_LISTENERS_2) {
        try {
          l.partActivated(partReference(part));
        } catch (RuntimeException e) {
          RLogger.logError(e);
        }
      }
    if (part instanceof IEditorPart)
      activeEditor = (IEditorPart) part;
    // AbstractSelectionService.partActivated: without an explicit selection, the activated part's provider gives it.
    if (sel == null && changed && activeProvider != null)
      sel = activeProvider.getSelection();
    if (sel != null)
      fireSelection(part, sel);
  }

  /** The webview of a server-hosted part got the focus: activate the part with its site's selection. */
  public static void shellActivated(Shell shell) {
    IWorkbenchPart part = null;
    synchronized (Workbench.class) {
      for (EditorRef r : OPEN_EDITORS)
        if (r.part.getSite() != null && r.part.getSite().getShell() == shell)
          part = r.part;
      for (ViewDescriptor d : VIEWS.values())
        if (d.instance != null && d.instance.getSite() != null && d.instance.getSite().getShell() == shell)
          part = d.instance;
    }
    if (part == null || part == activePart)
      return;
    ISelection sel = null;
    IWorkbenchPartSite site = part.getSite();
    if (site != null && site.getSelectionProvider() != null)
      sel = site.getSelectionProvider().getSelection();
    partActivated(part, sel);
  }

  /**
   * VSCODE: the editor group (VS Code view column, 1-based) of the tab of a shell changed: the E4 model of the editors
   * follows it (EditorModel), so that the part stacks are the editor groups of VS Code.
   */
  public static void shellColumnChanged(Shell shell, int column) {
    IEditorPart editor = null;
    synchronized (Workbench.class) {
      for (EditorRef r : OPEN_EDITORS)
        if (r.part.getSite() != null && r.part.getSite().getShell() == shell)
          editor = r.part;
    }
    if (editor != null)
      com.unitesk.requality.vscode.ui.e4.workbench.modeling.EditorModel.editorInColumn(editor, column);
  }

  public static IWorkbenchPart activePart() {
    return activePart;
  }

  public static ISelection currentSelection() {
    return selection;
  }

  /**
   * The E4 layout step of a two-editor view ({@code EModelService.insert(stack2, stack1, RIGHT_OF)} and
   * {@code bringToTop} of both parts): {@code right} is shown in the editor group right of the one showing
   * {@code left}, both stay visible.
   */
  public static void placeEditorBeside(IEditorPart left, IEditorPart right) {
    if (right == null || right == left || right.getSite() == null || right.getSite().getShell() == null)
      return;
    JsonObject p = new JsonObject();
    p.addProperty("shell", right.getSite().getShell().getId());
    if (left != null && left.getSite() != null && left.getSite().getShell() != null)
      p.addProperty("beside", left.getSite().getShell().getId());
    notifyClient("ui/placeShell", p);
  }

  private static String tabText(IEditorPart p) {
    String t = p.getTitle() == null ? "" : p.getTitle();
    return p.isDirty() ? "*" + t : t;
  }

  static void notifyClient(String method, JsonObject params) {
    if (ServerContext.server() != null)
      ServerContext.client().notify(method, params);
  }

  // ---- IWorkbench

  @Override
  public Display getDisplay() {
    return Display.getDefault();
  }

  @Override
  public IWorkbenchWindow getActiveWorkbenchWindow() {
    return WINDOW;
  }

  @Override
  public IWorkbenchWindow[] getWorkbenchWindows() {
    return new IWorkbenchWindow[] { WINDOW };
  }

  @Override
  public ISharedImages getSharedImages() {
    return SharedImages.INSTANCE;
  }

  private static final IWorkbenchOperationSupport OPERATIONS = new IWorkbenchOperationSupport() {
    @Override
    public IUndoContext getUndoContext() {
      return IOperationHistory.GLOBAL_UNDO_CONTEXT;
    }

    @Override
    public IOperationHistory getOperationHistory() {
      return OperationHistory.INSTANCE;
    }
  };

  @Override
  public IWorkbenchOperationSupport getOperationSupport() {
    return OPERATIONS;
  }

  private static final IWorkbenchHelpSystem HELP = new IWorkbenchHelpSystem() {
    @Override
    public void displayHelp() {
      displayHelpResource(null);
    }

    @Override
    public void displayHelp(String contextId) {
      displayHelpResource(null);
    }

    @Override
    public void displayHelpResource(String href) {
      JsonObject p = new JsonObject();
      p.addProperty("href", href);
      notifyClient("client/showHelp", p);
    }

    @Override
    public void setHelp(Control control, String contextId) {
    }
  };

  @Override
  public IWorkbenchHelpSystem getHelpSystem() {
    return HELP;
  }

  private static final IWebBrowser EXTERNAL_BROWSER = url -> {
    JsonObject p = new JsonObject();
    p.addProperty("url", url.toString());
    notifyClient("client/openExternal", p);
  };

  @Override
  public IWorkbenchBrowserSupport getBrowserSupport() {
    return new IWorkbenchBrowserSupport() {
      @Override
      public IWebBrowser getExternalBrowser() {
        return EXTERNAL_BROWSER;
      }

      @Override
      public IWebBrowser createBrowser(int style, String browserId, String name, String tooltip) {
        return EXTERNAL_BROWSER;
      }

      @Override
      public IWebBrowser createBrowser(String browserId) {
        return EXTERNAL_BROWSER;
      }
    };
  }

  /** Opens a URL in the system browser. */
  public static void openExternal(URL url) {
    try {
      EXTERNAL_BROWSER.openURL(url);
    } catch (PartInitException e) {
      RLogger.logError(e);
    }
  }

  private static final ITheme THEME = new ITheme() {
    @Override
    public ColorRegistry getColorRegistry() {
      return JFaceResources.getColorRegistry();
    }

    @Override
    public FontRegistry getFontRegistry() {
      return JFaceResources.getFontRegistry();
    }

    @Override
    public String getId() {
      return "vscode";
    }

    @Override
    public String getLabel() {
      return "VS Code";
    }
  };

  @Override
  public IThemeManager getThemeManager() {
    return () -> THEME;
  }

  private static final ICommandService COMMAND_SERVICE = new ICommandService() {
    @Override
    public Command getCommand(String commandId) {
      return CommandSupport.get().getCommand(commandId);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void refreshElements(String commandId, Map filter) {
      CommandSupport.get().refreshElements(commandId);
    }
  };

  private static final IHandlerService HANDLER_SERVICE = new IHandlerService() {
    @Override
    public Object executeCommand(String commandId, Event event)
        throws ExecutionException, NotDefinedException, NotEnabledException, NotHandledException {
      return CommandSupport.get().execute(commandId, null, event);
    }

    @Override
    public IHandlerActivation activateHandler(String commandId, IHandler handler) {
      return CommandSupport.get().activateHandler(commandId, handler);
    }

    @Override
    public void deactivateHandler(IHandlerActivation activation) {
      CommandSupport.get().deactivateHandler(activation);
    }

    @Override
    public IEvaluationContext getCurrentState() {
      return CommandSupport.get().currentState();
    }

    @Override
    public IEvaluationContext createContextSnapshot(boolean includeSelection) {
      return CommandSupport.get().currentState();
    }
  };

  /** Activations of the contexts (IContextService); a context id is active while one of its activations is. */
  private static final List<ContextActivation> CONTEXT_ACTIVATIONS = new ArrayList<>();

  private static final class ContextActivation implements IContextActivation {
    final String contextId;

    ContextActivation(String contextId) {
      this.contextId = contextId;
    }
  }

  /** Active contexts are VS Code context keys {@code requality.context.<context id>} (for keybindings and menus). */
  private static void contextChanged(String contextId) {
    boolean active;
    synchronized (CONTEXT_ACTIVATIONS) {
      active = CONTEXT_ACTIVATIONS.stream().anyMatch(a -> a.contextId.equals(contextId));
    }
    if (ServerContext.server() != null)
      ServerContext.client().setContext("requality.context." + contextId, active);
  }

  private static final IContextService CONTEXT_SERVICE = new IContextService() {
    @Override
    public IContextActivation activateContext(String contextId) {
      ContextActivation a = new ContextActivation(contextId);
      synchronized (CONTEXT_ACTIVATIONS) {
        CONTEXT_ACTIVATIONS.add(a);
      }
      contextChanged(contextId);
      return a;
    }

    @Override
    public void deactivateContext(IContextActivation activation) {
      synchronized (CONTEXT_ACTIVATIONS) {
        CONTEXT_ACTIVATIONS.remove(activation);
      }
      if (activation instanceof ContextActivation)
        contextChanged(((ContextActivation) activation).contextId);
    }

    @Override
    public Collection<String> getActiveContextIds() {
      Set<String> ids = new LinkedHashSet<>();
      synchronized (CONTEXT_ACTIVATIONS) {
        for (ContextActivation a : CONTEXT_ACTIVATIONS)
          ids.add(a.contextId);
      }
      return ids;
    }
  };

  @Override
  @SuppressWarnings("unchecked")
  public <T> T getService(Class<T> api) {
    if (api == ICommandService.class)
      return (T) COMMAND_SERVICE;
    if (api == IHandlerService.class)
      return (T) HANDLER_SERVICE;
    if (api == IContextService.class)
      return (T) CONTEXT_SERVICE;
    if (api == com.unitesk.requality.vscode.ui.e4.workbench.modeling.EModelService.class)
      return (T) com.unitesk.requality.vscode.ui.e4.workbench.modeling.EditorModel.SERVICE;
    if (api == com.unitesk.requality.vscode.ui.e4.core.services.log.Logger.class)
      return (T) LOGGER;
    return null;
  }

  @Override
  public boolean isClosing() {
    return false;
  }

  @Override
  public boolean isStarting() {
    return false;
  }

  @Override
  public void addWindowListener(IWindowListener listener) {
    WINDOW_LISTENERS.add(listener);
  }

  @Override
  public void removeWindowListener(IWindowListener listener) {
    WINDOW_LISTENERS.remove(listener);
  }

  private static final IDecoratorManager DECORATORS = new IDecoratorManager() {
  };

  @Override
  public IDecoratorManager getDecoratorManager() {
    return DECORATORS;
  }

  @Override
  public IEditorRegistry getEditorRegistry() {
    return EditorRegistry.INSTANCE;
  }

  /** Hooks run on the UI thread once an editor part has created its controls (before its tab is shown). */
  private static final List<java.util.function.Consumer<IEditorPart>> EDITOR_CREATED_HOOKS = new java.util.concurrent.CopyOnWriteArrayList<>();

  /** Adds a hook run when an editor part has created its controls. */
  public static void addEditorCreatedHook(java.util.function.Consumer<IEditorPart> hook) {
    EDITOR_CREATED_HOOKS.add(hook);
  }

  /** The product name of the window title. */
  static final String PRODUCT_NAME = "Requality";

  /**
   * {@code ApplicationWorkbenchWindowAdvisor.computeTitle}: the title of the window from the IDE preferences (the
   * workspace name, the perspective name, the active editor, the workspace location and the product name, joined by
   * " - "), sent to VS Code as {@code client/windowTitle}: the title without the editor, and its parts before and after
   * the editor. VSCODE: the active editor is the one of the VS Code window, which the extension puts in the title.
   */
  static void updateWindowTitle() {
    com.unitesk.requality.vscode.ui.jface.preference.IPreferenceStore ps = com.unitesk.requality.vscode.ui.ide.dialogs.IDEInternalPreferences
        .getStore();
    // the parts of IDEWorkbenchWindowAdvisor.computeTitle in its order; the active editor (its title tool tip) is the
    // part of VS Code (the active editor of its window), between the parts before and after it
    JsonArray before = new JsonArray();
    JsonArray after = new JsonArray();
    if (ps.getBoolean(com.unitesk.requality.vscode.ui.ide.dialogs.IDEInternalPreferences.SHOW_LOCATION_NAME)) {
      String name = ps.getString(com.unitesk.requality.vscode.ui.ide.dialogs.IDEInternalPreferences.WORKSPACE_NAME);
      if (name != null && !name.isEmpty())
        before.add(name);
    }
    IPerspectiveRegistry.IPerspectiveDescriptor persp = PAGE.getPerspective();
    if (ps.getBoolean(com.unitesk.requality.vscode.ui.ide.dialogs.IDEInternalPreferences.SHOW_PERSPECTIVE_IN_TITLE)
        && persp != null)
      before.add(persp.getLabel());
    boolean editor = ps.getBoolean(com.unitesk.requality.vscode.ui.ide.dialogs.IDEInternalPreferences.SHOW_ACTIVE_EDITOR_INFO_IN_TITLE);
    if (ps.getBoolean(com.unitesk.requality.vscode.ui.ide.dialogs.IDEInternalPreferences.SHOW_LOCATION)
        && ServerContext.server() != null && !ServerContext.server().workspace().folders().isEmpty())
      after.add(ServerContext.server().workspace().folders().get(0).getAbsolutePath());
    if (ps.getBoolean(com.unitesk.requality.vscode.ui.ide.dialogs.IDEInternalPreferences.SHOW_PRODUCT_IN_TITLE))
      after.add(PRODUCT_NAME);
    StringJoiner sj = new StringJoiner(" - ");
    before.forEach(e -> sj.add(e.getAsString()));
    after.forEach(e -> sj.add(e.getAsString()));
    JsonObject p = new JsonObject();
    p.addProperty("title", sj.toString());
    p.add("before", before);
    p.addProperty("editor", editor);
    p.add("after", after);
    p.addProperty("perspective", persp == null ? null : persp.getLabel());
    notifyClient("client/windowTitle", p);
  }

  /**
   * The perspective the workbench opens in (see ViewsService): its views are created but not shown, as VS Code shows
   * the views of the Requality panel itself.
   */
  public static void openInitialPerspective(IPerspectiveRegistry.IPerspectiveDescriptor desc) {
    addTitlePropertyChangeListener();
    PAGE.applyPerspective(desc, false);
  }

  private static boolean titleListenerAdded;

  /**
   * {@code IDEWorkbenchWindowAdvisor.addPropertyChangeListeners}: the title is computed again when an IDE preference
   * of the title changes (Window &gt; Preferences &gt; General &gt; Workspace).
   */
  private static synchronized void addTitlePropertyChangeListener() {
    if (titleListenerAdded)
      return;
    titleListenerAdded = true;
    com.unitesk.requality.vscode.ui.ide.dialogs.IDEInternalPreferences.getStore().addPropertyChangeListener(event -> {
      String property = event.getProperty();
      if (com.unitesk.requality.vscode.ui.ide.dialogs.IDEInternalPreferences.SHOW_LOCATION.equals(property)
          || com.unitesk.requality.vscode.ui.ide.dialogs.IDEInternalPreferences.SHOW_LOCATION_NAME.equals(property)
          || com.unitesk.requality.vscode.ui.ide.dialogs.IDEInternalPreferences.WORKSPACE_NAME.equals(property)
          || com.unitesk.requality.vscode.ui.ide.dialogs.IDEInternalPreferences.SHOW_PERSPECTIVE_IN_TITLE.equals(property)
          || com.unitesk.requality.vscode.ui.ide.dialogs.IDEInternalPreferences.SHOW_PRODUCT_IN_TITLE.equals(property)
          || com.unitesk.requality.vscode.ui.ide.dialogs.IDEInternalPreferences.SHOW_ACTIVE_EDITOR_INFO_IN_TITLE.equals(property))
        updateWindowTitle();
    });
  }

  @Override
  public IPerspectiveRegistry getPerspectiveRegistry() {
    return PerspectiveRegistry.INSTANCE;
  }

  @Override
  public IWorkbenchPage showPerspective(String perspectiveId, IWorkbenchWindow window)
      throws WorkbenchException {
    IPerspectiveRegistry.IPerspectiveDescriptor desc = PerspectiveRegistry.INSTANCE.findPerspectiveWithId(perspectiveId);
    if (desc == null)
      throw new WorkbenchException("Perspective " + perspectiveId + " cannot be found.");
    IWorkbenchWindow w = window != null ? window : getActiveWorkbenchWindow();
    IWorkbenchPage page = w.getActivePage();
    page.setPerspective(desc);
    return page;
  }

  @Override
  public IViewRegistry getViewRegistry() {
    return ViewRegistry.INSTANCE;
  }

  @Override
  public IWizardRegistry getNewWizardRegistry() {
    return new WizardRegistry("org.eclipse.ui.newWizards");
  }

  @Override
  public IWizardRegistry getImportWizardRegistry() {
    return new WizardRegistry("org.eclipse.ui.importWizards");
  }

  @Override
  public IWorkingSetManager getWorkingSetManager() {
    return com.unitesk.requality.vscode.ui.workbench.internal.WorkingSetManager.get();
  }

  @Override
  public IWizardRegistry getExportWizardRegistry() {
    return new WizardRegistry("org.eclipse.ui.exportWizards");
  }

  // ---- selection service

  private static final List<ISelectionListener> SELECTION_LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();
  private static final Map<String, ISelection> PART_SELECTIONS = new java.util.concurrent.ConcurrentHashMap<>();

  private static final ISelectionService SELECTION_SERVICE = new ISelectionService() {
    @Override
    public ISelection getSelection() {
      return selection;
    }

    @Override
    public ISelection getSelection(String partId) {
      return PART_SELECTIONS.get(partId);
    }

    @Override
    public void addSelectionListener(ISelectionListener listener) {
      SELECTION_LISTENERS.add(listener);
    }

    @Override
    public void removeSelectionListener(ISelectionListener listener) {
      SELECTION_LISTENERS.remove(listener);
    }

    @Override
    public void addPostSelectionListener(ISelectionListener listener) {
      SELECTION_LISTENERS.add(listener);
    }

    @Override
    public void removePostSelectionListener(ISelectionListener listener) {
      SELECTION_LISTENERS.remove(listener);
    }
  };

  public static ISelectionService selectionService() {
    return SELECTION_SERVICE;
  }

  /** {@code IWorkbench.restart()}: the VS Code host is not restarted by the extension; the user is asked to reload. */
  // VSCODE: VS Code has no workbench restart; the user reloads the window.
  @Override
  public boolean restart() {
    ServerContext.client().showMessage(ClientApi.Severity.info, "Workbench",
        "Reload the VS Code window to apply the changes.", null, false, false);
    return false;
  }

  @Override
  public boolean saveAllEditors(boolean confirm) {
    for (IEditorPart e : PAGE.getEditors())
      if (e.isDirty())
        e.doSave(null);
    return true;
  }

  // ---- window

  static final class Window implements IWorkbenchWindow {
    /** The shell that stands for the workbench window when no shell is active (it is never shown). */
    private static volatile Shell windowShell;

    /**
     * The active shell, else the shell of the window: as in Eclipse the window always has a shell, which the plug-in
     * uses as the parent of its questions (EclipseModalMessageConsumer: the format update question of a catalog).
     */
    @Override
    public Shell getShell() {
      Display d = Display.getDefault();
      Shell active = d.getActiveShell();
      if (active != null && !active.isDisposed())
        return active;
      Shell s = windowShell;
      if (s == null || s.isDisposed()) {
        Shell created = d.getThread() == Thread.currentThread() ? new Shell(SWT.NONE) : d.syncCall(() -> new Shell(SWT.NONE));
        synchronized (Window.class) {
          if (windowShell == null || windowShell.isDisposed())
            windowShell = created;
          s = windowShell;
        }
        if (s != created)
          d.asyncExec(created::dispose);
      }
      return s;
    }

    @Override
    public IWorkbenchPage getActivePage() {
      return PAGE;
    }

    @Override
    public IWorkbenchPage[] getPages() {
      return new IWorkbenchPage[] { PAGE };
    }

    @Override
    public IWorkbench getWorkbench() {
      return INSTANCE;
    }

    @Override
    public <T> T getService(Class<T> api) {
      return INSTANCE.getService(api);
    }

    @Override
    public ISelectionService getSelectionService() {
      return SELECTION_SERVICE;
    }

    @Override
    public boolean isClosing() {
      return false;
    }

    @Override
    public void addPageListener(IPageListener listener) {
      PAGE_LISTENERS.add(listener);
    }

    @Override
    public void removePageListener(IPageListener listener) {
      PAGE_LISTENERS.remove(listener);
    }
  }

  // ---- references

  static final class ViewRef implements IViewReference {
    final ViewDescriptor d;

    ViewRef(ViewDescriptor d) {
      this.d = d;
    }

    @Override
    public IViewPart getView(boolean restore) {
      if (d.instance == null && restore) {
        try {
          return PAGE.showView(d.id, null, IWorkbenchPage.VIEW_CREATE);
        } catch (PartInitException e) {
          RLogger.logError(e);
        }
      }
      return d.instance;
    }

    @Override
    public IWorkbenchPart getPart(boolean restore) {
      return getView(restore);
    }

    @Override
    public String getId() {
      return d.id;
    }

    @Override
    public String getTitle() {
      return d.instance == null ? d.id : d.instance.getTitle();
    }

    @Override
    public String getPartName() {
      return getTitle();
    }

    @Override
    public IWorkbenchPage getPage() {
      return PAGE;
    }
  }

  static final class EditorRef implements IEditorReference {
    final String id;
    final IEditorPart part;

    EditorRef(String id, IEditorPart part) {
      this.id = id;
      this.part = part;
    }

    @Override
    public IEditorPart getEditor(boolean restore) {
      return part;
    }

    @Override
    public IEditorInput getEditorInput() {
      return part.getEditorInput();
    }

    @Override
    public boolean isDirty() {
      return part.isDirty();
    }

    @Override
    public IWorkbenchPart getPart(boolean restore) {
      return part;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public String getTitle() {
      return part.getTitle();
    }

    @Override
    public String getPartName() {
      return part.getTitle();
    }

    @Override
    public IWorkbenchPage getPage() {
      return PAGE;
    }
  }

  // ---- page

  static final class Page implements IWorkbenchPage {
    /** The working sets of the window (see {@link #setWorkingSets}). */
    private IWorkingSet[] workingSets = new IWorkingSet[0];
    /** The perspective whose layout the page shows (null: none). */
    private IPerspectiveRegistry.IPerspectiveDescriptor perspective;
    /** The perspectives open in the page, in the order they were opened. */
    private final List<IPerspectiveRegistry.IPerspectiveDescriptor> openPerspectives = new ArrayList<>();
    /** The ids of the views each open perspective shows, by perspective id. */
    private final Map<String, List<String>> placedViews = new LinkedHashMap<>();
    /** The show view shortcuts of each perspective (the Window &gt; Show View menu), by perspective id. */
    private final Map<String, List<String>> showViewShortcuts = new LinkedHashMap<>();
    /** The New submenu shortcuts (wizards) of each perspective, by perspective id. */
    private final Map<String, List<String>> newWizardShortcuts = new LinkedHashMap<>();
    /** The Open Perspective submenu shortcuts of each perspective, by perspective id. */
    private final Map<String, List<String>> perspectiveShortcuts = new LinkedHashMap<>();
    private com.unitesk.requality.vscode.ui.workbench.internal.AggregateWorkingSet aggregateWorkingSet;
    /** The manager the window's state was read from; a new manager (a new workspace file) reads it again. */
    private IWorkingSetManager stateManager;
    private final IPropertyChangeListener workingSetPropertyChangeListener = event -> {
      if (IWorkingSetManager.CHANGE_WORKING_SET_REMOVE.equals(event.getProperty())) {
        List<IWorkingSet> newList = new ArrayList<>(Arrays.asList(workingSets));
        if (newList.remove(event.getOldValue())) {
          setWorkingSets(newList.toArray(new IWorkingSet[newList.size()]));
        }
      }
    };

    /**
     * Reads the working sets of the window from the working sets file (the names saved by {@link #setWorkingSets}),
     * once for each manager. The aggregate is made again by {@link #getAggregateWorkingSet}.
     */
    private void ensureState() {
      IWorkingSetManager manager = Workbench.INSTANCE.getWorkingSetManager();
      if (manager == stateManager) {
        return;
      }
      stateManager = manager;
      aggregateWorkingSet = null;
      com.unitesk.requality.vscode.ui.workbench.internal.WorkingSetManager stored = com.unitesk.requality.vscode.ui.workbench.internal.WorkingSetManager.get();
      List<IWorkingSet> restored = new ArrayList<>();
      for (String name : stored.getWindowWorkingSetNames()) {
        IWorkingSet workingSet = manager.getWorkingSet(name);
        if (workingSet != null) {
          restored.add(workingSet);
        }
      }
      workingSets = restored.toArray(new IWorkingSet[restored.size()]);
      manager.addPropertyChangeListener(workingSetPropertyChangeListener);
    }

    @Override
    public IWorkingSet[] getWorkingSets() {
      ensureState();
      return workingSets;
    }

    @Override
    public void setWorkingSets(IWorkingSet[] newWorkingSets) {
      ensureState();
      IWorkingSetManager manager = Workbench.INSTANCE.getWorkingSetManager();
      if (newWorkingSets != null) {
        manager.addPropertyChangeListener(workingSetPropertyChangeListener);
      } else {
        manager.removePropertyChangeListener(workingSetPropertyChangeListener);
      }
      if (newWorkingSets == null) {
        newWorkingSets = new IWorkingSet[0];
      }
      IWorkingSet[] oldWorkingSets = workingSets;
      // filter out any duplicates if necessary
      if (newWorkingSets.length > 1) {
        Set<IWorkingSet> setOfSets = new HashSet<>();
        for (IWorkingSet workingSet : newWorkingSets) {
          if (workingSet == null) {
            throw new IllegalArgumentException();
          }
          setOfSets.add(workingSet);
        }
        newWorkingSets = setOfSets.toArray(new IWorkingSet[setOfSets.size()]);
      }
      workingSets = newWorkingSets;
      if (!Arrays.equals(oldWorkingSets, newWorkingSets)) {
        List<String> names = new ArrayList<>();
        for (IWorkingSet workingSet : workingSets) {
          names.add(workingSet.getName());
        }
        com.unitesk.requality.vscode.ui.workbench.internal.WorkingSetManager.get()
            .setWindowWorkingSetNames(names.toArray(new String[names.size()]));
        if (aggregateWorkingSet != null) {
          aggregateWorkingSet.setComponents(workingSets);
        }
      }
    }

    /**
     * {@code org.eclipse.ui.internal.WorkbenchPage.getAggregateWorkingSet}: the "Window Working Set", the union of the
     * working sets of the window. Its id is kept in the working sets file, so the same aggregate is used after a restart.
     */
    @Override
    public IAggregateWorkingSet getAggregateWorkingSet() {
      ensureState();
      if (aggregateWorkingSet == null) {
        com.unitesk.requality.vscode.ui.workbench.internal.WorkingSetManager stored = com.unitesk.requality.vscode.ui.workbench.internal.WorkingSetManager.get();
        IWorkingSetManager manager = Workbench.INSTANCE.getWorkingSetManager();
        String id = stored.getWindowAggregateId();
        if (id == null) {
          id = "Aggregate for window " + System.currentTimeMillis();
          stored.setWindowAggregateId(id);
        }
        IWorkingSet existing = manager.getWorkingSet(id);
        if (existing instanceof com.unitesk.requality.vscode.ui.workbench.internal.AggregateWorkingSet restoredAggregate) {
          aggregateWorkingSet = restoredAggregate;
          if (!Arrays.equals(restoredAggregate.getComponents(), workingSets)) {
            restoredAggregate.setComponents(workingSets);
          }
        } else {
          aggregateWorkingSet = (com.unitesk.requality.vscode.ui.workbench.internal.AggregateWorkingSet) manager.createAggregateWorkingSet(
              id, "Window Working Set", workingSets);
          manager.addWorkingSet(aggregateWorkingSet);
        }
      }
      return aggregateWorkingSet;
    }

    @Override
    public IWorkbenchPart getActivePart() {
      return activePart;
    }

    @Override
    public IWorkbenchPartReference getActivePartReference() {
      IWorkbenchPart p = activePart;
      if (p == null)
        return null;
      synchronized (Workbench.class) {
        for (EditorRef r : OPEN_EDITORS)
          if (r.part == p)
            return r;
        for (ViewDescriptor d : VIEWS.values())
          if (d.instance == p)
            return new ViewRef(d);
      }
      return null;
    }

    @Override
    public IEditorPart getActiveEditor() {
      return activeEditor;
    }

    @Override
    public IEditorReference[] getEditorReferences() {
      synchronized (Workbench.class) {
        return OPEN_EDITORS.toArray(new IEditorReference[0]);
      }
    }

    @Override
    public IEditorPart[] getEditors() {
      synchronized (Workbench.class) {
        IEditorPart[] r = new IEditorPart[OPEN_EDITORS.size()];
        for (int i = 0; i < r.length; i++)
          r[i] = OPEN_EDITORS.get(i).part;
        return r;
      }
    }

    @Override
    public IEditorPart findEditor(IEditorInput input) {
      synchronized (Workbench.class) {
        for (EditorRef r : OPEN_EDITORS)
          if (input != null && input.equals(r.part.getEditorInput()))
            return r.part;
      }
      return null;
    }

    @Override
    public IViewReference[] getViewReferences() {
      List<IViewReference> r = new ArrayList<>();
      synchronized (Workbench.class) {
        for (ViewDescriptor d : VIEWS.values())
          if (d.instance != null)
            r.add(new ViewRef(d));
      }
      return r.toArray(new IViewReference[0]);
    }

    @Override
    public IViewPart findView(String viewId) {
      ViewDescriptor d;
      synchronized (Workbench.class) {
        d = VIEWS.get(viewId);
      }
      // an external part that is not created yet is created when it is found (as a shown view)
      if (d != null && d.external && d.instance == null) {
        try {
          return showView(viewId, null, IWorkbenchPage.VIEW_CREATE);
        } catch (PartInitException e) {
          RLogger.logError(e);
          return null;
        }
      }
      synchronized (Workbench.class) {
        return d == null ? null : d.instance;
      }
    }

    @Override
    public IViewReference findViewReference(String viewId) {
      synchronized (Workbench.class) {
        ViewDescriptor d = VIEWS.get(viewId);
        return d == null || d.instance == null ? null : new ViewRef(d);
      }
    }

    @Override
    public IViewPart showView(String viewId) throws PartInitException {
      return showView(viewId, null, VIEW_ACTIVATE);
    }

    @Override
    public IViewPart showView(String viewId, String secondaryId, int mode) throws PartInitException {
      ViewDescriptor d;
      synchronized (Workbench.class) {
        d = VIEWS.get(viewId);
        if (d == null) {
          d = viewFromExtensions(viewId);
          if (d != null)
            VIEWS.put(viewId, d);
        }
      }
      if (d == null)
        throw new PartInitException("View " + viewId + " is not available in VS Code");
      final ViewDescriptor fd = d;
      if (d.instance == null)
        d.instance = Display.getDefault().syncCall(() -> createView(fd));
      if (d.instance == null)
        throw new PartInitException("Could not create view " + viewId);
      if (mode != VIEW_CREATE) {
        JsonObject p = new JsonObject();
        p.addProperty("id", d.external ? viewId : d.hostView);
        p.addProperty("focus", mode == VIEW_ACTIVATE);
        notifyClient("client/showView", p);
      }
      return d.instance;
    }

    private IViewPart createView(ViewDescriptor d) {
      if (d.instance != null)
        return d.instance;
      // an external part sets itself up in its factory
      if (d.external)
        return d.factory.get();
      IViewPart part = d.factory.get();
      Shell created = null;
      try {
        PartSite site = new PartSite(d.id, part);
        part.init(site);
        Shell shell = new Shell(SWT.NONE);
        created = shell;
        shell.setHost("view", d.hostView);
        shell.setLayout(new FillLayout());
        site.setShell(shell);
        part.createPartControl(shell);
        // VSCODE: the view toolbar is realized with the view, so its button image keys are published.
        site.getActionBars().getToolBarManager().getControl();
        // The content description of the part (e.g. "3 items" of the marker views, "Selection from ..." of a pinned
        // Properties view) is the description of the VS Code view.
        if (part instanceof WorkbenchPart) {
          WorkbenchPart wp = (WorkbenchPart) part;
          String viewId = d.hostView;
          Runnable send = () -> {
            if (com.unitesk.requality.vscode.core.ServerContext.server() != null) {
              com.google.gson.JsonObject p = new com.google.gson.JsonObject();
              p.addProperty("view", viewId);
              p.addProperty("description", wp.getContentDescription());
              com.unitesk.requality.vscode.core.ServerContext.client().notify("client/viewDescription", p);
            }
          };
          wp.addPropertyListener((source, propId) -> {
            if (propId == IWorkbenchPart.PROP_TITLE)
              send.run();
          });
          if (!wp.getContentDescription().isEmpty())
            send.run();
        }
        shell.addListener(SWT.Dispose, e -> {
          synchronized (Workbench.class) {
            if (d.instance == part)
              d.instance = null;
          }
          part.dispose();
        });
        shell.open();
        return part;
      } catch (PartInitException | RuntimeException e) {
        // The view could not be created: its shell (and with it the part) is disposed.
        RLogger.logError(e);
        if (created != null && !created.isDisposed())
          created.dispose();
        else
          try {
            part.dispose();
          } catch (RuntimeException x) {
            RLogger.logError(x);
          }
        return null;
      }
    }

    @Override
    public void hideView(IViewPart view) {
      synchronized (Workbench.class) {
        for (Map.Entry<String, ViewDescriptor> e : VIEWS.entrySet()) {
          ViewDescriptor d = e.getValue();
          if (d.instance != view)
            continue;
          if (!d.external) {
            IWorkbenchPartSite s = view.getSite();
            if (s != null && s.getShell() != null)
              Display.getDefault().asyncExec(() -> s.getShell().dispose());
          }
          // the VS Code view of the part is hidden as well (its Hide command)
          String id = d.external ? e.getKey() : d.hostView;
          if (id != null) {
            JsonObject p = new JsonObject();
            p.addProperty("id", id);
            notifyClient("client/hideView", p);
          }
        }
      }
    }

    @Override
    public IEditorPart openEditor(IEditorInput input, String editorId) throws PartInitException {
      return openEditor(input, editorId, true, MATCH_INPUT);
    }

    @Override
    public IEditorPart openEditor(IEditorInput input, String editorId, boolean activate) throws PartInitException {
      return openEditor(input, editorId, activate, MATCH_INPUT);
    }

    @Override
    public IEditorPart openEditor(IEditorInput input, String editorId, boolean activate, int matchFlags)
        throws PartInitException {
      Supplier<? extends IEditorPart> factory;
      synchronized (Workbench.class) {
        factory = EDITORS.get(editorId);
        if (factory == null && editorId != null) {
          factory = editorFromExtensions(editorId);
          if (factory != null)
            EDITORS.put(editorId, factory);
        }
        for (EditorRef r : OPEN_EDITORS) {
          boolean idOk = (matchFlags & MATCH_ID) == 0 || r.id.equals(editorId);
          boolean inputOk = (matchFlags & MATCH_INPUT) == 0 || input != null && input.equals(r.part.getEditorInput());
          if (matchFlags != MATCH_NONE && idOk && inputOk) {
            if (activate)
              activate(r.part);
            return r.part;
          }
        }
      }
      if (factory == null) {
        ExternalEditorOpener o = externalOpener;
        if (o == null)
          throw new PartInitException("Editor " + editorId + " is not available in VS Code");
        return o.open(input, editorId, activate);
      }
      PartInitException[] error = new PartInitException[1];
      final Supplier<? extends IEditorPart> ffactory = factory;
      IEditorPart part = Display.getDefault().syncCall(() -> {
        IEditorPart p = ffactory.get();
        Shell[] created = new Shell[1];
        boolean[] hooked = new boolean[1];
        try {
          PartSite site = new PartSite(editorId, p);
          p.init(site, input);
          Shell shell = new Shell(SWT.NONE);
          created[0] = shell;
          shell.setLayout(new FillLayout());
          shell.setText(p.getTitle());
          site.setShell(shell);
          p.createPartControl(shell);
          for (java.util.function.Consumer<IEditorPart> hook : EDITOR_CREATED_HOOKS)
            hook.accept(p);
          // The editor tab shows the part title, "*" while dirty, and follows later changes (PROP_TITLE/PROP_DIRTY).
          shell.setText(tabText(p));
          p.addPropertyListener((source, propId) -> {
            if (propId == IWorkbenchPart.PROP_TITLE || propId == IWorkbenchPart.PROP_DIRTY)
              Display.getDefault().asyncExec(() -> {
                if (!shell.isDisposed())
                  shell.setText(tabText(p));
              });
          });
          shell.addListener(SWT.Dispose, e -> {
            editorClosed(p);
            p.dispose();
          });
          hooked[0] = true;
          editorOpened(editorId, p);
          shell.open();
          partActivated(p, null);
          return p;
        } catch (PartInitException | RuntimeException e) {
          // The editor could not be created: its tab and the part are disposed (Eclipse shows the error instead).
          error[0] = e instanceof PartInitException ? (PartInitException) e
              : new PartInitException("Could not open the editor " + editorId + ": " + e, e);
          if (!(e instanceof PartInitException))
            RLogger.logError(e);
          if (created[0] != null && !created[0].isDisposed())
            created[0].dispose();
          if (!hooked[0])
            try {
              p.dispose();
            } catch (RuntimeException x) {
              RLogger.logError(x);
            }
          return null;
        }
      });
      if (error[0] != null)
        throw error[0];
      return part;
    }

    @Override
    public boolean closeEditor(IEditorPart editor, boolean save) {
      if (save && editor.isDirty())
        editor.doSave(null);
      IWorkbenchPartSite s = editor.getSite();
      if (s != null && s.getShell() != null) {
        Display.getDefault().asyncExec(() -> {
          if (!s.getShell().isDisposed())
            s.getShell().dispose();
        });
      } else {
        JsonObject p = new JsonObject();
        p.addProperty("editor", System.identityHashCode(editor));
        editorClosed(editor);
        editor.dispose();
      }
      return true;
    }

    @Override
    public boolean closeEditors(IEditorReference[] editorRefs, boolean save) {
      for (IEditorReference r : editorRefs)
        closeEditor(r.getEditor(false), save);
      return true;
    }

    @Override
    public boolean closeAllEditors(boolean save) {
      return closeEditors(getEditorReferences(), save);
    }

    @Override
    public void activate(IWorkbenchPart part) {
      bringToTop(part);
      partActivated(part, null);
    }

    @Override
    public void bringToTop(IWorkbenchPart part) {
      if (part == null)
        return;
      IWorkbenchPartSite s = part.getSite();
      if (s != null && s.getShell() != null) {
        Shell shell = s.getShell();
        Display.getDefault().asyncExec(() -> {
          if (!shell.isDisposed())
            shell.setActive();
        });
        return;
      }
      JsonObject p = new JsonObject();
      p.addProperty("id", part.getId());
      notifyClient("client/showView", p);
    }

    @Override
    public boolean isPartVisible(IWorkbenchPart part) {
      return part != null;
    }

    @Override
    public IWorkbenchWindow getWorkbenchWindow() {
      return WINDOW;
    }

    /** IWorkbenchPage.getInput(): the input of the page is the workspace root. */
    @Override
    public com.unitesk.requality.vscode.ui.runtime.IAdaptable getInput() {
      return com.unitesk.requality.vscode.ui.resources.ResourcesPlugin.getWorkspace().getRoot();
    }

    @Override
    public ISelection getSelection() {
      return selection;
    }

    @Override
    public ISelection getSelection(String partId) {
      return SELECTION_SERVICE.getSelection(partId);
    }

    @Override
    public void addSelectionListener(ISelectionListener listener) {
      SELECTION_SERVICE.addSelectionListener(listener);
    }

    @Override
    public void removeSelectionListener(ISelectionListener listener) {
      SELECTION_SERVICE.removeSelectionListener(listener);
    }

    @Override
    public void addPartListener(IPartListener listener) {
      PART_LISTENERS.add(listener);
    }

    @Override
    public void removePartListener(IPartListener listener) {
      PART_LISTENERS.remove(listener);
    }

    @Override
    public void addPartListener(IPartListener2 listener) {
      PART_LISTENERS_2.add(listener);
    }

    @Override
    public void removePartListener(IPartListener2 listener) {
      PART_LISTENERS_2.remove(listener);
    }

    @Override
    public IPerspectiveRegistry.IPerspectiveDescriptor getPerspective() {
      return perspective;
    }

    /** {@code IWorkbenchPage.getShowViewShortcuts}: the shortcuts of the current perspective. */
    @Override
    public String[] getShowViewShortcuts() {
      return shortcuts(showViewShortcuts);
    }

    /** {@code IWorkbenchPage.getNewWizardShortcuts}: the shortcuts of the New submenu of the current perspective. */
    @Override
    public String[] getNewWizardShortcuts() {
      return shortcuts(newWizardShortcuts);
    }

    /** The shortcuts of the Open Perspective submenu of the current perspective. */
    @Override
    public String[] getPerspectiveShortcuts() {
      return shortcuts(perspectiveShortcuts);
    }

    private String[] shortcuts(Map<String, List<String>> byPerspective) {
      List<String> l = perspective == null ? null : byPerspective.get(perspective.getId());
      return l == null ? new String[0] : l.toArray(new String[0]);
    }

    @Override
    public IPerspectiveRegistry.IPerspectiveDescriptor[] getOpenPerspectives() {
      return openPerspectives.toArray(new IPerspectiveRegistry.IPerspectiveDescriptor[0]);
    }

    /**
     * {@code IWorkbenchPage.setPerspective}: the perspective factory lays out its views, and the views are shown in VS
     * Code. The perspective that is already shown is not applied again.
     */
    @Override
    public void setPerspective(IPerspectiveRegistry.IPerspectiveDescriptor desc) {
      if (desc == null || desc.equals(perspective))
        return;
      applyPerspective(desc, true);
    }

    /**
     * {@code WorkbenchPage.resetPerspective}: the views are arranged as the perspective defines them again. The
     * extension resets the locations of the views first ({@code client/resetViewLocations}).
     */
    @Override
    public void resetPerspective() {
      if (perspective == null)
        return;
      String id = perspective.getId();
      PerspectiveCustomization.remove(id);
      showViewShortcuts.remove(id);
      newWizardShortcuts.remove(id);
      perspectiveShortcuts.remove(id);
      JsonObject p = new JsonObject();
      p.addProperty("perspective", id);
      notifyClient("client/resetViewLocations", p);
      resetting = true;
      try {
        applyPerspective(perspective, true);
      } finally {
        resetting = false;
      }
    }

    /** True while Reset Perspective applies the layout of the perspective again. */
    private boolean resetting;

    /**
     * The places of the views of the perspective's layout ({@code client/viewLayout}): the region of the window each view
     * of the layout is in (left, right, top or bottom of the editor area). VSCODE: VS Code places the views in its side
     * bars and its panel; the extension moves them there when the perspective is opened the first time and when it is
     * reset, as Eclipse lays out a perspective it opens.
     */
    private void publishViewLayout(String perspectiveId, PageLayout layout, List<String> views) {
      JsonArray placed = new JsonArray();
      for (String viewId : views) {
        String vsId;
        synchronized (Workbench.class) {
          ViewDescriptor d = VIEWS.get(viewId);
          vsId = d == null ? null : d.external ? viewId : d.hostView;
        }
        int region = layout.regionOf(viewId);
        if (vsId == null || region == 0)
          continue;
        JsonObject v = new JsonObject();
        v.addProperty("id", vsId);
        v.addProperty("region", region == IPageLayout.LEFT ? "left" : region == IPageLayout.RIGHT ? "right"
            : region == IPageLayout.TOP ? "top" : "bottom");
        placed.add(v);
      }
      JsonObject p = new JsonObject();
      p.addProperty("perspective", perspectiveId);
      p.addProperty("reset", resetting);
      p.add("views", placed);
      notifyClient("client/viewLayout", p);
    }

    /**
     * {@code Customize Perspective...} ({@link IWorkbenchPage#customizePerspective}): the shortcuts and the hidden menu
     * items of the current perspective become its customization, which is applied whenever the perspective is shown.
     */
    @Override
    public void customizePerspective(List<String> newWizards, List<String> perspectives, List<String> showViews,
        List<String> menuHidden) {
      if (perspective == null)
        return;
      String id = perspective.getId();
      PerspectiveCustomization.put(id,
          new PerspectiveCustomization.Customization(newWizards, perspectives, showViews, menuHidden));
      newWizardShortcuts.put(id, new ArrayList<>(newWizards));
      perspectiveShortcuts.put(id, new ArrayList<>(perspectives));
      showViewShortcuts.put(id, new ArrayList<>(showViews));
      MainMenu.publishHidden(id);
      ShowViewMenu.publish();
    }

    /**
     * {@code WorkbenchPage.closePerspective}: the perspective is no longer open and its views are hidden (the views
     * that another open perspective shows, and the views of the extension, stay). VSCODE: the editors stay open and
     * the window is not closed, so saveParts and closePage have no effect.
     */
    @Override
    public void closePerspective(IPerspectiveRegistry.IPerspectiveDescriptor desc, boolean saveParts, boolean closePage) {
      if (desc == null || !openPerspectives.contains(desc))
        return;
      String id = desc.getId();
      openPerspectives.remove(desc);
      List<String> views = placedViews.remove(id);
      if (perspective != null && id.equals(perspective.getId()))
        perspective = null;
      if (views != null)
        for (String viewId : views)
          if (!isPlacedByOpenPerspective(viewId)) {
            IViewPart part = findView(viewId);
            if (part != null)
              hideView(part);
          }
      updateWindowTitle();
      MainMenu.publishHidden(perspective == null ? null : perspective.getId());
      ShowViewMenu.publish();
    }

    /** {@code WorkbenchPage.closeAllPerspectives}: closes each open perspective (see {@link #closePerspective}). */
    @Override
    public void closeAllPerspectives(boolean saveEditors, boolean closePage) {
      if (saveEditors && !Workbench.INSTANCE.saveAllEditors(true))
        return;
      for (IPerspectiveRegistry.IPerspectiveDescriptor d : getOpenPerspectives())
        closePerspective(d, saveEditors, closePage);
    }

    private boolean isPlacedByOpenPerspective(String viewId) {
      for (List<String> l : placedViews.values())
        if (l.contains(viewId))
          return true;
      return false;
    }

    /**
     * {@code WorkbenchPage.savePerspectiveAs}: the views of the page (the views placed by the current perspective and the
     * views shown since) are saved as the layout of the perspective, which becomes the current one and replaces the
     * previous perspective among the open ones. The saved perspective is read again from the registry, so it has its
     * custom definition. VSCODE: the layout is the list of the views, as VS Code shows no other layout.
     */
    @Override
    public void savePerspectiveAs(IPerspectiveRegistry.IPerspectiveDescriptor desc) {
      if (!(desc instanceof PerspectiveRegistry.PerspectiveDescriptor))
        return;
      List<String> shortcuts = new ArrayList<>(Arrays.asList(getShowViewShortcuts()));
      List<String> views = new ArrayList<>();
      if (perspective != null && placedViews.containsKey(perspective.getId()))
        views.addAll(placedViews.get(perspective.getId()));
      for (IViewReference ref : getViewReferences())
        if (!views.contains(ref.getId()))
          views.add(ref.getId());
      PerspectiveRegistry.INSTANCE.savePerspective((PerspectiveRegistry.PerspectiveDescriptor) desc, views);
      IPerspectiveRegistry.IPerspectiveDescriptor saved = PerspectiveRegistry.INSTANCE.findPerspectiveWithId(desc.getId());
      if (perspective != null) {
        openPerspectives.remove(perspective);
        placedViews.remove(perspective.getId());
      }
      placedViews.put(saved.getId(), views);
      showViewShortcuts.put(saved.getId(), shortcuts);
      openPerspectives.remove(saved);
      openPerspectives.add(saved);
      perspective = saved;
      updateWindowTitle();
      MainMenu.publishHidden(saved.getId());
      ShowViewMenu.publish();
    }

    /**
     * Applies the layout of a perspective: its factory places the views (shown in VS Code if {@code show}), or its custom
     * definition does. The perspective becomes the current one and an open one. VSCODE: the views of the previous
     * perspective stay open.
     */
    private void applyPerspective(IPerspectiveRegistry.IPerspectiveDescriptor desc, boolean show) {
      if (!(desc instanceof PerspectiveRegistry.PerspectiveDescriptor))
        return;
      PerspectiveRegistry.PerspectiveDescriptor pd = (PerspectiveRegistry.PerspectiveDescriptor) desc;
      String id = desc.getId();
      List<String> views = new ArrayList<>();
      if (pd.hasCustomDefinition()) {
        for (String viewId : pd.getViews()) {
          try {
            showView(viewId, null, show ? IWorkbenchPage.VIEW_VISIBLE : IWorkbenchPage.VIEW_CREATE);
            views.add(viewId);
          } catch (PartInitException e) {
            RLogger.logError(e);
          }
        }
      } else {
        PageLayout layout = new PageLayout(show);
        try {
          IPerspectiveFactory f = (IPerspectiveFactory) pd.getElement().createExecutableExtension("class");
          f.createInitialLayout(layout);
        } catch (com.unitesk.requality.vscode.ui.runtime.CoreException | RuntimeException e) {
          RLogger.logError(e);
          return;
        }
        for (PageLayout.ViewPlacement p : layout.getViews())
          if (p.isAvailable() && !p.isPlaceholder())
            views.add(p.getViewId());
        publishViewLayout(id, layout, views);
        showViewShortcuts.put(id, new ArrayList<>(layout.getShowViewShortcuts()));
        newWizardShortcuts.put(id, new ArrayList<>(layout.getNewWizardShortcuts()));
        perspectiveShortcuts.put(id, new ArrayList<>(layout.getPerspectiveShortcuts()));
      }
      // the customization of the perspective (Customize Perspective...) replaces the shortcuts of its definition
      PerspectiveCustomization.Customization c = PerspectiveCustomization.get(id);
      if (c != null) {
        showViewShortcuts.put(id, c.getShowViews());
        newWizardShortcuts.put(id, c.getNewWizards());
        perspectiveShortcuts.put(id, c.getPerspectives());
      }
      placedViews.put(id, views);
      openPerspectives.remove(desc);
      openPerspectives.add(desc);
      perspective = desc;
      updateWindowTitle();
      MainMenu.publishHidden(id);
      ShowViewMenu.publish();
    }
  }
}
