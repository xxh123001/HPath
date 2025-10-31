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

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.commands.ProjectHistoryManager.ProjectHistoryEntry;

/**
 * Enhanced project open dialog with recent projects history.
 * 
 * Features:
 * - Show recent projects
 * - Open in Finder/Explorer
 * - Quick access to recent projects
 * - Browse for other projects
 * 
 * @author HPath Team
 */
public class EnhancedProjectOpenDialog {
    
    private static final Logger logger = LoggerFactory.getLogger(EnhancedProjectOpenDialog.class);
    
    /**
     * Show dialog to open project with history
     */
    public static File showDialog(Window owner) {
        Dialog<File> dialog = new Dialog<>();
        dialog.setTitle("Open Project");
        dialog.initOwner(owner);
        
        BorderPane mainPane = new BorderPane();
        mainPane.setPadding(new Insets(15));
        mainPane.setPrefWidth(600);
        mainPane.setPrefHeight(500);
        
        // Title
        Label titleLabel = new Label("Recent Projects");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        
        // Recent projects list
        ProjectHistoryManager historyManager = ProjectHistoryManager.getInstance();
        ListView<ProjectHistoryEntry> listView = new ListView<>();
        listView.setItems(FXCollections.observableArrayList(historyManager.getRecentProjects()));
        listView.setCellFactory(lv -> new javafx.scene.control.ListCell<ProjectHistoryEntry>() {
            @Override
            protected void updateItem(ProjectHistoryEntry entry, boolean empty) {
                super.updateItem(entry, empty);
                if (empty || entry == null) {
                    setText(null);
                    setGraphic(null);
                    setTooltip(null);
                } else {
                    setText(String.format("%s\n   %s\n   Last opened: %s",
                        entry.getProjectName(),
                        entry.getPath(),
                        entry.getFormattedDate()));
                    
                    setTooltip(new Tooltip(entry.getPath()));
                    
                    // Mark if project doesn't exist
                    if (!entry.exists()) {
                        setStyle("-fx-text-fill: gray;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });
        
        // Placeholder
        listView.setPlaceholder(new Label("No recent projects.\nClick 'Browse...' to open a project."));
        
        // Double-click to open
        final File[] selectedFile = {null};
        listView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                ProjectHistoryEntry selected = listView.getSelectionModel().getSelectedItem();
                if (selected != null && selected.exists()) {
                    selectedFile[0] = new File(selected.getPath());
                    dialog.setResult(selectedFile[0]);
                    dialog.close();
                }
            }
        });
        
        // Top buttons: Open in Finder + Clear History
        Button openInFinderBtn = new Button("📂 Open in Finder");
        openInFinderBtn.setTooltip(new Tooltip("Show selected project in Finder/Explorer"));
        openInFinderBtn.setOnAction(e -> {
            ProjectHistoryEntry selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                openInFinder(new File(selected.getPath()));
            }
        });
        openInFinderBtn.disableProperty().bind(
            listView.getSelectionModel().selectedItemProperty().isNull()
        );
        
        Button clearHistoryBtn = new Button("Clear History");
        clearHistoryBtn.setOnAction(e -> {
            historyManager.clearHistory();
            listView.getItems().clear();
        });
        
        Button refreshBtn = new Button("🔄 Refresh");
        refreshBtn.setOnAction(e -> {
            listView.setItems(FXCollections.observableArrayList(historyManager.getRecentProjects()));
        });
        
        HBox topButtons = new HBox(10, openInFinderBtn, clearHistoryBtn, refreshBtn);
        topButtons.setPadding(new Insets(0, 0, 10, 0));
        
        // Main content
        VBox centerBox = new VBox(10);
        centerBox.getChildren().addAll(titleLabel, topButtons, listView);
        VBox.setVgrow(listView, Priority.ALWAYS);
        
        mainPane.setCenter(centerBox);
        
        // Dialog buttons
        ButtonType browseButtonType = new ButtonType("Browse...");
        ButtonType openButtonType = ButtonType.OK;
        ButtonType cancelButtonType = ButtonType.CANCEL;
        
        dialog.getDialogPane().getButtonTypes().addAll(browseButtonType, openButtonType, cancelButtonType);
        dialog.getDialogPane().setContent(mainPane);
        
        // Browse button action
        Button browseButton = (Button) dialog.getDialogPane().lookupButton(browseButtonType);
        browseButton.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Open Project");
            chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("QuPath Projects", "*.qpproj")
            );
            
            File file = chooser.showOpenDialog(owner);
            if (file != null) {
                selectedFile[0] = file;
                dialog.setResult(file);
                dialog.close();
            }
            e.consume();
        });
        
        // Open button action
        Button openButton = (Button) dialog.getDialogPane().lookupButton(openButtonType);
        openButton.setText("Open");
        openButton.setOnAction(e -> {
            ProjectHistoryEntry selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null && selected.exists()) {
                selectedFile[0] = new File(selected.getPath());
                dialog.setResult(selectedFile[0]);
            }
            e.consume();
        });
        openButton.disableProperty().bind(
            listView.getSelectionModel().selectedItemProperty().isNull()
        );
        
        // Show dialog
        dialog.setResultConverter(dialogButton -> {
            return selectedFile[0];
        });
        
        var result = dialog.showAndWait();
        return result.orElse(null);
    }
    
    /**
     * Open file/folder in Finder (Mac) or Explorer (Windows/Linux)
     */
    private static void openInFinder(File file) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                
                // If it's a file, show its parent directory
                File toOpen = file.isDirectory() ? file : file.getParentFile();
                
                if (toOpen != null && toOpen.exists()) {
                    desktop.open(toOpen);
                    logger.info("Opened in Finder: {}", toOpen.getAbsolutePath());
                }
            } else {
                // Fallback: try system commands
                String os = System.getProperty("os.name").toLowerCase();
                File toOpen = file.isDirectory() ? file : file.getParentFile();
                
                if (toOpen != null && toOpen.exists()) {
                    if (os.contains("mac")) {
                        Runtime.getRuntime().exec(new String[]{"open", toOpen.getAbsolutePath()});
                    } else if (os.contains("win")) {
                        Runtime.getRuntime().exec(new String[]{"explorer", toOpen.getAbsolutePath()});
                    } else {
                        Runtime.getRuntime().exec(new String[]{"xdg-open", toOpen.getAbsolutePath()});
                    }
                    logger.info("Opened in file manager: {}", toOpen.getAbsolutePath());
                }
            }
        } catch (IOException e) {
            logger.error("Failed to open in file manager", e);
            Dialogs.showErrorMessage("Open in Finder", 
                "Failed to open file manager: " + e.getMessage());
        }
    }
}

