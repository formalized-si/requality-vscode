package com.unitesk.requality.report.template;

public class StoredParamDefinition implements IParamValueConsumer, IDefValueData, IParamsProvider {
  public String name;
  public String description;
  public Object defvalue;
  public ParamTypeDefinition[] types =
      new ParamTypeDefinition[] {ParamTypeDefinition.STRING};
  public String provider = null;
  public String typesstring;
  @Override
  public void setTypes(String tstring, ParamTypeDefinition[] types2) {
    this.typesstring = tstring;
    types = types2;
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
  @Override
  public boolean isContainer() {
    return false;
  }
  @Override
  public IParamsProvider[] getProviders() {
    return new IParamsProvider[] {this};
  }
  
}
