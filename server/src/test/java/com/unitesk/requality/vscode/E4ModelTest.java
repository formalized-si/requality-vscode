package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.rpc.JsonRpcConnection;
import com.unitesk.requality.vscode.ui.e4.model.application.ui.MElementContainer;
import com.unitesk.requality.vscode.ui.e4.model.application.ui.MUIElement;
import com.unitesk.requality.vscode.ui.e4.model.application.ui.basic.MPart;
import com.unitesk.requality.vscode.ui.e4.model.application.ui.basic.MPartSashContainerElement;
import com.unitesk.requality.vscode.ui.e4.model.application.ui.basic.impl.PartStackImpl;
import com.unitesk.requality.vscode.ui.e4.workbench.modeling.EModelService;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.workbench.EditorPart;
import com.unitesk.requality.vscode.ui.workbench.IEditorInput;
import com.unitesk.requality.vscode.ui.workbench.IEditorPart;
import com.unitesk.requality.vscode.ui.workbench.IEditorSite;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;
import com.unitesk.requality.vscode.ui.workbench.PartInitException;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.Workbench;

/**
 * The E4 model subset that the Update Processor Tasks view (ToDoList) uses to place its two documents side by side:
 * the model services of the editor area over the VS Code editor groups, and the placement sent to the extension
 * ({@code ui/placeShell}). The double click of the view is not driven: {@link #toDoListPlacement} runs the lines of
 * ToDoList's double click (upstream order) on two opened editors.
 */
class E4ModelTest {
  static ServerHarness h;
  static final String EDITOR_ID = "com.unitesk.requality.test.e4editor";
  /** The {@code ui/placeShell} notifications the server sent (the harness records only its own list). */
  static final List<JsonObject> PLACEMENTS = new CopyOnWriteArrayList<>();

  /** A server-hosted editor with no content: only its place in the editor area matters here. */
  public static final class TestEditor extends EditorPart {
    @Override
    public void init(IEditorSite site, IEditorInput input) throws PartInitException {
      setSite(site);
      setInput(input);
      setPartName(input.getName());
    }

    @Override
    public void createPartControl(Composite parent) {
    }

    @Override
    public void setFocus() {
    }

    @Override
    public void doSave(IProgressMonitor monitor) {
    }

    @Override
    public void doSaveAs() {
    }

    @Override
    public boolean isDirty() {
      return false;
    }

    @Override
    public boolean isSaveAsAllowed() {
      return false;
    }
  }

  static IEditorInput input(String name) {
    return new IEditorInput() {
      @Override
      public boolean exists() {
        return true;
      }

      @Override
      public String getName() {
        return name;
      }

      @Override
      public String getToolTipText() {
        return name;
      }
    };
  }

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    Field client = ServerHarness.class.getDeclaredField("client");
    client.setAccessible(true);
    ((JsonRpcConnection) client.get(h)).onNotification("ui/placeShell", PLACEMENTS::add);
    Workbench.registerEditor(EDITOR_ID, TestEditor::new);
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  static IWorkbenchPage page() {
    return PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
  }

  /**
   * The lines of ToDoList's double click that place the second document (upstream order, the same calls on the E4
   * model): {@code ip} is the first document, {@code ip2} the second one.
   */
  @SuppressWarnings("unchecked")
  static void toDoListPlacement(IEditorPart ip, IEditorPart ip2) {
    boolean changed = false;
    MPartSashContainerElement cn = null;
    MPartSashContainerElement cn2 = null;

    EModelService modelService = null;
    MPart mpartService = null;
    modelService = ip.getSite().getService(EModelService.class);
    mpartService = ip.getSite().getService(MPart.class);
    cn = (MPartSashContainerElement) mpartService.getParent();
    for (MUIElement muie : cn.getParent().getChildren())
      if (muie instanceof MElementContainer<?>) {
        for (MUIElement mu : ((MElementContainer<MUIElement>) muie).getChildren())
          if (mu.getTags().contains("Editor") && !cn.equals(muie))
            cn2 = (MPartSashContainerElement) muie;
      }
    if (cn2 == null) {
      changed = true;
      cn2 = (MPartSashContainerElement) modelService.cloneElement(cn, null);
      cn2.setParent(cn.getParent());
      ((PartStackImpl) cn2).getChildren().clear();
    }
    if (modelService == null) {
      modelService = ip2.getSite().getService(EModelService.class);
    }
    mpartService = ip2.getSite().getService(MPart.class);

    boolean containsMpartService = false;
    if (cn2 != null) {
      containsMpartService = false;
      if (((PartStackImpl) cn2).getChildren().contains(mpartService))
        containsMpartService = true;
      if (!containsMpartService) {
        ((PartStackImpl) cn2).getChildren().add((MPart) mpartService);
        modelService.bringToTop(ip2.getSite().getService(MPart.class));
      }
    }

    if (changed) {
      modelService.insert(cn2, cn, EModelService.RIGHT_OF, 0.5f);
    }
    if (!mpartService.isVisible())
      modelService.bringToTop(mpartService);
    if (ip != null && !ip.getSite().getService(MPart.class).isVisible())
      modelService.bringToTop(ip.getSite().getService(MPart.class));
  }

  static JsonObject lastPlacement() {
    return PLACEMENTS.isEmpty() ? null : PLACEMENTS.get(PLACEMENTS.size() - 1);
  }

