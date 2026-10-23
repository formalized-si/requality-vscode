package org.eclipse.swt.internal.win32;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@code org.eclipse.swt.internal.win32.OS}: the registry functions of the Win32 API of SWT that the Requality plug-in
 * calls by reflection (Activator.start: the Internet Explorer connection limit and LongPathsEnabled). The server has no
 * SWT natives: the keys of HKEY_LOCAL_MACHINE are read with the {@code reg} tool of Windows. VSCODE: the web editors are
 * VS Code webviews, not the Internet Explorer control of the SWT browser, so the feature-control key of Internet
 * Explorer (FEATURE_MAXCONNECTIONSPERSERVER, which lifts the connection limit of that control) is reported as present:
 * the limit it lifts does not exist here.
 */
public class OS {
  /**
   * {@code OS.HKEY_LOCAL_MACHINE} as SWT defines it: the int 0x80000002 widened to a long (sign-extended, as the
   * predefined keys of 64-bit Windows are); the plug-in passes the same int literal by reflection.
   */
  public static final long HKEY_LOCAL_MACHINE = 0x80000002;
  public static final int KEY_READ = 0x20019;
  /** {@code ERROR_SUCCESS}, {@code ERROR_FILE_NOT_FOUND}. */
  static final int ERROR_SUCCESS = 0, ERROR_FILE_NOT_FOUND = 2;
  static final String IE_FEATURE_CONTROL = "Software\\Microsoft\\Internet Explorer\\Main\\FeatureControl\\";

  private static final AtomicLong HANDLES = new AtomicLong(1);
  private static final Map<Long, String> KEYS = new ConcurrentHashMap<>();

  /** Opens a key of HKEY_LOCAL_MACHINE: its handle is put in {@code phkResult[0]}. */
  public static int RegOpenKeyEx(long hKey, TCHAR lpSubKey, int ulOptions, int samDesired, long[] phkResult) {
    if (hKey != HKEY_LOCAL_MACHINE || lpSubKey == null)
      return ERROR_FILE_NOT_FOUND;
    String key = lpSubKey.string;
    if (!key.startsWith(IE_FEATURE_CONTROL) && reg("query", "HKLM\\" + key) == null)
      return ERROR_FILE_NOT_FOUND;
    long h = HANDLES.getAndIncrement();
    KEYS.put(h, key);
    phkResult[0] = h;
    return ERROR_SUCCESS;
  }

  /** Reads a REG_DWORD value of an open key into {@code lpData[0]}. */
  public static int RegQueryValueEx(long hKey, TCHAR lpValueName, long lpReserved, int[] lpType, int[] lpData, int[] lpcbData) {
    String key = KEYS.get(hKey);
    if (key == null || lpValueName == null)
      return ERROR_FILE_NOT_FOUND;
    String out = reg("query", "HKLM\\" + key, "/v", lpValueName.string);
    if (out == null)
      return ERROR_FILE_NOT_FOUND;
    // "    LongPathsEnabled    REG_DWORD    0x1"
    for (String line : out.split("\\R")) {
      String[] parts = line.trim().split("\\s+");
      if (parts.length >= 3 && parts[0].equalsIgnoreCase(lpValueName.string) && parts[1].equals("REG_DWORD")) {
        if (lpType != null && lpType.length > 0)
          lpType[0] = 4;
        if (lpData != null && lpData.length > 0)
          lpData[0] = (int) Long.decode(parts[2]).longValue();
        return ERROR_SUCCESS;
      }
    }
    return ERROR_FILE_NOT_FOUND;
  }

  public static int RegCloseKey(long hKey) {
    KEYS.remove(hKey);
    return ERROR_SUCCESS;
  }

  /** The output of {@code reg} with the arguments, or null when it fails (or is not available). */
  static String reg(String... args) {
    String[] cmd = new String[args.length + 1];
    // the reg tool of Windows (a test gives another program of the same output)
    cmd[0] = System.getProperty("requality.win32.reg", "reg");
    System.arraycopy(args, 0, cmd, 1, args.length);
    try {
      Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
      StringBuilder sb = new StringBuilder();
      try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), Charset.defaultCharset()))) {
        for (String l; (l = r.readLine()) != null;)
          sb.append(l).append('\n');
      }
      if (!p.waitFor(10, TimeUnit.SECONDS)) {
        p.destroyForcibly();
        return null;
      }
      return p.exitValue() == 0 ? sb.toString() : null;
    } catch (Exception e) {
      return null;
    }
  }
}
