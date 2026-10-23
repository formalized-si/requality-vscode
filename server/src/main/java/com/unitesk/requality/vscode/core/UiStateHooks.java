package com.unitesk.requality.vscode.core;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.tree.IStateChangeHandler;
import com.unitesk.requality.core.tree.TreeState;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.repo.RevisionInfo;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;

/**
 * The tree state hooks of all projects ({@code TreeStateManager} hooks of "*") with the hooks of the Requality Links
 * Explorer run on the UI thread. RequalityLCNF clears the node it shows from a state hook (initNewFocusCleaner: a tree
 * of the node is halted or restarted), which the core calls on the thread that changes the state, while the view
 * updates its tree on the UI thread from that field: the field could be cleared between the view's check and its use.
 * Run on the UI thread, the hook comes before or after the update of the view, as the other UI work of the view.
 *
 * <p>The map of the hooks of "*" is replaced by this map: the core calls the hooks of a state from a copy of the entries
 * (wrapped handlers); {@code addStateHook}/{@code removeStateHook} read and write the handler arrays with {@code get} and
 * {@code put}, which see the handlers as they were added, so they find and remove them by identity.
 */
public final class UiStateHooks extends ConcurrentHashMap<TreeState, IStateChangeHandler[]> {
  private static final long serialVersionUID = 1L;
  /** The class of the Links Explorer, whose state hooks are anonymous classes of it. */
  private static final String UI_OWNER = "com.unitesk.requality.vscode.port.eclipse.ui.RequalityLCNF$";

  private final transient Map<IStateChangeHandler, IStateChangeHandler> wrappers = Collections.synchronizedMap(new IdentityHashMap<>());

  /** Replaces the hooks of "*" of the state manager by this map (the hooks already added are kept). */
  public static void install(TreeStateManager tsm) {
    try {
      Field f = TreeStateManager.class.getDeclaredField("stateHooks");
      f.setAccessible(true);
      @SuppressWarnings("unchecked")
      Map<String, Map<TreeState, IStateChangeHandler[]>> hooks = (Map<String, Map<TreeState, IStateChangeHandler[]>>) f.get(tsm);
      synchronized (tsm) {
        UiStateHooks all = new UiStateHooks();
        Map<TreeState, IStateChangeHandler[]> old = hooks.get(TreeStateManager.ALL_CONST);
        if (old != null)
          synchronized (old) {
            for (Map.Entry<TreeState, IStateChangeHandler[]> e : old.entrySet())
              all.put(e.getKey(), e.getValue());
          }
        hooks.put(TreeStateManager.ALL_CONST, all);
      }
    } catch (ReflectiveOperationException e) {
      RLogger.logError(e);
    }
  }

  /** The handlers of the state as they were added. */
  @Override
  public IStateChangeHandler[] get(Object key) {
    IStateChangeHandler[] stored = super.get(key);
    if (stored == null)
      return null;
    IStateChangeHandler[] added = new IStateChangeHandler[stored.length];
    for (int i = 0; i < stored.length; i++)
      added[i] = stored[i] instanceof OnUiThread ? ((OnUiThread) stored[i]).hook : stored[i];
    return added;
  }

  /** Stores the handlers of the state, a hook of the Links Explorer wrapped to run on the UI thread. */
  @Override
  public IStateChangeHandler[] put(TreeState key, IStateChangeHandler[] value) {
    IStateChangeHandler[] stored = new IStateChangeHandler[value.length];
    for (int i = 0; i < value.length; i++) {
      IStateChangeHandler h = value[i];
      stored[i] = h != null && !(h instanceof OnUiThread) && h.getClass().getName().startsWith(UI_OWNER)
          ? wrappers.computeIfAbsent(h, OnUiThread::new)
          : h;
    }
    return super.put(key, stored);
  }

  /** A state hook run on the UI thread (after the state change, in the order of the changes). */
  public static final class OnUiThread implements IStateChangeHandler {
    final IStateChangeHandler hook;

    public OnUiThread(IStateChangeHandler hook) {
      this.hook = hook;
    }

    @Override
    public void stateChanges(TreeDB db, String lname, String pname, RevisionInfo ri, TreeState oldState, TreeState state) {
      Display.getDefault().asyncExec(() -> hook.stateChanges(db, lname, pname, ri, oldState, state));
    }

    @Override
    public boolean filterTreeDB(TreeDB db) {
      return hook.filterTreeDB(db);
    }
  }
}
