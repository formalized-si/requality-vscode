package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitorWithBlocking;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.jface.resource.ImageDescriptor;
import com.unitesk.requality.vscode.ui.runtime.Job;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.dialogs.WorkbenchPreferences;
import com.unitesk.requality.vscode.ui.workbench.progress.IProgressConstants;
import com.unitesk.requality.vscode.ui.workbench.progress.ProgressInfoItem;
import com.unitesk.requality.vscode.ui.workbench.progress.ProgressManager;
import com.unitesk.requality.vscode.ui.workbench.progress.ProgressView;

/**
 * {@code org.eclipse.ui.views.ProgressView} and the progress dialog of user jobs: a running job is a row of the view
 * with its name, its task and its progress, its stop button cancels it; a finished job is kept with KEEP until Remove
 * All Finished Operations; with "Always run in background" off a user job opens its dialog, whose Run in Background
 * leaves the job running; with the preference on no dialog opens.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProgressViewTest {
  static final String HOST = "requality.progress";
  static final String NO_OPERATIONS = ProgressInfoItem.NO_OPERATIONS;
  static final String STOP = "Cancel Operation";
  static final String REMOVE_ALL = "Remove All Finished Operations";
  static final String RUN_IN_BACKGROUND = "Run in &Background";
  static final String ALWAYS_RUN = "Always r&un in background";
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

  /** A user job that reports a task and a sub task, and works until it is released or asked to stop. */
  static final class Work extends Job {
    final CountDownLatch started = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
    final AtomicBoolean canceled = new AtomicBoolean();

    Work(String name) {
      super(name);
      setUser(true);
    }

    @Override
    protected IStatus run(IProgressMonitor monitor) {
      monitor.beginTask("Exporting catalog", 100);
      monitor.subTask("alpha.xml");
      monitor.worked(40);
      started.countDown();
      try {
        while (!monitor.isCanceled() && release.getCount() > 0)
          Thread.sleep(10);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      canceled.set(monitor.isCanceled());
      return monitor.isCanceled() ? Status.CANCEL_STATUS : Status.OK_STATUS;
    }
  }

  /** The shell of the progress view shown in the VS Code view of the Requality panel (opened by views/open). */
  static String progressShell() throws Exception {
    h.call("views/open", "view", ProgressView.ID);
    return h.await2("the shell of the progress view", () -> {
      String shell = null;
      for (JsonObject o : h.notifications("ui/openShell"))
        if (o.has("host") && "view".equals(o.get("host").getAsString()) && o.has("view")
            && HOST.equals(o.get("view").getAsString()))
          shell = o.get("shell").getAsString();
      return shell;
    });
  }

  /** The shell of the progress dialog of a job, opened with its name as the title. */
  static String dialogShell(String jobName) throws Exception {
    return h.await2("the dialog of " + jobName, () -> {
      String shell = null;
      for (JsonObject o : h.notifications("ui/openShell"))
        if (o.has("title") && jobName.equals(o.get("title").getAsString()))
          shell = o.get("shell").getAsString();
      return shell;
    });
  }

  static JsonObject snapshot(String shell) throws Exception {
    return h.call("ui/snapshot", "shell", shell).getAsJsonObject();
  }

  static boolean closed(String shell) throws Exception {
    return snapshot(shell).has("closed");
  }

  /** The text of a widget as the snapshot shows it: the mnemonics ({@code &}) are not shown. */
  static boolean textIs(JsonObject props, String expected) {
    String text = text(props);
    return text != null && text.equals(expected.replace("&", ""));
  }

  static String text(JsonObject props) {
    return props.has("text") && !props.get("text").isJsonNull() ? props.get("text").getAsString() : null;
  }

  static String tooltip(JsonObject props) {
    return props.has("tooltip") && !props.get("tooltip").isJsonNull() ? props.get("tooltip").getAsString() : null;
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

  static boolean hasLabel(String shell, java.util.function.Predicate<String> text) throws Exception {
    return find(shell, "label", p -> text.test(text(p))) != null;
  }

  static boolean hasLabel(String shell, String text) throws Exception {
    return find(shell, "label", p -> text.equals(text(p))) != null;
  }

  /** The selection of the first progress indicator of the shell, or -1. */
  static int progressSelection(String shell) throws Exception {
    for (JsonElement e : snapshot(shell).getAsJsonArray("ops")) {
      JsonObject o = e.getAsJsonObject();
      if ("progress".equals(o.get("type").getAsString()) && o.has("props")
          && o.getAsJsonObject("props").has("value"))
        return o.getAsJsonObject("props").getAsJsonArray("value").get(0).getAsInt();
    }
    return -1;
  }

  static void setRunInBackground(boolean value) {
    WorkbenchPreferences.getStore().setValue(WorkbenchPreferences.RUN_IN_BACKGROUND, value);
  }

  static boolean runInBackground() {
    return WorkbenchPreferences.getStore().getBoolean(WorkbenchPreferences.RUN_IN_BACKGROUND);
  }

  @Test
  @Order(1)
  void theViewSaysThatNoOperationsAreToDisplayWhenNoJobRuns() throws Exception {
    String shell = progressShell();
    h.await("the empty text shown", v -> {
      try {
        return hasLabel(shell, NO_OPERATIONS);
      } catch (Exception e) {
        return false;
      }
    });
  }

  @Test
  @Order(2)
  void aRunningJobIsARowWithItsNameTaskAndProgressAndItsStopButtonCancelsIt() throws Exception {
    Work work = new Work("Exporting catalog");
    work.schedule();
    h.await("the job to start", v -> work.started.getCount() == 0);
    String shell = progressShell();

    h.await("the name, the task and the progress of the job shown", v -> {
      try {
        return hasLabel(shell, "Exporting catalog") && hasLabel(shell, s -> s != null && s.contains("alpha.xml"))
            && progressSelection(shell) == 40;
      } catch (Exception e) {
        return false;
      }
    });
    assertEquals(40, progressSelection(shell), "the progress of the job");
    assertEquals(40, work.getPercentDone(), "the progress of the job in the progress manager");

    String stop = find(shell, "button", p -> STOP.equals(tooltip(p)));
    assertNotNull(stop, "the stop button of the row");
    event(stop, "selection");
    assertTrue(work.join(10000, null), "the job ends after its stop");
    assertTrue(work.canceled.get(), "the job was asked to stop");
    h.await("the row removed", v -> {
      try {
        return !hasLabel(shell, s -> s != null && s.startsWith("Exporting catalog")) && hasLabel(shell, NO_OPERATIONS);
      } catch (Exception e) {
        return false;
      }
    });
  }

  @Test
  @Order(3)
  void aFinishedKeptJobStaysInTheViewUntilRemoveAllFinishedOperations() throws Exception {
    Work work = new Work("Kept import");
    work.setProperty(IProgressConstants.KEEP_PROPERTY, Boolean.TRUE);
    work.release.countDown();
    work.schedule();
    assertTrue(work.join(10000, null), "the job ends");
    assertEquals(Status.OK_STATUS, work.getResult(), "the job finished without error");
    String shell = progressShell();
    h.await("the finished job shown", v -> {
      try {
        return hasLabel(shell, s -> s != null && s.startsWith("Kept import (Finished at"));
      } catch (Exception e) {
        return false;
      }
    });
    assertFalse(ProgressManager.getInstance().getJobs().isEmpty(), "the finished job is kept");

    // the Remove All Finished Operations action of the view toolbar
    ProgressView view = (ProgressView) PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
        .findView(ProgressView.ID);
    assertTrue(view.getClearAllAction().isEnabled(), "Remove All Finished Operations is enabled");
    view.getClearAllAction().run();
    h.await("the finished job removed", v -> {
      try {
        return !hasLabel(shell, s -> s != null && s.startsWith("Kept import"));
      } catch (Exception e) {
        return false;
      }
    });
    assertFalse(ProgressManager.getInstance().getJobs().contains(work), "no finished job is kept");
  }

  @Test
  @Order(4)
  void withRunInBackgroundOffAUserJobOpensItsDialogAndRunInBackgroundLeavesItRunning() throws Exception {
    setRunInBackground(false);
    try {
      Work work = new Work("Importing archive");
      work.schedule();
      String dialog = dialogShell("Importing archive");
      h.await("the job to start", v -> work.started.getCount() == 0);
      assertTrue(hasLabel(dialog, s -> s != null && s.startsWith("Importing archive")),
          "the name of the job in the dialog");
      h.await("the task of the job in the dialog", v -> {
        try {
          return hasLabel(dialog, s -> s != null && s.contains("alpha.xml"));
        } catch (Exception e) {
          return false;
        }
      });
      assertEquals(40, progressSelection(dialog), "the progress of the job in the dialog");

      event(find(dialog, "button", p -> textIs(p, RUN_IN_BACKGROUND)), "selection");
      h.await("the dialog closed", v -> {
        try {
          return closed(dialog);
        } catch (Exception e) {
          return false;
        }
      });
      assertEquals(Job.RUNNING, work.getState(), "the job goes on in the background");
      assertFalse(work.isCancelRequested(), "the job is not canceled");
      assertFalse(runInBackground(), "the preference is not changed by Run in Background");

      work.release.countDown();
      assertTrue(work.join(10000, null), "the job ends");
      assertFalse(work.canceled.get(), "the job was not canceled");
    } finally {
      setRunInBackground(true);
    }
  }

  @Test
  @Order(5)
  void theDialogCancelStopsTheJobAndTheCheckboxSetsThePreference() throws Exception {
    setRunInBackground(false);
    try {
      Work work = new Work("Checking catalog");
      work.schedule();
      String dialog = dialogShell("Checking catalog");
      h.await("the job to start", v -> work.started.getCount() == 0);
      event(find(dialog, "button", p -> textIs(p, ALWAYS_RUN)), "selection");
      event(find(dialog, "button", p -> textIs(p, "Cancel")), "selection");
      assertTrue(work.join(10000, null), "the job ends after its cancel");
      assertTrue(work.canceled.get(), "the dialog's Cancel asked the job to stop");
      h.await("the dialog closed", v -> {
        try {
          return closed(dialog);
        } catch (Exception e) {
          return false;
        }
      });
      // Cancel does not change the preference, even though the checkbox was checked
      assertFalse(runInBackground(), "Cancel leaves the preference");

      Work again = new Work("Checking index");
      again.schedule();
      String second = dialogShell("Checking index");
      h.await("the job to start", v -> again.started.getCount() == 0);
      event(find(second, "button", p -> textIs(p, ALWAYS_RUN)), "selection");
      event(find(second, "button", p -> textIs(p, RUN_IN_BACKGROUND)), "selection");
      h.await("the preference set by the checkbox", v -> runInBackground());
      again.release.countDown();
      assertTrue(again.join(10000, null), "the job ends");
    } finally {
      setRunInBackground(true);
    }
  }

  @Test
  @Order(6)
  void withRunInBackgroundOnNoDialogOpensForAUserJob() throws Exception {
    setRunInBackground(true);
    Work work = new Work("Quiet export");
    work.release.countDown();
    work.schedule();
    assertTrue(work.join(10000, null), "the job ends");
    // the notifications sent before a response are processed before it
    h.call("ui/snapshot", "shell", "none");
    List<JsonObject> dialogs = h.notifications("ui/openShell");
    assertFalse(dialogs.stream().anyMatch(o -> o.has("title") && "Quiet export".equals(o.get("title").getAsString())),
        "no dialog for the job");
  }

  /** A system job that works until it is released (not shown unless the system operations are shown). */
  static Work system(String name) {
    Work w = new Work(name);
    w.setUser(false);
    w.setSystem(true);
    return w;
  }

  /** The item of a menu with the label (mnemonics ignored), or null. */
  static JsonObject menuItem(String menu, String label) throws Exception {
    JsonObject p = new JsonObject();
    p.addProperty("menu", menu);
    for (JsonElement e : h.call("menu/items", p).getAsJsonArray()) {
      JsonObject o = e.getAsJsonObject();
      String text = o.has("label") ? o.get("label").getAsString() : null;
      if (text != null && text.replace("&", "").equals(label.replace("&", "")))
        return o;
    }
    return null;
  }

  /** Runs a menu item; the answer comes when the action ends (a dialog it opens is answered by the test). */
  static java.util.concurrent.CompletableFuture<JsonElement> runItem(String menu, JsonObject item) {
    JsonObject p = new JsonObject();
    p.addProperty("menu", menu);
    p.addProperty("index", item.get("index").getAsInt());
    return h.callAsync("menu/run", p);
  }

  static boolean enabled(JsonObject props) {
    return !props.has("enabled") || props.get("enabled").getAsBoolean();
  }

  static boolean closedQuietly(String shell) {
    try {
      return closed(shell);
    } catch (Exception e) {
      return false;
    }
  }

  /** A job that reports that it is blocked until it is unblocked, then works until it is released. */
  static final class Blocking extends Job {
    final CountDownLatch started = new CountDownLatch(1);
    final CountDownLatch unblock = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);

    Blocking(String name) {
      super(name);
      setUser(true);
    }

    @Override
    protected IStatus run(IProgressMonitor monitor) {
      monitor.beginTask("Reading lock", IProgressMonitor.UNKNOWN);
      started.countDown();
      ((IProgressMonitorWithBlocking) monitor)
          .setBlocked(new Status(IStatus.WARNING, HOST, "Waiting for the catalog lock"));
      await(unblock);
      ((IProgressMonitorWithBlocking) monitor).clearBlocked();
      await(release);
      return Status.OK_STATUS;
    }
  }

  /** A job that does not stop when it is asked to: it works until it is released. */
  static final class Stubborn extends Job {
    final CountDownLatch started = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);

    Stubborn(String name) {
      super(name);
      setUser(true);
    }

    @Override
    protected IStatus run(IProgressMonitor monitor) {
      monitor.beginTask("Exporting", IProgressMonitor.UNKNOWN);
      started.countDown();
      await(release);
      return Status.OK_STATUS;
    }
  }

  /** A job of a family: the kept jobs of the same family are replaced by a KEEPONE job (Job.belongsTo). */
  static final class Grouped extends Job {
    final String group;

    Grouped(String name, String group) {
      super(name);
      this.group = group;
      setProperty(IProgressConstants.KEEPONE_PROPERTY, Boolean.TRUE);
    }

    @Override
    public boolean belongsTo(Object family) {
      return family instanceof Grouped && ((Grouped) family).group.equals(group);
    }

    @Override
    protected IStatus run(IProgressMonitor monitor) {
      return Status.OK_STATUS;
    }
  }

  static void await(CountDownLatch latch) {
    try {
      latch.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** A key event of the shell: an Escape traversal (SWT.TRAVERSE_ESCAPE). */
  static void escape(String shell) {
    JsonObject p = new JsonObject();
    p.addProperty("id", shell);
    p.addProperty("type", "traverse");
    JsonObject data = new JsonObject();
    data.addProperty("detail", 1 << 1);
    p.add("data", data);
    h.notify("ui/event", p);
  }

  @Test
  @Order(7)
  void theViewMenuPreferencesSetWhetherTheSystemOperationsAreShown() throws Exception {
    progressShell();
    setShowSystemJobs(false);
    JsonObject item = menuItem(ProgressView.ID + ".viewMenu", "Preferences...");
    assertNotNull(item, "the Preferences item of the view menu");
    runItem(ProgressView.ID + ".viewMenu", item);
    String dialog = dialogShell("Progress Preferences");
    String check = find(dialog, "button", p -> textIs(p, "Show sleeping and system operations"));
    assertNotNull(check, "the check box of the progress preferences");
    assertNotNull(find(dialog, "button", p -> textIs(p, "Restore Defaults")), "the Restore Defaults button");
    event(check, "selection");
    event(find(dialog, "button", p -> textIs(p, "OK")), "selection");
    h.await("the dialog closed", v -> closedQuietly(dialog));
    assertTrue(ProgressManager.showSystemJobs(), "the preference is stored");
    assertTrue(WorkbenchPreferences.getStore().getBoolean(WorkbenchPreferences.SHOW_SYSTEM_JOBS),
        "the preference is in the workbench preferences");
  }

  static void setShowSystemJobs(boolean show) {
    ProgressManager.getInstance().setShowSystemJobs(show);
  }

  @Test
  @Order(8)
  void systemOperationsAreListedOnlyWhenTheViewShowsThem() throws Exception {
    String shell = progressShell();
    setShowSystemJobs(false);
    Work system = system("Indexing workspace");
    system.schedule();
    h.await("the system job to start", v -> system.started.getCount() == 0);
    assertFalse(ProgressManager.getInstance().getJobs().contains(system), "hidden while the preference is off");

    setShowSystemJobs(true);
    h.await("the system job listed with its System prefix", v -> {
      try {
        return hasLabel(shell, "System: Indexing workspace");
      } catch (Exception e) {
        return false;
      }
    });
    assertTrue(ProgressManager.getInstance().getJobs().contains(system), "listed while the preference is on");

    setShowSystemJobs(false);
    h.await("the system job removed again", v -> {
      try {
        return !hasLabel(shell, s -> s != null && s.startsWith("System: Indexing workspace"));
      } catch (Exception e) {
        return false;
      }
    });
    system.release.countDown();
    assertTrue(system.join(10000, null), "the system job ends");
  }

  @Test
  @Order(9)
  void aBlockedJobShowsItsReasonUntilItIsUnblocked() throws Exception {
    Blocking job = new Blocking("Locked import");
    job.schedule();
    h.await("the job to start", v -> job.started.getCount() == 0);
    String shell = progressShell();
    h.await("the reason of the block shown", v -> {
      try {
        return hasLabel(shell, "Locked import (Blocked: Waiting for the catalog lock)");
      } catch (Exception e) {
        return false;
      }
    });
    job.unblock.countDown();
    h.await("the block cleared", v -> {
      try {
        return hasLabel(shell, "Locked import");
      } catch (Exception e) {
        return false;
      }
    });
    job.release.countDown();
    assertTrue(job.join(10000, null), "the job ends");
  }

  @Test
  @Order(10)
  void aJobAskedToStopShowsCancelRequestedWithItsStopButtonDisabled() throws Exception {
    Stubborn job = new Stubborn("Stubborn export");
    job.schedule();
    h.await("the job to start", v -> job.started.getCount() == 0);
    String shell = progressShell();
    String stop = find(shell, "button", p -> STOP.equals(tooltip(p)));
    assertNotNull(stop, "the stop button of the row");
    event(stop, "selection");
    h.await("the cancel requested shown", v -> {
      try {
        return hasLabel(shell, "Stubborn export (Cancel Requested)");
      } catch (Exception e) {
        return false;
      }
    });
    assertNotNull(find(shell, "button", p -> STOP.equals(tooltip(p)) && !enabled(p)),
        "the stop button is disabled until the job ends");
    job.release.countDown();
    assertTrue(job.join(10000, null), "the job ends");
    assertTrue(job.isCancelRequested(), "the job was asked to stop");
  }

  @Test
  @Order(11)
  void aJobThatFinishedWithAnErrorIsKeptWithItsErrorLink() throws Exception {
    Job failing = new Job("Broken import") {
      @Override
      protected IStatus run(IProgressMonitor monitor) {
        return new Status(IStatus.ERROR, HOST, "The catalog is broken");
      }
    };
    failing.schedule();
    assertTrue(failing.join(10000, null), "the job ends");
    assertEquals(IStatus.ERROR, failing.getResult().getSeverity(), "the job finished with an error");
    String shell = progressShell();
    h.await("the error job kept with its error link", v -> {
      try {
        return hasLabel(shell, s -> s != null && s.startsWith("Broken import (Finished at"))
            && find(shell, "link", p -> text(p) != null && text(p).contains("The catalog is broken")) != null
            && find(shell, "label", p -> imageIs(p, "error.png")) != null;
      } catch (Exception e) {
        return false;
      }
    });
    ProgressManager.getInstance().removeFinished(failing);
    h.await("the error job removed by its Remove button", v -> {
      try {
        return !hasLabel(shell, s -> s != null && s.startsWith("Broken import"));
      } catch (Exception e) {
        return false;
      }
    });
  }

  /** Whether the image of the widget is the file of the name (the image is sent as its path). */
  static boolean imageIs(JsonObject props, String fileName) {
    return props.has("image") && !props.get("image").isJsonNull()
        && props.get("image").getAsString().replace('\\', '/').endsWith("/" + fileName);
  }

  @Test
  @Order(16)
  void aJobShowsItsOwnImageAndTheDefaultJobImageOtherwise() throws Exception {
    Work own = new Work("Own icon");
    own.setProperty(IProgressConstants.ICON_PROPERTY, ImageDescriptor.fromResource("icons/shared/pview.png"));
    Work plain = new Work("Plain icon");
    own.schedule();
    plain.schedule();
    h.await("both jobs to start", v -> own.started.getCount() == 0 && plain.started.getCount() == 0);
    String shell = progressShell();
    h.await("the rows shown with their images", v -> {
      try {
        return hasLabel(shell, "Own icon") && hasLabel(shell, "Plain icon")
            && find(shell, "label", p -> imageIs(p, "pview.png")) != null
            && find(shell, "label", p -> imageIs(p, "progress_task.png")) != null;
      } catch (Exception e) {
        return false;
      }
    });
    own.release.countDown();
    plain.release.countDown();
    assertTrue(own.join(10000, null), "the first job ends");
    assertTrue(plain.join(10000, null), "the second job ends");
  }

  @Test
  @Order(12)
  void aQueuedJobIsWaitingAndASleepingJobIsNotListed() throws Exception {
    Job queued = new Job("Queued check") {
      @Override
      protected IStatus run(IProgressMonitor monitor) {
        return Status.OK_STATUS;
      }
    };
    assertEquals("Queued check (Waiting)", ProgressInfoItem.nameText(queued, null), "a job that is not run yet");

    Job sleeping = new Job("Later check") {
      @Override
      protected IStatus run(IProgressMonitor monitor) {
        return Status.OK_STATUS;
      }
    };
    sleeping.schedule(2000);
    assertEquals("Later check (Sleeping)", ProgressInfoItem.nameText(sleeping, null), "a sleeping job");
    assertFalse(ProgressManager.getInstance().getJobs().contains(sleeping), "a sleeping job is not listed");
    assertTrue(sleeping.cancel(), "the sleeping job is canceled");
    assertTrue(sleeping.join(5000, null), "the canceled job ends");
  }

  @Test
  @Order(13)
  void theRowMenuCancelsTheSelectedJob() throws Exception {
    Work work = new Work("Menu cancel");
    work.schedule();
    h.await("the job to start", v -> work.started.getCount() == 0);
    String shell = progressShell();
    String[] name = new String[1];
    h.await("the row of the job shown", v -> {
      try {
        name[0] = find(shell, "label", p -> "Menu cancel".equals(text(p)));
        return name[0] != null;
      } catch (Exception e) {
        return false;
      }
    });
    event(name[0], "mousedown");
    JsonObject cancel = menuItem(ProgressView.ID + ".popup", "Cancel");
    assertNotNull(cancel, "the Cancel item of the row menu");
    assertTrue(cancel.get("enabled").getAsBoolean(), "Cancel is enabled for the selected job");
    runItem(ProgressView.ID + ".popup", cancel).get(60, TimeUnit.SECONDS);
    assertTrue(work.join(10000, null), "the job ends");
    assertTrue(work.canceled.get(), "the menu canceled the job");
  }

  @Test
  @Order(14)
  void aKeepOneJobReplacesTheKeptJobsOfItsFamily() throws Exception {
    Grouped first = new Grouped("Export one", "exports");
    first.schedule();
    assertTrue(first.join(10000, null), "the first job ends");
    Grouped other = new Grouped("Other kept", "imports");
    other.setProperty(IProgressConstants.KEEPONE_PROPERTY, null);
    other.setProperty(IProgressConstants.KEEP_PROPERTY, Boolean.TRUE);
    other.schedule();
    assertTrue(other.join(10000, null), "the other job ends");
    assertTrue(ProgressManager.getInstance().getJobs().contains(first), "the first job is kept");

    Grouped second = new Grouped("Export two", "exports");
    second.schedule();
    assertTrue(second.join(10000, null), "the second job ends");
    assertFalse(ProgressManager.getInstance().getJobs().contains(first), "the kept job of the family is replaced");
    assertTrue(ProgressManager.getInstance().getJobs().contains(second), "the new job is kept");
    assertTrue(ProgressManager.getInstance().getJobs().contains(other), "the job of another family is kept");
    ProgressManager.getInstance().removeFinished(second);
    ProgressManager.getInstance().removeFinished(other);
  }

  @Test
  @Order(15)
  void escapeInTheDialogCancelsTheJobAndLeavesThePreference() throws Exception {
    setRunInBackground(false);
    try {
      Work work = new Work("Escaped export");
      work.schedule();
      String dialog = dialogShell("Escaped export");
      h.await("the job to start", v -> work.started.getCount() == 0);
      escape(dialog);
      assertTrue(work.join(10000, null), "the job ends after the Escape");
      assertTrue(work.canceled.get(), "Escape asked the job to stop");
      h.await("the dialog closed", v -> closedQuietly(dialog));
      assertFalse(runInBackground(), "Escape, as Cancel, leaves the preference");
    } finally {
      setRunInBackground(true);
    }
  }

  /** A job put to sleep while it waits (Job.sleep), retried while the pool takes the job before it sleeps. */
  static Job sleepingJob(String name, java.util.concurrent.CountDownLatch ran) throws Exception {
    // only the job that was put to sleep counts its run: a job that started before sleep() is done and replaced
    java.util.concurrent.atomic.AtomicReference<Job> asleep = new java.util.concurrent.atomic.AtomicReference<>();
    for (int attempt = 0; attempt < 200; attempt++) {
      Job job = new Job(name) {
        @Override
        protected IStatus run(IProgressMonitor monitor) {
          if (asleep.get() == this)
            ran.countDown();
          return Status.OK_STATUS;
        }
      };
      job.schedule();
      if (job.sleep()) {
        asleep.set(job);
        return job;
      }
      job.join(5000, null);
    }
    throw new AssertionError("no job could be put to sleep");
  }

  @Test
  @Order(30)
  void aJobThatGoesToSleepWhileShownStaysWithTheSleepingOperations() throws Exception {
    ProgressManager pm = ProgressManager.getInstance();
    boolean before = ProgressManager.showSystemJobs();
    try {
      // "Show sleeping and system operations" on: ProgressManager.sleepJobInfo refreshes the job (as sleeping)
      pm.setShowSystemJobs(true);
      java.util.concurrent.CountDownLatch ran = new java.util.concurrent.CountDownLatch(1);
      Job sleeper = sleepingJob("Sleeper shown", ran);
      assertEquals(Job.SLEEPING, sleeper.getState());
      assertTrue(pm.getJobs().contains(sleeper), "the sleeping job stays listed");
      assertEquals("Sleeper shown (Sleeping)", ProgressInfoItem.nameText(sleeper, null));
      // the views are filled again without the sleeping jobs when the preference changes (getJobInfos)
      pm.setShowSystemJobs(false);
      assertFalse(pm.getJobs().contains(sleeper), "not listed once the view is filled again");
      // woken up, it runs
      sleeper.wakeUp();
      assertTrue(ran.await(10, java.util.concurrent.TimeUnit.SECONDS), "the woken job runs");
      assertTrue(sleeper.join(10000, null));

      // the preference off: the job is removed from the view when it goes to sleep
      java.util.concurrent.CountDownLatch ran2 = new java.util.concurrent.CountDownLatch(1);
      Job hidden = sleepingJob("Sleeper hidden", ran2);
      assertFalse(pm.getJobs().contains(hidden), "removed when it goes to sleep");
      // a sleeping job is canceled at once (it does not run)
      assertTrue(hidden.cancel());
      assertTrue(hidden.join(1000, null), "the canceled sleeping job is done");
      assertEquals(1, ran2.getCount(), "the canceled job did not run");
      assertEquals(IStatus.CANCEL, hidden.getResult().getSeverity());
    } finally {
      pm.setShowSystemJobs(before);
    }
  }
}
