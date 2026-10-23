package com.unitesk.requality.core.service;

import java.io.IOException;
import java.net.BindException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import javax.servlet.GenericServlet;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import com.unitesk.requality.core.jobs.JobManager;
import com.unitesk.requality.core.jobs.RunnableWithMonitor;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.storage.ResManager;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.nodetype.ReportData;
import com.unitesk.requality.tool.CoreUtils;
import com.unitesk.requality.tool.cli.CommandLineArgsProvider;

public class ResourcesProvider implements IPortStatusProvider {
  public static String KEY = UUID.randomUUID().toString();
  public static final String PROJECTS = "projectData";
  private ServletContextHandler context;
  private ServletHolder sh;
  private Server server;
  private Thread serverThread = null;
  private int port = 9807;
  private static ResourcesProvider one = new ResourcesProvider();
  public static String RANDOM_STRING=generateRandom();
  private boolean destroyed = false;
  private PortStatus portState = PortStatus.NOT_INITED;
  private ServerConnector connector;
  public boolean initiated = false;
  private static String generateRandom() {
    StringBuilder sb = new StringBuilder();
    Random r = new Random();
    char c = '0';
    for(int i = 0;i<12;i++)
      switch(r.nextInt(3)) {
        case 0:
          sb.append((char)(r.nextInt('Z'-'A')+'A'));
          break;
        case 1:
          sb.append((char)(r.nextInt('z'-'a')+'a'));
          break;
        case 2:
          sb.append((char)(r.nextInt('9'-'0')+'0'));
          break;
      }
    return sb.toString();
  }
  public static ResourcesProvider getOne() {
    return one;
  }
  @Override
  public int getPort() {
	  return port;
  }
  public void init() {
    PortStateRegister.getOne().registerProvider(this);
    Thread t = new Thread(new Runnable() {
      @Override
      public void run() {
        destroy();
      }
    });
    Runtime.getRuntime().addShutdownHook(t);
    StaticFilePaths.initIfNot();
    String[] ca = CommandLineArgsProvider.cLineArgs;
    for(int i = 0;i<ca.length;i++){
      String lowerCa = ca[i].toLowerCase();
      String sname = "-httpport".toLowerCase();
      int ind = lowerCa.indexOf(sname);
      if(ind>-1) {
        ind = lowerCa.indexOf("=", ind);
        if(ind != -1)
        {
          String portPart = lowerCa.substring(ind+1).trim();
          try{
            port = Integer.parseInt(portPart);
          }
          catch(Exception e){
            RLogger.logInfo("unsuccessfull parsing "+ca+" port setting");
          }
          
        }
      }
    }
    JobManager.jm.startBaseJob("Resources server process", new RunnableWithMonitor() {
      @Override
      public boolean shouldRerun() {
        return true;
      }
      @Override
      public long getTimeToReshedule() {
        return 4000;
      }
      @Override
      public boolean shouldRun() {
        return ResManager.getManager().getTracker().isInited();
      }
      @Override
      public void run(IProgressMonitor pm) {
        try {
          ResourcesServlet ds = ResourcesServlet.getOne();//init static vars
          ProjectsContentServlet pserv = new ProjectsContentServlet();
          GenericServlet rootContent = new DefaultServlet();
          int i = 0, j = 0;
          portState = PortStatus.NOT_INITED;
          while(j<10 && portState != PortStatus.STARTED) {
              j++;
              try {
                  if(PortUtils.testPortIsFree(port)) {
                    portState = PortStatus.NOT_INITED;
                    server = new Server(port);
                    //.setStopAtShutdown(true);
                    connector = new ServerConnector(server,1,1);
                    QueuedThreadPool qtr = (QueuedThreadPool) connector.getExecutor();
                    qtr.setDaemon(true);
                    qtr.setMinThreads(1);
                    qtr.setReservedThreads(1);
                    qtr.setStopTimeout(-1);
                    qtr.setIdleTimeout(-1);
                    connector.setPort(port);                    
                    String resPath = ResManager.getManager().getJSResourcesPath();
                    ServletContextHandler basecontext = new ServletContextHandler(null, "/", ServletContextHandler.NO_SESSIONS);
                    basecontext.setResourceBase(resPath);
                    sh = new ServletHolder(ds);
                    
                    // rootHolder = new ServletHolder(rootContent);
                    //context.addServlet(rootHolder, "/*");
                    
                    List<ContextHandler> lcontexts = new LinkedList<>();

                    sh.setInitParameter("resourceBase",resPath);
                    context = new ServletContextHandler(basecontext, "/");
                    context.addServlet(sh, "/"+RANDOM_STRING+"/*");
                    context.addServlet(sh, "/noScript/"+RANDOM_STRING+"/*");
                    context.addServlet(sh, "/script/"+RANDOM_STRING+"/*");
                    lcontexts.add(context);
                    
                    ContextHandlerCollection contexts = new ContextHandlerCollection();
                    
                    ServletContextHandler pcontext = new ServletContextHandler(basecontext, "/"+RANDOM_STRING+"/"+PROJECTS);
                    ServletHolder sh2 = new ServletHolder(pserv);
                    sh2.setInitParameter("dirAllowed","false");
                    sh2.setInitParameter("pathInfoOnly","true");
                    pcontext.addServlet(sh2, "/*");
                    lcontexts.add(pcontext);
                    
                    contexts.setHandlers(lcontexts.toArray(new ContextHandler[0]));
                    
                    server.setHandler(contexts);
                    
                    server.start();
                    System.err.println(server.dump());
                    RLogger.logInfo("Http server for documents was started on port "+port);
                    RLogger.logInfo("SERVER INITIATED "+RANDOM_STRING+" "+server.getState());
                    portState = PortStatus.STARTED;
                    initiated  = true;
                }
              } catch(IOException ioe) {
                boolean error = true;
                if(ioe.getCause() instanceof BindException) {
                  error = false;
                  //ignore port occupied
                }
                if(error) {
                  RLogger.logInfo("HTTP server for documents start error " + ioe.getMessage()+" "+CoreUtils.getTrace(ioe.getStackTrace()));
                  RLogger.logError("Cannot init resources server on port "+port+" because it is occupied");
                  RLogger.logError(ioe);
                }else
                {
                  RLogger.logDebug("HTTP server for documents start error " + ioe.getMessage()+" "+CoreUtils.getTrace(ioe.getStackTrace()),5);
                  RLogger.logDebug("Reinit - cannot init resources server on port "+port+" because it is occupied "+j,5);
                    RLogger.logDebug(ioe , 5);
                }
              }
              catch (Exception e) {
                  RLogger.logError(e);
                  try {
                      Thread.currentThread().join(1000);
                  } catch (InterruptedException e1) {
                      e1.printStackTrace();
                  }
              }   
              finally {
                i++;
              }
              if(portState!=PortStatus.STARTED)
                switch (PortInitStrategy.strategy) {
                  case FIXED:{
                    throw new PortOccupiedException("Cannot init resources server on port "+port+" because it is occupied");  
                  }
                  case DYNAMIC_ON_OCCUPIED:{
                    port+=5;                  
                  }break;
                }else
	              if(server!=null)
					serverThread = Thread.currentThread();
	              else
	            	Thread.currentThread().stop();
          } 
        }catch(PortOccupiedException e) {
          RLogger.logError(e);
          portState = PortStatus.OCCUPIED;
        }
      }
    }, false);
  }

