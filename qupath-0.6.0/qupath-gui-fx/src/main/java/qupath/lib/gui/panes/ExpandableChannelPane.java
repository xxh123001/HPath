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

package qupath.lib.gui.panes;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.viewer.QuPathViewer;

import java.util.*;

/**
 * Expandable channel pane that shows RGB channels in a collapsible tree structure.
 * This replaces the default flat channel list with a hierarchical view.
 */
public class ExpandableChannelPane extends VBox {
    
    private static final Logger logger = LoggerFactory.getLogger(ExpandableChannelPane.class);
    
    private QuPathViewer viewer;
    private ListView<ChannelRow> channelListView;
    private Map<String, ChannelGroup> channelGroups = new LinkedHashMap<>();
    private javafx.collections.ListChangeListener<ChannelDisplayInfo> channelSelectionListener;
    private boolean isUpdatingCheckboxes = false;
    
    public ExpandableChannelPane(QuPathViewer viewer) {
        this.viewer = viewer;
        this.setPadding(new Insets(3));
        this.setSpacing(3);
        
        // Title with All/None buttons
        Label titleLabel = new Label("Channel");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        
        Button btnSelectAll = new Button("All");
        btnSelectAll.setStyle("-fx-font-size: 9px; -fx-padding: 2 5 2 5;");
        btnSelectAll.setTooltip(new Tooltip("Show all channels"));
        btnSelectAll.setOnAction(e -> selectAllChannels());
        
        Button btnDeselectAll = new Button("None");
        btnDeselectAll.setStyle("-fx-font-size: 9px; -fx-padding: 2 5 2 5;");
        btnDeselectAll.setTooltip(new Tooltip("Hide all channels"));
        btnDeselectAll.setOnAction(e -> deselectAllChannels());
        
        HBox titleBox = new HBox(10, titleLabel, btnSelectAll, btnDeselectAll);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        titleBox.setPadding(new Insets(3, 0, 3, 0));
        
        // Single column layout with larger font
        channelListView = new ListView<>();
        channelListView.setCellFactory(param -> new ChannelRowCell());
        channelListView.setPrefHeight(400);
        channelListView.setStyle("-fx-fixed-cell-size: 32; -fx-font-size: 12px;"); // Larger row height and font
        VBox.setVgrow(channelListView, Priority.ALWAYS);
        
        this.getChildren().addAll(titleBox, channelListView);
        
        // Listen to image data changes
        viewer.imageDataProperty().addListener((obs, oldData, newData) -> {
            Platform.runLater(() -> {
                updateChannelList();
                setupChannelSelectionListener();
            });
        });
        
        updateChannelList();
        setupChannelSelectionListener();
    }
    
    /**
     * Setup listener for channel selection changes from left table
     */
    private void setupChannelSelectionListener() {
        // Remove old listener if exists
        if (channelSelectionListener != null && viewer.getImageDisplay() != null) {
            try {
                viewer.getImageDisplay().selectedChannels().removeListener(channelSelectionListener);
            } catch (Exception e) {
                // Ignore
            }
        }
        
        // Add new listener
        if (viewer.getImageDisplay() != null) {
            channelSelectionListener = c -> {
                if (!isUpdatingCheckboxes) {
                    logger.debug("Channel selection changed, updating ExpandableChannelPane checkboxes");
                    Platform.runLater(this::updateAllCheckboxStates);
                }
            };
            viewer.getImageDisplay().selectedChannels().addListener(channelSelectionListener);
            logger.debug("Channel selection listener added to ExpandableChannelPane");
        }
    }
    
    /**
     * Update all checkbox states to match current ImageDisplay selection
     */
    private void updateAllCheckboxStates() {
        isUpdatingCheckboxes = true;
        try {
            for (ChannelRow row : channelListView.getItems()) {
                row.updateCheckboxState();
            }
            for (ChannelGroup group : channelGroups.values()) {
                group.updateCheckboxStates();
            }
        } finally {
            isUpdatingCheckboxes = false;
        }
    }
    
    /**
     * Select all channels
     */
    private void selectAllChannels() {
        isUpdatingCheckboxes = true;
        try {
            ImageDisplay display = viewer.getImageDisplay();
            if (display != null) {
                // Handle all channels including those in groups
                for (ChannelGroup group : channelGroups.values()) {
                    for (ChannelDisplayInfo ch : group.channels) {
                        display.setChannelSelected(ch, true);
                    }
                }
                
                // Handle individual channels
                for (ChannelRow row : channelListView.getItems()) {
                    if (row.channel != null && !row.isParent && !row.isChild) {
                        display.setChannelSelected(row.channel, true);
                    }
                }
                
                // Update UI
                Platform.runLater(() -> {
                    updateAllCheckboxStates();
                    channelListView.refresh(); // Refresh to update colors
                });
                viewer.repaint();
            }
        } finally {
            isUpdatingCheckboxes = false;
        }
    }
    
