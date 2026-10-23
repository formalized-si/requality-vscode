package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.jface.dialogs.ProgressMonitorDialog;
import com.unitesk.requality.vscode.ui.jface.operation.IRunnableWithProgress;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;

/**
 * {@code org.eclipse.jface.dialogs.ProgressMonitorDialog}: the "Progress Information" dialog shows the task name, the sub
 * task and the progress of a forked operation; its Cancel button cancels the monitor and the dialog closes after the
 * run; without fork the operation runs on the display thread; with {@code setOpenOnRun(false)} the dialog is not shown.
 */
class ProgressMonitorDialogTest {
  static ServerHarness h;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    h.initialize(h.workspace);
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  /** A run started on the display thread, as a command does; its outcome is recorded. */
  static final class Run {
    final AtomicBoolean done = new AtomicBoolean();
    final AtomicReference<Throwable> failure = new AtomicReference<>();

    void start(ProgressMonitorDialog dialog, boolean fork, boolean cancelable, IRunnableWithProgress operation) {
      Display.getDefault().asyncExec(() -> {
        try {
          dialog.run(fork, cancelable, operation);
        } catch (Throwable e) {
          failure.set(e);
        } finally {
          done.set(true);
        }
      });
    }
  }

  /** The ui/openShell notification of the first progress dialog opened after {@code before} of them. */
  static JsonObject awaitProgressShell(int before) throws Exception {
    JsonObject[] found = new JsonObject[1];
    h.await("a progress dialog to open", v -> {
      List<JsonObject> l = h.progressShells();
      if (l.size() > before) {
        found[0] = l.get(before);
        return true;
      }
      return false;
    });
    return found[0];
  }

  static JsonObject snapshot(String shell) throws Exception {
    return h.call("ui/snapshot", "shell", shell).getAsJsonObject();
  }

  static boolean closed(String shell) throws Exception {
    return snapshot(shell).has("closed");
  }

  static void event(String id, String type) {
    JsonObject p = new JsonObject();
    p.addProperty("id", id);
    p.addProperty("type", type);
    p.add("data", new JsonObject());
    h.notify("ui/event", p);
  }

  /** The id of the first widget of the type whose props match the predicate, or null. */
  static String find(String shell, String type, java.util.function.Predicate<JsonObject> props) throws Exception {
    for (JsonElement e : snapshot(shell).getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if (!type.equals(o.has("type") ? o.get("type").getAsString() : null))
        continue;
      JsonObject p = o.has("props") ? o.getAsJsonObject("props") : new JsonObject();
      if (props.test(p))
        return o.get("id").getAsString();
    }
    return null;
  }

  static String text(JsonObject props) {
    return props.has("text") && !props.get("text").isJsonNull() ? props.get("text").getAsString() : null;
  }

  static String button(String shell, String text) throws Exception {
    return find(shell, "button", p -> text.equals(text(p)));
  }

  /** Whether the button is enabled in the snapshot (buttons are enabled unless they say otherwise). */
  static boolean enabled(String shell, String text) throws Exception {
    String id = button(shell, text);
    if (id == null)
      return false;
    for (JsonElement e : snapshot(shell).getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if (id.equals(o.get("id").getAsString())) {
        JsonObject p = o.has("props") ? o.getAsJsonObject("props") : new JsonObject();
        return !p.has("enabled") || p.get("enabled").getAsBoolean();
      }
    }
    return false;
  }

  /** The selection of the progress indicator of the shell, or -1. */
  static int progressSelection(String shell) throws Exception {
    for (JsonElement e : snapshot(shell).getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if ("progress".equals(o.get("type").getAsString()) && o.has("props")
          && o.getAsJsonObject("props").has("value"))
        return o.getAsJsonObject("props").getAsJsonArray("value").get(0).getAsInt();
    }
    return -1;
  }

  static boolean hasLabel(String shell, String text) throws Exception {
    return find(shell, "label", p -> text.equals(text(p))) != null;
  }

  @Test
  void aForkedCancelableRunShowsTheDialogAndCancelEndsTheRun() throws Exception {
    AtomicBoolean started = new AtomicBoolean();
    AtomicBoolean canceledSeen = new AtomicBoolean();
    IRunnableWithProgress operation = monitor -> {
      monitor.beginTask("Copying files", 10);
      monitor.subTask("alpha.txt");
      monitor.worked(4);
      started.set(true);
      while (!monitor.isCanceled())
        Thread.sleep(10);
      canceledSeen.set(true);
    };
    int before = h.progressShells().size();
    Run run = new Run();
    run.start(new ProgressMonitorDialog(null), true, true, operation);
    JsonObject shell = awaitProgressShell(before);
    String sid = shell.get("shell").getAsString();
    assertEquals(ProgressMonitorDialog.TITLE, shell.get("title").getAsString(), "title of the dialog");

    h.await("the operation to start", v -> started.get());
    h.await("the task, the sub task and the progress shown", v -> {
      try {
        return hasLabel(sid, "Copying files") && hasLabel(sid, "alpha.txt") && progressSelection(sid) == 4;
      } catch (Exception e) {
        return false;
      }
    });
    h.await("Cancel enabled", v -> {
      try {
        return enabled(sid, "Cancel");
      } catch (Exception e) {
        return false;
      }
    });

    event(button(sid, "Cancel"), "selection");
    h.await("the operation canceled", v -> canceledSeen.get());
    h.await("the run finished", v -> run.done.get());
    assertNull(run.failure.get(), "the run completes without failure");
    h.await("the dialog closed after the run", v -> {
      try {
        return closed(sid);
      } catch (Exception e) {
        return false;
      }
    });
  }

  @Test
  void aRunWithoutForkRunsOnTheDisplayThreadAndClosesTheDialog() throws Exception {
    AtomicBoolean onDisplayThread = new AtomicBoolean();
    IRunnableWithProgress operation = monitor -> {
      onDisplayThread.set(Display.getDefault().isUIThread());
      monitor.beginTask("Scanning", 5);
      monitor.subTask("beta");
      monitor.worked(5);
    };
    int before = h.progressShells().size();
    Run run = new Run();
    run.start(new ProgressMonitorDialog(null), false, false, operation);
    JsonObject shell = awaitProgressShell(before);
    String sid = shell.get("shell").getAsString();
    assertEquals(ProgressMonitorDialog.TITLE, shell.get("title").getAsString(), "title of the dialog");
    h.await("the run finished", v -> run.done.get());
    assertNull(run.failure.get(), "the run completes without failure");
    assertTrue(onDisplayThread.get(), "the operation runs on the display thread");
    h.await("the dialog closed after the run", v -> {
      try {
        return closed(sid);
      } catch (Exception e) {
        return false;
      }
    });
  }

  @Test
  void withOpenOnRunFalseTheDialogIsNotShown() throws Exception {
    int before = h.progressShells().size();
    ProgressMonitorDialog dialog = new ProgressMonitorDialog(null);
    dialog.setOpenOnRun(false);
    AtomicBoolean ran = new AtomicBoolean();
    Run run = new Run();
    run.start(dialog, false, true, monitor -> {
      monitor.beginTask("Hidden", 1);
      monitor.worked(1);
      ran.set(true);
    });
    h.await("the run finished", v -> run.done.get());
    assertNull(run.failure.get(), "the run completes without failure");
    assertTrue(ran.get(), "the operation ran");
    // the notifications sent before a response are processed before it: no progress dialog was opened
    h.call("ui/snapshot", "shell", "none");
    assertEquals(before, h.progressShells().size(), "no progress dialog opened");
  }
}
