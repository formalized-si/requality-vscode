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
 * processes, network, reflection or class loading. The list names the classes a script may use (all others are refused).
 */
final class ScriptSandbox {
  private ScriptSandbox() {
  }

  /** The classes of java.lang a script may use. */
  private static final Set<String> LANG = Set.of("String", "Boolean", "Character", "Byte", "Short", "Integer", "Long",
      "Float", "Double", "Number", "Math", "StringBuilder", "StringBuffer", "Object", "Comparable", "CharSequence",
      "Iterable", "Enum");

  /**
   * The classes of java.util a script may use: collections, dates and helpers (not Formatter or Scanner, which open
   * files, nor ServiceLoader, ResourceBundle, Timer or Properties, nor Locale or TimeZone, whose defaults are the ones of
   * the process). A nested class is judged by its outer class.
   */
  private static final Set<String> UTIL = Set.of("AbstractCollection", "AbstractList", "AbstractMap", "AbstractQueue",
      "AbstractSequentialList", "AbstractSet", "ArrayDeque", "ArrayList", "Arrays", "BitSet", "Calendar", "Collection",
      "Collections", "Comparator", "Date", "Deque", "Dictionary", "EnumMap", "EnumSet", "Enumeration", "GregorianCalendar",
      "HashMap", "HashSet", "Hashtable", "IdentityHashMap", "ImmutableCollections", "Iterator", "LinkedHashMap",
      "LinkedHashSet", "LinkedList", "List", "ListIterator", "Map", "NavigableMap", "NavigableSet",
      "NoSuchElementException", "Objects", "Optional", "OptionalDouble", "OptionalInt", "OptionalLong", "PriorityQueue",
      "Queue", "RandomAccess", "Set", "SortedMap", "SortedSet", "Stack", "StringJoiner", "TreeMap", "TreeSet",
      "UUID", "Vector", "WeakHashMap");

  /**
   * The packages of Requality whose classes a script may use: the elements of the checker rules (core.script); the
   * scripts get the values of the attributes as strings, numbers, lists and elements, not the nodes of the tree.
   */
  private static final String[] REQUALITY = { "com.unitesk.requality.core.script." };

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
    // the listed collections and dates of java.util (not its subpackages: logging writes files, concurrent makes threads)
    if (c.startsWith("java.util.") && c.indexOf('.', 10) < 0)
      return UTIL.contains(c.substring(10));
    if (c.startsWith("java.util.regex.") || c.startsWith("java.text.") || c.startsWith("java.time.") && !c.startsWith("java.time.zone."))
      return true;
    for (String p : REQUALITY)
      if (c.startsWith(p))
        return true;
    return false;
  }
}
