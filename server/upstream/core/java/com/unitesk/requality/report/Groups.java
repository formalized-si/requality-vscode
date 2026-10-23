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
package com.unitesk.requality.report;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.report.template.TemplateScope;

public class Groups {
  private static final QName DESCRIPTION = new QName("description");
  private static final QName REPORT_GROUP = new QName("reportGroup");
  private static final QName NAME = new QName("name");
  private static final QName ORDER = new QName("order");
  private static final QName PARENT = new QName("parent");
  
  private static final QName ID = new QName("id");
  //public static final String DEFAULT_ID = "com.unitesk.requality.reports.requirements";

  private boolean descMode = false;
  private TemplateScope scope;
  private LinkedList<Group> children = new LinkedList<>();

  private String reportPath;
  
  public Groups(String reportPath, TemplateScope scope) throws IOException {
    this.reportPath = reportPath;
    this.scope = scope;
    load(reportPath);
  }

  public String getReportPath() {
    return reportPath;
  }

  public void load(String location) throws IOException {
    File file = new File(URI.create(location + "/" + "groups.xml"));
    if (!file.exists() || !file.isFile()) {
      throw new IOException("File " + file.getPath() + " does not exist.");
    }
    FileInputStream fis = new FileInputStream(file.getPath());
    InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
    try{
      load(isr);
    }
    finally{
      isr.close();
      fis.close();
    }
  }

  public void load(Reader reader) throws IOException {
    Map<String, Object> vars = new HashMap<>();
    String first = "";

    try {
      XMLInputFactory factory = XMLInputFactory.newInstance();
      XMLEventReader eReader = factory.createXMLEventReader(reader);

      while (eReader.hasNext()) {
        String res;
        XMLEvent event = eReader.nextEvent();
        switch (event.getEventType()) {
          case XMLStreamConstants.START_ELEMENT:
            processElem(event.asStartElement(), vars);
            break;
          case XMLStreamConstants.END_ELEMENT:
            processEndElem(event.asEndElement(), vars);
            break;
          case XMLStreamConstants.CHARACTERS:
            if(descMode)
              children.getLast().description.append(event.asCharacters().getData());
            break;
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  private void processEndElem(EndElement elem, Map<String, Object> vars) throws IOException {
     QName name = elem.getName();
    if (name.equals(DESCRIPTION)){
      descMode = false;
    }
    if(descMode){
      children.getLast().description.append(elem.toString());
      return;
    }
   
  }
  private void processElem(StartElement elem, Map<String, Object> vars) throws IOException {
    QName name = elem.getName();
    if (name.equals(DESCRIPTION)){
      descMode = true;
      return;
    }
    javax.xml.stream.events.Attribute attr;
    attr = elem.getAttributeByName(ID);
    String id = attr != null ? attr.getValue() : "";
    attr = elem.getAttributeByName(NAME);
    String rname = attr != null ? attr.getValue() : "";
    attr = elem.getAttributeByName(PARENT);
    String parent = attr != null ? attr.getValue() : "";
    attr = elem.getAttributeByName(ORDER);
    int ord = Integer.MAX_VALUE;
    try{
      ord = attr != null ? Integer.parseInt(attr.getValue()) : Integer.MAX_VALUE;
    }
    catch(NumberFormatException nfe){
      RLogger.logInfo("Order parameter for "+id+" group has incorrect value. Resetted to Maxint");
      ord = Integer.MAX_VALUE;
    }

    // source = new File(folder, source).getAbsolutePath();
    if (name.equals(REPORT_GROUP)) {
      children.add(new Group(id, rname, parent, ord, scope));  
    } 
  }

  /**
   * Copies catalogue into selected place.
   *
   * @param source path to catalogue which must be copied.
   * @param location path to where catalogue must be copied.
   * @return true if coping was successful, false - otherwise.
   */
  public boolean copyDir(String source, String location) {
    //TODO test it
    String fileSeparator = System.getProperty("file.separator");
    source = source.replace("/", fileSeparator);
    location = location.replace("\\", fileSeparator);
    boolean result = true;
    File loc = new File(location);
    if (!loc.exists()) {
      loc.mkdirs();
    }
    File dir = new File(source);
    if (dir.exists() && dir.isDirectory()) {
      File[] files = dir.listFiles();
      for (File file : files) {
        String filePath = file.getPath();
        String locPath = new File(location, file.getName()).getPath();

        if (file.isDirectory()) {
          copyDir(filePath, locPath);
        } else {
          copyFile(filePath, locPath);
        }
      }
    } else {
      System.err.println("Directory " + dir.getPath() + " does not exist.");
      result = false;
    }
    return result;
  }

  /**
   * Copies file into selected place.
   *
   * @param source path to file which must be copied.
   * @param location path to location where file must be copied.
   * @return true if coping successful, false - otherwise.
   */
  public boolean copyFile(String source, String location) {
    boolean result = true;

    try {
      String fileSeparator = System.getProperty("file.separator");
      source = source.replace("/", fileSeparator);
      location = location.replace("\\", fileSeparator);
      File inFile = new File(source);
      if (inFile.exists() && inFile.isFile()) {
        File outFile = new File(location);
        if (outFile.exists()) {
          System.err.println("File " + outFile.getPath() + " already exists.");
          outFile.delete();
        }
        if (!outFile.exists()) {
          RLogger.logDebug("Creating input stream from file: " + inFile.getPath());

          InputStream in = new FileInputStream(inFile);
          OutputStream out = new FileOutputStream(outFile);
          byte[] buf = new byte[1024];
          int len;
          while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
          }
          in.close();
          out.close();
        } else {
          System.err.println("Cannot delete file " + outFile.getPath() + ".");
        }
      } else {
        System.err.println("File " + inFile.getPath() + " does not exist.");
      }
    } catch (Exception e) {
      e.printStackTrace();
      result = false;
    }
    return result;
  }


  public List<Group> getAllGroups() {
    return children;
  }

}
