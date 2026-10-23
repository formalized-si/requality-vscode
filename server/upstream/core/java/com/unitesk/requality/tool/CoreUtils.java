package com.unitesk.requality.tool;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PrimitiveIterator.OfInt;
import java.util.Set;
import java.util.Stack;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.eclipse.jetty.util.URIUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Document.OutputSettings;
import org.jsoup.nodes.Entities.EscapeMode;

import com.unitesk.requality.core.attribute.IReqURIResolver;
import com.unitesk.requality.core.attribute.ReqURIResolveResult;
import com.unitesk.requality.core.attribute.reference.RelManager;
import com.unitesk.requality.core.attribute.reference.RelationsTreeContext;
import com.unitesk.requality.core.attribute.reference.TreeRelationsCounter;
import com.unitesk.requality.core.exception.NodeNotFoundException;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.selection.SelectionRecord;
import com.unitesk.requality.core.storage.DBLocalName;
import com.unitesk.requality.core.storage.IFileDeleteListener;
import com.unitesk.requality.core.storage.IResTracker;
import com.unitesk.requality.core.storage.ITreeStorage;
import com.unitesk.requality.core.storage.LateResolveRequest;
import com.unitesk.requality.core.storage.TrackerCacheManipulatorsManager;
import com.unitesk.requality.core.storage.cache.CacheStorage;
import com.unitesk.requality.core.tree.TreeState;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.document.OmnivorousImporter;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.nodetype.TextNode;
import com.unitesk.requality.nodetype.VirtualNode;
import com.unitesk.requality.repo.RevisionInfo;
import com.unitesk.requality.report.ReportGenerationContext;
import com.unitesk.requality.tool.cli.CommandLineArgsProvider;
import com.unitesk.requality.tool.cli.ConsoleProgressMonitor;
import com.unitesk.requality.tool.cli.RequalityCLI;

public class CoreUtils {
  public static TreeNode[] NO_NODES = new TreeNode[0];
  private static List<IFileDeleteListener> listeners = new LinkedList<>();  
  public static final DateFormat SQL_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
  public static final String CACHE_FOLDER_NAME = ".reqcache";
  public static int DEBUG_LEVEL = 0;
  private static final String REQ_URI_START = "requality://";
  private static String locked = "";
  private static Map<File,String> names = new HashMap<>();
  private static Map<File, Long> lchange = new HashMap<>();
  
  public static void addFileDeleteListener(IFileDeleteListener fdl)
  {
    listeners.add(fdl);
  }
  public static List<IFileDeleteListener> getListeners(){
    return listeners;
  }
  public static String getWorkspaceOperation(){
    synchronized (NO_NODES) {
      return locked;
    }
  }
  public static void lockWorkspace(String message){
    synchronized (NO_NODES) {
      locked = message;
    }
  }
  public static void unlockWorkspace(){
    synchronized (NO_NODES) {
      locked = "";
    }
  }
  public static DeleteHandler deleteHandler = new DeleteHandler(), 
		  					  baseDeleteHandler = deleteHandler;
  public static class DeleteHandler{
    public boolean delete(File f, boolean forceNow, boolean clearRec, int depth){
      boolean del = true;
      boolean canDel = true;

        if(clearRec)
          CacheStorage.closeRecConnection(f);
	    if(f.isDirectory()){
	      canDel = false;
	      if(depth==-2||depth>0){
	        for(File c:f.listFiles())
	          del = del && delete(c, forceNow, false, depth!=-2?depth-1:-2);
	        if(f.listFiles().length == 0)
	          canDel = true;
	      }
	      else
	        canDel = f.list().length==0;
	    }
	    if(del && canDel){
	      java.nio.file.Path p = f.toPath();
	      if(forceNow){
	        for(IFileDeleteListener fdl:listeners)fdl.fileWillBeDeleted(p);
	        //problems with git pach objects on win now
	        if(f.exists()) {
	          if(!f.delete())
	          {
	    	    try {
	              Thread.currentThread().join(500);
	            } catch (InterruptedException e) {
	            }
	    	    if(!f.delete())
	              throw new RuntimeException("Cannot delete "+f.toPath());
	          }
	        }
	        for(IFileDeleteListener fdl:listeners)fdl.fileWasDeleted(p);
	        return del;
	      }
	      else{
	        for(IFileDeleteListener fdl:listeners)fdl.fileWillBeDeleted(p);
	        f.deleteOnExit();
	        for(IFileDeleteListener fdl:listeners)fdl.fileWasDeleted(p);
	        return true;
	      }
	    }else
	      return !canDel;
    }
  }

  public static String getParentQualifiedId(String QId) {
    return QId.substring(0, QId.lastIndexOf("/"));
  }
  
