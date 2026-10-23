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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.CharArrayWriter;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.DocumentSelector;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.txt.CharsetDetector;
import org.apache.tika.sax.ContentHandlerDecorator;
import org.htmlcleaner.CleanerProperties;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.core.model.Pair;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.operation.IProgressMonitor;
import com.unitesk.requality.core.storage.OFlag;
import com.unitesk.requality.core.storage.formatversion.IInputStreamProvider;
import com.unitesk.requality.nodetype.Document;

import net.arnx.wmf2svg.gdi.svg.SvgGdi;
import net.arnx.wmf2svg.gdi.wmf.WmfParser;

public class OmnivorousImporter {

  private ParseContext context;
  private Parser parser;
  private ImageSavingParser embeddedParser;
  //private File embeddedResourceFolder;
  private TreeNode doc;

  private static class SupportedDocumentSelector implements DocumentSelector {
    public boolean select(Metadata metadata) {
      String type = metadata.get(Metadata.CONTENT_TYPE);
      return type != null && (type.startsWith("application/") || type.startsWith("image/"));
    }
  }

  public void importDocument(Document doc, File inFile, Map<String, Document> docFiles, String outFileName, TreeDB base, TreeNode node, PrintStream ps)
      throws Exception {
      IInputStreamProvider fisp = new IInputStreamProvider() {
  @Override
    public InputStream getStream() {
        try {
          return new FileInputStream(inFile);
        } catch (FileNotFoundException e) {
          RLogger.logError(e);
        }
        return null;
      }
    };
    importDocument(doc, inFile.getName(), fisp, inFile.getParentFile(), docFiles, outFileName, base, null, node, ps);
  }

  public void importDocument(Document doc, String filename, IInputStreamProvider filestream, File htmlParent, Map<String, Document> docFiles, String outFileName, TreeDB base, IProgressMonitor pm, TreeNode node, PrintStream ps)
      throws Exception {
    //TODO more proper monitor handling
    boolean html = filename.toLowerCase().endsWith("htm")||filename.toLowerCase().endsWith("htm")
        ||filename.toLowerCase().endsWith("xhtml")||filename.toLowerCase().endsWith("html");
    MediaType mimeType = html?MediaType.TEXT_PLAIN:null;
    if(mimeType == null)
    try (InputStream is = filestream.getStream()) {
      mimeType = getType(is);
    }
    if (html || mimeType == MediaType.TEXT_PLAIN
        && !filename.endsWith(".pdf")) {
      Pair<String, TagNode> cret = getCharset(filestream);
      DocumentImporter htmlImporter = new DocumentImporter();
      htmlImporter.importDocument(doc, filename, filestream, htmlParent, docFiles, cret.getSecond(), outFileName, base, node, cret.getFirst(),pm, ps);
    } else {
      lateCss = null;
      WordDocExtractor.lateCssStyles = null;
      // InputStream in = null;
      OutputStream out = null;
     // Writer writer = null;
      try {
        //out = new FileOutputStream(outFile);

        String s = 
            importDOC(
                filestream, doc);
        if (s != null) {
          ByteArrayInputStream bis =  new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
          try{
            doc.setResourceContent(outFileName, bis, OFlag.NONE);
          }finally{
            bis.close();
          }
       //   writer = new OutputStreamWriter(out, "UTF-8");
      //    writer.write(s);
        }
      } catch (FileNotFoundException e) {
        e.printStackTrace();
      } catch (IOException e) {
        e.printStackTrace();
      } finally {
        // in.close();
       // if (writer != null) writer.close(); // writer closes underlying stream 'out'
      //  if (out != null) out.close();
      }
    }
  }

