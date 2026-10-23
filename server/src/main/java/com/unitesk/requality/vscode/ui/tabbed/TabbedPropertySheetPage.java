package com.unitesk.requality.vscode.ui.tabbed;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.ScrolledComposite;
import com.unitesk.requality.vscode.ui.workbench.IPageSite;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPart;
import com.unitesk.requality.vscode.ui.workbench.PageSite;

/**
 * {@code org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetPage}: a title, the tab list on the left and the
 * sections of the selected tab on the right. Sections are created once and reused, as in Eclipse.
 */
public class TabbedPropertySheetPage implements com.unitesk.requality.vscode.ui.workbench.IPropertySheetPage {
  private final ITabbedPropertySheetPageContributor contributor;
  private final TabbedPropertySheetWidgetFactory factory = new TabbedPropertySheetWidgetFactory();
  private Composite control;
  private Label title;
  private com.unitesk.requality.vscode.ui.swt.widgets.List tabList;
  private Composite tabArea;
  private final Map<String, ISection> sectionInstances = new HashMap<>();
  private final Map<String, Composite> sectionComposites = new HashMap<>();
  private final Map<String, Composite> tabComposites = new HashMap<>();
  private List<String> currentTabs = new ArrayList<>();
  private Map<String, List<PropertyRegistry.Section>> currentSections = new LinkedHashMap<>();
  private String selectedTab;
  private IWorkbenchPart part;
  private ISelection selection;

  public TabbedPropertySheetPage(ITabbedPropertySheetPageContributor contributor) {
    this.contributor = contributor;
  }

  public TabbedPropertySheetWidgetFactory getWidgetFactory() {
    return factory;
  }

  private com.unitesk.requality.vscode.ui.workbench.IPageSite pageSite;

  @Override
  public void init(com.unitesk.requality.vscode.ui.workbench.IPageSite site) {
    pageSite = site;
  }

  @Override
  public void setActionBars(com.unitesk.requality.vscode.ui.workbench.IActionBars actionBars) {
  }

  @Override
  public void setFocus() {
  }

  @Override
  public void createControl(Composite parent) {
    control = new Composite(parent, SWT.NONE);
    GridLayout gl = new GridLayout(2, false);
    gl.marginWidth = 0;
    gl.marginHeight = 0;
    gl.horizontalSpacing = 0;
    control.setLayout(gl);
    control.setLayoutData(new GridData(GridData.FILL_BOTH));
    if (PropertyRegistry.hasLabelProvider()) {
      // Eclipse shows the title bar only when the contributor has a label provider.
      title = new Label(control, SWT.NONE);
      GridData td = new GridData(GridData.FILL_HORIZONTAL);
      td.horizontalSpan = 2;
      title.setLayoutData(td);
      title.setCssClass("properties-title");
    }
    tabList = new com.unitesk.requality.vscode.ui.swt.widgets.List(control, SWT.SINGLE);
    GridData ld = new GridData(GridData.FILL_VERTICAL);
    ld.widthHint = 120;
    tabList.setLayoutData(ld);
    tabList.setCssClass("properties-tabs");
    tabList.addListener(SWT.Selection, e -> {
      int i = tabList.getSelectionIndex();
      if (i >= 0 && i < currentTabs.size())
        showTab(currentTabs.get(i));
    });
    ScrolledComposite sc = new ScrolledComposite(control, SWT.V_SCROLL | SWT.H_SCROLL);
    sc.setLayoutData(new GridData(GridData.FILL_BOTH));
    sc.setExpandHorizontal(true);
    sc.setExpandVertical(true);
    tabArea = new Composite(sc, SWT.NONE);
    GridLayout al = new GridLayout();
    al.marginWidth = 6;
    al.marginHeight = 4;
    tabArea.setLayout(al);
    sc.setContent(tabArea);
  }

  @Override
  public Control getControl() {
    return control;
  }

  /**
   * {@code org.eclipse.ui.part.Page#getSite()}: the page site given by the PropertySheet (PageBookView.initPage), else
   * one over the site of the part whose selection the page shows.
   */
  @Override
  public IPageSite getSite() {
    if (pageSite != null)
      return pageSite;
    return new PageSite(part.getSite());
  }

  public IWorkbenchPart getPart() {
    return part;
  }

  public ISelection getCurrentSelection() {
    return selection;
  }

  /** Ids of the tabs shown for the current selection. */
  public List<String> getCurrentTabIds() {
    return new ArrayList<>(currentTabs);
  }

  public String getSelectedTabId() {
    return selectedTab;
  }

  /** {@code org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetPage#getCurrentTab()}: the selected tab, or null. */
  public TabContents getCurrentTab() {
    if (selectedTab == null)
      return null;
    final String id = selectedTab;
    return new TabContents() {
      @Override
      public String getId() {
        return id;
      }

      @Override
      public String getLabel() {
        return labelOf(id);
      }

      @Override
      public ISection[] getSections() {
        List<ISection> out = new ArrayList<>();
        for (PropertyRegistry.Section s : sectionsOf(id)) {
          ISection section = sectionInstances.get(s.key);
          if (section != null)
            out.add(section);
        }
        return out.toArray(new ISection[0]);
      }
    };
  }

