/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.knx.handler;

import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.isNotBlank;

import java.math.BigDecimal;
import java.net.InetSocketAddress;

import org.apache.commons.lang.StringUtils;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.link.ItemChannelLinkRegistry;
import org.openhab.binding.knx.KnxBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tuwien.auto.calimero.exception.KNXException;
import tuwien.auto.calimero.link.KNXNetworkLink;
import tuwien.auto.calimero.link.KNXNetworkLinkIP;
import tuwien.auto.calimero.link.medium.TPSettings;

/**
 * The {@link IpBridgeThingHandler} is responsible for handling commands, which
 * are sent to one of the channels. It implements a KNX/IP Gateway, that either
 * acts a a conduit for other {@link KnxBaseThingHandler}s, or for Channels that
 * are directly defined on the bridge
 *
 * @author Karel Goderis - Initial contribution
 * @author Thomas Eichstaedt-Engelen
 */
public class IpBridgeThingHandler extends KnxBridgeBaseThingHandler {

    private Logger logger = LoggerFactory.getLogger(IpBridgeThingHandler.class);

    // List of all Configuration parameters
    public static final String CONFIG_PARAM_IP_ADDRESS = "ipAddress";
    public static final String CONFIG_PARAM_IP_CONNECTION_TYPE = "ipConnectionType";
    public static final String CONFIG_PARAM_LOCAL_IP = "localIp";
    public static final String CONFIG_PARAM_PORT_NUMBER = "portNumber";
    public static final String SYSTEM_PARAM_LOCAL_IP = "liteserver.localip";

    private final static int DEFAULT_KNX_PORT = 3671;

    /**
     * the ip connection type for connecting to the KNX bus. Could be either
     * TUNNEL or ROUTING
     */
    private static int ipConnectionType;
    /** the ip address to use for connecting to the KNX bus */
    private static String ip;
    /** the port to use for connecting to the KNX bus */
    private static int port = DEFAULT_KNX_PORT;

    public IpBridgeThingHandler() {
        // Default constructor required in order to have OSGi Declarative
        // Services working
        super(null, null);
    }

    public IpBridgeThingHandler(Bridge bridge, ItemChannelLinkRegistry itemChannelLinkRegistry) {
        super(bridge, itemChannelLinkRegistry);
    }

    @Override
    public void initialize() {

        ip = (String) getConfig().get(CONFIG_PARAM_IP_ADDRESS);

        String connectionTypeString = (String) getConfig().get(CONFIG_PARAM_IP_CONNECTION_TYPE);
        if (isNotBlank(connectionTypeString)) {
            if ("TUNNEL".equals(connectionTypeString)) {
                ipConnectionType = KNXNetworkLinkIP.TUNNELING;
            } else if ("ROUTER".equals(connectionTypeString)) {
                ipConnectionType = KNXNetworkLinkIP.ROUTING;
                if (StringUtils.isBlank(ip)) {
                    ip = KnxBindingConstants.DEFAULT_MULTICAST_IP;
                }
            } else {
                logger.warn("unknown IP connection type '" + connectionTypeString
                        + "'! Known types are either 'TUNNEL' or 'ROUTER'");
            }
        } else {
            // defaults to connection type TUNNELING
            ipConnectionType = KNXNetworkLinkIP.TUNNELING;
        }

        Object portNumberObject = getConfig().get(CONFIG_PARAM_PORT_NUMBER);
        if (portNumberObject != null && isNotBlank(portNumberObject.toString())) {
            port = ((BigDecimal) portNumberObject).intValue();
        } else {
            port = DEFAULT_KNX_PORT;
        }

        super.initialize();
    }

    @Override
	public KNXNetworkLink establishConnection() throws KNXException {

        final String sysparamLocalIp = System.getProperty(SYSTEM_PARAM_LOCAL_IP);
        final String localIpString = (String) getConfig().get(CONFIG_PARAM_LOCAL_IP);

        if (isBlank(ip)) {
            logger.warn("Empty hostname! Cannot connect to KNX > please check your thing configuration.");
            return null;
        }

        InetSocketAddress remoteEndPoint = new InetSocketAddress(ip, port);
        InetSocketAddress localEndPoint = null;

        if (isBlank(localIpString) && isNotBlank(sysparamLocalIp)) {
            logger.info("No local ip configured for KNX bridge {}. Using fallback system property '{}' which is {}.",
                    getThing().getUID().toString(), SYSTEM_PARAM_LOCAL_IP, sysparamLocalIp);
            localEndPoint = new InetSocketAddress(sysparamLocalIp, 0);
        } else if (isNotBlank(localIpString)) {
            localEndPoint = new InetSocketAddress(localIpString, 0);
        } else {
            final String localPublicIp = IpAddressHelper.getFirstLocalIpAddress();
            if (localPublicIp == null) {
                logger.warn("Couldn't find an IP address for this host. "
                        + "Please check the .hosts configuration or use the 'localIp' parameter to configure a valid IP address.");
            } else {
                logger.info("No local ip configured for KNX bridge {}. Using auto fallback which is {}.",
                        getThing().getUID().toString(), localPublicIp);
                localEndPoint = new InetSocketAddress(localPublicIp, 0);
            }
        }

        if (logger.isInfoEnabled()) {
            if (link instanceof KNXNetworkLinkIP) {
                String ipConnectionTypeString = ipConnectionType == KNXNetworkLinkIP.ROUTING ? "ROUTER" : "TUNNEL";
                logger.info("Establishing connection to KNX bus on {} in mode {}.", ip + ":" + port,
                        ipConnectionTypeString);
            }
        }

        try {
            return new KNXNetworkLinkIP(ipConnectionType, localEndPoint, remoteEndPoint, false, TPSettings.TP1);
        } catch (Exception e) {
            logger.error("Error connecting to KNX bus: {}", e.getMessage());
        }

        throw new KNXException("Connection to KNX bus on " + ip + ":" + port + " in mode "
                + (ipConnectionType == KNXNetworkLinkIP.ROUTING ? "ROUTER" : "TUNNEL") + " could not be established");
    }

}
