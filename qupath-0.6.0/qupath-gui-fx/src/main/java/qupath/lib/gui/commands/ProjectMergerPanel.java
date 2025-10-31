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
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathObject;

/**
 * Panel for importing images and objects separately, then merging them.
 * 
 * Features:
 * - Import images (left panel)
 * - Import JSON/GeoJSON objects (right panel)
 * - Search with Ctrl+F in both panels
 * - Checkbox selection
 * - Merge selected image + objects
 * - Display merged results in bottom panel
 * 
 * @author HPath Team
 */
public class ProjectMergerPanel {
    
    private static final Logger logger = LoggerFactory.getLogger(ProjectMergerPanel.class);
    
    private QuPathGUI qupath;
    private Stage stage;
    
    // Image list (left)
    private ObservableList<ImageEntry> imageEntries = FXCollections.observableArrayList();
    private FilteredList<ImageEntry> filteredImages;
    private ListView<ImageEntry> imageListView;
    private TextField imageSearchField;
    
    // Object list (right)
    private ObservableList<ObjectEntry> objectEntries = FXCollections.observableArrayList();
    private FilteredList<ObjectEntry> filteredObjects;
    private ListView<ObjectEntry> objectListView;
    private TextField objectSearchField;
    
    // Merged results (bottom)
    private ObservableList<MergedEntry> mergedEntries = FXCollections.observableArrayList();
    private ListView<MergedEntry> mergedListView;
    
    public ProjectMergerPanel(QuPathGUI qupath) {
        this.qupath = qupath;
        createUI();
    }
    
    /**
     * Show the panel
     */
    public void show() {
        if (stage != null && stage.isShowing()) {
            stage.toFront();
            return;
        }
        stage.show();
    }
    
    /**
     * Create the UI
     */
    private void createUI() {
        stage = new Stage();
        stage.setTitle("Project Merger");
        stage.initOwner(qupath.getStage());
        
        BorderPane mainPane = new BorderPane();
        mainPane.setPadding(new Insets(10));
        
        // ===== Top section: Images and Objects =====
        HBox topSection = new HBox(10);
        
        // Left: Image import
        VBox imageBox = createImagePanel();
        
        // Right: Object import
        VBox objectBox = createObjectPanel();
        
        topSection.getChildren().addAll(imageBox, objectBox);
        HBox.setHgrow(imageBox, Priority.ALWAYS);
        HBox.setHgrow(objectBox, Priority.ALWAYS);
        
        // ===== Middle: Merge button =====
        Button mergeButton = new Button("⬇ Merge Selected");
        mergeButton.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        mergeButton.setMaxWidth(Double.MAX_VALUE);
        mergeButton.setOnAction(e -> mergeSelected());
        
        HBox mergeBox = new HBox(mergeButton);
        mergeBox.setPadding(new Insets(10, 0, 10, 0));
        HBox.setHgrow(mergeButton, Priority.ALWAYS);
        
        // ===== Bottom: Merged results =====
        VBox bottomSection = createMergedPanel();
        
        // ===== Layout =====
        VBox centerBox = new VBox(10);
        centerBox.getChildren().addAll(topSection, mergeBox, bottomSection);
        VBox.setVgrow(topSection, Priority.ALWAYS);
        VBox.setVgrow(bottomSection, Priority.ALWAYS);
        
        mainPane.setCenter(centerBox);
        
        Scene scene = new Scene(mainPane, 1000, 800);
        stage.setScene(scene);
    }
    
