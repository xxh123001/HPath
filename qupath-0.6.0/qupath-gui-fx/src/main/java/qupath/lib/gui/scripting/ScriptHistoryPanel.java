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

import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Panel for displaying and managing script execution history.
 * 
 * @author HPath Team
 */
public class ScriptHistoryPanel extends BorderPane {
    
    private static final Logger logger = LoggerFactory.getLogger(ScriptHistoryPanel.class);
    
    private final ScriptHistoryManager historyManager;
    private final ListView<ScriptHistoryEntry> historyList;
    private final TextField searchField;
    private final FilteredList<ScriptHistoryEntry> filteredHistory;
    
    private Consumer<ScriptHistoryEntry> onRerun;
    private Consumer<ScriptHistoryEntry> onOpenInEditor;
    
    public ScriptHistoryPanel() {
        this.historyManager = ScriptHistoryManager.getInstance();
        this.filteredHistory = new FilteredList<>(historyManager.getHistory());
        this.historyList = new ListView<>(filteredHistory);
        this.searchField = new TextField();
        
        setupUI();
        setupListView();
        setupContextMenu();
    }
    
    /**
     * Setup the UI layout
     */
    private void setupUI() {
        // Top: Search area
        Label searchLabel = new Label("Search:");
        searchField.setPromptText("Filter history...");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            updateFilter(newVal);
        });
        
        HBox searchBox = new HBox(10);
        searchBox.setPadding(new Insets(5));
        searchBox.getChildren().addAll(searchLabel, searchField);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        
        // Center: History list
        VBox centerBox = new VBox(5);
        Label listLabel = new Label("Recent Scripts:");
        listLabel.setPadding(new Insets(5, 5, 0, 5));
        centerBox.getChildren().addAll(listLabel, historyList);
        VBox.setVgrow(historyList, Priority.ALWAYS);
        
        // Bottom: Action buttons
        Button clearButton = new Button("Clear History");
        clearButton.setOnAction(e -> {
            historyManager.clearHistory();
            logger.info("History cleared");
        });
        
        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> {
            historyList.refresh();
        });
        
        HBox buttonBox = new HBox(10);
        buttonBox.setPadding(new Insets(5));
        buttonBox.getChildren().addAll(clearButton, refreshButton);
        
        // Layout
        setTop(searchBox);
        setCenter(centerBox);
        setBottom(buttonBox);
        setPadding(new Insets(5));
        
        setMinWidth(300);
        setPrefWidth(350);
    }
    
    /**
     * Setup the list view with custom cell factory
     */
    private void setupListView() {
        historyList.setCellFactory(lv -> new ListCell<ScriptHistoryEntry>() {
            @Override
            protected void updateItem(ScriptHistoryEntry entry, boolean empty) {
                super.updateItem(entry, empty);
                
                if (empty || entry == null) {
                    setText(null);
                    setGraphic(null);
                    setTooltip(null);
                } else {
                    // Format: status icon + name + time
                    String status = entry.isSuccess() ? "✓" : "✗";
                    String text = String.format("%s %s\n   %s (%s)",
                        status,
                        entry.getScriptName(),
                        entry.getFormattedTimestamp(),
                        entry.getFormattedDuration()
                    );
                    
                    setText(text);
                    
                    // Set text color based on success
                    if (entry.isSuccess()) {
                        setStyle("-fx-text-fill: green;");
                    } else {
                        setStyle("-fx-text-fill: red;");
                    }
                    
                    // Tooltip with more info
                    String tooltipText = String.format(
                        "Script: %s\nTime: %s\nDuration: %s\nStatus: %s",
                        entry.getScriptName(),
                        entry.getFormattedTimestamp(),
                        entry.getFormattedDuration(),
                        entry.isSuccess() ? "Success" : "Failed"
                    );
                    setTooltip(new Tooltip(tooltipText));
                }
            }
        });
        
        // Double-click to rerun
        historyList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                ScriptHistoryEntry selected = historyList.getSelectionModel().getSelectedItem();
                if (selected != null && onRerun != null) {
                    onRerun.accept(selected);
                }
            }
        });
    }
    
    /**
     * Setup context menu for history items
     */
    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        
        MenuItem rerunItem = new MenuItem("Re-run Script");
        rerunItem.setOnAction(e -> {
            ScriptHistoryEntry selected = historyList.getSelectionModel().getSelectedItem();
            if (selected != null && onRerun != null) {
                onRerun.accept(selected);
            }
        });
        
        MenuItem openItem = new MenuItem("Open in Editor");
        openItem.setOnAction(e -> {
            ScriptHistoryEntry selected = historyList.getSelectionModel().getSelectedItem();
            if (selected != null && onOpenInEditor != null) {
                onOpenInEditor.accept(selected);
            }
        });
        
        MenuItem viewOutputItem = new MenuItem("View Output");
        viewOutputItem.setOnAction(e -> {
            ScriptHistoryEntry selected = historyList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                showOutputDialog(selected);
            }
        });
        
        MenuItem copyScriptItem = new MenuItem("Copy Script to Clipboard");
        copyScriptItem.setOnAction(e -> {
            ScriptHistoryEntry selected = historyList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
                javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                content.putString(selected.getScriptContent());
                clipboard.setContent(content);
                logger.info("Script copied to clipboard");
            }
        });
        
        MenuItem deleteItem = new MenuItem("Delete Entry");
        deleteItem.setOnAction(e -> {
            ScriptHistoryEntry selected = historyList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                historyManager.removeEntry(selected);
            }
        });
        
        contextMenu.getItems().addAll(
            rerunItem,
            openItem,
            new SeparatorMenuItem(),
            viewOutputItem,
            copyScriptItem,
            new SeparatorMenuItem(),
            deleteItem
        );
        
        historyList.setContextMenu(contextMenu);
    }
    
    /**
     * Update filter based on search text
     */
    private void updateFilter(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            filteredHistory.setPredicate(null);
        } else {
            String lowerSearch = searchText.toLowerCase();
            filteredHistory.setPredicate(entry -> 
                entry.getScriptName().toLowerCase().contains(lowerSearch) ||
                entry.getScriptContent().toLowerCase().contains(lowerSearch)
            );
        }
    }
    
    /**
     * Show dialog with script output
     */
    private void showOutputDialog(ScriptHistoryEntry entry) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
            javafx.scene.control.Alert.AlertType.INFORMATION
        );
        alert.setTitle("Script Output");
        alert.setHeaderText(entry.getScriptName() + " - " + entry.getFormattedTimestamp());
        
        javafx.scene.control.TextArea textArea = new javafx.scene.control.TextArea();
        
        StringBuilder content = new StringBuilder();
        content.append("Status: ").append(entry.isSuccess() ? "Success" : "Failed").append("\n");
        content.append("Duration: ").append(entry.getFormattedDuration()).append("\n");
        content.append("\n=== Output ===\n");
        content.append(entry.getOutput());
        
        if (!entry.isSuccess() && entry.getErrorMessage() != null) {
            content.append("\n\n=== Error ===\n");
            content.append(entry.getErrorMessage());
        }
        
        textArea.setText(content.toString());
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefRowCount(20);
        textArea.setPrefColumnCount(60);
        
        alert.getDialogPane().setContent(textArea);
        alert.showAndWait();
    }
    
    /**
     * Set callback for when user wants to rerun a script
     */
    public void setOnRerun(Consumer<ScriptHistoryEntry> callback) {
        this.onRerun = callback;
    }
    
    /**
     * Set callback for when user wants to open script in editor
     */
    public void setOnOpenInEditor(Consumer<ScriptHistoryEntry> callback) {
        this.onOpenInEditor = callback;
    }
    
    /**
     * Refresh the history list
     */
    public void refresh() {
        historyList.refresh();
    }
}

