package com.unitesk.requality.report.template;

import com.unitesk.requality.report.template.StoredParamsContainer.Condition;

public class TemplateParamDefinition implements IParamValueConsumer,IDefValueData{
  public String name;
  public ParamTypeDefinition[]types;
  public StoredParamsContainer attrs = new StoredParamsContainer(Condition.WITHOUT);
  public Object defvalue;
  public String description;
  public String stringTypes = null;
  public String provider = null;
  public void setTypes(String tstring,ParamTypeDefinition[] types) {
    this.types = types;
    this.stringTypes = tstring;
  }
  public void addParamDefinition(StoredParamDefinition spd) {
    attrs.addParamDefinition(spd);
  }
  public void addParamDefinition(StoredParamsContainer spd) {
    attrs.addParamContainer(spd);
  }
  @Override
  public void setDefaultValue(Object defvalue) {
    this.defvalue = defvalue;
  }
  @Override
  public Object getDefaultValue() {
    return defvalue;
  }
  @Override
  public void setDataProvider(String data) {
    provider = data;
  }
  @Override
  public String getDataProvider() {
    return provider;
  }
}
