package com.unitesk.requality.vscode.ui.runtime;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.unitesk.requality.core.log.RLogger;

/**
 * The extension registry of the Requality plug-in: the {@code plugin.xml} of the Eclipse plug-in (copied to the
 * server resources), with the classes of the Eclipse layer resolved to their ports.
 */
public final class ExtensionRegistry implements IExtensionRegistry {
  public static final String PLUGIN_ID = "com.unitesk.requality";
  private static volatile ExtensionRegistry instance;
  private static volatile File pluginXml;

  private final Map<String, List<Ext>> byPoint = new LinkedHashMap<>();

  /** Location of plugin.xml (set by the bootstrap; defaults to {@code <resources>/plugin.xml}). */
  public static void setPluginXml(File f) {
    pluginXml = f;
    instance = null;
  }

  public static ExtensionRegistry get() {
    ExtensionRegistry r = instance;
    if (r == null) {
      synchronized (ExtensionRegistry.class) {
        if (instance == null)
          instance = new ExtensionRegistry(pluginXml);
        r = instance;
      }
    }
    return r;
  }

  /**
   * Contributions of the Eclipse platform plug-ins the Requality plug-in binds to (e.g. the navigator resource action
   * providers matched by {@code org.eclipse.ui.navigator.resources.*}), read after plugin.xml.
   */
  public static final String PLATFORM_XML = "/com/unitesk/requality/vscode/ui/runtime/platform-plugin.xml";

  private ExtensionRegistry(File xml) {
    if (xml == null || !xml.isFile())
      return;
    read(xml.toURI().toString());
    java.net.URL platform = ExtensionRegistry.class.getResource(PLATFORM_XML);
    if (platform != null)
      read(platform.toString());
  }

  private void read(String uri) {
    try {
      DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
      f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      f.setExpandEntityReferences(false);
      Element root = f.newDocumentBuilder().parse(uri).getDocumentElement();
      NodeList exts = root.getChildNodes();
      for (int i = 0; i < exts.getLength(); i++) {
        Node n = exts.item(i);
        if (n instanceof Element && "extension".equals(n.getNodeName())) {
          Element e = (Element) n;
          Ext ext = new Ext(e);
          byPoint.computeIfAbsent(ext.point, k -> new ArrayList<>()).add(ext);
        }
      }
    } catch (Exception e) {
      RLogger.logError(e);
    }
  }

