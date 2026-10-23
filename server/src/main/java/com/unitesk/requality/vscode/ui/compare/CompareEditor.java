package com.unitesk.requality.vscode.ui.compare;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.core.ServerContext;
import com.unitesk.requality.vscode.ui.compare.contentmergeviewer.TextMergeViewer;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.ICompareInput;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.IDiffContainer;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.dialogs.ProgressMonitorDialog;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.FillLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Display;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;

/**
 * {@code org.eclipse.compare.internal.CompareEditor}: the editor that shows a compare editor input.
 * VSCODE: a single two-way text comparison is shown by the VS Code diff editor ({@code client/openDiff}: workspace
 * files by their path, other contents (local history states, revisions, the clipboard) as read-only documents of the
 * {@code requality-compare} scheme served by {@link CompareContents}); other inputs (trees of differences, three-way
 * compares, content merge viewers of their own such as the Requality node viewer, custom structure panes) are shown by
 * the panes of the input in an editor panel.
 */
public final class CompareEditor {
  /** {@code CompareEditor.CONFIRM_SAVE_PROPERTY}. */
  public static final String CONFIRM_SAVE_PROPERTY = "org.eclipse.compare.internal.CONFIRM_SAVE_PROPERTY";

  private CompareEditor() {
  }

  /** {@code CompareUIPlugin.openCompareEditor}: runs the input; shows its result unless it failed or has no differences. */
  static void open(CompareEditorInput input) {
    Display d = Display.getDefault();
    if (!d.isUIThread()) {
      d.syncExec(() -> open(input));
      return;
    }
    if (!compareResultOK(input, d.getActiveShell())) {
      CompareLinks.forget(input);
      return;
    }
    Object result = input.getCompareResult();
    if (result instanceof ICompareInput && !(result instanceof IDiffContainer && ((IDiffContainer) result).hasChildren())
        && !hasCustomPane(input) && ((ICompareInput) result).getAncestor() == null
        && isTextCompare((ICompareInput) result)) {
      JsonObject diff = diffParams(input.getTitle(), (ICompareInput) result, input.getCompareConfiguration());
      CompareLinks.diffOpened(input, diff);
      sendDiff(diff);
      return;
    }
    Shell shell = new Shell(SWT.NONE);
    shell.setLayout(new FillLayout());
    shell.setText(input.getTitle());
    input.addPropertyChangeListener(e -> {
      if (CompareEditorInput.PROP_TITLE.equals(e.getProperty()) && !shell.isDisposed())
        shell.setText(input.getTitle());
    });
    input.createContents(shell);
    shell.open();
    CompareLinks.shellOpened(input, shell);
    input.setFocus2();
  }

  /** {@code CompareUIPlugin.compareResultOK}: runs the input with progress and reports a failure or no differences. */
  static boolean compareResultOK(CompareEditorInput input, Shell shell) {
    try {
      new ProgressMonitorDialog(shell).run(true, true, input);
      String message = input.getMessage();
      if (message != null) {
        MessageDialog.openError(shell, "Compare Failed", message);
        return false;
      }
      if (input.getCompareResult() == null) {
        MessageDialog.openInformation(shell, "Compare", "There are no differences between the selected inputs.");
        return false;
      }
      return true;
    } catch (InterruptedException x) {
      // canceled by user
    } catch (InvocationTargetException x) {
      MessageDialog.openError(shell, "Compare Failed", x.getTargetException().getMessage());
    }
    return false;
  }

  private static boolean hasCustomPane(CompareEditorInput input) {
    for (Class<?> c = input.getClass(); c != null && c != CompareEditorInput.class; c = c.getSuperclass()) {
      try {
        c.getDeclaredMethod("createStructureInputPane", com.unitesk.requality.vscode.ui.swt.widgets.Composite.class);
        return true;
      } catch (NoSuchMethodException e) {
        // not in this class
      }
    }
    return false;
  }

