#!/usr/bin/env python3
"""Mechanical first step of porting one class of the Eclipse UI layer (server/upstream/eclipse-reference/java).

Copies the class to server/src/main/java/com/unitesk/requality/vscode/port/... (same relative package), rewrites the
package, the imports of SWT/JFace/Eclipse classes that the remote SWT layer provides, and the Requality Eclipse-layer
imports. Remaining org.eclipse imports are printed: they must be replaced by hand (see docs/PORTING-GUIDE.md).

usage: python3 scripts/port-class.py com/unitesk/requality/eclipse/views/properties/MainPropertySection.java [--force]
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REF = os.path.join(ROOT, 'server', 'upstream', 'eclipse-reference', 'java')
OUT = os.path.join(ROOT, 'server', 'src', 'main', 'java')
UI = 'com.unitesk.requality.vscode.ui'
PORT = 'com.unitesk.requality.vscode.port'

# Eclipse-layer packages outside com.unitesk.requality.eclipse (names clash with core packages for report/tests).
LAYER_CLASSES = {
    'com.unitesk.requality.report.StatisticManager': PORT + '.report.StatisticManager',
    'com.unitesk.requality.tests.TestDialogProvider': PORT + '.tests.TestDialogProvider',
    'com.unitesk.requality.tests.EclipseTestGenerator': PORT + '.tests.EclipseTestGenerator',
    'com.unitesk.requality.tests.ITestDialog': PORT + '.tests.ITestDialog',
}

SIMPLE = [
    (r'org\.eclipse\.swt\.SWT\b', UI + '.swt.SWT'),
    (r'org\.eclipse\.swt\.SWTException\b', UI + '.swt.SWTException'),
    (r'org\.eclipse\.swt\.widgets\.', UI + '.swt.widgets.'),
    (r'org\.eclipse\.swt\.layout\.', UI + '.swt.layout.'),
    (r'org\.eclipse\.swt\.awt\.', UI + '.swt.awt.'),
    (r'org\.eclipse\.e4\.core\.services\.log\.', UI + '.e4.core.services.log.'),
    (r'org\.eclipse\.swt\.events\.', UI + '.swt.events.'),
    (r'org\.eclipse\.swt\.graphics\.', UI + '.swt.graphics.'),
    (r'org\.eclipse\.swt\.custom\.(ScrolledComposite|CCombo|CTabFolder|CTabItem|BusyIndicator)\b', UI + r'.swt.widgets.\1'),
    (r'org\.eclipse\.swt\.browser\.Browser\b', UI + '.swt.widgets.Browser'),
    (r'org\.eclipse\.jface\.(dialogs|viewers|wizard|window|preference|fieldassist|resource|operation|action|text)\.', UI + r'.jface.\1.'),
    (r'org\.eclipse\.jface\.util\.(IPropertyChangeListener|PropertyChangeEvent)\b', UI + r'.jface.preference.\1'),
    (r'org\.eclipse\.ui\.dialogs\.(ElementTreeSelectionDialog|ElementListSelectionDialog|ListSelectionDialog|ISelectionStatusValidator|SelectionDialog)\b', UI + r'.jface.dialogs.\1'),
    (r'org\.eclipse\.ui\.dialogs\.PropertyPage\b', UI + '.jface.preference.PropertyPage'),
    (r'org\.eclipse\.ui\.views\.properties\.tabbed\.', UI + '.tabbed.'),
    (r'org\.eclipse\.core\.runtime\.(IStatus|Status|IProgressMonitor|NullProgressMonitor|CoreException)\b', UI + r'.runtime.\1'),
    (r'org\.eclipse\.core\.runtime\.jobs\.(Job|ISchedulingRule)\b', UI + r'.runtime.\1'),
    (r'org\.eclipse\.core\.resources\.WorkspaceJob\b', UI + '.runtime.WorkspaceJob'),
    (r'org\.eclipse\.ui\.progress\.UIJob\b', UI + '.runtime.UIJob'),
    (r'org\.eclipse\.ui\.IWorkbenchPart\b', UI + '.workbench.IWorkbenchPart'),
    (r'org\.eclipse\.ui\.forms\.widgets\.Hyperlink\b', UI + '.swt.widgets.Hyperlink'),
    (r'org\.eclipse\.ui\.forms\.events\.(IHyperlinkListener|HyperlinkEvent)\b', UI + r'.swt.widgets.Hyperlink.\1'),
    (r'org\.eclipse\.core\.resources\.(IResource|IFile|IFolder|IContainer|IProject|IProjectDescription|IWorkspace|IWorkspaceRoot|IWorkspaceRunnable|ResourcesPlugin|ICommand|IResourceRuleFactory|ResourceAttributes|IStorage|IResourceChangeListener|IResourceChangeEvent|IResourceDelta)\b', UI + r'.resources.\1'),
    (r'org\.eclipse\.core\.runtime\.(IPath|Path|IAdaptable|Platform|IConfigurationElement|IExtensionRegistry|IExtension|IExtensionPoint|IContributor|InvalidRegistryObjectException|OperationCanceledException|MultiStatus)\b', UI + r'.runtime.\1'),
    (r'org\.eclipse\.core\.runtime\.jobs\.MultiRule\b', UI + '.runtime.MultiRule'),
    (r'org\.eclipse\.core\.commands\.operations\.', UI + '.commands.operations.'),
    (r'org\.eclipse\.core\.commands\.(AbstractHandler|ExecutionEvent|ExecutionException|IHandler|IHandler2|IHandlerListener|HandlerEvent|Command|NotHandledException|NotEnabledException|NotDefinedException)\b', UI + r'.commands.\1'),
    (r'org\.eclipse\.core\.expressions\.(IEvaluationContext|EvaluationContext)\b', UI + r'.commands.\1'),
    (r'org\.eclipse\.ui\.commands\.(IElementUpdater|ICommandService)\b', UI + r'.commands.\1'),
    (r'org\.eclipse\.ui\.menus\.UIElement\b', UI + '.commands.UIElement'),
    (r'org\.eclipse\.ui\.handlers\.(HandlerUtil|IHandlerService|IHandlerActivation)\b', UI + r'.commands.\1'),
    (r'org\.eclipse\.ui\.(PlatformUI|IWorkbench|IWorkbenchWindow|IWorkbenchPage|IViewPart|IEditorPart|IEditorInput|IEditorReference|IViewReference|IWorkbenchPartReference|IWorkbenchPartSite|IViewSite|IEditorSite|IActionBars|PartInitException|WorkbenchException|ISharedImages|ISources|IPropertyListener|IWorkbenchPropertyPage|IWorkbenchPreferencePage|IWorkbenchWizard|INewWizard|IImportWizard|IExportWizard)\b', UI + r'.workbench.\1'),
    (r'org\.eclipse\.ui\.part\.(ViewPart|EditorPart|WorkbenchPart)\b', UI + r'.workbench.\1'),
    (r'org\.eclipse\.ui\.operations\.IWorkbenchOperationSupport\b', UI + '.workbench.IWorkbenchOperationSupport'),
    (r'org\.eclipse\.ui\.help\.IWorkbenchHelpSystem\b', UI + '.workbench.IWorkbenchHelpSystem'),
    (r'org\.eclipse\.ui\.browser\.(IWebBrowser|IWorkbenchBrowserSupport)\b', UI + r'.workbench.\1'),
    (r'org\.eclipse\.ui\.themes\.(ITheme|IThemeManager)\b', UI + r'.workbench.\1'),
    (r'org\.eclipse\.ui\.model\.(IWorkbenchAdapter|WorkbenchLabelProvider|WorkbenchContentProvider|BaseWorkbenchContentProvider)\b', UI + r'.workbench.model.\1'),
    (r'org\.eclipse\.ui\.actions\.CompoundContributionItem\b', UI + '.workbench.CompoundContributionItem'),
    (r'org\.eclipse\.ui\.ide\.IDE\b', UI + '.ide.IDE'),
    (r'org\.eclipse\.ui\.navigator\.', UI + '.navigator.'),
    (r'org\.eclipse\.ui\.part\.(IShowInSource|ShowInContext|IPage|Page|IPageSite|PageSite|IContributedContentsView)\b', UI + r'.workbench.\1'),
    (r'org\.eclipse\.ui\.views\.contentoutline\.(ContentOutlinePage|IContentOutlinePage)\b', UI + r'.workbench.\1'),
    (r'org\.eclipse\.ui\.views\.properties\.(IPropertySheetPage|PropertySheet)\b', UI + r'.workbench.\1'),
    (r'org\.eclipse\.ui\.(IMemento|IPersistableElement|IStorageEditorInput|IContextService|IContextActivation|IActionDelegate)\b', UI + r'.workbench.\1'),
    (r'org\.eclipse\.ui\.contexts\.(IContextService|IContextActivation)\b', UI + r'.workbench.\1'),
    (r'org\.eclipse\.swt\.dnd\.(DND|DropTargetEvent|TransferData)\b', UI + r'.swt.dnd.\1'),
    (r'org\.eclipse\.jface\.util\.LocalSelectionTransfer\b', UI + '.jface.util.LocalSelectionTransfer'),
    (r'org\.eclipse\.core\.resources\.mapping\.', UI + '.resources.mapping.'),
    (r'org\.eclipse\.core\.runtime\.IAdapterFactory\b', UI + '.runtime.IAdapterFactory'),
    (r'org\.eclipse\.core\.resources\.(IncrementalProjectBuilder|IMarker)\b', UI + r'.resources.\1'),
    (r'org\.eclipse\.ui\.wizards\.(IWizardDescriptor|IWizardRegistry)\b', UI + r'.workbench.\1'),
    (r'org\.eclipse\.ui\.dialogs\.SaveAsDialog\b', UI + '.workbench.dialogs.SaveAsDialog'),
    (r'org\.eclipse\.ui\.menus\.(ExtensionContributionFactory|IContributionRoot)\b', UI + r'.workbench.menus.\1'),
    (r'org\.eclipse\.ui\.services\.IServiceLocator\b', UI + '.workbench.services.IServiceLocator'),
    (r'org\.eclipse\.core\.expressions\.(Expression|EvaluationResult)\b', UI + r'.commands.\1'),
    (r'org\.eclipse\.ui\.model\.(IWorkbenchAdapter2|WorkbenchAdapter)\b', UI + r'.workbench.model.\1'),
    (r'org\.eclipse\.ui\.dialogs\.(WizardNewFileCreationPage|ContainerSelectionDialog)\b', UI + r'.workbench.dialogs.\1'),
    (r'org\.eclipse\.ui\.dialogs\.ResourceSelectionDialog\b', UI + '.jface.dialogs.ResourceSelectionDialog'),
    (r'org\.eclipse\.ui\.(ISelectionService|ISelectionListener|IDecoratorManager|IEditorRegistry|IPerspectiveRegistry)\b', UI + r'.workbench.\1'),
    (r'org\.eclipse\.jface\.viewers\.(IDecoration|ILightweightLabelDecorator|IDecorationContext|Decoration)\b', UI + r'.jface.viewers.\1'),
    (r'org\.eclipse\.ui\.views\.properties\.tabbed\.ITypeMapper\b', UI + '.tabbed.ITypeMapper'),
    (r'org\.eclipse\.swt\.dnd\.(Clipboard|Transfer|TextTransfer|HTMLTransfer)\b', UI + r'.swt.dnd.\1'),
    (r'org\.eclipse\.swt\.browser\.(LocationListener|LocationAdapter|LocationEvent|ProgressListener|ProgressAdapter|ProgressEvent)\b', UI + r'.swt.browser.\1'),
    (r'com\.unitesk\.requality\.eclipse\.', PORT + '.eclipse.'),
    (r'com\.unitesk\.requality\.(marker|model|views)\.', PORT + r'.\1.'),
]


_STRING = re.compile(r'"(?:\\.|[^"\\\n])*"' + "|'(?:\\\\.|[^'\\\\\\n])+'")


def _rewrite_code(src, fn):
    """Applies fn to the code between string and char literals (literals keep ids such as view ids unchanged)."""
    out = []
    pos = 0
    for m in _STRING.finditer(src):
        out.append(fn(src[pos:m.start()]))
        out.append(m.group(0))
        pos = m.end()
    out.append(fn(src[pos:]))
    return ''.join(out)


def port_text(src):
    return _rewrite_code(src, _port_code)


def _port_code(src):
    out = src
    out = re.sub(r'^package com\.unitesk\.requality\.eclipse\b', 'package ' + PORT + '.eclipse', out, flags=re.M)
    out = re.sub(r'^package com\.unitesk\.requality\.(marker|model|views)\b', r'package ' + PORT + r'.\1', out, flags=re.M)
    out = re.sub(r'^package com\.unitesk\.requality\.(report|tests);', r'package ' + PORT + r'.\1;', out, flags=re.M)
    for k, v in LAYER_CLASSES.items():
        out = out.replace('import ' + k + ';', 'import ' + v + ';')
    for pat, rep in SIMPLE:
        out = re.sub(pat, rep, out)
    # Duplicate imports produced by the mapping.
    seen = set()
    lines = []
    for line in out.split('\n'):
        if line.startswith('import '):
            if line in seen:
                continue
            seen.add(line)
        lines.append(line)
    return '\n'.join(lines)


def target_path(rel):
    rel = rel.replace('\\', '/')
    if rel.startswith('com/unitesk/requality/eclipse/'):
        return os.path.join(OUT, 'com/unitesk/requality/vscode/port/eclipse', rel[len('com/unitesk/requality/eclipse/'):])
    m = re.match(r'com/unitesk/requality/(marker|model|views|report|tests)/(.*)', rel)
    if m:
        return os.path.join(OUT, 'com/unitesk/requality/vscode/port', m.group(1), m.group(2))
    raise SystemExit('not an Eclipse-layer class: ' + rel)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    force = '--force' in sys.argv
    if not args:
        print(__doc__)
        sys.exit(1)
    for rel in args:
        src_path = os.path.join(REF, rel)
        dst = target_path(rel)
        if os.path.exists(dst) and not force:
            print('exists (use --force): ' + os.path.relpath(dst, ROOT))
            continue
        with open(src_path, encoding='utf-8') as f:
            text = port_text(f.read())
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        with open(dst, 'w', encoding='utf-8') as f:
            f.write(text)
        rest = sorted(set(re.findall(r'^import (?:static )?(org\.eclipse\.[\w.]+);', text, flags=re.M)))
        print('ported ' + os.path.relpath(dst, ROOT))
        for r in rest:
            print('  TODO replace: ' + r)


if __name__ == '__main__':
    main()
