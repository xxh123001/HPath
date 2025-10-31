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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loader for external script files.
 * Supports searching multiple paths, caching, and preventing circular dependencies.
 * 
 * Usage:
 * <pre>
 * ScriptLoader loader = ScriptLoader.getInstance();
 * String script = loader.loadScript("utils.groovy");
 * </pre>
 * 
 * @author HPath Team
 */
public class ScriptLoader {
    
    private static final Logger logger = LoggerFactory.getLogger(ScriptLoader.class);
    private static ScriptLoader instance = null;
    
    private List<Path> searchPaths = new ArrayList<>();
    private Map<String, String> cache = new HashMap<>();
    private Set<String> currentlyLoading = new HashSet<>();
    private boolean cachingEnabled = true;
    
    private ScriptLoader() {
        addDefaultSearchPaths();
    }
    
    /**
     * Get singleton instance
     */
    public static synchronized ScriptLoader getInstance() {
        if (instance == null) {
            instance = new ScriptLoader();
        }
        return instance;
    }
    
    /**
     * Add default search paths for script loading
     */
    private void addDefaultSearchPaths() {
        // Current working directory
        searchPaths.add(Paths.get(System.getProperty("user.dir")));
        
        // User scripts directory
        String userHome = System.getProperty("user.home");
        Path userScripts = Paths.get(userHome, ".qupath", "scripts");
        if (Files.exists(userScripts) || tryCreateDirectory(userScripts)) {
            searchPaths.add(userScripts);
        }
        
        logger.debug("Initialized ScriptLoader with {} search paths", searchPaths.size());
    }
    
    /**
     * Try to create a directory, return true if successful or already exists
     */
    private boolean tryCreateDirectory(Path path) {
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                logger.info("Created scripts directory: {}", path);
            }
            return true;
        } catch (IOException e) {
            logger.warn("Could not create directory: {}", path, e);
            return false;
        }
    }
    
    /**
     * Add a custom search path for scripts
     * @param path the path to add
     */
    public void addSearchPath(Path path) {
        if (!searchPaths.contains(path)) {
            searchPaths.add(path);
            logger.debug("Added search path: {}", path);
        }
    }
    
    /**
     * Add a custom search path for scripts
     * @param pathString the path string to add
     */
    public void addSearchPath(String pathString) {
        addSearchPath(Paths.get(pathString));
    }
    
    /**
     * Load a script file and return its content.
     * Supports relative and absolute paths.
     * 
     * @param path the path to the script (relative or absolute)
     * @return the script content
     * @throws IOException if the script cannot be found or loaded
     */
    public String loadScript(String path) throws IOException {
        return loadScript(path, null);
    }
    
    /**
     * Load a script file and return its content.
     * 
     * @param path the path to the script (relative or absolute)
     * @param basePath the base path for resolving relative paths (can be null)
     * @return the script content
     * @throws IOException if the script cannot be found or loaded
     */
    public String loadScript(String path, File basePath) throws IOException {
        // Normalize path
        String normalizedPath = path.replace('\\', '/');
        
        // Check for circular dependency
        if (currentlyLoading.contains(normalizedPath)) {
            throw new IOException("Circular dependency detected: " + normalizedPath);
        }
        
        // Check cache first
        if (cachingEnabled && cache.containsKey(normalizedPath)) {
            logger.debug("Loading script from cache: {}", normalizedPath);
            return cache.get(normalizedPath);
        }
        
        // Find the script file
        File scriptFile = findScript(normalizedPath, basePath);
        if (scriptFile == null) {
            throw new IOException("Script not found: " + normalizedPath + 
                "\nSearched in paths: " + searchPaths);
        }
        
        // Mark as currently loading (prevent circular deps)
        currentlyLoading.add(normalizedPath);
        
        try {
            // Load the script
            String content = Files.readString(scriptFile.toPath());
            logger.info("Loaded script: {} ({} bytes)", scriptFile.getAbsolutePath(), content.length());
            
            // Cache it
            if (cachingEnabled) {
                cache.put(normalizedPath, content);
            }
            
            return content;
        } finally {
            currentlyLoading.remove(normalizedPath);
        }
    }
    
    /**
     * Find a script file by searching through search paths
     * 
     * @param path the script path (relative or absolute)
     * @param basePath the base path for relative resolution (can be null)
     * @return the script file, or null if not found
     */
    private File findScript(String path, File basePath) {
        // Try as absolute path first
        File file = new File(path);
        if (file.isAbsolute() && file.exists() && file.isFile()) {
            return file;
        }
        
        // Try relative to base path
        if (basePath != null) {
            File parent = basePath.isDirectory() ? basePath : basePath.getParentFile();
            if (parent != null) {
                File candidate = new File(parent, path);
                if (candidate.exists() && candidate.isFile()) {
                    return candidate;
                }
            }
        }
        
        // Search in search paths
        for (Path searchPath : searchPaths) {
            try {
                File candidate = searchPath.resolve(path).toFile();
                if (candidate.exists() && candidate.isFile()) {
                    return candidate;
                }
            } catch (Exception e) {
                // Invalid path, continue searching
                logger.debug("Could not resolve path {} in {}", path, searchPath);
            }
        }
        
        return null;
    }
    
    /**
     * Check if a script exists
     * @param path the script path
     * @return true if the script can be found
     */
    public boolean scriptExists(String path) {
        return scriptExists(path, null);
    }
    
    /**
     * Check if a script exists
     * @param path the script path
     * @param basePath the base path for relative resolution
     * @return true if the script can be found
     */
    public boolean scriptExists(String path, File basePath) {
        return findScript(path, basePath) != null;
    }
    
    /**
     * Get list of all search paths
     * @return list of search paths
     */
    public List<Path> getSearchPaths() {
        return new ArrayList<>(searchPaths);
    }
    
    /**
     * Clear the script cache
     */
    public void clearCache() {
        int size = cache.size();
        cache.clear();
        logger.info("Cleared script cache ({} entries)", size);
    }
    
    /**
     * Enable or disable caching
     * @param enabled true to enable caching
     */
    public void setCachingEnabled(boolean enabled) {
        this.cachingEnabled = enabled;
        if (!enabled) {
            clearCache();
        }
        logger.debug("Script caching {}", enabled ? "enabled" : "disabled");
    }
    
    /**
     * Check if caching is enabled
     * @return true if caching is enabled
     */
    public boolean isCachingEnabled() {
        return cachingEnabled;
    }
    
    /**
     * Get number of cached scripts
     * @return cache size
     */
    public int getCacheSize() {
        return cache.size();
    }
    
    /**
     * List all available scripts in search paths
     * @return list of script names
     */
    public List<String> listAvailableScripts() {
        List<String> scripts = new ArrayList<>();
        
        for (Path searchPath : searchPaths) {
            try {
                if (Files.exists(searchPath) && Files.isDirectory(searchPath)) {
                    Files.walk(searchPath, 3) // Max depth 3
                        .filter(Files::isRegularFile)
                        .filter(p -> isScriptFile(p.toString()))
                        .forEach(p -> {
                            try {
                                String relative = searchPath.relativize(p).toString();
                                scripts.add(relative);
                            } catch (Exception e) {
                                // Ignore
                            }
                        });
                }
            } catch (IOException e) {
                logger.warn("Error listing scripts in {}", searchPath, e);
            }
        }
        
        return scripts;
    }
    
    /**
     * Check if a file is a script file based on extension
     */
    private boolean isScriptFile(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".groovy") || 
               lower.endsWith(".py") || 
               lower.endsWith(".ijm") ||
               lower.endsWith(".js");
    }
}

