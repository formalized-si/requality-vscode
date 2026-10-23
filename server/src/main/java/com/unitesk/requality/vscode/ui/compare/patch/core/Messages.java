package com.unitesk.requality.vscode.ui.compare.patch.core;

/**
 * {@code org.eclipse.compare.internal.core.Messages}: the English texts of the patch core messages (the messages.properties
 * of org.eclipse.compare.core, as literals) and the {@code NLS.bind} substitution of their {0}, {1}... placeholders.
 */
final class Messages {
  static final String Activator_1 = "Internal error";
  static final String FileDiffResult_0 = "(file already exists)";
  static final String FileDiffResult_1 = "(file does not exist)";
  static final String FileDiffResult_2 = "{0} {1}";
  static final String FileDiffResult_3 = "{0} (hunk \\#{1})";

  private Messages() {
  }

  /** {@code Messages.bind(pattern, args)}: the arguments replace the {n} placeholders of the pattern. */
  static String bind(String pattern, Object... args) {
    String s = pattern;
    for (int i = 0; i < args.length; i++)
      s = s.replace("{" + i + "}", String.valueOf(args[i]));
    return s;
  }
}