  /** Whether the input compares texts that have no content merge viewer of their own (the text merge viewer). */
  public static boolean isTextCompare(ICompareInput input) {
    String type = CompareUI.commonType(input);
    if (ITypedElement.FOLDER_TYPE.equals(type) || type != null && CompareUI.viewerFor(type) != null)
      return false;
    String l = CompareUI.guessType(input.getLeft()), r = CompareUI.guessType(input.getRight());
    if (l == null && r == null)
      return false;
    return (l == null || ITypedElement.TEXT_TYPE.equals(l)) && (r == null || ITypedElement.TEXT_TYPE.equals(r));
  }

  /** Opens the left and right sides of the input in the VS Code diff editor ({@code client/openDiff}). */
  public static void openDiff(String title, ICompareInput input, CompareConfiguration cc) {
    sendDiff(diffParams(title, input, cc));
  }

  /** The {@code client/openDiff} parameters of the input: its title and its sides (a workspace file or contents). */
  private static JsonObject diffParams(String title, ICompareInput input, CompareConfiguration cc) {
    JsonObject p = new JsonObject();
    p.addProperty("title", title);
    p.add("left", side(input.getLeft(), input.getRight(), cc == null ? null : cc.getLeftLabel(input)));
    p.add("right", side(input.getRight(), input.getLeft(), cc == null ? null : cc.getRightLabel(input)));
    return p;
  }

  /** Sends a {@code client/openDiff} request: VS Code shows the diff, or reveals the diff editor that shows the sides. */
  public static void sendDiff(JsonObject diff) {
    if (ServerContext.server() != null)
      ServerContext.client().notify("client/openDiff", diff);
  }

  /** A side of a diff: a workspace file by its path, else its contents (empty when the element is missing). */
  static JsonObject side(ITypedElement e, ITypedElement other, String label) {
    JsonObject o = new JsonObject();
    String name = e != null ? e.getName() : other != null ? other.getName() : "";
    o.addProperty("name", name);
    o.addProperty("label", label != null ? label : name);
    IFile file = e instanceof IResourceProvider && ((IResourceProvider) e).getResource() instanceof IFile
        ? (IFile) ((IResourceProvider) e).getResource() : null;
    boolean buffered = e instanceof BufferedContent && ((BufferedContent) e).fContent != null;
    if (file != null && e instanceof ResourceNode && !buffered && file.getLocation() != null
        && new File(file.getLocation().toOSString()).isFile()) {
      o.addProperty("path", file.getLocation().toOSString());
      return o;
    }
    String text = e == null ? "" : TextMergeViewer.textOf(e);
    o.addProperty("id", CompareContents.register(name, text));
    return o;
  }

  /** Opens the contents of an element read-only in an editor ({@code client/openContent}). */
  public static void openContent(String title, ITypedElement element) {
    openContent(title, element, null);
  }

  /**
   * Opens the contents of an element read-only in an editor of VS Code ({@code client/openContent}; editor: "default"
   * or null for the text editor, or the view type of an editor of VS Code).
   */
  public static void openContent(String title, ITypedElement element, String editor) {
    JsonObject p = new JsonObject();
    p.addProperty("title", title);
    p.addProperty("name", element.getName());
    p.addProperty("id", CompareContents.register(element.getName(), TextMergeViewer.textOf(element)));
    if (editor != null && !"default".equals(editor))
      p.addProperty("editor", editor);
    if (ServerContext.server() != null)
      ServerContext.client().notify("client/openContent", p);
  }

  /** A compare element of a text (for tests and for contents that are not files). */
  public static ITypedElement textElement(String name, String text) {
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    return new BufferedContentElement(name, bytes);
  }

  private static final class BufferedContentElement implements ITypedElement, IEncodedStreamContentAccessor {
    private final String name;
    private final byte[] bytes;

    BufferedContentElement(String name, byte[] bytes) {
      this.name = name;
      this.bytes = bytes;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public com.unitesk.requality.vscode.ui.swt.graphics.Image getImage() {
      return null;
    }

    @Override
    public String getType() {
      String ext = CompareUI.extension(name);
      return ext.isEmpty() ? ITypedElement.UNKNOWN_TYPE : ext;
    }

    @Override
    public InputStream getContents() {
      return new ByteArrayInputStream(bytes);
    }

    @Override
    public String getCharset() {
      return "UTF-8";
    }
  }
}
