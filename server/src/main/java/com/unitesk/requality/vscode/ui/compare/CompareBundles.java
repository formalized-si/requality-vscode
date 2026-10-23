package com.unitesk.requality.vscode.ui.compare;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.vscode.ui.runtime.CoreException;
import com.unitesk.requality.vscode.ui.runtime.IStatus;
import com.unitesk.requality.vscode.ui.runtime.Status;

/**
 * {@code org.eclipse.compare.internal.Utilities}: the lookups of the resource bundles of the compare actions and the
 * reading of the contents of a compare element as text.
 */
public final class CompareBundles {
  private CompareBundles() {
  }

  /** The bundle of the given name ({@code org.eclipse.compare.internal.ReplaceWithEditionAction}), read from the resources. */
  public static ResourceBundle bundle(String name) {
    return ResourceBundle.getBundle(name);
  }

  /** The text of the key, or the key itself when the bundle has none. */
  public static String getString(ResourceBundle bundle, String key) {
    return getString(bundle, key, key);
  }

  /** The text of the key, or the default when the bundle has none. */
  public static String getString(ResourceBundle bundle, String key, String dfltValue) {
    if (bundle != null) {
      try {
        return bundle.getString(key);
      } catch (MissingResourceException x) {
        // fall through
      }
    }
    return dfltValue;
  }

  /** The text of the key formatted with the argument ({@code {0}}); "!key!" when the bundle has none. */
  public static String getFormattedString(ResourceBundle bundle, String key, String arg) {
    if (bundle != null) {
      try {
        return MessageFormat.format(bundle.getString(key), arg);
      } catch (MissingResourceException x) {
        RLogger.logError(x);
      }
    }
    return "!" + key + "!";
  }

  /** The whole contents of an element as text (its charset when it has one, else UTF-8). */
  public static String readString(ITypedElement element) throws CoreException {
    if (!(element instanceof IStreamContentAccessor))
      return "";
    IStreamContentAccessor sca = (IStreamContentAccessor) element;
    String charset = element instanceof IEncodedStreamContentAccessor
        ? ((IEncodedStreamContentAccessor) element).getCharset()
        : null;
    try (InputStream in = sca.getContents()) {
      if (in == null)
        return "";
      byte[] bytes = in.readAllBytes();
      return new String(bytes, charset == null ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(charset));
    } catch (IOException | IllegalArgumentException e) {
      throw new CoreException(new Status(IStatus.ERROR, CompareUI.PLUGIN_ID, 0, e.getMessage(), e));
    }
  }
}
