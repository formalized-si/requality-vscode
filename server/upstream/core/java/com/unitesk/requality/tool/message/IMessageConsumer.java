package com.unitesk.requality.tool.message;

import java.util.concurrent.atomic.AtomicBoolean;

import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.storage.OperationResult;
import com.unitesk.requality.nodetype.Document;

public interface IMessageConsumer {
  void openError(String title, String message, String errorStatus, boolean wait);
  void openInfo(String title, String message, boolean wait, Pair<String, Object> ... metaParams);
  void openBigInfo(String messageBoxTitle, String messageBoxMessage, boolean wait);
  boolean openQuestion(String title, String message, boolean defval, boolean wait);
  boolean openQuestionOkCancel(String title, String message, boolean defval, boolean wait);
  Document openSelectDoc(Document docfrom, Document docdefault);
  Object openView(TreeDB db, String name, boolean async, String ... args);
  Pair<String, Boolean> openInputDialog(String title, String message, String def, InputValidator inputValidator);
  Pair<AtomicBoolean, OperationResult> openQuestionOkCancel(String title, String message, boolean def, boolean waitFor, Runnable okActons,
      Runnable cancelActions);
  Object openEditor(String editorid, TreeNode node, TreeNode sel);
}
