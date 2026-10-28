#!/usr/bin/env python3
"""Generates the contributes section of package.json from the Eclipse -> VS Code command map below.

Every Requality Eclipse command, menu entry, toolbar button, view and key binding is listed here with the
VS Code command that replaces it. Run after editing: python3 scripts/gen-manifest.py
"""
import json, os, collections

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PKG = os.path.join(ROOT, 'package.json')

# when-clause helpers (viewItem tags come from ExplorerService.contextValue: "is.<Class>" for the class hierarchy)
def is_(*classes):
    return '(' + ' || '.join("viewItem =~ /\\bis\\.%s\\b/" % c for c in classes) + ')'
EXPL = "view == requality.explorer"
# The view toolbar and view menu of the Requality Explorer: the Eclipse view (requality.explorerView, setting
# requality.explorer.view = eclipse) and the native tree (requality.explorer)
EXPL_VIEWS = "view =~ /^requality\\.explorer(View)?$/"
EXPL_ECLIPSE = "view == requality.explorerView"
# Key bindings of the explorer: VS Code sets no focusedView for the focus inside a webview view, the server views set
# requality.focusedView (src/ui/rswtHost.ts)
KB_EXPL = "focusedView == requality.explorer || requality.focusedView == requality.explorerView"
KB_LINKS = "focusedView == requality.links || requality.focusedView == requality.links"
NODE = "viewItem =~ /\\brequality\\.node\\b/"
PROJ = "viewItem =~ /\\brequality\\.project\\b/"
READY_PROJ = "viewItem =~ /\\brequality\\.project\\b.*\\bready\\b/"
RES = "viewItem =~ /\\brequality\\.resource\\b/"
PROJ_OR_RES = "viewItem =~ /\\brequality\\.(project|resource)\\b/"
SINGLE = "!listMultiSelection"

C = collections.OrderedDict()
def cmd(id, title, eclipse=None, icon=None, palette=True, enablement=None):
    C[id] = dict(command=id, title=title, category='Requality')
    if icon: C[id]['icon'] = icon
    if enablement: C[id]['enablement'] = enablement
    C[id]['_eclipse'] = eclipse
    C[id]['_palette'] = palette

# ---- workspace / project
cmd('requality.newProject', 'New Requality Project...', 'com.unitesk.requality.wizards.ReqWizard (newWizard)', icon='$(new-folder)')
cmd('requality.importDocument', 'Import Document...', 'DocumentImportWizard (importWizard) / CNF ImportAction')
cmd('requality.importReqIF', 'Import from ReqIF...', 'ImportFromReqIFWizard (importWizard)')
cmd('requality.exportReqIF', 'Export to ReqIF...', 'ExportToReqIFWizard (exportWizard)')
cmd('requality.projectProperties', 'Project Properties', 'propertyPages com.unitesk.requality.properties.*')
cmd('requality.preferences', 'Preferences', 'preferencePages requality.PreferencesPage')
cmd('requality.help', 'Requality Manual', 'org.eclipse.help.toc docs/manual/toc.xml')
# Window > Show View (the views of the Requality product) and Show View > Other...
SHOW_VIEWS = [('explorer', 'Requality Explorer'), ('properties', 'Properties'), ('outline', 'Outline'),
              ('links', 'Requality Links Explorer'), ('todo', 'Update Processor Tasks'), ('bookmarks', 'Bookmarks'),
              ('tasks', 'Tasks'), ('problems', 'Problems'), ('progress', 'Progress'), ('history', 'History'), ('synchronize', 'Synchronize')]
# The Eclipse ids of the views (org.eclipse.ui.views of plugin.xml and of the platform bundles).
SHOW_VIEW_IDS = {'explorer': 'com.unitesk.requality.views.RequalityExplorer', 'properties': 'org.eclipse.ui.views.PropertySheet',
                 'outline': 'org.eclipse.ui.views.ContentOutline', 'links': 'com.unitesk.requality.views.RequalityLinksExplorer',
                 'todo': 'com.unitesk.requality.views.ToDoList', 'bookmarks': 'org.eclipse.ui.views.BookmarkView',
                 'tasks': 'org.eclipse.ui.views.TaskList', 'problems': 'org.eclipse.ui.views.ProblemView',
                 'progress': 'org.eclipse.ui.views.ProgressView', 'history': 'org.eclipse.team.ui.GenericHistoryView',
                 'synchronize': 'org.eclipse.team.sync.views.SynchronizeView'}
for vid, label in SHOW_VIEWS:
    cmd('requality.showView.' + vid, label, 'Window > Show View > ' + label + ' (org.eclipse.ui.views.showView viewId=' + SHOW_VIEW_IDS[vid] + ')', palette=False)
cmd('requality.showView.other', 'Other...', 'Window > Show View > Other... (org.eclipse.ui.views.showView: ShowViewDialog)', palette=False)
# Window > Perspective (org.eclipse.ui commands of the workbench window). Open Perspective lists the perspective shortcuts
# of the current perspective (PerspectiveMenu: none of the registered perspectives is a shortcut) and Other...
cmd('requality.perspective.open', 'Open Perspective...', 'org.eclipse.ui.perspectives.showPerspective (SelectPerspectiveDialog)')
cmd('requality.perspective.openOther', 'Other...', 'Window > Perspective > Open Perspective > Other... (org.eclipse.ui.perspectives.showPerspective)', palette=False)
cmd('requality.perspective.customize', 'Customize Perspective...', 'org.eclipse.ui.window.customizePerspective (Window > Perspective > Customize Perspective...: CustomizePerspectiveDialog)')
cmd('requality.perspective.saveAs', 'Save Perspective As...', 'org.eclipse.ui.window.savePerspective (Window > Perspective > Save Perspective As...: SavePerspectiveDialog)')
cmd('requality.perspective.reset', 'Reset Perspective...', 'org.eclipse.ui.window.resetPerspective (Window > Perspective > Reset Perspective...)')
cmd('requality.perspective.close', 'Close Perspective', 'org.eclipse.ui.window.closePerspective (Window > Perspective > Close Perspective)')
cmd('requality.perspective.closeAll', 'Close All Perspectives', 'org.eclipse.ui.window.closeAllPerspectives (Window > Perspective > Close All Perspectives)')
cmd('requality.compare', 'Requality Compare', 'org.eclipse.compare.contentMergeViewers JSONCompareViewerCreator (Compare With)', icon='$(diff)')
cmd('requality.refresh', 'Refresh', 'CNF Refresh (F5)', icon='$(refresh)')
cmd('requality.explorer.collapseAll', 'Collapse All', 'CommonNavigatorActionGroup: CollapseAllHandler (the view toolbar of the Eclipse view)', icon='$(collapse-all)')
cmd('requality.filters', 'Filters and Customization...', 'CommonNavigatorActionGroup FilterActionGroup: SelectFiltersAction (CommonFilterSelectionDialog)', icon='$(filter)')
cmd('requality.recentFilters', 'Recent Filters...', 'FilterActionGroup: Recent Filters (ToggleFilterAction)', palette=False)
cmd('requality.linkWithEditor', 'Link with Editor', 'CNF Link with Editor', icon='$(link)')
cmd('requality.unlinkWithEditor', 'Unlink from Editor', 'CNF Link with Editor (toggle off)', icon='$(link-external)')
cmd('requality.undo', 'Undo', 'org.eclipse.ui.edit.undo (UndoRedoMech)')
cmd('requality.redo', 'Redo', 'org.eclipse.ui.edit.redo (UndoRedoMech)')
cmd('requality.open', 'Open', 'com.unitesk.requality.commands.OpenNode', palette=False)
cmd('requality.showProperties', 'Show Properties', 'org.eclipse.ui.views.PropertySheet', palette=False)
cmd('requality.properties', 'Properties', 'org.eclipse.ui.file.properties (Alt+Enter): PropertiesActionProvider PropertyDialogAction, else PropertyDialogHandler', palette=False)
cmd('requality.restartServer', 'Restart Requality Server', None)
cmd('requality.showLog', 'Show Server Log', None)
cmd('requality.configureMcp', 'Connect AI Agent (MCP)...', None)
# ---- creation (CreateNode with requality.parameter.NodeType, dynamic New menu)
cmd('requality.new', 'Other...', 'com.unitesk.requality.CreateDynNewMenu (NewMenuPoint)', palette=False)
# The New submenu: Test (CreateTest) and the NewMenuPoint items (NewTypedTemplatedMenuPoint, with the active template)
# of the node types creatable under the node; "Other..." shows the whole dynamic menu.
NEW_TYPES = [('Requirement', 'Requirement'), ('TextNode', 'Text Node'), ('TestPurpose', 'Test Purpose'), ('Comment', 'Comment'),
             ('DocFolder', 'Document Folder'), ('ReportFolder', 'Report Folder'), ('ReportSettings', 'Report Settings'),
             ('VirtualNode', 'Virtual Node'), ('NodeType', 'Node Type')]
for t, title in NEW_TYPES:
    cmd('requality.create' + t, title, 'CreateDynNewMenu / NewTypedTemplatedMenuPoint ' + t, palette=False)
