/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.knx.handler;

import static org.openhab.binding.knx.internal.dpt.KnxCoreTypeMapper.toDPTid;

import java.math.BigDecimal;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.thing.link.ItemChannelLinkRegistry;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.Type;
import org.openhab.binding.knx.internal.dpt.KnxCoreTypeMapper;
import org.openhab.binding.knx.internal.dpt.KnxTypeMapper;
import org.openhab.binding.knx.internal.logging.LogAdapter;
import org.openhab.binding.knx.internal.reader.DatapointReaderManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tuwien.auto.calimero.CloseEvent;
import tuwien.auto.calimero.DetachEvent;
import tuwien.auto.calimero.FrameEvent;
import tuwien.auto.calimero.GroupAddress;
import tuwien.auto.calimero.datapoint.CommandDP;
import tuwien.auto.calimero.datapoint.Datapoint;
import tuwien.auto.calimero.exception.KNXException;
import tuwien.auto.calimero.link.KNXNetworkLink;
import tuwien.auto.calimero.link.NetworkLinkListener;
import tuwien.auto.calimero.log.LogManager;
import tuwien.auto.calimero.process.ProcessCommunicator;
import tuwien.auto.calimero.process.ProcessCommunicatorImpl;
import tuwien.auto.calimero.process.ProcessEvent;
import tuwien.auto.calimero.process.ProcessListener;

/**
 * The {@link KnxBridgeBaseThingHandler} is responsible for handling commands,
 * which are sent to one of the channels.
 *
 * @author Kai Kreuzer / Karel Goderis - Initial contribution
 */
public abstract class KnxBridgeBaseThingHandler extends BaseBridgeHandler implements ProcessListener {

    protected Logger logger = LoggerFactory.getLogger(KnxBridgeBaseThingHandler.class);

    // List of all Configuration parameters
    public final static String READ = "read";
    public final static String INTERVAL = "interval";

    public final static String AUTO_RECONNECT_PERIOD = "autoReconnectPeriod";

    public final static String READING_PAUSE = "readingPause";
    public final static String RESPONSE_TIMEOUT = "responseTimeout";
    public final static String READ_RETRIES_LIMIT = "readRetriesLimit";
    
    public static final int defaultResponseTimeout = 3;
    
    private List<KnxBusListener> knxBusListeners = new CopyOnWriteArrayList<>();
    private KnxTypeMapper typeMapper = new KnxCoreTypeMapper();

    protected ItemChannelLinkRegistry itemChannelLinkRegistry;

    private ProcessCommunicator pc = null;
    private final LogAdapter logAdapter = new LogAdapter();
    protected KNXNetworkLink link;

    private DatapointReaderManager knxDatapointReader;

    // signals that the connection is shut down on purpose
    public boolean shutdown = false;
    
    
    public KnxBridgeBaseThingHandler(Bridge bridge, ItemChannelLinkRegistry itemChannelLinkRegistry) {
        super(bridge);
        this.itemChannelLinkRegistry = itemChannelLinkRegistry;
    }
    
    
    public void registerKnxBusListener(KnxBusListener knxBusListener) {
        if (knxBusListener != null) {
            knxBusListeners.add(knxBusListener);
        }
    }

    public void unregisterKnxBusListener(KnxBusListener knxBusListener) {
        if (knxBusListener != null) {
            knxBusListeners.remove(knxBusListener);
        }
    }
    
    
    @Override
    public void initialize() {
        LogManager.getManager().addWriter(null, logAdapter);
        connect();
        
        if (knxDatapointReader == null) {
            int readingPause = ((BigDecimal) getConfig().get(READING_PAUSE)).intValue();
            int readRetryLimit = ((BigDecimal) getConfig().get(READ_RETRIES_LIMIT)).intValue();

            knxDatapointReader = new DatapointReaderManager(this, scheduler, readingPause, readRetryLimit);
        }
        
        knxDatapointReader.start();
    }
    
    @Override
    public void dispose() {
        knxDatapointReader.shutdown();
        disconnect();
        LogManager.getManager().removeWriter(null, logAdapter);
    }
    
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    /**
     * Returns the KNXNetworkLink for talking to the KNX bus. The link can be
     * null, if it has not (yet) been established successfully.
     *
     * @return the KNX network link
     */
    public ProcessCommunicator getCommunicator() {
        if (link != null && !link.isOpen()) {
            connect();
        }
        return pc;
    }

