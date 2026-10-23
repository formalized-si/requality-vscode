package com.unitesk.requality.vscode.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonElement;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.EventSource;
import com.unitesk.requality.core.selection.FSId;
import com.unitesk.requality.core.selection.SelectionManager;
import com.unitesk.requality.core.selection.SelectionManager.SourcePriority;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.vscode.RequalityServer;
import com.unitesk.requality.vscode.core.Nodes;
import com.unitesk.requality.vscode.core.Workspace;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPart;
import com.unitesk.requality.vscode.ui.workbench.Workbench;
import com.unitesk.requality.vscode.ui.resources.ProjectRef;

/**
 * Workbench selection: the VS Code views report their selection here, which updates the core SelectionManager like
 * the Eclipse views do (RequalityCNF.convertToObjects) and drives the Properties view.
 */
public final class SelectionService implements Services.Service {
  /** A VS Code view acting as an Eclipse selection source. */
  public static final class Part implements IWorkbenchPart {
    private final String id;
    private final String title;
    private final FSId fsid;

    public Part(String id, String title, FSId fsid) {
      this.id = id;
      this.title = title;
      this.fsid = fsid;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public String getTitle() {
      return title;
    }

    @Override
    public FSId getFSId() {
      return fsid;
    }
  }

  private static final Map<String, Part> PARTS = new ConcurrentHashMap<>();
  private static RequalityServer server;

  /** The workbench part of a VS Code view: the Requality Explorer, Links Explorer, Outline or an editor. */
  public static IWorkbenchPart part(String source) {
    if ("explorer".equals(source) || source == null)
      return ExplorerService.navigator();
    return PARTS.computeIfAbsent(source, s -> {
      EventSource es;
      String id;
      String title;
      switch (s) {
        case "links":
          es = EventSource.LCNF;
          id = "com.unitesk.requality.views.RequalityLinksExplorer";
          title = "Requality Links Explorer";
          break;
        case "outline":
          es = EventSource.OUTLINE;
          id = "org.eclipse.ui.views.ContentOutline";
          title = "Outline";
          break;
        case "todo":
          es = EventSource.EXPLORER;
          id = "com.unitesk.requality.views.ToDoList";
          title = "Update Processor Tasks";
          break;
        default:
          es = EventSource.EDITOR;
          id = s;
          title = "Editor";
      }
      return new Part(id, title, SelectionManager.getOne().registerNewFSId(es, null, true));
    });
  }

  /** Registers a part (e.g. a ported view) as the part of a VS Code view source. */
  public static void registerPart(String source, Part part) {
    PARTS.put(source, part);
  }

  /** CommonNavigator "Link with Editor": an activated editor selects its input in the explorer. */
  private static final com.unitesk.requality.vscode.ui.workbench.IPartListener LINK = new com.unitesk.requality.vscode.ui.workbench.IPartListener() {
    @Override
    public void partActivated(IWorkbenchPart part) {
      if (!(part instanceof com.unitesk.requality.vscode.ui.workbench.IEditorPart)
          || !com.unitesk.requality.vscode.port.eclipse.editors.EditorUtils.LINKED_PROVIDER.isLinked())
        return;
      com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection s = com.unitesk.requality.vscode.ui.navigator.LinkHelperService
          .findSelection(((com.unitesk.requality.vscode.ui.workbench.IEditorPart) part).getEditorInput());
      if (s != null)
        ExplorerService.navigator().selectReveal(s);
    }

    @Override
    public void partBroughtToTop(IWorkbenchPart part) {
    }

    @Override
    public void partClosed(IWorkbenchPart part) {
    }

    @Override
    public void partDeactivated(IWorkbenchPart part) {
    }

    @Override
    public void partOpened(IWorkbenchPart part) {
    }
  };

  @Override
  public void register(RequalityServer srv) {
    server = srv;
    com.unitesk.requality.vscode.ui.workbench.PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().addPartListener(LINK);
        Workbench.registerExternalView(com.unitesk.requality.vscode.port.eclipse.ui.cnf.RequalityCNF.ID, ExplorerService::navigator);
    srv.rpc().onRequest("selection/set", p -> {
      srv.requireInitialized();
      select(Nodes.optString(p, "source", "explorer"),
          p.has("nodes") && p.get("nodes").isJsonArray() ? p.getAsJsonArray("nodes") : new com.google.gson.JsonArray());
      return null;
    });
  }

  /**
   * Makes {@code ids} (node ids and "project:" items) the selection of the view {@code source}: updates the core
   * SelectionManager, the workbench selection and the Properties view (on the UI thread).
   */
  public static void select(String source, com.google.gson.JsonArray ids) {
    List<Object> objects = new ArrayList<>();
    List<TreeNode> nodes = new ArrayList<>();
    List<Object> elements = new ArrayList<>();
    TreeDB first = null;
    for (JsonElement e : ids) {
      if (e == null || e.isJsonNull())
        continue;
      String id = e.getAsString();
      if (id.startsWith(ExplorerService.RESOURCE_PREFIX) || id.startsWith(ExplorerService.PROJECT_PREFIX)) {
        // a project (IProject), a file or folder of a project (IFile / IFolder)
        Object r = ExplorerService.element(id);
        if (r == null)
          continue;
        objects.add(r);
        elements.add(r);
        if (r instanceof ProjectRef && ((ProjectRef) r).getTreeDB() != null && ((ProjectRef) r).getTreeDB().getRootNode() != null)
          nodes.add(((ProjectRef) r).getTreeDB().getRootNode());
        continue;
      }
      TreeNode n = Nodes.find(id);
      if (n == null)
        continue;
      if (first == null)
        first = n.getTreeDB();
      if (n.getTreeDB() != first)
        continue;
      nodes.add(n);
      elements.add(n);
    }
    IWorkbenchPart part = part(source);
    FSId fsid = part.getFSId();
    StructuredSelection selection = new StructuredSelection(elements);
    boolean explorer = "explorer".equals(source) || source == null;
    Display.getDefault().syncExec(() -> {
      if (explorer) {
        // The selection of the VS Code tree is a selection change of the viewer (JFace): the CommonNavigator's
        // selection listener updates the SelectionManager (RequalityCNF.treeSelection), as for the Eclipse viewer.
        ExplorerService.viewer().fireSelection(selection);
      } else {
        SelectionManager sman = SelectionManager.getOne();
        SelectionRecord sr = new SelectionRecord(objects.toArray(new Object[0]), nodes.toArray(new TreeNode[0]));
        sman.updateSelection(SourcePriority.EDITOR, fsid, sr);
        sman.activateSource(SourcePriority.SOURCE_CHANGE, fsid.getEventSource(), fsid, true, true);
      }
      Workbench.partActivated(part, selection);
      // CommonNavigator "Link with Editor": the explorer selection activates its editor (LinkHelperService).
      if ("explorer".equals(source) && com.unitesk.requality.vscode.port.eclipse.editors.EditorUtils.LINKED_PROVIDER.isLinked())
        Display.getDefault().asyncExec(() -> {
          try {
            com.unitesk.requality.vscode.ui.navigator.LinkHelperService.activateEditor(
                com.unitesk.requality.vscode.ui.workbench.PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(),
                selection);
          } catch (RuntimeException e) {
            com.unitesk.requality.core.log.RLogger.logError(e);
          }
        });
      // The Properties view (PropertySheet) follows the workbench selection fired by partActivated.
    });
  }
}
