package com.unitesk.requality.vscode.mcp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import com.unitesk.requality.core.storage.OFlag;
import com.unitesk.requality.nodetype.Document;

/**
 * The headings of an imported PDF. The text of a PDF has no headings: a heading line and the paragraph after it come as
 * one paragraph with a line break ("2.2 Limits<br/>The pressure shall ..."), and the outline (bookmarks) comes as a list
 * at the end. A first line that is a numbered heading ("2.2 Limits", short, no end mark) or a title of the outline
 * becomes a heading of its level, and the rest of the paragraph a paragraph of its own; the outline list goes when each
 * of its titles is a heading of the text.
 */
final class PdfHeadings {
  private PdfHeadings() {
  }

  /** A numbered heading: "2", "2.2", "A.1" or "Annex B" style numbers, then a short title without an end mark. */
  private static final Pattern NUMBERED = Pattern.compile("^((?:\\d+|[A-Z])(?:\\.\\d+)*)\\.?\\s+(\\S.{0,118})$");

  /** Makes the headings of the document text (in the open transaction of the import). */
  static void recover(Document doc) throws IOException {
    String name = doc.getResourceName();
    byte[] content;
    try (InputStream in = doc.getResourceContent(name)) {
      if (in == null)
        return;
      content = in.readAllBytes();
    }
    org.jsoup.nodes.Document d = org.jsoup.Jsoup.parse(new String(content, StandardCharsets.UTF_8), "",
        org.jsoup.parser.Parser.xmlParser());
    Element body = d.selectFirst("body");
    if (body == null)
      return;
    // the outline: the last list of the body, outside the pages, with the titles by their nesting level
    Element outline = null;
    Map<String, Integer> titles = new HashMap<>();
    Element last = body.children().isEmpty() ? null : body.children().last();
    if (last != null && last.normalName().equals("ul")) {
      outline = last;
      collect(outline, 1, titles);
    }
    java.util.Set<String> found = new java.util.HashSet<>();
    boolean changed = false;
    for (Element p : new ArrayList<>(body.select("p"))) {
      if (outline != null && p.parents().contains(outline))
        continue;
      // the headings at the start of the paragraph, one line each ("2 Limits", "2.1 Pressure", then the text)
      while (true) {
        String line = firstLine(p, false);
        boolean whole = line == null;
        if (whole)
          line = p.text();
        line = line.replaceAll("[\\s\\u00a0]+", " ").trim();
        int level = level(line, titles);
        if (level == 0)
          break;
        found.add(key(line));
        Element h = new Element("h" + level);
        h.text(line);
        p.before(h);
        changed = true;
        if (whole) {
          p.remove();
          break;
        }
        firstLine(p, true);
        if (p.text().trim().isEmpty()) {
          p.remove();
          break;
        }
      }
    }
    if (outline != null && !titles.isEmpty() && found.containsAll(titles.keySet())) {
      outline.remove();
      changed = true;
    }
    if (!changed)
      return;
    d.outputSettings().syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml).prettyPrint(false);
    doc.setResourceContent(name, new ByteArrayInputStream(d.outerHtml().getBytes(StandardCharsets.UTF_8)), OFlag.NONE);
  }

  /**
   * The first line of a paragraph: its text up to the first line break or new line after some text; null when the
   * paragraph has one line. {@code remove}: the line and the break are taken out of the paragraph.
   */
  private static String firstLine(Element p, boolean remove) {
    StringBuilder t = new StringBuilder();
    boolean text = false;
    List<Node> taken = new ArrayList<>();
    for (Node n : new ArrayList<>(p.childNodes())) {
      if (n instanceof Element && ((Element) n).normalName().equals("br")) {
        taken.add(n);
        if (!text)
          continue;
        if (remove)
          taken.forEach(Node::remove);
        return t.toString();
      }
      if (n instanceof TextNode) {
        String s = ((TextNode) n).getWholeText();
        for (int i = 0; i < s.length(); i++) {
          char c = s.charAt(i);
          if (c == '\n' && text) {
            t.append(s, 0, i);
            if (remove) {
              taken.forEach(Node::remove);
              ((TextNode) n).text(s.substring(i + 1));
            }
            return t.toString();
          }
          if (!Character.isWhitespace(c))
            text = true;
        }
        t.append(s);
        taken.add(n);
        continue;
      }
      if (n instanceof Element) {
        t.append(((Element) n).text());
        text |= !((Element) n).text().isBlank();
      }
      taken.add(n);
    }
    return null;
  }

  private static void collect(Element list, int level, Map<String, Integer> titles) {
    for (Element li : list.children()) {
      if (!li.normalName().equals("li"))
        continue;
      String own = li.ownText().replaceAll("[\\s\\u00a0]+", " ").trim();
      if (!own.isEmpty())
        titles.putIfAbsent(key(own), Math.min(6, level));
      for (Element sub : li.children())
        if (sub.normalName().equals("ul"))
          collect(sub, level + 1, titles);
    }
  }

  private static String key(String s) {
    return s.toLowerCase(Locale.ROOT).replaceAll("[\\s\\u00a0]+", " ").trim();
  }

  /** The heading level of a first line (1 to 6), or 0 when it is no heading. */
  static int level(String line, Map<String, Integer> titles) {
    if (line.isEmpty() || line.length() > 120)
      return 0;
    Integer t = titles.get(key(line));
    if (t != null)
      return t;
    Matcher m = NUMBERED.matcher(line);
    if (!m.matches())
      return 0;
    String title = m.group(2);
    // a sentence is no heading: an end mark, many words, or a number only ("5 bar" in a table, a page number)
    if (title.matches(".*[.;:!?,]$") || title.split(" ").length > 12 || !title.matches(".*\\p{L}.*")
        || Character.isLowerCase(title.charAt(0)))
      return 0;
    int dots = 0;
    for (char c : m.group(1).toCharArray())
      if (c == '.')
        dots++;
    return Math.min(6, dots + 1);
  }
}