  public String importDOC(IInputStreamProvider inFile, TreeNode doc) throws IOException {
    CharArrayWriter caw = null;
    try {
      caw = new CharArrayWriter();
      //embeddedResourceFolder = erf;
      try(InputStream is = inFile.getStream()){
        parserSetup(doc, is);
      }
      boolean error = true;
      try(InputStream is = inFile.getStream()){
        importStream(doc, is, caw, new Metadata());
        error = false;
      }catch(Exception e) {
        error = true;//first attempt can be incorrect
      }
      if(error)
      {
        caw.reset();
        fallbackToGenericParser();
        try(InputStream is = inFile.getStream()){
          try {
            importStream(doc, is, caw, new Metadata());
          } catch (Exception e) {
            RLogger.logError(e);
          }
        }
      }
      String s = new String(caw.toCharArray());
      if (lateCss == null)
        lateCss = "";
      s = s.replace("${[cssclasses]}", lateCss);
      return s;
    } finally {
      if(caw!=null)
        caw.close();
    }
  }
  public static InputStream getStream(InputStream f, String charset) throws FileNotFoundException{
    final Charset c = Charset.forName(charset);
    byte tagstart = c.encode("<").get(), tagend = c.encode(">").get(), space = c.encode(" ").get(), isclosed = c.encode("/").get(), q1 = c.encode("'").get(), q2 = c.encode("\"").get();
    BufferedInputStream fr = new BufferedInputStream(f){
      @Override
      public int read(byte[] b) throws IOException {
        throw new EOFException();
      }
      byte[]extra = null;
      int extraoffset = 0, extralen = -1;
      String oTag = null;
      String tagName = null;
      int ts = -1, ic = -1, cq = 0, cq2 = 0;
      LinkedList<Integer> pos = new LinkedList<>();
      @Override
      public synchronized int read(byte[] b, int off, int len) throws IOException {
        int startoff = off;
        int cut = 0;
        if(extra!=null){
          for(int i = 0;i<extralen;i++)
            b[i+off] = extra[i+extraoffset];
          off+=extralen;
          if(off+len>b.length){
            cut+=extralen;
            len-=extralen;
          }
        }
        int read = super.read(b,off,len);
        if(read<len)
          cut = 0;
        int ret = read==-1?extralen:extralen!=-1?(read+extralen):read;
        if(ret == -1)
          return ret;
        extralen = -1;
        pos.clear();
        for(int i = startoff;i<read;i++){
          if(b[i] == tagstart)
            ts = i;
          if(tagName == null && ts>-1 && b[i] == space){
            if(oTag!=null)
              tagName = oTag+new String(b,0,i);
            else
              tagName = new String(b, ts+1, i-ts-1, c);
          }
          if(b[i] == q1)
            cq++;
          if(b[i] == q2)
            cq2++;
          if(b[i] == isclosed && cq % 2 == 0 && cq2 % 2 == 0){
            if(tagName == null){
              if(oTag!=null)
                tagName = oTag+new String(b,0,i);
              else
                tagName = new String(b, ts+1, i-ts-1, c);
            }
            ic = i;
          }
          if(b[i] == tagend)
          {
            if(tagName == null){
              if(oTag!=null)
                tagName = oTag+new String(b,0,i);
              else
                tagName = new String(b, ts+1, i-ts-1, c);
            }
            if(tagName.toLowerCase().equals("frame") && ic==-1){
              pos.add(i);
            }
            oTag = null;
            tagName = null;
            ts = -1;
            ic = -1;
          }
        }
        if(tagName == null && ts!=-1)
          oTag = new String(b, ts+1, len-ts-1, c);
        extra = null;
        extralen = -1;
        extraoffset = 0;
        int poss = pos.size();
        int diff = poss+cut+ret-len;
        if(diff>0){
          extra = new byte[diff];
          extralen = diff;
          extraoffset = 0;
          //some stream part was cut
        }

        if(cut>0){
          for(int i = 0;i<cut;i++)
            extra[poss+i] = b[b.length-cut+i+1];
        }
        if(poss>0){
          int carret = ret;
          ret+=poss;
          Integer work = pos.pollLast();
          while(carret>startoff && work!=-1){
            carret--;
            int newind = carret+pos.size()+1;
            if(newind>=len){
              extra[newind-len] = b[carret];
            }else
              b[newind] = b[carret];
            if(carret == work)
            {
              newind--;
              if(newind>=len){
                extra[newind-len] = isclosed;
              }else
                b[newind] = isclosed;
              if(pos.size()==0)
                work = -1;
              else
                work = pos.pollLast();
            }
          }
        }
        return ret;
      }
      @Override
      public synchronized int read() throws IOException {
        throw new EOFException();
      }
      @Override
      public synchronized int available() throws IOException {
        // TODO Auto-generated method stub
        return super.available()+extralen;
      }
    };
    return fr;
  }