  /** Workbench selection changed: recompute the tabs for the selected elements and refresh the sections. */
  @Override
  public void selectionChanged(IWorkbenchPart part, ISelection selection) {
    this.part = part;
    this.selection = selection;
    List<Object> elements = new ArrayList<>();
    if (selection instanceof IStructuredSelection)
      for (Object o : ((IStructuredSelection) selection).toList())
        elements.add(o);
    Object element = elements.isEmpty() ? null : elements.get(0);
    Map<String, List<PropertyRegistry.Section>> byTab = new LinkedHashMap<>();
    for (PropertyRegistry.Tab t : PropertyRegistry.tabs()) {
      List<PropertyRegistry.Section> list = new ArrayList<>();
      for (PropertyRegistry.Section s : PropertyRegistry.sections())
        if (s.tab.equals(t.id) && s.accepts(elements))
          list.add(s);
      if (!list.isEmpty())
        byTab.put(t.id, list);
    }
    List<String> tabs = new ArrayList<>(byTab.keySet());
    boolean tabsChanged = !tabs.equals(currentTabs);
    currentTabs = tabs;
    currentSections = byTab;
    if (title != null)
      title.setText(titleOf(element));
    if (tabsChanged) {
      List<String> labels = new ArrayList<>();
      for (String id : tabs)
        labels.add(labelOf(id));
      tabList.setItems(labels.toArray(new String[0]));
    }
    String next = selectedTab != null && tabs.contains(selectedTab) ? selectedTab : (tabs.isEmpty() ? null : tabs.get(0));
    for (List<PropertyRegistry.Section> l : byTab.values())
      for (PropertyRegistry.Section s : l)
        if (sectionInstances.containsKey(s.key))
          sectionInstances.get(s.key).setInput(part, selection);
    showTab(next);
  }

  private static String labelOf(String tabId) {
    for (PropertyRegistry.Tab t : PropertyRegistry.tabs())
      if (t.id.equals(tabId))
        return t.label;
    return tabId;
  }

  protected String titleOf(Object element) {
    return element == null ? "" : element.toString();
  }

  private void showTab(String tabId) {
    if (selectedTab != null && !selectedTab.equals(tabId)) {
      for (PropertyRegistry.Section s : sectionsOf(selectedTab))
        if (sectionInstances.containsKey(s.key))
          sectionInstances.get(s.key).aboutToBeHidden();
    }
    for (Map.Entry<String, Composite> e : tabComposites.entrySet()) {
      boolean on = e.getKey().equals(tabId);
      ((GridData) e.getValue().getLayoutData()).exclude = !on;
      e.getValue().setVisible(on);
    }
    selectedTab = tabId;
    if (tabId == null) {
      tabArea.layout();
      return;
    }
    tabList.setSelection(currentTabs.indexOf(tabId));
    Composite tc = tabComposites.get(tabId);
    if (tc == null) {
      tc = new Composite(tabArea, SWT.NONE);
      GridLayout gl = new GridLayout();
      gl.marginWidth = 0;
      gl.marginHeight = 0;
      tc.setLayout(gl);
      tc.setLayoutData(new GridData(GridData.FILL_BOTH));
      tabComposites.put(tabId, tc);
    }
    // Sections of this tab for the current element; other cached sections of the tab are hidden.
    List<PropertyRegistry.Section> active = sectionsOf(tabId);
    for (Map.Entry<String, Composite> e : sectionComposites.entrySet()) {
      if (e.getValue().getParent() != tc)
        continue;
      boolean on = active.stream().anyMatch(s -> s.key.equals(e.getKey()));
      ((GridData) e.getValue().getLayoutData()).exclude = !on;
      e.getValue().setVisible(on);
    }
    for (PropertyRegistry.Section s : active) {
      ISection section = sectionInstances.get(s.key);
      if (section == null) {
        section = s.factory.get();
        sectionInstances.put(s.key, section);
        Composite sc = new Composite(tc, SWT.NONE);
        GridLayout gl = new GridLayout();
        gl.marginWidth = 0;
        gl.marginHeight = 2;
        sc.setLayout(gl);
        sectionComposites.put(s.key, sc);
        section.createControls(sc, this);
        sc.setLayoutData(new GridData(section.shouldUseExtraSpace() || active.size() == 1 ? GridData.FILL_BOTH : GridData.FILL_HORIZONTAL));
        section.setInput(part, selection);
      }
      section.aboutToBeShown();
      section.refresh();
    }
    tc.setVisible(true);
    ((GridData) tc.getLayoutData()).exclude = false;
    tabArea.layout();
  }

  private List<PropertyRegistry.Section> sectionsOf(String tabId) {
    List<PropertyRegistry.Section> l = currentSections.get(tabId);
    return l == null ? new ArrayList<>() : l;
  }

  public void refresh() {
    for (PropertyRegistry.Section s : sectionsOf(selectedTab))
      if (sectionInstances.containsKey(s.key))
        sectionInstances.get(s.key).refresh();
  }

  public void resizeScrolledComposite() {
    tabArea.layout();
  }

  public String getContributorId() {
    return contributor == null ? "" : contributor.getContributorId();
  }

  @Override
  public void dispose() {
    for (ISection s : sectionInstances.values())
      s.dispose();
    sectionInstances.clear();
    if (control != null && !control.isDisposed())
      control.dispose();
  }
}
