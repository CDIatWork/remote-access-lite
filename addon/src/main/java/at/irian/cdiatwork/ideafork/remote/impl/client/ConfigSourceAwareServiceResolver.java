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
package at.irian.cdiatwork.ideafork.remote.impl.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.deltaspike.core.api.config.ConfigResolver;
import org.apache.deltaspike.core.util.ExceptionUtils;
import at.irian.cdiatwork.ideafork.remote.spi.ServiceDescriptor;
import at.irian.cdiatwork.ideafork.remote.spi.ServiceResolver;

import javax.enterprise.context.ApplicationScoped;
import java.io.IOException;

@ApplicationScoped
public class ConfigSourceAwareServiceResolver implements ServiceResolver {
    public ServiceDescriptor resolveServiceDetails(String serviceKey) {
        //result resolved by DistributedCacheConfigSource
        String cachedServiceDescriptor = ConfigResolver.getProjectStageAwarePropertyValue(serviceKey);

        ServiceDescriptor foundDescriptor = null;

        if (cachedServiceDescriptor != null) {
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                foundDescriptor = objectMapper.readValue(cachedServiceDescriptor, ServiceDescriptor.class);
            } catch (IOException e) {
                throw ExceptionUtils.throwAsRuntimeException(e);
            }
        }

        return foundDescriptor;
    }
}