  private static int compareNumbers(String in1, String in2) {
    while (in1.length() > in2.length()) in2 = "0" + in2;
    while (in1.length() < in2.length()) in1 = "0" + in1;
    int i = 0;
    while (in1.charAt(i) == in2.charAt(i) && i < (in1.length() - 1)) i++;
    char c1 = in1.charAt(i), c2 = in2.charAt(i);
    if (c1 > c2) return 1;
    if (c1 < c2) return -1;
    return 0;
  }
  public static int compareNumberedStrings(String s1, String s2) {
    int k = 0;
    StringBuffer l1 = new StringBuffer(),
        l2 = new StringBuffer(),
        nu1 = new StringBuffer(),
        nu2 = new StringBuffer();
    int i1 = 0, i2 = 0;
    if(s1 == null)
      return -1;
    if(s2 == null)
      return 1;
    for (; i1 < s1.length() && i2 < s2.length(); ) {
      while (i1 < s1.length() && Character.isDigit(s1.charAt(i1))) {
        nu1.append(s1.charAt(i1));
        i1++;
      }
      while (i2 < s2.length() && Character.isDigit(s2.charAt(i2))) {
        nu2.append(s2.charAt(i2));
        i2++;
      }
      String in1 = "0", in2 = "0";
      if (nu1.length() > 0) in1 = nu1.toString();
      if (nu2.length() > 0) in2 = nu2.toString();
      if (nu1.length() > 0)
        if (nu2.length() > 0) k = compareNumbers(in1, in2);
        else k = -1;
      else if (nu2.length() > 0) k = 1;
      if (k != 0) return k;
      if (i1 < s1.length() && i2 < s2.length()) {
        while (i1 < s1.length() && !Character.isDigit(s1.charAt(i1))) {
          l1.append(s1.charAt(i1));
          i1++;
        }
        while (i2 < s2.length() && !Character.isDigit(s2.charAt(i2))) {
          l2.append(s2.charAt(i2));
          i2++;
        }
        k = l1.toString().compareToIgnoreCase(l2.toString());
        if (k != 0) return k;
      }
    }
    return s1.substring(i1).compareToIgnoreCase(s2.substring(i2));
  }
  private static int countRTNChilds(TreeNode node) {
    int cnt = 0;
    for(TreeNode tn:node.getActiveWorkChildren())
      if(tn.getType().equals(Requirement.TYPE_NAME)||tn.getType().equals(TextNode.TYPE_NAME))
        cnt++;
    return cnt;
  }
  public static boolean streamEquals(InputStream i1, InputStream i2) throws IOException {
    if(i1 == null)
      return i2 != null;
    if(i2 == null)
      return false;
    ReadableByteChannel ch1 = Channels.newChannel(i1);
    ReadableByteChannel ch2 = Channels.newChannel(i2);

    ByteBuffer buf1 = ByteBuffer.allocateDirect(1024);
    ByteBuffer buf2 = ByteBuffer.allocateDirect(1024);

    try {
        while (true) {

            int n1 = ch1.read(buf1);
            int n2 = ch2.read(buf2);

            if (n1 == -1 || n2 == -1) return n1 == n2;

            buf1.flip();
            buf2.flip();

            for (int i = 0; i < Math.min(n1, n2); i++)
                if (buf1.get() != buf2.get())
                    return false;

            buf1.compact();
            buf2.compact();
        }

    } finally {
      if (ch1 != null) ch1.close();
      if (ch2 != null) ch2.close();
      if (i1 != null) i1.close();
      if (i2 != null) i2.close();
    }
  }
  public static boolean isHeader(TreeNode node){
    String ctype = node.getType(); 
    boolean isReq = ctype.equals(Requirement.TYPE_NAME);
    boolean isTxt = ctype.equals(TextNode.TYPE_NAME);
    boolean isVN = ctype.equals(VirtualNode.TYPE_NAME);
    boolean isTS = ctype.equals("TestStep");
    if(isReq || isTxt || isVN || isTS){
      //  var child = getNodeByUUID(uuid);
        if (isTS || (isReq || isVN) && node.getActiveWorkChildren().size()>0 && 
        (countRTNChilds(node))>0 
                || node.hasHeaderAttribute())
                    return true;
    }
    //TODO remove or make it customizable?
    if(node.attrs().getStringAttributeValue("Вид", "Требование").equals("Заголовок"))
      return true;
    return false;
  }
  public static String splitWords(String s) {
    if (s == null) return null;
    for (int i = 1; i < s.length(); i++) // s.length() is variable
    if (s.charAt(i - 1) >= 'a'
          && s.charAt(i - 1) <= 'z'
          && s.charAt(i) >= 'A'
          && s.charAt(i) <= 'Z') {
        s = s.substring(0, i) + " " + s.substring(i);
      }
    return s;
  }
  public static Map<Character, String> getRepl(){
    return repl;
  }
  static Map<Character, String> repl = new HashMap<>();
  static{
    repl.put('%', "%25");
    repl.put('!', "%21");
    repl.put('*', "%2A");
    repl.put('\'', "%27");
    repl.put('"', "%22");
    repl.put('(', "%28");
    repl.put(' ', "%20");
    repl.put(')', "%29");
    repl.put(';', "%3B");
    repl.put(':', "%3A");
    repl.put('@', "%40");
    repl.put('&', "%26");
    repl.put('=', "%3D");
    repl.put('+', "%2B");
    repl.put('$', "%24");
    repl.put(',', "%2C");
    //repl.put('/', "%2f");
    repl.put('?', "%3F");
    repl.put('#', "%23");
    repl.put('[', "%5B");
    repl.put(']', "%5D");
  }
  private static String escapeString(String s){
    StringBuilder sb = new StringBuilder();
    for(int i = 0;i<s.length();i++)
    {
      char c = s.charAt(i);
      sb.append(repl.getOrDefault(c,Character.toString(c)));
    }
    return sb.toString();
  }
  private static Charset charset = StandardCharsets.UTF_8;
  private static String deEscapeString(String s){
    return URIUtil.decodePath(s);//TODO test properly and maybe replace
  }
  private static int hexNum(char next) {
    if(Character.isDigit(next))
      return next-'0';
    if(Character.isUpperCase(next))
      return next-'A'+10;
    return next-'a'+10;
  }
  private static boolean isHex(char ch) {
    return Character.isDigit(ch)||(ch>='a'&&ch<='f')||(ch>='A'&&ch<='F');
  }
  public static String URLStrictEncodePart(String filename){// TODO Auto-generated method stub
    return URLEncoder.encode(filename.replace(" ", (char)7+""), StandardCharsets.UTF_8).replace("%07", "%20");
  }
  public static String URLEncode(String s) {
    StringBuilder sb = new StringBuilder();
    int i = 0, j = 0, lh = -1;
    while(i<s.length()){
      if(j>=s.length()||!Character.isLetterOrDigit(s.charAt(j))){
        sb.append(s.substring(i,j));        
        if(lh == -1){
          lh = j;
          i = j;
        }
        i++;
        j = i;
      }
      while(j<s.length()&&Character.isLetterOrDigit(s.charAt(j))){
        j++;
      }
      if(lh>-1&&i>0&&i<s.length()&&Character.isLetterOrDigit(s.charAt(i)))
      {
        sb.append(URIParserUtil.escapeChars(escapeString(s.substring(lh,i))));
        lh = -1;
      }
    }
    if(lh!=-1)
      sb.append(URIParserUtil.escapeChars(escapeString( s.substring(lh,s.length()))));
    if(j>i)
      sb.append(s.substring(i,j));
    return sb.toString();
  }

  public static String URLDecode(String s) {
    return clearURIChars(deEscapeString(s));
  }
  public static String htmlDeescape(String str) {
    if (str == null) return null;
    return htmlDespaces(str.replace("&lt;","<").replace("&gt;",">").replace("&amp;","&"));
  }

