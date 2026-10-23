package com.unitesk.requality.report;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.htmlcleaner.TagNode;

import com.unitesk.requality.core.jobs.JobManager;
import com.unitesk.requality.core.jobs.RunnableWithMonitor;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.service.ResourcesProvider;
import com.unitesk.requality.core.service.ResourcesServlet;
import com.unitesk.requality.core.service.ResourcesServlet.DocumentKind;
import com.unitesk.requality.core.service.StaticFilePaths;
import com.unitesk.requality.tool.CoreUtils;

public class ReportOutProcessor {
  private static ConcurrentLinkedDeque<ReportUpdateJob> queue = new ConcurrentLinkedDeque<>(); 
  private static ConcurrentLinkedDeque<ReportUpdateJob> working = new ConcurrentLinkedDeque<>(); 
  
  private static RunnableWithMonitor reportUpdater = null;
  private static int UPDATERS_DEFAULT_COUNT = 20;
  private static AtomicInteger updatersCount = new AtomicInteger(0);
  
  public static boolean isOutdated(Path tempIdFile, String id) {
    if(!Files.exists(tempIdFile))
      return true;
    String s;
    try {
      s = Files.readString(tempIdFile);
    } catch (IOException e){// TODO Auto-generated catch block
      RLogger.logDebug(CoreUtils.getTrace(e));
      return true;
    }
    if(s == null || !s.contains(id))
      return true;
    return false;
  }


