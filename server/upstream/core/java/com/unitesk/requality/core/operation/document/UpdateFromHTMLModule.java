package com.unitesk.requality.core.operation.document;

import java.io.CharArrayWriter;
import java.io.File;
import java.io.PrintStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import com.unitesk.requality.core.attribute.AppliedEnumValue;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeAvailability;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeStorage;
//import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.EnumDefinitionValue;
import com.unitesk.requality.core.attribute.EnumValue;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.OperationNotAllowedException;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.attribute.valueprovider.ConstValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.HtmlValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.IValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.TempFolderFilesProvider;
import com.unitesk.requality.core.fid.FIDUpdater;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.NodeDesc;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.mass.LibreOfficeAfterProcessing;
import com.unitesk.requality.core.operation.mass.TermsHandler;
import com.unitesk.requality.core.sorting.SortingSettings;
import com.unitesk.requality.core.sorting.SortingSettings.SortingMethod;
import com.unitesk.requality.core.sorting.TreeNodeComparator;
import com.unitesk.requality.core.storage.LinkResolver;
import com.unitesk.requality.core.storage.TermsManipulator;
import com.unitesk.requality.core.tree.ITreeLogic.IdContainsWrongChars;
import com.unitesk.requality.core.tree.ITreeLogic.IdEmpty;
import com.unitesk.requality.core.tree.TreeLogic;
import com.unitesk.requality.nodetype.Comment;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TextNode;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.DeepFirstTreeWalker;
import com.unitesk.requality.tool.HTMLSpecChars;
import com.unitesk.requality.tool.Index;
import com.unitesk.requality.tool.cli.RequalityCLI;

/*
 * Some notes
 * Alg on indexes - old are saved to ind
 */

public class UpdateFromHTMLModule extends SAXDocumentProcessor {
  private static final Map<String, String> keyRepl;
  private double ufweigth = 2.0d, doorsid = 5.0d;
  private static Set<String> knownClasses = new HashSet<>();
  private String author = null;
  private TreeNode root = null;
  private static Map<String, String> baseClasses = new HashMap<>();
  private static Map<UUID, Set<String>> tdef = new HashMap<>();
  private TreeDB db;
  private static Map<String, UUID> tdefpos = new HashMap<>();
  private static Set<String> forceNotList = new HashSet<>();
  private Map<UUID, Index> newInd = new HashMap<>();
  private StringBuilder warn = new StringBuilder();

  static {
    keyRepl = new HashMap<>();
    keyRepl.put(TermsManipulator.USE_TERM_ALT_ATTR_NAME.toLowerCase(), TermsManipulator.USE_TERM_ATTR_NAME);
    keyRepl.put("name", TreeNode.ATTR_NAME);
    //clas.toLowerCase().contains("reqheader")&&!clas.contains("requirement")&&!clas.toLowerCase().contains("textheader")&&!clas.contains("textnode")
    //	&&!clas.toLowerCase().contains("reqHeaderleaf")&&!clas.toLowerCase().contains("attribute")&&!clas.toLowerCase().contains("todo")
    baseClasses.put("reqheader", "");
    baseClasses.put("requirement", Requirement.TYPE_NAME);
    baseClasses.put("textheader", TextNode.TYPE_NAME);
    baseClasses.put("textnode", TextNode.TYPE_NAME);
    baseClasses.put("attribute", "");
    baseClasses.put("todo", Comment.TYPE_NAME);
    baseClasses.put("meta", "");
    knownClasses.add("reqheader");
    knownClasses.add("requirement");
    knownClasses.add("textheader");
    knownClasses.add("textnode");
    knownClasses.add("attribute");
    knownClasses.add("todo");
    knownClasses.add("meta");
    forceNotList.add(TreeNode.ATTR_NAME);
  }

  private Map<String, String> classes;

  private class InterestingStackState {
    private int stsize;
    private String tag, cl;

    private InterestingStackState(String t, String c) {
      this.stsize = tempString.length();
      this.tag = t;
      this.cl = c;
    }

    private boolean isRelated(EndElement ee) {
      return ee.getName().getLocalPart().equals(tag);
    }

    private String getCl() {
      return cl;
    }

    private String getTag() {
      return tag;
    }

    @Override
    public String toString() {
      return tag + "-" + stsize + "[" + cl + "]";
    }
  }

  private class HelpMark {
    String value;
    Integer temppos;

    public HelpMark(String value) {
      this.value = value;
      if (value.endsWith("-header")) {
        value = value.substring(0, value.length() - 7);
      } else if (value.endsWith("-text")) value = value.substring(0, value.length() - 5);
      temppos = tempString.length();
    }

    public String getValue() {
      return value;
    }

    public boolean isAfter(int pos) {
      return pos >= temppos;
    }
  }

  LinkedList<InterestingStackState> interest = new LinkedList<>();
  LinkedList<HelpMark> marks = new LinkedList<>();
  Map<Integer, TreeNode> lastNodes = new HashMap<>();
  StringBuffer tempString = new StringBuffer();
  StringBuffer csstext = new StringBuffer();
  private LinkedList<String> ignoreNext = new LinkedList<>();
  private LinkedList<String> notignoreNext = new LinkedList<>();

  private int lastlvl = 0;
  private LinkResolver lr = null;
  private Map<UUID, NodeDesc> ndmap = new HashMap<>();
  private Map<UUID, Integer> ndlvl = new HashMap<>();
  private Map<UUID, Integer> ndInd = new HashMap<>();
  private Map<Integer, LinkedList<NodeDesc>> nodeByLvl =
      new HashMap<>();
  private Map<UUID, List<String>> nodeMarks = new HashMap<>();

  private Map<UUID, LinkedList<UUID>> structuralChildren = new HashMap<>();
  private Map<UUID, UUID> structuralParent = new HashMap<>();

  private Map<UUID, LinkedList<UUID>> nameChildren = new HashMap<>();
  private Map<UUID, List<UUID>> nameParents = new HashMap<>();
  private Map<UUID, String> parentName = new HashMap<>();

  private Map<String, TreeNode> nodesByDoors = new HashMap<>();

  public void addStructuralChild(UUID parent, UUID child) {
    if (parent != null) {
      if (!structuralChildren.containsKey(parent))
        structuralChildren.put(parent, new LinkedList<UUID>());
      structuralChildren.get(parent).add(child);
    }
    structuralParent.put(child, parent);
  }

  private String getUVID(NodeDesc nd) {
    String ret = "";
    UUID par = null;
    if (nameParents.containsKey(nd.getUUId())
        && nameParents.get(nd.getUUId()) != null
        && !nameParents.get(nd.getUUId()).isEmpty())
      par = nameParents.get(nd.getUUId()).get(0); //all tends to have the same name
    if (par != null && ndmap.get(par) != null) ret += getUVID(ndmap.get(par)) + "/";
    return ret + getNameOrId(nd);
  }

  public void addNameChild(List<UUID> pars, UUID child, String parname) {
    for (UUID parent : pars) {
      if (!nameChildren.containsKey(parent)) nameChildren.put(parent, new LinkedList<UUID>());
      if (!nameChildren.get(parent).contains(child)) nameChildren.get(parent).add(child);
    }
    nameParents.put(child, pars);
    parentName.put(child, parname);
  }

  @Override
  protected void initProcess(TreeNode pnode) {
    cnt = 1;
    warn.setLength(0);
    rootNode = null;
    db = pnode.getTreeDB();
    root = db.getRootNode();
    author = null;
    csstext.setLength(0);
    bodystarted = false;
    Attribute a = doc.attrs().getAttribute("author");
    if (a != null) author = a.getValue().toString();
    classes = new HashMap<>();
    classes.putAll(baseClasses);
    a = doc.attrs().getAttribute("classMap");
    if (a != null){
      TypedValue tv = a.getTValue();
      if(tv!=null && tv.getValue()!=null &&
        tv.getType() == AttributeType.LIST &&
        ((ListValue)tv.getGValue()).getType() == AttributeType.STRING) {
          List<?> l = ((ListValue)tv.getGValue()).getValues();
          String f, s;
          for (Object o : l) {
            String st = o.toString();
            int ind = st.indexOf("|");
            if (ind > -1) {
              f = st.substring(0, ind);
              s = st.substring(ind);
              classes.put(f, s);
            }
          }
        }
    }

    //processnode = db.getNode(Requirement.getTypeRootQId());
    tempString.setLength(0);
    nodeByLvl.clear();
    ndmap.clear();

    lr = db.getLinkResolver();
    structuralChildren.clear();
    nameChildren.clear();
    structuralParent.clear();
    nameParents.clear();

    //tempRoot = newNodeDesc(uid, null, "_root", new HashMap<String,Attribute>(){{put(TreeNode.ATTR_TYPE,new Attribute(uid,AttributeType.STRING,TreeNode.ATTR_TYPE,Requirement.TYPE_NAME));}},
    //	new LinkedList<UUID>());
    //nodeByLvl.put(0, new LinkedList<NodeDesc>(){{add(tempRoot);}});

    final UUID uid = UUID.randomUUID();
    nameChildren.put(uid, new LinkedList<UUID>());
    nodeByLvl.put(0, new LinkedList<NodeDesc>());
    nodeByLvl
        .get(0)
        .add(
            new NodeDesc(
                db,
                uid,
                null,
                null,
                null,
                "Requirements",
                AttributeStorage.formTypeMap(uid, Requirement.TYPE_NAME),
                Collections.emptyList(),
                false));
    ndmap.put(uid, nodeByLvl.get(0).get(0));
    ndlvl.put(uid, 0);
    maxInd = 1;
    ndInd.put(uid, 0);
    lastlvl = 1;
    nodesByDoors.clear();
    DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(db.getNode(Requirement.getTypeRootQId()));
    Iterator<TreeNode> id = dftw.iterator();
    String fidname = FIDUpdater.getFIDName(db);
    while (id.hasNext()) {
      TreeNode nde = id.next();
      String did = nde.attrs().getStringAttributeValue(fidname);
      if (did != null) {
        //String did = at.getValue().toString();
        nodesByDoors.put(did, nde);
      }
    }
    Attribute ba = root.getTreeDB().getRootNode().attrs().getAttribute("Вид");
    if (ba == null || ba.getType() != AttributeType.ENUM_DEFINITION) {
      EnumDefinitionValue edv =
          new EnumDefinitionValue(
              AttributeType.STRING,
              new ArrayList<EnumValue>() {
                /** */
                private static final long serialVersionUID = -5007683111859282645L;

                {
                  add(new EnumValue("Требование", null));
                  add(new EnumValue("Описание", null));
                  add(new EnumValue("Примечание", null));
                  add(new EnumValue("Обоснование", null));
                  add(new EnumValue("Заголовок", null));
                }
              });
      //enumDefs.put("Вид", edv);
      root.getTreeDB().getRootNode().attrs().setConstAttribute("Вид", edv, 0);
      root.getTreeDB().getRootNode().commitAttributes();
    }
    TreeNode store = root.getTreeDB().getRootNode();
    Attribute prim = store.attrs().getAttribute("Производное");
    if (prim == null) {
      List<EnumValue> lv = new ArrayList<>();
      lv.add(new EnumValue(" ", null));
      lv.add(new EnumValue("Пр", null));
      EnumDefinitionValue edv = new EnumDefinitionValue(AttributeType.STRING, lv);
      store.attrs().setConstAttribute("Производное", edv, 0);
      store.commitAttributes();
    }
  }

  private boolean isUsefull(String name) {
    return !name.equals("br");
  }

  private int cnt = 1;
  private int maxInd = 1;

  private NodeDesc newNodeDesc(
      UUID uid, final NodeDesc par, String id, Map<String, AttributeDefinition> attrs, LinkedList<UUID> chi) {
    AttributeDefinition la = attrs.get(TreeNode.ATTR_NAME);
    AttributeDefinition aname = null;
    if (la != null) aname = la;
    String name = aname == null ? null : aname.getRawValue().getValue().toString();
    NodeDesc ne =
        new NodeDesc(
            db,
            uid,
            null,
            par == null ? null : par.getUUId(),
            par == null ? null : par.getUUId(),
            id,
            attrs,
            chi,
            false);
    ndmap.put(uid, ne);
    int plvl = 0;
    if (par != null) {
      addNameChild(
          new ArrayList<UUID>() {
            private static final long serialVersionUID = -4802231037500064069L;

            {
              {
                add(par.getUUId());
              }
            }
          },
          uid,
          getNameOrId(par));
      plvl = ndlvl.get(par.getUUId());
    }
    if (nodeMarks.containsKey(uid)) {
      ne.setAttribute(
        new AttributeDefinition(
          uid,
          "_tags",
          AttributeAvailability.LOCAL,
          new ConstValueProvider(AttributeType.LIST, new ListValue(AttributeType.STRING, new ArrayList<Object>(nodeMarks.get(uid)))),
          Flag.HIDDEN
        )
      );
    }
    ndlvl.put(uid, plvl + 1);
    ndInd.put(uid, maxInd++);
    lr.updateNameInCache(uid, name);
    //		addNameChild(new LinkedList<UUID>(){{add(root.getUUId());}}, uid, "/");
    if (!nodeByLvl.containsKey(plvl + 1)) nodeByLvl.put(plvl + 1, new LinkedList<NodeDesc>());
    LinkedList<NodeDesc> lvls = nodeByLvl.get(plvl + 1);
    ne.setAttribute(
        new AttributeDefinition(
            uid,
            TreeDB.INDEX_ATTRIBUTE,
            new ConstValueProvider(AttributeType.INT, Integer.toString(cnt++)),
            Flag.HIDDEN));

    if (lvls != null && !lvls.isEmpty()) {
      NodeDesc nd2 = lvls.iterator().next();
      if (par == null || nameParents.containsKey(nd2.getUUId())
          && !nameParents.get(nd2.getUUId()).isEmpty()
          && !nameParents.get(nd2.getUUId()).iterator().next().equals(par.getUUId()))
        lvls = new LinkedList<>();
    } else lvls = new LinkedList<>();
    lvls.addLast(ne);
    nodeByLvl.put(plvl + 1, lvls);
    return ne;
  }

  private boolean bodystarted = false, cssstarted = false;

