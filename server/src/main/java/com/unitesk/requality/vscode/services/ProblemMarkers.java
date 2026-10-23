package com.unitesk.requality.vscode.services;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.unitesk.requality.core.lock.LockType;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.vscode.RequalityServer;
import com.unitesk.requality.vscode.port.eclipse.core.EclipseTracker;
import com.unitesk.requality.vscode.ui.resources.IMarker;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.MarkerStore;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IPath;

/**
 * Problem markers ({@code org.eclipse.core.resources.problemmarker} and subtypes, e.g. the Requality checker marker)
 * shown in the VS Code Problems panel: every change of a resource's markers republishes the diagnostics of that
 * file ({@code problems/set}), batched like the Eclipse Problems view refresh.
 */
public final class ProblemMarkers implements Services.Service {
  private static RequalityServer server;
  private static final Map<IPath, IResource> PENDING = new LinkedHashMap<>();
  private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r, "requality-problems");
    t.setDaemon(true);
    return t;
  });
  private static boolean scheduled;

  /** Whether a node has problem markers (on its marker resource: the document file or the node file). */
  public static boolean hasMarkers(TreeNode node) {
    try {
      IResource res = EclipseTracker.getMarkerResourceByNode(node, LockType.TRY_LOCK);
      return res != null && res.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_ZERO).length > 0;
    } catch (Exception e) {
      return false;
    }
  }

  @Override
  public void register(RequalityServer srv) {
    server = srv;
    MarkerStore.addListener(ProblemMarkers::changed);
  }

  private static void changed(List<IResource> resources) {
    synchronized (PENDING) {
      for (IResource r : resources)
        PENDING.put(r.getFullPath(), r);
      if (scheduled)
        return;
      scheduled = true;
    }
    TIMER.schedule(ProblemMarkers::publish, 100, TimeUnit.MILLISECONDS);
  }

  private static void publish() {
    Map<IPath, IResource> batch;
    synchronized (PENDING) {
      batch = new LinkedHashMap<>(PENDING);
      PENDING.clear();
      scheduled = false;
    }
    if (server == null)
      return;
    JsonArray files = new JsonArray();
    for (IResource r : batch.values()) {
      IPath loc = r.getLocation();
      if (loc == null)
        continue;
      JsonObject f = new JsonObject();
      f.addProperty("uri", new File(loc.toOSString()).toURI().toString());
      JsonArray problems = new JsonArray();
      try {
        for (IMarker m : r.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_ZERO)) {
          JsonObject p = new JsonObject();
          p.addProperty("message", m.getAttribute(IMarker.MESSAGE, ""));
          p.addProperty("severity", m.getAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO));
          p.addProperty("line", m.getAttribute(IMarker.LINE_NUMBER, -1));
          p.addProperty("location", m.getAttribute(IMarker.LOCATION, ""));
          p.addProperty("type", m.getType());
          problems.add(p);
        }
      } catch (CoreException e) {
        RLogger.logError(e);
      }
      f.add("problems", problems);
      files.add(f);
    }
    if (files.size() == 0)
      return;
    JsonObject p = new JsonObject();
    p.add("files", files);
    server.client().notify("problems/set", p);
  }
}
