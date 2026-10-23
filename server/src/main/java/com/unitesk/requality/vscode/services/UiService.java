package com.unitesk.requality.vscode.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.RequalityServer;
import com.unitesk.requality.vscode.core.Nodes;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.swt.widgets.Widget;

/**
 * Connects the remote SWT {@link Display} to the extension: shells become webviews ({@code ui/openShell},
 * {@code ui/closeShell}), widget operations are streamed ({@code ui/update}) and renderer events come back
 * ({@code ui/event}). A (re)loaded webview asks for the full widget tree ({@code ui/snapshot}).
 */
public final class UiService implements Services.Service {
  @Override
  public void register(RequalityServer server) {
    Display.setTransport(new Display.Transport() {
      @Override
      public void update(String shell, long seq, JsonArray ops) {
        JsonObject p = new JsonObject();
        p.addProperty("shell", shell);
        p.addProperty("seq", seq);
        p.add("ops", ops);
        server.client().notify("ui/update", p);
      }

      @Override
      public void openShell(Shell shell, JsonObject options) {
        JsonObject p = options.deepCopy();
        p.addProperty("shell", shell.getId());
        server.client().notify("ui/openShell", p);
      }

      @Override
      public void closeShell(Shell shell) {
        JsonObject p = new JsonObject();
        p.addProperty("shell", shell.getId());
        server.client().notify("ui/closeShell", p);
      }
    });
    server.rpc().onRequest("ui/snapshot", p -> {
      String shellId = Nodes.string(p, "shell");
      Display d = Display.getDefault();
      return d.syncCall(() -> {
        Widget w = d.find(shellId);
        JsonObject r = new JsonObject();
        if (!(w instanceof Shell) || w.isDisposed()) {
          r.addProperty("closed", true);
          return r;
        }
        JsonObject snap = d.snapshot((Shell) w);
        snap.addProperty("title", ((Shell) w).getText());
        return snap;
      });
    });
    // Diagnostics of the web editors (UniEditor, ModuleEditor...): whether the page of each open editor has
    // connected to the server and initialized (BrowserState READY of its socket service).
    server.rpc().onRequest("ui/webEditors", p -> Display.getDefault().syncCall(() -> {
      com.google.gson.JsonArray r = new com.google.gson.JsonArray();
      for (com.unitesk.requality.vscode.ui.workbench.IEditorPart e : com.unitesk.requality.vscode.ui.workbench.PlatformUI
          .getWorkbench().getActiveWorkbenchWindow().getActivePage().getEditors()) {
        if (!(e instanceof com.unitesk.requality.vscode.port.eclipse.ui.editors.JSEditor))
          continue;
        com.unitesk.requality.core.service.EditorsSocketService s = socketServiceOf(
            (com.unitesk.requality.vscode.port.eclipse.ui.editors.JSEditor) e);
        JsonObject o = new JsonObject();
        o.addProperty("title", e.getTitle());
        o.addProperty("ready", s != null && (s.states.containsValue(com.unitesk.requality.document.BrowserState.READY)
            || !initedSockets(s).isEmpty()));
        com.unitesk.requality.vscode.ui.swt.widgets.Browser b = ((com.unitesk.requality.vscode.port.eclipse.ui.editors.JSEditor) e).getBrowser();
        o.addProperty("loads", b == null ? 0 : b.getLoadCount());
        o.addProperty("url", b == null ? null : b.getUrl());
        r.add(o);
      }
      return r;
    }));
    // A webview of a server-hosted part got the focus: it becomes the active part (Eclipse part activation).
    server.rpc().onNotificationOrdered("ui/shellFocused", p -> Display.getDefault().asyncExec(() -> {
      com.unitesk.requality.vscode.ui.swt.widgets.Widget w = Display.getDefault().find(Nodes.string(p, "shell"));
      if (w instanceof com.unitesk.requality.vscode.ui.swt.widgets.Shell)
        shellActivated((com.unitesk.requality.vscode.ui.swt.widgets.Shell) w);
    }));
    // The VS Code editor group (view column) of the tab of a shell: the E4 model of the editors follows it.
    server.rpc().onNotificationOrdered("ui/shellColumn", p -> Display.getDefault().asyncExec(() -> {
      com.unitesk.requality.vscode.ui.swt.widgets.Widget w = Display.getDefault().find(Nodes.string(p, "shell"));
      if (w instanceof com.unitesk.requality.vscode.ui.swt.widgets.Shell && p.has("column") && !p.get("column").isJsonNull())
        com.unitesk.requality.vscode.ui.workbench.Workbench.shellColumnChanged(
            (com.unitesk.requality.vscode.ui.swt.widgets.Shell) w, p.get("column").getAsInt());
    }));
    server.rpc().onNotificationOrdered("ui/event", p -> {
      String id = Nodes.string(p, "id");
      String type = Nodes.string(p, "type");
      // A press of the mouse on a control of a part activates the part, as the control gets the focus in Eclipse (also
      // when VS Code reported no focus of the webview: its window was not the active window)
      if (ACTIVATING_EVENTS.contains(type))
        Display.getDefault().asyncExec(() -> {
          com.unitesk.requality.vscode.ui.swt.widgets.Widget w = Display.getDefault().find(id);
          if (!(w instanceof com.unitesk.requality.vscode.ui.swt.widgets.Control) || w.isDisposed())
            return;
          com.unitesk.requality.vscode.ui.workbench.IWorkbenchPart before = com.unitesk.requality.vscode.ui.workbench.Workbench.activePart();
          com.unitesk.requality.vscode.ui.swt.widgets.Shell shell = ((com.unitesk.requality.vscode.ui.swt.widgets.Control) w).getShell();
          com.unitesk.requality.vscode.ui.workbench.Workbench.shellActivated(shell);
          if (com.unitesk.requality.vscode.ui.workbench.Workbench.activePart() != before)
            com.unitesk.requality.vscode.ui.compare.CompareLinks.shellActivated(shell);
        });
      Display.getDefault().dispatchClientEvent(id, type,
          p.has("data") && p.get("data").isJsonObject() ? p.getAsJsonObject("data") : new JsonObject());
    });
  }

