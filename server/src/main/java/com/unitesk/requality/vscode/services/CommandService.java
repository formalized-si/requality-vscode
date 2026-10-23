package com.unitesk.requality.vscode.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.RequalityServer;
import com.unitesk.requality.vscode.core.Nodes;
import com.unitesk.requality.vscode.rpc.RpcException;
import com.unitesk.requality.vscode.ui.commands.Command;
import com.unitesk.requality.vscode.ui.commands.CommandSupport;
import com.unitesk.requality.vscode.ui.commands.EvaluationContext;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.workbench.ViewToolbarContributions;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.commands.Expressions;
import com.unitesk.requality.vscode.ui.commands.IElementUpdater;
import com.unitesk.requality.vscode.ui.commands.IEvaluationContext;
import com.unitesk.requality.vscode.ui.commands.IHandler;
import com.unitesk.requality.vscode.ui.commands.IHandler2;
import com.unitesk.requality.vscode.ui.commands.IHandlerActivation;
import com.unitesk.requality.vscode.ui.commands.NotDefinedException;
import com.unitesk.requality.vscode.ui.commands.NotEnabledException;
import com.unitesk.requality.vscode.ui.commands.NotHandledException;
import com.unitesk.requality.vscode.ui.commands.UIElement;
import com.unitesk.requality.vscode.ui.commands.operations.IOperationHistory;
import com.unitesk.requality.vscode.ui.commands.operations.IUndoContext;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.workbench.IEditorPart;
import com.unitesk.requality.vscode.ui.workbench.ISources;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPart;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.Workbench;

/**
 * The commands and handlers of plugin.xml ({@code org.eclipse.ui.commands}, {@code org.eclipse.ui.handlers}): the
 * extension executes Eclipse commands by id; the active handler is chosen by its activeWhen expression like the
 * Eclipse handler service, checked with enabledWhen and {@link IHandler#isEnabled()}, and run on the UI thread.
 */
public final class CommandService extends CommandSupport implements Services.Service {
  /** A handler contribution (or a handler activated by code). */
  private static final class HandlerDef implements IHandlerActivation {
    final String commandId;
    final String className;
    final IConfigurationElement activeWhen;
    final IConfigurationElement enabledWhen;
    final Map<String, String> params = new HashMap<>();
    final int priority;
    /** The part whose site activated the handler (active only while the part is active), or null. */
    Object part;
    IHandler handler;
    boolean failed;

    HandlerDef(String commandId, String className, IConfigurationElement activeWhen, IConfigurationElement enabledWhen) {
      this.commandId = commandId;
      this.className = className;
      this.activeWhen = activeWhen;
      this.enabledWhen = enabledWhen;
      this.priority = Expressions.priority(activeWhen);
    }

    HandlerDef(String commandId, IHandler handler) {
      this(commandId, handler.getClass().getName(), null, null);
      this.handler = handler;
    }

    HandlerDef(String commandId, IHandler handler, int priority) {
      this.commandId = commandId;
      this.className = handler.getClass().getName();
      this.activeWhen = null;
      this.enabledWhen = null;
      this.priority = priority;
      this.handler = handler;
    }

    @Override
    public String getCommandId() {
      return commandId;
    }

    @Override
    public IHandler getHandler() {
      return instance();
    }

    synchronized IHandler instance() {
      if (handler == null && !failed) {
        try {
          handler = (IHandler) Class.forName(ExtensionRegistry.portedClassName(className)).getDeclaredConstructor()
              .newInstance();
        } catch (Throwable t) {
          failed = true;
          RLogger.logInfo("Handler " + className + " is not available: " + t);
        }
      }
      return handler;
    }
  }

  public static final String UNDO = "org.eclipse.ui.edit.undo";
  public static final String REDO = "org.eclipse.ui.edit.redo";
  private final Map<String, String> commandNames = new LinkedHashMap<>();
  private final List<HandlerDef> handlers = new CopyOnWriteArrayList<>();
  private final List<HandlerDef> activations = new CopyOnWriteArrayList<>();
  private RequalityServer server;