  /**
   * Class of the Eclipse plug-in → class of this server: the Eclipse layer lives in {@code vscode.port}; core classes
   * keep their names.
   */
  public static String portedClassName(String name) {
    if (name == null)
      return null;
    if (name.startsWith("org.eclipse.core.resources.") && name.lastIndexOf('.') == "org.eclipse.core.resources".length())
      return "com.unitesk.requality.vscode.ui.resources." + name.substring("org.eclipse.core.resources.".length());
    // Adapter types of plugin.xml (org.eclipse.core.runtime.adapters): the workbench adapters and resource mappings.
    if (name.startsWith("org.eclipse.core.resources.mapping."))
      return "com.unitesk.requality.vscode.ui.resources.mapping." + name.substring("org.eclipse.core.resources.mapping.".length());
    if (name.startsWith("org.eclipse.ui.model."))
      return "com.unitesk.requality.vscode.ui.workbench.model." + name.substring("org.eclipse.ui.model.".length());
    // VSCODE: every resource is an IAdaptable here, so the enablement "PlatformObject" (SymlinkDecorator) matches resources.
    if (name.equals("org.eclipse.core.runtime.IAdaptable") || name.equals("org.eclipse.core.runtime.PlatformObject"))
      return "com.unitesk.requality.vscode.ui.runtime.IAdaptable";
    if (name.equals("org.eclipse.ui.IWorkbenchPart") || name.equals("org.eclipse.ui.IEditorPart")
        || name.equals("org.eclipse.ui.IViewPart"))
      return "com.unitesk.requality.vscode.ui.workbench." + name.substring("org.eclipse.ui.".length());
    // The resource actions of the navigator (org.eclipse.ui.navigator.resources).
    if (name.startsWith("org.eclipse.ui.internal.navigator.resources.actions."))
      return "com.unitesk.requality.vscode.ui.navigator.resources."
          + name.substring("org.eclipse.ui.internal.navigator.resources.actions.".length());
    if (name.startsWith("org.eclipse.ui.navigator.resources."))
      return "com.unitesk.requality.vscode.ui.navigator.resources."
          + name.substring("org.eclipse.ui.navigator.resources.".length());
    if (name.startsWith("org.eclipse.ui.internal.navigator.resources.workbench."))
      return "com.unitesk.requality.vscode.ui.navigator.resources.workbench."
          + name.substring("org.eclipse.ui.internal.navigator.resources.workbench.".length());
    // the filters and the other classes of org.eclipse.ui.navigator.resources (ResourceNameRegexpFilter)
    if (name.startsWith("org.eclipse.ui.internal.navigator.resources.") && name.lastIndexOf('.') == "org.eclipse.ui.internal.navigator.resources".length())
      return "com.unitesk.requality.vscode.ui.navigator.resources." + name.substring("org.eclipse.ui.internal.navigator.resources.".length());
    if (name.startsWith("org.eclipse.ui.internal.navigator.workingsets."))
      return "com.unitesk.requality.vscode.ui.navigator.workingsets."
          + name.substring("org.eclipse.ui.internal.navigator.workingsets.".length());
    // Working sets of the workbench (org.eclipse.ui.IWorkingSet, ResourceWorkingSetFilter).
    if (name.equals("org.eclipse.ui.IWorkingSet") || name.equals("org.eclipse.ui.IAggregateWorkingSet")
        || name.equals("org.eclipse.ui.ResourceWorkingSetFilter"))
      return "com.unitesk.requality.vscode.ui.workbench." + name.substring("org.eclipse.ui.".length());
    // The new wizards of the IDE (org.eclipse.ui.ide): Project, Folder and File (wizards.newresource).
    if (name.startsWith("org.eclipse.ui.wizards.newresource."))
      return "com.unitesk.requality.vscode.ui.ide.wizards." + name.substring("org.eclipse.ui.wizards.newresource.".length());
    // The preference pages of the workbench (org.eclipse.ui.workbench): Perspectives.
    if (name.startsWith("org.eclipse.ui.internal.dialogs.") && name.lastIndexOf('.') == "org.eclipse.ui.internal.dialogs".length())
      return "com.unitesk.requality.vscode.ui.workbench.dialogs." + name.substring("org.eclipse.ui.internal.dialogs.".length());
    // The property pages of the IDE (org.eclipse.ui.ide): Resource, Project References, Project Natures.
    if (name.startsWith("org.eclipse.ui.internal.ide.dialogs."))
      return "com.unitesk.requality.vscode.ui.ide.dialogs." + name.substring("org.eclipse.ui.internal.ide.dialogs.".length());
    // The lightweight decorators of the IDE (org.eclipse.ui.decorators): LinkedResourceDecorator, SymlinkDecorator, ...
    if (name.startsWith("org.eclipse.ui.internal.ide.") && name.lastIndexOf('.') == "org.eclipse.ui.internal.ide".length())
      return "com.unitesk.requality.vscode.ui.ide." + name.substring("org.eclipse.ui.internal.ide.".length());
    // The import and export wizards of the IDE (org.eclipse.ui.ide): ported to ui.ide.datatransfer.
    if (name.startsWith("org.eclipse.ui.wizards.datatransfer.") || name.startsWith("org.eclipse.ui.internal.wizards.datatransfer."))
      return "com.unitesk.requality.vscode.ui.ide.datatransfer." + name.substring(name.lastIndexOf('.') + 1);
    // Team (org.eclipse.team.ui) and Compare (org.eclipse.compare): the actions and the wizards of the popup menus.
    // Team UI (org.eclipse.team.ui: TeamUI, the history pages and their compare inputs) and the team core history (the revisions).
    if (name.startsWith("org.eclipse.team.ui."))
      return "com.unitesk.requality.vscode.ui.team.ui." + name.substring("org.eclipse.team.ui.".length());
    if (name.startsWith("org.eclipse.team.core.history."))
      return "com.unitesk.requality.vscode.ui.team.core.history." + name.substring("org.eclipse.team.core.history.".length());
    if (name.startsWith("org.eclipse.team.internal.core.history."))
      return "com.unitesk.requality.vscode.ui.team.core.history." + name.substring("org.eclipse.team.internal.core.history.".length());
    if (name.startsWith("org.eclipse.team.internal.core.") && name.lastIndexOf('.') == "org.eclipse.team.internal.core".length())
      return "com.unitesk.requality.vscode.ui.team.core." + name.substring("org.eclipse.team.internal.core.".length());
    if (name.startsWith("org.eclipse.team.internal.ui."))
      return "com.unitesk.requality.vscode.ui.team." + name.substring("org.eclipse.team.internal.ui.".length());
    if (name.startsWith("org.eclipse.compare.internal.core.patch.") || name.startsWith("org.eclipse.compare.internal.patch."))
      return "com.unitesk.requality.vscode.ui.compare.patch." + name.substring(name.lastIndexOf('.') + 1);
    if (name.startsWith("org.eclipse.compare.internal."))
      return "com.unitesk.requality.vscode.ui.compare." + name.substring("org.eclipse.compare.internal.".length());
    // The classes of the public API of org.eclipse.compare (ZipFileStructureCreator, structuremergeviewer.*).
    if (name.startsWith("org.eclipse.compare."))
      return "com.unitesk.requality.vscode.ui.compare." + name.substring("org.eclipse.compare.".length());
    if (name.startsWith("com.unitesk.requality.eclipse."))
      return "com.unitesk.requality.vscode.port.eclipse." + name.substring("com.unitesk.requality.eclipse.".length());
    for (String p : new String[] { "marker", "model", "views" })
      if (name.startsWith("com.unitesk.requality." + p + "."))
        return "com.unitesk.requality.vscode.port." + name.substring("com.unitesk.requality.".length());
    if (name.equals("com.unitesk.requality.report.StatisticManager"))
      return "com.unitesk.requality.vscode.port.report.StatisticManager";
    if (name.startsWith("com.unitesk.requality.tests.TestDialogProvider")
        || name.startsWith("com.unitesk.requality.tests.EclipseTestGenerator")
        || name.startsWith("com.unitesk.requality.tests.ITestDialog"))
      return "com.unitesk.requality.vscode.port." + name.substring("com.unitesk.requality.".length());
    return name;
  }

