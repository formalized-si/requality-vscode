package com.unitesk.requality.vscode.ui.compare.contentmergeviewer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.compare.CompareConfiguration;
import com.unitesk.requality.vscode.ui.compare.ComparePreferencePage;
import com.unitesk.requality.vscode.ui.compare.CompareUI;
import com.unitesk.requality.vscode.ui.compare.IEditableContent;
import com.unitesk.requality.vscode.ui.compare.IEncodedStreamContentAccessor;
import com.unitesk.requality.vscode.ui.compare.IStreamContentAccessor;
import com.unitesk.requality.vscode.ui.compare.ITypedElement;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.custom.StyleRange;
import com.unitesk.requality.vscode.ui.swt.custom.StyledText;
import com.unitesk.requality.vscode.ui.swt.graphics.Color;
import com.unitesk.requality.vscode.ui.swt.graphics.RGB;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;

/**
 * {@code org.eclipse.compare.contentmergeviewer.TextMergeViewer}: the text compare viewer ("Text Compare"): the left
 * and right texts side by side (with the ancestor above in a three-way compare), the changed lines highlighted.
 * VSCODE: the panes are read-only text areas with the differences of the lines highlighted in the colors of the VS Code
 * diff editor; editing a compared file is done in the VS Code diff editor the compare editor opens.
 */
public class TextMergeViewer extends ContentMergeViewer {
  static final Color INSERTED = Color.theme("var(--vscode-diffEditor-insertedLineBackground, var(--vscode-diffEditor-insertedTextBackground))",
      new RGB(220, 255, 220));
  static final Color REMOVED = Color.theme("var(--vscode-diffEditor-removedLineBackground, var(--vscode-diffEditor-removedTextBackground))",
      new RGB(255, 220, 220));

  private Composite fAncestorPane;
  private Composite fLeftPane;
  private Composite fRightPane;
  private Label fAncestorLabel;
  private Label fLeftLabel;
  private Label fRightLabel;
  private StyledText fAncestor;
  private StyledText fLeft;
  private StyledText fRight;
  private String fLeftText = "";
  private String fRightText = "";

  public TextMergeViewer(Composite parent, CompareConfiguration configuration) {
    this(parent, SWT.NONE, configuration);
  }

  public TextMergeViewer(Composite parent, int style, CompareConfiguration configuration) {
    super(style, null, configuration);
    buildControl(parent);
    getControl().setData(CompareUI.COMPARE_VIEWER_TITLE, getTitle());
    setData(CompareUI.COMPARE_VIEWER_TITLE, getTitle());
  }

  @Override
  public String getTitle() {
    return "Text Compare";
  }

  @Override
  protected void createControls(Composite composite) {
    fAncestorPane = pane(composite);
    fAncestorLabel = new Label(fAncestorPane, SWT.NONE);
    fAncestor = text(fAncestorPane);
    fLeftPane = pane(composite);
    fLeftLabel = new Label(fLeftPane, SWT.NONE);
    fLeft = text(fLeftPane);
    fRightPane = pane(composite);
    fRightLabel = new Label(fRightPane, SWT.NONE);
    fRight = text(fRightPane);
  }

  private static Composite pane(Composite parent) {
    Composite c = new Composite(parent, SWT.NONE);
    GridLayout gl = new GridLayout(1, false);
    gl.marginWidth = gl.marginHeight = 0;
    c.setLayout(gl);
    return c;
  }

  private static StyledText text(Composite parent) {
    StyledText t = new StyledText(parent, SWT.MULTI | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.READ_ONLY);
    t.setEditable(false);
    t.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    return t;
  }

  @Override
  protected void handleResizeAncestor(int x, int y, int width, int height) {
    fAncestorPane.setBounds(x, y, width, height);
    fAncestorPane.setVisible(width > 0);
  }

  @Override
  protected void handleResizeLeftRight(int x, int y, int leftWidth, int centerWidth, int rightWidth, int height) {
    fLeftPane.setBounds(x, y, leftWidth, height);
    fRightPane.setBounds(x + leftWidth + centerWidth, y, rightWidth, height);
  }

  @Override
  protected void updateContent(Object ancestor, Object left, Object right) {
    CompareConfiguration cc = getCompareConfiguration();
    Object input = getInput();
    fAncestorLabel.setText(label(cc.getAncestorLabel(input), ancestor));
    fLeftLabel.setText(label(cc.getLeftLabel(input), left));
    fRightLabel.setText(label(cc.getRightLabel(input), right));
    String a = ancestor == null ? null : textOf(ancestor);
    fLeftText = left == null ? "" : textOf(left);
    fRightText = right == null ? "" : textOf(right);
    fAncestor.setText(a == null ? "" : a);
    fLeft.setText(fLeftText);
    fRight.setText(fRightText);
    fAncestor.setStyleRange(null);
    fLeft.setStyleRange(null);
    fRight.setStyleRange(null);
    boolean ignoreWhitespace = ignoreWhitespace(cc);
    if (a != null) {
      highlight(a, fLeftText, fAncestor, fLeft, ignoreWhitespace);
      highlight(a, fRightText, null, fRight, ignoreWhitespace);
    } else {
      highlight(fLeftText, fRightText, fLeft, fRight, ignoreWhitespace);
    }
  }

