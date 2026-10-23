package com.unitesk.requality.vscode.ui.swt.widgets;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.core.ClientApi.Severity;
import com.unitesk.requality.vscode.core.ServerContext;
import com.unitesk.requality.vscode.ui.swt.SWT;

/** {@code org.eclipse.swt.widgets.MessageBox}: a native modal VS Code message; returns the SWT button id. */
public class MessageBox extends Dialog {
  private String message = "";

  public MessageBox(Shell parent) {
    this(parent, SWT.OK | SWT.ICON_INFORMATION);
  }

  public MessageBox(Shell parent, int style) {
    super(parent, style);
  }

  public void setMessage(String message) {
    this.message = message == null ? "" : message;
  }

  public String getMessage() {
    return message;
  }

  public int open() {
    int s = getStyle();
    List<String> labels = new ArrayList<>();
    List<Integer> ids = new ArrayList<>();
    int[][] buttons = { { SWT.YES, 0 }, { SWT.NO, 1 }, { SWT.OK, 2 }, { SWT.RETRY, 3 }, { SWT.ABORT, 4 },
        { SWT.IGNORE, 5 }, { SWT.CANCEL, 6 } };
    String[] names = { "Yes", "No", "OK", "Retry", "Abort", "Ignore", "Cancel" };
    for (int[] b : buttons)
      if (SWT.has(s, b[0])) {
        labels.add(names[b[1]]);
        ids.add(b[0]);
      }
    if (labels.isEmpty()) {
      labels.add("OK");
      ids.add(SWT.OK);
    }
    Severity sev = SWT.has(s, SWT.ICON_ERROR) ? Severity.error : SWT.has(s, SWT.ICON_WARNING) ? Severity.warning : Severity.info;
    String r = ServerContext.client().showMessage(sev, getText(), message, null, true, true, labels.toArray(new String[0]));
    int i = r == null ? -1 : labels.indexOf(r);
    if (i < 0)
      return ids.contains(SWT.CANCEL) ? SWT.CANCEL : ids.contains(SWT.NO) ? SWT.NO : ids.get(ids.size() - 1);
    return ids.get(i);
  }
}
