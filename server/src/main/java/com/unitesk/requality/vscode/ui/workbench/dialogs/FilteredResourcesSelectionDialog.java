package com.unitesk.requality.vscode.ui.workbench.dialogs;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.unitesk.requality.vscode.ui.jface.dialogs.SelectionDialog;
import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IFolder;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.resources.IWorkspaceRoot;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.layout.GridData;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;
import com.unitesk.requality.vscode.ui.swt.widgets.Control;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.swt.widgets.Table;
import com.unitesk.requality.vscode.ui.swt.widgets.TableItem;
import com.unitesk.requality.vscode.ui.swt.widgets.Text;

/**
 * {@code org.eclipse.ui.dialogs.FilteredResourcesSelectionDialog}: a pattern and the resources of the workspace that
 * match it, with their container. A pattern matches the name of a resource from its start (case-insensitive), by its
 * capital letters (camel case: {@code ATF} finds {@code AbcTestFile}), or by wildcards ({@code *} and {@code ?}).
 * The selected resource is the result ({@link #getResult()}).
 */
public class FilteredResourcesSelectionDialog extends SelectionDialog {
  /** The most matches listed. */
  static final int MAX_MATCHES = 300;

  private final IContainer container;
  private final int typesMask;
  private final boolean multi;
  private List<IResource> all;
  private final List<IResource> matches = new ArrayList<>();
  private Text patternText;
  private Table table;

  public FilteredResourcesSelectionDialog(Shell parentShell, boolean multi, IContainer container, int typesMask) {
    super(parentShell);
    this.multi = multi;
    this.container = container;
    this.typesMask = typesMask;
  }

  @Override
  protected Control createDialogArea(Composite parent) {
    Composite composite = (Composite) super.createDialogArea(parent);
    createMessageArea(composite);
    patternText = new Text(composite, SWT.SINGLE | SWT.BORDER);
    patternText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
    patternText.addModifyListener(e -> refresh());
    table = new Table(composite, (multi ? SWT.MULTI : SWT.SINGLE) | SWT.BORDER | SWT.V_SCROLL);
    table.setLayoutData(new GridData(GridData.FILL_BOTH));
    all = collect();
    refresh();
    return composite;
  }

  /** Lists the resources matching the pattern of the text. */
  void refresh() {
    if (table == null || table.isDisposed())
      return;
    String pattern = patternText == null ? "" : patternText.getText().trim();
    matches.clear();
    table.removeAll();
    for (IResource r : all) {
      if (!matches(pattern, r.getName()))
        continue;
      matches.add(r);
      TableItem item = new TableItem(table, SWT.NONE);
      item.setText(label(r));
      if (matches.size() >= MAX_MATCHES)
        break;
    }
    if (!matches.isEmpty())
      table.setSelection(0);
  }

  @Override
  protected void okPressed() {
    int[] selected = table == null ? new int[0] : table.getSelectionIndices();
    List<IResource> chosen = new ArrayList<>();
    for (int i : selected)
      if (i >= 0 && i < matches.size())
        chosen.add(matches.get(i));
    if (chosen.isEmpty() && !matches.isEmpty())
      chosen.add(matches.get(0));
    if (chosen.isEmpty())
      return;
    setSelectionResult(chosen.toArray());
    super.okPressed();
  }

  /** The name and the container path of a resource ("name - project/folder"). */
  static String label(IResource r) {
    String full = r.getFullPath().toString();
    int slash = full.lastIndexOf('/');
    String parent = slash <= 0 ? "" : full.substring(0, slash);
    return parent.isEmpty() ? r.getName() : r.getName() + " - " + parent;
  }

  /** The resources of the container (its members, recursively) of the types. */
  private List<IResource> collect() {
    List<IResource> r = new ArrayList<>();
    if (container instanceof IWorkspaceRoot) {
      for (IProject p : ((IWorkspaceRoot) container).getProjects()) {
        if ((typesMask & IResource.PROJECT) != 0)
          r.add(p);
        collect(p, r);
      }
    } else {
      collect(container, r);
    }
    return r;
  }

  private void collect(IContainer c, List<IResource> into) {
    if (into.size() > 20000)
      return;
    try {
      if (!c.isAccessible())
        return;
      for (IResource m : c.members()) {
        if (m.getName().startsWith("."))
          continue;
        if (m instanceof IFile && (typesMask & IResource.FILE) != 0)
          into.add(m);
        else if (m instanceof IFolder && (typesMask & IResource.FOLDER) != 0)
          into.add(m);
        if (m instanceof IContainer)
          collect((IContainer) m, into);
      }
    } catch (CoreException e) {
      // the members of a container that cannot be read are not listed
    }
  }

  /** A pattern (wildcards, prefix, camel case) matches the name. */
  static boolean matches(String pattern, String name) {
    if (pattern == null || pattern.isEmpty())
      return true;
    if (pattern.indexOf('*') >= 0 || pattern.indexOf('?') >= 0)
      return glob(pattern).matcher(name).matches();
    if (name.regionMatches(true, 0, pattern, 0, pattern.length()))
      return true;
    if (!pattern.equals(pattern.toUpperCase()) || pattern.equals(pattern.toLowerCase()))
      return false;
    StringBuilder caps = new StringBuilder();
    for (char c : name.toCharArray())
      if (Character.isUpperCase(c))
        caps.append(c);
    return caps.toString().startsWith(pattern);
  }

  private static Pattern glob(String glob) {
    StringBuilder sb = new StringBuilder();
    for (char c : glob.toCharArray()) {
      if (c == '*')
        sb.append(".*");
      else if (c == '?')
        sb.append('.');
      else
        sb.append(Pattern.quote(String.valueOf(c)));
    }
    return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
  }
}
