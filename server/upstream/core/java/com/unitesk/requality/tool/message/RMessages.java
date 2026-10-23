package com.unitesk.requality.tool.message;

import java.util.concurrent.atomic.AtomicBoolean;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.OperationResult;
import com.unitesk.requality.nodetype.Document;

public class RMessages {
  public static final String ID_CHANGE_DIALOG = "ChangeIdDialog";
  public static final String ID_FILE_DIALOG = "FileDialog";
  public static final String DOWNLOAD_LINK_DIALOG = "DownloadLinkDialog";
  private static IMessageConsumer mc = new ConsoleMessageConsumer();

  public static void initMessageConsumer(IMessageConsumer mca){
    mc = mca;
  }
  
  public static void openError(String title, String message, String errorStatus, boolean wait) {
    mc.openError(title,message,errorStatus,wait);
    RLogger.logError((title!=null?title:"error")+""+": "+(message!=null?message:""));
  }

  public static void openBigInfo(String messageBoxTitle, String messageBoxMessage, boolean inf) {
    mc.openBigInfo(messageBoxTitle, messageBoxMessage, inf);
  }
  
  public static void openInfo(String messageBoxTitle, String messageBoxMessage, boolean inf, Pair<String, Object> ... metaParams) {
    mc.openInfo(messageBoxTitle, messageBoxMessage, inf, metaParams);
  }

  public static Boolean openQuestion(String title, String message, boolean def, boolean b) {
    return mc.openQuestion(title,message,def,b);
  }
  public static Boolean openQuestionOkCancel(String title, String message, boolean def, boolean b) {
    return mc.openQuestionOkCancel(title,message,def,b);
  }

  public static Document openSelectDoc(Document docfrom, Document docdefault) {
    return mc.openSelectDoc(docfrom, docdefault);
  }

  public static Object openView(TreeDB db, String name, boolean async, String ... args) {
    return mc.openView(db, name,async, args);
  }
  public static Object openView(TreeDB db, String name, String ... args) {
    return openView(db, name, true, args);
  }

  public static Pair<String, Boolean> openInputDialog(String title, String message, String def, InputValidator inputValidator) {
	return mc.openInputDialog(title,message,def,inputValidator);
  }

  public static Pair<AtomicBoolean, OperationResult> openQuestionOkCancel(String title, String message, boolean b, boolean c,
      Runnable runnable, Runnable runnable2) {
    return mc.openQuestionOkCancel(title, message, b, c, runnable, runnable2);
  }

  public static Object openEditor(String editorid, TreeNode node, TreeNode sel) {
    return mc.openEditor(editorid, node, sel);
  }
}