  @SuppressWarnings("unused")
  private LinkedList<XMLEvent> writeStack = new LinkedList<XMLEvent>();

  private Set<String> torem = new HashSet<>();
  private Stack<String> opennedTags = new Stack<>();
  private Stack<boolean[]> state = new Stack<>();
  private TreeNode rootNode = null;;

  @Override
  protected boolean processEvent(
      XMLEvent event, XMLEventFactory eventFactory, XMLEventWriter writer, TreeNode rootNode,PrintStream ps)
      throws Exception {
    writer.add(event);
    writeStack.addLast(event);
    event = null;
    if (writeStack.size() > 4) event = writeStack.removeFirst();
    if (event == null) return true;
    handleEvent(rootNode, event, ps);
    return true;
  }

  private static int BOLD = 0, ITALIC = 1, UNDERLINED = 2, STRIKED = 3;
  //bold, italic,underlined,oblique
  private boolean[] styles = new boolean[] {false, false, false, false, false};
  private String lasttext = null;

  private String getClosestStyle(int st) {
    for (int i = opennedTags.size() - 1; i >= 0; i--) {
      String s = opennedTags.get(i);
      if (st == BOLD && (s.toLowerCase().equals("b") || s.toLowerCase().equals("strong"))) return s;
      if (st == ITALIC && (s.toLowerCase().equals("i") || s.toLowerCase().equals("em"))) return s;
      if (st == STRIKED && (s.toLowerCase().equals("s") || s.toLowerCase().equals("del"))) return s;
      if (st == UNDERLINED && (s.toLowerCase().equals("u"))) return s;
    }
    return "";
  }

  private String getSimpleStyle(int st) {
    if (st == BOLD) return "b";
    if (st == ITALIC) return "i";
    if (st == STRIKED) return "s";
    if (st == UNDERLINED) return "u";
    return "";
  }

