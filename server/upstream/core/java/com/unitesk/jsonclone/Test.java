package com.unitesk.jsonclone;

import java.io.StringWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import com.unitesk.requality.core.log.RLogger;

/**
 * Test class. This file is not formally a member of the com.unitesk.jsonclone. library. It is just a casual test
 * tool.
 */
public class Test {

  /**
   * Entry point.
   *
   * @param args
   */
  public static void main(String args[]) {
    Iterator<Object> it;
    JSONArray a;
    JSONObject j;
    JSONStringer jj;
    Object o;
    String s;

    /**
     * Obj is a typical class that implements JSONString. It also provides some beanie methods that
     * can be used to construct a JSONObject. It also demonstrates constructing a JSONObject with an
     * array of names.
     */
    class Obj implements JSONString {
      public String aString;
      public double aNumber;
      public boolean aBoolean;

      public Obj(String string, double n, boolean b) {
        this.aString = string;
        this.aNumber = n;
        this.aBoolean = b;
      }

      public double getNumber() {
        return this.aNumber;
      }

      public String getString() {
        return this.aString;
      }

      public boolean isBoolean() {
        return this.aBoolean;
      }

      public String getBENT() {
        return "All uppercase key";
      }

      public String getX() {
        return "x";
      }

      public String toJSONString() {
        return "{"
            + JSONObject.quote(this.aString)
            + ":"
            + JSONObject.doubleToString(this.aNumber)
            + "}";
      }

      public String toString() {
        return this.getString()
            + " "
            + this.getNumber()
            + " "
            + this.isBoolean()
            + "."
            + this.getBENT()
            + " "
            + this.getX();
      }
    }

    Obj obj = new Obj("A beany object", 42, true);

    try {
      s = "[0.1]";
      a = new JSONArray(s);
      RLogger.logInfo(a.toString());
      RLogger.logInfo("");

      j =
          XML.toJSONObject(
              "<![CDATA[This is a collection of test patterns and examples for com.unitesk.jsonclone.]]>  Ignore the stuff past the end.  ");
      RLogger.logInfo(j.toString());
      RLogger.logInfo("");

      j = new JSONObject();
      o = null;
      j.put("booga", o);
      j.put("wooga", JSONObject.NULL);
      RLogger.logInfo(j.toString());
      RLogger.logInfo("");

      j = new JSONObject();
      j.increment("two");
      j.increment("two");
      RLogger.logInfo(j.toString());
      RLogger.logInfo("");

      s = "<test><blank></blank><empty/></test>";
      j = XML.toJSONObject(s);
      RLogger.logInfo(j.toString(2));
      RLogger.logInfo(XML.toString(j));
      RLogger.logInfo("");

      s = "{     \"list of lists\" : [         [1, 2, 3],         [4, 5, 6],     ] }";
      j = new JSONObject(s);
      RLogger.logInfo(j.toString(4));
      RLogger.logInfo(XML.toString(j));

      s =
          "<recipe name=\"bread\" prep_time=\"5 mins\" cook_time=\"3 hours\"> <title>Basic bread</title> <ingredient amount=\"8\" unit=\"dL\">Flour</ingredient> <ingredient amount=\"10\" unit=\"grams\">Yeast</ingredient> <ingredient amount=\"4\" unit=\"dL\" state=\"warm\">Water</ingredient> <ingredient amount=\"1\" unit=\"teaspoon\">Salt</ingredient> <instructions> <step>Mix all ingredients together.</step> <step>Knead thoroughly.</step> <step>Cover with a cloth, and leave for one hour in warm room.</step> <step>Knead again.</step> <step>Place in a bread baking tin.</step> <step>Cover with a cloth, and leave for one hour in warm room.</step> <step>Bake in the oven at 180(degrees)C for 30 minutes.</step> </instructions> </recipe> ";
      j = XML.toJSONObject(s);
      RLogger.logInfo(j.toString(4));
      RLogger.logInfo("");

      j = JSONML.toJSONObject(s);
      RLogger.logInfo(j.toString());
      RLogger.logInfo(JSONML.toString(j));
      RLogger.logInfo("");

      a = JSONML.toJSONArray(s);
      RLogger.logInfo(a.toString(4));
      RLogger.logInfo(JSONML.toString(a));
      RLogger.logInfo("");

      s =
          "<div id=\"demo\" class=\"JSONML\"><p>JSONML is a transformation between <b>JSON</b> and <b>XML</b> that preserves ordering of document features.</p><p>JSONML can work with JSON arrays or JSON objects.</p><p>Three<br/>little<br/>words</p></div>";
      j = JSONML.toJSONObject(s);
      RLogger.logInfo(j.toString(4));
      RLogger.logInfo(JSONML.toString(j));
      RLogger.logInfo("");

      a = JSONML.toJSONArray(s);
      RLogger.logInfo(a.toString(4));
      RLogger.logInfo(JSONML.toString(a));
      RLogger.logInfo("");

      s =
          "<person created=\"2006-11-11T19:23\" modified=\"2006-12-31T23:59\">\n <firstName>Robert</firstName>\n <lastName>Smith</lastName>\n <address type=\"home\">\n <street>12345 Sixth Ave</street>\n <city>Anytown</city>\n <state>CA</state>\n <postalCode>98765-4321</postalCode>\n </address>\n </person>";
      j = XML.toJSONObject(s);
      RLogger.logInfo(j.toString(4));

      j = new JSONObject(obj);
      RLogger.logInfo(j.toString());

      s =
          "{ \"entity\": { \"imageURL\": \"\", \"name\": \"IXXXXXXXXXXXXX\", \"id\": 12336, \"ratingCount\": null, \"averageRating\": null } }";
      j = new JSONObject(s);
      RLogger.logInfo(j.toString(2));

      jj = new JSONStringer();
      s =
          jj.object()
              .key("single")
              .value("MARIE HAA'S")
              .key("Johnny")
              .value("MARIE HAA\\'S")
              .key("foo")
              .value("bar")
              .key("baz")
              .array()
              .object()
              .key("quux")
              .value("Thanks, Josh!")
              .endObject()
              .endArray()
              .key("obj keys")
              .value(JSONObject.getNames(obj))
              .endObject()
              .toString();
      RLogger.logInfo(s);

      RLogger.logInfo(
          new JSONStringer()
              .object()
              .key("a")
              .array()
              .array()
              .array()
              .value("b")
              .endArray()
              .endArray()
              .endArray()
              .endObject()
              .toString());

      jj = new JSONStringer();
      jj.array();
      jj.value(1);
      jj.array();
      jj.value(null);
      jj.array();
      jj.object();
      jj.key("empty-array").array().endArray();
      jj.key("answer").value(42);
      jj.key("null").value(null);
      jj.key("false").value(false);
      jj.key("true").value(true);
      jj.key("big").value(123456789e+88);
      jj.key("small").value(123456789e-88);
      jj.key("empty-object").object().endObject();
      jj.key("long");
      jj.value(9223372036854775807L);
      jj.endObject();
      jj.value("two");
      jj.endArray();
      jj.value(true);
      jj.endArray();
      jj.value(98.6);
      jj.value(-100.0);
      jj.object();
      jj.endObject();
      jj.object();
      jj.key("one");
      jj.value(1.00);
      jj.endObject();
      jj.value(obj);
      jj.endArray();
      RLogger.logInfo(jj.toString());

      RLogger.logInfo(new JSONArray(jj.toString()).toString(4));

      int ar[] = {1, 2, 3};
      JSONArray ja = new JSONArray(ar);
      RLogger.logInfo(ja.toString());

      String sa[] = {"aString", "aNumber", "aBoolean"};
      j = new JSONObject(obj, sa);
      j.put("Testing JSONString interface", obj);
      RLogger.logInfo(j.toString(4));

      j =
          new JSONObject(
              "{slashes: '///', closetag: '</script>', backslash:'\\\\', ei: {quotes: '\"\\''},eo: {a: '\"quoted\"', b:\"don't\"}, quotes: [\"'\", '\"']}");
      RLogger.logInfo(j.toString(2));
      RLogger.logInfo(XML.toString(j));
      RLogger.logInfo("");

      j =
          new JSONObject(
              "{foo: [true, false,9876543210,    0.0, 1.00000001,  1.000000000001, 1.00000000000000001,"
                  + " .00000000000000001, 2.00, 0.1, 2e100, -32,[],{}, \"string\"], "
                  + "  to   : null, op : 'Good',"
                  + "ten:10} postfix comment");
      j.put("String", "98.6");
      j.put("JSONObject", new JSONObject());
      j.put("JSONArray", new JSONArray());
      j.put("int", 57);
      j.put("double", 123456789012345678901234567890.);
      j.put("true", true);
      j.put("false", false);
      j.put("null", JSONObject.NULL);
      j.put("bool", "true");
      j.put("zero", -0.0);
      j.put("\\u2028", "\u2028");
      j.put("\\u2029", "\u2029");
      a = j.getJSONArray("foo");
      a.put(666);
      a.put(2001.99);
      a.put("so \"fine\".");
      a.put("so <fine>.");
      a.put(true);
      a.put(false);
      a.put(new JSONArray());
      a.put(new JSONObject());
      j.put("keys", JSONObject.getNames(j));
      RLogger.logInfo(j.toString(4));
      RLogger.logInfo(XML.toString(j));

      RLogger.logInfo("String: " + j.getDouble("String"));
      RLogger.logInfo("  bool: " + j.getBoolean("bool"));
      RLogger.logInfo("    to: " + j.getString("to"));
      RLogger.logInfo("  true: " + j.getString("true"));
      RLogger.logInfo("   foo: " + j.getJSONArray("foo"));
      RLogger.logInfo("    op: " + j.getString("op"));
      RLogger.logInfo("   ten: " + j.getInt("ten"));
      RLogger.logInfo("  oops: " + j.optBoolean("oops"));

      s =
          "<xml one = 1 two=' \"2\" '><five></five>First \u0009&lt;content&gt;<five></five> This is \"content\". <three>  3  </three>JSON does not preserve the sequencing of elements and contents.<three>  III  </three>  <three>  T H R E E</three><four/>Content text is an implied structure in XML. <six content=\"6\"/>JSON does not have implied structure:<seven>7</seven>everything is explicit.<![CDATA[CDATA blocks<are><supported>!]]></xml>";
      j = XML.toJSONObject(s);
      RLogger.logInfo(j.toString(2));
      RLogger.logInfo(XML.toString(j));
      RLogger.logInfo("");

      ja = JSONML.toJSONArray(s);
      RLogger.logInfo(ja.toString(4));
      RLogger.logInfo(JSONML.toString(ja));
      RLogger.logInfo("");

      s =
          "<xml do='0'>uno<a re='1' mi='2'>dos<b fa='3'/>tres<c>true</c>quatro</a>cinqo<d>seis<e/></d></xml>";
      ja = JSONML.toJSONArray(s);
      RLogger.logInfo(ja.toString(4));
      RLogger.logInfo(JSONML.toString(ja));
      RLogger.logInfo("");

      s =
          "<mapping><empty/>   <class name = \"Customer\">      <field name = \"ID\" type = \"string\">         <bind-xml name=\"ID\" node=\"attribute\"/>      </field>      <field name = \"FirstName\" type = \"FirstName\"/>      <field name = \"MI\" type = \"MI\"/>      <field name = \"LastName\" type = \"LastName\"/>   </class>   <class name = \"FirstName\">      <field name = \"text\">         <bind-xml name = \"text\" node = \"text\"/>      </field>   </class>   <class name = \"MI\">      <field name = \"text\">         <bind-xml name = \"text\" node = \"text\"/>      </field>   </class>   <class name = \"LastName\">      <field name = \"text\">         <bind-xml name = \"text\" node = \"text\"/>      </field>   </class></mapping>";
      j = XML.toJSONObject(s);

      RLogger.logInfo(j.toString(2));
      RLogger.logInfo(XML.toString(j));
      RLogger.logInfo("");
      ja = JSONML.toJSONArray(s);
      RLogger.logInfo(ja.toString(4));
      RLogger.logInfo(JSONML.toString(ja));
      RLogger.logInfo("");

      j =
          XML.toJSONObject(
              "<?xml version=\"1.0\" ?><Book Author=\"Anonymous\"><Title>Sample Book</Title><Chapter id=\"1\">This is chapter 1. It is not very long or interesting.</Chapter><Chapter id=\"2\">This is chapter 2. Although it is longer than chapter 1, it is not any more interesting.</Chapter></Book>");
      RLogger.logInfo(j.toString(2));
      RLogger.logInfo(XML.toString(j));
      RLogger.logInfo("");

      j =
          XML.toJSONObject(
              "<!DOCTYPE bCard 'http://www.cs.caltech.edu/~adam/schemas/bCard'><bCard><?xml default bCard        firstname = ''        lastname  = '' company   = '' email = '' homepage  = ''?><bCard        firstname = 'Rohit'        lastname  = 'Khare'        company   = 'MCI'        email     = 'khare@mci.net'        homepage  = 'http://pest.w3.org/'/><bCard        firstname = 'Adam'        lastname  = 'Rifkin'        company   = 'Caltech Infospheres Project'        email     = 'adam@cs.caltech.edu'        homepage  = 'http://www.cs.caltech.edu/~adam/'/></bCard>");
      RLogger.logInfo(j.toString(2));
      RLogger.logInfo(XML.toString(j));
      RLogger.logInfo("");

      j =
          XML.toJSONObject(
              "<?xml version=\"1.0\"?><customer>    <firstName>        <text>Fred</text>    </firstName>    <ID>fbs0001</ID>    <lastName> <text>Scerbo</text>    </lastName>    <MI>        <text>B</text>    </MI></customer>");
      RLogger.logInfo(j.toString(2));
      RLogger.logInfo(XML.toString(j));
      RLogger.logInfo("");

      j =
          XML.toJSONObject(
              "<!ENTITY tp-address PUBLIC '-//ABC University::Special Collections Library//TEXT (titlepage: name and address)//EN' 'tpspcoll.sgm'><list type='simple'><head>Repository Address </head><item>Special Collections Library</item><item>ABC University</item><item>Main Library, 40 Circle Drive</item><item>Ourtown, Pennsylvania</item><item>17654 USA</item></list>");
      RLogger.logInfo(j.toString());
      RLogger.logInfo(XML.toString(j));
      RLogger.logInfo("");

      j =
          XML.toJSONObject(
              "<test intertag status=ok><empty/>deluxe<blip sweet=true>&amp;&quot;toot&quot;&toot;&#x41;</blip><x>eks</x><w>bonus</w><w>bonus2</w></test>");
      RLogger.logInfo(j.toString(2));
      RLogger.logInfo(XML.toString(j));
      RLogger.logInfo("");

      j =
          HTTP.toJSONObject(
              "GET / HTTP/1.0\nAccept: image/gif, image/x-xbitmap, image/jpeg, image/pjpeg, application/vnd.ms-powerpoint, application/vnd.ms-excel, application/msword, */*\nAccept-Language: en-us\nUser-Agent: Mozilla/4.0 (compatible; MSIE 5.5; Windows 98; Win 9x 4.90; T312461; Q312461)\nHost: www.nokko.com\nConnection: keep-alive\nAccept-encoding: gzip, deflate\n");
      RLogger.logInfo(j.toString(2));
      RLogger.logInfo(HTTP.toString(j));
      RLogger.logInfo("");

      j =
          HTTP.toJSONObject(
              "HTTP/1.1 200 Oki Doki\nDate: Sun, 26 May 2002 17:38:52 GMT\nServer: Apache/1.3.23 (Unix) mod_perl/1.26\nKeep-Alive: timeout=15, max=100\nConnection: Keep-Alive\nTransfer-Encoding: chunked\nContent-Type: text/html\n");
      RLogger.logInfo(j.toString(2));
      RLogger.logInfo(HTTP.toString(j));
      RLogger.logInfo("");

      j =
          new JSONObject(
              "{nix: null, nux: false, null: 'null', 'Request-URI': '/', Method: 'GET', 'HTTP-Version': 'HTTP/1.0'}");
      RLogger.logInfo(j.toString(2));
      RLogger.logInfo("isNull: " + j.isNull("nix"));
      RLogger.logInfo("   has: " + j.has("nix"));
      RLogger.logInfo(XML.toString(j));
      RLogger.logInfo(HTTP.toString(j));
      RLogger.logInfo("");

      j =
          XML.toJSONObject(
              "<?xml version='1.0' encoding='UTF-8'?>"
                  + "\n\n"
                  + "<SOAP-ENV:Envelope"
                  + " xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\""
                  + " xmlns:xsi=\"http://www.w3.org/1999/XMLSchema-instance\""
                  + " xmlns:xsd=\"http://www.w3.org/1999/XMLSchema\">"
                  + "<SOAP-ENV:Body><ns1:doGoogleSearch"
                  + " xmlns:ns1=\"urn:GoogleSearch\""
                  + " SOAP-ENV:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"
                  + "<key xsi:type=\"xsd:string\">GOOGLEKEY</key> <q"
                  + " xsi:type=\"xsd:string\">'+search+'</q> <start"
                  + " xsi:type=\"xsd:int\">0</start> <maxResults"
                  + " xsi:type=\"xsd:int\">10</maxResults> <filter"
                  + " xsi:type=\"xsd:boolean\">true</filter> <restrict"
                  + " xsi:type=\"xsd:string\"></restrict> <safeSearch"
                  + " xsi:type=\"xsd:boolean\">false</safeSearch> <lr"
                  + " xsi:type=\"xsd:string\"></lr> <ie"
                  + " xsi:type=\"xsd:string\">latin1</ie> <oe"
                  + " xsi:type=\"xsd:string\">latin1</oe>"
                  + "</ns1:doGoogleSearch>"
                  + "</SOAP-ENV:Body></SOAP-ENV:Envelope>");
      RLogger.logInfo(j.toString(2));
      RLogger.logInfo(XML.toString(j));
      RLogger.logInfo("");

      j =
          new JSONObject(
              "{Envelope: {Body: {\"ns1:doGoogleSearch\": {oe: \"latin1\", filter: true, q: \"'+search+'\", key: \"GOOGLEKEY\", maxResults: 10, \"SOAP-ENV:encodingStyle\": \"http://schemas.xmlsoap.org/soap/encoding/\", start: 0, ie: \"latin1\", safeSearch:false, \"xmlns:ns1\": \"urn:GoogleSearch\"}}}}");
      RLogger.logInfo(j.toString(2));
      RLogger.logInfo(XML.toString(j));
      RLogger.logInfo("");

      j = CookieList.toJSONObject("  f%oo = b+l=ah  ; o;n%40e = t.wo ");
      RLogger.logInfo(j.toString(2));
      RLogger.logInfo(CookieList.toString(j));
      RLogger.logInfo("");

      j = Cookie.toJSONObject("f%oo=blah; secure ;expires = April 24, 2002");
      RLogger.logInfo(j.toString(2));
      RLogger.logInfo(Cookie.toString(j));
      RLogger.logInfo("");

      j =
          new JSONObject(
              "{script: 'It is not allowed in HTML to send a close script tag in a string<script>because it confuses browsers</script>so we insert a backslash before the /'}");
      RLogger.logInfo(j.toString());
      RLogger.logInfo("");

      JSONTokener jt =
          new JSONTokener("{op:'test', to:'session', pre:1}{op:'test', to:'session', pre:2}");
      j = new JSONObject(jt);
      RLogger.logInfo(j.toString());
      RLogger.logInfo("pre: " + j.optInt("pre"));
      int i = jt.skipTo('{');
      RLogger.logInfo(i);
      j = new JSONObject(jt);
      RLogger.logInfo(j.toString());
      RLogger.logInfo("");

      a =
          CDL.toJSONArray(
              "Comma delimited list test, '\"Strip\"Quotes', 'quote, comma', No quotes, 'Single Quotes', \"Double Quotes\"\n1,'2',\"3\"\n,'It is \"good,\"', \"It works.\"\n\n");

      s = CDL.toString(a);
      RLogger.logInfo(s);
      RLogger.logInfo("");
      RLogger.logInfo(a.toString(4));
      RLogger.logInfo("");
      a = CDL.toJSONArray(s);
      RLogger.logInfo(a.toString(4));
      RLogger.logInfo("");

      a = new JSONArray(" [\"<escape>\", next is an implied null , , ok,] ");
      RLogger.logInfo(a.toString());
      RLogger.logInfo("");
      RLogger.logInfo(XML.toString(a));
      RLogger.logInfo("");

      j =
          new JSONObject(
              "{ fun => with non-standard forms ; forgiving => This package can be used to parse formats that are similar to but not stricting conforming to JSON; why=To make it easier to migrate existing data to JSON,one = [[1.00]]; uno=[[{1=>1}]];'+':+6e66 ;pluses=+++;empty = '' , 'double':0.666,true: TRUE, false: FALSE, null=NULL;[true] = [[!,@;*]]; string=>  o. k. ; \r oct=0666; hex=0x666; dec=666; o=0999; noh=0x0x}");
      RLogger.logInfo(j.toString(4));
      RLogger.logInfo("");
      if (j.getBoolean("true") && !j.getBoolean("false")) {
        RLogger.logInfo("It's all good");
      }

      RLogger.logInfo("");
      j = new JSONObject(j, new String[] {"dec", "oct", "hex", "missing"});
      RLogger.logInfo(j.toString(4));

      RLogger.logInfo("");
      RLogger.logInfo(new JSONStringer().array().value(a).value(j).endArray());

      j =
          new JSONObject(
              "{string: \"98.6\", long: 2147483648, int: 2147483647, longer: 9223372036854775807, double: 9223372036854775808}");
      RLogger.logInfo(j.toString(4));

      RLogger.logInfo("\ngetInt");
      RLogger.logInfo("int    " + j.getInt("int"));
      RLogger.logInfo("long   " + j.getInt("long"));
      RLogger.logInfo("longer " + j.getInt("longer"));
      // RLogger.logInfo("double " + j.getInt("double"));
      // RLogger.logInfo("string " + j.getInt("string"));

      RLogger.logInfo("\ngetLong");
      RLogger.logInfo("int    " + j.getLong("int"));
      RLogger.logInfo("long   " + j.getLong("long"));
      RLogger.logInfo("longer " + j.getLong("longer"));
      // RLogger.logInfo("double " + j.getLong("double"));
      // RLogger.logInfo("string " + j.getLong("string"));

      RLogger.logInfo("\ngetDouble");
      RLogger.logInfo("int    " + j.getDouble("int"));
      RLogger.logInfo("long   " + j.getDouble("long"));
      RLogger.logInfo("longer " + j.getDouble("longer"));
      RLogger.logInfo("double " + j.getDouble("double"));
      RLogger.logInfo("string " + j.getDouble("string"));

      j.put("good sized", 9223372036854775807L);
      RLogger.logInfo(j.toString(4));

      a = new JSONArray("[2147483647, 2147483648, 9223372036854775807, 9223372036854775808]");
      RLogger.logInfo(a.toString(4));

      RLogger.logInfo("\nKeys: ");
      it = j.keys();
      while (it.hasNext()) {
        s = (String) it.next();
        RLogger.logInfo(s + ": " + j.getString(s));
      }

      RLogger.logInfo("\naccumulate: ");
      j = new JSONObject();
      j.accumulate("stooge", "Curly");
      j.accumulate("stooge", "Larry");
      j.accumulate("stooge", "Moe");
      a = j.getJSONArray("stooge");
      a.put(5, "Shemp");
      RLogger.logInfo(j.toString(4));

      RLogger.logInfo("\nwrite:");
      try(StringWriter sw = new StringWriter()){
        try(Writer swa = j.write(sw)){
          RLogger.logInfo(swa);
        }
      }

      s = "<xml empty><a></a><a>1</a><a>22</a><a>333</a></xml>";
      j = XML.toJSONObject(s);
      RLogger.logInfo(j.toString(4));
      RLogger.logInfo(XML.toString(j));

      s =
          "<book><chapter>Content of the first chapter</chapter><chapter>Content of the second chapter      <chapter>Content of the first subchapter</chapter>      <chapter>Content of the second subchapter</chapter></chapter><chapter>Third Chapter</chapter></book>";
      j = XML.toJSONObject(s);
      RLogger.logInfo(j.toString(4));
      RLogger.logInfo(XML.toString(j));

      a = JSONML.toJSONArray(s);
      RLogger.logInfo(a.toString(4));
      RLogger.logInfo(JSONML.toString(a));

      Collection<Object> c = null;
      Map<Object, Object> m = null;

      j = new JSONObject(m);
      a = new JSONArray(c);
      j.append("stooge", "Joe DeRita");
      j.append("stooge", "Shemp");
      j.accumulate("stooges", "Curly");
      j.accumulate("stooges", "Larry");
      j.accumulate("stooges", "Moe");
      j.accumulate("stoogearray", j.get("stooges"));
      j.put("map", m);
      j.put("collection", c);
      j.put("array", a);
      a.put(m);
      a.put(c);
      RLogger.logInfo(j.toString(4));

      s =
          "{plist=Apple; AnimalSmells = { pig = piggish; lamb = lambish; worm = wormy; }; AnimalSounds = { pig = oink; lamb = baa; worm = baa;  Lisa = \"Why is the worm talking like a lamb?\" } ; AnimalColors = { pig = pink; lamb = black; worm = pink; } } ";
      j = new JSONObject(s);
      RLogger.logInfo(j.toString(4));

      s = " (\"San Francisco\", \"New York\", \"Seoul\", \"London\", \"Seattle\", \"Shanghai\")";
      a = new JSONArray(s);
      RLogger.logInfo(a.toString());

      s =
          "<a ichi='1' ni='2'><b>The content of b</b> and <c san='3'>The content of c</c><d>do</d><e></e><d>re</d><f/><d>mi</d></a>";
      j = XML.toJSONObject(s);

      RLogger.logInfo(j.toString(2));
      RLogger.logInfo(XML.toString(j));
      RLogger.logInfo("");
      ja = JSONML.toJSONArray(s);
      RLogger.logInfo(ja.toString(4));
      RLogger.logInfo(JSONML.toString(ja));
      RLogger.logInfo("");

      s =
          "<Root><MsgType type=\"node\"><BatchType type=\"string\">111111111111111</BatchType></MsgType></Root>";
      j = JSONML.toJSONObject(s);
      RLogger.logInfo(j);
      ja = JSONML.toJSONArray(s);
      RLogger.logInfo(ja);

      RLogger.logInfo("\nTesting Exceptions: ");

      RLogger.logInfo("Exception: ");
      try {
        a = new JSONArray("[\n\r\n\r}");
        RLogger.logInfo(a.toString());
      } catch (Exception e) {
        RLogger.logInfo(e);
      }

      RLogger.logInfo("Exception: ");
      try {
        a = new JSONArray("<\n\r\n\r      ");
        RLogger.logInfo(a.toString());
      } catch (Exception e) {
        RLogger.logInfo(e);
      }

      RLogger.logInfo("Exception: ");
      try {
        a = new JSONArray();
        a.put(Double.NEGATIVE_INFINITY);
        a.put(Double.NaN);
        RLogger.logInfo(a.toString());
      } catch (Exception e) {
        RLogger.logInfo(e);
      }
      RLogger.logInfo("Exception: ");
      try {
        RLogger.logInfo(j.getDouble("stooge"));
      } catch (Exception e) {
        RLogger.logInfo(e);
      }
      RLogger.logInfo("Exception: ");
      try {
        RLogger.logInfo(j.getDouble("howard"));
      } catch (Exception e) {
        RLogger.logInfo(e);
      }
      RLogger.logInfo("Exception: ");
      try {
        RLogger.logInfo(j.put(null, "howard"));
      } catch (Exception e) {
        RLogger.logInfo(e);
      }
      RLogger.logInfo("Exception: ");
      try {
        RLogger.logInfo(a.getDouble(0));
      } catch (Exception e) {
        RLogger.logInfo(e);
      }
      RLogger.logInfo("Exception: ");
      try {
        RLogger.logInfo(a.get(-1));
      } catch (Exception e) {
        RLogger.logInfo(e);
      }
      RLogger.logInfo("Exception: ");
      try {
        RLogger.logInfo(a.put(Double.NaN));
      } catch (Exception e) {
        RLogger.logInfo(e);
      }
      RLogger.logInfo("Exception: ");
      try {
        j = XML.toJSONObject("<a><b>    ");
      } catch (Exception e) {
        RLogger.logInfo(e);
      }
      RLogger.logInfo("Exception: ");
      try {
        j = XML.toJSONObject("<a></b>    ");
      } catch (Exception e) {
        RLogger.logInfo(e);
      }
      RLogger.logInfo("Exception: ");
      try {
        j = XML.toJSONObject("<a></a    ");
      } catch (Exception e) {
        RLogger.logInfo(e);
      }
      RLogger.logInfo("Exception: ");
      try {
        ja = new JSONArray(new Object());
        RLogger.logInfo(ja.toString());
      } catch (Exception e) {
        RLogger.logInfo(e);
      }

      RLogger.logInfo("Exception: ");
      try {
        s = "[)";
        a = new JSONArray(s);
        RLogger.logInfo(a.toString());
      } catch (Exception e) {
        RLogger.logInfo(e);
      }

      RLogger.logInfo("Exception: ");
      try {
        s = "<xml";
        ja = JSONML.toJSONArray(s);
        RLogger.logInfo(ja.toString(4));
      } catch (Exception e) {
        RLogger.logInfo(e);
      }

      RLogger.logInfo("Exception: ");
      try {
        s = "<right></wrong>";
        ja = JSONML.toJSONArray(s);
        RLogger.logInfo(ja.toString(4));
      } catch (Exception e) {
        RLogger.logInfo(e);
      }

      RLogger.logInfo("Exception: ");
      try {
        s = "{\"koda\": true, \"koda\": true}";
        j = new JSONObject(s);
        RLogger.logInfo(j.toString(4));
      } catch (Exception e) {
        RLogger.logInfo(e);
      }

      RLogger.logInfo("Exception: ");
      try {
        jj = new JSONStringer();
        s =
            jj.object()
                .key("bosanda")
                .value("MARIE HAA'S")
                .key("bosanda")
                .value("MARIE HAA\\'S")
                .endObject()
                .toString();
        RLogger.logInfo(j.toString(4));
      } catch (Exception e) {
        RLogger.logInfo(e);
      }
    } catch (Exception e) {
      RLogger.logInfo(e.toString());
    }
  }
}
