package com.unitesk.requality.vscode.ui.team.synchronize;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;

import com.unitesk.requality.vscode.ui.compare.CompareConfiguration;
import com.unitesk.requality.vscode.ui.compare.CompareEditorInput;
import com.unitesk.requality.vscode.ui.compare.IEditableContent;
import com.unitesk.requality.vscode.ui.compare.ITypedElement;
import com.unitesk.requality.vscode.ui.compare.IResourceProvider;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.DiffNode;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.Differencer;
import com.unitesk.requality.vscode.ui.compare.structuremergeviewer.ICompareInput;
import com.unitesk.requality.vscode.ui.resources.IFile;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.team.LocalResourceTypedElement;

/**
 * {@code org.eclipse.team.internal.ui.synchronize.SaveablesCompareEditorInput}: the compare editor input of two or three
 * files of the workspace. The local files are the sides of the compare, their changed contents are written when the
 * input is saved. VSCODE: the Eclipse saveables of the workbench (the editor's dirty state per side) are not ported; the
 * changes of a side are written by {@link #saveChanges(IProgressMonitor)}.
 */
public class SaveablesCompareEditorInput extends CompareEditorInput {
  private final ITypedElement fAncestorElement;
  private final ITypedElement fLeftElement;
  private final ITypedElement fRightElement;

  /** A compare input with the ancestor (null for a two-way compare) and the left and right sides. */
  public SaveablesCompareEditorInput(ITypedElement ancestor, ITypedElement left, ITypedElement right) {
    super(new CompareConfiguration());
    this.fAncestorElement = ancestor;
    this.fLeftElement = left;
    this.fRightElement = right;
  }

  /** {@code SaveablesCompareEditorInput.createFileElement}: a typed element of the local file. */
  public static ITypedElement createFileElement(IFile file) {
    return new LocalFileElement(file);
  }

  /** The labels of the sides: the workspace paths of the files (or the names of the other elements). */
  private String[] getLabels() {
    IResource leftResource = getResource(fLeftElement);
    IResource rightResource = getResource(fRightElement);
    if (leftResource != null && rightResource != null) {
      String leftLabel = leftResource.getFullPath().makeRelative().toString();
      String rightLabel = rightResource.getFullPath().makeRelative().toString();
      if (fAncestorElement != null) {
        IResource ancestorResource = getResource(fAncestorElement);
        if (ancestorResource != null) {
          // VSCODE: as Eclipse, the label of the ancestor is the one of the right resource
          String ancestorLabel = rightResource.getFullPath().makeRelative().toString();
          return new String[] { ancestorLabel, leftLabel, rightLabel };
        }
      }
      return new String[] { leftLabel, rightLabel };
    }
    if (fAncestorElement != null)
      return new String[] { fAncestorElement.getName(), fLeftElement.getName(), fRightElement.getName() };
    return new String[] { fLeftElement.getName(), fRightElement.getName() };
  }

  @Override
  public String getToolTipText() {
    String[] labels = getLabels();
    if (labels.length == 3)
      return MessageFormat.format("Three-way compare of ''{1}'' and ''{2}'' relative to common ancestor ''{0}''",
          (Object[]) labels);
    return MessageFormat.format("Two-way compare of ''{0}'' with ''{1}''", (Object[]) labels);
  }

  @Override
  public String getTitle() {
    String[] labels = getLabels();
    if (labels.length == 3)
      return MessageFormat.format("Compare (''{0}'' - ''{1}'' - ''{2}'')", (Object[]) labels);
    return MessageFormat.format("Compare (''{0}'' - ''{1}'')", (Object[]) labels);
  }

  /** The compare input of the sides: a difference node of the ancestor, left and right elements. */
  @Override
  protected Object prepareInput(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
    ICompareInput input = fAncestorElement == null
        ? new DiffNode(null, Differencer.CHANGE, null, fLeftElement, fRightElement)
        : new DiffNode(null, Differencer.CONFLICTING, fAncestorElement, fLeftElement, fRightElement);
    getCompareConfiguration().setLeftEditable(isEditable(fLeftElement));
    getCompareConfiguration().setRightEditable(isEditable(fRightElement));
    initLabels();
    return input;
  }

  private static boolean isEditable(Object obj) {
    return obj instanceof IEditableContent && ((IEditableContent) obj).isEditable();
  }

  private void initLabels() {
    CompareConfiguration cc = getCompareConfiguration();
    IResource ancestorResource = getResource(fAncestorElement);
    IResource leftResource = getResource(fLeftElement);
    IResource rightResource = getResource(fRightElement);
    if (ancestorResource != null)
      cc.setAncestorLabel(ancestorResource.getFullPath().makeRelative().toString());
    if (leftResource != null && rightResource != null) {
      cc.setLeftLabel(leftResource.getFullPath().makeRelative().toString());
      cc.setRightLabel(rightResource.getFullPath().makeRelative().toString());
    }
  }

  private static IResource getResource(ITypedElement element) {
    if (element instanceof LocalResourceTypedElement && element instanceof IResourceProvider)
      return ((IResourceProvider) element).getResource();
    return null;
  }

  /** Writes the changed contents of the local sides to their files. */
  @Override
  public void saveChanges(IProgressMonitor monitor) throws CoreException {
    super.saveChanges(monitor);
    commit(fLeftElement, monitor);
    commit(fRightElement, monitor);
  }

  private static void commit(ITypedElement element, IProgressMonitor monitor) throws CoreException {
    if (element instanceof LocalFileElement) {
      ((LocalFileElement) element).commit(monitor);
    }
  }

  /** The typed element of a local file: its buffered contents are written to the file when saved. */
  private static final class LocalFileElement extends LocalResourceTypedElement {
    private boolean dirty;

    LocalFileElement(IFile file) {
      super(file);
    }

    @Override
    public void setContent(byte[] contents) {
      dirty = true;
      super.setContent(contents);
    }

    void commit(IProgressMonitor monitor) throws CoreException {
      if (!dirty)
        return;
      byte[] bytes = getContent();
      if (bytes != null && getResource() instanceof IFile)
        ((IFile) getResource()).setContents(new ByteArrayInputStream(bytes), IResource.KEEP_HISTORY, monitor);
      discardBuffer();
      dirty = false;
    }
  }
}
