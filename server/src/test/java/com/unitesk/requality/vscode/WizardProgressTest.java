package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.jface.operation.IRunnableWithProgress;
import com.unitesk.requality.vscode.ui.jface.wizard.ProgressMonitorPart;
import com.unitesk.requality.vscode.ui.jface.wizard.Wizard;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardDialog;
import com.unitesk.requality.vscode.ui.jface.wizard.WizardPage;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;

/**
 * The progress part of the wizard dialog ({@code org.eclipse.jface.wizard.WizardDialog}): an operation run by the page
 * with {@code getContainer().run(true, true, ...)} shows its task and progress in the part and disables the buttons; the
 * stop button of the part cancels the operation; after the run the part is hidden and the buttons are restored.
 */
class WizardProgressTest {
  static final String TITLE = "Wizard Progress Test";
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

  /** The page runs a cancelable operation that waits until it is canceled. */
  static final class ProgressPage extends WizardPage {
    final AtomicBoolean started = new AtomicBoolean();
    final AtomicBoolean canceledSeen = new AtomicBoolean();
    final AtomicBoolean finished = new AtomicBoolean();
    volatile ProgressMonitorPart part;

    ProgressPage() {
      super("progress", "Progress", null);
      setDescription("Runs a cancelable operation");
      setPageComplete(true);
    }

    @Override
    public void createControl(Composite parent) {
      Composite c = new Composite(parent, SWT.NONE);
      c.setLayout(new GridLayout());
      Button run = new Button(c, SWT.PUSH);
      run.setText("Run operation");
      run.addSelectionListener(new SelectionAdapter() {
        @Override
        public void widgetSelected(SelectionEvent e) {
          runOperation();
        }
      });
      setControl(c);
    }

    void runOperation() {
      IRunnableWithProgress operation = monitor -> {
        if (monitor instanceof ProgressMonitorPart)
          part = (ProgressMonitorPart) monitor;
        monitor.beginTask("Scanning the folders", 10);
        monitor.subTask("alpha");
        monitor.worked(4);
        started.set(true);
        while (!monitor.isCanceled())
          Thread.sleep(10);
        canceledSeen.set(true);
      };
      try {
        getContainer().run(true, true, operation);
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
      finished.set(true);
    }
  }

  static final class ProgressWizard extends Wizard {
    final ProgressPage page = new ProgressPage();

    ProgressWizard() {
      setWindowTitle(TITLE);
      setNeedsProgressMonitor(true);
      addPage(page);
    }

    @Override
    public boolean performFinish() {
      return true;
    }
  }

  /** The shell of the wizard dialog, opened after the given count of opened shells. */
  static JsonObject awaitWizardShell(int before) throws Exception {
    JsonObject[] found = new JsonObject[1];
    h.await("the " + TITLE + " dialog", v -> {
      List<JsonObject> l = h.notifications("ui/openShell");
      for (int k = before; k < l.size(); k++)
        if (TITLE.equals(l.get(k).has("title") ? l.get(k).get("title").getAsString() : null)) {
          found[0] = l.get(k);
          return true;
        }
      return false;
    });
    return found[0];
  }

  static void event(String id, String type) {
    JsonObject p = new JsonObject();
    p.addProperty("id", id);
    p.addProperty("type", type);
    p.add("data", new JsonObject());
    h.notify("ui/event", p);
  }

  static JsonObject snapshot(String shell) throws Exception {
    return h.call("ui/snapshot", "shell", shell).getAsJsonObject();
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

  /** The id of the button with the text, or null. */
  static String button(String shell, String text) throws Exception {
    return find(shell, "button", p -> p.has("text") && text.equals(p.get("text").getAsString()));
  }

  /** Whether the button with the text is enabled in the snapshot. */
  static boolean enabled(String shell, String text) throws Exception {
    String id = button(shell, text);
    assertNotNull(id, text + " button");
    JsonObject p = null;
    for (JsonElement e : snapshot(shell).getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if (id.equals(o.get("id").getAsString()))
        p = o.getAsJsonObject("props");
    }
    return p == null || !p.has("enabled") || p.get("enabled").getAsBoolean();
  }

  /** The progress selection of the progress indicator of the shell, or -1. */
  static int progressSelection(String shell) throws Exception {
    for (JsonElement e : snapshot(shell).getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if ("progress".equals(o.get("type").getAsString()) && o.has("props")
          && o.getAsJsonObject("props").has("value"))
        return o.getAsJsonObject("props").getAsJsonArray("value").get(0).getAsInt();
    }
    return -1;
  }

  /** The text of the label of the progress part (the task and subtask), or the empty string. */
  static String taskLabel(String shell) throws Exception {
    String snap = snapshot(shell).toString();
    return snap.contains("Scanning the folders: alpha") ? "Scanning the folders: alpha" : "";
  }

  @Test
  void theProgressPartShowsTheOperationAndTheStopButtonCancelsIt() throws Exception {
    ProgressWizard wizard = new ProgressWizard();
    int before = h.notifications("ui/openShell").size();
    Display.getDefault().asyncExec(() -> new WizardDialog(null, wizard).open());
    JsonObject shell = awaitWizardShell(before);
    String sid = shell.get("shell").getAsString();
    assertTrue(enabled(sid, "Finish"), "Finish enabled before the operation");

    event(button(sid, "Run operation"), "selection");
    h.await("the operation to start", v -> wizard.page.started.get());
    h.await("the task and the progress in the part", v -> {
      try {
        return taskLabel(sid).length() > 0 && progressSelection(sid) == 4;
      } catch (Exception e) {
        return false;
      }
    });
    assertEquals(4, progressSelection(sid), "worked of the operation shown by the progress indicator");
    assertFalse(enabled(sid, "Finish"), "Finish disabled while the operation runs");
    assertFalse(enabled(sid, "Cancel"), "Cancel disabled while the operation runs");
    assertFalse(enabled(sid, "Run operation"), "page controls disabled while the operation runs");
    assertTrue(wizard.page.part != null && wizard.page.part.isVisible(), "progress part visible while the operation runs");

    String stop = find(sid, "button", p -> p.has("image") && p.get("image").getAsString().endsWith("stop.png"));
    assertNotNull(stop, "stop button of the progress part");
    event(stop, "selection");
    h.await("the operation canceled", v -> wizard.page.canceledSeen.get());
    h.await("the operation finished", v -> wizard.page.finished.get());

    assertFalse(wizard.page.part.isVisible(), "progress part hidden after the run");
    h.await("the buttons restored", v -> {
      try {
        return enabled(sid, "Finish") && enabled(sid, "Run operation");
      } catch (Exception e) {
        return false;
      }
    });
    assertTrue(enabled(sid, "Cancel"), "Cancel enabled after the run");
    h.closeShell(sid);
  }
}
