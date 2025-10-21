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

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.utils.FXUtils;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.display.MultiChannelOverlayServer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Command to overlay multiple single-channel images as a multi-channel composite.
 * This allows loading separate image files and displaying them as layered channels.
 * 
 * @author Custom Implementation
 * @since v0.6.0
 */
public class MultiChannelOverlayCommand implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(MultiChannelOverlayCommand.class);
    
    private QuPathGUI qupath;
    
    /**
     * Constructor.
     * @param qupath the current QuPath instance
     */
    public MultiChannelOverlayCommand(QuPathGUI qupath) {
        this.qupath = qupath;
    }
    
    @Override
    public void run() {
        var project = qupath.getProject();
        if (project == null) {
            Dialogs.showErrorMessage("Multi-channel overlay", 
                "Please create or open a project first!");
            return;
        }
        
        var imageList = project.getImageList();
        if (imageList.isEmpty()) {
            Dialogs.showErrorMessage("Multi-channel overlay", 
                "No images found in the project. Please add images first!");
            return;
        }
        
        showChannelSelectionDialog(imageList);
    }
    
    private void showChannelSelectionDialog(List<ProjectImageEntry<BufferedImage>> imageList) {
        Stage dialog = new Stage();
        dialog.initOwner(qupath.getStage());
        dialog.setTitle("Multi-Channel Overlay - Select Images");
        FXUtils.addCloseWindowShortcuts(dialog);
        
        VBox mainPane = new VBox(10);
        mainPane.setPadding(new Insets(10));
        
        Label instructions = new Label("Select images to overlay as separate channels:");
        instructions.setStyle("-fx-font-weight: bold;");
        
        ListView<ImageChannelEntry> listView = new ListView<>();
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listView.setPrefHeight(300);
        
        // Populate list with available images
        for (ProjectImageEntry<BufferedImage> entry : imageList) {
            listView.getItems().add(new ImageChannelEntry(entry));
        }
        
        // Options pane
        GridPane optionsPane = new GridPane();
        optionsPane.setHgap(10);
        optionsPane.setVgap(5);
        optionsPane.add(new Label("Tip: Select images in the order you want them layered"), 0, 0, 2, 1);
        
        // Add checkbox for RGB expansion option
        CheckBox expandRGBCheckbox = new CheckBox("Expand RGB images to separate R, G, B channels");
        expandRGBCheckbox.setSelected(false); // Default: keep RGB as single channel (more compact)
        optionsPane.add(expandRGBCheckbox, 0, 1, 2, 1);
        
        Button btnOverlay = new Button("Create Overlay");
        btnOverlay.setDefaultButton(true);
        btnOverlay.setOnAction(e -> {
            var selected = listView.getSelectionModel().getSelectedItems();
            if (selected.isEmpty()) {
                Dialogs.showWarningNotification("Multi-channel overlay", 
                    "Please select at least one image");
                return;
            }
            
            if (selected.size() > 10) {
                Dialogs.showWarningNotification("Multi-channel overlay", 
                    "Maximum 10 channels supported");
                return;
            }
            
            boolean expandRGB = expandRGBCheckbox.isSelected();
            dialog.close();
            createMultiChannelOverlay(new ArrayList<>(selected), expandRGB);
        });
        
        Button btnCancel = new Button("Cancel");
        btnCancel.setCancelButton(true);
        btnCancel.setOnAction(e -> dialog.close());
        
        ButtonBar buttonBar = new ButtonBar();
        buttonBar.getButtons().addAll(btnOverlay, btnCancel);
        
        mainPane.getChildren().addAll(instructions, listView, optionsPane, buttonBar);
        
        Scene scene = new Scene(mainPane, 500, 450);
        dialog.setScene(scene);
        dialog.show();
    }
    
    private void createMultiChannelOverlay(List<ImageChannelEntry> entries, boolean expandRGB) {
        try {
            // Load all image servers
            List<ImageServer<BufferedImage>> servers = new ArrayList<>();
            List<String> channelNames = new ArrayList<>();
            
            for (ImageChannelEntry entry : entries) {
                try {
                    ImageServer<BufferedImage> server = entry.getProjectEntry().readImageData().getServer();
                    servers.add(server);
                    channelNames.add(entry.getProjectEntry().getImageName());
                    logger.info("Loaded image: {}", entry.getProjectEntry().getImageName());
                } catch (IOException e) {
                    logger.error("Error loading image: " + entry.getProjectEntry().getImageName(), e);
                    Dialogs.showErrorMessage("Load error", 
                        "Failed to load: " + entry.getProjectEntry().getImageName());
                    return;
                }
            }
            
            if (servers.isEmpty()) {
                Dialogs.showErrorMessage("Multi-channel overlay", "No images could be loaded");
                return;
            }
            
            // Create multi-channel server with RGB expansion option
            MultiChannelOverlayServer overlayServer = new MultiChannelOverlayServer(servers, channelNames, expandRGB);
            
            // Create image data and display in viewer
            Platform.runLater(() -> {
                try {
                    ImageData<BufferedImage> imageData = new ImageData<>(overlayServer);
                    qupath.getViewer().setImageData(imageData);
                    
                    // Wait for UI to update, then configure channels
                    Platform.runLater(() -> {
                        try {
                            var display = qupath.getViewer().getImageDisplay();
                            var availableChannels = display.availableChannels();
                            
                            logger.info("Configuring {} channels for display", availableChannels.size());
                            
                            // Select all channels and ensure they are visible
                            for (var channel : availableChannels) {
                                logger.info("Selecting channel: {}", channel.getName());
                                display.setChannelSelected(channel, true);
                            }
                            
                            // Force update of the display
                            qupath.getViewer().repaint();
                            
                            logger.info("Multi-channel overlay created successfully with {} channels", servers.size());
                            Dialogs.showInfoNotification("Multi-channel overlay", 
                                "Successfully created overlay with " + servers.size() + " channels");
                                
                        } catch (Exception ex) {
                            logger.error("Error configuring channels", ex);
                        }
                    });
                        
                } catch (Exception ex) {
                    logger.error("Error creating multi-channel overlay", ex);
                    Dialogs.showErrorMessage("Multi-channel overlay", 
                        "Error creating overlay: " + ex.getMessage());
                }
            });
            
        } catch (Exception e) {
            logger.error("Error in multi-channel overlay", e);
            Dialogs.showErrorMessage("Multi-channel overlay", 
                "Unexpected error: " + e.getMessage());
        }
    }
    
    /**
     * Wrapper class for image entries in the list view
     */
    private static class ImageChannelEntry {
        private final ProjectImageEntry<BufferedImage> entry;
        
        public ImageChannelEntry(ProjectImageEntry<BufferedImage> entry) {
            this.entry = entry;
        }
        
        public ProjectImageEntry<BufferedImage> getProjectEntry() {
            return entry;
        }
        
        @Override
        public String toString() {
            return entry.getImageName();
        }
    }
}

