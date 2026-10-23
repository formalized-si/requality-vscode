package com.unitesk.requality.core.attribute.reference;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.unitesk.requality.core.model.Pair;

public class TreeRelationsCounter {
  public enum Direction {Forward, Backward}
  public static TreeRelationsCounter EMPTY = new TreeRelationsCounter(Direction.Forward, Collections.emptyMap(), Collections.emptyMap());
  public static Pair<TreeRelationsCounter, TreeRelationsCounter> EMPTY_PAIR = new Pair<>(EMPTY, EMPTY);
  public Direction dir = Direction.Forward;
  public static String ALL="|ALL|";
  public Map<String,Map<String,Integer>> linksInfo = null;
  public Map<String,Map<String,Integer>> termsLinksInfo = null;
  public boolean isEmpty() {
    return this == EMPTY || termsLinksInfo.size() == 0 && linksInfo.size() == 0;
  }
  public TreeRelationsCounter(Direction d, Map<String,Map<String,Integer>> links, Map<String,Map<String,Integer>> termsLinks) {
    this.dir = d;
    this.linksInfo = links;
    this.termsLinksInfo = termsLinks;
  }
  public TreeRelationsCounter merge(TreeRelationsCounter first) {
      Map<String,Map<String,Integer>> ret1 = new HashMap<>();
      Map<String,Map<String,Integer>> ret2 = new HashMap<>();
      if(dir!=first.dir)
        throw new RuntimeException ("Attempted to merge non-compatible links set");
      TreeRelationsCounter ret = new TreeRelationsCounter(dir, ret1, ret2);
      ret1.putAll(linksInfo);
      for(Entry<String, Map<String, Integer>> en: first.linksInfo.entrySet()) {
        if(!ret1.containsKey(en.getKey()))
          ret1.put(en.getKey(), new HashMap<>(en.getValue()));
        else {
          Map<String, Integer> old = ret1.get(en.getKey());
          for(Entry<String, Integer> en2: en.getValue().entrySet()) {
            if(!old.containsKey(en2.getKey()))
              old.put(en2.getKey(), en2.getValue());
            else
              old.put(en2.getKey(), en2.getValue()+old.get(en2.getKey()));
          }
        }
      }
      ret2.putAll(termsLinksInfo);
      for(Entry<String, Map<String, Integer>> en: first.termsLinksInfo.entrySet()) {
          if(!ret2.containsKey(en.getKey()))
            ret2.put(en.getKey(), new HashMap<>(en.getValue()));
          else {
            Map<String, Integer> old = ret2.get(en.getKey());
            for(Entry<String, Integer> en2: en.getValue().entrySet()) {
              if(!old.containsKey(en2.getKey()))
                old.put(en2.getKey(), en2.getValue());
              else
                old.put(en2.getKey(), en2.getValue()+old.get(en2.getKey()));
            }
          }
        }
      return ret;
  }
  public void addRecord(String p,String rt, boolean isTerm) {
      Map<String, Integer> old;
      if(isTerm)
      {
        old = termsLinksInfo.get(p);
        if(old == null) {
        	old = new HashMap<>();
        	termsLinksInfo.put(p, old);
        }
      }else {
        old = linksInfo.get(p);
        if(old == null) {
        	old = new HashMap<>();
        	linksInfo.put(p, old);
        }
      }
      old.put(rt, old.getOrDefault(rt, 0)+1);
      old.put(ALL, old.getOrDefault(ALL, 0)+1);
  }
  @Override
  public String toString() {
      StringBuffer sb = new StringBuffer();
      sb.append(dir == Direction.Forward?"F":"B");
      sb.append("[T:");
      boolean f = true;
      for(Entry<String, Map<String, Integer>> en: termsLinksInfo.entrySet()) {
        if(f)
          f = false;
        else
          sb.append(", ");
        sb.append(en.getKey());
        sb.append(": ");
        sb.append(en.getValue());
      }
      sb.append("]");
      sb.append("[L:");
      f = true;
      for(Entry<String, Map<String, Integer>> en: linksInfo.entrySet()) {
        if(f)
          f = false;
        else
          sb.append(", ");
        sb.append(en.getKey());
        sb.append(": ");
        sb.append(en.getValue());
      }
      sb.append("]");
      return sb.toString();
  }
}
