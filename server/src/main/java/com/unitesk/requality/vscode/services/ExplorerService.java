package com.unitesk.requality.vscode.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.checker.CheckerContainer;
import com.unitesk.requality.core.checker.IMark;
import com.unitesk.requality.core.operation.ChangesSet;
import com.unitesk.requality.core.events.DefaultTreeChangeListener;
import com.unitesk.requality.core.events.ITreeChangeListener;
import com.unitesk.requality.core.events.TreeNodeChangeEvent;
import com.unitesk.requality.core.exception.NodeNotFoundException;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.sorting.SortingSettings;
import com.unitesk.requality.core.sorting.TreeNodeComparator;
import com.unitesk.requality.core.storage.ITreeStateListener;
import com.unitesk.requality.core.storage.OFlag;
import com.unitesk.requality.core.tree.TreeState;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.nodetype.Comment;
import com.unitesk.requality.nodetype.DocFolder;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.InstancesFolder;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.NodeType;
import com.unitesk.requality.nodetype.ReportData;
import com.unitesk.requality.nodetype.ReportFolder;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.RestrictedFolder;
import com.unitesk.requality.nodetype.TestPurpose;
import com.unitesk.requality.nodetype.TextNode;
import com.unitesk.requality.vscode.RequalityServer;
import com.unitesk.requality.vscode.core.CoreBootstrap;
import com.unitesk.requality.vscode.core.Icons;
import com.unitesk.requality.vscode.core.Icons.Overlay;
import com.unitesk.requality.vscode.core.Icons.Quadrant;
import com.unitesk.requality.vscode.core.Nodes;
import com.unitesk.requality.vscode.core.Workspace;

/**
 * Requality Explorer content: port of {@code RequalityContentProvider}, {@code RequalityWorkbenchAdapter},
 * {@code RequalitySorter}, the CNF filters and the lightweight decorators.
 */
public final class ExplorerService implements Services.Service {

  /**
   * Adds tags to an item's context value, used by menu {@code when} clauses (for example {@code new.<Type>} for the
   * dynamic New menu).
   */
  public interface ContextTagProvider {
    void tags(TreeNode node, List<String> out);
  }

  private static final List<ContextTagProvider> TAG_PROVIDERS = new CopyOnWriteArrayList<>();

  public static void registerContextTags(ContextTagProvider p) {
    TAG_PROVIDERS.add(p);
  }

  /** CNF common filters: id -> hidden node types, default activation (plugin.xml). */
  public static final Map<String, String[]> FILTER_TYPES = new LinkedHashMap<>();
  public static final Set<String> FILTERS_ACTIVE_BY_DEFAULT = new HashSet<>();
  static {
    FILTER_TYPES.put("documents", new String[] { Document.TYPE_NAME, DocFolder.TYPE_NAME });
    FILTER_TYPES.put("requirements", new String[] { Requirement.TYPE_NAME });
    FILTER_TYPES.put("types", new String[] { NodeType.TYPE_NAME });
    FILTER_TYPES.put("locations", new String[] { Location.TYPE_NAME });
    FILTER_TYPES.put("reportData", new String[] { ReportData.TYPE_NAME, ReportFolder.TYPE_NAME });
    FILTER_TYPES.put("comments", new String[] { Comment.TYPE_NAME });
    FILTER_TYPES.put("restrictedFolders", new String[] { RestrictedFolder.TYPE_NAME });
    FILTER_TYPES.put("instancesFolders", new String[] { InstancesFolder.TYPE_NAME });
    FILTERS_ACTIVE_BY_DEFAULT.addAll(Arrays.asList("types", "locations", "restrictedFolders", "instancesFolders"));
  }

  private static final Set<String> SHOWN_NAMES = new HashSet<>(Arrays.asList(TreeNode.ATTR_LABEL,
      TreeDB.INDEX_ATTRIBUTE, CheckerContainer.VATTR_ERRORS, SortingSettings.SORT_METHOD, TreeNode.ATTR_NAME,
      TextNode.ATTR_TNTYPE, TreeNode.ATTR_SHOWN_CHILDREN));

  private static volatile Set<String> hiddenTypes = defaultHiddenTypes();

  /** A filter of the explorer, by its key in the setting requality.explorer.filters, is active. */
  static boolean resourceFilter(String key) {
    String id = ExplorerContentService.SETTING_KEYS.get(key);
    return id != null && viewer().contentService().filters().isActive(id);
  }

  /**
   * The filter activation changed (NavigatorFilterService.updateViewer): the node types hidden by the active
   * Requality filters (RequalityCNF.updateStaticFilter).
   */
  static void filtersChanged() {
    Set<String> types = new HashSet<>();
    for (Map.Entry<String, String[]> e : FILTER_TYPES.entrySet())
      if (resourceFilter(e.getKey()))
        types.addAll(Arrays.asList(e.getValue()));
    hiddenTypes = types;
  }

  /** Whether a content extension of the explorer is active (Filters and Customization > Content). */
  static boolean contentActive(String id) {
    return viewer().contentService().isActive(id);
  }

  /** Projects and resources are provided by the non-Requality project content or the resource content. */
  static boolean resourcesShown() {
    return contentActive(ExplorerContentService.NON_REQUALITY_CONTENT) || contentActive(ExplorerContentService.RESOURCE_CONTENT);
  }

  public static final String RESOURCE_PREFIX = "resource:";
  private static RequalityServer server;

