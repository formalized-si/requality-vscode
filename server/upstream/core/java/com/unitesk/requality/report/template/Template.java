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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import com.dslplatform.json.DslJson;
import com.dslplatform.json.JsonReader;
import com.unitesk.requality.core.IMultiPartDataProvider;
import com.unitesk.requality.core.MultiPart;
import com.unitesk.requality.core.api.API;
import com.unitesk.requality.core.attribute.AttributeAvailability;
import com.unitesk.requality.core.attribute.AttributeStorage;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.TypedValue;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.storage.NodeMapper;
import com.unitesk.requality.nodetype.ReportSettings;
import com.unitesk.requality.nodetype.Requirement;
import com.unitesk.requality.report.ReportGenerationContext;
import com.unitesk.requality.report.ReportUtils;

public class Template {
  public static String PROJECT_TEMPLATES_FOLDER = ".templates";
  private static final QName DESCRIPTION = new QName("description");

  private static final QName OR = new QName("or");
  private static final QName AND = new QName("and");
  private static final QName ATTRIBUTE = new QName("attribute");
  private static final QName PARAMS = new QName("param");

  private static final QName PARAM_DS = new QName("paramDataSource");

//  private static final QName CONDITION = new QName("condition");
//  private static final QName OP1 = new QName("op1");
//  private static final QName OP2 = new QName("op2");
//  private static final QName OPD = new QName("opd");
  private static final QName GROUP = new QName("group");
  private static final QName TYPE = new QName("type");
  private static final QName DEF_VALUE = new QName("defaultValue");
  private static final QName TEMPLATE = new QName("template");
  private static final QName CP_CONV = new QName("cpSettingsConverter");
  private static final QName DATA_SOURCE = new QName("dataSource");
  private static final QName MULTIPART_PROVIDER = new QName("multiPartSource");
  private static final QName NODE_ID_PATTERN = new QName("nodeIdPattern");
  private static final QName FILE = new QName("file");
  private static final QName DIR = new QName("dir");
  private static final QName NAME = new QName("name");
  private static final QName REPORT = new QName("report");
  private static final QName ORDER = new QName("order");
  private static final QName SOURCE = new QName("source");
  private static final QName LOCATION = new QName("location");
  private static final QName DATACLASS = new QName("class");
  private static final QName READONLY = new QName("read-only");
  private static final QName WITH_STATISTICS = new QName("usesStatistics");
  private static final QName MULTIPARTSUPPORT = new QName("supports-multipart");
  private static final QName GUI = new QName("gui");
  private static final QName BUTTON = new QName("button");
  private static final QName LABEL = new QName("label");
  private static final QName COMMAND = new QName("command");
  private static final QName TEXT = new QName("text");
  private static final QName GLOBAL = new QName("global");
  public static final String DEFAULT_ID = "com.unitesk.requality.reports.tableview";
  private static String ALLOWS_PREFIX = "allows";

  private String reportPath;
  private boolean notExists = false;
  public TemplateScope source;
  // private String description;
  private String group;
  private List<TemplateFile> templates = new ArrayList<>();
  private Map<String, String> dataObjectsClass = new HashMap<>();
  private Map<String, String> multipartObjectsClass = new HashMap<>();
  private Map<String, IMultiPartDataProvider> multipartObjects = null;
  private Map<String, IReportDataProvider> dataObjects = null;

  private Map<Class, Object> initedProviders = new HashMap<>();

  private String id;
  private String reportName;
  private int order = Integer.MAX_VALUE;
  private StringBuilder desc = new StringBuilder();
  private StringBuilder opdesc = new StringBuilder();
  private boolean isGuiStarted, isDescStarted, attrOppened = false;// , opStarted = false;
  private long lastGuiIndex = 0;
  private String cpConvertClass = null;
  private String nodeIdPattern = null;
  private IReportSettingsConverter cpConvertClassInstance = null;
  // id, map of params,including _type == [Button], _command = "command to call on
  // click"
  private Map<String, Map<String, String>> guiMap = new HashMap<>();
  private IReportSettingsConverter conv = null;
  private LinkedList<IDefValueData> settings = new LinkedList<>();
  private boolean readonly = false;
  private boolean withStatistics = false;
  private boolean supportsMutipart = false;
  private IMultiPartDataProvider mpProvider;

