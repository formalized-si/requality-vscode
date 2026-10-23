package com.unitesk.requality.core.operation.document;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.nodetype.Requirement;

public class CustomTextParser extends DocumentProcessor {
  public static final String START_SECTION_PATTERN = "section_pattern",
      REQ_ID_PATTERN = "req_id_pattern",
      ATTRIB_PATTERN = "attribute_pattern",
      LEVEL_PATTERN = "level_pattern";
  private Pattern levelPattern;
  Map<String, Object> setts = new HashMap<>();
  public static Map<String, Object> defaultSettings = new HashMap<>();

  static {
    defaultSettings.put(START_SECTION_PATTERN, "^(.*)Page 2(.*)$");
    defaultSettings.put(REQ_ID_PATTERN, "<p>[\\s]*((RA[^\\s]+)[^\\n]*)");
    defaultSettings.put(
        ATTRIB_PATTERN, "[ \\t\\x0b\\r\\f]*([ \\t\\x0b\\r\\f\\w]+):[ \\t\\x0b\\r\\f]*([^\\n]*)");
    defaultSettings.put(LEVEL_PATTERN, "[\\W]");
  }

  @Override
  public boolean configure(com.unitesk.requality.nodetype.Document doc, Map<String,Object> params, PrintStream pw) {
    setts = params;
  //  if (setts == null || setts.isEmpty()) return false;
    TypedValue pt = (TypedValue) params.get(LEVEL_PATTERN);
    if(pt == null)
      return false;
    levelPattern = Pattern.compile(pt.getGValue());
    return super.configure(doc, params, pw);
  };

  private static byte BEFORE_BODY = 0, BEFORE_SECTION = 1, IN_SECTION = 2, REQUIREMENT = 3;

  private byte getLevel(String input) {
    Matcher m = levelPattern.matcher(input);
    byte i = 0;
    while (m.find()) i++;
    return i;
  }