cmd('requality.createTest', 'Test', 'com.unitesk.requality.CreateTest', palette=False)
cmd('requality.addRefTo', 'Add reference to...', 'com.unitesk.requality.commands.AddRefTo', palette=False)
cmd('requality.openBindings', 'Open bindings', 'com.unitesk.requality.commands.OpenBindings (Ctrl+R)')
# ---- edit
cmd('requality.copyQId', 'Copy Full Name', 'CopyNodeQId', palette=False)
cmd('requality.copy', 'Copy', 'CopyNode / org.eclipse.ui.edit.copy', palette=False)
cmd('requality.pasteInto', 'Paste into', 'PasteIntoNode / org.eclipse.ui.edit.paste', palette=False)
cmd('requality.pasteNear', 'Paste near', 'PasteNearNode', palette=False)
cmd('requality.delete', 'Delete Selection', 'org.eclipse.ui.edit.delete (DeleteSelection + delete handlers)', palette=False)
cmd('requality.renameDocFolder', 'Rename Document Folder', 'org.eclipse.ui.edit.rename (RenameDocFolderHandler)', palette=False)
cmd('requality.renameProject', 'Rename...', 'navigator RefactorActionProvider: RenameResourceAction + ProjectRenameParticipant', palette=False)
cmd('requality.rename', 'Rename', 'org.eclipse.ui.edit.rename (F2): project rename or RenameDocFolderHandler', palette=False)
cmd('requality.showHiddenChildren', 'Show Hidden Children', 'ShowHiddenChildren', palette=False)
cmd('requality.hideVirtualNodes', 'Hide Selected Virtual Nodes', 'HideInstancerNode', palette=False)
cmd('requality.find', 'Find Node...', 'org.eclipse.ui.edit.findReplace (FindNodeHandler)')
cmd('requality.transferredStatus', 'Transferred Status...', 'TransferedStatusGroup / TranserredStatusSubMenu', palette=False)
cmd('requality.massOps', 'Open Mass Ops Wizard', 'com.unitesk.requality.batch.wizard')
cmd('requality.fixLocationsDescriptions', 'Fix Locations Descriptions', 'FixLocationsDescription', palette=False)
cmd('requality.forceCheckers', 'Clear caches and run Checkers', 'ForceCheckers', palette=False)
cmd('requality.deleteStatisticsFile', 'Delete Statistics File', 'DeleteStatisticsFileHandler', palette=False)
cmd('requality.checkers', 'Checkers...', 'com.unitesk.requality.menu.Checkers (CheckerCNFMenuBuilder)', palette=False)
cmd('requality.resetProblems', 'Reset Problems', 'ResetProblems', palette=False)
# ---- editors
cmd('requality.openUniEditor', 'Edit in UniEditor', 'UniEditorOpen', palette=False)
cmd('requality.openModuleEditor', 'Edit in ModuleEditor', 'ModuleEditorOpen', palette=False)
cmd('requality.openReview', 'Review', 'ReviewEditorOpen', palette=False)
cmd('requality.openExternal.moduleEditor', 'ModuleEditor', 'ModuleEditorOpenExternal', palette=False)
cmd('requality.openExternal.uniEditor', 'UniEditor', 'UniEditorOpenExternal', palette=False)
cmd('requality.openExternal.markup', 'Markup', 'MarkupOpenExternal', palette=False)
cmd('requality.openExternal.report', 'Report', 'ReportOpenExternal', palette=False)
cmd('requality.findNext', 'Find Next', 'org.eclipse.ui.edit.findNext (Ctrl+K, Document Editor)', palette=False)
cmd('requality.findPrevious', 'Find Previous', 'org.eclipse.ui.edit.findPrevious (Ctrl+Shift+K, Document Editor)', palette=False)
# ---- documents
for pid, title, ecl in [
    ('saxHeaders', 'HTML Headers Document Processor', 'SAXHeadersDocumentProcessor'),
    ('updateDocument', 'Update Document', 'ProcessNewSpecificationVersion'),
    ('textToReq', 'Custom Text To Requirements Parser', 'TextToReqParser'),
    ('updateFromLibreOffice', 'Update From Libre Office', 'UpdateHTMLModule'),
    ('oldMarkup', 'Test processor for updating old markup', 'OldMarkupProcessor'),
    ('paramExtraction', 'Param extracton processor', 'ParamExtrProcessor')]:
    cmd('requality.docproc.' + pid, title, 'document.processor ' + ecl + ' (CNF DocumentProcessorAction)', palette=False)
# ---- reports
cmd('requality.generateReport', 'Generate Report', 'GenerateReport', palette=False)
cmd('requality.exportReport', 'Export Report', 'ExportReport', palette=False)
cmd('requality.exportToOdt', 'Export To Odt', 'ExportToOdt', palette=False)
cmd('requality.coverageSettings', 'Coverage Settings', 'com.unitesk.reqcoverage.OpenCoverageSettings', palette=False)
cmd('requality.progressSettings', 'Progress Settings', 'com.unitesk.requality.ProgressSettings', palette=False)
cmd('requality.traceabilitySettings', 'Traceability Settings', 'com.unitesk.requality.TraceabilitySettings', palette=False)
cmd('requality.traceSettings', 'Trace Settings', 'com.unitesk.requality.TraceSettings', palette=False)
cmd('requality.tvSettings', 'Table view settings', 'com.unitesk.requality.TVSettings', palette=False)
# ---- links explorer / explorer toolbar
cmd('requality.selectReferencedNode', 'Select Referenced Node', 'RequilityCNFRefSel (Explorer toolbar)', icon={'light': 'dist/server/resources/icons/zerolink.png', 'dark': 'dist/server/resources/icons/zerolink.png'})
cmd('requality.links.changeDirection', 'Change reference direction in view', 'RequilityLinksDirection', icon={'light': 'dist/server/resources/icons/biarrow.png', 'dark': 'dist/server/resources/icons/biarrow.png'})
cmd('requality.links.selectAttribute', 'Select filter for reference names', 'SelectLinksAttribute', icon={'light': 'dist/server/resources/icons/attribute.png', 'dark': 'dist/server/resources/icons/attribute.png'})
cmd('requality.links.lightLinks', 'Light up links', 'LightLinks', icon={'light': 'dist/server/resources/icons/link_break.png', 'dark': 'dist/server/resources/icons/link_break.png'})
# The handlers swap the ToolItem image with the state (context keys published by the ported handlers): one VS Code
# command per image, all running the same Eclipse command.
def ico(n):
    return {'light': 'dist/server/resources/icons/%s.png' % n, 'dark': 'dist/server/resources/icons/%s.png' % n}
cmd('requality.selectReferencedNode.oneLink', 'Select Referenced Node', 'RequilityCNFRefSel (onelink.png)', icon=ico('onelink'), palette=False)
cmd('requality.selectReferencedNode.multiLink', 'Select Referenced Node', 'RequilityCNFRefSel (multilink.png)', icon=ico('multilink'), palette=False)
cmd('requality.links.changeDirection.down', 'Change reference direction in view', 'RequilityLinksDirection (downarrow.png)', icon=ico('downarrow'), palette=False)
cmd('requality.links.changeDirection.up', 'Change reference direction in view', 'RequilityLinksDirection (uparrow.png)', icon=ico('uparrow'), palette=False)
cmd('requality.links.lightLinks.on', 'Light up links', 'LightLinks (light_link.png)', icon=ico('light_link'), palette=False)
# ---- update processor tasks
cmd('requality.todo.hideCompleted', 'Hide Completed Items', 'HideCompletedItems (toggle)', icon={'light': 'dist/server/resources/icons/exclusion_filter.png', 'dark': 'dist/server/resources/icons/exclusion_filter.png'})
cmd('requality.todo.showCompleted', 'Show Completed Items', 'HideCompletedItems (toggle off)', icon='$(eye)')
cmd('requality.todo.delete', 'Delete Selected Task', 'DeleteToDoItem', icon={'light': 'dist/server/resources/icons/rem_co.png', 'dark': 'dist/server/resources/icons/rem_co.png'})
cmd('requality.todo.deleteAll', 'Delete All Update Processor Tasks', 'DeleteAllToDoItems', icon={'light': 'dist/server/resources/icons/trash.png', 'dark': 'dist/server/resources/icons/trash.png'})

# ---- the navigator resource actions of projects (org.eclipse.ui.navigator.resources action providers) and the
# Project menu of the IDE
PROJECT_ACTIONS = [
    ('requality.project.copy', 'Copy', 'EditActionProvider: CopyAction'),
    ('requality.project.paste', 'Paste', 'EditActionProvider: PasteAction (CopyProjectOperation, Copy Project dialog)'),
    ('requality.project.delete', 'Delete', 'EditActionProvider: DeleteResourceAction (Delete Resources dialog)'),
    ('requality.project.move', 'Move...', 'RefactorActionProvider: MoveProjectAction (Move Project dialog)'),
    ('requality.project.build', 'Build Project', 'ResourceMgmtActionProvider: BuildAction (when not building automatically)'),
    ('requality.project.refresh', 'Refresh', 'ResourceMgmtActionProvider: RefreshAction'),
    ('requality.project.open', 'Open Project', 'ResourceMgmtActionProvider: OpenResourceAction'),
    ('requality.project.close', 'Close Project', 'ResourceMgmtActionProvider: CloseResourceAction'),
    ('requality.project.closeUnrelated', 'Close Unrelated Projects', 'ResourceMgmtActionProvider: CloseUnrelatedProjectsAction'),
    ('requality.goInto', 'Go Into', 'GoIntoActionProvider: GoIntoAction (an open project or a folder becomes the input of the explorer)'),
    ('requality.importAsProject', 'Import as Project', 'FoldersAsProjectsActionProvider: OpenFolderAsProjectAction'),
    ('requality.goToProject', 'Go to Project', 'FoldersAsProjectsActionProvider: SelectProjectForFolderAction'),
    ('requality.import', 'Import...', 'PortingActionProvider: ImportResourcesAction (Import wizard selection)'),
    ('requality.export', 'Export...', 'PortingActionProvider: ExportResourcesAction (Export wizard selection)'),
]
# Labels of the popup actions whose text depends on the selection (alternatives separated by "|", "*" at the end: a prefix)
ACTION_LABELS = {
    'requality.importAsProject': 'Import as Project|Import as Projects',
    'requality.goToProject': "Go to project '*|Go to corresponding projects",
}
for cid, title, ecl in PROJECT_ACTIONS:
    cmd(cid, title, ecl, palette=False)
