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

package qupath.lib.gui.scripting;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;

/**
 * Watches external script files for changes and automatically reloads them.
 * Allows editing scripts in external editors (VS Code, IntelliJ, etc.) 
 * with real-time sync to QuPath.
 * 
 * @author HPath Team
 */
public class ScriptFileWatcher {
    
    private static final Logger logger = LoggerFactory.getLogger(ScriptFileWatcher.class);
    
    private WatchService watchService;
    private Map<WatchKey, Path> watchKeys = new HashMap<>();
    private Map<Path, FileChangeListener> listeners = new HashMap<>();
    private Thread watchThread;
    private volatile boolean running = false;
    
    /**
     * Callback interface for file changes
     */
    public interface FileChangeListener {
        void onFileChanged(Path file);
    }
    
    /**
     * Start the file watcher
     */
    public void start() {
        if (running) {
            return;
        }
        
        try {
            watchService = FileSystems.getDefault().newWatchService();
            running = true;
            
            watchThread = new Thread(this::watchLoop, "ScriptFileWatcher");
            watchThread.setDaemon(true);
            watchThread.start();
            
            logger.info("Script file watcher started");
        } catch (IOException e) {
            logger.error("Failed to start file watcher", e);
        }
    }
    
    /**
     * Stop the file watcher
     */
    public void stop() {
        running = false;
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                logger.error("Error closing watch service", e);
            }
        }
        logger.info("Script file watcher stopped");
    }
    
    /**
     * Watch a file for changes
     * @param file the file to watch
     * @param listener callback when file changes
     */
    public void watchFile(Path file, FileChangeListener listener) {
        try {
            Path directory = file.getParent();
            
            // Register directory if not already registered
            if (!watchKeys.values().contains(directory)) {
                WatchKey key = directory.register(watchService,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE);
                watchKeys.put(key, directory);
                logger.debug("Registered directory for watching: {}", directory);
            }
            
            // Register listener for this specific file
            listeners.put(file, listener);
            logger.info("Watching file: {}", file);
            
        } catch (IOException e) {
            logger.error("Failed to watch file: {}", file, e);
        }
    }
    
    /**
     * Stop watching a file
     * @param file the file to stop watching
     */
    public void unwatchFile(Path file) {
        listeners.remove(file);
        logger.debug("Stopped watching file: {}", file);
    }
    
    /**
     * Main watch loop
     */
    private void watchLoop() {
        while (running) {
            WatchKey key;
            try {
                // Wait for events (with timeout to allow clean shutdown)
                key = watchService.poll(1, TimeUnit.SECONDS);
                if (key == null) {
                    continue;
                }
                
                Path directory = watchKeys.get(key);
                if (directory == null) {
                    continue;
                }
                
                // Process events
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    
                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();
                    Path fullPath = directory.resolve(filename);
                    
                    // Check if we have a listener for this file
                    FileChangeListener listener = listeners.get(fullPath);
                    if (listener != null) {
                        // Notify on JavaFX thread
                        Platform.runLater(() -> {
                            logger.info("File changed: {}", fullPath);
                            listener.onFileChanged(fullPath);
                        });
                    }
                }
                
                // Reset the key
                boolean valid = key.reset();
                if (!valid) {
                    watchKeys.remove(key);
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error in watch loop", e);
            }
        }
    }
    
    /**
     * Check if a file is being watched
     */
    public boolean isWatching(Path file) {
        return listeners.containsKey(file);
    }
    
    /**
     * Get number of files being watched
     */
    public int getWatchedFileCount() {
        return listeners.size();
    }
}

