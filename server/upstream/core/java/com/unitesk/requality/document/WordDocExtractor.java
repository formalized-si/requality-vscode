/*
 * Copyright 2015 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.unitesk.requality.document;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.OldWordFileFormatException;
import org.apache.poi.hwpf.extractor.Word6Extractor;
import org.apache.poi.hwpf.model.FieldsDocumentPart;
import org.apache.poi.hwpf.model.ListLevel;
import org.apache.poi.hwpf.model.PicturesTable;
import org.apache.poi.hwpf.model.StyleDescription;
import org.apache.poi.hwpf.sprm.ParagraphSprmUncompressor;
import org.apache.poi.hwpf.usermodel.Bookmark;
import org.apache.poi.hwpf.usermodel.BorderCode;
import org.apache.poi.hwpf.usermodel.CharacterRun;
import org.apache.poi.hwpf.usermodel.Field;
import org.apache.poi.hwpf.usermodel.HWPFList;
import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.hwpf.usermodel.ParagraphProperties;
import org.apache.poi.hwpf.usermodel.Picture;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.hwpf.usermodel.Table;
import org.apache.poi.hwpf.usermodel.TableCell;
import org.apache.poi.hwpf.usermodel.TableRow;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.OfficeParser.POIFSDocumentType;
import org.apache.tika.parser.microsoft.WordExtractor;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

public class WordDocExtractor extends WordExtractor {
  // some codes to support special characters
  private static final char UNICODECHAR_NONBREAKING_HYPHEN = '\u2011';
  private static final char UNICODECHAR_ZERO_WIDTH_SPACE = '\u200b';
  public static final float TWIPS_PER_INCH = 1440.0f;
  // new constant for width\height calculation
  public static final int TWIPS_PER_PIXEL = 15;
  private boolean isListOpened = false;
  // last level of paragraph for list, last style of list and the width of
  // tabultaion
  private int lastParagraphLevel = -1, lastListStyle = -1, tabWidth = 0;
  // parametras for tabulation that is represented as a html
  // picture
  private AttributesImpl tabImageAttributes;
  private XHTMLWriteBuffer defaultWriter = new XHTMLWriteBuffer(null);

  public WordDocExtractor(ParseContext context, Metadata metadata) {
    super(context, metadata);
  }

  private Map<String, String> attrToClassMap = new HashMap<>();
  private long lastid = 0;

  private String getClass(String attributes) {
    if (!attrToClassMap.containsKey(attributes))
      attrToClassMap.put(attributes, "class_" + Long.toString(lastid++));
    return attrToClassMap.get(attributes);
  }

  private String getAttrsForClass(String sl) {
    for (String s : attrToClassMap.keySet()) if (attrToClassMap.get(s).equals(sl)) return s;
    return "";
  }

  private DirectoryNode rt;
  private static ArrayList<String> styleStopTags = new ArrayList<>();

  /*
   * Method that is wrap for direct XHTML charater writing. For table level
   * more than 1(inner table) it helps to save table information for futher
   * integration in table with lower table level
   */
  private void writeCharacters(
      XHTMLContentHandler xhtml, XHTMLWriteBuffer writeBuffer, String buffer) throws SAXException {
    if (writeBuffer.getWriter() != null) {
      writeBuffer.addEvent(new XHTMLevent(2, buffer));
    } else {
      if (SymbolStarts) {
        String out = "";
        for (int i = 0; i < buffer.length(); i++) {
          int c = buffer.charAt(i);
          out += SymbolToUnicode.toUnicode((c > 61440) ? c - 61440 : c);
        }
        char[] cont = out.toCharArray();
        xhtml.ignorableWhitespace(cont, 0, cont.length);
      } else xhtml.characters(buffer);
    }
  }

  /*
   * Method that is wrap for XHTML tag start. For table level more than
   * 1(inner table) it helps to save table information for futher integration
   * in table with lower table level
   */
  private void startTag(XHTMLContentHandler xhtml, XHTMLWriteBuffer writeBuffer, String tag)
      throws SAXException {
    startTag(xhtml, writeBuffer, tag, null);
  }

  /*
   * Method that is wrap for XHTML tag start. Support for single attribute.
   * More information below.
   */
  private void startTag(
      XHTMLContentHandler xhtml,
      XHTMLWriteBuffer writeBuffer,
      String tag,
      String string,
      String string2)
      throws SAXException {
    AttributesImpl ai = new AttributesImpl();
    ai.addAttribute("", string, string, "", string2);
    startTag(xhtml, writeBuffer, tag, ai);
  }

  /*
   * Method that is wrap for XHTML tag start. Support for a set of attribute
   * More information below.
   */
  private void startTag(
      XHTMLContentHandler xhtml, XHTMLWriteBuffer writeBuffer, String tag, AttributesImpl ai)
      throws SAXException {
    tag = tag.toLowerCase();
    writeBuffer.addToLists(tag, ai);
    if (writeBuffer.getWriter() != null) {
      writeBuffer.addEvent(new XHTMLevent(0, tag, ai));
    } else {
      if (ai != null) xhtml.startElement(tag, ai);
      else xhtml.startElement(tag);
    }
  }

  /*
   * Method that is wrap for XHTML tag end. For table level more than 1(inner
   * table) it helps to save table information for futher integration in table
   * with lower table level
   */
  private void endTag(XHTMLContentHandler xhtml, XHTMLWriteBuffer writeBuffer, String tag)
      throws SAXException {
    tag = tag.toLowerCase();
    // if we have no target tag in list, then it is
    // already was closed
    if (!writeBuffer.tagListContains(tag)) return;
    // take first tag
    String tg = writeBuffer.tagListPop();

    if (tg.equals("ul") || tg.equals("ol")) writeBuffer.resetIndents();
    // if tag we take is not a target one
    // then close all tags until we take
    // right one
    while (!tg.equals(tag)) {
      if (writeBuffer.getWriter() != null) {
        writeBuffer.addEvent(new XHTMLevent(1, tag));
      } else {
        xhtml.endElement(tg);
      }
      tg = writeBuffer.tagListPop();
      if (tg.equals("ul") || tg.equals("ol")) writeBuffer.resetIndents();
    }
    if (writeBuffer.getWriter() != null) {
      writeBuffer.addEvent(new XHTMLevent(1, tag));
    } else xhtml.endElement(tag);
  }

  /*
   * Method that suppors text formation, that is bold, italic, underlined,
   * striekd or For table level more than 1(inner table) it helps to save
   * table information for futher integration in table with lower table level,
   * sup,sub text. Also it is used to support different font sizes and
   * families.
   */
  // symbol font recognition mode
  private boolean SymbolStarts = false;

  private void startCRStyling(
      XHTMLContentHandler xhtml, XHTMLWriteBuffer writeBuffer, CharacterRun cr)
      throws SAXException {
    AttributesImpl aim = new AttributesImpl();
    // attributes for font tag
    String fam = "";
    try {
      fam = cr.getSymbolFont().getMainFontName();
    } catch (IllegalStateException e) {
      fam = cr.getFontName();
    }
    SymbolStarts = fam.equals("Symbol");
    // list of tags to be active
    Map<String, String> stNow = writeBuffer.getVisibleStyling();
    Map<String, String> toStyle = new TreeMap<>();
    toStyle.put("font-family", fam);
    toStyle.put("font-size", cr.getFontSize() / 2 + "pt");
    if (cr.getUnderlineCode() > 0) {
      toStyle.put("text-decoration", "underline");
    } else {
      toStyle.put("text-decoration", "none");
    }
    if (cr.isBold()) {
      toStyle.put("font-weight", "bold");
    } else {
      toStyle.put("font-weight", "normal");
    }
    if (cr.isItalic()) {
      toStyle.put("font-style", "italic");
    } else {
      toStyle.put("font-style", "normal");
    }
    if (cr.isStrikeThrough() || cr.isDoubleStrikeThrough()) {
      toStyle.put("text-decoration", "line-through");
    } else {
      if (!toStyle.containsKey("text-decoration")) toStyle.put("text-decoration", "none");
    }
    // sub and sup text
    short sub = cr.getSubSuperScriptIndex();
    if (sub != 0) {
      toStyle.put("font-size", cr.getFontSize() * 3 / 10 + "pt");
      if (sub == 2) toStyle.put("vertical-align", "sub");
      else toStyle.put("vertical-align", "super");
    } else toStyle.put("vertical-align", "baseline");

    Map<String, String> change = new TreeMap<>();
    for (String s : toStyle.keySet())
      if (!stNow.containsKey(s) || !stNow.get(s).equals(toStyle.get(s)))
        change.put(s, toStyle.get(s));
    aim.addAttribute("", "style", "style", "", buildStyle(change));
    if (!change.isEmpty()) {
      while (stNow.containsKey("vertical-align") && change.containsKey("vertical-align")
          || stNow.containsKey("text-decoration") && change.containsKey("text-decoration")
          || stNow.containsKey("font-weight") && change.containsKey("font-weight")
          || stNow.containsKey("fons-style") && change.containsKey("font-style")) {
        safeEndPopTag(xhtml, writeBuffer);
        stNow = writeBuffer.getVisibleStyling();
        for (String s : toStyle.keySet())
          if (!stNow.containsKey(s) || !stNow.get(s).equals(toStyle.get(s)))
            change.put(s, toStyle.get(s));
      }
      startTag(xhtml, writeBuffer, "font", aim);
    }
  }

  private String buildStyle(Map<String, String> toStyle) {
    String s2 = "";
    for (String s : toStyle.keySet()) s2 += s + ":" + toStyle.get(s) + ";";
    // TODO Auto-generated method stub
    return s2;
  }

  /*
   * Method that is used to close first opened tag and return its string name
   * For table level more than 1(inner table) it helps to save table
   * information for futher integration in table with lower table level
   */
  private String safeEndPopTag(XHTMLContentHandler xhtml, XHTMLWriteBuffer writeBuffer)
      throws SAXException {
    if (writeBuffer.tagListIsEmpty()) return null;
    String s = writeBuffer.tagListPop();
    if (s.equals("ul") || s.equals("ol")) writeBuffer.resetIndents();
    if (writeBuffer.getWriter() != null) {
      writeBuffer.addEvent(new XHTMLevent(1, s));
    } else xhtml.endElement(s);
    return s;
  }

  @Override
  protected void parse(DirectoryNode root, XHTMLContentHandler xhtml)
      throws IOException, SAXException, TikaException {
    if (activeExtractor == null) activeExtractor = this;
    // safe tags that are possibly openned
    int tagsnow = defaultWriter.tagListSize();
    rt = root;
    HWPFDocument document = null;;
    try {
      try {
        document = new HWPFDocument(root);
      } catch (OldWordFileFormatException e) {
        parseWord6(root, xhtml);
        return;
      }
      org.apache.poi.hwpf.extractor.WordExtractor wordExtractor =
          new org.apache.poi.hwpf.extractor.WordExtractor(document);
      try{
        // sets a parameter of tabulation for choosen document.
        // tabulation will be supported in a form of empty pictures
        tabImageAttributes = new AttributesImpl();
        tabWidth = document.getDocProperties().getDxaTab() / TWIPS_PER_PIXEL;
        tabImageAttributes.addAttribute("", "width", "width", "", tabWidth + "");
        tabImageAttributes.addAttribute("", "height", "height", "", "1");
        tabImageAttributes.addAttribute("", "border", "border", "", "0");
        // this is represention of data of empty picture
        tabImageAttributes.addAttribute(
            "", "src", "src", "", "data:image/gif;base64,R0lGODlhAQABAAD/ACwAAAAAAQABAAACADs%3D");
        // addTextIfAny(xhtml, 0, "header", wordExtractor.getHeaderText());
    
        // Grab the list of pictures. As far as we can tell,
        // the pictures should be in order, and may be directly
        // placed or referenced from an anchor
        PicturesTable pictureTable = document.getPicturesTable();
        PicturesSource pictures = new PicturesSource(document);
    
        int bkm = document.getBookmarks().getBookmarksCount();
        for (int i = 0; i < bkm; i++)
          bookmarkNames.add(document.getBookmarks().getBookmark(i).getName());
    
        // Do the main paragraph text
        Range r = document.getRange();
        for (int i = 0; i < r.numParagraphs(); i++) {
          Paragraph p = r.getParagraph(i);
          i += handleParagraph(p, 0, r, document, pictures, pictureTable, xhtml);
        }
        // Do everything else
        for (String paragraph : wordExtractor.getMainTextboxText()) {
          writeCharacters(xhtml, defaultWriter, paragraph);
        }
    
        for (String paragraph : wordExtractor.getFootnoteText()) {
          writeCharacters(xhtml, defaultWriter, paragraph);
        }
    
        for (String paragraph : wordExtractor.getCommentsText()) {
          writeCharacters(xhtml, defaultWriter, paragraph);
        }
    
        for (String paragraph : wordExtractor.getEndnoteText()) {
          writeCharacters(xhtml, defaultWriter, paragraph);
        }
        // addTextIfAny(xhtml, 0, "footer", wordExtractor.getFooterText());
        for (int i = xhtmlbuffers.size() - 1; i > 0; i--)
          xhtmlbuffers.get(i).apply(xhtml, defaultWriter);
        while (defaultWriter.tagListSize() > tagsnow) {
          safeEndPopTag(xhtml, defaultWriter);
        }
    
        // Handle any pictures that we haven't output yet
        for (Picture p = pictures.nextUnclaimed(); p != null; ) {
          handlePictureCharacterRun(null, defaultWriter, p, pictures, xhtml);
          p = pictures.nextUnclaimed();
        }
        if (activeExtractor == this) {
          // TODO check embedded docs
          activeExtractor = null;
          lateCssStyles = getCssStyles() + getStaticCssStyles();
        }
      }finally{
        wordExtractor.close();
      }
    }finally {
      if(document!=null)
        document.close();
    }
  }

  private static String getStaticCssStyles() {
    return "li.listText:before{ content:  attr(listText) ;}\nli.listText { padding-left: 1em; text-indent: -.7em;list-style: none;}";
  }

  /*
   * Method that is used to get an CSS representation of aligmnent for
   * different entities, for example, for paragraph or list
   */
  private String getAligmentCss(int al) {
    String s = "";
    switch (al) {
      case 0:
        s = "left";
        break;
      case 1:
        s = "center";
        break;
      case 2:
        s = "right";
        break;
      case 3:
        s = "justify";
        break;
      default:
        break;
    }
    return s.equals("") ? "" : "text-align: " + s + ";";
  }

  /*
   * Method that is used to get an CSS representation of vertical aligmnent.
   * This is used in table handling
   */
  private String getVerticalAligmentCss(int al) {
    String s = "";
    switch (al) {
      case 0:
        s = "top";
        break;
      case 1:
        s = "center";
        break;
      case 2:
        s = "bottom";
        break;
      default:
        break;
    }
    return s.equals("") ? "" : "vertical-align: " + s + ";";
  }

  // a set of string constants to support different types
  // of cell border. It tends to be wrong for old
  // word formats.
  private static String[] bordertype = {
    "hidden", "solid", "solid", "double", "solid", "solid", "dashed", "solid", "solid", "dotted",
    "dotted", "solid", "dashed", "dashed", "dashed", "dashed", "dashed", "dashed", "dashed",
    "dashed", "dashed", "dashed", "dashed", "solid", "solid", "dashed", "dashed", "groove",
    "groove", "groove"
  };
  // a set of string constants to support different colors
  // of cell border. It tends to be wrong for old
  // word formats.
  private static String[] bordercolor = {
    "black",
    "black",
    "blue",
    "cyan",
    "green",
    "magenta",
    "red",
    "yellow",
    "white",
    "DarkBlue",
    "DarkCyan",
    "DarkMagenta",
    "DarkRed",
    "DarkYellow",
    "DarkGray",
    "LightGray",
    "LightGray"
  };
  private static WordDocExtractor activeExtractor;
  protected static String lateCssStyles;

  /*
   * Method for getting a css representation of word-styled border. Includes
   * border type, width and color.
   */
  private String getBorderInfo(String s, BorderCode bc) {
    int tp = bc.getBorderType();
    if (bc.isEmpty() || tp == 0 || bc.getLineWidth() == 0) return "";
    Double d = bc.getLineWidth() / 8.0;
    return s
        + Math.round(d + 0.5)
        + "px "
        + ((tp > bordertype.length) ? "solid" : bordertype[tp])
        + " "
        + bordercolor[bc.getColor()]
        + ";";
  }
  /*
   * Method for getting a css representation of table cell border styling. It
   * includes type of borders, they width and color.
   */

  private String getCellStyle(
      TableCell cell,
      TableRow row,
      boolean toppest,
      boolean bottomest,
      boolean leftest,
      boolean rightest) {
    BorderCode top =
        cell.getBrcTop() != null && cell.getBrcTop().getBorderType() != 0
            ? cell.getBrcTop()
            : toppest ? row.getTopBorder() : row.getHorizontalBorder();
    BorderCode bottom =
        cell.getBrcBottom() != null && cell.getBrcBottom().getBorderType() != 0
            ? cell.getBrcBottom()
            : bottomest ? row.getBottomBorder() : row.getHorizontalBorder();

    BorderCode left =
        cell.getBrcLeft() != null && cell.getBrcLeft().getBorderType() != 0
            ? cell.getBrcLeft()
            : leftest ? row.getLeftBorder() : row.getVerticalBorder();
    BorderCode right =
        cell.getBrcRight() != null && cell.getBrcRight().getBorderType() != 0
            ? cell.getBrcRight()
            : rightest ? row.getRightBorder() : row.getVerticalBorder();
    return getBorderInfo("border-left: ", left)
        + getBorderInfo("border-right: ", right)
        + getBorderInfo("border-top:", top)
        + getBorderInfo("border-bottom:", bottom);
  }

  // List that is used to support an information
  // about maximum values for numerical lists.
  // This is important because list tags can
  // be separated from each other with plain text
  // without formation.
  // <list_id,int[8]maxForLevel>
  private java.util.HashMap<Integer, int[]> lastMax = new java.util.HashMap<>();

  // Below there is a set of methods used to support
  // cell merging.
  static int[] buildTableCellEdgesArray(Table table) {
    Set<Integer> edges = new TreeSet<>();
    for (int r = 0; r < table.numRows(); r++) {
      TableRow tableRow = table.getRow(r);
      for (int c = 0; c < tableRow.numCells(); c++) {
        TableCell tableCell = tableRow.getCell(c);
        edges.add(Integer.valueOf(tableCell.getLeftEdge()));
        edges.add(Integer.valueOf(tableCell.getLeftEdge() + tableCell.getWidth()));
      }
    }
    Integer[] sorted = edges.toArray(new Integer[0]);
    int[] result = new int[sorted.length];
    for (int i = 0; i < sorted.length; i++) {
      result[i] = sorted[i].intValue();
    }
    return result;
  }

  protected int getNumberColumnsSpanned(
      int[] tableCellEdges, int currentEdgeIndex, TableCell tableCell) {
    int nextEdgeIndex = currentEdgeIndex;
    int colSpan = 0;
    int cellRightEdge = tableCell.getLeftEdge() + tableCell.getWidth();
    while (tableCellEdges[nextEdgeIndex] < cellRightEdge) {
      colSpan++;
      nextEdgeIndex++;
    }
    return colSpan;
  }

  protected int getNumberRowsSpanned(
      Table table, int currentRowIndex, int currentColumnIndex, TableCell tableCell) {
    if (!tableCell.isFirstVerticallyMerged()) return 1;
    final int numRows = table.numRows();
    int count = 1;
    for (int r1 = currentRowIndex + 1; r1 < numRows; r1++) {
      TableRow nextRow = table.getRow(r1);
      if (currentColumnIndex >= nextRow.numCells()) break;
      TableCell nextCell = nextRow.getCell(currentColumnIndex);
      if (!nextCell.isVerticallyMerged() || nextCell.isFirstVerticallyMerged()) break;
      count++;
    }
    return count;
  }

  protected int getTableCellEdgesIndexSkipCount(
      Table table, int r, int[] tableCellEdges, int currentEdgeIndex, int c, TableCell tableCell) {
    TableCell upperCell = null;
    for (int r1 = r - 1; r1 >= 0; r1--) {
      final TableRow row = table.getRow(r1);
      if (row == null || c >= row.numCells()) continue;

      final TableCell prevCell = row.getCell(c);
      if (prevCell != null && prevCell.isFirstVerticallyMerged()) {
        upperCell = prevCell;
        break;
      }
    }
    if (upperCell == null) {
      System.err.println("First vertically merged cell for " + tableCell + " not found");
      return 0;
    }

    return getNumberColumnsSpanned(tableCellEdges, currentEdgeIndex, tableCell);
  }

  /*
   * Method to present indents in a form of css attributes
   */
  private String getCSSByIndents(double[] indentsa) {
    String indentsstyle = "";
    indentsstyle += "margin-top: " + indentsa[2] + "in;padding-top:0;";
    indentsstyle += "margin-bottom: " + indentsa[3] + "in;padding-bottom:0;";
    indentsstyle += "margin-left:" + indentsa[0] + "in;padding-left:0;";
    indentsstyle += "margin-right:" + indentsa[1] + "in;padding-right:0;";
    indentsstyle += "text-indent:" + indentsa[4] + "in;";
    return indentsstyle;
  }

  /*
   * Method to dig information about indents for single paragraph
   */
  private double[] getIndents(org.apache.poi.hwpf.model.StyleSheet sh, ParagraphProperties pr) {
    double[] indentsa = new double[5];
    // TODO move first line indent
    indentsa[0] = (pr.getIndentFromLeft() / TWIPS_PER_INCH);
    indentsa[1] = (pr.getIndentFromRight() / TWIPS_PER_INCH);
    indentsa[2] = (pr.getSpacingBefore() / TWIPS_PER_INCH);
    indentsa[3] = (pr.getSpacingAfter() / TWIPS_PER_INCH);
    indentsa[4] = (pr.getFirstLineIndent() / TWIPS_PER_INCH);
    return indentsa;
  }

  // Stack that is used to support bookmarks handling
  private Stack<Bookmark> openbms = new Stack<>();

  // Method to convert information from ico24 form to
  // HTML rgb
  public static String ico24ToHtml(int ico24) {
    int r = (ico24 >> 0) & 0xff; // red;
    int g = (ico24 >> 8) & 0xff; // green
    int b = (ico24 >> 16) & 0xff; // blue
    int rgb = (r << 16) | (g << 8) | b;
    // Hack. Find a better way to maintain leading zeroes
    return "#" + Integer.toHexString(0xC000000 | rgb).substring(1);
  }

  /*
   * Class to support inner tables. Used to write different XHTML events on
   * tempral buffers and rewrite them to page in the future
   */
  private class XHTMLevent {
    // Type of event. 0 - start, 1-end, 2-characters
    int type = 0;
    // Set of attributes for tag
    AttributesImpl aim = null;
    // for 0 and 1 it is a tag name, for 2 it is an
    // characters to write
    String buffer;

    public XHTMLevent(int tp, String tag, AttributesImpl ai) {
      type = tp;
      buffer = tag;
      aim = ai;
    }

    public XHTMLevent(int tp, String buffer) {
      type = tp;
      this.buffer = buffer;
    }

    // conversion to string to meet better Watch representation
    @Override
    public String toString() {
      switch (type) {
        case 0:
          {
            String s = "";
            if (aim != null)
              for (int i = 0; i < aim.getLength(); i++)
                s += "'" + aim.getQName(i) + "'=" + aim.getValue(i) + " ";
            return "<" + buffer + " " + s + ">\n\r";
          }
        case 1:
          {
            return "</" + buffer + ">\n\r";
          }
      }
      return "[" + buffer + "]\n\r";
    }

    /*
     * apply changes stored in this event on current document
     */
    public void apply(XHTMLWriteBuffer writeBuffer, XHTMLContentHandler xhtml) throws SAXException {
      switch (type) {
        case 0:
          {
            if (aim != null) {
              startTag(xhtml, writeBuffer, buffer, aim);
            } else {
              startTag(xhtml, writeBuffer, buffer);
            }
          }
          break;
        case 1:
          {
            endTag(xhtml, writeBuffer, buffer);
          }
          break;
        case 2:
          {
            writeCharacters(xhtml, writeBuffer, buffer);
          }
          break;
      }
    }
  }

  private static final Pattern stylePat = Pattern.compile("([a-zA-Z-]*):([^\\;]*);");

  // Class to support buffering functions
  // between requality code and XHML contructor
  // . As an example of usage - nesting tables
  private class XHTMLWriteBuffer {
    Queue<XHTMLevent> writer;
    // list of tags that are currently open
    private Stack<String> listTag = new Stack<>();
    private Stack<AttributesImpl> listAtribs = new Stack<>();
    // list of tags visible at this point(an "breaking"
    // tags can be fount in static constructor)
    private Stack<String> listVisibleTag = new Stack<>();
    private double[] activeIndent = new double[6];
    private Stack<double[]> indentdiffs = new Stack<>();

    @Override
    public boolean equals(Object o) {
      XHTMLWriteBuffer wb;
      if (!(o instanceof XHTMLWriteBuffer)) return false;
      wb = (XHTMLWriteBuffer) o;
      return wb.writer.equals(writer);
    }

    // applies this buffer to content handler
    public void apply(XHTMLContentHandler xhtml, XHTMLWriteBuffer current) throws SAXException {
      while (!writer.isEmpty()) pollEvent().apply(current, xhtml);
      while (!tagListIsEmpty()) endTag(xhtml, current, tagListPop());
    }

    public XHTMLWriteBuffer(Queue<XHTMLevent> l) {
      writer = l;
    }

    public void addEvent(XHTMLevent xhtmLevent) {
      writer.add(xhtmLevent);
    }

    public Queue<XHTMLevent> getWriter() {
      return writer;
    }

    // set active indent to previous
    public void resetIndents() {
      activeIndent = popIndents();
    }

    // get the last indent
    public double[] popIndents() {
      return indentdiffs.pop();
    }

    public int tagListSize() {
      return listTag.size();
    }

    public boolean tagListIsEmpty() {
      return listTag.isEmpty();
    }

    public Map<String, String> getVisibleStyling() {
      // TODO optimize it
      Map<String, String> style = new TreeMap<>();
      for (int i = 0; i < listVisibleTag.size(); i++) {
        AttributesImpl ai = listAtribs.get(i);
        if (ai == null) continue;
        String s = ai.getValue("style"), s2 = ai.getValue("class");
        if (s != null) {
          Matcher m = stylePat.matcher(s);
          while (m.find()) style.put(m.group(1), m.group(2));
        }
        if (s2 != null) {
          String attr = getAttrsForClass(s2);
          Matcher m = stylePat.matcher(attr);
          while (m.find()) style.put(m.group(1), m.group(2));
        }
      }
      return style;
    }

    // checks for tag to be in 'visible'
    // tag list. It means tags from last
    // 'not visible' tag. The list of that
    // kind of tags can be found in static
    // constructor below
    public boolean tagVisibleListContains(String string) {
      return listVisibleTag.contains(string);
    }

    public String tagListPop() {
      String s = listTag.pop();
      listAtribs.pop();
      listVisibleTagSafePop();
      return s;
    }

    public boolean tagListContains(String tag) {
      return listTag.contains(tag);
    }

    public void addToLists(String tag, AttributesImpl ai) {
      listTag.push(tag);
      listAtribs.push(ai);
      listVisibleTagSafeAdd(tag);
    }

    private String listVisibleTagSafePop() {
      if (!listVisibleTag.empty()) return listVisibleTag.pop();
      else {
        if (listTag.empty()) return null;
        listVisibleTag.clear();
        int j = 0;
        while (j < listTag.size() && !styleStopTags.contains(listTag.get(j))) j++;
        listVisibleTag.addAll(listTag.subList(0, j));
        if (j < listTag.size() && styleStopTags.contains(listTag.get(j)))
          listVisibleTag.add(listTag.get(j));
      }
      return null;
    }

    private void setActiveIndents(double[] ai) {
      activeIndent = ai;
    }

    private double[] getActiveIndents() {
      return activeIndent;
    }

    // Add tag to "visible" list but
    // supports "visibility" property
    private void listVisibleTagSafeAdd(String tag) {
      if (styleStopTags.contains(tag)) {
        listVisibleTag.clear();
        listVisibleTag.add(tag);
      } else listVisibleTag.push(tag);
    }

    public void saveIndents() {
      indentdiffs.push(activeIndent.clone());
    }

    public XHTMLevent pollEvent() {
      if (writer == null) return null;
      return writer.poll();
    }
  }

  // Set of buffers for different table levels. It stores a
  // set of XHTML commands collected on different tables with
  // level 2 or more. It is added because poi gives us infor
  // mation about a table with deeper level before we get information
  // about table with lowel level.
  // To fight this, we store xhtml events of inner tables
  // and write them when first cell of table with lower level
  // appears. This trick helps to show all as it tends to be
  // presented.
  private SortedMap<Integer, XHTMLWriteBuffer> xhtmlbuffers =
      new TreeMap<>();
  // variable for field procedding
  int fieldLevel = 0;

  private int handleParagraph(
      Paragraph p,
      int parentTableLevel,
      Range r,
      HWPFDocument document,
      PicturesSource pictures,
      PicturesTable pictureTable,
      XHTMLContentHandler xhtml)
      throws SAXException, IOException, TikaException {
    // information about indents of paragraph.
    // It includes different margins and first line indent
    double[] indentsa;
    // string representation of styling
    String style;
    indentsa = getIndents(document.getStyleSheet(), p.cloneProperties());
    style = "";
    // get a level of table(it we are in it) for current paragraph
    int tableLevel = p.getTableLevel();
    // buffers for inner tables
    XHTMLWriteBuffer writeBuffer = defaultWriter;
    if (tableLevel > 1) {
      if (!xhtmlbuffers.containsKey(tableLevel))
        xhtmlbuffers.put(tableLevel, new XHTMLWriteBuffer(new LinkedList<XHTMLevent>()));
      writeBuffer = xhtmlbuffers.get(tableLevel);
    }
    // get information about a paragraph's external tag
    TagAndStyle tas;
    if (document.getStyleSheet().numStyles() > p.getStyleIndex()) {
      StyleDescription std = document.getStyleSheet().getStyleDescription(p.getStyleIndex());
      if ((std != null && std.getName() != null && std.getName().length() > 0)
         /* || (p.getLvl() > 0 && p.getLvl() < 9)*/) {//TODO test
        tas = buildParagraphTagAndStyle(std.getName(), p.getLvl() + 1, (parentTableLevel > 0));
      } else {
        tas = new TagAndStyle(parentTableLevel > 0 ? "font" : "p", null);
      }
    } else {
      tas = new TagAndStyle(parentTableLevel > 0 ? "font" : "p", null);
    }
    // instead of getting heading level from tas,
    // we use information from poi
    boolean isHeading = (p.getLvl() > 0 && p.getLvl() < 9) || (tas != null && tas.isHeading());
    // an information about a position of oppened wrapping
    // tag for this paragraph in a list of oppened tags
    int tasstarted = -1;
    // this condition assumes that we are in list or header
    if (p.getIlfo() > 0
        && !(p.isInTable()
            && (parentTableLevel != p.getTableLevel())
            && r.getTable(p).getRow(0).getCell(0).getParagraph(0).getEndOffset()
                == p.getEndOffset())) {
      // check that we are in the list
      if (p.isInList()) {
        // close opened li tags
        while (writeBuffer.tagVisibleListContains("li")) safeEndPopTag(xhtml, writeBuffer);
        HWPFList list = p.getList();
        // get the level of indent
        int level = p.getIlvl() + 1;
        // get information about given level of list
        ListLevel listLevel =
            list.getListData().numLevels() == 1
                ? list.getListData().getLevels()[0]
                : list.getListData().getLevels()[level - 1];
        // close some tags if we are at wrong level of list
        // or the style of list changes
        if (isListOpened && level < lastParagraphLevel || p.getIlfo() != lastListStyle) {
          String tag2;
          // if style changes then close current level
          // of list
          for (int i = level; i < lastParagraphLevel + ((p.getIlfo() != lastListStyle) ? 1 : 0); ) {
            if (!writeBuffer.tagVisibleListContains("ul")
                && !writeBuffer.tagVisibleListContains("ol")) {
              isListOpened = false;
              break;
            } else {
              tag2 = safeEndPopTag(xhtml, writeBuffer);
              if (tag2 == null) break;
              if (tag2.equals("ul") || tag2.equals("ol")) {
                i++;
              }
            }
          }
        }
        // if we have no ul and ol then list is not oppened now
        if (!writeBuffer.tagListContains("ul") && !writeBuffer.tagListContains("ol"))
          isListOpened = false;
        // flags for ol/ul to be open and list style to be numerical
        boolean newList = false, isNumbered = listLevel.getNumberFormat() != 23;
        // if we need to open ul or ol
        if ((isListOpened && ((p.getIlfo() != lastListStyle)) || (level > lastParagraphLevel))
            || !isListOpened) {
          newList = true;
          String tag;
          String liststyle = "";
          // if format of list is numeric then
          // use appropriate css styling
          if (listLevel.getNumberFormat() != 23) {
            tag = "ol";
            // today it does not support custom characters.
            // it can be solved by adding css description
            switch (listLevel.getNumberFormat()) {
              case -1:
                {
                  liststyle = "list-style-type:none;";
                }
                break;
              case 1:
                {
                  liststyle = "list-style-type:upper-roman;";
                }
                break;
              case 2:
                {
                  liststyle = "list-style-type:lower-roman;";
                }
                break;
              case 3:
                {
                  liststyle = "list-style-type:upper-latin;";
                }
                break;
              case 4:
                {
                  liststyle = "list-style-type:lower-latin;";
                }
                break;
            }
          } else {
            // if format of list is not numeric then
            // try to map word markers to html one
            switch (listLevel.getNumberText().charAt(0)) {
              case 173:
              case 61691:
              case 61672:
              case 9702:
                {
                  liststyle = "list-style-type: circle;";
                }
                break;
              case 61550:
              case 61557:
              case 9642:
                {
                  liststyle = "list-style-type: square;";
                }
                break;
              case 61692:
              case 61656:
              case 61623:
              case 61548:
                {
                  liststyle = "list-style-type: disc;";
                }
                break;
            }
            tag = "ul";
          }
          // set indents of ul\ol to empty - temporary solution
          ParagraphProperties pp =
              ParagraphSprmUncompressor.uncompressPAP(
                  p.cloneProperties(), listLevel.getGrpprlPapx(), 0);
          double[] dd = getIndents(document.getStyleSheet(), pp);
          writeBuffer.saveIndents();
          double[] ai = writeBuffer.getActiveIndents();
          for (int i = 0; i < 2; i++) {
            dd[i] -= ai[i];
            ai[i] += dd[i];
          }
          writeBuffer.setActiveIndents(ai);
          liststyle += getCSSByIndents(dd) + getAligmentCss(p.getJustification());
          if (!liststyle.equals("")) {
            startTag(xhtml, writeBuffer, tag, "style", liststyle);
          } else {
            startTag(xhtml, writeBuffer, tag);
          }
          isListOpened = true;
        }
        int index = listLevel.getStartAt();
        if (index < 0) index = 1;
        if (!lastMax.containsKey(list.getLsid())) {
          // in this case we need to start new list
          // array is 0..7 levels and set start index at 8 to 1
          int[] vals = new int[9];
          vals[0] = 1;
          if (list.getListData().numLevels() == 1) Arrays.fill(vals, listLevel.getStartAt());
          else for (int i = 1; i < 9; i++) vals[i] = list.getListData().getLevel(i).getStartAt();
          lastMax.put(list.getLsid(), vals);
          // == -1 to support 'maximum' storage
          lastMax.get(list.getLsid())[level - 1] = index - 1;
        }
        int[] lastMaxInt = lastMax.get(list.getLsid());
        Integer lastmax = lastMaxInt[level - 1];
        if (lastmax < 0) lastmax = index - 1;
        if (isNumbered) lastmax = lastmax + 1;
        String listText = listLevel.getNumberText();
        if (listText == null || listLevel.getNumberFormat() != 0) listText = "";
        // reset max values for lists
        synchronized (lastMax) {
          for (int i = level; (i < 8) && (lastMaxInt[i] != -1); i++) lastMaxInt[i] = -1;
          lastMaxInt[level - 1] = lastmax;
        }
        for (int i = 0; i < lastMaxInt.length; i++)
          listText = listText.replace((char) i + "", lastMaxInt[i] + "");
        AttributesImpl aim = new AttributesImpl();
        writeBuffer.saveIndents();
        double[] ai = writeBuffer.getActiveIndents();
        for (int i = 0; i < 4; i++) {
          indentsa[i] -= ai[i];
          ai[i] += indentsa[i];
        }
        writeBuffer.setActiveIndents(ai);
        style = getCSSByIndents(indentsa) + ";" + getAligmentCss(p.getJustification());
        // starts wrapping tag in list
        if (tas != null) {
          lastParagraphLevel = level;
          lastListStyle = p.getIlfo();
          String cl = getClass(style + getStyleForCR(p.getCharacterRun(0)));
          style = "";

          if (tas.getStyleClass() != null) cl += ' ' + tas.getStyleClass();
          aim.addAttribute("", "class", "class", "", cl);

          tasstarted = writeBuffer.tagListSize();
          startTag(xhtml, writeBuffer, tas.getTag(), aim);
        }
        // for current level set maximum value
        lastMax.put(list.getLsid(), lastMaxInt);
        if (tasstarted > -1) {
          aim = new AttributesImpl();
          style =
              getCSSByIndents(new double[] {0, 0, 0, 0, 0, 0})
                  + getAligmentCss(p.getJustification());
          aim.addAttribute(
              "",
              "class",
              "class",
              "",
              getClass(style) + (listText.equals("") ? "" : (" listText")));
        }
        // for new lists(or lists, appears after break)
        // we need to set starting value
        if (newList) {
          if (isNumbered) aim.addAttribute("", "value", "value", "", lastmax + "");
        }

        if (!listText.equals("")) aim.addAttribute("", "listText", "listText", "", listText);
        startTag(xhtml, writeBuffer, "li", aim);
      }
    } else {
      // close tags in visible section
      while (writeBuffer.tagVisibleListContains("ul")
          || writeBuffer.tagVisibleListContains("ol")
          || writeBuffer.tagVisibleListContains("li")) safeEndPopTag(xhtml, writeBuffer);
      lastParagraphLevel = 0;
    }
    // Note - a poi bug means we can't currently properly recurse
    // into nested tables, so currently we don't
    // UPDATED. Now nested tables are supported for
    // some cases, but we need to check it
    if (tasstarted == -1 && tas != null) {
      AttributesImpl aim = new AttributesImpl();
      writeBuffer.saveIndents();
      double[] ai = writeBuffer.getActiveIndents();
      for (int i = 0; i < 2; i++) {
        indentsa[i] -= ai[i];
        ai[i] += indentsa[i];
      }
      writeBuffer.setActiveIndents(ai);
      style =
          getCSSByIndents(indentsa)
              + ";"
              + getAligmentCss(p.getJustification())
              + getStyleForCR(p.getCharacterRun(0));
      aim.addAttribute("", "class", "class", "", getClass(style));
      tasstarted = writeBuffer.tagListSize();
      startTag(xhtml, writeBuffer, tas.getTag(), aim);
    }
    boolean firstCell = true;
    if (p.isInTable() && p.getTableLevel() > parentTableLevel) {
      Table t = r.getTable(p);
      startTag(
          xhtml, writeBuffer, "table", "style", "border-collapse: collapse; border-spacing: 0;");
      style = "";
      startTag(xhtml, writeBuffer, "tbody");
      // below it is a text for
      // cell joins
      final int[] tableCellEdges = buildTableCellEdgesArray(t);
      int nrows = t.numRows();
      int maxColumns = Integer.MIN_VALUE;
      for (int row = 0; row < nrows; row++)
        maxColumns = Math.max(maxColumns, t.getRow(row).numCells());
      for (int rn = 0; rn < nrows; rn++) {
        TableRow row = t.getRow(rn);
        startTag(
            xhtml,
            writeBuffer,
            "tr",
            "style",
            "height:"
                + row.getRowHeight() / TWIPS_PER_INCH
                + "in;"
                + (!row.cantSplit() ? "keep-together:always;" : ""));
        int rnc = row.numCells();
        int currentEdgeIndex = 0;
        for (int cn = 0; cn < rnc; cn++) {
          TableCell cell = row.getCell(cn);
          if (cell.isVerticallyMerged() && !cell.isFirstVerticallyMerged()) {
            currentEdgeIndex +=
                getTableCellEdgesIndexSkipCount(t, rn, tableCellEdges, currentEdgeIndex, cn, cell);
            continue;
          }
          int colSpan = getNumberColumnsSpanned(tableCellEdges, currentEdgeIndex, cell);
          currentEdgeIndex += colSpan;

          if (colSpan == 0) continue;
          final int rowSpan = getNumberRowsSpanned(t, rn, cn, cell);
          AttributesImpl aim = new AttributesImpl();
          if (colSpan != 1) aim.addAttribute("", "colspan", "colspan", "", String.valueOf(colSpan));
          if (rowSpan > 1) aim.addAttribute("", "rowspan", "rowspan", "", String.valueOf(rowSpan));
          aim.addAttribute(
              "",
              "style",
              "style",
              "",
              getCellStyle(cell, row, rn == 0, rn == t.numRows() - 1, cn == 0, cn == rnc - 1)
                  + "width:"
                  + cell.getWidth() / TWIPS_PER_INCH
                  + "in;"
                  + "padding-start:"
                  + (row.getGapHalf() / TWIPS_PER_INCH)
                  + "in;"
                  + "padding-end:"
                  + (row.getGapHalf() / TWIPS_PER_INCH)
                  + "in;"
                  + getVerticalAligmentCss(cell.getVertAlign()));
          startTag(xhtml, writeBuffer, "td", aim);
          // if we have buffer with information about nested table
          // then we need to write it into stream
          if (xhtmlbuffers.containsKey(tableLevel + 1) && firstCell) {
            xhtmlbuffers.get(tableLevel + 1).apply(xhtml, writeBuffer);
            xhtmlbuffers.remove(tableLevel + 1);
            firstCell = false;
          } else {
            for (int pn = 0; pn < cell.numParagraphs(); pn++) {
              Paragraph cellP = cell.getParagraph(pn);
              pn +=
                  handleParagraph(
                      cellP, t.getTableLevel(), cell, document, pictures, pictureTable, xhtml);
            }
            if (xhtmlbuffers.containsKey(tableLevel + 1)) {
              xhtmlbuffers.get(tableLevel + 1).apply(xhtml, writeBuffer);
              xhtmlbuffers.remove(tableLevel + 1);
              firstCell = false;
            }
            // for empty cell we tries to write nobr
            // character
            if (cell.text().trim().isEmpty())
              writeCharacters(xhtml, writeBuffer, (char) (0xA0) + "");
          }
          endTag(xhtml, writeBuffer, "td");
        }
        endTag(xhtml, writeBuffer, "tr");
      }
      endTag(xhtml, writeBuffer, "tbody");
      endTag(xhtml, writeBuffer, "table");
      if (tasstarted > -1) {
        // close starting tag
        for (; writeBuffer.tagListSize() > tasstarted; safeEndPopTag(xhtml, writeBuffer)) ;
        writeBuffer.resetIndents();
      }
      return (t.numParagraphs() - 1);
    }
    boolean skipfield = false;
    for (int j = 0; j < p.numCharacterRuns(); j++) {
      CharacterRun cr = p.getCharacterRun(j);
      // skip field - means that field
      // will output nothing
      skipfield = true;
      // FIELD_BEGIN_MARK:
      if (cr.text().contains("\u0013")) {
        fieldLevel++;
        Field field =
            document
                .getFields()
                .getFieldByStartOffset(FieldsDocumentPart.MAIN, cr.getStartOffset());
        // 58 is an embedded document
        // 56 is a document link
        if (field != null) {
          if (field.getType() == 58 || field.getType() == 56) {
            // Embedded Object: add a <div
            // class="embedded" id="_X"/> so consumer can see where
            // in the main text each embedded document
            // occurred:
            String id = "_" + field.getMarkSeparatorCharacterRun(r).getPicOffset();
            AttributesImpl attributes = new AttributesImpl();
            attributes.addAttribute("", "class", "class", "CDATA", "embedded");
            attributes.addAttribute("", "id", "id", "CDATA", id);
            startTag(xhtml, writeBuffer, "span", attributes);
            endTag(xhtml, writeBuffer, "span");
            skipfield = false;
          }
          if (field.getType() == 37 || field.getType() == 67) skipfield = false;
        }
      }
      if (cr.text().startsWith("\u0013")) {
        j +=
            handleSpecialCharacterRuns(
                document, writeBuffer, p, j, isHeading, pictures, skipfield, xhtml);
      } else if (cr.text().startsWith("\b")) {//\u0008"
        // Floating Picture(s)
        for (int pn = 0; pn < cr.text().length(); pn++) {
          // Assume they're in the order from the unclaimed list...
          // UPDATE bad assume, sometimes it is wrong
          Picture picture = pictures.nextUnclaimed();
          // Output
          handlePictureCharacterRun(cr, writeBuffer, picture, pictures, xhtml);
        }
      } else if (pictureTable.hasPicture(cr)) {
        // Inline Picture
        Picture picture = pictures.getFor(cr);
        handlePictureCharacterRun(cr, writeBuffer, picture, pictures, xhtml);
      } else if (cr.text().contains("\u0015")) {
        int k = cr.text().indexOf("\u0015");
        while (k > -1) {
          if (fieldLevel > 0) fieldLevel--;
          k = cr.text().indexOf("\u0015", k + 1);
        }
      } else {
        if (fieldLevel == 0) handleCharacterRun(document, cr, writeBuffer, isHeading, xhtml);
      }
    }
    Bookmark bm = null;
    if (!openbms.isEmpty())
     {
      bm = openbms.peek();
      while (!openbms.isEmpty()
          && bm.getEnd() <= p.getCharacterRun(p.numCharacterRuns() - 1).getEndOffset()) {
        bm = openbms.pop();
        endTag(xhtml, writeBuffer, "a");
      }
     }
    if (tasstarted > -1) {
      for (; writeBuffer.tagListSize() > tasstarted; safeEndPopTag(xhtml, writeBuffer)) ;
      writeBuffer.resetIndents();
    }
    return 0;
  }

  private String getStyleForCR(CharacterRun cr) {
    String fam = "";
    try {
      fam = cr.getSymbolFont().getMainFontName();
    } catch (IllegalStateException e) {
      fam = cr.getFontName();
    }
    SymbolStarts = fam.equals("Symbol");
    String st = "";

    st += "font-family:" + fam + ";";
    st += "font-size:" + cr.getFontSize() / 2 + "pt;";

    return st;
  }

  public String getCssStyles() {
    String s = "";
    for (String k : attrToClassMap.keySet()) {
      String val = attrToClassMap.get(k);
      s += "." + val + "{" + k + "}\n";
    }
    return s;
  }

  Set<String> bookmarkNames = new HashSet<>();

  private void handleCharacterRun(
      HWPFDocument document,
      CharacterRun cr,
      XHTMLWriteBuffer writeBuffer,
      boolean skipStyling,
      XHTMLContentHandler xhtml)
      throws SAXException {
    // supporting for bookmarks in text
    Bookmark bm = null;
    if (!openbms.isEmpty())
      {
        bm = openbms.peek();
        while (!openbms.isEmpty() && bm.getEnd() <= cr.getStartOffset()) {
          bm = openbms.pop();
          endTag(xhtml, writeBuffer, "a");
        }
      }
    // list of possible in-text events. For example, start of bookmark or
    // tabulation.
    // Now includes bookmarks start\end and tabulation/
    SortedMap<Integer, Integer> spevent = new TreeMap<>();
    SortedMap<Integer, Bookmark> indexToBookmark = new TreeMap<>();

    for (List<Bookmark> bms :
        (document
            .getBookmarks()
            .getBookmarksStartedBetween(cr.getStartOffset(), cr.getEndOffset())
            .values()))
      for (Bookmark b : bms) {
        int index = b.getStart();
        spevent.put(index, 2);
        indexToBookmark.put(index, b);
        index = b.getEnd();
        if (spevent.containsKey(index)) {
          spevent.put(index, spevent.get(index) + 4);
        } else {
          spevent.put(index, 4);
        }
        if (index > cr.getEndOffset()) openbms.push(b);
      }

    // Skip trailing newlines
    if (!isRendered(cr) || cr.text().equals("\r")) return;
    // Clean up the text
    String text = cr.text();
    text = text.replace('\r', '\n');
    // it is \n according to word specification
    text = text.replace("\u000b", "\n");
    if (text.endsWith("\u0007")) {
      // Strip the table cell end marker
      text = text.substring(0, text.length() - 1);
    }

    // Copied from POI's
    // org/apache/poi/hwpf/converter/AbstractWordConverter.processCharacters:
    // Non-breaking hyphens are returned as char 30
    text = text.replace((char) 30, UNICODECHAR_NONBREAKING_HYPHEN);
    // Non-required hyphens to zero-width space
    text = text.replace((char) 31, UNICODECHAR_ZERO_WIDTH_SPACE);
    if (text.length() > 0) startCRStyling(xhtml, writeBuffer, cr);
    int tindex = text.indexOf('\t', 0);
    while (tindex >= 0) {
      if (spevent.containsKey(tindex + cr.getStartOffset())) {
        spevent.put(tindex + cr.getStartOffset(), 1 + spevent.get(tindex + cr.getStartOffset()));
      } else {
        spevent.put(tindex + cr.getStartOffset(), 1);
      }
      tindex = text.indexOf('\t', tindex + 1);
    }
    int startOffset = 0;
    // applying of different entities
    while (!spevent.isEmpty() && spevent.firstKey() <= cr.getEndOffset()) {
      int index = spevent.firstKey(), value = spevent.get(index);
      writeCharacters(xhtml, writeBuffer, text.substring(startOffset, index - cr.getStartOffset()));
      if ((value & 1) == 1) {
        startTag(xhtml, writeBuffer, "img", tabImageAttributes);
        endTag(xhtml, writeBuffer, "img");
      }
      if ((value & 2) == 2)
        startTag(xhtml, writeBuffer, "a", "id", indexToBookmark.get(index).getName());
      if ((value & 4) == 4) endTag(xhtml, writeBuffer, "a");
      startOffset = index - cr.getStartOffset() + (((value & 1) == 1) ? 1 : 0);
      spevent.remove(index);
    }

    writeCharacters(xhtml, writeBuffer, text.substring(startOffset, text.length()));
  }

  /**
   * Can be \13..text..\15 or \13..control..\14..text..\15 . Nesting is allowed
   *
   * @param skipfield
   */
  private int handleSpecialCharacterRuns(
      HWPFDocument document,
      XHTMLWriteBuffer writeBuffer,
      Paragraph p,
      int index,
      boolean skipStyling,
      PicturesSource pictures,
      boolean skipfield,
      XHTMLContentHandler xhtml)
      throws SAXException, TikaException, IOException {
    List<CharacterRun> controls = new ArrayList<>();
    List<Object> texts = new ArrayList<>();
    boolean has14 = false;
    boolean parsingSuccess = false;
    // Split it into before and after the 14
    int i;
    XHTMLWriteBuffer innerBuffer = null;
    for (i = index + 1; i < p.numCharacterRuns(); i++) {
      CharacterRun cr = p.getCharacterRun(i);
      if (cr.text().contains("\u0001")) {
        // drawing
        // TODO possibly here
      }
      if (cr.text().contains("\u0013")) {
        // Nested, oh joy...
        fieldLevel++;
        // buffering for nested field
        innerBuffer = new XHTMLWriteBuffer(new LinkedList<WordDocExtractor.XHTMLevent>());
        int increment =
            handleSpecialCharacterRuns(
                document, innerBuffer, p, i + 1, skipStyling, pictures, parsingSuccess, xhtml);
        texts.add(innerBuffer);
        i += increment;
      } else if (cr.text().contains("\u0014")) {
        has14 = true;
        if (cr.isOle2()) {
          parsingSuccess = parseEmbeddedWord(xhtml, "" + cr.getPicOffset());
        }
      } else if (cr.text().contains("\u0015")) {
        int k = cr.text().indexOf("\u0015");
        while (k > -1) {
          fieldLevel--;
          k = cr.text().indexOf("\u0015", k + 1);
        }

        if (!has14) {
          texts = new ArrayList<>();
          controls = new ArrayList<>();
        }
        break;
      } else {
        if (has14) {
          texts.add(cr);
        } else {
          controls.add(cr);
        }
      }
    }

    // Do we need to do something special with this?
    if (controls.size() > 0) {
      StringBuilder text = new StringBuilder(controls.get(0).text());
      for (int j = 1; j < controls.size(); j++) {
        text.append(controls.get(j).text().trim());
      }
      String textS = text.toString();
      if (textS.startsWith("PAGEREF")) {
        String url = "";
        int beg = textS.indexOf("_Toc"), k = beg + 4;
        while (Character.isDigit(text.charAt(k))) k++;
        url = text.substring(beg, k);
        String aurl = url;
        if (bookmarkNames.contains(url)) aurl = "#" + url;
        startTag(xhtml, writeBuffer, "a", "href", aurl);
        int j = 0;
        while (texts.size() > j) {
          if (texts.get(j) instanceof CharacterRun) {
            CharacterRun cr = (CharacterRun) texts.get(j);
            handleCharacterRun(document, cr, writeBuffer, skipStyling, xhtml);
          } else {
            ((XHTMLWriteBuffer) texts.get(j)).apply(xhtml, writeBuffer);
          }
          j++;
        }
        endTag(xhtml, writeBuffer, "a");
      } else if (textS.startsWith("REF")) {
        String url = "";
        int beg = textS.indexOf("REF") + 4, k = beg;
        while (Character.isLetterOrDigit(text.charAt(k))) k++;
        url = textS.substring(beg, k);
        String aurl = url;
        if (bookmarkNames.contains(url)) aurl = "#" + url;
        startTag(xhtml, writeBuffer, "a", "href", aurl);
        int j = 0;
        while (texts.size() > j) {
          if (texts.get(j) instanceof CharacterRun) {
            CharacterRun cr = (CharacterRun) texts.get(j);
            handleCharacterRun(document, cr, writeBuffer, skipStyling, xhtml);
          } else {
            ((XHTMLWriteBuffer) texts.get(j)).apply(xhtml, writeBuffer);
          }
          j++;
        }
        endTag(xhtml, writeBuffer, "a");
      } else if (textS.startsWith("HYPERLINK") && textS.indexOf('"') > -1) {
        String url = textS.substring(textS.indexOf('"') + 1, textS.lastIndexOf('"'));

        String aurl = url;
        if (bookmarkNames.contains(url)) aurl = "#" + url;
        startTag(xhtml, writeBuffer, "a", "href", aurl);
        int j = 0;
        while (texts.size() > j) {
          if (texts.get(j) instanceof CharacterRun) {
            CharacterRun cr = (CharacterRun) texts.get(j);
            handleCharacterRun(document, cr, writeBuffer, skipStyling, xhtml);
          } else {
            ((XHTMLWriteBuffer) texts.get(j)).apply(xhtml, writeBuffer);
          }
          j++;
        }
        endTag(xhtml, writeBuffer, "a");
      }
      if (!parsingSuccess && !skipfield) {
        // Just output the text ones
        for (Object o : texts) {
          if (o instanceof CharacterRun) {
            CharacterRun cr = (CharacterRun) o;
            // TODO character 8 is a drawing.
            // somewhere here we must use it
            if (pictures.hasPicture(cr)) {
              Picture picture = pictures.getFor(cr);
              handlePictureCharacterRun(cr, writeBuffer, picture, pictures, xhtml);
            } else {
              handleCharacterRun(document, cr, writeBuffer, skipStyling, xhtml);
            }
          } else {
            ((XHTMLWriteBuffer) o).apply(xhtml, writeBuffer);
          }
        }
      }
    } else if (!skipfield) {
      // We only had text
      // Output as-is
      for (Object o : texts) {
        if (o instanceof CharacterRun) {
          CharacterRun cr = (CharacterRun) o;
          handleCharacterRun(document, cr, writeBuffer, skipStyling, xhtml);
        } else {
          ((XHTMLWriteBuffer) o).apply(xhtml, writeBuffer);
        }
      }
    }

    // Tell them how many to skip over
    return i - index;
  }

  private boolean canHandle(DirectoryEntry dir) throws IOException {
    POIFSDocumentType type = POIFSDocumentType.detectType(dir);
    if (type != POIFSDocumentType.WORDDOCUMENT) return false;
    return true;
  }

  private boolean parseEmbeddedWord(XHTMLContentHandler xhtml, String offset)
      throws TikaException, SAXException, IOException {
    try {
      DirectoryEntry objectPool = (DirectoryEntry) rt.getEntry("ObjectPool");
      for (Entry entry : objectPool) {
        if (entry.getName().startsWith("_")
            && entry.getName().regionMatches(1, offset, 0, offset.length())
            && entry instanceof DirectoryEntry) {
          if (canHandle((DirectoryEntry) entry)) {
            handleEmbeddedOfficeDoc((DirectoryEntry) entry, xhtml, true);
            return true;
          } else return false;
        }
      }
      return false;
    } catch (FileNotFoundException e) {
      return false;
    }
  }

  @SuppressWarnings("deprecation")
  private void handlePictureCharacterRun(
      CharacterRun cr,
      XHTMLWriteBuffer writeBuffer,
      Picture picture,
      PicturesSource pictures,
      XHTMLContentHandler xhtml)
      throws SAXException, IOException, TikaException {
    if (!isRendered(cr) || picture == null) {
      // Oh dear, we've run out...
      // Probably caused by multiple \u0008 images referencing
      // the same real image
      return;
    }

    // Which one is it?
    String extension = picture.suggestFileExtension();
    int pictureNumber = pictures.pictureNumber(picture);
    if (pictureNumber == 0) {
      pictures.all.add(picture);
      pictureNumber = pictures.pictureNumber(picture);
    }
    // Make up a name for the picture
    // There isn't one in the file, but we need to be able to reference
    // the picture from the img tag and the embedded resource
    String filename = "image" + pictureNumber + (extension.length() > 0 ? "." + extension : "");

    // Grab the mime type for the picture
    String mimeType = picture.getMimeType();
    int aspectRatioX = 0;
    int aspectRatioY = 0;
    try {
      aspectRatioX = picture.getHorizontalScalingFactor();
      aspectRatioY = picture.getVerticalScalingFactor();
    } catch (Exception e) {
      // TODO bad bad picture(
    }
    float imageWidth = 0;
    float imageHeight = 0;

    float cropTop = 0;
    float cropBottom = 0;
    float cropLeft = 0;
    float cropRight = 0;
    try {
      imageWidth =
          aspectRatioX > 0
              ? Math.round(picture.getDxaGoal() * aspectRatioX * 1.0 / 1000 / TWIPS_PER_PIXEL)
              : picture.getDxaGoal() / TWIPS_PER_PIXEL;

      if (aspectRatioX > 0) {
        cropRight =
            Math.round(picture.getDxaCropRight() * aspectRatioX * 1.0 / 1000 / TWIPS_PER_PIXEL);
        cropLeft =
            Math.round(picture.getDxaCropLeft() * aspectRatioX * 1.0 / 1000 / TWIPS_PER_PIXEL);
      } else {
        imageWidth = picture.getWidth();
        cropRight = picture.getDxaCropRight() / TWIPS_PER_PIXEL;
        cropLeft = picture.getDxaCropLeft() / TWIPS_PER_PIXEL;
      }
      imageHeight =
          aspectRatioY > 0
              ? Math.round(picture.getDyaGoal() * aspectRatioY * 1.0 / 1000 / TWIPS_PER_PIXEL)
              : picture.getDyaGoal() / TWIPS_PER_PIXEL;
      if (aspectRatioY > 0) {
        cropTop = Math.round(picture.getDyaCropTop() * aspectRatioY * 1.0 / 1000 / TWIPS_PER_PIXEL);
        cropBottom =
            Math.round(picture.getDyaCropBottom() * aspectRatioY * 1.0 / 1000 / TWIPS_PER_PIXEL);
      } else {
        imageHeight = picture.getHeight();
        cropTop = picture.getDyaCropTop() / TWIPS_PER_PIXEL;
        cropBottom = picture.getDyaCropBottom() / TWIPS_PER_PIXEL;
      }
    } catch (Exception e) {
      // TODO exception handling
    }
    if (cr != null && mimeType.contains("wmf")) {
      if (cropTop != 0 || cropRight != 0 || cropBottom != 0 || cropLeft != 0) {
        float visibleWidth = Math.max(0, imageWidth - cropLeft - cropRight);
        float visibleHeight = Math.max(0, imageHeight - cropTop - cropBottom);
        filename = "image" + pictureNumber + ".svg";
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "data", "data", "", "embedded:" + filename);
        attributes.addAttribute("", "height", "height", "", "" + visibleHeight + "pt");
        attributes.addAttribute("", "width", "width", "", "" + visibleWidth + "pt");
        attributes.addAttribute("", "type", "type", "", "image/svg+xml"); // it
        // would
        // be
        // converted
        // to
        // svg
        // later
        startTag(xhtml, writeBuffer, "object", attributes);
        endTag(xhtml, writeBuffer, "object");
      } else {
        filename = "image" + pictureNumber + ".svg";
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "data", "data", "", "embedded:" + filename);
        if (imageHeight != 0) attributes.addAttribute("", "height", "height", "", "" + imageHeight);
        if (imageWidth != 0) attributes.addAttribute("", "width", "width", "", "" + imageWidth);
        attributes.addAttribute("", "type", "type", "", "image/svg+xml"); // it
        // would
        // be
        // converted
        // to
        // svg
        // later
        startTag(xhtml, writeBuffer, "object", attributes);
        endTag(xhtml, writeBuffer, "object");
      }
    } else {

      // Output the img tag
      AttributesImpl attr = new AttributesImpl();
      attr.addAttribute("", "src", "src", "CDATA", "embedded:" + filename);
      attr.addAttribute("", "alt", "alt", "CDATA", filename);
      if (imageWidth != 0) attr.addAttribute("", "width", "width", "", imageWidth + "");
      if (imageHeight != 0) attr.addAttribute("", "height", "height", "", imageHeight + "");
      startTag(xhtml, writeBuffer, "img", attr);
      endTag(xhtml, writeBuffer, "img");
    }
    // Have we already output this one?
    // (Only expose each individual image once)
    if (!pictures.hasOutput(picture)) {
      TikaInputStream stream = TikaInputStream.get(picture.getRawContent());
      try{
        handleEmbeddedResource(stream, filename, null, mimeType, xhtml, false);
        pictures.recordOutput(picture);
      }
      finally{
        stream.close();
      }
    }
  }

  /**
   * Outputs a section of text if the given text is non-empty.
   *
   * @param xhtml XHTML content handler
   * @param section the class of the &lt;div/&gt; section emitted
   * @param text text to be emitted, if any
   * @throws SAXException if an error occurs
   */
  /*
   * private void addTextIfAny( XHTMLContentHandler xhtml,
   * XHTMLWriteBuffer writeBuffer, String section, String text) throws
   * SAXException { if (text != null && text.length() > 0) {
   * startTag(xhtml, writeBuffer, "div", "class", section);
   * writeCharacters(xhtml, writeBuffer, text); endTag(xhtml, writeBuffer,
   * "div"); } } not used locally
   */

  @SuppressWarnings("deprecation")
  protected void parseWord6(DirectoryNode root, XHTMLContentHandler xhtml)
      throws IOException, SAXException {
    Word6Extractor extractor;
    //DO NOT put this in a try/autoclose.  This will close the root
    //which we don't want to do because there may be other
    //documents in the root.
    extractor = new Word6Extractor(root);

    for (String p : extractor.getParagraphText()) {
      xhtml.element("p", p);
    }
    
  }

  private static final Map<String, TagAndStyle> fixedParagraphStyles =
      new HashMap<>();
  private static final TagAndStyle defaultParagraphStyle = new TagAndStyle("p", null);

  static {
    fixedParagraphStyles.put("Default", defaultParagraphStyle);
    fixedParagraphStyles.put("Normal", defaultParagraphStyle);
    fixedParagraphStyles.put("heading", new TagAndStyle("h1", null));
    fixedParagraphStyles.put("Heading", new TagAndStyle("h1", null));
    fixedParagraphStyles.put("Заголовок", new TagAndStyle("h1", null));
    fixedParagraphStyles.put("Title", new TagAndStyle("h1", "title"));
    fixedParagraphStyles.put("Subtitle", new TagAndStyle("h2", "subtitle"));
    fixedParagraphStyles.put("HTML Preformatted", new TagAndStyle("pre", null));
    styleStopTags.add("table");
    styleStopTags.add("tr");
    styleStopTags.add("td");
    styleStopTags.add("th");
  }

  /** Given a style name, return what tag should be used, and what style should be applied to it. */
  public static TagAndStyle buildParagraphTagAndStyle(
      String styleName, int hlevel, boolean isTable) {
    TagAndStyle tagAndStyle = fixedParagraphStyles.get(styleName);
    if (tagAndStyle != null) {
      return tagAndStyle;
    }

    if (styleName.equals("Table Contents") && isTable) {
      return defaultParagraphStyle;
    }

    String tag = "p";
    String styleClass = null;

    if (styleName.startsWith("Heading ") && hlevel == 0)
      hlevel = Integer.parseInt(styleName.substring(8));

    if (hlevel > 0 && hlevel < 9) {
      int num = 1;
      try {
        num = hlevel;
      } catch (NumberFormatException e) {
      }
      // Turn it into a H1 - H6 (H7+ isn't valid!)
      tag = "h" + Math.min(num, 8);
    }
    styleClass = styleName.replace(' ', '_');
    styleClass = styleClass.substring(0, 1).toLowerCase() + styleClass.substring(1);
    return new TagAndStyle(tag, styleClass);
  }

  /**
   * Determines if character run should be included in the extraction.
   *
   * @param cr character run.
   * @return true if character run should be included in extraction.
   */
  private boolean isRendered(final CharacterRun cr) {
    return cr == null || !cr.isMarkedDeleted() || (cr.isMarkedDeleted() && officeParserConfig.isIncludeDeletedContent());
  }

  /**
   * Provides access to the pictures both by offset, iteration over the un-claimed, and peeking
   * forward
   */
  private static class PicturesSource {
    private PicturesTable picturesTable;
    private Set<Picture> output = new HashSet<>();
    private Map<Integer, Picture> lookup;
    private List<Picture> nonU1based;
    private List<Picture> all;
    private int pn = 0;

    private PicturesSource(HWPFDocument doc) {
      picturesTable = doc.getPicturesTable();
      all = picturesTable.getAllPictures();

      // Build the Offset-Picture lookup map
      lookup = new HashMap<>();
      for (Picture p : all) {
        lookup.put(p.getStartOffset(), p);
      }

      // Work out which Pictures aren't referenced by
      // a \u0001 in the main text
      // These are \u0008 escher floating ones, ones
      // found outside the normal text, and who
      // knows what else...
      nonU1based = new ArrayList<>();
      nonU1based.addAll(all);
      Range r = doc.getRange();
      for (int i = 0; i < r.numCharacterRuns(); i++) {
        CharacterRun cr = r.getCharacterRun(i);
        if (picturesTable.hasPicture(cr)) {
          Picture p = getFor(cr);
          int at = nonU1based.indexOf(p);
          nonU1based.set(at, null);
        }
      }
    }

    private boolean hasPicture(CharacterRun cr) {
      return picturesTable.hasPicture(cr);
    }

    private void recordOutput(Picture picture) {
      output.add(picture);
    }

    private boolean hasOutput(Picture picture) {
      return output.contains(picture);
    }

    private int pictureNumber(Picture picture) {
      return all.indexOf(picture) + 1;
    }

    private Picture getFor(CharacterRun cr) {
      return lookup.get(cr.getPicOffset());
    }

    /** Return the next unclaimed one, used towards the end */
    private Picture nextUnclaimed() {
      Picture p = null;
      while (pn < nonU1based.size()) {
        p = nonU1based.get(pn);
        pn++;
        if (p != null) return p;
      }
      return null;
    }
  }
}
