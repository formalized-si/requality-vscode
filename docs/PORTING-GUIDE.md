# Porting guide: Requality Eclipse UI layer → VS Code server

The VS Code extension runs the original Requality code. The Requality core (`server/upstream/core`) is used
unchanged. The Eclipse UI layer of the plug-in (`server/upstream/eclipse-reference/java`, 320 classes) is ported
class by class into `server/src/main/java/com/unitesk/requality/vscode/port/...` and runs inside the Java server on
top of a compatibility layer that implements the Eclipse APIs it uses:

| Eclipse API | Compatibility layer (`com.unitesk.requality.vscode.ui...`) | Notes |
|---|---|---|
| `org.eclipse.swt.*` (widgets, layout, events, graphics, custom, browser, dnd) | `ui.swt.*` | Same class names, methods and SWT constant values. Widgets are rendered remotely in VS Code webviews. One UI thread (`Display`), modal dialogs run a nested event loop exactly as in SWT. |
| `org.eclipse.jface.*` (dialogs, viewers, wizard, window, preference, action, resource, fieldassist, text, operation) | `ui.jface.*` | |
| `org.eclipse.ui.views.properties.tabbed.*` | `ui.tabbed.*` | |
| `org.eclipse.core.runtime.*` (IStatus, Status, MultiStatus, IProgressMonitor, Job, IPath, Path, Platform, IConfigurationElement, IExtensionRegistry, IAdaptable...) | `ui.runtime.*` | `Platform.getExtensionRegistry()` reads the plug-in's own `plugin.xml`; `createExecutableExtension` instantiates the ported class. |
| `org.eclipse.core.resources.*` (IResource, IFile, IFolder, IContainer, IProject, IWorkspace, IWorkspaceRoot, ResourcesPlugin, IMarker...) | `ui.resources.*` | File-system backed. Workspace projects = Requality projects + VS Code workspace folders. `ProjectRef` implements `IProject`. Markers: `MarkerStore`. |
| `org.eclipse.core.commands.*`, `org.eclipse.ui.handlers.*`, `org.eclipse.ui.commands.*`, `org.eclipse.ui.menus.UIElement`, `org.eclipse.core.expressions` | `ui.commands.*` | `AbstractHandler`, `ExecutionEvent`, `HandlerUtil`, `IHandlerService`, `ICommandService`. Handlers are found from `plugin.xml` by `CommandService` (activeWhen/enabledWhen evaluated). |
| `org.eclipse.core.commands.operations.*` | `ui.commands.operations.*` | `OperationHistory` is the workbench undo history. |
| `org.eclipse.ui.*` workbench (PlatformUI, IWorkbench, IWorkbenchWindow, IWorkbenchPage, IWorkbenchPart, IViewPart, IEditorPart, ViewPart, EditorPart, ISharedImages, IEditorInput, PartInitException, ISelectionService, CompoundContributionItem, wizards interfaces...) | `ui.workbench.*` | `Workbench`: one window, one page. Server-hosted views/editors are rendered in VS Code webviews. |
| `org.eclipse.ui.model.*` | `ui.workbench.model.*` | `WorkbenchLabelProvider`, `WorkbenchContentProvider`, `IWorkbenchAdapter`. |

Run `python3 scripts/port-class.py <class>` (already done for you in an agent workspace): it copies the class,
rewrites the package (`com.unitesk.requality.eclipse.X` → `com.unitesk.requality.vscode.port.eclipse.X`,
`com.unitesk.requality.{marker,model,views,report,tests}` → `com.unitesk.requality.vscode.port.{...}`) and all
imports covered by the table above. What remains is fixing the compile errors by hand.

## Rules

1. **1:1.** Keep the class name, package (under `port`), fields, methods, logic, strings, labels, order of widgets,
   layouts and behaviour of the original. Do not simplify, do not "improve", do not drop features, do not leave
   empty bodies where the original had logic. The user must not be able to tell the difference except for the
   host (VS Code instead of Eclipse).
2. **Adaptations are minimal and marked.** Where an Eclipse API has no equivalent, change as little as possible and
   put a `// VSCODE: <why>` comment on the changed lines (one short sentence). Examples below.
3. **Fix the compatibility layer, not the ported code.** If a class/method of SWT/JFace/Eclipse that the code uses
   is missing in `ui.*`, add it there with the original Eclipse name, signature and semantics (look at how the
   neighbouring code is written; keep the Eclipse javadoc style `/** {@code org.eclipse...}. */`). Never add a
   Requality-specific method to `ui.*`.
4. **Facades.** `port/eclipse/Activator`, `RequalityPlugin`, `core/EclipseTracker`, `editors/EditorUtils`,
   `ui/cnf/RequalityCNF`, `api/EclipseAPI` are hand-written facades of upstream classes that are not ported as a
   whole. If ported code calls a method a facade lacks, add the method to the facade by porting that method's body
   from the upstream class (`server/upstream/eclipse-reference/java/...`), adapted as above.
5. **Never edit** `server/upstream/**` (vendored original code), the TypeScript sources, `package.json`, tests or
   classes owned by other agents. Only your own classes (listed in `OWN.txt`), the compatibility layer `ui/**` and
   the facades.
6. **Threading.** Exactly as in Eclipse: widget code on the UI thread (`Display.syncExec/asyncExec`), long work in
   `Job`/`WorkspaceJob` or the core `JobManager`. Do not remove `asyncExec` calls.
