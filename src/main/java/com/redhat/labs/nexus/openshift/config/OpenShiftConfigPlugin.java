package com.redhat.labs.nexus.openshift.config;

/*-
 * #%L
 * com.redhat.labs.nexus:nexus-openshift-plugin
 * %%
 * Copyright (C) 2008 - 2019 Red Hat
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.redhat.labs.nexus.openshift.helpers.RepositoryApi;
import com.redhat.labs.nexus.openshift.watchers.BlobStoreConfigWatcher;
import com.redhat.labs.nexus.openshift.watchers.RepositoryConfigWatcher;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.nexus.blobstore.api.BlobStoreManager;
import org.sonatype.nexus.security.SecuritySystem;

import javax.inject.Inject;

public class OpenShiftConfigPlugin {
  private static final Logger LOG = LoggerFactory.getLogger(OpenShiftConfigPlugin.class);

  @Inject
  BlobStoreManager blobStoreManager;

  @Inject
  RepositoryApi repository;

  @Inject
  SecuritySystem security;

  public OpenShiftConfigPlugin() throws Exception {
    // This supports both stock K8s AND OpenShift so we don't have to use one or the other.
    // If running in OpenShift or K8s, it will automatically detect the correct settings and service account credentials
    // from the /run/secrets/kubernetes.io/serviceaccount directory
    try (OpenShiftClient client = new DefaultOpenShiftClient()) {
      Secret nexusCredentials = client.secrets().withName("nexus").get();

      String password = nexusCredentials.getData().getOrDefault("password", System.getenv().getOrDefault("NEXUS_PASSWORD", "admin123"));
      security.changePassword("admin", password);

      client.configMaps().withLabel("nexus-type==blobstore").list().getItems().stream().forEach(blobStore -> {
        BlobStoreConfigWatcher.addBlobStore(blobStore, blobStoreManager);
      });

      client.configMaps().withLabel("nexus-type==repository").list().getItems().stream().forEach(repoConfig -> {
        RepositoryConfigWatcher.createNewRepository(repository, repoConfig);
      });

      client.configMaps().withLabel("nexus-type==blobstore").watch(new BlobStoreConfigWatcher(blobStoreManager));

      client.configMaps().withLabel("nexus-type==repository").watch(new RepositoryConfigWatcher(repository, blobStoreManager));
    }
  }
}
