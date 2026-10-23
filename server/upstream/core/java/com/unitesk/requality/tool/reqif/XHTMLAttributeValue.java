package com.unitesk.requality.tool.reqif;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import com.unitesk.requality.core.log.RLogger;
import com.unitesk.requality.tool.CoreUtils;

public class XHTMLAttributeValue extends AbstractStringAttributeValue {
  private static String TAG_TV = "THE-VALUE";
  //	private String value;//must store already reqifed version of descr
  public XHTMLAttributeValue(AttributeDefinition definition, String value) {
    super(definition, "XHTML");
    this.value = value;
  }

  private static Map<Thread,Transformer> fm = new HashMap<>();
  private static Transformer getFactory(){
    Transformer f = fm.get(Thread.currentThread());
    if(f!=null)
      return f;
    TransformerFactory tFactory =  TransformerFactory.newInstance();
    tFactory.setURIResolver(new URIResolver(){
    
      @Override
      public Source resolve(String arg0, String arg1) throws TransformerException {
        return null;
      }
    });
    Transformer transformer;
    try {
      f = transformer = tFactory.newTransformer();
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
      transformer.setOutputProperty(OutputKeys.METHOD, "xml");
      transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    } catch (TransformerConfigurationException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    fm.put(Thread.currentThread(), f);
    return f;
  }
  private static String xhtmlToText(Node res) {
    Transformer transformer = getFactory();
    StringWriter buffer = new StringWriter();
    for (int i = 0; i < res.getChildNodes().getLength(); i++)
      try {
        transformer.transform(new DOMSource(res.getChildNodes().item(i)), new StreamResult(buffer));
      } catch (TransformerException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    String ress = buffer.toString().trim().replace("reqif-xhtml:", "");
    if (ress.startsWith("<div>")) ress = ress.substring(5);
    if (ress.endsWith("</div>")) ress = ress.substring(0, ress.length() - 6);
    ress = ress.replace("<div/>", "");
    return ress.trim();
  }

  public static void textToXHTML(ReqIFModel model, String value, Node theval) {
    String s =
        "<root xmlns:reqif-xhtml=\"http://www.w3.org/1999/xhtml\">"
            + (value != null && !value.isEmpty() ? "<reqif-xhtml:div>" : "")
            + CoreUtils.htmlEncapsulate(value,true)
            + (value != null && !value.isEmpty() ? "</reqif-xhtml:div>" : "")
            + "</root>";

    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

    //dbf.setSchema(model.getBuilder().getSchema());;
    dbf.setNamespaceAware(true);
    try {
      dbf.setValidating(false);
      DocumentBuilder builder = dbf.newDocumentBuilder();
      Document d = builder.parse(new ByteArrayInputStream(s.getBytes(Charset.forName("UTF-8"))));
      d.setStrictErrorChecking(false);
      //	Document d = ReqIFModel.getBuilder().parse(new ByteArrayInputStream(s.getBytes(Charset.forName("UTF-8"))));
      Node n = d.getFirstChild(); //d.getFirstChild().getChildNodes();
      //n = d.cloneNode(true);
      //n = theval.getOwnerDocument().adoptNode(n);
      //theval.appendChild(n);
      NodeList nl = n.getChildNodes();
      List<Node> nodes = new LinkedList<>();
      for (int i = 0; i < nl.getLength(); i++) nodes.add(nl.item(i));
      for (Node nli : nodes) {
        n = nli.cloneNode(true);
        n = theval.getOwnerDocument().adoptNode(n);
        theval.appendChild(n);
        if (!recRework(n, theval, "reqif-xhtml", model)) theval.removeChild(n);
      }
    } catch (Exception e) {
      if (value != null) RLogger.logInfo("Parsing error on '" + value + "'");
      //value.replaceAll("reqif:xhtml", replacement)
      // TODO Auto-generated catch block
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  private static boolean recRework(Node n, Node par, String prefix, ReqIFModel model) {
    return recRework(n, par, prefix, model, 0, -1);
  }

  private static boolean recRework(
      Node n, Node par, String prefix, ReqIFModel model, int lilvl, int liind) {
    if ((n.getPrefix() == null || !n.getPrefix().equals(prefix))
        && !n.getNodeName().startsWith("#")) {
      n =
          n.getOwnerDocument()
              .renameNode(n, model.getNamespaceURI(prefix), prefix + ":" + n.getNodeName());
      //n.setPrefix(prefix);
    }
    int nlvl = lilvl;
    boolean wasli = false, deletenode = false;
    Node after = null;
    if (n.getNodeName().endsWith("li")) {
      String ch = "●";
      if (nlvl > 1) ch = "○";
      if (nlvl > 2) ch = "■";
      for (int i = 1; i < nlvl; i++) ch = "     " + ch;
      Node trg = n;
      while (!par.equals(trg.getParentNode())) trg = trg.getParentNode();
      par.insertBefore(par.getOwnerDocument().createElement(prefix + ":br"), trg);
      Node pre = par.getOwnerDocument().createElement(prefix + ":pre");
      par.insertBefore(pre, trg);
      pre.appendChild(par.getOwnerDocument().createTextNode(ch));
      wasli = true;
      deletenode = true;
    }

    if (n.getNodeName().endsWith("dl")) {
      Node trg = n;
      nlvl++;
      while (!par.equals(trg.getParentNode())) trg = trg.getParentNode();
      par.insertBefore(par.getOwnerDocument().createElement(prefix + ":br"), trg);
      after = par.getOwnerDocument().createElement(prefix + ":br");
      deletenode = true;
    }
    if (n.getNodeName().endsWith("dt")) {
      Node trg = n;
      while (!par.equals(trg.getParentNode())) trg = trg.getParentNode();
      after = par.getOwnerDocument().createElement(prefix + ":br");
      deletenode = true;
    }

    if (n.getNodeName().endsWith("dd")) {
      Node trg = n;
      while (!par.equals(trg.getParentNode())) trg = trg.getParentNode();
      after = par.getOwnerDocument().createElement(prefix + ":br");
      deletenode = true;
    }

    if (n.getNodeName().endsWith("ol") || n.getNodeName().endsWith("ul")) {
      nlvl++;
      liind = n.getNodeName().endsWith("ol") ? 0 : -1;
      deletenode = true;
    }
    Node npar = n;
    while (npar.getParentNode() != par) npar = npar.getParentNode();
    if (n.getParentNode() != par && !deletenode) {
      par.insertBefore(n, npar);
    }
    if (!n.getNodeName().toLowerCase().endsWith("object")) {
      //		n.getAttributes();
      NamedNodeMap nnm = n.getAttributes();
      if (nnm != null) {
        List<Node> toa = new ArrayList<>();
        for (int i = 0; i < nnm.getLength(); i++) {
          Node a = nnm.item(i);
          Node na =
              n.getOwnerDocument()
                  .createAttributeNS(model.getNamespaceURI(prefix), prefix + ":" + a.getNodeName());
          na.setNodeValue(a.getNodeValue());
          toa.add(na);
        }
        int i = 0;
        while (nnm.getLength() > i) {
          Node n2 = nnm.item(i);
          if (n2.getNamespaceURI() != null)
            nnm.removeNamedItemNS(n2.getNamespaceURI(), n2.getNodeName());
          else nnm.removeNamedItem(n2.getNodeName());
        }
        for (Node nd : toa) {
          nnm.setNamedItem(nd);
        }
      }
    }
    //if(n.getNodeName().toLowerCase().equals("li"))
    NodeList nl = n.getChildNodes();
    List<Node> nds = new LinkedList<>();
    for (int i = 0; i < nl.getLength(); i++) {
      nds.add(nl.item(i));
    }
    for (Node nde : nds) {
      Node rpar = n;
      if (deletenode) rpar = par;
      recRework(nde, rpar, prefix, model, nlvl, liind);
    }
    if (after != null) par.insertBefore(after, npar);
    //		if(wasli){
    //			Node trg = n;
    //			while(!par.equals(trg.getParentNode()))
    //				trg = trg.getParentNode();
    //			if(trg!=null && !trg.getNodeName().endsWith("br"))
    //				par.insertBefore(par.getOwnerDocument().createElement(prefix+":br"),trg);
    //		}
    if (deletenode) {
      n.getParentNode().removeChild(n);
    }
    return !deletenode;
  }

  @Override
  public Element save(Node par, Document doc, ReqIFModel model) {
    Element nd = super.save(par, doc, model);
    if (value != null) {
      Element val = ReqIFDefinition.createElement(doc, model.getNamespaceURI(""), TAG_TV);
      nd.appendChild(val);
      textToXHTML(model, value, val);
    }
    return nd;
  }

  public static AbstractAttributeValue load(Node node, EntityWithAttributeDefs type) {
    Node def = ReqIFModel.getFirstChild(node, DEF);
    String dval = null;
    if (def != null) {
      Node adef = def.getFirstChild();
      while (adef != null
          && (adef.getNodeName() == null || !adef.getNodeName().startsWith(DEF_TAG)))
        adef = adef.getNextSibling();
      if (adef != null) dval = adef.getTextContent();
    }
    //node.getNodeName().substring(node.getNodeName().lastIndexOf("-")+1);
    AttributeDefinition ad = type.getAttributeDefinition(dval);
    ;
    if (ad == null) 
      throw new RuntimeException("Attribute definition was not found during xhtml loading! " + dval);
    Node vl = ReqIFModel.getFirstChild(node, TAG_TV);
    String val = null;
    if (vl != null) val = xhtmlToText(vl);
    return new XHTMLAttributeValue(ad, val);
  }

  @Override
  public String getNameSpaceTag() {
    return null;
  }

  @Override
  public Object clone() {
    return new XHTMLAttributeValue(definition, getString());
  }

  @Override
  public void setString(String doorsOLECrutch) {
    value = doorsOLECrutch;
  }
}