7. **No hidden failures.** No `@SuppressWarnings` to silence errors, no catch-and-ignore that the original did not
   have, no `throw new UnsupportedOperationException` in place of logic.
8. **Done means:** `./compile.sh` in your workspace prints `OK`, every class in `OWN.txt` is ported, every
   `// VSCODE:` change is justified, and your final report lists (a) the classes, (b) every addition to `ui/**` or
   facades, (c) every behaviour difference from Eclipse (should be none or nearly none).

## Replacements for Eclipse APIs without a direct equivalent

| Eclipse | VS Code server |
|---|---|
| `PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell()` | Works (returns the active dialog shell or null; dialogs accept a null parent). |
| `IDE.openEditor(page, input, id)` / `page.openEditor(input, id)` | `PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().openEditor(input, id)` (Workbench page: server-hosted editors or the extension's web editors). |
| `page.showView(id)` | Works (`Workbench`): server-hosted views or VS Code views (`client/showView`). |
| `CommonNavigator` / `CommonViewer` of the Requality Explorer | The explorer tree is a native VS Code tree (`ExplorerService`). `RequalityCNF.getInstance()` is the part. Selection: `SelectionManager` (already synchronized). Refresh/reveal/select: add methods to the `RequalityCNF` facade that call `ExplorerService` (e.g. `refresh(TreeNode)` → `ExplorerService.refresh(node)`); do not create SWT trees for it. |
| Perspectives (`IPerspectiveFactory`, `showPerspective`) | VS Code has none: the call becomes a no-op with `// VSCODE:`. |
| `IResourceChangeListener`, `IResourceDelta` | `ResourcesPlugin.getWorkspace().addResourceChangeListener` exists; deltas are delivered from the extension's file watcher. |
| Clipboard (`org.eclipse.swt.dnd.Clipboard`, `TextTransfer`) | `ui.swt.dnd` (system clipboard of VS Code). |
| `Browser` (`org.eclipse.swt.browser`) | `ui.swt.widgets.Browser`: iframe; `execute`, `evaluate`, `LocationListener` (changing/changed), `ProgressListener` (completed) work for Requality pages. |
| `org.eclipse.ui.forms.widgets.Hyperlink` | `ui.swt.widgets.Hyperlink`. |
| Toolbar items of a view (`ToolItem` changed by handlers) | View toolbars are VS Code `view/title` menu items; toggled state is a context key: `ServerContext.client().setContext("requality.<view>.<state>", value)` with `// VSCODE:`. |
| Problem markers (`IMarker`, `createMarker`, `findMarkers`, `deleteMarkers`) | Work (`ui.resources.MarkerStore`); problems are published as VS Code diagnostics by the server. |
| Messages (`MessageDialog.openError/openQuestion/...`) | Work (native VS Code dialogs). |
| `org.eclipse.ui.dialogs.*SelectionDialog`, `ElementTreeSelectionDialog`, `ListSelectionDialog` | `ui.jface.dialogs` (same names). |
| `AbstractUIPlugin.getDialogSettings()` | `Activator.getDefault().getDialogSettings()` (persisted). |
| Drag and drop (`DragSource`, `DropTarget`, `DropTargetListener`, transfers) | `ui.swt.dnd`: the control gets the `dragSource`/`dropTarget` role in the webview; a drop carries the source control (its `DragSource` gives the data on the server) or the URIs of files dragged from VS Code views (`FileTransfer`, `ResourceTransfer`, `LocalSelectionTransfer`) and the text (`TextTransfer`). |
| `IWorkspace.validateEdit` (read-only files) | Works: the team core's `FileModificationValidatorManager` and the Team UI's `DefaultUIFileModificationValidator` are contributed in platform-plugin.xml and ask to make the files writable. |
| Dirty editors (`ITextFileBuffer.isDirty`) | `client/dirtyDocuments`: the files of the VS Code documents with unsaved changes. |
| LTK refactoring (`org.eclipse.ltk`) | `ui.ltk` (Refactoring, processors, RefactoringWizard, RefactoringWizardDialog2 with Preview, status page) and the resource refactorings of `ui.ltk.resource` (Rename/Delete/Move Resources through `LTKLauncher`). |
| Team/Compare (`org.eclipse.compare`, `org.eclipse.team`) | `ui.compare`, `ui.team` (compare editor inputs, structure compare, Apply Patch, local history, Team Project Set, Synchronize view). |

## Workspace and build

Each agent works in its own copy created by `python3 scripts/agent-workspace.py create <name> <classes.txt>`:

```
<workspace>/OWN.txt                 your classes (paths relative to server/upstream/eclipse-reference/java)
<workspace>/server/src/main/java    sources: repository state + your pre-ported classes under vscode/port/...
<workspace>/server/upstream/eclipse-reference/java   the original Eclipse classes (read them!)
<workspace>/compile.sh              compiles your classes + the ui/** and facade files you changed
```

Work loop: read the original class, read the pre-ported class, run `./compile.sh`, fix errors (in your class
following the rules, or in `ui/**`/facades by adding the missing Eclipse API), repeat until `OK`. Then re-read your
class against the original once more, line by line, to check nothing was lost.

The repository build is `cd server && ./gradlew compileJava -q` (full) and `./gradlew test` (JUnit, uses
`ServerHarness` to run the server in-process). The extension: `npm run compile`.
