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

import at.irian.cdiatwork.ideafork.jwt.api.IdentityHolder;
import at.irian.cdiatwork.ideafork.jwt.impl.AuthenticationManager;
import at.irian.cdiatwork.ideafork.remote.api.*;
import at.irian.cdiatwork.ideafork.remote.spi.ServiceDescriptor;
import at.irian.cdiatwork.ideafork.remote.spi.ServiceInvocationContext;
import at.irian.cdiatwork.ideafork.remote.spi.ServiceResolver;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.deltaspike.core.util.AnnotationUtils;
import org.apache.deltaspike.core.util.ClassUtils;
import org.apache.deltaspike.core.util.ExceptionUtils;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.client.*;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
@ResourceClient(name = "", version = "")
public class RemoteResourceHandler implements InvocationHandler {
    private static final Logger LOG = Logger.getLogger(RemoteResourceHandler.class.getName());

    @Inject
    private BeanManager beanManager;

    @Inject
    private IdentityHolder identityHolder;

    @Inject
    private AuthenticationManager authenticationManager;

    @Inject
    private ServiceResolver serviceResolver;

    @Inject
    private ServiceInvocationContext serviceInvocationContext;

    private Map<String, ServiceDescriptor> previousServiceDescriptors = new ConcurrentHashMap<String, ServiceDescriptor>(); //in case one service couldn't connect - it shouldn't impact all others immediately (due to the immediate remove from the distributed cache)
    private Map<String, String> preferredAddressHolder = new ConcurrentHashMap<String, String>();

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        ResourceClient resourceClient = AnnotationUtils.extractAnnotationFromMethodOrClass(beanManager, method, proxy.getClass(), ResourceClient.class);

        String serviceName = resourceClient.name();
        String serviceVersion = resourceClient.version();

        String overruledVersion = serviceInvocationContext.getVersion();

        if (overruledVersion != null) {
            serviceVersion = overruledVersion;
        }

        String serviceKey = serviceVersion + "/" + serviceName;
        ServiceDescriptor sd = serviceResolver.resolveServiceDetails(serviceKey);

        boolean fireEvent = true;
        if (sd == null) {
            sd = this.previousServiceDescriptors.get(serviceKey);
            fireEvent = false; //don't fire it again if the try via the locally cached descriptor fails as well
        } else {
            this.previousServiceDescriptors.put(serviceKey, sd);
        }

        if (sd == null) {
            LOG.logp(Level.WARNING, method.getDeclaringClass().getName(), serviceName, "service '" + serviceName + "' isn't available");
            return null;
        }
        Client client = ClientBuilder.newClient();

        client = client.property("http.connection.timeout", resourceClient.connectionTimeout());
        client = client.property("http.receive.timeout", resourceClient.readTimeout());

        List<String> addressesToTry = new ArrayList<String>();
        String preferredAddress = this.preferredAddressHolder.get(serviceKey);

        if (preferredAddress != null) {
            addressesToTry.add(preferredAddress);
        }

        if (resourceClient.preferLocalNode() && !addressesToTry.contains("127.0.0.1")) {
            addressesToTry.add("127.0.0.1"); //just to prefer the local-machine
        }

        addressesToTry.addAll(sd.getAddresses());

