package com.unitesk.requality.vscode.ui.navigator;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.commands.EvaluationContext;
import com.unitesk.requality.vscode.ui.commands.Expressions;
import com.unitesk.requality.vscode.ui.jface.action.GroupMarker;
import com.unitesk.requality.vscode.ui.jface.action.IMenuManager;
import com.unitesk.requality.vscode.ui.jface.action.Separator;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredViewer;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;
import com.unitesk.requality.vscode.ui.workbench.IActionBars;
import com.unitesk.requality.vscode.ui.workbench.actions.ActionContext;

/**
 * {@code org.eclipse.ui.navigator.NavigatorActionService}: the action providers (plugin.xml {@code actionProvider})
 * of a navigator viewer — nested in the content extensions bound to the viewer ({@code viewerContentBinding}) or
 * bound by {@code viewerActionBinding} — fill its context menu (insertion points of the viewer's
 * {@code popupMenu}) and action bars, each one only for selections its enablement accepts.
 */
public class NavigatorActionService {
  private static final String NAVIGATOR_CONTENT = "org.eclipse.ui.navigator.navigatorContent";
  private static final String NAVIGATOR_VIEWER = "org.eclipse.ui.navigator.viewer";

  private static final class Descriptor {
    final IConfigurationElement element;
    final IConfigurationElement parent;
    CommonActionProvider provider;
    boolean failed;

    Descriptor(IConfigurationElement element, IConfigurationElement parent) {
      this.element = element;
      this.parent = parent;
    }

    /** The provider's enablement, else (nested provider) the possibleChildren / enablement of its content. */
    IConfigurationElement enablement() {
      IConfigurationElement[] e = element.getChildren("enablement");
      if (e.length > 0)
        return e[0];
      if (parent != null)
        for (String n : new String[] { "possibleChildren", "enablement", "triggerPoints" }) {
          IConfigurationElement[] p = parent.getChildren(n);
          if (p.length > 0)
            return p[0];
        }
      return null;
    }

    boolean isEnabledFor(IStructuredSelection selection) {
      IConfigurationElement en = enablement();
      if (en == null || selection == null)
        return en == null || selection != null;
      if (selection.isEmpty()) {
        // an empty selection: the enablement is evaluated on an empty list (e.g. adapt Collection with count 0)
        EvaluationContext ctx = new EvaluationContext(null, java.util.Collections.emptyList());
        ctx.addVariable("selection", selection);
        return Expressions.evaluateContainer(en, ctx);
      }
      for (Object o : selection.toList()) {
        // VSCODE: the whole selection is the variable "selection" (enablement "with variable=selection", as in Eclipse).
        EvaluationContext ctx = new EvaluationContext(null, o);
        ctx.addVariable("selection", selection);
        if (!Expressions.evaluateContainer(en, ctx))
          return false;
      }
      return true;
    }
  }

  private final String viewerId;
  private final ICommonViewerSite site;
  private final StructuredViewer viewer;
  private final INavigatorContentService contentService;
  private List<Descriptor> descriptors;

  public NavigatorActionService(ICommonViewerSite site, StructuredViewer viewer, INavigatorContentService contentService) {
    this.viewerId = site == null ? null : site.getId();
    this.site = site;
    this.viewer = viewer;
    this.contentService = contentService;
  }

  /** A navigator of a part that has no action providers (viewer without site). */
  public NavigatorActionService() {
    this(null, null, null);
  }

  public static List<Pattern> patterns(String viewerId, String binding, String include) {
    List<Pattern> out = new ArrayList<>();
    for (IConfigurationElement b : ExtensionRegistry.get().getConfigurationElementsFor(NAVIGATOR_VIEWER)) {
      if (!binding.equals(b.getName()) || !viewerId.equals(b.getAttribute("viewerId")))
        continue;
      for (IConfigurationElement inc : b.getChildren("includes"))
        for (IConfigurationElement x : inc.getChildren(include))
          if (x.getAttribute("pattern") != null)
            try {
              out.add(Pattern.compile(x.getAttribute("pattern")));
            } catch (java.util.regex.PatternSyntaxException e) {
              // Eclipse skips an invalid binding pattern (logged), the other bindings still apply.
              RLogger.logError(e);
            }
    }
    return out;
  }

  public static boolean matches(List<Pattern> ps, String id) {
    if (id != null)
      for (Pattern p : ps)
        if (p.matcher(id).matches())
          return true;
    return false;
  }

  private synchronized List<Descriptor> descriptors() {
    if (descriptors == null) {
      descriptors = new ArrayList<>();
      if (viewerId == null)
        return descriptors;
      List<Pattern> content = patterns(viewerId, "viewerContentBinding", "contentExtension");
      List<Pattern> actions = patterns(viewerId, "viewerActionBinding", "actionExtension");
      for (IConfigurationElement e : ExtensionRegistry.get().getConfigurationElementsFor(NAVIGATOR_CONTENT)) {
        if ("navigatorContent".equals(e.getName()) && matches(content, e.getAttribute("id"))) {
          for (IConfigurationElement a : e.getChildren("actionProvider"))
            descriptors.add(new Descriptor(a, e));
        } else if ("actionProvider".equals(e.getName()) && matches(actions, e.getAttribute("id"))) {
          descriptors.add(new Descriptor(e, null));
        }
      }
    }
    return descriptors;
  }

