package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.JTextPane;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.awt.SWT_AWT;
import com.unitesk.requality.vscode.ui.swt.custom.StyleRange;
import com.unitesk.requality.vscode.ui.swt.custom.StyledText;
import com.unitesk.requality.vscode.ui.swt.graphics.Color;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.swt.widgets.Widget;

/**
 * {@code org.eclipse.swt.awt.SWT_AWT}: a Swing text pane in a scroll pane, embedded in an SWT composite, is shown as a
 * StyledText that stays in sync with the document; the node compare shows the descriptions with the difference colors.
 */
class SwingBridgeTest {
  static ServerHarness h;

  @BeforeAll
  static void start() throws Exception {
    h = new ServerHarness();
    TestCatalogs.copyEtsi(h.workspace, "TS103096");
    h.initialize(h.workspace);
    h.awaitProjects(1);
  }

  @AfterAll
  static void stop() throws Exception {
    h.close();
  }

  /** Embeds the pane as ElementComparePanel does (SWT_AWT.new_Frame, Frame.add of a scroll pane). */
  private static StyledText embed(JTextPane pane) {
    Shell shell = new Shell(Display.getDefault());
    Composite composite = new Composite(shell, SWT.EMBEDDED);
    new SWT_AWT().new_Frame(composite).add(new javax.swing.JScrollPane(pane));
    Widget[] children = composite.getChildren();
    assertEquals(1, children.length);
    return (StyledText) children[0];
  }

  private static JTextPane pane(String text) throws Exception {
    JTextPane pane = new JTextPane(new DefaultStyledDocument());
    pane.getDocument().insertString(0, text, null);
    return pane;
  }

  private static String docText(JTextPane pane) throws Exception {
    return pane.getDocument().getText(0, pane.getDocument().getLength());
  }

  private static void event(StyledText st, String type, String text) {
    JsonObject p = new JsonObject();
    p.addProperty("id", st.getWidgetId());
    p.addProperty("type", type);
    JsonObject data = new JsonObject();
    if (text != null)
      data.addProperty("text", text);
    p.add("data", data);
    h.notify("ui/event", p);
  }

  @Test
  void documentTextIsShownAndFollowsChanges() throws Exception {
    JTextPane pane = pane("first\nsecond");
    StyledText st = embed(pane);
    assertEquals("first\nsecond", st.getText());
    pane.getDocument().remove(0, 5);
    pane.getDocument().insertString(0, "changed", null);
    assertEquals("changed\nsecond", st.getText());
  }

  @Test
  void backgroundAttributesBecomeStyleRanges() throws Exception {
    JTextPane pane = pane("0123456789");
    StyledText st = embed(pane);
    assertTrue(st.getStyleRanges().isEmpty());
    SimpleAttributeSet bg = new SimpleAttributeSet();
    StyleConstants.setBackground(bg, new java.awt.Color(204, 255, 153));
    pane.getStyledDocument().setCharacterAttributes(2, 3, bg, false);
    assertEquals(1, st.getStyleRanges().size());
    StyleRange r = st.getStyleRanges().get(0);
    assertEquals(2, r.start);
    assertEquals(3, r.length);
    assertEquals(new Color(Display.getDefault(), 204, 255, 153).css(), r.background.css());
  }

  @Test
  void editsOfTheStyledTextUpdateTheDocumentOnly() throws Exception {
    JTextPane pane = pane("hello world");
    StyledText st = embed(pane);
    event(st, "modify", "hello there");
    h.await("the edit in the document", v -> {
      try {
        return "hello there".equals(docText(pane));
      } catch (Exception e) {
        return false;
      }
    });
    assertEquals("hello there", st.getText());
  }

  @Test
  void focusOutOfTheStyledTextReachesTheSwingFocusListeners() throws Exception {
    JTextPane pane = pane("text");
    StyledText st = embed(pane);
    AtomicBoolean lost = new AtomicBoolean();
    pane.addFocusListener(new java.awt.event.FocusAdapter() {
      @Override
      public void focusLost(java.awt.event.FocusEvent e) {
        lost.set(true);
      }
    });
    event(st, "focusout", null);
    h.await("the focus loss", v -> lost.get());
  }

  @Test
  void setEditableOfTheSwingComponentIsMirrored() throws Exception {
    JTextPane pane = pane("text");
    StyledText st = embed(pane);
    assertTrue(st.getEditable());
    pane.setEditable(false);
    assertFalse(st.getEditable());
    pane.setEditable(true);
    assertTrue(st.getEditable());
  }

  /** Two requirement files of the test catalog with different, non-empty descriptions (the compare colors them). */
  private static List<Path> twoRequirementFilesWithDifferentDescriptions() throws Exception {
    List<Path> candidates;
    try (Stream<Path> s = Files.walk(new File(h.workspace, "TS103096").toPath())) {
      candidates = s.filter(f -> f.toString().endsWith(".json") && f.getNameCount() > 4).filter(f -> {
        try {
          String c = Files.readString(f);
          return c.contains("\"Requirement\"") && c.contains("\"_name\"");
        } catch (java.io.IOException e) {
          return false;
        }
      }).collect(Collectors.toList());
    }
    List<Path> withDescription = new ArrayList<>();
    List<String> descriptions = new ArrayList<>();
    for (Path f : candidates) {
      String d = descriptionOf(f);
      if (!d.isEmpty()) {
        withDescription.add(f);
        descriptions.add(d);
      }
    }
    for (int i = 0; i < withDescription.size(); i++)
      for (int j = i + 1; j < withDescription.size(); j++)
        if (!descriptions.get(i).equals(descriptions.get(j)))
          return List.of(withDescription.get(i), withDescription.get(j));
    return List.of();
  }