  private boolean hasActiveSimpleStyle(int st) {
    for (String s : opennedTags) {
      if (st == BOLD && (s.toLowerCase().equals("b") || s.toLowerCase().equals("strong")))
        return true;
      if (st == ITALIC && (s.toLowerCase().equals("i") || s.toLowerCase().equals("em")))
        return true;
      if (st == STRIKED && (s.toLowerCase().equals("s") || s.toLowerCase().equals("del")))
        return true;
      if (st == UNDERLINED && (s.toLowerCase().equals("u"))) return true;
    }
    return false;
  }
  private PrintStream ps;
  private void handleEvent(TreeNode rootNode, XMLEvent event, PrintStream ps) {
    this.ps = ps;
    if (event.isStartElement()) {
      lasttext = null;
      StartElement se = event.asStartElement();
      String localname = se.getName().getLocalPart();
      state.add(Arrays.copyOf(styles, styles.length));
      opennedTags.add(localname);

      if (localname.toLowerCase().equals("b") || localname.toLowerCase().equals("strong"))
        styles[BOLD] = true;
      if (localname.toLowerCase().equals("i") || localname.toLowerCase().equals("em"))
        styles[ITALIC] = true;
      if (localname.toLowerCase().equals("s") || localname.toLowerCase().equals("del"))
        styles[STRIKED] = true;
      if (localname.toLowerCase().equals("u")) styles[UNDERLINED] = true;

      Iterator<?> it = se.getAttributes();
      String css = null, ac = null;
      while (it.hasNext()) {
        javax.xml.stream.events.Attribute a = (javax.xml.stream.events.Attribute) it.next();
        if (a != null) {
          String key = a.getName().getLocalPart();
          if (key.toLowerCase().equals("style")) css = a.getValue();
          if (key.toLowerCase().equals("class")) ac = locateStyles(localname, a.getValue());
        }
      }
      if (ac != null) css = css != null ? (ac + ";" + css) : ac;
      if (css != null) {
        Map<String, String> kv = parseCSS(css);
        for (String s : kv.keySet()) {
          String val = kv.get(s).toLowerCase();
          switch (s.toLowerCase()) {
            case "font-style":
              {
                if (val.contains("normal")) {
                  //styles[OBLIQUE] = false;
                  styles[ITALIC] = false;
                }
                if (val.contains("italic")) {
                  styles[ITALIC] = true;
                }
                if (val.contains("oblique")) {
                  styles[ITALIC] = true; //TODO fix
                }
              }
              break;
            case "font-weight":
              {
                if (val.contains("bold")) {
                  styles[BOLD] = true;
                }
                if (val.contains("normal")) {
                  styles[BOLD] = false;
                }
              }
              break;
            case "text-decoration":
              {
                if (val.contains("overline")) {
                  //TODO overline
                }
                if (val.contains("line-through")) styles[STRIKED] = true;
                if (val.contains("underline")) {
                  styles[UNDERLINED] = true;
                }
                if (val.contains("none")) {
                  styles[UNDERLINED] = false;
                  styles[STRIKED] = false;
                }
              }
              break;
          }
        }
      }

      if (tempString.toString().length() > 0 && bodystarted && interest.isEmpty()) {
        NodeDesc ln = locateLast(lastlvl, null, false);
        if (ln != null) {
          AttributeDefinition desc = ln.getAttributes().get(TreeNode.ATTR_DESCRIPTION);
          //					if(desclist!=null && !desclist.isEmpty())
          //						desc = desclist.iterator().next();
          String val = "";
          HtmlValueProvider old = null;
          if (desc != null) {
            val = desc.getRawValue().getValue().toString();
            old = (HtmlValueProvider) desc.getValueProvider();
          }
          val += tempString.toString();
          tempString.setLength(0);
          ;
          ln.setAttribute(
              new AttributeDefinition(ln.getUUId(), TreeNode.ATTR_DESCRIPTION,
                  old!=null?old.createAppended(AttributeType.HTML, val):
                  new HtmlValueProvider(AttributeType.HTML, val, 
                    TempFolderFilesProvider.getNewNotInited(AttributeType.HTML)),Flag.HIDDEN
              )
          );
        }
      }
      if (localname.toLowerCase().equals("body")) {
        bodystarted = true;
        //find css classes here

      }
      if (bodystarted && localname.toLowerCase().equals("hr")) bodystarted = false;
      if (!bodystarted && localname.equals("style")) //may be the one{
      {
        javax.xml.stream.events.Attribute a = se.getAttributeByName(new QName("type"));
        if (a != null && a.getValue().equals("text/css")) //css
        cssstarted = true;
      }
      boolean ignore = false;
      String toadd = null, toaddclass = null;
      boolean isInterest = false;
      if (isUsefull(localname) && bodystarted) {
        if (localname.equals("p")
            || localname.equals("div")
            || localname.equals("pre")
            || localname.equals("span")
            || localname.equals("ul")
            || localname.equals("ol")
            || localname.startsWith("h") && localname.length() == 2) {
          javax.xml.stream.events.Attribute a = se.getAttributeByName(new QName("class"));
          if (a != null && !a.getValue().equals("") && isInteresting(a.getValue())
              || interest.isEmpty()
                  && (localname.equals("p")
                      || localname.startsWith(
                          "h"))) { //if there is no openned reqs then we see new header or text
            toadd = localname;
            toaddclass = a == null ? null : a.getValue();
            ignore = !localname.equals("pre") && !localname.equals("ol") && !localname.equals("ul");
            isInterest = true;
          }
        }
        if (localname.equals("input")) {
          javax.xml.stream.events.Attribute aid = se.getAttributeByName(new QName("type"));
          if (aid != null && aid.getValue().equals("hidden")) {
            //tends to be attributes
            javax.xml.stream.events.Attribute aval = se.getAttributeByName(new QName("value"));
            if (aval != null) {
              ignore = true;
            }
          }
        }
        if (localname.equals("a")) {
          javax.xml.stream.events.Attribute aid = se.getAttributeByName(new QName("id"));
          javax.xml.stream.events.Attribute aname = se.getAttributeByName(new QName("name"));
          javax.xml.stream.events.Attribute clss = se.getAttributeByName(new QName("class"));
          String val = null;
          if (aid != null) val = aid.getValue();
          if (val == null && aname != null) val = aname.getValue();
          if (clss != null && clss.getValue().equals("rootPath")) {
            if (val == null || val.equals("")) val = "/";
            if (val.startsWith("'") || val.startsWith("\""))
              val = val.substring(1, val.length());
            if (val.endsWith("'") || val.endsWith("\""))
              val = val.substring(0, val.length() - 1);
            ignore = true;
          } else if (val != null) {
            marks.addLast(new HelpMark(val));
            ignore = true;
          }
        }
      }
      if (localname.equals("input")) //may be attributes
      ignore = true;
      if (!ignore && bodystarted && !localname.equals("body")) {
        ignore = toString(se, tempString);
      }
      if (isInterest) interest.addLast(new InterestingStackState(toadd, toaddclass));

      if (ignore) ignoreNext.addLast(localname);
      else if (!ignoreNext.isEmpty() && ignoreNext.getLast().equals(localname))
        notignoreNext.addLast(localname);
    }
    if (event.isEndElement()) {
      EndElement ee = event.asEndElement();
      String localname = ee.getName().getLocalPart();
      String oname = "";
      if (!opennedTags.isEmpty()) oname = opennedTags.pop();
      styles = state.pop();
      if (localname.equals("li")) {
        if (lasttext == null || lasttext.isEmpty()) {
          if (!lookLeft(opennedTags)) {
            opennedTags.add("li");
            state.add(Arrays.copyOf(styles, styles.length));
          } else {
            if (tempString.length() > 4
                && tempString
                    .substring(tempString.length() - 4, tempString.length())
                    .equals("<li>")) {
              tempString.setLength(tempString.length() - 4);
              tempString.append("</li><li>");

              opennedTags.pop();
              state.pop();
              opennedTags.add("li");
              state.add(Arrays.copyOf(styles, styles.length));
            }
          }
          //					state.pop();
          //					state.add(Arrays.copyOf(styles,styles.length));
          return; //right?
        } else {
          //	oname = opennedTags.pop();
          //styles = state.pop();
        }
      }
      if (oname.equals("li") && !oname.equals(localname)) {
        oname = opennedTags.pop();
        styles = state.pop();
      }
      while (!oname.equals(localname) && opennedTags.size() > 2) {
        ps.println(
            "Strange happens - " + oname + " oppened but " + localname + " was closed");
        if (oname.equals("li")
            && tempString.length() > 4
            && tempString.substring(tempString.length() - 4, tempString.length()).equals("<li>")) {
          tempString.setLength(tempString.length() - 4);
        } else {
          tempString.append("</" + oname + ">");
          if (opennedTags.peek().equals(oname)) {
            oname = opennedTags.pop();
            styles = state.pop();
          }
        }
      }
      if (localname.equals("style") && !bodystarted) cssstarted = false;
      InterestingStackState st = null;
      boolean ignore = false;
      if (!ignoreNext.isEmpty() && ignoreNext.getLast().equals(localname)) {
        if (!notignoreNext.isEmpty() && notignoreNext.getLast().equals(localname))
          notignoreNext.removeLast(); //TODO here
        else {
          ignoreNext.removeLast();
          ignore = true;
        }
      }
      Integer pos = -1;
      if (bodystarted) {
        if (isUsefull(localname)) {
          if (!interest.isEmpty() && interest.getLast().isRelated(ee)) {
            st = interest.removeLast();
            pos = st.stsize;
          }
        }
        if (!interest.isEmpty() && (localname.equals("p") || localname.equals("div"))) {
          tempString.append("<br/>");
        }
//        if (!ignore) {
//          tempString.append("</" + localname + ">");
//        }
          if (!ignore && bodystarted) {
            int k = tempString.length();
            toString(ee, tempString);
          }
        if (st!=null && interest.isEmpty() && opennedTags.size() == 2) {
          if(!ignore&&bodystarted)
            toString(ee, tempString);
          String rawcontent = tempString.toString(); //.substring(pos);
          String content = rawcontent.replace("<span></span>", "").replace((char) 160 + "", "");

          int stt = 0, en = content.length() - 1;
          while (stt < (content.length() - 1)
              && (Character.isWhitespace(content.charAt(stt)) || content.charAt(stt) == (char) 160))
            stt++;
          while (en > 0
              && (Character.isWhitespace(content.charAt(en)) || content.charAt(en) == (char) 160))
            en--;
          if (en > -1 && en >= stt) {
            if (((en + 1) < content.length() || stt > 0)) content = content.substring(stt, en + 1);
          } else content = "";
          if (content.startsWith("<br/>")) content = content.substring(5);
          //tempString.setLength(pos);

          tempString.setLength(0);
          LinkedList<HelpMark> suitable = new LinkedList<>();

          while (!marks.isEmpty() && marks.getLast().isAfter(pos))
            suitable.addLast(marks.removeLast());
          String tag = st.getTag(), clas = st.getCl();

          List<String> guesses = new LinkedList<>();
          int lvl = lastlvl;
          int plvl = lastlvl - 1;
          for (HelpMark hm : suitable)
            try {
              String s = hm.getValue().trim();
              //TODO mb fix
              if (s.endsWith("-header")) s = s.substring(0, s.length() - 7);
              if (s.endsWith("-text")) s = s.substring(0, s.length() - 5);
              if (!s.isEmpty()) {
                //TreeNode node = rootNode.getTreeDB().getNode(s,Path.QID|Path.UV|Path.UUID);
                //if(node!=null)
                guesses.add(s);
              }
            } catch (Exception e) {
              RLogger.logError(e);
            }
          boolean toadd = true;
          //if(!isInteresting(clas))
          //clas=null;
          if (!content.isEmpty()) {
            if (tag.equals("p")) content = "<br>" + content;
          }
          boolean isHeader = false;
          if (!hasKnownClass(clas)) { //absolutely new
            if (tag.toLowerCase().startsWith("h")) { //header
              //lvl = lastlvl;//Integer.parseInt(tag.substring(1));
              if (content.trim().contains("<strike") && content.trim().contains("</strike>")) {
                String name = content.trim();
                int start = name.indexOf("<strike"), end = name.indexOf("</strike>");
                torem.add(CoreUtils.htmlClear(name.substring(name.indexOf(">", start) + 1, end)));
                content = name.substring(0, start) + name.substring(end + 9);
                //	return;//TODO check
              }
              {
                lvl = 1;
                int spos = content.indexOf("/");
                while (spos > -1) {
                  spos = content.indexOf("/", spos + 1);
                  lvl++;
                }
                plvl = lvl - 1;
                isHeader = true;
              }
              //struct requirement
            } else {
              lvl = lastlvl;
              plvl = lastlvl - 1;
              //TODO WUT?
              //	Object[]res= locateStruct(lvl,tag.toLowerCase().startsWith("h")?Requirement.TYPE_NAME:TextNode.TYPE_NAME,
              //									"text");
              //						lvl = (Integer)res[0];
              NodeDesc nd =
                  locateLast(lvl, new String[] {Requirement.TYPE_NAME, TextNode.TYPE_NAME}, true);
              if (nd == null) toadd = true;
              else {
                toadd = addNewTNCheck(nd, content);
                if (!toadd) {
                  content = "";
                  lastlvl = ndlvl.get(nd.getUUId());
                } else {
                  plvl = lastlvl;
                  lvl = plvl + 1;
                  //lastlvl = lvl;//add at the same lvl
                }
              }
            }
          } else {
            if (clas.toLowerCase().contains("todo")) {
              lvl = lastlvl + 1;
              plvl = lastlvl;
              NodeDesc nd =
                  locateLast(plvl, new String[] {Requirement.TYPE_NAME, TextNode.TYPE_NAME}, true);
              if (nd != null) toadd = addNewTODOCheck(nd, content);

              //	lvl = (Integer) locateStruct(lvl,Comment.TYPE_NAME,
              //		"text")[0];

              //ps.println("ups, todo! :(");
            }
            if (clas.toLowerCase().contains("meta")) {
              lvl = lastlvl;
              plvl = lvl - 1;
              String rootid =
                  CoreUtils.removeTag("", content)
                      .replace("\r", "")
                      .replace("\n", "")
                      .trim()
                      .substring(5)
                      .trim();
              if (rootid.startsWith("\"") || rootid.startsWith("'")) rootid = rootid.substring(1);
              if (rootid.endsWith("\"") || rootid.endsWith("'"))
                rootid = rootid.substring(0, rootid.length() - 1);
              if (!rootid.isEmpty()) {
                TreeNode test =
                    rootNode
                        .getTreeDB()
                        .getNode(rootid, Path.QID | Path.UV | Path.UUID);
                if(test == null && !rootid.startsWith(Requirement.getTypeRootQId())){
                  if(!rootid.startsWith("/"))
                    rootid = "/"+rootid;
                  rootid = Requirement.getTypeRootQId()+rootid;
                  test =
                    rootNode
                        .getTreeDB()
                        .getNode(rootid, Path.QID | Path.UV | Path.UUID);
                }
                if(test == null)
                  test =
                    rootNode
                      .getTreeDB()
                      .getNode(rootid, Path.QID | Path.UV | Path.UUID | Path.CREATE);
                if (test != null) this.rootNode = test;
              }
              //this.rootNode = rootNode.getTreeDB().getRootNode();
              toadd = false;
            }
            if (clas.toLowerCase().contains("attribute")) {
              lvl = lastlvl;
              plvl = lvl - 1;
              NodeDesc req = locateLast(lvl, null, false);
              String key = null;
              StringBuilder value = new StringBuilder();
              String[] vals = content.split("\n"); //all lines
              for (String s : vals) {
                s = s.replaceAll("<[^>]*>", "");
                int psa = s.indexOf(":");
                if (psa > -1) {
                  if (key != null) key = key.trim();
                  if (key != null && !key.isEmpty()&& !key.startsWith("_")) {
                    if (keyRepl.containsKey(key.toLowerCase()))
                      key = getRepl(rootNode.getTreeDB(),key.toLowerCase());
                    addAttribute(req, key, value);
                  }
                  value.setLength(0);
                  int strt = 0;
                  if (s.startsWith("<br/>")) strt = 5;
                  key = s.substring(strt, psa);
                  while ((psa + 1) < s.length() && Character.isWhitespace(s.charAt(psa + 1))) psa++;
                  value.append(s.substring(psa + 1));
                  //req.setAttribute(new Attribute(req.getUUId(),AttributeType.STRING,key,value));
                } else {
                  value.append("\n");
                  value.append(s);
                }
              }
              if (key != null) key = key.trim();
              if (key != null && !key.isEmpty()) {
                if (keyRepl.containsKey(key.toLowerCase())) key = keyRepl.get(key.toLowerCase());
                addAttribute(req, key, value);
              }
              toadd = false;
              //Заглушко
            } else if (clas.toLowerCase().contains("reqheader")
                || clas.toLowerCase().contains("textnode")) {
              //headerinfo
              int rhpos = clas.toLowerCase().indexOf("reqheader");
              if (rhpos > -1) isHeader = true;
              if (rhpos > -1 && clas.substring(9 + rhpos).startsWith("leaf")
                  || clas.toLowerCase().contains("textnode")) {
                ignore = true;
                lvl = lastlvl;
                plvl = lvl; //?
                NodeDesc nd =
                    locateLast(
                        lastlvl, new String[] {Requirement.TYPE_NAME, TextNode.TYPE_NAME}, true);
                UUID par = null;
                if (nameParents.get(nd.getUUId()) != null) {
                  Iterator<UUID> it = nameParents.get(nd.getUUId()).iterator();
                  if (it.hasNext()) par = it.next();
                }
                if (par == null) par = nodeByLvl.get(0).get(0).getUUId();
                plvl = ndlvl.get(par);
                //if (nd != null) {
                lvl = ndlvl.get(nd.getUUId());
                toadd = addNewTNCheck(nd, content);
                addVid(nd, clas); //TODO test
                //}
                if (!toadd) {
                  content = "";
                  lastlvl = ndlvl.get(nd.getUUId());
                }
                //leaf case
              } else {
                lvl = 1;
                int spos = content.indexOf("/");
                while (spos > -1) {
                  spos = content.indexOf("/", spos + 1);
                  lvl++;
                }
                plvl = lvl - 1;
              }
            } else if (clas.toLowerCase().contains("requirement")) {
              lvl = lastlvl;
              //							ilvl = lvl;
              LinkedList<NodeDesc> stck = nodeByLvl.get(lvl);
              if (stck == null) stck = new LinkedList<>();
              ListIterator<NodeDesc> it = stck.listIterator(stck.size());
              toadd = false;
              if (it.hasPrevious()) {
                NodeDesc d = it.previous();
                if (d.getType().equals(Requirement.TYPE_NAME)) {
                  toadd = false;
                  AttributeDefinition desc = d.getAttributes().get(TreeNode.ATTR_DESCRIPTION);
                  String ds = "";
                  HtmlValueProvider old = null;
                  if (desc != null && desc.getRawValue().getValue() != null){
                    ds = desc.getRawValue().getValue().toString();
                    old = (HtmlValueProvider) desc.getValueProvider();
                  }
                  if (!content.equals("")) {
                    //if(!ds.isEmpty()&&(tag.equals("p")||tag.equals("div")))
                    //ds+="<br/>";
                    ds += content;
                    ds = ds.trim();
                    if (ds.startsWith("<br>")) ds = ds.substring(4);
                    ds = ds.trim();
                    content = "";
                    d.setAttribute(
                        new AttributeDefinition(d.getUUId(), TreeNode.ATTR_DESCRIPTION,
                            old!=null? old.createAppended(AttributeType.HTML, ds):
                              new HtmlValueProvider(AttributeType.HTML, ds,
                                TempFolderFilesProvider.getNewNotInited(AttributeType.HTML)),
                            Flag.HIDDEN
                        )
                    );
                  }
                }
                //if(content.equals(""))
                //break;
              }
              lastlvl = lvl;
            } else if (clas.toLowerCase().contains("textheader")) {
              lvl = 0;
              int spos = content.indexOf("/");
              while (spos > -1) {
                spos = content.indexOf("/", spos + 1);
                lvl++;
              }
              plvl = lvl - 1;
              isHeader = true;
              //ilvl=lvl;
            }
          }
          if (toadd
              && !(content
                  .replace("<br>", "")
                  .replace("\n", "")
                  .replace("</br>", "")
                  .trim()
                  .isEmpty())) {
            if (content.startsWith("<br>")) content = content.substring(4);
            NodeDesc lastStruct = null;
            //if(ilvl==0)
            //ilvl=1;
            int mod = 0;
            if (content.startsWith("[")) {
              int cl = content.indexOf("]");
              String ints = content.substring(2, cl);
              mod = Integer.parseInt(ints);
              if (mod != 0) content = content.substring(cl + 1);
            }

            UUID uid = UUID.randomUUID();
            String newID = "", newDesc = "", newType = "TextNode", tntype = null;
            newType =
                hasKnownClass(clas) && clas.toLowerCase().contains("todo")
                    ? Comment.TYPE_NAME
                    : ((!hasKnownClass(clas)
                                && tag.toLowerCase().startsWith("h")) //header without class
                            || hasKnownClass(clas)
                                && (clas.toLowerCase().contains("requirement")
                                    || clas.toLowerCase().contains("reqheader")))
                        ? Requirement.TYPE_NAME
                        : TextNode.TYPE_NAME;

            tntype =
                !newType.equals(TextNode.TYPE_NAME)
                    ? null
                    : (hasKnownClass(clas) && clas.toLowerCase().contains("textheader"))
                        ? "header"
                        : "text";
            newDesc = newType.equals("Requirement") ? "" : "header".equals(tntype) ? "" : content;
            newID =
                (newType.equals("Requirement") || "header".equals(tntype))
                    ? CoreUtils.htmlClear(content.replace("\n", " "))
                    : null;
            {
              Set<String> ids = new HashSet<>();
              //ps.println(lvl+" "+lastlvl+" "+newType+" "+content);
              if (newID != null && !newID.isEmpty()) createNameParents(newID);

              Object[] locate =
                  locateStruct(
                      plvl,
                      newType /*tag.toLowerCase().startsWith("h")?Requirement.TYPE_NAME:TextNode.TYPE_NAME*/,
                      tntype /*"text"*/);
              lastStruct = (NodeDesc) locate[1];
              lvl = (Integer) locate[0]; //ilvl=lvl;
              if (lastStruct == null || lvl <= 0) {
                if (lvl == -1) {
                  lvl = (Integer) locate[0]; //ilvl=lvl+1;
                }
                if (lvl < 0) lvl = 0;
              }

              if (lastStruct != null) {
                if (nameChildren.containsKey(lastStruct.getUUId()))
                  for (UUID cuid : nameChildren.get(lastStruct.getUUId())) {
                    NodeDesc nd = ndmap.get(cuid);
                    if (nd != null) {
                      ids.add(nd.getId());
                      String name = getName(nd);
                      if (name != null) ids.add(name);
                    }
                  }
              }
              Map<String, AttributeDefinition> attrs = new HashMap<>();
              //String name = null;
              //if(newType.equals(Requirement.TYPE_NAME)||tntype.equals("header")){
              String nID = null;
              NodeDesc parent = lastStruct;
              //								if(newID!=null)
              //								newID = reworkId(newID);
              newID = reworkId(newID);
              boolean br = false;
              if (newID != null && !newID.isEmpty() && parent != null) {
                NodeDesc par = parent; //?
                try {
                  if (newID.startsWith("/")) newID = newID.substring(1);
                  if (newID.contains("/")) {
                    //ps.println("locate "+newID);
                    try {
                      par =
                          lr.getNodesByUFName(
                                  nodeByLvl.get(0).get(0),
                                  newID.substring(0, newID.lastIndexOf("/")),
                                  null,
                                  ndmap,
                                  nameChildren)[0];
                      lvl = ndlvl.get(par.getUUId()) + 1;
                    } catch (Exception r) {
                      r.printStackTrace();
                    }
                  }
                } catch (Exception e) {
                  e.printStackTrace();
                }

                String sid = getLastIdPart(newID);
                NodeDesc nd = findChild(par, sid);
                if (nd != null) {
                  if (isAuto(nd)) {
                    lastStruct = nd;
                    lastlvl = lvl;
                    nd.removeAttribute("_auto");
                    nID = sid;
                    uid = nd.getUUId();
                    br = true;
                  }
                  if (!br) {
                    int db = 2;
                    while (findChild(par, sid + "_" + db) != null) db++;
                    if (!attrs.containsKey(TreeNode.ATTR_NAME)) {
                      attrs.put(
                          TreeNode.ATTR_NAME,
                          new AttributeDefinition(uid, TreeNode.ATTR_NAME,
                              AttributeDefinition.detectValueProvider(TreeNode.ATTR_NAME,
                                  AttributeType.STRING, sid, TempFolderFilesProvider.EMPTY)
                          ,Flag.HIDDEN)
                      );
                      attrs.put(
                          "ignoreName",
                          new AttributeDefinition(uid, "ignoreName", 
                              new ConstValueProvider(AttributeType.STRING, "true")
                          ,0)
                      );
                    }
                    newID += "_" + db;
                  }
                }
                if (!br) {
                  //name = processNewID(uid, newID);
                  nID = processNewID(uid, newID);
                  if (nID.equals("")) ps.println("null id");
                  //nID =  reworkId(processNewID(uid, newID));
                  parent = ndmap.get(nameParents.get(uid).get(0));
                }
              } else if (lastStruct != null) {
                //int cnt = 0;
                //if(nameChildren.get(lastStruct.getUUId())!=null)
                //cnt = nameChildren.get(lastStruct.getUUId()).size();
                //									nID = "|:"+cnt+":|";//new mode
                nID = RequalityCLI.getNextId(ids);
                processNewID(uid, getUVID(lastStruct) + "/" + nID);
              }
              if (!br) {
                if (lastStruct != null && nID == null) nID = RequalityCLI.getNextId(ids);
                if (nID == null) {
                  nID = "001";
                  locateStruct(
                      lvl,
                      newType /*tag.toLowerCase().startsWith("h")?Requirement.TYPE_NAME:TextNode.TYPE_NAME*/,
                      tntype /*"text"*/);
                }
                //newID=null;
                if (!newDesc.isEmpty()) {
                  attrs.put(
                      TreeNode.ATTR_DESCRIPTION,
                      new AttributeDefinition(uid, TreeNode.ATTR_DESCRIPTION, 
                          new HtmlValueProvider(AttributeType.HTML, newDesc,
                            TempFolderFilesProvider.getNewNotInited(AttributeType.HTML)),
                          Flag.HIDDEN
                      )
                  );
                }
                if (tntype != null) {
                  attrs.put(
                    TextNode.ATTR_TNTYPE,
                    new AttributeDefinition(uid, TextNode.ATTR_TNTYPE, 
                      new ConstValueProvider(AttributeType.STRING, tntype)
                      ,Flag.HIDDEN)
                  );
                }
                attrs.put(
                  TreeNode.ATTR_TYPE,
                  new AttributeDefinition(uid, TreeNode.ATTR_TYPE,
                    new ConstValueProvider(AttributeType.STRING, newType)
                    ,Flag.HIDDEN)
                );
                if (newType.equals(Comment.TYPE_NAME) && author != null) {
                  attrs.put(
                      Comment.ATTR_AUTHOR,
                      new AttributeDefinition(uid, Comment.ATTR_AUTHOR, new 
                          ConstValueProvider(AttributeType.STRING, author)
                          ,Flag.HIDDEN
                      )
                      
                  );
//                      new Attribute(
//                          Version.getUnset(),
//                          uid,
//                          AttributeType.STRING,
//                          Comment.ATTR_AUTHOR,
//                          author));
                }
                //list.add(new Attribute(Version.getUnset(),uid,AttributeType.STRING,TreeDB.INDEX_ATTRIBUTE,Long.toString(gpos)));
                //attrs.put(TreeDB.INDEX_ATTRIBUTE, list);
                //now he have structural node to add to
                if (parent != null)
                  parent.setAttribute(
                    new AttributeDefinition(parent.getUUId(), TreeNode.ATTR_TYPE, 
                        new ConstValueProvider(AttributeType.STRING, Requirement.TYPE_NAME)
                        ,Flag.HIDDEN
                    )
                  );
                boolean valid = false;
                try {
                  valid = TreeLogic.isIdValidExc(nID);
                } catch (IdEmpty e) {
                  // TODO Auto-generated catch block
                  e.printStackTrace();
                } catch (IdContainsWrongChars e) {
                  // TODO Auto-generated catch block
                  e.printStackTrace();
                }
                if (!valid) {
                  if (nID.isEmpty())
                    nID =
                        RequalityCLI.getNextId(
                            parent, attrs.get(TreeNode.ATTR_TYPE).getRawValue().getValue().toString(), "");
                  StringBuilder sb = new StringBuilder();
                  for (int i = 0; i < nID.length(); i++) {
                    char c = nID.charAt(i);
                    if (Character.isAlphabetic(c) || c == '_' || c == '-' || Character.isDigit(c))
                      sb.append(c);
                    if (Character.isWhitespace(c))
                      sb.append(" ");
                  }
                  if (!attrs.containsKey(TreeNode.ATTR_NAME)) {
                    attrs.put(
                        TreeNode.ATTR_NAME,
                        new AttributeDefinition(uid, TreeNode.ATTR_NAME, 
                            AttributeDefinition.detectValueProvider(TreeNode.ATTR_NAME,
                                AttributeType.STRING, nID, TempFolderFilesProvider.EMPTY),
                            Flag.HIDDEN
                        )
                    );
                    attrs.put(
                        "ignoreName", new AttributeDefinition(uid, "ignoreName", 
                            new ConstValueProvider(AttributeType.STRING, "true"),
                            0
                        )
                    );
                  }
                  nID = sb.toString();
                }
                NodeDesc nd = findChild(parent, nID);
                if (nd != null) {
                  if (!attrs.containsKey(TreeNode.ATTR_NAME)) {
                    attrs.put(
                        TreeNode.ATTR_NAME,
                        new AttributeDefinition(uid, TreeNode.ATTR_NAME, 
                            AttributeDefinition.detectValueProvider(TreeNode.ATTR_NAME,
                                AttributeType.STRING, nID, TempFolderFilesProvider.EMPTY),
                            Flag.HIDDEN)
                    );
                    //    new Attribute(Version.getUnset(), uid, TreeNode.ATTR_NAME, nID));
                    attrs.put(
                        "ignoreName", new AttributeDefinition(uid, "ignoreName", new ConstValueProvider(AttributeType.STRING,"true"),
                            0)
                    );
                  }
                  int i = 0;
                  while (findChild(parent, nID + i++) == null) ;
                  nID = nID + i;
                }
              }
              nodeMarks.put(uid, guesses);
              NodeDesc newOne;
              if (br) newOne = findChild(parent, nID);
              else newOne = newNodeDesc(uid, parent, nID, attrs, new LinkedList<UUID>());
              addVid(newOne, clas);
              //structural place for node, leaf nodes will be placed by names
              //addStructuralChild(lastStruct==null?null:lastStruct.getUUId(),uid);

              //setting root

              for (int i = lvl + 1; i <= lastlvl; i++)
                if (nodeByLvl.containsKey(i)) nodeByLvl.get(i).clear();
              lastlvl = lvl;
              //ps.println("new "+newType+" "+content+" "+getQID(newOne)+" "+getUVID(newOne));
            }
          }
          if (lvl < 0) lvl = 0;
        }
      } else if (cssstarted && localname.equals("span")) cssstarted = false;
    }
    if (event.isCharacters()) {
      Characters ch = event.asCharacters();
      CharArrayWriter caw = new CharArrayWriter();
      try {
        ch.writeAsEncodedUnicode(caw);
      } catch (XMLStreamException e) {
        e.printStackTrace();
      }
      caw.close();
      String val = caw.toString();
      if (lasttext == null) lasttext = val;
      else lasttext = lasttext + val;
      if (!val.isEmpty()) {
        if (bodystarted && !interest.isEmpty()) {
          int k = tempString.length();
          String s = val;
          if (!opennedTags.contains("pre")) s = replaceN(s, " ");
          for (int i = 0; i < styles.length; i++) {
            boolean stb = styles[i], sto = hasActiveSimpleStyle(i);
            if (stb && !sto) {
              String st = getSimpleStyle(i);
              s = "<" + st + ">" + s + "</" + st + ">";
            }
            if (!stb && sto) {
              String st = getClosestStyle(i);
              if (st != "") s = "</" + st + ">" + s + "<" + st + ">";
            }
          }
          tempString.append(s);
        } else if (cssstarted) csstext.append(val);
        else if (opennedTags.size() > 1) {
          String s = val;
          if (!opennedTags.contains("pre")) s = replaceN(s, " ");
          s = s.replace("  ", " ");
          tempString.append(s);
        }
      }
    }
    //		}
    //		catch(Exception e){
    //			e.printStackTrace();
    //		}
    //add sorting indexes
  }

