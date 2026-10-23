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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.OfficeParser;
import org.apache.tika.parser.microsoft.SummaryExtractor;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
public class WordDocParser extends OfficeParser {

  private static final long serialVersionUID = -7303396691077710455L;

  @Override
  public void parse(
      InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
      throws IOException, SAXException, TikaException {
    XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
    xhtml.startDocument();
    DirectoryNode root = null;
    Closeable ca = null,ca2 = null;
    POIFSFileSystem mustCloseFs = null;
    TikaInputStream tstream = TikaInputStream.cast(stream);
    POIFSFileSystem fs = null;
    try{
      if (tstream == null) {
        mustCloseFs = new POIFSFileSystem(new CloseShieldInputStream(stream));
        root = mustCloseFs.getRoot();
      } else {
          final Object container = tstream.getOpenContainer();
          if (container instanceof POIFSFileSystem) {
              root = ((POIFSFileSystem) container).getRoot();
          } else if (container instanceof DirectoryNode) {
              root = (DirectoryNode) container;
          } else {
              if (tstream.hasFile()) {
                  fs = new POIFSFileSystem(tstream.getFile(), true);
              } else {
                  fs = new POIFSFileSystem(new CloseShieldInputStream(tstream));
              }
              //tstream will close the fs, no need to close this below
              tstream.setOpenContainer(fs);
              root = fs.getRoot();
  
          }
      }

      // Parse summary entries first, to make metadata available early
      new SummaryExtractor(metadata).parseSummaries(root);
  
      // Parse remaining document entries
      POIFSDocumentType type = POIFSDocumentType.detectType(root);
  
      if (type != POIFSDocumentType.UNKNOWN) {
        metadata.set(Metadata.CONTENT_TYPE, type.toString());
      }
  
      switch (type) {
        case WORDDOCUMENT:
          new WordDocExtractor(context, metadata).parse(root, xhtml);
          break;
        default:
          break;
      }
      if(root instanceof Closeable)
        ((Closeable)root).close();
      xhtml.endDocument();
    }finally{
      if(fs!=null)
        fs.close();
      if(tstream!=null)
        tstream.close();
      if(ca!=null)
        ca.close();
      if(ca2!=null)
        ca2.close();
      if(root!=null && (root instanceof Closeable))
        ((Closeable)root).close();
      IOUtils.closeQuietly(mustCloseFs);
    }
  }
}
