/*
 * Copyright (C) 2017-2019 Dremio Corporation
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
package com.dremio.dac.daemon;

import static com.dremio.dac.server.test.DataPopulatorUtils.addDefaultDremioUser;

import com.dremio.common.AutoCloseables;
import com.dremio.dac.model.usergroup.UserName;
import com.dremio.dac.server.DACSecurityContext;
import com.dremio.dac.server.test.SampleDataPopulator;
import com.dremio.dac.service.collaboration.CollaborationHelper;
import com.dremio.dac.service.datasets.DatasetVersionMutator;
import com.dremio.dac.service.reflection.ReflectionServiceHelper;
import com.dremio.dac.service.search.SearchService;
import com.dremio.dac.service.source.SourceService;
import com.dremio.datastore.api.LegacyKVStoreProvider;
import com.dremio.exec.catalog.ConnectionReader;
import com.dremio.exec.server.SabotContext;
import com.dremio.exec.store.CatalogService;
import com.dremio.options.OptionManager;
import com.dremio.service.Service;
import com.dremio.service.jobs.JobsService;
import com.dremio.service.namespace.NamespaceService;
import com.dremio.service.reflection.ReflectionSettings;
import com.dremio.service.reflection.ReflectionSettingsImpl;
import com.dremio.service.users.SystemUser;
import com.dremio.service.users.UserService;
import java.time.Clock;
import javax.inject.Provider;
import javax.ws.rs.core.SecurityContext;

/** Starts the SampleDataPopulator */
public class SampleDataPopulatorService implements Service {
  private final Provider<SabotContext> sabotContextProvider;
  private final Provider<UserService> userServiceProvider;
  private final Provider<LegacyKVStoreProvider> kvStore;
  private final Provider<JobsService> jobsService;
  private final Provider<CatalogService> catalogService;
  private final Provider<ConnectionReader> connectionReader;
  private final Provider<OptionManager> optionManager;
  private final Provider<SearchService> searchService;

  private SampleDataPopulator sample;

  private final boolean prepopulate;
  private final boolean addDefaultUser;

  public SampleDataPopulatorService(
      Provider<SabotContext> sabotContextProvider,
      Provider<LegacyKVStoreProvider> kvStore,
      Provider<UserService> userServiceProvider,
      Provider<JobsService> jobsService,
      Provider<CatalogService> catalogService,
      Provider<ConnectionReader> connectionReader,
      Provider<SearchService> searchService,
      Provider<OptionManager> optionManager,
      boolean prepopulate,
      boolean addDefaultUser) {
    this.sabotContextProvider = sabotContextProvider;
    this.kvStore = kvStore;
    this.userServiceProvider = userServiceProvider;
    this.jobsService = jobsService;
    this.catalogService = catalogService;
    this.connectionReader = connectionReader;
    this.searchService = searchService;
    this.optionManager = optionManager;
    this.prepopulate = prepopulate;
    this.addDefaultUser = addDefaultUser;
  }

  public Provider<OptionManager> getOptionManager() {
    return optionManager;
  }

  @Override
  public void start() throws Exception {
    final LegacyKVStoreProvider kv = kvStore.get();
    final NamespaceService systemUserNamespaceService =
        sabotContextProvider.get().getNamespaceService(SystemUser.SYSTEM_USERNAME);

    addDefaultUser();
    if (prepopulate) {
      final ReflectionServiceHelper reflectionServiceHelper =
          new SampleReflectionServiceHelper(systemUserNamespaceService, kvStore, optionManager);

      final DatasetVersionMutator data =
          new DatasetVersionMutator(
              kv,
              jobsService.get(),
              catalogService.get(),
              optionManager.get(),
              sabotContextProvider.get());
      SecurityContext securityContext =
          new DACSecurityContext(
              new UserName(SystemUser.SYSTEM_USERNAME), SystemUser.SYSTEM_USER, null);
      final SourceService ss =
          new SourceService(
              Clock.systemUTC(),
              optionManager.get(),
              systemUserNamespaceService,
              data,
              catalogService.get(),
              reflectionServiceHelper,
              null,
              connectionReader.get(),
              securityContext);
      final UserService userService = userServiceProvider.get();
      sample =
          new SampleDataPopulator(
              sabotContextProvider.get(),
              ss,
              data,
              userService,
              sabotContextProvider.get().getNamespaceService(SampleDataPopulator.DEFAULT_USER_NAME),
              SampleDataPopulator.DEFAULT_USER_NAME,
              new CollaborationHelper(
                  kv,
                  systemUserNamespaceService,
                  securityContext,
                  searchService.get(),
                  userService,
                  catalogService.get(),
                  optionManager.get()));

      sample.populateInitialData();
    }
  }

  public void addDefaultUser() throws Exception {
    final NamespaceService ns =
        sabotContextProvider.get().getNamespaceService(SystemUser.SYSTEM_USERNAME);

    if (addDefaultUser) {
      addDefaultDremioUser(userServiceProvider.get(), ns);
    }
  }

  @Override
  public void close() throws Exception {
    AutoCloseables.close(sample);
  }

  /**
   * ReflectionServiceHelper for SampleDataPopulator. All it needs is retrieving of
   * ReflectionSettings.
   */
  class SampleReflectionServiceHelper extends ReflectionServiceHelper {
    private final NamespaceService namespace;
    private final Provider<LegacyKVStoreProvider> storeProvider;

    public SampleReflectionServiceHelper(
        NamespaceService namespace,
        Provider<LegacyKVStoreProvider> storeProvider,
        Provider<OptionManager> optionManagerProvider) {
      super(null, null, optionManagerProvider.get());
      this.namespace = namespace;
      this.storeProvider = storeProvider;
    }

    @Override
    public ReflectionSettings getReflectionSettings() {
      return new ReflectionSettingsImpl(
          () -> namespace, catalogService, storeProvider, this::getOptionManager);
    }
  }
}