  @Override
  public IConfigurationElement[] getConfigurationElementsFor(String extensionPointId) {
    List<IConfigurationElement> r = new ArrayList<>();
    for (Ext e : byPoint.getOrDefault(extensionPointId, new ArrayList<>()))
      for (IConfigurationElement c : e.getConfigurationElements())
        r.add(c);
    return r.toArray(new IConfigurationElement[0]);
  }

  @Override
  public IConfigurationElement[] getConfigurationElementsFor(String namespace, String extensionPointName) {
    return getConfigurationElementsFor(namespace + "." + extensionPointName);
  }

  @Override
  public IExtensionPoint getExtensionPoint(String extensionPointId) {
    List<Ext> exts = byPoint.getOrDefault(extensionPointId, new ArrayList<>());
    return new IExtensionPoint() {
      @Override
      public IConfigurationElement[] getConfigurationElements() {
        return getConfigurationElementsFor(extensionPointId);
      }

      @Override
      public IExtension[] getExtensions() {
        return exts.toArray(new IExtension[0]);
      }

      @Override
      public String getUniqueIdentifier() {
        return extensionPointId;
      }
    };
  }

  @Override
  public IExtension[] getExtensions(String namespace) {
    List<IExtension> r = new ArrayList<>();
    if (PLUGIN_ID.equals(namespace))
      for (List<Ext> l : byPoint.values())
        r.addAll(l);
    return r.toArray(new IExtension[0]);
  }

  private static final IContributor CONTRIBUTOR = () -> PLUGIN_ID;