  @Test
  void secondDocumentIsPlacedRightOfTheFirstAndBothAreShown() throws Exception {
    int before = PLACEMENTS.size();
    IEditorPart first = (IEditorPart) page().openEditor(input("first.req"), EDITOR_ID, true);
    IEditorPart second = (IEditorPart) page().openEditor(input("second.req"), EDITOR_ID, true);
    assertNotNull(first.getSite().getShell());
    assertNotNull(second.getSite().getShell());

    toDoListPlacement(first, second);

    MPart p1 = first.getSite().getService(MPart.class);
    MPart p2 = second.getSite().getService(MPart.class);
    assertNotNull(p1);
    assertNotNull(p2);
    PartStackImpl g1 = (PartStackImpl) p1.getParent();
    PartStackImpl g2 = (PartStackImpl) p2.getParent();
    assertTrue(g1 != g2, "the second document is in its own editor group");
    assertSame(g1.getParent(), g2.getParent(), "both groups are in the editor area");
    List<MUIElement> area = g1.getParent().getChildren();
    assertEquals(area.indexOf(g1) + 1, area.indexOf(g2), "the second group is right of the first");
    assertTrue(p1.isVisible(), "the first document is shown");
    assertTrue(p2.isVisible(), "the second document is shown");

    // The extension moves the second editor right of the first one (and reveals both).
    // Notifications are sent asynchronously: wait for the placement of the second editor.
    h.await("the placement of the second editor", v -> PLACEMENTS.size() > before);
    JsonObject last = lastPlacement();
    assertEquals(second.getSite().getShell().getId(), last.get("shell").getAsString());
    // The beside editor is an editor of the first group: all of them are in its VS Code column.
    List<String> firstGroupShells = new java.util.ArrayList<>();
    for (MUIElement e : g1.getChildren())
      firstGroupShells.add(((MPart) e).getEditor().getSite().getShell().getId());
    assertTrue(firstGroupShells.contains(last.get("beside").getAsString()), firstGroupShells.toString());
  }

  @Test
  void cloneInsertAndBringToTopOnTheCompatModel() throws Exception {
    IEditorPart ed = (IEditorPart) page().openEditor(input("third.req"), EDITOR_ID, true);
    EModelService ms = ed.getSite().getService(EModelService.class);
    assertNotNull(ms);
    MPart part = ed.getSite().getService(MPart.class);
    PartStackImpl group = (PartStackImpl) part.getParent();
    assertTrue(group.getTags().contains("Editor"), "an editor group is tagged Editor");

    // cloneElement: an empty group with the tags of the source, not in the area.
    MUIElement clone = ms.cloneElement(group, null);
    assertTrue(clone instanceof PartStackImpl);
    assertTrue(((PartStackImpl) clone).getChildren().isEmpty());
    assertTrue(clone.getTags().contains("Editor"));
    assertNull(clone.getParent());

    // insert RIGHT_OF places the clone after the group, in the same container.
    MElementContainer<MUIElement> area = group.getParent();
    int at = area.getChildren().indexOf(group);
    clone.setParent(area);
    int placements = PLACEMENTS.size();
    ms.insert((MPartSashContainerElement) clone, (MPartSashContainerElement) group, EModelService.RIGHT_OF, 0.5f);
    assertEquals(at + 1, area.getChildren().indexOf(clone));
    assertSame(area, clone.getParent());
    Thread.sleep(300); // notifications are asynchronous: give a placement time to arrive
    assertEquals(placements, PLACEMENTS.size(), "an empty group has no editor to place");

    // insert LEFT_OF moves the clone before the group.
    ms.insert((MPartSashContainerElement) clone, (MPartSashContainerElement) group, EModelService.LEFT_OF, 0.5f);
    assertEquals(area.getChildren().indexOf(group) - 1, area.getChildren().indexOf(clone));
    assertEquals(1, area.getChildren().stream().filter(c -> c == clone).count(), "the clone is in the area once");

    // bringToTop selects the part of its group and keeps it visible.
    ms.bringToTop(part);
    assertTrue(part.isVisible());
    assertSame(part, group.getSelectedElement());
  }

  @Test
  void theEditorGroupsFollowTheColumnsOfVsCode() throws Exception {
    IEditorPart a = (IEditorPart) page().openEditor(input("col-a.req"), EDITOR_ID, true);
    IEditorPart b = (IEditorPart) page().openEditor(input("col-b.req"), EDITOR_ID, true);
    // the extension reports the editor group (view column) of each tab (ui/shellColumn)
    for (Object[] c : new Object[][] { { a, 1 }, { b, 3 } }) {
      JsonObject p = new JsonObject();
      p.addProperty("shell", ((IEditorPart) c[0]).getSite().getShell().getId());
      p.addProperty("column", (Integer) c[1]);
      h.notify("ui/shellColumn", p);
    }
    h.await("b in its own group", v -> {
      MPart pb = b.getSite().getService(MPart.class);
      MPart pa = a.getSite().getService(MPart.class);
      return pa != null && pb != null && pa.getParent() != pb.getParent();
    });
    PartStackImpl ga = (PartStackImpl) a.getSite().getService(MPart.class).getParent();
    PartStackImpl gb = (PartStackImpl) b.getSite().getService(MPart.class).getParent();
    List<MUIElement> area = ga.getParent().getChildren();
    assertTrue(area.indexOf(ga) < area.indexOf(gb), "column 1 is left of column 3");
    // b is moved to column 1: it joins a's group and its empty group goes
    JsonObject p = new JsonObject();
    p.addProperty("shell", b.getSite().getShell().getId());
    p.addProperty("column", 1);
    h.notify("ui/shellColumn", p);
    h.await("b in the group of a", v -> b.getSite().getService(MPart.class).getParent() == a.getSite().getService(MPart.class).getParent());
    assertFalse(area.contains(gb), "the empty group is removed");
  }
}
