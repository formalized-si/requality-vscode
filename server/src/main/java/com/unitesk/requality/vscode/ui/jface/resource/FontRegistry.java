package com.unitesk.requality.vscode.ui.jface.resource;

import java.util.HashMap;
import java.util.Map;

import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.graphics.Font;
import com.unitesk.requality.vscode.ui.swt.graphics.FontData;

/** {@code org.eclipse.jface.resource.FontRegistry}: symbolic fonts (the webview uses the editor font). */
public class FontRegistry {
  private final Map<String, FontData[]> fonts = new HashMap<>();

  public FontRegistry() {
  }

  public FontRegistry(Object display) {
  }

  public FontRegistry(Object display, boolean cleanOnDisplayDisposal) {
  }

  public synchronized void put(String symbolicName, FontData[] fontData) {
    fonts.put(symbolicName, fontData);
  }

  public synchronized boolean hasValueFor(String symbolicName) {
    return fonts.containsKey(symbolicName);
  }

  public synchronized FontData[] getFontData(String symbolicName) {
    FontData[] d = fonts.get(symbolicName);
    return d != null ? d : new FontData[] { new FontData("", 10, SWT.NORMAL) };
  }

  public Font get(String symbolicName) {
    return new Font(null, getFontData(symbolicName));
  }

  public Font getBold(String symbolicName) {
    FontData d = getFontData(symbolicName)[0];
    return new Font(null, d.getName(), d.getHeight(), SWT.BOLD);
  }

  public Font getItalic(String symbolicName) {
    FontData d = getFontData(symbolicName)[0];
    return new Font(null, d.getName(), d.getHeight(), SWT.ITALIC);
  }
}
