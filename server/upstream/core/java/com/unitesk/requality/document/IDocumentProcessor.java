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
package com.unitesk.requality.document;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.util.Map;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.nodetype.Document;

// TODO: (document processor) user's arguments
/**
 * Interface for processors of document in xhtml format. See
 * com.unitesk.requality.eclipse.views.documents.DocumentProcessor as an example of implementation
 */
public interface IDocumentProcessor {
  /**
   * Get numbers of calls of monitor.worked required to complete processing. Triple - for loading,
   * processing and save
   *
   * @return number of monitor.worked
   */
  int[] getCounterMax();

  int[] getCounterValues();

  void setCounterValues(int lc, int pc, int sc);

  /**
   * Set encoding for input stream
   *
   * @param encoding encoding
   */
  void setEncoding(String encoding);

  /**
   * Process document with given id
   *
   * @param docId identifier of document
   * @param rootNode root element of requirements catalog
   * @param pw place to send output from processors
   * @throws IOException
   */
  void process(Document docId, TreeNode rootNode, PrintStream pw, Writer writer, IProgressMonitor monitor) throws IOException;

  /**
   * Provide additional configuration process if needed
   *
   * @param doc document to be processed further
   * @param params map of additional params. Can be provided by gui
   * @param pw strem to output some messages, System.out can be used
   * @return true if document is sucessfully configured
   */
  public boolean configure(Document doc, Map<String, Object> params, PrintStream pw);


}
