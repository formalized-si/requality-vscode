package com.unitesk.requality.core.service;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.servlet.DefaultServlet;

import com.unitesk.requality.core.events.Moment;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.storage.ITreeStateListener;
import com.unitesk.requality.core.storage.ResManager;

public class ProjectsContentServlet extends DefaultServlet {
  private Map<String, File>fileProjectNames = null;
  private ITreeStateListener tsl = new ITreeStateListener() {	//TODO also rename?
	@Override
	public void settingsChanged(TreeDB db) {
	}
	@Override
	public void open(TreeDB db) {
		fileProjectNames = null;
	}
	@Override
	public void delete(String lname, String pname, TreeDB db) {
		fileProjectNames = null;
	}
	
	@Override
	public void close(TreeDB db) {
		fileProjectNames = null;
	}
  };

  private synchronized Map<String, File> getProjectNames(){
    if(fileProjectNames==null) {
      fileProjectNames = new HashMap<>();
      for(File f:ResManager.getManager().getAllResourceProjects())
      {
        try {
          File ptp = f.toPath().toRealPath().toFile();
          fileProjectNames.put(ptp.getName(), ptp);
        } catch (IOException e) {
        }
      }
    }
    return fileProjectNames;
  }
  

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse rresp) throws ServletException, IOException {
    String rbase = request.getRequestURI().substring(request.getContextPath().length()+1);
    int fpos = rbase.indexOf("/");
    if(fpos>-1) {
      String fpart = rbase.substring(0, fpos);
      File basePath = getProjectNames().get(fpart);
      if(basePath!=null) {
        File tfile = basePath.toPath().resolve(rbase.substring(fpos+1)).toFile();
        if(tfile.exists()) {
          String mimetype = getServletContext().getMimeType(tfile.getName());//TODO test
          rresp.setStatus(200);
          ResourcesServlet.outContent(rresp, tfile, mimetype);
          return;
        }
      }
    }
    super.doGet(request, rresp);
  }
  
  public ProjectsContentServlet(){
    super();
    ResManager.getManager().addListener(Moment.BEFORE, tsl);
  }
  @Override
  public void destroy() {
    super.destroy();
    ResManager.getManager().removeListener(Moment.BEFORE, tsl);
  }
}