  @Override
  protected boolean process(Document doc, Reader reader, Writer writer, TreeNode rootNode, PrintStream ps)
      throws IOException {
    byte level = 0;
    Map<Byte, Requirement> lastReqs = new HashMap<>();
    Requirement rootReq =
        (Requirement) rootNode.getTreeDB().getNode(Requirement.getTypeRootQId(), Path.QID);
    lastReqs.put(level, rootReq);
    BufferedReader br = new BufferedReader(reader);
    //new BufferedWriter(writer);
    short state = BEFORE_BODY;
    StringBuffer text = new StringBuffer(""),
        pcomposer = new StringBuffer(""),
        ptextcomposer = new StringBuffer("");
    TypedValue tv = (TypedValue) setts.get(ATTRIB_PATTERN); 
    String attrS = tv!=null?tv.getGValue():null;
    tv = (TypedValue) setts.get(START_SECTION_PATTERN);
    Pattern section = Pattern.compile(tv!=null?tv.getGValue():null);
    tv = (TypedValue) setts.get(REQ_ID_PATTERN);
    Pattern requirement = Pattern.compile(tv!=null?tv.getGValue():null);
    Pattern attribute = (attrS == null || attrS.equals("")) ? null : Pattern.compile(attrS);
    boolean ended = false;
    boolean inP = false;
    String locId = null, locA = "";
    ;
    String temps = null;
    while (br.ready()) {
      String s = null;
      if (temps != null && temps.length() > 0) {
        s = temps;
        temps = null;
      } else s = br.readLine();

      if (s.contains("</body>")) state = BEFORE_BODY;
      if (state == BEFORE_BODY) {
        writer.append(s);
        writer.append("\n");
        if (s.contains("<body>")) state = BEFORE_SECTION;
      } else if (state == BEFORE_SECTION) {
        writer.append(s.replaceAll("\\<[^>]*>", ""));
        if (!"</p>".equals(s) && !s.replaceAll("\\<[^>]*>", "").replace("\n", "").trim().isEmpty())
          writer.append("<br/>"); //restore paragraphs

        writer.append("\n");
        if (section.matcher(s).find()) state = IN_SECTION;
      } else {
        String textC = s.replaceAll("\\<[^>]*>", "");
        inP = s.contains("</p>");
        if (inP) {
          String pContent = pcomposer.toString(), pTextContent = ptextcomposer.toString();
          pcomposer.setLength(0);
          ptextcomposer.setLength(0);

          Matcher mr = requirement.matcher("\n" + pContent);
          boolean isr = mr.find();
          /*else
          if(state == ATTRIBUTE&&req!=null){
          	req.putAttribute(new Attribute(req, AttributeType.STRING, lastAKey, val));
          	req.attrs().saveAttributes();
          }*/
          boolean skipstr = false;
          if (isr) {
            text.setLength(0);
            state = REQUIREMENT;
            String id = mr.group(2), name = mr.group(1).trim();
            //get level
            byte ol = level;
            level = getLevel(id);
            for (byte i = ol; i >= level && i > 0; i--) {
              if (lastReqs.containsKey(i)) lastReqs.remove(i);
            }
            Requirement parent = lastReqs.get((byte) 0);
            if (level > 0) {
              ol = (byte) (level - 1);
              while ((ol - 1) > 0 && !lastReqs.containsKey(ol)) ol = (byte) (ol - 1);
              if (lastReqs.containsKey(ol)) parent = lastReqs.get(ol);
            }
            Requirement newR = (Requirement) parent.createChild(id);
            lastReqs.put(level, newR);
            Location location = doc.createLocation();
            locId = location.getSUUId();
            locA = "<a name=\"" + locId + "\" id=\"id_" + locId + "\" class=\"requality_id\"/>";
            newR.addLocation(location.getUUId());
            if (name.length() > 50) name = name.substring(0, 50);
            newR.setName(name);
            newR.commitAttributes();
            ended = false;
            temps = pContent.substring(mr.end() - 1);
            pTextContent = pContent.substring(0, mr.end() - 1).replaceAll("\\<[^>]*>", "");
            skipstr = true;
          } else {
            pcomposer.append(s);
            ptextcomposer.append(textC);
            if (!textC.replace("\n", "").trim().isEmpty()) {
              ptextcomposer.append("\n");
              pcomposer.append("\n");
            }
            text.append(pTextContent + "\n");
          }
          if (!pContent.trim().replace("\n", "").isEmpty()) {
            String w = pTextContent;
            if (w.startsWith("\n")) w = w.substring(1);
            if (state != REQUIREMENT || skipstr) {
              writer.append(w.replaceAll("\n", "<br/>") + "<br/>");
              writer.append("\n");
            } else {
              writer.append(
                  "<span class=\"requality_text id_"
                      + locId
                      + "\">"
                      + locA
                      + w.replaceAll("\n", "<br/>")
                      + "</span>"
                      + "<br/>");
              writer.append("\n");
              locA = "";
            }
            Requirement req = lastReqs.get(level);
            if (req != null && state == REQUIREMENT) {
              String val = text.toString();
              while (val.indexOf(" \n") > -1) val = val.replaceAll(" \n", "\n");
              while (val.indexOf("\n\n") > -1) val = val.replaceAll("\n\n", "\n");
              text.setLength(0);
              if (!ended) {
                ended = true;
              }

              Matcher ma = null;
              if (attribute != null)
              {
                ma = attribute.matcher("\n" + val);
                if(ma!=null){
                  boolean isa = ma.find();
                  while (isa) {
                    String lastAKey = ma.group(1);
                    int end = ma.end();
                    String v = "";
                    if (ma.groupCount() > 2 && ma.group(2) != null && !ma.group(2).trim().equals("")) {
                      v = ma.group(2);
                    }
                    val = val.substring(0, ma.start()) + val.substring(ma.end() - 1);
                    end = ma.start();
                    ma = attribute.matcher("\n" + val);
                    v = v.trim();
                    while (v.startsWith("\n")) v = v.substring(1);
                    while (v.endsWith("\n")) v = v.substring(0, v.length() - 2);
                    if (v.trim().equals("")) {
                      String pval = val.substring(end);
                      Matcher mch = attribute.matcher("\n" + pval);
                      if (!mch.find()) {
                        v = pval;
                        val = "";
                      } else {
                        v = pval.substring(0, mch.start() - 1);
                        val = val.substring(mch.start());
                      }
                      ma = attribute.matcher("\n" + val);
                    }
                    v = v.trim();
                    while (v.startsWith("\n")) v = v.substring(1);
                    while (v.endsWith("\n")) v = v.substring(0, v.length() - 2);
                    req.attrs().setConstAttribute(lastAKey, v, AttributeDefinition.detectFlags(lastAKey));
                    isa = ma.find();
                  }
                }
              }
              if (val.startsWith("\n")) val = val.substring(1);
              if (val.endsWith("\n")) val = val.substring(0, val.length() - 2);
              if (req.getDescription() != null) val = req.getDescription() + "\n" + val;
              req.setDescription(val);
              req.commitAttributes();
            }
          }
        } else {
          pcomposer.append(s);
          ptextcomposer.append(textC);
          if (!textC.replace("\n", "").trim().isEmpty()) {
            ptextcomposer.append("\n");
            pcomposer.append("\n");
          }
        }
      }

      //						if(state == ATTRIBUTE || state == REQUIREMENT){
      //text excluding tags
      //					}

    }
    String val = text.toString();
    Requirement req = lastReqs.get(level);
    if (state == REQUIREMENT) {
      if (!ended) {
        ended = true;
      }
      req.setDescription(val);
      req.commitAttributes();
    }
    ps.append("Processing completed");
    return true;
  }
}
