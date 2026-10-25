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
 * update_document_version: the requirement locations of a document move to its new version sentence by sentence. A
 * marked text found again in the new version moves unchanged; a text whose sentence changed moves to the most similar
 * sentence (status "changed", with its similarity); a text with no similar sentence is "lost". The old document and its
 * locations stay, as with the Update Document processor of Requality, whose character diff split the marked texts into
 * fragments of other sentences. The result also lists the uncovered statements of the new version.
 */
final class SpecUpdate {
  private SpecUpdate() {
  }

  /** The least similarity of a changed sentence; a suggestion for a lost text needs SUGGEST. */
  static final double MOVE = 0.6;
  static final double SUGGEST = 0.4;
  private static final Pattern STATEMENT = Pattern.compile("\\b(shall|must|should|required|requires|will)\\b",
      Pattern.CASE_INSENSITIVE);

  /** Words that every requirement statement has: they tell nothing about which sentence it is. */
  private static final Set<String> COMMON = new HashSet<>(Arrays.asList("a", "an", "the", "and", "or", "of", "to", "in", "on",
      "for", "with", "by", "at", "as", "from", "is", "are", "be", "been", "it", "its", "this", "that", "these", "those", "when",
      "if", "then", "shall", "must", "should", "will", "may", "can", "not", "no"));

  private static Set<String> words(String s) {
    Set<String> w = new HashSet<>();
    for (String t : s.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
      if (!t.isEmpty() && !COMMON.contains(t))
        w.add(t);
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

  /** The exact occurrence of a text (white space aside) nearest to the relative position, or null. */
  private static int[] exact(DocumentText dt, String text, double relative) {
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
    Matcher m = Pattern.compile(re.toString()).matcher(dt.text);
    int[] best = null;
    double dist = Double.MAX_VALUE;
    while (m.find()) {
      double d = Math.abs((double) m.start() / Math.max(1, dt.text.length()) - relative);
      if (d < dist) {
        dist = d;
        best = new int[] { m.start(), m.end() };
      }
    }
    return best;
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
    JsonArray suggestions = new JsonArray();
    Location created;

    Move(Location old) {
      this.old = old;
    }

    String status() {
      if (newRanges.isEmpty())
        return "lost";
      return lost == 0 && exact == oldTexts.size() ? "unchanged" : "changed";
    }
  }

  static JsonObject run(TreeDB db, Document oldDoc, Document newDoc) throws Exception {
    return run(db, oldDoc, newDoc, false);
  }

  /** {@code detachOld}: the requirements keep no location in the old document (it stays, as the history). */
  static JsonObject run(TreeDB db, Document oldDoc, Document newDoc, boolean detachOld) throws Exception {
    if (oldDoc == newDoc)
      throw new ToolError("The old and the new document are the same node");
    DocumentText oldT = DocumentText.of(oldDoc);
    DocumentText newT = DocumentText.of(newDoc);
    if (!newT.locations.isEmpty())
      throw new ToolError(newDoc.getQualifiedId() + " has locations already; give a new version without locations");
    List<Sentence> sentences = sentences(newT);
    List<Move> moves = new ArrayList<>();
    for (Location l : oldT.locations) {
      Move mv = new Move(l);
      for (Requirement r : l.getRequirements(false, true))
        if (r != null && !r.isDisposed())
          mv.requirements.add(r);
      if (mv.requirements.isEmpty())
        continue; // a location of no requirement does not move
      for (int[] f : DocumentText.fragments(l)) {
        if (f[0] < 0 || f[1] > oldT.text.length())
          continue;
        String text = oldT.text.substring(f[0], f[1]).replaceAll("[\\s\\u00a0]+", " ").trim();
        if (text.isEmpty())
          continue;
        mv.oldTexts.add(text);
        int[] ex = exact(newT, text, (double) f[0] / Math.max(1, oldT.text.length()));
        if (ex != null) {
          mv.exact++;
          mv.newRanges.add(ex);
          mv.newTexts.add(newT.text.substring(ex[0], ex[1]).replaceAll("[\\s\\u00a0]+", " ").trim());
          continue;
        }
        Object[] b = best(newT, sentences, text);
        double sc = b == null ? 0 : (double) b[1];
        if (b != null && sc >= MOVE) {
          Sentence s = (Sentence) b[0];
          mv.newRanges.add(new int[] { s.start, s.end });
          mv.newTexts.add(s.text);
          mv.similarity = Math.min(mv.similarity, sc);
        } else {
          mv.lost++;
          if (b != null && sc >= SUGGEST) {
            Sentence s = (Sentence) b[0];
            JsonObject sug = new JsonObject();
            sug.addProperty("p", s.p);
            sug.addProperty("quote", s.text);
            sug.addProperty("similarity", Math.round(sc * 100) / 100.0);
            mv.suggestions.add(sug);
          }
        }
      }
      moves.add(mv);
    }
    // one transaction: one undo step
    Core.write(db, "Update document version", () -> {
      for (Move mv : moves) {
        if (mv.newRanges.isEmpty())
          continue;
        Location l = newDoc.createLocation(null);
        List<Object> frags = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int[] r : mv.newRanges)
          if (seen.add(r[0] + Location.FRAGMENT_SEPARATOR + r[1]))
            frags.add(r[0] + Location.FRAGMENT_SEPARATOR + r[1]);
        frags.sort(Location.FRAGMENT_SORTER);
        l.attrs().setConstAttribute(Location.ATTR_MARKUP, new ListValue(AttributeType.STRING, frags), Flag.HIDDEN);
        l.setDescription("<p>" + Changes.escape(String.join(" ", new java.util.LinkedHashSet<>(mv.newTexts))) + "</p>");
        l.commitAttributes();
        for (Requirement r : mv.requirements) {
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
      return null;
    });
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
          if ("changed".equals(mv.status()))
            lo.addProperty("similarity", Math.round(mv.similarity * 100) / 100.0);
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
    // the statements of the new version that no requirement marks now: candidates for new requirements
    DocumentText after = DocumentText.of(newDoc);
    JsonArray fresh = new JsonArray();
    for (Sentence s : sentences(after)) {
      if (!STATEMENT.matcher(s.text).find())
        continue;
      boolean covered = false;
      for (Location l : after.locations)
        for (int[] f : DocumentText.fragments(l))
          covered |= f[0] < s.end && f[1] > s.start;
      if (!covered) {
        JsonObject n = new JsonObject();
        n.addProperty("p", s.p);
        n.addProperty("quote", s.text);
        fresh.add(n);
      }
    }
    o.add("uncovered_statements", fresh);
    o.addProperty("note", (detachOld ? "The requirements have no locations in the old document now; the document stays. "
        : "The old document and its locations stay (detach_old removes them from the requirements). ")
        + "Fix the changed and lost requirements and add the uncovered statements with apply_changes.");
    return o;
  }
}
