package com.unitesk.requality.vscode.mcp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.vscode.mcp.McpServer.ToolError;

/**
 * update_document_version: the requirement locations of a document move to its new version sentence by sentence, in
 * the transaction of the caller. A marked text found again in the new version moves unchanged; a text whose sentence
 * changed moves to the most similar sentence (status "changed", with its similarity and flags when its modal words,
 * negations or numbers changed); a text with no similar sentence is "lost". The old document stays, as with the Update
 * Document processor of Requality, whose character diff split the marked texts into fragments of other sentences. The
 * result also lists the uncovered statements of the new version.
 */
final class SpecUpdate {
  private SpecUpdate() {
  }

  /** The least similarity of a changed sentence; a suggestion for a lost text needs SUGGEST. */
  static final double MOVE = 0.7;
  static final double SUGGEST = 0.5;
  /** A marked text with fewer content words is found again only in a paragraph like its old one. */
  static final int SHORT = 3;
  static final double CONTEXT = 0.5;
  private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;
  /** A requirement statement, in English or Russian. */
  static final Pattern STATEMENT = Pattern.compile("\\b(shall|must|should|required|requires|will|mandatory|"
      + "должен|должна|должно|должны|обязан|обязана|обязано|обязаны|необходимо|следует|требуется|допускается|запрещается)\\b", FLAGS);

  /** The words that make the force of a statement: a change of them changes its meaning, also when the rest stays. */
  private static final Set<String> MODAL = new HashSet<>(Arrays.asList("shall", "must", "should", "may", "can", "will", "might",
      "could", "required", "optional", "recommended", "mandatory", "not", "no", "never", "only", "должен", "должна", "должно",
      "должны", "может", "могут", "следует", "необходимо", "обязан", "обязана", "обязано", "обязаны", "допускается",
      "запрещается", "рекомендуется", "не", "нет", "никогда", "только"));

  /** Words that tell nothing about which sentence a text is (English and Russian). */
  private static final Set<String> COMMON = new HashSet<>(Arrays.asList("a", "an", "the", "and", "or", "of", "to", "in", "on",
      "for", "with", "by", "at", "as", "from", "is", "are", "be", "been", "it", "its", "this", "that", "these", "those", "when",
      "if", "then", "и", "в", "во", "на", "с", "со", "по", "к", "ко", "о", "об", "от", "до", "для", "из", "у", "за", "при", "а",
      "но", "или", "что", "это", "как", "то", "же", "ли"));

