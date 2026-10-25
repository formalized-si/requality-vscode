package com.unitesk.requality.vscode.mcp;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.vscode.mcp.McpServer.ToolError;

/**
 * The text of a document as the Document Editor marks it: the text content of the document body (the markup of a
 * location gives ranges of characters of this text), split into paragraphs (the text of one block element), and the
 * coverage of the paragraphs by the locations of the document.
 */
final class DocumentText {
  /** The elements whose text is a paragraph. */
  private static final Set<String> BLOCKS = new HashSet<>(Arrays.asList("p", "li", "td", "th", "h1", "h2", "h3", "h4", "h5",
      "h6", "pre", "div", "dd", "dt", "caption", "blockquote", "figcaption", "address", "body"));

  /** A paragraph: the range of its characters in the text. */
  static final class Paragraph {
    final int index;
    final int start;
    final int end;
    final String text;
    int covered;
    final Set<Location> locations = new LinkedHashSet<>();

    Paragraph(int index, int start, int end, String text) {
      this.index = index;
      this.start = start;
      this.end = end;
      this.text = text;
    }

    /** Characters that are not white space. */
    int weight() {
      return text.replaceAll("\\s", "").length();
    }

    String coverage() {
      int w = weight();
      if (covered == 0 || w == 0)
        return "none";
      return covered * 10 >= w * 9 ? "full" : "partial";
    }
  }

  final Document doc;
  final String text;
  final List<Paragraph> paragraphs = new ArrayList<>();
  final List<Location> locations = new ArrayList<>();

  private DocumentText(Document doc, String text) {
    this.doc = doc;
    this.text = text;
  }

  static Document document(TreeNode n) {
    if (!(n instanceof Document))
      throw new ToolError(n.getQualifiedId() + " is not a document (a " + n.getType() + ")");
    return (Document) n;
  }

