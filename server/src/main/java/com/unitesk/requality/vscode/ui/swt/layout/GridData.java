package com.unitesk.requality.vscode.ui.swt.layout;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.ui.swt.SWT;

/** {@code org.eclipse.swt.layout.GridData} with the SWT constants and defaults. */
public final class GridData {
  public static final int BEGINNING = SWT.BEGINNING;
  public static final int CENTER = 2;
  public static final int END = 3;
  public static final int FILL = SWT.FILL;
  public static final int VERTICAL_ALIGN_BEGINNING = 1 << 1;
  public static final int VERTICAL_ALIGN_CENTER = 1 << 2;
  public static final int VERTICAL_ALIGN_END = 1 << 3;
  public static final int VERTICAL_ALIGN_FILL = 1 << 4;
  public static final int HORIZONTAL_ALIGN_BEGINNING = 1 << 5;
  public static final int HORIZONTAL_ALIGN_CENTER = 1 << 6;
  public static final int HORIZONTAL_ALIGN_END = 1 << 7;
  public static final int HORIZONTAL_ALIGN_FILL = 1 << 8;
  public static final int GRAB_HORIZONTAL = 1 << 9;
  public static final int GRAB_VERTICAL = 1 << 10;
  public static final int FILL_VERTICAL = VERTICAL_ALIGN_FILL | GRAB_VERTICAL;
  public static final int FILL_HORIZONTAL = HORIZONTAL_ALIGN_FILL | GRAB_HORIZONTAL;
  public static final int FILL_BOTH = FILL_VERTICAL | FILL_HORIZONTAL;

  public int verticalAlignment = CENTER;
  public int horizontalAlignment = BEGINNING;
  public int widthHint = SWT.DEFAULT;
  public int heightHint = SWT.DEFAULT;
  public int horizontalIndent = 0;
  public int verticalIndent = 0;
  public int horizontalSpan = 1;
  public int verticalSpan = 1;
  public boolean grabExcessHorizontalSpace = false;
  public boolean grabExcessVerticalSpace = false;
  public int minimumWidth = 0;
  public int minimumHeight = 0;
  public boolean exclude = false;

  public GridData() {
  }

  public GridData(int style) {
    if ((style & VERTICAL_ALIGN_BEGINNING) != 0)
      verticalAlignment = BEGINNING;
    if ((style & VERTICAL_ALIGN_CENTER) != 0)
      verticalAlignment = CENTER;
    if ((style & VERTICAL_ALIGN_FILL) != 0)
      verticalAlignment = FILL;
    if ((style & VERTICAL_ALIGN_END) != 0)
      verticalAlignment = END;
    if ((style & HORIZONTAL_ALIGN_BEGINNING) != 0)
      horizontalAlignment = BEGINNING;
    if ((style & HORIZONTAL_ALIGN_CENTER) != 0)
      horizontalAlignment = CENTER;
    if ((style & HORIZONTAL_ALIGN_FILL) != 0)
      horizontalAlignment = FILL;
    if ((style & HORIZONTAL_ALIGN_END) != 0)
      horizontalAlignment = END;
    grabExcessHorizontalSpace = (style & GRAB_HORIZONTAL) != 0;
    grabExcessVerticalSpace = (style & GRAB_VERTICAL) != 0;
  }

  public GridData(int width, int height) {
    this.widthHint = width;
    this.heightHint = height;
  }

  public GridData(int horizontalAlignment, int verticalAlignment, boolean grabExcessHorizontalSpace,
      boolean grabExcessVerticalSpace) {
    this(horizontalAlignment, verticalAlignment, grabExcessHorizontalSpace, grabExcessVerticalSpace, 1, 1);
  }

  public GridData(int horizontalAlignment, int verticalAlignment, boolean grabExcessHorizontalSpace,
      boolean grabExcessVerticalSpace, int horizontalSpan, int verticalSpan) {
    this.horizontalAlignment = normalize(horizontalAlignment);
    this.verticalAlignment = normalize(verticalAlignment);
    this.grabExcessHorizontalSpace = grabExcessHorizontalSpace;
    this.grabExcessVerticalSpace = grabExcessVerticalSpace;
    this.horizontalSpan = horizontalSpan;
    this.verticalSpan = verticalSpan;
  }

  /** SWT.LEFT/RIGHT/TOP/BOTTOM/CENTER/END are accepted as alignments too. */
  private static int normalize(int a) {
    if (a == SWT.LEFT || a == SWT.TOP)
      return BEGINNING;
    if (a == SWT.RIGHT || a == SWT.BOTTOM || a == SWT.END)
      return END;
    if (a == SWT.CENTER)
      return CENTER;
    return a;
  }

  private static String align(int a) {
    switch (normalize(a)) {
      case CENTER:
        return "center";
      case END:
        return "end";
      case FILL:
        return "fill";
      default:
        return "start";
    }
  }

  public JsonObject toJson() {
    JsonObject o = new JsonObject();
    o.addProperty("type", "grid");
    o.addProperty("ha", align(horizontalAlignment));
    o.addProperty("va", align(verticalAlignment));
    o.addProperty("gh", grabExcessHorizontalSpace);
    o.addProperty("gv", grabExcessVerticalSpace);
    o.addProperty("hspan", horizontalSpan);
    o.addProperty("vspan", verticalSpan);
    if (widthHint != SWT.DEFAULT)
      o.addProperty("w", widthHint);
    if (heightHint != SWT.DEFAULT)
      o.addProperty("h", heightHint);
    if (horizontalIndent != 0)
      o.addProperty("hi", horizontalIndent);
    if (verticalIndent != 0)
      o.addProperty("vi", verticalIndent);
    if (minimumWidth != 0)
      o.addProperty("minw", minimumWidth);
    if (minimumHeight != 0)
      o.addProperty("minh", minimumHeight);
    if (exclude)
      o.addProperty("exclude", true);
    return o;
  }
}
