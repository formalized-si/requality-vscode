/*
 * Copyright 2011-2026 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
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
package com.unitesk.requality.core.operation.document;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.commons.io.output.WriterOutputStream;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.storage.OFlag;
import com.unitesk.requality.core.tree.TreeStateManager;
import com.unitesk.requality.document.IDocumentProcessor;
import com.unitesk.requality.nodetype.Document;
import com.unitesk.requality.tool.message.RMessages;

// TODO: headless document processing support

public abstract class DocumentProcessor implements IDocumentProcessor {
  public static boolean eclipseGUI = false;
  protected String encoding = "UTF-8";
  //    protected boolean showSuperMessageBox = true;
  protected boolean showMessages = true;
  protected boolean noTransactions = false;
  protected String messageBoxTitle = null;
  protected String messageBoxMessage = null;

  public void setNoTransactionsFlag(boolean tf) {
    noTransactions = tf;
  }
 
  public void setShowMessages(boolean v, boolean defValue) {
    showMessages = v;
    questionBoxAnswer = defValue;
  }

  public void toggleShowMessages() {
    showMessages = !showMessages;
  }

  @Override
  public int[] getCounterMax() {
    return new int[] {1000, 0, 400};
  };

  @Override
  public void setEncoding(String encoding) {
    this.encoding = encoding;
  }

  int[] progress = new int[] {0, 0, 0};
  private IProgressMonitor monitor;

  private int sumCountersMax() {
    int[] maxs = getCounterMax();
    int sum = 0;
    for (int i = 0; i < 3; i++) sum += maxs[i];
    return sum;
  }

  protected IProgressMonitor getMonitor() {
    return monitor;
  }

  private int lastTargetWorked = 0;

  private void updateProgressMonitor() {
    int s = 0;
    for (int i = 0; i < 3; i++) s += progress[i];
    if (monitor != null) monitor.worked(s - lastTargetWorked);
    lastTargetWorked = s;
  }

  @Override
  public void setCounterValues(int lc, int pc, int sc) {
    progress[0] = lc;
    progress[1] = pc;
    progress[2] = sc;
    updateProgressMonitor();
  }

  @Override
  public int[] getCounterValues() {
    return progress;
  }

  protected void cancelError(String t1, String t2, String st) {
    if (restoretempl && templ != null)
      for (String s : templ.keySet()) doc.getTreeDB().setActiveTemplate(s, null);
    showErrorBox(t1, t2, st);
  }

  protected void cancelMessage(String s1, String s2) {
    if (restoretempl && templ != null)
      for (String s : templ.keySet()) doc.getTreeDB().setActiveTemplate(s, null);
    showMessageBox(s1, s2);
  }

  @Override
  public void process(Document doc, TreeNode rootNode,PrintStream ps, Writer writer, IProgressMonitor monitor)
      throws IOException {
    //Shell parent = PlatformUI.getWorkbench().getActiveWorkbenchWindow()
    //      .getShell();
    progress = new int[] {0, 0, 0};
    this.doc = doc;
    lastTargetWorked = 0;
    TreeDB db = rootNode.getTreeDB();
    TreeStateManager tsm = TreeStateManager.getOne(db.getTracker());
    if (!noTransactions&&!tsm.canStartTransaction(db.getLocalName(), tsm.getRevisionInfo(db))) {
      cancelError(
          "Document Processor Cancelled",
          "Document Processor Cancelled: Project catalog is used by other process",
          null);
      return;
    }
    boolean cont = noTransactions;
    boolean started = !cont && db.startTransaction("Document Processor", true);
    cont = cont || started;
    if (cont) {
      try {
        String htmlName = doc.getResourceName();
        MonitoredInputStream inStream =
            new MonitoredInputStream(doc.getResourceContent(htmlName), 0);
        //inStream = new BufferedInputStream(doc.getResourceContent(htmlName));
        this.monitor = monitor;
        Reader reader = new InputStreamReader(inStream, encoding);
        if (monitor != null)
          monitor.beginTask("Document '" + doc.getQualifiedId() + "' processing", 1);
        ByteArrayOutputStream byteOut = null;
        Writer baseWriter = writer;
        boolean internalWriter = false;
        if(writer == null || byteOut == null) {
          internalWriter = true;
          byteOut = new ByteArrayOutputStream();
          writer = new OutputStreamWriter(byteOut, encoding);
        }
        if (!isCanceled() && process(doc, reader, writer, rootNode, ps)) {
          writer.close();
          reader.close();
          inStream.close();
          if (isCanceled()) {
            writer.close();
            reader.close();
            if(started) {
              started = false;
              db.rollback();
            }
            cancelMessage("Document Processor", "Document processing canceled");
            return;
          }
          if(internalWriter) {
            if(baseWriter!=null) {
              try(MonitoredInputStream outIn = new MonitoredInputStream(new ByteArrayInputStream(byteOut.toByteArray()), 2)){
                try(WriterOutputStream wos = new WriterOutputStream(baseWriter, StandardCharsets.UTF_8)){
                  outIn.transferTo(wos);
                }
              }
              baseWriter.close();
            }else
              try(MonitoredInputStream outIn = new MonitoredInputStream(new ByteArrayInputStream(byteOut.toByteArray()), 2)){
                doc.setResourceContent(htmlName, outIn, new ProcessorData(), OFlag.NONE);
              }
          }
          if (isCanceled()) {
            writer.close();
            reader.close();
            if(started) {
              started = false;
              db.rollback();
            }
            cancelMessage("Document Processor", "Document processing canceled");
            return;
          }
          int[] cv = getCounterValues();
          cv[2] = getCounterMax()[2];
          setCounterValues(cv[0], cv[1], cv[2]);
          if(started) {
            started = false;
            db.commit();
          }

          if (messageBoxTitle != null && messageBoxMessage != null) {
            showMessageBox(messageBoxTitle, messageBoxMessage);
          } else {
            showMessageBox("Document Processor", "Document processing finished");
          }
        } else {
          writer.close();
          reader.close();
          if(baseWriter!=null)
            baseWriter.close();
          if(started) {
            started = false;
            db.rollback();
          }
          if (isCanceled()) cancelMessage("Document Processor", "Document processing is cancelled");
        }

      } catch (final Exception e) {
        RLogger.logError(e);
        if(started) {
          started = false;
          db.rollback();
        }
        cancelError(
            "Document Processor Error",
            "Document Processor Error: " + e.getLocalizedMessage(),
           e.getMessage());
      } finally {
        if(started)
          db.rollback();
      }
    } else {
      cancelError(
          "Document Processor Error",
          "Some actions are already being performed",
          "transaction started");
    }
    if (restoretempl && templ != null)
      for (String s : templ.keySet()) doc.getTreeDB().setActiveTemplate(s, null);
  }

  protected boolean isCanceled() {
    if (monitor == null) return false;
    return monitor.isCanceled();
  }

  private void setCounterValues(int[] vals) {
    setCounterValues(vals[0], vals[1], vals[2]);
  }

  private class MonitoredInputStream extends BufferedInputStream {
    int max;
    int param;

    public MonitoredInputStream(InputStream in, int param) {
      super(in);
      this.param = param;
      try {
        max = available();
      } catch (IOException e) {
        RLogger.logError(e);
      }
    }

    @Override
    public synchronized int read(byte[] b, int off, int len) throws IOException {
      int r = super.read(b, off, len);
      int mx = getCounterMax()[param];
      int[] cv = getCounterValues();
      cv[param] = max == 0 ? 0 : (mx - available() * mx / max);
      setCounterValues(cv);
      return r;
    };
  }

  protected abstract boolean process(Document doc, Reader reader, Writer writer, TreeNode rootNode, PrintStream ps)
      throws IOException;

  private boolean restoretempl = false;
  private Map<String, String> templ;
  protected Document doc;

  @Override
  public boolean configure(Document doc, Map<String,Object> params, PrintStream pw) {
    Map<String, String> templ = doc.getTreeDB().getActiveTemplates();
    boolean hastempl = false;
    this.doc = doc;
    for (String s : templ.keySet()) {
      if (templ.get(s) != null && !templ.get(s).isEmpty()) {
        hastempl = true;
        break;
      }
    }
    if (hastempl)
      if (!showQuestionBox(
          "Several templates are active",
          "WARNING. One or more templates are active for current project",
          "Please press ok if you want it to stay active.\nOr press cancel if you want to disable it for current document processing.",
          "")) {
        for (String s : templ.keySet()) doc.getTreeDB().setActiveTemplate(s, null);
        restoretempl = true;
      }
    return true;
  }

  protected void showBIGMessageBox(final String messageBoxTitle, final String messageBoxMessage) {
    if (!showMessages) {
      RLogger.logInfo("binfo: "+messageBoxTitle + ": " + messageBoxMessage);
    } else {
      RMessages.openBigInfo(messageBoxTitle,messageBoxMessage, false);
    }
  }

  protected void showMessageBox(final String messageBoxTitle, final String messageBoxMessage) {
    if (!showMessages) {
      RLogger.logInfo("info: "+messageBoxTitle + ": " + messageBoxMessage);
    } else {
      RMessages.openInfo(messageBoxTitle, messageBoxMessage, false);
    }
  }

  protected void showErrorBox(final String title, final String message, final String errorStatus) {
    if (!showMessages) {
      RLogger.logInfo("error: "+title + ": " + message);
    } else {
      RMessages.openError(title, message, errorStatus, false);
      
    }
  }

  private static Boolean lastquestionboxvalue;
  private boolean questionBoxAnswer = true;
  protected Boolean showQuestionBox(
      final String title, final String label1, final String message, final String label2) {
    lastquestionboxvalue = false;
    if(!showMessages)
      RLogger.logInfo("question(inactive): "+title + ": " + message);
    else {
      lastquestionboxvalue = RMessages.openQuestion(title, message, false, questionBoxAnswer);
    } 
    return lastquestionboxvalue;
  }

  private Document tempprefferedDoc;

  protected Document showSelectAnotherDocumentDialog(final Document doc, Document prefferedDoc) {
    return showSelectAnotherDocumentDialog(doc, prefferedDoc, false);
  }

  protected Document showSelectAnotherDocumentDialog(
      final Document doc, Document prefferedDoc, boolean forcePreffered) {
    tempprefferedDoc = null;
    if (!eclipseGUI || !showMessages || !showMessages || forcePreffered) {
      return prefferedDoc;
    } else {
      tempprefferedDoc =  RMessages.openSelectDoc(doc, prefferedDoc);
//      IWorkbench w = PlatformUI.getWorkbench();
//      w.getDisplay()
//          .syncExec(
//              new Runnable() {
//                @Override
//                public void run() {
//                  
//                }
//              });
    }
    return tempprefferedDoc;
  }

}
