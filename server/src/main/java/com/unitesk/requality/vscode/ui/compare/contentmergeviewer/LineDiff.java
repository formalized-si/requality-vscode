package com.unitesk.requality.vscode.ui.compare.contentmergeviewer;

/**
 * The lines that differ between two texts (the role of {@code org.eclipse.compare.rangedifferencer.RangeDifferencer}
 * with the line comparator of the text merge viewer): a longest common subsequence of the lines by Myers' algorithm;
 * the lines not in it are changed.
 */
final class LineDiff {
  private LineDiff() {
  }

  /** For each line of {@code a} and of {@code b}, whether it is not part of the common lines. */
  static boolean[][] changedLines(String[] a, String[] b) {
    return changedLines(a, b, false);
  }

  /**
   * As {@link #changedLines(String[], String[])}; with {@code ignoreWhitespace} (the "Ignore white space" option of the
   * Compare preferences) lines that differ only in their white space are common.
   */
  static boolean[][] changedLines(String[] a, String[] b, boolean ignoreWhitespace) {
    boolean[] ca = new boolean[a.length], cb = new boolean[b.length];
    java.util.Arrays.fill(ca, true);
    java.util.Arrays.fill(cb, true);
    int n = a.length, m = b.length, max = n + m;
    if (max == 0)
      return new boolean[][] { ca, cb };
    int offset = max;
    int[] v = new int[2 * max + 2];
    java.util.List<int[]> trace = new java.util.ArrayList<>();
    int dFound = -1;
    outer:
    for (int d = 0; d <= max; d++) {
      trace.add(v.clone());
      for (int k = -d; k <= d; k += 2) {
        int x;
        if (k == -d || k != d && v[offset + k - 1] < v[offset + k + 1])
          x = v[offset + k + 1];
        else
          x = v[offset + k - 1] + 1;
        int y = x - k;
        while (x < n && y < m && eq(a[x], b[y], ignoreWhitespace)) {
          x++;
          y++;
        }
        v[offset + k] = x;
        if (x >= n && y >= m) {
          dFound = d;
          break outer;
        }
      }
      if (trace.size() > 4000) // too many differences: everything that is not a common prefix/suffix is changed
        return prefixSuffix(a, b, ignoreWhitespace);
    }
    int x = n, y = m;
    for (int d = dFound; d > 0; d--) {
      int[] vd = trace.get(d);
      int k = x - y;
      int prevK = k == -d || k != d && vd[offset + k - 1] < vd[offset + k + 1] ? k + 1 : k - 1;
      int prevX = vd[offset + prevK];
      int prevY = prevX - prevK;
      while (x > prevX && y > prevY) {
        ca[x - 1] = false;
        cb[y - 1] = false;
        x--;
        y--;
      }
      x = prevX;
      y = prevY;
    }
    while (x > 0 && y > 0) {
      ca[x - 1] = false;
      cb[y - 1] = false;
      x--;
      y--;
    }
    return new boolean[][] { ca, cb };
  }

  private static boolean[][] prefixSuffix(String[] a, String[] b, boolean ignoreWhitespace) {
    boolean[] ca = new boolean[a.length], cb = new boolean[b.length];
    int p = 0;
    while (p < a.length && p < b.length && eq(a[p], b[p], ignoreWhitespace))
      p++;
    int s = 0;
    while (s < a.length - p && s < b.length - p && eq(a[a.length - 1 - s], b[b.length - 1 - s], ignoreWhitespace))
      s++;
    for (int i = p; i < a.length - s; i++)
      ca[i] = true;
    for (int i = p; i < b.length - s; i++)
      cb[i] = true;
    return new boolean[][] { ca, cb };
  }

  /** Lines are equal ignoring their line delimiters (and all their white space when ignoring white space). */
  private static boolean eq(String l1, String l2, boolean ignoreWhitespace) {
    if (ignoreWhitespace)
      return strip(l1).replaceAll("\\s+", "").equals(strip(l2).replaceAll("\\s+", ""));
    return strip(l1).equals(strip(l2));
  }

  private static String strip(String l) {
    int e = l.length();
    while (e > 0 && (l.charAt(e - 1) == '\n' || l.charAt(e - 1) == '\r'))
      e--;
    return l.substring(0, e);
  }
}
