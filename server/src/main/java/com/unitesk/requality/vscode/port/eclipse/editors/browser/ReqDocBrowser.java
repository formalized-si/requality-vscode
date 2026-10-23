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

package com.unitesk.requality.vscode.port.eclipse.editors.browser;

import java.io.File;
import java.net.URI;
import java.nio.file.FileSystem;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import com.unitesk.requality.vscode.ui.swt.SWT;
import com.unitesk.requality.vscode.ui.swt.widgets.Browser;
import com.unitesk.requality.vscode.ui.swt.browser.LocationListener;
import com.unitesk.requality.vscode.ui.swt.browser.ProgressListener;
import com.unitesk.requality.vscode.ui.swt.events.KeyEvent;
import com.unitesk.requality.vscode.ui.swt.events.KeyListener;
import com.unitesk.requality.vscode.ui.swt.events.MouseListener;
import com.unitesk.requality.vscode.ui.swt.layout.FillLayout;
import com.unitesk.requality.vscode.ui.swt.widgets.Composite;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Path;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.selection.FSId;
import com.unitesk.requality.core.service.DocumentsSocketService;
import com.unitesk.requality.core.service.WSSocket;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.document.BrowserState;
import com.unitesk.requality.document.ILinkedStateProvider;
import com.unitesk.requality.document.ILocationChangedHandler;
import com.unitesk.requality.vscode.port.eclipse.ITreeDBProvider;
import com.unitesk.requality.vscode.port.eclipse.RequalityPlugin;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.Location;
import com.unitesk.requality.tool.CoreUtils;

public class ReqDocBrowser extends Composite {
  private static final String NO_SOCKET_INITED = "<html><body><h3>Socket for markup browser is not inited</h3></body></html>";
  private Browser browserComponent;
  private boolean commandRuning = false;
  private List<IJSCommand> jsCommands = new ArrayList<>();
  public MouseListener mouseListener = null;
  protected DocumentsSocketService docservice;
  protected Consumer<Document> openEditorAndChangeFocus = null;
  private int pausedSocketServiceCount = 0;
  private boolean inited = false;
  private boolean loadingStarted = false;
  
  @Override
  public void addMouseListener(MouseListener lis){
    this.mouseListener = lis;
  }
  
  private File detectFile(String loc) {
    URI uri = URI.create(loc);
    String sch = uri.getScheme().toLowerCase();
    if(sch.equals("file")) {
        return new File(uri);   
    }
    if(sch.startsWith("http")){
        String host = uri.getHost().toLowerCase();
        if(host.equals("localhost") || host.equals("127.0.0.1")){//TODO mb change 
            String path = uri.getPath();
            int s = 0, e = path.length();
            if(path.startsWith("/"))
                s = 1;
            if(path.endsWith("/"))
                e--;
            path = path.substring(s,e);
            String[]comps = path.split("/");
            if(comps.length>0 && comps[1].toLowerCase().equals("documents") && docservice!=null){
                TreeDB db = docservice.getTreeDB();
                //1 is now random part
                String doc = comps[2];
                String proj = null;
                int sep = doc.indexOf("::");
                if(sep>-1){
                    proj = doc.substring(0,sep);
                    doc = doc.substring(sep+2);
                }
                Document doca = null;
                if(proj == null)
                {
                    for(TreeDB dba:ResManager.getManager().getTreeDBs()){
                        try{
                            TreeNode tnode = dba.getNodeExc(doc, Path.QID|Path.UUID|Path.UV);
                            if(tnode!=null){
                                doca = (Document) tnode;
                                break;
                            }
                        }catch(Exception exa){
                        }
                    }
                }else{
                    com.unitesk.requality.core.operation.IProgressMonitor mon = db.getMonitor();//TODO add IDE monitor
                    db = ResManager.getManager().getTreeDB(null, proj, proj, true, mon);
                    TreeNode tnode = db.getNodeExc(doc, Path.QID|Path.UUID|Path.UV);
                    if(tnode!=null)
                        doca = (Document) tnode;
                }
                //doca is base document, lets move to relative one (or to local resource)
                if(doca!=null)
                {
                    String res = null;
                    if(comps.length>4){
                      StringBuilder sideDocPath = new StringBuilder("../");//out of resources folders
                      StringBuilder localResourcePath = new StringBuilder();
                      for(int i = 4;i<comps.length;i++){
                        if(sideDocPath.length()>3)
                          sideDocPath.append("/");
                        if(i == comps.length-1){
                          String htmlName = comps[i];
                          String lowerHtmlName = htmlName.toLowerCase();
                          if(lowerHtmlName.endsWith("xhtml")) {
                          }else if(lowerHtmlName.endsWith("html")){
                            htmlName = htmlName.substring(0,htmlName.length()-4)+"xhtml";
                          } else if (lowerHtmlName.endsWith("htm")){
                            htmlName = htmlName.substring(0,htmlName.length()-3)+"xhtml";
                          } else
                            htmlName = null;
                          if(htmlName!=null)//TODO nonhtml resources?
                            sideDocPath.append(htmlName);
                        }else
                          sideDocPath.append(comps[i]);
                        if(localResourcePath.length()>0)
                          localResourcePath.append("/");
                        localResourcePath.append(comps[i]);
                      }
                      TreeNode anotherDoc = doca.getTreeDB().getNode(doca.getWorkParent().getQualifiedId()+"/"+sideDocPath);
                      if(anotherDoc!=null)
                        return new File(anotherDoc.getNodeResourcesPath(((Document)anotherDoc).getResourceName()));
                      res = localResourcePath.toString();
                    }
                    if(res!=null)
                      return doca.getNodeResourcesFile().toPath().resolve(res).toFile();
                    else
                      return new File(doca.getNodeResourcesPath(doca.getResourceName()));
                     
                }
            }
        }
      }
      return null;
    }

