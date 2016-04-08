/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.knx.internal.factory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.link.ItemChannelLinkRegistry;
import org.openhab.binding.knx.KnxBindingConstants;
import org.openhab.binding.knx.handler.GenericKnxThingHandler;
import org.openhab.binding.knx.handler.IpBridgeThingHandler;
import org.openhab.binding.knx.handler.SerialBridgeThingHandler;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

/**
 * The {@link KnxHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Karel Goderis - Initial contribution
 */
public class KnxHandlerFactory extends BaseThingHandlerFactory {

    private Logger logger = LoggerFactory.getLogger(KnxHandlerFactory.class);

    public final static Collection<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Lists.newArrayList(
            KnxBindingConstants.THING_TYPE_IP_BRIDGE, KnxBindingConstants.THING_TYPE_SERIAL_BRIDGE,
            KnxBindingConstants.THING_TYPE_GENERIC);

    private Map<ThingUID, ServiceRegistration<?>> discoveryServiceRegs = new HashMap<>();
    protected ItemChannelLinkRegistry itemChannelLinkRegistry;

    protected void setItemChannelLinkRegistry(ItemChannelLinkRegistry registry) {
        itemChannelLinkRegistry = registry;
    }

    protected void unsetItemChannelLinkRegistry(ItemChannelLinkRegistry registry) {
        itemChannelLinkRegistry = null;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        if (SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID)) {
            return true;
        }
        if (thingTypeUID.getBindingId().equals(KnxBindingConstants.BINDING_ID)) {
            logger.error("This ThingType ({}) is unsupported, since knx:generic is used for most things!",
                    thingTypeUID.getAsString());
        }
        return false;
    }

    @Override
    public Thing createThing(ThingTypeUID thingTypeUID, Configuration configuration, ThingUID thingUID,
            ThingUID bridgeUID) {
        if (SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID)) {
            ThingUID thinguid = createThingUID(thingTypeUID, thingUID, bridgeUID);
            return super.createThing(thingTypeUID, configuration, thinguid, bridgeUID);
        }

        throw new IllegalArgumentException("The thing type " + thingTypeUID + " is not supported by the KNX binding.");
    }

    private ThingUID createThingUID(ThingTypeUID thingTypeUID, ThingUID thingUID, ThingUID bridgeUID) {
        if (thingUID == null) {
            String idstr = Long.toHexString(new Random(System.nanoTime()).nextLong());
            thingUID = new ThingUID(thingTypeUID, idstr, bridgeUID.getId());
        }
        return thingUID;
    }

    @Override
    protected ThingHandler createHandler(Thing thing) {
        if (thing.getThingTypeUID().equals(KnxBindingConstants.THING_TYPE_IP_BRIDGE)) {
            IpBridgeThingHandler handler = new IpBridgeThingHandler((Bridge) thing, itemChannelLinkRegistry);
            // registerGADiscoveryService(handler);
            return handler;
        } else if (thing.getThingTypeUID().equals(KnxBindingConstants.THING_TYPE_SERIAL_BRIDGE)) {
            SerialBridgeThingHandler handler = new SerialBridgeThingHandler((Bridge) thing, itemChannelLinkRegistry);
            // registerGADiscoveryService(handler);
            return handler;
        } else if (thing.getThingTypeUID().equals(KnxBindingConstants.THING_TYPE_GENERIC)) {
            return new GenericKnxThingHandler(thing, itemChannelLinkRegistry);
        } else {
            return null;
        }
    }
    
}
