package com.unitesk.requality.vscode.mcp;

import java.util.Set;

import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;

/**
 * The JavaScript of a catalog (attribute formulas, checker rules, the formulas of report settings) runs in Rhino, which
 * lets a script use any Java class: a formula could run a program or write a file. The MCP server lets an agent write
 * such formulas, and an agent follows the text of the documents it reads. So the scripts of the server see only the
 * classes of values (strings, numbers, collections, dates, text formats) and of the tree of Requality: no files,
 * processes, network, reflection or class loading.
 */
final class ScriptSandbox {
  private ScriptSandbox() {
  }

  /** The classes of java.lang a script may use. */
  private static final Set<String> LANG = Set.of("String", "Boolean", "Character", "Byte", "Short", "Integer", "Long",
      "Float", "Double", "Number", "Math", "StringBuilder", "StringBuffer", "Object", "Comparable", "CharSequence",
      "Iterable", "Enum");

  /** The packages of Requality whose classes a script may use: the tree, the node types and the attributes. */
  private static final String[] REQUALITY = { "com.unitesk.requality.core.model.", "com.unitesk.requality.nodetype.",
      "com.unitesk.requality.core.attribute.", "com.unitesk.requality.core.search." };

  private static boolean installed;

  /** Installs the sandbox for every Rhino context of the process (before the first script runs). */
  static synchronized void install() {
    if (installed)
      return;
    installed = true;
    ContextFactory.initGlobal(new ContextFactory() {
      @Override
      protected Context makeContext() {
        Context cx = super.makeContext();
        cx.setClassShutter(SHUTTER);
        return cx;
      }
    });
  }

  static final ClassShutter SHUTTER = ScriptSandbox::visible;

  /** True when a script may use the class of this name. */
  static boolean visible(String name) {
    // a nested class is judged by its outer class
    int nested = name.indexOf('$');
    String c = nested < 0 ? name : name.substring(0, nested);
    if (c.startsWith("java.lang.") && c.indexOf('.', 10) < 0)
      return LANG.contains(c.substring(10));
    if (c.equals("java.math.BigDecimal") || c.equals("java.math.BigInteger"))
      return true;
    // the collections and dates of java.util (not its subpackages: logging writes files, concurrent makes threads)
    if (c.startsWith("java.util.") && c.indexOf('.', 10) < 0)
      return !c.equals("java.util.ServiceLoader") && !c.equals("java.util.Properties");
    if (c.startsWith("java.util.regex.") || c.startsWith("java.text.") || c.startsWith("java.time."))
      return true;
    // a document copies its images to any folder (duplicateImages)
    if (c.equals("com.unitesk.requality.nodetype.Document"))
      return false;
    for (String p : REQUALITY)
      if (c.startsWith(p))
        return true;
    return false;
  }
}
