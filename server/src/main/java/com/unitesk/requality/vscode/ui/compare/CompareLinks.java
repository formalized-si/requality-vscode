package com.unitesk.requality.vscode.ui.compare;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * The Link with Editor of the views that open compare editors for their elements ({@code SynchronizeView} linking, the
 * {@code isLinkingEnabled}, {@code linkToEditor} and {@code partActivated} of Eclipse's team UI). A compare editor opened
 * for an element is linked with it: when the element is selected with the linking on, its compare editor is brought to the
 * top (a text compare is sent again with {@code client/openDiff}, which reveals the open diff editor of VS Code; another
 * compare is shown in its panel, which is set active), and when the compare editor becomes active, the element is selected.
 * <p>
 * VSCODE: the diff editors of VS Code report their activation and closing with the sides they were opened with, as the
 * extension echoes them: {@code editor/activeDiff} and {@code editor/diffClosed} carry {@code left} and {@code right}, each
 * the {@code path} (a workspace file) or the {@code id} (read-only contents) of the side in the {@code client/openDiff}
 * request. The panels of the compare editors report their activation as {@code ui/shellFocused}.
 */
public final class CompareLinks {
  private static final List<Link> LINKS = new CopyOnWriteArrayList<>();
  private static volatile BooleanSupplier linking = () -> false;

  /** A compare editor linked with an element: a text compare (the diff sent to VS Code) or a panel (its shell). */
  private static final class Link {
    final CompareEditorInput input;
    final Object element;
    final Consumer<Object> select;
    JsonObject diff;
    Shell shell;

    Link(CompareEditorInput input, Object element, Consumer<Object> select) {
      this.input = input;
      this.element = element;
      this.select = select;
    }
  }

  private CompareLinks() {
  }

  /** Sets the Link with Editor state of the views (the Synchronize view: its preference). */
  public static void setLinkingEnabled(BooleanSupplier enabled) {
    linking = enabled == null ? () -> false : enabled;
  }

  /** Whether the views are linked with the compare editors. */
  public static boolean isLinkingEnabled() {
    return linking.getAsBoolean();
  }

  /**
   * The compare input is about to be opened for the element: when it opens, it is linked with the element, and selecting the
   * element shows it. The select callback selects the element in its view (called on the UI thread).
   */
  public static void expect(CompareEditorInput input, Object element, Consumer<Object> select) {
    if (input != null && element != null && select != null)
      LINKS.add(new Link(input, element, select));
  }

  /** The compare input did not open (it failed, or it has no differences): it is not linked. */
  static void forget(CompareEditorInput input) {
    LINKS.removeIf(l -> l.input == input && l.diff == null && l.shell == null);
  }

  /** The compare input opened as a text compare in VS Code, with the {@code client/openDiff} request that opened it. */
  static void diffOpened(CompareEditorInput input, JsonObject diff) {
    for (Link l : LINKS)
      if (l.input == input && l.diff == null && l.shell == null)
        l.diff = diff;
  }

  /** The compare input opened in its panel (the shell of the panel). */
  static void shellOpened(CompareEditorInput input, Shell shell) {
    for (Link l : LINKS)
      if (l.input == input && l.diff == null && l.shell == null) {
        l.shell = shell;
        shell.addDisposeListener(e -> LINKS.remove(l));
      }
  }

  /**
   * The element was selected in its view: with the linking on, its compare editor is brought to the top. Returns whether an
   * open compare editor of the element was shown.
   */
  public static boolean bringToTop(Object element) {
    if (!isLinkingEnabled())
      return false;
    for (int i = LINKS.size() - 1; i >= 0; i--) {
      Link l = LINKS.get(i);
      if (!Objects.equals(l.element, element))
        continue;
      if (l.diff != null) {
        CompareEditor.sendDiff(l.diff);
        return true;
      }
      if (l.shell != null && !l.shell.isDisposed()) {
        l.shell.setActive();
        return true;
      }
    }
    return false;
  }

  /** The diff editor of VS Code with the two sides became active: with the linking on, its element is selected. */
  public static void diffActivated(JsonObject left, JsonObject right) {
    if (!isLinkingEnabled())
      return;
    Link l = find(left, right);
    if (l != null)
      l.select.accept(l.element);
  }

  /** The diff editor of VS Code with the two sides was closed: it is no longer shown when its element is selected. */
  public static void diffClosed(JsonObject left, JsonObject right) {
    Link l = find(left, right);
    if (l != null)
      LINKS.remove(l);
  }

  /** A panel of a compare editor became active: with the linking on, its element is selected. */
  public static void shellActivated(Shell shell) {
    if (!isLinkingEnabled() || shell == null)
      return;
    for (Link l : LINKS)
      if (l.shell == shell) {
        l.select.accept(l.element);
        return;
      }
  }

  private static Link find(JsonObject left, JsonObject right) {
    if (left == null || right == null)
      return null;
    for (Link l : LINKS)
      if (l.diff != null && key(l.diff.getAsJsonObject("left")).equals(key(left))
          && key(l.diff.getAsJsonObject("right")).equals(key(right)))
        return l;
    return null;
  }

  /** The identity of a side of a diff: its path, else its id. */
  private static String key(JsonObject side) {
    if (side == null)
      return "";
    if (side.has("path"))
      return "path:" + side.get("path").getAsString();
    return side.has("id") ? "id:" + side.get("id").getAsString() : "";
  }
}
