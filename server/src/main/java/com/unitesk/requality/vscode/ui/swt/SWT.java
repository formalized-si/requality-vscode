package com.unitesk.requality.vscode.ui.swt;

/**
 * Constants of {@code org.eclipse.swt.SWT} used by the ported Requality UI, with the original values.
 */
public final class SWT {
  private SWT() {
  }

  // ---- styles
  public static final int NONE = 0;
  public static final int NULL = 0;
  public static final int DEFAULT = -1;
  public static final int BAR = 1 << 1;
  public static final int DROP_DOWN = 1 << 2;
  public static final int POP_UP = 1 << 3;
  public static final int SEPARATOR = 1 << 1;
  public static final int TOGGLE = 1 << 1;
  public static final int ARROW = 1 << 2;
  public static final int PUSH = 1 << 3;
  public static final int RADIO = 1 << 4;
  public static final int CHECK = 1 << 5;
  public static final int CASCADE = 1 << 6;
  public static final int MULTI = 1 << 1;
  public static final int SINGLE = 1 << 2;
  public static final int READ_ONLY = 1 << 3;
  public static final int WRAP = 1 << 6;
  public static final int SEARCH = 1 << 7;
  public static final int ICON_SEARCH = 1 << 9;
  public static final int ICON_CANCEL = 1 << 8;
  public static final int SIMPLE = 1 << 6;
  public static final int PASSWORD = 1 << 22;
  public static final int SHADOW_IN = 1 << 2;
  public static final int SHADOW_OUT = 1 << 3;
  public static final int SHADOW_ETCHED_IN = 1 << 4;
  public static final int SHADOW_ETCHED_OUT = 1 << 6;
  public static final int SHADOW_NONE = 1 << 5;
  public static final int INDETERMINATE = 1 << 1;
  public static final int TOOL = 1 << 2;
  public static final int NO_TRIM = 1 << 3;
  public static final int RESIZE = 1 << 4;
  public static final int TITLE = 1 << 5;
  public static final int CLOSE = 1 << 6;
  public static final int MENU = CLOSE;
  public static final int MIN = 1 << 7;
  public static final int MAX = 1 << 10;
  public static final int H_SCROLL = 1 << 8;
  public static final int V_SCROLL = 1 << 9;
  public static final int NO_SCROLL = 1 << 4;
  public static final int BORDER = 1 << 11;
  public static final int CLIP_CHILDREN = 1 << 12;
  public static final int CLIP_SIBLINGS = 1 << 13;
  public static final int ON_TOP = 1 << 14;
  public static final int HIDE_SELECTION = 1 << 15;
  public static final int FULL_SELECTION = 1 << 16;
  public static final int SMOOTH = 1 << 16;
  public static final int NO_BACKGROUND = 1 << 18;
  public static final int NO_FOCUS = 1 << 19;
  public static final int NO_RADIO_GROUP = 1 << 22;
  public static final int FLAT = 1 << 23;
  public static final int CENTER = 1 << 24;
  public static final int EMBEDDED = 1 << 24;
  public static final int VIRTUAL = 1 << 28;
  public static final int LEFT = 1 << 14;
  public static final int LEAD = LEFT;
  public static final int RIGHT = 1 << 17;
  public static final int TRAIL = RIGHT;
  public static final int UP = 1 << 7;
  public static final int TOP = UP;
  public static final int DOWN = 1 << 10;
  public static final int BOTTOM = DOWN;
  public static final int HORIZONTAL = 1 << 8;
  public static final int VERTICAL = 1 << 9;
  public static final int DATE = 1 << 5;
  public static final int TIME = 1 << 7;
  public static final int CALENDAR = 1 << 10;
  public static final int SHORT = 1 << 15;
  public static final int MEDIUM = 1 << 16;
  public static final int LONG = 1 << 28;
  public static final int DIALOG_TRIM = TITLE | CLOSE | BORDER;
  public static final int SHELL_TRIM = CLOSE | TITLE | MIN | MAX | RESIZE;
  public static final int MODELESS = 0;
  public static final int PRIMARY_MODAL = 1 << 15;
  public static final int APPLICATION_MODAL = 1 << 16;
  public static final int SYSTEM_MODAL = 1 << 17;
  public static final int OPEN = 1 << 12;
  public static final int SAVE = 1 << 13;
  public static final int MOZILLA = 1 << 15;
  public static final int WEBKIT = 1 << 16;
  public static final int EDGE = 1 << 18;