    /**
     * Deselect all channels
     */
    private void deselectAllChannels() {
        isUpdatingCheckboxes = true;
        try {
            ImageDisplay display = viewer.getImageDisplay();
            if (display != null) {
                // Handle all channels including those in groups
                for (ChannelGroup group : channelGroups.values()) {
                    for (ChannelDisplayInfo ch : group.channels) {
                        display.setChannelSelected(ch, false);
                    }
                }
                
                // Handle individual channels
                for (ChannelRow row : channelListView.getItems()) {
                    if (row.channel != null && !row.isParent && !row.isChild) {
                        display.setChannelSelected(row.channel, false);
                    }
                }
                
                // Update UI
                Platform.runLater(() -> {
                    updateAllCheckboxStates();
                    channelListView.refresh(); // Refresh to update colors
                });
                viewer.repaint();
            }
        } finally {
            isUpdatingCheckboxes = false;
        }
    }
    
    private void updateChannelList() {
        channelListView.getItems().clear();
        channelGroups.clear();
        
        if (viewer == null || viewer.getImageDisplay() == null) {
            logger.info("Viewer or display is null, skipping channel list update");
            return;
        }
        
        ImageDisplay display = viewer.getImageDisplay();
        List<ChannelDisplayInfo> channels = display.availableChannels();
        logger.info("Updating channel list with {} channels", channels.size());
        
        // Group channels by base name
        Map<String, List<ChannelDisplayInfo>> grouped = groupChannels(channels);
        logger.info("Grouped into {} groups", grouped.size());
        
        // Create rows for single column
        for (Map.Entry<String, List<ChannelDisplayInfo>> entry : grouped.entrySet()) {
            String baseName = entry.getKey();
            List<ChannelDisplayInfo> channelList = entry.getValue();
            
            logger.info("Processing group '{}' with {} channels", baseName, channelList.size());
            if (channelList.size() >= 1) {
                for (ChannelDisplayInfo ch : channelList) {
                    logger.info("  - Channel: {}", ch.getName());
                }
            }
            
            if (channelList.size() == 3 && hasRGBChannels(channelList)) {
                // RGB group - create parent row
                logger.info("Creating RGB group for '{}'", baseName);
                ChannelGroup group = new ChannelGroup(baseName, channelList);
                channelGroups.put(baseName, group);
                channelListView.getItems().add(group.parentRow);
            } else {
                // Single channel
                logger.info("Adding {} as individual channel(s)", baseName);
                for (ChannelDisplayInfo ch : channelList) {
                    ChannelRow row = new ChannelRow(ch, false, null);
                    channelListView.getItems().add(row);
                }
            }
        }
        
        logger.info("Channel list updated: {} items", channelListView.getItems().size());
    }
    
    private Map<String, List<ChannelDisplayInfo>> groupChannels(List<ChannelDisplayInfo> channels) {
        Map<String, List<ChannelDisplayInfo>> grouped = new LinkedHashMap<>();
        
        for (ChannelDisplayInfo channel : channels) {
            String name = channel.getName();
            String baseName;
            
            // Extract base name for RGB channels
            // Channel names are like: "light.svs (Red) (C1)"
            if (name.contains(" (Red)")) {
                int idx = name.indexOf(" (Red)");
                baseName = name.substring(0, idx);
            } else if (name.contains(" (Green)")) {
                int idx = name.indexOf(" (Green)");
                baseName = name.substring(0, idx);
            } else if (name.contains(" (Blue)")) {
                int idx = name.indexOf(" (Blue)");
                baseName = name.substring(0, idx);
            } else {
                baseName = name;
            }
            
            grouped.computeIfAbsent(baseName, k -> new ArrayList<>()).add(channel);
        }
        
        return grouped;
    }
    
    private boolean hasRGBChannels(List<ChannelDisplayInfo> channels) {
        if (channels.size() != 3) return false;
        boolean hasRed = false, hasGreen = false, hasBlue = false;
        for (ChannelDisplayInfo ch : channels) {
            String name = ch.getName();
            if (name.contains(" (Red)")) hasRed = true;
            else if (name.contains(" (Green)")) hasGreen = true;
            else if (name.contains(" (Blue)")) hasBlue = true;
        }
        return hasRed && hasGreen && hasBlue;
    }
    
