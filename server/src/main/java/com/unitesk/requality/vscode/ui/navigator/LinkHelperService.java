package com.unitesk.requality.vscode.ui.navigator;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.commands.EvaluationContext;
import com.unitesk.requality.vscode.ui.commands.Expressions;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;
import com.unitesk.requality.vscode.ui.workbench.IEditorInput;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;

/**
 * {@code org.eclipse.ui.navigator.LinkHelperService}: the link helpers of plugin.xml
 * ({@code org.eclipse.ui.navigator.linkHelper}) that "Link with Editor" of a navigator uses, chosen by their
 * editorInputEnablement / selectionEnablement expressions.
 */
public final class LinkHelperService {
  private static final class Descriptor {
    final IConfigurationElement element;
    ILinkHelper helper;
    boolean failed;

    Descriptor(IConfigurationElement element) {
      this.element = element;
    }

    synchronized ILinkHelper get() {
      if (helper == null && !failed) {
        try {
          helper = (ILinkHelper) element.createExecutableExtension("class");
        } catch (CoreException | ClassCastException e) {
          failed = true;
          RLogger.logInfo("Link helper " + element.getAttribute("class") + " is not available");
        }
      }
      return helper;
    }

    boolean enabledFor(String child, Object o) {
      IConfigurationElement[] en = element.getChildren(child);
      return en.length > 0 && Expressions.evaluateContainer(en[0], new EvaluationContext(null, o));
    }
  }

  private static List<Descriptor> descriptors;

  private LinkHelperService() {
  }

  private static synchronized List<Descriptor> descriptors() {
    if (descriptors == null) {
      descriptors = new ArrayList<>();
      for (IConfigurationElement e : ExtensionRegistry.get().getConfigurationElementsFor("org.eclipse.ui.navigator.linkHelper"))
        if ("linkHelper".equals(e.getName()))
          descriptors.add(new Descriptor(e));
    }
    return descriptors;
  }

  /** The navigator selection for an editor input (first enabled helper with a non-empty answer). */
  public static IStructuredSelection findSelection(IEditorInput input) {
    for (Descriptor d : descriptors()) {
      if (!d.enabledFor("editorInputEnablement", input))
        continue;
      ILinkHelper h = d.get();
      if (h == null)
        continue;
      IStructuredSelection s = h.findSelection(input);
      if (s != null && !s.isEmpty())
        return s;
    }
    return null;
  }

  /** Activates the editors linked to a navigator selection. */
  public static void activateEditor(IWorkbenchPage page, IStructuredSelection selection) {
    if (selection == null || selection.isEmpty())
      return;
    for (Descriptor d : descriptors()) {
      if (!d.enabledFor("selectionEnablement", selection.getFirstElement()))
        continue;
      ILinkHelper h = d.get();
      if (h != null)
        h.activateEditor(page, selection);
    }
  }
}
