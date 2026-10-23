package com.unitesk.requality.vscode.ui.tabbed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.jface.viewers.IFilter;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;

/**
 * {@code org.eclipse.ui.internal.views.properties.tabbed.view.TabbedPropertyRegistry}: the tabs and sections that
 * {@code plugin.xml} contributes to a property contributor ({@code propertyContributor}, {@code propertyTabs},
 * {@code propertySections}), ordered as Eclipse orders them.
 */
public final class PropertyRegistry {
  private static final String TOP = "top";

  /** A tab. */
  public static final class Tab {
    public final String id;
    public final String label;
    public final String category;
    public final String afterTab;

    Tab(String id, String label, String category, String afterTab) {
      this.id = id;
      this.label = label;
      this.category = category;
      this.afterTab = afterTab == null ? TOP : afterTab;
    }
  }

  /** A section: shown in {@code tab} when every selected element is an instance of an input type and passes the filter. */
  public static final class Section {
    public final String id;
    /** Unique key (section ids may repeat in plugin.xml). */
    public final String key;
    public final String tab;
    public final Supplier<ISection> factory;
    public final Class<?>[] inputs;
    public final IFilter filter;
    public final String afterSection;
    public final int enablesFor;

    Section(String id, String key, String tab, Supplier<ISection> factory, Class<?>[] inputs, IFilter filter,
        String afterSection, int enablesFor) {
      this.id = id;
      this.key = key;
      this.tab = tab;
      this.factory = factory;
      this.inputs = inputs;
      this.filter = filter;
      this.afterSection = afterSection == null ? TOP : afterSection;
      this.enablesFor = enablesFor;
    }

    /** SectionDescriptor.appliesTo for the elements of a selection. */
    public boolean accepts(List<?> elements) {
      if (elements.isEmpty())
        return false;
      if (enablesFor > 0 && elements.size() != enablesFor)
        return false;
      for (Object element : elements) {
        if (element == null)
          return false;
        Class<?> mapped = typeMapper != null ? typeMapper.mapType(element) : element.getClass();
        if (mapped == null)
          mapped = element.getClass();
        boolean typeOk = false;
        for (Class<?> c : inputs)
          if (c.isAssignableFrom(mapped) || c.isInstance(element))
            typeOk = true;
        if (!typeOk || filter != null && !filter.select(element))
          return false;
      }
      return true;
    }

    public boolean accepts(Object element) {
      return accepts(Collections.singletonList(element));
    }
  }

  private static final List<Tab> TABS = new CopyOnWriteArrayList<>();
  private static final List<Section> SECTIONS = new CopyOnWriteArrayList<>();
  private static volatile ITypeMapper typeMapper;
  private static volatile boolean labelProvider;
  private static volatile String loadedFor;

  private PropertyRegistry() {
  }

  public static void tab(String id, String label) {
    TABS.add(new Tab(id, label, null, null));
  }

  public static void section(String id, String tab, Supplier<ISection> factory, IFilter filter, String afterSection,
      Class<?>... inputs) {
    SECTIONS.add(new Section(id, id + "#" + SECTIONS.size(), tab, factory, inputs, filter, afterSection, 0));
  }

  public static List<Tab> tabs() {
    return new ArrayList<>(TABS);
  }

  /** Sections in their order within each tab. */
  public static List<Section> sections() {
    return new ArrayList<>(SECTIONS);
  }

  public static ITypeMapper getTypeMapper() {
    return typeMapper;
  }

  /** Whether the contributor declares a label provider (Eclipse shows the title bar only then). */
  public static boolean hasLabelProvider() {
    return labelProvider;
  }

  private static Class<?> load(String className) {
    try {
      return Class.forName(ExtensionRegistry.portedClassName(className));
    } catch (ClassNotFoundException | LinkageError e) {
      return null;
    }
  }

