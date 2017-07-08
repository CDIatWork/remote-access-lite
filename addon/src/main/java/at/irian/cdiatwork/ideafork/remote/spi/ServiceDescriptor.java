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
package at.irian.cdiatwork.ideafork.remote.spi;

import java.util.Set;

@SuppressWarnings("unused")
public class ServiceDescriptor {
    private String protocol;
    private Set<String> addresses;
    private String port;
    private String targetServiceMethod;
    private String key;
    private String version;

    public ServiceDescriptor() {
        //needed for de-serialization
    }

    public ServiceDescriptor(String applicationPath, Set<String> addresses, String port, String resourceName, String version) {
        this("http://", applicationPath, addresses, port, resourceName, version);
    }

    private ServiceDescriptor(String protocol, String applicationPath, Set<String> addresses, String port, String resourceName, String version) {
        this.protocol = protocol;
        this.addresses = addresses;
        this.port = port;
        this.targetServiceMethod = resourceName.replace("/", "");
        this.version = version.replace("/", "");
        this.key = version + "/" + resourceName;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public Set<String> getAddresses() {
        return addresses;
    }

    public void setAddresses(Set<String> addresses) {
        this.addresses = addresses;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getTargetServiceMethod() {
        return targetServiceMethod;
    }

    public void setTargetServiceMethod(String targetServiceMethod) {
        this.targetServiceMethod = targetServiceMethod;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