  /** The "Ignore white space" of the configuration; without one, the Compare preference. */
  static boolean ignoreWhitespace(CompareConfiguration cc) {
    Object v = cc == null ? null : cc.getProperty(CompareConfiguration.IGNORE_WHITESPACE);
    return v != null ? Boolean.TRUE.equals(v) : ComparePreferencePage.isIgnoreWhitespace();
  }

  private static String label(String label, Object element) {
    if (label != null)
      return label;
    return element instanceof ITypedElement ? ((ITypedElement) element).getName() : "";
  }

  /** Highlights the lines that differ: removed in the first text, inserted in the second. */
  private static void highlight(String a, String b, StyledText ta, StyledText tb, boolean ignoreWhitespace) {
    String[] la = lines(a), lb = lines(b);
    boolean[][] changed = LineDiff.changedLines(la, lb, ignoreWhitespace);
    if (ta != null)
      style(ta, la, changed[0], REMOVED);
    style(tb, lb, changed[1], INSERTED);
  }

  private static void style(StyledText t, String[] lines, boolean[] changed, Color color) {
    int offset = 0;
    for (int i = 0; i < lines.length; i++) {
      int len = lines[i].length();
      if (changed[i] && len > 0)
        t.setStyleRange(new StyleRange(offset, len, null, color));
      offset += len;
    }
  }

  /** The lines of a text, each with its line delimiter. */
  static String[] lines(String s) {
    List<String> r = new ArrayList<>();
    int start = 0;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\n' || c == '\r') {
        if (c == '\r' && i + 1 < s.length() && s.charAt(i + 1) == '\n')
          i++;
        r.add(s.substring(start, i + 1));
        start = i + 1;
      }
    }
    if (start < s.length())
      r.add(s.substring(start));
    return r.toArray(new String[0]);
  }

  /** The text of a compare element (decoded with its charset, UTF-8 by default). */
  public static String textOf(Object element) {
    if (!(element instanceof IStreamContentAccessor))
      return "";
    try (InputStream is = ((IStreamContentAccessor) element).getContents()) {
      if (is == null)
        return "";
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      is.transferTo(out);
      return new String(out.toByteArray(), charset(element));
    } catch (CoreException | IOException e) {
      return "";
    }
  }

  /** The charset of a compare element (UTF-8 by default). */
  public static Charset charset(Object element) {
    try {
      String cs = element instanceof IEncodedStreamContentAccessor
          ? ((IEncodedStreamContentAccessor) element).getCharset() : null;
      return cs == null ? StandardCharsets.UTF_8 : Charset.forName(cs);
    } catch (CoreException | RuntimeException e) {
      return StandardCharsets.UTF_8;
    }
  }

  @Override
  protected void copy(boolean leftToRight) {
    if (leftToRight) {
      fRightText = fLeftText;
      fRight.setText(fRightText);
      setRightDirty(true);
    } else {
      fLeftText = fRightText;
      fLeft.setText(fLeftText);
      setLeftDirty(true);
    }
  }

  @Override
  protected byte[] getContents(boolean left) {
    Object input = getInput();
    Object element = null;
    if (input instanceof com.unitesk.requality.vscode.ui.compare.structuremergeviewer.ICompareInput)
      element = left ? ((com.unitesk.requality.vscode.ui.compare.structuremergeviewer.ICompareInput) input).getLeft()
          : ((com.unitesk.requality.vscode.ui.compare.structuremergeviewer.ICompareInput) input).getRight();
    return (left ? fLeftText : fRightText).getBytes(charset(element));
  }

  /** Writes a changed side to its element ({@link IEditableContent}). */
  public void flush() {
    Object input = getInput();
    if (!(input instanceof com.unitesk.requality.vscode.ui.compare.structuremergeviewer.ICompareInput))
      return;
    com.unitesk.requality.vscode.ui.compare.structuremergeviewer.ICompareInput ci = (com.unitesk.requality.vscode.ui.compare.structuremergeviewer.ICompareInput) input;
    if (isLeftDirty() && ci.getLeft() instanceof IEditableContent) {
      ((IEditableContent) ci.getLeft()).setContent(getContents(true));
      setLeftDirty(false);
    }
    if (isRightDirty() && ci.getRight() instanceof IEditableContent) {
      ((IEditableContent) ci.getRight()).setContent(getContents(false));
      setRightDirty(false);
    }
  }
}