cmd('requality.edit.copy', 'Copy', 'org.eclipse.ui.edit.copy (Ctrl+C): CopyHandler, or the CopyAction of projects', palette=False)
cmd('requality.edit.paste', 'Paste', 'org.eclipse.ui.edit.paste (Ctrl+V): PasteIntoHandler, or the PasteAction of projects', palette=False)
cmd('requality.edit.refresh', 'Refresh', 'org.eclipse.ui.file.refresh (F5): the RefreshAction of projects', palette=False)
cmd('requality.buildAll', 'Build All', 'org.eclipse.ui.project.buildAll (GlobalBuildAction, Ctrl+B)')
cmd('requality.clean', 'Clean...', 'org.eclipse.ui.project.cleanAction (BuildCleanAction: CleanDialog)')
cmd('requality.buildAutomatically', 'Build Automatically', 'org.eclipse.ui.project.buildAutomatically (ToggleAutoBuildAction)')
C['requality.buildAutomatically']['toggled'] = 'requality.autoBuilding'
cmd('requality.openWith', 'Open With...', 'OpenActionProvider: the Open With submenu (OpenWithMenu)', palette=False)
cmd('requality.showIn', 'Show In...', 'ShowInActionProvider: the Show In submenu (Alt+Shift+W)', palette=False)
cmd('requality.goToResource', 'Go to Resource...', 'GotoActionProvider: GotoResourceAction (Navigate > Go To > Resource...)')
# WorkManagementActionProvider (Edit > Add Bookmark..., Add Task...) and the IDE marker views
cmd('requality.addBookmark', 'Add Bookmark', 'WorkManagementActionProvider: AddBookmarkAction (org.eclipse.ui.edit.addBookmark)')
cmd('requality.addTask', 'Add Task...', 'WorkManagementActionProvider: AddTaskAction (org.eclipse.ui.edit.addTask)')
# PropertySheet: Pin to Selection (PinPropertySheetAction) in the toolbar of the Properties view
cmd('requality.properties.pin', 'Pin to Selection', 'PropertySheet: PinPropertySheetAction (pins this property view to the current selection)', icon='$(pin)', palette=False)
cmd('requality.properties.unpin', 'Pin to Selection', 'PropertySheet: PinPropertySheetAction (checked: unpins the view)', icon='$(pinned)', palette=False)
# The view menus of the Bookmarks and Tasks views (menu:org.eclipse.ui.views.BookmarkView / TaskList of org.eclipse.ui.ide)
MARKER_VIEW_MENUS = []
for vid, eid, groups in (('bookmarks', 'org.eclipse.ui.views.BookmarkView', False), ('tasks', 'org.eclipse.ui.views.TaskList', True)):
    items = [('show', 'Show', 'viewSubmenu', 'Show'), ('sortBy', 'Sort By', 'viewSubmenu', 'Sort By'),
             ('filters', 'Filters...', 'viewAction', 'Filters...'), ('columns', 'Configure Columns...', 'viewAction', 'Configure Columns...')]
    if groups:
        items.insert(1, ('groupBy', 'Group By', 'viewSubmenu', 'Group By'))
    for i, (key, title, kind, label) in enumerate(items):
        cid = 'requality.%s.%s' % (vid, key)
        cmd(cid, title, 'ExtendedMarkersView: the view menu of %s' % eid, palette=False)
        MARKER_VIEW_MENUS.append((cid, vid, kind, label, eid + '.viewMenu', i))
# The Synchronize view (SynchronizeView.configureToolBar): the toolbar (the Synchronizations drop-down, Pin) and the view
# menu (Pin, Link with Editor, Remove Current Synchronization, Remove Un-Pinned Synchronizations).
SYNC_VIEW = 'org.eclipse.team.sync.views.SynchronizeView'
cmd('requality.synchronize.synchronizations', 'Synchronizations', 'SynchronizeView: SynchronizePageDropDownAction (the participants and Synchronize...)', icon='$(sync)', palette=False)
SYNC_VIEW_MENUS = [('pin', 'Pin Current Synchronization', 'PinParticipantAction'), ('link', 'Link with Editor', 'ToggleLinkingAction'),
                   ('removeCurrent', 'Remove Current Synchronization', 'RemoveSynchronizeParticipantAction'),
                   ('removeUnpinned', 'Remove Un-Pinned Synchronizations', 'RemoveSynchronizeParticipantAction (all)')]
for key, title, action in SYNC_VIEW_MENUS:
    cmd('requality.synchronize.' + key, title, 'SynchronizeView: ' + action, icon='$(pin)' if key == 'pin' else None, palette=False)
for key in ('pin', 'link'):
    C['requality.synchronize.' + key]['toggled'] = 'requality.checked.synchronize.' + key
# The Progress view (ProgressView): Remove All Finished Operations of its toolbar and view menu.
PROGRESS_VIEW = 'org.eclipse.ui.views.ProgressView'
cmd('requality.progress.removeAllFinished', 'Remove All Finished Operations', 'ProgressView: ClearAllAction (view toolbar and menu)', icon='$(clear-all)', palette=False)
cmd('requality.progress.preferences', 'Preferences...', 'ProgressView: ViewPreferencesAction (view menu): JobsViewPreferenceDialog', palette=False)
# NewActionProvider: the New submenu of resources; File > New > Other... (Ctrl+N)
cmd('requality.newResource.project', 'Project...', 'NewActionProvider: NewProjectAction', palette=False)
cmd('requality.newResource.folder', 'Folder', 'NewActionProvider: commonWizard org.eclipse.ui.wizards.new.folder', palette=False)
cmd('requality.newResource.file', 'File', 'NewActionProvider: commonWizard org.eclipse.ui.wizards.new.file', palette=False)
cmd('requality.newResource.other', 'Other...', 'NewActionProvider: ActionFactory.NEW (NewWizardAction)', palette=False)
cmd('requality.newWizard', 'New...', 'org.eclipse.ui.newWizard (Ctrl+N): the New wizard selection dialog')
cmd('requality.fileImport', 'Import...', 'org.eclipse.ui.file.import (File > Import...): the Import wizard selection')
cmd('requality.fileExport', 'Export...', 'org.eclipse.ui.file.export (File > Export...): the Export wizard selection')
# WorkingSetActionProvider: the view menu of the explorer
cmd('requality.workingSet.select', 'Select Working Set...', 'WorkingSetFilterActionGroup: SelectWorkingSetAction')
cmd('requality.workingSet.deselect', 'Deselect Working Set', 'WorkingSetFilterActionGroup: ClearWorkingSetAction')
cmd('requality.workingSet.edit', 'Edit Active Working Set...', 'WorkingSetFilterActionGroup: EditWorkingSetAction')
cmd('requality.workingSet.recent', 'Recent Working Sets...', 'WorkingSetFilterActionGroup: the most recently used working sets (WorkingSetMenuContributionItem)', palette=False)
cmd('requality.workingSet.topProjects', 'Projects', 'WorkingSetRootModeActionGroup: Top Level Elements > Projects', palette=False)
cmd('requality.workingSet.topWorkingSets', 'Working Sets', 'WorkingSetRootModeActionGroup: Top Level Elements > Working Sets', palette=False)
C['requality.workingSet.topProjects']['toggled'] = 'requality.checked.workingSet.topProjects'
C['requality.workingSet.topWorkingSets']['toggled'] = 'requality.checked.workingSet.topWorkingSets'
# CommonNavigatorActionGroup: the frame actions of the view toolbar, shown while one of them is enabled
cmd('requality.explorer.back', 'Back', 'CommonNavigatorActionGroup: BackAction (view toolbar)', icon='$(arrow-left)', palette=False, enablement='requality.explorer.canBack')
cmd('requality.explorer.forward', 'Forward', 'CommonNavigatorActionGroup: ForwardAction (view toolbar)', icon='$(arrow-right)', palette=False, enablement='requality.explorer.canForward')
cmd('requality.explorer.up', 'Up One Level', 'CommonNavigatorActionGroup: UpAction (view toolbar)', icon='$(arrow-up)', palette=False, enablement='requality.explorer.canUp')
cmd('requality.importProjects', 'Import Existing Projects...', 'Import > General > Existing Projects into Workspace (WizardProjectsImportPage)')
# org.eclipse.ui.ide: File > Open Projects from File System... (SmartImportWizard) and, from org.eclipse.ui.ide.application,
# Configure > Configure and Detect Nested Projects... of open projects
cmd('requality.openProjectsFromFileSystem', 'Open Projects from File System...', 'org.eclipse.e4.ui.importer.openDirectory (File > Open Projects from File System...)')
cmd('requality.configureProject', 'Configure and Detect Nested Projects...', 'org.eclipse.e4.ui.importer.configureProject (Configure submenu of projects)', palette=False)
# org.eclipse.team.ui and org.eclipse.compare: the Team, Compare With and Replace With menus of resources and nodes
# (popupMenus objectContributions of the two bundles), Restore from Local History... of folders and projects, Import
# Project Set... of .psf files.
TEAM_ACTIONS = [
    ('requality.team.applyPatch', 'Apply Patch...', 'Team>Apply Patch...', 'org.eclipse.team.ui ApplyPatchAction (Team > Apply Patch...)'),
    ('requality.team.showLocalHistory', 'Show Local History', 'Team>Show Local History', 'org.eclipse.team.ui ShowLocalHistory (Team > Show Local History)'),
    ('requality.team.shareProject', 'Share Project...', 'Team>Share Project...', 'org.eclipse.team.ui ConfigureProjectAction (Team > Share Project...)'),
    ('requality.team.importProjectSet', 'Import Project Set...', 'Import Project Set...', 'org.eclipse.team.ui ImportProjectSetAction (.psf files)'),
    ('requality.compareWith.eachOther', 'Each Other', 'Compare With>Each Other', 'org.eclipse.team.ui / org.eclipse.compare CompareAction (Compare With > Each Other)'),
    ('requality.compareWith.localHistory', 'Local History...', 'Compare With>Local History...', 'org.eclipse.team.ui CompareLocalHistory (Compare With > Local History...)'),
    ('requality.compareWith.clipboard', 'Clipboard', 'Compare With>Clipboard', 'org.eclipse.compare ClipboardCompare (Compare With > Clipboard)'),
    ('requality.replaceWith.localHistory', 'Local History...', 'Replace With>Local History...', 'org.eclipse.team.ui ReplaceLocalHistory (Replace With > Local History...)'),
    ('requality.replaceWith.previous', 'Previous from Local History', 'Replace With>Previous from Local History', 'org.eclipse.compare ReplaceWithPreviousEditionAction'),
    ('requality.replaceWith.clipboard', 'Clipboard', 'Replace With>Clipboard', 'org.eclipse.compare ClipboardReplace (Replace With > Clipboard)'),
    ('requality.restoreFromLocalHistory', 'Restore from Local History...', 'Restore from Local History...', 'org.eclipse.compare AddFromHistoryAction (folders and projects)'),
]
for cid, title, _, ecl in TEAM_ACTIONS:
    cmd(cid, title, ecl, palette=False)

