package com.unitesk.requality.vscode.ui.compare;

import java.io.File;
import java.text.MessageFormat;

import com.unitesk.requality.vscode.ui.commands.AbstractHandler;
import com.unitesk.requality.vscode.ui.commands.ExecutionEvent;
import com.unitesk.requality.vscode.ui.commands.ExecutionException;
import com.unitesk.requality.vscode.ui.commands.HandlerUtil;
import com.unitesk.requality.vscode.ui.jface.dialogs.IDialogConstants;
import com.unitesk.requality.vscode.ui.jface.dialogs.MessageDialog;
import com.unitesk.requality.vscode.ui.jface.viewers.ISelection;
import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.swt.widgets.Shell;
import com.unitesk.requality.vscode.ui.team.LocalResourceTypedElement;
import com.unitesk.requality.vscode.ui.workbench.IWorkbenchPage;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.Differencer;

/**
 * {@code org.eclipse.compare.internal.CompareWithOtherResourceHandler}: the handler of the command
 * {@code org.eclipse.compare.compareWithOther} ("Compare With Other Resource"): the dialog chooses the sides of the
 * compare, which are compared as the resources of "Compare With Each Other" (external files and folders: as their
 * structure, two-way or three-way).
 */
public class CompareWithOtherResourceHandler extends AbstractHandler {
  @Override
  public Object execute(ExecutionEvent event) throws ExecutionException {
    ISelection selection = HandlerUtil.getCurrentSelection(event);
    IWorkbenchPage workbenchPage = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();
    // CompareAction#isEnabled(ISelection)
    CompareConfiguration cc = new CompareConfiguration();
    cc.setProperty(CompareEditor.CONFIRM_SAVE_PROPERTY, Boolean.FALSE);
    ResourceCompareInput input = new ResourceCompareInput(cc);
    int selectionSize = 0;
    if (selection instanceof IStructuredSelection)
      selectionSize = ((IStructuredSelection) selection).toArray().length;
    if (!input.isEnabled(selection) && selectionSize != 1)
      return null;
    Shell shell = workbenchPage.getWorkbenchWindow().getShell();
    CompareWithOtherResourceDialog dialog = new CompareWithOtherResourceDialog(shell, selection);
    if (dialog.open() != IDialogConstants.OK_ID)
      return null;
    Object[] sides = dialog.getResult();
    if (allResources(sides)) {
      // CompareAction#run(ISelection)
      IResource[] resources = new IResource[sides.length];
      for (int i = 0; i < sides.length; i++)
        resources[i] = (IResource) sides[i];
      if (!input.setResources(resources))
        return null;
      input.initializeCompareConfiguration();
      CompareUI.openCompareEditorOnPage(input, workbenchPage);
    } else {
      openExternal(shell, sides);
    }
    return null;
  }

  private static boolean allResources(Object[] sides) {
    for (Object side : sides)
      if (!(side instanceof IResource))
        return false;
    return true;
  }

  /**
   * Compares the sides when one is an external file or folder: two-way, or three-way with the first side as the common
   * ancestor, as the resources of ResourceCompareInput (VSCODE: see ExternalFileTypedElement).
   */
  private static void openExternal(Shell shell, Object[] sides) {
    boolean threeWay = sides.length == 3;
    ITypedElement ancestor = threeWay ? typed(sides[0]) : null;
    ITypedElement left = typed(sides[threeWay ? 1 : 0]);
    ITypedElement right = typed(sides[threeWay ? 2 : 1]);
    String title = threeWay
        ? MessageFormat.format("Compare (''{0}'' - ''{1}'' - ''{2}'')", ancestor.getName(), left.getName(), right.getName())
        : MessageFormat.format("Compare (''{0}'' - ''{1}'')", left.getName(), right.getName());
    CompareConfiguration config = new CompareConfiguration();
    if (threeWay)
      config.setAncestorLabel(label(sides[0]));
    config.setLeftLabel(label(sides[threeWay ? 1 : 0]));
    config.setRightLabel(label(sides[threeWay ? 2 : 1]));
    config.setLeftEditable(left instanceof LocalResourceTypedElement);
    config.setRightEditable(right instanceof LocalResourceTypedElement);
    CompareEditorInput input = new CompareEditorInput(config) {
      @Override
      protected Object prepareInput(IProgressMonitor monitor) {
        return new Differencer().findDifferences(threeWay, monitor, null, ancestor, left, right);
      }
    };
    input.setTitle(title);
    CompareUI.openCompareEditor(input);
  }

  private static ITypedElement typed(Object side) {
    if (side instanceof File)
      return new ExternalFileTypedElement((File) side);
    if (side instanceof IFile)
      return new LocalResourceTypedElement((IFile) side);
    return new LocalResourceTypedElement((IResource) side);
  }

  private static String label(Object side) {
    if (side instanceof File)
      return ((File) side).getAbsolutePath();
    return ((IResource) side).getFullPath().toOSString();
  }
}
