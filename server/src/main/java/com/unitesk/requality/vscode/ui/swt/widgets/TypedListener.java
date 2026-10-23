package com.unitesk.requality.vscode.ui.swt.widgets;

import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.DisposeEvent;
import com.unitesk.requality.vscode.ui.swt.events.DisposeListener;
import com.unitesk.requality.vscode.ui.swt.events.FocusEvent;
import com.unitesk.requality.vscode.ui.swt.events.FocusListener;
import com.unitesk.requality.vscode.ui.swt.events.KeyEvent;
import com.unitesk.requality.vscode.ui.swt.events.KeyListener;
import com.unitesk.requality.vscode.ui.swt.events.MenuDetectEvent;
import com.unitesk.requality.vscode.ui.swt.events.MenuDetectListener;
import com.unitesk.requality.vscode.ui.swt.events.ModifyEvent;
import com.unitesk.requality.vscode.ui.swt.events.ModifyListener;
import com.unitesk.requality.vscode.ui.swt.events.MouseEvent;
import com.unitesk.requality.vscode.ui.swt.events.MouseListener;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.events.SelectionListener;
import com.unitesk.requality.vscode.ui.swt.events.TraverseEvent;
import com.unitesk.requality.vscode.ui.swt.events.TraverseListener;
import com.unitesk.requality.vscode.ui.swt.events.TreeEvent;
import com.unitesk.requality.vscode.ui.swt.events.TreeListener;
import com.unitesk.requality.vscode.ui.swt.events.VerifyEvent;
import com.unitesk.requality.vscode.ui.swt.events.VerifyListener;

/** {@code org.eclipse.swt.widgets.TypedListener}: adapts typed listeners to untyped events. */
public class TypedListener implements Listener {
  protected final Object eventListener;

  public TypedListener(Object listener) {
    this.eventListener = listener;
  }

  public Object getEventListener() {
    return eventListener;
  }

  @Override
  public void handleEvent(Event e) {
    switch (e.type) {
      case SWT.Selection: {
        SelectionEvent se = new SelectionEvent(e);
        ((SelectionListener) eventListener).widgetSelected(se);
        e.doit = se.doit;
        break;
      }
      case SWT.DefaultSelection: {
        SelectionEvent se = new SelectionEvent(e);
        ((SelectionListener) eventListener).widgetDefaultSelected(se);
        e.doit = se.doit;
        break;
      }
      case SWT.Move:
        ((com.unitesk.requality.vscode.ui.swt.events.ControlListener) eventListener)
            .controlMoved(new com.unitesk.requality.vscode.ui.swt.events.ControlEvent(e));
        break;
      case SWT.Resize:
        ((com.unitesk.requality.vscode.ui.swt.events.ControlListener) eventListener)
            .controlResized(new com.unitesk.requality.vscode.ui.swt.events.ControlEvent(e));
        break;
      case SWT.Paint:
        ((com.unitesk.requality.vscode.ui.swt.events.PaintListener) eventListener)
            .paintControl(new com.unitesk.requality.vscode.ui.swt.events.PaintEvent(e));
        break;
      case SWT.MouseMove:
        ((com.unitesk.requality.vscode.ui.swt.events.MouseMoveListener) eventListener).mouseMove(new MouseEvent(e));
        break;
      case SWT.MouseEnter:
        ((com.unitesk.requality.vscode.ui.swt.events.MouseTrackListener) eventListener).mouseEnter(new MouseEvent(e));
        break;
      case SWT.MouseExit:
        ((com.unitesk.requality.vscode.ui.swt.events.MouseTrackListener) eventListener).mouseExit(new MouseEvent(e));
        break;
      case SWT.MouseHover:
        ((com.unitesk.requality.vscode.ui.swt.events.MouseTrackListener) eventListener).mouseHover(new MouseEvent(e));
        break;
      case SWT.Modify:
        ((ModifyListener) eventListener).modifyText(new ModifyEvent(e));
        break;
      case SWT.Verify: {
        VerifyEvent ve = new VerifyEvent(e);
        ((VerifyListener) eventListener).verifyText(ve);
        e.text = ve.text;
        e.doit = ve.doit;
        break;
      }
      case SWT.KeyDown: {
        KeyEvent ke = new KeyEvent(e);
        ((KeyListener) eventListener).keyPressed(ke);
        e.doit = ke.doit;
        break;
      }
      case SWT.KeyUp: {
        KeyEvent ke = new KeyEvent(e);
        ((KeyListener) eventListener).keyReleased(ke);
        e.doit = ke.doit;
        break;
      }
      case SWT.FocusIn:
        ((FocusListener) eventListener).focusGained(new FocusEvent(e));
        break;
      case SWT.FocusOut:
        ((FocusListener) eventListener).focusLost(new FocusEvent(e));
        break;
      case SWT.MouseDoubleClick:
        ((MouseListener) eventListener).mouseDoubleClick(new MouseEvent(e));
        break;
      case SWT.MouseDown:
        ((MouseListener) eventListener).mouseDown(new MouseEvent(e));
        break;
      case SWT.MouseUp:
        ((MouseListener) eventListener).mouseUp(new MouseEvent(e));
        break;
      case SWT.Dispose:
        ((DisposeListener) eventListener).widgetDisposed(new DisposeEvent(e));
        break;
      case SWT.Traverse: {
        TraverseEvent te = new TraverseEvent(e);
        ((TraverseListener) eventListener).keyTraversed(te);
        e.doit = te.doit;
        break;
      }
      case SWT.MenuDetect: {
        MenuDetectEvent me = new MenuDetectEvent(e);
        ((MenuDetectListener) eventListener).menuDetected(me);
        e.doit = me.doit;
        break;
      }
      case SWT.Close: {
        com.unitesk.requality.vscode.ui.swt.events.ShellEvent se = new com.unitesk.requality.vscode.ui.swt.events.ShellEvent(e);
        ((com.unitesk.requality.vscode.ui.swt.events.ShellListener) eventListener).shellClosed(se);
        e.doit = se.doit;
        break;
      }
      case SWT.Show:
        if (eventListener instanceof com.unitesk.requality.vscode.ui.swt.events.MenuListener)
          ((com.unitesk.requality.vscode.ui.swt.events.MenuListener) eventListener).menuShown(new com.unitesk.requality.vscode.ui.swt.events.MenuEvent(e));
        break;
      case SWT.Hide:
        if (eventListener instanceof com.unitesk.requality.vscode.ui.swt.events.MenuListener)
          ((com.unitesk.requality.vscode.ui.swt.events.MenuListener) eventListener).menuHidden(new com.unitesk.requality.vscode.ui.swt.events.MenuEvent(e));
        break;
      case SWT.Expand:
        ((TreeListener) eventListener).treeExpanded(new TreeEvent(e));
        break;
      case SWT.Collapse:
        ((TreeListener) eventListener).treeCollapsed(new TreeEvent(e));
        break;
      default:
        break;
    }
  }
}
