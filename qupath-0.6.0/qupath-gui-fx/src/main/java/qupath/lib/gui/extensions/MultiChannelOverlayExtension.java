/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2025 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.lib.gui.extensions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.display.MultiChannelOverlayServer;
import qupath.lib.images.servers.ImageServers;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.io.GsonTools;

/**
 * Extension to register the MultiChannelOverlayServer for JSON serialization.
 * This ensures the server builder is registered before any projects are loaded.
 * 
 * @author Custom Implementation
 * @since v0.6.0
 */
public class MultiChannelOverlayExtension implements QuPathExtension {
    
    private static final Logger logger = LoggerFactory.getLogger(MultiChannelOverlayExtension.class);
    private static boolean registered = false;
    
    // Register early using static initializer
    static {
        registerServerBuilder();
    }
    
    private static synchronized void registerServerBuilder() {
        if (!registered) {
            try {
                @SuppressWarnings("unchecked")
                GsonTools.SubTypeAdapterFactory<ServerBuilder> factory = 
                    (GsonTools.SubTypeAdapterFactory<ServerBuilder>) ImageServers.getServerBuilderFactory();
                factory.registerSubtype(MultiChannelOverlayServer.MultiChannelOverlayServerBuilder.class, "multiChannelOverlay");
                logger.info("MultiChannelOverlayServerBuilder registered successfully");
                registered = true;
            } catch (Exception e) {
                logger.error("Failed to register MultiChannelOverlayServerBuilder", e);
            }
        }
    }
    
    @Override
    public void installExtension(QuPathGUI qupath) {
        // Ensure registration happened
        registerServerBuilder();
        logger.info("Multi-channel overlay extension installed");
    }

    @Override
    public String getName() {
        return "Multi-channel overlay extension";
    }

    @Override
    public String getDescription() {
        return "Provides support for overlaying multiple images as separate channels";
    }
}