  @Override
  public void register(RequalityServer srv) {
    server = srv;
    CommandSupport.set(this);
    srv.rpc().onRequest("command/execute", p -> {
      srv.requireInitialized();
      load();
      String id = Nodes.string(p, "command");
      Map<String, String> params = new HashMap<>();
      if (p.has("params") && p.get("params").isJsonObject())
        for (Map.Entry<String, JsonElement> e : p.getAsJsonObject("params").entrySet())
          params.put(e.getKey(), e.getValue().isJsonNull() ? null : e.getValue().getAsString());
      // the selection is set in the same task of the UI thread as the command runs
      Runnable select = p.has("nodes") && p.get("nodes").isJsonArray()
          ? () -> SelectionService.select(Nodes.optString(p, "source", "explorer"), p.getAsJsonArray("nodes"))
          : null;
      // VSCODE: a command run from a view/title button of its view gets the ToolItem of that button as its trigger.
      String toolbarView = Nodes.optString(p, "toolbarView", null);
      Object r = executeOnUi(id, params, null, select, toolbarView);
      return r == null ? null : String.valueOf(r);
    });
    srv.rpc().onRequest("command/state", p -> {
      srv.requireInitialized();
      load();
      List<String> ids = new ArrayList<>();
      if (p.has("commands") && p.get("commands").isJsonArray())
        for (JsonElement e : p.getAsJsonArray("commands"))
          ids.add(e.getAsString());
      else
        ids.addAll(commandNames.keySet());
      return Display.getDefault().syncCall(() -> state(ids));
    });
  }

  private boolean loaded;

  /** Reads the command and handler contributions (once, after the core started and plugin.xml is known). */
  private synchronized void load() {
    if (loaded)
      return;
    loaded = true;
    for (IConfigurationElement c : ExtensionRegistry.get().getConfigurationElementsFor("org.eclipse.ui.commands")) {
      if (!"command".equals(c.getName()))
        continue;
      commandNames.put(c.getAttribute("id"), c.getAttribute("name"));
      String def = c.getAttribute("defaultHandler");
      if (def != null)
        handlers.add(new HandlerDef(c.getAttribute("id"), def, null, null));
    }
    for (IConfigurationElement h : ExtensionRegistry.get().getConfigurationElementsFor("org.eclipse.ui.handlers")) {
      if (!"handler".equals(h.getName()))
        continue;
      IConfigurationElement[] aw = h.getChildren("activeWhen");
      IConfigurationElement[] ew = h.getChildren("enabledWhen");
      HandlerDef d = new HandlerDef(h.getAttribute("commandId"), h.getAttribute("class"), aw.length > 0 ? aw[0] : null,
          ew.length > 0 ? ew[0] : null);
      for (IConfigurationElement p : h.getChildren("parameter"))
        d.params.put(p.getAttribute("id"), p.getAttribute("value"));
      handlers.add(d);
    }
  }

  /** The evaluation context of the current workbench state (ISources variables). */
  @Override
  public IEvaluationContext currentState() {
    Object sel = Workbench.currentSelection();
    EvaluationContext ctx = new EvaluationContext(null, sel);
    ctx.addVariable(ISources.ACTIVE_CURRENT_SELECTION_NAME, sel);
    ctx.addVariable(ISources.ACTIVE_MENU_SELECTION_NAME, sel);
    IWorkbenchPart part = Workbench.activePart();
    ctx.addVariable(ISources.ACTIVE_PART_NAME, part == null ? IEvaluationContext.UNDEFINED_VARIABLE : part);
    ctx.addVariable(ISources.ACTIVE_PART_ID_NAME, part == null ? IEvaluationContext.UNDEFINED_VARIABLE : part.getId());
    IEditorPart editor = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor();
    ctx.addVariable(ISources.ACTIVE_EDITOR_NAME, editor == null ? IEvaluationContext.UNDEFINED_VARIABLE : editor);
    ctx.addVariable(ISources.ACTIVE_EDITOR_ID_NAME, editor == null ? IEvaluationContext.UNDEFINED_VARIABLE
        : editor.getSite() != null ? editor.getSite().getId() : editor.getId());
    Object shell = Display.getDefault().getActiveShell();
    ctx.addVariable(ISources.ACTIVE_SHELL_NAME, shell == null ? IEvaluationContext.UNDEFINED_VARIABLE : shell);
    ctx.addVariable(ISources.ACTIVE_WORKBENCH_WINDOW_NAME, PlatformUI.getWorkbench().getActiveWorkbenchWindow());
    ctx.addVariable(ISources.ACTIVE_WORKBENCH_WINDOW_SHELL_NAME,
        shell == null ? IEvaluationContext.UNDEFINED_VARIABLE : shell);
    return ctx;
  }