  public ReqDocBrowser(Composite parent, ITreeDBProvider db, int style) {
    super(parent, style);   
    this.setLayout(new FillLayout());
    browserComponent = RequalityPlugin.newBrowser(this, db, RequalityPlugin.getBrowserType());
  }
  protected boolean handleBrowserLocation(String loc) {

    if (docservice == null || docservice.getTreeDB() == null) return true;
    //TODO rewrite in document out service
    try {
      int rpos = loc.lastIndexOf("#");
      if(rpos>-1)
        loc = loc.substring(0,rpos);
      rpos = loc.lastIndexOf("/?");
      if(rpos>-1)
        loc = loc.substring(0,rpos);
      rpos = loc.lastIndexOf("html?");
      if(rpos>-1)
        loc = loc.substring(0,rpos+4);
      File p1 = detectFile(loc);
      Document startDoc = docservice.getStartDocument();
      File p2 = startDoc.getNodeResourcesFile().toPath().resolve(startDoc.getResourceName()).toFile();
      FileSystem fs = null;
      try {
        fs = p1!=null&&p1.exists()&&p1.toPath()!=null?p1.toPath().getFileSystem():null;
        if (p1!=null && p1.exists() && p2.exists()) {
          String rp =
              CoreUtils.getRelativePath(
                  fs, p2.toURI().toString(), p1.toURI().toString());
          TreeNode doc2 = docservice.tryFind(p2, startDoc, p1, rp.split("/"));
          if(doc2!=null && (docservice.containsDoc(doc2) || startDoc.getSUUId().equals(doc2.getSUUId())))
            return true;
          if (doc2 != null && doc2.getType().equals(Document.TYPE_NAME)) {
            openEditorAndChangeFocus.accept((Document) doc2);
          }
          return false;
        }
      }finally {
        if(fs!=null)try {fs.close();}catch(Exception e) {};
      }
    } catch (Exception e) {
      RLogger.logError(e);
    }
    return true;
  
  }

  protected void runCommands() {
    if (!isDisposed())
      browserComponent.getDisplay()
        .syncExec(
          new Runnable() {
            @Override
            public void run() {
              while (jsCommands.size() > 0) {
                if(isDisposed())
                  return;
                try {
                  List<IJSCommand> cmd2proc = new ArrayList<>(jsCommands);
                  jsCommands.clear();
                  for (IJSCommand jsCode : cmd2proc) {
                    if(isDisposed())
                      return;
                    runJSCommand(jsCode);
                  }
                } catch (Exception e) {
                  e.printStackTrace();
                }
              }
              jsCommands.clear();
            }
          });
  }

  private void runJSCommand(IJSCommand jsCommand) {
    commandRuning = true;
    String js = jsCommand.getJS();//.replace("\n", "<br/>");
    if (CoreUtils.DEBUG_LEVEL > 0) {
      if (jsCommand.isEvaluate()) {
        js = "try{" + js + "}catch(ee){alert(ee+' on eval '+ee.trace);alert(\"" + js.replace('"', '`') + "\");}";
      } else {
        js = "try{res = " + js + ";return res;}catch(el){alert(el.toString()+' on exec '+el.trace);alert(\"" + js.replace('"', '`') + "\");}";
      }
    }
    try {
      Object result;
      if (jsCommand.isEvaluate()) {
        result = browserComponent.evaluate(js,true);
      } else {
        result = browserComponent.execute(js);
      }
      jsCommand.onComplete(result, this);
      return;
    } catch (Exception e) {
      RLogger.logError(e);
      jsCommand.onError(e);
    } finally {
      commandRuning = false;
    }
  }

  protected void scheduleJSCommand(IJSCommand jsCommand) {
  
    if(docservice==null || !docservice.isFullReady())
      return;
    // add command to queue
    jsCommands.add(jsCommand);
    if ( commandRuning) {
      return;
    }
    runCommands();
  }

  public void initDebug(){
 //   if(!isFullReady())
    //  return ;
    String script;
    script = "var imported = document.createElement('script');"+
             "imported.src = 'https://cdnjs.cloudflare.com/ajax/libs/firebug-lite/1.4.0/firebug-lite.js#startOpened=true';"+
             "imported.crossorigin='anonymous';"+
             "document.body.appendChild(imported);";
    browserComponent.evaluate(script,true);
  }
  