  public void destroy() {
    if(destroyed)
      return;
    destroyed = true;
    try{
      synchronized (ResourcesProvider.this) {
        ResourcesProvider.this.notifyAll();
      }
    }catch(Exception r){
        RLogger.logError(r);
    }
    try {
      if(context!=null)
        context.stop();
      if(server!=null) {
        for(Connector s:server.getConnectors())
          s.stop();
        if(server.isRunning())
          server.stop();
        synchronized (server) {
          if(serverThread!=null)
        	serverThread.interrupt();
        }
      }
      RLogger.logInfo("Resources server on "+port+" stopped");
    }catch(Exception e) {
      RLogger.logError(e);
    }
    try {
      if(connector!=null && connector.isRunning())
        connector.close();
    }catch(Exception e) {
      RLogger.logError(e);
    }
      
    
  }

  public URI formProjectURL(String projectPath) {
	StringBuilder pathBuilder = new StringBuilder();
	pathBuilder.append("/");
	pathBuilder.append(RANDOM_STRING);
	pathBuilder.append("/"+PROJECTS+"/");
	pathBuilder.append(projectPath);
	try {
      return new URI("http",null, "127.0.0.1",port, pathBuilder.toString(),null,null);
	} catch (URISyntaxException e) {
      e.printStackTrace();
	}
	return null;
  }
  public URI formDocumentURL(Document startDoc, String session, int wsport) {
    if(startDoc == null)
      return null;
	try {
		StringBuilder pathBuilder = new StringBuilder();
		pathBuilder.append("/");
		pathBuilder.append(RANDOM_STRING);
		pathBuilder.append("/"+ResourcesServlet.DOCUMENT+"/");
		pathBuilder.append(CoreUtils.URLEncode(startDoc.getTreeDB().getSemiLocalName()));
		pathBuilder.append("::");
		pathBuilder.append(startDoc.getSUUId());
		pathBuilder.append("/");
		pathBuilder.append(session);
		if(wsport>-1){
			pathBuilder.append("-");
			pathBuilder.append(wsport);
		}
		pathBuilder.append("/");
		return new URI("http",null, "127.0.0.1",port, pathBuilder.toString(),null,null);
	} catch (URISyntaxException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	return null;
  }
  public URI formReportsURL(ReportData startDoc, String session, int wsport) {
	try {
		StringBuilder pathBuilder = new StringBuilder();
		pathBuilder.append("/");
		pathBuilder.append(RANDOM_STRING);
		pathBuilder.append("/"+ResourcesServlet.REPORT+"/");
		pathBuilder.append(CoreUtils.URLEncode(startDoc.getTreeDB().getSemiLocalName()));
		pathBuilder.append("::");
		pathBuilder.append(startDoc.getSUUId());
		pathBuilder.append("/");
		pathBuilder.append(session);
		if(wsport>0) {
		  pathBuilder.append("-");
		  pathBuilder.append(wsport);
		}
		pathBuilder.append("/");
		return new URI("http",null, "127.0.0.1",port, pathBuilder.toString(),null,null);
	} catch (URISyntaxException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	return null;
  }
  public URI formEditorURL(String editorKind, TreeNode target, String session, int wsport) {
	try {
		StringBuilder pathBuilder = new StringBuilder();
		pathBuilder.append("/");
		pathBuilder.append(RANDOM_STRING);
		pathBuilder.append("/");
		pathBuilder.append(editorKind);
		pathBuilder.append("/");
		pathBuilder.append(CoreUtils.URLEncode(target.getTreeDB().getSemiLocalName()));
		pathBuilder.append("::");
		pathBuilder.append(target.getSUUId());
		pathBuilder.append("/");
		pathBuilder.append(session);
		if(wsport>-1){
			pathBuilder.append("-");
			pathBuilder.append(wsport);
		}
		pathBuilder.append("/");
		return new URI("http",null, "127.0.0.1",port, pathBuilder.toString(),null,null);
	} catch (URISyntaxException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	return null;
  }
  public ServiceStatus getStatus() {
    return new ServiceStatus(Integer.toString(port), server.getState(), portState);
  }
  public URI getResourcesURI(TreeDB db, UUID uuid) {
	StringBuilder pathBuilder = new StringBuilder();
	pathBuilder.append("/");
	pathBuilder.append(RANDOM_STRING);
	pathBuilder.append("/");
	pathBuilder.append(ResourcesServlet.RESOURCES);
	pathBuilder.append("/");
	pathBuilder.append(CoreUtils.URLEncode(db.getSemiLocalName()));
	pathBuilder.append("::");
	pathBuilder.append(uuid);
	pathBuilder.append("/");
	try {
		return new URI("http",null, "127.0.0.1",port, pathBuilder.toString(),null,null);
	} catch (URISyntaxException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	return null;
		
  }
  public PortStatus getPortState() {
    return portState;
  }
  private Set<Integer> ports = new HashSet<>();
  @Override
  public String getPortUserInfo() {
    return "Resource server";
  }
  @Override
  public PortStatus getPortStatus() {
    return portState;
  }
  public String serverDump() {
    return server.dump();
  }
}