  /** Source priority of a global action handler: activated by the part site for its part (ActivePartExpression). */
  private static final int ACTIVE_PART_PRIORITY = 1 << 18;
  private final Map<com.unitesk.requality.vscode.ui.jface.action.IAction, HandlerDef> actionHandlers = new java.util.WeakHashMap<>();
  /** Set while one state request looks up several commands: the action bars were brought up to date once. */
  private static final ThreadLocal<Boolean> ACTION_BARS_FRESH = new ThreadLocal<>();

  /**
   * The global action handler the active part set for a retargetable command (SubActionBars.setGlobalActionHandler
   * activates it for the command while the part is active), with the part's action bars brought up to date.
   */
  private HandlerDef globalActionHandler(String commandId) {
    String actionId = com.unitesk.requality.vscode.ui.workbench.actions.ActionFactory.actionIdOf(commandId);
    IWorkbenchPart part = Workbench.activePart();
    if (actionId == null || part == null || !(part.getSite() instanceof com.unitesk.requality.vscode.ui.workbench.PartSite))
      return null;
    com.unitesk.requality.vscode.ui.workbench.PartSite site = (com.unitesk.requality.vscode.ui.workbench.PartSite) part.getSite();
    if (!Boolean.TRUE.equals(ACTION_BARS_FRESH.get()))
      site.updateActionBars();
    com.unitesk.requality.vscode.ui.jface.action.IAction action = site.getActionBars().getGlobalActionHandler(actionId);
    if (action == null)
      return null;
    synchronized (actionHandlers) {
      return actionHandlers.computeIfAbsent(action, a -> new HandlerDef(commandId,
          new com.unitesk.requality.vscode.ui.commands.ActionHandler(a), ACTIVE_PART_PRIORITY));
    }
  }

  /**
   * The active handler of a command: code activations first, then the contributions whose activeWhen holds and the
   * global action handler of the active part, the highest source priority winning (between contributions: the one
   * whose parameters match, then the first declared; the part's handler wins a tie, being activated deeper).
   */
  private HandlerDef activeHandler(String commandId, IEvaluationContext ctx, Map<String, String> params) {
    IWorkbenchPart activePart = Workbench.activePart();
    HandlerDef partActivation = null;
    for (int i = activations.size() - 1; i >= 0; i--) {
      HandlerDef a = activations.get(i);
      if (!a.commandId.equals(commandId))
        continue;
      if (a.part == null)
        return a;
      if (partActivation == null && a.part == activePart)
        partActivation = a;
    }
    // the handlers of the active part (its site's activations, else its global action handler)
    HandlerDef best = partActivation != null ? partActivation : globalActionHandler(commandId);
    HandlerDef partHandler = best;
    for (HandlerDef d : handlers) {
      if (!d.commandId.equals(commandId))
        continue;
      boolean active;
      try {
        active = Expressions.evaluateContainer(d.activeWhen, ctx);
      } catch (RuntimeException e) {
        RLogger.logError(e);
        active = false;
      }
      if (!active || d.instance() == null)
        continue;
      if (best == null || d.priority > best.priority || d.priority == best.priority && best != partHandler
          && paramsMatch(d, params) && !paramsMatch(best, params))
        best = d;
    }
    return best;
  }

  private static boolean paramsMatch(HandlerDef d, Map<String, String> params) {
    if (d.params.isEmpty() || params == null)
      return false;
    for (Map.Entry<String, String> e : d.params.entrySet())
      if (!e.getValue().equals(params.get(e.getKey())))
        return false;
    return true;
  }

