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
package at.irian.cdiatwork.ideafork.remote.impl;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import org.apache.deltaspike.core.api.exclude.Exclude;
import at.irian.cdiatwork.ideafork.remote.api.ServiceNotReachableEvent;
import at.irian.cdiatwork.ideafork.remote.spi.DistributedCacheManager;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import static java.util.concurrent.TimeUnit.SECONDS;

//exclude it if a value is configured and it isn't 'hazelcast'
@ApplicationScoped
public class HazelcastCacheManager implements DistributedCacheManager {
    @Inject
    private HazelcastInstance hazelcastInstance;

    @Produces
    @ApplicationScoped
    protected HazelcastInstance exposeHazelcastInstance() {
        Config config = new Config();
        return Hazelcast.newHazelcastInstance(config);
    }

    @Override
    public IMap<String, String> getCache() {
        return hazelcastInstance.getMap("service-configs");
    }

    @Override
    public void removeLocally(String key) {
        getCache().tryRemove(key, 3, SECONDS);
    }

    protected void onServiceNotReachableEvent(@Observes ServiceNotReachableEvent event, DistributedCacheManager cacheProvider) {
        cacheProvider.removeLocally(event.getServiceKey());
    }
}