# ---- what each command runs on the server: an Eclipse command (CommandService), a wizard (WizardService) or a
# dynamic menu (MenuService). Commands without an entry are implemented by the extension (src/features).
CN = 'com.unitesk.requality.commands.'
NT = 'requality.parameter.NodeType'
ECLIPSE = {
    'requality.newProject': ('wizard', 'com.unitesk.requality.wizards.ReqWizard'),
    # CNFActionProvider actions of the explorer context menu, by their text (MenuService EXPLORER_POPUP)
    'requality.importDocument': ('action', 'Import Document...'),
    'requality.renameProject': ('action', 'Rename...'),
    'requality.docproc.saxHeaders': ('action', 'HTML Headers Document Processor'),
    'requality.docproc.updateDocument': ('action', 'Update Document'),
    'requality.docproc.textToReq': ('action', 'Custom Text To Requirements Parser'),
    'requality.docproc.updateFromLibreOffice': ('action', 'Update From Libre Office'),
    'requality.docproc.oldMarkup': ('action', 'Test processor for updating old markup'),
    'requality.docproc.paramExtraction': ('action', 'Param extracton processor'),
    'requality.importReqIF': ('wizard', 'com.unitesk.requality.eclipse.importFromReqIF'),
    'requality.exportReqIF': ('wizard', 'com.unitesk.requality.eclipse.exportToReqIF'),
    'requality.properties': ('command', 'org.eclipse.ui.file.properties'),
    'requality.showView.other': ('command', 'org.eclipse.ui.views.showView'),
    'requality.perspective.open': ('command', 'org.eclipse.ui.perspectives.showPerspective'),
    'requality.perspective.openOther': ('command', 'org.eclipse.ui.perspectives.showPerspective'),
    'requality.perspective.customize': ('command', 'org.eclipse.ui.window.customizePerspective'),
    'requality.perspective.saveAs': ('command', 'org.eclipse.ui.window.savePerspective'),
    'requality.perspective.reset': ('command', 'org.eclipse.ui.window.resetPerspective'),
    'requality.perspective.close': ('command', 'org.eclipse.ui.window.closePerspective'),
    'requality.perspective.closeAll': ('command', 'org.eclipse.ui.window.closeAllPerspectives'),
    'requality.undo': ('command', 'org.eclipse.ui.edit.undo'),
    'requality.redo': ('command', 'org.eclipse.ui.edit.redo'),
    'requality.new': ('menu', 'com.unitesk.requality.CreateDynNewMenu'),
    'requality.createTest': ('command', 'com.unitesk.requality.CreateTest'),
    'requality.addRefTo': ('command', CN + 'AddRefTo'),
    'requality.openBindings': ('command', CN + 'OpenBindings'),
    'requality.copyQId': ('command', CN + 'CopyNodeQId'),
    'requality.copy': ('command', CN + 'CopyNode'),
    'requality.pasteInto': ('command', CN + 'PasteIntoNode'),
    'requality.pasteNear': ('command', CN + 'PasteNearNode'),
    'requality.delete': ('command', 'org.eclipse.ui.edit.delete'),
    'requality.renameDocFolder': ('command', 'org.eclipse.ui.edit.rename'),
    'requality.showHiddenChildren': ('command', CN + 'ShowHiddenChildren'),
    'requality.hideVirtualNodes': ('command', CN + 'HideInstancerNode'),
    'requality.find': ('command', 'org.eclipse.ui.edit.findReplace'),
    'requality.transferredStatus': ('menu', 'com.unitesk.requality.eclipse.handlers.TransferedStatusGroup'),
    'requality.massOps': ('command', 'com.unitesk.requality.batch.wizard'),
    'requality.fixLocationsDescriptions': ('command', CN + 'FixLocationsDescription'),
    'requality.forceCheckers': ('command', CN + 'ForceCheckers'),
    'requality.deleteStatisticsFile': ('command', CN + 'DeleteStatisticsFileHandler'),
    'requality.checkers': ('menu', 'com.unitesk.requality.menu.AddCheckerBuilder'),
    'requality.resetProblems': ('command', CN + 'ResetProblems'),
    'requality.openUniEditor': ('command', CN + 'UniEditorOpen'),
    'requality.openModuleEditor': ('command', CN + 'ModuleEditorOpen'),
    'requality.openReview': ('command', CN + 'ReviewEditorOpen'),
    'requality.openExternal.moduleEditor': ('command', CN + 'ModuleEditorOpenExternal'),
    'requality.openExternal.uniEditor': ('command', CN + 'UniEditorOpenExternal'),
    'requality.openExternal.markup': ('command', CN + 'MarkupOpenExternal'),
    'requality.openExternal.report': ('command', CN + 'ReportOpenExternal'),
    'requality.findNext': ('command', 'org.eclipse.ui.edit.findNext'),
    'requality.findPrevious': ('command', 'org.eclipse.ui.edit.findPrevious'),
    'requality.generateReport': ('command', CN + 'GenerateReport'),
    'requality.exportReport': ('command', CN + 'ExportReport'),
    'requality.exportToOdt': ('command', CN + 'ExportToOdt'),
    'requality.coverageSettings': ('command', 'com.unitesk.reqcoverage.OpenCoverageSettings'),
    'requality.progressSettings': ('command', 'com.unitesk.requality.ProgressSettings'),
    'requality.traceabilitySettings': ('command', 'com.unitesk.requality.TraceabilitySettings'),
    'requality.traceSettings': ('command', 'com.unitesk.requality.TraceSettings'),
    'requality.tvSettings': ('command', 'com.unitesk.requality.TVSettings'),
    'requality.selectReferencedNode': ('command', CN + 'RequilityCNFRefSel'),
    'requality.selectReferencedNode.oneLink': ('command', CN + 'RequilityCNFRefSel'),
    'requality.selectReferencedNode.multiLink': ('command', CN + 'RequilityCNFRefSel'),
    'requality.links.changeDirection': ('command', CN + 'RequilityLinksDirection'),
    'requality.links.changeDirection.down': ('command', CN + 'RequilityLinksDirection'),
    'requality.links.changeDirection.up': ('command', CN + 'RequilityLinksDirection'),
    'requality.links.lightLinks.on': ('command', CN + 'LightLinks'),
    'requality.links.selectAttribute': ('command', CN + 'SelectLinksAttribute'),
    'requality.links.lightLinks': ('command', CN + 'LightLinks'),
    'requality.todo.hideCompleted': ('command', CN + 'HideCompletedItems'),
    'requality.todo.showCompleted': ('command', CN + 'HideCompletedItems'),
    'requality.todo.delete': ('command', CN + 'DeleteToDoItem'),
    'requality.todo.deleteAll': ('command', CN + 'DeleteAllToDoItems'),
}
for t, _ in NEW_TYPES:
    ECLIPSE['requality.create' + t] = ('newType', t)
for cid, title, _ in PROJECT_ACTIONS:
    ECLIPSE[cid] = ('action', ACTION_LABELS.get(cid, title))
# Submenus of the explorer popup filled by the action providers (OpenWithMenu, Show In): their items in a quick pick
ECLIPSE['requality.openWith'] = ('submenu', 'Open With')
# The view menu of the explorer (CommonNavigatorActionGroup and the action providers)
ECLIPSE['requality.filters'] = ('viewAction', 'Filters and Customization...')
ECLIPSE['requality.workingSet.select'] = ('viewAction', 'Select Working Set...')
ECLIPSE['requality.workingSet.deselect'] = ('viewAction', 'Deselect Working Set')
ECLIPSE['requality.workingSet.edit'] = ('viewAction', 'Edit Active Working Set...')
ECLIPSE['requality.workingSet.recent'] = ('viewList', '^[0-9]+ ')
ECLIPSE['requality.workingSet.topProjects'] = ('viewAction', 'Top Level Elements>Projects')
ECLIPSE['requality.workingSet.topWorkingSets'] = ('viewAction', 'Top Level Elements>Working Sets')
ECLIPSE['requality.addBookmark'] = ('command', 'org.eclipse.ui.edit.addBookmark')
ECLIPSE['requality.addTask'] = ('command', 'org.eclipse.ui.edit.addTask')
ECLIPSE['requality.newResource.project'] = ('action', 'New>Project...')
ECLIPSE['requality.newResource.folder'] = ('action', 'New>Folder')
ECLIPSE['requality.newResource.file'] = ('action', 'New>File')
ECLIPSE['requality.newResource.other'] = ('action', 'New>Other...')
ECLIPSE['requality.newWizard'] = ('command', 'org.eclipse.ui.newWizard')
ECLIPSE['requality.fileImport'] = ('command', 'org.eclipse.ui.file.import')
ECLIPSE['requality.fileExport'] = ('command', 'org.eclipse.ui.file.export')
# File > Import > General > Existing Projects into Workspace (WizardProjectsImportPage)
ECLIPSE['requality.importProjects'] = ('wizard', 'org.eclipse.ui.wizards.import.ExternalProject')
ECLIPSE['requality.openProjectsFromFileSystem'] = ('command', 'org.eclipse.e4.ui.importer.openDirectory')
ECLIPSE['requality.configureProject'] = ('action', 'Configure>Configure and Detect Nested Projects...')
ECLIPSE['requality.recentFilters'] = ('viewSubmenu', 'Recent Filters')
ECLIPSE['requality.showIn'] = ('submenu', 'Show In')
ECLIPSE['requality.goToResource'] = ('command', 'org.eclipse.ui.navigate.goToResource')
ECLIPSE['requality.edit.copy'] = ('command', 'org.eclipse.ui.edit.copy')
ECLIPSE['requality.edit.paste'] = ('command', 'org.eclipse.ui.edit.paste')
ECLIPSE['requality.edit.refresh'] = ('command', 'org.eclipse.ui.file.refresh')
ECLIPSE['requality.buildAll'] = ('command', 'org.eclipse.ui.project.buildAll')
ECLIPSE['requality.clean'] = ('command', 'org.eclipse.ui.project.cleanAction')
ECLIPSE['requality.buildAutomatically'] = ('command', 'org.eclipse.ui.project.buildAutomatically')
for cid, _, label, _ in TEAM_ACTIONS:
    ECLIPSE[cid] = ('action', label)