  public static void startReportUpdate(TreeNode fnode, String fresourceName, File baseFile, String upSeq, String tempFileContent){
      //build upper seq for related report data
    int depth = 0;
    String name = fresourceName;
    if(name == null)
       return;
    int ind = name.indexOf("/");
    while(ind>-1) {
      depth++;
      name = name.substring(ind+1);
      ind = name.indexOf("/");
    }
    if(upSeq == null) {
      upSeq = "../../..";
      for(int i = 0; i < depth;i++)
        upSeq+="/..";
    }
    if(tempFileContent == null)
      tempFileContent = ResourcesServlet.getOne().getId();
    File proceedReportFile = ResourcesServlet.getProcessedReportFile(fnode, fresourceName);
    final File fprocessedFile = proceedReportFile;
    
    logLoadingInfo("report processing started :"+fresourceName);
    processReportContent(baseFile, fprocessedFile, fresourceName,
         fnode, upSeq, tempFileContent);
    logLoadingInfo("report processing over for "+fresourceName);
  }
  protected static void processReportContent(File baseFile, File fprocessedFile, String fresourcePath, 
      TreeNode fnode, String upperSequence, String prid) {
    if(fprocessedFile == null || fresourcePath == null)
      return;
    String fresourceName = gatherName(fresourcePath);
    logLoadingInfo("report processing - start content calc for "+fresourceName);
    try {
      java.nio.file.Path tempIdFile = fprocessedFile.getParentFile().toPath()
          .resolve(fprocessedFile.getName() + "_tempId");
      boolean rem = true;
      if (isOutdated(tempIdFile, prid))
        rem = true;
      if (rem) 
        removeProcessingFiles(tempIdFile, fprocessedFile);
      TreeDB db = fnode.getTreeDB();
      if(db.isDisposed())
        return;//outdated request, db is closed
      File unprocessedReportFile = baseFile!=null?baseFile:db.getStorage().getResource(fnode.getUUId(), fresourcePath);
      if (Files.exists(tempIdFile)) {//tends to be only if parallel problem is here
        throw new RuntimeException ("Another processing is still performing");
      } else {
        logLoadingInfo("report processing - exact process start "+fresourceName);
        if (unprocessedReportFile == null || !unprocessedReportFile.exists()) {
          throw new RuntimeException ("Base file does not exist");
        } else {
          try {
            TagNode d = null;
            long t1 = System.currentTimeMillis();
            {
              logLoadingInfo("report processing - oppened initial file read "+fresourceName);
              Files.createDirectories(tempIdFile.getParent());//create dirs if not exists
              Files.write(tempIdFile,
                  StandardCharsets.UTF_8.encode(prid).array(),
                  StandardOpenOption.CREATE_NEW);
              try {
                logLoadingInfo("report processing - create tempId on "+fresourceName);
                fprocessedFile.getParentFile().mkdirs();
                if(fprocessedFile.exists())
                  fprocessedFile.delete();
                try {
                  Files.createFile(fprocessedFile.toPath());
                } catch (IOException e){// TODO Auto-generated catch block
                  e.printStackTrace();
                }
                try(FileWriter fus = new FileWriter(fprocessedFile,
                        StandardCharsets.UTF_8)){
                  logLoadingInfo("report processing - started writer "+fresourceName +" to "+fprocessedFile.toPath().toString());
                  try (FileReader fr = new FileReader(unprocessedReportFile, StandardCharsets.UTF_8)){
                    String lastTagname = null, prevTagname = null;
                    boolean inTag = false;
                    StringBuffer tagNameBuffer = new StringBuffer();
                    LinkedList<Character> inQuote = new LinkedList<>();
                    boolean ignoreProcessingNext = false;
                    boolean tagIsEnd = false;
                    boolean prevTagStart = false;
                    boolean ignoreWriteChar = false;
                    boolean dropWriteIgnore = false;
                    boolean bodyStarted = false;
                    boolean tagIsComment = false;
                    boolean headhandled = false;
                    boolean injected = false;
                    int commentTag = -1;
                    long last = 0;
                    long pos = 0;
                    int r = 0;
                    while (r!=-1) {
                      if ((pos - last) > ResourcesServlet.BYTES_BUFF_SIZE) {
                        last = pos;
                      }
                      dropWriteIgnore = false;
                      r = fr.read();
                      if (r == -1)
                        break;
                      if (ignoreProcessingNext) {
                        fus.write(r);
                        pos++;
                        continue;
                      }
                      if (bodyStarted) {
                        fus.write(r);
                        pos++;
                        continue;
                      }
                      char c = (char) r;
                      if (c == '\\')
                        ignoreProcessingNext = true;
                      if (c == '<' && !inTag && inQuote.size() == 0) {
                        commentTag = 0;
                        inTag = true;
                        tagIsComment = false;
                        tagIsEnd = false;
                        prevTagStart = true;
                        if (tagNameBuffer.length() > 0)
                          fus.write(tagNameBuffer.toString());
                        tagNameBuffer.setLength(0);
                        if (c != '<')
                          tagNameBuffer.append(c);
                        ignoreWriteChar = true;
                      } else if (inTag) {
                        if (prevTagStart) {
                          if (c == '/') {
                            // tagNameBuffer.append('/');
                            tagIsEnd = true;
                          }
                        }
                        if (c == '>') {
                          boolean tagEnded = false;
                          if (tagIsComment) {
                            if (commentTag == 2) {
                              tagIsComment = false;
                              tagEnded = true;
                              // tag was all comment
                            }
                          }
                          if (inQuote.size() == 0) {
                            tagEnded = true;
                            prevTagname = lastTagname;
                            lastTagname = tagNameBuffer.toString();
                            dropWriteIgnore = true;
                            inTag = false;
                            // here handles tag changes,
                            if (!headhandled && lastTagname != null
                                && lastTagname.toLowerCase()
                                    .trim()
                                    .equals("body") && !injected) {
                              // no head case
                              fus.write("<head>");
                              headhandled = true;
                              appendReportScripts(fus,
                                ResourcesServlet.initScripts.get(DocumentKind.REPORT));
                              injected=true;
                              fus.write("</head>");
                            }
                            if (tagIsEnd && lastTagname != null
                                && lastTagname.toLowerCase()
                                    .trim()
                                    .equals("/head") && !headhandled && !injected) {
                              headhandled = true;
                              appendReportScripts(fus,
                                  ResourcesServlet.initScripts.get(DocumentKind.REPORT));
                              injected = true;
                            }
                            if (lastTagname != null && lastTagname
                                .toLowerCase().equals("body"))
                              bodyStarted = true;
                            int filepos = lastTagname==null?-1:lastTagname.toLowerCase().indexOf("file:/"); 
                            if(filepos>-1 && lastTagname != null) {
                              int nextQuote = lastTagname.indexOf("'", filepos+1);
                              int nextQuoteAlt = lastTagname.indexOf("\"", filepos+1);
                              nextQuote = nextQuote == -1? Integer.MAX_VALUE:nextQuote;
                              nextQuoteAlt = nextQuoteAlt == -1? Integer.MAX_VALUE:nextQuoteAlt;
                              nextQuote = Math.min(nextQuoteAlt, nextQuote);
                              String path = lastTagname.substring(filepos, nextQuote);
                              for(String s:ResourcesServlet.getProjectPaths()) {
                                int pathPos = path.indexOf(s);
                                if(pathPos>-1) {
                                  if(s.endsWith("/"))
                                    s = s.substring(0,s.length()-1);
                                  int beforeName = s.lastIndexOf('/');
                                  if(beforeName == -1)
                                    beforeName = s.length();
                                  else
                                    beforeName++;
                                  path = path.substring(pathPos+beforeName);
                                  lastTagname = lastTagname.substring(0,filepos)+
                                      upperSequence+ResourcesProvider.PROJECTS+"/"+path+
                                      lastTagname.substring(nextQuote);
                                  break;
                                }
                              }
                              //replace local paths to project-related if possible
                              
                            }
                          }
                          if (tagEnded) {
                            fus.write('<');
                            fus.write(lastTagname);
                            tagNameBuffer.setLength(0);
                          }
  
                        } else {
                          if (!tagIsComment) {
                            if (c == '!') {
                              if (commentTag == 0)
                                commentTag++;
                              else
                                commentTag = -1;
                            } else {
                              if (c == '-' && commentTag >= 0
                                  && commentTag < 4) {
                                prevTagStart = false;
                                commentTag++;
                              } else
                                commentTag = -1;
                              if (commentTag == 3) {
                                commentTag = 0;
                                tagIsComment = true;
                              }
                            }
                          } else {
                            if (commentTag > -1) {
                              if (c == '-')
                                commentTag++;
                              else
                                commentTag = 0;
                            }
                          }
                          tagNameBuffer.append(c);
                          if (!Character.isWhitespace(c))
                            prevTagStart = false;
                          if (!tagIsComment
                              && (c == '"' || c == '\'')) {
                            if (inQuote.size() == 0) {
                              inQuote.add(c);
                            } else if (inQuote.peekLast().equals(c))
                              inQuote.removeLast();
                            else
                              inQuote.add(c);
                          }
                        }
                      }
                      if (dropWriteIgnore) {
                        dropWriteIgnore = false;
                        ignoreWriteChar = false;
                      }
                      if (!ignoreWriteChar) {
                        pos++;
                        fus.write(c);
                      }
//                      fus.flush();
                    }
//                    fus.flush();
                    
                  }catch (Exception e) {
                    RLogger.logError(e);
                  }
                  logLoadingInfo("report processing - ends writer "+fresourceName);
                } catch (Exception e) {
                  RLogger.logError(e);
                }finally {
                }
                logLoadingInfo("report processing "+fresourceName+" - time to save hc "
                    + (System.currentTimeMillis() - t1));
              }
              catch (Exception e) {
                RLogger.logError(e);
              } finally {
              } 
            }
            logLoadingInfo("report processing "+fresourceName+" - time to clear and rework "
                    + (System.currentTimeMillis() - t1) + " " + fresourceName);
          } catch (IOException e1) {
            RLogger.logDebug(CoreUtils.getTrace(e1));
          } finally {
            logLoadingInfo("report processing thread over start "+fresourceName+", resfile:"+fprocessedFile.exists()+", tempId:"+Files.exists(tempIdFile));
            if(Files.exists(tempIdFile))
              Files.delete(tempIdFile);
            //logLoadingInfo("report processing - statused: status:"+response.getStatus()+" type: "+response.getContentType());
          }
        }
      }
    } catch (Exception e) {
      RLogger.logError(e);
    }
  }
  private static String gatherName(String fresourcePath){// TODO Auto-generated method stub
    int pos = fresourcePath.lastIndexOf("/");
    if(pos>-1)
      return fresourcePath.substring(pos+1);
    return fresourcePath;
  }

