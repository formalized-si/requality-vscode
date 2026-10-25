package com.unitesk.requality.vscode.mcp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.unitesk.requality.core.attribute.AppliedEnumValue;
import com.unitesk.requality.core.attribute.Attribute;
import com.unitesk.requality.core.attribute.AttributeAvailability;
import com.unitesk.requality.core.attribute.AttributeDefinition;
import com.unitesk.requality.core.attribute.AttributeType;
import com.unitesk.requality.core.attribute.EnumDefinitionValue;
import com.unitesk.requality.core.attribute.EnumValue;
import com.unitesk.requality.core.attribute.Flag;
import com.unitesk.requality.core.attribute.ListValue;
import com.unitesk.requality.core.attribute.RequiredAttributesRule;
import com.unitesk.requality.core.attribute.valueprovider.ConstValueProvider;
import com.unitesk.requality.core.attribute.valueprovider.TempFolderFilesProvider;
import com.unitesk.requality.core.checker.CheckerRulesHolder;
import com.unitesk.requality.core.checker.DefTermProjectChecker;
import com.unitesk.requality.core.checker.UVIdProjectChecker;
import com.unitesk.requality.core.model.IDMechanics;
import com.unitesk.requality.core.model.TreeDB;
import com.unitesk.requality.core.model.TreeNode;
import com.unitesk.requality.core.search.JSFormula;
import com.unitesk.requality.vscode.mcp.McpServer.InvalidParams;
import com.unitesk.requality.vscode.mcp.McpServer.ToolError;

/**
 * The settings of a project, as the project properties of Requality keep them on the root node: the project attributes
 * (inherited by all nodes), the enumerations, the mandatory attributes of each node type, the checker rules (JavaScript),
 * the checkers of user-visible ids and defined terms, the id prefix, the default link attribute and the active node
 * templates.
 */
final class ProjectSettings {
  private ProjectSettings() {
  }

  /** The keys of change_project_settings. */
  static final String[] KEYS = { "attributes", "enums", "required_attributes", "checker_rules", "checkers", "id_prefix",
      "default_link_attribute", "active_templates" };

  // ---- reading

  static JsonObject read(TreeDB db) {
    TreeNode root = db.getRootNode();
    JsonObject o = new JsonObject();
    JsonObject attrs = new JsonObject();
    for (Attribute a : root.attrs()) {
      if (a == null || a.getKey().startsWith("_") || a.getKey().startsWith("%") || !a.isLocal(root.getUUId())
          || a.getType() == AttributeType.ENUM_DEFINITION)
        continue;
      JsonObject x = new JsonObject();
      x.addProperty("type", typeName(a));
      x.add("value", value(a.getValue()));
      if (a.getAvailability() != AttributeAvailability.SUBTREE)
        x.addProperty("inherited", false);
      attrs.add(a.getKey(), x);
    }
    o.add("attributes", attrs);
    JsonObject enums = new JsonObject();
    for (Attribute a : db.getEnumDefinitons()) {
      if (!(a.getValue() instanceof EnumDefinitionValue))
        continue;
      EnumDefinitionValue edv = (EnumDefinitionValue) a.getValue();
      JsonObject x = new JsonObject();
      x.addProperty("type", edv.getType() == null ? "string" : edv.getType().name().toLowerCase());
      JsonArray vs = new JsonArray();
      for (EnumValue v : edv) {
        JsonObject e = new JsonObject();
        e.addProperty("value", String.valueOf(v.getValue()));
        if (v.getComment() != null && !v.getComment().isEmpty())
          e.addProperty("comment", v.getComment());
        vs.add(e);
      }
      x.add("values", vs);
      enums.add(a.getKey(), x);
    }
    o.add("enums", enums);
    JsonObject req = new JsonObject();
    Map<String, Map<String, AttributeDefinition>> map = db.getAttributeRules().getRequiredAttrRule().getMap();
    if (map != null)
      for (Map.Entry<String, Map<String, AttributeDefinition>> e : new java.util.TreeMap<>(map).entrySet()) {
        JsonArray l = new JsonArray();
        for (Map.Entry<String, AttributeDefinition> d : e.getValue().entrySet()) {
          JsonObject x = new JsonObject();
          x.addProperty("attribute", d.getKey());
          Object v = d.getValue().getRawValue() == null ? null : d.getValue().getRawValue().getValue();
          x.add("default", value(v));
          l.add(x);
        }
        req.add(e.getKey(), l);
      }
    o.add("required_attributes", req);
    JsonArray rules = new JsonArray();
    CheckerRulesHolder crh = db.getAttributeRules().getCheckerRules();
    if (crh != null && crh.rules != null)
      for (JSFormula f : crh.rules) {
        JsonObject x = new JsonObject();
        x.addProperty("name", f.getName());
        x.addProperty("formula", f.getFormula());
        rules.add(x);
      }
    o.add("checker_rules", rules);
    JsonObject checkers = new JsonObject();
    checkers.addProperty("user_visible_ids", UVIdProjectChecker.isEnabled(db));
    checkers.addProperty("defined_terms", DefTermProjectChecker.isEnabled(db));
    o.add("checkers", checkers);
    String prefix = IDMechanics.getStoredIdPrefix(db);
    o.addProperty("id_prefix", prefix != null ? prefix : IDMechanics.getShownIdPrefix(db));
    o.addProperty("default_link_attribute", db.getDefReferenceName());
    JsonArray templates = new JsonArray();
    Map<String, String> active = db.getActiveTemplates();
    for (TreeNode t : db.getTemplates()) {
      JsonObject x = new JsonObject();
      x.addProperty("id", t.getId());
      x.addProperty("type", t.getType());
      x.addProperty("active", t.getId().equals(active.get(t.getType())));
      templates.add(x);
    }
    o.add("templates", templates);
    return o;
  }

