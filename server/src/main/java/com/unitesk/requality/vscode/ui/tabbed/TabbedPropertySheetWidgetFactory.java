package com.unitesk.requality.vscode.ui.tabbed;

import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Button;
import com.unitesk.requality.vscode.ui.swt.widgets.CCombo;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Group;
import com.unitesk.requality.vscode.ui.swt.widgets.Label;
import com.unitesk.requality.vscode.ui.swt.widgets.List;
import com.unitesk.requality.vscode.ui.swt.widgets.Table;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;
import com.unitesk.requality.vscode.ui.swt.widgets.Tree;

/** {@code org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetWidgetFactory} (Eclipse Forms toolkit). */
public class TabbedPropertySheetWidgetFactory {
  public Composite createComposite(Composite parent) {
    return createComposite(parent, SWT.NONE);
  }

  public Composite createComposite(Composite parent, int style) {
    return new Composite(parent, style);
  }

  public Composite createFlatFormComposite(Composite parent) {
    Composite c = new Composite(parent, SWT.NONE);
    c.setLayout(new GridLayout());
    return c;
  }

  public Composite createPlainComposite(Composite parent, int style) {
    return new Composite(parent, style);
  }

  public Label createLabel(Composite parent, String text) {
    return createLabel(parent, text, SWT.NONE);
  }

  public Label createLabel(Composite parent, String text, int style) {
    Label l = new Label(parent, style);
    l.setText(text);
    return l;
  }

  public Label createCLabel(Composite parent, String text) {
    return createLabel(parent, text);
  }

  public Label createCLabel(Composite parent, String text, int style) {
    return createLabel(parent, text, style);
  }

  public Text createText(Composite parent, String value) {
    return createText(parent, value, SWT.SINGLE | SWT.BORDER);
  }

  public Text createText(Composite parent, String value, int style) {
    Text t = new Text(parent, style | SWT.BORDER);
    t.setText(value == null ? "" : value);
    return t;
  }

  public Button createButton(Composite parent, String text, int style) {
    Button b = new Button(parent, style);
    b.setText(text);
    return b;
  }

  public CCombo createCCombo(Composite parent) {
    return createCCombo(parent, SWT.BORDER);
  }

  public CCombo createCCombo(Composite parent, int style) {
    return new CCombo(parent, style);
  }

  public Group createGroup(Composite parent, String text) {
    Group g = new Group(parent, SWT.NONE);
    g.setText(text);
    return g;
  }

  public List createList(Composite parent, int style) {
    return new List(parent, style);
  }

  public Table createTable(Composite parent, int style) {
    return new Table(parent, style);
  }

  public Tree createTree(Composite parent, int style) {
    return new Tree(parent, style);
  }

  public com.unitesk.requality.vscode.ui.swt.widgets.CTabFolder createTabFolder(Composite parent, int style) {
    return new com.unitesk.requality.vscode.ui.swt.widgets.CTabFolder(parent, style);
  }

  public com.unitesk.requality.vscode.ui.swt.widgets.CTabItem createTabItem(
      com.unitesk.requality.vscode.ui.swt.widgets.CTabFolder folder, int style) {
    return new com.unitesk.requality.vscode.ui.swt.widgets.CTabItem(folder, style);
  }

  public void adapt(Control control) {
  }

  public void adapt(Composite composite) {
  }

  public void paintBordersFor(Composite parent) {
  }

  public void dispose() {
  }
}
