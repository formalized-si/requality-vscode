package com.unitesk.requality.vscode.ui.navigator;

import com.unitesk.requality.vscode.ui.jface.viewers.Viewer;
import com.unitesk.requality.vscode.ui.jface.viewers.ViewerComparator;
import com.unitesk.requality.vscode.ui.resources.IContainer;
import com.unitesk.requality.vscode.ui.resources.IResource;

/**
 * {@code org.eclipse.ui.views.navigator.ResourceComparator}: sorts resources with the folders before the files, then by
 * name ({@link #NAME}) or by file extension and name ({@link #TYPE}). Elements that are not resources are sorted by
 * their class, as in Eclipse.
 */
public class ResourceComparator extends ViewerComparator {
  /** Constructor argument value that indicates to sort items by name. */
  public static final int NAME = 1;
  /** Constructor argument value that indicates to sort items by extension. */
  public static final int TYPE = 2;

  private int criteria;

  /**
   * Creates a resource sorter that will use the given sort criteria.
   *
   * @param criteria the sort criterion to use: one of {@link #NAME} or {@link #TYPE}
   */
  public ResourceComparator(int criteria) {
    super();
    this.criteria = criteria;
  }

  /**
   * Returns an integer value representing the relative sort priority of the given element based on its class: resources
   * are 2, everything else is 0.
   */
  protected int classComparison(Object element) {
    if (element instanceof IResource) {
      return 2;
    }
    return 0;
  }

  @Override
  public int compare(Viewer viewer, Object o1, Object o2) {
    // have to deal with non-resources in navigator
    // if one or both objects are not resources, returned a comparison based on class.
    if (!(o1 instanceof IResource r1 && o2 instanceof IResource r2)) {
      return compareClass(o1, o2);
    }
    if (r1 instanceof IContainer && r2 instanceof IContainer) {
      return compareNames(r1, r2);
    } else if (r1 instanceof IContainer) {
      return -1;
    } else if (r2 instanceof IContainer) {
      return 1;
    } else if (criteria == NAME) {
      return compareNames(r1, r2);
    } else if (criteria == TYPE) {
      return compareTypes(r1, r2);
    } else {
      return 0;
    }
  }

  /** Returns a number reflecting the collation order of the given elements based on their class. */
  protected int compareClass(Object element1, Object element2) {
    return classComparison(element1) - classComparison(element2);
  }

  /** Returns a number reflecting the collation order of the given resources based on their names. */
  protected int compareNames(IResource resource1, IResource resource2) {
    return getComparator().compare(resource1.getName(), resource2.getName());
  }

  /** Orders by file extension; resources with the same extension are ordered by name. */
  protected int compareTypes(IResource resource1, IResource resource2) {
    String ext1 = getExtensionFor(resource1);
    String ext2 = getExtensionFor(resource2);
    int result = getComparator().compare(ext1, ext2);
    if (result != 0) {
      return result;
    }
    return compareNames(resource1, resource2);
  }

  /** Returns the sort criteria of this sorter. */
  public int getCriteria() {
    return criteria;
  }

  private String getExtensionFor(IResource resource) {
    String ext = resource.getFileExtension();
    return ext == null ? "" : ext;
  }

  /** Sets the sort criteria of this sorter. */
  public void setCriteria(int criteria) {
    this.criteria = criteria;
  }
}
