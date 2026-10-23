package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.workbench.IAggregateWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.IElementFactory;
import com.unitesk.requality.vscode.ui.workbench.IMemento;
import com.unitesk.requality.vscode.ui.workbench.IPersistableElement;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSetManager;
import com.unitesk.requality.vscode.ui.workbench.internal.ElementFactories;
import com.unitesk.requality.vscode.ui.workbench.internal.WorkingSetManager;
import com.unitesk.requality.vscode.ui.workbench.internal.WorkingSetRegistry;

/**
 * {@code org.eclipse.ui.internal.WorkingSetManager}: the working sets are written to the working sets file of the
 * workspace and read again by a new manager (the restart of the server).
 */
class WorkingSetPersistenceTest {
  private static final String FACTORY = "com.unitesk.requality.test.NamedElementFactory";

  /** An element persisted with its name, as a resource is persisted with its path. */
  static final class NamedElement implements IAdaptable, IPersistableElement {
    final String name;

    NamedElement(String name) {
      this.name = name;
    }

    @Override
    public String getFactoryId() {
      return FACTORY;
    }

    @Override
    public void saveState(IMemento memento) {
      memento.putString("name", name);
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof NamedElement n && n.name.equals(name);
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }

    @Override
    public String toString() {
      return "NamedElement(" + name + ")";
    }
  }

  static {
    // the extension points of the server (the Requality plugin.xml and platform-plugin.xml, read by the bootstrap)
    ExtensionRegistry.setPluginXml(new File("upstream/eclipse-reference/plugin.xml"));
    ElementFactories.register(FACTORY, new IElementFactory() {
      @Override
      public IAdaptable createElement(IMemento memento) {
        String name = memento.getString("name");
        return name == null ? null : new NamedElement(name);
      }
    });
  }

  @TempDir
  File dir;

  @Test
  void workingSetsSurviveARestart() throws Exception {
    WorkingSetManager.setWorkspaceStateFile(new File(dir, "requality-workspace.json"));
    File file = new File(dir, "requality-workspace-workingsets.xml");

    WorkingSetManager first = new WorkingSetManager();
    IWorkingSet set = first.createWorkingSet("Alpha", new IAdaptable[] { new NamedElement("a"), new NamedElement("b") });
    first.addWorkingSet(set);
    IWorkingSet aggregate = first.createAggregateWorkingSet("All", "All", new IWorkingSet[] { set });
    first.addWorkingSet(aggregate);
    first.addRecentWorkingSet(set);
    assertTrue(file.isFile(), "the working sets file is written");
    String xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    assertTrue(xml.contains("<workingSetManager"), xml);
    assertTrue(xml.contains("<workingSet"), xml);
    assertTrue(xml.contains("<mruList"), xml);

    // a new manager reads the file as the server does after a restart
    WorkingSetManager second = new WorkingSetManager();
    second.restoreState();
    IWorkingSet[] restored = second.getWorkingSets();
    assertEquals(1, restored.length, "the aggregate is not visible");
    assertEquals("Alpha", restored[0].getName());
    assertArrayEquals(new IAdaptable[] { new NamedElement("a"), new NamedElement("b") }, restored[0].getElements());
    assertEquals(2, second.getAllWorkingSets().length);
    assertEquals(1, java.util.Arrays.stream(second.getAllWorkingSets())
        .filter(w -> w instanceof IAggregateWorkingSet).count());
    assertEquals(1, second.getRecentWorkingSets().length);
    assertEquals("Alpha", second.getRecentWorkingSets()[0].getName());
    assertNull(second.getWorkingSet("Missing"));
  }

  @Test
  void removalIsPersisted() {
    WorkingSetManager.setWorkspaceStateFile(new File(dir, "w.json"));
    WorkingSetManager m = new WorkingSetManager();
    IWorkingSet set = m.createWorkingSet("Beta", new IAdaptable[] { new NamedElement("x") });
    m.addWorkingSet(set);
    m.removeWorkingSet(set);

    WorkingSetManager after = new WorkingSetManager();
    after.restoreState();
    assertEquals(0, after.getAllWorkingSets().length);
  }

  @Test
  void theIdeResourceTypeIsDeclared() {
    assertNotNull(WorkingSetRegistry.get().getWorkingSetDescriptor("org.eclipse.ui.resourceWorkingSetPage"));
    assertEquals("Resource",
        WorkingSetRegistry.get().getWorkingSetDescriptor("org.eclipse.ui.resourceWorkingSetPage").getName());
  }

  @Test
  void listenersAreNotifiedOfAddAndRemove() {
    WorkingSetManager m = new WorkingSetManager();
    List<String> events = new ArrayList<>();
    m.addPropertyChangeListener(e -> events.add(e.getProperty()));
    IWorkingSet set = m.createWorkingSet("Delta", new IAdaptable[0]);
    m.addWorkingSet(set);
    m.removeWorkingSet(set);
    assertEquals(List.of(IWorkingSetManager.CHANGE_WORKING_SET_ADD, IWorkingSetManager.CHANGE_WORKING_SET_REMOVE),
        events);
  }
}