  private TemplateFileProcessor processor = null;
  private LinkedList<StoredParamsContainer> storedParamsContainer = new LinkedList<StoredParamsContainer>();
  private Map<String, Boolean> specificParameters = new HashMap<>();
  private static Map<String, Boolean> specificParametersDefValue = new HashMap<>();
  public static String PARAM_SUPPORT_CROSSPROJECT = "allowsCrossProject";
  public static String PARAM_SUPPORT_GLOBAL_NUMERATION = "allowsProjectNumeration";
  public static String PARAM_ROOT_INCLUDED = "allowsRootInGeneration";
  // private LinkedList<ConditionContainer> ccont = new LinkedList<>();
  // private LinkedList<Integer> contlord = new LinkedList<>();
  // TODO check rec params
  // private int contord = 0;

  static {
    specificParametersDefValue.putIfAbsent(PARAM_SUPPORT_CROSSPROJECT, true);
    specificParametersDefValue.putIfAbsent(PARAM_SUPPORT_GLOBAL_NUMERATION, false);
    specificParametersDefValue.putIfAbsent(PARAM_ROOT_INCLUDED, false);
  }

  public boolean getParameterValue(String key) {
    Boolean val = specificParameters.get(key);
    if (val == null)
      val = specificParametersDefValue.get(key);
    return val == null ? false : val;
  }
  private Object valueToJsonOutput(AttributeStorage as, String key, UUID origin, AttributeAvailability scope,
      Object defvalue, ParamTypeDefinition valueType, AttributeType at, TypedValue raw) {
    boolean list = valueType != null;
    at = list ? AttributeType.LIST : at;
    if (defvalue instanceof TypedValue) {
      TypedValue tv = (TypedValue) defvalue;
      defvalue = tv.getValue();
      at = tv.getType();
    }
    return defvalue;
  }
  public void gatherDefValue(IParamsProvider o, Map<String, Object>ret) {
    IParamsProvider[] provs = o.getProviders();
    if (o.isContainer()) {
      for (IParamsProvider prov : provs)
        gatherDefValue(prov, ret);
    }
    StoredParamDefinition spd = (StoredParamDefinition) provs[0];
    ParamTypeDefinition[] s = spd.types;
    ParamTypeDefinition prov = s[s.length - 1];
    Object val = valueToJsonOutput(null, spd.name, null, null, spd.defvalue, prov.commonListValueType,
        prov.commonType, null);
    if(val!=null) {
      String name = spd.name;
      ret.put(name, val);
    }
  }
  
  public Map<String, Object> getDefaultParamsValues(){
    Map<String, Object> ret = null;
    TemplateParamDefinition[] descrs = getParamDescriptions();
    for (TemplateParamDefinition tpd : descrs) {
      Object val = valueToJsonOutput(null, tpd.name, null, null, tpd.defvalue, null,
        null, null);
      String name = tpd.name;
      if(ret == null)
        ret = new HashMap<>();
      ret.put(name, val);//may be null
      if (tpd.attrs != null && tpd.attrs.stored != null) {
        for (IParamsProvider o : tpd.attrs.stored) {
          if(ret == null)
            ret = new HashMap<>();
          gatherDefValue(o, ret);
        }
      }
    }
    return ret == null ? Collections.emptyMap():ret;
  }
    

  public Template(String id, String name, String group, TemplateScope scope) throws IOException {
    this.reportPath = null;
    this.id = id;
    this.reportName = name;
    this.readonly = true;
    this.group = group;
    this.source = scope;
    // load(reportPath);
    notExists = true;
  }

  public Template(String reportPath, TemplateScope scope) throws IOException {
    this.reportPath = reportPath;
    this.source = scope;
    load(reportPath);
  }

  public boolean getNotExists() {
    return notExists;
  }

  public String getReportPath() {
    return reportPath;
  }

