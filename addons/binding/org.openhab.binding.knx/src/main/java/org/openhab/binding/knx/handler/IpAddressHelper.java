/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.knx.handler;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Used by the IpBridgeHandler to automatically provide a local IP
 * address when no localAddress has been configured. 
 * 
 * @author Sebastian Janzen - initial contribution
 */
public class IpAddressHelper {
	
    private static Logger logger = LoggerFactory.getLogger(IpAddressHelper.class);

    private static boolean isPrivateIPAddress(String ipAddress) {
        InetAddress ia = null;
        try {
            InetAddress ad = InetAddress.getByName(ipAddress);
            byte[] ip = ad.getAddress();
            ia = InetAddress.getByAddress(ip);
        } catch (UnknownHostException e) {
            logger.debug("Could not determine if ip {} is local or not.", ipAddress, e);
        }
        return ia.isSiteLocalAddress();
    }

    public static List<String> getAllIpAddresses() throws SocketException {
        Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
        List<String> results = new ArrayList<>();
        while (e.hasMoreElements()) {
            Enumeration<InetAddress> ee = e.nextElement().getInetAddresses();
            while (ee.hasMoreElements()) {
                InetAddress i = ee.nextElement();
                // System.out.println(i.getHostAddress());
                results.add(i.getHostAddress());
            }
        }
        return results;
    }

    public static String getFirstLocalIpAddress() {
        try {
            for (String ip : getAllIpAddresses()) {
                if (isPrivateIPAddress(ip)) {
                    logger.debug("Found local ip address: {}", ip);
                    return ip;
                }
            }
        } catch (SocketException e) {
            logger.debug("Error to get all local ip addresses: {}", e.getLocalizedMessage());
        }
        return null;
    }
    
}
