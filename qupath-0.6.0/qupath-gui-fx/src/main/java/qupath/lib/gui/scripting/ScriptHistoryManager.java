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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Manager for script execution history.
 * Persists history to disk and provides search/filter capabilities.
 * 
 * @author HPath Team
 */
public class ScriptHistoryManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ScriptHistoryManager.class);
    private static ScriptHistoryManager instance = null;
    
    private static final String HISTORY_DIR_NAME = ".qupath";
    private static final String HISTORY_FILE_NAME = "script-history.json";
    private static final int MAX_HISTORY_SIZE = 1000;
    
    private final Path historyFile;
    private final ObservableList<ScriptHistoryEntry> history;
    private final Gson gson;
    
    private ScriptHistoryManager() {
        String userHome = System.getProperty("user.home");
        Path historyDir = Paths.get(userHome, HISTORY_DIR_NAME);
        
        try {
            if (!Files.exists(historyDir)) {
                Files.createDirectories(historyDir);
            }
        } catch (IOException e) {
            logger.error("Could not create history directory", e);
        }
        
        this.historyFile = historyDir.resolve(HISTORY_FILE_NAME);
        this.history = FXCollections.observableArrayList();
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .registerTypeAdapter(Duration.class, new DurationAdapter())
                .create();
        
        loadHistory();
    }
    
    /**
     * Get singleton instance
     */
    public static synchronized ScriptHistoryManager getInstance() {
        if (instance == null) {
            instance = new ScriptHistoryManager();
        }
        return instance;
    }
    
    /**
     * Add a new history entry
     */
    public void addEntry(ScriptHistoryEntry entry) {
        history.add(0, entry); // Add to beginning (most recent first)
        
        // Trim history if too large
        while (history.size() > MAX_HISTORY_SIZE) {
            history.remove(history.size() - 1);
        }
        
        logger.debug("Added history entry: {}", entry.getScriptName());
        
        // Auto-save
        saveHistory();
    }
    
    /**
     * Get all history entries
     */
    public ObservableList<ScriptHistoryEntry> getHistory() {
        return history;
    }
    
    /**
     * Get recent history entries
     * @param count number of entries to return
     */
    public List<ScriptHistoryEntry> getRecent(int count) {
        return history.stream()
                .limit(count)
                .collect(Collectors.toList());
    }
    
    /**
     * Search history by script name
     */
    public List<ScriptHistoryEntry> searchByName(String query) {
        String lowerQuery = query.toLowerCase();
        return history.stream()
                .filter(e -> e.getScriptName().toLowerCase().contains(lowerQuery))
                .collect(Collectors.toList());
    }
    
    /**
     * Filter history by success status
     */
    public List<ScriptHistoryEntry> filterBySuccess(boolean success) {
        return history.stream()
                .filter(e -> e.isSuccess() == success)
                .collect(Collectors.toList());
    }
    
    /**
     * Filter history by language
     */
    public List<ScriptHistoryEntry> filterByLanguage(String language) {
        return history.stream()
                .filter(e -> e.getLanguage().equalsIgnoreCase(language))
                .collect(Collectors.toList());
    }
    
    /**
     * Clear all history
     */
    public void clearHistory() {
        history.clear();
        saveHistory();
        logger.info("Cleared script history");
    }
    
    /**
     * Remove a specific entry
     */
    public void removeEntry(ScriptHistoryEntry entry) {
        history.remove(entry);
        saveHistory();
    }
    
    /**
     * Save history to disk
     */
    public void saveHistory() {
        try {
            String json = gson.toJson(history);
            Files.writeString(historyFile, json);
            logger.debug("Saved {} history entries", history.size());
        } catch (IOException e) {
            logger.error("Failed to save history", e);
        }
    }
    
    /**
     * Load history from disk
     */
    private void loadHistory() {
        if (!Files.exists(historyFile)) {
            logger.debug("No history file found, starting with empty history");
            return;
        }
        
        try {
            String json = Files.readString(historyFile);
            TypeToken<List<ScriptHistoryEntry>> typeToken = new TypeToken<List<ScriptHistoryEntry>>() {};
            List<ScriptHistoryEntry> loaded = gson.fromJson(json, typeToken.getType());
            
            if (loaded != null) {
                history.addAll(loaded);
                logger.info("Loaded {} history entries", history.size());
            }
        } catch (Exception e) {
            logger.error("Failed to load history", e);
        }
    }
    
    /**
     * Adapter for LocalDateTime JSON serialization
     */
    private static class LocalDateTimeAdapter extends com.google.gson.TypeAdapter<LocalDateTime> {
        private final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        
        @Override
        public void write(com.google.gson.stream.JsonWriter out, LocalDateTime value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.format(formatter));
            }
        }
        
        @Override
        public LocalDateTime read(com.google.gson.stream.JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return LocalDateTime.parse(in.nextString(), formatter);
        }
    }
    
    /**
     * Adapter for Duration JSON serialization
     */
    private static class DurationAdapter extends com.google.gson.TypeAdapter<Duration> {
        @Override
        public void write(com.google.gson.stream.JsonWriter out, Duration value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toMillis());
            }
        }
        
        @Override
        public Duration read(com.google.gson.stream.JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return Duration.ofMillis(in.nextLong());
        }
    }
}

