package com.unitesk.requality.vscode.ui.compare.structuremergeviewer;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.unitesk.requality.vscode.ui.compare.IStreamContentAccessor;
import com.unitesk.requality.vscode.ui.compare.ITypedElement;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IProgressMonitor;
import com.unitesk.requality.vscode.ui.runtime.OperationCanceledException;

/**
 * {@code org.eclipse.compare.structuremergeviewer.Differencer}: finds the differences between two or three
 * structures of {@link IStructureComparator}s (children matched by equals) and builds a tree of {@link DiffNode}s for
 * them; leaves are compared by their byte contents.
 */
public class Differencer {
  public static final int NO_CHANGE = 0;
  public static final int ADDITION = 1;
  public static final int DELETION = 2;
  public static final int CHANGE = 3;
  public static final int CHANGE_TYPE_MASK = 3;
  public static final int LEFT = 4;
  public static final int RIGHT = 8;
  public static final int CONFLICTING = 12;
  public static final int DIRECTION_MASK = 12;
  public static final int PSEUDO_CONFLICT = 16;

  static class Node {
    List<Node> fChildren;
    int fCode;
    Object fAncestor;
    Object fLeft;
    Object fRight;

    Node() {
    }

    Node(Node parent, Object ancestor, Object left, Object right) {
      parent.add(this);
      fAncestor = ancestor;
      fLeft = left;
      fRight = right;
    }

    void add(Node child) {
      if (fChildren == null)
        fChildren = new ArrayList<>();
      fChildren.add(child);
    }

    Object visit(Differencer d, Object parent, int level) {
      if (fCode == NO_CHANGE)
        return null;
      Object data = d.visit(parent, fCode, fAncestor, fLeft, fRight);
      if (fChildren != null)
        for (Node n : fChildren)
          n.visit(d, data, level + 1);
      return data;
    }
  }

  public Differencer() {
  }

  /** The root of the differences (built by {@link #visit}), or null when the inputs do not differ. */
  public Object findDifferences(boolean threeWay, IProgressMonitor pm, Object data, Object ancestor, Object left,
      Object right) {
    Node root = new Node();
    int code = traverse(threeWay, root, pm, threeWay ? ancestor : null, left, right);
    if (code != NO_CHANGE) {
      List<Node> l = root.fChildren;
      if (!l.isEmpty())
        return l.get(0).visit(this, data, 0);
    }
    return null;
  }

  private int traverse(boolean threeWay, Node parent, IProgressMonitor pm, Object ancestor, Object left, Object right) {
    Object[] ancestorChildren = getChildren(ancestor);
    Object[] rightChildren = getChildren(right);
    Object[] leftChildren = getChildren(left);
    int code = NO_CHANGE;
    Node node = new Node(parent, ancestor, left, right);
    boolean content = true; // we reset this if we have at least one child
    if ((threeWay && ancestorChildren != null || !threeWay) && rightChildren != null && leftChildren != null) {
      // VSCODE: a LinkedHashSet (Eclipse: HashSet) so that the order of the differences follows the inputs.
      Set<Object> allSet = new LinkedHashSet<>(20);
      Map<Object, Object> ancestorSet = null;
      Map<Object, Object> rightSet = null;
      Map<Object, Object> leftSet = null;
      if (ancestorChildren != null) {
        ancestorSet = new HashMap<>(10);
        for (Object c : ancestorChildren) {
          ancestorSet.put(c, c);
          allSet.add(c);
        }
      }
      rightSet = new HashMap<>(10);
      for (Object c : rightChildren) {
        rightSet.put(c, c);
        allSet.add(c);
      }
      leftSet = new HashMap<>(10);
      for (Object c : leftChildren) {
        leftSet.put(c, c);
        allSet.add(c);
      }
      for (Object keyChild : allSet) {
        if (pm != null) {
          if (pm.isCanceled())
            throw new OperationCanceledException();
          updateProgress(pm, keyChild);
        }
        Object ancestorChild = ancestorSet != null ? ancestorSet.get(keyChild) : null;
        Object leftChild = leftSet.get(keyChild);
        Object rightChild = rightSet.get(keyChild);
        int c = traverse(threeWay, node, pm, ancestorChild, leftChild, rightChild);
        if ((c & CHANGE_TYPE_MASK) != NO_CHANGE) {
          code |= CHANGE; // deletions and additions of child result in a change of the container
          code |= c & DIRECTION_MASK; // incoming & outgoing are just ored
          content = false;
        }
      }
    }
    if (content) // a leaf
      code = compare(threeWay, ancestor, left, right);
    node.fCode = code;
    return code;
  }