  private String getRepl(TreeDB db, String key) {
    String rc = keyRepl.get(key);
    if(key.equals("fid"))
      return FIDUpdater.getFIDName(db);
    if(rc!=null)
      return rc;
    return null;
  }

  //TODO mb add replace of all &; to utf chars
  private String specReplace(String string) {
    int ind = string.indexOf("&");
    while (ind > -1) {
      int pos = ind + 1;
      int end = -1;
      while (pos < string.length() && end == -1) {
        char c = string.charAt(pos);
        if (!Character.isLetterOrDigit(c))
          if (c == ';') end = pos;
          else end = -2;
        pos++;
      }
      if (end > -1) {
        String substr = string.substring(ind + 1, end);
        String repl = HTMLSpecChars.getCode(substr);
        if (repl != null) {
          repl = Character.toString((char)(Integer.parseInt(repl)));
          string = string.substring(0, ind) + repl + string.substring(end+1);
          ind += repl.length() - substr.length()-1;
          ;
        }
      }
      ind = string.indexOf("&", ind + 1);
    }
    return string;
  }

  private boolean isAuto(NodeDesc nd) {
    // TODO Auto-generated method stub
    return nd.getAttributes().containsKey("_auto")
        && nd.getAttributes().get("_auto").getRawValue().getValue().toString().equals("true");
  }

  private Map<String, String> parseCSS(String css) {
    Map<String, String> vals = new HashMap<>();
    if (css != null)
      for (String p : css.split(";")) {
        int sc = p.indexOf(":");
        if (sc > -1) {
          String key = p.substring(0, sc).trim(), val = p.substring(sc + 1).trim();
          vals.put(key, val);
        }
      }
    return vals;
  }

  private NodeDesc findChild(NodeDesc parent, String nID) {
    if (nID == null || nID.isEmpty()) return null;
    if (nameChildren.get(parent.getUUId()) != null)
      for (UUID uid : nameChildren.get(parent.getUUId())) {
        NodeDesc nd = ndmap.get(uid);
        if (nd == null) continue;
        if (nd.getId().toLowerCase().equals(nID.toLowerCase())
            || getName(nd) != null && getName(nd).equals(nID)) return nd;
      }
    return null;
  }

  private boolean lookLeft(Stack<String> opennedTags2) {
    Stack<String> s2 = new Stack<>();
    s2.addAll(opennedTags2);
    while (!s2.isEmpty()) {
      String tag = s2.pop();
      if (tag.equals("ol") || tag.equals("ul")) return false;
      if (tag.equals("li")) return true;
    }
    return true;
  }

  private void addVid(final NodeDesc newOne, String clas) {
    if (clas == null) return;
    int tpos = clas.indexOf("textnode");
    if (tpos > -1) {
      if (clas.length() > 8 && clas.charAt(tpos + 8) == '-') {
        String vid = null; //"Описание";
        if (clas.substring(tpos + 9).startsWith("def")) vid = "Описание";
        if (clas.substring(tpos + 9).startsWith("just")) vid = "Обоснование";
        if (clas.substring(tpos + 9).startsWith("note")) vid = "Примечание";
        if (vid != null) {
          final String fvid = vid;
          newOne
              .getAttributes()
              .put(
                  "Вид",
                  new AttributeDefinition(newOne.getUUId(), "Вид",
                      new ConstValueProvider(AttributeType.ENUM, 
                          new AppliedEnumValue(fvid, null, AttributeType.STRING, "Вид")
                      ),
                      0
                  )
          );
        }
      }
    }
  }

  private boolean hasKnownClass(String clas) {
    if (clas == null) return false;
    for (String s : knownClasses) if (clas.toLowerCase().contains(s)) return true;
    return false;
  }

  private void addAttribute(NodeDesc req, String key, StringBuilder value) {
    if (key.length() == 0) {
      ps.println("Empty key!");
      return;
    }
    char f = key.charAt(0);
    String val = value.toString().trim();
    if (val.toLowerCase().startsWith("missing!: ")) val = val.substring(10);
    if (req.getAttributes().containsKey(key) && !forceNotList.contains(key)) {
      AttributeDefinition a = req.getAttributes().get(key);
      List<Object> list = new ArrayList<>();
      TypedValue tv = a.getRawValue();
      if (tv.getType() != AttributeType.LIST) list.add(tv.getValue().toString());//TODO cannot be ref vals here?
      else list.addAll(((ListValue)tv.getValue()).getValues());//TODO tends to bug on lvl2  
      list.add(val);
      ListValue lv = new ListValue(AttributeType.STRING, list);
      req.setAttribute(
          new AttributeDefinition(req.getUUId(), key, AttributeDefinition.detectValueProvider(key,AttributeType.LIST,lv, TempFolderFilesProvider.EMPTY)));
    } else
      req.setAttribute(
          new AttributeDefinition(
            req.getUUId(),key,
            AttributeDefinition.detectValueProvider(key, AttributeType.STRING, value.toString().trim(), TempFolderFilesProvider.EMPTY)
          )
      );
    if (key.equals(TreeNode.ATTR_NAME)) {
      //req.removeAttribute("ignoreName");
      //Force it not to be ignored
      req.setAttribute(
          new AttributeDefinition(req.getUUId(), "ignoreName", 
              new ConstValueProvider(AttributeType.STRING, "false"),
              0
          )
      );
    }
  }

  //	private boolean checkNameChilds(NodeDesc par, String sid) {
  //		if(par == null)
  //			return true;
  //		if(nameChildren.containsKey(par.getUUId()))
  //			for(UUID u2id:nameChildren.get(par.getUUId()))
  //				if(ndmap.get(u2id).getId().toLowerCase().equals(sid.toLowerCase())){//problem;
  //					//dupe of ids
  //					return true;
  //				}
  //		return false;
  //	}
  private static Set<String> standartAttrs =
      new HashSet<String>() {
        /** */
        private static final long serialVersionUID = -6843820394905800L;

        {
          add("ignoreHeader");
          add("ignoreName");
          add("Вид");
          add(TreeNode.ATTR_DESCRIPTION);
          add(TreeNode.ATTR_TYPE);
          add(TreeDB.INDEX_ATTRIBUTE);
          add(TextNode.ATTR_TNTYPE);
        }
      };

  private boolean addNewTNCheck(NodeDesc nd, String content) {
    //ps.println((char)0x00A6);
    if (nd != null) {
      for (String s : nd.getAttributes().keySet())
        if (!standartAttrs.contains(s) && !s.startsWith("_")) //then this is ended
        return true;
      if (!nameChildren.containsKey(nd.getUUId()) || nameChildren.get(nd.getUUId()).isEmpty()) {
        AttributeDefinition desc = nd.getAttributes().get(TreeNode.ATTR_DESCRIPTION);
        TypedValue rw = null;
        HtmlValueProvider old = null;
        if(desc!=null){
          rw = desc.getValueProvider().getRawValue();
          old = (HtmlValueProvider) desc.getValueProvider();
        }
        String ds = "";
        if (rw != null
            && rw.getValue() != null
            && !rw.getValue().toString().replace("\n", "").trim().isEmpty()) {
          if (!nd.getType().equals(TextNode.TYPE_NAME)) return true;
          ds += rw.getValue().toString();
        } else {
          //nd.setAttribute(new Attribute(Version.getUnset(),nd.getUUId(),AttributeType.BOOL,"ignoreHeader",true));
          nd.setAttribute(
              new AttributeDefinition(nd.getUUId(), TreeNode.ATTR_TYPE, AttributeType.STRING, TextNode.TYPE_NAME));
        }
        ds = ds + content;
        if (ds.startsWith("<br>")) ds = ds.substring(4);
        nd.setAttribute(
            new AttributeDefinition(nd.getUUId(), TreeNode.ATTR_DESCRIPTION,
                old!=null?old.createAppended(AttributeType.HTML, ds)
                    :
                      new HtmlValueProvider(AttributeType.HTML,ds,
                        TempFolderFilesProvider.getNewNotInited(AttributeType.HTML)),
                Flag.HIDDEN));
        content = "";
        return false;
      }
    }
    return true;
  }