  private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)*");

  private static Set<String> words(String s) {
    Set<String> w = new HashSet<>();
    for (String t : s.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
      if (!t.isEmpty() && !COMMON.contains(t))
        w.add(t);
    return w;
  }

  private static Set<String> modal(String s) {
    Set<String> w = new HashSet<>();
    for (String t : s.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
      if (MODAL.contains(t))
        w.add(t);
    return w;
  }

  private static Set<String> numbers(String s) {
    Set<String> w = new HashSet<>();
    Matcher m = NUMBER.matcher(s);
    while (m.find())
      w.add(m.group().replace(',', '.'));
    return w;
  }

  /** Dice coefficient of the word sets. */
  static double similarity(String a, String b) {
    Set<String> x = words(a);
    Set<String> y = words(b);
    if (x.isEmpty() || y.isEmpty())
      return 0;
    int common = 0;
    for (String w : x)
      if (y.contains(w))
        common++;
    return 2.0 * common / (x.size() + y.size());
  }

  /** A sentence of a document: its paragraph, its range in the text and its text. */
  static final class Sentence {
    final int p;
    final int start;
    final int end;
    final String text;

    Sentence(int p, int start, int end, String text) {
      this.p = p;
      this.start = start;
      this.end = end;
      this.text = text;
    }
  }

  private static final Pattern SENTENCE_END = Pattern.compile("[.!?;](?=\\s|$)");

  /** The sentences of the paragraphs (a paragraph without an end mark is one sentence). */
  static List<Sentence> sentences(DocumentText dt) {
    List<Sentence> out = new ArrayList<>();
    for (DocumentText.Paragraph p : dt.paragraphs) {
      String raw = dt.text.substring(p.start, p.end);
      int from = 0;
      Matcher m = SENTENCE_END.matcher(raw);
      while (true) {
        int to = m.find() ? m.end() : raw.length();
        add(out, dt, p, from, to);
        if (to >= raw.length())
          break;
        from = to;
      }
    }
    return out;
  }

  private static void add(List<Sentence> out, DocumentText dt, DocumentText.Paragraph p, int from, int to) {
    int s = p.start + from;
    int e = p.start + to;
    while (s < e && Character.isWhitespace(dt.text.charAt(s)))
      s++;
    while (e > s && Character.isWhitespace(dt.text.charAt(e - 1)))
      e--;
    if (s < e)
      out.add(new Sentence(p.index, s, e, dt.text.substring(s, e).replaceAll("[\\s\\u00a0]+", " ")));
  }

  /** The best sentence (or paragraph, for a text of several sentences) for a text, with its similarity. */
  private static Object[] best(DocumentText dt, List<Sentence> sentences, String text, String oldParagraph,
      List<int[]> taken) {
    double score = 0;
    double rank = -1;
    Sentence bestS = null;
    for (Sentence s : sentences) {
      if (overlaps(taken, s.start, s.end))
        continue;
      double sc = similarity(text, bare(text, s.text));
      // of two alike sentences, the one in a paragraph like the old one (its section)
      double r = sc + 0.15 * Math.min(1, context(dt, s.start, oldParagraph));
      if (sc > 0 && r > rank) {
        rank = r;
        score = sc;
        bestS = s;
      }
    }
    for (DocumentText.Paragraph p : dt.paragraphs) {
      if (overlaps(taken, p.start, p.end))
        continue;
      double sc = similarity(text, bare(text, plain(p.text)));
      if (sc > score + 0.05) {
        score = sc;
        int s = p.start;
        int e = p.end;
        while (s < e && Character.isWhitespace(dt.text.charAt(s)))
          s++;
        while (e > s && Character.isWhitespace(dt.text.charAt(e - 1)))
          e--;
        bestS = new Sentence(p.index, s, e, p.text);
      }
    }
    return bestS == null ? null : new Object[] { bestS, score };
  }

  /** Whether a range overlaps one of the ranges. */
  private static boolean overlaps(List<int[]> ranges, int start, int end) {
    for (int[] r : ranges)
      if (r[0] < end && start < r[1])
        return true;
    return false;
  }

  /** A new sentence without its clause number when the old text has none: the number is no changed word. */
  private static String bare(String old, String sentence) {
    return CLAUSE.matcher(old).find() ? sentence : CLAUSE.matcher(sentence).replaceFirst("");
  }

  /** A text with all white space as single spaces, for comparisons. */
  static String plain(String s) {
    return s.replaceAll("[\\s\\u00a0]+", " ").trim();
  }

  /**
   * The occurrence of a text (white space aside, whole words) in the new version: of several, the one in the paragraph
   * most like the old paragraph of the text, then the nearest to its relative position. A short text (a number, a
   * word) counts only in a paragraph like its old one. Null when there is none.
   */
  /** The places of a text in a document, whole words (white space between the words may differ). */
  private static List<int[]> occurrences(DocumentText dt, String text) {
    List<int[]> out = new ArrayList<>();
    StringBuilder re = new StringBuilder();
    for (String w : text.trim().split("[\\s\\u00a0]+")) {
      if (w.isEmpty())
        continue;
      if (re.length() > 0)
        re.append("[\\s\\u00a0]*");
      re.append(Pattern.quote(w));
    }
    if (re.length() == 0)
      return out;
    // whole words: "5" is not the end of "2025" (the text of the blocks has no separator between them: the start and
    // the end of a paragraph are the ends of a word too)
    String t = text.trim();
    boolean wordStart = Character.isLetterOrDigit(t.charAt(0));
    boolean wordEnd = Character.isLetterOrDigit(t.charAt(t.length() - 1));
    Matcher m = Pattern.compile(re.toString()).matcher(dt.text);
    while (m.find()) {
      DocumentText.Paragraph p = dt.paragraphAt(m.start());
      if (wordStart && m.start() > 0 && Character.isLetterOrDigit(dt.text.charAt(m.start() - 1))
          && !(p != null && dt.text.substring(p.start, m.start()).isBlank()))
        continue;
      DocumentText.Paragraph q = dt.paragraphAt(m.end() - 1);
      if (wordEnd && m.end() < dt.text.length() && Character.isLetterOrDigit(dt.text.charAt(m.end()))
          && !(q != null && dt.text.substring(m.end(), q.end).isBlank()))
        continue;
      out.add(new int[] { m.start(), m.end() });
    }
    return out;
  }

  /** How much the paragraph of a place in the new text is like an old paragraph: 2 when the same, else 0 to 1. */
  private static double context(DocumentText dt, int at, String oldParagraph) {
    DocumentText.Paragraph p = dt.paragraphAt(at);
    return p == null ? 0 : plain(p.text).equals(oldParagraph) ? 2 : similarity(oldParagraph, p.text);
  }

  /**
   * The place in the new version of the marked text at {@code oldAt} of the old version (-1: no position, an old
   * location without ranges), or null. A text can occur more than once: each old occurrence is given the new one with
   * the most similar paragraph (and the nearest position), one to one, so that a changed copy does not take the place of
   * a copy that stayed. The new occurrences that other old occurrences take go to {@code taken}.
   */
  private static int[] exact(DocumentText oldT, DocumentText dt, String text, String oldParagraph, int oldAt,
      List<int[]> taken) {
    List<int[]> news = occurrences(dt, text);
    if (news.isEmpty())
      return null;
    List<int[]> olds = oldAt < 0 ? new ArrayList<>() : occurrences(oldT, text);
    int mine = -1;
    for (int i = 0; i < olds.size(); i++)
      if (mine < 0 || Math.abs(olds.get(i)[0] - oldAt) < Math.abs(olds.get(mine)[0] - oldAt))
        mine = i;
    int[] best = null;
    double bestContext = -1;
    if (olds.size() <= 1) {
      // one old occurrence (or none known): the new one in the most similar paragraph, then the nearest
      double relative = oldAt < 0 ? 0.5 : (double) oldAt / Math.max(1, oldT.text.length());
      double dist = Double.MAX_VALUE;
      for (int[] n : news) {
        double c = context(dt, n[0], oldParagraph);
        double d = Math.abs((double) n[0] / Math.max(1, dt.text.length()) - relative);
        if (c > bestContext || c == bestContext && d < dist) {
          bestContext = c;
          dist = d;
          best = n;
        }
      }
    } else {
      // the pairs of an old and a new occurrence, the most alike first: each occurrence is taken once
      List<double[]> pairs = new ArrayList<>();
      for (int i = 0; i < olds.size(); i++) {
        DocumentText.Paragraph op = oldT.paragraphAt(olds.get(i)[0]);
        String para = op == null ? text : plain(op.text);
        double ro = (double) olds.get(i)[0] / Math.max(1, oldT.text.length());
        for (int j = 0; j < news.size(); j++) {
          double rn = (double) news.get(j)[0] / Math.max(1, dt.text.length());
          pairs.add(new double[] { context(dt, news.get(j)[0], para) + 0.1 * (1 - Math.abs(ro - rn)), i, j });
        }
      }
      pairs.sort((a, b) -> Double.compare(b[0], a[0]));
      boolean[] oldTaken = new boolean[olds.size()];
      boolean[] newTaken = new boolean[news.size()];
      for (double[] pr : pairs) {
        int i = (int) pr[1];
        int j = (int) pr[2];
        if (oldTaken[i] || newTaken[j])
          continue;
        oldTaken[i] = true;
        newTaken[j] = true;
        if (i == mine) {
          best = news.get(j);
          bestContext = context(dt, best[0], oldParagraph);
          break;
        }
        taken.add(news.get(j));
      }
    }
    if (best != null && words(text).size() < SHORT && bestContext < CONTEXT)
      return null;
    return best;
  }

  /** The sentences of a paragraph, as plain texts. */
  private static List<String> sentencesOf(List<Sentence> all, int p) {
    List<String> out = new ArrayList<>();
    for (Sentence s : all)
      if (s.p == p)
        out.add(plain(s.text));
    return out;
  }

  /** The plan for one location of the old document. */
  private static final class Move {
    final Location old;
    final List<Requirement> requirements = new ArrayList<>();
    final List<String> oldTexts = new ArrayList<>();
    final List<int[]> newRanges = new ArrayList<>();
    final List<String> newTexts = new ArrayList<>();
    double similarity = 1;
    int exact;
    int lost;
    boolean modalityChanged;
    boolean numbersChanged;
    /** The sentences that came into and went out of the paragraphs of a text that stayed the same. */
    final Set<String> added = new java.util.LinkedHashSet<>();
    final Set<String> removed = new java.util.LinkedHashSet<>();
    JsonArray suggestions = new JsonArray();
    Location created;
    /** The location has no ranges (an old catalog): its text is the description of the location. */
    boolean fromDescription;
    /** The headings of the sections of the old and the new place (null: none). */
    String oldSection;
    String newSection;

    /** The text is in a section of another heading (a renumbered heading is the same section). */
    boolean sectionChanged() {
      return oldSection != null && newSection != null && !unnumbered(oldSection).equals(unnumbered(newSection));
    }
    String descriptionText;

    Move(Location old) {
      this.old = old;
    }

    /**
     * unchanged: the same text in the same paragraph; context_changed: the same text, sentences came into or went out of
     * its paragraph; changed: moved to a similar sentence; lost: no similar sentence.
     */
    String status() {
      // a location of an old catalog with no text at all (no ranges, no description): nothing to move
      if (oldTexts.isEmpty())
        return "empty";
      if (newRanges.isEmpty())
        return "lost";
      if (lost == 0 && exact == oldTexts.size())
        return added.isEmpty() && removed.isEmpty() && !sectionChanged() ? "unchanged" : "context_changed";
      return "changed";
    }
  }

  /** The sections of the first place of a location that moved. */
  private static void sections(Move mv, DocumentText oldT, DocumentText newT, int oldAt, int newAt) {
    if (mv.newSection != null || mv.oldSection != null || oldAt < 0)
      return;
    mv.oldSection = section(oldT, oldAt);
    mv.newSection = section(newT, newAt);
  }

  /** The content words of a text in their order (not the common ones, not numbers: numbers_changed tells them), once each. */
  private static List<String> wordList(String s) {
    List<String> out = new ArrayList<>();
    for (String t : s.split("[^\\p{L}\\p{N}]+")) {
      String w = t.toLowerCase(Locale.ROOT);
      if (!w.isEmpty() && !COMMON.contains(w) && !w.matches("\\d+") && !out.contains(w))
        out.add(w);
    }
    return out;
  }

  /** A heading without its clause number. */
  static String unnumbered(String heading) {
    return plain(CLAUSE.matcher(plain(heading)).replaceFirst(""));
  }

  /** The text of the heading of the section of a place in a document (the nearest heading before it), or null. */
  static String section(DocumentText dt, int at) {
    DocumentText.Paragraph p = dt.paragraphAt(at);
    for (int i = p == null ? -1 : p.index; i >= 0; i--)
      if (dt.paragraphs.get(i).heading > 0)
        return dt.paragraphs.get(i).text;
    return null;
  }

  /** A clause number at the start of a sentence ("2.1 ", "3. "). */
  private static final Pattern CLAUSE = Pattern.compile("^\\(?\\d+(?:\\.\\d+)*[.)]?\\s+");

  /** The words of {@code a} that {@code b} does not have. */
  private static JsonArray missing(String a, String b) {
    List<String> other = wordList(b);
    JsonArray out = new JsonArray();
    for (String w : wordList(a))
      if (!other.contains(w))
        out.add(w);
    return out;
  }

  /**
   * Moves the locations in the open transaction. {@code detachOld}: the requirements keep no location in the old
   * document (it stays, as the history).
   */
  static JsonObject run(TreeDB db, Document oldDoc, Document newDoc, boolean detachOld) throws Exception {
    if (oldDoc == newDoc)
      throw new ToolError("The old and the new document are the same node");
    DocumentText oldT = DocumentText.of(oldDoc);
    DocumentText newT = DocumentText.of(newDoc);
    if (!newT.locations.isEmpty())
      throw new ToolError(newDoc.getQualifiedId() + " has locations already (an update made it?); give a new version without "
          + "locations");
    if (oldT.locations.isEmpty())
      throw new ToolError(oldDoc.getQualifiedId() + " has no locations: nothing to move. Import the new version with "
          + "import_document");
    List<Sentence> sentences = sentences(newT);
    List<Sentence> oldSentences = sentences(oldT);
    List<Move> moves = new ArrayList<>();
    boolean owned = false;
    for (Location l : oldT.locations) {
      Move mv = new Move(l);
      for (Requirement r : l.getRequirements(false, true))
        if (r != null && !r.isDisposed())
          mv.requirements.add(r);
      if (mv.requirements.isEmpty())
        continue; // a location of no requirement does not move
      owned = true;
      List<int[]> frags = DocumentText.fragments(l);
      if (frags.isEmpty()) {
        // a location of an old catalog without ranges: its description has its text (no position in the document)
        String d = l.getDescription();
        String text = d == null ? "" : Core.text(d).replaceAll("[\\s\\u00a0]+", " ").trim();
        // a description without a letter or a digit (".") names no text: the location is empty
        if (text.matches(".*[\\p{L}\\p{N}].*")) {
          mv.fromDescription = true;
          frags = new ArrayList<>();
          frags.add(new int[] { -1, -1 });
          mv.descriptionText = text;
        }
      }
      for (int[] f : frags) {
        String text;
        if (f[0] == -1) {
          text = mv.descriptionText;
        } else {
          if (f[0] < 0 || f[1] > oldT.text.length())
            continue;
          text = oldT.text.substring(f[0], f[1]).replaceAll("[\\s\\u00a0]+", " ").trim();
        }
        if (text.isEmpty())
          continue;
        mv.oldTexts.add(text);
        DocumentText.Paragraph oldP = f[0] < 0 ? null : oldT.paragraphAt(f[0]);
        String oldPara = oldP == null ? text : plain(oldP.text);
        List<int[]> taken = new ArrayList<>();
        int[] ex = exact(oldT, newT, text, oldPara, f[0], taken);
        if (ex != null) {
          mv.exact++;
          mv.newRanges.add(ex);
          sections(mv, oldT, newT, f[0], ex[0]);
          mv.newTexts.add(plain(newT.text.substring(ex[0], ex[1])));
          // the same text in a changed paragraph: the sentences around it can change its meaning
          DocumentText.Paragraph newP = newT.paragraphAt(ex[0]);
          if (oldP != null && newP != null && !plain(newP.text).equals(oldPara)) {
            List<String> before = sentencesOf(oldSentences, oldP.index);
            List<String> after = sentencesOf(sentences, newP.index);
            for (String x : after)
              if (!before.contains(x))
                mv.added.add(x);
            for (String x : before)
              if (!after.contains(x))
                mv.removed.add(x);
          }
          continue;
        }
        // the places of the other copies of the text are not free
        Object[] b = best(newT, sentences, text, oldPara, taken);
        double sc = b == null ? 0 : (double) b[1];
        if (b != null && sc >= MOVE) {
          Sentence s = (Sentence) b[0];
          int start = s.start;
          String newText = s.text;
          // the clause number of the new sentence is not part of the requirement (the old text had none)
          Matcher cl = CLAUSE.matcher(s.text);
          if (cl.find() && !CLAUSE.matcher(text).find()) {
            String raw = newT.text.substring(s.start, s.end);
            Matcher rawCl = CLAUSE.matcher(raw.stripLeading());
            if (rawCl.find()) {
              start = s.start + (raw.length() - raw.stripLeading().length()) + rawCl.end();
              newText = s.text.substring(cl.end());
            }
          }
          mv.newRanges.add(new int[] { start, s.end });
          mv.newTexts.add(newText);
          sections(mv, oldT, newT, f[0], start);
          mv.similarity = Math.min(mv.similarity, sc);
          mv.modalityChanged |= !modal(text).equals(modal(newText));
          mv.numbersChanged |= !numbers(text).equals(numbers(newText));
        } else {
          mv.lost++;
          // the sentences of the new version most like the lost text: where it can be in other words
          List<Object[]> scored = new ArrayList<>();
          for (Sentence s : sentences) {
            double x = similarity(text, bare(text, s.text));
            if (x >= SUGGEST)
              scored.add(new Object[] { s, x });
          }
          scored.sort((x, y) -> Double.compare((double) y[1], (double) x[1]));
          for (Object[] x : scored.subList(0, Math.min(3, scored.size()))) {
            Sentence s = (Sentence) x[0];
            JsonObject sug = new JsonObject();
            sug.addProperty("p", s.p);
            sug.addProperty("quote", s.text);
            sug.addProperty("similarity", Math.round((double) x[1] * 100) / 100.0);
            mv.suggestions.add(sug);
          }
        }
      }
      moves.add(mv);
    }
    if (!owned)
      throw new ToolError("The locations of " + oldDoc.getQualifiedId() + " belong to no requirement (was it updated already, "
          + "with detach_old?): nothing to move");
    // one new location for each set of ranges: the old locations that move to the same text share it
    Map<String, Location> byRanges = new java.util.HashMap<>();
    for (Move mv : moves) {
      if (mv.newRanges.isEmpty())
        continue;
      List<Object> frags = new ArrayList<>();
      Set<String> seen = new HashSet<>();
      for (int[] r : mv.newRanges)
        if (seen.add(r[0] + Location.FRAGMENT_SEPARATOR + r[1]))
          frags.add(r[0] + Location.FRAGMENT_SEPARATOR + r[1]);
      frags.sort(Location.FRAGMENT_SORTER);
      String key = frags.toString();
      Location l = byRanges.get(key);
      if (l == null) {
        l = newDoc.createLocation(null);
        l.attrs().setConstAttribute(Location.ATTR_MARKUP, new ListValue(AttributeType.STRING, frags), Flag.HIDDEN);
        l.setDescription("<p>" + Changes.escape(String.join(" ", new java.util.LinkedHashSet<>(mv.newTexts))) + "</p>");
        l.commitAttributes();
        byRanges.put(key, l);
      }
      for (Requirement r : mv.requirements)
        if (!r.getLocations(false).contains(l)) {
          r.addLocation(l.getUUId());
          r.commitAttributes();
        }
      mv.created = l;
    }
    if (detachOld)
      for (Move mv : moves)
        for (Requirement r : mv.requirements) {
          r.removeLocation(mv.old);
          r.commitAttributes();
        }
    // the result for each requirement (a requirement can have several locations in the document)
    Map<Requirement, List<Move>> byReq = new LinkedHashMap<>();
    for (Move mv : moves)
      for (Requirement r : mv.requirements)
        byReq.computeIfAbsent(r, k -> new ArrayList<>()).add(mv);
    JsonArray reqs = new JsonArray();
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (String k : new String[] { "unchanged", "context_changed", "changed", "lost" })
      counts.put(k, 0);
    for (Map.Entry<Requirement, List<Move>> e : byReq.entrySet()) {
      Requirement r = e.getKey();
      Set<String> st = new HashSet<>();
      for (Move mv : e.getValue())
        st.add(mv.status());
      // a location without text tells nothing about the requirement
      st.remove("empty");
      if (st.isEmpty())
        st.add("unchanged");
      // the status of the requirement: the one of its locations, or the worst one that is not lost when some moved
      String status = st.size() == 1 ? st.iterator().next()
          : st.contains("changed") || st.contains("lost") && (st.contains("unchanged") || st.contains("context_changed"))
              ? "changed" : "context_changed";
      counts.merge(status, 1, Integer::sum);
      JsonObject o = new JsonObject();
      o.addProperty("requirement", r.getQualifiedId());
      o.addProperty("uuid", r.getSUUId());
      if (r.getName() != null && !r.getName().isEmpty())
        o.addProperty("name", r.getName());
      o.addProperty("status", status);
      if (!"unchanged".equals(status)) {
        // its own description, which may need the change too (no description: the text is the one of the location)
        com.unitesk.requality.core.attribute.Attribute da = r.attrs().getAttribute(com.unitesk.requality.core.model.TreeNode.ATTR_DESCRIPTION);
        String d = da == null || da.getValue() == null || !da.isLocal(r.getUUId()) ? null : Core.text(da.getValue().toString());
        if (d != null && !d.isEmpty())
          o.addProperty("description", d.length() > 300 ? d.substring(0, 300) + " …" : d);
      }
      JsonArray locs = new JsonArray();
      for (Move mv : e.getValue()) {
        JsonObject lo = new JsonObject();
        lo.addProperty("status", mv.status());
        lo.addProperty("old_location", mv.old.getSUUId());
        if (mv.fromDescription)
          lo.addProperty("from_description", true);
        if (mv.oldTexts.isEmpty())
          lo.addProperty("note", "The old location has no text: nothing moved");
        if (!"unchanged".equals(mv.status()) && !mv.oldTexts.isEmpty()) {
          JsonArray ot = new JsonArray();
          mv.oldTexts.forEach(ot::add);
          lo.add("old_text", ot);
        }
        if (mv.created != null) {
          lo.addProperty("new_location", mv.created.getSUUId());
          // its paragraph in the new version (read_document)
          DocumentText.Paragraph np = mv.newRanges.isEmpty() ? null : newT.paragraphAt(mv.newRanges.get(0)[0]);
          if (np != null)
            lo.addProperty("p", np.index);
          // another section: the text went to another part of the specification (check that it is the right one)
          if (mv.sectionChanged()) {
            JsonObject sec = new JsonObject();
            sec.addProperty("old", mv.oldSection);
            sec.addProperty("new", mv.newSection);
            lo.add("section_changed", sec);
          }
          String newText = String.join(" … ", new java.util.LinkedHashSet<>(mv.newTexts));
          if (!"unchanged".equals(mv.status()))
            lo.addProperty("new_text", newText);
          if ("changed".equals(mv.status())) {
            lo.addProperty("similarity", Math.round(mv.similarity * 100) / 100.0);
            // the words that went and came: a changed word can change the meaning at a high similarity
            String oldText = String.join(" ", mv.oldTexts);
            lo.add("words_removed", missing(oldText, newText));
            lo.add("words_added", missing(newText, oldText));
            if (mv.modalityChanged)
              lo.addProperty("modality_changed", true);
            if (mv.numbersChanged)
              lo.addProperty("numbers_changed", true);
          }
          if (!mv.added.isEmpty()) {
            JsonArray x = new JsonArray();
            mv.added.forEach(x::add);
            lo.add("paragraph_added", x);
          }
          if (!mv.removed.isEmpty()) {
            JsonArray x = new JsonArray();
            mv.removed.forEach(x::add);
            lo.add("paragraph_removed", x);
          }
        }
        if (mv.suggestions.size() > 0)
          lo.add("suggestions", mv.suggestions);
        locs.add(lo);
      }
      o.add("locations", locs);
      reqs.add(o);
    }
    JsonObject o = new JsonObject();
    o.addProperty("old_document", oldDoc.getQualifiedId());
    o.addProperty("new_document", newDoc.getQualifiedId());
    JsonObject stats = new JsonObject();
    stats.addProperty("requirements", byReq.size());
    for (Map.Entry<String, Integer> c : counts.entrySet())
      stats.addProperty(c.getKey(), c.getValue());
    o.add("summary", stats);
    // the lost and changed ones first: they need a review
    JsonArray sorted = new JsonArray();
    for (String st : new String[] { "lost", "changed", "context_changed", "unchanged" })
      for (int i = 0; i < reqs.size(); i++)
        if (st.equals(reqs.get(i).getAsJsonObject().get("status").getAsString()))
          sorted.add(reqs.get(i));
    // the sentences of the new version that the old one does not have and no requirement marks now: candidates for
    // new requirements (statement: with a word of a requirement, such as shall or must)
    Set<String> old = new HashSet<>();
    for (Sentence s : oldSentences)
      old.add(plain(s.text));
    DocumentText after = DocumentText.of(newDoc);
    JsonArray fresh = new JsonArray();
    for (Sentence s : sentences(after)) {
      if (old.contains(plain(s.text)))
        continue;
      boolean covered = false;
      for (Location l : after.locations)
        for (int[] f : DocumentText.fragments(l))
          covered |= f[0] < s.end && f[1] > s.start;
      if (!covered) {
        JsonObject n = new JsonObject();
        n.addProperty("p", s.p);
        n.addProperty("quote", s.text);
        n.addProperty("statement", STATEMENT.matcher(s.text).find());
        fresh.add(n);
      }
    }
    stats.addProperty("new_sentences", fresh.size());
    o.add("new_sentences", fresh);
    o.add("requirements", sorted);
    o.addProperty("note", (detachOld ? "The requirements have no locations in the old document now; the document stays as "
        + "the history (superseded_by names the new version). " : "The requirements keep their locations in the old document "
        + "too (detach_old false). ") + "Check the changed requirements (words_removed and words_added, modality_changed, "
        + "numbers_changed) and the ones whose paragraph changed (context_changed), fix the lost ones, and add requirements "
        + "for the new sentences that are statements, with apply_changes.");
    return o;
  }

  /**
   * The result of an update for the agent: the summary, the new sentences and the requirements to check (not the
   * unchanged ones, unless {@code unchanged}), at most {@code limit} of each list. The history keeps all.
   */
  static JsonObject brief(JsonObject full, boolean unchanged, int limit, int change) {
    JsonObject o = new JsonObject();
    for (String k : new String[] { "old_document", "new_document", "summary" })
      o.add(k, full.get(k));
    JsonArray fresh = full.getAsJsonArray("new_sentences");
    o.add("new_sentences", first(fresh, limit));
    JsonArray check = new JsonArray();
    for (com.google.gson.JsonElement r : full.getAsJsonArray("requirements"))
      if (unchanged || !"unchanged".equals(r.getAsJsonObject().get("status").getAsString()))
        check.add(r);
    o.add("requirements", first(check, limit));
    List<String> more = new ArrayList<>();
    if (fresh.size() > limit)
      more.add((fresh.size() - limit) + " more new sentences");
    if (check.size() > limit)
      more.add((check.size() - limit) + " more requirements");
    if (!more.isEmpty())
      o.addProperty("more", String.join(", ", more) + ": review_changes with change " + change + " and offset " + limit
          + " lists the next ones");
    if (!unchanged && full.getAsJsonObject("summary").get("unchanged").getAsInt() > 0)
      o.addProperty("unchanged", "The unchanged requirements are not listed (include_unchanged true lists them)");
    o.add("note", full.get("note"));
    return o;
  }

  static JsonArray first(JsonArray a, int n) {
    if (a.size() <= n)
      return a;
    JsonArray out = new JsonArray();
    for (int i = 0; i < n; i++)
      out.add(a.get(i));
    return out;
  }
}
