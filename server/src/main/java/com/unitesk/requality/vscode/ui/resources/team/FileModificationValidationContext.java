package com.unitesk.requality.vscode.ui.resources.team;

/**
 * {@code org.eclipse.core.resources.team.FileModificationValidationContext}: the context of a validateEdit: the UI
 * context (a Shell) when the user may be asked, null otherwise.
 */
public class FileModificationValidationContext {
  /** {@code VALIDATE_PROMPT}: the context that allows the validator to prompt the user without a shell. */
  public static final FileModificationValidationContext VALIDATE_PROMPT = new FileModificationValidationContext(null);

  private final Object shell;

  public FileModificationValidationContext(Object shell) {
    this.shell = shell;
  }

  /** The Shell of the UI context, or null. */
  public final Object getShell() {
    return shell;
  }
}