  private boolean addNewTODOCheck(NodeDesc nd, String content) {
    //ps.println((char)0x00A6);
    if (nd != null) {
      for (String s : nd.getAttributes().keySet())
        if (!standartAttrs.contains(s) && !s.startsWith("_")) //then this is ended
        return true;
      if (!nameChildren.containsKey(nd.getUUId()) || nameChildren.get(nd.getUUId()).isEmpty()) {
        AttributeDefinition desc = nd.getAttributes().get(TreeNode.ATTR_DESCRIPTION);
        String ds = "";
        HtmlValueProvider old = null;
        if(desc!=null)
          old = (HtmlValueProvider) desc.getValueProvider();
        if (desc != null
            && desc.getRawValue() != null
            && !desc.getRawValue().toString().isEmpty()) {
          if (!nd.getType().equals(Comment.TYPE_NAME)) return true;
          ds += desc.getRawValue().toString();
        } else {
          nd.setAttribute(
              new AttributeDefinition(nd.getUUId(), "ignoreHeader", AttributeType.BOOL,true));
          nd.setAttribute(
              new AttributeDefinition(nd.getUUId(), TreeNode.ATTR_TYPE, AttributeType.STRING, Comment.TYPE_NAME));
        }
        nd.setAttribute(new AttributeDefinition(nd.getUUId(), TreeNode.ATTR_DESCRIPTION,
            old!=null?old.createAppended(AttributeType.HTML, ds+content):
            new HtmlValueProvider(AttributeType.HTML, ds+content,
              TempFolderFilesProvider.getNewNotInited(AttributeType.HTML)),
            Flag.HIDDEN));
        content = "";
        return false;
      }
    }
    return true;
  }

  private boolean isInteresting(String clas) {
    if (clas == null) return false;
    for (String cl : classes.keySet()) if (clas.toLowerCase().contains(cl)) return true;
    return false;
    //return clas!=null && !clas.toLowerCase().contains("reqheader")&&!clas.contains("requirement")&&!clas.toLowerCase().contains("textheader")&&!clas.contains("textnode")
    //	&&!clas.toLowerCase().contains("reqHeaderleaf")&&!clas.toLowerCase().contains("attribute")&&!clas.toLowerCase().contains("todo");
  }

  private void writeStylesFor(String name, StringBuffer sb, String before) {
    int ind = csstext.indexOf(name);
    while (ind > -1) {
      String last = csstext.substring(Math.max(0, ind - 60), ind);
      if (before == null
          || last.endsWith(before)
          || !Character.isJavaIdentifierPart(last.charAt(last.length() - 1))) {
        if (!Character.isJavaIdentifierPart(csstext.substring(ind + name.length()).charAt(0))) {
          int obr = csstext.indexOf("{", ind), cbr = csstext.indexOf("}", ind);
          if (cbr > obr) {
            //proper brackets
            sb.append(csstext.substring(obr + 1, cbr));
            sb.append(";");
          }
        }
      }
      ind = csstext.indexOf(name, ind + 1);
    }
  }

  private String locateStyles(String localname, String value) {
    StringBuffer ret = new StringBuffer();
    ret.append(";");
    writeStylesFor(localname + " ", ret, null);
    writeStylesFor(localname + ",", ret, null);
    if (value != null) {
      String[] classes = (value).split("\\s");
      for (String cl : classes)
        if (!cl.isEmpty() && !isInteresting(cl)) writeStylesFor("." + cl, ret, localname);
    }
    ret.append(";");
    int pe = ret.indexOf(";");
    StringBuffer sbuf = new StringBuffer();
    if (pe > -1) {
      int ne = ret.indexOf(";", pe + 1);
      while (ne > -1) {
        String cont = ret.substring(pe + 1, ne);
        if (!cont.isEmpty()
            && !cont.contains("margin")
            && !cont.contains("padding")
            && !cont.contains("writing-mode")
            && !cont.contains("color")
            && !cont.contains("width")
            && !cont.contains("height")
            && !cont.contains("clear")
            && !cont.contains("font-family")) {
          sbuf.append(cont);
          sbuf.append(";");
        }
        pe = ne;
        ne = ret.indexOf(";", pe + 1);
      }
    }
    int i = sbuf.length() - 1;
    while (i > 0 && sbuf.charAt(i) == ';') i--;
    int j = 0;
    while (j < i && j < (sbuf.length() - 1) && sbuf.charAt(j) == ';') j++;
    return sbuf.substring(j, i + 1).toString().replace("\n", "").trim();
  }

  private void createNameParents(String newID) {
    final NodeDesc root = nodeByLvl.get(0).get(0);
    StringTokenizer st = new StringTokenizer(newID, "/");
    if (!st.hasMoreTokens()) return;
    String tk = st.nextToken();
    tk = reworkId(tk);
    if (!st.hasMoreTokens()) return;
    NodeDesc[] fst = lr.getNodesByUFName(root, tk, null, ndmap, nameChildren), nextit;
    if (fst.length == 0) {
      final UUID newuid = UUID.randomUUID();
      Map<String, AttributeDefinition> attrs = AttributeStorage.formTypeMap(newuid, Requirement.TYPE_NAME);
      attrs.put("_auto", new AttributeDefinition(newuid, "_auto", AttributeType.BOOL, true));
      NodeDesc nd = newNodeDesc(newuid, root, tk, attrs, new LinkedList<UUID>());
      fst = new NodeDesc[] {nd};
    }
    nextit = new NodeDesc[fst.length];
    tk = st.nextToken();
    if (!st.hasMoreTokens()) return;
    NodeDesc lastexists = fst[0];
    while (st.hasMoreTokens()) {
      nextit = new NodeDesc[fst.length];
      int j = 0;
      for (int i = 0; i < fst.length; i++) {
        if (fst[i] == null) continue;
        NodeDesc nd = fst[i];
        fst[i] = null;
        if (nameChildren.containsKey(nd.getUUId())) {
          List<UUID> childs = nameChildren.get(nd.getUUId());
          for (UUID uid : childs) {
            NodeDesc chi = ndmap.get(uid);
            if (chi.getId().equals(tk)||getName(chi)!=null &&getName(chi).equals(tk)) { //todo name?
              lastexists = chi; //can be more but tends to be correct
              nextit[j++] = chi;
              break;
            }
          }
        }
      }
      NodeDesc[] temp = fst;
      fst = nextit;
      nextit = temp;
      if (j == 0) { //has no new
        while (st.hasMoreTokens()) { //create all parents
          final NodeDesc par = lastexists;
          final UUID newuid = UUID.randomUUID();
          tk = reworkId(tk);
          Map<String, AttributeDefinition> attrs =
              AttributeStorage.formTypeMap(newuid, Requirement.TYPE_NAME);
          attrs.put("_auto", new AttributeDefinition(newuid, "_auto", AttributeType.BOOL, true));
          NodeDesc nd = newNodeDesc(newuid, par, tk, attrs, new LinkedList<UUID>());
          lastexists = nd;
          tk = reworkId(st.nextToken());
        }
      }
      if (st.hasMoreTokens()) tk = reworkId(st.nextToken());
    }
  }

  private String reworkId(String tk) {
    boolean isValid = false;
    if (Attribute.checkNameIsValid(tk, true)) isValid = true;
    tk = tk.replace("&quot;", "\"");
    if (!isValid) {
      StringBuffer sb = new StringBuffer();
      for (int i = 0; i < tk.length(); i++) {
        char c = tk.charAt(i);
        if (Character.isAlphabetic(c)
            || c == '_'
            || c == '-'
            || Character.isWhitespace(c)
            || c == '\\'
            || c == '/'
            || Character.isDigit(c)
            || c == '"'
            || c == '\''
            || c == '.') sb.append(c);
      }
      tk = sb.toString();
    }
    return tk;
  }

  private NodeDesc locateLast(int lvl, String[] tps, boolean single) {
    Set<String> flt = null;
    if (tps != null) flt = new HashSet<>(Arrays.asList(tps));
    boolean stop = false;
    while (lvl > -1 && !stop) {
      if (nodeByLvl.containsKey(lvl) && nodeByLvl.get(lvl) != null) {
        LinkedList<NodeDesc> nodes = nodeByLvl.get(lvl);
        if (nodes != null) {
          ListIterator<NodeDesc> it = nodes.listIterator(nodes.size());
          //while(it.hasPrevious()&&!stop)
          if (it.hasPrevious()) //seems to be more correct
          {
            NodeDesc nd = it.previous();
            if (nd != null) {
              if (flt == null || flt.contains(nd.getType())) {
                return nd;
              }
            }
          }
          if (single) ps.println("Parent problem on " + tps);
          //stop = true;
        }
      }
      lvl--;
    }
    return null;
  }

  private Object[] locateStruct(int lvl, String type, String tntp) {
    boolean stop = false;
    NodeDesc str = null;
    while (lvl > -1 && !stop) {
      if (nodeByLvl.containsKey(lvl) && nodeByLvl.get(lvl) != null) {
        LinkedList<NodeDesc> nodes = nodeByLvl.get(lvl);
        if (nodes != null) {
          ListIterator<NodeDesc> it = nodes.listIterator(nodes.size());
          //while(it.hasPrevious()&&!stop)
          if (it.hasPrevious()) //seems to be more correct
          {
            NodeDesc nd = it.previous();
            if (nd != null) {
              if (nd.getType().equals(Requirement.TYPE_NAME)
                  || //may be~=true
                  nd.getType().equals(TextNode.TYPE_NAME)
                  || nd.getType().equals(Comment.TYPE_NAME)) {
                str = nd;

                Map<String, AttributeDefinition> attr = nd.getAttributes();
                if (nd.getType().equals(Comment.TYPE_NAME)
                    || nd.getType().equals(TextNode.TYPE_NAME)
                        && (attr.get(TextNode.ATTR_TNTYPE) == null
                            || !attr.get(TextNode.ATTR_TNTYPE)
                                .getRawValue().getValue()
                                .toString()
                                .equals("header"))) {
                  str = ndmap.get(nameParents.get(nd.getUUId()).get(0));
                  lvl--;
                }
                stop = true;
                break;
              }
            }
          }
        }
      }
      lvl--;
    }

    return new Object[] {lvl + 1, str};
  }

  private void toString(EndElement ee, StringBuffer sb2) {
    sb2.append("</");
    sb2.append(ee.getName().getLocalPart());
    sb2.append(">");
  }

  private boolean toString(StartElement se, StringBuffer sb2) {
    //TODO possible source of strange handling
    String localname = se.getName().getLocalPart();
    String styling = null;

    Iterator<?> ia = se.getAttributes();
    String cl = null;
    boolean notEmpty = false;
    while (ia.hasNext()) {
      javax.xml.stream.events.Attribute a = (javax.xml.stream.events.Attribute) ia.next();
      if (a != null) {
        if (a.getName().getLocalPart().equals("class")) { //ignore{
          cl = a.getValue();
          continue;
        }
        if (a.getName().getLocalPart().equals("face")) {
          continue;
        }
        if (a.getName().getLocalPart().equals("style")) {
          continue;
        }
        if (a.getName().getLocalPart().equals("lang")) {
          continue;
        }
        notEmpty = true;
        break;
      }
    }
    ia = se.getAttributes();
    styling = locateStyles(localname, cl);
    //		else
    //		styling += " "+locateStyles(localname,cl);
    if (!localname.equals("div") && !localname.equals("span") && !localname.equals("font")
        || notEmpty
        || styling != null && !styling.isEmpty()) {
      sb2.append("<");
      sb2.append(localname);
      notEmpty = true;
      while (ia.hasNext()) {
        javax.xml.stream.events.Attribute a = (javax.xml.stream.events.Attribute) ia.next();
        if (a != null) {
          if (a.getName().getLocalPart().equals("class")) { //ignore{
            cl = a.getValue();
            continue;
          }
          if (a.getName().getLocalPart().equals("face")
              || a.getName().getLocalPart().equals("lang")) {
            continue;
          }
          if (a.getName().getLocalPart().equals("style")) {
            if (styling == null) styling = a.getValue();
            else styling += " " + a.getValue();
            continue;
          }
          notEmpty = true;
          sb2.append(" ");
          sb2.append(a.getName().getLocalPart());
          sb2.append("='");
          sb2.append(a.getValue());
          sb2.append("'");
        }
      }
    }
    if (styling != null && !styling.isEmpty()) {
      tempString.append(" style='");
      tempString.append(styling);
      tempString.append("'");
      notEmpty = true;
    }
    if (notEmpty) sb2.append(">");
    return !notEmpty;
  }

  private String getLastIdPart(String newID) {
    if (newID.contains("/")) return newID.substring(newID.lastIndexOf("/") + 1);
    else return newID;
  }

  private String processNewID(UUID uid2, String newID) {
    if (newID.contains("/")) {
      String path = newID.substring(0, newID.lastIndexOf("/"));
      List<UUID> pars = new LinkedList<>();
      NodeDesc[] nds =
          lr.getNodesByUFName(nodeByLvl.get(0).get(0), path, null, ndmap, nameChildren);
      for (NodeDesc nd : nds) pars.add(nd.getUUId());
      addNameChild(pars, uid2, path);
      return newID.substring(newID.lastIndexOf("/") + 1);
    } else {
      UUID rt = null;
      if (nodeByLvl.containsKey(0)
          && !nodeByLvl.get(0).isEmpty()
          && nodeByLvl.get(0).get(0) != null) rt = nodeByLvl.get(0).get(0).getUUId();
      //root root, maybe add to requirements?
      final UUID root = rt;
      //suggests that is on root
      addNameChild(
          new LinkedList<UUID>() {
            /** */
            private static final long serialVersionUID = -8792459920284678425L;

            {
              add(root);
            }
          },
          uid2,
          "");
    }
    return newID;
  }