  protected static void appendReportScripts(FileWriter fus, String[] srcs) throws IOException {
    for(String script : srcs){
      boolean defer = true;
      if(script.startsWith("!")){
        defer = false;
        script = script.substring(1);
      }
      boolean added = false;
      if(script.startsWith("*")){
         script = script.substring(1);
         File stat = StaticFilePaths.paths.get(script);
         if(stat == null)
           stat = StaticFilePaths.paths.get("/"+script);
         if(stat!=null){
           fus.write("<script>"+Files.readString(stat.toPath())+"</script>");
           added = true;
         }
      }
      if(!added){
        if(script.endsWith("css")) {
          fus.write("<link rel = 'stylesheet' type = 'text/css' href='"+script+"'></link>");
        }else {
          fus.write("<script defer = 'true' type = 'text/javascript' src='"+script+"'></script>");
        }
      }
        //eh.appendChild(sc);
    }
  }
  private static void logLoadingInfo(String string) {
    if(CoreUtils.DEBUG_LEVEL>10)//TODO stay it for release?
      RLogger.logDebug(string, 11);
  
  }
  private static void removeProcessingFiles(java.nio.file.Path tempIdFile,File fprocessedFile){// TODO Auto-generated method stub
    try {
      Files.delete(tempIdFile);
    } catch (IOException e) {
    }
    fprocessedFile.delete();
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
  public static void shelduleReportUpdate(TreeNode node,String fileName,File baseFile,String upSeq,String tempFileContent, String port){// TODO Auto-generated method stub
    synchronized (queue) {
      ReportUpdateJob old = null;
      for(ReportUpdateJob s:working) {
        if(s.node.getSUUId().equals(node.getSUUId()) && s.fileName.equals(fileName)) {
          if(s.port == null && port!=null)
            s.port = port;//TODO many?
          return;
        }
      }
      for(ReportUpdateJob s:queue) {
        if(s.node.getSUUId().equals(node.getSUUId()) && s.fileName.equals(fileName)) {
          old = s;
          break;
        }
      }
      if(old!=null) {
        queue.remove(old);
        if(port == null)
          port = old.port;
      }
      ReportUpdateJob ruj = new ReportUpdateJob(node,fileName,baseFile,upSeq,tempFileContent, port);
      queue.addFirst(ruj);//reprioritize
      if(reportUpdater==null)
      {
        reportUpdater = new RunnableWithMonitor(){
          @Override
          public void run(IProgressMonitor pm) {
             ReportUpdateJob ruj = null;
             boolean handled = true;
             while(handled){
               handled = false;
               synchronized(queue) {
                 ruj = queue.poll();
                 if(ruj!=null)
                   working.add(ruj);
               }
               if(ruj != null) {
                 handled = true;
                 try {
                   startReportUpdate(ruj.node, ruj.fileName, ruj.baseFile, ruj.upSeq, ruj.tempFileContent);
                 }catch(Exception e) {
                   RLogger.logError(e);//TODO clear temp file?
                 }finally {
                   working.remove(ruj);
                 }
                 if(ruj.port!=null)
                   sendReportUpdated(ruj.node, ruj.port);
               }
             }
               synchronized (queue) {
                 updatersCount.set(updatersCount.get()-1);
                 if(updatersCount.get() == 0)
                   reportUpdater = null;//restart on jobs over
             }
          }
        };
      }
      int nowCount = updatersCount.get();
      if(nowCount < UPDATERS_DEFAULT_COUNT) { 
        updatersCount.set(nowCount+1);
        JobManager.basejm.startJob("Update report resources content ("+nowCount+")", reportUpdater, true);
      }
    }
  }

  protected static void sendReportUpdated(TreeNode node, String port){// TODO Auto-generated method stub
    //TODO uncomment on too long waits, require res service change
//    List<GenericSocketService> sockets = GenericSocketService.detectSocket(port, node.getSUUId());
//    for(GenericSocketService s: sockets) {
//      List<WSSocket>sess = s.resourceNodeToSocks.get(node);
//      for(WSSocket sock: sess)
//        sock.send("refresh", new Pair[0]);
//    }
  }
}

