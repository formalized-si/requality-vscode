package com.unitesk.requality.vscode.ui.swt.widgets;

import java.util.ArrayList;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.graphics.Color;
import com.unitesk.requality.vscode.ui.swt.graphics.Font;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.graphics.Rectangle;

/** {@code org.eclipse.swt.widgets.TableItem}. */
public class TableItem extends Item {
  final Table parent;
  private final java.util.List<String> texts = new ArrayList<>();
  private final java.util.List<String> images = new ArrayList<>();
  private final java.util.List<String> foregrounds = new ArrayList<>();
  private final java.util.List<String> backgrounds = new ArrayList<>();
  private boolean checked;
  private boolean grayed;
  private Color background;
  private Color foreground;
  private Font font;

  public TableItem(Table parent, int style) {
    this(parent, style, -1);
  }

  public TableItem(Table parent, int style, int index) {
    super(parent, style, "row");
    this.parent = parent;
    int at = parent.addItem(this, index);
    
    created();
  }

  public Table getParent() {
    return parent;
  }

  private static void ensure(java.util.List<String> l, int index) {
    while (l.size() <= index)
      l.add(null);
  }

  @Override
  public void setText(String text) {
    setText(0, text);
  }

  public void setText(int index, String text) {
    checkWidget();
    ensure(texts, index);
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
    return index < texts.size() && texts.get(index) != null ? texts.get(index) : "";
  }

  @Override
  public void setImage(Image image) {
    setImage(0, image);
  }

  public void setImage(int index, Image image) {
    checkWidget();
    ensure(images, index);
    images.set(index, image == null ? null : image.getPath());
    set("images", new ArrayList<>(images));
  }

  public void setImage(Image[] imgs) {
    for (int i = 0; i < imgs.length; i++)
      setImage(i, imgs[i]);
  }

  @Override
  public Image getImage() {
    return getImage(0);
  }

  public Image getImage(int index) {
    return index < images.size() && images.get(index) != null ? new Image(null, images.get(index)) : null;
  }

  public void setChecked(boolean checked) {
    checkWidget();
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

  public void setBackground(Color c) {
    background = c;
    set("bg", c == null ? null : c.css());
  }

  public void setBackground(int index, Color c) {
    ensure(backgrounds, index);
    backgrounds.set(index, c == null ? null : c.css());
    set("bgs", new ArrayList<>(backgrounds));
  }

  public Color getBackground() {
    return background;
  }

  public void setForeground(Color c) {
    foreground = c;
    set("fg", c == null ? null : c.css());
  }

  public void setForeground(int index, Color c) {
    ensure(foregrounds, index);
    foregrounds.set(index, c == null ? null : c.css());
    set("fgs", new ArrayList<>(foregrounds));
  }

  public Color getForeground() {
    return foreground;
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

  public void setFont(int index, Font f) {
    setFont(f);
  }

  public Font getFont() {
    return font;
  }

  public Rectangle getBounds() {
    return new Rectangle(0, 0, 0, 20);
  }

  public Rectangle getBounds(int index) {
    return getBounds();
  }

  void clear() {
    texts.clear();
    images.clear();
    set("texts", new ArrayList<>(texts));
    set("images", new ArrayList<>(images));
  }

  @Override
  JsonObject createOp() {
    JsonObject o = super.createOp();
    o.getAsJsonObject("props").addProperty("position", parent.items.indexOf(this));
    return o;
  }

  @Override
  void releaseWidget() {
    parent.removeItem(this);
  }
}
