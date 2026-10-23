package com.unitesk.requality.vscode.ui.swt.widgets;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.ShellListener;
import com.unitesk.requality.vscode.ui.swt.graphics.Image;
import com.unitesk.requality.vscode.ui.swt.graphics.Point;

/**
 * {@code org.eclipse.swt.widgets.Shell}: a top-level window, rendered by a webview panel of the extension (or by a
 * webview view when {@link #setHost} names one, e.g. the Properties view).
 */
public class Shell extends Composite {
  private final Shell parentShell;
  private String text = "";
  private Image image;
  private Button defaultButton;
  private boolean opened;
  private String host = "panel";
  private String hostView;
  private Point minimumSize;

  public Shell() {
    this((Shell) null, SWT.SHELL_TRIM);
  }

  public Shell(int style) {
    this((Shell) null, style);
  }

  public Shell(Display display) {
    this((Shell) null, SWT.SHELL_TRIM);
  }

  public Shell(Display display, int style) {
    this((Shell) null, style);
  }

  public Shell(Shell parent) {
    this(parent, SWT.DIALOG_TRIM);
  }

  public Shell(Shell parent, int style) {
    super(null, style, "shell");
    this.parentShell = parent;
    created();
  }

  @Override
  Widget getParentWidget() {
    return null;
  }

  @Override
  Shell getShellInternal() {
    return this;
  }

  /** The owner shell (dialogs), may be null. */
  public Shell getParentShell() {
    return parentShell;
  }

  /** Renders the shell in a webview view (for example {@code requality.properties}) instead of a panel. */
  public void setHost(String host, String viewId) {
    this.host = host;
    this.hostView = viewId;
  }

  public String getHost() {
    return host;
  }

  public String getHostView() {
    return hostView;
  }

  public void setText(String text) {
    checkWidget();
    if (text == null)
      SWT.error(SWT.ERROR_NULL_ARGUMENT);
    this.text = text;
    set("title", this.text);
  }

  /**
   * {@code org.eclipse.swt.widgets.Shell.getShells()}: all shells of the display which are descendants of this shell
   * (the owner chain of {@link #getParentShell()} leads to this shell).
   */
  public Shell[] getShells() {
    checkWidget();
    java.util.List<Shell> r = new java.util.ArrayList<>();
    for (Shell s : getDisplay().getShells()) {
      for (Shell p = s.getParentShell(); p != null; p = p.getParentShell()) {
        if (p == this) {
          r.add(s);
          break;
        }
      }
    }
    return r.toArray(new Shell[0]);
  }

  public String getText() {
    return text;
  }

  public void setImage(Image image) {
    this.image = image;
  }

  public Image getImage() {
    return image;
  }

  public void setDefaultButton(Button b) {
    defaultButton = b;
    set("defaultButton", b == null ? null : b.id);
  }

  public Button getDefaultButton() {
    return defaultButton;
  }

  public void setMinimumSize(int width, int height) {
    minimumSize = new Point(width, height);
  }

  public void setMinimumSize(Point p) {
    minimumSize = p;
  }

  public Point getMinimumSize() {
    return minimumSize == null ? new Point(0, 0) : minimumSize;
  }

  public void open() {
    checkWidget();
    opened = true;
    display.setActiveShell(this);
    JsonObject o = new JsonObject();
    o.addProperty("title", text);
    o.addProperty("host", host);
    o.addProperty("view", hostView);
    Display.Transport t = Display.transport();
    if (t != null)
      t.openShell(this, o);
  }

  public boolean isOpened() {
    return opened && !isDisposed();
  }

  @Override
  public boolean isVisible() {
    return isOpened();
  }

  public void setActive() {
    display.setActiveShell(this);
    call("reveal", null);
  }

  public void forceActive() {
    setActive();
  }

  /** Closes like the user would: Close listeners may veto with {@code doit = false}. */
  public void close() {
    checkWidget();
    Event e = new Event();
    notifyListeners(SWT.Close, e);
    if (e.doit && !isDisposed())
      dispose();
  }

  @Override
  public void dispose() {
    if (isDisposed())
      return;
    boolean wasOpened = opened;
    super.dispose();
    display.shellDisposed(this);
    Display.Transport t = Display.transport();
    if (t != null && wasOpened)
      t.closeShell(this);
  }

  public void addShellListener(ShellListener l) {
    addTyped(SWT.Close, l);
  }

  public void removeShellListener(ShellListener l) {
    removeTyped(SWT.Close, l);
  }

  @Override
  protected void onClientEvent(String type, JsonObject d) {
    if ("close".equals(type)) {
      // The user closed the webview: behave like the window close button.
      if (!isDisposed())
        close();
      if (!isDisposed()) {
        // A vetoed close cannot keep a closed webview open: reopen it.
        open();
      }
      return;
    }
    if ("activate".equals(type)) {
      display.setActiveShell(this);
      return;
    }
    super.onClientEvent(type, d);
  }
}
