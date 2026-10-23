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

import static org.apache.tika.sax.XHTMLContentHandler.XHTML;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.DelegatingParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.ContentHandlerDecorator;
import org.apache.tika.sax.EmbeddedContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import com.unitesk.requality.core.log.RLogger;

public class InlineEmbeddedDocumentExtractor extends ParsingEmbeddedDocumentExtractor {

  private static final Parser DELEGATING_PARSER = new DelegatingParser();
  private final ParseContext context;

  public InlineEmbeddedDocumentExtractor(ParseContext context) {
    super(context);
    this.context = context;
  }

  @Override
  public void parseEmbedded(
      InputStream stream, ContentHandler handler, Metadata metadata, boolean outputHtml)
      throws SAXException, IOException {
    if (outputHtml) {
      AttributesImpl attributes = new AttributesImpl();
      attributes.addAttribute("", "class", "class", "CDATA", "package-entry");
      handler.startElement(XHTML, "div", "div", attributes);
    }

    // Use the delegate parser to parse this entry
    TemporaryResources tmp = new TemporaryResources();
    try {
      CloseShieldInputStream csis = new CloseShieldInputStream(stream);
      final TikaInputStream newStream =
          TikaInputStream.get(csis, tmp);
      try{
        if (stream instanceof TikaInputStream) {
          final Object container = ((TikaInputStream) stream).getOpenContainer();
          if (container != null) {
            newStream.setOpenContainer(container);
          }
        }
        DELEGATING_PARSER.parse(
            newStream,
            new EmbeddedContentHandler(new BodyContentHandler(decorateHandler(handler))),
            metadata,
            context);
      }
      finally{
        newStream.close();
        csis.close();
      }
    } catch (TikaException e) {
      RLogger.logError(e);
      // TODO: can we log a warning somehow?
      // Could not parse the entry, just skip the content
    } finally {
      tmp.close();
    }

    if (outputHtml) {
      handler.endElement(XHTML, "div", "div");
    }
  }

  private ContentHandler decorateHandler(ContentHandler handler) {
    return new ContentHandlerDecorator(handler) {
      @Override
      public void startElement(String uri, String localName, String name, Attributes atts)
          throws SAXException {
        if ("table".equals(localName)) {
          AttributesImpl newAttrs;
          if (atts instanceof AttributesImpl) newAttrs = (AttributesImpl) atts;
          else newAttrs = new AttributesImpl(atts);
          //newAttrs.addAttribute("", "frame", "frame", "", "border");
          //newAttrs.addAttribute("", "rules", "rules", "", "all");
          super.startElement(uri, localName, name, newAttrs);
        } else super.startElement(uri, localName, name, atts);
      }
    };
  }
}
