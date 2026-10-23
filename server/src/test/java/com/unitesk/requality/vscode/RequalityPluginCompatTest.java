package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.unitesk.requality.vscode.core.VSCodeTracker;
import com.unitesk.requality.vscode.port.eclipse.RequalityPlugin;
import com.unitesk.requality.vscode.ui.runtime.Bundle;
import com.unitesk.requality.vscode.ui.runtime.ExtensionRegistry;
import com.unitesk.requality.vscode.ui.runtime.Platform;
import java.io.File;
import org.junit.jupiter.api.Test;

/** Compatibility of the Eclipse API that the identical {@code RequalityPlugin} uses. */
public class RequalityPluginCompatTest {

  @Test
  public void missingXulRunnerBundleIsNull() {
    assertNull(Platform.getBundle("org.mozilla.xulrunner"));
  }

  @Test
  public void requalityBundleHasNoXulRunnerEntries() {
    assertNotNull(Platform.getBundle("com.unitesk.requality"));
    assertNull(Bundle.REQUALITY.findEntries("missing/xulrunner", "xulrunner", false));
  }

  @Test
  public void browserTypeIsAlwaysAvailable() {
    assertTrue(RequalityPlugin.getBrowserType() >= 0);
    assertTrue(RequalityPlugin.getBrowserEngineNames().isEmpty());
    assertTrue(RequalityPlugin.getBrowserEnginesState().isEmpty());
  }

  @Test
  public void additionalTypesAreThePluginXmlNodeTypes() {
    ExtensionRegistry.setPluginXml(new File("upstream/eclipse-reference/plugin.xml"));
    assertFalse(RequalityPlugin.getAdditionalTypes().isEmpty());
    assertTrue(RequalityPlugin.getAdditionalTypes().keySet().containsAll(VSCodeTracker.NODE_TYPES.keySet()));
  }

  /**
   * The SWT Win32 registry functions the Activator calls by reflection: the Internet Explorer feature-control key is
   * present (the webviews of VS Code have no connection limit of the Internet Explorer control); other keys are read
   * with the reg tool of Windows, so outside Windows they are not found.
   */
  @Test
  void theRegistryOfTheActivatorIsReadAsTheSwtWin32ApiReadsIt() throws Exception {
    Class<?> os = Class.forName("org.eclipse.swt.internal.win32.OS");
    Class<?> tchar = Class.forName("org.eclipse.swt.internal.win32.TCHAR");
    java.lang.reflect.Method open = os.getDeclaredMethod("RegOpenKeyEx", long.class, tchar, int.class, int.class, long[].class);
    Object ie = tchar.getConstructor(int.class, String.class, boolean.class).newInstance(0,
        "Software\\Microsoft\\Internet Explorer\\Main\\FeatureControl\\MAXCONNECTIONSPERSERVER", true);
    long[] h = new long[1];
    org.junit.jupiter.api.Assertions.assertEquals(0, open.invoke(null, (long) 0x80000002, ie, 0, 0x20019, h));
    assertTrue(h[0] != 0);
    org.junit.jupiter.api.Assertions.assertEquals(0, os.getDeclaredMethod("RegCloseKey", long.class).invoke(null, h[0]));
    if (!System.getProperty("os.name").toLowerCase().startsWith("windows")) {
      Object fs = tchar.getConstructor(int.class, String.class, boolean.class).newInstance(0,
          "SYSTEM\\CurrentControlSet\\Control\\FileSystem", true);
      org.junit.jupiter.api.Assertions.assertEquals(2, open.invoke(null, (long) 0x80000002, fs, 0, 0x20019, new long[1]));
    }
  }

  /**
   * The registry is read with the reg tool as on Windows: a program giving the output of "reg query" stands for it (the
   * LongPathsEnabled value of the FileSystem key, and a key that does not exist).
   */
  @Test
  void theRegistryValuesAreReadFromTheOutputOfRegQuery() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("windows"));
    File reg = File.createTempFile("reg", ".sh");
    reg.deleteOnExit();
    java.nio.file.Files.writeString(reg.toPath(), String.join("\n",
        "#!/bin/sh",
        "case \"$2\" in",
        "  'HKLM\\SYSTEM\\CurrentControlSet\\Control\\FileSystem')",
        "    if [ \"$3\" = /v ]; then",
        "      printf '\\r\\nHKEY_LOCAL_MACHINE\\\\SYSTEM\\\\CurrentControlSet\\\\Control\\\\FileSystem\\r\\n    LongPathsEnabled    REG_DWORD    0x1\\r\\n\\r\\n'",
        "    else printf 'HKEY_LOCAL_MACHINE\\\\SYSTEM\\\\CurrentControlSet\\\\Control\\\\FileSystem\\r\\n'; fi; exit 0;;",
        "  *) echo 'ERROR: The system was unable to find the specified registry key or value.'; exit 1;;",
        "esac", ""));
    assertTrue(reg.setExecutable(true));
    String old = System.getProperty("requality.win32.reg");
    System.setProperty("requality.win32.reg", reg.getAbsolutePath());
    try {
      Class<?> os = Class.forName("org.eclipse.swt.internal.win32.OS");
      Class<?> tchar = Class.forName("org.eclipse.swt.internal.win32.TCHAR");
      java.lang.reflect.Constructor<?> t = tchar.getConstructor(int.class, String.class, boolean.class);
      java.lang.reflect.Method open = os.getDeclaredMethod("RegOpenKeyEx", long.class, tchar, int.class, int.class, long[].class);
      java.lang.reflect.Method query = os.getDeclaredMethod("RegQueryValueEx", long.class, tchar, long.class, int[].class, int[].class, int[].class);
      long[] h = new long[1];
      org.junit.jupiter.api.Assertions.assertEquals(0, open.invoke(null, (long) 0x80000002,
          t.newInstance(0, "SYSTEM\\CurrentControlSet\\Control\\FileSystem", true), 0, 0x20019, h));
      int[] data = new int[2];
      org.junit.jupiter.api.Assertions.assertEquals(0, query.invoke(null, h[0], t.newInstance(0, "LongPathsEnabled", true), 0L,
          null, data, new int[] { 8 }));
      org.junit.jupiter.api.Assertions.assertEquals(1, data[0]);
      org.junit.jupiter.api.Assertions.assertEquals(2, query.invoke(null, h[0], t.newInstance(0, "Missing", true), 0L, null,
          new int[2], new int[] { 8 }));
      org.junit.jupiter.api.Assertions.assertEquals(2, open.invoke(null, (long) 0x80000002,
          t.newInstance(0, "SOFTWARE\\NoSuchKey", true), 0, 0x20019, new long[1]));
    } finally {
      if (old == null)
        System.clearProperty("requality.win32.reg");
      else
        System.setProperty("requality.win32.reg", old);
    }
  }
}
