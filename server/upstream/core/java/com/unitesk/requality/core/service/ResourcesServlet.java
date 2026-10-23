package com.unitesk.requality.core.service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletException;
import javax.servlet.ServletResponse;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.eclipse.jetty.server.HttpOutput;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.htmlcleaner.CleanerProperties;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.SimpleHtmlSerializer;
import org.htmlcleaner.TagNode;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.unitesk.requality.core.Consts;
import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.jobs.JobManager;
import com.unitesk.requality.core.jobs.RunnableWithMonitor;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.storage.ITreeStateListener;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.core.tree.TreeState;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.ReportData;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.report.ReportOutProcessor;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.LocationHelper;
import com.unitesk.requality.tool.cli.ConsoleProgressMonitor;

public class ResourcesServlet extends DefaultServlet{
  public enum DocumentKind{MARKUP, REPORT, MODULEEDITOR, UNIEDITOR, REVIEW, RESOURCES, TREE};
  public static InputStream NIS = InputStream.nullInputStream();
  public static final String DOCUMENT = "documents";
  public static final String REPORT = "reports";
  public static final String MODULEEDITOR = "moduleeditor";
  public static final String UNIEDITOR = "unieditor";
  public static final String REVIEW = "review";
  public static final String TREE = "tree";
  public static final String RESOURCES = "resources";
  private static final String RETURN_BACK_BUTTON = "<button id=\"go-back\" onclick=\"javascript:history.back();\">Return back</button>";
  private static final String NO_REPORT_HTML = "<html><head><script type='text/javascript' src='varStorage.js'></script><script type='text/javascript' src='jquery/jquery.js'></script><script type='text/javascript'  src='socketsHandlersReport.js'></script><script type='text/javascript' src='sockets.js'></script><script type='text/javascript'  src='report.js'></script></head><body><h1>Content cannot be loaded!</h1>Resource content cannot be found for resource {res} of node {node}. Please generate new report.<p>"+RETURN_BACK_BUTTON+"</p></body></html>";
  private static final String REPORT_UPDATE_HTML = "<html><head><script type='text/javascript' src='varStorage.js'></script><script type='text/javascript' src='jquery/jquery.js'></script><script type='text/javascript' src='socketsHandlersReport.js'></script><script type='text/javascript' src='sockets.js'></script><script type='text/javascript'  src='report.js'></script></head><body><h1>Report content being processed.</h1><p>Please wait, this page will be updated soon.</p><script language='javascript'>"
    + "setTimeout(() => {\n"
    + "  location.reload();\n"
    + "}, {time});\n"
    + "</script></body></html>";
  private static final String NO_REPORT_TEXT = "Content cannot be loaded! Resource content cannot be loaded for resource {res} of node {node}."+RETURN_BACK_BUTTON;


  protected static final QName SRCNAME = new QName("src");
  protected static final QName HREFNAME = new QName("href");
  protected static final QName DATANAME = new QName("data");
  public static final int BYTES_BUFF_SIZE = 16384;
  public static final String REPORT_FOLDER_NAME = "reports";
  protected static final long RES_LOAD_TIMEOUT = 300000;
  public static Map<DocumentKind, String[]> initScripts = new HashMap<>();
  static {
    initScripts.put(DocumentKind.MARKUP, new String[] { "consts.js", "varStorage.js", "jquery/jquery.js","searchBar.css", "searchBar.js","vakata/dist/themes/default/style.min.css","vakata/dist/jstree.js", "selectDialog.js","rangy/rangy-core.js",
        "rangy/rangy-textrange.js" ,"rangy/rangy-classapplier.js", "rangy/rangy-highlighter.js", "markup.js",
      "socketsHandlersMarkup.js","sockets.js", "selectionchange.js", "edit.js", "menu-edit.js"});
    initScripts.put(DocumentKind.REPORT, new String[] {"*varStorage.js", "jquery/jquery.js","searchBar.css", "searchBar.js","socketsHandlersReport.js","sockets.js","!consts.js", "report.js"});
    initScripts.put(DocumentKind.UNIEDITOR, new String[0]);
    initScripts.put(DocumentKind.MODULEEDITOR, new String[0]);
    initScripts.put(DocumentKind.REVIEW, new String[0]);
  }
  static XMLInputFactory xif;
  static XMLOutputFactory xof;
  static XMLEventFactory eventFactory;
  static {
    try {
      xif = XMLInputFactory.newInstance();
      xif.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
      xif.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
      xif.setProperty(XMLInputFactory.IS_COALESCING, true);
      xof = XMLOutputFactory.newInstance();
      eventFactory = XMLEventFactory.newInstance();
    }catch(Exception r) {
      r.printStackTrace();
    }
    RLogger.logDebug("Resources provider service initiated",3);
  }
  private static ResourcesServlet one = null;

