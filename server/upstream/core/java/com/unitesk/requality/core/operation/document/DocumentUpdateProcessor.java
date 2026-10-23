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

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Document;
/** Abstract class for document update processor */
public abstract class DocumentUpdateProcessor extends DocumentProcessor {

  private static final Object NEW_DOCUMENT = "newDocument";
  private TreeDB db;
  private Document oldDocument;

  @Override
  protected boolean process(Document doc, Reader reader, Writer writer, TreeNode rootNode,PrintStream ps)
      throws IOException {
    Reader readerold = null;
    boolean b = false;
    try {
      String htmlName = oldDocument.getResourceName();
      readerold = new InputStreamReader(oldDocument.getResourceContent(htmlName), encoding);
      b = process(oldDocument, doc, readerold, reader, writer, rootNode,ps);
    } finally {
      if(readerold!=null)
        readerold.close();
    }
    ps.append("Processing completed");
    return b;
  }

  protected abstract boolean process(
      Document oldDoc,
      Document doc,
      Reader readerold,
      Reader reader,
      Writer writer,
      TreeNode rootNode, PrintStream ps);

  @Override
  public boolean configure(final Document doc, Map<String,Object> params, PrintStream pw) {
    return configure(doc, (Document)params.get(NEW_DOCUMENT));
  }
  /*
   * Preferred to call this method, especially in console mode
   */
  public boolean configure(final Document doc, final Document prefdoc) {
    return configure(doc, prefdoc, false);
  }

  public boolean configure(final Document doc, final Document prefdoc, boolean forcePreffered) {
    this.db = doc.getTreeDB();
    oldDocument = showSelectAnotherDocumentDialog(doc, prefdoc, forcePreffered);
    return oldDocument != null;
  }
}