for cid, vid, kind, label, menu, i in MARKER_VIEW_MENUS:
    ECLIPSE[cid] = (kind, label, {'menu': menu})
for vid, label in SHOW_VIEWS:
    ECLIPSE['requality.showView.' + vid] = ('command', 'org.eclipse.ui.views.showView', {'org.eclipse.ui.views.showView.viewId': SHOW_VIEW_IDS[vid]})
ECLIPSE['requality.synchronize.synchronizations'] = ('menu', SYNC_VIEW + '.synchronizations')
ECLIPSE['requality.progress.removeAllFinished'] = ('viewAction', 'Remove All Finished Operations', {'menu': PROGRESS_VIEW + '.viewMenu'})
ECLIPSE['requality.progress.preferences'] = ('viewAction', 'Preferences...', {'menu': PROGRESS_VIEW + '.viewMenu'})
for key, title, action in SYNC_VIEW_MENUS:
    ECLIPSE['requality.synchronize.' + key] = ('viewAction', title, {'menu': SYNC_VIEW + '.viewMenu'})
for k in ECLIPSE:
    assert k in C, k
# Eclipse greys out commands whose handler is disabled for the selection: the extension mirrors the handler state
# (command/state) in a context key per command.
for k, v in ECLIPSE.items():
    if v[0] in ('command', 'action', 'submenu', 'viewAction', 'viewSubmenu', 'viewList') and k not in ('requality.undo', 'requality.redo'):
        C[k]['enablement'] = 'requality.enabled.' + k[len('requality.'):]

def strip(c):
    return {k: v for k, v in c.items() if not k.startswith('_')}

commands = [strip(c) for c in C.values()]

# ---- menus
ctx = []  # view/item/context for the explorer
def m(command, when, group):
    ctx.append(dict(command=command, when=when, group=group))
M = lambda *xs: ' && '.join(x for x in xs if x)
# group.new (1_new)
ctx.append(dict(submenu='requality.newMenu', when=M(EXPL, NODE, SINGLE), group='1_new@1'))
# NewActionProvider: the New submenu of projects, folders and files
ctx.append(dict(submenu='requality.newResourceMenu', when=M(EXPL, PROJ_OR_RES, SINGLE), group='1_new@0'))
m('requality.addRefTo', M(EXPL, NODE), '1_new@2')
m('requality.importDocument', M(EXPL, is_('DocFolder')), '1_new@3')
# group.edit (5_edit)
m('requality.deleteStatisticsFile', M(EXPL, READY_PROJ, SINGLE), '4_edit@0')
m('requality.copyQId', M(EXPL, NODE, SINGLE), '5_edit@1')
m('requality.copy', M(EXPL, NODE), '5_edit@2')
m('requality.pasteInto', M(EXPL, NODE, SINGLE), '5_edit@3')
m('requality.pasteNear', M(EXPL, NODE, SINGLE), '5_edit@4')
m('requality.openUniEditor', M(EXPL, is_('Requirement')), '5_edit@5')
m('requality.openModuleEditor', M(EXPL, is_('Requirement')), '5_edit@6')
m('requality.massOps', EXPL, '5_edit@7')
m('requality.openReview', M(EXPL, is_('Requirement'), "!(viewItem =~ /\\bis\\.TextNode\\b/)"), '5_edit@8')
m('requality.renameDocFolder', M(EXPL, is_('DocFolder'), SINGLE), '5_edit@9')
# EditActionProvider / RefactorActionProvider of projects: shown when the provider puts the action in the popup
SHOWN = lambda k: 'requality.shown.' + k[len('requality.'):]
m('requality.project.copy', M(EXPL, PROJ_OR_RES, SHOWN('requality.project.copy')), '5_edit@20')
m('requality.project.paste', M(EXPL, PROJ_OR_RES, SHOWN('requality.project.paste')), '5_edit@21')
m('requality.project.delete', M(EXPL, PROJ_OR_RES, SHOWN('requality.project.delete')), '5_edit@22')
m('requality.project.move', M(EXPL, PROJ_OR_RES, SHOWN('requality.project.move')), '5_reorganize@0')
m('requality.renameProject', M(EXPL, PROJ_OR_RES, SINGLE), '5_reorganize@1')
# GoIntoActionProvider: Go Into (appended to group.new, after the New submenu)
m('requality.goInto', M(EXPL, PROJ_OR_RES, SINGLE, SHOWN('requality.goInto')), '1_new@9')
# OpenActionProvider: Open of a file, the Open With submenu; ShowInActionProvider: Show In; FoldersAsProjectsActionProvider
m('requality.open', M(EXPL, RES, is_('IFile')), '2_open@1')
m('requality.openWith', M(EXPL, RES, SINGLE, SHOWN('requality.openWith')), '2_open@2')
m('requality.showIn', M(EXPL, PROJ_OR_RES, SHOWN('requality.showIn')), '2_open@3')
m('requality.goToProject', M(EXPL, RES, SHOWN('requality.goToProject')), '2_open@4')
m('requality.importAsProject', M(EXPL, RES, SHOWN('requality.importAsProject')), '5_x_port@0')
# PortingActionProvider: Import..., Export... (group.port)
m('requality.import', M(EXPL, PROJ_OR_RES, SHOWN('requality.import')), '5_x_port@1')
m('requality.export', M(EXPL, PROJ_OR_RES, SHOWN('requality.export')), '5_x_port@2')
m('requality.delete', M(EXPL, NODE), '5_edit@10')
m('requality.showHiddenChildren', EXPL, '5_edit@11')
m('requality.hideVirtualNodes', EXPL, '5_edit@12')
ctx.append(dict(submenu='requality.transferredStatusMenu', when=M(EXPL, NODE), group='5_edit@13'))
m('requality.fixLocationsDescriptions', M(EXPL, READY_PROJ, SINGLE), '6_project@1')
m('requality.forceCheckers', M(EXPL, READY_PROJ, SINGLE), '6_project@2')
ctx.append(dict(submenu='requality.externalMenu', when=EXPL, group='6_project@3'))
# group.generate (7_generate)
m('requality.generateReport', M(EXPL, is_('ReportSettings'), "!(viewItem =~ /\\bis\\.ReportData\\b/)"), '7_generate@1')
m('requality.exportReport', M(EXPL, is_('ReportData')), '7_generate@2')
for i, pid in enumerate(['saxHeaders', 'updateDocument', 'textToReq', 'updateFromLibreOffice', 'oldMarkup', 'paramExtraction']):
    m('requality.docproc.' + pid, M(EXPL, is_('Document'), SINGLE), '7_generate@%d' % (10 + i))
# group.build (8_build): ResourceMgmtActionProvider
for i, cid in enumerate(['requality.project.build', 'requality.project.refresh', 'requality.project.open',
                         'requality.project.close', 'requality.project.closeUnrelated']):
    m(cid, M(EXPL, PROJ_OR_RES if cid == 'requality.project.refresh' else PROJ, SHOWN(cid)), '8_build@%d' % (i + 1))
# additions: the Team, Compare With and Replace With submenus (shown when the server popup has their items)
ctx.append(dict(submenu='requality.teamMenu', when=EXPL, group='8_x_additions@1'))
m('requality.team.importProjectSet', M(EXPL, SHOWN('requality.team.importProjectSet')), '8_x_additions@2')
ctx.append(dict(submenu='requality.compareWithMenu', when=EXPL, group='8_x_additions@3'))
ctx.append(dict(submenu='requality.replaceWithMenu', when=EXPL, group='8_x_additions@4'))
m('requality.restoreFromLocalHistory', M(EXPL, SHOWN('requality.restoreFromLocalHistory')), '8_x_additions@5')
ctx.append(dict(submenu='requality.configureMenu', when=EXPL, group='8_x_additions@6'))
# group.properties (9_properties)
ctx.append(dict(submenu='requality.checkersMenu', when=M(EXPL, READY_PROJ, SINGLE), group='9_properties@1'))
# PropertiesActionProvider: Properties of projects, folders and files (IAdaptable)
m('requality.properties', M(EXPL, PROJ_OR_RES), '9_properties@2')
m('requality.showProperties', M(EXPL, NODE, SINGLE), '9_properties@3')

new_menu = [dict(command='requality.createTest', when=M(is_('Requirement', 'TestPurpose', 'TextNode'), SINGLE), group='1@1')]
new_menu += [dict(command='requality.create' + t, when="viewItem =~ /\\bnew\\.%s\\b/" % t, group='2@%d' % (i + 1)) for i, (t, _) in enumerate(NEW_TYPES)]
new_menu += [dict(command='requality.new', group='3@1')]
top_level_menu = [
    dict(command='requality.workingSet.topProjects', group='1@1'),
    dict(command='requality.workingSet.topWorkingSets', group='1@2'),
]
new_resource_menu = [
    dict(command='requality.newResource.project', when=SHOWN('requality.newResource.project'), group='1@1'),
    dict(command='requality.newResource.folder', when=SHOWN('requality.newResource.folder'), group='2@1'),
    dict(command='requality.newResource.file', when=SHOWN('requality.newResource.file'), group='2@2'),
    dict(command='requality.newResource.other', when=SHOWN('requality.newResource.other'), group='3@1'),
]
external_menu = [
    dict(command='requality.openExternal.moduleEditor', when=is_('Requirement')),
    dict(command='requality.openExternal.uniEditor', when=is_('Requirement')),
    dict(command='requality.openExternal.markup', when=is_('Document')),
    dict(command='requality.openExternal.report', when=is_('ReportData')),
]
checkers_menu = [
    dict(command='requality.checkers', group='1@1'),
    dict(command='requality.resetProblems', group='2@1'),
]
transferred_menu = [dict(command='requality.transferredStatus')]
# The order of the items as the server popup has them (PluginActionBuilder inserts after the group).
team_menu = [
    dict(command='requality.team.applyPatch', when=SHOWN('requality.team.applyPatch'), group='1@1'),
    dict(command='requality.team.showLocalHistory', when=SHOWN('requality.team.showLocalHistory'), group='4@1'),
    dict(command='requality.team.shareProject', when=SHOWN('requality.team.shareProject'), group='9@1'),
]
compare_with_menu = [
    dict(command='requality.compareWith.eachOther', when=SHOWN('requality.compareWith.eachOther'), group='1@1'),
    dict(command='requality.compareWith.localHistory', when=SHOWN('requality.compareWith.localHistory'), group='1@2'),
    dict(command='requality.compareWith.clipboard', when=SHOWN('requality.compareWith.clipboard'), group='1@3'),
]
replace_with_menu = [
    dict(command='requality.replaceWith.localHistory', when=SHOWN('requality.replaceWith.localHistory'), group='1@1'),
    dict(command='requality.replaceWith.clipboard', when=SHOWN('requality.replaceWith.clipboard'), group='1@2'),
    dict(command='requality.replaceWith.previous', when=SHOWN('requality.replaceWith.previous'), group='1@3'),
]

