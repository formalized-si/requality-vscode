package com.unitesk.requality.vscode.ui.runtime;

/** {@code org.eclipse.core.runtime.Assert}: the precondition checks of the Eclipse API. */
public final class Assert {
  private Assert() {
  }

  /** {@code org.eclipse.core.runtime.Assert.isNotNull}: the object is not null. */
  public static void isNotNull(Object object) {
    isNotNull(object, "The object must not be null");
  }

  /** {@code org.eclipse.core.runtime.Assert.isNotNull}: the object is not null, else the message is thrown. */
  public static void isNotNull(Object object, String message) {
    if (object == null)
      throw new AssertionFailedException(message);
  }

  /** {@code org.eclipse.core.runtime.Assert.isTrue}: the condition holds, else the message is thrown. */
  public static void isTrue(boolean condition, String message) {
    if (!condition)
      throw new AssertionFailedException(message);
  }

  /** {@code org.eclipse.core.runtime.Assert.isTrue}: the condition holds. */
  public static void isTrue(boolean condition) {
    isTrue(condition, "Assertion failed");
  }

  /** {@code org.eclipse.core.runtime.AssertionFailedException}. */
  public static final class AssertionFailedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public AssertionFailedException(String message) {
      super(message);
    }
  }
}
