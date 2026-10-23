package com.unitesk.requality.vscode.ui.swt.widgets;

import com.unitesk.requality.vscode.ui.swt.graphics.Rectangle;

/** {@code org.eclipse.swt.widgets.Event}. */
public class Event {
  public Display display;
  public Widget widget;
  public int type;
  public int detail;
  public Widget item;
  public int index;
  public int x;
  public int y;
  public int width;
  public int height;
  public int count;
  public Object data;
  public int button;
  public char character;
  public int keyCode;
  public int keyLocation;
  public int stateMask;
  public int start;
  public int end;
  public String text;
  public boolean doit = true;
  public int time;

  public Rectangle getBounds() {
    return new Rectangle(x, y, width, height);
  }
}
