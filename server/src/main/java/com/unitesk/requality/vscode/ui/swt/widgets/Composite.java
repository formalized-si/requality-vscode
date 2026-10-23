package com.unitesk.requality.vscode.ui.swt.widgets;

import java.util.ArrayList;
import java.util.List;

/** {@code org.eclipse.swt.widgets.Composite}. */
public class Composite extends Scrollable {
  final List<Control> children = new ArrayList<>();
  private Layout layout;
  private int backgroundMode;

  public Composite(Composite parent, int style) {
    this(parent, style, "composite");
    created();
  }

  /** For subclasses, which must call {@link #created()}. */
  protected Composite(Composite parent, int style, String kind) {
    super(parent, style, kind);
  }

  void addChild(Control c) {
    synchronized (display) {
      children.add(c);
    }
  }

  void removeChild(Control c) {
    synchronized (display) {
      children.remove(c);
    }
  }

  void moveChild(Control c, Control ref, boolean above) {
    synchronized (display) {
      children.remove(c);
      int i = ref == null ? (above ? 0 : children.size()) : children.indexOf(ref) + (above ? 0 : 1);
      children.add(Math.max(0, Math.min(i, children.size())), c);
      List<String> order = new ArrayList<>();
      for (Control x : children)
        order.add(x.id);
      call("order", order);
    }
  }

  @Override
  java.util.List<Widget> snapshotChildren() {
    java.util.List<Widget> r = new ArrayList<>(super.snapshotChildren());
    r.addAll(java.util.Arrays.asList(getChildren()));
    return r;
  }

  public Control[] getChildren() {
    synchronized (display) {
      List<Control> r = new ArrayList<>();
      for (Control c : children)
        if (!c.isDisposed())
          r.add(c);
      return r.toArray(new Control[0]);
    }
  }

  public void setLayout(Layout layout) {
    checkWidget();
    this.layout = layout;
    set("layout", layout == null ? null : layout.toJson());
  }

  public Layout getLayout() {
    return layout;
  }

  /** Re-sends the (possibly mutated) layout; the browser lays out by itself. */
  public void layout() {
    layout(true);
  }

  public void layout(boolean changed) {
    if (layout != null)
      set("layout", layout.toJson());
    for (Control c : getChildren()) {
      c.syncLayoutData();
      if (c instanceof Composite)
        ((Composite) c).layout(changed);
    }
  }

  public void layout(boolean changed, boolean all) {
    layout(changed);
  }

  public void layout(Control[] changed) {
    layout(true);
  }

  public void setBackgroundMode(int mode) {
    backgroundMode = mode;
  }

  public int getBackgroundMode() {
    return backgroundMode;
  }

  public void setTabList(Control[] tabList) {
  }

  @Override
  void releaseChildren() {
    for (Control c : getChildren())
      c.dispose();
  }
}
