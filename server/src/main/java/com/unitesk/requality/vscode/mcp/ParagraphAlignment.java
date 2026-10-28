package com.unitesk.requality.vscode.mcp;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The paragraphs of two versions of a document that are the same paragraph (a patience diff of their texts): the same
 * paragraphs in the same order, and between them the changed paragraphs that are most alike. A marked text goes first to
 * the new paragraph of its old paragraph: a copy of the text in another paragraph does not take its place.
 */
final class ParagraphAlignment {
  private ParagraphAlignment() {
  }

  /** The least similarity of two changed paragraphs that are one paragraph. */
  static final double ALIKE = 0.5;
  /** The most pairs of changed paragraphs that are compared in one block; a larger block pairs them in order. */
  static final long PAIRS = 40_000;

  /** For each old paragraph, the index of its new paragraph, or -1 (removed, or changed too much). */
  static int[] align(List<DocumentText.Paragraph> olds, List<DocumentText.Paragraph> news) {
    int[] map = new int[olds.size()];
    Arrays.fill(map, -1);
    Deque<int[]> work = new ArrayDeque<>();
    work.push(new int[] { 0, olds.size(), 0, news.size() });
    while (!work.isEmpty()) {
      int[] r = work.pop();
      int a0 = r[0];
      int a1 = r[1];
      int b0 = r[2];
      int b1 = r[3];
      // the same paragraphs at the start and at the end of the range
      while (a0 < a1 && b0 < b1 && same(olds, a0, news, b0))
        map[a0++] = b0++;
      while (a0 < a1 && b0 < b1 && same(olds, a1 - 1, news, b1 - 1))
        map[--a1] = --b1;
      if (a0 == a1 || b0 == b1)
        continue;
      List<int[]> anchors = anchors(olds, a0, a1, news, b0, b1);
      if (anchors.isEmpty()) {
        alike(olds, a0, a1, news, b0, b1, map);
        continue;
      }
      int pa = a0;
      int pb = b0;
      for (int[] an : anchors) {
        map[an[0]] = an[1];
        work.push(new int[] { pa, an[0], pb, an[1] });
        pa = an[0] + 1;
        pb = an[1] + 1;
      }
      work.push(new int[] { pa, a1, pb, b1 });
    }
    return map;
  }

  private static boolean same(List<DocumentText.Paragraph> olds, int a, List<DocumentText.Paragraph> news, int b) {
    return olds.get(a).text.equals(news.get(b).text);
  }

  /**
   * The paragraphs that occur once in both ranges, in the longest run in which their order is the same in both: pairs
   * of an old and a new index, in order.
   */
  private static List<int[]> anchors(List<DocumentText.Paragraph> olds, int a0, int a1, List<DocumentText.Paragraph> news,
      int b0, int b1) {
    Map<String, int[]> count = new HashMap<>();
    for (int i = a0; i < a1; i++) {
      int[] c = count.computeIfAbsent(olds.get(i).text, k -> new int[] { 0, -1, 0, -1 });
      c[0]++;
      c[1] = i;
    }
    for (int j = b0; j < b1; j++) {
      int[] c = count.get(news.get(j).text);
      if (c != null) {
        c[2]++;
        c[3] = j;
      }
    }
    List<int[]> unique = new ArrayList<>();
    for (int i = a0; i < a1; i++) {
      int[] c = count.get(olds.get(i).text);
      if (c[0] == 1 && c[2] == 1)
        unique.add(new int[] { i, c[3] });
    }
    // the longest increasing run of the new indexes (patience sorting)
    int n = unique.size();
    int[] tails = new int[n];
    int[] prev = new int[n];
    int len = 0;
    for (int k = 0; k < n; k++) {
      int j = unique.get(k)[1];
      int lo = 0;
      int hi = len;
      while (lo < hi) {
        int mid = (lo + hi) >>> 1;
        if (unique.get(tails[mid])[1] < j)
          lo = mid + 1;
        else
          hi = mid;
      }
      prev[k] = lo > 0 ? tails[lo - 1] : -1;
      tails[lo] = k;
      if (lo == len)
        len++;
    }
    int[][] run = new int[len][];
    for (int k = len == 0 ? -1 : tails[len - 1], i = len - 1; k >= 0; k = prev[k], i--)
      run[i] = unique.get(k);
    return Arrays.asList(run);
  }

  /** Changed paragraphs between the same ones: the most alike pairs first, each paragraph once. */
  private static void alike(List<DocumentText.Paragraph> olds, int a0, int a1, List<DocumentText.Paragraph> news, int b0,
      int b1, int[] map) {
    if ((long) (a1 - a0) * (b1 - b0) > PAIRS) {
      for (int k = 0; a0 + k < a1 && b0 + k < b1; k++)
        if (SpecUpdate.similarity(olds.get(a0 + k).text, news.get(b0 + k).text) >= ALIKE)
          map[a0 + k] = b0 + k;
      return;
    }
    List<double[]> pairs = new ArrayList<>();
    for (int i = a0; i < a1; i++)
      for (int j = b0; j < b1; j++) {
        double s = SpecUpdate.similarity(olds.get(i).text, news.get(j).text);
        if (s >= ALIKE)
          // of two equally alike pairs, the one nearer to the same place in the block
          pairs.add(new double[] { s - 0.001 * Math.abs((i - a0) - (j - b0)) / Math.max(1, a1 - a0 + b1 - b0), i, j });
      }
    pairs.sort((x, y) -> Double.compare(y[0], x[0]));
    boolean[] taken = new boolean[b1 - b0];
    for (double[] p : pairs) {
      int i = (int) p[1];
      int j = (int) p[2];
      if (map[i] < 0 && !taken[j - b0]) {
        map[i] = j;
        taken[j - b0] = true;
      }
    }
  }
}
