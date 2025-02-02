/*
 * Copyright (c) 2023 General Motors GTO LLC
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * SPDX-FileType: SOURCE
 * SPDX-FileCopyrightText: 2023 General Motors GTO LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.uprotocol.uri.serializer;


import java.util.Objects;
import org.eclipse.uprotocol.uri.validator.UriValidator;
import org.eclipse.uprotocol.v1.UAuthority;
import org.eclipse.uprotocol.v1.UEntity;
import org.eclipse.uprotocol.v1.UResource;
import org.eclipse.uprotocol.v1.UUri;

/**
 * UUri Serializer that serializes a UUri to a long format string per
 * https://github.com/eclipse-uprotocol/uprotocol-spec/blob/main/basics/uri.adoc
 */
public class LongUriSerializer implements UriSerializer<String> {

    private static final LongUriSerializer INSTANCE = new LongUriSerializer();

    private LongUriSerializer(){}

    public static LongUriSerializer instance() {
        return INSTANCE;
    }

    /**
     * Support for serializing {@link UUri} objects into their String format.
     * @param Uri {@link UUri} object to be serialized to the String format.
     * @return Returns the String format of the supplied {@link UUri} that can be used as a sink or a source in a uProtocol publish communication.
     */
    @Override
    public String serialize(UUri Uri) {
        if (Uri == null || UriValidator.isEmpty(Uri)) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        if (Uri.hasAuthority() && !Uri.getAuthority().getName().isBlank()) {
            sb.append("//");
            sb.append(Uri.getAuthority().getName());
        }
        sb.append("/");

        sb.append(buildSoftwareEntityPartOfUri(Uri.getEntity()));
        
        sb.append(buildResourcePartOfUri(Uri));

        return sb.toString().replaceAll("/+$", "");
    }

    private static String buildResourcePartOfUri(UUri uri) {
        if (!uri.hasResource()) {
            return "";
        }
        final UResource uResource = uri.getResource();

        StringBuilder sb = new StringBuilder("/");
        sb.append(uResource.getName());

        if (uResource.hasInstance() && !uResource.getInstance().isBlank()) {
            sb.append(".").append(uResource.getInstance());
        }
        if (uResource.hasMessage() && !uResource.getMessage().isBlank()) {
            sb.append("#").append(uResource.getMessage());
        }
        
        return sb.toString();
    }

    /**
     * Create the service part of the uProtocol URI from an  software entity object.
     * @param use  Software Entity representing a service or an application.
     */
    private static String buildSoftwareEntityPartOfUri(UEntity use) {
        StringBuilder sb = new StringBuilder(use.getName().trim());
        sb.append("/");
        if (use.getVersionMajor() > 0) {
            sb.append(use.getVersionMajor());
        }

        return sb.toString();
    }


    /**
     * Deserialize a String into a UUri object.
     * @param uProtocolUri A long format uProtocol URI.
     * @return Returns an UUri data object.
     */
    @Override
    public UUri deserialize(String uProtocolUri) {
        if (uProtocolUri == null || uProtocolUri.isBlank()) {
            return UUri.getDefaultInstance();
        }

        String uri = uProtocolUri.contains(":") ? uProtocolUri.substring(uProtocolUri.indexOf(":")+1) : uProtocolUri 
                .replace('\\', '/');
        
        boolean isLocal = !uri.startsWith("//");

        final String[] uriParts = uri.split("/");
        final int numberOfPartsInUri = uriParts.length;

        if(numberOfPartsInUri == 0 || numberOfPartsInUri == 1) {
            return UUri.getDefaultInstance();
        }

        String useName;
        String useVersion = "";

        UResource uResource = null;

        UAuthority uAuthority = null;

        if(isLocal) {
            useName = uriParts[1];
            if (numberOfPartsInUri > 2) {
                useVersion = uriParts[2];

                if (numberOfPartsInUri > 3) {
                    uResource = parseFromString(uriParts[3]);
                }
            } 
        } else {
            // If authority is blank, it is an error
            if (uriParts[2].isBlank()) {
                return UUri.getDefaultInstance();
            }
            uAuthority = UAuthority.newBuilder().setName(uriParts[2]).build();

            if (uriParts.length > 3) {
                useName = uriParts[3];
                if (numberOfPartsInUri > 4) {
                    useVersion = uriParts[4];

                    if (numberOfPartsInUri > 5) { 
                        uResource = parseFromString(uriParts[5]);
                    }

                } 
            } else {
                return UUri.newBuilder()
                .setAuthority(uAuthority)
                .build();
            }
        }

        Integer useVersionInt = null;
        try {
            if (!useVersion.isBlank()) {
                useVersionInt = Integer.valueOf(useVersion);
            }
        } catch (NumberFormatException ignored) {
            return UUri.getDefaultInstance();
        }

        UEntity.Builder UEntityFactory = UEntity.newBuilder().setName(useName);

        if (useVersionInt != null) {
            UEntityFactory.setVersionMajor(useVersionInt);
        }
            
        UUri.Builder uriBuilder = UUri.newBuilder().setEntity(UEntityFactory);
        if (uAuthority != null) {
            uriBuilder.setAuthority(uAuthority);
        }
        if (uResource != null) {
            uriBuilder.setResource(uResource);
        }
        return uriBuilder.build();
    }

    /**
     * Static factory method for creating a UResource using a string that contains 
     * name + instance + message.
     * @param resourceString String that contains the UResource information.
     * @return Returns a UResource object.
     */
    private static UResource parseFromString(String resourceString) {
        Objects.requireNonNull(resourceString, " Resource must have a command name.");
        String[] parts = resourceString.split("#");
        String nameAndInstance = parts[0];

        String[] nameAndInstanceParts = nameAndInstance.split("\\.");
        String resourceName = nameAndInstanceParts[0];
        String resourceInstance = nameAndInstanceParts.length > 1 ? nameAndInstanceParts[1] : null;
        String resourceMessage = parts.length > 1 ? parts[1] : null;

        UResource.Builder uResourceBuilder = UResource.newBuilder().setName(resourceName);
        if (resourceInstance != null) {
            uResourceBuilder.setInstance(resourceInstance);
        }
        if (resourceMessage != null) {
            uResourceBuilder.setMessage(resourceMessage);
        }

        if (resourceName.contains("rpc") && resourceInstance != null && resourceInstance.contains("response")) {
            uResourceBuilder.setId(0);
        }
        
        return uResourceBuilder.build();
    }

}
