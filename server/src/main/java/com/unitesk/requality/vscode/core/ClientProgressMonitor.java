package com.unitesk.requality.vscode.core;

import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonObject;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.operation.IProgressMonitor;

/**
 * Port of {@code EclipseProgressMonitor}: nested Requality tasks are folded into one progress bar shown by the
 * extension in the progress area of the status bar ({@code progress/begin}, {@code progress/report},
 * {@code progress/end}); the job is cancelled from the Progress view (its stop button cancels the monitor; the
 * extension may also send {@code progress/cancel}).
 */
public final class ClientProgressMonitor implements IProgressMonitor {
  private static final Map<String, ClientProgressMonitor> ACTIVE = new ConcurrentHashMap<>();
  private static final int UNKNOWN = -1;
  private static final long REPORT_INTERVAL_MS = 100;

  private final ClientApi client;
  private final String token = UUID.randomUUID().toString();
  private final String title;
  private final boolean cancellable;
  private final boolean prominent;
  private int max = UNKNOWN;
  private int monPos;
  private int reportedPos;
  private final LinkedList<Long> taskStarts = new LinkedList<>();
  private final LinkedList<Long> taskMax = new LinkedList<>();
  private final LinkedList<Long> taskCnt = new LinkedList<>();
  private final LinkedList<String> taskName = new LinkedList<>();
  private boolean inited;
  private boolean begun;
  private volatile boolean canceled;
  private long lastReport;

  /**
   * @param prominent user-visible work (a user job): every job is shown in the progress area of the status bar, as
   *          Eclipse runs jobs in the background (a user job opens the progress dialog of the server when "Always run
   *          in background" is off, see ProgressMonitorFocusJobDialog)
   */
  public ClientProgressMonitor(ClientApi client, String title, boolean cancellable, boolean prominent) {
    this.client = client;
    this.title = title;
    this.cancellable = cancellable;
    this.prominent = prominent;
  }

  /** Handles {@code progress/cancel} from the extension. */
  public static void cancel(String token) {
    ClientProgressMonitor m = ACTIVE.get(token);
    if (m != null)
      m.cancel();
  }

  private synchronized void begin(String message) {
    if (begun)
      return;
    begun = true;
    ACTIVE.put(token, this);
    JsonObject p = new JsonObject();
    p.addProperty("token", token);
    p.addProperty("title", title);
    p.addProperty("message", message);
    p.addProperty("cancellable", cancellable);
    p.addProperty("location", "window");
    client.notify("progress/begin", p);
  }

  private synchronized void report(String message, boolean force) {
    if (!begun)
      begin(message);
    long now = System.currentTimeMillis();
    if (!force && now - lastReport < REPORT_INTERVAL_MS)
      return;
    lastReport = now;
    JsonObject p = new JsonObject();
    p.addProperty("token", token);
    p.addProperty("message", message);
    if (max > 0) {
      int percent = (int) Math.min(100, (long) monPos * 100 / max);
      p.addProperty("percent", percent);
      reportedPos = monPos;
    }
    client.notify("progress/report", p);
  }

  /** Ends the progress in the extension. Safe to call more than once. */
  public synchronized void end() {
    if (!begun)
      return;
    begun = false;
    ACTIVE.remove(token);
    JsonObject p = new JsonObject();
    p.addProperty("token", token);
    client.notify("progress/end", p);
  }

  @Override
  public synchronized void worked(int val) {
    if (taskCnt.isEmpty())
      return;
    taskCnt.addLast(taskCnt.pollLast() + val);
    Long maxv = taskMax.peekLast();
    if (maxv == null || maxv == 0)
      maxv = 1L;
    monPos = (int) (taskCnt.peekLast() * max / maxv);
    if (monPos != reportedPos)
      report(taskName.peekLast(), false);
  }

  @Override
  public synchronized void doneAll() {
    while (!taskName.isEmpty())
      doneTask();
    end();
  }

  @Override
  public synchronized int getRest() {
    if (taskMax.isEmpty())
      return max - monPos;
    return (int) (taskMax.peekLast() - taskCnt.peekLast());
  }

  @Override
  public int getMax() {
    return max;
  }

  @Override
  public synchronized void beginTask(String name, int taskMaxValue) {
    if (canceled)
      return;
    if (!inited) {
      max = Math.max(taskMaxValue, 10000);
      inited = true;
    } else {
      monPos = 0;
    }
    taskMax.addLast((long) taskMaxValue);
    taskName.addLast(name);
    taskCnt.addLast(0L);
    taskStarts.addLast(System.currentTimeMillis());
    report(name, true);
  }

  @Override
  public synchronized void doneTask() {
    int newMon = 0;
    if (!taskStarts.isEmpty()) {
      String name = taskName.pollLast();
      long spent = System.currentTimeMillis() - taskStarts.pollLast();
      RLogger.logDebug("task " + name + " done. Time spend " + spent, 4);
      taskCnt.pollLast();
      taskMax.pollLast();
      if (!taskCnt.isEmpty()) {
        Long peek = taskMax.peekLast();
        newMon = (int) (taskCnt.peekLast() * max / (peek != 0 ? peek : 1));
      }
    }
    if (taskStarts.isEmpty()) {
      inited = false;
      end();
      return;
    }
    monPos = newMon;
    report(taskName.peekLast(), false);
  }

  @Override
  public boolean isCanceled() {
    return canceled;
  }

  @Override
  public void cancel() {
    canceled = true;
  }

  @Override
  public synchronized String getTask() {
    return taskName.isEmpty() ? null : taskName.getFirst();
  }

  @Override
  public boolean isDBLocal() {
    return false;
  }
}