  private static String typeName(Attribute a) {
    AttributeType t = a.getType();
    if (t == AttributeType.ENUM && a.getValue() instanceof AppliedEnumValue)
      return "enum:" + ((AppliedEnumValue) a.getValue()).getBaseKey();
    return t == null ? "string" : name(t);
  }

  private static String name(AttributeType t) {
    switch (t) {
      case INT:
        return "integer";
      case FLOAT:
        return "number";
      case BOOL:
        return "boolean";
      case LIST:
        return "list";
      case HTML:
        return "html";
      default:
        return t.name().toLowerCase();
    }
  }

  private static JsonElement value(Object v) {
    if (v == null)
      return com.google.gson.JsonNull.INSTANCE;
    if (v instanceof ListValue) {
      JsonArray l = new JsonArray();
      for (Object x : ((ListValue) v).getValues())
        l.add(String.valueOf(x));
      return l;
    }
    if (v instanceof EnumValue)
      return new JsonPrimitive(String.valueOf(((EnumValue) v).getValue()));
    if (v instanceof Boolean)
      return new JsonPrimitive((Boolean) v);
    if (v instanceof Number)
      return new JsonPrimitive((Number) v);
    return new JsonPrimitive(String.valueOf(v));
  }

  // ---- changing

  /** Changes the settings in the open transaction; returns the operations for the review. */
  static JsonArray change(TreeDB db, JsonObject a) throws Exception {
    TreeNode root = db.getRootNode();
    JsonArray ops = new JsonArray();
    // the enumerations first: the attributes may use them
    if (has(a, "enums"))
      for (Map.Entry<String, JsonElement> e : object(a, "enums").entrySet())
        ops.add(setEnum(db, root, e.getKey(), e.getValue()));
    if (has(a, "attributes"))
      for (Map.Entry<String, JsonElement> e : object(a, "attributes").entrySet())
        ops.add(setAttribute(db, root, e.getKey(), e.getValue()));
    if (has(a, "required_attributes"))
      ops.add(setRequired(db, object(a, "required_attributes")));
    if (has(a, "checker_rules"))
      ops.add(setRules(db, a.get("checker_rules")));
    if (has(a, "checkers")) {
      JsonObject c = object(a, "checkers");
      for (String k : c.keySet())
        if (!k.equals("user_visible_ids") && !k.equals("defined_terms"))
          throw new InvalidParams("checkers: unknown checker " + k + " (user_visible_ids, defined_terms)");
      if (c.has("user_visible_ids"))
        UVIdProjectChecker.setEnabled(db, Core.optBool(c, "user_visible_ids", true));
      if (c.has("defined_terms"))
        DefTermProjectChecker.setEnabled(db, Core.optBool(c, "defined_terms", true));
      JsonObject op = op("checkers");
      op.add("checkers", c);
      ops.add(op);
    }
    if (has(a, "id_prefix")) {
      String p = Core.str(a, "id_prefix").trim();
      if (!p.isEmpty() && !p.matches("[\\p{L}\\p{N}_.\\-]+"))
        throw new InvalidParams("id_prefix: letters, digits, '_', '.' and '-' only");
      IDMechanics.changeIdPrefix(db, p, true);
      JsonObject op = op("id_prefix");
      op.addProperty("id_prefix", p);
      ops.add(op);
    }
    if (has(a, "default_link_attribute")) {
      String l = Core.str(a, "default_link_attribute").trim();
      if (l.isEmpty() || l.startsWith("_") || !Attribute.checkNameIsValid(l, false))
        throw new InvalidParams("default_link_attribute: invalid attribute name " + l);
      root.attrs().setConstAttribute(TreeDB.DEFAULT_REFERENCE_NAME, l, Flag.HIDDEN);
      root.commitAttributes();
      JsonObject op = op("default_link_attribute");
      op.addProperty("default_link_attribute", l);
      ops.add(op);
    }
    if (has(a, "active_templates"))
      for (Map.Entry<String, JsonElement> e : object(a, "active_templates").entrySet()) {
        String id = e.getValue().isJsonNull() ? null : e.getValue().getAsString();
        if (id != null) {
          TreeNode t = db.getTemplate(id);
          if (t == null)
            throw new ToolError("active_templates: no template " + id + " in /Templates");
          if (!t.getType().equals(e.getKey()))
            throw new ToolError("active_templates: the template " + id + " is a " + t.getType() + ", not a " + e.getKey());
          db.setActiveTemplate(e.getKey(), id);
        } else {
          db.getActiveTemplates().remove(e.getKey());
          db.saveActiveTemplates();
        }
        JsonObject op = op("active_template");
        op.addProperty("type", e.getKey());
        op.add("template", e.getValue());
        ops.add(op);
      }
    if (ops.isEmpty())
      throw new InvalidParams("Give at least one of " + String.join(", ", KEYS));
    return ops;
  }

