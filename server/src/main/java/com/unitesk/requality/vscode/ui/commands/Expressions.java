package com.unitesk.requality.vscode.ui.commands;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.unitesk.requality.vscode.ui.jface.viewers.IStructuredSelection;
import com.unitesk.requality.vscode.ui.resources.IProject;
import com.unitesk.requality.vscode.ui.resources.IResource;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.IAdaptable;
import com.unitesk.requality.vscode.ui.runtime.IConfigurationElement;

/**
 * The core expressions of plugin.xml ({@code org.eclipse.core.expressions}): with, iterate, count, instanceof,
 * adapt, test, equals, and, or, not. Class names of the Eclipse layer resolve to their ports.
 */
public final class Expressions {
  private Expressions() {
  }

  /** Source priority of the variables an expression reads (ISources); higher wins between active handlers. */
  public static int priority(IConfigurationElement e) {
    if (e == null)
      return 0;
    int p = 0;
    if ("with".equals(e.getName()))
      p |= variablePriority(e.getAttribute("variable"));
    for (IConfigurationElement c : e.getChildren())
      p |= priority(c);
    return p;
  }

  private static int variablePriority(String v) {
    if (v == null)
      return 0;
    switch (v) {
      case "selection":
      case "activeMenuSelection":
        return 1 << 30;
      case "activeFocusControl":
      case "activeFocusControlId":
        return 1 << 23;
      case "activeEditorInput":
        return 1 << 21;
      case "activeSite":
        return 1 << 20;
      case "activePart":
        return 1 << 18;
      case "activePartId":
        return 1 << 17;
      case "activeEditor":
        return 1 << 16;
      case "activeEditorId":
        return 1 << 15;
      case "activeWorkbenchWindow":
        return 1 << 12;
      case "activeShell":
        return 1 << 10;
      default:
        return 1 << 3;
    }
  }

  /** Evaluates the children of {@code container} (activeWhen, enabledWhen, visibleWhen) as an AND. */
  public static boolean evaluateContainer(IConfigurationElement container, IEvaluationContext ctx) {
    if (container == null)
      return true;
    Object def = ctx.getDefaultVariable();
    for (IConfigurationElement c : container.getChildren())
      if (!evaluate(c, ctx, def))
        return false;
    return true;
  }

  private static boolean all(IConfigurationElement e, IEvaluationContext ctx, Object value) {
    for (IConfigurationElement c : e.getChildren())
      if (!evaluate(c, ctx, value))
        return false;
    return true;
  }

  public static boolean evaluate(IConfigurationElement e, IEvaluationContext ctx, Object value) {
    switch (e.getName()) {
      case "and":
        return all(e, ctx, value);
      case "or": {
        // CompositeExpression.evaluateOr: an empty "or" is true (the navigator's way to write "always")
        IConfigurationElement[] ch = e.getChildren();
        if (ch.length == 0)
          return true;
        for (IConfigurationElement c : ch)
          if (evaluate(c, ctx, value))
            return true;
        return false;
      }
      case "not": {
        IConfigurationElement[] ch = e.getChildren();
        return ch.length > 0 && !evaluate(ch[0], ctx, value);
      }
      case "with": {
        Object v = ctx.getVariable(e.getAttribute("variable"));
        if (v == null || v == IEvaluationContext.UNDEFINED_VARIABLE)
          return false;
        return all(e, ctx, v);
      }
      case "iterate":
        return iterate(e, ctx, value);
      case "count":
        return count(e.getAttribute("value"), elements(value).size());
      case "instanceof":
        return isInstance(value, e.getAttribute("value"));
      case "objectClass":
        // Legacy (object contribution) expression: the element is an instance of the class.
        return isInstance(value, e.getAttribute("name"));
      case "adapt": {
        Object adapted = adapt(value, e.getAttribute("type"));
        return adapted != null && all(e, ctx, adapted);
      }
      case "equals":
        return value != null && String.valueOf(value).equals(e.getAttribute("value"));
      case "test":
        return test(e, value);
      case "systemTest":
        return String.valueOf(e.getAttribute("value")).equals(System.getProperty(e.getAttribute("property")));
      case "reference":
        return true;
      default:
        return false;
    }
  }

  private static boolean iterate(IConfigurationElement e, IEvaluationContext ctx, Object value) {
    List<Object> items = elements(value);
    String op = e.getAttribute("operator");
    boolean and = op == null || "and".equals(op);
    if (items.isEmpty()) {
      String ifEmpty = e.getAttribute("ifEmpty");
      return ifEmpty != null ? Boolean.parseBoolean(ifEmpty) : and;
    }
    for (Object o : items) {
      boolean r = all(e, ctx, o);
      if (and && !r)
        return false;
      if (!and && r)
        return true;
    }
    return and;
  }

  @SuppressWarnings("rawtypes")
  public static List<Object> elements(Object value) {
    if (value instanceof IStructuredSelection) {
      List<Object> r = new ArrayList<>();
      for (Iterator it = ((IStructuredSelection) value).iterator(); it.hasNext();)
        r.add(it.next());
      return r;
    }
    if (value instanceof Collection)
      return new ArrayList<Object>((Collection) value);
    if (value instanceof Object[]) {
      List<Object> r = new ArrayList<>();
      Collections.addAll(r, (Object[]) value);
      return r;
    }
    return value == null ? new ArrayList<>() : new ArrayList<>(Collections.singletonList(value));
  }

