package com.unitesk.requality.vscode.ui.jface.fieldassist;

import java.util.HashMap;
import java.util.Map;

/** {@code org.eclipse.jface.fieldassist.FieldDecorationRegistry}. */
public class FieldDecorationRegistry {
  public static final String DEC_CONTENT_PROPOSAL = "DEC_CONTENT_PROPOSAL";
  public static final String DEC_REQUIRED = "DEC_REQUIRED";
  public static final String DEC_ERROR = "DEC_ERROR";
  public static final String DEC_WARNING = "DEC_WARNING";
  public static final String DEC_INFORMATION = "DEC_INFORMATION";
  public static final String DEC_ERROR_QUICKFIX = "DEC_ERRORQUICKFIX";
  private static final FieldDecorationRegistry DEFAULT = new FieldDecorationRegistry();
  private final Map<String, FieldDecoration> decorations = new HashMap<>();

  private FieldDecorationRegistry() {
    decorations.put(DEC_ERROR, new FieldDecoration(null, "", "error"));
    decorations.put(DEC_ERROR_QUICKFIX, new FieldDecoration(null, "", "error"));
    decorations.put(DEC_WARNING, new FieldDecoration(null, "", "warning"));
    decorations.put(DEC_INFORMATION, new FieldDecoration(null, "", "info"));
    decorations.put(DEC_REQUIRED, new FieldDecoration(null, "", "info"));
    decorations.put(DEC_CONTENT_PROPOSAL, new FieldDecoration(null, "", "info"));
  }

  public static FieldDecorationRegistry getDefault() {
    return DEFAULT;
  }

  public FieldDecoration getFieldDecoration(String id) {
    return decorations.get(id);
  }

  public void registerFieldDecoration(String id, String description, Object image) {
    decorations.put(id, new FieldDecoration(null, description, "info"));
  }
}
