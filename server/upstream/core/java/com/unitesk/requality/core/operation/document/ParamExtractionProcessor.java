package com.unitesk.requality.core.operation.document;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.XMLEvent;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.nodetype.Requirement;

/** Class for automatically extracting headers into requirements */
public class ParamExtractionProcessor extends SAXDocumentProcessor {
  boolean headerStarted = false;
  StringBuilder stored = new StringBuilder();
  Map<Requirement, Integer> reqLevel = new HashMap<>();
  Stack<Requirement> lastOppened = new Stack<>();
  //Map<Integer, Requirement> startedTags = new HashMap<Integer, Requirement>();
  List<String> createdLocations = new LinkedList<>();
  String lastLocId = null;
  QName listString = new QName("listText");

  public boolean processEvent(
      XMLEvent event, XMLEventFactory eventFactory, XMLEventWriter writer, TreeNode rootNode, PrintStream ps)
      throws Exception {
    //Обработчик отдельных событий по документу - открытие тега, закрытие, текст - все поступает сюда
    //в виде event
    if (event.isStartElement()) { //Если событие это начало тега
      String tagName = getLocalTagName(event); //Получаем имя тега
      int hl = headLevel(tagName);
      if (hl > 0) {
        lastLocId = null;
        writer.add(event);
        headerStarted = true;
        stored.setLength(0);
        Requirement par = null;
        int k = lastOppened.size() - 1;
        while (k >= 0 && par == null) {
          Requirement r = lastOppened.get(k);
          if (hl > reqLevel.get(r)) par = r;
          k--;
        }
        //Тут и ниже - создание отдельного объекта - требования
        //Напоминаю - существует фрагмент текста(выделение тегами в документе),
        //объект - фрагмент(хранится как потомок документа) и объект - требование,
        //которое может быть связано с одним или несколькими фрагментами
        //тут создается последний тип
        Requirement createdReq = null;
        if (par != null) createdReq = createRequirement(null, par);
        else createdReq = createRequirement();
        reqLevel.put(createdReq, hl);
        lastOppened.add(createdReq);
      } else {
        if (tagName.equals("li")) {
          Attribute a = event.asStartElement().getAttributeByName(listString);
          if (a != null) {
            stored.append(a.getValue());
            stored.append(" ");
          }
        }
        writer.add(event);
      }
    } else if (event
        .isEndElement()) { //Обработка закрытия тега, примерно в это время могут закрываться открытые обработчиком теги для фрагмента
      String tagName = getLocalTagName(event);
      int hl = headLevel(tagName);
      if (hl > 0) {
        String reqName = stored.toString().replace("\r", "").replace("\n", "").replace("\t", " ");
        //Получение имени созданного выше требования, объект уже был создан но не назван, его название присваивается здесь
        //Можно было в принципе создавать требование здесь
        if (reqName.length() > 50) reqName = reqName.substring(0, 50);
        int k = lastOppened.size() - 1;
        //Снижение уровня вложенности насколько я помню.
        while (k > 0
            && (lastOppened.get(k).isDisposed()
                || lastOppened.get(k).getName() != null
                || reqLevel.get(lastOppened.get(k)) != hl)) k--;
        Requirement selected = null;
        if (k >= 0) selected = lastOppened.get(k);
        if (selected!=null && (reqLevel.get(selected) != hl || selected.isDisposed() || selected.getName() != null))
          selected = null;
        if (selected != null)
          if (lastLocId != null) {
            //startedTags.remove(hl);
            selected.setName(reqName.trim());
            selected.commitAttributes();
            lastLocId = null;
            //		createdReq = null;
          } else selected.delete();
      }
      writer.add(event);
    } else if (event.isCharacters()) { //Обработка текстовых фрагментов
      String content = event.asCharacters().getData();
      int len = content.trim().replace("\n", "").length();
      int k = lastOppened.size() - 1;
      Requirement createdReq = null;
      while (k >= 0 && (lastOppened.get(k).isDisposed() || lastOppened.get(k).getName() != null))
        k--;
      if (k > -1) createdReq = lastOppened.get(k);
      if (createdReq != null && len > 0)
        lastLocId = startRequirementFragment(createdReq, lastLocId);
      stored.append(content);
      writer.add(event);
      if (createdReq != null && len > 0) endRequirementFragment();
    } else writer.add(event);
    return true;
  }

  private int headLevel(String tagName) {
    if (tagName.length() != 2) return 0;
    char h = tagName.charAt(0);
    if (h != 'h' && h != 'H') return 0;
    int headLevel = tagName.charAt(1) - '0';
    if (headLevel < 1 || headLevel > 8) return 0;
    return headLevel;
  }

  @Override
  protected void initProcess(TreeNode root) {
    headerStarted = false;
    stored.setLength(0);
    reqLevel.clear();
    lastOppened.clear();
    createdLocations.clear();
    lastLocId = null;
  }

  @Override
  protected void stopProcess() {}
}