# The buttons of the view toolbars of plugin.xml (toolbar:<viewId>): the ToolItems of the server views, whose image and
# checked state are the context keys requality.toolItem.<command id>.image / .checked (ToolItem.setImage/setSelection).
LINKS_VIEW = 'com.unitesk.requality.views.RequalityLinksExplorer'
EXPLORER_VIEW = 'com.unitesk.requality.views.RequalityExplorer'
TODO_VIEW = 'com.unitesk.requality.views.ToDoList'
TOOLBAR_VIEWS = {
    'requality.selectReferencedNode': EXPLORER_VIEW, 'requality.selectReferencedNode.oneLink': EXPLORER_VIEW,
    'requality.selectReferencedNode.multiLink': EXPLORER_VIEW,
    'requality.links.changeDirection': LINKS_VIEW, 'requality.links.changeDirection.down': LINKS_VIEW,
    'requality.links.changeDirection.up': LINKS_VIEW, 'requality.links.selectAttribute': LINKS_VIEW,
    'requality.links.lightLinks': LINKS_VIEW, 'requality.links.lightLinks.on': LINKS_VIEW,
    'requality.todo.hideCompleted': TODO_VIEW, 'requality.todo.showCompleted': TODO_VIEW,
    'requality.todo.delete': TODO_VIEW, 'requality.todo.deleteAll': TODO_VIEW,
}
def toolitem(command, key):
    return 'requality.toolItem.com.unitesk.requality.commands.%s.%s' % (command, key)
REF_IMG = toolitem('RequilityCNFRefSel', 'image')
DIR_IMG = toolitem('RequilityLinksDirection', 'image')
LIGHT_IMG = toolitem('LightLinks', 'image')
HIDE_CHECKED = toolitem('HideCompletedItems', 'checked')

title = [
    dict(command='requality.configureMcp', when=EXPL_VIEWS, group='mcp@1'),
    dict(command='requality.explorer.back', when=EXPL_VIEWS + ' && requality.explorer.frames', group='navigation@-3'),
    dict(command='requality.explorer.forward', when=EXPL_VIEWS + ' && requality.explorer.frames', group='navigation@-2'),
    dict(command='requality.explorer.up', when=EXPL_VIEWS + ' && requality.explorer.frames', group='navigation@-1'),
    dict(command='requality.newProject', when=EXPL, group='navigation@1'),
    dict(command='requality.selectReferencedNode', when=EXPL_VIEWS + ' && %s != onelink.png && %s != multilink.png' % (REF_IMG, REF_IMG), group='navigation@2'),
    dict(command='requality.selectReferencedNode.oneLink', when=EXPL_VIEWS + ' && %s == onelink.png' % REF_IMG, group='navigation@2'),
    dict(command='requality.selectReferencedNode.multiLink', when=EXPL_VIEWS + ' && %s == multilink.png' % REF_IMG, group='navigation@2'),
    dict(command='requality.refresh', when=EXPL, group='navigation@3'),
    dict(command='requality.explorer.collapseAll', when=EXPL_ECLIPSE, group='navigation@4'),
    dict(command='requality.linkWithEditor', when=EXPL_VIEWS + ' && !requality.linked', group='navigation@5'),
    dict(command='requality.unlinkWithEditor', when=EXPL_VIEWS + ' && requality.linked', group='navigation@5'),
    dict(command='requality.links.changeDirection', when='view == requality.links && %s != downarrow.png && %s != uparrow.png' % (DIR_IMG, DIR_IMG), group='navigation@1'),
    dict(command='requality.links.changeDirection.down', when='view == requality.links && %s == downarrow.png' % DIR_IMG, group='navigation@1'),
    dict(command='requality.links.changeDirection.up', when='view == requality.links && %s == uparrow.png' % DIR_IMG, group='navigation@1'),
    dict(command='requality.links.selectAttribute', when='view == requality.links', group='navigation@2'),
    dict(command='requality.links.lightLinks', when='view == requality.links && %s != light_link.png' % LIGHT_IMG, group='navigation@3'),
    dict(command='requality.links.lightLinks.on', when='view == requality.links && %s == light_link.png' % LIGHT_IMG, group='navigation@3'),
    dict(command='requality.todo.hideCompleted', when='view == requality.todo && !%s' % HIDE_CHECKED, group='navigation@1'),
    dict(command='requality.todo.showCompleted', when='view == requality.todo && %s' % HIDE_CHECKED, group='navigation@1'),
    dict(command='requality.todo.delete', when='view == requality.todo', group='navigation@2'),
    dict(command='requality.todo.deleteAll', when='view == requality.todo', group='navigation@3'),
    *[dict(command=cid, when='view == requality.%s && requality.shown.%s' % (vid, cid[len('requality.'):]), group='1_markers@%d' % i)
      for cid, vid, kind, label, menu, i in MARKER_VIEW_MENUS],
    dict(command='requality.progress.removeAllFinished', when='view == requality.progress && requality.shown.progress.removeAllFinished', group='navigation@1'),
    dict(command='requality.progress.removeAllFinished', when='view == requality.progress && requality.shown.progress.removeAllFinished', group='1_progress@1'),
    dict(command='requality.progress.preferences', when='view == requality.progress && requality.shown.progress.preferences', group='1_progress@2'),
    dict(command='requality.synchronize.synchronizations', when='view == requality.synchronize', group='navigation@1'),
    dict(command='requality.synchronize.pin', when='view == requality.synchronize && requality.shown.synchronize.pin', group='navigation@2'),
    *[dict(command='requality.synchronize.' + key, when='view == requality.synchronize && requality.shown.synchronize.' + key, group='1_sync@%d' % i)
      for i, (key, title, action) in enumerate(SYNC_VIEW_MENUS, 1)],
    dict(command='requality.properties.pin', when='view == requality.properties && !requality.properties.pinned', group='navigation@1'),
    dict(command='requality.properties.unpin', when='view == requality.properties && requality.properties.pinned', group='navigation@1'),
    dict(command='requality.workingSet.select', when=EXPL_VIEWS + ' && requality.shown.workingSet.select', group='1_workingSets@1'),
    dict(command='requality.workingSet.deselect', when=EXPL_VIEWS + ' && requality.shown.workingSet.deselect', group='1_workingSets@2'),
    dict(command='requality.workingSet.edit', when=EXPL_VIEWS + ' && requality.shown.workingSet.edit', group='1_workingSets@3'),
    dict(command='requality.workingSet.recent', when=EXPL_VIEWS + ' && requality.shown.workingSet.recent', group='1_workingSets@4'),
    dict(submenu='requality.topLevelMenu', when=EXPL_VIEWS + ' && requality.shown.workingSet.topProjects', group='2_topLevel@1'),
    dict(command='requality.filters', when=EXPL_VIEWS, group='7_filters@1'),
    dict(command='requality.recentFilters', when=EXPL_VIEWS + ' && requality.shown.recentFilters', group='7_filters@2'),
    # The main menu of the Eclipse workbench (File, Edit, Navigate, Project, Window, Help) with the items of the
    # Requality product: VS Code has no extension menus in its menu bar, so they are submenus of the explorer's "...".
    *[dict(submenu='requality.mainMenu.' + m, when=EXPL_VIEWS, group='9_mainMenu@%d' % i)
      for i, m in enumerate(['file', 'edit', 'navigate', 'project', 'window', 'help'], 1)],
]

hidden_in_palette = [dict(command=k, when='false') for k, v in C.items() if not v['_palette']]
# Collapse All is a button of the Eclipse view (the native tree has its own)
hidden_in_palette.append(dict(command='requality.explorer.collapseAll', when='config.requality.explorer.view != native'))

