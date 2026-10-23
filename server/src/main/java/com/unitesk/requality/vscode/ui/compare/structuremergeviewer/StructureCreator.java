package com.unitesk.requality.vscode.ui.compare.structuremergeviewer;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.compare.CompareBundles;
import com.unitesk.requality.vscode.ui.compare.ITypedElement;
import com.unitesk.requality.vscode.ui.compare.patch.LineReader;
import com.unitesk.requality.vscode.ui.jface.text.Document;
import com.unitesk.requality.vscode.ui.jface.text.IDocument;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;

/**
 * {@code org.eclipse.compare.structuremergeviewer.StructureCreator}: the base of the structure creators that parse the
 * text of an input into a structure of {@link IStructureComparator}s.
 * VSCODE: there are no shared documents of text editors and no document partitioners: the structure is always created
 * from the text of the input, and the nodes are not saved back into a document (there is no document range node).
 */
public abstract class StructureCreator implements IStructureCreator2 {
  @Override
  public IStructureComparator getStructure(Object input) {
    if (!(input instanceof ITypedElement element))
      return null;
    String contents;
    try {
      contents = CompareBundles.readString(element);
    } catch (CoreException e) {
      RLogger.logError(e);
      return null;
    }
    IDocument doc = new Document(contents);
    setupDocument(doc);
    try {
      return createStructureComparator(input, doc, null);
    } catch (CoreException e) {
      RLogger.logError(e);
      return null;
    }
  }

  @Override
  public IStructureComparator createStructure(Object element, IProgressMonitor monitor) throws CoreException {
    return getStructure(element);
  }

  /** Creates the structure of the element from its document. */
  protected abstract IStructureComparator createStructureComparator(Object element, IDocument document,
      IProgressMonitor monitor) throws CoreException;

  /** Hook for a document partitioner (none is ported). */
  protected void setupDocument(IDocument document) {
  }

  @Override
  public void save(IStructureComparator node, Object input) {
    // VSCODE: the nodes of the structure are not bound to a document range (see the class comment)
  }

  @Override
  public ITypedElement createElement(Object element, Object input, IProgressMonitor monitor) throws CoreException {
    String[] path = getPath(element, input);
    if (path == null) {
      IStructureComparator locate = locate(element, input);
      return locate instanceof ITypedElement t ? t : null;
    }
    IStructureComparator structure = createStructure(input, monitor);
    if (structure == null)
      return null;
    return findElement(structure, path);
  }

  @Override
  public IStructureComparator locate(Object element, Object input) {
    String[] path = getPath(element, input);
    if (path == null)
      return null;
    IStructureComparator structure = getStructure(input);
    if (structure == null)
      return null;
    return (IStructureComparator) findElement(structure, path);
  }

  protected ITypedElement findElement(IStructureComparator structure, String[] path) {
    return (ITypedElement) find(structure, path, 0);
  }

  private IStructureComparator find(IStructureComparator tree, String[] path, int index) {
    if (tree == null || tree.getChildren() == null)
      return null;
    for (Object c : tree.getChildren()) {
      if (c instanceof ITypedElement child && child.getName().equals(path[index])) {
        if (index == path.length - 1)
          return (IStructureComparator) c;
        IStructureComparator result = find((IStructureComparator) c, path, index + 1);
        if (result != null)
          return result;
      }
    }
    return null;
  }

  /** The path of the element in the structure, or null when it cannot be located by a path. */
  protected String[] getPath(Object element, Object input) {
    return null;
  }

  @Override
  public void destroy(Object object) {
  }

  /** Whether the contents of the two nodes are equal, ignoring the white space when asked. */
  public boolean contentsEquals(Object node1, char contributor1, Object node2, char contributor2,
      boolean ignoreWhitespace) {
    List<String> lines1 = readLines(getContents(node1, false));
    List<String> lines2 = readLines(getContents(node2, false));
    StringBuilder buffer1 = new StringBuilder();
    StringBuilder buffer2 = new StringBuilder();
    int maxLines = Math.max(lines1.size(), lines2.size());
    for (int i = 0; i < maxLines; i++) {
      buffer1.append(lines1.size() > i ? lines1.get(i) : "");
      buffer2.append(lines2.size() > i ? lines2.get(i) : "");
    }
    if (ignoreWhitespace)
      return withoutWhitespace(buffer1).equals(withoutWhitespace(buffer2));
    return buffer1.toString().equals(buffer2.toString());
  }

  private static List<String> readLines(String text) {
    return LineReader.readLines(new BufferedReader(new StringReader(text == null ? "" : text)));
  }

  private static String withoutWhitespace(CharSequence s) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < s.length(); i++)
      if (!Character.isWhitespace(s.charAt(i)))
        b.append(s.charAt(i));
    return b.toString();
  }
}
