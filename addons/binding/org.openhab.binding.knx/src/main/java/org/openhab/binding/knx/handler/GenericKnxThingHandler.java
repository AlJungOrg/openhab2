/**
 * Copyright (c) 2014 openHAB UG (haftungsbeschraenkt) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.knx.handler;

import static org.openhab.binding.knx.handler.KnxChannelConfigParser.CONFIG_KEY_COMMAND_GA;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.smarthome.core.items.Item;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.link.ItemChannelLinkRegistry;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.eclipse.smarthome.core.types.Type;
import org.openhab.binding.knx.KnxBindingConstants;
import org.openhab.binding.knx.handler.KnxChannelConfigParser.TypedGroupaddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tuwien.auto.calimero.GroupAddress;
import tuwien.auto.calimero.IndividualAddress;
import tuwien.auto.calimero.datapoint.CommandDP;
import tuwien.auto.calimero.datapoint.Datapoint;
import tuwien.auto.calimero.exception.KNXFormatException;

/**
 * This GenericThingHandler can represent a single GA as well as a complete KNX installation.
 * It is up to the administrator to specify the amount and type of channels individually. Since
 * the reasonable interpretation of KNX things is a matter of taste the administrator has full
 * flexibility. 
 * 
 * @see KnxChannelConfigParser for more details on how the channel configuration should
 * look like.
 * 
 * @author Thomas Eichstaedt-Engelen - Initial contribution
 */
public class GenericKnxThingHandler extends BaseThingHandler implements KnxBusListener {

    private Logger logger = LoggerFactory.getLogger(GenericKnxThingHandler.class);

    // List of all Configuration parameters
    private static final String READ = "read";
    private static final String INTERVAL = "interval";

    /** used to store events that we have sent ourselves; we need to remember them for not reacting to them */
    protected static List<String> ignoreEventList = new ArrayList<String>();

    protected ItemChannelLinkRegistry itemChannelLinkRegistry;

    /** holds the reference to the underlying brdge which has been injected by 'bridgeHandlerInitialized' */
    private KnxBridgeBaseThingHandler knxBridgeHandler;
    
    
    public GenericKnxThingHandler(Thing thing, ItemChannelLinkRegistry itemChannelLinkRegistry) {
        super(thing);
        this.itemChannelLinkRegistry = itemChannelLinkRegistry;
    }

    @Override
    public void initialize() {
        // do nothing. especially do not switch this thing online ...
    }

    @Override
    public void bridgeHandlerInitialized(ThingHandler bridgeHandler, Bridge bridge) {
        updateStatus(ThingStatus.ONLINE);
        
        knxBridgeHandler = (KnxBridgeBaseThingHandler) bridgeHandler;
        knxBridgeHandler.registerKnxBusListener(this);

        logger.trace("Got BridgeHandler injected '{}' > schedule Initializer", bridgeHandler.getThing().getUID());
        
        if (Boolean.TRUE.equals(getConfig().get(READ))) {
            for (Channel channel : getThing().getChannels()) {
                scheduleToRead(channel);
            }
        }
    }

