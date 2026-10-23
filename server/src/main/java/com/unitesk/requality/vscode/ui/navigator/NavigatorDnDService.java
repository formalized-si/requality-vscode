package com.unitesk.requality.vscode.ui.navigator;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.commands.EvaluationContext;
import com.unitesk.requality.vscode.ui.commands.Expressions;
import com.unitesk.requality.vscode.ui.jface.util.LocalSelectionTransfer;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;
import com.unitesk.requality.vscode.ui.swt.dnd.DND;
import com.unitesk.requality.vscode.ui.swt.dnd.DropTargetEvent;
import com.unitesk.requality.vscode.ui.swt.dnd.TransferData;

/**
 * {@code org.eclipse.ui.internal.navigator.dnd.NavigatorDnDService} with {@code CommonDropAdapter.performDrop}: the
 * drop assistants of the navigator content extensions bound to a viewer (plugin.xml {@code dropAssistant} with its
 * {@code possibleDropTargets}) validate and perform a drop of the dragged selection (the local selection transfer)
 * on a target element.
 */
public final class NavigatorDnDService {
  private static final String NAVIGATOR_CONTENT = "org.eclipse.ui.navigator.navigatorContent";
  /** The type id of the local selection transfer (LocalSelectionTransfer). */
  private static final int LOCAL_SELECTION = 1;
  private final String viewerId;

  public NavigatorDnDService(String viewerId) {
    this.viewerId = viewerId;
  }

  /** The drop assistants whose possible drop targets accept the target, in plugin.xml order. */
  public List<CommonDropAdapterAssistant> findCommonDropAdapterAssistants(Object target) {
    List<CommonDropAdapterAssistant> r = new ArrayList<>();
    List<Pattern> content = NavigatorActionService.patterns(viewerId, "viewerContentBinding", "contentExtension");
    for (IConfigurationElement e : ExtensionRegistry.get().getConfigurationElementsFor(NAVIGATOR_CONTENT)) {
      if (!"navigatorContent".equals(e.getName()) || !NavigatorActionService.matches(content, e.getAttribute("id")))
        continue;
      for (IConfigurationElement da : e.getChildren("dropAssistant")) {
        IConfigurationElement[] targets = da.getChildren("possibleDropTargets");
        if (targets.length > 0 && !Expressions.evaluateContainer(targets[0], new EvaluationContext(null, target)))
          continue;
        try {
          Object o = da.createExecutableExtension("class");
          if (o instanceof CommonDropAdapterAssistant)
            r.add((CommonDropAdapterAssistant) o);
        } catch (CoreException ex) {
          // platform assistants that are not provided (e.g. working sets) are skipped
          RLogger.logDebug("Drop assistant " + da.getAttribute("class") + " is not available", 5);
        }
      }
    }
    return r;
  }

  /**
   * Drops the selection on the target: the first assistant that validates the drop handles it. The status of the
   * handling, or a cancel status when no assistant accepts the drop.
   */
  public IStatus performDrop(IStructuredSelection selection, Object target, int operation) {
    LocalSelectionTransfer transfer = LocalSelectionTransfer.getTransfer();
    transfer.setSelection(selection);
    transfer.setSelectionSetTime(System.currentTimeMillis());
    TransferData type = new TransferData(LOCAL_SELECTION);
    IStatus rejected = null;
    try {
      for (CommonDropAdapterAssistant a : findCommonDropAdapterAssistants(target)) {
        if (!a.isSupportedType(type))
          continue;
        IStatus valid = a.validateDrop(target, operation, type);
        if (valid == null || !valid.isOK()) {
          if (rejected == null && valid != null && valid.getSeverity() == IStatus.ERROR)
            rejected = valid;
          continue;
        }
        CommonDropAdapter adapter = new CommonDropAdapter();
        adapter.setCurrentTarget(target);
        adapter.setCurrentOperation(operation);
        adapter.setCurrentTransfer(type);
        DropTargetEvent event = new DropTargetEvent(target);
        event.detail = operation;
        event.currentDataType = type;
        event.data = selection;
        IStatus s = a.handleDrop(adapter, event, target);
        return s == null ? Status.OK_STATUS : s;
      }
      // VSCODE: Eclipse shows a rejected drop by the "no drop" cursor while dragging; VS Code asks only on the
      // drop, so the reason of the assistant that rejected it is returned (and shown by the extension).
      return rejected != null ? rejected : Status.CANCEL_STATUS;
    } finally {
      transfer.setSelection(null);
    }
  }

  public static int defaultOperation() {
    return DND.DROP_MOVE;
  }
}
