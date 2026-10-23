package com.unitesk.requality.vscode.ui.swt.widgets;

import java.util.Objects;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.FocusListener;
import com.unitesk.requality.vscode.ui.swt.events.KeyListener;
import com.unitesk.requality.vscode.ui.swt.events.MenuDetectListener;
import com.unitesk.requality.vscode.ui.swt.events.MouseListener;
import com.unitesk.requality.vscode.ui.swt.events.TraverseListener;
import com.unitesk.requality.vscode.ui.swt.graphics.Color;
import com.unitesk.requality.vscode.ui.swt.graphics.Cursor;
import com.unitesk.requality.vscode.ui.swt.graphics.Font;
import com.unitesk.requality.vscode.ui.swt.graphics.Point;
import com.unitesk.requality.vscode.ui.swt.graphics.Rectangle;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.RowData;

/** {@code org.eclipse.swt.widgets.Control}. Geometry is computed by the browser, so size queries are estimates. */
public abstract class Control extends Widget {
  final Composite parent;
  private Object layoutData;
  private String sentLayoutData;
  private boolean enabled = true;
  private boolean visible = true;
  private String toolTip;
  private Color background;
  private Color foreground;
  private Font font;
  private Menu menu;
  private Point size = new Point(0, 0);
  /** Pop-up menus created with this control as parent. */
  final java.util.List<Menu> menus = new java.util.ArrayList<>();

  protected Control(Composite parent, int style, String kind) {
    super(parent, style, kind);
    this.parent = parent;
    if (parent != null)
      parent.addChild(this);
  }

  @Override
  Widget getParentWidget() {
    return parent;
  }

  @Override
  java.util.List<Widget> snapshotChildren() {
    java.util.List<Widget> r = new java.util.ArrayList<>();
    for (Menu m : menus)
      if (!m.isDisposed())
        r.add(m);
    return r;
  }

  @Override
  Shell getShellInternal() {
    return parent == null ? null : parent.getShellInternal();
  }

  public Composite getParent() {
    return parent;
  }

  public Shell getShell() {
    return getShellInternal();
  }

  public Object getLayoutData() {
    return layoutData;
  }

  public void setLayoutData(Object layoutData) {
    checkWidget();
    this.layoutData = layoutData;
    syncLayoutData();
  }

  /** Sends the layout data if it changed (SWT code mutates GridData objects after setting them). */
  void syncLayoutData() {
    JsonObject j = null;
    if (layoutData instanceof GridData)
      j = ((GridData) layoutData).toJson();
    else if (layoutData instanceof RowData)
      j = ((RowData) layoutData).toJson();
    String s = j == null ? null : j.toString();
    if (Objects.equals(s, sentLayoutData))
      return;
    sentLayoutData = s;
    set("layoutData", j);
  }

  public void setEnabled(boolean enabled) {
    checkWidget();
    this.enabled = enabled;
    set("enabled", enabled);
  }

  public boolean getEnabled() {
    return enabled;
  }

  public boolean isEnabled() {
    return enabled && (parent == null || parent.isEnabled());
  }

  public void setVisible(boolean visible) {
    checkWidget();
    this.visible = visible;
    set("visible", visible);
  }

  public boolean getVisible() {
    return visible;
  }

  public boolean isVisible() {
    return visible && (parent == null || parent.isVisible());
  }

  public void setToolTipText(String text) {
    checkWidget();
    toolTip = text;
    set("tooltip", text);
  }

  public String getToolTipText() {
    return toolTip;
  }

  public void setBackground(Color c) {
    checkWidget();
    background = c;
    set("bg", c == null ? null : c.css());
  }