  private String getName(NodeDesc nd) {
    if (nd != null) {
      //			List<Attribute> la = nd.getAttributes().get(TreeNode.ATTR_NAME);
      //			if(la!=null&&!la.isEmpty()){
      AttributeDefinition a = nd.getAttributes().get(TreeNode.ATTR_NAME);
      return a == null || a.getRawValue()==null || a.getRawValue().getValue() == null ? null : a.getRawValue().getValue().toString();
      //}
    }
    return null;
  }

  private String getNameOrId(NodeDesc nd) {
    String name = getName(nd);
    if (name != null) return name;
    return nd.getId();
  }
  //	private void recWriteStruct(NodeDesc c, int lvl){
  //		if(c == null)
  //			return;
  //		for(int i = 0;i<lvl;i++)
  //			ps.println("\t");
  //
  //		ps.println("-'"+getNameOrId(c)+"{"+c.getId()+"}' ("+c.getType()+")"+"["+c.getUUId()+"]");
  //		ps.println();
  //		if(structuralChildren.containsKey(c.getUUId()))
  //			for(UUID uid:structuralChildren.get(c.getUUId()))
  //				recWriteStruct(ndmap.get(uid), lvl+1);
  //	}
  private void recWriteNames(NodeDesc c, int lvl) {
    if (c == null) return;
    for (int i = 0; i < lvl; i++) ps.println("\t");

    ps.println(
        "-'"
            + getNameOrId(c)
            + "{"
            + c.getId()
            + "}' ("
            + c.getType()
            + ")"
            + "["
            + c.getUUId()
            + "]");
    if (nameParents.containsKey(c.getUUId())) {
      if (nameParents.get(c.getUUId()).size() > 1) ps.println("*");
      else if (!nameParents.containsKey(c.getUUId())
          || nameParents.get(c.getUUId()).isEmpty()
          || structuralParent.get(c.getUUId()) == null
          || !nameParents.get(c.getUUId()).get(0).equals(structuralParent.get(c.getUUId())))
        ps.println("@");
    }
    ps.println("");
    if (nameChildren.containsKey(c.getUUId()))
      for (UUID uid : nameChildren.get(c.getUUId())) recWriteNames(ndmap.get(uid), lvl + 1);
  }

  private class NodeAssumption {
    public NodeAssumption(TreeNode tnode, NodeDesc enode) {
      this.tnode = tnode;
      this.enode = enode;
    }

    public TreeNode tnode;
    public NodeDesc enode;
  }

  @Override
  protected void stopProcess() {
    //TODO links fixer
    //show hierarchy
    while (!writeStack.isEmpty()) {
      XMLEvent xe = writeStack.removeFirst();
      handleEvent(db.getRootNode(), xe, ps);
    }
    NodeDesc[] roots = new NodeDesc[] {nodeByLvl.get(0).get(0)};
    if (rootNode == null || !rootNode.getQualifiedId().startsWith(Requirement.getTypeRootQId()))
      rootNode = root.getTreeDB().getNode(Requirement.getTypeRootQId());
    if (rootNode != null) {
      NodeDesc rt =
          lr.findNodeDesc(
              nodeByLvl.get(0).get(0), rootNode.getFullName(true).split("/"), ndmap, nameChildren);
      if (rt != null) roots = new NodeDesc[] {rt};
      else {
        rt =
            lr.findNodeDesc(
                nodeByLvl.get(0).get(0), new String[] {rootNode.getId()}, ndmap, nameChildren);
        if (rt != null) roots = new NodeDesc[] {rt};
      }
    }
    if (rootNode == null) rootNode = root.getTreeDB().getNode(Requirement.getTypeRootQId());
    //recWriteNames(root,0);
    //recWriteStruct(root,0);
    //		if(roots[0].getId().equals("")){
    //			UUID[]uids = nameChildren.get(roots[0].getUUId()).toArray(new UUID[0]);
    //			roots = new NodeDesc[uids.length];
    //			int i = 0;
    //			for(UUID uid:uids)
    //			{
    //				if(!ndmap.get(uid).getId().isEmpty())
    //				//rootsPath[i] = getUVID(roots);
    //					roots[i++]=ndmap.get(uid);
    //			}
    //			if(i<roots.length)
    //				roots = Arrays.copyOf(roots, i);
    //		}
    NodeAssumption rootNodes[] = new NodeAssumption[roots.length];
    if (roots.length == 1 && rootNode != null)
      rootNodes[0] = new NodeAssumption(rootNode, roots[0]);
    else {
      Set<UUID> redInd = new HashSet<>();
      String fidname = FIDUpdater.getFIDName(db);
      for (int i = 0; i < roots.length; i++) {
        NodeDesc nd = roots[i];
        if (nd.getId().isEmpty()) continue;
        String rp = getUVID(nd);
        recWriteNames(nd, 1);
        //TODO determine path
        Map<TreeNode, Double> possibleRepRoots =
            locateSimilarByUV(rp, null, db.getNode(Requirement.getTypeRootQId()));
        if (nd.getAttributes().containsKey(fidname)) {
          AttributeDefinition a = nd.getAttributes().get(fidname);
          if (a != null && a.getRawValue().getValue() != null) {
            String val = a.getRawValue().getValue().toString();
            if (nodesByDoors.containsKey(val)) possibleRepRoots.put(nodesByDoors.get(val), doorsid);
          }
        }
        Double mx = Double.MIN_VALUE;
        if (possibleRepRoots.isEmpty()) {
          String name = rp;
          if (name.startsWith("/")) name = name.substring(1, name.length());
          if (name.indexOf("/") > -1) name = name.substring(0, name.indexOf("/"));
          TreeNode node =
              db.getNode(Requirement.getTypeRootQId()).createChild(name, Requirement.TYPE_NAME);
          redInd.add(node.getUUId());
          possibleRepRoots.put(node, 1.0D);
        }
        if (nd.getAttributes().containsKey(fidname)) {
          AttributeDefinition a = nd.getAttributes().get(fidname);
          if (a != null && a.getRawValue().getValue() != null) {
            String id = a.getRawValue().getValue().toString();
            TreeNode node = nodesByDoors.get(id);
            if (node != null) possibleRepRoots.put(node, doorsid);
          }
        }
        for (TreeNode nd2 : possibleRepRoots.keySet()) {
          Double d = possibleRepRoots.get(nd2);
          if (d > mx) {
            mx = d;
            rootNodes[i] = new NodeAssumption(nd2, nd);
            if (rootNodes[i].tnode.isRoot())
              rootNodes[i] = new NodeAssumption(root.findWorkChild("Requirements"), nd);
          }
        }
      }
    }
    Arrays.sort(
        rootNodes,
        new Comparator<NodeAssumption>() {

          private TreeNode[] buildParents(TreeNode node) {
            List<TreeNode> pars = new LinkedList<>();
            node = node.getWorkParent();
            while (node != null) {
              pars.add(node);
              node = node.getWorkParent();
            }
            return pars.toArray(new TreeNode[0]);
          }

          @Override
          public int compare(NodeAssumption o1, NodeAssumption o2) {
            //first - upper nodes
            //then - by req sorting
            if (o1 == null) return o2 == null ? 0 : -1;
            if (o2 == null) return 1;
            TreeNode n1 = o1.tnode, n2 = o2.tnode;
            TreeNode[] p1 = buildParents(n1), p2 = buildParents(n2);
            if (p1.length > p2.length) return -1;
            if (p1.length < p2.length) return -1;
            int i = 0;
            while (!p1[i].getUUId().equals(p2[i].getUUId())) i++;
            if (i == 0) {
              return o1.enode.getIndex().compareTo(o2.enode.getIndex());
            } else
              return TreeNodeComparator.getComparator(n1.getTreeDB().getSortingSettings())
                  .compare(p1[i - 1], p2[i - 1]);
          }
        });
    LinkedList<UUID> orderStruct = new LinkedList<>(), nameStruct = new LinkedList<>();
    Map<UUID, NodeAssumption> iroot = new HashMap<>();
    for (NodeAssumption na : rootNodes) {
      determineOrder(structuralChildren, na.enode.getUUId(), na, orderStruct, iroot, false);
      determineOrder(nameChildren, na.enode.getUUId(), na, nameStruct, iroot, false);
    }
    Map<UUID, Map<TreeNode, Double>> probability = new HashMap<>();
    String fidname = FIDUpdater.getFIDName(db);
    for (UUID uid : nameStruct) {
      NodeDesc nd = ndmap.get(uid);
      String fuid = getUVID(nd);
      if (fuid.startsWith("/")) fuid = fuid.substring(1);
      //gposs.put(uid, locateSimilarByUV(fuid,nd.getType(), iroot.get(uid).tnode));
      if (nd.getAttributes().containsKey(fidname)) {
        AttributeDefinition a = nd.getAttributes().get(fidname);
        if (a != null && a.getRawValue().getValue() != null) {
          String id = a.getRawValue().getValue().toString();
          TreeNode node = nodesByDoors.get(id);
          if (node != null) {
            if (!probability.containsKey(uid))
              probability.put(uid, new HashMap<TreeNode, Double>());
            probability.get(uid).put(node, doorsid);
          }
        }
      }
    }

    Map<TreeNode, UUID> backMap = new HashMap<>();
    Map<UUID, TreeNode> directMap = new HashMap<>();
    assignTreeNodes(backMap, directMap, probability);
    URI dpath = doc.getHTMLPath();
    dpath = new File(dpath).getParentFile().toURI();

    Set<UUID> washere = new HashSet<>();
    LinkedList<UUIDRoot> hndl = new LinkedList<>();
    for (NodeAssumption na : rootNodes) {
      directMap.put(na.enode.getUUId(), na.tnode);
      backMap.put(na.tnode, na.enode.getUUId());
      //hndl.addLast(na.enode.getUUId());
    }
    //directMap.put(null, rootNode!=null?rootNode:db.getNode(Requirement.getTypeRootQId()));

    Map<String, EnumDefinitionValue> enumDefs = new HashMap<>();

    {
      Map<String, Attribute> tatrs = db.getRootNode().attrs().getActiveAttributes();
      for (String s : tatrs.keySet()) {
        //				List<Attribute> atrs =tatrs.get(s);
        //				if(atrs!=null && !atrs.isEmpty()){
        Attribute a = tatrs.get(s);
        if (a != null
            && a.getGValue() != null
            && a.getType().equals(AttributeType.ENUM_DEFINITION))
          enumDefs.put(s, (EnumDefinitionValue) a.getGValue());
        //}
      }
    }
    UUID rid = nodeByLvl.get(0).iterator().next().getUUId();
    if (!directMap.containsKey(rid)&&!backMap.containsKey(rootNode)) directMap.put(rid, rootNode);
    Set<UUID> roottreenodes = new HashSet<>();
    //hndl.addFirst(e);
    for (NodeAssumption ns : rootNodes) {
      hndl.addFirst(new UUIDRoot(ns.enode.getUUId(), ns.tnode));
      roottreenodes.add(ns.tnode.getUUId());
    }
    Set<TreeNode> rem = new HashSet<>();
    Set<TreeNode> toup = new HashSet<>();
    Map<TreeNode, Index> inch = new HashMap<>();
    while (!hndl.isEmpty()) {
      UUIDRoot ur = hndl.removeFirst();
      UUID uid = ur.uuid;
      TreeNode root = ur.root;
      NodeDesc nd = ndmap.get(uid);
      UUID par = null;
      if (nameParents.get(uid) != null) par = nameParents.get(uid).iterator().next();
      recHndl(root, nd, par, washere, directMap, backMap, rem, dpath, hndl, enumDefs, toup, inch);
    }
    //TODO desc compare
    //build back comp
    for (String path : torem) {
      TreeNode node = db.getNode(path, Path.QID | Path.UUID | Path.XPATH | Path.UV);
      if (node != null)
        if (washere.contains(node.getUUId()))
          throw new RuntimeException("remove existing exception!");
      if (node != null) node.delete();
    }
    DeepFirstTreeWalker dftw = new DeepFirstTreeWalker(rootNode, false, false, true);
    Iterator<TreeNode> it = dftw.iterator();
    Set<TreeNode> tosave = new HashSet<>();
    while (it.hasNext()) {
      TreeNode node = it.next();
      if (!Requirement.getTypeRootQId().equals(node.getQualifiedId()))
        if (!washere.contains(node.getUUId())) {
          ps.println("toRemove: " + node.getQualifiedId());
          rem.add(node);
        }
    }
    for (TreeNode node : rem) {
      String s = node.getQualifiedId();
      if(!node.isDisposed()){
        node.delete();
        ps.println("removed: " + s + " " + node.getUUId());
      }
    }

    for (TreeNode node : toup) {
      if (node.isDisposed()) continue;

      node.commitAttributes();
    }
    //		dftw = new DeepFirstTreeWalker(root.getTreeDB().getNode(Requirement.getTypeRootQId()));
    //		Iterator<TreeNode> it2 = dftw.iterator();
    //		while(it2.hasNext()){
    //			TreeNode node = it2.next();
    //			for(String s:node.attrs().keySet()){
    //				Attribute a = node.attrs().getAttribute(s);
    //
    //				if(a!=null&&(a.getKey().toLowerCase().equals("обоснование")||a.getKey().toLowerCase().equals("примечание")))
    //				{
    //					String[] targs = null;
    //					if(a.getResultType()==AttributeType.LIST){
    //						List<Object> vals = a.getGValue(node.attrs());
    //						targs = new String[vals.size()];
    //						int i = 0;
    //						for(Object v:vals)
    //							targs[vals.size()-i++-1] = v.toString();
    //
    //					}else
    //						targs = new String[]{a.getValue(node.attrs()).toString().trim()};
    //					for(String targ:targs){
    //
    //					}
    //				}
    //			}
    //		}
    for (TreeNode nde : tosave) nde.commitAttributes();
    Set<TreeNode> pars = new HashSet<>();
    for (TreeNode node : inch.keySet())
      if (!roottreenodes.contains(node.getUUId())) pars.add(node.getWorkParent());
    for (TreeNode par : pars) {
      //create new order - new ind for all and old where new is not set
      List<TreeNode> alt =
          par.getSortedShownChildren(
              TreeNodeComparator.getComparator(SortingSettings.init(SortingMethod.BY_INDEX, false)),
              null); //get childs sorted by index, elems are mixed by types
      List<TreeNode> children =
          par.getSortedWorkChildren(
              new Comparator<TreeNode>() {
                @Override
                public int compare(TreeNode o1, TreeNode o2) {
                  Index i1 = newInd.get(o1.getUUId()), i2 = newInd.get(o2.getUUId());
                  if (i1 == null) i1 = o1.getIndex();
                  if (i2 == null) i2 = o2.getIndex();
                  if (i1 == null) return i2 == null ? 0 : -1;
                  if (i2 == null) return 1;
                  return i1.compareTo(i2);
                }
              },
              null,
              false);
      int[] left = new int[children.size()], right = new int[children.size()];
      Arrays.fill(left, -1);
      Arrays.fill(right, -1);
      fixOrder(children, alt, inch);

      int i = 0;
      int lastNN = -1;
      while (i < children.size()) {
        if (inch.get(children.get(i)) != null) {
          lastNN = i;
        }
        if (lastNN != -1) left[i] = lastNN;
        i++;
      }
      i = children.size() - 1;
      lastNN = -1;
      while (i > -1) {
        if (inch.get(children.get(i)) != null) {
          lastNN = i;
        }
        if (lastNN != -1) right[i] = lastNN;
        i--;
      }
      for (i = 0; i < children.size(); i++) {
        TreeNode chii = children.get(i);
        if (inch.get(chii) != null) {
          if (!chii.getIndex().toString().equals(inch.get(chii).toString())) {
            chii.setIndex(inch.get(chii),false);//TODO think
            chii.commitAttributes();
          }
        } else {
          Index lefti = Index.getZero();
          if (left[i] > -1) {
            lefti = inch.get(children.get(left[i]));
          }
          Index righti = null;
          if (right[i] > -1) righti = inch.get(children.get(right[i]));
          else righti = lefti.findGreaterIndex().findGreaterIndex();
          Index newI = lefti.findMiddleIndex(righti);
          if (!newI.toString().equals(chii.getIndex().toString())) {
            chii.setIndex(newI,false);//TODO think
            inch.put(chii, newI);
            int r = i;
            right[i] = i;
            int was = left[i];
            while (r < children.size() && was == left[r]) {
              left[r] = i;
              r++;
            }
            chii.commitAttributes();
          }
        }
      }
    }
    backMap.clear();
    interest.clear();
    marks.clear();
    tdef.clear();
    tdefpos.clear();
    lastNodes.clear();
    tempString.setLength(0);
    ignoreNext.clear();
    notignoreNext.clear();
    lastlvl = 1;
    lr = db.getLinkResolver();
    ndmap.clear();
    nodeByLvl.clear();
    nodeMarks.clear();
    structuralChildren.clear();
    structuralParent.clear();
    nameChildren.clear();
    nameParents.clear();
    parentName.clear();
    torem.clear();
    final UUID uid = UUID.randomUUID();
    nodeByLvl.put(0, new LinkedList<NodeDesc>());
    nodeByLvl
        .get(0)
        .add(
            new NodeDesc(
                db,
                uid,
                null,
                null,
                null,
                "Requirements",
                AttributeStorage.formTypeMap(uid, Requirement.TYPE_NAME),
                Collections.emptyList(),
                false));
    ndlvl.put(uid, 0);
    ndInd.put(uid, 0);
    maxInd = 1;
    nameChildren.put(uid, new LinkedList<UUID>());
    ndmap.put(uid, nodeByLvl.get(0).get(0));
    nameParents.put(uid, null);

    TreeNode reqroot = rootNode.getTreeDB().getNode(Requirement.getTypeRootQId());
    
    LibreOfficeAfterProcessing loap = new LibreOfficeAfterProcessing();
    if (warn.length() > 0) warn.append("\n");
    loap.messages.append(warn.toString());
    loap.setIgnoreTransaction(true);
    loap.execute(reqroot);

    TermsHandler th = new TermsHandler();
    if (warn.length() > 0) warn.append("\n");
    th.messages.append(warn.toString());
    th.setIgnoreTransaction(true);
    th.execute(reqroot);

    String mss = loap.messages.toString();
    if (mss != null && !mss.isEmpty()) showBIGMessageBox("Messages", mss);
  }