  static boolean count(String spec, int size) {
    if (spec == null)
      return true;
    switch (spec) {
      case "*":
        return true;
      case "?":
        return size <= 1;
      case "!":
        return size == 0;
      case "+":
        return size >= 1;
      default:
        break;
    }
    // CountExpression: "-N)" is fewer than N, "(N-" is more than N, otherwise an exact number.
    try {
      if (spec.startsWith("-") && spec.endsWith(")"))
        return size < Integer.parseInt(spec.substring(1, spec.length() - 1).trim());
      if (spec.startsWith("(") && spec.endsWith("-"))
        return size > Integer.parseInt(spec.substring(1, spec.length() - 1).trim());
    } catch (NumberFormatException ex) {
      return false;
    }
    try {
      return size == Integer.parseInt(spec.trim());
    } catch (NumberFormatException ex) {
      return false;
    }
  }

  private static Class<?> classFor(String name) {
    if (name == null)
      return null;
    try {
      return Class.forName(ExtensionRegistry.portedClassName(name));
    } catch (ClassNotFoundException | LinkageError e) {
      return null;
    }
  }

  /** instanceof by type name, including supertypes not loadable here (compared by name). */
  public static boolean isInstance(Object value, String type) {
    if (value == null || type == null)
      return false;
    Class<?> c = classFor(type);
    if (c != null)
      return c.isInstance(value);
    String ported = ExtensionRegistry.portedClassName(type);
    for (Class<?> k = value.getClass(); k != null; k = k.getSuperclass()) {
      if (k.getName().equals(type) || k.getName().equals(ported))
        return true;
      for (Class<?> i : k.getInterfaces())
        if (i.getName().equals(type) || i.getName().equals(ported))
          return true;
    }
    return false;
  }

  private static Object adapt(Object value, String type) {
    if (isInstance(value, type))
      return value;
    Class<?> c = classFor(type);
    if (c != null && value instanceof IAdaptable)
      return ((IAdaptable) value).getAdapter(c);
    return null;
  }

  private static boolean test(IConfigurationElement e, Object value) {
    String property = e.getAttribute("property");
    String expected = e.getAttribute("value");
    if ("org.eclipse.core.resources.projectNature".equals(property)) {
      IProject p = value instanceof IResource ? ((IResource) value).getProject() : null;
      try {
        return p != null && p.isOpen() && p.hasNature(expected);
      } catch (CoreException ex) {
        return false;
      }
    }
    // ResourceMappingPropertyTester of org.eclipse.core.resources: a persistent property of the projects of a mapping.
    if ("org.eclipse.core.resources.projectPersistentProperty".equals(property)
        && value instanceof com.unitesk.requality.vscode.ui.resources.mapping.ResourceMapping)
      return projectPersistentProperty((com.unitesk.requality.vscode.ui.resources.mapping.ResourceMapping) value,
          e.getAttribute("args"), expected);
    // ProjectPropertyTester and ResourcePropertyTester of org.eclipse.core.resources.
    if ("org.eclipse.core.resources.open".equals(property))
      return value instanceof IProject && ((IProject) value).isOpen() == toBoolean(expected);
    if (value instanceof IResource) {
      IResource r = (IResource) value;
      switch (String.valueOf(property)) {
        case "org.eclipse.core.resources.name":
          return matchesPattern(r.getName(), expected);
        case "org.eclipse.core.resources.path":
          return matchesPattern(r.getFullPath().toString(), expected);
        case "org.eclipse.core.resources.extension":
          return r.getFileExtension() != null && matchesPattern(r.getFileExtension(), expected);
        case "org.eclipse.core.resources.readOnly":
          return r.isReadOnly() == toBoolean(expected);
        default:
          return false;
      }
    }
    return false;
  }

  /**
   * {@code ResourceMappingPropertyTester}: whether a project of the mapping has the persistent property (args: the
   * qualified name, optionally the expected value); every project that has it must have the expected value.
   */
  private static boolean projectPersistentProperty(com.unitesk.requality.vscode.ui.resources.mapping.ResourceMapping mapping,
      String args, String expectedValue) {
    IProject[] projects = mapping.getProjects();
    if (projects == null || projects.length == 0)
      return false;
    String[] a = args == null || args.isEmpty() ? new String[0] : args.split(",");
    String propertyName = a.length == 0 ? expectedValue : a[0].trim();
    String expectedVal = a.length > 1 ? a[1].trim() : null;
    if (propertyName == null)
      return false;
    int dot = propertyName.lastIndexOf('.');
    com.unitesk.requality.vscode.ui.runtime.QualifiedName key = dot < 0
        ? new com.unitesk.requality.vscode.ui.runtime.QualifiedName("", propertyName)
        : new com.unitesk.requality.vscode.ui.runtime.QualifiedName(propertyName.substring(0, dot), propertyName.substring(dot + 1));
    boolean found = false;
    for (IProject project : projects) {
      try {
        String actualVal = project.getPersistentProperty(key);
        if (actualVal == null)
          continue;
        found = true;
        if (expectedVal != null && !expectedVal.equals(actualVal))
          return false;
      } catch (CoreException ex) {
        // ignored, as Eclipse does
      }
    }
    return found;
  }

  /** The boolean of a test value; a missing value means true (ResourcePropertyTester.toBoolean). */
  private static boolean toBoolean(String expected) {
    return expected == null || Boolean.parseBoolean(expected);
  }

  /** A '*' and '?' pattern match ignoring case (StringMatcher of the resource property testers). */
  private static boolean matchesPattern(String text, String pattern) {
    if (pattern == null)
      return false;
    StringBuilder re = new StringBuilder();
    for (char c : pattern.toCharArray())
      re.append(c == '*' ? ".*" : c == '?' ? "." : java.util.regex.Pattern.quote(String.valueOf(c)));
    return java.util.regex.Pattern.compile(re.toString(), java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL)
        .matcher(text).matches();
  }
}
