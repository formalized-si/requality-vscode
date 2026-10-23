package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.jface.dialogs.IMessageProvider;
import com.unitesk.requality.vscode.ui.jface.dialogs.TitleAreaDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.ArrayContentProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.CellEditor;
import com.unitesk.requality.vscode.ui.jface.viewers.ColumnLabelProvider;
import com.unitesk.requality.vscode.ui.jface.viewers.EditingSupport;
import com.unitesk.requality.vscode.ui.jface.viewers.TableViewer;
import com.unitesk.requality.vscode.ui.jface.viewers.TableViewerColumn;
import com.unitesk.requality.vscode.ui.jface.viewers.TextCellEditor;
import com.unitesk.requality.vscode.ui.jface.window.Window;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Combo;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;

/** The remote SWT/JFace layer over the real RPC protocol: dialogs, events, snapshots and cell editing. */
class RswtTest {
  static ServerHarness h;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    h.initialize(h.workspace);
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  /** A row of the test table. */
  static final class Row {
    String name;

    Row(String name) {
      this.name = name;
    }
  }

  static final class TestDialog extends TitleAreaDialog {
    Text name;
    Combo kind;
    Button radioA;
    Button radioB;
    String result;
    final List<Row> rows = new ArrayList<>();
    TableViewer viewer;
    final List<String> radioEvents = new ArrayList<>();

    TestDialog() {
      super(null);
      rows.add(new Row("first"));
      rows.add(new Row("second"));
    }

    @Override
    protected void configureShell(Shell s) {
      super.configureShell(s);
      s.setText("Test Dialog");
    }

    @Override
    protected Control createDialogArea(Composite parent) {
      Composite area = (Composite) super.createDialogArea(parent);
      setTitle("Edit element");
      setMessage("Enter a name", IMessageProvider.INFORMATION);
      Composite c = new Composite(area, SWT.NONE);
      c.setLayout(new GridLayout(2, false));
      c.setLayoutData(new GridData(GridData.FILL_BOTH));
      new Label(c, SWT.NONE).setText("Name:");
      name = new Text(c, SWT.BORDER);
      name.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
      name.addModifyListener(e -> {
        if (name.getText().isEmpty())
          setErrorMessage("Name is empty");
        else
          setErrorMessage(null);
        getButton(OK).setEnabled(!name.getText().isEmpty());
      });
      new Label(c, SWT.NONE).setText("Kind:");
      kind = new Combo(c, SWT.READ_ONLY);
      kind.setItems("text", "header");
      kind.select(0);
      radioA = new Button(c, SWT.RADIO);
      radioA.setText("A");
      radioA.setSelection(true);
      radioA.addListener(SWT.Selection, e -> radioEvents.add("A:" + radioA.getSelection()));
      radioB = new Button(c, SWT.RADIO);
      radioB.setText("B");
      radioB.addListener(SWT.Selection, e -> radioEvents.add("B:" + radioB.getSelection()));
      viewer = new TableViewer(c, SWT.BORDER | SWT.FULL_SELECTION);
      GridData gd = new GridData(GridData.FILL_BOTH);
      gd.horizontalSpan = 2;
      viewer.getTable().setLayoutData(gd);
      viewer.getTable().setHeaderVisible(true);
      TableViewerColumn col = new TableViewerColumn(viewer, SWT.NONE);
      col.getColumn().setText("Name");
      col.getColumn().setWidth(150);
      col.setLabelProvider(new ColumnLabelProvider() {
        @Override
        public String getText(Object element) {
          return ((Row) element).name;
        }
      });
      col.setEditingSupport(new EditingSupport(viewer) {
        @Override
        protected CellEditor getCellEditor(Object element) {
          return new TextCellEditor(viewer.getTable());
        }

        @Override
        protected boolean canEdit(Object element) {
          return true;
        }

        @Override
        protected Object getValue(Object element) {
          return ((Row) element).name;
        }

        @Override
        protected void setValue(Object element, Object value) {
          ((Row) element).name = (String) value;
          viewer.update(element, null);
        }
      });
      viewer.setContentProvider(ArrayContentProvider.getInstance());
      viewer.setInput(rows);
      return area;
    }

    @Override
    protected void okPressed() {
      result = name.getText() + "/" + kind.getText();
      super.okPressed();
    }
  }

  /** Snapshot operations received so far (a snapshot consumes the pending updates of its shell). */
  static final List<JsonObject> SNAPSHOT_OPS = new java.util.concurrent.CopyOnWriteArrayList<>();

  private static List<JsonObject> allOps(String shell) {
    List<JsonObject> ops = new ArrayList<>(SNAPSHOT_OPS);
    for (JsonObject u : h.notifications("ui/update"))
      if (u.get("shell").getAsString().equals(shell))
        for (JsonElement e : u.getAsJsonArray("ops"))
          ops.add(e.getAsJsonObject());
    return ops;
  }

  private static String findCreated(String shell, String type, String prop, String value) {
    for (JsonObject op : allOps(shell)) {
      if (!"create".equals(op.get("op").getAsString()) || !type.equals(op.get("type").getAsString()))
        continue;
      if (prop == null)
        return op.get("id").getAsString();
      JsonElement v = op.getAsJsonObject("props").get(prop);
      if (v != null && v.isJsonPrimitive() && value.equals(v.getAsString()))
        return op.get("id").getAsString();
    }
    return null;
  }