  public static String htmlEscape(String str) {
    if (str == null) return null;
    return htmlSpaces(str.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"));
  }

  public static String adaptTables(String str, String width, String targetwidth) {
    int ind = str.indexOf("<table");
    int last = 0;
    while(ind>-1)
    {
      last = ind;
      int end = str.indexOf(">",ind);
      int w = str.indexOf("width");
      if(w<end){
        int c = w+6;
        while(Character.isWhitespace(str.charAt(c)))
          c++;
        char ch = str.charAt(c);
        char br = ' ';
        if(!Character.isDigit(ch))
          br = ch;
        else 
          c--;
        int wend =  Math.min(str.indexOf(br,c+1), end)-1;
        String wdth = str.substring(c+1,wend);
        if(wdth.contains(width))
          str = str.substring(last, c)+targetwidth+str.substring(wend);
      }
      ind = str.indexOf("<table",ind+10);
    }
    return str;
  }
  public static String htmlSpaces(String str) {
    if (str == null) return null;
    return str.replace("  ", (char) 160 + "" + (char) 160);
  }
  public static String htmlDespaces(String str) {
    if (str == null) return null;
    return str.replace((char) 160 + "" + (char) 160,"  ");
  }

  public static String listToString(List<String> list, String separator) {
    return listToString(list.toArray(), separator);
  }

  public static String listToString(Object[] list, String separator) {
    return listToString(list, separator, false);
  }

  public static String listToString(Object[] list, String separator, boolean b) {
    String r = "";
    String qu = b ? "'" : "";
    if (list == null) list = new Object[0];
    for (int i = 0; i < list.length; i++)
      if (b && !list[i].toString().startsWith("'")) r += qu + list[i] + qu + separator;
      else r += list[i] + separator;
    if (r.equals("")) return r;
    return r.substring(0, r.length() - separator.length());
  }

  private static Pattern tagFind = Pattern.compile("\\<[/]?([a-zA-Z]+)(?:[^>]*)\\>");
  private static Map<String, Stack<String>> gtags = new HashMap<>(),
      gclosed = new HashMap<>(),
      gclosedNames = new HashMap<>(),
      gtagsBasic = new HashMap<>();
  public static String htmlEncapsulate(String newDescr) {
    return htmlEncapsulate(newDescr, false);
  }
  /**
   * Method to close all opened tags and fix some problems with tags overlap. Function only
   * considering general structure of tags without deep analysis. Single tags needs to be provide in
   * accordance with XHTML style like "<br>
   * " instead of "<br>
   * ". Otherwise closing tag will be added. Example: for string "<b>one<i> more</b><u><img/> <br>
   * " function will return "<b>one<i> more</i></b><u><img/> <br>
   * </br> </u>". "<br>
   * " is not comply with XHTML standard and therefore it is provided by closing tag.
   *
   * @param xhtml markup with possible unclosed and overlapping tags
   * @return correct xhtml markup
   */
  public static String htmlEncapsulate(String newDescr, boolean replamp) {
    if (newDescr == null) return null;
    //Whitelist wl = Whitelist.relaxed();
    //wl.addTags("svg");
//    wl.addTags("img");
//    wl.addAttributes("img", "alt");
//    wl.addAttributes("img", "src");
//    wl.addProtocols("a", "href", "requality");
//    wl.addTags("table", "td", "tr");
    OutputSettings os = new OutputSettings();
    os.syntax(Document.OutputSettings.Syntax.xml);
    os.escapeMode(EscapeMode.xhtml);
    os.prettyPrint(false);
    os.charset(Charset.forName("UTF-8"));
    Document doc = 
        Jsoup.parse("<html><body>"+newDescr+"</body></html>");
    doc.outputSettings(os);
    String ret = doc.body().html();
//    if(replamp)
//      ret = replAmp(ret);
    return ret;
  }

  public static String replAmp(String string) {
    if(string == null)
      return null;
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
          repl = "&#" + repl;
          string = string.substring(0, ind) + repl + string.substring(end);
          ind += repl.length() - substr.length();
          ;
        }
      }
      ind = string.indexOf("&", ind + 1);
    }
    return string;
  }
  public static String replHtmlChars(String string) {
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
        if(repl == null) {
          if(substr.equals("lt")) repl = Integer.toString((int)'<'); 
          if(substr.equals("gt")) repl = Integer.toString((int)'>');
          if(substr.equals("amp")) repl = Integer.toString((int)'&');
        }
        if (repl != null) {
          try {
            int replI = Integer.parseInt(repl);
        	repl = Character.toString((char)replI);
          }catch(NumberFormatException nfe) {
        	repl = "&"+repl+";";
          }
          string = string.substring(0, ind) + repl + string.substring(end+1);
          ind += repl.length() - substr.length();
          ;
        }else if(!substr.isEmpty())
          RLogger.logDebug("Unknown character for replace "+substr);
      }
      ind = string.indexOf("&", ind + 1);
    }
    return string;
  }

  public static String replaceBracket(String substring) {
    int bpos = substring.indexOf("<");
    while (bpos > -1) {
      substring = substring.substring(0, bpos) + "&lt;" + substring.substring(bpos + 1);
      bpos = substring.indexOf("<", bpos + 1);
    }
    bpos = substring.indexOf(">");
    while (bpos > -1) {
      substring = substring.substring(0, bpos) + "&gt;" + substring.substring(bpos + 1);
      bpos = substring.indexOf(">", bpos + 1);
    }
    return substring;
  }

  public static String htmlClear(String content) {
    if(content == null)
      return null;
    int lpos = content.indexOf("<");
    while (lpos > -1) {
      int rpos = content.indexOf(">", lpos);
      content = content.substring(0, lpos) + content.substring(rpos + 1);
      lpos = content.indexOf("<", lpos * 2 - rpos + 1);
    }
    return content;
  }

  public static int calcDist(String s1, String s2) {
    if (s1 == null) return Integer.MAX_VALUE;
    if (s2 == null) return Integer.MAX_VALUE;
    int m = s1.length(), n = s2.length();
    int[] d1;
    int[] d2 = new int[n + 1];

    for (int i = 0; i <= n; i++) d2[i] = i;

    for (int i = 1; i <= m; i++) {
      d1 = d2;
      d2 = new int[n + 1];
      for (int j = 0; j <= n; j++) {
        if (j == 0) d2[j] = i;
        else {
          int cost = (s1.charAt(i - 1) != s2.charAt(j - 1)) ? 1 : 0;
          if (d2[j - 1] < d1[j] && d2[j - 1] < d1[j - 1] + cost) d2[j] = d2[j - 1] + 1;
          else if (d1[j] < d1[j - 1] + cost) d2[j] = d1[j] + 1;
          else d2[j] = d1[j - 1] + cost;
        }
      }
    }
    return d2[n];
  }

  public static String TABLES_REFS = "relates-table";

  public static String reworkLibreDesc(
      TreeNode node,
      String desc,
      boolean replace,
      Set<TreeNode> tosave,
      Map<UUID, String> doorsids) {
    int rpos = desc.indexOf("[");
    while (rpos > -1) {
      int min = Math.max(0, rpos - 200);
      int apos = -1;
      int hpos = desc.substring(min, rpos).lastIndexOf("href");
      if (hpos > -1) {
        hpos = hpos + min;
        apos = desc.substring(min, rpos).lastIndexOf("<a") + min;
        char quote = '"';
        int mode = 0;
        while (mode < 3) {
          char c = desc.charAt(hpos);
          if (c == '=' && mode == 0) mode = 1;
          else if ((c == '\'' || c == '"') && (mode == 1)) {
            quote = c;
            mode = 2;
          } else if (mode == 2) {
            mode = 3;
            String link = desc.substring(hpos, desc.indexOf(quote, hpos)).trim();
            if (link.startsWith("#")) link = link.substring(1);
            if (link.indexOf("|") > -1) link = link.substring(0, link.indexOf("|"));
            List<TreeNode> rnode = null;
            try {
              rnode = node.getTreeDB()
                 .getLinkResolver()
                 .findNodesByPathIgnoreDisposed(
                    node,
                    null,
                    node.getTreeDB(),
                    link,
                    Path.XPATH | Path.QID | Path.UUID | Path.UV);
            } catch (NodeNotFoundException e) {
              // TODO Auto-generated catch block
              e.printStackTrace();
            }
            // TODO warn on not found db
            if (rnode == null || rnode.size() == 0) {
              rnode = new LinkedList<>();
              rnode.add(node.getTreeDB().getByTags(node, link));
            }
            if (rnode.size() > 0)
              if (rnode.iterator().next() == null) rnode = null;
              else link = rnode.iterator().next().getQualifiedId();

            if (rnode != null
                && rnode.size() > 0
                && replace
                && doorsids != null
                && doorsids.containsKey(rnode.iterator().next().getUUId())) {
              desc =
                  desc.substring(0, apos)
                      + doorsids.get(rnode.iterator().next().getUUId())
                      + desc.substring(desc.indexOf("</a>", apos));
              rpos = apos;
            }
          }

          hpos++;
        }
      }
      rpos = desc.indexOf("[", rpos + 1);
    }
    return desc;
  }

  public static String removeTag(String name, String s) {
    int pos = s.indexOf("<" + name);
    while (pos > -1) {
      s = s.substring(0, pos) + s.substring(s.indexOf(">", pos) + 1, s.length());
      pos = s.indexOf("<" + name);
    }
    return s;
  }

  public static String getTrace(Throwable exc) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintWriter pw = new PrintWriter(baos);
    exc.printStackTrace(pw);
    pw.close();
    try {
      baos.close();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    try {
      return baos.toString("UTF-8");
    } catch (UnsupportedEncodingException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return "Incorrect encoding";
  }

  private static java.nio.file.Path dir = null;
  public static java.nio.file.Path getMainDir(URI p) {
    if(p == null)
      return null;
    File f = new File(p);
    File nf = f.toPath().toFile();
    if(!nf.exists())
      nf.mkdirs();
    return nf.toPath();
  }
  public static java.nio.file.Path getCacheDir(URI p) {
    if(p == null)
      return null;
    File f = new File(p);
    File nf = f.toPath().resolve(CACHE_FOLDER_NAME).toFile();
    
    if(!nf.exists())
      nf.mkdirs();
    File fi = f.toPath().resolve(".gitignore").toFile();
    try {
      addIgnore(fi, CACHE_FOLDER_NAME+"/");
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return nf.toPath();
  }
  private static void addIgnore(File fi,String st) throws IOException {
    if(!fi.exists())
      try {
        fi.createNewFile();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    FileReader fr = new FileReader(fi);
    BufferedReader br = new BufferedReader(fr);
    boolean found = false;
    boolean single = false;
    try{
      while(br.ready()){
        String s = br.readLine();
        single = true;
        if(s.equals(st)){
          found = true;
          break;
        }
      }
    }finally{
      br.close();
      fr.close();
    }
    if(!found)
    {
      FileWriter fw = new FileWriter(fi,true);
      BufferedWriter bw = new BufferedWriter(fw);
      try{
        if(single)
          bw.write("\n");
        bw.append(st);
      }
      finally{
        bw.close();
        fw.close();
      }
    }
  }
  public static java.nio.file.Path getTempDir() {
    if(dir == null) {
      synchronized (CoreUtils.class) {
        if(dir!=null)
          return dir;
        try {
          dir = Files.createTempDirectory("requality");
          File df = dir.toFile();
          if(!df.exists())
            df.mkdirs();
          df.deleteOnExit();//remove temp folders after run
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
          @Override
          public void run() {
            delete(dir.toFile(), true, false);
          }
        }));
      }
    }
    return dir;
  }
  public static String getTrace(StackTraceElement[] stackTrace) {
    StringBuilder sb = new StringBuilder();
    for(StackTraceElement s:stackTrace){
      sb.append(s);
      sb.append("\n");
    }
    return sb.toString();
  }
  //Pattern.compile("[_]{0,2}[%]{0,1}[A-Za-z0-9а-яА-Я_[$].,-]+[A-Za-z0-9а-яА-Я,._[$] -]*");
  private static Set<Character> allowedIdChars = new HashSet<>();
  static{
    allowedIdChars.add('_');
    allowedIdChars.add('$');
    allowedIdChars.add(',');
    allowedIdChars.add('.');
    allowedIdChars.add('-');
  }
  
  public static String filterNodeId (String nameOrId) {
    return filterNodeId(nameOrId, Collections.emptySet());
  }
  public static String filterNodeId(String nameOrId, Set<Character> extraAllowed) {
    StringBuilder sb = new StringBuilder();
    for(int i = 0;i<nameOrId.length();i++){
      char c = nameOrId.charAt(i);
      if(Character.isLetterOrDigit(c)||allowedIdChars.contains(c)||Character.isWhitespace(c)||extraAllowed.contains(c))
        sb.append(c);
    }
    return sb.toString();
  }
  public static final String GIT_MERGE_START = "<<<<<<< ",GIT_MERGE_MIDDLE = "=======", GIT_MERGE_END = ">>>>>>> ";
  public static String filterGitJson(String s,boolean first){
    if(s == null)
      return null;
    if(s.isEmpty())
      return s;
    StringBuilder ret = new StringBuilder();
    int ind = s.indexOf(GIT_MERGE_START);
    if(ind == -1)
      return s;
    while(ind>-1){
      int nexteol = s.indexOf("\n",ind+1);
      if(nexteol == -1)
        nexteol = s.length();
      int neq = s.indexOf(GIT_MERGE_MIDDLE,ind+1);
      if(neq>-1){
        int neqn = s.indexOf("\n",neq+1);
        if(neqn == -1)
          neqn = s.length();
          
        int nff = s.indexOf(GIT_MERGE_END,neq+1);
        int nexteol2 = s.indexOf("\n",nff+1);
        if(nexteol2 == -1)
          nexteol2 = s.length();
        if(ind>0)
          ret.append(s.substring(0, ind-1));
        if(first){
          ret.append(s.substring(nexteol, neq));
          ret.append(s.substring(nexteol2+1));
        }else{
          ret.append(s.substring(neqn+1, nff));
          ret.append(s.substring(nexteol2+1));
        }
        ind = s.indexOf(GIT_MERGE_START, ind+1);
      }
    }
    return ret.toString();
  }

  public static java.nio.file.Path getCacheDir(TreeDB proj){// TODO Auto-generated method stub
    return getCacheDir(proj.getStorage().getLocation());
  }
  public static java.nio.file.Path getMainDir(TreeDB db){
    return getMainDir(db.getStorage().getLocation());  
  }
  public static java.nio.file.Path getCacheDir(ITreeStorage s){// TODO Auto-generated method stub
    return getCacheDir(s.getLocation());
  }
  public static RelationsTreeContext gatherRelatedTrees(TreeDB db, IProgressMonitor monitor, int iterations, Set<String> names, boolean addSelf) {
    if(db==null)
      throw new RuntimeException ("null tree during related tree gathering");
    if(names == null)
      names = new HashSet<String>();
    return gatherRelatedTrees(db.getTracker(), db, monitor, iterations, names, addSelf);
  }
  public static RelationsTreeContext gatherRelatedTrees(IResTracker tracker, TreeDB dbfrom, IProgressMonitor monitor, int iterations, Set<String> namesAlt, boolean addSelf) {    
    RelationsTreeContext rtc = new RelationsTreeContext(); 
    LinkedList<String> hndl = new LinkedList<>();
    LinkedList<String> hndled = new LinkedList<>();
    if(namesAlt!=null)
      hndl.addAll(namesAlt);
    if(dbfrom!=null && dbfrom.getProjectName()!=null && !hndl.contains(dbfrom.getProjectName()))
      hndl.add(dbfrom.getProjectName());
    if(dbfrom!=null && dbfrom.getLocalName()!=null && !hndl.contains(dbfrom.getLocalName()))
      hndl.add(dbfrom.getLocalName());
    //rtc.allRelated.add(dbfrom);
    //rtc.allRelatedNames.add(dbfrom.getProjectName());
    Map<String, Integer> deepMap = new HashMap<>();
    for(String s:hndl)
      deepMap.put(s,0);
    TrackerCacheManipulatorsManager cmm = tracker.getCMM(dbfrom);
    CacheStorage cs = cmm.getLinkManipulator().getLCM().getStorage(dbfrom);
    Map<String, List<LateResolveRequest>> map = tracker.getCMM(dbfrom).getLinkManipulator().getLCM().loadLates(tracker.getCPCacheStorage(dbfrom));
    while(!hndl.isEmpty())
    {
      String dbname = hndl.removeFirst();
      Integer deep = deepMap.getOrDefault(dbname, 0);
      TreeDB db = tracker.getTreeDB(dbfrom, dbname, dbname, true, monitor);//TODO IDE monitor?
//      if(db == null) {
//        RLogger.logError("found non-existing tree "+dbname+" during related tree collection on report generation for "+dbfrom!=null?dbfrom.getSemiProjectName():dbname);
//        continue;
//      }
      if(!rtc.allRelatedNames.contains(dbname)){
        String lname = db == null?dbname:db.getLocalName();
        String pname = db == null?dbname:db.getProjectName();
        hndled.add(lname);
        if(!hndled.contains(dbname))
          hndled.add(dbname);
        if(!hndled.contains(pname))
          hndled.add(pname);
        Pair<TreeRelationsCounter, TreeRelationsCounter> rel = collectDBs(cmm, cs, lname,pname, rtc, map);//TODO is dbname correct?
        Set<String> set = Collections.emptySet();
        if(!rel.getFirst().isEmpty())
        {
           set = new HashSet<>();
           set.addAll(rel.getFirst().linksInfo.keySet());
           set.addAll(rel.getFirst().termsLinksInfo.keySet());
           
        }
        if(!rel.getSecond().isEmpty())
        {
           if(set.isEmpty())
             set = new HashSet<>();
           set.addAll(rel.getSecond().linksInfo.keySet());
           set.addAll(rel.getSecond().termsLinksInfo.keySet());
        }
        rtc.addLinksInfo(lname, rel.getFirst(), rel.getSecond());

        set.removeAll(rtc.allRelatedNames);
        if(db!=null && (addSelf || namesAlt == null || !namesAlt.contains(dbname)) && db.getProjectName() != null)//TODO test null pname?
          rtc.allRelated.add(db);
        rtc.allRelatedNames.add(lname);
        rtc.allRelatedNames.add(pname);
        if(db!=null)
          rtc.registerName(DBLocalName.formLocalNameKey(db, lname, db.revision),
            db.getProjectName());
        rtc.addLinksInfo(lname, rel.getFirst(), rel.getSecond());
        for(String s:set) {
          if(!hndl.contains(s) && !hndled.contains(s)) {
            if(iterations==-1 || deep<iterations) {
              deepMap.put(s, deep+1);
            }
            hndl.add(s);
          }
        }
      }
    }
    if(dbfrom!=null && !rtc.allRelatedNames.contains(dbfrom.getProjectName()))
      rtc.allRelatedNames.add(dbfrom.getProjectName());
    return rtc;
  }
  private static Pair<TreeRelationsCounter, TreeRelationsCounter> collectDBs(TrackerCacheManipulatorsManager cmm, CacheStorage cs, String lname, String pname, RelationsTreeContext rtc, Map<String, List<LateResolveRequest>> map) {
    return RelManager.getRelatedProjects(cmm, cs, lname, pname, map);
  }
  private static boolean delete(File f, boolean forceNow,boolean remcache, int depth) {
    return deleteHandler.delete(f,forceNow,remcache,depth);
  }
  public static void delete(File f, boolean forceNow, boolean remcache){
    delete(f, forceNow,remcache, false);
  }
  public static void delete(File f, boolean forceNow, boolean remcache, boolean singleLevel) {
    delete(f, forceNow,remcache, singleLevel?1:-2);
  }
  public static void markDeleteOnExit(File f) {
    if(f.isDirectory())
      for(File c:f.listFiles())
        markDeleteOnExit(c);
    f.deleteOnExit();
  }
  public static String resolveURI(TreeNode node, IReqURIResolver rslv, String descr) {
    //TODO use it in reports
    if(rslv == null || descr == null || rslv == IReqURIResolver.EMPTY)
      return descr;
    StringBuffer ret = new StringBuffer();
    int im = descr.indexOf("<a"), lim = 0; // ,im2=descr.indexOf("<object");
    while (im > -1) {
      ret.append(descr.substring(lim, im));
      int end = descr.indexOf(">", im);
      if (end == -1) break; // not ended one
      int be = end-1;
      while(be>im && Character.isWhitespace(descr.charAt(be))){
        be--;
      }
      boolean nosec = descr.charAt(be) == '/';
      String val = "";
      int endatag = -1;
      if(!nosec){
        endatag = descr.indexOf("</a",end);
        val = descr.substring(end+1, endatag);
      }
      int hndl = im + 2;
      Map<String, Pair<Integer, String>> params = RequalityCLI.gatherParams(descr,hndl,end);
      Pair<Integer, String> par = null;
      Pair<Integer, String> cl=null;
      for(Entry<String, Pair<Integer, String>> pp: params.entrySet()){
        String key = pp.getKey().toLowerCase(); 
        if(key.endsWith(":href")||key.equals("href"))
            par = pp.getValue();
        if(key.endsWith(":class")||key.equals("class"))
          cl = pp.getValue();
      }
      //par = params.get("href");
      {
        char pth = 0;;
        byte state = 0;
        if(par!=null && par.getFirst()>-1){
          String href = par.getSecond().trim();
          if(href.startsWith(REQ_URI_START)){
            href = href.substring(REQ_URI_START.length());
            ReqURIResolveResult rs = rslv.resolve(node, href, val);
            if (rs!=ReqURIResolveResult.NF) {
              ret.append(descr.substring(im, par.getFirst()));
              ret.append(rs.link);
              im = par.getFirst()+par.getSecond().length();
              if(nosec){
                ret.append(descr.substring(im, be));
                ret.append(">");
                ret.append(val);
                //replaced 
                ret.append("</a>");
                im = end+1;
              }else
              {
                
                ret.append(descr.substring(im, end+1));
                ret.append(val);
                //replaced
                //ret.append(rs.value);
                im = endatag;
              }
            } else {
              String cls = null;
              //String pref = detectPrefix(params);
              if(cl != null)
              {
                cls = cl.getSecond() +" notResolved";
              }else{
                cls = "notResolved";
              }
              if(nosec){
                ret.append(descr.substring(im, be));
                ret.append(" ");
//                if(pref!=null){
//                  ret.append(pref);
//                  ret.append(":");
//                }
                ret.append("class = \"");
                ret.append(cls);
                ret.append("\">");
                ret.append(val);
                //replaced 
                ret.append("</a>");
                im = end+1;
              }else
              {
                ret.append(descr.substring(im, end));
                ret.append(" ");
//                if(pref!=null){
//                  ret.append(pref);
//                  ret.append(":");
//                  
//                }
                ret.append("class = \"");
                ret.append(cls+"\">");
                ret.append(val);
                //replaced
                //ret.append(rs.value);
                im = endatag;
              }
              // ret.append(path);
            }
          }
        }
        
        lim = im;
      }
      im = descr.indexOf("<a",end);
    }
    if (lim > -1) ret.append(descr.substring(lim));
    return ret.toString();
  }

  private static String detectPrefix(Map<String, Pair<Integer, String>> params) {
    for(String s:params.keySet()){
      int ind = s.indexOf(":");
      if(ind>-1)
        return s.substring(0, ind);
    }
    return null;
  }
  public static boolean busyCondition(SelectionRecord nodes) {
    for(TreeNode node: nodes.fullSelection) {
      if(node == null)
        RLogger.logDebug(CoreUtils.getTrace(Thread.currentThread().getStackTrace()));
      if(node!=null && busyCondition(node.getTreeDB()))
        return true;
    }
    return false;
  }

  public static boolean busyCondition(TreeDB db) {
    boolean tl = TreeStateManager.getOne(db.getTracker()).getState(db.getLocalName(), 
    		TreeStateManager.getOne(db.getTracker()).getRevisionInfo(db))==TreeState.READY;
    if(db.getCacheStorage().inTransaction() || !tl){
      return true;
    }
    return false;
  }

  public static String getTreeUniqueId(TreeDB db, RevisionInfo ri) {
    return ri!=null?db.getLocalName()+":"+ri.toString():db.getLocalName();
  }

  public static String getFileName(String fid, boolean withext) {
    int lastsl = fid.lastIndexOf("/");
    int s = 0, e = fid.length();
    if(lastsl>-1)
      s = lastsl+1;
    if(!withext){
      int p = fid.lastIndexOf(".");
      if(p>-1)
        e = p;
    }
    return fid.substring(s, e);
  }

  public static String getFileName(java.nio.file.Path fid, boolean withext) {
    int cnt = fid.getNameCount();
    String name =  fid.subpath(cnt-1, cnt).toString();
    if(!withext){
      int p = name.lastIndexOf(".");
      return name.substring(0,p);
    }
    return name;
  }
  public static java.nio.file.Path getJsonPathParentJsonPath(java.nio.file.Path relPath) {
    java.nio.file.Path folderPath = relPath.getParent();
    if(folderPath == null)
      return null;
    String fname = getFileName(folderPath, true);
//    if(fname.indexOf(".")>-1)
//      return folderPath;
    return folderPath.resolveSibling(fname+".json");
  }
  public static String getFileParentFile(String file) {
    int lastsl = file.lastIndexOf("/");
    if(lastsl == -1 || lastsl == 1)// ./ case
      return null;//no parent
    return file.substring(0,lastsl)+".json";
  }
  public static String getRelativePath(FileSystem fs,String s1, String s2) {

    int sim = 0;
    while (sim < s1.length() && sim < s2.length() && s1.charAt(sim) == s2.charAt(sim)) sim++;
    if (sim == 0) {
      for(java.nio.file.Path pa : fs.getRootDirectories())
        if(s2.startsWith(pa.toString().replace(File.separator, "/")))
          return s2;
      while(s1.startsWith("/"))
        s1 = s1.substring(1);
      while(s1.endsWith("/"))
        s1 = s1.substring(0,s1.length()-1);
      StringBuilder sb = new StringBuilder();
      int cnt = 0;
      int i = -2;
      do{
        i = s1.indexOf("/",i+1);
        cnt++;
      }
      while(i<s1.length() && i!=-1);
      for(int j = 0;j<cnt;j++)
        sb.append("../");
      sb.append(s2);
      return sb.toString();
    }else{
    	int ls = s1.substring(0,sim).lastIndexOf("/");
        if (ls != -1 && sim > ls) sim = ls + 1;
    }
    String sims = s1.substring(0, sim);
    s1 = s1.substring(sim);
    s2 = s2.substring(sim);
    for(java.nio.file.Path pa : fs.getRootDirectories()){
      if(s2.startsWith(pa.toString().replace(File.separator, "/")))
        return (sims+s2);//we have to use absolute path;
    }
    if(s2.isEmpty()){

      StringBuilder sb = new StringBuilder();

      while(s1.startsWith("/"))
        s1 = s1.substring(1);
      while(s1.endsWith("/"))
        s1 = s1.substring(0,s1.length()-1);
      int cnt = 0;
      int i = -2;
      do{
        i = s1.indexOf("/",i+1);
        cnt++;
      }
      while(i<s1.length() && i!=-1);
      sb = new StringBuilder();
      for(int j = 0;j<cnt;j++)
        sb.append("../");
      sb.append(s2);
      return sb.toString();
    }
    StringBuilder buf = new StringBuilder("./");
    int cnt = 0;
    int i = -2;
    if(!s1.isEmpty()){
      do{
        i = s1.indexOf("/",i+1);
        buf.append("../");
        cnt++;
      }
      while(i<s1.length() && i!=-1);
    }
    buf.append(s2);
    return buf.toString();
  }
  public static long getTime() {
    return System.nanoTime();
  }
  public static String getCharset(String html) throws IOException {
    return OmnivorousImporter.getCharset(html);
  }
  public static String[] getCommandLineArgs() {
    return CommandLineArgsProvider.cLineArgs;
  }
  private static char octToChar(StringBuilder s) {
    int n=0;
    int m=0;
    byte st=(byte) s.length();
    
    OfInt it =s.chars().iterator();
    m = (s.length()-1)*8; 
    while(it.hasNext()) {
      int i = it.next();
      char c = (char) i;
	  n=(c-'0')*m+n;
	  m-=8;
	  if(m == 0)
		  m = 1;
    }
	return (char)n;
  }
  private static char hexToChar(StringBuilder s) {
    int n=0;
    int m=0;
    byte st=(byte) s.length();
    
    OfInt it =s.chars().iterator();
    m = (s.length()-1)*16; 
    while(it.hasNext()) {
      int i = it.next();
      char c = (char) i;
      if(c>='0'&&c<='9')
    	n=(c-'0')*m+n;
      else {
    	n=(Character.toLowerCase(c)-'a'+10)*m+n;
      }
	  m-=16;
	  if(m == 0)
		  m = 1;
    }
	return (char)n;
  }
  public static String filterOutText(String aval,boolean cropSpaces) {
	StringBuilder sb = new StringBuilder();
	StringBuilder cha = new StringBuilder();
	byte ignore = 0;
	int lvl = 0;
	boolean schar = false;
	for(int i = 0;i<aval.length();i++) {//TODO rewrite
		char c = aval.charAt(i);
		if(ignore==0)
		{
			if(c == '\\') {
				ignore = 1;
			} else
				if(c == '<') {
					lvl++;
				}else
					if(c == '>') {
						lvl--;
					}else
						if(lvl==0) {
							if(!cropSpaces || sb.length()==0 || sb.charAt(sb.length()-1)!=' ' || c!=' ')
								sb.append(c);
						}
		}
		else
			switch(ignore) {
				case 1:{
					if(c == 'x' || c == 'X') {//hex numericals
						ignore = 4;
					}else
					if(Character.isDigit(c)) {
						//zero or octal
						if(c == '0')
							ignore = 3;
						else
							ignore = 4;
						cha.append(c);
					}else {
						i--;
						ignore = 0;
					}
				}break;
//				case 2:{
//					ignore = 0;
//					s
//					i--;
//				}break;
				case 3:{
					if(!Character.isDigit(c)) {
						ignore = 0;
						sb.append((char)0);
						cha.setLength(0);
						i--;
					}else {
						ignore = 4;
						cha.append(c);
					}
				}break;
				case 4:
				case 5:
				case 6:{
					if(ignore< 6 && (c>='0' && c<='9' || Character.toLowerCase(c)>='a'&&Character.toLowerCase(c)<='f')) {
						ignore++;
						cha.append(c);
					}else {
						ignore = 0;
						sb.append(hexToChar(cha));
						cha.setLength(0);
						i--;
					}
				}break;
			}
			if(ignore == 1) {
			}
	}
	if(ignore>0) {

		switch(ignore) {

			case 3:
				sb.append(octToChar(cha));
				break;
			case 4:
			case 5:
			case 6:{
					ignore = 0;
					sb.append(hexToChar(cha));
					cha.setLength(0);
				}break;
		}
	}
	return sb.toString();
  }
  public static String spaceOne(String one) {
	StringBuilder sb = new StringBuilder();
	for(int i = 0;i<one.length();i++) {
		char c = one.charAt(i);
		if(sb.length()==0 || sb.charAt(sb.length()-1)!=' '||c!=' ')
			sb.append(c);
	}
	return sb.toString();
  }

  public static boolean forgetName(File pfile) {
    if(pfile.exists()) {
      File iproj = pfile.toPath().resolve(".project").toFile();
      if(iproj.exists()) {
        Long cha = lchange.remove(iproj);
        return cha!=null && cha!=0;
      }
    }
    return false;
  }  
  //TODO rewrite, used to fake project name for manual rename refactoring

  public static String getProjectName(File ch) {
  	FileInputStream fis = null;
  	File iproj = null;
	try {
	  iproj = ch.toPath().resolve(".project").toFile();
	  long changeTime = lchange.getOrDefault(iproj,0L);
	  if(changeTime==iproj.lastModified()&&iproj.lastModified()!=0 || changeTime == -1)
		return names.get(iproj);
	  else
        lchange.put(iproj, iproj.lastModified());
	  fis = new FileInputStream(iproj);
	} catch (FileNotFoundException e1) {
	  return null;
	}
	String name = null;
  	try{
      XMLInputFactory xif = XMLInputFactory.newInstance();
      xif.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
      xif.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
      xif.setProperty(XMLInputFactory.IS_COALESCING, true);
      XMLEventReader reader = xif.createXMLEventReader(fis);
      int deep = 0;
      byte record = 0;
      StringBuilder sb = new StringBuilder();
      while (reader.hasNext()) {
        XMLEvent event = (XMLEvent) reader.next();
        try {
          if(event.isStartElement()){
          	deep++;
          	StartElement se = event.asStartElement();
          	if(se.getName().getLocalPart().toLowerCase().equals("name")&&deep == 2)
          	  record = 1;
          }else if(event.isEndElement()){
          	deep--;
          	if(record == 1)
          	  record = 2;
          }else if(event.isCharacters()){
          	if(record == 1)
          	  sb.append(event.asCharacters().getData());
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
      if(record == 2) {
    	name = sb.toString();
      	return name;
      }
    } catch (XMLStreamException e) {
      e.printStackTrace();
    } catch (FactoryConfigurationError e) {
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
	}finally{
	  try {
	    if(fis!=null)
		  fis.close();
	  } catch (IOException e) {
        e.printStackTrace();
	  }
	  if(iproj!=null && name!=null)
		names.put(iproj, name);
	}
	return names.get(iproj);
  }
  public static String gatherStoredType(String vtype) {
    int pos = vtype.lastIndexOf(',');
    if(pos>-1)
    	return vtype.substring(pos+1).trim();
    return vtype;
  }
  public static Object[] reworkPath(String url) {
    String uid = url.toString().replace(".html", ".xhtml");
    String mark = null;
    int sch = uid.indexOf("#");
    if (sch > -1) {
      if (uid.indexOf("/", sch) == -1) {
        mark = uid.substring(sch + 1);
        uid = uid.substring(0, sch);
      }
    }
    uid = uid.replace("/./", "/");
    int point = uid.indexOf("../");
    int up = -1;//one for ./resources/.xhtml
    while (point == 0) {
      uid = uid.substring(3);
      point = uid.indexOf("../", point);
      up++;
    }
    while (point > -1) {
      int prep = uid.substring(0, point - 1).lastIndexOf("/");
      if (prep > -1) uid = uid.substring(0, prep) + uid.substring(point + 2);
      point = uid.indexOf("../", point);
    }
    return new Object[] {uid, mark, up};
  }
  public static String getShortTrace() {
    StackTraceElement[]el = Thread.currentThread().getStackTrace();
    if(el.length>3)
      return el[3].toString();
    return el[0].toString();
  }

  private static ScriptEngineManager factory = new ScriptEngineManager();
  private static ScriptEngine engine = null;
  static {
	engine = factory.getEngineByName("JavaScript");
    if(engine == null)
      engine = factory.getEngineByName("Nashorn");
    if(engine == null)
      engine = factory.getEngineByName("GrallVM");
    if(engine == null)
      engine = factory.getEngineByName("ECMAScript");
	if(engine == null)
      engine = factory.getEngineByName("rhino");
  }
  public static ScriptEngine getJSEngine() {
	return engine;
  }
  public static URI getURI(String ret) {
	File f = new File(ret);
	return f.toURI();
  }
  public static String getFIDOrUUID(ReportGenerationContext context, TreeNode node, boolean external) {
    if(node == null)
      return null;
    //TODO inspect usages
    String fid = external?node.getNodeIdForReport(context):node.getForeignID();
    return fid == null ? node.getSUUId() : fid;
  }
  public static String clearURIChars(String s) {
    for(Character f:CoreUtils.repl.keySet())
      s = s.replace(CoreUtils.repl.get(f), Character.toString(f));
    return s;
  }
  public static void stopCacheConnections(File f) {
    File[] files = f.listFiles();
    if(files!=null)
      for(File fa:files)
        stopCacheConnections(fa);
    CacheStorage.closeConnection(f.toURI());
  }
  public static void stopCacheConnections(TreeDB db) {
    stopCacheConnections(getCacheDir(db).toFile());
  }
  public static IProgressMonitor getMonitorOrConsole(TreeDB db) {
    IProgressMonitor mon = db == null? null: db.getMonitor();
    if(mon == null)
      mon = new ConsoleProgressMonitor();
    return mon;
  }
}
