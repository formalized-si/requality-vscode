package com.unitesk.requality.vscode.ui.workbench.actions;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import com.unitesk.requality.vscode.ui.ide.IDE;
import com.unitesk.requality.vscode.ui.jface.action.Action;
import com.unitesk.requality.vscode.ui.jface.action.ActionContributionItem;
import com.unitesk.requality.vscode.ui.jface.action.IAction;
import com.unitesk.requality.vscode.ui.jface.action.IContributionItem;
import com.unitesk.requality.vscode.ui.jface.action.Separator;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.window.Window;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.workbench.CompoundContributionItem;
import com.unitesk.requality.vscode.ui.workbench.IEditorRegistry;
import com.unitesk.requality.vscode.ui.workbench.IEditorRegistry.IEditorDescriptor;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;
import com.unitesk.requality.vscode.ui.workbench.PartInitException;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.dialogs.EditorSelectionDialog;

/**
 * {@code org.eclipse.ui.actions.OpenWithMenu}: the editors of a file as radio items ("Open With" submenu), the
 * "Default Editor", the system editor and "Other..." (the {@link EditorSelectionDialog}). Choosing an editor remembers
 * it for the file ({@link IDE#setDefaultEditor}).
 */
public class OpenWithMenu extends CompoundContributionItem {
  public static final String ID = "org.eclipse.ui.OpenWithMenu";

  private final IWorkbenchPage page;
  private final IFile file;

  public OpenWithMenu(IWorkbenchPage page, IFile file) {
    super(ID);
    this.page = page;
    this.file = file;
  }

  @Override
  protected IContributionItem[] getContributionItems() {
    List<IContributionItem> items = new ArrayList<>();
    if (file == null)
      return new IContributionItem[0];
    IEditorRegistry registry = PlatformUI.getWorkbench().getEditorRegistry();
    IEditorDescriptor defaultEditor = registry.findEditor(IEditorRegistry.DEFAULT_TEXT_EDITOR_ID);
    IEditorDescriptor preferredEditor = IDE.getDefaultEditor(file);
    IEditorDescriptor[] editors = registry.getEditors(file.getName());
    Collator collator = Collator.getInstance();
    Arrays.sort(editors, Comparator.comparing(IEditorDescriptor::getLabel, collator));
    boolean defaultFound = false;
    List<String> mapped = new ArrayList<>();
    for (IEditorDescriptor editor : editors) {
      if (mapped.contains(editor.getId()))
        continue;
      items.add(menuItem(editor, preferredEditor));
      if (defaultEditor != null && editor.getId().equals(defaultEditor.getId()))
        defaultFound = true;
      mapped.add(editor.getId());
    }
    if (editors.length > 0)
      items.add(new Separator());
    if (!defaultFound && defaultEditor != null)
      items.add(menuItem(defaultEditor, preferredEditor));
    items.add(menuItem(registry.findEditor(IEditorRegistry.SYSTEM_EXTERNAL_EDITOR_ID), preferredEditor));
    IEditorDescriptor inPlace = registry.findEditor(IEditorRegistry.SYSTEM_INPLACE_EDITOR_ID);
    if (inPlace != null)
      items.add(menuItem(inPlace, preferredEditor));
    items.add(defaultMenuItem(preferredEditor == null));
    items.add(new Separator());
    items.add(new ActionContributionItem(new Action("Other...") {
      @Override
      public void run() {
        EditorSelectionDialog dialog = new EditorSelectionDialog(page.getWorkbenchWindow().getShell());
        dialog.setFileName(file.getName());
        dialog.setMessage("Choose the editor for opening " + file.getName() + ":");
        if (dialog.open() == Window.OK) {
          IEditorDescriptor editor = dialog.getSelectedEditor();
          if (editor != null)
            openEditor(editor.getId());
        }
      }
    }));
    return items.toArray(new IContributionItem[0]);
  }

  private IContributionItem menuItem(IEditorDescriptor descriptor, IEditorDescriptor preferred) {
    String id = descriptor.getId();
    Action a = new Action(descriptor.getLabel(), IAction.AS_RADIO_BUTTON) {
      @Override
      public void run() {
        openEditor(id);
      }
    };
    a.setChecked(preferred != null && id.equals(preferred.getId()));
    return new ActionContributionItem(a);
  }

  private IContributionItem defaultMenuItem(boolean markAsSelected) {
    Action a = new Action("&Default Editor", IAction.AS_RADIO_BUTTON) {
      @Override
      public void run() {
        IDE.setDefaultEditor(file, null);
        openEditor(IDE.editorIdOf(file));
      }
    };
    a.setChecked(markAsSelected);
    return new ActionContributionItem(a);
  }

  /** Opens the file in the editor and remembers the choice for the file. */
  void openEditor(String editorId) {
    try {
      IDE.setDefaultEditor(file, editorId);
      IDE.openEditor(page, file, editorId, true);
    } catch (PartInitException e) {
      MessageDialog.openError(page.getWorkbenchWindow().getShell(), "Problems Opening Editor", e.getMessage());
    }
  }
}