  /** The {@code _description} attribute of a node file, as the compare shows it (empty when absent). */
  private static String descriptionOf(Path file) throws Exception {
    JsonElement root = JsonParser.parseString(Files.readString(file));
    if (!root.isJsonObject() || !root.getAsJsonObject().has("attributes"))
      return "";
    JsonElement attrs = root.getAsJsonObject().get("attributes");
    if (!attrs.isJsonObject() || !attrs.getAsJsonObject().has("_description"))
      return "";
    JsonElement desc = attrs.getAsJsonObject().get("_description");
    if (!desc.isJsonObject() || !desc.getAsJsonObject().has("value"))
      return "";
    // the saved workspace form wraps the value once more: {"flag": 4, "value": {"value": "text"}}
    JsonElement value = desc.getAsJsonObject().get("value");
    if (value.isJsonObject() && value.getAsJsonObject().has("value"))
      value = value.getAsJsonObject().get("value");
    return value.isJsonPrimitive() ? value.getAsString() : "";
  }

  /** The description panes (StyledText) of the compare shell, from its snapshot. */
  private static List<StyledText> descriptionPanes(String shell) throws Exception {
    List<StyledText> r = new ArrayList<>();
    for (com.google.gson.JsonElement e : h.call("ui/snapshot", "shell", shell).getAsJsonObject().getAsJsonArray("ops")) {
      JsonObject op = e.getAsJsonObject();
      if ("create".equals(op.get("op").getAsString()) && "styledtext".equals(op.get("type").getAsString())) {
        Widget w = Display.getDefault().find(op.get("id").getAsString());
        if (w instanceof StyledText)
          r.add((StyledText) w);
      }
    }
    return r;
  }

  @Test
  void nodeCompareShowsTheDescriptionsWithTheDifferenceColors() throws Exception {
    List<Path> files = twoRequirementFilesWithDifferentDescriptions();
    assertEquals(2, files.size(), "two requirements with different descriptions");
    JsonObject left = new JsonObject();
    left.addProperty("name", files.get(0).getFileName().toString());
    left.addProperty("path", files.get(0).toString());
    left.addProperty("label", "left");
    JsonObject right = new JsonObject();
    right.addProperty("name", files.get(1).getFileName().toString());
    right.addProperty("path", files.get(1).toString());
    right.addProperty("label", "right");
    right.addProperty("content", Files.readString(files.get(1)));
    JsonObject p = new JsonObject();
    p.addProperty("title", "Compare");
    p.add("left", left);
    p.add("right", right);
    int before = h.notifications("ui/openShell").size();
    assertTrue(h.call("compare/open", p).getAsBoolean());
    JsonObject panel = h.awaitPanelShell(before);
    String sid = panel.get("shell").getAsString();

    // a Swing document separates its lines with \n (DefaultEditorKit.EndOfLineStringProperty keeps the original one)
    List<String> expected = new ArrayList<>();
    expected.add(descriptionOf(files.get(0)).replace("\r\n", "\n"));
    expected.add(descriptionOf(files.get(1)).replace("\r\n", "\n"));
    Collections.sort(expected);
    boolean differ = !expected.get(0).equals(expected.get(1));
    assertTrue(differ);

    Color add = new Color(Display.getDefault(), 204, 255, 153);
    Color remove = new Color(Display.getDefault(), 255, 204, 204);
    h.await("the description panes with the difference colors", v -> {
      try {
        List<StyledText> panes = descriptionPanes(sid);
        if (panes.size() != 2)
          return false;
        boolean colored = panes.stream().flatMap(x -> x.getStyleRanges().stream()).anyMatch(
            r -> r.background != null && (r.background.css().equals(add.css()) || r.background.css().equals(remove.css())));
        return colored == differ;
      } catch (Exception e) {
        return false;
      }
    });
    List<StyledText> panes = descriptionPanes(sid);
    List<String> shown = new ArrayList<>();
    for (StyledText st : panes)
      shown.add(st.getText());
    Collections.sort(shown);
    assertEquals(expected, shown, () -> "the descriptions of the two nodes are shown: " + escaped(expected) + " / " + escaped(shown));
    h.closeShell(sid);
  }

  /** The texts with their control and non-ASCII characters escaped (a mismatch shows where they differ). */
  static String escaped(List<String> texts) {
    StringBuilder b = new StringBuilder();
    for (String t : texts) {
      b.append('[');
      for (char c : t.toCharArray())
        b.append(c < 32 || c > 126 ? String.format("\\u%04x", (int) c) : String.valueOf(c));
      b.append(']');
    }
    return b.toString();
  }
}