  // ---- layout alignment
  public static final int BEGINNING = 1;
  public static final int FILL = 4;
  public static final int END = 16777224;

  // ---- background / fonts / colors / cursors
  public static final int INHERIT_NONE = 0;
  public static final int INHERIT_DEFAULT = 1;
  public static final int INHERIT_FORCE = 2;
  public static final int NORMAL = 0;
  public static final int BOLD = 1 << 0;
  public static final int ITALIC = 1 << 1;
  public static final int CURSOR_ARROW = 0;
  public static final int CURSOR_WAIT = 1;
  public static final int COLOR_WHITE = 1;
  public static final int COLOR_BLACK = 2;
  public static final int COLOR_RED = 3;
  public static final int COLOR_DARK_RED = 4;
  public static final int COLOR_GREEN = 5;
  public static final int COLOR_DARK_GREEN = 6;
  public static final int COLOR_YELLOW = 7;
  public static final int COLOR_DARK_YELLOW = 8;
  public static final int COLOR_BLUE = 9;
  public static final int COLOR_DARK_BLUE = 10;
  public static final int COLOR_MAGENTA = 11;
  public static final int COLOR_DARK_MAGENTA = 12;
  public static final int COLOR_CYAN = 13;
  public static final int COLOR_DARK_CYAN = 14;
  public static final int COLOR_GRAY = 15;
  public static final int COLOR_DARK_GRAY = 16;
  public static final int COLOR_WIDGET_BACKGROUND = 22;
  public static final int COLOR_LIST_BACKGROUND = 25;
  public static final int COLOR_LIST_FOREGROUND = 24;
  public static final int COLOR_TITLE_INACTIVE_BACKGROUND = 35;
  public static final int COLOR_INFO_BACKGROUND = 29;

  // ---- keys and modifiers
  public static final int ALT = 1 << 16;
  public static final int SHIFT = 1 << 17;
  public static final int CTRL = 1 << 18;
  public static final int CONTROL = CTRL;
  public static final int COMMAND = 1 << 22;
  public static final int MOD1 = CTRL;
  public static final int MOD2 = SHIFT;
  public static final int MOD3 = ALT;
  public static final int BUTTON1 = 1 << 19;
  public static final char CR = '\r';
  public static final char LF = '\n';
  public static final char TAB = '\t';
  public static final char BS = '\b';
  public static final char DEL = 0x7F;
  public static final char ESC = 0x1B;
  public static final int KEYCODE_BIT = 1 << 24;
  public static final int ARROW_UP = KEYCODE_BIT + 1;
  public static final int ARROW_DOWN = KEYCODE_BIT + 2;
  public static final int ARROW_LEFT = KEYCODE_BIT + 3;
  public static final int ARROW_RIGHT = KEYCODE_BIT + 4;
  public static final int PAGE_UP = KEYCODE_BIT + 5;
  public static final int PAGE_DOWN = KEYCODE_BIT + 6;
  public static final int HOME = KEYCODE_BIT + 7;
  public static final int END_KEY = KEYCODE_BIT + 8;
  public static final int INSERT = KEYCODE_BIT + 9;
  public static final int F1 = KEYCODE_BIT + 10;
  public static final int F2 = KEYCODE_BIT + 11;
  public static final int F3 = KEYCODE_BIT + 12;
  public static final int F4 = KEYCODE_BIT + 13;
  public static final int F5 = KEYCODE_BIT + 14;
  public static final int KEYPAD_CR = KEYCODE_BIT + 80;

