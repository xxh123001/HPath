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

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a single entry in the script execution history.
 * Records script name, content, execution time, result, and output.
 * 
 * @author HPath Team
 */
public class ScriptHistoryEntry {
    
    private static final DateTimeFormatter TIMESTAMP_FORMAT = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final String id;
    private final LocalDateTime timestamp;
    private final String scriptName;
    private final String scriptContent;
    private final Duration duration;
    private final boolean success;
    private final String output;
    private final String errorMessage;
    private final String language;
    
    private ScriptHistoryEntry(Builder builder) {
        this.id = builder.id;
        this.timestamp = builder.timestamp;
        this.scriptName = builder.scriptName;
        this.scriptContent = builder.scriptContent;
        this.duration = builder.duration;
        this.success = builder.success;
        this.output = builder.output;
        this.errorMessage = builder.errorMessage;
        this.language = builder.language;
    }
    
    public String getId() {
        return id;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public String getScriptName() {
        return scriptName;
    }
    
    public String getScriptContent() {
        return scriptContent;
    }
    
    public Duration getDuration() {
        return duration;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getOutput() {
        return output;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public String getLanguage() {
        return language;
    }
    
    /**
     * Get formatted timestamp string
     */
    public String getFormattedTimestamp() {
        return timestamp.format(TIMESTAMP_FORMAT);
    }
    
    /**
     * Get formatted duration string
     */
    public String getFormattedDuration() {
        long seconds = duration.getSeconds();
        long millis = duration.toMillis() % 1000;
        
        if (seconds < 1) {
            return String.format("%dms", duration.toMillis());
        } else if (seconds < 60) {
            return String.format("%.2fs", seconds + millis / 1000.0);
        } else {
            long minutes = seconds / 60;
            seconds = seconds % 60;
            return String.format("%dm %ds", minutes, seconds);
        }
    }
    
    /**
     * Get short display string for the entry
     */
    public String getShortDescription() {
        String status = success ? "✓" : "✗";
        return String.format("%s %s (%s) - %s", 
            status, scriptName, getFormattedDuration(), getFormattedTimestamp());
    }
    
    @Override
    public String toString() {
        return getShortDescription();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ScriptHistoryEntry)) return false;
        ScriptHistoryEntry other = (ScriptHistoryEntry) obj;
        return Objects.equals(id, other.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    /**
     * Create a new builder
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for ScriptHistoryEntry
     */
    public static class Builder {
        private String id = UUID.randomUUID().toString();
        private LocalDateTime timestamp = LocalDateTime.now();
        private String scriptName = "Untitled";
        private String scriptContent = "";
        private Duration duration = Duration.ZERO;
        private boolean success = false;
        private String output = "";
        private String errorMessage = null;
        private String language = "Groovy";
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder scriptName(String scriptName) {
            this.scriptName = scriptName;
            return this;
        }
        
        public Builder scriptContent(String scriptContent) {
            this.scriptContent = scriptContent;
            return this;
        }
        
        public Builder duration(Duration duration) {
            this.duration = duration;
            return this;
        }
        
        public Builder success(boolean success) {
            this.success = success;
            return this;
        }
        
        public Builder output(String output) {
            this.output = output;
            return this;
        }
        
        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }
        
        public Builder language(String language) {
            this.language = language;
            return this;
        }
        
        public ScriptHistoryEntry build() {
            return new ScriptHistoryEntry(this);
        }
    }
}