  private final Map<TreeDB, ITreeChangeListener> listeners = new ConcurrentHashMap<>();
  private final Map<TreeDB, SortingSettings> sortings = new ConcurrentHashMap<>();
  private final Set<String> pendingRefresh = Collections.synchronizedSet(new LinkedHashSet<>());
  private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r, "requality-explorer-refresh");
    t.setDaemon(true);
    return t;
  });
  private volatile boolean refreshScheduled;

  /** {@code RequalityCNF.isShown}: attributes shown in the label or by a decorator. */
  public static boolean isShown(String attr) {
    if (SHOWN_NAMES.contains(attr))
      return true;
    // RequalityCNF.isShown: attributes the decorators depend on.
    com.unitesk.requality.vscode.ui.workbench.LightweightDecorators.ensureCreated();
    for (com.unitesk.requality.vscode.port.marker.RequalityDecorator rd : com.unitesk.requality.vscode.port.marker.RequalityDecorator
        .getAll()) {
      Set<String> atrs = rd.getRelatedKeys();
      if (atrs != null && atrs.contains(attr))
        return true;
    }
    return false;
  }

  private static Set<String> defaultHiddenTypes() {
    Set<String> s = new HashSet<>();
    for (String f : FILTERS_ACTIVE_BY_DEFAULT)
      s.addAll(Arrays.asList(FILTER_TYPES.get(f)));
    return s;
  }

  /** {@code RequalityCNF.updateStaticFilter}: hidden node types from the active filters. */
  public static boolean passesStaticFilter(TreeNode node) {
    if (node == null)
      return false;
    String type = node.getType();
    return type != null && !hiddenTypes.contains(type);
  }

  @Override
  public void register(RequalityServer srv) {
    server = srv;
    instance = this;
    // RequalityCNF.updateStaticFilter: the content providers that check the static filter (outline pages, node
    // selection dialogs) hide the node types the explorer's active filters hide.
    com.unitesk.requality.vscode.port.eclipse.views.RequalityContentProvider.setStaticFilter(ExplorerService::passesStaticFilter);
    // IDecoratorManager.update(...) of the decorators recomputes the explorer labels.
    com.unitesk.requality.vscode.ui.workbench.IDecoratorManager.LISTENERS.add(id -> refreshAll());
    srv.rpc().onRequest("explorer/roots", p -> {
      srv.requireInitialized();
      return roots();
    });
    srv.rpc().onRequest("explorer/children", p -> {
      srv.requireInitialized();
      String id = Nodes.string(p, "id");
      return viewer().filter(element(id), children(id));
    });
    srv.rpc().onRequest("explorer/item", p -> {
      srv.requireInitialized();
      return itemFor(Nodes.string(p, "id"));
    });
    // The VS Code tree reports expanded and collapsed items (CommonViewer.getExpandedElements of the frames).
    srv.rpc().onNotification("explorer/expanded", p -> {
      ExplorerViewer v = viewer();
      v.expansionChanged(Nodes.string(p, "id"), p.has("expanded") && p.get("expanded").getAsBoolean());
    });
    // The frame actions of the view toolbar (CommonNavigatorActionGroup: Back, Forward, Up).
    srv.rpc().onRequest("explorer/frameAction", p -> {
      srv.requireInitialized();
      String action = Nodes.string(p, "action");
      com.unitesk.requality.vscode.ui.swt.widgets.Display.getDefault().syncExec(() -> {
        ExplorerViewer v = viewer();
        com.unitesk.requality.vscode.ui.jface.action.IAction a = "back".equals(action) ? v.getBackAction()
            : "forward".equals(action) ? v.getForwardAction() : "up".equals(action) ? v.getUpAction() : null;
        if (a != null && a.isEnabled())
          a.run();
      });
      return frames();
    });
    srv.rpc().onRequest("explorer/frames", p -> {
      srv.requireInitialized();
      return frames();
    });
    // Collapse All of the toolbar of the Eclipse view (CollapseAllHandler: the viewer collapses all its items)
    srv.rpc().onRequest("explorer/collapseAll", p -> {
      srv.requireInitialized();
      com.unitesk.requality.vscode.ui.swt.widgets.Display.getDefault().syncExec(() -> {
        ExplorerViewer v = navigator == null ? null : (ExplorerViewer) navigator.getCommonViewer();
        if (v != null && v.isRendered())
          v.collapseAll();
      });
      return null;
    });
    srv.rpc().onRequest("explorer/parent", p -> {
      srv.requireInitialized();
      String id = Nodes.string(p, "id");
      String parent;
      if (id.startsWith(PROJECT_PREFIX))
        parent = null;
      else if (id.startsWith(RESOURCE_PREFIX))
        parent = resourceParentId(new java.io.File(id.substring(RESOURCE_PREFIX.length())));
      else
        parent = parentId(Nodes.require(p, "id"));
      // the roots of the VS Code tree are the children of the viewer input (a frame)
      return parent != null && parent.equals(idOf(viewer().getInput())) ? null : parent;
    });
    // The settings of the explorer (Filters and Customization): filter activation, content activation, user filters.
    srv.rpc().onRequest("explorer/setFilters", p -> {
      JsonObject active = p.has("filters") && p.get("filters").isJsonObject() ? p.getAsJsonObject("filters") : null;
      JsonObject content = p.has("contentExtensions") && p.get("contentExtensions").isJsonObject() ? p.getAsJsonObject("contentExtensions") : null;
      JsonArray user = p.has("userFilters") && p.get("userFilters").isJsonArray() ? p.getAsJsonArray("userFilters") : null;
      com.unitesk.requality.vscode.ui.swt.widgets.Display.getDefault().syncExec(() -> viewer().contentService().applySettings(active, content, user));
      return null;
    });
    srv.rpc().onRequest("explorer/open", p -> {
      srv.requireInitialized();
      return com.unitesk.requality.vscode.ui.swt.widgets.Display.getDefault().syncCall(() -> {
        if (p.has("nodes") && p.get("nodes").isJsonArray())
          SelectionService.select("explorer", p.getAsJsonArray("nodes"));
        return openSelection();
      });
    });
    // CommonNavigator drag and drop: the dragged nodes are dropped on a node by the drop assistants of plugin.xml
    // (CNFDropAssistant); "after" is a drop with Shift (insert after the target), "reuse" a drop with Alt.
    // Resources (projects, folders, files) dropped on a project, folder or file go to the ResourceDropAdapterAssistant:
    // "move" or "copy" (a drop with Ctrl in Eclipse).
    srv.rpc().onRequest("explorer/drop", p -> {
      srv.requireInitialized();
      List<Object> sources = new ArrayList<>();
      for (JsonElement e : p.getAsJsonArray("sources")) {
        Object o = element(e.getAsString());
        if (o != null)
          sources.add(o);
      }
      Object target = element(Nodes.string(p, "target"));
      String op = Nodes.optString(p, "operation", "move");
      if (target == null || sources.isEmpty())
        return false;
      int operation = "copy".equals(op) ? com.unitesk.requality.vscode.ui.swt.dnd.DND.DROP_COPY
          : com.unitesk.requality.vscode.ui.navigator.NavigatorDnDService.defaultOperation();
      return com.unitesk.requality.vscode.ui.swt.widgets.Display.getDefault().syncCall(() -> {
        boolean shift = com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF.shiftPressed;
        boolean alt = com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF.altPressed;
        com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF.shiftPressed = "after".equals(op);
        com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF.altPressed = "reuse".equals(op);
        try {
          com.unitesk.requality.vscode.ui.runtime.IStatus st = new com.unitesk.requality.vscode.ui.navigator.NavigatorDnDService(
              com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF.ID).performDrop(
                  treeSelection(sources), target, operation);
          if (st != null && st.getSeverity() == com.unitesk.requality.vscode.ui.runtime.IStatus.ERROR && !st.isMultiStatus())
            return st.getMessage(); // the drop was rejected: the reason
          return st != null && st.getSeverity() != com.unitesk.requality.vscode.ui.runtime.IStatus.ERROR;
        } finally {
          com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF.shiftPressed = shift;
          com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF.altPressed = alt;
        }
      });
    });
    // ResourceDropAdapterAssistant: files dropped on a project or folder are copied into it.
    srv.rpc().onRequest("explorer/dropFiles", p -> {
      srv.requireInitialized();
      String target = Nodes.string(p, "target");
      java.io.File dir;
      if (target.startsWith(PROJECT_PREFIX)) {
        Workspace.Project wp = srv.workspace().anyProject(target.substring(PROJECT_PREFIX.length()));
        dir = wp == null || wp.closed ? null : wp.dir;
      } else if (target.startsWith(RESOURCE_PREFIX)) {
        java.io.File f = new java.io.File(target.substring(RESOURCE_PREFIX.length()));
        dir = f.isDirectory() ? f : f.getParentFile();
      } else
        dir = null;
      if (dir == null)
        return false;
      List<String> paths = new ArrayList<>();
      for (JsonElement e : p.getAsJsonArray("paths"))
        paths.add(e.getAsString());
      java.io.File fdir = dir;
      com.unitesk.requality.vscode.ui.swt.widgets.Display.getDefault().syncExec(() -> {
        com.unitesk.requality.vscode.ui.resources.IContainer c = com.unitesk.requality.vscode.ui.resources.ResourcesPlugin
            .getWorkspace().getRoot().getContainerForLocation(com.unitesk.requality.vscode.ui.runtime.Path.fromOSString(fdir.getAbsolutePath()));
        if (c != null)
          new com.unitesk.requality.vscode.ui.workbench.actions.CopyFilesAndFoldersOperation(
              com.unitesk.requality.vscode.ui.workbench.PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell())
                  .copyFiles(paths.toArray(new String[0]), c);
      });
      return true;
    });
    srv.rpc().onRequest("explorer/setLinked", p -> {
      setLinked(Nodes.optBool(p, "linked", true));
      return null;
    });
    // The common filters shown in Filters and Customization, with their activation.
    srv.rpc().onRequest("explorer/filters", p -> {
      JsonArray arr = new JsonArray();
      com.unitesk.requality.vscode.ui.navigator.NavigatorFilterService fs = viewer().contentService().filters();
      for (com.unitesk.requality.vscode.ui.navigator.filters.ICommonFilterDescriptor d : fs.getVisibleFilterDescriptorsForUI()) {
        JsonObject o = new JsonObject();
        o.addProperty("id", d.getId());
        o.addProperty("name", d.getName());
        o.addProperty("activeByDefault", d.isActiveByDefault());
        o.addProperty("active", fs.isActive(d.getId()));
        arr.add(o);
      }
      return arr;
    });
  }

  /** The selection of dragged tree items (CommonDragAdapter: an ITreeSelection with the path of each item). */
  private static com.unitesk.requality.vscode.ui.jface.viewers.TreeSelection treeSelection(List<Object> elements) {
    com.unitesk.requality.vscode.ui.jface.viewers.TreePath[] paths = new com.unitesk.requality.vscode.ui.jface.viewers.TreePath[elements.size()];
    for (int i = 0; i < paths.length; i++)
      paths[i] = new com.unitesk.requality.vscode.ui.jface.viewers.TreePath(new Object[] { elements.get(i) });
    return new com.unitesk.requality.vscode.ui.jface.viewers.TreeSelection(paths);
  }

  /** Called once the core is started (tracker exists). */
  public void attach() {
    CoreBootstrap.tracker().addListener(Moment.AFTER, new ITreeStateListener() {
      @Override
      public void open(TreeDB db) {
        listen(db);
        refreshAll();
      }

      @Override
      public void close(TreeDB db) {
        unlisten(db);
        refreshAll();
      }

      @Override
      public void delete(String localName, String projectName, TreeDB db) {
        unlisten(db);
        refreshAll();
      }

      @Override
      public void settingsChanged(TreeDB db) {
        refreshAll();
      }
    });
    for (TreeDB db : CoreBootstrap.tracker().getTreeDBs())
      if (db != null)
        listen(db);
  }

  public static final String PROJECT_PREFIX = "project:";

  /**
   * The roots of the VS Code tree: the children of the viewer input (the workspace root: its projects; or the
   * project or folder the explorer went into), as the viewer filters let them through.
   */
  private JsonArray roots() {
    if (listenersAttached.compareAndSet(false, true))
      attach();
    ExplorerViewer v = viewer();
    Object input = v.getInput();
    if (input != null && !(input instanceof com.unitesk.requality.vscode.ui.resources.IWorkspaceRoot)) {
      String id = idOf(input);
      return id == null ? new JsonArray() : v.filter(input, children(id));
    }
    return v.filter(input, projectList());
  }

  /** The projects of the workspace as explorer items. */
  private JsonArray projectList() {
    JsonArray arr = new JsonArray();
    // the projects come from the non-Requality project content and the resource content (Filters and Customization)
    if (!resourcesShown())
      return arr;
    // NonReqContentProvider: all projects of the workspace, the catalogs and the other Eclipse projects.
    List<Workspace.Project> ps = new ArrayList<>(server.workspace().projects());
    ps.addAll(server.workspace().otherProjects());
    // RequalitySorter: projects by name.
    ps.sort((a, b) -> a.localName.compareTo(b.localName));
    for (Workspace.Project p : ps)
      if (!(p.closed && resourceFilter("closedProjects")))
        arr.add(projectItem(p));
    return arr;
  }

  private final java.util.concurrent.atomic.AtomicBoolean listenersAttached = new java.util.concurrent.atomic.AtomicBoolean();

  /**
   * The image of a project in the explorer (WorkbenchLabelProvider of the resource content: WorkbenchProject's
   * IDE.SharedImages.IMG_OBJ_PROJECT, or IMG_OBJ_PROJECT_CLOSED of a closed project), as an absolute path.
   */
  static String projectImage(boolean open) {
    return com.unitesk.requality.vscode.ui.workbench.PlatformUI.getWorkbench().getSharedImages().getImage(open
        ? com.unitesk.requality.vscode.ui.workbench.ISharedImages.IMG_OBJ_PROJECT
        : com.unitesk.requality.vscode.ui.workbench.ISharedImages.IMG_OBJ_PROJECT_CLOSED).getPath();
  }

  private JsonObject projectItem(Workspace.Project p) {
    if (p == null)
      return null;
    JsonObject o = new JsonObject();
    o.addProperty("id", PROJECT_PREFIX + p.localName);
    o.addProperty("kind", "project");
    o.addProperty("path", p.dir.getAbsolutePath());
    String name = com.unitesk.requality.tool.CoreUtils.getProjectName(p.dir);
    if (name == null)
      name = p.localName;
    if (p.closed) {
      // A closed project (IProject.isOpen false): listed with the closed project image, without children.
      o.addProperty("label", name);
      o.addProperty("icon", projectImage(false));
      o.addProperty("hasChildren", false);
      o.addProperty("contextValue", "requality.project is.IProject closed" + (p.requality ? "" : " nonRequality"));
      return o;
    }
    if (!p.requality) {
      // Another Eclipse project: its files (WorkbenchContentProvider).
      o.addProperty("label", name);
      o.addProperty("icon", projectImage(true));
      o.addProperty("hasChildren", !members(p.dir).isEmpty());
      o.addProperty("contextValue", "requality.project is.IProject nonRequality");
      return o;
    }
    TreeDB db = Nodes.findDB(p.localName);
    boolean ready = db != null && Nodes.isReady(db);
    String label;
    // CNFDecorator / RequalityWorkbenchAdapter: "(broken)" and "(loading)" postfixes.
    // The IProject label is the project name (.project), as for the root node of a loaded catalog.
    if (p.error != null)
      label = name + " (" + com.unitesk.requality.core.Consts.BROKEN_POSTFIX + ")";
    else if (!ready)
      label = name + " (" + com.unitesk.requality.core.Consts.LOADING_POSTFIX + ")";
    else
      label = name;
    o.addProperty("label", label);
    o.addProperty("icon", projectImage(true));
    o.addProperty("hasChildren", ready && (hasChildren(db.getRootNode())
        || !resourceFilter("resourcesInRequalityProjects") && !members(p.dir).isEmpty()));
    o.addProperty("contextValue", "requality.project is.IProject" + (ready ? " ready" : ""));
    if (ready)
      o.addProperty("node", Nodes.id(db.getRootNode()));
    return o;
  }

  private JsonArray children(String id) {
    JsonArray arr = new JsonArray();
    TreeNode parent;
    if (id.startsWith(RESOURCE_PREFIX)) {
      if (resourcesShown())
        for (java.io.File f : members(new java.io.File(id.substring(RESOURCE_PREFIX.length()))))
          arr.add(resourceItem(f));
      return arr;
    }
    if (id.startsWith(PROJECT_PREFIX)) {
      Workspace.Project wp = server.workspace().anyProject(id.substring(PROJECT_PREFIX.length()));
      if (wp != null && wp.closed)
        return arr;
      if (wp != null && !wp.requality) {
        if (resourcesShown())
          for (java.io.File f : members(wp.dir))
            arr.add(resourceItem(f));
        return arr;
      }
      TreeDB db = Nodes.findDB(id.substring(PROJECT_PREFIX.length()));
      if (db == null || !Nodes.isReady(db))
        return arr;
      // the nodes of a catalog come from the Requality content ("Requality Database")
      if (!contentActive(ExplorerContentService.REQUALITY_CONTENT)) {
        if (wp != null && !resourceFilter("resourcesInRequalityProjects") && resourcesShown())
          for (java.io.File f : members(wp.dir))
            arr.add(resourceItem(f));
        return arr;
      }
      // Without the NotRequalityDataFilter a catalog also shows its files (resourceContent).
      if (wp != null && !resourceFilter("resourcesInRequalityProjects") && resourcesShown()) {
        JsonArray r = children(Nodes.id(db.getRootNode()));
        for (java.io.File f : members(wp.dir))
          r.add(resourceItem(f));
        return r;
      }
      parent = db.getRootNode();
    } else {
      parent = Nodes.find(id);
      if (parent == null)
        return arr;
    }
    TreeDB db = parent.getTreeDB();
    TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
    TreeState ts = tsm.getState(db.getLocalName(), tsm.getRevisionInfo(db));
    if (ts == TreeState.NOT_LOADED || TreeState.isProjectLoadable(ts))
      return arr;
    List<TreeNode> nodes = new ArrayList<>();
    for (TreeNode n : parent.getSortedShownChildren())
      if (n != null && passesStaticFilter(n))
        nodes.add(n);
    // RequalitySorter: TreeNodeComparator, then label.
    java.util.Comparator<TreeNode> cmp = TreeNodeComparator.getComparator(db);
    nodes.sort((a, b) -> {
      int i = cmp.compare(a, b);
      return i != 0 ? i : String.valueOf(a).compareTo(String.valueOf(b));
    });
    for (TreeNode n : nodes)
      arr.add(item(n));
    return arr;
  }

  /**
   * The members of a folder shown by the explorer (the ".* resources" filter hides names starting with a dot),
   * folders first, then by name (ResourceExtensionSorter).
   */
  static List<java.io.File> members(java.io.File dir) {
    java.io.File[] fs = dir.listFiles();
    List<java.io.File> r = new ArrayList<>();
    if (fs == null)
      return r;
    boolean hideDot = resourceFilter("startsWithDot");
    for (java.io.File f : fs)
      if (!(hideDot && f.getName().startsWith(".")))
        r.add(f);
    java.text.Collator collator = java.text.Collator.getInstance();
    r.sort((a, b) -> a.isDirectory() != b.isDirectory() ? (a.isDirectory() ? -1 : 1) : collator.compare(a.getName(), b.getName()));
    return r;
  }

  /** The explorer item of an id, or null. */
  private JsonObject itemFor(String id) {
    if (id.startsWith(PROJECT_PREFIX))
      return projectItem(server.workspace().anyProject(id.substring(PROJECT_PREFIX.length())));
    if (id.startsWith(RESOURCE_PREFIX)) {
      java.io.File f = new java.io.File(id.substring(RESOURCE_PREFIX.length()));
      return f.exists() ? resourceItem(f) : null;
    }
    TreeNode n = Nodes.find(id);
    return n == null ? null : item(n);
  }

  static ExplorerViewer viewer() {
    return (ExplorerViewer) navigator().getCommonViewer();
  }

  private static volatile com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF navigator;

  /** The VS Code view that shows the Eclipse view of the explorer (its SWT tree). */
  public static final String RENDERED_VIEW = "requality.explorerView";

  /**
   * How the Requality Explorer is shown (initialize option {@code explorerView}): true for the Eclipse view (the SWT
   * tree of the part, in the VS Code view {@link #RENDERED_VIEW}), false for the native tree of VS Code
   * ({@code requality.explorer}, whose items the extension asks for).
   */
  private static volatile boolean rendered;

  public static void setRendered(boolean value) {
    rendered = value;
    // views/open of the VS Code view shows the part (from initialize on: the view may be resolved before the part is
    // created)
    if (value)
      com.unitesk.requality.vscode.ui.workbench.Workbench.setHostView(
          com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF.ID, RENDERED_VIEW);
  }

  public static boolean isRendered() {
    return rendered;
  }

  /**
   * The Requality Explorer part (RequalityCNF, plugin.xml view): created on first use with its view site, its
   * viewer (the VS Code tree) and the saved state of the view, then shown by the VS Code view.
   */
  public static com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF navigator() {
    com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF cnf = navigator;
    if (cnf == null) {
      // the part is created on the UI thread, as the Eclipse workbench creates views
      com.unitesk.requality.vscode.ui.swt.widgets.Display display = com.unitesk.requality.vscode.ui.swt.widgets.Display.getDefault();
      com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF[] created = new com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF[1];
      display.syncExec(() -> created[0] = createNavigator());
      cnf = created[0];
    }
    return cnf;
  }

  private static synchronized com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF createNavigator() {
    if (navigator == null) {
      com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF cnf = new com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF();
      navigator = cnf;
      // the Eclipse view: the part's tree in a shell that the VS Code view of the explorer hosts (as the other views of
      // the server); else the viewer whose tree is the VS Code tree
      com.unitesk.requality.vscode.ui.swt.widgets.Shell shell = new com.unitesk.requality.vscode.ui.swt.widgets.Shell(
          com.unitesk.requality.vscode.ui.swt.SWT.NONE);
      if (rendered) {
        shell.setHost("view", RENDERED_VIEW);
        shell.setLayout(new com.unitesk.requality.vscode.ui.swt.layout.FillLayout());
        ExplorerViewer.createRendered(cnf, shell);
      } else
        ExplorerViewer.create(cnf);
      com.unitesk.requality.vscode.ui.workbench.PartSite site =
          new com.unitesk.requality.vscode.ui.workbench.PartSite(com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF.ID, cnf);
      if (rendered)
        site.setShell(shell);
      // CommonNavigatorManager: on a selection change the action providers fill the view's action bars.
      site.setActionBarsUpdater(() -> cnf.getNavigatorActionService().fillActionBars(site.getActionBars()));
      try {
        cnf.init(site, ExplorerViewState.load());
        cnf.setNavigatorActionService(actionService(cnf));
      } catch (com.unitesk.requality.vscode.ui.workbench.PartInitException e) {
        com.unitesk.requality.core.log.RLogger.logError(e);
      }
      cnf.createPartControl(rendered ? shell : new com.unitesk.requality.vscode.ui.swt.widgets.Shell());
      if (rendered)
        renderView(cnf, shell);
    }
    return navigator;
  }

  /**
   * The Eclipse view of the explorer: the context menu of its tree (CommonNavigator.initContextMenu: the popup the
   * action providers, the plugin.xml menu contributions and the object contributions fill each time it shows), the
   * drag and drop of its tree (CommonViewer.initDragAndDrop: the drop assistants of the navigator), its selection sent
   * to the extension (the selection of the commands VS Code runs), then its shell shown by the VS Code view.
   */
  private static void renderView(com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF cnf,
      com.unitesk.requality.vscode.ui.swt.widgets.Shell shell) {
    ExplorerViewer viewer = (ExplorerViewer) cnf.getCommonViewer();
    com.unitesk.requality.vscode.ui.swt.widgets.Tree tree = viewer.getTree();
    com.unitesk.requality.vscode.ui.jface.action.MenuManager popup = new com.unitesk.requality.vscode.ui.jface.action.MenuManager();
    popup.setRemoveAllWhenShown(true);
    popup.addMenuListener(MenuService::fillExplorerPopup);
    tree.setMenu(popup.createContextMenu(tree));

    com.unitesk.requality.vscode.ui.swt.dnd.DragSource drag = new com.unitesk.requality.vscode.ui.swt.dnd.DragSource(tree,
        com.unitesk.requality.vscode.ui.swt.dnd.DND.DROP_MOVE | com.unitesk.requality.vscode.ui.swt.dnd.DND.DROP_COPY);
    drag.setTransfer(com.unitesk.requality.vscode.ui.jface.util.LocalSelectionTransfer.getTransfer());
    drag.addDragListener(new com.unitesk.requality.vscode.ui.swt.dnd.DragSourceAdapter() {
      @Override
      public void dragStart(com.unitesk.requality.vscode.ui.swt.dnd.DragSourceEvent event) {
        com.unitesk.requality.vscode.ui.jface.viewers.ISelection sel = viewer.getSelection();
        event.doit = !sel.isEmpty();
        com.unitesk.requality.vscode.ui.jface.util.LocalSelectionTransfer.getTransfer().setSelection(sel);
      }

      @Override
      public void dragSetData(com.unitesk.requality.vscode.ui.swt.dnd.DragSourceEvent event) {
        event.data = viewer.getSelection();
      }
    });
    com.unitesk.requality.vscode.ui.swt.dnd.DropTarget drop = new com.unitesk.requality.vscode.ui.swt.dnd.DropTarget(tree,
        com.unitesk.requality.vscode.ui.swt.dnd.DND.DROP_MOVE | com.unitesk.requality.vscode.ui.swt.dnd.DND.DROP_COPY);
    drop.setTransfer(com.unitesk.requality.vscode.ui.jface.util.LocalSelectionTransfer.getTransfer(),
        com.unitesk.requality.vscode.ui.swt.dnd.FileTransfer.getInstance());
    drop.addDropListener(new com.unitesk.requality.vscode.ui.swt.dnd.DropTargetAdapter() {
      @Override
      public void drop(com.unitesk.requality.vscode.ui.swt.dnd.DropTargetEvent event) {
        Object target = event.item != null ? event.item.getData() : null;
        if (target == null)
          return;
        if (event.data instanceof String[]) {
          // files dragged from VS Code: copied into the folder of the target (as explorer/dropFiles)
          dropFiles(target, (String[]) event.data);
          return;
        }
        com.unitesk.requality.vscode.ui.jface.viewers.ISelection sel = event.data instanceof com.unitesk.requality.vscode.ui.jface.viewers.ISelection
            ? (com.unitesk.requality.vscode.ui.jface.viewers.ISelection) event.data
            : com.unitesk.requality.vscode.ui.jface.util.LocalSelectionTransfer.getTransfer().getSelection();
        if (!(sel instanceof com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection) || sel.isEmpty())
          return;
        List<Object> sources = new ArrayList<>(((com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection) sel).toList());
        boolean nodes = sources.stream().allMatch(o -> o instanceof TreeNode);
        // RequalityCNF: Shift inserts after the target, Ctrl reuses the dropped nodes (its key listener of the tree);
        // resources are copied with Ctrl, else moved
        boolean shift = (event.stateMask & com.unitesk.requality.vscode.ui.swt.SWT.SHIFT) != 0;
        boolean ctrl = (event.stateMask & com.unitesk.requality.vscode.ui.swt.SWT.CTRL) != 0;
        int operation = !nodes && ctrl ? com.unitesk.requality.vscode.ui.swt.dnd.DND.DROP_COPY
            : com.unitesk.requality.vscode.ui.navigator.NavigatorDnDService.defaultOperation();
        boolean oldShift = com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF.shiftPressed;
        boolean oldAlt = com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF.altPressed;
        com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF.shiftPressed = nodes && shift;
        com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF.altPressed = nodes && ctrl;
        try {
          com.unitesk.requality.vscode.ui.runtime.IStatus st = new com.unitesk.requality.vscode.ui.navigator.NavigatorDnDService(
              com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF.ID).performDrop(treeSelection(sources), target, operation);
          if (st != null && st.getSeverity() == com.unitesk.requality.vscode.ui.runtime.IStatus.ERROR && !st.isMultiStatus())
            com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog.openError(shell, "Drop", st.getMessage());
        } finally {
          com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF.shiftPressed = oldShift;
          com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF.altPressed = oldAlt;
        }
      }
    });

    viewer.addSelectionChangedListener(event -> {
      ExplorerService e = instance;
      if (e == null)
        return;
      JsonArray items = new JsonArray();
      JsonArray nodeIds = new JsonArray();
      if (event.getSelection() instanceof com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection)
        for (Object o : ((com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection) event.getSelection()).toList()) {
          String id = idOf(o);
          if (id != null)
            items.add(id);
          if (o instanceof TreeNode)
            nodeIds.add(Nodes.id((TreeNode) o));
          else if (id != null && id.startsWith(PROJECT_PREFIX)) {
            TreeDB db = Nodes.findDB(id.substring(PROJECT_PREFIX.length()));
            if (db != null && db.getRootNode() != null)
              nodeIds.add(Nodes.id(db.getRootNode()));
          }
        }
      JsonObject p = new JsonObject();
      p.add("items", items);
      p.add("nodes", nodeIds);
      e.server.client().notify("explorer/selection", p);
    });
    shell.open();
  }

  /** Files dropped on an element of the explorer are copied into its folder (the project, the folder of a file). */
  static void dropFiles(Object target, String[] paths) {
    String id = idOf(target);
    ExplorerService e = instance;
    if (id == null || e == null)
      return;
    java.io.File dir;
    if (id.startsWith(PROJECT_PREFIX)) {
      Workspace.Project wp = e.server.workspace().anyProject(id.substring(PROJECT_PREFIX.length()));
      dir = wp == null || wp.closed ? null : wp.dir;
    } else if (id.startsWith(RESOURCE_PREFIX)) {
      java.io.File f = new java.io.File(id.substring(RESOURCE_PREFIX.length()));
      dir = f.isDirectory() ? f : f.getParentFile();
    } else
      dir = null;
    if (dir == null)
      return;
    com.unitesk.requality.vscode.ui.resources.IContainer c = com.unitesk.requality.vscode.ui.resources.ResourcesPlugin.getWorkspace()
        .getRoot().getContainerForLocation(com.unitesk.requality.vscode.ui.runtime.Path.fromOSString(dir.getAbsolutePath()));
    if (c != null)
      new com.unitesk.requality.vscode.ui.workbench.actions.CopyFilesAndFoldersOperation(
          com.unitesk.requality.vscode.ui.workbench.PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell())
              .copyFiles(paths, c);
  }

  /**
   * CommonNavigatorManager of the explorer: the action providers (plugin.xml viewerActionBinding) work on the explorer
   * viewer and its content service; the saved state of the view is restored into them.
   */
  private static com.unitesk.requality.vscode.ui.navigator.NavigatorActionService actionService(
      com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF cnf) {
    com.unitesk.requality.vscode.ui.jface.viewers.ISelectionProvider provider = cnf.getCommonViewer();
    com.unitesk.requality.vscode.ui.navigator.NavigatorActionService service = new com.unitesk.requality.vscode.ui.navigator.NavigatorActionService(
        new com.unitesk.requality.vscode.ui.navigator.ICommonViewerSite() {
          @Override
          public String getId() {
            return com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF.ID;
          }

          @Override
          public com.unitesk.requality.vscode.ui.swt.widgets.Shell getShell() {
            com.unitesk.requality.vscode.ui.swt.widgets.Shell s = com.unitesk.requality.vscode.ui.workbench.PlatformUI.getWorkbench()
                .getActiveWorkbenchWindow().getShell();
            // VSCODE: before the workbench window has a shell, the shell of the explorer's viewer
            return s != null ? s : cnf.getCommonViewer().getControl().getShell();
          }

          @Override
          public com.unitesk.requality.vscode.ui.jface.viewers.ISelectionProvider getSelectionProvider() {
            return provider;
          }

          @Override
          public void setSelectionProvider(com.unitesk.requality.vscode.ui.jface.viewers.ISelectionProvider p) {
          }
        }, cnf.getCommonViewer(), cnf.getCommonViewer().getNavigatorContentService());
    // CommonNavigatorManager.restoreState: the action providers get the saved state of the view
    service.restoreState(ExplorerViewState.load());
    return service;
  }

  /** CommonNavigator open (double click / Enter): the global OPEN action of the explorer's action providers runs. */
  static boolean openSelection() {
    com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF cnf = navigator();
    cnf.getNavigatorActionService().fillActionBars(cnf.getViewSite().getActionBars());
    com.unitesk.requality.vscode.ui.jface.action.IAction open = cnf.getViewSite().getActionBars()
        .getGlobalActionHandler(com.unitesk.requality.vscode.ui.navigator.ICommonActionConstants.OPEN);
    if (open == null || !open.isEnabled())
      return false;
    open.run();
    return true;
  }

  /** Saves the state of the explorer view (the memento of its action providers). */
  public static void saveViewState() {
    ExplorerViewState.save(navigator().getNavigatorActionService()::saveState);
  }

  /** The "Link with Editor" toggle of the VS Code explorer (explorer/setLinked). */
  public static void setLinked(boolean value) {
    navigator().setLinkingEnabled(value);
    com.unitesk.requality.tool.Const.NO_EXPLORER_EDITOR_LINK = !value;
  }

  /** The explorer selection objects of the SelectionManager (projects and nodes). */
  static List<Object> selectedObjects() {
    com.unitesk.requality.core.selection.SelectionRecord sr = com.unitesk.requality.core.selection.SelectionManager.getOne()
        .getSelection(com.unitesk.requality.core.selection.EventSource.EXPLORER);
    List<Object> r = new ArrayList<>();
    if (sr == null)
      return r;
    if (sr.fullSelectionObjects != null)
      r.addAll(sr.fullSelectionObjects);
    if (sr.fullSelection != null)
      for (TreeNode n : sr.fullSelection)
        if (!r.contains(n))
          r.add(n);
    return r;
  }

  /** The items of the workspace projects (the children of the workspace root). */
  static JsonArray projectItems() {
    ExplorerService e = instance;
    return e == null ? new JsonArray() : e.projectList();
  }

  /** The items of the children of an item. */
  static JsonArray childItems(String id) {
    ExplorerService e = instance;
    return e == null || id == null ? new JsonArray() : e.children(id);
  }

  /** The item of an explorer element, or null. */
  static JsonObject itemOf(Object element) {
    ExplorerService e = instance;
    String id = idOf(element);
    return e == null || id == null ? null : e.itemFor(id);
  }

  /** Whether an element of the explorer has children (the workspace root always). */
  static boolean hasChildren(Object element) {
    if (element instanceof com.unitesk.requality.vscode.ui.resources.IWorkspaceRoot)
      return true;
    JsonObject item = itemOf(element);
    return item != null && item.has("hasChildren") && item.get("hasChildren").getAsBoolean();
  }

  /**
   * The explorer element of an item id: a project ({@code IProject}), a file or folder of a project ({@code IFile},
   * {@code IFolder}) or a node; null when it does not exist.
   */
  public static Object element(String id) {
    if (id == null)
      return null;
    if (id.startsWith(RESOURCE_PREFIX)) {
      java.io.File f = new java.io.File(id.substring(RESOURCE_PREFIX.length()));
      com.unitesk.requality.vscode.ui.runtime.IPath loc = com.unitesk.requality.vscode.ui.runtime.Path.fromOSString(f.getAbsolutePath());
      com.unitesk.requality.vscode.ui.resources.IWorkspaceRoot root = com.unitesk.requality.vscode.ui.resources.ResourcesPlugin
          .getWorkspace().getRoot();
      return f.isDirectory() ? root.getContainerForLocation(loc) : root.getFileForLocation(loc);
    }
    if (id.startsWith(PROJECT_PREFIX)) {
      String lname = id.substring(PROJECT_PREFIX.length());
      RequalityServer srv = server;
      Workspace.Project wp = srv == null ? null : srv.workspace().anyProject(lname);
      if (wp == null)
        return null;
      TreeDB db = Nodes.findDB(lname);
      return db != null ? new com.unitesk.requality.vscode.ui.resources.ProjectRef(db, wp.dir)
          : new com.unitesk.requality.vscode.ui.resources.ProjectRef(lname, wp.dir);
    }
    return Nodes.find(id);
  }

  /** The item id of an explorer element; null for the workspace root and for other objects. */
  public static String idOf(Object element) {
    if (element instanceof com.unitesk.requality.core.model.ITreeNodeProvider && !(element instanceof TreeNode))
      element = ((com.unitesk.requality.core.model.ITreeNodeProvider) element).getNode();
    if (element instanceof com.unitesk.requality.vscode.ui.resources.IProject)
      return PROJECT_PREFIX + com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker.getLocalName(
          (com.unitesk.requality.vscode.ui.resources.IProject) element);
    if (element instanceof com.unitesk.requality.vscode.ui.resources.IResource) {
      if (element instanceof com.unitesk.requality.vscode.ui.resources.IWorkspaceRoot)
        return null;
      com.unitesk.requality.vscode.ui.runtime.IPath loc = ((com.unitesk.requality.vscode.ui.resources.IResource) element).getLocation();
      return loc == null ? null : RESOURCE_PREFIX + loc.toFile().getAbsolutePath();
    }
    if (element instanceof TreeNode) {
      TreeNode n = (TreeNode) element;
      return n.isRoot() ? PROJECT_PREFIX + n.getTreeDB().getSemiLocalName() : Nodes.id(n);
    }
    return null;
  }

  /** The parent of an explorer element (ITreeContentProvider.getParent): projects are children of the workspace root. */
  static Object parentElement(Object element) {
    if (element == null || element instanceof com.unitesk.requality.vscode.ui.resources.IWorkspaceRoot)
      return null;
    if (element instanceof com.unitesk.requality.vscode.ui.resources.IProject)
      return com.unitesk.requality.vscode.ui.resources.ResourcesPlugin.getWorkspace().getRoot();
    if (element instanceof com.unitesk.requality.vscode.ui.resources.IResource)
      return ((com.unitesk.requality.vscode.ui.resources.IResource) element).getParent();
    if (element instanceof TreeNode) {
      TreeNode n = (TreeNode) element;
      if (n.isRoot())
        return com.unitesk.requality.vscode.ui.resources.ResourcesPlugin.getWorkspace().getRoot();
      return element(parentId(n));
    }
    return null;
  }

  /** Refreshes the item of an element (its label and children). */
  static void refreshElement(Object element) {
    ExplorerService e = instance;
    String id = idOf(element);
    if (e == null || id == null)
      return;
    e.pendingRefresh.add(id);
    e.scheduleRefresh();
  }

  /** Reveals an item of the VS Code tree; {@code select} also selects it, {@code expand} expands it. */
  static void revealItem(String id, boolean select, boolean expand) {
    ExplorerService e = instance;
    if (e == null || id == null)
      return;
    if (rendered) {
      Object element = element(id);
      if (element != null)
        revealInTree(element, select, expand);
      return;
    }
    JsonObject p = new JsonObject();
    p.addProperty("node", id);
    p.addProperty("select", select);
    p.addProperty("expand", expand);
    p.addProperty("focus", false);
    e.server.client().notify("explorer/reveal", p);
  }

  /**
   * The state of the frames: whether Back, Forward and Up are enabled (the view toolbar shows them while one is),
   * their tool tips, and the title tool tip of the view (the label of the input the explorer went into).
   */
  static JsonObject frames() {
    ExplorerViewer v = viewer();
    JsonObject o = new JsonObject();
    o.addProperty("back", v.getBackAction().isEnabled());
    o.addProperty("forward", v.getForwardAction().isEnabled());
    o.addProperty("up", v.getUpAction().isEnabled());
    o.addProperty("backToolTip", v.getBackAction().getToolTipText());
    o.addProperty("forwardToolTip", v.getForwardAction().getToolTipText());
    o.addProperty("upToolTip", v.getUpAction().getToolTipText());
    Object input = v.getInput();
    o.addProperty("titleToolTip", input == null || input instanceof com.unitesk.requality.vscode.ui.resources.IWorkspaceRoot ? ""
        : v.getCommonNavigator().getTitleToolTip());
    return o;
  }

  /** The frames changed (CommonNavigator.updateTitle): the VS Code view shows the new state. */
  static void framesChanged() {
    ExplorerService e = instance;
    if (e != null && e.server != null)
      e.server.client().notify("explorer/frames", frames());
  }

  /** Item of a file or folder of a project (IResource of the resource content). */
  /** The label provider of the resources of the "non Requality project provider" content (plugin.xml). */
  private static final com.unitesk.requality.vscode.ui.navigator.resources.workbench.ResourceExtensionLabelProvider RESOURCE_LABELS = new com.unitesk.requality.vscode.ui.navigator.resources.workbench.ResourceExtensionLabelProvider();

  static JsonObject resourceItem(java.io.File f) {
    JsonObject o = new JsonObject();
    o.addProperty("id", RESOURCE_PREFIX + f.getAbsolutePath());
    o.addProperty("kind", "resource");
    Object r = element(RESOURCE_PREFIX + f.getAbsolutePath());
    // DecoratingLabelProvider: the lightweight decorators of the element (overlays, prefix and suffix of the label)
    com.unitesk.requality.vscode.ui.jface.viewers.Decoration d = r == null
        ? new com.unitesk.requality.vscode.ui.jface.viewers.Decoration() : decoration(r);
    String text = r == null ? f.getName() : RESOURCE_LABELS.getText(r);
    o.addProperty("label", d.getPrefix() + text + d.getSuffix());
    // the description of the element (Eclipse status line) is the tool tip of the item
    String description = r == null ? null : RESOURCE_LABELS.getDescription(r);
    if (description != null)
      o.addProperty("tooltip", description);
    boolean dir = f.isDirectory();
    // without overlays the file icon theme of VS Code; with them the PNG of the shared image of the file or folder
    o.addProperty("icon", d.getOverlays().isEmpty() ? (dir ? "folder" : "file") : composeIcon(sharedIcon(dir), d));
    o.addProperty("hasChildren", dir && !members(f).isEmpty());
    o.addProperty("contextValue", "requality.resource " + (dir ? "is.IFolder" : "is.IFile"));
    o.addProperty("path", f.getAbsolutePath());
    return o;
  }

  /** The path of the workbench image of files or folders (the image of an undecorated resource in the tree). */
  static String sharedIconPath(boolean dir) {
    return sharedIcon(dir);
  }

  /** The workbench shared image of a file or folder (IMG_OBJ_FILE / IMG_OBJ_FOLDER): the base of a decorated icon. */
  private static String sharedIcon(boolean dir) {
    com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor d = com.unitesk.requality.vscode.ui.workbench.PlatformUI
        .getWorkbench().getSharedImages().getImageDescriptor(dir
            ? com.unitesk.requality.vscode.ui.workbench.ISharedImages.IMG_OBJ_FOLDER
            : com.unitesk.requality.vscode.ui.workbench.ISharedImages.IMG_OBJ_FILE);
    return d == null || d.getPath() == null ? Icons.DEFAULT_ICON : d.getPath();
  }

  /** The explorer parent of a file or folder: its folder, or its project. */
  String resourceParentId(java.io.File f) {
    java.io.File parent = f.getAbsoluteFile().getParentFile();
    if (parent == null)
      return null;
    List<Workspace.Project> ps = new ArrayList<>(server.workspace().projects());
    ps.addAll(server.workspace().otherProjects());
    for (Workspace.Project p : ps)
      if (p.dir.getAbsoluteFile().equals(parent))
        return PROJECT_PREFIX + p.localName;
    return RESOURCE_PREFIX + parent.getAbsolutePath();
  }

  /** {@code RequalityContentProvider.hasChildren}. */
  static boolean hasChildren(TreeNode node) {
    List<UUID> chi = node.getWorkChildrenUUIDs();
    if (chi.isEmpty())
      return false;
    TreeDB db = node.getTreeDB();
    int x = chi.size();
    for (UUID u : chi) {
      try {
        TreeNode n = db.getCachedNodeOrVirt(u);
        if (n != null && !passesStaticFilter(n))
          x--;
      } catch (RuntimeException e) {
        x--;
      }
    }
    return x > 0;
  }

  static String parentId(TreeNode node) {
    if (node.getTreeDB().getRootNodeUUID().equals(node.getWorkParentUUID()))
      return PROJECT_PREFIX + node.getTreeDB().getSemiLocalName();
    try {
      TreeNode p = node.getShownParentExc();
      return p == null ? null : Nodes.id(p);
    } catch (NodeNotFoundException e) {
      return null;
    }
  }

  /** Item for a node; also used by other views that show nodes. */
  public static JsonObject item(TreeNode node) {
    JsonObject o = new JsonObject();
    o.addProperty("id", Nodes.id(node));
    o.addProperty("kind", "node");
    o.addProperty("type", node.getType());
    // WorkbenchLabelProvider decorated by the lightweight decorators (DecoratingLabelProvider of the navigator).
    com.unitesk.requality.vscode.ui.jface.viewers.Decoration d = decoration(node);
    o.addProperty("label", d.getPrefix() + label(node) + d.getSuffix());
    o.addProperty("icon", icon(node, d));
    JsonObject colors = colors(d);
    if (colors != null)
      o.add("decoration", colors);
    o.addProperty("hasChildren", hasChildren(node));
    o.addProperty("contextValue", contextValue(node));
    o.addProperty("qid", node.getQualifiedId());
    return o;
  }

  /** {@code RequalityWorkbenchAdapter.getLabel}. */
  /** IWorkbenchAdapter.getLabel of the node (RequalityWorkbenchAdapter), undecorated. */
  public static String label(TreeNode node) {
    com.unitesk.requality.vscode.ui.workbench.model.IWorkbenchAdapter a = workbenchAdapter(node);
    if (a != null)
      return a.getLabel(node);
    if (node.isRoot())
      return node.getTreeDB().getProjectName();
    return node.toString();
  }

  /** {@code RequalityWorkbenchAdapter.getImageDescriptor} plus decorators. */
  /** The lightweight decorations of an element (empty when they cannot be computed). */
  static com.unitesk.requality.vscode.ui.jface.viewers.Decoration decoration(Object element) {
    try {
      return com.unitesk.requality.vscode.ui.workbench.LightweightDecorators.decorate(element);
    } catch (RuntimeException e) {
      com.unitesk.requality.core.log.RLogger.logError(e);
      return new com.unitesk.requality.vscode.ui.jface.viewers.Decoration();
    }
  }

  /** The decorated icon of a node (its workbench adapter image with the decorators' overlays). */
  public static String icon(TreeNode node) {
    return icon(node, decoration(node));
  }

  static String icon(Object element, com.unitesk.requality.vscode.ui.jface.viewers.Decoration d) {
    return composeIcon(baseImage(element), d);
  }

  /**
   * The PNG of a base image (absolute or resource-relative path) with the overlays of the decorations in their quadrants
   * (an overlay of the REPLACE quadrant replaces the base image).
   */
  static String composeIcon(String base, com.unitesk.requality.vscode.ui.jface.viewers.Decoration d) {
    List<Overlay> overlays = new ArrayList<>();
    for (com.unitesk.requality.vscode.ui.jface.viewers.Decoration.Overlay o : d.getOverlays()) {
      if (o.image == null || o.image.getPath() == null)
        continue;
      if (o.quadrant == com.unitesk.requality.vscode.ui.jface.viewers.IDecoration.REPLACE) {
        base = o.image.getPath();
        continue;
      }
      Quadrant q;
      switch (o.quadrant) {
        case com.unitesk.requality.vscode.ui.jface.viewers.IDecoration.TOP_LEFT:
          q = Quadrant.TOP_LEFT;
          break;
        case com.unitesk.requality.vscode.ui.jface.viewers.IDecoration.TOP_RIGHT:
          q = Quadrant.TOP_RIGHT;
          break;
        case com.unitesk.requality.vscode.ui.jface.viewers.IDecoration.BOTTOM_LEFT:
          q = Quadrant.BOTTOM_LEFT;
          break;
        case com.unitesk.requality.vscode.ui.jface.viewers.IDecoration.UNDERLAY:
          q = Quadrant.UNDERLAY;
          break;
        default:
          q = Quadrant.BOTTOM_RIGHT;
      }
      overlays.add(new Overlay(o.image.getPath(), q));
    }
    return Icons.compose(base, overlays);
  }

  /** IWorkbenchAdapter.getImageDescriptor of the element (RequalityWorkbenchAdapter for nodes). */
  private static String baseImage(Object element) {
    com.unitesk.requality.vscode.ui.workbench.model.IWorkbenchAdapter a = workbenchAdapter(element);
    com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor desc = a == null ? null : a.getImageDescriptor(element);
    if (desc != null && desc.getPath() != null)
      return desc.getPath();
    if (element instanceof TreeNode)
      return Icons.nodeTypeIcon(((TreeNode) element).getType());
    return Icons.DEFAULT_ICON;
  }

  private static com.unitesk.requality.vscode.ui.workbench.model.IWorkbenchAdapter workbenchAdapter(Object element) {
    return (com.unitesk.requality.vscode.ui.workbench.model.IWorkbenchAdapter) com.unitesk.requality.vscode.ui.runtime.Platform
        .getAdapterManager().getAdapter(element, com.unitesk.requality.vscode.ui.workbench.model.IWorkbenchAdapter.class);
  }

  /** Foreground/background colors and font style set by decorators, for the VS Code tree. */
  static JsonObject colors(com.unitesk.requality.vscode.ui.jface.viewers.Decoration d) {
    JsonObject o = new JsonObject();
    if (d.getForegroundColor() != null)
      o.addProperty("foreground", hex(d.getForegroundColor().getRGB()));
    if (d.getBackgroundColor() != null)
      o.addProperty("background", hex(d.getBackgroundColor().getRGB()));
    if (d.getFont() != null) {
      o.addProperty("italic", d.getFont().isItalic());
      o.addProperty("bold", d.getFont().isBold());
    }
    return o.size() == 0 ? null : o;
  }

  private static String hex(com.unitesk.requality.vscode.ui.swt.graphics.RGB rgb) {
    return String.format("#%02x%02x%02x", rgb.red, rgb.green, rgb.blue);
  }

  /** {@code instanceof} tags for menu {@code when} clauses, plus state flags. */
  public static String contextValue(TreeNode node) {
    StringBuilder sb = new StringBuilder("requality.node");
    for (Class<?> c = node.getClass(); c != null && TreeNode.class.isAssignableFrom(c); c = c.getSuperclass())
      sb.append(" is.").append(c.getSimpleName());
    sb.append(" type.").append(node.getType());
    if (node.isVirtual())
      sb.append(" virtual");
    if (node.isRoot())
      sb.append(" root");
    List<String> extra = new ArrayList<>();
    for (ContextTagProvider p : TAG_PROVIDERS) {
      try {
        p.tags(node, extra);
      } catch (RuntimeException e) {
        // a failing provider only hides its menu entries
      }
    }
    for (String t : extra)
      sb.append(' ').append(t);
    return sb.toString();
  }

  // ---------------------------------------------------------------- change tracking

  private void listen(TreeDB db) {
    if (db == null || listeners.containsKey(db))
      return;
    ITreeChangeListener l = new ChangeListener(db);
    listeners.put(db, l);
    sortings.put(db, db.getSortingSettings());
    db.addGUIListener(l);
  }

  private void unlisten(TreeDB db) {
    if (db == null)
      return;
    ITreeChangeListener l = listeners.remove(db);
    sortings.remove(db);
    if (l != null)
      db.removeGUIListener(l);
  }

  private static volatile ExplorerService instance;

  /** Refreshes the explorer item of a node (its label, icon and children). */
  public static void refreshNode(TreeNode node) {
    ExplorerService e = instance;
    if (e != null)
      e.refresh(node);
  }

  /** Refreshes the whole explorer (e.g. after a decorator change). */
  public static void refreshAllNodes() {
    ExplorerService e = instance;
    if (e != null)
      e.refreshAll();
  }

  /** Reveals a node in the explorer; {@code select} also selects it (CommonViewer.setSelection with reveal). */
  public static void reveal(TreeNode node, boolean select, boolean expand, boolean focus) {
    ExplorerService e = instance;
    if (e == null || node == null)
      return;
    if (rendered) {
      revealInTree(node.isRoot() ? element(PROJECT_PREFIX + node.getTreeDB().getSemiLocalName()) : node, select, expand);
      return;
    }
    JsonObject p = new JsonObject();
    p.addProperty("node", node.isRoot() ? PROJECT_PREFIX + node.getTreeDB().getSemiLocalName() : Nodes.id(node));
    p.addProperty("select", select);
    p.addProperty("expand", expand);
    p.addProperty("focus", focus);
    e.server.client().notify("explorer/reveal", p);
  }

  /** Reveals an element in the tree of the Eclipse view (selects and expands it as asked). */
  private static void revealInTree(Object element, boolean select, boolean expand) {
    if (element == null || navigator == null)
      return;
    com.unitesk.requality.vscode.ui.swt.widgets.Display.getDefault().asyncExec(() -> {
      ExplorerViewer v = (ExplorerViewer) navigator.getCommonViewer();
      if (select)
        v.setSelection(new com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection(element), true);
      else
        v.reveal(element);
      if (expand)
        v.setExpandedState(element, true);
    });
  }

  private void refreshAll() {
    pendingRefresh.clear();
    pendingRefresh.add("*");
    scheduleRefresh();
  }

  private void refresh(TreeNode node) {
    if (node == null)
      return;
    if (node.isRoot())
      pendingRefresh.add(PROJECT_PREFIX + node.getTreeDB().getSemiLocalName());
    else
      pendingRefresh.add(Nodes.id(node));
    scheduleRefresh();
  }

  /** TochHandler: refreshes are batched (25 ms in Eclipse). */
  private synchronized void scheduleRefresh() {
    if (refreshScheduled)
      return;
    refreshScheduled = true;
    timer.schedule(() -> {
      List<String> ids;
      synchronized (this) {
        refreshScheduled = false;
      }
      synchronized (pendingRefresh) {
        ids = new ArrayList<>(pendingRefresh);
        pendingRefresh.clear();
      }
      if (ids.isEmpty())
        return;
      JsonObject p = new JsonObject();
      if (ids.contains("*"))
        p.add("ids", null);
      else
        p.add("ids", com.unitesk.requality.vscode.rpc.JsonRpcConnection.toJson(ids));
      server.client().notify("explorer/refresh", p);
      if (rendered && navigator != null) {
        // the tree of the Eclipse view shows the changed items again
        List<Object> elements = new ArrayList<>();
        boolean all = ids.contains("*");
        if (!all)
          for (String id : ids) {
            Object o = element(id);
            if (o != null)
              elements.add(o);
          }
        com.unitesk.requality.vscode.ui.swt.widgets.Display.getDefault().asyncExec(() -> {
          ExplorerViewer v = (ExplorerViewer) navigator.getCommonViewer();
          if (all || elements.isEmpty())
            v.refreshTree(null);
          else
            for (Object o : elements)
              v.refreshTree(o);
        });
      }
    }, 25, TimeUnit.MILLISECONDS);
  }

  /** {@code RequalityContentProvider.treeChangeListener}. */
  private final class ChangeListener extends DefaultTreeChangeListener {
    private final TreeDB db;

    ChangeListener(TreeDB db) {
      this.db = db;
    }

    private boolean rootActions(TreeNode node) {
      if (node.getUUId().equals(node.getTreeDB().getRootNode().getUUId())) {
        SortingSettings now = node.getTreeDB().getSortingSettings();
        if (!now.equals(sortings.get(db))) {
          sortings.put(db, now);
          refreshAll();
          return true;
        }
      }
      return false;
    }

    @Override
    public void moved(TreeNode node, TreeNode oldParent, TreeNode newParent) {
      refresh(node);
    }

    @Override
    public void deleted(TreeNode node) {
      if (node == null)
        return;
      try {
        refresh(node.getShownParentExc());
      } catch (NodeNotFoundException e) {
        // late delete
      }
    }

    @Override
    public void changed(TreeNode node, ChangesSet<Attribute> what) {
      if (rootActions(node))
        return;
      for (String w : what.getAll())
        if (w.equals(TreeNode.ATTR_LABEL) || isShown(w) || w.equals(TreeNode.ATTR_SHOWN_CHILDREN)) {
          refresh(node);
          return;
        }
    }

    @Override
    public void created(TreeNode node) {
      refresh(node.getShownParent());
    }

    @Override
    public void childrenOrderChanged(TreeNode node, byte flag) {
      if (!OFlag.isNoGuiUpdate(flag))
        refresh(node);
    }

    @Override
    public void changed(List<TreeNodeChangeEvent> changes) {
      for (TreeNodeChangeEvent ch : changes) {
        try {
          switch (ch.getType()) {
            case MOVE: {
              if (ch.getMoment() == Moment.BEFORE)
                continue;
              TreeNode node = ch.getTreeNodeTarget();
              refresh(node);
              TreeNode old = (TreeNode) ch.getArguments()[0];
              if (old != null)
                refresh(old);
              if (old == null || !old.equals(node.getShownParentExc()))
                refresh(node.getShownParentExc());
              break;
            }
            case CHILDREN_ORDER_CHANGE:
              refresh(ch.getTreeNodeTarget());
              break;
            case CREATE:
              if (ch.getMoment() == Moment.BEFORE)
                continue;
              refresh(ch.getTreeNodeTarget().getShownParentExc());
              break;
            case DELETE:
              if (ch.getMoment() == Moment.BEFORE)
                continue;
              refresh(ch.getTreeNodeTarget().getShownParentExc());
              break;
            case CHANGE: {
              if (ch.getMoment() == Moment.BEFORE)
                continue;
              TreeNode node = ch.getTreeNodeTarget();
              if (node.isRoot() && rootActions(node))
                continue;
              if (node.isDisposed())
                continue;
              boolean changeIt = false, changeParent = false;
              @SuppressWarnings("unchecked")
              ChangesSet<Attribute> what = (ChangesSet<Attribute>) ch.getArguments()[0];
              for (Map.Entry<String, Attribute> en : what.getNewMap().entrySet()) {
                if (en.getKey().equals(TreeNode.ATTR_LABEL) || isShown(en.getKey()))
                  changeIt = true;
                if (Attribute.affectsPosition(node, en.getKey()))
                  changeParent = true;
                if (changeParent)
                  break;
              }
              if (changeParent)
                refresh(node.getShownParentExc());
              else if (changeIt)
                refresh(node);
              break;
            }
            default:
              break;
          }
        } catch (NodeNotFoundException e) {
          // ignore late events
        }
      }
    }
  }

}
