/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.knx.handler;

import static org.apache.commons.lang.StringUtils.isNotBlank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.eclipse.smarthome.config.core.Configuration;
import org.openhab.binding.knx.internal.dpt.KnxCoreTypeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tuwien.auto.calimero.GroupAddress;

/**
 * The {@link KnxChannelConfigParser} is responsible for parsing a given channel configuration
 * of an {@link GenericKnxThingHandler}. The overall structure of the configuration block looks
 * like this:
 * 
 * <pre>
 * {
 *	"channels": [
 *		{
 *          ...
 *			"id": "Dimmer_1461013197226656000",
 *			"itemType": "Dimmer",
 *			"configuration": {
 *				"initialize": "1/2/1:5.001",
 *				"commands": {
 *					"OnOffType": {
 *						"command": "1/1/1:1.001",
 *						"listening": [
 *							"1/10/1:1.001"
 *						]
 *					},
 *					"PercentType": {
 *						"command": "1/2/1:5.001",
 *						"listening": [
 *							"1/11/1:5.001"
 *						]
 *					}
 *				}
 *			}
 *		}
 *  ],
 *  ...
 * </pre>
 * 
 * The configuration mainly comprises of two components: first the GA being used to initialize
 * the according state while starting up the platform and second all necessary commands with
 * their respective type according to the given item type this channel will be bound to afterwards.
 * The commands section provides a mapping for each accepted command (see Items acceptedCommand
 * list) to the respective group address. Group addresses can provided as typed GA by postfixing
 * a GA with the according KNX DPT.
 * 
 * Each command can either provide a command GA or an array of listening GAs or both.
 * 
 * @author Thomas Eichstaedt-Engelen - Initial contribution
 */
public class KnxChannelConfigParser {

    private Logger logger = LoggerFactory.getLogger(KnxChannelConfigParser.class);

    public static final String CONFIG_KEY_COMMANDS = "commands";

    public static final String CONFIG_KEY_ITEM_TYPE = "itemType";
    public static final String CONFIG_KEY_COMMAND_GA = "command";
    public static final String CONFIG_KEY_LISTENING = "listening";
    public static final String CONFIG_KEY_INITIALIZE_GA = "initialize";
    public static final String CONFIG_KEY_AUTOUPDATE = "autoupdate";

    private Configuration channelConfig;

    
    public KnxChannelConfigParser(Configuration channelConfig) {
        this.channelConfig = channelConfig;
    }

    
    private List<TypedGroupaddress> allGas() {
        List<TypedGroupaddress> gas = new ArrayList<>();
        for (String commandType : getCommandsConfig().keySet()) {
            gas.addAll(allGasByCommandType(commandType));
        }
        return gas;
    }

    private List<TypedGroupaddress> allGasByCommandType(String commandType) {
        List<TypedGroupaddress> gas = new ArrayList<>();     
        
        TypedGroupaddress commandGa = commandGa(commandType);
        if (commandGa != null) {
            gas.add(commandGa);
        }
        // listeningGas returns an empty list (in case)
        gas.addAll(listeningGas(commandType));
        
        return gas;
    }

