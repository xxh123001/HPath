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

package qupath.lib.gui.commands;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 * Manager for recent project history.
 * Stores and retrieves recently opened project paths.
 * 
 * @author HPath Team
 */
public class ProjectHistoryManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ProjectHistoryManager.class);
    private static ProjectHistoryManager instance;
    
    private static final String HISTORY_DIR = ".qupath";
    private static final String HISTORY_FILE = "recent-projects.json";
    private static final int MAX_HISTORY = 20;
    
    private final Path historyFilePath;
    private final ObservableList<ProjectHistoryEntry> history;
    private final Gson gson;
    
    private ProjectHistoryManager() {
        String userHome = System.getProperty("user.home");
        Path historyDir = Paths.get(userHome, HISTORY_DIR);
        
        try {
            if (!Files.exists(historyDir)) {
                Files.createDirectories(historyDir);
            }
        } catch (IOException e) {
            logger.error("Could not create history directory", e);
        }
        
        this.historyFilePath = historyDir.resolve(HISTORY_FILE);
        this.history = FXCollections.observableArrayList();
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .create();
        
        loadHistory();
    }
    
    public static synchronized ProjectHistoryManager getInstance() {
        if (instance == null) {
            instance = new ProjectHistoryManager();
        }
        return instance;
    }
    
    /**
     * Add a project to history
     */
    public void addProject(File projectFile) {
        if (projectFile == null || !projectFile.exists()) {
            return;
        }
        
        String absolutePath = projectFile.getAbsolutePath();
        
        // Remove if already exists (to update timestamp)
        history.removeIf(entry -> entry.path.equals(absolutePath));
        
        // Add to beginning
        ProjectHistoryEntry entry = new ProjectHistoryEntry(absolutePath);
        history.add(0, entry);
        
        // Trim to max size
        while (history.size() > MAX_HISTORY) {
            history.remove(history.size() - 1);
        }
        
        saveHistory();
        logger.info("Added project to history: {}", projectFile.getName());
    }
    
    /**
     * Get all history entries
     */
    public ObservableList<ProjectHistoryEntry> getHistory() {
        return history;
    }
    
    /**
     * Get recent projects (that still exist)
     */
    public List<ProjectHistoryEntry> getRecentProjects() {
        return history.stream()
                .filter(entry -> new File(entry.path).exists())
                .collect(Collectors.toList());
    }
    
    /**
     * Clear all history
     */
    public void clearHistory() {
        history.clear();
        saveHistory();
    }
    
    /**
     * Remove a specific entry
     */
    public void removeEntry(ProjectHistoryEntry entry) {
        history.remove(entry);
        saveHistory();
    }
    
    /**
     * Save history to disk
     */
    private void saveHistory() {
        try {
            String json = gson.toJson(history);
            Files.writeString(historyFilePath, json);
            logger.debug("Saved project history ({} entries)", history.size());
        } catch (IOException e) {
            logger.error("Failed to save project history", e);
        }
    }
    
    /**
     * Load history from disk
     */
    private void loadHistory() {
        if (!Files.exists(historyFilePath)) {
            logger.debug("No project history file found");
            return;
        }
        
        try {
            String json = Files.readString(historyFilePath);
            TypeToken<List<ProjectHistoryEntry>> typeToken = new TypeToken<List<ProjectHistoryEntry>>() {};
            List<ProjectHistoryEntry> loaded = gson.fromJson(json, typeToken.getType());
            
            if (loaded != null) {
                history.addAll(loaded);
                logger.info("Loaded {} project history entries", history.size());
            }
        } catch (Exception e) {
            logger.error("Failed to load project history", e);
        }
    }
    
    /**
     * Project history entry
     */
    public static class ProjectHistoryEntry {
        private String path;
        private LocalDateTime lastOpened;
        
        public ProjectHistoryEntry(String path) {
            this.path = path;
            this.lastOpened = LocalDateTime.now();
        }
        
        public String getPath() {
            return path;
        }
        
        public LocalDateTime getLastOpened() {
            return lastOpened;
        }
        
        public String getProjectName() {
            return new File(path).getName();
        }
        
        public String getFormattedDate() {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            return lastOpened.format(formatter);
        }
        
        public boolean exists() {
            return new File(path).exists();
        }
        
        @Override
        public String toString() {
            return String.format("%s (%s)", getProjectName(), getFormattedDate());
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
}

