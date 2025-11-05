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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.interfaces.ROI;

/**
 * Navigator panel for searching and jumping to annotations in large images.
 * 
 * Features:
 * - Search annotations by name, classification, or ID
 * - Double-click to jump to annotation in viewer
 * - Auto-zoom and highlight
 * - Real-time filtering
 * 
 * @author HPath Team
 */
public class AnnotationNavigatorCommand {
    
    private static final Logger logger = LoggerFactory.getLogger(AnnotationNavigatorCommand.class);
    
    private QuPathGUI qupath;
    private Stage stage;
    private ListView<PathObject> annotationList;
    private TextField searchField;
    private FilteredList<PathObject> filteredAnnotations;
    private ObservableList<PathObject> allAnnotations;
    
    public AnnotationNavigatorCommand(QuPathGUI qupath) {
        this.qupath = qupath;
    }
    
    /**
     * Show the navigator window
     */
    public void show() {
        if (stage != null && stage.isShowing()) {
            stage.toFront();
            refreshAnnotations();
            return;
        }
        
        createUI();
        refreshAnnotations();
        stage.show();
    }
    
    /**
     * Create the UI
     */
    private void createUI() {
        stage = new Stage();
        stage.setTitle("Annotation Navigator");
        stage.initOwner(qupath.getStage());
        
        BorderPane mainPane = new BorderPane();
        mainPane.setPadding(new Insets(10));
        
        // Search field
        searchField = new TextField();
        searchField.setPromptText("🔍 Search annotations (name, class, ID)...");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            updateFilter(newVal);
        });
        
        // Annotation list
        allAnnotations = FXCollections.observableArrayList();
        filteredAnnotations = new FilteredList<>(allAnnotations);
        annotationList = new ListView<>(filteredAnnotations);
        
        // Custom cell factory for better display
        annotationList.setCellFactory(lv -> new ListCell<PathObject>() {
            @Override
            protected void updateItem(PathObject obj, boolean empty) {
                super.updateItem(obj, empty);
                
                if (empty || obj == null) {
                    setText(null);
                    setGraphic(null);
                    setTooltip(null);
                } else {
                    // Format: [Class] Name (measurements)
                    String className = obj.getPathClass() != null ? 
                        obj.getPathClass().toString() : "Unclassified";
                    String name = obj.getName() != null ? obj.getName() : "Unnamed";
                    
                    // Try to get custom IDs first, fallback to QuPath UUID
                    String customId = obj.retrieveMetadataValue("id") != null ? 
                        obj.retrieveMetadataValue("id").toString() : null;
                    String kId = obj.retrieveMetadataValue("kId") != null ? 
                        obj.retrieveMetadataValue("kId").toString() : null;
                    String qupathId = obj.getID() != null ? obj.getID().toString() : null;
                    
                    // Use custom ID if available, otherwise use QuPath ID
                    String displayId;
                    if (customId != null) {
                        displayId = customId.length() > 8 ? customId.substring(0, 8) : customId;
                    } else if (kId != null) {
                        displayId = kId.length() > 8 ? kId.substring(0, 8) : kId;
                    } else if (qupathId != null) {
                        displayId = qupathId.length() > 8 ? qupathId.substring(0, 8) : qupathId;
                    } else {
                        displayId = "no-id";
                    }
                    
                    // Add measurement info
                    int numMeasurements = obj.getMeasurementList().size();
                    String measurementInfo = numMeasurements > 0 ? 
                        String.format(" [%d measurements]", numMeasurements) : "";
                    
                    String text = String.format("[%s] %s%s\n   ID: %s...", 
                        className, name, measurementInfo, displayId);
                    setText(text);
                    
                    // Tooltip with more details
                    ROI roi = obj.getROI();
                    StringBuilder tooltipBuilder = new StringBuilder();
                    tooltipBuilder.append(String.format("Class: %s\nName: %s\n", className, name));
                    
                    // Show all available IDs
                    if (customId != null) {
                        tooltipBuilder.append(String.format("Custom ID: %s\n", customId));
                    }
                    if (kId != null) {
                        tooltipBuilder.append(String.format("kId: %s\n", kId));
                    }
                    if (qupathId != null) {
                        tooltipBuilder.append(String.format("QuPath UUID: %s\n", qupathId));
                    }
                    
                    tooltipBuilder.append(String.format(
                        "Centroid: (%.1f, %.1f)\nArea: %.2f µm²\n" +
                        "Measurements: %d\n\nDouble-click to jump to this annotation",
                        roi.getCentroidX(), roi.getCentroidY(),
                        roi.getScaledArea(1.0, 1.0),
                        numMeasurements
                    ));
                    
                    String tooltipText = tooltipBuilder.toString();
                    setTooltip(new Tooltip(tooltipText));
                    
                    // Color based on classification
                    if (obj.getPathClass() != null && obj.getColor() != null) {
                        Integer color = obj.getColor();
                        int r = (color >> 16) & 0xFF;
                        int g = (color >> 8) & 0xFF;
                        int b = color & 0xFF;
                        setStyle(String.format("-fx-text-fill: rgb(%d,%d,%d);", r, g, b));
                    } else {
                        setStyle("");
                    }
                }
            }
        });
        
        // Double-click to jump
        annotationList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                PathObject selected = annotationList.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    jumpToAnnotation(selected);
                }
            }
        });
        
        // Info label and refresh button
        Label infoLabel = new Label();
        infoLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            int filtered = filteredAnnotations.size();
            int total = allAnnotations.size();
            return String.format("Showing: %d / %d annotations", filtered, total);
        }, filteredAnnotations, allAnnotations));
        infoLabel.setStyle("-fx-font-size: 11px; -fx-opacity: 0.7;");
        
        // Refresh button
        javafx.scene.control.Button refreshButton = new javafx.scene.control.Button("🔄 Refresh");
        refreshButton.setTooltip(new Tooltip("Refresh annotation list"));
        refreshButton.setOnAction(e -> {
            refreshAnnotations();
            logger.info("Annotation list refreshed");
        });
        
        // Bottom panel with info and refresh button
        javafx.scene.layout.HBox bottomBox = new javafx.scene.layout.HBox(10);
        bottomBox.setPadding(new Insets(5, 0, 0, 0));
        bottomBox.getChildren().addAll(infoLabel, refreshButton);
        javafx.scene.layout.HBox.setHgrow(infoLabel, Priority.ALWAYS);
        
        // Layout
        VBox centerBox = new VBox(10);
        centerBox.getChildren().addAll(searchField, annotationList, bottomBox);
        VBox.setVgrow(annotationList, Priority.ALWAYS);
        
        mainPane.setCenter(centerBox);
        
        Scene scene = new Scene(mainPane, 500, 600);
        stage.setScene(scene);
        
        // Refresh when image changes
        qupath.imageDataProperty().addListener((obs, oldVal, newVal) -> {
            refreshAnnotations();
        });
    }
    
    /**
     * Refresh the annotation list
     */
    private void refreshAnnotations() {
        Platform.runLater(() -> {
            allAnnotations.clear();
            
            var imageData = qupath.getViewer().getImageData();
            if (imageData != null) {
                var annotations = imageData.getHierarchy().getAnnotationObjects();
                allAnnotations.addAll(annotations);
                logger.debug("Loaded {} annotations", annotations.size());
            }
        });
    }
    
    /**
     * Update filter based on search text
     */
    private void updateFilter(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            filteredAnnotations.setPredicate(null);
        } else {
            String lowerSearch = searchText.toLowerCase();
            filteredAnnotations.setPredicate(obj -> {
                // Search in name
                if (obj.getName() != null && obj.getName().toLowerCase().contains(lowerSearch)) {
                    return true;
                }
                
                // Search in classification
                if (obj.getPathClass() != null && 
                    obj.getPathClass().toString().toLowerCase().contains(lowerSearch)) {
                    return true;
                }
                
                // Search in QuPath internal ID (UUID)
                if (obj.getID() != null && 
                    obj.getID().toString().toLowerCase().contains(lowerSearch)) {
                    return true;
                }
                
                // Search in custom ID properties (from imported JSON)
                // Check for "id" property
                if (obj.retrieveMetadataValue("id") != null) {
                    String customId = obj.retrieveMetadataValue("id").toString().toLowerCase();
                    if (customId.contains(lowerSearch)) {
                        return true;
                    }
                }
                
                // Check for "kId" property  
                if (obj.retrieveMetadataValue("kId") != null) {
                    String kId = obj.retrieveMetadataValue("kId").toString().toLowerCase();
                    if (kId.contains(lowerSearch)) {
                        return true;
                    }
                }
                
                // Search in measurements
                for (var m : obj.getMeasurementList().getMeasurements()) {
                    if (m.getName().toLowerCase().contains(lowerSearch)) {
                        return true;
                    }
                }
                
                return false;
            });
        }
    }
    
    /**
     * Jump to annotation in viewer
     */
    private void jumpToAnnotation(PathObject annotation) {
        QuPathViewer viewer = qupath.getViewer();
        if (viewer == null) {
            logger.warn("No viewer available");
            return;
        }
        
        // Select the annotation
        viewer.getHierarchy().getSelectionModel().setSelectedObject(annotation);
        
        // Get ROI bounds
        ROI roi = annotation.getROI();
        if (roi == null) {
            return;
        }
        
        // Calculate center and zoom
        double cx = roi.getCentroidX();
        double cy = roi.getCentroidY();
        
        // Calculate appropriate zoom level based on ROI size
        double roiWidth = roi.getBoundsWidth();
        double roiHeight = roi.getBoundsHeight();
        double viewerWidth = viewer.getView().getWidth();
        double viewerHeight = viewer.getView().getHeight();
        
        // Zoom to fit the annotation with some padding
        double zoomX = viewerWidth / (roiWidth * 1.5);
        double zoomY = viewerHeight / (roiHeight * 1.5);
        double zoom = Math.min(zoomX, zoomY);
        
        // Limit zoom range
        zoom = Math.max(0.1, Math.min(zoom, 10.0));
        
        // Set viewer location and zoom
        viewer.setCenterPixelLocation(cx, cy);
        viewer.setDownsampleFactor(1.0 / zoom);
        
        logger.info("Jumped to annotation: {} at ({}, {}), zoom: {}", 
            annotation.getName(), cx, cy, zoom);
    }
}

