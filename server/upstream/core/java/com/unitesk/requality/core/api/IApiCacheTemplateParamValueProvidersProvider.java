package com.unitesk.requality.core.api;

import java.util.Set;
import com.unitesk.requality.report.template.ParamDataProvider;

public interface IApiCacheTemplateParamValueProvidersProvider {
	public Set<String> getIds();
	public ParamDataProvider getProvider(String s);
}