  public String load(String location) throws IOException {
    File file = new File(URI.create(reportPath + "/" + "template.xml"));
    if (!file.exists() || !file.isFile()) {
      throw new IOException("File " + file.getPath() + " does not exist.");
    }
    FileInputStream fis = new FileInputStream(file.getPath());
    InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
    try {
      return load(isr);
    } finally {
      isr.close();
      fis.close();

      if (getParameterValue(PARAM_SUPPORT_CROSSPROJECT)) {
        TemplateParamDefinition tpd = new TemplateParamDefinition();
        tpd.defvalue = "false";
        tpd.description = "Flag allowing to enable report generation for related projected(linked by terms or references);";
        tpd.types = new ParamTypeDefinition[] { new ParamTypeDefinition(AttributeType.BOOL) };
        tpd.name = ReportSettings.ATTR_CP;
        settings.add(tpd);
      }

      if (getParameterValue(PARAM_SUPPORT_GLOBAL_NUMERATION)) {
        TemplateParamDefinition tpd = new TemplateParamDefinition();
        tpd.defvalue = "false";
        tpd.description = "Flag allowing to use project-scoped numeration for nodes in report;";
        tpd.types = new ParamTypeDefinition[] { new ParamTypeDefinition(AttributeType.BOOL) };
        tpd.name = ReportSettings.ATTR_GN;
        settings.add(tpd);
      }

      TemplateParamDefinition tpd = new TemplateParamDefinition();
      tpd.defvalue = Requirement.getTypeRootQId();
      tpd.description = "Root requirement used in report generation. Can be ignored by some reports";
      tpd.types = new ParamTypeDefinition[] { new ParamTypeDefinition(AttributeType.TREENODE),
          ParamTypeDefinition.STRING };
      tpd.name = ReportSettings.ATTR_ROOT_REQUIREMENT;
      settings.add(tpd);

    }
  }

  public String load(Reader reader) throws IOException {
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
          res = processElem(event.asStartElement(), vars);
          if (first.length() == 0) {
            first = res;
          }
          break;
        case XMLStreamConstants.END_ELEMENT:
          processEndElem(event.asEndElement(), vars);
          if (isDescStarted) {
            if (event.asEndElement().getName().equals(DESCRIPTION))
              isDescStarted = false;
            else
              desc.append("</" + event.asEndElement().getName().getLocalPart() + ">");
          }
          if (isGuiStarted && event.asEndElement().getName().equals(GUI))
            isGuiStarted = false;
          break;
        case XMLStreamConstants.CHARACTERS:
          if (isDescStarted)
            desc.append(event.asCharacters().getData());
//            if(opStarted)
//              opdesc.append(event.asCharacters().getData());
          break;
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return first;
  }

  public Map<String, Map<String, String>> getGUIElements() {
    return Collections.unmodifiableMap(guiMap);
  }

  public static DslJson<Object> dslJson = new DslJson<>();

  private String processEndElem(EndElement elem, Map<String, Object> vars) throws IOException {
    if (processor != null) {
      if (processor.isEnded())
        processor = null;
      else
        return processor.processEndElem(elem, vars);
    }

    QName name = elem.getName();
//    if(name.equals(OP1)){
//      contlord.pollLast();
//      contord = contlord.peekLast();
//      if(opdesc.length()>0)
//          ((Condition)ccont.peekLast()).setOp1(opdesc.toString());
//      opStarted = false;
//      opdesc.setLength(0);
//    }
//    if(name.equals(OP2)){
//      contlord.pollLast();
//      contord = contlord.peek();
//      if(opdesc.length()>0)
//        ((Condition)ccont.peekLast()).setOp2(opdesc.toString());
//      opStarted = false;
//      opdesc.setLength(0);
//    }
//    if(name.equals(OPD)){
//      if(opdesc.length()>0)
//        ((Condition)ccont.peekLast()).setOperation(opdesc.toString());
//      opStarted = false;
//      opdesc.setLength(0);
//    }
//    if(name.equals(CONDITION))
//    {
//      Condition con = (Condition) ccont.pollLast();//TODO error
//      ccont.peekLast().setContainer(con, contord);
//      contlord.pollLast();
//    }
    if (name.equals(PARAMS))
      storedParamsContainer.pollLast();
    if (name.equals(OR) || name.equals(AND))
      storedParamsContainer.pollLast();
    if (name.equals(ATTRIBUTE)) {
      attrOppened = false;
      settings.pollLast();
    }
    if (name.equals(DEF_VALUE)) {
      IDefValueData tpd = settings.getLast();
      JsonReader<Object> rd = dslJson.newReader(desc.toString());
      rd.read();
      tpd.setDefaultValue(NodeMapper.readAny(null, rd));
      desc.setLength(0);
      isDescStarted = false;
    }
    return null;// TODO remove
  }

