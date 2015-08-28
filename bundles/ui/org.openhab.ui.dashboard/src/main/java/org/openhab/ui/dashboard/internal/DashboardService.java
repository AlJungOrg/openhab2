/**
 * Copyright (c) 2014-2015 openHAB UG (haftungsbeschraenkt) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.ui.dashboard.internal;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import org.apache.commons.io.IOUtils;
import org.openhab.ui.dashboard.DashboardTile;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This component registers the dashboard resources.
 * 
 * @author Kai Kreuzer - Initial contribution
 */
public class DashboardService {
	
    public static final String DASHBOARD_ALIAS = "/start";

    /** the name of the servlet to be used in the URL */
    public static final String SERVLET_NAME = "index";

    private static final Logger logger = LoggerFactory.getLogger(DashboardService.class);

    protected HttpService httpService;
    
    protected Set<DashboardTile> tiles = new CopyOnWriteArraySet<>();

    private BundleContext bundleContext;

    protected void activate(ComponentContext componentContext) {
        try {
            bundleContext = componentContext.getBundleContext();
            Hashtable<String, String> props = new Hashtable<String, String>();
            httpService.registerServlet(DASHBOARD_ALIAS + "/" + SERVLET_NAME, createServlet(), props, httpService.createDefaultHttpContext());
            httpService.registerResources(DASHBOARD_ALIAS, "web", null);
            logger.info("Started dashboard at " + DASHBOARD_ALIAS);
        } catch (NamespaceException | ServletException e) {
            logger.error("Error during dashboard startup: {}", e.getMessage());
        }
    }

    protected void deactivate(ComponentContext componentContext) {
        httpService.unregister(DASHBOARD_ALIAS);
        logger.info("Stopped dashboard");
    }

    protected void setHttpService(HttpService httpService) {
        this.httpService = httpService;
    }

    protected void unsetHttpService(HttpService httpService) {
        this.httpService = null;
    }
    
    protected void addDashboardTile(DashboardTile tile) {
        tiles.add(tile);
    }

    protected void removeDashboardTile(DashboardTile tile) {
        tiles.remove(tile);
    }
    
    protected HttpServlet createServlet() {
        String indexTemplate = findRessourceEntry("index.html");
        String entryTemplate = findRessourceEntry("entry.html");
        return new DashboardServlet(indexTemplate, entryTemplate, tiles);
    }

	private String findRessourceEntry(String ressourcePath) {
		URL relevantEntry = null;
		
		Enumeration<URL> entries = bundleContext.getBundle().findEntries("templates", ressourcePath, true);
		if (entries != null) {
			while (entries.hasMoreElements()) {
				relevantEntry = entries.nextElement();
			}
		}
		
        if (relevantEntry != null) {
            try {
                return IOUtils.toString(relevantEntry.openStream());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new RuntimeException("Cannot find " + ressourcePath + " - failed to initialize dashboard servlet");
        }
	}
    
}
