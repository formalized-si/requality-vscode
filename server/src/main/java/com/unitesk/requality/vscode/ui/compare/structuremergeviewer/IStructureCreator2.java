package com.unitesk.requality.vscode.ui.compare.structuremergeviewer;

import com.unitesk.requality.vscode.ui.compare.ITypedElement;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;

/**
 * {@code org.eclipse.compare.structuremergeviewer.IStructureCreator2}: a structure creator that builds the structure
 * with a progress monitor and releases it when the viewer does not need it anymore.
 */
public interface IStructureCreator2 extends IStructureCreator {
  IStructureComparator createStructure(Object input, IProgressMonitor monitor) throws CoreException;

  ITypedElement createElement(Object element, Object input, IProgressMonitor monitor) throws CoreException;

  void destroy(Object object);
}