  private String processElem(StartElement elem, Map<String, Object> vars) throws IOException {
    if (processor != null) {
      if (processor.isEnded())
        processor = null;
      else
        return processor.processElem(elem, vars);
    }
    javax.xml.stream.events.Attribute attr;
    attr = elem.getAttributeByName(SOURCE);
    String source = attr != null ? attr.getValue() : "";
    attr = elem.getAttributeByName(LOCATION);
    String location = attr != null ? attr.getValue() : "";
    attr = elem.getAttributeByName(DATACLASS);
    String className = attr != null ? attr.getValue() : "";

    String ttype = "ftl";
    if (!source.toLowerCase().endsWith("ftl"))
      ttype = detectType(source);

    QName name = elem.getName();

    if (isDescStarted) {
      desc.append("<" + name.getLocalPart());
      Iterator<Attribute> ita = elem.getAttributes();
      while (ita.hasNext()) {
        Attribute b = ita.next();
        String val = b.getValue();
        if (val != null) {
          desc.append(" ");
          desc.append(b.getName().getLocalPart());
          desc.append("='");
          desc.append(val.replace("\'", "\\\\'"));
          desc.append("'");
        }
      }
      desc.append(">");
      return "";
    }
    if (name.equals(DESCRIPTION)) {
      isDescStarted = true;
      return "";
    }
    if (name.equals(DEF_VALUE)) {
      desc.setLength(0);
      isDescStarted = true;
    }
    if (name.equals(OR)) {
      StoredParamsContainer spc = new StoredParamsContainer(
          com.unitesk.requality.report.template.StoredParamsContainer.Condition.OR);
      storedParamsContainer.peekLast().addParamContainer(spc);
      storedParamsContainer.add(spc);
    }
    if (name.equals(AND)) {
      StoredParamsContainer spc = new StoredParamsContainer(
          com.unitesk.requality.report.template.StoredParamsContainer.Condition.AND);
      storedParamsContainer.peekLast().addParamContainer(spc);
      storedParamsContainer.add(spc);
    }
    if (name.equals(ATTRIBUTE)) {
      StoredParamDefinition spd = new StoredParamDefinition();
      attrOppened = true;
      if (settings.isEmpty())
        throw new RuntimeException("Attribute must be in param tag("
            + (reportPath == null ? "NOPATH" : reportPath) + "/" + (id != null ? id : "NOID") + ")");

      attr = elem.getAttributeByName(new QName("name"));
      if (attr == null)
        throw new IOException("template param must have name attribute");
      spd.name = attr.getValue();
      attr = elem.getAttributeByName(new QName("description"));
      if (attr != null)
        spd.description = attr.getValue();

      attr = elem.getAttributeByName(new QName("default"));
      if (attr != null)
        spd.defvalue = attr.getValue();

      spd.types = new ParamTypeDefinition[] { ParamTypeDefinition.STRING };
      attr = elem.getAttributeByName(TYPE);
      if (attr != null) {
        String val = attr.getValue();
        parseType(spd, val);
      }
      StoredParamsContainer spc = storedParamsContainer.peekLast();
      spc.addParamDefinition(spd);
      settings.add(spd);
    }
    if (name.equals(PARAMS)) {
      TemplateParamDefinition tpd = new TemplateParamDefinition();
      attr = elem.getAttributeByName(new QName("name"));
      if (attr == null)
        throw new IOException("template param must have name attribute");
      tpd.name = attr.getValue();
      attr = elem.getAttributeByName(new QName("description"));
      if (attr != null)
        tpd.description = attr.getValue();

      attr = elem.getAttributeByName(DEF_VALUE);
      if (attr != null)
        tpd.defvalue = attr.getValue();

      tpd.types = new ParamTypeDefinition[] { ParamTypeDefinition.STRING };
      attr = elem.getAttributeByName(TYPE);
      if (attr != null) {
        String val = attr.getValue();
        parseType(tpd, val);
      }
      String vp = null;
      settings.add(tpd);
      storedParamsContainer.add(tpd.attrs);
      // contlord.addLast(0);
      // contord = 0;
    }
    if (name.equals(PARAM_DS)) {
      IDefValueData tld = settings.getLast();
      attr = elem.getAttributeByName(new QName("provider"));
      if (attr != null) {
        String val = attr.getValue();
        if (val == null)
          throw new IOException("template param value provider must have a value if presented");
        String tpvp = val;
        if (tpvp == null)
          throw new IOException("template param value provider with id '" + val + "' was not found");
        if (tpvp != null) {
          // TemplateFileProcessor tfp = tpvp.getSettingsLoader();
          // if(tfp!=null)
          // processor = tfp;
          tld.setDataProvider(tpvp);
        }
      }
    }
//    if(name.equals(OP1)){
//      //contlord.addLast(0);
//      contord = 0;
//      opStarted = true;
//    }
//    if(name.equals(OP2)){
//      //contlord.addLast(1);
//      contord = 1;
//      opStarted = true;
//    }
//    if(name.equals(OPD)){
//      opStarted = true;
//    }
    // source = new File(folder, source).getAbsolutePath();
    if (name.equals(TEMPLATE)) {
      try {
        templates.add(new TemplateFile(ttype, reportPath, source, location, className, readonly));
      } catch (Exception e) {
        e.printStackTrace();
      }
      // processTemplate(source, vars, location);
    } else if (name.equals(FILE)) {
      copyFile(source, location);
    } else if (name.equals(DIR)) {
      copyDir(source, location);
    } else if (name.equals(REPORT)) {
      attr = elem.getAttributeByName(new QName("id"));
      if (attr == null) {
        throw new IOException("report must have id attribute");
      }
      id = attr.getValue();
      attr = elem.getAttributeByName(ORDER);
      try {
        order = attr != null ? Integer.parseInt(attr.getValue()) : Integer.MAX_VALUE;
      } catch (NumberFormatException nfe) {
        RLogger.logInfo("Order parameter for " + id + " template has incorrect value. Resetted to Maxint");
        order = Integer.MAX_VALUE;
      }
      attr = elem.getAttributeByName(GROUP);
      group = attr != null ? attr.getValue() : TemplatesManager.DEF_GROUP;
      attr = elem.getAttributeByName(new QName("name"));
      reportName = attr == null ? id : attr.getValue();
      attr = elem.getAttributeByName(READONLY);
      readonly = attr != null && attr.getValue() != null ? attr.getValue().toLowerCase().equals("true") : false;

      attr = elem.getAttributeByName(NODE_ID_PATTERN);
      nodeIdPattern = attr != null ? attr.getValue() : null;

      attr = elem.getAttributeByName(WITH_STATISTICS);
      withStatistics = attr != null && attr.getValue() != null ? "true".equals(attr.getValue().toLowerCase())
          : false;

      attr = elem.getAttributeByName(MULTIPARTSUPPORT);
      supportsMutipart = attr != null && attr.getValue() != null ? attr.getValue().toLowerCase().equals("true")
          : false;

      attr = elem.getAttributeByName(GLOBAL);
      Iterator<Attribute> ia = elem.getAttributes();
      while (ia.hasNext()) {
        Attribute a = ia.next();
        String lpart = a.getName().getLocalPart();
        if (lpart.startsWith(ALLOWS_PREFIX))
          specificParameters.put(lpart, a.getValue().toLowerCase().equals("true"));
      }
    } else if (name.equals(CP_CONV)) {
      attr = elem.getAttributeByName(DATACLASS);
      if (attr != null)
        cpConvertClass = attr.getValue();
    } else if (name.equals(MULTIPART_PROVIDER)) {
      String clname = "OBJECT_" + dataObjectsClass.size(), cl = null;
      attr = elem.getAttributeByName(NAME);
      if (attr != null)
        clname = attr.getValue();
      attr = elem.getAttributeByName(DATACLASS);
      if (attr != null)
        cl = attr.getValue();
      if (cl != null)
        multipartObjectsClass.put(clname, cl);
    } else if (name.equals(DATA_SOURCE)) {
      String clname = "OBJECT_" + dataObjectsClass.size(), cl = null;
      attr = elem.getAttributeByName(NAME);
      if (attr != null)
        clname = attr.getValue();
      attr = elem.getAttributeByName(DATACLASS);
      if (attr != null)
        cl = attr.getValue();
      if (cl != null)
        dataObjectsClass.put(clname, cl);
    } else if (name.equals(GUI)) {
      guiMap = new HashMap<>();
      Map<String, String> settings = new HashMap<>();
      // TODO settings, for ex. layout
      guiMap.put("settings", settings);
      isGuiStarted = true;
    } else if (name.equals(BUTTON)) {
      attr = elem.getAttributeByName(COMMAND);
      lastGuiIndex++;
      HashMap<String, String> params = new HashMap<>();
      String id = name + "_" + lastGuiIndex;
      params.put("_type", name.getLocalPart());
      params.put("_command", attr != null ? attr.getValue() : "");
      attr = elem.getAttributeByName(TEXT);
      params.put("_text", attr != null ? attr.getValue() : id);
      guiMap.put(id, params);
    } else if (name.equals(LABEL)) {
      attr = elem.getAttributeByName(TEXT);
      lastGuiIndex++;
      HashMap<String, String> params = new HashMap<>();
      String id = name + "_" + lastGuiIndex;
      params.put("_type", name.getLocalPart());
      params.put("_text", attr != null ? attr.getValue() : id);
      guiMap.put(id, params);
    }
    return location;
  }