  public static Pair<String, TagNode> getCharset(IInputStreamProvider isprov) throws IOException {
//    CleanerProperties properties = new CleanerProperties();
//    properties.setAdvancedXmlEscape(true);
//    properties.setTranslateSpecialEntities(true);
//    properties.setRecognizeUnicodeChars(true);
//    properties.setUseEmptyElementTags(false);
//    properties.setNamespacesAware(true);
//    properties.setUseCdataForScriptAndStyle(true);
//    properties.setOmitDeprecatedTags(false);
//    properties.setOmitDoctypeDeclaration(false);
//    properties.setCharset("UTF-8");
    HtmlCleaner cleaner = getCharsetCleaner();
    TagNode root = null;
    FileInputStream fr = null;
    try {
      try(InputStream sourceFile = isprov.getStream()){
        root=cleaner.clean(sourceFile,"UTF-8");
      }
      List<? extends TagNode> elements = root.getElementListHavingAttribute("CONTENT", true);
      for (TagNode node : elements) {
        String content = node.getAttributeByName("CONTENT").toLowerCase();
        if (content.contains("charset=")) {
          String charset = content.substring(content.indexOf("charset=") + "charset=".length());
          InputStream fr2 = null;
          try(InputStream sourceFile = isprov.getStream()){
            fr2  = getStream(sourceFile, charset);
            return new Pair<>(charset,cleaner.clean(fr2,charset));
          }
          catch(Exception e){
            RLogger.logError(e);
          }
          finally{
            if(fr2!=null)
              fr2.close();
          }
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
      throw e;
    } catch (NullPointerException ne) {
    }
    CharsetDetector cd = new CharsetDetector();
    try(InputStream sourceFile = isprov.getStream()){
      try(TikaInputStream tis = TikaInputStream.get(sourceFile)){
        cd.setText(tis);
      }
    }
    String charset = cd.detect().getName();
    InputStream fr2 = null;
    try(InputStream sourceFile = isprov.getStream()){
      fr2 = getStream(sourceFile, charset);
      return new Pair<>(charset,cleaner.clean(fr2,charset));
    }
    finally{
      if(fr2!=null)
      fr2.close();
    }
  }
  private static HtmlCleaner ccleaner = null;
  private static HtmlCleaner getCharsetCleaner(){
    if(ccleaner == null){
      CleanerProperties properties = new CleanerProperties();
      properties.setAdvancedXmlEscape(true);
      properties.setTranslateSpecialEntities(true);
      properties.setRecognizeUnicodeChars(true);
      properties.setUseEmptyElementTags(false);
      properties.setNamespacesAware(true);
      properties.setUseCdataForScriptAndStyle(true);
      properties.setOmitDeprecatedTags(false);
//      properties.setOmitDoctypeDeclaration(false);
      properties.setOmitDoctypeDeclaration(true);
      ccleaner = new HtmlCleaner(properties);
    }
    return ccleaner;
  }
  public static String getCharset(String sourceFile) throws IOException {
    
    try {
      TagNode root = getCharsetCleaner().clean(sourceFile);
      List<? extends TagNode> elements = root.getElementListHavingAttribute("CONTENT", true);
      for (TagNode node : elements) {
        String content = node.getAttributeByName("CONTENT").toLowerCase();
        if (content.contains("charset=")) {
          String charset = content.substring(content.indexOf("charset=") + "charset=".length());
          return charset;
        }
      }

    } catch (NullPointerException ne) {
    }
    return "UTF-8";
  }

  private static MimeTypes detector = new MimeTypes();
  private MediaType getType(InputStream file) throws FileNotFoundException, IOException {
    try(TikaInputStream tis = TikaInputStream.get(file)) {
      MediaType type = detector.detect(tis, new Metadata());
      return type;
    }
  }

  private void parserSetup(TreeNode doc, InputStream file) throws IOException {
    MimeTypes detector = new MimeTypes();
    this.doc = doc;
    try (TikaInputStream tis = TikaInputStream.cast(file)){
      MediaType type = detector.detect(tis, new Metadata());
//      setType(metadata, type.getType());
      if (MediaType.OCTET_STREAM.equals(type)) {
//        POIFSFileSystem filesystem = null;
        try {
//          filesystem = new POIFSFileSystem(TikaInputStream.cast(file));
          parser = new WordDocParser();
          embeddedParser = new ImageSavingParser(new WordDocParser(), doc);
          context = new ParseContext();
          context.set(DocumentSelector.class, new SupportedDocumentSelector());
          context.set(Parser.class, embeddedParser);
          context.set(EmbeddedDocumentExtractor.class, new InlineEmbeddedDocumentExtractor(context));
        } catch (Exception e) {
          fallbackToGenericParser();
        }
        finally{
//          if(filesystem!=null)
            try {
//              filesystem.close();
            }catch(Exception e) {
              RLogger.logDebug(e,3);
            }
        }
      } else fallbackToGenericParser();
    }
  }

  private void fallbackToGenericParser() {
    parser = new AutoDetectParser();
    embeddedParser = new ImageSavingParser(new AutoDetectParser(), doc);
    context = new ParseContext();
    context.set(DocumentSelector.class, new SupportedDocumentSelector());
    context.set(Parser.class, embeddedParser);
    context.set(EmbeddedDocumentExtractor.class, new InlineEmbeddedDocumentExtractor(context));
  }

  private void importStream(TreeNode doc, InputStream input, Writer target, Metadata md) throws IOException, SAXException, TikaException, TransformerConfigurationException {
    try {
      ContentHandler handler = getContentHandler(doc, target);
      parser.parse(input, handler, md, context);
    } finally {
      input.close();
    }
  }

  private String lateCss = null;

  private ContentHandler getContentHandler(TreeNode doc, Writer writer) throws TransformerConfigurationException {
    SAXTransformerFactory factory = (SAXTransformerFactory) SAXTransformerFactory.newInstance();
    TransformerHandler handler = factory.newTransformerHandler();
    Transformer t = handler.getTransformer();
    t.setOutputProperty(OutputKeys.METHOD, "xml");
    t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    t.setOutputProperty(OutputKeys.INDENT, "yes");
    handler.setResult(new StreamResult(writer));
    return new ContentHandlerDecorator(handler) {
      @Override
      public void startElement(String uri, String localName, String name, Attributes atts)
          throws SAXException {
        if ("img".equals(localName) || "object".equals(localName)) {
          AttributesImpl newAttrs;
          if (atts instanceof AttributesImpl) {
            newAttrs = (AttributesImpl) atts;
          } else {
            newAttrs = new AttributesImpl(atts);
          }

          for (int i = 0; i < newAttrs.getLength(); i++) {
            if ("src".equals(newAttrs.getLocalName(i)) || "data".equals(newAttrs.getLocalName(i))) {
              String src = newAttrs.getValue(i);
              if (src.startsWith("embedded:")) {
                String filename = src.substring(src.indexOf(':') + 1);
                try {
                  String img = embeddedParser.requestSave(filename);
                  String newSrc = img;//TODO
                  newAttrs.setValue(i, newSrc);
                } catch (IOException e) {
                  System.err.println("Error creating temp image file " + filename);
                }
              }
            }
          }
          super.startElement(uri, localName, name, newAttrs);
        } else super.startElement(uri, localName, name, atts);
        if ("head".equals(localName)) {
          super.startElement(uri, "style", "style", new AttributesImpl());
          String val = "${[cssclasses]}";
          super.characters(val.toCharArray(), 0, val.length());
          super.endElement(uri, "style", "style");
        }
      }

      @Override
      public void endElement(String arg0, String arg1, String arg2) throws SAXException {
        if ("html".equals(arg2) && WordDocExtractor.lateCssStyles != null)
          lateCss = WordDocExtractor.lateCssStyles;
        super.endElement(arg0, arg1, arg2);
      };

      @Override
      public void characters(char[] chars, int start, int length) {
        try {
          String line = new String(chars).substring(start, start + length);
          if (line.equals("\n")) {
            super.startElement("", "br", "br", null);
            super.endElement("", "br", "br");
          } else {
            String[] parts = line.split("\n");
            if (parts.length == 1 && line.contains("\n")) {
              super.characters(parts[0].toCharArray(), 0, parts[0].length());
              super.startElement("", "br", "br", null);
              super.endElement("", "br", "br");
            } else if (line.contains("\n")) {
              for (int i = 0; i < parts.length - 1; i++) {
                String part = parts[i];
                super.characters(part.toCharArray(), 0, part.length());
                super.startElement("", "br", "br", null);
                super.endElement("", "br", "br");
              }
              if (parts.length > 0) {
                String part = parts[parts.length - 1];
                super.characters(part.toCharArray(), 0, part.length());
              }
            } else if (chars.length == 1
                && Character.UnicodeBlock.of(chars[0]) == Character.UnicodeBlock.PRIVATE_USE_AREA) {
              chars[0] = fixChar(chars[0]);
              super.characters(chars, 0, line.length());
            } else super.characters(chars, start, length);
          }
        } catch (SAXException e) {
          e.printStackTrace();
        }
      }
    };
  }

  private char fixChar(char c) {
    return (char) (c & 0x00ff);
  }

  public static void main(String[] args) throws Exception {
    OmnivorousImporter ole = new OmnivorousImporter();
    ole.importDocument(null, new File(args[0]), Collections.emptyMap(), args[1], null, null,System.out);
  }

  private class ImageSavingParser implements Parser {
    private static final long serialVersionUID = -5118411199480257743L;
    private Map<String, String> wanted = new HashMap<>();
    private Parser downstreamParser;
    private TreeNode doc;

    private ImageSavingParser(Parser downstreamParser, TreeNode doc) {
      this.downstreamParser = downstreamParser;
      this.doc = doc;
    }

    public String requestSave(String embeddedName) throws IOException {
      String file = "embedded/" + embeddedName;
      wanted.put(embeddedName, file);
      return file;
    }

    public Set<MediaType> getSupportedTypes(ParseContext context) {
      return null;
    }

    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
        throws IOException, SAXException, TikaException {
      String name = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
      RLogger.logDebug("Embedded parser: parsing " + name);
      if (name != null && wanted.containsKey(name)) {
        doc.setResourceContent(wanted.get(name), stream, OFlag.NONE);
        if (metadata.get(Metadata.CONTENT_TYPE).contains("wmf")) convertWMF2PNG(doc, wanted.get(name));
      } else {
        if (downstreamParser != null) {
          downstreamParser.parse(stream, handler, metadata, context);
        }
      }
    }

    public void convertWMF2PNG(TreeNode ndoc, String fileS) throws IOException {
      FileOutputStream out = null;
      File tmp = File.createTempFile("requality-temp", (new File(fileS)).getName());
      InputStream in = ndoc.getResourceContent(fileS);
      try {
        WmfParser parser = new WmfParser();
        final SvgGdi gdi = new SvgGdi();
        parser.parse(in, gdi);
        in.close();
        
        org.w3c.dom.Document doc = gdi.getDocument();
        out = new FileOutputStream(tmp);
        TransformerFactory factory = TransformerFactory.newInstance();
        Transformer transformer = factory.newTransformer();
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, "-//W3C//DTD SVG 1.0//EN");
        transformer.setOutputProperty(
            OutputKeys.DOCTYPE_SYSTEM, "http://www.w3.org/TR/2001/REC-SVG-20010904/DTD/svg10.dtd");
        transformer.transform(new DOMSource(doc), new StreamResult(out));
        out.flush();
        out.close();
        in = new FileInputStream(tmp);
        ndoc.setResourceContent(fileS, in, OFlag.NONE);
      } catch (Throwable t) {
        t.printStackTrace();
      } finally {
        if(out!=null){
          out.flush();
          out.close();
        }
        in.close();
        tmp.delete();
      }
    }
  }
}
