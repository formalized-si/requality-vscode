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

package com.unitesk.requality.core.descriptors;

import com.unitesk.requality.core.operation.document.IDocProcessorWithWizard;
import com.unitesk.requality.document.IDocumentProcessor;

public class DocumentProcessorDescriptor implements Comparable<DocumentProcessorDescriptor> {
  private String name;
  private String id;
  private IDocumentProcessor processor;
  private Object wizard;

  public DocumentProcessorDescriptor(String id, String name, IDocumentProcessor docProc, Object w) {
    this.id = id;
    this.name = name;
    this.processor = docProc;
    if(docProc instanceof IDocProcessorWithWizard)
    	((IDocProcessorWithWizard)processor).setWizard(w);
    wizard = w;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public Object getWizard() {
    return wizard;
  }

  public IDocumentProcessor getProcessor() {
    return processor;
  }

  @Override
  public int compareTo(DocumentProcessorDescriptor o) {
    return name.compareTo(o.getName());
  }
}
