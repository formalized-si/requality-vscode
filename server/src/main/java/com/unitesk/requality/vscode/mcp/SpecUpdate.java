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
  private static Object[] best(DocumentText dt, List<Sentence> sentences, String text) {
    double score = 0;
    Sentence bestS = null;
    for (Sentence s : sentences) {
      double sc = similarity(text, s.text);
      if (sc > score) {
        score = sc;
        bestS = s;
      }
    }
    for (DocumentText.Paragraph p : dt.paragraphs) {
      double sc = similarity(text, p.text);
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

  /** A text with all white space as single spaces, for comparisons. */
  static String plain(String s) {
    return s.replaceAll("[\\s\\u00a0]+", " ").trim();
  }

  /**
   * The occurrence of a text (white space aside, whole words) in the new version: of several, the one in the paragraph
   * most like the old paragraph of the text, then the nearest to its relative position. A short text (a number, a
   * word) counts only in a paragraph like its old one. Null when there is none.
   */
  private static int[] exact(DocumentText dt, String text, String oldParagraph, double relative) {
    StringBuilder re = new StringBuilder();
    for (String w : text.trim().split("[\\s\\u00a0]+")) {
      if (w.isEmpty())
        continue;
      if (re.length() > 0)
        re.append("[\\s\\u00a0]*");
      re.append(Pattern.quote(w));
    }
    if (re.length() == 0)
      return null;
    // whole words: "5" is not the end of "2025" (the text of the blocks has no separator between them: the start and
    // the end of a paragraph are the ends of a word too)
    String t = text.trim();
    boolean wordStart = Character.isLetterOrDigit(t.charAt(0));
    boolean wordEnd = Character.isLetterOrDigit(t.charAt(t.length() - 1));
    Matcher m = Pattern.compile(re.toString()).matcher(dt.text);
    int[] best = null;
    double bestContext = -1;
    double dist = Double.MAX_VALUE;
    while (m.find()) {
      DocumentText.Paragraph p = dt.paragraphAt(m.start());
      if (wordStart && m.start() > 0 && Character.isLetterOrDigit(dt.text.charAt(m.start() - 1))
          && !(p != null && dt.text.substring(p.start, m.start()).isBlank()))
        continue;
      DocumentText.Paragraph q = dt.paragraphAt(m.end() - 1);
      if (wordEnd && m.end() < dt.text.length() && Character.isLetterOrDigit(dt.text.charAt(m.end()))
          && !(q != null && dt.text.substring(m.end(), q.end).isBlank()))
        continue;
      double context = p == null ? 0 : plain(p.text).equals(oldParagraph) ? 2 : similarity(oldParagraph, p.text);
      double d = Math.abs((double) m.start() / Math.max(1, dt.text.length()) - relative);
      if (context > bestContext || context == bestContext && d < dist) {
        bestContext = context;
        dist = d;
        best = new int[] { m.start(), m.end() };
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

    Move(Location old) {
      this.old = old;
    }

    String status() {
      if (newRanges.isEmpty())
        return "lost";
      return lost == 0 && exact == oldTexts.size() && added.isEmpty() && removed.isEmpty() ? "unchanged" : "changed";
    }
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
      for (int[] f : DocumentText.fragments(l)) {
        if (f[0] < 0 || f[1] > oldT.text.length())
          continue;
        String text = oldT.text.substring(f[0], f[1]).replaceAll("[\\s\\u00a0]+", " ").trim();
        if (text.isEmpty())
          continue;
        mv.oldTexts.add(text);
        DocumentText.Paragraph oldP = oldT.paragraphAt(f[0]);
        String oldPara = oldP == null ? text : plain(oldP.text);
        int[] ex = exact(newT, text, oldPara, (double) f[0] / Math.max(1, oldT.text.length()));
        if (ex != null) {
          mv.exact++;
          mv.newRanges.add(ex);
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
        Object[] b = best(newT, sentences, text);
        double sc = b == null ? 0 : (double) b[1];
        if (b != null && sc >= MOVE) {
          Sentence s = (Sentence) b[0];
          mv.newRanges.add(new int[] { s.start, s.end });
          mv.newTexts.add(s.text);
          mv.similarity = Math.min(mv.similarity, sc);
          mv.modalityChanged |= !modal(text).equals(modal(s.text));
          mv.numbersChanged |= !numbers(text).equals(numbers(s.text));
        } else {
          mv.lost++;
          // the sentences of the new version most like the lost text: where it can be in other words
          List<Object[]> scored = new ArrayList<>();
          for (Sentence s : sentences) {
            double x = similarity(text, s.text);
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
    int unchanged = 0;
    int changed = 0;
    int lost = 0;
    for (Map.Entry<Requirement, List<Move>> e : byReq.entrySet()) {
      Requirement r = e.getKey();
      Set<String> st = new HashSet<>();
      for (Move mv : e.getValue())
        st.add(mv.status());
      String status = st.size() == 1 ? st.iterator().next() : st.contains("unchanged") || st.contains("changed") ? "changed" : "lost";
      switch (status) {
        case "unchanged":
          unchanged++;
          break;
        case "changed":
          changed++;
          break;
        default:
          lost++;
      }
      JsonObject o = new JsonObject();
      o.addProperty("requirement", r.getQualifiedId());
      o.addProperty("uuid", r.getSUUId());
      if (r.getName() != null)
        o.addProperty("name", r.getName());
      o.addProperty("status", status);
      if (!"unchanged".equals(status)) {
        // its own description, which may need the change too (no description: the text is the one of the location)
        com.unitesk.requality.core.attribute.Attribute da = r.attrs().getAttribute(com.unitesk.requality.core.model.TreeNode.ATTR_DESCRIPTION);
        String d = da == null || da.getValue() == null || !da.isLocal(r.getUUId()) ? null : Core.text(da.getValue().toString());
        o.addProperty("description", d == null || d.isEmpty() ? "" : d.length() > 300 ? d.substring(0, 300) + " …" : d);
      }
      JsonArray locs = new JsonArray();
      for (Move mv : e.getValue()) {
        JsonObject lo = new JsonObject();
        lo.addProperty("status", mv.status());
        lo.addProperty("old_location", mv.old.getSUUId());
        if (!"unchanged".equals(mv.status())) {
          JsonArray ot = new JsonArray();
          mv.oldTexts.forEach(ot::add);
          lo.add("old_text", ot);
        }
        if (mv.created != null) {
          lo.addProperty("new_location", mv.created.getSUUId());
          lo.addProperty("new_text", String.join(" … ", new java.util.LinkedHashSet<>(mv.newTexts)));
          if ("changed".equals(mv.status())) {
            if (mv.exact == mv.oldTexts.size() && mv.lost == 0)
              lo.addProperty("text_same", true);
            else
              lo.addProperty("similarity", Math.round(mv.similarity * 100) / 100.0);
            if (mv.modalityChanged)
              lo.addProperty("modality_changed", true);
            if (mv.numbersChanged)
              lo.addProperty("numbers_changed", true);
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
    stats.addProperty("unchanged", unchanged);
    stats.addProperty("changed", changed);
    stats.addProperty("lost", lost);
    o.add("summary", stats);
    // the changed and lost ones first: they need a review
    JsonArray sorted = new JsonArray();
    for (String s : new String[] { "lost", "changed", "unchanged" })
      for (int i = 0; i < reqs.size(); i++)
        if (s.equals(reqs.get(i).getAsJsonObject().get("status").getAsString()))
          sorted.add(reqs.get(i));
    o.add("requirements", sorted);
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
    o.add("new_sentences", fresh);
    o.addProperty("note", (detachOld ? "The requirements have no locations in the old document now; the document stays. "
        : "The requirements keep their locations in the old document too (detach_old false). ")
        + "Check the changed requirements (modality_changed, numbers_changed and the paragraph changes first), fix the lost "
        + "ones, and add requirements for the new sentences that are requirements, with apply_changes.");
    return o;
  }
}