  private static boolean has(JsonObject a, String k) {
    return a.has(k) && !a.get(k).isJsonNull();
  }

  private static JsonObject object(JsonObject a, String k) {
    if (!a.get(k).isJsonObject())
      throw new InvalidParams(k + " must be an object");
    return a.getAsJsonObject(k);
  }

  private static JsonObject op(String what) {
    JsonObject o = new JsonObject();
    o.addProperty("op", "settings");
    o.addProperty("setting", what);
    return o;
  }

  /** The type of a value by its name: string, integer, number, boolean, list, html. */
  private static AttributeType type(String name) {
    switch (name.toLowerCase()) {
      case "string":
      case "text":
        return AttributeType.STRING;
      case "integer":
      case "int":
        return AttributeType.INT;
      case "number":
      case "float":
        return AttributeType.FLOAT;
      case "boolean":
      case "bool":
        return AttributeType.BOOL;
      case "list":
        return AttributeType.LIST;
      case "html":
        return AttributeType.HTML;
      default:
        throw new InvalidParams("unknown type " + name + " (string, integer, number, boolean, list, html, enum:<name>)");
    }
  }

  /** A value of the type from JSON. */
  private static Object convert(AttributeType t, JsonElement v, String what) {
    try {
      switch (t) {
        case INT:
          return v.getAsInt();
        case FLOAT:
          return v.getAsDouble();
        case BOOL:
          if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException();
          return v.getAsBoolean();
        case LIST: {
          if (!v.isJsonArray())
            throw new IllegalArgumentException();
          List<Object> items = new ArrayList<>();
          for (JsonElement x : v.getAsJsonArray())
            items.add(x.getAsString());
          return new ListValue(AttributeType.STRING, items);
        }
        case HTML:
          return Changes.html(v.getAsString());
        default:
          return v.getAsString();
      }
    } catch (RuntimeException e) {
      throw new InvalidParams(what + ": " + v + " is no " + name(t));
    }
  }

