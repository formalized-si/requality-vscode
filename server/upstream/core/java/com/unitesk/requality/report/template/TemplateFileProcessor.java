package com.unitesk.requality.report.template;

import java.io.IOException;
import java.util.Map;

import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;

public interface TemplateFileProcessor {
  String processElem(StartElement elem, Map<String, Object> vars) throws IOException;
  String processEndElem(EndElement elem, Map<String, Object> vars) throws IOException;
  boolean isEnded();
}