  /** Creates the diff node of a difference (in the container {@code data}). */
  protected Object visit(Object data, int result, Object ancestor, Object left, Object right) {
    return new DiffNode((IDiffContainer) data, result, (ITypedElement) ancestor, (ITypedElement) left, (ITypedElement) right);
  }

  private int compare(boolean threeway, Object ancestor, Object left, Object right) {
    int description = NO_CHANGE;
    if (threeway) {
      if (ancestor == null) {
        if (left == null) {
          if (right != null)
            description = RIGHT | ADDITION;
        } else {
          if (right == null) {
            description = LEFT | ADDITION;
          } else {
            description = CONFLICTING | ADDITION;
            if (contentsEqual(left, 'L', right, 'R'))
              description |= PSEUDO_CONFLICT;
          }
        }
      } else {
        if (left == null) {
          if (right == null) {
            description = CONFLICTING | DELETION | PSEUDO_CONFLICT;
          } else {
            if (contentsEqual(ancestor, 'A', right, 'R'))
              description = LEFT | DELETION;
            else
              description = CONFLICTING | CHANGE;
          }
        } else {
          if (right == null) {
            if (contentsEqual(ancestor, 'A', left, 'L'))
              description = RIGHT | DELETION;
            else
              description = CONFLICTING | CHANGE;
          } else {
            boolean ay = contentsEqual(ancestor, 'A', left, 'L');
            boolean am = contentsEqual(ancestor, 'A', right, 'R');
            if (ay && am) {
              // no change
            } else if (ay && !am) {
              description = RIGHT | CHANGE;
            } else if (!ay && am) {
              description = LEFT | CHANGE;
            } else {
              description = CONFLICTING | CHANGE;
              if (contentsEqual(left, 'L', right, 'R'))
                description |= PSEUDO_CONFLICT;
            }
          }
        }
      }
    } else { // two way compare ignores ancestor
      if (left == null) {
        if (right != null)
          description = ADDITION;
      } else {
        if (right == null)
          description = DELETION;
        else if (!contentsEqual(left, 'L', right, 'R'))
          description = CHANGE;
      }
    }
    return description;
  }

  protected boolean contentsEqual(Object input1, char contributor1, Object input2, char contributor2) {
    return contentsEqual(input1, input2);
  }

  /** Whether the byte contents of the two inputs are equal. */
  protected boolean contentsEqual(Object input1, Object input2) {
    if (input1 == input2)
      return true;
    try (InputStream is1 = getStream(input1); InputStream is2 = getStream(input2)) {
      if (is1 == null && is2 == null) // no byte contents
        return true;
      if (is1 == null || is2 == null) // only one has contents
        return false;
      while (true) {
        int c1 = is1.read();
        int c2 = is2.read();
        if (c1 == -1 && c2 == -1)
          return true;
        if (c1 != c2)
          break;
      }
    } catch (IOException ex) {
      // not equal
    }
    return false;
  }

  private InputStream getStream(Object o) {
    if (o instanceof IStreamContentAccessor) {
      try {
        return ((IStreamContentAccessor) o).getContents();
      } catch (CoreException ex) {
        // no contents
      }
    }
    return null;
  }

  protected Object[] getChildren(Object input) {
    if (input instanceof IStructureComparator)
      return ((IStructureComparator) input).getChildren();
    return null;
  }

  protected void updateProgress(IProgressMonitor progressMonitor, Object node) {
    if (node instanceof ITypedElement)
      progressMonitor.subTask(MessageFormat.format("Comparing {0}", ((ITypedElement) node).getName()));
  }
}
