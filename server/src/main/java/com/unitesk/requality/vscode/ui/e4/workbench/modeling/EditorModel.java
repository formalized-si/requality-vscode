package com.unitesk.requality.vscode.ui.e4.workbench.modeling;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.e4.model.application.ui.MElementContainer;
import com.unitesk.requality.vscode.ui.e4.model.application.ui.MUIElement;
import com.unitesk.requality.vscode.ui.e4.model.application.ui.basic.MPart;
import com.unitesk.requality.vscode.ui.e4.model.application.ui.basic.MPartSashContainer;
import com.unitesk.requality.vscode.ui.e4.model.application.ui.basic.MPartSashContainerElement;
import com.unitesk.requality.vscode.ui.e4.model.application.ui.basic.impl.PartStackImpl;
import com.unitesk.requality.vscode.ui.workbench.IEditorPart;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPart;
import com.unitesk.requality.vscode.ui.workbench.Workbench;

/**
 * The E4 model of the editor area over the VS Code editor groups: the editor area is an {@link MPartSashContainer}
 * whose children are the editor groups ({@link PartStackImpl}, tag "Editor"), each holding the {@link MPart}s of its
 * editors. The layout is sent to the VS Code extension with {@link Workbench#placeEditorBeside}.
 */
public final class EditorModel {
  private EditorModel() {
  }

  private static final MPartSashContainer AREA = new MPartSashContainer();
  private static final List<MPart> PARTS = new ArrayList<>();

  /** The model service of the workbench ({@code IWorkbench.getService(EModelService.class)}). */
  public static final EModelService SERVICE = new EModelService() {
    @Override
    public MUIElement cloneElement(MUIElement element, Object applicationFragment) {
      if (!(element instanceof PartStackImpl))
        throw new IllegalArgumentException("Only editor groups are cloned: " + element);
      PartStackImpl clone = new PartStackImpl();
      clone.getTags().addAll(element.getTags());
      return clone;
    }

    @Override
    public void insert(MPartSashContainerElement toInsert, MPartSashContainerElement relativeTo, int where, float ratio) {
      insertBeside(toInsert, relativeTo, where);
    }

    @Override
    public void bringToTop(MUIElement element) {
      EditorModel.bringToTop(element);
    }
  };

  /** The model part of an editor, or null for a part that is not an editor of the editor area. */
  public static synchronized MPart partOf(Object part) {
    for (MPart p : PARTS)
      if (p.getEditor() == part)
        return p;
    return null;
  }

  /** An editor opened: its part joins the group of the active editor, else the first group, else a new group. */
  public static synchronized void editorOpened(IEditorPart editor) {
    if (editor == null || partOf(editor) != null)
      return;
    MPart part = new MPart(editor);
    PARTS.add(part);
    PartStackImpl group = groupOfActiveEditor();
    if (group == null)
      group = newGroup();
    group.getChildren().add(part);
    group.setSelectedElement(part);
  }

  /** The VS Code view column of each group (the group's position among the editor groups). */
  private static final java.util.Map<PartStackImpl, Integer> COLUMNS = new java.util.IdentityHashMap<>();

  /**
   * VSCODE: the tab of the editor is in the VS Code editor group of the column: its part moves to the part stack of that
   * column (created, and ordered among the stacks by column, when no editor was there).
   */
  public static synchronized void editorInColumn(IEditorPart editor, int column) {
    MPart part = partOf(editor);
    if (part == null || column <= 0)
      return;
    PartStackImpl target = null;
    for (java.util.Map.Entry<PartStackImpl, Integer> e : COLUMNS.entrySet())
      if (e.getValue() == column && AREA.getChildren().contains(e.getKey()))
        target = e.getKey();
    if (target == null && part.getParent() instanceof PartStackImpl current && !COLUMNS.containsKey(current))
      target = current; // the group of the part had no column yet: it is this column's
    if (target == null) {
      target = new PartStackImpl();
      target.getTags().add("Editor");
      int index = 0;
      for (MUIElement g : AREA.getChildren()) {
        Integer c = g instanceof PartStackImpl ? COLUMNS.get(g) : null;
        if (c != null && c < column)
          index = AREA.getChildren().indexOf(g) + 1;
      }
      AREA.getChildren().add(index, target);
    }
    COLUMNS.put(target, column);
    if (part.getParent() != target) {
      MElementContainer<MUIElement> from = part.getParent();
      if (from != null) {
        from.getChildren().remove(part);
        if (from instanceof PartStackImpl && from.getChildren().isEmpty()) {
          AREA.getChildren().remove(from);
          COLUMNS.remove(from);
        }
      }
      target.getChildren().add(part);
    }
  }

