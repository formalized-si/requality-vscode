package com.unitesk.requality.vscode.ui.swt.widgets;

import java.util.ArrayList;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.graphics.Color;
import com.unitesk.requality.vscode.ui.swt.graphics.Font;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.graphics.Rectangle;

/** {@code org.eclipse.swt.widgets.TreeItem}. */
public class TreeItem extends Item {
  final Tree tree;
  final TreeItem parentItem;
  final java.util.List<TreeItem> items = new ArrayList<>();
  private final java.util.List<String> texts = new ArrayList<>();
  private final java.util.List<String> images = new ArrayList<>();
  private boolean expanded;
  private boolean checked;
  private boolean grayed;
  private Color foreground;
  private Color background;
  private Font font;

  public TreeItem(Tree parent, int style) {
    this(parent, style, -1);
  }

  public TreeItem(Tree parent, int style, int index) {
    super(parent, style, "treeitem");
    tree = parent;
    parentItem = null;
    insert(parent.items, index);
    created();
  }

  public TreeItem(TreeItem parentItem, int style) {
    this(parentItem, style, -1);
  }

  public TreeItem(TreeItem parentItem, int style, int index) {
    super(parentItem, style, "treeitem");
    tree = parentItem.tree;
    this.parentItem = parentItem;
    insert(parentItem.items, index);
    parentItem.set("hasChildren", true);
    created();
  }

  private void insert(java.util.List<TreeItem> list, int index) {
    if (index < 0 || index > list.size())
      list.add(this);
    else
      list.add(index, this);
  }

  private java.util.List<TreeItem> siblings() {
    return parentItem == null ? tree.items : parentItem.items;
  }

  @Override
  JsonObject createOp() {
    JsonObject o = super.createOp();
    o.getAsJsonObject("props").addProperty("position", siblings().indexOf(this));
    return o;
  }

  @Override
  java.util.List<Widget> snapshotChildren() {
    return new ArrayList<>(items);
  }

  public Tree getParent() {
    return tree;
  }

  public TreeItem getParentItem() {
    return parentItem;
  }

  public int getItemCount() {
    return items.size();
  }

  public TreeItem getItem(int index) {
    return items.get(index);
  }

  public TreeItem[] getItems() {
    return items.toArray(new TreeItem[0]);
  }

  public int indexOf(TreeItem item) {
    return items.indexOf(item);
  }

  public void setItemCount(int count) {
    while (items.size() > count)
      items.get(items.size() - 1).dispose();
    while (items.size() < count)
      new TreeItem(this, 0);
  }

  public void removeAll() {
    for (TreeItem i : getItems())
      i.dispose();
  }

  public void clearAll(boolean all) {
  }

  /** Marks the item as expandable before its children are created (lazy trees). */
  public void setHasChildren(boolean b) {
    set("hasChildren", b);
  }

  @Override
  public void setText(String text) {
    setText(0, text);
  }

  public void setText(int index, String text) {
    while (texts.size() <= index)
      texts.add("");
    texts.set(index, text == null ? "" : text);
    set("texts", new ArrayList<>(texts));
  }

  public void setText(String[] strings) {
    for (int i = 0; i < strings.length; i++)
      setText(i, strings[i]);
  }

  @Override
  public String getText() {
    return getText(0);
  }

  public String getText(int index) {
    return index < texts.size() ? texts.get(index) : "";
  }

  @Override
  public void setImage(Image image) {
    setImage(0, image);
  }

  public void setImage(int index, Image image) {
    while (images.size() <= index)
      images.add(null);
    images.set(index, image == null ? null : image.getPath());
    set("images", new ArrayList<>(images));
  }

  @Override
  public Image getImage() {
    return getImage(0);
  }

  public Image getImage(int index) {
    return index < images.size() && images.get(index) != null ? new Image(null, images.get(index)) : null;
  }

  public void setExpanded(boolean expanded) {
    this.expanded = expanded;
    set("expanded", expanded);
  }

  public boolean getExpanded() {
    return expanded;
  }

  public void setChecked(boolean checked) {
    this.checked = checked;
    set("checked", checked);
  }

  void setCheckedQuiet(boolean checked) {
    this.checked = checked;
    setQuiet("checked", checked);
  }

  public boolean getChecked() {
    return checked;
  }

  public void setGrayed(boolean grayed) {
    this.grayed = grayed;
    set("grayed", grayed);
  }

  public boolean getGrayed() {
    return grayed;
  }

  public void setForeground(Color c) {
    foreground = c;
    set("fg", c == null ? null : c.css());
  }

  public Color getForeground() {
    return foreground;
  }

  public void setBackground(Color c) {
    background = c;
    set("bg", c == null ? null : c.css());
  }

  public Color getBackground() {
    return background;
  }

  public void setFont(Font f) {
    font = f;
    if (f == null) {
      set("font", null);
      return;
    }
    JsonObject o = new JsonObject();
    o.addProperty("bold", f.isBold());
    o.addProperty("italic", f.isItalic());
    set("font", o);
  }

  public Font getFont() {
    return font;
  }

  public Rectangle getBounds() {
    return new Rectangle(0, 0, 0, 20);
  }

  @Override
  void releaseChildren() {
    for (TreeItem i : getItems())
      i.dispose();
  }

  @Override
  void releaseWidget() {
    siblings().remove(this);
    tree.unselect(this);
    if (parentItem != null && parentItem.items.isEmpty())
      parentItem.set("hasChildren", false);
  }
}
