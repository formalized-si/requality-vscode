package com.unitesk.requality.vscode.ui.team.actions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.Collator;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.google.gson.JsonObject;
import com.unitesk.requality.vscode.core.ServerContext;
import com.unitesk.requality.vscode.ui.compare.CompareEditor;
import com.unitesk.requality.vscode.ui.ide.IDE;
import com.unitesk.requality.vscode.ui.jface.action.ContributionItem;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.jface.window.Window;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IStorage;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.NullProgressMonitor;
import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.Menu;
import com.unitesk.requality.vscode.ui.swt.widgets.MenuItem;
import com.unitesk.requality.vscode.ui.team.FileRevisionTypedElement;
import com.unitesk.requality.vscode.ui.team.core.history.IFileRevision;
import com.unitesk.requality.vscode.ui.team.history.AbstractHistoryCategory;
import com.unitesk.requality.vscode.ui.team.history.LocalHistoryPage;
import com.unitesk.requality.vscode.ui.team.ui.history.HistoryPage;
import com.unitesk.requality.vscode.ui.workbench.EditorRegistry;
import com.unitesk.requality.vscode.ui.workbench.IEditorRegistry;
import com.unitesk.requality.vscode.ui.workbench.IEditorRegistry.IEditorDescriptor;
import com.unitesk.requality.vscode.ui.workbench.PlatformUI;
import com.unitesk.requality.vscode.ui.workbench.dialogs.EditorSelectionDialog;

/**
 * {@code org.eclipse.team.internal.ui.actions.OpenWithMenu}: populates the "Open With" submenu of a history page with
 * one radio item per editor of the selected file revision (the preferred editor checked), the text editor,
 * "Default Editor" and "Other..." (the {@link EditorSelectionDialog}).
 * VSCODE: the current revision is the workspace file and opens as Open With of a file ({@link IDE#openEditor}); an
 * older revision opens read-only ({@code FileRevisionEditorInput}): in the text editor or an editor of VS Code
 * ({@link CompareEditor#openContent}), or in the application of the operating system on a copy of its contents.
 */
public class OpenWithMenu extends ContributionItem {
  private IStructuredSelection selection;

  private final HistoryPage page;

  private final IEditorRegistry registry = PlatformUI.getWorkbench().getEditorRegistry();

  /** The id of this action. */
  public static final String ID = "org.eclipse.ui.OpenWithMenu";

  /* Compares the labels from two IEditorDescriptor objects */
  private static final Comparator<IEditorDescriptor> comparer = new Comparator<>() {
    private final Collator collator = Collator.getInstance();

    @Override
    public int compare(IEditorDescriptor arg0, IEditorDescriptor arg1) {
      return collator.compare(arg0.getLabel(), arg1.getLabel());
    }
  };

  /** Constructs a new instance of {@code OpenWithMenu} for the page where the editor is opened. */
  public OpenWithMenu(HistoryPage page) {
    super(ID);
    this.page = page;
  }

  /** Creates the menu item for the editor descriptor. */
  private MenuItem createMenuItem(Menu menu, final IEditorDescriptor descriptor,
      final IEditorDescriptor preferredEditor) {
    final MenuItem menuItem = new MenuItem(menu, SWT.RADIO);
    boolean isPreferred = preferredEditor != null && descriptor.getId().equals(preferredEditor.getId());
    menuItem.setSelection(isPreferred);
    menuItem.setText(descriptor.getLabel());
    menuItem.addListener(SWT.Selection, event -> {
      if (event.type == SWT.Selection && menuItem.getSelection())
        openEditor(descriptor, false);
    });
    return menuItem;
  }

  /** Creates the Other... menu item. */
  private void createOtherMenuItem(final Menu menu) {
    final IFileRevision fileResource = getFileRevision();
    if (fileResource == null)
      return;
    new MenuItem(menu, SWT.SEPARATOR);
    final MenuItem menuItem = new MenuItem(menu, SWT.PUSH);
    menuItem.setText("Other...");
    menuItem.addListener(SWT.Selection, event -> {
      if (event.type == SWT.Selection) {
        EditorSelectionDialog dialog = new EditorSelectionDialog(menu.getShell());
        dialog.setMessage(MessageFormat.format("Choose the editor for opening {0}:", fileResource.getName()));
        if (dialog.open() == Window.OK) {
          IEditorDescriptor editor = dialog.getSelectedEditor();
          if (editor != null)
            openEditor(editor, editor.isOpenExternal());
        }
      }
    });
  }