  private static final class Ext implements IExtension {
    final String point;
    final String id;
    final String name;
    final List<IConfigurationElement> elements = new ArrayList<>();

    Ext(Element e) {
      point = e.getAttribute("point");
      id = e.getAttribute("id");
      name = e.getAttribute("name");
      NodeList ch = e.getChildNodes();
      for (int i = 0; i < ch.getLength(); i++)
        if (ch.item(i) instanceof Element)
          elements.add(new Conf((Element) ch.item(i), this, this));
    }

    @Override
    public IConfigurationElement[] getConfigurationElements() {
      return elements.toArray(new IConfigurationElement[0]);
    }

    @Override
    public String getExtensionPointUniqueIdentifier() {
      return point;
    }

    @Override
    public String getLabel() {
      return name;
    }

    @Override
    public String getUniqueIdentifier() {
      // An id with a dot is already qualified (Eclipse 3.2+); a simple id is qualified by the plug-in id.
      return id == null || id.isEmpty() ? null : id.indexOf('.') >= 0 ? id : PLUGIN_ID + "." + id;
    }

    @Override
    public String getSimpleIdentifier() {
      return id == null || id.isEmpty() ? null : id;
    }

    @Override
    public String getNamespaceIdentifier() {
      return PLUGIN_ID;
    }

    @Override
    public IContributor getContributor() {
      return CONTRIBUTOR;
    }

    @Override
    public boolean isValid() {
      return true;
    }
  }

  private static final class Conf implements IConfigurationElement {
    final Element el;
    final Ext ext;
    final Object parent;
    final List<IConfigurationElement> children = new ArrayList<>();

    Conf(Element el, Ext ext, Object parent) {
      this.el = el;
      this.ext = ext;
      this.parent = parent;
      NodeList ch = el.getChildNodes();
      for (int i = 0; i < ch.getLength(); i++)
        if (ch.item(i) instanceof Element)
          children.add(new Conf((Element) ch.item(i), ext, this));
    }

    @Override
    public Object createExecutableExtension(String propertyName) throws CoreException {
      String cls = getAttribute(propertyName);
      if (cls == null)
        throw new CoreException(new Status(IStatus.ERROR, PLUGIN_ID, "No attribute " + propertyName));
      int colon = cls.indexOf(':');
      if (colon > 0)
        cls = cls.substring(0, colon);
      String ported = portedClassName(cls);
      try {
        Class<?> c = Class.forName(ported);
        return c.getDeclaredConstructor().newInstance();
      } catch (Throwable t) {
        throw new CoreException(new Status(IStatus.ERROR, PLUGIN_ID, "Cannot create " + ported, t));
      }
    }

    @Override
    public String getAttribute(String name) {
      return el.hasAttribute(name) ? el.getAttribute(name) : null;
    }

    @Override
    public String[] getAttributeNames() {
      NamedNodeMap m = el.getAttributes();
      String[] r = new String[m.getLength()];
      for (int i = 0; i < r.length; i++)
        r[i] = m.item(i).getNodeName();
      return r;
    }

    @Override
    public IConfigurationElement[] getChildren() {
      return children.toArray(new IConfigurationElement[0]);
    }

    @Override
    public IConfigurationElement[] getChildren(String name) {
      List<IConfigurationElement> r = new ArrayList<>();
      for (IConfigurationElement c : children)
        if (c.getName().equals(name))
          r.add(c);
      return r.toArray(new IConfigurationElement[0]);
    }

    @Override
    public IExtension getDeclaringExtension() {
      return ext;
    }

    @Override
    public String getName() {
      return el.getNodeName();
    }

    @Override
    public Object getParent() {
      return parent;
    }

    @Override
    public String getValue() {
      String t = el.getTextContent();
      return t == null || t.trim().isEmpty() ? null : t.trim();
    }

    @Override
    public String getNamespaceIdentifier() {
      return PLUGIN_ID;
    }

    @Override
    public IContributor getContributor() {
      return CONTRIBUTOR;
    }

    @Override
    public boolean isValid() {
      return true;
    }
  }
}