  /** attributes: name -> {type, value} sets a project attribute (inherited by all nodes); null removes it. */
  private static JsonObject setAttribute(TreeDB db, TreeNode root, String key, JsonElement spec) {
    if (key.startsWith("_") || key.startsWith("%") || !Attribute.checkNameIsValid(key, false))
      throw new InvalidParams("attributes: invalid attribute name " + key);
    JsonObject op = op("attribute");
    op.addProperty("name", key);
    if (spec.isJsonNull()) {
      if (root.attrs().removeAttribute(key) == null)
        throw new ToolError("attributes: the project has no attribute " + key);
      root.commitAttributes();
      op.addProperty("removed", true);
      return op;
    }
    if (!spec.isJsonObject() || !spec.getAsJsonObject().has("value"))
      throw new InvalidParams("attributes: " + key + " must be {\"type\": ..., \"value\": ...} or null");
    JsonObject s = spec.getAsJsonObject();
    String typeName = Core.optStr(s, "type", "string");
    JsonElement v = s.get("value");
    AttributeDefinition d;
    if (typeName.toLowerCase().startsWith("enum:")) {
      String en = typeName.substring(5);
      EnumDefinitionValue edv = enumDefinition(db, en);
      if (edv == null)
        throw new ToolError("attributes: no enumeration " + en + " (see get_project_settings)");
      String val = v.getAsString();
      if (!edv.containsValue(val))
        throw new InvalidParams("attributes: " + val + " is not a value of " + en + " (" + values(edv) + ")");
      d = new AttributeDefinition(root.getUUId(), key, AttributeAvailability.SUBTREE,
          new ConstValueProvider(AttributeType.ENUM, new AppliedEnumValue(val, null, edv.getType(), en)),
          AttributeDefinition.detectFlags(key));
    } else {
      AttributeType t = type(typeName);
      d = new AttributeDefinition(root.getUUId(), key, AttributeAvailability.SUBTREE,
          new ConstValueProvider(t, convert(t, v, "attributes: " + key)), AttributeDefinition.detectFlags(key));
    }
    root.attrs().setAttribute(d);
    root.commitAttributes();
    op.addProperty("type", typeName);
    op.add("value", v);
    return op;
  }

  static EnumDefinitionValue enumDefinition(TreeDB db, String name) {
    for (Attribute a : db.getEnumDefinitons())
      if (a.getKey().equals(name) && a.getValue() instanceof EnumDefinitionValue)
        return (EnumDefinitionValue) a.getValue();
    return null;
  }

  private static List<String> values(EnumDefinitionValue edv) {
    List<String> out = new ArrayList<>();
    for (EnumValue v : edv)
      out.add(String.valueOf(v.getValue()));
    return out;
  }

  /** The nodes that use a value of an enumeration (all values: null), at most a few, for an error. */
  private static List<String> users(TreeDB db, String en, String value, int max) {
    List<String> out = new ArrayList<>();
    java.util.ArrayDeque<TreeNode> q = new java.util.ArrayDeque<>();
    q.add(db.getRootNode());
    while (!q.isEmpty() && out.size() < max) {
      TreeNode n = q.poll();
      for (Attribute a : n.attrs())
        if (a != null && a.isLocal(n.getUUId()) && a.getValue() instanceof AppliedEnumValue) {
          AppliedEnumValue v = (AppliedEnumValue) a.getValue();
          if (en.equals(v.getBaseKey()) && (value == null || value.equals(String.valueOf(v.getValue())))) {
            out.add(n.getQualifiedId() + " (" + a.getKey() + ")");
            break;
          }
        }
      for (TreeNode c : n.getWorkChildren())
        if (c != null && !c.isDisposed() && !c.isVirtual())
          q.add(c);
    }
    return out;
  }