  @Override
  public void fill(Menu menu, int index) {
    final IFileRevision fileRevision = getFileRevision();
    if (fileRevision == null)
      return;

    IEditorDescriptor defaultTextEditor = registry.findEditor(IEditorRegistry.DEFAULT_TEXT_EDITOR_ID);
    IEditorDescriptor preferredEditor = getDefaultEditor(fileRevision);

    IEditorDescriptor[] editors = registry.getEditors(fileRevision.getName());
    Collections.sort(Arrays.asList(editors), comparer);
    boolean defaultFound = false;

    // Check that we don't add it twice. This is possible if the same editor goes to two mappings.
    List<String> alreadyMapped = new ArrayList<>();

    for (IEditorDescriptor editor : editors) {
      if (!alreadyMapped.contains(editor.getId())) {
        createMenuItem(menu, editor, preferredEditor);
        if (defaultTextEditor != null && editor.getId().equals(defaultTextEditor.getId()))
          defaultFound = true;
        alreadyMapped.add(editor.getId());
      }
    }

    // Only add a separator if there is something to separate
    if (editors.length > 0)
      new MenuItem(menu, SWT.SEPARATOR);

    // Add default editor. Check it if it is saved as the preference.
    if (!defaultFound && defaultTextEditor != null)
      createMenuItem(menu, defaultTextEditor, preferredEditor);

    createDefaultMenuItem(menu, fileRevision, preferredEditor == null);

    // add Other... menu item
    createOtherMenuItem(menu);
  }

  public void createDefaultMenuItem(Menu menu, final IFileRevision revision, boolean markAsSelected) {
    final MenuItem menuItem = new MenuItem(menu, SWT.RADIO);
    menuItem.setSelection(markAsSelected);
    menuItem.setText("&Default Editor");
    menuItem.addListener(SWT.Selection, event -> {
      if (event.type == SWT.Selection && menuItem.getSelection())
        openEditor(getDefaultEditor(revision), false);
    });
  }

  /**
   * {@code Utils.getDefaultEditor(IFileRevision)}: the editor associated with the name of the revision, null when no
   * editor is associated with it.
   */
  private IEditorDescriptor getDefaultEditor(IFileRevision revision) {
    return registry.getEditors(revision.getName()).length == 0 ? null : registry.getDefaultEditor(revision.getName());
  }

  /** Opens the given editor on the selected file revision (null: the system editor). */
  protected void openEditor(IEditorDescriptor editorDescriptor, boolean openUsingDescriptor) {
    IFileRevision fileRevision = getFileRevision();
    if (fileRevision == null)
      return;
    try {
      IStorage storage = fileRevision.getStorage(new NullProgressMonitor());
      String editorId = editorDescriptor == null ? IEditorRegistry.SYSTEM_EXTERNAL_EDITOR_ID : editorDescriptor.getId();
      if (storage instanceof IFile file) {
        IDE.openEditor(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(), file, editorId, true);
        return;
      }
      if (EditorRegistry.isExternalProgram(editorId)) {
        // an external program (Open With > Other... > Browse...) runs on a copy of the revision
        IDE.openPathWithProgram(revisionCopy(fileRevision, storage).getAbsolutePath(), editorId);
        return;
      }
      String editor = EditorRegistry.opensInVSCode(editorId) ? EditorRegistry.vscodeEditorName(editorId) : "default";
      if ("system".equals(editor)) {
        File copy = revisionCopy(fileRevision, storage);
        JsonObject p = new JsonObject();
        p.addProperty("path", copy.getAbsolutePath());
        p.addProperty("editor", "system");
        if (ServerContext.server() != null)
          ServerContext.client().notify("client/openFile", p);
        return;
      }
      CompareEditor.openContent(fileRevision.getName() + " " + LocalHistoryPage.revisionLabel(fileRevision),
          new FileRevisionTypedElement(fileRevision, null), editor);
    } catch (CoreException | IOException e) {
      MessageDialog.openError(page.getHistoryPageSite().getShell(), "A problem occurred while opening the editor",
          e.getMessage());
    }
  }

  /** The contents of the revision in a temporary file of its name (the input of an external editor). */
  private static File revisionCopy(IFileRevision fileRevision, IStorage storage) throws CoreException, IOException {
    File dir = Files.createTempDirectory("requality-revision").toFile();
    dir.deleteOnExit();
    File copy = new File(dir, fileRevision.getName());
    try (InputStream in = storage.getContents()) {
      Files.copy(in, copy.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
    copy.setReadOnly();
    copy.deleteOnExit();
    return copy;
  }

  private IFileRevision getFileRevision() {
    IFileRevision revision = null;
    if (selection == null)
      return null;
    for (Object tempRevision : selection.toArray()) {
      // If not a revision, don't try opening
      if (tempRevision instanceof AbstractHistoryCategory)
        continue;
      revision = (IFileRevision) tempRevision;
    }
    return revision;
  }

  /** Returns {@code true}, as the menu is computed each time it is shown. */
  @Override
  public boolean isDynamic() {
    return true;
  }

  /** Records the selection of the history page. */
  public void selectionChanged(IStructuredSelection selection) {
    this.selection = selection;
  }
}