  public static void removeRev(List<TreeNode> alt, List<TreeNode> filtered, Map<TreeNode, Index> inch, PrintStream ps) {
    int m = alt.size(), n = filtered.size();
    if (m == 0 && n == 0) return;
    int[][] d = new int[m + 1][n + 1];
    char[][] p = new char[m + 1][n + 1];

    for (int i = 0; i <= m; i++) {
      d[i][0] = i;
      p[i][0] = 'D';
    }
    for (int i = 0; i <= n; i++) {
      d[0][i] = i;
      p[0][i] = 'I';
    }
    for (int i = 1; i <= m; i++)
      for (int j = 1; j <= n; j++) {
        int cost = (alt.get(i - 1) != filtered.get(j - 1)) ? 1 : 0;
        if (d[i][j - 1] < d[i - 1][j] && d[i][j - 1] < d[i - 1][j - 1] + cost) {
          //Вставка
          d[i][j] = d[i][j - 1] + 1;
          p[i][j] = 'I';
        } else if (d[i - 1][j] < d[i - 1][j - 1] + cost) {
          //Удаление
          d[i][j] = d[i - 1][j] + 1;
          p[i][j] = 'D';
        } else {
          //Замена или отсутствие операции
          d[i][j] = d[i - 1][j - 1] + cost;
          p[i][j] = (cost == 1) ? 'R' : 'M';
        }
      }
    StringBuilder route = new StringBuilder("");
    int i = m, j = n;
    do {
      if (i == 0 || j == 0) ps.println(1);
      char c = p[i][j];
      if (c == 'I') inch.put(filtered.get(j - 1), null);
      if (c == 'D') inch.put(alt.get(i - 1), null);
      if (c == 'R') inch.put(filtered.get(j - 1), null);
      if (c == 'R' || c == 'M') {
        i--;
        j--;
      } else if (c == 'D') {
        i--;
      } else {
        j--;
      }
    } while ((i != 0) || (j != 0));
    //return new Prescription(D[m][n], route.reverse().toString());
  }
  //creates two array and then removes some set of nodes to make changes of indexes
  //with minimal changes
  private void fixOrder(List<TreeNode> children, List<TreeNode> alta, Map<TreeNode, Index> inch) {
    // TODO Auto-generated method stub
    List<TreeNode> filtered = new ArrayList<>();
    int j = 0;
    for (TreeNode nd: children)
      if (inch.get(nd) != null) filtered.add(nd);
    //filtered = Arrays.copyOf(filtered, j);
    j = 0;
    List<TreeNode> alt = new ArrayList<>();
    for (TreeNode tn:alta) 
      if (inch.get(tn) != null) 
        alt.add(tn);
    //alt = Arrays.copyOf(alt, j);
    //		Arrays.sort(alt,new Comparator<TreeNode>(){
    //			@Override
    //			public int compare(TreeNode o1, TreeNode o2) {
    //				return inch.get(o1).compareTo(inch.get(o2));
    //			}
    //		});
    boolean stop = false;
    for (int i = 0; i < alt.size() && !stop; i++)
      if (!alt.get(i).getUUId().equals(filtered.get(i).getUUId())) stop = true;
    if (stop) removeRev(alt, filtered, inch, ps);
  }