  /** enums: name -> {type, values: [value or {value, comment}]} sets an enumeration; null removes an unused one. */
  private static JsonObject setEnum(TreeDB db, TreeNode root, String name, JsonElement spec) {
    if (name.startsWith("_") || name.startsWith("%") || !Attribute.checkNameIsValid(name, false))
      throw new InvalidParams("enums: invalid name " + name);
    EnumDefinitionValue old = enumDefinition(db, name);
    JsonObject op = op("enum");
    op.addProperty("name", name);
    if (spec.isJsonNull()) {
      if (old == null)
        throw new ToolError("enums: the project has no enumeration " + name);
      List<String> used = users(db, name, null, 5);
      if (!used.isEmpty())
        throw new ToolError("enums: " + name + " is used by " + used + ": change these values first");
      root.attrs().removeAttribute(name);
      root.commitAttributes();
      op.addProperty("removed", true);
      return op;
    }
    if (!spec.isJsonObject() || !spec.getAsJsonObject().has("values") || !spec.getAsJsonObject().get("values").isJsonArray())
      throw new InvalidParams("enums: " + name + " must be {\"type\": ..., \"values\": [...]} or null");
    JsonObject s = spec.getAsJsonObject();
    if (old == null && root.attrs().getAttribute(name) != null)
      throw new ToolError("enums: the project has an attribute " + name + " that is no enumeration");
    AttributeType t = type(Core.optStr(s, "type", "string"));
    if (t != AttributeType.STRING && t != AttributeType.INT && t != AttributeType.FLOAT)
      throw new InvalidParams("enums: the type of an enumeration is string, integer or number");
    List<EnumValue> vs = new ArrayList<>();
    Map<String, Boolean> seen = new LinkedHashMap<>();
    for (JsonElement x : s.getAsJsonArray("values")) {
      JsonElement v = x.isJsonObject() ? x.getAsJsonObject().get("value") : x;
      String comment = x.isJsonObject() ? Core.optStr(x.getAsJsonObject(), "comment", null) : null;
      if (v == null || v.isJsonNull())
        throw new InvalidParams("enums: " + name + ": a value is missing");
      Object val = convert(t, v, "enums: " + name);
      if (seen.put(String.valueOf(val), true) != null)
        throw new InvalidParams("enums: " + name + ": the value " + val + " is twice");
      vs.add(new EnumValue(val, comment));
    }
    if (vs.isEmpty())
      throw new InvalidParams("enums: " + name + " needs at least one value");
    // a value that nodes use stays
    if (old != null)
      for (String v : values(old))
        if (!seen.containsKey(v)) {
          List<String> used = users(db, name, v, 5);
          if (!used.isEmpty())
            throw new ToolError("enums: the value " + v + " of " + name + " is used by " + used + ": change these first");
        }
    root.attrs().setConstAttribute(name, new EnumDefinitionValue(t, vs), AttributeDefinition.detectFlags(name));
    root.commitAttributes();
    op.addProperty("type", name(t));
    op.add("values", s.get("values"));
    return op;
  }

