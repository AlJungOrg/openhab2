/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.knx.item;

import java.util.Set;

import org.eclipse.smarthome.core.autoupdate.AutoUpdateBindingConfigProvider;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingRegistry;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.link.ItemThingLinkRegistry;
import org.openhab.binding.knx.handler.KnxChannelConfigParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Thomas Eichstaedt-Engelen - Initial contribution
 */
public class KnxAutoupdateBindingConfigProvider implements AutoUpdateBindingConfigProvider {

    private static final Logger logger = 
    	LoggerFactory.getLogger(KnxAutoupdateBindingConfigProvider.class);

	private ThingRegistry thingRegistry;
    private ItemThingLinkRegistry itemThingLinkRegistry;
    
    
    public KnxAutoupdateBindingConfigProvider() {
    }

    protected void setThingRegistry(ThingRegistry registry) {
    	this.thingRegistry = registry;
    }
    
    protected void unsetThingRegistry(ThingRegistry registry) {
    	this.thingRegistry = null;
    }
    
    protected void setItemThingLinkRegistry(ItemThingLinkRegistry registry) {
        itemThingLinkRegistry = registry;
    }

    protected void unsetItemThingLinkRegistry(ItemThingLinkRegistry registry) {
        itemThingLinkRegistry = null;
    }
    
    
    @Override
    public Boolean autoUpdate(String itemName) {
        Set<ThingUID> linkedThings = itemThingLinkRegistry.getLinkedThings(itemName);
        for (ThingUID thingUID : linkedThings) {
        	Thing thing = thingRegistry.get(thingUID);
        	if (thing != null) {
	            for (Channel channel : thing.getChannels()) {
	                KnxChannelConfigParser configMapper = new KnxChannelConfigParser(channel.getConfiguration());
	                return configMapper.isAutoUpdate();
	            }
        	} else {
        		logger.debug("Didn't find thing '{}' in ThingRegistry. Returning autoUpdate == TRUE though.", thingUID);
        	}
        }
        return Boolean.TRUE;
    }

}
