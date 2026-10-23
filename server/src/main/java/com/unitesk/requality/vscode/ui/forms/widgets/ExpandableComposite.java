package com.unitesk.requality.vscode.ui.forms.widgets;

import java.util.ArrayList;
import java.util.List;

import com.unitesk.requality.vscode.ui.forms.events.ExpansionEvent;
import com.unitesk.requality.vscode.ui.forms.events.IExpansionListener;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.events.SelectionAdapter;
import com.unitesk.requality.vscode.ui.swt.events.SelectionEvent;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;

/**
 * {@code org.eclipse.ui.forms.widgets.ExpandableComposite}: a composite with a title that shows or hides its client
 * control. VSCODE: the twistie and the title are a flat button ("▸ title" / "▾ title").
 */
public class ExpandableComposite extends Composite {
  public static final int TWISTIE = 1 << 1;
  public static final int TREE_NODE = 1 << 2;
  public static final int FOCUS_TITLE = 1 << 3;
  public static final int CLIENT_INDENT = 1 << 4;
  public static final int COMPACT = 1 << 5;
  public static final int EXPANDED = 1 << 6;
  public static final int TITLE_BAR = 1 << 8;
  public static final int SHORT_TITLE_BAR = 1 << 9;
  public static final int NO_TITLE = 1 << 12;

  private final int expansionStyle;
  private final Button toggle;
  private final List<IExpansionListener> listeners = new ArrayList<>();
  private String text = "";
  private Control client;
  private boolean expanded;

  public ExpandableComposite(Composite parent, int style) {
    this(parent, SWT.NONE, style);
  }

  public ExpandableComposite(Composite parent, int style, int expansionStyle) {
    super(parent, style);
    this.expansionStyle = expansionStyle;
    GridLayout gl = new GridLayout(1, false);
    gl.marginWidth = 0;
    gl.marginHeight = 0;
    setLayout(gl);
    toggle = new Button(this, SWT.PUSH | SWT.FLAT);
    toggle.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
    toggle.addSelectionListener(new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent e) {
        boolean newState = !expanded;
        for (IExpansionListener l : new ArrayList<>(listeners))
          l.expansionStateChanging(new ExpansionEvent(ExpandableComposite.this, newState));
        setExpanded(newState);
        for (IExpansionListener l : new ArrayList<>(listeners))
          l.expansionStateChanged(new ExpansionEvent(ExpandableComposite.this, newState));
      }
    });
    expanded = (expansionStyle & EXPANDED) != 0;
    updateToggle();
  }

  public void setText(String title) {
    text = title == null ? "" : title;
    updateToggle();
  }

  public String getText() {
    return text;
  }

  /** The control shown when the composite is expanded. */
  public void setClient(Control client) {
    this.client = client;
    GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
    if ((expansionStyle & CLIENT_INDENT) != 0)
      gd.horizontalIndent = 16;
    client.setLayoutData(gd);
    updateClient();
  }

  public Control getClient() {
    return client;
  }

  public void setExpanded(boolean expanded) {
    this.expanded = expanded;
    updateToggle();
    updateClient();
  }

  public boolean isExpanded() {
    return expanded;
  }

  public void addExpansionListener(IExpansionListener listener) {
    listeners.add(listener);
  }

  public void removeExpansionListener(IExpansionListener listener) {
    listeners.remove(listener);
  }

  public int getExpansionStyle() {
    return expansionStyle;
  }

  private void updateToggle() {
    toggle.setText((expanded ? "▾ " : "▸ ") + text);
  }

  private void updateClient() {
    if (client != null && !client.isDisposed()) {
      client.setVisible(expanded);
      Object ld = client.getLayoutData();
      if (ld instanceof GridData)
        ((GridData) ld).exclude = !expanded;
      layout(true, true);
    }
  }
}