  /** An editor closed: its part leaves its group, and the group goes with its last editor. */
  public static synchronized void editorClosed(IEditorPart editor) {
    MPart part = partOf(editor);
    if (part == null)
      return;
    PARTS.remove(part);
    if (part.getParent() instanceof PartStackImpl) {
      PartStackImpl group = (PartStackImpl) part.getParent();
      group.getChildren().remove(part);
      if (group.getChildren().isEmpty()) {
        AREA.getChildren().remove(group);
        COLUMNS.remove(group);
      }
    }
  }

  private static PartStackImpl groupOfActiveEditor() {
    IWorkbenchPart active = Workbench.activePart();
    MPart activePart = partOf(active);
    if (activePart != null && activePart.getParent() instanceof PartStackImpl)
      return (PartStackImpl) activePart.getParent();
    for (MUIElement g : AREA.getChildren())
      if (g instanceof PartStackImpl)
        return (PartStackImpl) g;
    return null;
  }

  private static PartStackImpl newGroup() {
    PartStackImpl group = new PartStackImpl();
    group.getTags().add("Editor");
    AREA.getChildren().add(group);
    return group;
  }

  private static synchronized void insertBeside(MPartSashContainerElement toInsert, MPartSashContainerElement relativeTo,
      int where) {
    MElementContainer<MUIElement> container = relativeTo.getParent();
    if (container == null)
      throw new IllegalArgumentException("No container of " + relativeTo);
    MElementContainer<MUIElement> from = toInsert.getParent();
    if (from != null)
      from.getChildren().remove(toInsert);
    int index = container.getChildren().indexOf(relativeTo);
    boolean after = where == EModelService.RIGHT_OF || where == EModelService.BELOW;
    container.getChildren().add(after ? index + 1 : index, toInsert);
    if (where == EModelService.RIGHT_OF || where == EModelService.LEFT_OF) {
      MPart left = where == EModelService.RIGHT_OF ? selectedPart(relativeTo) : selectedPart(toInsert);
      MPart right = where == EModelService.RIGHT_OF ? selectedPart(toInsert) : selectedPart(relativeTo);
      if (left != null && right != null)
        Workbench.placeEditorBeside(left.getEditor(), right.getEditor());
    }
  }

  private static synchronized void bringToTop(MUIElement element) {
    MPart part = element instanceof MPart ? (MPart) element : selectedPart(element);
    if (part == null || !(part.getParent() instanceof PartStackImpl))
      return;
    PartStackImpl group = (PartStackImpl) part.getParent();
    group.setSelectedElement(part);
    int index = AREA.getChildren().indexOf(group);
    if (index > 0) {
      MPart left = selectedPart(AREA.getChildren().get(index - 1));
      if (left != null)
        Workbench.placeEditorBeside(left.getEditor(), part.getEditor());
    }
  }

  /** The shown editor of a group (or the element itself when it is an editor). */
  private static MPart selectedPart(MUIElement element) {
    if (element instanceof MPart)
      return (MPart) element;
    if (element instanceof PartStackImpl) {
      MUIElement shown = ((PartStackImpl) element).getSelectedElement();
      return shown instanceof MPart ? (MPart) shown : null;
    }
    return null;
  }
}