# Single-key creation works inside the Open bindings popup (BindingsDialog list type-ahead), as in Eclipse.
keybindings = [
    dict(command='requality.openBindings', key='ctrl+r', mac='cmd+r', when=KB_EXPL),
    dict(command='requality.findNext', key='ctrl+k', mac='cmd+k', when='activeWebviewPanelId == requality.markup'),
    dict(command='requality.findPrevious', key='ctrl+shift+k', mac='cmd+shift+k', when='activeWebviewPanelId == requality.markup'),
    dict(command='requality.delete', key='delete', mac='cmd+backspace', when=KB_EXPL),
    dict(command='requality.edit.copy', key='ctrl+c', mac='cmd+c', when=KB_EXPL),
    dict(command='requality.edit.paste', key='ctrl+v', mac='cmd+v', when=KB_EXPL),
    dict(command='requality.edit.refresh', key='f5', when=KB_EXPL),
    dict(command='requality.buildAll', key='ctrl+b', mac='cmd+b', when=KB_EXPL),
    dict(command='requality.find', key='ctrl+f', mac='cmd+f', when=KB_EXPL),
    dict(command='requality.undo', key='ctrl+z', mac='cmd+z', when=KB_EXPL + ' || ' + KB_LINKS),
    dict(command='requality.redo', key='ctrl+y', mac='cmd+shift+z', when=KB_EXPL + ' || ' + KB_LINKS),
    dict(command='requality.properties', key='alt+enter', when=KB_EXPL),
    dict(command='requality.showIn', key='alt+shift+w', when=KB_EXPL),
    dict(command='requality.newWizard', key='ctrl+n', mac='cmd+n', when=KB_EXPL),
    dict(command='requality.properties', key='cmd+i', when='isMac && (' + KB_EXPL + ')'),
    dict(command='requality.rename', key='f2', when=KB_EXPL),
    # Show View bindings of org.eclipse.ui.ide and org.eclipse.team.ui (M2+M3+Q <letter>, carbon M1+M3+Q <letter>):
    # the Show View dialog is VS Code's Open View, the views (the Eclipse Problems view too) are focused in VS Code.
    dict(command='workbench.action.openView', key='alt+shift+q q', mac='cmd+alt+q q'),
    dict(command='requality.problems.focus', key='alt+shift+q x', mac='cmd+alt+q x'),
    dict(command='requality.outline.focus', key='alt+shift+q o', mac='cmd+alt+q o'),
    dict(command='requality.synchronize.focus', key='alt+shift+q y', mac='cmd+alt+q y'),
    dict(command='requality.history.focus', key='alt+shift+q z', mac='cmd+alt+q z'),
]

configuration = {
    'title': 'Requality',
    'properties': collections.OrderedDict([
        ('requality.java.home', {'type': 'string', 'default': '', 'scope': 'machine', 'markdownDescription': 'JDK/JRE (17 or newer) used to run the Requality server. Empty: `JAVA_HOME`, then `java` on `PATH`.'}),
        ('requality.server.maxHeap', {'type': 'string', 'default': '2g', 'scope': 'machine', 'description': 'Maximum Java heap of the Requality server (-Xmx).'}),
        ('requality.libreOfficePath', {'type': 'string', 'default': 'soffice', 'scope': 'machine', 'description': 'Path to libreoffice(v5+) soffice.'}),
        ('requality.imageMagickPath', {'type': 'string', 'default': '', 'scope': 'machine', 'description': 'Path to ImageMagick.'}),
        ('requality.globalCacheLocation', {'type': 'string', 'default': '', 'scope': 'machine', 'description': 'Global cache location.'}),
        ('requality.debugLevel', {'type': 'integer', 'default': 0, 'minimum': 0, 'maximum': 10, 'description': 'Requality core debug level (Eclipse -debug).'}),
        ('requality.explorer.view', {'type': 'string', 'enum': ['eclipse', 'native'], 'default': 'eclipse', 'enumDescriptions': [
            'The view of the Eclipse plug-in (RequalityCNF): its tree with the images and colors of the explorer, its popup menu, drag and drop with Shift, Ctrl and Alt, and its key bindings.',
            'A VS Code tree with the items of the explorer and the commands of its popup menu (VS Code reports no modifier keys of a drop: the kind of drop is picked).'],
            'description': 'How the Requality Explorer is shown. Changing it restarts the Requality server.'}),
        ('requality.explorer.linkWithEditor', {'type': 'boolean', 'default': True, 'description': 'Link the Requality Explorer selection with the active editor.'}),
        ('requality.window.title', {'type': 'boolean', 'default': True, 'description': 'Use the window title of Eclipse (workspace name, perspective, active editor, location and product, as Preferences > General > Workspace selects them) as the title of the VS Code window (the window.title setting of the workspace; a title you set there yourself is kept).'}),
        ('requality.explorer.filters', {'type': 'object', 'default': {}, 'description': 'Requality Explorer filters (true = hide).', 'properties': collections.OrderedDict([
            ('documents', {'type': 'boolean', 'default': False, 'description': 'Document Filter'}),
            ('requirements', {'type': 'boolean', 'default': False, 'description': 'Requirement Filter'}),
            ('types', {'type': 'boolean', 'default': True, 'description': 'Types Filter'}),
            ('locations', {'type': 'boolean', 'default': True, 'description': 'Locations Filter'}),
            ('reportData', {'type': 'boolean', 'default': False, 'description': 'Report Data Filter'}),
            ('comments', {'type': 'boolean', 'default': False, 'description': 'Comment Filter'}),
            ('restrictedFolders', {'type': 'boolean', 'default': True, 'description': 'Restricted Folders Filter'}),
            ('instancesFolders', {'type': 'boolean', 'default': True, 'description': 'Instances Folder Filter'}),
            ('resourcesInRequalityProjects', {'type': 'boolean', 'default': True, 'description': 'Resource in Requality Project Filter (a catalog shows its nodes, not its files)'}),
            ('startsWithDot', {'type': 'boolean', 'default': True, 'description': '.* resources'}),
            ('endsWithClass', {'type': 'boolean', 'default': False, 'description': '*.class resources'}),
            ('closedProjects', {'type': 'boolean', 'default': False, 'description': 'Closed projects'}),
        ])}),
        ('requality.explorer.contentExtensions', {'type': 'object', 'default': {}, 'description': 'Requality Explorer content extensions (Filters and Customization > Content): id -> active.'}),
        ('requality.explorer.userFilters', {'type': 'array', 'default': [], 'description': 'Requality Explorer user filters (Filters and Customization > User filters): resources whose names match an enabled pattern are hidden.',
            'items': {'type': 'object', 'properties': {'pattern': {'type': 'string'}, 'enabled': {'type': 'boolean'}}}}),
    ]),
}

contributes = collections.OrderedDict()
contributes['commands'] = commands
contributes['submenus'] = [
    dict(id='requality.newMenu', label='New'),
    dict(id='requality.newResourceMenu', label='New'),
    dict(id='requality.topLevelMenu', label='Top Level Elements'),
    dict(id='requality.externalMenu', label='Edit in external browser'),
    dict(id='requality.checkersMenu', label='Checkers'),
    dict(id='requality.transferredStatusMenu', label='Transferred status'),
    dict(id='requality.teamMenu', label='Team'),
    dict(id='requality.compareWithMenu', label='Compare With'),
    dict(id='requality.replaceWithMenu', label='Replace With'),
    dict(id='requality.configureMenu', label='Configure'),
    dict(id='requality.mainMenu.file', label='File'),
    dict(id='requality.mainMenu.edit', label='Edit'),
    dict(id='requality.mainMenu.navigate', label='Navigate'),
    dict(id='requality.mainMenu.navigate.goTo', label='Go To'),
    dict(id='requality.mainMenu.project', label='Project'),
    dict(id='requality.mainMenu.window', label='Window'),
    dict(id='requality.mainMenu.window.showView', label='Show View'),
    dict(id='requality.mainMenu.window.perspective', label='Perspective'),
    dict(id='requality.mainMenu.window.perspective.open', label='Open Perspective'),
    dict(id='requality.mainMenu.help', label='Help'),
]
contributes['viewsContainers'] = {
    'activitybar': [dict(id='requality', title='Requality', icon='media/requality.svg')],
    'panel': [dict(id='requality-panel', title='Requality', icon='media/requality.svg')],
    # right of the editor area in the layout of a perspective (the secondary side bar)
    'secondarySidebar': [dict(id='requality-right', title='Requality', icon='media/requality.svg')],
}
contributes['views'] = {
    'requality': [
        # the Requality Explorer as the Eclipse view (the SWT tree of RequalityCNF) or as a native tree
        dict(id='requality.explorerView', name='Requality Explorer', type='webview', icon='media/requality.svg', when='config.requality.explorer.view != native'),
        dict(id='requality.explorer', name='Requality Explorer', icon='media/requality.svg', when='config.requality.explorer.view == native'),
    ],
    # ReqEditorPerspectiveFactory: Outline right of the editor area, the Links Explorer below it
    'requality-right': [
        dict(id='requality.outline', name='Outline', type='webview', icon='media/requality.svg'),
        dict(id='requality.links', name='Requality Links Explorer', type='webview', icon='media/requality.svg'),
    ],
    'requality-panel': [
        dict(id='requality.properties', name='Properties', type='webview', icon='media/requality.svg'),
        dict(id='requality.problems', name='Problems', type='webview', icon='media/requality.svg'),
        dict(id='requality.todo', name='Update Processor Tasks', type='webview', icon='media/requality.svg'),
        dict(id='requality.bookmarks', name='Bookmarks', type='webview', icon='media/requality.svg'),
        dict(id='requality.tasks', name='Tasks', type='webview', icon='media/requality.svg'),
        dict(id='requality.history', name='History', type='webview', icon='media/requality.svg'),
        dict(id='requality.synchronize', name='Synchronize', type='webview', icon='media/requality.svg'),
        dict(id='requality.progress', name='Progress', type='webview', icon='media/requality.svg'),
    ],
}
contributes['viewsWelcome'] = [
    dict(view='requality.explorer', contents='No Requality projects in this workspace.\n[New Requality Project](command:requality.newProject)\n[Open Folder](command:vscode.openFolder)', when='requality.serverState == ready'),
    dict(view='requality.explorer', contents='Starting the Requality server...', when='requality.serverState == starting'),
    dict(view='requality.explorer', contents='The Requality server is not running.\n[Restart Requality Server](command:requality.restartServer)\n[Show Server Log](command:requality.showLog)', when='requality.serverState == failed'),
]
# Eclipse Compare (Compare With > Each Other, team compare of a node file) opens the Requality content merge viewer.
editor_title = [dict(command='requality.compare', when='isInDiffEditor && resourceExtname == .json', group='navigation@1')]
explorer_context = [dict(command='requality.compare', when='listDoubleSelection && resourceExtname == .json', group='3_compare@9')]
# The items of the Eclipse main menus (IDE and Requality contributions), in the order of the Eclipse menus.
main_file = [
    dict(command='requality.newWizard', group='1_new@1'),
    dict(command='requality.openProjectsFromFileSystem', group='2_open@1'),
    dict(command='requality.rename', group='4_edit@1'),
    dict(command='requality.edit.refresh', group='4_edit@2'),
    dict(command='requality.fileImport', group='5_import@1'),
    dict(command='requality.fileExport', group='5_import@2'),
    dict(command='requality.properties', group='6_properties@1'),
]
main_edit = [
    dict(command='requality.undo', group='1_undo@1'),
    dict(command='requality.redo', group='1_undo@2'),
    dict(command='requality.edit.copy', group='2_edit@1'),
    dict(command='requality.edit.paste', group='2_edit@2'),
    dict(command='requality.delete', group='2_edit@3'),
    # No Edit > Find Next / Find Previous: the plugin.xml menu contribution is commented out upstream (#13947)
    dict(command='requality.addBookmark', group='4_additions@1'),
    dict(command='requality.addTask', group='4_additions@2'),
]
main_navigate = [
    dict(command='requality.goInto', group='1_go@1'),
    dict(submenu='requality.mainMenu.navigate.goTo', group='1_go@2'),
    dict(command='requality.showIn', group='2_show@1'),
]
main_navigate_goto = [
    dict(command='requality.explorer.back', group='1_go@1'),
    dict(command='requality.explorer.forward', group='1_go@2'),
    dict(command='requality.explorer.up', group='1_go@3'),
    dict(command='requality.goToResource', group='2_resource@1'),
]
main_project = [
    dict(command='requality.project.open', group='1_open@1'),
    dict(command='requality.project.close', group='1_open@2'),
    dict(command='requality.buildAll', group='2_build@1'),
    dict(command='requality.project.build', group='2_build@2'),
    dict(command='requality.clean', group='2_build@3'),
    dict(command='requality.buildAutomatically', group='2_build@4'),
    dict(command='requality.projectProperties', group='3_properties@1'),
]
# ShowViewMenu: the show-view shortcuts of the perspective and the views opened with Other..., sorted by label (the
# server publishes them as requality.showViewMenu.<view id>), then Other...
main_window_showview = [dict(command='requality.showView.' + vid, when='requality.showViewMenu.' + SHOW_VIEW_IDS[vid], group='1_views@%d' % i)
                        for i, (vid, _) in enumerate(sorted(SHOW_VIEWS, key=lambda v: v[1].lower()), 1)] + [
    dict(command='requality.showView.other', group='2_other@1')]
