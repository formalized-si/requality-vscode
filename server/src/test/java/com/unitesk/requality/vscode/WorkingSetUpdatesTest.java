package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.jface.util.LocalSelectionTransfer;
import com.unitesk.requality.vscode.ui.jface.viewers.StructuredSelection;
import com.unitesk.requality.vscode.ui.navigator.workingsets.ProjectInWorkingSetDropAdapterAssistant;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.IResourceChangeEvent;
import com.unitesk.requality.vscode.ui.resources.ResourcesPlugin;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.Path;
import com.unitesk.requality.vscode.ui.workbench.IAggregateWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSet;
import com.unitesk.requality.vscode.ui.workbench.IWorkingSetManager;
import com.unitesk.requality.vscode.ui.workbench.ResourceWorkingSetFilter;
import com.unitesk.requality.vscode.ui.workbench.Workbench;

/**
 * The resource working sets follow the changes of the resources (the IDE's ResourceWorkingSetUpdater: a moved or renamed
 * project or file is replaced, a deleted one is removed), the window working set, the filter by containment, and the drop
 * of a project on a working set (the ProjectInWorkingSetDropAdapterAssistant).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkingSetUpdatesTest {
  static final String RESOURCE_PAGE = "org.eclipse.ui.resourceWorkingSetPage";
  static ServerHarness h;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    TestCatalogs.copyEtsi(h.workspace, "TS103096");
    File plain = new File(h.workspace, "Plain");
    new File(plain, "docs").mkdirs();
    Files.write(new File(plain, ".project").toPath(), ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        + "<projectDescription><name>Plain</name><comment></comment><projects></projects>"
        + "<buildSpec></buildSpec><natures></natures></projectDescription>\n").getBytes(StandardCharsets.UTF_8));
    Files.write(new File(plain, "docs/readme.txt").toPath(), "text".getBytes(StandardCharsets.UTF_8));
    Files.write(new File(plain, "top.txt").toPath(), "top".getBytes(StandardCharsets.UTF_8));
    h.initialize(h.workspace);
    h.awaitProjects(1);
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  static IWorkingSetManager manager() {
    return Workbench.INSTANCE.getWorkingSetManager();
  }

  static IWorkingSet resourceSet(String name, IAdaptable... elements) {
    IWorkingSet ws = manager().createWorkingSet(name, elements);
    ws.setId(RESOURCE_PAGE);
    manager().addWorkingSet(ws);
    return ws;
  }

  @Test
  @Order(1)
  void aRenamedProjectIsReplacedInItsWorkingSet() throws Exception {
    IProject plain = ResourcesPlugin.getWorkspace().getRoot().getProject("Plain");
    IWorkingSet ws = resourceSet("Renames", plain);
    plain.move(ResourcesPlugin.getWorkspace().newProjectDescription("Renamed"), true, (IProgressMonitor) null);
    IAdaptable[] elements = ws.getElements();
    assertEquals(1, elements.length, "the project is still in the working set");
    assertEquals("Renamed", ((IResource) elements[0]).getName());
  }

  @Test
  @Order(2)
  void aDeletedFileIsRemovedFromItsWorkingSet() throws Exception {
    IFile readme = ResourcesPlugin.getWorkspace().getRoot().getFile(Path.fromOSString("/Renamed/docs/readme.txt"));
    IWorkingSet ws = resourceSet("Files", readme);
    assertEquals(1, ws.getElements().length);
    readme.delete(true, (IProgressMonitor) null);
    assertEquals(0, ws.getElements().length, "the deleted file leaves the working set");
  }

  @Test
  @Order(3)
  void theWindowWorkingSetAggregatesTheWindowSets() throws Exception {
    IResource top = ResourcesPlugin.getWorkspace().getRoot().getFile(Path.fromOSString("/Renamed/top.txt"));
    IWorkingSet ws = resourceSet("Window members", top);
    com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage page = Workbench.INSTANCE.getActiveWorkbenchWindow()
        .getActivePage();
    page.setWorkingSets(new IWorkingSet[] { ws });
    assertEquals(1, page.getWorkingSets().length);
    IAggregateWorkingSet window = page.getAggregateWorkingSet();
    assertEquals("Window Working Set", window.getLabel());
    assertEquals(Arrays.asList(ws), Arrays.asList(window.getComponents()));
    assertEquals(1, window.getElements().length, "the window set has the elements of its components");
    page.setWorkingSets(null);
    assertEquals(0, window.getComponents().length);
  }

  @Test
  @Order(4)
  void aProjectDroppedOnAWorkingSetIsAddedToIt() throws Exception {
    IProject renamed = ResourcesPlugin.getWorkspace().getRoot().getProject("Renamed");
    IWorkingSet target = resourceSet("Drop target");
    LocalSelectionTransfer.getTransfer().setSelection(new StructuredSelection(renamed));
    new ProjectInWorkingSetDropAdapterAssistant().handleDrop(null, null, target);
    assertTrue(Arrays.stream(target.getElements()).anyMatch(e -> renamed.equals(e)), "the project is in the set");
  }

  @Test
  @Order(5)
  void theFilterShowsTheMembersAndTheirAncestors() throws Exception {
    IResource docs = ResourcesPlugin.getWorkspace().getRoot().getFolder(Path.fromOSString("/Renamed/docs"));
    IResource inDocs = ResourcesPlugin.getWorkspace().getRoot().getFile(Path.fromOSString("/Renamed/docs/other.txt"));
    IResource top = ResourcesPlugin.getWorkspace().getRoot().getFile(Path.fromOSString("/Renamed/top.txt"));
    ResourceWorkingSetFilter filter = new ResourceWorkingSetFilter();
    filter.setWorkingSet(manager().createWorkingSet("Docs", new IAdaptable[] { docs }));
    assertTrue(filter.select(null, null, docs), "the member is shown");
    assertTrue(filter.select(null, null, inDocs), "a child of the member is shown");
    assertTrue(filter.select(null, null, ResourcesPlugin.getWorkspace().getRoot().getProject("Renamed")),
        "the ancestor project is shown");
    assertFalse(filter.select(null, null, top), "an unrelated file is hidden");
    filter.setWorkingSet(null);
    assertTrue(filter.select(null, null, top), "without a working set everything is shown");
    assertNotNull(filter);
  }
}