  // ---- events
  public static final int ERROR_UNSPECIFIED = 1;
  public static final int ERROR_NO_HANDLES = 2;
  public static final int ERROR_NO_MORE_CALLBACKS = 3;
  public static final int ERROR_NULL_ARGUMENT = 4;
  public static final int ERROR_INVALID_ARGUMENT = 5;
  public static final int ERROR_INVALID_RANGE = 6;
  public static final int ERROR_CANNOT_BE_ZERO = 7;
  public static final int ERROR_WIDGET_DISPOSED = 24;
  public static final int ERROR_THREAD_INVALID_ACCESS = 22;
  public static final int None = 0;
  public static final int KeyDown = 1;
  public static final int KeyUp = 2;
  public static final int MouseDown = 3;
  public static final int MouseUp = 4;
  public static final int MouseMove = 5;
  public static final int MouseEnter = 6;
  public static final int MouseExit = 7;
  public static final int MouseDoubleClick = 8;
  public static final int Paint = 9;
  public static final int Move = 10;
  public static final int Resize = 11;
  public static final int Dispose = 12;
  public static final int Selection = 13;
  public static final int DefaultSelection = 14;
  public static final int FocusIn = 15;
  public static final int FocusOut = 16;
  public static final int Expand = 17;
  public static final int Collapse = 18;
  public static final int Close = 21;
  public static final int Show = 22;
  public static final int Hide = 23;
  public static final int Modify = 24;
  public static final int Verify = 25;
  public static final int Activate = 26;
  public static final int Deactivate = 27;
  public static final int Traverse = 31;
  public static final int MenuDetect = 35;
  public static final int SetData = 36;
  public static final int Iconify = 19;
  public static final int Deiconify = 20;
  public static final int Help = 28;
  public static final int DragDetect = 29;
  public static final int Arm = 30;
  public static final int MouseHover = 32;
  public static final int HardKeyDown = 33;
  public static final int HardKeyUp = 34;
  public static final int MouseWheel = 37;
  public static final int MouseVerticalWheel = 37;
  public static final int MouseHorizontalWheel = 38;
  public static final int Settings = 39;
  public static final int EraseItem = 40;
  public static final int MeasureItem = 41;
  public static final int PaintItem = 42;
  public static final int ImeComposition = 43;
  public static final int OrientationChange = 44;
  public static final int Skin = 45;
  public static final int OpenDocument = 46;
  public static final int Touch = 47;
  public static final int Gesture = 48;
  public static final int Segments = 49;

  // ---- detail
  public static final int CHECK_DETAIL = 1 << 5;
  public static final int TRAVERSE_RETURN = 1 << 2;
  public static final int TRAVERSE_ESCAPE = 1 << 1;
  public static final int TRAVERSE_TAB_NEXT = 1 << 4;
  public static final int TRAVERSE_TAB_PREVIOUS = 1 << 3;
  public static final int ICON_ERROR = 1;
  public static final int ICON_INFORMATION = 1 << 1;
  public static final int ICON_QUESTION = 1 << 2;
  public static final int ICON_WARNING = 1 << 3;
  public static final int ICON_WORKING = 1 << 4;
  public static final int OK = 1 << 5;
  public static final int YES = 1 << 6;
  public static final int NO = 1 << 7;
  public static final int CANCEL = 1 << 8;
  public static final int ABORT = 1 << 9;
  public static final int RETRY = 1 << 10;
  public static final int IGNORE = 1 << 11;

  public static boolean has(int style, int bit) {
    return (style & bit) != 0;
  }

  /** {@code SWT.error(int)}: argument errors are IllegalArgumentExceptions, the others SWTExceptions, as in SWT. */
  public static void error(int code) {
    switch (code) {
      case ERROR_NULL_ARGUMENT:
        throw new IllegalArgumentException("Argument cannot be null");
      case ERROR_INVALID_ARGUMENT:
        throw new IllegalArgumentException("Argument not valid");
      case ERROR_INVALID_RANGE:
        throw new IllegalArgumentException("Index out of bounds");
      case ERROR_CANNOT_BE_ZERO:
        throw new IllegalArgumentException("Argument cannot be zero");
      case ERROR_WIDGET_DISPOSED:
        throw new SWTException(code, "Widget is disposed");
      case ERROR_THREAD_INVALID_ACCESS:
        throw new SWTException(code, "Invalid thread access");
      default:
        throw new SWTException(code, "Unspecified error");
    }
  }
}