  private static void event(String id, String type, JsonObject data) {
    JsonObject p = new JsonObject();
    p.addProperty("id", id);
    p.addProperty("type", type);
    p.add("data", data == null ? new JsonObject() : data);
    h.notify("ui/event", p);
  }

  private static JsonObject data(String k, Object v) {
    JsonObject o = new JsonObject();
    o.add(k, com.unitesk.requality.vscode.rpc.JsonRpcConnection.toJson(v));
    return o;
  }

  @Test
  void dialogEventsSnapshotAndCellEditing() throws Exception {
    TestDialog d = new TestDialog();
    AtomicInteger rc = new AtomicInteger(-1);
    int before = h.notifications("ui/openShell").size();
    int closedBefore = h.notifications("ui/closeShell").size();
    Thread t = new Thread(() -> rc.set(d.open()));
    t.start();
    JsonObject opened = h.awaitPanelShell(before);
    String shell = opened.get("shell").getAsString();
    assertEquals("Test Dialog", opened.get("title").getAsString());
    h.await("ops", x -> findCreated(shell, "text", null, null) != null);

    // Snapshot recreates the tree in display order: shell first, parents before children.
    JsonObject snap = h.call("ui/snapshot", "shell", shell).getAsJsonObject();
    JsonArray ops = snap.getAsJsonArray("ops");
    for (JsonElement e : ops)
      SNAPSHOT_OPS.add(e.getAsJsonObject());
    assertEquals("shell", ops.get(0).getAsJsonObject().get("type").getAsString());
    List<String> seen = new ArrayList<>();
    for (JsonElement e : ops) {
      JsonObject op = e.getAsJsonObject();
      if (!op.get("parent").isJsonNull())
        assertTrue(seen.contains(op.get("parent").getAsString()), "parent before child: " + op);
      seen.add(op.get("id").getAsString());
    }
    Files.writeString(Path.of(System.getProperty("user.dir"), "build", "rswt-dialog-snapshot.json"), snap.toString());

    String textId = findCreated(shell, "text", null, null);
    String okId = findCreated(shell, "button", "text", "OK");
    assertNotNull(okId);
    // Empty name: OK disabled and error shown.
    event(textId, "modify", data("text", ""));
    event(textId, "modify", data("text", "Req 1"));
    String comboId = findCreated(shell, "combo", null, null);
    event(comboId, "selection", data("index", 1));

    // Radio group: selecting B deselects A, both notified (SWT order: A first).
    String radioB = findCreated(shell, "button", "text", "B");
    event(radioB, "selection", data("selection", true));
    h.await("radio events", x -> d.radioEvents.size() == 2);
    assertEquals(List.of("A:false", "B:true"), d.radioEvents);

    // Cell editing: a click on a cell of a selected row opens the inline editor; applying saves through
    // EditingSupport.
    String tableId = findCreated(shell, "table", null, null);
    String firstRow = findCreated(shell, "row", null, null);
    JsonObject sel = new JsonObject();
    JsonArray ids = new JsonArray();
    ids.add(firstRow);
    sel.add("selection", ids);
    sel.addProperty("item", firstRow);
    event(tableId, "selection", sel);
    JsonObject cell = new JsonObject();
    cell.addProperty("item", firstRow);
    cell.addProperty("column", 0);
    cell.addProperty("trigger", "click");
    event(tableId, "cell", cell);
    h.await("editCell call", x -> allOps(shell).stream().anyMatch(op -> "call".equals(op.get("op").getAsString())
        && "editCell".equals(op.get("method").getAsString())));
    JsonObject apply = new JsonObject();
    apply.addProperty("item", firstRow);
    apply.addProperty("column", 0);
    apply.addProperty("value", "renamed");
    event(tableId, "cellApply", apply);
    h.await("row updated", x -> "renamed".equals(d.rows.get(0).name));
    h.await("row text sent", x -> allOps(shell).stream().anyMatch(op -> "set".equals(op.get("op").getAsString())
        && op.get("id").getAsString().equals(firstRow) && op.getAsJsonObject("props").toString().contains("renamed")));

    event(okId, "selection", null);
    t.join(20000);
    assertFalse(t.isAlive());
    assertEquals(Window.OK, rc.get());
    assertEquals("Req 1/header", d.result);
    h.await("shell closed", x -> h.notifications("ui/closeShell").size() > closedBefore);
    JsonObject after = h.call("ui/snapshot", "shell", shell).getAsJsonObject();
    assertTrue(after.get("closed").getAsBoolean());
  }

  @Test
  void userClosingThePanelCancels() throws Exception {
    TestDialog d = new TestDialog();
    AtomicReference<Integer> rc = new AtomicReference<>();
    int before = h.notifications("ui/openShell").size();
    Thread t = new Thread(() -> rc.set(d.open()));
    t.start();
    String shell = h.awaitPanelShell(before).get("shell").getAsString();
    event(shell, "close", null);
    t.join(20000);
    assertEquals(Window.CANCEL, rc.get().intValue());
    assertTrue(Display.getDefault().find(shell) == null);
  }
}
