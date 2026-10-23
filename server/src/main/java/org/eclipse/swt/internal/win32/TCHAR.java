package org.eclipse.swt.internal.win32;

/**
 * {@code org.eclipse.swt.internal.win32.TCHAR}: a string for the Win32 API of SWT. The Requality plug-in reaches this
 * internal SWT class by name (Activator.start reads the registry through {@code Class.forName}), so the server provides
 * it under the SWT name; it only keeps the string (see {@link OS}).
 */
public class TCHAR {
  final String string;

  public TCHAR(int codePage, String string, boolean terminate) {
    this.string = string;
  }

  @Override
  public String toString() {
    return string;
  }
}