    public TypedGroupaddress findTypedGa(String commandType, GroupAddress ga) {
        for (TypedGroupaddress typedGa : allGasByCommandType(commandType)) {
            if (typedGa.equals(ga.toString())) {
                return typedGa;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private TypedGroupaddress commandGa(String commandType) {
        Map<String, Object> configValueMap = (Map<String, Object>) getCommandsConfig().get(commandType);
        if (configValueMap != null) {
            String commandGaStr = (String) configValueMap.get(CONFIG_KEY_COMMAND_GA);
            if (isNotBlank(commandGaStr)) {
                return new TypedGroupaddress(commandGaStr, commandType);
            } else {
                logger.trace("No command GA configured for command type '{}'.", commandType);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<TypedGroupaddress> listeningGas(String commandType) {
        List<TypedGroupaddress> gas = new ArrayList<>();
        Map<String, Object> configValueMap = (Map<String, Object>) getCommandsConfig().get(commandType);
        if (configValueMap != null) {
            List<String> listeningGas = (List<String>) configValueMap.get(CONFIG_KEY_LISTENING);
            if (listeningGas != null) {
                for (String listeningGaStr : listeningGas) {
                    if (isNotBlank(listeningGaStr)) {
                        gas.add(new TypedGroupaddress(listeningGaStr, commandType));
                    }
                }
            }
            return gas;
        } else {
            return Collections.EMPTY_LIST;
        }
    }

    public TypedGroupaddress initializingGa() {
        String initializeGaStr = (String) channelConfig.get(CONFIG_KEY_INITIALIZE_GA);
        if (StringUtils.isNotBlank(initializeGaStr)) {
            // TODO: TEE: we could think over to implement more magic to infer DPT from according GA
            return new TypedGroupaddress(initializeGaStr);
        }
        return null;
    }

    public List<String> getConfigKeys() {
        return new ArrayList<String>(getCommandsConfig().keySet());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getChannelConfigByCommandType(String commandType) {
        for (String commandTypeKey : getCommandsConfig().keySet()) {
            if (commandType.equalsIgnoreCase(commandTypeKey)) {
                return (Map<String, Object>) getCommandsConfig().get(commandTypeKey);
            }
        }
        return null;
    }

    public boolean listensTo(GroupAddress destination) {
        for (TypedGroupaddress typedGa : allGas()) {
            if (typedGa.equals(destination.toString())) {
                return true;
            }
        }
        return false;
    }

    public boolean listensTo(String commandTypeKey, GroupAddress ga) {
        Map<String, Object> channelConfigByType = getChannelConfigByCommandType(commandTypeKey);
        if (channelConfigByType != null) {
            TypedGroupaddress commandGa = commandGa(commandTypeKey);
            if (commandGa != null && StringUtils.equals(ga.toString(), commandGa.gaStr)) {
                return true;
            } else {
                for (TypedGroupaddress listeningGa : listeningGas(commandTypeKey)) {
                    if (listeningGa.equals(ga.toString())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getCommandsConfig() {
    	if (channelConfig.get(CONFIG_KEY_COMMANDS) != null) {
    		return (Map<String, Object>) channelConfig.get(CONFIG_KEY_COMMANDS);
    	} else {
    		return new HashMap<String, Object>();
    	}
    }

    public Boolean isAutoUpdate() {
        return Boolean.TRUE.equals(channelConfig.get(CONFIG_KEY_AUTOUPDATE));
    }


    class TypedGroupaddress {

        private final String SEPARATOR = ":";

        String gaStr;
        String dptStr;

        public TypedGroupaddress(String gaStr) {
            this.gaStr = gaStr;
            if (gaStr.contains(SEPARATOR)) {
                String[] gaDptStr = gaStr.split(SEPARATOR);
                this.gaStr = gaDptStr[0];
                this.dptStr = gaDptStr[1];
            }
        }

        public TypedGroupaddress(String gaStr, String commandType) {
            this.gaStr = gaStr;
            if (gaStr.contains(SEPARATOR)) {
                String[] gaDptStr = gaStr.split(SEPARATOR);
                this.gaStr = gaDptStr[0];
                this.dptStr = gaDptStr[1];
            } else {
                this.dptStr = KnxCoreTypeMapper.toDPTid(commandType);
            }
        }

        public String getGaStr() {
            return gaStr;
        }

        public String getDptStr() {
            return dptStr;
        }

        @Override
        public int hashCode() {
        	if (gaStr != null) {
        		return gaStr.hashCode();
        	} else {
        		return -1;
        	}
        }

        @Override
        public boolean equals(Object obj) {
        	if (gaStr != null) {
        		return gaStr.equals(obj);
        	} else {
        		return false;
        	}
        }
        
        @Override
        public String toString() {
            return gaStr + SEPARATOR + dptStr;
        }
        
    }

}