    @Override
    public void bridgeHandlerDisposed(ThingHandler bridgeHandler, Bridge bridge) {
        if (bridgeHandler != null && bridge != null) {
            ((KnxBridgeBaseThingHandler) bridgeHandler).unregisterKnxBusListener(this);
            knxBridgeHandler = null;
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        } else {
            logger.warn("Can not dispose ThingHandler '{}' because it's bridge is not existing",
                    this.getThing().getUID());
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        handleCommandInternal(channelUID, command);
    }

    @Override
    public void handleUpdate(ChannelUID channelUID, State state) {
        handleCommandInternal(channelUID, state);
    }

    private void handleCommandInternal(ChannelUID channelUID, Type type) {
        if (!shouldIgnoreEvent(channelUID, type)) {
            Channel channel = getThing().getChannel(channelUID.getId());
            KnxChannelConfigParser configParser = new KnxChannelConfigParser(channel.getConfiguration());

            String commandType = type.getClass().getSimpleName();
            Map<String, Object> channelConfigMap = configParser.getChannelConfigByCommandType(commandType);
            if (channelConfigMap != null) {
                String ga = (String) channelConfigMap.get(CONFIG_KEY_COMMAND_GA);
                String dpt = "";

                if (!ga.contains(",")) {
                    writeToKNX(ga, dpt, type);
                } else {
                    // this is a workaround to handle commands to be split over several groupaddresses
                    // which seem to be needed for HSBTypes.
                    String[] gas = ga.split(",");
                    for (int index = 0; index < gas.length; index++) {
                        writeToKNX(ga, dpt, type);
                    }
                }
            } else {
                logger.warn(
                        "Didn't find any channel configuration for commandType '{}' -> check your Thing configuration of channel '{}'!",
                        commandType, channelUID);
            }
        } else {
            logger.trace(
                    "We received this event (channel='{}', state='{}') from KNX, so we don't send it back again -> ignore!",
                    channelUID, type.toString());
        }
    }

    private void writeToKNX(String ga, String dpt, Type type) {
        if (knxBridgeHandler == null) {
            logger.warn("KNX bridge handler not found. Cannot handle command without bridge.");
            return;
        }

        // each of the GAs can be configured with it's specific DPT e.g. "1/1/0:5001"
        if (ga.contains(":")) {
            String[] gaDpt = ga.split(":");
            ga = gaDpt[0];
            dpt = gaDpt[1];
        }

        knxBridgeHandler.writeToKNX(ga, type, dpt);
    }

    @Override
    public void channelLinked(ChannelUID channelUID) {
        Channel channel = getThing().getChannel(channelUID.getId());
        if (Boolean.TRUE.equals(getConfig().get(READ))) {
            logger.debug("Item has been bound to channel {} > read state from KNX", channel.getUID());
            scheduleToRead(channel);
        }
    }

    private void scheduleToRead(Channel channel) {
        logger.trace("Going to initialize channel {}!", channel.getUID());

        KnxChannelConfigParser configParser = new KnxChannelConfigParser(channel.getConfiguration());
        TypedGroupaddress initGa = configParser.initializingGa();
        logger.trace("ChannelConfiguration: {}", configParser);
        if (initGa != null) {
            try {
                if (knxBridgeHandler != null) {
                    int readInterval = ((BigDecimal) getConfig().get(INTERVAL)).intValue();
                    Datapoint dp = new CommandDP(new GroupAddress(initGa.gaStr), getThing().getUID().toString(), 0,
                            initGa.dptStr);
                    knxBridgeHandler.scheduleRead(dp, readInterval);
                } else {
                    logger.warn(
                            "This case will hopefully never occur. If yes the Thing-lifecycle would be somewhat broken.");
                }
            } catch (KNXFormatException knxe) {
                logger.warn("Couldn't create groupaddress '{}' which seems to be invalid!", initGa.gaStr);
            }
        }
    }

    @Override
    public void onKnxEvent(IndividualAddress source, GroupAddress destination, byte[] asdu) {
        if (knxBridgeHandler == null) {
            logger.warn(
                    "Processing KNX bus data (source='{}', dest='{}', data='{}') but no there is no configured handler!",
                    source, destination, asdu);
            return;
        }

        for (Channel channel : getThing().getChannels()) {
            KnxChannelConfigParser configParser = new KnxChannelConfigParser(channel.getConfiguration());
            for (String commandType : configParser.getConfigKeys()) {
                if (configParser.listensTo(commandType, destination)) {
                    TypedGroupaddress typedGa = configParser.findTypedGa(commandType, destination);
                    if (typedGa.dptStr != null) {
                        Type type = knxBridgeHandler.getEshType(destination, typedGa.dptStr, asdu);
                        if (type != null) {
                            if (configParser.listensTo(destination)) {
                                updateStateAndIgnore(channel.getUID(), type);
                            }
                        } else {
                            final char[] hexCode = "0123456789ABCDEF".toCharArray();
                            StringBuilder sb = new StringBuilder(2 + asdu.length * 2);
                            sb.append("0x");
                            for (byte b : asdu) {
                                sb.append(hexCode[(b >> 4) & 0xF]);
                                sb.append(hexCode[(b & 0xF)]);
                            }

                            logger.warn(
                                    "Ignoring KNX bus data: couldn't transform to an openHAB type (not supported). Destination='{}', dpt='{}', data='{}'",
                                    destination, typedGa.dptStr, sb);
                            return;
                        }
                    } else {
                        logger.warn("Ignoring KNX bus data: no DPT is defined for group address '{}'", destination);
                    }
                }
            }
        }
    }

    private boolean shouldIgnoreEvent(ChannelUID channelUID, Type type) {
        String ignoreEventListKey = channelUID.toString() + type.toString();
        if (ignoreEventList.contains(ignoreEventListKey)) {
            return ignoreEventList.remove(ignoreEventListKey);
        }
        return false;
    }

    private void updateStateAndIgnore(ChannelUID channelUID, Type type) {

        Set<Item> itemSet = null;

        Channel channel = getThing().getChannel(channelUID.getId());
        if (channel != null) {
            itemSet = channel.getLinkedItems();
        } else {
            throw new IllegalArgumentException("Channel with ID '" + channelUID + "' does not exists.");
        }

        if (itemSet == null || itemSet.size() == 0) {
            logger.debug("itemSet is NULL or empty > no Items linked to Channel?");
            return;
        }

        for (Item anItem : itemSet) {
            logger.trace("The channel '{}' is bound to item '{}' ", channelUID, anItem);

            for (ChannelUID cUID : itemChannelLinkRegistry.getBoundChannels(anItem.getName())) {
                logger.trace("Item '{}' has a channel with id '{}'", anItem, cUID);
                if (cUID.getBindingId().equals(KnxBindingConstants.BINDING_ID)
                        && !cUID.toString().equals(channelUID.toString())) {
                    logger.trace("Added event (channel='{}', type='{}') to the ignore event list", cUID,
                            type.toString());
                    ignoreEvent(cUID, type);
                }
            }
        }

        updateState(channelUID, (State) type);
        logger.trace("Processed event (channel='{}', value='{}')", channelUID, type.toString());
    }

    private void ignoreEvent(ChannelUID channelUID, Type type) {
        String ignoreEventListKey = channelUID.toString() + type.toString();
        ignoreEventList.add(ignoreEventListKey);
    }

}