  private String detectType(String source) {
    if (source == null || source.isEmpty())
      return null;
    source = source.toLowerCase();
    int point = source.lastIndexOf(".");
    if (point > -1)
      return source.substring(point + 1);
    return null;
  }

  private void parseType(IParamValueConsumer tpd, String val) {
    if (val == null)
      return;
    String[] valp = val.split(",");
    ParamTypeDefinition[] types = new ParamTypeDefinition[valp.length];
    int i = 0;
    for (String s : valp)
      types[i++] = parseParamTypeDefinition(s);
    tpd.setTypes(val, types);
  }

  private ParamTypeDefinition parseParamTypeDefinition(String s) {
    s = s.trim();
    String su = s.toUpperCase();
    int ind = s.indexOf("LIST");
    if (ind == 0)
      return new ParamTypeDefinition(AttributeType.LIST,
          parseParamTypeDefinition(s.substring(s.indexOf('[') + 1, s.lastIndexOf("]"))));
    else {
      ind = s.indexOf("MAP");
      if (ind == 0)
        return new ParamTypeDefinition(AttributeType.MAP,
            parseParamTypeDefinition(s.substring(s.indexOf('[') + 1, s.lastIndexOf("]"))));
      else {
        try {
          AttributeType at = AttributeType.valueOf(s);
          if (at != null)
            return new ParamTypeDefinition(at);
        } catch (Exception e) {
        }
        return new ParamTypeDefinition(s);
      }
    }
  }