  public Color getBackground() {
    return background != null ? background : display.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND);
  }

  public void setForeground(Color c) {
    checkWidget();
    foreground = c;
    set("fg", c == null ? null : c.css());
  }

  public Color getForeground() {
    return foreground != null ? foreground : display.getSystemColor(SWT.COLOR_BLACK);
  }

  public void setFont(Font f) {
    checkWidget();
    font = f;
    if (f == null) {
      set("font", null);
      return;
    }
    JsonObject o = new JsonObject();
    o.addProperty("bold", f.isBold());
    o.addProperty("italic", f.isItalic());
    o.addProperty("height", f.getHeight());
    set("font", o);
  }

  public Font getFont() {
    return font != null ? font : display.getSystemFont();
  }

  public void setCursor(Cursor c) {
  }

  /** Extension for JFace ControlDecoration: {kind, text} or null. */
  public void setDecoration(JsonObject decoration) {
    set("decoration", decoration);
  }

  /** Extension: CSS classes for the renderer (used by the JFace dialog parts). */
  public void setCssClass(String classes) {
    set("cls", classes);
  }

  public void setMenu(Menu m) {
    checkWidget();
    menu = m;
    set("menu", m == null ? null : m.id);
  }

  public Menu getMenu() {
    return menu;
  }

  public boolean setFocus() {
    checkWidget();
    display.setFocusControl(this);
    call("focus", null);
    return true;
  }

  public boolean forceFocus() {
    return setFocus();
  }

  public boolean isFocusControl() {
    return display.getFocusControl() == this;
  }

  public void setRedraw(boolean redraw) {
  }

  public void redraw() {
  }

  public void update() {
  }

  public void pack() {
  }

  public void pack(boolean changed) {
  }

  /**
   * {@code org.eclipse.swt.widgets.Control.requestLayout()}: marks the receiver and its ancestors as needing layout.
   * The browser lays out by itself, so the top-most ancestor's layout is re-sent.
   */
  public void requestLayout() {
    checkWidget();
    Composite top = this instanceof Composite ? (Composite) this : parent;
    while (top != null && top.parent != null)
      top = top.parent;
    if (top != null)
      top.layout(true);
  }

  public void setSize(int width, int height) {
    size = new Point(width, height);
  }

  public void setSize(Point p) {
    setSize(p.x, p.y);
  }

  public Point getSize() {
    return size;
  }

  public void setBounds(int x, int y, int width, int height) {
    setLocation(x, y);
    setSize(width, height);
  }

  public void setBounds(Rectangle r) {
    setBounds(r.x, r.y, r.width, r.height);
  }

  /** The bounds last set by the program (the renderer places controls with its layouts and does not report them). */
  public Rectangle getBounds() {
    return new Rectangle(location.x, location.y, size.x, size.y);
  }

  /**
   * {@code org.eclipse.swt.widgets.Control.getBorderWidth()}: the renderer draws the borders with CSS and does not
   * report a native border width, so 0 is returned.
   */
  public int getBorderWidth() {
    return 0;
  }

  private Point location = new Point(0, 0);

  public void setLocation(int x, int y) {
    location = new Point(x, y);
  }

  public void setLocation(Point p) {
    setLocation(p.x, p.y);
  }

  public Point getLocation() {
    return new Point(location.x, location.y);
  }

  public Point computeSize(int wHint, int hHint) {
    return new Point(wHint == SWT.DEFAULT ? 100 : wHint, hHint == SWT.DEFAULT ? 22 : hHint);
  }

  public Point computeSize(int wHint, int hHint, boolean changed) {
    return computeSize(wHint, hHint);
  }

  public Point toDisplay(int x, int y) {
    return new Point(x, y);
  }

  public Point toControl(int x, int y) {
    return new Point(x, y);
  }

  public boolean traverse(int traversal) {
    return false;
  }

  public void moveAbove(Control control) {
    if (parent != null)
      parent.moveChild(this, control, true);
  }

  public void moveBelow(Control control) {
    if (parent != null)
      parent.moveChild(this, control, false);
  }

  @Override
  void releaseWidget() {
    if (parent != null)
      parent.removeChild(this);
  }

  public void addKeyListener(KeyListener l) {
    addTyped(SWT.KeyUp, l);
    addTyped(SWT.KeyDown, l);
  }

  public void removeKeyListener(KeyListener l) {
    removeTyped(SWT.KeyUp, l);
    removeTyped(SWT.KeyDown, l);
  }

  public void addFocusListener(FocusListener l) {
    addTyped(SWT.FocusIn, l);
    addTyped(SWT.FocusOut, l);
  }

  public void removeFocusListener(FocusListener l) {
    removeTyped(SWT.FocusIn, l);
    removeTyped(SWT.FocusOut, l);
  }

  public void addMouseListener(MouseListener l) {
    addTyped(SWT.MouseDown, l);
    addTyped(SWT.MouseUp, l);
    addTyped(SWT.MouseDoubleClick, l);
  }

  public void removeMouseListener(MouseListener l) {
    removeTyped(SWT.MouseDown, l);
    removeTyped(SWT.MouseUp, l);
    removeTyped(SWT.MouseDoubleClick, l);
  }

  public void addMenuDetectListener(MenuDetectListener l) {
    addTyped(SWT.MenuDetect, l);
  }

  public void removeMenuDetectListener(MenuDetectListener l) {
    removeTyped(SWT.MenuDetect, l);
  }

  public void addControlListener(com.unitesk.requality.vscode.ui.swt.events.ControlListener l) {
    addTyped(SWT.Move, l);
    addTyped(SWT.Resize, l);
  }

  public void removeControlListener(com.unitesk.requality.vscode.ui.swt.events.ControlListener l) {
    removeTyped(SWT.Move, l);
    removeTyped(SWT.Resize, l);
  }

  public void addPaintListener(com.unitesk.requality.vscode.ui.swt.events.PaintListener l) {
    addTyped(SWT.Paint, l);
  }

  public void removePaintListener(com.unitesk.requality.vscode.ui.swt.events.PaintListener l) {
    removeTyped(SWT.Paint, l);
  }

  public void addMouseMoveListener(com.unitesk.requality.vscode.ui.swt.events.MouseMoveListener l) {
    addTyped(SWT.MouseMove, l);
  }

  public void removeMouseMoveListener(com.unitesk.requality.vscode.ui.swt.events.MouseMoveListener l) {
    removeTyped(SWT.MouseMove, l);
  }

  public void addMouseTrackListener(com.unitesk.requality.vscode.ui.swt.events.MouseTrackListener l) {
    addTyped(SWT.MouseEnter, l);
    addTyped(SWT.MouseExit, l);
    addTyped(SWT.MouseHover, l);
  }

  public void removeMouseTrackListener(com.unitesk.requality.vscode.ui.swt.events.MouseTrackListener l) {
    removeTyped(SWT.MouseEnter, l);
    removeTyped(SWT.MouseExit, l);
    removeTyped(SWT.MouseHover, l);
  }

  public void addTraverseListener(TraverseListener l) {
    addTyped(SWT.Traverse, l);
  }

  public void removeTraverseListener(TraverseListener l) {
    removeTyped(SWT.Traverse, l);
  }

  static Event keyEvent(JsonObject d) {
    Event e = new Event();
    e.keyCode = d.has("keyCode") ? d.get("keyCode").getAsInt() : 0;
    String ch = d.has("character") && !d.get("character").isJsonNull() ? d.get("character").getAsString() : "";
    e.character = ch.isEmpty() ? (char) 0 : ch.charAt(0);
    e.stateMask = d.has("stateMask") ? d.get("stateMask").getAsInt() : 0;
    return e;
  }

  /** A mouse event with the item under the pointer (rows of trees and tables), remembered for getItem(Point). */
  private Event mouseEventAt(JsonObject d) {
    Event e = mouseEvent(d);
    // The table column under the pointer travels in Event.index (unused by SWT mouse events).
    e.index = d.has("column") && !d.get("column").isJsonNull() ? d.get("column").getAsInt() : -1;
    if (d.has("item") && !d.get("item").isJsonNull()) {
      Widget w = display.find(d.get("item").getAsString());
      if (w instanceof Item)
        e.item = w;
    }
    mouseEventReceived(e);
    return e;
  }

  /** Called with every mouse event before the listeners (Tree and Table remember the item under the pointer). */
  void mouseEventReceived(Event e) {
  }

  static Event mouseEvent(JsonObject d) {
    Event e = new Event();
    e.button = d.has("button") ? d.get("button").getAsInt() : 1;
    e.x = d.has("x") ? d.get("x").getAsInt() : 0;
    e.y = d.has("y") ? d.get("y").getAsInt() : 0;
    e.stateMask = d.has("stateMask") ? d.get("stateMask").getAsInt() : 0;
    e.count = d.has("count") ? d.get("count").getAsInt() : 1;
    return e;
  }

  /**
   * VSCODE: the drag and drop role of the control in the webview ("dragSource" of a {@code DragSource}, "dropTarget"
   * of a {@code DropTarget}).
   */
  public void setDndRole(String role, boolean on) {
    set(role, on);
  }

  /** Common control events; subclasses handle their own state events first and delegate here. */
  @Override
  protected void onClientEvent(String type, JsonObject d) {
    switch (type) {
      case "focusin":
        display.setFocusControl(this);
        notifyListeners(SWT.FocusIn, new Event());
        break;
      case "focusout":
        if (display.getFocusControl() == this)
          display.setFocusControl(null);
        notifyListeners(SWT.FocusOut, new Event());
        break;
      case "keydown":
        notifyListeners(SWT.KeyDown, keyEvent(d));
        break;
      case "keyup":
        notifyListeners(SWT.KeyUp, keyEvent(d));
        break;
      case "traverse": {
        Event e = keyEvent(d);
        e.detail = d.has("detail") ? d.get("detail").getAsInt() : 0;
        notifyListeners(SWT.Traverse, e);
        break;
      }
      case "dblclick":
        notifyListeners(SWT.MouseDoubleClick, mouseEventAt(d));
        break;
      case "mousedown":
        notifyListeners(SWT.MouseDown, mouseEventAt(d));
        break;
      case "mouseup":
        notifyListeners(SWT.MouseUp, mouseEventAt(d));
        break;
      case "mousemove":
        notifyListeners(SWT.MouseMove, mouseEventAt(d));
        break;
      case "mouseenter":
        notifyListeners(SWT.MouseEnter, mouseEventAt(d));
        break;
      case "mouseexit":
        notifyListeners(SWT.MouseExit, mouseEventAt(d));
        break;
      case "mousehover":
        notifyListeners(SWT.MouseHover, mouseEventAt(d));
        break;
      case "menudetect": {
        Event e = mouseEvent(d);
        notifyListeners(SWT.MenuDetect, e);
        if (e.doit && menu != null && !menu.isDisposed())
          menu.showAt(e.x, e.y);
        break;
      }
      default:
        break;
    }
  }
}