  public static ResourcesServlet getOne(){// TODO Auto-generated method stub
    if(one == null)
      one = new ResourcesServlet();
    return one;
  }
  private static Set<String>fileProjectPaths = null;
  CleanerProperties cp;
  HtmlCleaner hc;
  SimpleHtmlSerializer serializer;
  private ITreeStateListener tsl = new ITreeStateListener() {  //TODO also rename?
  @Override
  public void settingsChanged(TreeDB db) {
  }
  @Override
  public void open(TreeDB db) {
      fileProjectPaths = null;
  }
  @Override
  public void delete(String localName, String projectName, TreeDB db) {
      fileProjectPaths = null;
  }
  
  @Override
  public void close(TreeDB db) {
      fileProjectPaths = null;
  }
  };
  public ResourcesServlet(){
    super();

    cp = new CleanerProperties();
    cp.setCharset(StandardCharsets.UTF_8.toString());
    cp.setAllowHtmlInsideAttributes(true);
    cp.setAllowMultiWordAttributes(true);
    cp.setRecognizeUnicodeChars(true);
    cp.setOmitComments(true);
    cp.setOmitXmlDeclaration(true);
    cp.setNamespacesAware(false);
    serializer = new SimpleHtmlSerializer(cp);
    hc = new HtmlCleaner(cp);
    ResManager.getManager().addListener(Moment.BEFORE, tsl);
  }
  @Override
  public void destroy() {
    super.destroy();
    ResManager.getManager().removeListener(Moment.BEFORE, tsl);
  }
  public static Set<String> getProjectPaths(){
    if(fileProjectPaths==null) {
      fileProjectPaths = new HashSet<>();
      for(File f:ResManager.getManager().getAllResourceProjects())
      {
        try {
          File ptp = f.toPath().toRealPath().toFile();
          String path = ptp.toURI().toString();
          fileProjectPaths.add(path.substring(6));
        } catch (IOException e) {
        }
      }
    }
    return fileProjectPaths;
  }
  private void injectScripts(DocumentKind dc, TreeNode node, XMLEventFactory eventFactory, XMLEventWriter writer) throws XMLStreamException {
    //css
    Attribute rl = eventFactory.createAttribute("rel", "stylesheet");
    Attribute hr = eventFactory.createAttribute("href", "edit.css");
    Attribute[] attrs = new Attribute[] {rl,hr};
    StartElement se = eventFactory.createStartElement("", "", "link", Arrays.asList(attrs).iterator(), null);//TODO is null acceptable?
    writer.add(se);
    writer.add(eventFactory.createEndElement("", "", "link"));
    //TODO loading of scripts set
    for(String script: initScripts.get(dc)) {
      if(script.endsWith("css")) {
        rl = eventFactory.createAttribute("rel", "stylesheet");
        hr = eventFactory.createAttribute("href", script);
        attrs = new Attribute[] {rl,hr};
        se = eventFactory.createStartElement("", "", "link", Arrays.asList(attrs).iterator(), null);//TODO is null acceptable?
        writer.add(se);
        //writer.add(eventFactory.createCharacters("\n"));
        writer.add(eventFactory.createEndElement("", "", "link"));
            
      }else {
        if(script.equalsIgnoreCase("consts.js")) {
          Map<String, Object> consts = getConsts(dc,node);
          if(consts.isEmpty())
            continue;
        }
        attrs = new Attribute[] {eventFactory.createAttribute("type", "text/javascript"),
            eventFactory.createAttribute("defer", "true"),
            eventFactory.createAttribute("src", script)};
        se = eventFactory.createStartElement("", "", "script", Arrays.asList(attrs).iterator(), null);//TODO is null acceptable?
        writer.add(se);
        writer.add(eventFactory.createEndElement("", "", "script"));  
      }
    }
  }
  private static String prid = UUID.randomUUID().toString(); 
  private static ConcurrentLinkedQueue<File> pauseWait = new ConcurrentLinkedQueue<>();
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse rresp) throws ServletException, IOException {
    logLoadingInfo("get resource "+request.getRequestURI());
    String ruri = request.getRequestURI();
    String rbase = request.getServletPath();
    String port = request.getParameter("port");
    String nsp = request.getParameter("noScript");
    boolean notScriptMode = nsp!=null && nsp.equalsIgnoreCase("true");
    //varStorage
    AsyncContext async = null; 
    logLoadingInfo("Loading call on "+request.toString());
    //detect no script mode
    if(rbase.startsWith("noScript")||rbase.startsWith("/noScript"))
      notScriptMode = true;
    else
      if(rbase.startsWith("script")||rbase.startsWith("/script"))
        notScriptMode = false;
    
    int skip = rbase.length();
    if(ruri.charAt(skip) == '/')
        skip++;
    ruri = ruri.substring(skip);
    int ressep = ruri.indexOf("/");
    String category = null;
    if(ressep>-1) {
       category = ruri.substring(0,ressep);
       ruri = ruri.substring(ressep+1);
       ressep = ruri.indexOf("/");
    }
    //detect session, resource name and node
    String session = null;
    //String urlPort = null;
    String resourceName = null;
    String nodeId = null;
    if(ressep>-1) {
      nodeId = ruri.substring(0,ressep);
      ruri = ruri.substring(ressep+1);
      ressep = ruri.indexOf("/");
    }
    List<GenericSocketService> disabledSockets = null;
    try {
      //detect mode (document, report, editor or resource) 
      DocumentKind catKind = detectKindByCategory(category);
      //TODO move tree-related code to separate service
      if(ressep>-1 && catKind != DocumentKind.RESOURCES && catKind != DocumentKind.TREE) {
        session = ruri.substring(0,ressep);
        int pos = session.indexOf("-");
        if(pos>-1) {
          port = session.substring(pos+1);
          session = session.substring(0, pos);
        }
        resourceName = ruri.substring(ressep+1);
      }else
        resourceName = ruri;
      resourceName = CoreUtils.URLDecode(resourceName);
      final String fsession = session;
      String fullNodeId = nodeId;
      String projname = null;
      if(nodeId!=null) {
        int projectsep = nodeId.indexOf("::");
        if(projectsep>-1) {
          projname = nodeId.substring(0,projectsep);
          nodeId = nodeId.substring(projectsep+2);
        }
      }
      UUID uid = null;
      try{
        uid = UUID.fromString(nodeId);
      }
      catch(IllegalArgumentException e) {
      }
      TreeDB db = null;
      TreeNode node = null;
      if(projname!=null)//Project name set, get tree and node
      {
        com.unitesk.requality.core.operation.IProgressMonitor mon = new ConsoleProgressMonitor();//TODO add IDE monitor
        projname = CoreUtils.URLDecode(projname);
        db = ResManager.getManager().getTreeDB(null, projname, projname, true, mon);
        TreeState ts = db.getTracker().getTreeStateManager().getState(db.getLocalName(), null);
        if(!TreeState.readyState(ts) && ts!=TreeState.MODIFYING)
           return;//db is loading
        node = db.getNode(nodeId);
      }
      else//Project name was not set, search node in all projects
      {
        for(TreeDB dba:ResManager.getManager().getTreeDBs()) {
          try {
            TreeNode nodea = dba.getNodeExc(ruri,Path.QID|Path.UV|Path.UUID);//TODO only uuid
            if(nodea!=null) {
              node = nodea;
              db = dba;
              break;
            }
          }
          catch(Exception e) {
          }
        }
      }
      int cnt = 0;
//      while(db.getStorage().getResourcesOutOfSync() && cnt<20)
//        try {
//          logLoadingInfo ("Requesting a resource when resources are tends to be outdated "+resourceName);
//          cnt++;
//          Thread.currentThread().join(100);
//        } catch (InterruptedException e2) {
//          RLogger.logError(e2);
//        }
      DocumentKind dc = detectKind(category,node);
      String mainRes = getMainResource(dc, node);
      if(resourceName == null || resourceName.isEmpty() || resourceName.equals("/")) {
        resourceName = mainRes; 
      }
      resourceName = CoreUtils.URLDecode(resourceName);
      
      if(dc == DocumentKind.TREE) {
        if(db!=null)
          sendGetTreeAnswer(db, resourceName, rresp);
        else
          RLogger.logError("request on non-existing tree "+projname);
        return;
      }
      if(node==null) {
        rresp.setContentType("application/json");
        rresp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        try(PrintWriter pw = rresp.getWriter()){
          pw.println("{ \"status\": \"error\", \"message\":\"Node "+ruri+" not found\"}");
        }
        RLogger.logError("Error loading node "+ruri);
        logLoadingInfo("get resource "+request.getRequestURI()+" NODE NOT FOUND!");
        return;
      }
      
      //if resource requested then return it
      if(dc == DocumentKind.RESOURCES){
        handleResourceRequest(resourceName, rresp, node, request);
        return;
      }
      
      if(resourceName.startsWith(mainRes+"/")&&resourceName.length()>mainRes.length()+1)
        resourceName = resourceName.substring(mainRes.length()+1);
      
      InputStream rdis = null;
      
      File fp = StaticFilePaths.paths.get(resourceName);
      if(fp == null)
        fp = StaticFilePaths.paths.get("/"+resourceName);
      
      //remove trailing and ended slashs
      while(resourceName.endsWith("/"))
        resourceName = resourceName.substring(0,resourceName.length()-1);
      while(resourceName.startsWith("/"))
        resourceName = resourceName.substring(1);
      
      String uResourceName = resourceName.toLowerCase();

      logLoadingInfo("request resource "+resourceName+" "+dc);
      if(resourceName.equals("consts.js")||resourceName.equals("/consts.js")) {
        //consts init for some editors
        logLoadingInfo("request resource(const) "+resourceName+" "+dc);
        outConsts(dc, node, rresp);
        return;
      }else {
        final String resId = resourceName;
        boolean isHTML = (uResourceName.endsWith("htm"))||(uResourceName.endsWith("html"));
        boolean isScriptedDocument = isHTML && dc == DocumentKind.REPORT || mainRes!=null && resId.contains(mainRes);
        boolean isNotScriptedDocument = !isScriptedDocument;
        logLoadingInfo("request resource meta for "+resourceName+": html("+isHTML+"), scripting("+isScriptedDocument+")");
        if(isScriptedDocument)//pause sockets initial timeouts
        {
          disabledSockets  = GenericSocketService.detectSocket(port, fullNodeId);
          for(GenericSocketService sock:disabledSockets) {
            logLoadingInfo("request socket pause on "+resourceName);;
            if(sock!=null && !sock.isDisposed()) 
              sock.pauseDisposeTimer();
          }
        }
        
        boolean ignoreProcessing = (dc == DocumentKind.MARKUP || dc == DocumentKind.REPORT) && notScriptMode;
        logLoadingInfo("ignore processing for "+resourceName+"? :"+ignoreProcessing);
        if(isNotScriptedDocument || ignoreProcessing) {
          rresp.setCharacterEncoding("UTF-8");
          if(handleNonMainResource(fp, node, request, rresp, resId)){
            logLoadingInfo("handles non-main resource "+resourceName);
            disconnectSocketsIfAny(disabledSockets);
            disabledSockets = null;
            return;
          }
         logLoadingInfo("get out of if for non-main resource "+resourceName);
        }
        
        //HttpServletResponse response = (HttpServletResponse) async.getResponse();
        logLoadingInfo("started async for "+resourceName);
        //HttpOutput out = (HttpOutput) response.getOutputStream();
        if(dc == DocumentKind.MARKUP ) {
          rresp.setStatus(202);
          try {
              InputStream dis = node.getResourceContent(resId);
              
              if(dis == null) {
                  logLoadingInfo("no resource content for "+resourceName+", disables async");
                  RLogger.logDebug("Resource loading call causes it not to be found on "+node.getUserVisibleName()+" (resId: "+resId+")",6);
                  rresp.sendError(404, NO_REPORT_HTML.replace("{node}", node.getUserVisibleName())
                          .replace("{res}", resId));
                  disconnectSocketsIfAny(disabledSockets);
                  disabledSockets = null;
                  return;
              }
              async = request.startAsync(request, rresp);
              HttpServletResponse response = (HttpServletResponse) async.getResponse();
              
              rdis = dis;
              String mt = getServletContext().getMimeType(resId);
              response.setContentType(mt);
              response.setStatus(200);
              
              AtomicReference<PipedInputStream> pis = new AtomicReference<PipedInputStream>();
              PipedOutputStream pos = new PipedOutputStream();
              //TODO add more proper parser for html like reports
              XMLEventReader reader = xif.createXMLEventReader(dis,StandardCharsets.UTF_8.name());
              XMLEventWriter writer = xof.createXMLEventWriter(pos,StandardCharsets.UTF_8.name());
              final TreeNode fnode = node;
              
              List<GenericSocketService> socks = disabledSockets;
              disabledSockets = null;//make sockets disabled only after refinement
              
              AtomicBoolean hasReaderEvent = new AtomicBoolean(true);
              HttpOutput out = (HttpOutput) response.getOutputStream();
              logLoadingInfo("markup rework and get "+resourceName);
              setUpOutputStream(out,pos,pis, hasReaderEvent, response, async, dis, socks, reader, writer);
              startRefineMarkup(dc, out,reader,pis,hasReaderEvent, fnode, pos, writer, fsession, port);
              return;
          } catch (XMLStreamException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
          logLoadingInfo("markup loading problem "+resourceName);
          rresp.sendError(500, "markup content loading problem");
        } else {
          AtomicBoolean haltProcessing = new AtomicBoolean(false);
          if(dc == DocumentKind.MODULEEDITOR || dc == DocumentKind.UNIEDITOR || dc == DocumentKind.REVIEW)
          {
            rresp.setStatus(200);
            if(fp!=null) {
              logLoadingInfo("return static content "+resourceName);
              outStaticEditorsFile(node, fp, rresp, null);
              return;
            }else {
              HttpServletResponse response = (HttpServletResponse) rresp;
              response.setStatus(200);
              response.setContentType("text/html; charset=UTF-8");
              logLoadingInfo("return modulehtml content "+resourceName);
              fp = StaticFilePaths.paths.get("moduleeditor.html");//TODO different editors + 404
              List<GenericSocketService> dsockets = disabledSockets;
              disabledSockets = null;
              outStaticEditorsFile(node, fp, response, new Runnable() {
        
                @Override
                public void run() {
                  int status = ((HttpServletResponse)rresp).getStatus();
                  if(dsockets!=null) 
                    unpauseSockets(dsockets);
                }
              });
              return;
            }
          }else {
            String content = null;
            if(dc == DocumentKind.REPORT) {
              //response.setContentType("text/html; charset=UTF-8");
//
//              List<GenericSocketService> dsockets = disabledSockets;
//              disabledSockets = null;
              
              final TreeNode fnode = node;
              
              logLoadingInfo("report start processing "+resourceName);
              
              String fresourceName = resourceName;
              HttpServletResponse bresponse = rresp;
              
              boolean processedFileExists = false;
              File proceedReportFile = null;
              if(!isHTML){//if we gandles non-html resources
                logLoadingInfo("report processing- NON HTML path :"+fresourceName); 
                //Non html related files
                File res = fnode.getResource(resId);
                if (res==null || !res.exists()) {
                  logLoadingInfo("report processing path not found for "+fresourceName);
                  RLogger.logDebug("res not found and connection dropped " + resId, 3);
                  bresponse.sendError(404);
                  disconnectSocketsIfAny(disabledSockets);
                  disabledSockets = null;
                  return;
                } else {
                  File fc = res;
                  bresponse.setStatus(200);
                  bresponse.setContentLengthLong(res.length());
                  String mimetype = getServletContext().getMimeType(fresourceName);
                  
                  logLoadingInfo("report processing - return static content for "+fresourceName);
                  outContent(bresponse, fc, mimetype);
                  return;
                }
              }
              
              try {
                if(!fnode.hasResource(fresourceName)) {
                  content = NO_REPORT_HTML.replace("{node}", fnode.getUserVisibleName())
                            .replace("{res}", resId);//no report content
                  disconnectSocketsIfAny(disabledSockets);
                  disabledSockets = null;
                }else {
                  logLoadingInfo("report processing- isHTML path :"+fresourceName);
                  proceedReportFile = getProcessedReportFile(fnode, resId);
                  processedFileExists = proceedReportFile.exists();
                  java.nio.file.Path tempIdFile = proceedReportFile.getParentFile().toPath()
                      .resolve(proceedReportFile.getName() + "_tempId");
                  logLoadingInfo("report processing path exists for "+fresourceName+": "+processedFileExists);
                  if(!processedFileExists || Files.exists(tempIdFile)) {
                    rresp.setStatus(200);
                    bresponse.setStatus(200);//TODO?
                    if(ReportOutProcessor.isOutdated(tempIdFile, prid.toString())) {
                      StringBuilder upperSeqBuilder = new StringBuilder();
                      String s = request.getRequestURI();
                      int pos = request.getServletPath().length()+1;
                      while(pos>-1) {
                        pos = s.indexOf("/", pos+1);
                        if(pos>-1)
                          upperSeqBuilder.append("../");
                      }
                      String upperStr = upperSeqBuilder.toString();
                      logLoadingInfo("Start processing file " +fresourceName+" on "+fnode.getSUUId());
                      if(fnode.getType().equals(ReportData.TYPE_NAME)) {
                        ReportData rd = (ReportData) fnode;
                        for(String resName:rd.listResources()) {
                          if(resName.equals(fresourceName))
                            continue;
                          String lowName = resName.toLowerCase();
                          if(lowName.endsWith("htm") || lowName.endsWith("html")){
                            proceedReportFile = getProcessedReportFile(fnode, resName);
                            if(!proceedReportFile.exists())
                              ReportOutProcessor.shelduleReportUpdate(fnode, resName, null, null, prid.toString(), null);
                          }
                        }
                      }
                      ReportOutProcessor.shelduleReportUpdate(fnode, fresourceName, null, upperStr, prid.toString(), port);
                    }
                    long time = calcTime(fnode, fresourceName);
                    System.err.println("calc time for res "+fresourceName+" "+time);
                    content = REPORT_UPDATE_HTML.replace("{node}", fnode.getUserVisibleName())
                            .replace("{res}", resId).replace("{time}", Long.toString(time));
                  }
                  else{
                    content = null;//needed?
                    rresp.setStatus(200);
                    bresponse.setStatus(200);//TODO?
                    String mimetype = getServletContext().getMimeType(proceedReportFile.getName());
                    outContent(bresponse, proceedReportFile, mimetype);
                  }
                }
                if(content!=null) {
                  String fcontent = content;
                  bresponse.setStatus(200);//TODO?            
                  try{
                    outContent(bresponse,
                      new AtomicReference<>(()->{return new ByteArrayInputStream(fcontent.getBytes(StandardCharsets.UTF_8));}),
                      resId, -1, ()->{return true;});
                  } catch (Exception e) {
                    RLogger.logError(e);
                  }
                }
              } finally {
              }
            }
          }
        }
      }
    }
    finally{
      if(disabledSockets!=null){
        unpauseSockets(disabledSockets);
        logLoadingInfo("disables in main thread socket pause for "+resourceName);
      }
      logLoadingInfo("state for initial request on "+resourceName+" state:"+rresp.getStatus()+", type:"+rresp.getContentType());
    }
  }
  
  private long calcTime(TreeNode fnode,String fresourceName){// TODO Auto-generated method stub
    File f = fnode.getResource(fresourceName);
    try {
      return Math.max(Files.size(f.toPath())/5000, 1000);
    } catch (IOException e){// TODO Auto-generated catch block
      e.printStackTrace();
    }
    return 2000;
}
  protected static void outContent(HttpServletResponse response, File proceedReportFile, String mime) {
    if(mime!=null) 
      response.setContentType(mime);
    try {
      response.setContentLengthLong(Files.size(proceedReportFile.toPath()));
    } catch (IOException e1){// TODO Auto-generated catch block
      RLogger.logError(e1);
    }
    try{
      outContent(response, new AtomicReference<>(createResultFileStreamProvider(proceedReportFile)), proceedReportFile.getName(), -1, ()->{return true;});
    } catch (Exception e) {
      RLogger.logError(e);
    }
  }
  
  protected static void outContent(HttpServletResponse response, AtomicReference<Supplier<InputStream>> fca, String filename, long fileLen, Supplier<Boolean> overState) throws UnsupportedEncodingException {
    response.setHeader("Content-Disposition", "inline; filename=\"" +CoreUtils.URLStrictEncodePart(filename)+"\"");
    if(fileLen!=-1)
      response.setContentLengthLong(fileLen);
    response.setCharacterEncoding("UTF-8");
    response.setBufferSize(BYTES_BUFF_SIZE);  
    response.setStatus(202);//loading parts
    try(OutputStream os = response.getOutputStream()){
      boolean over = false;
      Supplier<InputStream> fss = null;
      int pos = 0;
      int iter = 0;
      ByteBuffer buf = ByteBuffer.allocate(BYTES_BUFF_SIZE);
      while(!over || !overState.get()) {
        iter++;
        over = false;
        fss = fca.get();
        if(fss == null)
          return;
        try(InputStream fs = fss.get()){
          ReadableByteChannel fc = null;
          if(fs instanceof FileInputStream) {
            FileInputStream fis = (FileInputStream) fs;
            if(pos>0)
              fis.skip(pos);
            fc = fis.getChannel();
          } else if (fs == NIS) {//do nothing?
            over = true;
            try {
             Thread.currentThread().join(200);
            } catch (InterruptedException ignored) {
            }
            continue;
          } else {
            fc = Channels.newChannel(fs);
          }
          try {
            int bpos = 0;
            try {
              long read = 2;
              while(read!=-1){
                if(fc.isOpen()) {
                  read = fc.read(buf);
                  if(read>-1)
                    pos += read;
                }else
                  read = -1;
                if(buf.position()>bpos) {
                  os.write(buf.array(), bpos, buf.position());
                  os.flush();
                  buf.clear();
                }
              }
              if(buf.position()>bpos)
                os.write(buf.array(), bpos, buf.position());
              buf.clear();
            }finally {
              os.flush();//TODO pos and reopen
            }
            over = true;
          }finally {
            if(fc!=null)
              fc.close();
          }
        }
      } 
      response.setStatus(200);
    } catch (IOException e) {
      RLogger.logDebug(CoreUtils.getTrace(e));
    }
  }
  protected void disconnectSocketsIfAny(List<GenericSocketService> dsockets) {
    if(dsockets == null)
      return;
    for(GenericSocketService s:dsockets)
      s.setDisconnectedFromView(true);//keep message openned
  }
  private boolean handleNonMainResource(File fp, TreeNode node, HttpServletRequest request, HttpServletResponse resp, String fresId) throws IOException {
    String mimetype = getServletContext().getMimeType(fresId);
    if(fp!=null) {
      resp.setStatus(200);
      outContent(resp, fp, mimetype);
      return true;
    }else {
      File res = node.getResource(fresId);      
      if(res!=null) {
        resp.setContentLengthLong(res.length());
        resp.setStatus(200);
        logLoadingInfo("get resource content "+fresId+" - mime: "+mimetype);
          //resp.setContentType(getServletContext().getMimeType(resId));
        outContent(resp, res, mimetype);
        return true;
      }
    }
    resp.sendError(404);
    return true;
  }
  private void outConsts(DocumentKind dc, TreeNode node, HttpServletResponse resp) throws IOException {
    StringBuilder sb = new StringBuilder();
      Map<String, Object> consts = getConsts(dc,node);
      for(Entry<String, Object> en:consts.entrySet())
      {
        sb.append(en.getKey());
        sb.append("=");
        sb.append(en.getValue());
        sb.append(";");
        sb.append("\n");
      }
      HttpOutput out = null;
      try{
        out = (HttpOutput) resp.getOutputStream();
        resp.setStatus(200);
        resp.setContentType("text/javascript");
        try(OutputStream os = resp.getOutputStream()){
          os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
          os.flush();
        }catch(Exception e) {
          RLogger.logError(e);
        }
      }finally {
        if(out!=null)
          out.close();
      }
  }
  private void handleResourceRequest(String resId, ServletResponse resp, TreeNode node, HttpServletRequest request) throws IOException {

    if(resId.startsWith("/"))
      resId = resId.substring(1);
    String mime = getServletContext().getMimeType(resId);
    File res = node.getResource(resId);
    HttpServletResponse rresp = (HttpServletResponse) resp;
    if(res!=null && res.exists()){
      rresp.setStatus(200);
      outContent(rresp, res, mime);
    }else {
      rresp.setStatus(400);
      RLogger.logDebug("Resources cannot be get "+resId);
    }
    logLoadingInfo("get resource "+request.getRequestURI()+" - resources branch, success");
  }
  private void logLoadingInfo(String string) {
    if(CoreUtils.DEBUG_LEVEL>10)//TODO stay it for release?
      RLogger.logDebug(string, 11);
  
  }
  
  private boolean outStaticEditorsFile(TreeNode node, File fp, HttpServletResponse response, Runnable actionsAfter) throws IOException {
    if(fp.exists()){
      try{
        String mimetype = getServletContext().getMimeType(fp.getName());//TODO test?
        outContent(response, fp, mimetype);
      }finally{
        if(actionsAfter!=null)
          actionsAfter.run();
      }
      return true;
    }
    response.sendError(404, NO_REPORT_HTML.replace("{node}", node.getUserVisibleName())
            .replace("{res}", fp.toPath().toString()));
    if(actionsAfter!=null)
      actionsAfter.run();
    return true;
  }
  private void setUpOutputStream(HttpOutput out, PipedOutputStream pos, AtomicReference<PipedInputStream> pis,
      AtomicBoolean hasReaderEvent, HttpServletResponse response, AsyncContext async,
      InputStream dis, List<GenericSocketService> socks, XMLEventReader reader, XMLEventWriter writer) {
    AtomicBoolean stopProceed = new AtomicBoolean(false);
    async.addListener(new AsyncListener() {
      @Override
      public void onComplete(AsyncEvent arg0) throws IOException {
      }

      @Override
      public void onError(AsyncEvent arg0) throws IOException {
        stopProceed.set(true);
      }

      @Override
      public void onStartAsync(AsyncEvent arg0) throws IOException {
      }

      @Override
      public void onTimeout(AsyncEvent arg0) throws IOException {
        stopProceed.set(true);
      }
    });        
    out.setWriteListener(new WriteListener() {
      @Override
      public void onWritePossible() throws IOException {
        PipedInputStream pisv = new PipedInputStream();
        pos.connect(pisv);
        pis.set(pisv);
        try {
          byte[] bytes = new byte[BYTES_BUFF_SIZE];
          while (!out.isClosed()) {
            while(!out.isReady())
              try {
                Thread.currentThread().join(50);
              } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
              }
          //lbais = bais.get();
            int read = pisv.read(bytes,0,BYTES_BUFF_SIZE);
            if(read>0)
              out.write(bytes,0,read);
            try{
              if(!hasReaderEvent.get()&&read==-1||out.isClosed()) {
                try {
                  stopProceed.set(true);
                  response.setStatus(200);
                  async.complete();
                  dis.close();
                  try {
                    writer.close();
                    reader.close();
                  } catch (XMLStreamException e) {
                    e.printStackTrace();
                  }
                  out.close();
                  pisv.close();
                  pos.close();
                  return;
                }finally {
                  unpauseSockets(socks);
                }
              }
            }catch(IOException e){
              RLogger.logDebug(CoreUtils.getTrace(e));
            }
          }
        }finally {
          
        }
      }
      
      
      @Override
      public void onError(Throwable arg0) {
        RLogger.logError((Exception)arg0);
        getServletContext().log("Async Error", arg0);
        if(!stopProceed.get()) {
          PipedInputStream pisv = pis.get();
          stopProceed.set(true);
          try {
            response.setStatus(200);
          }catch(Exception e) {
            RLogger.logError(e);
          }
          try {
            async.complete();
          }catch(Exception e) {
            RLogger.logError(e);
          }
          try {
            dis.close();
          } catch (IOException e1) {
            RLogger.logDebug(CoreUtils.getTrace(e1));
          }
          try {
            writer.close();
            reader.close();
          } catch (XMLStreamException e) {
          }
          try {
            out.close();
          } catch (Exception e1) {
            RLogger.logDebug(CoreUtils.getTrace(e1));
          }
          if(pisv!=null)
            try {
              pisv.close();
            } catch (IOException e) {
              RLogger.logDebug(CoreUtils.getTrace(e));
            }
          try {
            pos.close();
          } catch (IOException e) {
            RLogger.logDebug(CoreUtils.getTrace(e));
          }
          unpauseSockets(socks);
        }
      }
    });
  }
  private void startRefineMarkup(DocumentKind dc,HttpOutput out,XMLEventReader reader, AtomicReference<PipedInputStream> pis,
      AtomicBoolean hasReaderEvent, TreeNode fnode, PipedOutputStream pos, XMLEventWriter writer, String fsession, String wsport) {
    AtomicBoolean injected = new AtomicBoolean(false);
    AtomicBoolean startedHead = new AtomicBoolean(false);
    AtomicBoolean wasHead = new AtomicBoolean(false);
    JobManager.jm.startBaseJob("refine markup doc "+reader, new RunnableWithMonitor() {
      private boolean waspis = true;
      @Override
      public void run(IProgressMonitor pm) {
        hasReaderEvent.set(true);
        try {
          int cnt = 0;
          while(!out.isClosed() && reader.hasNext()) {
            if(pis.get() == null && cnt<100) {
              if(waspis) {
                waspis = false;
                RLogger.logDebug("input stream not ready, wait for it "+pis, 3);
              }
              try {
                Thread.currentThread().join(100);
              } catch (InterruptedException e) {
                RLogger.logError(e);
              }
              continue;
            }
            if(!waspis) {
                waspis = true;
                RLogger.logDebug("resource was found! wait completed! "+pis, 3);
            }
            
            if(out.isClosed()) {
              hasReaderEvent.set(false);//close events
              return;
            }else
              hasReaderEvent.set(reader.hasNext());
            if(hasReaderEvent.get())
              try {
                XMLEvent xe = reader.nextEvent();
                boolean ignore = false, addClass = false;
                if(xe.isStartElement()) {
                  StartElement se = xe.asStartElement();
                  String name = se.getName().getLocalPart().toLowerCase();
                  if(name.equalsIgnoreCase("head")) {
                    startedHead.set(true);
                    wasHead.set(true);
                  }else if(name.equalsIgnoreCase("script") && startedHead.get() && !injected.get()) {
                    injected.set(true);
                    injectScripts(dc, fnode, eventFactory, writer);
                  }
                  else if(name.equalsIgnoreCase("body")) {
                    if(!wasHead.get())
                    {
                      injected.set(true);
                      wasHead.set(true);
                      //no head case
                      writer.add(eventFactory.createStartElement("", "", "head"));
                      injectScripts(dc, fnode, eventFactory, writer);
                      writer.add(eventFactory.createEndElement("", "", "head"));
                    }
                    addClass = true;
                  }
                  else if(name.equalsIgnoreCase("frame")) {//handle framed pages
                    Attribute src = se.getAttributeByName(SRCNAME);
                    if(src!=null && src.getValue().length()>0) {
                      TreeNode doca = findNodeByPath(fnode, src.getValue());
                      if(doca==null)
                        doca = findNodeByPath(fnode, src.getValue());
                      if(doca!=null && doca.getType().equals(Document.TYPE_NAME)) {
                      String reworkSrc = ResourcesProvider.getOne().formDocumentURL((Document)doca, fsession, Integer.parseInt(wsport)).toString();
                      ignore = true;//TODO add params?
                      LinkedList<Attribute> la = new LinkedList<>();
                      Iterator<Attribute> ia = se.getAttributes();
                      while(ia.hasNext()) {
                        Attribute a = ia.next();
                        if(!a.getName().getLocalPart().equalsIgnoreCase("src")) {
                          la.add(a);
                        }else
                          la.add(eventFactory.createAttribute("src",
                                                              reworkSrc));
                      }
                      writer.add(eventFactory.createStartElement(se.getName(),la.iterator(), se.getNamespaces()));
                    }
                  }
                }else if (name.equalsIgnoreCase("a")) {
                  Attribute src = se.getAttributeByName(HREFNAME);
                  if(src!=null && src.getValue().length()>0) {
                    TreeNode doca = findNodeByPath(fnode, src.getValue());
                    String resName = null;
                    if(doca==null) {
                      doca = findNodeByPath(fnode, src.getValue());
                    }
                    java.nio.file.Path docFile = fnode.getNodeResourcesFile().toPath().resolve(src.getValue());
                    if(Files.exists(docFile)) {
                      //detect node by res path
                       TreeNode root = fnode.getTreeDB().getRootNode();
                       URI rootLoc = LocationHelper.getLocation(root);
                       java.nio.file.Path projPath = new File(rootLoc).toPath().getParent().relativize(docFile);
                       String rootPath = projPath.toString();
                       if(rootPath.startsWith("root/"))
                         doca = findNodeByPath(root, rootPath.substring(5));
                    }
                    if(doca!=null && doca.getType().equals(Document.TYPE_NAME)) {
                      String reworkSrc = ResourcesProvider.getOne().formDocumentURL((Document)doca, fsession, Integer.parseInt(wsport)).toString();
                      ignore = true;//TODO add params?
                      LinkedList<Attribute> la = new LinkedList<>();
                      Iterator<Attribute> ia = se.getAttributes();
                      while(ia.hasNext()) {
                        Attribute a = ia.next();
                        if(!a.getName().getLocalPart().equalsIgnoreCase("href")) {
                          la.add(a);
                        }else
                          la.add(eventFactory.createAttribute("href",
                                                              reworkSrc));
                      }
                      writer.add(eventFactory.createStartElement(se.getName(),la.iterator(), se.getNamespaces()));
                    }
                  }
                }
              }
              if(xe.isEndElement()) {
                EndElement ee = xe.asEndElement();
                String name = ee.getName().getLocalPart().toLowerCase();
                if(name.equalsIgnoreCase("head") && !injected.get())  {
                  wasHead.set(true);
                  injectScripts(dc, fnode, eventFactory, writer);
                  injected.set(true);
                }
              }
              if(!ignore) {
                if(addClass) {
                  LinkedList<Attribute> la = new LinkedList<>();
                  Iterator<Attribute> ia = xe.asStartElement().getAttributes();
                  boolean was = false;
                  while(ia.hasNext()) {
                    Attribute a = ia.next();
                    if(!a.getName().getLocalPart().equalsIgnoreCase("class")) 
                      la.add(a);
                    else{
                      la.add(eventFactory.createAttribute("id",
                                                          "html_container"));
                      was = true;
                    }
                  }
                  if(!was)
                    la.add(eventFactory.createAttribute("id",
                                                        "html_container"));
                    writer.add(eventFactory.createStartElement(
                                          xe.asStartElement().getName(),la.iterator(),
                                          xe.asStartElement().getNamespaces()));
                  }else
                    writer.add(xe);
                }
              } catch (XMLStreamException e) {
                throw e;
              }
          }
        }catch(Exception e) {
          RLogger.logError("Exception occure durung handling of document "+fnode.getUserVisibleName());
          RLogger.logDebug(e,3);
        }finally {
          hasReaderEvent.set(false);
          try {
            pos.close();
          } catch (IOException e) {
            RLogger.logDebug(CoreUtils.getTrace(e));
          }
        }
      }
    }, false);
  }
  private static Supplier<InputStream> createResultFileStreamProvider(File resultFile){// TODO Auto-generated method stub
    return ()->{
      try {
        return new FileInputStream(resultFile);
      }catch(IOException e) {
        RLogger.logError(e);
      }
      return null;
    };
  }
  public static File getProcessedReportFile(TreeNode fnode, String resId) {
    java.nio.file.Path c = CoreUtils.getCacheDir(fnode.getTreeDB());
    if(c == null)
      return null;
    String date = fnode.attrs().getStringAttributeValue(ReportData.ATTR_DATE);
    if(date == null)
      date = "";
    else
      date = date.replace(" ", "_").replace(":", "_");
    c = c.resolve(REPORT_FOLDER_NAME).resolve(fnode.getSUUId()).resolve(date);
    File f = c.resolve(resId).toFile();
    f.getParentFile().mkdirs();
    return f;
  }
  protected void unpauseSockets(List<GenericSocketService> socks) {
    for(GenericSocketService sock:socks)
      if(sock!=null) 
        sock.unpauseDisposeTimer();
  }
  private void sendGetTreeAnswer(TreeDB db, String resId, HttpServletResponse response) throws IOException {
    if(db == null) {
      response.setContentType("application/json");
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      try(PrintWriter writer = response.getWriter()){
        writer.println("{\"uuid\":\"0\",\"text\":\"project not found!\"}");
      }
      return;
    }
    Set<String> types = detectTypes(resId);
    String filter = detectFilter(resId);
    TreeNode root = db.getRootNode();
    LinkedList<TreeNode> toh = new LinkedList<>(); 
    toh.add(root);
    JSONArray ret = new JSONArray();
    boolean rootSkip = false;
    Map<TreeNode, JSONObject> parents = new HashMap<>();
    while(!toh.isEmpty()) {
      TreeNode tn = toh.removeFirst();
      String type = tn.getType();
      if(!types.contains(type)) {
        if(!rootSkip)
        {
          rootSkip = true;
          toh.addAll(tn.getSortedShownChildren());
        }
      }else {
        JSONArray childCont = null;
        JSONObject par = parents.get(tn);
        if(par==null)
          childCont = ret;
        else {
         childCont = (JSONArray) par.get("children");
         if(childCont == null) {
           childCont = new JSONArray();
           par.put("children", childCont);
         }
        }
        JSONObject no = new JSONObject();
        no.put("uuid", tn.getSUUId());
        no.put("text", tn.getLabel());
        no.put("type", tn.getType());
        no.put("icon", "../images/nodetypes/"+tn.getType()+".png");
        childCont.add(no);
        for(TreeNode chi: tn.getSortedShownChildren())
        {
          parents.put(chi, no);
          toh.add(chi);
        }
      }
    }
    response.setContentType("application/json");
    response.setStatus(HttpServletResponse.SC_OK);
    try(PrintWriter pw = response.getWriter()){
      pw.println(ret.toString());
    }
  }
  private String detectFilter(String resId) {
    return "*";//TODO implement
  }
  private static Set<String> DEFAULT_TREE_TYPES = new HashSet<>();
  static {
    DEFAULT_TREE_TYPES.add(Requirement.TYPE_NAME);
    DEFAULT_TREE_TYPES.add(com.unitesk.requality.nodetype.TextNode.TYPE_NAME);
  }
  private Set<String> detectTypes(String resId) {
    System.out.println(resId);
    return DEFAULT_TREE_TYPES;
  }
  private void getTagsByName(Set<TagNode> set, String name, TagNode d, int i) {
    if(d.getName().equalsIgnoreCase(name))
      set.add(d);
    if(i==0)
      return;
    for(TagNode n:d.getChildren())
      getTagsByName(set, name, n, i==-1?-1:i-1);
  }
  private TagNode findTag(String tagName, TagNode d, int deep) {
    if(d.getName().equalsIgnoreCase(tagName))
      return d;
    if(deep<=0)
      return null;
    List<TagNode> list = d.getChildren();
    for(TagNode n:list)
      if(n.getName().equalsIgnoreCase(tagName))
        return n;
      else {
        TagNode na = findTag(tagName, n, deep-1);
        if(na!=null)
          return na;
      }
    return null;
  }
  private Map<String, Object> getConsts(DocumentKind dc, TreeNode node) {
    if(dc == DocumentKind.MODULEEDITOR || dc == DocumentKind.REVIEW || dc == DocumentKind.UNIEDITOR) {
      Map<String, Object> ret = new HashMap<>();
      ret.put("debug_level", CoreUtils.DEBUG_LEVEL);
      String names = null;
      boolean ro = false;
      String consts = null;
      switch(dc) {
        case MODULEEDITOR:
          consts = String.join("','",Consts.MODULEEDITOR);
          names=consts.isEmpty()?"[]":"['"+consts+"']";
          break;
        case REVIEW:
          consts = String.join("','",Consts.REVIEW_CONSTS);
          names=consts.isEmpty()?"[]":"['"+consts+"']";
          ro = true;
          break;
        case UNIEDITOR:
          consts = String.join("','",Consts.UNIEDITOR);
          names=consts.isEmpty()?"[]":"['"+consts+"']";
          break;
      }
      ret.put("extraAttrsNames", names);
      ret.put("startNode", "'"+node.getSUUId()+"'");
      ret.put("selectedNode","null");//TODO add update
      ret.put("readOnlyMode", ro);
      return ret;
    }else if (dc == DocumentKind.REPORT) {
      Map<String ,String> ret = new HashMap<>();
      ret.put("window.inited", "true");//mark sockets can be inited
    }
    return Collections.emptyMap();
  }
  private String getMainResource(DocumentKind dc, TreeNode node) {
  if(node instanceof Document) {
    return ((Document)node).getResourceName();
  }
  if(dc == DocumentKind.REPORT)
    return ((ReportData)node).attrs().getStringAttributeValue(ReportData.ATTR_LAST_GENERATED);
  if(dc == DocumentKind.MODULEEDITOR)
    return "moduleeditor.html";
  if(dc == DocumentKind.UNIEDITOR || dc == DocumentKind.REVIEW)
    return "unieditor.html";

  return "";
  }
  private DocumentKind detectKindByCategory(String category) {
    if(category!=null) {
      if(category.equals(DOCUMENT))
        return DocumentKind.MARKUP;
      if(category.equals(REPORT))
        return DocumentKind.REPORT;
      if(category.equals(MODULEEDITOR))
        return DocumentKind.MODULEEDITOR;
      if(category.equals(UNIEDITOR))
        return DocumentKind.UNIEDITOR;
      if(category.equals(REVIEW))
        return DocumentKind.REVIEW;
      if(category.equals(RESOURCES))
        return DocumentKind.RESOURCES;
      if(category.equals(TREE))
        return DocumentKind.TREE;
    }
    return null;
  }
  private DocumentKind detectKind(String category, TreeNode node) {
    DocumentKind dk = detectKindByCategory(category);
    if(dk != null)
      return dk;
    switch(node.getType()) {
      case Document.TYPE_NAME:
        return DocumentKind.MARKUP;
      case ReportData.TYPE_NAME:
        return DocumentKind.REPORT;
    }
    return DocumentKind.UNIEDITOR;
  }
  private static TreeNode findNodeByPath(TreeNode doc, String path) {
    String puid = doc.getQualifiedId();
    puid = puid.substring(0, puid.lastIndexOf("/"));
   
    String url = path;
    Object[] rework = CoreUtils.reworkPath(url);
    String rpath = (String) rework[0];
    int up = (int) rework[2];
    TreeNode node = doc.getTreeDB().getNode(getNodePathFromXHTML(rpath), Path.QID | Path.UV | Path.UUID);
    if (node == null) {
      while(up>0) {
        up--;
        int pos = puid.lastIndexOf("/");
        if(pos == -1)
          RLogger.logError("incorrect path call(tends to be outside of project)");
        puid = puid.substring(0,pos);
      }
      rework = CoreUtils.reworkPath(puid + "/" + rework[0]);
      node = doc.getTreeDB().getNode(getNodePathFromXHTML((String)rework[0]), Path.QID | Path.UV | Path.UUID);
    }
    return node;
  }
  private static String getNodePathFromXHTML(String sb) {
    String s = sb;
    int lastSl = s.lastIndexOf("/");
    if(lastSl==-1)
      return sb;
    String name = s.substring(lastSl+1);
    s = s.substring(0, lastSl);
    lastSl = s.lastIndexOf("/");
    if(lastSl == -1)
      return sb;
    String test = s.substring(lastSl+1);
    if(!test.endsWith(ResManager.RESOURCES_SUFFIX))
      return sb;
    return  s.substring(0,lastSl+1)+name;
  }
  public String getId(){// TODO Auto-generated method stub
    return prid.toString();
  }

}
