package com.unitesk.requality.core.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import com.unitesk.requality.repo.ICVSClient;
import com.unitesk.requality.repo.IRepoMapper;
@ApiExtension
public interface IAPIRepoSource {

  Map<String, List<ICVSClient>> getRepositoryProviders();

  Collection<IRepoMapper> getRepositoryMappers();
}