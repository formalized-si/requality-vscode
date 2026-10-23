package com.unitesk.requality.tool.message;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import com.unitesk.requality.core.jobs.JobManager;
import com.unitesk.requality.core.jobs.RunnableWithMonitor;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.storage.OperationResult;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.tool.CoreUtils;

public class ConsoleMessageConsumer implements IMessageConsumer {
  private static DateFormat df = CoreUtils.SQL_DATE_FORMAT;
  public static String getTime(){
    return "\\"+df.format(new Date())+"\\ ";
  }
  
  @Override
  public void openError(String title, String message, String errorStatus, boolean wait) {
    System.out.println(getTime()+"Error["+title+"]: "+message+errorStatus == null?"":("\n{"+errorStatus+"}")); 
    if(wait)
      waitChar();
  }

  private void waitChar() {
    try {
      System.in.read();
    } catch (IOException e) {
      e.printStackTrace();
    } 
  }

  @Override
  public void openInfo(String title, String message, boolean wait, Pair<String, Object> ... metaParams) {
    System.out.println(getTime()+"Info["+title+"]: "+message); 
    if(wait)
      waitChar();
  }

  @Override
  public void openBigInfo(String title, String message, boolean wait) {
    System.out.println(getTime()+"BInfo["+title+"]: "+message); 
    if(wait)
      waitChar();
  }

  @Override
  public boolean openQuestion(String title, String message, boolean defval, boolean wait) {
    System.out.println(getTime()+"Quest["+title+"]: "+message+" press y to yes"); 
    if(wait)
      try {
        return Character.toLowerCase((char)System.in.read())=='y';
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    return defval;
  }

  @Override
  public boolean openQuestionOkCancel(String title, String message, boolean defval, boolean wait) {
    System.out.println(getTime()+"Quest["+title+"]: "+message+" press y to ok"); 
    if(wait)
      try {
        return Character.toLowerCase((char)System.in.read())=='y';
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    return defval;
  }
  @Override
  public Pair<AtomicBoolean, OperationResult> openQuestionOkCancel(String title, String message, boolean def, boolean waitFor,
      Runnable okActons, Runnable cancelActions) {
    System.out.println(getTime()+"Quest["+title+"]: "+message+" press y to ok");
    AtomicBoolean actionOver = new AtomicBoolean(false);
    AtomicBoolean ret = new AtomicBoolean(def);
    RunnableWithMonitor rwm = new RunnableWithMonitor() {
      @Override
      public void run(IProgressMonitor pm) {
        try {
          ret.set(Character.toLowerCase((char)System.in.read())=='y');
          try {
            if(ret.get())
              okActons.run();
            else
              cancelActions.run();
          }finally {
            actionOver.set(true);
          }
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }  
      }
    };
    if(waitFor)
    {
      rwm.run(null);
      return new Pair<>(actionOver, ret.get()?OperationResult.SUCEEDED:OperationResult.UNSUCEEDED);
    }
    JobManager.jm.startBaseJob(title, rwm, false);
    return new Pair<>(actionOver, OperationResult.PARALLEL);
  }
  @Override
  public Document openSelectDoc(Document doc, Document docdef) {
	return docdef;
  }

  @Override
  public Object openView(TreeDB db, String name, boolean async, String ... args) {
    RLogger.logInfo("Call for "+name+" view.");
    return null;
  }

  @Override
  public Pair<String, Boolean> openInputDialog(String title, String message, String def, InputValidator inputValidator) {
	System.out.println(getTime()+"Input["+title+"]: "+message+". Print answer here:");
    InputStreamReader bis = new InputStreamReader(System.in);
    BufferedReader br = new BufferedReader(bis);
    try {
      return new Pair<>(br.readLine(), true);
    } catch (IOException e) {
		e.printStackTrace();
	}finally {
      try {
		br.close();
	} catch (IOException e) {
		e.printStackTrace();
	}
      try {
		bis.close();
	} catch (IOException e) {
		e.printStackTrace();
	}
    }
	return new Pair<>(def, false);
  }

  @Override
  public Object openEditor(String editorid, TreeNode node, TreeNode sel) {
    RLogger.logInfo("open Editor "+editorid+" call on "+node.getUserVisibleName());
    return null;
  }

}