  /** The client events of a press of the mouse on a control (a click, a double click, the menu, a drag). */
  private static final java.util.Set<String> ACTIVATING_EVENTS = java.util.Set.of("selection", "default", "menudetect",
      "dragstart", "mousedown", "expand", "collapse");

  /** A shell of a part became active (its webview got the focus, or the mouse was pressed in it). */
  private static void shellActivated(com.unitesk.requality.vscode.ui.swt.widgets.Shell shell) {
    if (shell == null)
      return;
    com.unitesk.requality.vscode.ui.workbench.Workbench.shellActivated(shell);
    com.unitesk.requality.vscode.ui.compare.CompareLinks.shellActivated(shell);
  }

  /** The sockets of the pages that initialized their session (GenericSocketService.currentInitedSockets). */
  private static java.util.Collection<?> initedSockets(com.unitesk.requality.core.service.GenericSocketService s) {
    try {
      java.lang.reflect.Field f = com.unitesk.requality.core.service.GenericSocketService.class.getDeclaredField("currentInitedSockets");
      f.setAccessible(true);
      Object v = f.get(s);
      return v instanceof java.util.Collection ? (java.util.Collection<?>) v : java.util.List.of();
    } catch (ReflectiveOperationException | RuntimeException e) {
      return java.util.List.of();
    }
  }

  /** The socket service of a web editor page (the protected field {@code socservice} of JSEditor). */
  static {
    com.unitesk.requality.vscode.ui.workbench.Workbench.addEditorCreatedHook(UiService::passEditMode);
  }

  /**
   * The edit mode of a web editor reaches its page when the page connects to the editor's socket service (the session
   * message of EditorsSocketService), after createPartControl. A part that sets its mode in createPartControl after the
   * service was created (ReviewEditor: read-only Review page) gives the mode to the service here, before the webview of
   * the part connects, so that the page gets the mode the part set.
   */
  static void passEditMode(com.unitesk.requality.vscode.ui.workbench.IEditorPart part) {
    if (!(part instanceof com.unitesk.requality.vscode.port.eclipse.ui.editors.JSEditor))
      return;
    try {
      com.unitesk.requality.core.service.EditorsSocketService s = socketServiceOf(
          (com.unitesk.requality.vscode.port.eclipse.ui.editors.JSEditor) part);
      if (s == null)
        return;
      java.lang.reflect.Field partMode = com.unitesk.requality.vscode.port.eclipse.ui.editors.JSEditor.class.getDeclaredField("editMode");
      partMode.setAccessible(true);
      java.lang.reflect.Field serviceMode = com.unitesk.requality.core.service.EditorsSocketService.class.getDeclaredField("editMode");
      serviceMode.setAccessible(true);
      int mode = partMode.getInt(part);
      if (serviceMode.getInt(s) != mode)
        serviceMode.setInt(s, mode);
    } catch (ReflectiveOperationException e) {
      com.unitesk.requality.core.log.RLogger.logError(e);
    }
  }

  private static com.unitesk.requality.core.service.EditorsSocketService socketServiceOf(
      com.unitesk.requality.vscode.port.eclipse.ui.editors.JSEditor editor) {
    try {
      java.lang.reflect.Field f = com.unitesk.requality.vscode.port.eclipse.ui.editors.JSEditor.class.getDeclaredField("socservice");
      f.setAccessible(true);
      return (com.unitesk.requality.core.service.EditorsSocketService) f.get(editor);
    } catch (ReflectiveOperationException ex) {
      return null;
    }
  }
}