  private static boolean enabled(HandlerDef d, IEvaluationContext ctx) {
    try {
      if (!Expressions.evaluateContainer(d.enabledWhen, ctx))
        return false;
      IHandler h = d.instance();
      if (h instanceof IHandler2)
        ((IHandler2) h).setEnabled(ctx);
      return h != null && h.isEnabled();
    } catch (RuntimeException e) {
      RLogger.logError(e);
      return false;
    }
  }

  private JsonObject state(List<String> ids) {
    IWorkbenchPart activePart = Workbench.activePart();
    if (activePart != null && activePart.getSite() instanceof com.unitesk.requality.vscode.ui.workbench.PartSite)
      ((com.unitesk.requality.vscode.ui.workbench.PartSite) activePart.getSite()).updateActionBars();
    ACTION_BARS_FRESH.set(true);
    try {
      return stateOf(ids);
    } finally {
      ACTION_BARS_FRESH.remove();
    }
  }

  private JsonObject stateOf(List<String> ids) {
    JsonObject r = new JsonObject();
    IEvaluationContext ctx = currentState();
    for (String id : ids) {
      JsonObject s = new JsonObject();
      HandlerDef d = activeHandler(id, ctx, null);
      if (d == null && (UNDO.equals(id) || REDO.equals(id))) {
        IOperationHistory h = PlatformUI.getWorkbench().getOperationSupport().getOperationHistory();
        IUndoContext c = PlatformUI.getWorkbench().getOperationSupport().getUndoContext();
        boolean can = UNDO.equals(id) ? h.canUndo(c) : h.canRedo(c);
        s.addProperty("handled", true);
        s.addProperty("enabled", can);
        com.unitesk.requality.vscode.ui.commands.operations.IUndoableOperation op = UNDO.equals(id)
            ? h.getUndoOperation(c) : h.getRedoOperation(c);
        s.addProperty("label", (UNDO.equals(id) ? "Undo" : "Redo") + (op != null ? " " + op.getLabel() : ""));
        r.add(id, s);
        continue;
      }
      s.addProperty("handled", d != null && d.instance().isHandled());
      s.addProperty("enabled", d != null && d.instance().isHandled() && enabled(d, ctx));
      // a global action handler: the text of the action (e.g. "Undo Copy Resources"), without mnemonics
      if (d != null && d.instance() instanceof com.unitesk.requality.vscode.ui.commands.ActionHandler) {
        String text = ((com.unitesk.requality.vscode.ui.commands.ActionHandler) d.instance()).getAction().getText();
        if (text != null)
          s.addProperty("label", text.split("\t")[0].replace("&&", "\u0000").replace("&", "").replace('\u0000', '&'));
      }
      if (d != null && d.instance() instanceof IElementUpdater) {
        UIElement el = new UIElement();
        try {
          ((IElementUpdater) d.instance()).updateElement(el, new HashMap<>());
        } catch (RuntimeException e) {
          RLogger.logError(e);
        }
        if (el.getText() != null)
          s.addProperty("label", el.getText());
        if (el.getChecked() != null)
          s.addProperty("checked", el.getChecked());
      }
      r.add(id, s);
    }
    return r;
  }

  private Object executeOnUi(String id, Map<String, String> params, Object trigger) {
    return executeOnUi(id, params, trigger, null);
  }

  private Object executeOnUi(String id, Map<String, String> params, Object trigger, Runnable before) {
    return executeOnUi(id, params, trigger, before, null);
  }

  private Object executeOnUi(String id, Map<String, String> params, Object trigger, Runnable before, String toolbarView) {
    Object[] result = new Object[1];
    Exception[] error = new Exception[1];
    Display.getDefault().syncExec(() -> {
      try {
        if (before != null)
          before.run();
        Object t = toolbarView == null ? trigger : ViewToolbarContributions.trigger(toolbarView, id);
        result[0] = execute(id, params, t);
      } catch (Exception e) {
        error[0] = e;
      } catch (Throwable t) {
        error[0] = new ExecutionException(String.valueOf(t), t);
      }
    });
    if (error[0] instanceof NotHandledException || error[0] instanceof NotDefinedException)
      throw new RpcException(RpcException.NOT_FOUND, error[0].getMessage());
    if (error[0] instanceof NotEnabledException)
      throw new RpcException(RpcException.REFUSED, error[0].getMessage());
    if (error[0] != null) {
      RLogger.logError(error[0]);
      Throwable cause = error[0].getCause() != null ? error[0].getCause() : error[0];
      throw new RpcException(RpcException.INTERNAL_ERROR, cause.getMessage() != null ? cause.getMessage() : cause.toString());
    }
    return result[0];
  }

