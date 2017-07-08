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
package at.irian.cdiatwork.ideafork.remote.api;

import org.apache.deltaspike.partialbean.api.PartialBeanBinding;

import javax.enterprise.util.Nonbinding;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@PartialBeanBinding

@Retention(RUNTIME)
@Target(TYPE)
public @interface ResourceClient {
    @Nonbinding
    String name();

    @Nonbinding
    String version();

    @Nonbinding
    boolean preferLocalNode() default true;

    @Nonbinding
    long connectionTimeout() default 3000;

    @Nonbinding
    long readTimeout() default 60_000;
}
