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
package com.unitesk.requality.report.template;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;

import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.ReportSettings;
import com.unitesk.requality.tool.BracesHanlder;
import com.unitesk.requality.tool.IVariableValueProvider;

public class TemplateFile {
  private IReportDataSource dataObject;
  private String templateType;
  private String templatePath;
  private String outputFile;
  private String dataClass;
  private String reportPath;
  private boolean readOnly;

  public TemplateFile(
      String type,
      String reportPath,
      String templatePath,
      String outputFile,
      String dataClass,
      boolean readOnly) {
    super();
    this.templateType = type;
    this.reportPath = reportPath;
    this.templatePath = templatePath;
    this.outputFile = outputFile;
    this.dataClass = dataClass;
    this.readOnly = readOnly;
  }

  public String getDataClass() {
    return dataClass;
  }

  public String getTemplatePath() {
    return templatePath;
  }
  
  public String getOutputFileName(String altOutputFilename, TreeDB db, TreeNode rootReq, ReportSettings report) {
    //TODO add more replaces
    String outputFile = this.outputFile;
    if(altOutputFilename!=null)
    	outputFile = altOutputFilename;
    if(db!=null)
      return BracesHanlder.process(outputFile, new IVariableValueProvider() {
        @Override
        public String getValue(String key) {
          switch(key) {
            case "project_name": return db.getLocalName();
            case "project_proj_name": return db.getProjectName();
          }
          if(key.startsWith("node") && rootReq != null)
          {
            String val = rootReq.attrs().getStringAttributeValue(key.substring(4));
            if(val!=null)
              return val;
            if(key.charAt(4) == '_') {
              val = rootReq.attrs().getStringAttributeValue(key.substring(5));
              if(val!=null)
                return val;
            }
          }
          if(key.startsWith("report") && report != null)
          {
            String val = report.attrs().getStringAttributeValue(key.substring(6));
            if(val!=null)
              return val;
            if(key.charAt(6) == '_' && rootReq != null) {
              val = rootReq.attrs().getStringAttributeValue(key.substring(7));
              if(val!=null)
                return val;
            }
          }
          return null;
        }
	});
    return outputFile;
  }

  public boolean getReadOnly() {
    return readOnly;
  }

  public IReportDataSource instanceDataObject()
      throws InstantiationException, IllegalAccessException, ClassNotFoundException {
    return instanceDataObject(ClassLoader.getSystemClassLoader());
  }

  public IReportDataSource instanceDataObject(ClassLoader baseClassLoader)
      throws InstantiationException, IllegalAccessException, ClassNotFoundException {
    if (dataObject != null) {
      return dataObject;
    }
    URL classPaths[] = null;
    try {
      classPaths = new URL[] {new File(URI.create(reportPath + "/" + "classes")).toURI().toURL()};
    } catch (MalformedURLException e) {
      e.printStackTrace();
    }

    URLClassLoader cl = URLClassLoader.newInstance(classPaths, baseClassLoader);
    @SuppressWarnings("unchecked")
    Class<IReportDataSource> ds = (Class<IReportDataSource>) cl.loadClass(getDataClass());
    try {
      cl.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
    dataObject = ds.newInstance();
    return dataObject;
  }

  public void setDataObject(IReportDataSource dataObject) {
    this.dataObject = dataObject;
  }

  public String getType() {
    return templateType;
  }
}