    /**
     * Represents a channel row in the list
     */
    class ChannelRow {
        ChannelDisplayInfo channel;
        String displayName; // Custom display name for parent rows
        boolean isParent;
        boolean isChild;
        ChannelGroup parentGroup; // For child rows - reference to parent group
        ChannelGroup ownedGroup;  // For parent rows - reference to the group this row owns
        BooleanProperty selectedProperty = new SimpleBooleanProperty(false);
        BooleanProperty expandedProperty = new SimpleBooleanProperty(false);
        
        ChannelRow(ChannelDisplayInfo channel, boolean isParent, ChannelGroup parentGroup) {
            this(channel, isParent, parentGroup, null);
        }
        
        ChannelRow(ChannelDisplayInfo channel, boolean isParent, ChannelGroup parentGroup, String customDisplayName) {
            this.channel = channel;
            this.isParent = isParent;
            this.isChild = (parentGroup != null);
            this.parentGroup = parentGroup;
            this.displayName = customDisplayName != null ? customDisplayName : (channel != null ? channel.getName() : "");
            
            // Update selection state
            updateSelectionState();
        }
        
        String getDisplayName() {
            return displayName;
        }
        
        void updateSelectionState() {
            ImageDisplay display = viewer.getImageDisplay();
            
            if (isParent && ownedGroup != null) {
                // For parent rows, check if ANY child channel is selected
                boolean anySelected = false;
                for (ChannelDisplayInfo ch : ownedGroup.channels) {
                    if (display.selectedChannels().contains(ch)) {
                        anySelected = true;
                        break;
                    }
                }
                selectedProperty.set(anySelected);
            } else if (channel != null) {
                // For regular channels, check if this channel is selected
                selectedProperty.set(display.selectedChannels().contains(channel));
            }
        }
        
        /**
         * Update checkbox state to match ImageDisplay (called from listener)
         */
        void updateCheckboxState() {
            updateSelectionState();
        }
    }
    
    /**
     * Channel group for RGB images
     */
    class ChannelGroup {
        String name;
        List<ChannelDisplayInfo> channels;
        ChannelRow parentRow;
        List<ChannelRow> childRows;
        BooleanProperty expandedProperty = new SimpleBooleanProperty(false);
        
        ChannelGroup(String name, List<ChannelDisplayInfo> channels) {
            this.name = name;
            this.channels = channels;
            this.childRows = new ArrayList<>();
            
            // Create parent row with custom display name (base name without "(Red)" etc.)
            // Use first channel as representative for color and data
            this.parentRow = new ChannelRow(channels.get(0), true, null, name);
            this.parentRow.expandedProperty = this.expandedProperty;
            this.parentRow.ownedGroup = this; // Set reference so parent can check child selection states
            this.parentRow.updateSelectionState(); // Update state after setting ownedGroup
            
            logger.info("Created parent row for '{}' with display name: {}", name, this.parentRow.getDisplayName());
            
            // Create child rows
            for (ChannelDisplayInfo ch : channels) {
                ChannelRow childRow = new ChannelRow(ch, false, this);
                childRows.add(childRow);
                logger.info("  Added child row: {}", ch.getName());
            }
            
            // Listen to expansion changes
            expandedProperty.addListener((obs, wasExpanded, isExpanded) -> {
                // Find parent row in the list
                int parentIndex = channelListView.getItems().indexOf(parentRow);
                
                if (parentIndex >= 0) {
                    if (isExpanded) {
                        // Add child rows after parent
                        logger.info("Expanding group '{}', adding {} child rows", name, childRows.size());
                        channelListView.getItems().addAll(parentIndex + 1, childRows);
                    } else {
                        // Remove child rows
                        logger.info("Collapsing group '{}'", name);
                        channelListView.getItems().removeAll(childRows);
                    }
                }
            });
        }
        
        void toggleAllChannels(boolean selected) {
            isUpdatingCheckboxes = true;
            try {
                ImageDisplay display = viewer.getImageDisplay();
                for (ChannelDisplayInfo ch : channels) {
                    display.setChannelSelected(ch, selected);
                }
                // Update UI
                parentRow.updateSelectionState();
                for (ChannelRow child : childRows) {
                    child.updateSelectionState();
                }
            } finally {
                isUpdatingCheckboxes = false;
            }
        }
        