  /** Reads the document: its text, paragraphs, locations and the coverage of the paragraphs. */
  static DocumentText of(Document doc) throws Exception {
    String html;
    try (InputStream in = doc.getResourceContent(doc.getResourceName())) {
      if (in == null)
        throw new ToolError(doc.getQualifiedId() + " has no content");
      html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    // XML and HTML parsers (the SAX text of the core, the DOM of the Document Editor) read CR LF and CR as LF: the same
    // offsets on Windows, where the imported documents have CR LF
    html = html.replace("\r\n", "\n").replace('\r', '\n');
    org.jsoup.nodes.Document d = Jsoup.parse(html);
    StringBuilder all = new StringBuilder();
    List<int[]> runs = new ArrayList<>();
    List<Element> owners = new ArrayList<>();
    NodeTraversor.traverse(new NodeVisitor() {
      @Override
      public void head(Node n, int depth) {
        String s = n instanceof TextNode ? ((TextNode) n).getWholeText() : n instanceof DataNode ? ((DataNode) n).getWholeData() : null;
        if (s == null || s.isEmpty())
          return;
        Element block = n.parent() instanceof Element ? (Element) n.parent() : null;
        while (block != null && !BLOCKS.contains(block.normalName()))
          block = block.parent();
        int start = all.length();
        all.append(s);
        boolean data = n instanceof DataNode;
        if (data)
          return; // script and style text counts for the offsets, it is no paragraph
        if (!runs.isEmpty() && owners.get(owners.size() - 1) == block && runs.get(runs.size() - 1)[1] == start)
          runs.get(runs.size() - 1)[1] = all.length();
        else {
          runs.add(new int[] { start, all.length() });
          owners.add(block);
        }
      }

      @Override
      public void tail(Node n, int depth) {
      }
    }, d.body());
    DocumentText dt = new DocumentText(doc, all.toString());
    for (int[] r : runs) {
      // the line breaks inside a block are the ones of the HTML source: single spaces
      String t = dt.text.substring(r[0], r[1]).replaceAll("[\\s\\u00a0]+", " ").trim();
      if (!t.isEmpty())
        dt.paragraphs.add(new Paragraph(dt.paragraphs.size(), r[0], r[1], t));
    }
    for (TreeNode c : doc.getWorkChildren())
      if (c instanceof Location && !c.isDisposed())
        dt.locations.add((Location) c);
    for (Location l : dt.locations)
      for (int[] f : fragments(l))
        for (Paragraph p : dt.paragraphs) {
          int from = Math.max(p.start, f[0]);
          int to = Math.min(p.end, f[1]);
          if (from < to) {
            p.covered += dt.text.substring(from, to).replaceAll("\\s", "").length();
            p.locations.add(l);
          }
        }
    for (Paragraph p : dt.paragraphs)
      p.covered = Math.min(p.covered, p.weight());
    return dt;
  }

  /** The distinct ranges of a location (old catalogs repeat them; a converted location can have none). */
  static List<int[]> fragments(Location l) {
    List<int[]> out = new ArrayList<>();
    if (l.attrs().getListAttributeValue(Location.ATTR_MARKUP) == null)
      return out;
    Set<String> seen = new HashSet<>();
    for (int[] f : l.getFragments())
      if (f[0] <= f[1] && seen.add(f[0] + "|" + f[1]))
        out.add(f);
    return out;
  }

  /** The marked text of a location, white space as single spaces. */
  String marked(Location l) {
    StringBuilder b = new StringBuilder();
    for (int[] f : fragments(l))
      if (f[0] >= 0 && f[1] <= text.length()) {
        if (b.length() > 0)
          b.append(" … ");
        b.append(text, f[0], f[1]);
      }
    return b.toString().replaceAll("[\\s\\u00a0]+", " ").trim();
  }

  /** The paragraph that contains a character, or null. */
  Paragraph paragraphAt(int pos) {
    for (Paragraph p : paragraphs)
      if (p.start <= pos && pos < p.end)
        return p;
    return null;
  }

  /** Characters (not white space) of all paragraphs and of the covered parts. */
  int[] coverageTotals() {
    int all = 0;
    int covered = 0;
    for (Paragraph p : paragraphs) {
      all += p.weight();
      covered += p.covered;
    }
    return new int[] { covered, all };
  }

  static List<String> requirements(Location l) {
    List<String> out = new ArrayList<>();
    for (Requirement r : l.getRequirements(false, true))
      if (r != null && !r.isDisposed())
        out.add(r.getQualifiedId());
    return out;
  }

  JsonObject paragraph(Paragraph p) {
    JsonObject o = new JsonObject();
    o.addProperty("p", p.index);
    o.addProperty("start", p.start);
    o.addProperty("end", p.end);
    o.addProperty("coverage", p.coverage());
    o.addProperty("text", p.text);
    if (!p.locations.isEmpty()) {
      JsonArray reqs = new JsonArray();
      Set<String> seen = new HashSet<>();
      for (Location l : p.locations)
        for (String r : requirements(l))
          if (seen.add(r))
            reqs.add(r);
      o.add("requirements", reqs);
    }
    return o;
  }

  JsonObject location(Location l) {
    JsonObject o = new JsonObject();
    o.addProperty("uuid", l.getSUUId());
    o.addProperty("document", doc.getQualifiedId());
    JsonArray ranges = new JsonArray();
    for (int[] f : fragments(l)) {
      JsonArray r = new JsonArray();
      r.add(f[0]);
      r.add(f[1]);
      ranges.add(r);
      Paragraph p = paragraphAt(f[0]);
      if (p != null && !o.has("p"))
        o.addProperty("p", p.index);
    }
    o.add("ranges", ranges);
    o.addProperty("text", marked(l));
    JsonArray reqs = new JsonArray();
    requirements(l).forEach(reqs::add);
    o.add("requirements", reqs);
    return o;
  }

  /**
   * The range of a quote: its words with any white space (or none, between block elements) between them; in one
   * paragraph when {@code paragraph} is not negative; the {@code occurrence}-th match (1 = first, 0 = the only one).
   */
  int[] find(String quote, int paragraph, int occurrence) {
    StringBuilder re = new StringBuilder();
    for (String w : quote.trim().split("[\\s\\u00a0]+")) {
      if (w.isEmpty())
        continue;
      if (re.length() > 0)
        re.append("[\\s\\u00a0]*");
      re.append(java.util.regex.Pattern.quote(w));
    }
    if (re.length() == 0)
      throw new ToolError("Empty quote");
    int from = 0;
    int to = text.length();
    if (paragraph >= 0) {
      if (paragraph >= paragraphs.size())
        throw new ToolError("No paragraph " + paragraph + " (the document has " + paragraphs.size() + ")");
      from = paragraphs.get(paragraph).start;
      to = paragraphs.get(paragraph).end;
    }
    List<int[]> found = new ArrayList<>();
    java.util.regex.Matcher m = java.util.regex.Pattern.compile(re.toString()).matcher(text);
    m.region(from, to);
    while (m.find())
      found.add(new int[] { m.start(), m.end() });
    if (found.isEmpty())
      throw new ToolError("The quote does not occur in " + (paragraph >= 0 ? "paragraph " + paragraph + " of " : "")
          + doc.getQualifiedId() + " (see read_document)");
    if (occurrence == 0 && found.size() > 1) {
      List<Integer> ps = new ArrayList<>();
      for (int[] f : found) {
        Paragraph p = paragraphAt(f[0]);
        ps.add(p == null ? -1 : p.index);
      }
      throw new ToolError("The quote occurs " + found.size() + " times (paragraphs " + ps + "); give 'paragraph' or 'occurrence'");
    }
    if (occurrence > found.size())
      throw new ToolError("The quote occurs only " + found.size() + " times");
    return found.get(Math.max(occurrence, 1) - 1);
  }
}
