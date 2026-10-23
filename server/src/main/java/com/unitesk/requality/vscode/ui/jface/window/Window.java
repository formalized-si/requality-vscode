package com.unitesk.requality.vscode.ui.jface.window;

import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.ShellAdapter;
import com.unitesk.requality.vscode.ui.swt.events.ShellEvent;
import com.unitesk.requality.vscode.ui.swt.graphics.Point;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Layout;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.jface.window.Window}. {@link #open()} creates the shell (a webview panel in VS Code) and, when
 * blocking, runs the nested event loop until the window closes. Called off the UI thread, it switches to it first.
 */
public abstract class Window implements IShellProvider {
  public static final int OK = 0;
  public static final int CANCEL = 1;

  private Shell parentShell;
  private Shell shell;
  private Control contents;
  private int returnCode = OK;
  private boolean block = true;
  private int shellStyle = SWT.SHELL_TRIM;

  protected Window(Shell parentShell) {
    this.parentShell = parentShell;
  }

  protected Window(IShellProvider provider) {
    this.parentShell = provider == null ? null : provider.getShell();
  }

  public void setBlockOnOpen(boolean block) {
    this.block = block;
  }

  protected void setShellStyle(int style) {
    shellStyle = style;
  }

  protected int getShellStyle() {
    return shellStyle;
  }

  public void setParentShell(Shell s) {
    parentShell = s;
  }

  protected Shell getParentShell() {
    return parentShell;
  }

  @Override
  public Shell getShell() {
    return shell;
  }

  protected Control getContents() {
    return contents;
  }

  public int getReturnCode() {
    return returnCode;
  }

  protected void setReturnCode(int code) {
    returnCode = code;
  }

  public void create() {
    shell = createShell();
    contents = createContents(shell);
    initializeBounds();
  }

  protected Shell createShell() {
    Shell s = new Shell(parentShell, shellStyle);
    s.addShellListener(new ShellAdapter() {
      @Override
      public void shellClosed(ShellEvent e) {
        e.doit = false;
        if (canHandleShellCloseEvent())
          handleShellCloseEvent();
      }
    });
    configureShell(s);
    return s;
  }

  protected void configureShell(Shell newShell) {
    Layout l = getLayout();
    if (l != null)
      newShell.setLayout(l);
  }

  protected Layout getLayout() {
    GridLayout layout = new GridLayout();
    layout.marginHeight = 0;
    layout.marginWidth = 0;
    return layout;
  }

  protected Control createContents(Composite parent) {
    return new Composite(parent, SWT.NONE);
  }

  protected void initializeBounds() {
  }

  protected Point getInitialSize() {
    return new Point(500, 400);
  }

  protected Point getInitialLocation(Point initialSize) {
    return new Point(0, 0);
  }

  protected void constrainShellSize() {
  }

  protected boolean canHandleShellCloseEvent() {
    return true;
  }

  protected void handleShellCloseEvent() {
    setReturnCode(CANCEL);
    close();
  }

  public int open() {
    Display display = Display.getDefault();
    if (!display.isUIThread())
      return display.syncCall(this::open);
    if (shell == null || shell.isDisposed()) {
      shell = null;
      create();
    }
    shell.open();
    if (block)
      display.runUntil(() -> shell == null || shell.isDisposed());
    return returnCode;
  }

  public boolean close() {
    if (shell == null || shell.isDisposed())
      return true;
    Shell s = shell;
    shell = null;
    s.dispose();
    contents = null;
    return true;
  }
}
