package com.unitesk.requality.vscode.ui.compare.structuremergeviewer;

/**
 * {@code org.eclipse.compare.structuremergeviewer.IStructureCreator}: creates the structure of an input (an archive, a
 * document) that the structure compare ({@link StructureDiffViewer}) shows as a tree of differences.
 */
public interface IStructureCreator {
  /** The name of the structure (the title of the structure viewer). */
  String getName();

  /** The structure of the input, or null when it cannot be parsed. */
  IStructureComparator getStructure(Object input);

  /** The element of the structure found by a path (see {@link #locate}); null when there is none. */
  IStructureComparator locate(Object path, Object input);

  /** The text compared for a node of the structure. */
  String getContents(Object node, boolean ignoreWhitespace);

  /** Saves the structure into the input (not supported by read-only structures such as archives). */
  void save(IStructureComparator node, Object input);
}