  /**
   * Copies catalogue into selected place.
   *
   * @param source   path to catalogue which must be copied.
   * @param location path to where catalogue must be copied.
   * @return true if coping was successful, false - otherwise.
   */
  public boolean copyDir(String source, String location) {
    // TODO test it
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
   * @param source   path to file which must be copied.
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

  public static Template fromXml(String path, Reader reader, TemplateScope scope) throws IOException {
    Template result = new Template(path, scope);
    result.load(reader);
    return result;
  }

  public List<TemplateFile> getTemplates() {
    return templates;
  }

  public String getId() {
    return id;
  }

  public boolean getReadOnly() {
    return readonly;
  }

  public boolean getUsesStatistics() {
    return withStatistics;
  }

  public String getName() {
    return reportName;
  }

  public String getSource() {
    return source.toString();
  }

  public int getOrder() {
    return order;
  }

  public IReportSettingsConverter getCPConvertClass() {
    if (cpConvertClass == null)
      return null;
    if (cpConvertClassInstance != null)
      return cpConvertClassInstance;
    try {
      Class c = Class.forName(cpConvertClass);
      cpConvertClassInstance = (IReportSettingsConverter) c.newInstance();
    } catch (ClassNotFoundException cne) {
      RLogger.logError("Class for settings converter is not found! " + cpConvertClass);
      RLogger.logError(cne);
    } catch (InstantiationException e) {
      RLogger.logError("Cannot create instance of settings converter! " + cpConvertClass);
      RLogger.logError(e);
    } catch (IllegalAccessException e) {
      RLogger.logError("Illegall access on settings converter initiation! " + cpConvertClass);
      RLogger.logError(e);
    }
    return cpConvertClassInstance;
  }

  public Map<String, IReportDataProvider> getDataSources() {
    if (dataObjects == null) {
      dataObjects = new HashMap<>();
      for (String s : dataObjectsClass.keySet()) {
        Class c = null;
        for (ClassLoader cl : API.getClassLoaders()) {
          try {
            c = Class.forName(dataObjectsClass.get(s), true, cl);
            if (c != null)
              break;
          } catch (ClassNotFoundException e) {
          }
        }
        if (c != null) {
          try {
            IReportDataProvider rdp = (IReportDataProvider) initedProviders.get(c);
            if (rdp == null) {
              rdp = (IReportDataProvider) c.newInstance();
              initedProviders.put(c, rdp);
            }
            dataObjects.put(s, rdp);

          } catch (InstantiationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
        } else
          RLogger.logError(
              "Class " + s + " was not found for template " + reportName + " [" + reportPath + "]");

      }
    }
    return dataObjects;
  }

  public Map<String, IMultiPartDataProvider> getMultiPartProviders() {
    if (multipartObjects == null) {
      multipartObjects = new HashMap<>();
      for (String s : multipartObjectsClass.keySet()) {
        Class c = null;
        for (ClassLoader cl : API.getClassLoaders()) {
          try {
            c = Class.forName(multipartObjectsClass.get(s), true, cl);
            if (c != null)
              break;
          } catch (ClassNotFoundException e) {
          }
        }
        if (c != null) {
          try {
            if (getDataSources().containsKey(s) && dataObjects.get(s) instanceof IMultiPartDataProvider) {
              mpProvider = (IMultiPartDataProvider) dataObjects.get(s);
            } else {
              IMultiPartDataProvider rdp = (IMultiPartDataProvider) initedProviders.get(c);
              if (rdp == null) {
                rdp = (IMultiPartDataProvider) c.newInstance();
                initedProviders.put(c, rdp);
              }
              multipartObjects.put(s, rdp);
              mpProvider = rdp;
            }
          } catch (InstantiationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
        } else
          RLogger.logError(
              "Class " + s + " was not found for template " + reportName + " [" + reportPath + "]");

      }
    }
    return multipartObjects;
  }

  public String getGroup() {
    return group == null ? TemplatesManager.DEF_GROUP : group;
  }

  public String getDescription() {
    return desc.toString();
  }

  public TemplateParamDefinition[] getParamDescriptions() {
    return settings.toArray(new TemplateParamDefinition[0]);
  }

  public boolean getSupportsMultiPartMode() {
    return supportsMutipart;
  }

  public MultiPart getMultiPartProvider(Requirement root, ReportUtils utils, ReportGenerationContext context,
      MultiPart defautValue) {
    if (mpProvider == null && dataObjects == null)
      getDataSources();// preload data objs
    if (mpProvider == null) {
      if (!multipartObjectsClass.isEmpty())
        getMultiPartProviders();
    }
    if (mpProvider != null) {
      return mpProvider.getMultiPart(root, utils, context);
    }
    return defautValue;
  }

  public String getNodeIdPattern() {
    return nodeIdPattern;
  }
}
