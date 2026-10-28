package com.unitesk.requality.vscode.mcp;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
  /** The elements whose text is no text of the document (it counts for the offsets). */
  private static final Set<String> SCRIPTS = new HashSet<>(Arrays.asList("script", "style"));

  /** The elements whose text is a paragraph. */
  private static final Set<String> BLOCKS = new HashSet<>(Arrays.asList("p", "li", "td", "th", "h1", "h2", "h3", "h4", "h5",
      "h6", "pre", "div", "dd", "dt", "caption", "blockquote", "figcaption", "address", "body"));

  /** A paragraph: the range of its characters in the text. */
  static final class Paragraph {
    final int index;
    final int start;
    final int end;
    final String text;
    /** The level of a heading (h1 to h6), 0 for other text. */
    final int heading;
    int covered;
    final Set<Location> locations = new LinkedHashSet<>();
    /** The sentences that no location covers (90% of their characters at least). */
    final List<String> uncovered = new ArrayList<>();
    /** The place of a paragraph in a table: the table (0 the first of the document), its row and column; -1: none. */
    int table = -1;
    int row = -1;
    int col = -1;
    /** The alternative texts (or file names) of the pictures in the paragraph. */
    final List<String> images = new ArrayList<>();

    Paragraph(int index, int start, int end, String text, int heading) {
      this.index = index;
      this.start = start;
      this.end = end;
      this.text = text;
      this.heading = heading;
    }

    /** Characters that are not white space. */
    int weight() {
      return text.replaceAll("\\s", "").length();
    }

    /** none: no location covers the paragraph; full: locations cover each of its sentences; else partial. */
    String coverage() {
      if (covered == 0 || weight() == 0)
        return "none";
      return uncovered.isEmpty() ? "full" : "partial";
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
    // An imported document is XHTML, and the core counts the offsets in its XML text: the XML parser reads it the same
    // way (an HTML parser drops the first line break of a <pre> and reads <script/> as an open element). Content that
    // is no XML is read as HTML.
    org.jsoup.nodes.Document d = Jsoup.parse(html, "", org.jsoup.parser.Parser.xmlParser());
    Element body = d.selectFirst("body");
    if (body == null) {
      d = Jsoup.parse(html);
      body = d.body();
    }
    StringBuilder all = new StringBuilder();
    List<int[]> runs = new ArrayList<>();
    List<Element> owners = new ArrayList<>();
    NodeTraversor.traverse(new NodeVisitor() {
      @Override
      public void head(Node n, int depth) {
        String s = n instanceof TextNode ? ((TextNode) n).getWholeText() : n instanceof DataNode ? ((DataNode) n).getWholeData() : null;
        if (s == null || s.isEmpty())
          return;
        Element parent = n.parent() instanceof Element ? (Element) n.parent() : null;
        Element block = parent;
        while (block != null && !BLOCKS.contains(block.normalName()))
          block = block.parent();
        int start = all.length();
        all.append(s);
        boolean data = n instanceof DataNode || parent != null && SCRIPTS.contains(parent.normalName());
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
    }, body);
    DocumentText dt = new DocumentText(doc, all.toString());
    // the tables of the document in their order (a paragraph tells its table, row and column)
    Map<Element, Integer> tables = new java.util.HashMap<>();
    for (Element t : body.getElementsByTag("table"))
      tables.put(t, tables.size());
    for (int i = 0; i < runs.size(); i++) {
      int[] r = runs.get(i);
      // the line breaks inside a block are the ones of the HTML source: single spaces
      String t = dt.text.substring(r[0], r[1]).replaceAll("[\\s\\u00a0]+", " ").trim();
      Element owner = owners.get(i);
      String name = owner == null ? "" : owner.normalName();
      int heading = name.length() == 2 && name.charAt(0) == 'h' && Character.isDigit(name.charAt(1)) ? name.charAt(1) - '0' : 0;
      if (!t.isEmpty()) {
        Paragraph para = new Paragraph(dt.paragraphs.size(), r[0], r[1], t, heading);
        place(para, owner, tables);
        dt.paragraphs.add(para);
      }
    }
    for (TreeNode c : doc.getWorkChildren())
      if (c instanceof Location && !c.isDisposed())
        dt.locations.add((Location) c);
    // a location of no requirement (left in an older version of a document by an update) covers nothing
    List<Location> owned = new ArrayList<>();
    for (Location l : dt.locations)
      if (l.getRequirements(false, false).length > 0)
        owned.add(l);
    for (Location l : owned)
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
    // the coverage of each sentence: a paragraph is covered when each of its sentences is
    boolean[] mask = new boolean[dt.text.length()];
    for (Location l : owned)
      for (int[] f : fragments(l))
        for (int i = Math.max(0, f[0]); i < Math.min(mask.length, f[1]); i++)
          mask[i] = true;
    for (SpecUpdate.Sentence st : SpecUpdate.sentences(dt)) {
      int weight = 0;
      int covered = 0;
      for (int i = st.start; i < st.end; i++) {
        if (Character.isWhitespace(dt.text.charAt(i)) || dt.text.charAt(i) == '\u00a0')
          continue;
        weight++;
        if (mask[i])
          covered++;
      }
      if (weight > 0 && covered * 10 < weight * 9)
        dt.paragraphs.get(st.p).uncovered.add(st.text);
    }
    return dt;
  }

  /** The table, row and column of a paragraph in a table cell, and the pictures of its block. */
  private static void place(Paragraph p, Element owner, Map<Element, Integer> tables) {
    if (owner == null)
      return;
    for (Element img : owner.getElementsByTag("img")) {
      String alt = img.attr("alt").trim();
      String src = img.attr("src").trim();
      String name = !alt.isEmpty() ? alt : src.isEmpty() ? "" : src.substring(src.lastIndexOf('/') + 1);
      if (!name.isEmpty())
        p.images.add(name);
    }
    Element cell = owner;
    while (cell != null && !cell.normalName().equals("td") && !cell.normalName().equals("th"))
      cell = cell.parent();
    if (cell == null)
      return;
    Element tr = cell.parent();
    while (tr != null && !tr.normalName().equals("tr"))
      tr = tr.parent();
    Element table = tr == null ? null : tr.parent();
    while (table != null && !table.normalName().equals("table"))
      table = table.parent();
    if (table == null || !tables.containsKey(table))
      return;
    p.table = tables.get(table);
    // the rows of this table, not of the tables in its cells
    int r = 0;
    for (Element x : table.getElementsByTag("tr")) {
      Element t = x.parent();
      while (t != null && !t.normalName().equals("table"))
        t = t.parent();
      if (t != table)
        continue;
      if (x == tr) {
        p.row = r;
        break;
      }
      r++;
    }
    int c = 0;
    for (Element x : tr.children()) {
      if (!x.normalName().equals("td") && !x.normalName().equals("th"))
        continue;
      if (x == cell) {
        p.col = c;
        break;
      }
      c += Math.max(1, parseInt(x.attr("colspan")));
    }
  }

  private static int parseInt(String s) {
    try {
      return Integer.parseInt(s.trim());
    } catch (NumberFormatException e) {
      return 1;
    }
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
    // the paragraphs are in the order of the text, their ranges do not overlap: the last one that starts at pos or before
    int lo = 0;
    int hi = paragraphs.size() - 1;
    Paragraph found = null;
    while (lo <= hi) {
      int mid = (lo + hi) >>> 1;
      Paragraph p = paragraphs.get(mid);
      if (p.start <= pos) {
        found = p;
        lo = mid + 1;
      } else {
        hi = mid - 1;
      }
    }
    return found != null && pos < found.end ? found : null;
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
    if (p.heading > 0)
      o.addProperty("heading", p.heading);
    if (p.table >= 0) {
      o.addProperty("table", p.table);
      o.addProperty("row", p.row);
      o.addProperty("col", p.col);
    }
    if (!p.images.isEmpty()) {
      JsonArray im = new JsonArray();
      p.images.forEach(im::add);
      o.add("images", im);
    }
    o.addProperty("coverage", p.coverage());
    o.addProperty("text", p.text);
    if ("partial".equals(p.coverage())) {
      JsonArray u = new JsonArray();
      p.uncovered.forEach(u::add);
      o.add("uncovered_text", u);
    }
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

  /** A character as the quote search compares it: typographic quotes and dashes as ASCII ones, letters in lower case. */
  static char fold(char c) {
    switch (c) {
      case '\u2018': case '\u2019': case '\u201a': case '\u201b': case '\u2032': case '`': case '\u00b4':
        return '\'';
      case '\u201c': case '\u201d': case '\u201e': case '\u201f': case '\u2033': case '\u00ab': case '\u00bb':
        return '"';
      case '\u2010': case '\u2011': case '\u2012': case '\u2013': case '\u2014': case '\u2015': case '\u2212':
        return '-';
      case '\u00a0': case '\u2007': case '\u202f':
        return ' ';
      default:
        return Character.toLowerCase(c);
    }
  }

  /** The text with each character folded (the same length: the offsets stay). */
  static String fold(String s) {
    char[] c = s.toCharArray();
    for (int i = 0; i < c.length; i++)
      c[i] = fold(c[i]);
    return new String(c);
  }

  private static java.util.regex.Pattern pattern(String quote) {
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
    return java.util.regex.Pattern.compile(re.toString());
  }

  /** The characters of a quote (not its white space) with any white space between them. */
  private static java.util.regex.Pattern loose(String quote) {
    StringBuilder re = new StringBuilder();
    for (char c : quote.toCharArray()) {
      if (Character.isWhitespace(c) || c == '\u00a0')
        continue;
      if (re.length() > 0)
        re.append("[\\s\\u00a0]*");
      re.append(java.util.regex.Pattern.quote(String.valueOf(c)));
    }
    if (re.length() == 0)
      throw new ToolError("Empty quote");
    return java.util.regex.Pattern.compile(re.toString());
  }

  private static List<int[]> matches(java.util.regex.Pattern p, String text, int from, int to) {
    List<int[]> found = new ArrayList<>();
    java.util.regex.Matcher m = p.matcher(text);
    m.region(from, to);
    while (m.find())
      found.add(new int[] { m.start(), m.end() });
    return found;
  }

  /**
   * The range of a quote: its words with any white space (or none, between block elements) between them; in one
   * paragraph when {@code paragraph} is not negative; the {@code occurrence}-th match (1 = first, 0 = the only one). A
   * quote that differs only in typographic quotes, dashes or letter case is found too.
   */
  int[] find(String quote, int paragraph, int occurrence) {
    int from = 0;
    int to = text.length();
    if (paragraph >= 0) {
      if (paragraph >= paragraphs.size())
        throw new ToolError("No paragraph " + paragraph + " (the document has " + paragraphs.size() + ")");
      from = paragraphs.get(paragraph).start;
      to = paragraphs.get(paragraph).end;
    }
    List<int[]> found = matches(pattern(quote), text, from, to);
    if (found.isEmpty())
      found = matches(pattern(fold(quote)), fold(text), from, to);
    // white space in other places ("10%" and "10 %")
    if (found.isEmpty())
      found = matches(loose(fold(quote)), fold(text), from, to);
    if (found.isEmpty())
      throw new ToolError("The quote does not occur in " + (paragraph >= 0 ? "paragraph " + paragraph + " of " : "")
          + doc.getQualifiedId() + closest(quote));
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

  /** The sentence of the document most like the quote, for the error of a quote that does not occur. */
  private String closest(String quote) {
    SpecUpdate.Sentence best = null;
    double score = 0;
    for (SpecUpdate.Sentence s : SpecUpdate.sentences(this)) {
      double sc = SpecUpdate.similarity(quote, s.text);
      if (sc > score) {
        score = sc;
        best = s;
      }
    }
    if (best == null || score < 0.3)
      return " (see read_document)";
    return ". The most similar text is in paragraph " + best.p + ": \"" + best.text + "\"";
  }
}