    public abstract KNXNetworkLink establishConnection() throws KNXException;

    public void connect() {
        try {
            shutdown = false;

            link = establishConnection();

            if (link == null)
                return;

            final BigDecimal autoReconnectPeriodBD = (BigDecimal) getConfig().get(AUTO_RECONNECT_PERIOD);
            final int autoReconnectPeriod = autoReconnectPeriodBD == null ? 0 : autoReconnectPeriodBD.intValue();

            BigDecimal responseTimeout = (BigDecimal) getConfig().get(RESPONSE_TIMEOUT);

            NetworkLinkListener linkListener = new NetworkLinkListener() {
                @Override
				public void linkClosed(CloseEvent e) {
                    // if the link is lost, we want to reconnect immediately

                    onConnectionLost();

                    if (!(CloseEvent.USER_REQUEST == e.getInitiator()) && !shutdown) {
                        logger.warn("KNX link has been lost (reason: {} on object {}) - reconnecting...", e.getReason(),
                                e.getSource().toString());
                        connect();
                    }
                    if (!link.isOpen() && !shutdown) {
                        logger.error("KNX link has been lost!");
                        if (autoReconnectPeriod > 0) {
                            logger.info("KNX link will be retried in " + autoReconnectPeriod + " seconds");
                            final Timer timer = new Timer();
                            TimerTask timerTask = new TimerTask() {
                                @Override
                                public void run() {
                                    if (shutdown) {
                                        timer.cancel();
                                    } else {
                                        logger.info("Trying to reconnect to KNX...");
                                        connect();
                                        if (link.isOpen()) {
                                            timer.cancel();
                                        }
                                    }
                                }
                            };
                            timer.schedule(timerTask, autoReconnectPeriod * 1000, autoReconnectPeriod * 1000);
                        }
                    }
                }

                @Override
				public void indication(FrameEvent e) {
                }

                @Override
				public void confirmation(FrameEvent e) {
                }
            };

            link.addLinkListener(linkListener);

            if (pc != null) {
                pc.removeProcessListener(this);
                pc.detach();
            }

            pc = new ProcessCommunicatorImpl(link);

            if (responseTimeout != null) {
            	pc.setResponseTimeout(responseTimeout.intValue() / 1000);
            } else {
            	pc.setResponseTimeout(defaultResponseTimeout);
            }
            pc.addProcessListener(this);

            updateStatus(ThingStatus.ONLINE);

        } catch (KNXException e) {
            logger.error("Error connecting to KNX bus: {}", e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        }
    }

    public void disconnect() {
        shutdown = true;
        if (pc != null) {
            KNXNetworkLink link = pc.detach();
            pc.removeProcessListener(this);

            if (link != null && link.isOpen()) {
                logger.info("Closing KNX connection");
                link.close();
            }
        }
    }

    public void onConnectionLost() {
        logger.debug("Updating thing status to OFFLINE.");

        knxDatapointReader.shutdown();
        updateStatus(ThingStatus.OFFLINE);
    }

    public void scheduleRead(Datapoint datapoint, int readInterval) {
        knxDatapointReader.scheduleRead(datapoint, readInterval);
    }

    public void readFromKnx(Datapoint dp) throws KNXException, InterruptedException {
        readFromKnx(dp.getMainAddress(), null, dp.getDPT());
    }

    public void readFromKnx(GroupAddress ga, String dataTypeString, String dpt)
            throws KNXException, InterruptedException {

        if (getThing().getStatus() == ThingStatus.ONLINE) {

            String finalDpt = null;
            if (dpt == null) {
                finalDpt = toDPTid(dataTypeString);
            } else {
                finalDpt = dpt;
            }

            Datapoint dp = new CommandDP(ga, getThing().getUID().toString(), 0, finalDpt);

            if (pc != null) {
                logger.trace("Sending read request to KNX for datapoint {}", dp.getMainAddress());
                pc.read(dp);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
	public void groupWrite(ProcessEvent e) {
        readFromKNX(e);
    }

    /**
     * {@inheritDoc}
     */
    @Override
	public void detached(DetachEvent e) {
        logger.error("Received detach Event.");
    }

    /**
     * Handles the given {@link ProcessEvent}. If the KNX ASDU is valid it is
     * passed on to the {@link KnxLifecycleListener}s that are interested in the
     * destination Group Address, and subsequently to the {@link KnxBusListener}
     * s that are interested in all KNX bus activity
     *
     * @param knxEvent
     *            the {@link ProcessEvent} to handle.
     */
    private void readFromKNX(ProcessEvent knxEvent) {
        try {
            GroupAddress destination = knxEvent.getDestination();
            byte[] asdu = knxEvent.getASDU();
            if (asdu.length == 0) {
                return;
            }

            logger.trace("Received a KNX telegram  from '{}' for destination '{}'", knxEvent.getSourceAddr(),
                    destination);

            for (KnxBusListener listener : knxBusListeners) {
                listener.onKnxEvent(knxEvent.getSourceAddr(), destination, asdu);
            }
            
            // we received an answer from knx which might be due to a request
            // we've send out while initializing. Since the DatapointReader is
            // scheduled to read several times before timing out/giving up we
            // need this callback in order to cancel further reading now.
            knxDatapointReader.unscheduleRead(destination);

        } catch (RuntimeException re) {
            logger.error("Error while receiving event from KNX bus", re);
        }
    }

    public void writeToKNX(String gaString, Type value, String dpt) {
        if (gaString == null) {
            logger.warn("Couldn't write to KNX without a destination address - NULL");
            return;
        }

        GroupAddress groupAddress = null;

        try {
            groupAddress = new GroupAddress(gaString);
        } catch (Exception e) {
            logger.error("An exception occurred while creating a Group Address : '{}'", e.getMessage());
        }

        // if no specific DPT has been configured we try to infer it from the Type of 'value'
        if (dpt == null || dpt == "") {
            dpt = toDPTid(value.getClass());
        }
        
        Datapoint datapoint = new CommandDP(groupAddress, getThing().getUID().toString(), 0, dpt);
        writeToKNX(datapoint, value);
    }

    public void writeToKNX(Datapoint datapoint, Type value) {

        ProcessCommunicator pc = getCommunicator();

        if (pc == null) {
            logger.error("Could not get hold of KNX Process Communicator");
            return;
        }

        try {
            String dpt = toDPTValue(value, datapoint.getDPT());
            if (dpt != null) {
                pc.write(datapoint, dpt);
                logger.debug("Wrote value '{}' to datapoint '{}'", value, datapoint);
            } else {
                logger.debug("Value '{}' can not be mapped to datapoint '{}'", value, datapoint);
            }
        } catch (KNXException e) {
            logger.warn("Value '{}' could not be sent to the KNX bus using datapoint '{}' - retrying one time: {}",
                    new Object[] { value, datapoint, e.getMessage() });
            try {
                // do a second try, maybe the reconnection was successful
                pc = getCommunicator();
                pc.write(datapoint, toDPTValue(value, datapoint.getDPT()));
                logger.debug("Wrote value '{}' to datapoint '{}' on second try", value, datapoint);
            } catch (KNXException e1) {
                logger.error(
                        "Value '{}' could not be sent to the KNX bus using datapoint '{}' - giving up after second try: {}",
                        new Object[] { value, datapoint, e1.getMessage() });
            }
        }
    }

    /**
     * Transforms an openHAB type (command or state) into a datapoint type value
     * for the KNX bus.
     *
     * @param type
     *            the openHAB command or state to transform
     * @param dpt
     *            the datapoint type to which should be converted
     *
     * @return the corresponding KNX datapoint type value as a string
     */
    public String toDPTValue(Type type, String dpt) {
        String value = typeMapper.toDPTValue(type, dpt);
        if (value != null) {
            return value;
        } else {
            return null;
        }
    }

    /**
     * Transforms the raw KNX bus data of a given datapoint into an openHAB type
     * (command or state)
     *
     * @param datapoint
     *            the datapoint to which the data belongs
     * @param asdu
     *            the byte array of the raw data from the KNX bus
     * @return the openHAB command or state that corresponds to the data
     */
    public Type getEshType(Datapoint datapoint, byte[] asdu) {
        Type type = typeMapper.toType(datapoint, asdu);
        if (type != null) {
            return type;
        } else {
            return null;
        }
    }

    public Type getEshType(GroupAddress destination, String dpt, byte[] asdu) {
        Datapoint datapoint = new CommandDP(destination, getThing().getUID().toString(), 0, dpt);
        return getEshType(datapoint, asdu);
    }

}