  protected boolean toBoolean(Object obj) {
    return obj != null ? (Boolean) obj : false;
  }


  public boolean findText(
      String aString, boolean aCaseSensitive, boolean aBackwards, boolean aWrapAround) {
    return (Boolean)
    browserComponent.evaluate(
            "return findText('"
                + aString
                + "',"
                + aCaseSensitive
                + ","
                + aBackwards
                + ","
                + aWrapAround
                + ");");
  }
    
  //private static File f = null;
  private File f = null;//TODO DEBUG version, for product use vers above 
  private String lastHref = null;
  ProgressListener completedListener;
  public String setUrl(String url, Document doc, FSId fcid, ILinkedStateProvider prov) {
    String sess = getDocService().getSession();
    if(!inited) {
      inited = true;
      //docservice = new DocumentsSocketService(doc, fcid, prov, ncl);
//      LocationListener permitter = LocationListener.changingAdapter(event->{
//        event.doit = lastHref==null||event.location.equals(lastHref);
//      });
      //browserComponent.addLocationListener(permitter);
      browserComponent.addKeyListener(new KeyListener() {
    
        @Override
        public void keyReleased(KeyEvent e) {
        }
    
        @Override
        public void keyPressed(KeyEvent e) {
          if(e.keyCode==27 && docservice!=null)
            docservice.setTargetLocation(null);
        }
      });
      browserComponent.addLocationListener(LocationListener.changingAdapter(event->{
          if(!handleBrowserLocation(event.location))//frames
              event.doit = false; 
            String loc = WSSocket.formKey(event.location, sess);
            if(docservice!=null && docservice.getState(loc) == null) {
              docservice.setState(loc, BrowserState.NEW);//TODO needed?
            }
            if(event.doit){
              boolean pause = false;
              boolean startAnim = false;
              synchronized(ReqDocBrowser.this){
                if(!loadingStarted){
                  startAnim = true;
                  loadingStarted = true;
                  pausedSocketServiceCount++;
                  if(pausedSocketServiceCount == 1)
                    pause = true;
                }
              }
              if(startAnim)
                addLoadingGif();
              if(pause)
                docservice.pauseDisposeTimer();
            }
      }));
      completedListener = ProgressListener.completedAdapter(event->{
          boolean unpause = false, stopAnim = false;
//          if(loadingStarted)
//            browserComponent.removeProgressListener(completedListener);
          synchronized(ReqDocBrowser.this){
            if(loadingStarted){
              stopAnim = true;
              loadingStarted = false;
              if(pausedSocketServiceCount>0){
                pausedSocketServiceCount--;
              }
              if(pausedSocketServiceCount == 0)
                unpause = true;
            }
          }
          if(stopAnim)
            dropLoadingGif();
          if(unpause)
            docservice.unpauseDisposeTimer();
        });
      browserComponent.addProgressListener(completedListener);
    }
    Integer port = docservice==null?null:docservice.getPort();
    if(port != null) {
      lastHref = getFullURL(url, port, doc.getSUUId(), sess);
        RLogger.logDebug(doc.getUserVisibleName()+" "+lastHref);
      browserComponent.setUrl(lastHref);
    }else
    browserComponent.setText(NO_SOCKET_INITED);
    return sess;
  }
  private boolean loading = false;
  protected void addLoadingGif() {
    if(loading)
      return;
    loading = true;
    browserComponent.getParent().setCursor(browserComponent.getDisplay().getSystemCursor(SWT.CURSOR_WAIT));
//    if(docservice!=null)
//      docservice.pauseDisposeTimer();
  }

  protected void dropLoadingGif() {
    if(loading){
      loading = false;
      browserComponent.getParent().setCursor(null);
//      if(docservice!=null)
//        docservice.unpauseDisposeTimer();
    }
  }

  public String getFullURL(String url, int port, String doc, String sess) {
    return url+"/?inTool=1&port="+port+"&document="+doc+"&session="+sess+"&";
  }
  protected void addScripts(Document doc) {
  }
  public Browser getWebBrowser() {
    return browserComponent;
  }

  @Override
  public boolean setFocus() {
    return browserComponent.setFocus();
  }
  @Override
  public void dispose() {
    if(!browserComponent.isDisposed()) {
      //webBrowser.stop();
      browserComponent.dispose();
    }
    super.dispose();
    if(docservice!=null)
      docservice.dispose(true);
  }

  public void scrollToTag(String mark) {
    browserComponent.execute(
        "window.scrollTo(0, $(." + mark + ").offset().top - window.innerHeight / 2);");
  }

  public void initDocService(Document doc, FSId fcid, ILocationChangedHandler iLocationChangedHandler, ILinkedStateProvider st, Location loc) {
    docservice = new DocumentsSocketService(doc, fcid, st, loc);
    docservice.locationChangedHandler = iLocationChangedHandler;

  }

  public void initSocket(String sess) {
    if(docservice!=null)
      docservice.initSocket(sess);
  }
  private long getTime() {
    return new Date().getTime();
  }

  public DocumentsSocketService getDocService() {
    return docservice;
  }
}