main_window_perspective = [
    dict(submenu='requality.mainMenu.window.perspective.open', group='1_open@1'),
    dict(command='requality.perspective.customize', group='2_customize@1'),
    dict(command='requality.perspective.saveAs', group='2_customize@2'),
    dict(command='requality.perspective.reset', group='2_customize@3'),
    dict(command='requality.perspective.close', group='3_close@1'),
    dict(command='requality.perspective.closeAll', group='3_close@2'),
]
main_window_perspective_open = [dict(command='requality.perspective.openOther', group='2_other@1')]
main_window = [
    dict(submenu='requality.mainMenu.window.showView', group='1_views@1'),
    dict(submenu='requality.mainMenu.window.perspective', group='1_views@2'),
    dict(command='requality.preferences', group='9_preferences@1'),
]
main_help = [
    dict(command='requality.help', group='1_help@1'),
]
# Customize Perspective > Menu Visibility hides items of the main menus (the server publishes requality.menuHidden.<id>).
def hideable(items):
    for it in items:
        key = 'requality.menuHidden.' + it.get('command', it.get('submenu'))
        it['when'] = '!' + key if 'when' not in it else '(' + it['when'] + ') && !' + key
    return items
for _menu in (main_file, main_edit, main_navigate, main_navigate_goto, main_project, main_window, main_window_showview,
              main_window_perspective, main_window_perspective_open, main_help):
    hideable(_menu)
contributes['menus'] = collections.OrderedDict([
    ('view/title', title),
    ('requality.mainMenu.file', main_file),
    ('requality.mainMenu.edit', main_edit),
    ('requality.mainMenu.navigate', main_navigate),
    ('requality.mainMenu.navigate.goTo', main_navigate_goto),
    ('requality.mainMenu.project', main_project),
    ('requality.mainMenu.window', main_window),
    ('requality.mainMenu.window.showView', main_window_showview),
    ('requality.mainMenu.window.perspective', main_window_perspective),
    ('requality.mainMenu.window.perspective.open', main_window_perspective_open),
    ('requality.mainMenu.help', main_help),
    ('editor/title', editor_title),
    ('explorer/context', explorer_context),
    ('view/item/context', ctx),
    ('requality.newMenu', new_menu),
    ('requality.newResourceMenu', new_resource_menu),
    ('requality.topLevelMenu', top_level_menu),
    ('requality.externalMenu', external_menu),
    ('requality.checkersMenu', checkers_menu),
    ('requality.transferredStatusMenu', transferred_menu),
    ('requality.teamMenu', team_menu),
    ('requality.compareWithMenu', compare_with_menu),
    ('requality.replaceWithMenu', replace_with_menu),
    ('requality.configureMenu', [dict(command='requality.configureProject', when=SHOWN('requality.configureProject'))]),
    ('commandPalette', hidden_in_palette),
])
contributes['keybindings'] = keybindings
# Label colors of the lightweight decorators (marker package): the RGB values of the Eclipse decorators.
contributes['colors'] = [
    dict(id='requality.decoration.linkedFrom', description='Requality Links Explorer: nodes referencing the selection (LinksDecorator).', defaults=dict(light='#ff81a3', dark='#ff81a3', highContrast='#ff81a3')),
    dict(id='requality.decoration.linkedTo', description='Requality Links Explorer: nodes referenced by the selection (LinksDecorator).', defaults=dict(light='#3b8fc7', dark='#abddfa', highContrast='#abddfa')),
    dict(id='requality.decoration.referenceTarget', description='Selected reference target (SelectedRefTargetDecorator).', defaults=dict(light='#808080', dark='#a0a0a0', highContrast='#ffffff')),
    dict(id='requality.decoration.repository', description='Nodes not connected to the repository (RepoDecorator).', defaults=dict(light='#646464', dark='#9a9a9a', highContrast='#ffffff')),
]
contributes['configuration'] = configuration

with open(PKG) as f:
    pkg = json.load(f, object_pairs_hook=collections.OrderedDict)
pkg['contributes'] = contributes
with open(PKG, 'w') as f:
    json.dump(pkg, f, indent=2, ensure_ascii=False)
    f.write('\n')

def ts_literal(v, indent=''):
    """A TypeScript object literal with single-quoted strings (quotes in strings escaped)."""
    if isinstance(v, dict):
        inner = indent + '  '
        return '{\n' + ',\n'.join(inner + ts_literal(k) + ': ' + ts_literal(x, inner) for k, x in v.items()) + '\n' + indent + '}'
    if isinstance(v, str):
        return "'" + v.replace('\\', '\\\\').replace("'", "\\'") + "'"
    return json.dumps(v)

# The main menus of the product for the server (Customize Perspective > Menu Visibility): the menus, their submenus and
# items in the order of the Eclipse menus, each item with its VS Code command, its Eclipse command and its label.
SUBMENU_LABELS = {m['id']: m['label'] for m in contributes['submenus']}
def menu_model(menu_id):
    items = []
    for it in contributes['menus'].get(menu_id, []):
        if 'submenu' in it:
            items.append(collections.OrderedDict([('id', it['submenu']), ('label', SUBMENU_LABELS[it['submenu']]),
                                                  ('group', it.get('group', '')), ('items', menu_model(it['submenu']))]))
        else:
            c = it['command']
            e = ECLIPSE.get(c)
            items.append(collections.OrderedDict([('id', c), ('label', C[c]['title']), ('group', it.get('group', '')),
                                                  ('eclipseCommand', e[1] if e and e[0] == 'command' else None)]))
    return items
MAIN_MENU_RES = os.path.join(ROOT, 'server', 'src', 'main', 'resources', 'com', 'unitesk', 'requality', 'vscode', 'ui', 'workbench', 'main-menu.json')
os.makedirs(os.path.dirname(MAIN_MENU_RES), exist_ok=True)
with open(MAIN_MENU_RES, 'w') as f:
    json.dump([collections.OrderedDict([('id', 'requality.mainMenu.' + m), ('label', SUBMENU_LABELS['requality.mainMenu.' + m]),
                                        ('items', menu_model('requality.mainMenu.' + m))])
               for m in ['file', 'edit', 'navigate', 'project', 'window', 'help']], f, indent=2, ensure_ascii=False)
    f.write('\n')

# Eclipse -> VS Code map for the parity document.
with open(os.path.join(ROOT, 'docs', 'command-map.json'), 'w') as f:
    json.dump([dict(command=k, title=v['title'], eclipse=v['_eclipse']) for k, v in C.items()], f, indent=2, ensure_ascii=False)
    f.write('\n')
# Server-side target of every command for the extension (src/generated/commandMap.ts).
os.makedirs(os.path.join(ROOT, 'src', 'generated'), exist_ok=True)
with open(os.path.join(ROOT, 'src', 'generated', 'commandMap.ts'), 'w') as f:
    f.write('// Generated by scripts/gen-manifest.py - do not edit.\n')
    f.write('/** What a VS Code command runs on the server: an Eclipse command, a wizard or a dynamic menu. */\n')
    f.write('export interface ServerTarget {\n  kind: \'command\' | \'wizard\' | \'menu\' | \'action\' | \'submenu\' | \'viewAction\' | \'viewSubmenu\' | \'viewList\' | \'newType\';\n  id: string;\n  params?: Record<string, string>;\n  /** The Eclipse view whose toolbar button runs the command (the ToolItem is the trigger of the command). */\n  toolbarView?: string;\n}\n\n')
    f.write('export const SERVER_TARGETS: Record<string, ServerTarget> = ')
    out = collections.OrderedDict()
    for k, v in ECLIPSE.items():
        t = collections.OrderedDict([('kind', v[0]), ('id', v[1])])
        if len(v) > 2 and v[2]:
            t['params'] = v[2]
        if k in TOOLBAR_VIEWS:
            t['toolbarView'] = TOOLBAR_VIEWS[k]
        out[k] = t
    f.write(ts_literal(out))
    f.write(';\n')
print('commands:', len(commands), 'context entries:', len(ctx))