  private void recHndl(
      TreeNode root,
      NodeDesc nd,
      UUID par,
      Set<UUID> washere,
      Map<UUID, TreeNode> directMap,
      Map<TreeNode, UUID> backMap,
      Set<TreeNode> rem,
      URI dpath,
      LinkedList<UUIDRoot> hndl,
      Map<String, EnumDefinitionValue> enumDefs,
      Set<TreeNode> toup,
      Map<TreeNode, Index> inch) {
    TreeNode newPar = null;
    if (nameParents.get(nd.getUUId()) != null) {
      newPar = directMap.get(nameParents.get(nd.getUUId()).iterator().next()); //TODO mult
    }
    TreeNode node;
    Boolean exist = false;
    if (!directMap.containsKey(nd.getUUId())||washere.contains(directMap.get(nd.getUUId()).getUUId())) { //create new
      node = noMoreTokens(nd, newPar, nd.getId(), inch);
      if (node == null) {
        try {
          if(newPar!=null){
            if ((newPar.isVirtual() || nd.getId().equals("") && newPar.getQualifiedId().equals(Requirement.getTypeRootQId()))){
            }else{
              String tp = nd.getType();
              node = newPar.createChild(nd.getId(), tp);
              inch.put(node, null);
            }
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
      }else{
        exist = true;
      }
    } else {
      node = directMap.get(nd.getUUId());
      exist = true;
    }
    if (node == null) return; //TODO what?
    directMap.put(nd.getUUId(), node);
    backMap.put(node, nd.getUUId());
    rem.remove(node);
    if (washere.contains(node.getUUId())) return;
    washere.add(node.getWorkParentUUID());//for hidden vnodes
    washere.add(node.getUUId());
    LinkedList<UUID> children = nameChildren.get(nd.getUUId());
    if (children != null) {
      Collections.sort(
          children,
          new Comparator<UUID>() {

            @Override
            public int compare(UUID o1, UUID o2) {
              return ndInd.get(o1).compareTo(ndInd.get(o2));
            }
          });
      for (UUID chi : children) hndl.addLast(new UUIDRoot(chi, root));
    }

    boolean isroot = (node.getQualifiedId() + "/").startsWith(root.getQualifiedId() + "/");

    Map<String, AttributeDefinition> attrs = new HashMap<>(nd.getAttributes());
    if (attrs.containsKey("_auto")) attrs.remove("_auto");
   // if (attrs != null && node != null) {
    {
    //mapNode.put(uid, node);
      AttributeDefinition ad = attrs.get(TreeNode.ATTR_DESCRIPTION);
      String newDesc = null;
      boolean changes = false;
      
      if(ad!=null&&ad.getRawValue()!=null){
        newDesc = reworkDescription(node, ad.getRawValue().getValue().toString());
      
        if (newDesc != null)
          attrs.put(
              TreeNode.ATTR_DESCRIPTION,
              node.attrs().createAttributeDescription(TreeNode.ATTR_DESCRIPTION,
                  ((HtmlValueProvider)ad.getValueProvider()).createAppended(AttributeType.HTML, newDesc)
              )
          );
      }
      
            //node.attrs().createAttribute(TreeNode.ATTR_DESCRIPTION, newDesc));
      Set<String> torem = new HashSet<>();
      for (String ak : node.attrs().getLocalAttributeKeys()) {
        String key = ak.toLowerCase();
        if (key.equals("примечание") || key.equals("обоснование")) torem.add(ak);
      }
      AttributeDefinition nname = attrs.get(TreeNode.ATTR_NAME);
      AttributeDefinition nid = attrs.get(TreeNode.ATTR_ID);
      if(exist)
      {
        String name = nid.getRawValue().getValue().toString();
        boolean in = true;
        if(nname!=null){
          name = nname.getRawValue().getValue().toString();
          AttributeDefinition ada = attrs.get(TreeNode.ATTR_IGNORE_NAME);
          in = ada != null && ada.getRawValue().getValue().toString().equals("true");
        }
        if(!name.equals(node.getId())){
          if(node.getName()==null)//new name and it needs to be ignored
            if(in)
              node.attrs().setAttribute(new AttributeDefinition(node.getUUId(), TreeNode.ATTR_IGNORE_NAME, 
                  new ConstValueProvider(AttributeType.STRING, "true")));
          node.setName(name);
          changes = true;
        }
      }else
      {
        if(nname!=null){
          node.setName(nname.getRawValue().getGValue());
          changes = true;
        }
      }
      for (String ak : attrs.keySet()) {
        if (ak.equals("ignoreName")
                && attrs.get(ak).getRawValue().getValue() != null
                && ak.equals("ignoreName")
                && !attrs.get(ak).getRawValue().getValue().toString().toLowerCase().equals("true") ||
                ak.equals(TreeNode.ATTR_ID)||ak.equalsIgnoreCase(TreeNode.ATTR_NAME))
          continue; //ignore = false -> ignore it
        AttributeDefinition a = attrs.get(ak);
        if (a == null) continue;
        //TODO look into.. attrs of diff viz?
        Attribute old = node.attrs().getAttribute(a.getKey());
        AttributeType ap = a.getType();
        AttributeType avp = a.getType() == AttributeType.LIST ? ((ListValue)a.getRawValue().getValue()).getType() : a.getType();
        String key = a.getKey().toLowerCase();
        if (key.equals("производное")) {
          String v = "Пр";
          //a = node.attrs().getAttribute("Производное");
          Object ao = a.getValue(node.attrs());
          if (ao == null
              || !ao.toString().toLowerCase().trim().equals("true")
                  && !ao.toString().toLowerCase().trim().equals("пр")) v = " ";
          AppliedEnumValue aev = new AppliedEnumValue(v, null, AttributeType.STRING, "Производное");
          if (ao == null || !ao.toString().trim().toLowerCase().equals(v.toLowerCase())) {
            if (old == null
                || !old.getDefinition().getType().equals(AttributeType.ENUM)
                || old.getGValue() == null
                || !old.getGValue().equals(aev)) {
              node.attrs().setConstAttribute("Производное", aev, 0);
              changes = true;
            }
          }
          continue;
        }
        if (key.equals("ref")
            || key.endsWith("-ref")
            || key.equals("refines")
            || key.startsWith("relates-")) {//TODO Tends to be moved to attribute creation
          if (ap != AttributeType.LIST) 
            {
              ap = AttributeType.REFERENCE;
            }
          else{
            avp = AttributeType.REFERENCE;
          }
        }
        if (enumDefs.containsKey(a.getKey())
            && ap != AttributeType.REFERENCE
            && ap != AttributeType.LIST) {
          ap = AttributeType.ENUM;
        }
        AttributeType lv = null;
        //String defname = a.getValueProvider().getType();
        if(a.getRawValue().getType()==AttributeType.LIST)
          lv = ((ListValue)a.getValue(node.attrs())).getType();
        if (!a.getType().equals(ap)
            || ap == AttributeType.LIST && avp != null && !avp.equals(lv)) {
          Object nv = a.getRawValue().getValue();
          if (nv instanceof ListValue && avp!=null&&!avp.equals(lv))
            nv = new ListValue(avp, ((ListValue) nv).getValues());
          if (a.getType() != AttributeType.ENUM && ap == AttributeType.ENUM)
            nv = new AppliedEnumValue(nv, null, a.getRawValue().getType(), a.getKey());
          IValueProvider vp = AttributeDefinition.detectValueProvider(key, ap, nv, TempFolderFilesProvider.EMPTY);
          a = new AttributeDefinition(a.getOrigin(), a.getKey(), vp);
          //a = node.attrs().createAttributeDescription(ap, a.getKey(), nv);
        }
        AttributeDefinition res = null;
        if (enumDefs.containsKey(a.getKey()) && !a.getType().equals(AttributeType.ENUM))
          a =
            new AttributeDefinition(a.getOrigin(), a.getKey(), new ConstValueProvider(AttributeType.ENUM,
                new AppliedEnumValue(a.getRawValue().getValue(), null, a.getRawValue().getType(), a.getKey())));
        if (old == null || !a.weakEquals(old.getDefinition())) {
          try {
            res = a.copy(node.getUUId());
            changes = true;
          } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
        }
        if ((res != null)) {
          changes = true;
          try{
            if (res.getKey().equals(TreeNode.ATTR_DESCRIPTION)) {
              Object v = res.getValue(node.attrs());
              if (!isDescrClose(
                  v == null ? null : v.toString(),
                  node.attrs().getStringAttributeValue(TreeNode.ATTR_DESCRIPTION)))
                setDescription(node, (HtmlValueProvider) res.getValueProvider(), dpath);
            } else if (res.getValue(node.attrs()) == null) node.attrs().removeAttribute(res.getKey());
            else node.attrs().setAttribute(res);
          }catch(OperationNotAllowedException anae){
            RLogger.logError(anae);
            //now only _id on Requirements
          }
          changes = true;
        }
        //what about tntype?

      }
      for (String s : torem) node.attrs().removeAttribute(s);
      String newname = null;
      if (attrs.get(TreeNode.ATTR_NAME) != null)
        newname = attrs.get(TreeNode.ATTR_NAME).getRawValue().getValue().toString();
      if (newname != null) {
        node.setName(newname);
        if (node.attrs().containsKey("ignoreName")
            && !node.attrs().getStringAttributeValue("ignoreName", "").equals("true"))
          node.attrs().removeAttribute("ignoreName");
      }

      //			if(attrs.get(TreeDB.INDEX_ATTRIBUTE)!=null && attrs.get(TreeDB.INDEX_ATTRIBUTE).iterator().next().getRawValue()!=null){
      //				node.setIndex(com.unitesk.requality.tools.Index.create(attrs.get(TreeDB.INDEX_ATTRIBUTE).iterator().next().getRawValue().toString()));
      //				changes = true;
      //			}
      if (!isroot) { //roots cannot be moved or renamed
        boolean ren =
            !node.getUUId().equals(rootNode.getUUId()) && !node.getId().equals(nd.getId());
        if (newPar != null && !isroot && !newPar.getUUId().equals(node.getWorkParentUUID())) {
          if (newPar.findWorkChild(nd.getId()) != null) {
            Set<String> ids = new HashSet<>();
            for (TreeNode node2 : node.getWorkParent().getWorkChildren()) ids.add(node2.getId());
            for (TreeNode node2 : newPar.getWorkChildren()) ids.add(node2.getId());
            node.setId(RequalityCLI.getNextId(newPar, node.getType(), null));
            node.setName(getNameOrId(nd));
            node.attrs().setConstAttribute("ignoreName", true, 0);
            changes = true;
            ren = false;
          }
          node.setParent(newPar);
          inch.put(node, null);
        }
        newInd.put(node.getUUId(), Index.create(nd.getIndex()));
        if (ren && node.getWorkParent().findWorkChild(nd.getId()) != null) {
          node.setId(RequalityCLI.getNextId(node.getWorkParent(), node.getType(), null));
          node.setName(getNameOrId(nd));
          node.attrs().setConstAttribute("ignoreName", true, 0);
          ren = false;
          changes = true;
        }
        if (ren && (!node.getId().equals("Requirements") || !node.getWorkParent().isRoot())){
          node.setId(nd.getId());
          changes = true;
        }
      }
      if (changes) node.commitAttributes();
      toup.add(node);
      /*if(newDesc!=null && (node.getDescription()==null || !node.getDescription().equals(newDesc)))
      	setDescription(node,newDesc);
      if(attrs.get(TextNode.ATTR_TNTYPE)!=null&& attrs.get(TextNode.ATTR_TNTYPE).getRawValue()!=null&&type.equals(TextNode.TYPE_NAME)&& (node.getAttribute(TextNode.ATTR_TNTYPE)==null || !node.getAttribute(TextNode.ATTR_TNTYPE).getValue().equals(attrs.get(TextNode.ATTR_TNTYPE)))){
      	if(!node.getType().equals(TextNode.TYPE_NAME))
      		ps.println("ouch "+getQID(nd)+" "+node.getQualifiedId());
      	else
      		((TextNode)node).setTextNodeType(attrs.get(TextNode.ATTR_TNTYPE).getRawValue().toString());
      }*/
    }
  }

  private boolean isDescrClose(String v1, String v2) {
    if (v1 == null) return v2 == null ? true : v2.trim().isEmpty();
    if (v2 == null) return v1.trim().isEmpty();
    return v1.trim().equals(v2.trim());
  }

  private Pattern tnKind =
      Pattern.compile(
          "[\\s\\n]*(Описание|Примечание|Обоснование).[\\s\\n]*(.*)",
          Pattern.MULTILINE | Pattern.DOTALL);
  //private Pattern badAttr = Pattern.compile("[\\s\\n]*([\\d\\w]+):[\\s\\n]*([^\n]*)\n*(.*)",Pattern.MULTILINE|Pattern.DOTALL);
  private TreeNode noMoreTokens(NodeDesc nd, TreeNode it, String rt, Map<TreeNode, Index> inch) {
    Map<String, AttributeDefinition> attrs = nd.getAttributes();
    if (attrs.containsKey("_auto")) attrs.remove("_auto");

    List<TreeNode> bases = it.findShownChildsPlusName(rt);
    //TODO textnode rename
    TreeNode base = null;
    if (bases != null && bases.size() > 0) {
      base = bases.iterator().next();
    }
    if (nd.getType() == null) ps.println("AAA! NULLL!!");
    if (it.isVirtual() || rt.equals("") && it.getQualifiedId().equals(Requirement.getTypeRootQId()))
      //TODO what to do?
      return null;
    if (base != null) {
      putInch(inch, base);
    }
    TreeNode node = base;
    return node;
  }

  private void putInch(Map<TreeNode, Index> inch, TreeNode base) {
    TreeNode bp = base.getWorkParent();
    if (bp == null) {
      inch.put(base, null);
      return;
    }
    //int ind = -1;
    Index ind = null;
    for (TreeNode node : bp.getWorkChildren()) {
      //if(!inch.containsKey(node)||inch.get(node)>-1)
      //ind++;
      if (node.getUUId().equals(base.getUUId()) && node.getIndex()!=null)
        ind = Index.create(node.getIndex().toString());
    }
    if (ind!=null && !ind.equals(Index.getUndefined())) inch.put(base, ind);
  }

  Pattern firstlastbr = Pattern.compile("[\\s\\n]*(^[\\s\\n])+[\\s\\n]*");
  Pattern notpren = Pattern.compile("(\\n)(?![^<]*>|[^<>]*<\\/)");

  private String replaceN(String string, String repl) {
    if (!string.contains("pre")) {
      string = string.replace("\n", repl);
      while (string.indexOf("  ") > -1) string = string.replace("  ", " ");
    } else string = string.replace(notpren.pattern(), repl);
    return string;
  }

  private String reworkDescription(TreeNode node2, String string) {
    //TODO pictures
    string =
        string
            .replace("&nbsp;", " ")
            .replace("</br>", "")
            .replace("<br>", "<br/>")
            .replace("<br/>", "\n");
    string = CoreUtils.htmlEncapsulate(string);
    //collect images?
    string = string.replace("imgs/", doc.getNodeResourcesPath() + "/imgs/");
    //if(string.startsWith("<span style='background: #ffefff'>"))
    //string = string.substring(34,string.length()-7);
    string = string.trim();
    if (string.isEmpty()) return null;
    return string;
  }

  private void assignTreeNodes(
      Map<TreeNode, UUID> backMap,
      Map<UUID, TreeNode> directMap,
      Map<UUID, Map<TreeNode, Double>> gposs) {
    for (UUID uid : gposs.keySet()) {
      double max = -1;
      TreeNode trg = null;
      if (gposs.get(uid) != null && gposs.get(uid).size() > 0)
        for (TreeNode nd : gposs.get(uid).keySet()) {
          if (gposs.get(uid).get(nd) > max) {
            if (backMap.containsKey(nd)) {
              if (gposs.get(backMap.get(nd)).get(nd) >= gposs.get(uid).get(nd)) {
                //new is not so good
              } else {
                //possible target
                max = gposs.get(uid).get(nd);
                trg = nd;
              }
            } else {
              max = gposs.get(uid).get(nd);
              trg = nd;
            }
          }
        }
      if (trg != null) {
        //ps.println(max+":- "+ndmap.get(uid)+" "+getQID(ndmap.get(uid))+" "+(trg!=null?trg.getQualifiedId():"")+" "+uid);
        backMap.put(trg, uid);
        directMap.put(uid, trg);
      }
    }
  }

  private Map<TreeNode, Double> locateSimilarByUV(
      String userVisibleName, String type, final TreeNode node2) {
    if (userVisibleName.equals("/") || userVisibleName.isEmpty())
      return new HashMap<TreeNode, Double>() {
        /** */
        private static final long serialVersionUID = 7443551297774470826L;

        {
          put(node2, ufweigth);
        }
      };
    TreeDB db = node2.getTreeDB();
    StringTokenizer st = new StringTokenizer(userVisibleName, "/");
    String nodeId = st.nextToken();
    final TreeNode rnode = db.getNode("/Requirements", Path.QID);
    boolean isqid = false;
    if (nodeId.equals("Requirements") || nodeId.equals(rnode.getName())) {
      isqid = true;
      if (st.hasMoreTokens()) nodeId = st.nextToken();
      else
        return new HashMap<TreeNode, Double>() {
          /** */
          private static final long serialVersionUID = -8186570830627444019L;

          {
            put(rnode, ufweigth);
          }
        };
    }
    //TODO fasr possiblities
    Map<TreeNode, Double> precurnodes = new HashMap<>();
    for (TreeNode node : rnode.getWorkChildren()) {
      String id = node.getId();
      int diff = Math.abs(nodeId.length() - id.length());
      if (diff <= (nodeId.length() / 2)) {
        //TODO rename support
        if (nodeId.equals(id)) precurnodes.put(node, ufweigth);
      }
      {
        String name = node.getName();
        //TODO rename support
        if (name != null && Math.abs(nodeId.length() - name.length()) <= (name.length() / 2))
          if (nodeId.equals(name)) precurnodes.put(node, ufweigth);
      }
      if (node.getSUUId().equals(nodeId)) precurnodes.put(node, ufweigth);
    }
    LinkResolver lr = rnode.getTreeDB().getLinkResolver();
    Set<UUID> namecache = lr.getNameCache(nodeId);
    if (namecache!=null && !isqid) {
      Set<UUID> nodes = namecache;
      if (nodes.size() < 1) return new HashMap<>();
      for (UUID uid : nodes) {
        TreeNode nde = db.getNode(uid);
        if (!precurnodes.containsKey(nde)) precurnodes.put(nde, ufweigth / nodes.size());
      }
    }

    //if cache of Requiremens names contains nodeId
    //then try to get appropriate node
    if (precurnodes.size() == 0) {
      return new HashMap<>();
    }

    String[] tokens = new String[st.countTokens()];
    int i = 0;
    while (st.hasMoreTokens()) tokens[i++] = st.nextToken();

    Map<TreeNode, Double> curnodes = new HashMap<TreeNode, Double>();
    for (TreeNode node : new HashSet<TreeNode>(precurnodes.keySet())) {
      List<TreeNode> uid = lr.findNodeByIdNamePath(node, tokens);
      if (uid != null && uid.size() > 0) {
        //TODO multy
        TreeNode nde = uid.iterator().next();
        if (type == null || nde.getType().equals(type)) curnodes.put(nde, precurnodes.get(node));
      }
    }
    return curnodes;
  }

  private void determineOrder(
      Map<UUID, LinkedList<UUID>> mao,
      UUID node,
      NodeAssumption na,
      LinkedList<UUID> order,
      Map<UUID, NodeAssumption> iroot,
      boolean childfirst) {
    iroot.put(node, na);
    if (!childfirst) order.add(node);
    if (mao.containsKey(node)) {
      List<UUID> nodes = mao.get(node);
      Collections.sort(
          nodes,
          new Comparator<UUID>() {
            @Override
            public int compare(UUID o1, UUID o2) {
              NodeDesc n1 = ndmap.get(o1), n2 = ndmap.get(o2);
              // TODO Auto-generated method stub
              if (ndInd.containsKey(n1.getUUId()) && ndInd.containsKey(n2.getUUId()))
                return ndInd.get(n1.getUUId()).compareTo(ndInd.get(n2.getUUId()));
              return n1.getIndex().compareTo(n2.getIndex());
            }
          });
      for (UUID child : nodes) determineOrder(mao, child, na, order, iroot, childfirst);
    }
    if (childfirst) order.add(node);
  }
}
