package com.unitesk.requality.vscode.ui.runtime;

import java.util.Objects;

/** {@code org.eclipse.core.runtime.QualifiedName}: a name qualified by a plug-in id, the key of a resource property. */
public final class QualifiedName {
  private final String qualifier;
  private final String localName;

  public QualifiedName(String qualifier, String localName) {
    this.qualifier = qualifier;
    this.localName = Objects.requireNonNull(localName);
  }

  public String getQualifier() {
    return qualifier;
  }

  public String getLocalName() {
    return localName;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof QualifiedName))
      return false;
    QualifiedName q = (QualifiedName) o;
    return Objects.equals(qualifier, q.qualifier) && localName.equals(q.localName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(qualifier, localName);
  }

  /** The key of the name in a store: {@code qualifier/localName}. */
  @Override
  public String toString() {
    return qualifier == null ? localName : qualifier + "/" + localName;
  }
}