        try {
            for (String address : addressesToTry) {
                try {
                    WebTarget webTarget = client.target(sd.getProtocol() + address + ":" + sd.getPort()).path(sd.getVersion()).path(sd.getTargetServiceMethod());

                    final Class responseType = method.getReturnType();
                    Class genericResponseType = null;

                    if (Collection.class.isAssignableFrom(responseType)) {
                        Object genericType = method.getGenericReturnType();

                        if (genericType instanceof ParameterizedType && ((ParameterizedType) genericType).getActualTypeArguments().length == 1) {
                            ParameterizedType parameterizedType = (ParameterizedType)method.getGenericReturnType();
                            Type[] genericTypeArguments = parameterizedType.getActualTypeArguments();

                            if (genericTypeArguments.length == 1 && genericTypeArguments[0] instanceof Class) {
                                genericResponseType = ((Class)genericTypeArguments[0]);
                            }
                        }
                    }


                    Object result = accessRemoteResource(args, webTarget, responseType, genericResponseType, method);
                    this.preferredAddressHolder.put(serviceKey, address);
                    return result;
                } catch (Throwable t) {
                    if (t instanceof UnexpectedServiceResultException) {
                        IgnoreResultWithStatusCode ignoreResultWithStatusCodeAnnotation =
                            AnnotationUtils.extractAnnotationFromMethod(beanManager, method, IgnoreResultWithStatusCode.class);

                        if (ignoreResultWithStatusCodeAnnotation != null) {
                            for (int statusCodeToIgnore : ignoreResultWithStatusCodeAnnotation.value()) {
                                if (statusCodeToIgnore == ((UnexpectedServiceResultException) t).getErrorCode()) {
                                    return null;
                                }
                            }
                        }
                        throw t;
                    }

                    if (t instanceof ConnectException || t.getCause() instanceof ConnectException) {
                        if (fireEvent) {
                            this.beanManager.fireEvent(new ServiceNotReachableEvent(serviceKey));
                        } else {
                            this.previousServiceDescriptors.remove(serviceKey);
                        }
                        continue;
                    }

                    if (t instanceof SocketTimeoutException || t.getCause() instanceof SocketTimeoutException) {
                        continue;
                    }

                    LOG.logp(Level.WARNING, method.getDeclaringClass().getName(), serviceName, "failed remote-service call", t);
                    throw ExceptionUtils.throwAsRuntimeException(t);
                }
            }
        } finally {
            client.close();
        }
        return null;
    }

    private Object accessRemoteResource(Object[] args, WebTarget webTarget, Class responseType, Class genericResponseType, Method method) throws IOException {
        Path subPath = method.getAnnotation(Path.class);
        if (subPath != null) {
            String subPathAsString = subPath.value();
            subPathAsString = replaceTemplateValues(args, method, subPathAsString, 0);

            if (!subPathAsString.startsWith("/")) {
                subPathAsString = "/" + subPathAsString;
            }

            if (!subPathAsString.endsWith("/")) {
                subPathAsString += "/";
            }
            webTarget = webTarget.path(subPathAsString);
        }

        if (method.getAnnotation(POST.class) != null) {
            return performPostRequest(webTarget, args, responseType, genericResponseType, method);
        }

        if (method.getAnnotation(GET.class) != null) {
            return performGetRequest(webTarget, args, responseType, genericResponseType, method);
        }

        if (method.getAnnotation(PUT.class) != null) {
            return performPutRequest(webTarget, args, responseType, genericResponseType, method);
        }

        if (method.getAnnotation(DELETE.class) != null) {
            return performDeleteRequest(webTarget, responseType, genericResponseType, method);
        }

        return performPostRequest(webTarget, args, responseType, genericResponseType, method);
    }

    //this prototype only supports post-requests
    private <T> T performPostRequest(WebTarget webTarget, Object[] args, Class<T> responseType, Class genericResponseType, Method method) throws IOException {
        Object value;

        List<Object> filteredArgs = new ArrayList<>();
        currentArg:
        for (int i = 0; i < args.length; i++) {
            for (Annotation parameterAnnotation : method.getParameterAnnotations()[i]) {
                if (QueryParam.class.isAssignableFrom(parameterAnnotation.annotationType())) {
                    String paramName = ((QueryParam) parameterAnnotation).value();
                    webTarget = webTarget.queryParam(paramName, args[i]);
                    continue currentArg;
                }
            }
            filteredArgs.add(args[i]);
        }

        if (filteredArgs.size() == 1) {
            value = filteredArgs.iterator().next();
        } else if (!filteredArgs.isEmpty()) {
            value = filteredArgs.toArray(new Object[filteredArgs.size()]);
        } else {
            value = new Object[]{};
        }

        ObjectMapper objectMapper = new ObjectMapper();
        value = createRequestObjectAsString(value, objectMapper);

        Response response;
        Invocation.Builder invocationBuilder =
                webTarget.request().header(HttpHeaders.AUTHORIZATION, "Bearer " + identityHolder.getCurrentToken());

        if (!Void.TYPE.equals(responseType)) {
            invocationBuilder.accept(MediaType.APPLICATION_JSON);
        }
        response = invocationBuilder.post(Entity.entity(value, MediaType.APPLICATION_JSON));

        return processResponse(response, responseType, genericResponseType, objectMapper, method);
    }

    private Object performPutRequest(WebTarget webTarget, Object[] args, Class responseType, Class genericResponseType, Method method) throws IOException {
        Object value = "";
        if (args.length == 1) {
            value = args[0];
        } else {
            List<Object> filteredArgs = new ArrayList<>();
            currentArg:
            for (int i = 0; i < args.length; i++) {
                for (Annotation parameterAnnotation : method.getParameterAnnotations()[i]) {
                    if (PathParam.class.isAssignableFrom(parameterAnnotation.annotationType())) {
                        continue currentArg;
                    }
                }
                filteredArgs.add(args[i]);
            }

            if (filteredArgs.size() == 1) {
                value = filteredArgs.iterator().next();
            } else if (!filteredArgs.isEmpty()) {
                value = filteredArgs.toArray(new Object[filteredArgs.size()]);
            }
        }

        ObjectMapper objectMapper = new ObjectMapper();
        value = createRequestObjectAsString(value, objectMapper);

        Response response;
        Invocation.Builder invocationBuilder =
                webTarget.request().header(HttpHeaders.AUTHORIZATION, "Bearer " + identityHolder.getCurrentToken());

        if (!Void.TYPE.equals(responseType)) {
            invocationBuilder.accept(MediaType.APPLICATION_JSON);
        }
        response = invocationBuilder.buildPut(Entity.entity(value, MediaType.APPLICATION_JSON)).invoke();
        return processResponse(response, responseType, genericResponseType, objectMapper, method);
    }

    private String replaceTemplateValues(Object[] args, Method method, String subPathAsString, int startIndex) {
        if (subPathAsString.contains("{")) {
            String placeholder = subPathAsString.substring(subPathAsString.indexOf("{") + 1, subPathAsString.indexOf("}"));
            int paramIndex = 0;
            current:
            for (Annotation[] annotations : method.getParameterAnnotations()) {
                if (paramIndex < startIndex) {
                    paramIndex++;
                    continue;
                }
                for (Annotation annotation : annotations) {
                    if (PathParam.class.isAssignableFrom(annotation.annotationType())) {
                        String pathParamName = ((PathParam) annotation).value();

                        if (placeholder.equals(pathParamName)) {
                            Object paramValue = args[paramIndex];
                            subPathAsString = subPathAsString.replace("{" + pathParamName + "}", paramValue != null ? paramValue.toString() : "");
                            break current;
                        }
                    }
                }
                paramIndex++;
            }
            return replaceTemplateValues(args, method, subPathAsString, paramIndex);
        } else {
            return subPathAsString;
        }
    }

    private Object performGetRequest(WebTarget webTarget, Object[] args, Class responseType, Class genericResponseType, Method method) throws IOException {
        //TODO test it
        int paramIndex = 0;
        for (Annotation[] annotations : method.getParameterAnnotations()) {
            for (Annotation annotation : annotations) {
                if (QueryParam.class.isAssignableFrom(annotation.annotationType())) {
                    String paramName = ((QueryParam) annotation).value();

                    webTarget = webTarget.queryParam(paramName, args[paramIndex]);
                    break;
                }
            }
            paramIndex++;
        }

        Response response = webTarget.request().header(HttpHeaders.AUTHORIZATION, "Bearer " + identityHolder.getCurrentToken()).accept(MediaType.APPLICATION_JSON).get();

        ObjectMapper objectMapper = new ObjectMapper();
        return processResponse(response, responseType, genericResponseType, objectMapper, method);
    }

    private Object performDeleteRequest(WebTarget webTarget, Class responseType, Class genericResponseType, Method method) throws IOException {
        Response response = webTarget.request().header(HttpHeaders.AUTHORIZATION, "Bearer " + identityHolder.getCurrentToken()).accept(MediaType.APPLICATION_JSON).delete();

        ObjectMapper objectMapper = new ObjectMapper();
        return processResponse(response, responseType, genericResponseType, objectMapper, method);
    }

    private <T> T processResponse(Response response, final Class<T> targetType, Class genericResponseType, ObjectMapper objectMapper, final Method method) throws IOException {
        String receivedToken = response.getHeaderString(HttpHeaders.AUTHORIZATION);

        if (receivedToken != null) {
            identityHolder.setCurrentToken(receivedToken);
        }

        String responseBody = response.readEntity(String.class);

        if (response.getStatus() < 200 || response.getStatus() >= 300) {
            throw new UnexpectedServiceResultException(response.getStatus(), responseBody);
        }

        if (Void.TYPE.equals(targetType) || responseBody == null || "".equals(responseBody)) {
            return null;
        }

        if (Collection.class.isAssignableFrom(targetType)) {
            JavaType typedList = objectMapper.getTypeFactory().constructCollectionType(ArrayList.class, genericResponseType);
            return objectMapper.readValue(responseBody, typedList);
        }
        return objectMapper.readValue(responseBody, targetType);
    }

    private Object createRequestObjectAsString(Object value, ObjectMapper objectMapper) {
        if (value instanceof String) {
            return value;
        }
        try {
            value = objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw ExceptionUtils.throwAsRuntimeException(e);
        }
        return value;
    }
}
