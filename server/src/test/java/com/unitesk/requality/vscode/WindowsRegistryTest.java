package com.unitesk.requality.vscode;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

/**
 * The Windows branch of Activator.start, as the plug-in runs it on Windows: the registry is read through the SWT Win32
 * API (the compat {@code org.eclipse.swt.internal.win32.OS} over the reg tool). By default a program gives the output
 * of "reg query" of a Windows where LongPathsEnabled is 0; the system property {@code requality.test.reg} names a real
 * reg program instead (e.g. the reg.exe of Wine, whose registry has the value {@code requality.test.reg.longPaths}).
 * On Windows the plug-in reads the registry of the system with its reg tool, and the test reads LongPathsEnabled with
 * PowerShell. With LongPathsEnabled 1 the plug-in shows no long paths warning, else it does; the connection limit of
 * the Internet Explorer control does not concern the webviews of VS Code, so that warning is never shown.
 */
class WindowsRegistryTest {
  static ServerHarness h;
  static String osName;
  /** The calls of the reg tool (one line each). */
  static File calls;
  /** A real reg program (empty: the output of the test's program). */
  static final String REAL_REG = System.getProperty("requality.test.reg", "");
  static final boolean WINDOWS = System.getProperty("os.name").toLowerCase().startsWith("windows");
  /** The value of LongPathsEnabled in the registry the reg program reads (-1: no value). */
  static int registryLongPaths;

  @BeforeAll
  static void start() throws Exception {
    calls = File.createTempFile("reg", ".calls");
    calls.deleteOnExit();
    if (WINDOWS) {
      // the reg tool of Windows, its calls logged; the value as PowerShell reads it
      File reg = File.createTempFile("reg", ".cmd");
      reg.deleteOnExit();
      Files.writeString(reg.toPath(), String.join("\r\n", "@echo off", "echo %* >> \"" + calls.getAbsolutePath() + "\"", "reg %*",
          "exit /b %ERRORLEVEL%", ""));
      System.setProperty("requality.win32.reg", reg.getAbsolutePath());
      registryLongPaths = powerShellLongPaths();
      h = new ServerHarness();
      h.initialize(h.workspace);
      return;
    }
    registryLongPaths = REAL_REG.isEmpty() ? 0 : Integer.getInteger("requality.test.reg.longPaths", 0);
    File reg = File.createTempFile("reg", ".sh");
    reg.deleteOnExit();
    if (!REAL_REG.isEmpty())
      Files.writeString(reg.toPath(), String.join("\n", "#!/bin/sh",
          "echo \"$*\" >> '" + calls.getAbsolutePath() + "'",
          "exec '" + REAL_REG + "' \"$@\"", ""));
    else
      Files.writeString(reg.toPath(), String.join("\n",
          "#!/bin/sh",
          "echo \"$*\" >> '" + calls.getAbsolutePath() + "'",
          "case \"$2\" in",
          "  'HKLM\\SYSTEM\\CurrentControlSet\\Control\\FileSystem')",
          "    if [ \"$3\" = /v ]; then",
          "      printf '\\r\\nHKEY_LOCAL_MACHINE\\\\SYSTEM\\\\CurrentControlSet\\\\Control\\\\FileSystem\\r\\n    LongPathsEnabled    REG_DWORD    0x0\\r\\n\\r\\n'",
          "    else printf 'HKEY_LOCAL_MACHINE\\\\SYSTEM\\\\CurrentControlSet\\\\Control\\\\FileSystem\\r\\n'; fi; exit 0;;",
          "  *) echo 'ERROR: The system was unable to find the specified registry key or value.'; exit 1;;",
          "esac", ""));
    assertTrue(reg.setExecutable(true));
    System.setProperty("requality.win32.reg", reg.getAbsolutePath());
    // the process launcher of the JDK takes the platform from os.name when it is first used: it is used before the
    // name changes (the launcher of this JVM runs the reg program)
    new ProcessBuilder(reg.getAbsolutePath(), "query", "HKLM\\SOFTWARE\\Init").start().waitFor();
    Files.writeString(calls.toPath(), "");
    // the plug-in's Activator decides on the name of the system when its class is loaded (with the server)
    osName = System.getProperty("os.name");
    System.setProperty("os.name", "Windows 10");
    h = new ServerHarness();
    h.initialize(h.workspace);
  }

  @AfterAll
  static void stop() throws Exception {
    if (h != null)
      h.close();
    if (osName != null)
      System.setProperty("os.name", osName);
    System.clearProperty("requality.win32.reg");
  }

  /** LongPathsEnabled of the registry of Windows as PowerShell reads it (-1: no value). */
  static int powerShellLongPaths() throws Exception {
    Process p = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command",
        "(Get-ItemProperty -Path 'HKLM:\\SYSTEM\\CurrentControlSet\\Control\\FileSystem' -Name LongPathsEnabled"
            + " -ErrorAction SilentlyContinue).LongPathsEnabled").redirectErrorStream(true).start();
    String out = new String(p.getInputStream().readAllBytes()).trim();
    assertTrue(p.waitFor(60, java.util.concurrent.TimeUnit.SECONDS));
    return out.isEmpty() ? -1 : Integer.parseInt(out);
  }

  static boolean opened(String titlePart) {
    for (JsonObject n : h.notifications("ui/openShell"))
      if (n.has("title") && n.get("title").getAsString().contains(titlePart))
        return true;
    return false;
  }

  /** The text of the plug-in's dialog strings (Localization: the language of the system). */
  static String text(String key, String def) {
    return com.unitesk.requality.core.localization.Localization.getLocalizedString(
        com.unitesk.requality.core.localization.StringsSource.DIALOG, key, def, null);
  }

  @Test
  void theLongPathsWarningOfWindowsIsShownAndNotTheConnectionLimit() throws Exception {
    h.await("the registry read by the plug-in", v -> {
      try {
        return Files.readString(calls.toPath()).contains("LongPathsEnabled");
      } catch (java.io.IOException e) {
        return false;
      }
    });
    String longPaths = text("title_warning_long_paths", "Warning! Long Paths is not enabled");
    if (registryLongPaths == 1) {
      // the value is read when the plug-in starts; its warning would be opened then
      Thread.sleep(3000);
      assertFalse(opened(longPaths), "long paths are enabled");
      assertFalse(opened(text("title_warning_connection_limit", "Warning! Active editors count may be limited")),
          "the connection limit of Internet Explorer does not apply");
      return;
    }
    h.await("the long paths warning of the plug-in", v -> opened(longPaths));
    String shell = h.notifications("ui/openShell").stream().filter(n -> n.has("title")
        && n.get("title").getAsString().contains(longPaths)).findFirst().get().get("shell").getAsString();
    String snap = h.call("ui/snapshot", "shell", shell).toString();
    for (String[] b : new String[][] { { "button_apply_warning_connection_limit", "Apply the fix" },
        { "button_later_warning_connection_limit", "Ask again later" },
        { "button_ignore_warning_connection_limit", "Do not show this dialog again" } })
      assertTrue(snap.contains(text(b[0], b[1])), b[1] + " in " + snap);
    // the key of Internet Explorer is reported present: no connection limit warning
    assertFalse(opened(text("title_warning_connection_limit", "Warning! Active editors count may be limited")),
        "the connection limit of Internet Explorer does not apply");
    h.closeShell(shell);
  }
}