  /** required_attributes: node type -> [{attribute, default}] (null or []: none); the nodes of the type get them. */
  private static JsonObject setRequired(TreeDB db, JsonObject spec) {
    RequiredAttributesRule rule = db.getAttributeRules().getRequiredAttrRule();
    Map<String, Map<String, AttributeDefinition>> map = new HashMap<>();
    if (rule.getMap() != null)
      for (Map.Entry<String, Map<String, AttributeDefinition>> e : rule.getMap().entrySet())
        map.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
    for (Map.Entry<String, JsonElement> e : spec.entrySet()) {
      String type = e.getKey();
      if (!Changes.TYPES.contains(type) && !type.equals("Document") && !type.equals("Location"))
        throw new InvalidParams("required_attributes: unknown node type " + type + " (" + Changes.TYPES + ", Document)");
      if (e.getValue().isJsonNull() || e.getValue().isJsonArray() && e.getValue().getAsJsonArray().isEmpty()) {
        map.remove(type);
        continue;
      }
      if (!e.getValue().isJsonArray())
        throw new InvalidParams("required_attributes: " + type + " must be a list of {attribute, default}");
      Map<String, AttributeDefinition> m = new LinkedHashMap<>();
      for (JsonElement x : e.getValue().getAsJsonArray()) {
        if (!x.isJsonObject() || !x.getAsJsonObject().has("attribute"))
          throw new InvalidParams("required_attributes: " + type + ": each item is {\"attribute\": ..., \"default\": ...}");
        JsonObject o = x.getAsJsonObject();
        String key = Core.str(o, "attribute");
        if (key.startsWith("_") || key.startsWith("%") || !Attribute.checkNameIsValid(key, false))
          throw new InvalidParams("required_attributes: invalid attribute name " + key);
        JsonElement def = o.has("default") && !o.get("default").isJsonNull() ? o.get("default") : new JsonPrimitive("");
        AttributeType t = def.isJsonPrimitive() && def.getAsJsonPrimitive().isBoolean() ? AttributeType.BOOL
            : def.isJsonPrimitive() && def.getAsJsonPrimitive().isNumber()
                ? (def.getAsString().matches("-?\\d+") ? AttributeType.INT : AttributeType.FLOAT)
                : def.isJsonArray() ? AttributeType.LIST : AttributeType.STRING;
        m.put(key, new AttributeDefinition(null, key, new ConstValueProvider(t, convert(t, def, "required_attributes: " + key))));
      }
      map.put(type, m);
    }
    rule.setMap(db, map);
    db.getAttributeRules().saveRules();
    // the nodes of the types get the attributes they miss, with the default values (as Requality does)
    int changed = 0;
    java.util.ArrayDeque<TreeNode> q = new java.util.ArrayDeque<>();
    q.add(db.getRootNode());
    while (!q.isEmpty()) {
      TreeNode n = q.poll();
      Map<String, AttributeDefinition> m = map.get(n.getType());
      boolean c = false;
      if (m != null)
        for (Map.Entry<String, AttributeDefinition> d : m.entrySet())
          if (n.attrs().getAttribute(d.getKey()) == null) {
            AttributeDefinition ad = d.getValue();
            n.attrs().setAttribute(new AttributeDefinition(n.getUUId(), d.getKey(), AttributeDefinition.detectValueProvider(
                d.getKey(), ad.getType(), ad.getRawValue().getValue(), TempFolderFilesProvider.getNewNotInited(ad.getType()))));
            c = true;
          }
      if (c) {
        n.commitAttributes();
        changed++;
      }
      for (TreeNode ch : n.getWorkChildren())
        if (ch != null && !ch.isDisposed() && !ch.isVirtual())
          q.add(ch);
    }
    JsonObject op = op("required_attributes");
    op.add("required_attributes", spec);
    op.addProperty("nodes_completed", changed);
    return op;
  }

  /** checker_rules: [{name, formula}] replaces the checker rules (JavaScript that is true for a correct node). */
  private static JsonObject setRules(TreeDB db, JsonElement spec) {
    if (!spec.isJsonArray())
      throw new InvalidParams("checker_rules must be a list of {name, formula}");
    CheckerRulesHolder crh = db.getAttributeRules().getCheckerRules();
    List<JSFormula> rules = new ArrayList<>();
    javax.script.ScriptEngine engine = com.unitesk.requality.tool.CoreUtils.getJSEngine();
    for (JsonElement x : spec.getAsJsonArray()) {
      if (!x.isJsonObject())
        throw new InvalidParams("checker_rules: each rule is {\"name\": ..., \"formula\": ...}");
      String name = Core.str(x.getAsJsonObject(), "name");
      String formula = Core.str(x.getAsJsonObject(), "formula");
      // a formula that does not compile would fail on every node
      if (engine instanceof javax.script.Compilable)
        try {
          ((javax.script.Compilable) engine).compile(formula);
        } catch (javax.script.ScriptException e) {
          throw new InvalidParams("checker_rules: the formula of " + name + " is no JavaScript expression: " + e.getMessage());
        }
      rules.add(new JSFormula(name, formula, crh));
    }
    crh.setRules(rules);
    JsonObject op = op("checker_rules");
    op.add("checker_rules", spec);
    return op;
  }
}
