/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.knx;

import org.eclipse.smarthome.core.thing.ThingTypeUID;

/**
 * The {@link KNXBinding} class defines common constants, which are used across
 * the whole binding.
 *
 * @author Kai Kreuzer / Thomas Eichstaedt-Engelen - Initial contribution
 */
public class KnxBindingConstants {

    /**
     * the default multicast ip address (see <a href=
     * "http://www.iana.org/assignments/multicast-addresses/multicast-addresses.xml">
     * iana</a> EIBnet/IP)
     */
    public static final String DEFAULT_MULTICAST_IP = "224.0.23.12";

    public static final String BINDING_ID = "knx";

    // List of all Thing Type UIDs
    public final static ThingTypeUID THING_TYPE_IP_BRIDGE = new ThingTypeUID(BINDING_ID, "ip");
    public final static ThingTypeUID THING_TYPE_SERIAL_BRIDGE = new ThingTypeUID(BINDING_ID, "serial");

    public final static ThingTypeUID THING_TYPE_GENERIC = new ThingTypeUID(BINDING_ID, "generic");

}
