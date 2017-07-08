/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package at.irian.cdiatwork.ideafork.remote.impl.server;

import at.irian.cdiatwork.ideafork.remote.spi.DistributedCacheManager;
import at.irian.cdiatwork.ideafork.remote.spi.ServiceDescriptor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.deltaspike.core.util.ExceptionUtils;
import org.apache.deltaspike.scheduler.api.Scheduled;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import javax.inject.Inject;
import javax.ws.rs.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

@Scheduled(cronExpression = "{service-config_update-schedule}", startScopes = { /*no additional scope-handling needed*/ })
public class ServiceConfigUpdateTask implements Job {
    private static final Logger LOG = Logger.getLogger(ServiceConfigUpdateTask.class.getName());

    @Inject
    private SimpleEndpointScannerExtension endpointScannerExtension;

    @Inject
    private DistributedCacheManager cacheProvider;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Set<Class> endpointClasses = endpointScannerExtension.getEndpointClasses();

        for (Class endpointClass : endpointClasses) {
            createCacheEntryFor(endpointClass);
        }
    }

    private void createCacheEntryFor(Class<?> beanClass) {
        Path path = beanClass.getAnnotation(Path.class);

        if (path != null) {
            ObjectMapper objectMapper = new ObjectMapper();

            try {
                String applicationPath = endpointScannerExtension.getApplicationPath();
                String serviceVersion = endpointScannerExtension.getVersion();
                ServiceDescriptor serviceDescriptor = ServiceDescriptorFactory.create(applicationPath, path.value(), serviceVersion);
                String serviceDescriptorAsJson = objectMapper.writeValueAsString(serviceDescriptor);

                ConcurrentMap<String, String> cache = cacheProvider.getCache();
                String cachedValue = cache.get(serviceDescriptor.getKey());

                if (!serviceDescriptorAsJson.equals(cachedValue)) {
                    cache.put(serviceDescriptor.getKey(), serviceDescriptorAsJson);
                }
                LOG.fine("cached endpoint descriptor: " + serviceDescriptor.getKey() + " -> " + serviceDescriptorAsJson);
            } catch (JsonProcessingException e) {
                throw ExceptionUtils.throwAsRuntimeException(e);
            }
        }
    }
}