    /**
     * Create image import panel (left)
     */
    private VBox createImagePanel() {
        VBox box = new VBox(10);
        box.setStyle("-fx-border-color: lightgray; -fx-border-width: 1; -fx-padding: 10;");
        
        // Title
        Label titleLabel = new Label("📷 Images");
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        
        // Import button
        Button importButton = new Button("Import Images...");
        importButton.setMaxWidth(Double.MAX_VALUE);
        importButton.setOnAction(e -> importImages());
        
        // Search field
        imageSearchField = new TextField();
        imageSearchField.setPromptText("🔍 Search images (Ctrl+F)...");
        
        // List view with checkboxes
        filteredImages = new FilteredList<>(imageEntries);
        imageListView = new ListView<>(filteredImages);
        imageListView.setCellFactory(lv -> {
            CheckBoxListCell<ImageEntry> cell = new CheckBoxListCell<>(ImageEntry::selectedProperty);
            return cell;
        });
        imageListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        
        // Search functionality
        imageSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            updateImageFilter(newVal);
        });
        
        // Ctrl+F to focus search
        imageListView.setOnKeyPressed(e -> {
            if (e.isShortcutDown() && e.getCode() == javafx.scene.input.KeyCode.F) {
                imageSearchField.requestFocus();
                e.consume();
            }
        });
        
        // Select all / Clear buttons
        Button selectAllImages = new Button("Select All");
        selectAllImages.setOnAction(e -> {
            filteredImages.forEach(img -> img.setSelected(true));
        });
        
        Button clearImages = new Button("Clear");
        clearImages.setOnAction(e -> {
            filteredImages.forEach(img -> img.setSelected(false));
        });
        
        HBox buttonBox = new HBox(5, selectAllImages, clearImages);
        
        // Layout
        box.getChildren().addAll(
            titleLabel,
            importButton,
            imageSearchField,
            imageListView,
            buttonBox
        );
        VBox.setVgrow(imageListView, Priority.ALWAYS);
        
        return box;
    }
    
    /**
     * Create object import panel (right)
     */
    private VBox createObjectPanel() {
        VBox box = new VBox(10);
        box.setStyle("-fx-border-color: lightgray; -fx-border-width: 1; -fx-padding: 10;");
        
        // Title
        Label titleLabel = new Label("📝 Objects (JSON/GeoJSON)");
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        
        // Import button
        Button importButton = new Button("Import Objects...");
        importButton.setMaxWidth(Double.MAX_VALUE);
        importButton.setOnAction(e -> importObjects());
        
        // Search field
        objectSearchField = new TextField();
        objectSearchField.setPromptText("🔍 Search objects (Ctrl+F)...");
        
        // List view with checkboxes
        filteredObjects = new FilteredList<>(objectEntries);
        objectListView = new ListView<>(filteredObjects);
        objectListView.setCellFactory(lv -> {
            CheckBoxListCell<ObjectEntry> cell = new CheckBoxListCell<>(ObjectEntry::selectedProperty);
            return cell;
        });
        objectListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        
        // Search functionality
        objectSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            updateObjectFilter(newVal);
        });
        
        // Ctrl+F to focus search
        objectListView.setOnKeyPressed(e -> {
            if (e.isShortcutDown() && e.getCode() == javafx.scene.input.KeyCode.F) {
                objectSearchField.requestFocus();
                e.consume();
            }
        });
        
        // Select all / Clear buttons
        Button selectAllObjects = new Button("Select All");
        selectAllObjects.setOnAction(e -> {
            filteredObjects.forEach(obj -> obj.setSelected(true));
        });
        
        Button clearObjects = new Button("Clear");
        clearObjects.setOnAction(e -> {
            filteredObjects.forEach(obj -> obj.setSelected(false));
        });
        
        HBox buttonBox = new HBox(5, selectAllObjects, clearObjects);
        
        // Layout
        box.getChildren().addAll(
            titleLabel,
            importButton,
            objectSearchField,
            objectListView,
            buttonBox
        );
        VBox.setVgrow(objectListView, Priority.ALWAYS);
        
        return box;
    }
    
    /**
     * Create merged results panel (bottom)
     */
    private VBox createMergedPanel() {
        VBox box = new VBox(10);
        box.setStyle("-fx-border-color: lightgray; -fx-border-width: 1; -fx-padding: 10;");
        
        // Title
        Label titleLabel = new Label("📊 Merged Results");
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        
        // Merged list
        mergedListView = new ListView<>(mergedEntries);
        mergedListView.setCellFactory(lv -> new javafx.scene.control.ListCell<MergedEntry>() {
            @Override
            protected void updateItem(MergedEntry entry, boolean empty) {
                super.updateItem(entry, empty);
                if (empty || entry == null) {
                    setText(null);
                } else {
                    setText(String.format("%s + %s (%d objects)", 
                        entry.imageName, entry.objectFileName, entry.objectCount));
                }
            }
        });
        
        // Double-click to open
        mergedListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                MergedEntry selected = mergedListView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    openMergedEntry(selected);
                }
            }
        });
        
        // Buttons
        Button openButton = new Button("Open Selected");
        openButton.setOnAction(e -> {
            MergedEntry selected = mergedListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                openMergedEntry(selected);
            }
        });
        
        Button removeButton = new Button("Remove");
        removeButton.setOnAction(e -> {
            MergedEntry selected = mergedListView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                mergedEntries.remove(selected);
            }
        });
        
        Button clearAllButton = new Button("Clear All");
        clearAllButton.setOnAction(e -> {
            mergedEntries.clear();
        });
        
        HBox buttonBox = new HBox(5, openButton, removeButton, clearAllButton);
        
        // Layout
        box.getChildren().addAll(titleLabel, mergedListView, buttonBox);
        VBox.setVgrow(mergedListView, Priority.ALWAYS);
        
        return box;
    }
    
    /**
     * Import images
     */
    private void importImages() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Images");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Image Files", "*.svs", "*.tif", "*.tiff", "*.ndpi", "*.vsi", "*.mrxs", "*.scn"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        
        List<File> files = fileChooser.showOpenMultipleDialog(stage);
        if (files != null && !files.isEmpty()) {
            for (File file : files) {
                ImageEntry entry = new ImageEntry(file);
                imageEntries.add(entry);
                logger.info("Added image: {}", file.getName());
            }
        }
    }
    
    /**
     * Import objects (JSON/GeoJSON)
     */
    private void importObjects() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Objects");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("JSON/GeoJSON Files", "*.json", "*.geojson"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        
        List<File> files = fileChooser.showOpenMultipleDialog(stage);
        if (files != null && !files.isEmpty()) {
            for (File file : files) {
                try {
                    ObjectEntry entry = new ObjectEntry(file);
                    objectEntries.add(entry);
                    logger.info("Added object file: {} ({} objects)", file.getName(), entry.getObjectCount());
                } catch (Exception e) {
                    logger.error("Failed to load object file: {}", file.getName(), e);
                    Dialogs.showErrorMessage("Import Error", 
                        "Failed to load " + file.getName() + ": " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Merge selected images and objects
     */
    private void mergeSelected() {
        // Get selected images
        List<ImageEntry> selectedImages = imageEntries.stream()
            .filter(ImageEntry::isSelected)
            .toList();
        
        // Get selected objects
        List<ObjectEntry> selectedObjects = objectEntries.stream()
            .filter(ObjectEntry::isSelected)
            .toList();
        
        if (selectedImages.isEmpty()) {
            Dialogs.showErrorMessage("Merge", "Please select at least one image!");
            return;
        }
        
        if (selectedObjects.isEmpty()) {
            Dialogs.showErrorMessage("Merge", "Please select at least one object file!");
            return;
        }
        
        // Create merged entries
        for (ImageEntry image : selectedImages) {
            for (ObjectEntry obj : selectedObjects) {
                MergedEntry merged = new MergedEntry(image, obj);
                mergedEntries.add(merged);
                logger.info("Merged: {} + {}", image.file.getName(), obj.file.getName());
            }
        }
        
        Dialogs.showInfoNotification("Merge Complete", 
            String.format("Created %d merged entries", 
                selectedImages.size() * selectedObjects.size()));
    }
    
    /**
     * Open merged entry in viewer
     */
    private void openMergedEntry(MergedEntry entry) {
        try {
            // Open image
            qupath.openImage(qupath.getViewer(), entry.imageEntry.file.getAbsolutePath(), true, true);
            
            // Wait for image to load
            Thread.sleep(500);
            
            // Import objects
            var imageData = qupath.getViewer().getImageData();
            if (imageData != null) {
                var hierarchy = imageData.getHierarchy();
                hierarchy.addObjects(entry.objectEntry.objects);
                hierarchy.fireHierarchyChangedEvent(this);
                
                logger.info("Opened merged entry: {} with {} objects", 
                    entry.imageName, entry.objectCount);
                
                Dialogs.showInfoNotification("Opened", 
                    String.format("Loaded %s with %d objects", entry.imageName, entry.objectCount));
            }
        } catch (Exception e) {
            logger.error("Failed to open merged entry", e);
            Dialogs.showErrorMessage("Open Error", "Failed to open: " + e.getMessage());
        }
    }
    
    /**
     * Update image filter
     */
    private void updateImageFilter(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            filteredImages.setPredicate(null);
        } else {
            String lower = searchText.toLowerCase();
            filteredImages.setPredicate(img -> 
                img.file.getName().toLowerCase().contains(lower)
            );
        }
    }
    
    /**
     * Update object filter
     */
    private void updateObjectFilter(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            filteredObjects.setPredicate(null);
        } else {
            String lower = searchText.toLowerCase();
            filteredObjects.setPredicate(obj -> 
                obj.file.getName().toLowerCase().contains(lower)
            );
        }
    }
    
    /**
     * Image entry with checkbox
     */
    static class ImageEntry {
        final File file;
        private javafx.beans.property.BooleanProperty selected = 
            new javafx.beans.property.SimpleBooleanProperty(false);
        
        ImageEntry(File file) {
            this.file = file;
        }
        
        javafx.beans.property.BooleanProperty selectedProperty() {
            return selected;
        }
        
        boolean isSelected() {
            return selected.get();
        }
        
        void setSelected(boolean value) {
            selected.set(value);
        }
        
        @Override
        public String toString() {
            return file.getName();
        }
    }
    
    /**
     * Object entry with checkbox
     */
    static class ObjectEntry {
        final File file;
        final List<PathObject> objects;
        private javafx.beans.property.BooleanProperty selected = 
            new javafx.beans.property.SimpleBooleanProperty(false);
        
        ObjectEntry(File file) throws IOException {
            this.file = file;
            this.objects = loadObjects(file);
        }
        
        private List<PathObject> loadObjects(File file) throws IOException {
            String json = Files.readString(file.toPath());
            JsonElement element = GsonTools.getInstance().fromJson(json, JsonElement.class);
            return GsonTools.parseObjectsFromGeoJSON(element);
        }
        
        int getObjectCount() {
            return objects.size();
        }
        
        javafx.beans.property.BooleanProperty selectedProperty() {
            return selected;
        }
        
        boolean isSelected() {
            return selected.get();
        }
        
        void setSelected(boolean value) {
            selected.set(value);
        }
        
        @Override
        public String toString() {
            return String.format("%s (%d objects)", file.getName(), objects.size());
        }
    }
    
    /**
     * Merged entry
     */
    static class MergedEntry {
        final ImageEntry imageEntry;
        final ObjectEntry objectEntry;
        final String imageName;
        final String objectFileName;
        final int objectCount;
        
        MergedEntry(ImageEntry image, ObjectEntry object) {
            this.imageEntry = image;
            this.objectEntry = object;
            this.imageName = image.file.getName();
            this.objectFileName = object.file.getName();
            this.objectCount = object.getObjectCount();
        }
    }
}

