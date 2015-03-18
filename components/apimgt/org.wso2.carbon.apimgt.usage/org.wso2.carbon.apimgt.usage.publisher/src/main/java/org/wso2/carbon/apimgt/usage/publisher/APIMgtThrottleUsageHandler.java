/*
*  Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.carbon.apimgt.usage.publisher;

import org.apache.synapse.MessageContext;
import org.apache.synapse.mediators.AbstractMediator;
import org.apache.synapse.rest.RESTConstants;
import org.wso2.carbon.apimgt.gateway.handlers.security.APISecurityUtils;
import org.wso2.carbon.apimgt.gateway.handlers.security.AuthenticationContext;
import org.wso2.carbon.apimgt.usage.publisher.dto.ThrottlePublisherDTO;
import org.wso2.carbon.apimgt.usage.publisher.internal.UsageComponent;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;

/*
* This is the class mediator which will handle publishing events upon throttle out events
*/
public class APIMgtThrottleUsageHandler extends AbstractMediator {

    private boolean enabled = UsageComponent.getApiMgtConfigReaderService().isEnabled();

    private volatile APIMgtUsageDataPublisher publisher;

    public APIMgtThrottleUsageHandler() {

        if (!enabled) {
            return;
        }

        if (publisher == null) {
            synchronized (this) {
                if (publisher == null) {
                    String publisherClass = UsageComponent.getApiMgtConfigReaderService().getPublisherClass();
                    try {
                        log.debug("Instantiating Data Publisher");
                        publisher = (APIMgtUsageDataPublisher) Class.forName(publisherClass).
                                newInstance();
                        publisher.init();
                    } catch (ClassNotFoundException e) {
                        log.error("Class not found " + publisherClass);
                    } catch (InstantiationException e) {
                        log.error("Error instantiating " + publisherClass);
                    } catch (IllegalAccessException e) {
                        log.error("Illegal access to " + publisherClass);
                    }
                }
            }
        }
    }

    public boolean mediate(MessageContext messageContext) {

        try {
            if (!enabled) {
                return true;
            }
            // gets the access token and username
            AuthenticationContext authContext = APISecurityUtils.getAuthenticationContext
                    (messageContext);
            if (authContext != null) {
                long currentTime = System.currentTimeMillis();
                ThrottlePublisherDTO throttlePublisherDTO = new ThrottlePublisherDTO();
                throttlePublisherDTO.setAccessToken(authContext.getApiKey());
                String username = authContext.getUsername();
                throttlePublisherDTO.setUsername(username);
                throttlePublisherDTO.setTenantDomain(MultitenantUtils.getTenantDomain(username));
                throttlePublisherDTO.setApiname((String) messageContext.getProperty(
                        "API_NAME"));
                throttlePublisherDTO.setVersion((String) messageContext.getProperty(
                        RESTConstants.SYNAPSE_REST_API_VERSION));
                throttlePublisherDTO.setContext((String) messageContext.getProperty(
                        RESTConstants.REST_API_CONTEXT));
                throttlePublisherDTO.setProvider((String) messageContext.getProperty(
                        "API_PUBLISHER"));
                throttlePublisherDTO.setApplicationName(authContext.getApplicationName());
                throttlePublisherDTO.setApplicationId(authContext.getApplicationId());
                throttlePublisherDTO.setThrottledTime(currentTime);
                publisher.publishEvent(throttlePublisherDTO);


            }


        } catch (Throwable e) {
            log.error("Cannot publish throttling event. " + e.getMessage(), e);
        }
        return true; // Should never stop the message flow
    }

    public boolean isContentAware() {
        return false;
    }
}

