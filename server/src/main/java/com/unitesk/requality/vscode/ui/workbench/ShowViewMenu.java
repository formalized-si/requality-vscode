package com.unitesk.requality.vscode.ui.workbench;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.unitesk.requality.vscode.core.ServerContext;
import com.unitesk.requality.vscode.ui.workbench.internal.ViewRegistry;
import com.unitesk.requality.vscode.ui.workbench.views.IViewDescriptor;

/**
 * {@code org.eclipse.ui.internal.ShowViewMenu}: the items of Window &gt; Show View. The items are the show view shortcuts
 * of the current perspective and the views opened with Other..., without duplicates, sorted by their labels (the
 * "Other..." entry is the command itself). Views that the registry does not know are not listed.
 *
 * <p>VSCODE: the menu of VS Code is static, so its state is published: for each view of the registry the context key
 * {@code requality.showViewMenu.<view id>} is true when the view is an item of the menu (see {@link #publish()}).
 */
public final class ShowViewMenu {
  /** The prefix of the context keys of the items: {@code requality.showViewMenu.<view id>}. */
  public static final String CONTEXT_KEY_PREFIX = "requality.showViewMenu.";

  /** {@code IIntroConstants.INTRO_VIEW_ID}: the Welcome view is not an item. */
  private static final String INTRO_VIEW_ID = "org.eclipse.ui.internal.introview";

  /** The views opened with Other..., by page. */
  private static final Map<IWorkbenchPage, List<String>> OPENED_VIEWS = new HashMap<>();

  private ShowViewMenu() {
  }

  /** {@code ShowViewMenu.fillMenu}: the items for the page (none if the page has no perspective), sorted by label. */
  public static synchronized List<IViewDescriptor> items(IWorkbenchPage page) {
    List<IViewDescriptor> r = new ArrayList<>();
    if (page == null || page.getPerspective() == null)
      return r;
    List<String> ids = new ArrayList<>(Arrays.asList(page.getShowViewShortcuts()));
    List<String> opened = OPENED_VIEWS.get(page);
    if (opened != null)
      for (String id : opened)
        if (!ids.contains(id))
          ids.add(id);
    for (String id : ids) {
      IViewDescriptor d = INTRO_VIEW_ID.equals(id) ? null : ViewRegistry.INSTANCE.find(id);
      if (d != null && r.stream().noneMatch(x -> x.getId().equals(id)))
        r.add(d);
    }
    Collator collator = Collator.getInstance();
    r.sort((a, b) -> collator.compare(a.getLabel(), b.getLabel()));
    return r;
  }

  /** The view {@code viewId} was opened with Other...: it is an item of the menu of the page from now on. */
  public static synchronized void viewOpened(IWorkbenchPage page, String viewId) {
    List<String> l = OPENED_VIEWS.computeIfAbsent(page, p -> new ArrayList<>());
    if (!l.contains(viewId))
      l.add(viewId);
  }

  /**
   * Publishes the items of the Show View menu of the workbench page: the context key of each view of the registry is
   * true when the view is an item. Called when the perspective changes and when a view is opened with Other...
   */
  public static void publish() {
    if (ServerContext.server() == null)
      return;
    List<String> shown = new ArrayList<>();
    for (IViewDescriptor d : items(Workbench.PAGE))
      shown.add(d.getId());
    for (IViewDescriptor d : ViewRegistry.INSTANCE.getViews())
      ServerContext.client().setContext(CONTEXT_KEY_PREFIX + d.getId(), shown.contains(d.getId()));
  }
}
