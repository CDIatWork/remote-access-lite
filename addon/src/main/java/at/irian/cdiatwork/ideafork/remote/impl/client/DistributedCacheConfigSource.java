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

import org.apache.deltaspike.core.api.provider.BeanManagerProvider;
import org.apache.deltaspike.core.api.provider.BeanProvider;
import org.apache.deltaspike.core.spi.config.ConfigSource;
import at.irian.cdiatwork.ideafork.remote.spi.DistributedCacheManager;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Map;

public class DistributedCacheConfigSource implements ConfigSource {
    private final static int ordinal = 3000;

    @Inject
    private DistributedCacheManager cacheProvider;

    @Override
    public int getOrdinal() {
        return ordinal;
    }

    @Override
    public String getPropertyValue(String key) {
        if (this.cacheProvider == null) {
            if (!BeanManagerProvider.isActive()) {
                return null;
            }
            BeanProvider.injectFields(this);
        }
        return cacheProvider.getCache().get(key);
    }

    @Override
    public String getConfigName() {
        return "distributed-cache-service-config";
    }

    @Override
    public boolean isScannable() {
        return false;
    }

    @Override
    public Map<String, String> getProperties() {
        return Collections.emptyMap();
    }
}