  /** Reads the contributions of {@code contributorId} from plugin.xml (once). */
  public static synchronized void loadFromExtensions(String contributorId) {
    if (contributorId.equals(loadedFor))
      return;
    loadedFor = contributorId;
    TABS.clear();
    SECTIONS.clear();
    ExtensionRegistry reg = ExtensionRegistry.get();
    List<String> categories = new ArrayList<>();
    for (IConfigurationElement c : reg.getConfigurationElementsFor("org.eclipse.ui.views.properties.tabbed.propertyContributor")) {
      if (!contributorId.equals(c.getAttribute("contributorId")))
        continue;
      labelProvider = c.getAttribute("labelProvider") != null;
      if (c.getAttribute("typeMapper") != null) {
        try {
          typeMapper = (ITypeMapper) c.createExecutableExtension("typeMapper");
        } catch (CoreException | ClassCastException e) {
          RLogger.logInfo("Type mapper of " + contributorId + " is not available: " + e.getMessage());
        }
      }
      for (IConfigurationElement cat : c.getChildren("propertyCategory"))
        categories.add(cat.getAttribute("category"));
    }
    List<Tab> tabs = new ArrayList<>();
    for (IConfigurationElement c : reg.getConfigurationElementsFor("org.eclipse.ui.views.properties.tabbed.propertyTabs")) {
      if (!contributorId.equals(c.getAttribute("contributorId")))
        continue;
      for (IConfigurationElement t : c.getChildren("propertyTab"))
        tabs.add(new Tab(t.getAttribute("id"), t.getAttribute("label"), t.getAttribute("category"), t.getAttribute("afterTab")));
    }
    TABS.addAll(sortTabs(tabs, categories));
    // TabbedPropertyRegistry: each section is appended to its tab with TabDescriptor.append.
    List<List<Section>> perTab = new ArrayList<>();
    for (int i = 0; i < TABS.size(); i++)
      perTab.add(new ArrayList<>());
    int n = 0;
    for (IConfigurationElement c : reg.getConfigurationElementsFor("org.eclipse.ui.views.properties.tabbed.propertySections")) {
      if (!contributorId.equals(c.getAttribute("contributorId")))
        continue;
      for (IConfigurationElement s : c.getChildren("propertySection")) {
        Section section = section(s, n++);
        if (section == null)
          continue;
        for (int i = 0; i < TABS.size(); i++)
          if (TABS.get(i).id.equals(section.tab))
            append(perTab.get(i), section);
      }
    }
    for (List<Section> l : perTab)
      SECTIONS.addAll(l);
  }

  private static Section section(IConfigurationElement s, int ordinal) {
    String cls = s.getAttribute("class");
    if (load(cls) == null) {
      RLogger.logInfo("Property section " + cls + " is not available");
      return null;
    }
    List<Class<?>> inputs = new ArrayList<>();
    for (IConfigurationElement in : s.getChildren("input")) {
      Class<?> c = load(in.getAttribute("type"));
      if (c != null)
        inputs.add(c);
    }
    IFilter filter = null;
    if (s.getAttribute("filter") != null) {
      try {
        filter = (IFilter) s.createExecutableExtension("filter");
      } catch (CoreException | ClassCastException e) {
        RLogger.logInfo("Section filter " + s.getAttribute("filter") + " is not available");
        return null;
      }
    }
    int enablesFor = 0;
    try {
      if (s.getAttribute("enablesFor") != null)
        enablesFor = Integer.parseInt(s.getAttribute("enablesFor"));
    } catch (NumberFormatException e) {
      enablesFor = 0;
    }
    Supplier<ISection> factory = () -> {
      try {
        return (ISection) s.createExecutableExtension("class");
      } catch (CoreException e) {
        RLogger.logError(e);
        return null;
      }
    };
    return new Section(s.getAttribute("id"), s.getAttribute("id") + "#" + ordinal, s.getAttribute("tab"), factory,
        inputs.toArray(new Class<?>[0]), filter, s.getAttribute("afterSection"), enablesFor);
  }

  /** TabDescriptor.append / insertSectionDescriptor. */
  private static void append(List<Section> sections, Section target) {
    if (target.afterSection.equals(TOP)) {
      sections.add(0, target);
      return;
    }
    for (int i = 0; i < sections.size(); i++) {
      Section d = sections.get(i);
      if (target.afterSection.equals(d.id)) {
        sections.add(i + 1, target);
        return;
      } else if (d.afterSection.equals(target.id)) {
        sections.add(i, target);
        return;
      }
    }
    sections.add(target);
  }

  /** TabbedPropertyRegistry.sortTabDescriptorsByCategory and sortTabDescriptorsByAfterTab. */
  static List<Tab> sortTabs(List<Tab> tabs, List<String> categories) {
    List<Tab> byCategory = new ArrayList<>();
    for (String cat : categories)
      for (Tab t : tabs)
        if (cat.equals(t.category))
          byCategory.add(t);
    for (Tab t : tabs)
      if (!byCategory.contains(t))
        byCategory.add(t);
    List<Tab> sorted = new ArrayList<>();
    int index = 0;
    while (index < byCategory.size()) {
      String category = byCategory.get(index).category;
      int end = index;
      while (end < byCategory.size() && java.util.Objects.equals(byCategory.get(end).category, category))
        end++;
      List<Tab> list = new ArrayList<>();
      for (int j = index; j < end; j++) {
        Tab t = byCategory.get(j);
        if (t.afterTab.equals(TOP))
          list.add(0, t);
        else
          list.add(t);
      }
      // Collections.sort is stable; the comparator is the Eclipse one (not transitive, like in Eclipse).
      list.sort((one, two) -> two.afterTab.equals(one.id) ? -1 : one.afterTab.equals(two.id) ? 1 : 0);
      sorted.addAll(list);
      index = end;
    }
    return sorted;
  }
}
