/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.knx.internal.reader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.openhab.binding.knx.handler.KnxBridgeBaseThingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tuwien.auto.calimero.GroupAddress;
import tuwien.auto.calimero.datapoint.Datapoint;

/**
 * This is the central class that takes care of the refreshing (cyclical
 * reading) of GAs from the KNX bus.
 * 
 * @author Volker Daube - initial contribution (OH1)
 * @author Thomas Eichstaedt-Engelen - migration to OH2
 */
public class DatapointReaderManager {

    private static final Logger logger = LoggerFactory.getLogger(DatapointReaderManager.class);

    private final int readRetryLimit;

    private DatapointReaderTask datapointReaderTask;
    private ScheduledExecutorService executorService;

    private KnxBridgeBaseThingHandler knxCommunicator;
    
    
    public DatapointReaderManager(KnxBridgeBaseThingHandler communicator, ScheduledExecutorService executorService, int readingPause, int readRetryLimit) {
        this.knxCommunicator = communicator;
        this.executorService = executorService;
        this.readRetryLimit = readRetryLimit;

        datapointReaderTask = new DatapointReaderTask(readingPause);
    }
    

    /**
     * Starts the scheduler
     */
    public void start() {
        logger.debug("Starting KNX Datapoint Reader");
        datapointReaderTask.proceed();
    }

    /**
     * Stop the scheduler
     */
    public void shutdown() {
        logger.debug("Terminating KNX Datapoint Reader.");
        datapointReaderTask.halt();
    }

    /**
     * Schedules a <code>Datapoint</code> to be cyclicly read. When parameter
     * <code>autoRefreshTimeInSecs</code> is 0 then calling ths method is equal
     * to calling <link>readOnce</link>. This function will return true if the
     * <code>Datapoint</code> was added or if it was already scheduled with an
     * identical <code>autoRefreshTimeInSecs</code>.
     * 
     * @param datapoint
     *            the <code>Datapoint</code> to be read
     * @param readIntervalInSecs
     *            time in seconds specifying the reading cycle. 0 is equal to
     *            calling <link>readOnce</link>
     */
    public synchronized void scheduleRead(Datapoint datapoint, int readIntervalInSecs) {
        if (datapoint == null) {
            logger.error("Argument datapoint cannot be null");
            return;
        }

        long interval = 0 == readIntervalInSecs ? 1000 : readIntervalInSecs * 1000;
        long repeatCounter = 0 == readIntervalInSecs ? readRetryLimit : -1;

        datapointReaderTask.add(new DatapointReader(datapoint, interval, repeatCounter));
    }
    
    public synchronized void unscheduleRead(GroupAddress ga) {
        datapointReaderTask.remove(ga);
    }
    
    private final class DatapointReader {

        private final Datapoint dp;
        private long last = -1; // past infinity
        private final long interval;
        private long repeatCounter;

        public DatapointReader(Datapoint dp, long interval, long repeatCounter) {
            this.dp = dp;
            this.interval = interval;
            this.repeatCounter = repeatCounter;
        }

        synchronized void read(long now) {
            logger.debug("Trying to read form KNX bus: {}", dp);

            try {
                knxCommunicator.readFromKnx(dp);
            } catch (Exception e) {
                logger.error("read from knx failed: {}", e);
            }

            this.last = now;
            this.repeatCounter--;
        }

        long getDesiredNext() {
            return last + interval;
        }

        boolean isDone() {
            return (this.repeatCounter == 0);
        }
        
        @Override
        public String toString() {
            return "DatapointReader [dp=" + dp + ", last=" + last + ", interval=" + interval + ", repeatCounter="
                    + repeatCounter + "]";
        }

    }
    
    
    private final class DatapointReaderTask implements Runnable {

        private final List<DatapointReader> readers = 
           Collections.synchronizedList(new ArrayList<DatapointReader>());

        private DatapointReader nextReader;
        private long nextTime;
        private ScheduledFuture<?> scheduledTask = null;

        private final long throttleRate;
        
        
        public DatapointReaderTask(long throttleRate) {
            this.throttleRate = throttleRate;
        }
        

        void add(DatapointReader dpr) {
            logger.trace("add DatapointReader {}", dpr.toString());
            
            readers.add(dpr);
            nextReader = dpr;
            
            proceed();
        }

        void remove(GroupAddress ga) {
            Iterator<DatapointReader> readerIterator = readers.iterator();
            while (readerIterator.hasNext()) {
                DatapointReader reader = readerIterator.next();
                if (ga.toString().equals(reader.dp.getMainAddress().toString())) {
                    logger.trace("going to remove ga '{}' from DatapointReaderTask!", ga.toString());
                    readerIterator.remove();
                    
                    // also reset 'nextReader' if this reader is equal to
                    // nextReader by chance ...
                    if (nextReader.equals(reader)) {
                        nextReader = null;
                    }
                }
            }
        }

        void halt() {
            if (null != scheduledTask) {
                scheduledTask.cancel(false);
            }
        }

        void proceed() {
            reschedule(0L);
        }

        @Override
        public void run() {
            long t = System.currentTimeMillis();

            logger.trace("{} woke up and going to read values from {}", t, nextReader);

            if (nextReader != null) {
                nextReader.read(t);
                if (nextReader.isDone()) {
                    readers.remove(nextReader);
                    logger.trace("remove DatapointReader {}", nextReader);
                }
            }

            // Initialize nextTime to infinity
            nextTime = Long.MAX_VALUE;
            nextReader = null;

            for (DatapointReader reader : readers) {
                long desiredNext = reader.getDesiredNext();
                if (desiredNext < nextTime) {
                    nextTime = desiredNext;
                    nextReader = reader;
                }
            }

            nextTime = Math.max(nextTime, t + throttleRate);

            reschedule(nextTime - t);
        }
        
        private void reschedule(long delay) {
            halt();
            scheduledTask = executorService.schedule(this, delay, TimeUnit.MILLISECONDS);
        }

    }

}