  // ---- CommandSupport

  @Override
  public Command getCommand(String id) {
    load();
    return new Command(id, commandNames.getOrDefault(id, id));
  }

  @Override
  public boolean isEnabled(String id) {
    load();
    IEvaluationContext ctx = currentState();
    HandlerDef d = activeHandler(id, ctx, null);
    return d != null && enabled(d, ctx);
  }

  @Override
  public boolean isHandled(String id) {
    load();
    HandlerDef d = activeHandler(id, currentState(), null);
    return d != null && d.instance().isHandled();
  }

  @Override
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public Object execute(String id, Map parameters, Object trigger)
      throws ExecutionException, NotDefinedException, NotEnabledException, NotHandledException {
    load();
    IEvaluationContext ctx0 = currentState();
    if ((UNDO.equals(id) || REDO.equals(id)) && activeHandler(id, ctx0, parameters) == null) {
      // Workbench Undo/Redo (UndoRedoActionGroup over the operation history).
      IOperationHistory h = PlatformUI.getWorkbench().getOperationSupport().getOperationHistory();
      IUndoContext c = PlatformUI.getWorkbench().getOperationSupport().getUndoContext();
      return UNDO.equals(id) ? h.undo(c, null, null) : h.redo(c, null, null);
    }
    if (!commandNames.containsKey(id) && handlers.stream().noneMatch(h -> h.commandId.equals(id))
        && activations.stream().noneMatch(h -> h.commandId.equals(id))
        && com.unitesk.requality.vscode.ui.workbench.actions.ActionFactory.actionIdOf(id) == null)
      throw new NotDefinedException("Command " + id + " is not defined");
    IEvaluationContext ctx = currentState();
    HandlerDef d = activeHandler(id, ctx, parameters);
    if (d == null || !d.instance().isHandled())
      throw new NotHandledException("No handler for " + commandNames.getOrDefault(id, id) + " in this context");
    if (!enabled(d, ctx))
      throw new NotEnabledException(commandNames.getOrDefault(id, id) + " is not enabled for the selection");
    ExecutionEvent ev = new ExecutionEvent(getCommand(id), parameters == null ? new HashMap<>() : parameters, trigger, ctx);
    return d.instance().execute(ev);
  }

  @Override
  public UIElement updateElement(String commandId) {
    load();
    HandlerDef d = activeHandler(commandId, currentState(), null);
    if (d == null || !(d.instance() instanceof IElementUpdater))
      return null;
    UIElement el = new UIElement();
    try {
      ((IElementUpdater) d.instance()).updateElement(el, new HashMap<>());
    } catch (RuntimeException e) {
      RLogger.logError(e);
    }
    return el;
  }

  @Override
  public IHandlerActivation activateHandler(String commandId, IHandler handler) {
    HandlerDef d = new HandlerDef(commandId, handler);
    activations.add(d);
    return d;
  }

  @Override
  public IHandlerActivation activateHandler(String commandId, IHandler handler, Object part) {
    HandlerDef d = new HandlerDef(commandId, handler, ACTIVE_PART_PRIORITY);
    d.part = part;
    activations.add(d);
    return d;
  }

  @Override
  public void deactivateHandler(IHandlerActivation activation) {
    activations.remove(activation);
  }

  @Override
  public void deactivateHandlers(Object part) {
    activations.removeIf(a -> a.part == part);
  }

  @Override
  public void refreshElements(String commandId) {
    if (server == null)
      return;
    JsonObject p = new JsonObject();
    JsonArray ids = new JsonArray();
    ids.add(commandId);
    p.add("commands", ids);
    server.client().notify("command/changed", p);
  }
}
