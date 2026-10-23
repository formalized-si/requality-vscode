package com.unitesk.requality.vscode.ui.compare;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The contents shown by the read-only documents of the {@code requality-compare} scheme in VS Code editors (the sides of
 * diffs that are not workspace files: local history states, revisions, the clipboard). The extension reads them with
 * {@code compare/content}; the most recent ones are kept.
 */
public final class CompareContents {
  private static final int MAX = 200;
  private static final Map<String, String[]> CONTENTS = new LinkedHashMap<String, String[]>(16, 0.75f, true) {
    private static final long serialVersionUID = 1L;

    @Override
    protected boolean removeEldestEntry(Map.Entry<String, String[]> eldest) {
      return size() > MAX;
    }
  };

  private CompareContents() {
  }

  /** Registers a text; the id of its document. */
  public static synchronized String register(String name, String text) {
    String id = UUID.randomUUID().toString();
    CONTENTS.put(id, new String[] { name, text });
    return id;
  }

  /** The text of a document, or null when it is no longer kept. */
  public static synchronized String get(String id) {
    String[] c = CONTENTS.get(id);
    return c == null ? null : c[1];
  }
}
