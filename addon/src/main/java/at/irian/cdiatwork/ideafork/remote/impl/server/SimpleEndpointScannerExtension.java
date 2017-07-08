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

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.Path;
import java.util.HashSet;
import java.util.Set;

public class SimpleEndpointScannerExtension implements Extension {
    private Set<Class> endpointClasses = new HashSet<Class>();
    private String applicationPath = "/";
    private String version = null;

    protected void detectEndpoints(@Observes ProcessAnnotatedType pat) {
        Class<?> beanClass = pat.getAnnotatedType().getJavaClass();

        ApplicationPath applicationPath = beanClass.getAnnotation(ApplicationPath.class);

        if (applicationPath != null) {
            this.applicationPath = applicationPath.value();
            return;
        }

        createCacheEntryFor(beanClass);
    }

    //needs to be aligned with the logic in ServiceConfigUpdateTask
    private void createCacheEntryFor(Class<?> beanClass) {
        Path path = beanClass.getAnnotation(Path.class);

        if (path != null) {
            this.endpointClasses.add(beanClass);
        }
    }

    public Set<Class> getEndpointClasses() {
        return endpointClasses;
    }

    private static String extractVersion(String applicationPath) {
        String serviceVersion = "0";
        if (applicationPath.startsWith("v")) {
            if (applicationPath.contains("/")) {
                serviceVersion = applicationPath.substring(applicationPath.indexOf("v"), applicationPath.indexOf("/"));
            } else {
                serviceVersion = applicationPath.substring(1);
            }
        } else if (applicationPath.contains("/v")) {
            serviceVersion = applicationPath.substring(applicationPath.indexOf("/v") + 1);
        }

        if (serviceVersion.contains("/")) {
            serviceVersion = serviceVersion.substring(0, serviceVersion.indexOf("/"));
        }
        return serviceVersion;
    }

    public String getApplicationPath() {
        return applicationPath;
    }

    public String getVersion() {
        if (version != null) {
            return version;
        }
        version = extractVersion(applicationPath);
        return version;
    }
}
