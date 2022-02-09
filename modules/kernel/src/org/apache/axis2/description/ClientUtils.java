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

package org.apache.axis2.description;

import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.axis2.client.Options;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.axis2.engine.ListenerManager;
import org.apache.axis2.i18n.Messages;
import org.apache.axis2.util.Utils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

/**
 * Utility methods for various clients to use.
 */
public class ClientUtils {

    private static final Log log = LogFactory.getLog(ClientUtils.class);

    public static TransportOutDescription inferOutTransport(AxisConfiguration ac,
                                                                         EndpointReference epr,
                                                                         MessageContext msgctx)
            throws AxisFault {
        String transportURI = (String) msgctx.getProperty(Constants.Configuration.TRANSPORT_URL);
        if (transportURI != null && !"".equals(transportURI)) {
            String transport = Utils.getURIScheme(transportURI);
            if (transport != null) {
                TransportOutDescription transportOut = ac.getTransportOut(transport);
                if (transportOut == null) {
                    log.error("No Tranport Sender found for : " + transport);
                    cleanupOperationContext(msgctx);
                    throw new AxisFault("No Tranport Sender found for : " + transport);
                } else {
                    return ac.getTransportOut(transport);
                }
            } else {
                log.error(Messages.getMessage("cannotInferTransport", transportURI));
                cleanupOperationContext(msgctx);
                throw new AxisFault(Messages.getMessage("cannotInferTransport", transportURI));
            }
        } else {
            if (msgctx.getOptions().getTransportOut() != null) {
                if (msgctx.getOptions().getTransportOut().getSender() == null) {
                    log.error(Messages.getMessage("Incomplete transport sender: missing sender!"));
                    cleanupOperationContext(msgctx);
                    throw new AxisFault("Incomplete transport sender: missing sender!");
                }
                return msgctx.getOptions().getTransportOut();
            }
            if (epr == null || (epr.getAddress() == null)) {
                log.error(Messages.getMessage("cannotInferTransportNoAddr"));
                cleanupOperationContext(msgctx);
                throw new AxisFault(Messages.getMessage("cannotInferTransportNoAddr"));
            }
            String uri = epr.getAddress();
            String transport = Utils.getURIScheme(uri);
            // Check if the message context contains the "HTTP_CARBON_MESSAGE" property to see if this message
            // came from the HTTP transport based on Netty. Depending on that, we need to change the "transport"
            // variable as below to pick the correct TransportOutDescription from the AxisConfiguration.
            if (Objects.nonNull(msgctx.getProperty(Constants.HTTP_CARBON_MESSAGE))) {
                if (Constants.TRANSPORT_HTTP.equalsIgnoreCase(transport)) {
                    transport = Constants.TRANSPORT_HTTPWS;
                } else if (Constants.TRANSPORT_HTTPS.equalsIgnoreCase(transport)) {
                    transport = Constants.TRANSPORT_HTTPSWSS;
                }
            }
            if (transport != null && ac.getTransportOut(transport) != null) {
                return ac.getTransportOut(transport);
            } else {
                log.error(Messages.getMessage("cannotInferTransport", uri));
                cleanupOperationContext(msgctx);
                throw new AxisFault(Messages.getMessage("cannotInferTransport", uri));
            }
        }
    }

    private static void cleanupOperationContext(MessageContext msgctx) {
        if (msgctx.getConfigurationContext() != null && msgctx.getMessageID() != null && msgctx.
                getConfigurationContext().getOperationContext(msgctx.getMessageID()) != null) {
            msgctx.getConfigurationContext().getOperationContext(msgctx.getMessageID()).cleanup();
        }
    }

    public static TransportInDescription inferInTransport(AxisConfiguration ac,
                                                                       Options options,
                                                                       MessageContext msgCtxt)
            throws AxisFault {
        String listenerTransportProtocol = options.getTransportInProtocol();
        if (listenerTransportProtocol == null) {
            EndpointReference replyTo = msgCtxt.getReplyTo();
            if (replyTo != null) {
                try {
                    URI uri = new URI(replyTo.getAddress());
                    listenerTransportProtocol = uri.getScheme();
                } catch (URISyntaxException e) {
                    //need to ignore
                }
            } else {
                //assume listener transport as sender transport
                if (msgCtxt.getTransportOut() != null) {
                    listenerTransportProtocol = msgCtxt.getTransportOut().getName();
                }
            }
        }
        TransportInDescription transportIn = null;
        if (options.isUseSeparateListener() || msgCtxt.getOptions().isUseSeparateListener()) {
            if ((listenerTransportProtocol != null) && !"".equals(listenerTransportProtocol)) {
                transportIn = ac.getTransportIn(listenerTransportProtocol);
                ListenerManager listenerManager =
                        msgCtxt.getConfigurationContext().getListenerManager();
                if (transportIn == null) {
                    // TODO : User should not be mandated to give an IN transport. If it is not given, we should
                    // ask from the ListenerManager to give any available transport for this client.
                    log.error(Messages.getMessage("unknownTransport",
                                                            listenerTransportProtocol));
                    throw new AxisFault(Messages.getMessage("unknownTransport",
                                                            listenerTransportProtocol));
                }
                synchronized (ClientUtils.class) {
                    if (!listenerManager.isListenerRunning(transportIn.getName())) {
                        listenerManager.addListener(transportIn, false);
                    }
                }
            }
            if (msgCtxt.getAxisService() != null) {
                if (!msgCtxt.isEngaged(Constants.MODULE_ADDRESSING)) {
                    log.error(Messages.getMessage("2channelNeedAddressing"));
                    throw new AxisFault(Messages.getMessage("2channelNeedAddressing"));
                }
            } else {
                if (!ac.isEngaged(Constants.MODULE_ADDRESSING)) {
                    log.error(Messages.getMessage("2channelNeedAddressing"));
                    throw new AxisFault(Messages.getMessage("2channelNeedAddressing"));
                }
            }
        }

        return transportIn;
    }
}