  private CommonActionProvider provider(Descriptor d) {
    synchronized (d) {
      if (d.provider == null && !d.failed) {
        try {
          CommonActionProvider p = (CommonActionProvider) d.element.createExecutableExtension("class");
          String id = d.element.getAttribute("id");
          p.internalInit(new ICommonActionExtensionSite() {
            @Override
            public String getExtensionId() {
              return id;
            }

            @Override
            public ICommonViewerSite getViewSite() {
              return site;
            }

            @Override
            public StructuredViewer getStructuredViewer() {
              return viewer;
            }

            @Override
            public INavigatorContentService getContentService() {
              return contentService;
            }
          });
          d.provider = p;
        } catch (CoreException | ClassCastException e) {
          // Eclipse-internal providers (org.eclipse.ui.internal.navigator.resources.actions.*) are not ported.
          d.failed = true;
          RLogger.logInfo("Navigator action provider " + d.element.getAttribute("class") + " is not available");
        }
      }
      return d.provider;
    }
  }

  private IStructuredSelection selection() {
    ISelection s = site == null || site.getSelectionProvider() == null ? null : site.getSelectionProvider().getSelection();
    return s instanceof IStructuredSelection ? (IStructuredSelection) s : StructuredSelection.EMPTY;
  }

  /** NavigatorActionService.prepareMenuForPlatformContributions: the insertion points of the viewer's popupMenu. */
  private void prepareMenu(IMenuManager menu) {
    if (menu.getItems().length > 0 || viewerId == null)
      return;
    for (IConfigurationElement v : ExtensionRegistry.get().getConfigurationElementsFor(NAVIGATOR_VIEWER)) {
      if (!"viewer".equals(v.getName()) || !viewerId.equals(v.getAttribute("viewerId")))
        continue;
      for (IConfigurationElement pm : v.getChildren("popupMenu"))
        for (IConfigurationElement ip : pm.getChildren("insertionPoint")) {
          String name = ip.getAttribute("name");
          menu.add("true".equals(ip.getAttribute("separator")) ? new Separator(name) : new GroupMarker(name));
        }
    }
  }

  public void fillContextMenu(IMenuManager menu) {
    prepareMenu(menu);
    IStructuredSelection sel = selection();
    for (Descriptor d : descriptors()) {
      if (!d.isEnabledFor(sel))
        continue;
      CommonActionProvider p = provider(d);
      if (p == null)
        continue;
      try {
        p.setContext(new ActionContext(sel));
        p.fillContextMenu(menu);
      } catch (RuntimeException e) {
        RLogger.logError(e);
      } finally {
        p.setContext(null);
      }
    }
  }

  /**
   * NavigatorActionService.fillActionBars: the global action handlers are cleared, then the action providers relevant
   * for the selection set theirs (called on every selection change of the viewer, like CommonNavigatorManager).
   */
  public void fillActionBars(IActionBars actionBars) {
    actionBars.clearGlobalActionHandlers();
    IStructuredSelection sel = selection();
    for (Descriptor d : descriptors()) {
      if (!d.isEnabledFor(sel))
        continue;
      CommonActionProvider p = provider(d);
      if (p == null)
        continue;
      try {
        p.setContext(new ActionContext(sel));
        p.fillActionBars(actionBars);
        p.updateActionBars();
      } catch (RuntimeException e) {
        RLogger.logError(e);
      } finally {
        p.setContext(null);
      }
    }
  }

  /**
   * NavigatorActionService.restoreState: the action providers relevant for an empty selection are created and get
   * the memento of the view.
   */
  public void restoreState(com.unitesk.requality.vscode.ui.workbench.IMemento memento) {
    for (Descriptor d : descriptors()) {
      if (!d.isEnabledFor(StructuredSelection.EMPTY))
        continue;
      CommonActionProvider p = provider(d);
      if (p != null)
        try {
          p.restoreState(memento);
        } catch (RuntimeException e) {
          RLogger.logError(e);
        }
    }
  }

  /** NavigatorActionService.saveState: the created action providers save their state in the memento of the view. */
  public void saveState(com.unitesk.requality.vscode.ui.workbench.IMemento memento) {
    for (Descriptor d : descriptors())
      if (d.provider != null)
        try {
          d.provider.saveState(memento);
        } catch (RuntimeException e) {
          RLogger.logError(e);
        }
  }

  public void updateActionBars() {
    for (Descriptor d : descriptors())
      if (d.provider != null)
        d.provider.updateActionBars();
  }

  public void dispose() {
    for (Descriptor d : descriptors())
      if (d.provider != null)
        d.provider.dispose();
  }
}
