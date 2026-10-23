package com.unitesk.requality.vscode.ui.runtime;

import java.io.File;

/** {@code org.eclipse.core.runtime.IPath}. */
public interface IPath extends Cloneable {
  char SEPARATOR = '/';
  char DEVICE_SEPARATOR = ':';

  IPath addFileExtension(String extension);

  IPath addTrailingSeparator();

  IPath append(String path);

  IPath append(IPath path);

  String getDevice();

  String getFileExtension();

  boolean hasTrailingSeparator();

  boolean isAbsolute();

  boolean isEmpty();

  boolean isPrefixOf(IPath anotherPath);

  boolean isRoot();

  boolean isUNC();

  boolean isValidPath(String path);

  boolean isValidSegment(String segment);

  String lastSegment();

  IPath makeAbsolute();

  IPath makeRelative();

  IPath makeRelativeTo(IPath base);

  IPath makeUNC(boolean toUNC);

  int matchingFirstSegments(IPath anotherPath);

  IPath removeFileExtension();

  IPath removeFirstSegments(int count);

  IPath removeLastSegments(int count);

  IPath removeTrailingSeparator();

  String segment(int index);

  int segmentCount();

  String[] segments();

  IPath setDevice(String device);

  File toFile();

  String toOSString();

  String toPortableString();

  IPath uptoSegment(int count);
}