        boolean areAllSelected() {
            ImageDisplay display = viewer.getImageDisplay();
            for (ChannelDisplayInfo ch : channels) {
                if (!display.selectedChannels().contains(ch)) {
                    return false;
                }
            }
            return true;
        }
        
        /**
         * Update checkbox states to match current ImageDisplay selection
         */
        void updateCheckboxStates() {
            parentRow.updateCheckboxState();
            for (ChannelRow child : childRows) {
                child.updateCheckboxState();
            }
        }
    }
    
    /**
     * Custom cell for rendering channel rows
     */
    class ChannelRowCell extends ListCell<ChannelRow> {
        private HBox container = new HBox(5); // Larger spacing
        private Button expandButton = new Button("▶");
        private CheckBox checkBox = new CheckBox();
        private Label nameLabel = new Label();
        private Rectangle colorRect = new Rectangle(20, 16); // Larger color indicator
        
        ChannelRowCell() {
            container.setAlignment(Pos.CENTER_LEFT);
            container.setPadding(new Insets(3)); // More padding
            // Larger button
            expandButton.setStyle("-fx-font-size: 11px; -fx-padding: 2 5 2 5; -fx-min-width: 24px; -fx-min-height: 24px;");
            // Larger checkbox
            checkBox.setStyle("-fx-padding: 2;");
            // Larger label
            nameLabel.setStyle("-fx-font-size: 12px;");
            colorRect.setStroke(Color.GRAY);
            colorRect.setStrokeWidth(0.8);
            
            // Expand button action
            expandButton.setOnAction(e -> {
                ChannelRow row = getItem();
                if (row != null && row.isParent) {
                    row.expandedProperty.set(!row.expandedProperty.get());
                    expandButton.setText(row.expandedProperty.get() ? "▼" : "▶");
                }
                e.consume();
            });
            
            // Checkbox action
            checkBox.setOnAction(e -> {
                ChannelRow row = getItem();
                if (row != null && !isUpdatingCheckboxes) {
                    isUpdatingCheckboxes = true;
                    try {
                        if (row.isParent) {
                            // Toggle all channels in group using display name
                            String baseName = row.getDisplayName();
                            ChannelGroup group = channelGroups.get(baseName);
                            if (group != null) {
                                logger.info("Toggling all channels in group '{}' to: {}", baseName, checkBox.isSelected());
                                group.toggleAllChannels(checkBox.isSelected());
                                // Refresh to update parent row color
                                Platform.runLater(() -> channelListView.refresh());
                            } else {
                                logger.warn("Could not find channel group for base name: {}", baseName);
                            }
                        } else {
                            // Toggle single channel
                            ImageDisplay display = viewer.getImageDisplay();
                            display.setChannelSelected(row.channel, checkBox.isSelected());
                            row.updateSelectionState();
                            
                            // Update parent if this is a child
                            if (row.isChild && row.parentGroup != null) {
                                row.parentGroup.parentRow.updateSelectionState();
                                // Refresh to update parent row color
                                Platform.runLater(() -> channelListView.refresh());
                            }
                        }
                        viewer.repaint();
                    } finally {
                        isUpdatingCheckboxes = false;
                    }
                }
            });
            
            // Color rect double-click to change color
            colorRect.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                    ChannelRow row = getItem();
                    if (row != null && row.channel != null) {
                        showColorPicker(row.channel);
                    }
                }
            });
        }
        
        @Override
        protected void updateItem(ChannelRow row, boolean empty) {
            super.updateItem(row, empty);
            
            if (empty || row == null) {
                setGraphic(null);
                return;
            }
            
            container.getChildren().clear();
            
            // Indentation for child rows - larger for better visibility
            if (row.isChild) {
                Region spacer = new Region();
                spacer.setMinWidth(25);
                container.getChildren().add(spacer);
            }
            
            // Expand button (only for parent rows)
            if (row.isParent) {
                expandButton.setText(row.expandedProperty.get() ? "▼" : "▶");
                container.getChildren().add(expandButton);
            } else if (!row.isChild) {
                // Spacer for non-expandable, non-child rows
                Region spacer = new Region();
                spacer.setMinWidth(30);
                container.getChildren().add(spacer);
            }
            
            // Checkbox - set state directly from row (no bidirectional binding to avoid loops)
            checkBox.setSelected(row.selectedProperty.get());
            container.getChildren().add(checkBox);
            
            // Channel name
            String displayName;
            if (row.isChild) {
                // Show only color name for children (Red, Green, Blue)
                String fullName = row.channel.getName();
                if (fullName.contains(" (Red)")) {
                    displayName = "Red";
                } else if (fullName.contains(" (Green)")) {
                    displayName = "Green";
                } else if (fullName.contains(" (Blue)")) {
                    displayName = "Blue";
                } else {
                    displayName = row.getDisplayName();
                }
            } else {
                // Use custom display name (for parents) or full name (for individual channels)
                displayName = row.getDisplayName();
            }
            nameLabel.setText(displayName);
            container.getChildren().add(nameLabel);
            
            // Color indicator
            updateColorRect(row.channel);
            container.getChildren().add(colorRect);
            
            setGraphic(container);
        }
        
        private void updateColorRect(ChannelDisplayInfo channel) {
            ChannelRow row = getItem();
            
            // For parent RGB rows, show mixed color based on selected children
            if (row != null && row.isParent) {
                String baseName = row.getDisplayName();
                ChannelGroup group = channelGroups.get(baseName);
                if (group != null) {
                    int r = 0, g = 0, b = 0;
                    ImageDisplay display = viewer.getImageDisplay();
                    
                    // Mix colors of selected channels
                    for (ChannelDisplayInfo ch : group.channels) {
                        if (display.selectedChannels().contains(ch)) {
                            Integer colorValue = ch.getColor();
                            if (colorValue != null) {
                                r += (colorValue >> 16) & 0xFF;
                                g += (colorValue >> 8) & 0xFF;
                                b += colorValue & 0xFF;
                            }
                        }
                    }
                    
                    // Clamp values to 255
                    r = Math.min(255, r);
                    g = Math.min(255, g);
                    b = Math.min(255, b);
                    
                    colorRect.setFill(Color.rgb(r, g, b));
                    return;
                }
            }
            
            // For regular channels, use their own color
            if (channel != null) {
            Integer colorValue = channel.getColor();
            if (colorValue != null) {
                int r = (colorValue >> 16) & 0xFF;
                int g = (colorValue >> 8) & 0xFF;
                int b = colorValue & 0xFF;
                colorRect.setFill(Color.rgb(r, g, b));
                }
            }
        }
        
        private void showColorPicker(ChannelDisplayInfo channel) {
            if (!(channel instanceof qupath.lib.display.DirectServerChannelInfo)) {
                logger.warn("Color picker only supported for DirectServerChannelInfo");
                return;
            }
            
            qupath.lib.display.DirectServerChannelInfo directChannel = 
                (qupath.lib.display.DirectServerChannelInfo) channel;
            
            Color currentColor = qupath.lib.gui.tools.ColorToolsFX.getCachedColor(channel.getColor());
            Dialog<Color> dialog = new Dialog<>();
            dialog.setTitle("Choose channel color");
            dialog.setHeaderText("Select a new color for channel: " + channel.getName());
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            
            ColorPicker colorPicker = new ColorPicker(currentColor);
            colorPicker.setPrefWidth(200);
            dialog.getDialogPane().setContent(colorPicker);
            
            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == ButtonType.OK) {
                    return colorPicker.getValue();
                }
                return null;
            });
            
            java.util.Optional<Color> result = dialog.showAndWait();
            result.ifPresent(newColor -> {
                // Update channel color using the DirectServerChannelInfo method
                directChannel.setLUTColor(
                    (int)(newColor.getRed() * 255),
                    (int)(newColor.getGreen() * 255),
                    (int)(newColor.getBlue() * 255)
                );
                
                // Update metadata if we have image data
                var imageData = viewer.getImageData();
                if (imageData != null) {
                    var server = imageData.getServer();
                    int channelIndex = directChannel.getChannel();
                    var metadata = server.getMetadata();
                    var channels = new java.util.ArrayList<>(metadata.getChannels());
                    channels.set(channelIndex, 
                        qupath.lib.images.servers.ImageChannel.getInstance(
                            channel.getName(), 
                            qupath.lib.gui.tools.ColorToolsFX.getRGB(newColor)
                        )
                    );
                    var metadata2 = new qupath.lib.images.servers.ImageServerMetadata.Builder(metadata)
                            .channels(channels)
                            .build();
                    imageData.updateServerMetadata(metadata2);
                    
                    // Save color properties
                    var display = viewer.getImageDisplay();
                    if (display != null) {
                        display.saveChannelColorProperties();
                    }
                }
                
                viewer.repaint();
                // Update the UI
                updateColorRect(channel);
                Platform.runLater(() -> {
                    channelListView.refresh();
                });
            });
        }
    }
}

