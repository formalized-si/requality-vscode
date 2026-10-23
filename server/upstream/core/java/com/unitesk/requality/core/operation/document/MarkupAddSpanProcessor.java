package com.unitesk.requality.core.operation.document;

import java.io.PrintStream;
import java.util.Iterator;
import java.util.List;

import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.document.LocFragment;
import com.unitesk.requality.nodetype.Document;

public class MarkupAddSpanProcessor extends SAXDocumentProcessor {
  List<LocFragment> mup = null;
  Iterator<LocFragment> it = null;
  LocFragment next = null, lastnext = null;
  private boolean bodyActive = false, ttEnabled = false;
  private boolean isEven = false;
  private int pos = 0;
//  LocFragment nextStarted = null;
//  LocFragment nextEnded = null;
  public MarkupAddSpanProcessor(List<LocFragment> proc) {
    super();
    mup = proc;
  }


  private Iterator<LocFragment> getIterator() {
    if(it == null)
      it = mup.iterator();
    return it;
  }

  @Override
  protected void stopProcess() {

  }

  @Override
  protected void initProcess(TreeNode root) {
    if(mup == null)
      mup = ((Document)root).getMarkupFragments();
  }
  @Override
  protected boolean processEvent(XMLEvent event, XMLEventFactory eventFactory, XMLEventWriter writer,
      TreeNode rootNode, PrintStream ps) throws Exception {
    boolean isStart = event.isStartElement();
    boolean isEnd = event.isEndElement();
    boolean ignoreAdd = false;
    if(isStart) {
      StartElement se = event.asStartElement();
      if(se.getName().getLocalPart().toLowerCase().equals("body")) {
        bodyActive = true;
      }
    } else if (isEnd) {
      
    } else {
      if(event.isCharacters()) {
        Characters ch = event.asCharacters();
        String chData = ch.getData();
//        if(!ttEnabled)
//          chData = chData.replaceAll("\s\s", " ");
        if(bodyActive) {
          int posEnd = pos+chData.length();
          int posInit = pos;
          boolean handled = true;
          int cnt = 0;
          while(handled && pos<posEnd) {
            handled = false;
            if(next == null) {
              next = getNext();
              isEven = !isEven;
            }
            if(next!=null) {
              boolean fragmentStartsHere = next.fragment[0] < posEnd;
              boolean fragmentEndsHere = next.fragment[1]>=pos;
              if(fragmentStartsHere && fragmentEndsHere) {
                handled = true;
                ignoreAdd = true;
                lastnext = next;
                int limstart = Math.max(next.fragment[0]-posInit,pos-posInit);
                String charStart = chData.substring(pos-posInit, limstart);
                if(charStart.length()>0)
                  writer.add(eventFactory.createCharacters(charStart));
                startFragmentSpan(next, eventFactory, writer, isEven);
                pos = limstart+posInit;
                //nextEnded = next;
                handled = true;
                ignoreAdd = true;
                int limend = Math.min(next.fragment[1]-posInit, posEnd-posInit);
                charStart = chData.substring(pos-posInit, limend);
                if(charStart.length()>0)
                  writer.add(eventFactory.createCharacters(charStart));
                endFragmentSpan(next, eventFactory, writer);
                pos = limend+posInit;
                lastnext = next;
                if(pos>=next.fragment[1]) {
                  next = null;
                }
                //nextStarted = null;
              }
            }
          }
          if(pos<posEnd) {
            if(ignoreAdd)
              writer.add(eventFactory.createCharacters(chData.substring(pos-posInit, posEnd-posInit)));
            pos = posEnd;
          }
        }
      }
    }
    if(!ignoreAdd) {
      writer.add(event);
    }
    return true;
  }


  private void startFragmentSpan(LocFragment lf, XMLEventFactory eventFactory, XMLEventWriter writer, boolean isEven) throws XMLStreamException {
    StartElement se = eventFactory.createStartElement("", "", "span");
    writer.add(se);
    writer.add(eventFactory.createAttribute("class", "fragment"+(isEven?" even":"")+" location_"+lf.loc.getSUUId()));
  }
  private void endFragmentSpan(LocFragment lf, XMLEventFactory eventFactory, XMLEventWriter writer) throws XMLStreamException {
    EndElement se = eventFactory.createEndElement("", "", "span");
    writer.add(se);
  }


  private boolean isInRange(int pos, int posStart, int posEnd) {
    return pos >= posStart && pos <= posEnd;
  }


  private LocFragment getNext() {
    if(next == null && getIterator().hasNext())
      next = getIterator().next();
    return next;
  }
}
