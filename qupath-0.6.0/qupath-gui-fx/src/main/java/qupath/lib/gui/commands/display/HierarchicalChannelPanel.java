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

package qupath.lib.gui.commands.display;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.display.ChannelDisplayInfo;

import java.util.*;

/**
 * A hierarchical channel panel that shows RGB channels in a tree structure.
 * - RGB images show as expandable parent items
 * - Click arrow to expand/collapse R, G, B sub-channels
 * - Click parent checkbox to select/deselect all sub-channels
 */
public class HierarchicalChannelPanel extends VBox {
    
    private static final Logger logger = LoggerFactory.getLogger(HierarchicalChannelPanel.class);
    
    private QuPathViewer viewer;
    private Map<String, ChannelGroup> channelGroups = new LinkedHashMap<>();
    private VBox channelListContainer;
    private javafx.collections.ListChangeListener<ChannelDisplayInfo> channelSelectionListener;
    private boolean isUpdatingCheckboxes = false; // Prevent recursive updates
    
    public HierarchicalChannelPanel(QuPathViewer viewer) {
        this.viewer = viewer;
        this.setPadding(new Insets(5));
        this.setSpacing(2);
        
        Label title = new Label("Channels (Hierarchical View)");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        
        channelListContainer = new VBox(2);
        ScrollPane scrollPane = new ScrollPane(channelListContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(400);
        
        this.getChildren().addAll(title, scrollPane);
        
        updateChannelList();
        
        // Listen to imageData changes to update listener
        viewer.imageDataProperty().addListener((obs, oldData, newData) -> {
            updateChannelList();
            setupChannelListener();
        });
        
        // Setup initial listener
        setupChannelListener();
    }
    
    /**
     * Setup listener for channel selection changes
     */
    private void setupChannelListener() {
        // Remove old listener if exists
        if (channelSelectionListener != null && viewer.getImageDisplay() != null) {
            try {
                viewer.getImageDisplay().selectedChannels().removeListener(channelSelectionListener);
            } catch (Exception e) {
                // Ignore if listener wasn't added
            }
        }
        
        // Create and add new listener
        if (viewer.getImageDisplay() != null) {
            channelSelectionListener = c -> {
                // Update all checkbox states when selection changes
                if (!isUpdatingCheckboxes) {
                    logger.debug("Channel selection changed, updating right panel checkboxes");
                    javafx.application.Platform.runLater(() -> updateAllCheckboxStates());
                }
            };
            viewer.getImageDisplay().selectedChannels().addListener(channelSelectionListener);
            logger.debug("Channel selection listener added to HierarchicalChannelPanel");
        }
    }
    
    public void updateChannelList() {
        channelListContainer.getChildren().clear();
        channelGroups.clear();
        
        if (viewer == null || viewer.getImageDisplay() == null) {
            return;
        }
        
        var display = viewer.getImageDisplay();
        var channels = display.availableChannels();
        
        // Group channels by base name
        Map<String, List<ChannelInfo>> groupedChannels = new LinkedHashMap<>();
        
        for (var channel : channels) {
            String name = channel.getName();
            String baseName;
            String subType = null;
            
            // Check if this is an RGB sub-channel
            if (name.endsWith(" (Red)")) {
                baseName = name.substring(0, name.length() - 6);
                subType = "Red";
            } else if (name.endsWith(" (Green)")) {
                baseName = name.substring(0, name.length() - 8);
                subType = "Green";
            } else if (name.endsWith(" (Blue)")) {
                baseName = name.substring(0, name.length() - 7);
                subType = "Blue";
            } else {
                baseName = name;
            }
            
            groupedChannels.computeIfAbsent(baseName, k -> new ArrayList<>());
            groupedChannels.get(baseName).add(new ChannelInfo(channel, subType));
        }
        
        // Create UI for each group
        for (var entry : groupedChannels.entrySet()) {
            String groupName = entry.getKey();
            List<ChannelInfo> channelInfos = entry.getValue();
            
            if (channelInfos.size() == 3 && hasRGB(channelInfos)) {
                // RGB group - create expandable tree
                ChannelGroup group = new ChannelGroup(groupName, channelInfos, true);
                channelGroups.put(groupName, group);
                channelListContainer.getChildren().add(group.getRootNode());
            } else {
                // Single channel or non-RGB
                for (ChannelInfo info : channelInfos) {
                    ChannelGroup group = new ChannelGroup(groupName, Collections.singletonList(info), false);
                    channelGroups.put(groupName, group);
                    channelListContainer.getChildren().add(group.getRootNode());
                }
            }
        }
    }
    
    private boolean hasRGB(List<ChannelInfo> channels) {
        boolean hasRed = false, hasGreen = false, hasBlue = false;
        for (ChannelInfo info : channels) {
            if ("Red".equals(info.subType)) hasRed = true;
            else if ("Green".equals(info.subType)) hasGreen = true;
            else if ("Blue".equals(info.subType)) hasBlue = true;
        }
        return hasRed && hasGreen && hasBlue;
    }
    
    /**
     * Update all checkbox states to match the current ImageDisplay selection
     */
    private void updateAllCheckboxStates() {
        for (ChannelGroup group : channelGroups.values()) {
            group.refreshCheckboxStates();
        }
    }
    
    // Helper class to store channel information
    private static class ChannelInfo {
        ChannelDisplayInfo channel;
        String subType; // "Red", "Green", "Blue", or null
        
        ChannelInfo(ChannelDisplayInfo channel, String subType) {
            this.channel = channel;
            this.subType = subType;
        }
    }
    
    // Channel group with expand/collapse functionality
    private class ChannelGroup {
        private String groupName;
        private List<ChannelInfo> channels;
        private boolean isRGB;
        private VBox rootNode;
        private boolean expanded = false;
        private Button expandButton;
        private CheckBox groupCheckbox;
        private VBox childrenContainer;
        
        ChannelGroup(String groupName, List<ChannelInfo> channels, boolean isRGB) {
            this.groupName = groupName;
            this.channels = channels;
            this.isRGB = isRGB;
            createUI();
        }
        
        private void createUI() {
            rootNode = new VBox(0);
            
            // Parent row
            HBox parentRow = new HBox(5);
            parentRow.setAlignment(Pos.CENTER_LEFT);
            parentRow.setPadding(new Insets(2));
            
            if (isRGB) {
                // Expand/collapse button
                expandButton = new Button("▶");
                expandButton.setStyle("-fx-font-size: 10px; -fx-padding: 0 5 0 5;");
                expandButton.setOnAction(e -> toggleExpand());
                
                // Group checkbox
                groupCheckbox = new CheckBox(groupName);
                groupCheckbox.setOnAction(e -> {
                    if (!isUpdatingCheckboxes) {
                        isUpdatingCheckboxes = true;
                        try {
                            boolean selected = groupCheckbox.isSelected();
                            selectAllChannels(selected);
                        } finally {
                            isUpdatingCheckboxes = false;
                        }
                    }
                });
                
                // Color indicator (show mixed color)
                Rectangle colorRect = new Rectangle(20, 15);
                colorRect.setFill(Color.WHITE);
                colorRect.setStroke(Color.GRAY);
                
                parentRow.getChildren().addAll(expandButton, groupCheckbox, colorRect);
                
                // Children container
                childrenContainer = new VBox(2);
                childrenContainer.setVisible(false);
                childrenContainer.setManaged(false);
                childrenContainer.setPadding(new Insets(0, 0, 0, 20));
                
                // Create child rows
                for (ChannelInfo info : channels) {
                    HBox childRow = createChannelRow(info);
                    childrenContainer.getChildren().add(childRow);
                }
                
                rootNode.getChildren().addAll(parentRow, childrenContainer);
                
            } else {
                // Single channel
                ChannelInfo info = channels.get(0);
                CheckBox checkbox = new CheckBox(groupName);
                checkbox.setOnAction(e -> {
                    if (!isUpdatingCheckboxes) {
                        isUpdatingCheckboxes = true;
                        try {
                            var display = viewer.getImageDisplay();
                            display.setChannelSelected(info.channel, checkbox.isSelected());
                        } finally {
                            isUpdatingCheckboxes = false;
                        }
                    }
                });
                
                Rectangle colorRect = new Rectangle(20, 15);
                updateColorRect(colorRect, info.channel);
                
                colorRect.setOnMouseClicked(e -> {
                    if (e.getClickCount() == 2) {
                        // Double-click to change color
                        showColorPicker(info.channel);
                    }
                });
                
                parentRow.getChildren().addAll(checkbox, colorRect);
                rootNode.getChildren().add(parentRow);
                
                // Update checkbox state
                updateCheckboxState(checkbox, info.channel);
            }
        }
        
        private HBox createChannelRow(ChannelInfo info) {
            HBox row = new HBox(5);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(2));
            
            CheckBox checkbox = new CheckBox(info.subType);
            checkbox.setOnAction(e -> {
                if (!isUpdatingCheckboxes) {
                    isUpdatingCheckboxes = true;
                    try {
                        var display = viewer.getImageDisplay();
                        display.setChannelSelected(info.channel, checkbox.isSelected());
                        updateGroupCheckboxState();
                    } finally {
                        isUpdatingCheckboxes = false;
                    }
                }
            });
            
            Rectangle colorRect = new Rectangle(20, 15);
            updateColorRect(colorRect, info.channel);
            
            colorRect.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) {
                    showColorPicker(info.channel);
                }
            });
            
            row.getChildren().addAll(checkbox, colorRect);
            
            // Update checkbox state
            updateCheckboxState(checkbox, info.channel);
            
            return row;
        }
        
        private void toggleExpand() {
            expanded = !expanded;
            expandButton.setText(expanded ? "▼" : "▶");
            childrenContainer.setVisible(expanded);
            childrenContainer.setManaged(expanded);
        }
        
        private void selectAllChannels(boolean selected) {
            var display = viewer.getImageDisplay();
            for (ChannelInfo info : channels) {
                display.setChannelSelected(info.channel, selected);
            }
            // Update child checkboxes
            if (expanded && childrenContainer != null) {
                for (var node : childrenContainer.getChildren()) {
                    if (node instanceof HBox) {
                        for (var child : ((HBox) node).getChildren()) {
                            if (child instanceof CheckBox) {
                                ((CheckBox) child).setSelected(selected);
                            }
                        }
                    }
                }
            }
        }
        
        private void updateGroupCheckboxState() {
            if (groupCheckbox == null) return;
            
            var display = viewer.getImageDisplay();
            int selectedCount = 0;
            for (ChannelInfo info : channels) {
                if (display.selectedChannels().contains(info.channel)) {
                    selectedCount++;
                }
            }
            
            groupCheckbox.setSelected(selectedCount == channels.size());
            groupCheckbox.setIndeterminate(selectedCount > 0 && selectedCount < channels.size());
        }
        
        private void updateCheckboxState(CheckBox checkbox, ChannelDisplayInfo channel) {
            var display = viewer.getImageDisplay();
            checkbox.setSelected(display.selectedChannels().contains(channel));
        }
        
        private void updateColorRect(Rectangle rect, ChannelDisplayInfo channel) {
            Integer colorValue = channel.getColor();
            if (colorValue != null) {
                int r = (colorValue >> 16) & 0xFF;
                int g = (colorValue >> 8) & 0xFF;
                int b = colorValue & 0xFF;
                rect.setFill(Color.rgb(r, g, b));
            }
        }
        
        private void showColorPicker(ChannelDisplayInfo channel) {
            // TODO: Implement color picker dialog
            logger.info("Color picker for channel: {}", channel.getName());
        }
        
        VBox getRootNode() {
            return rootNode;
        }
        
        /**
         * Refresh all checkbox states to match current ImageDisplay selection
         */
        void refreshCheckboxStates() {
            isUpdatingCheckboxes = true;
            try {
                var display = viewer.getImageDisplay();
                if (display == null) return;
                
                logger.debug("Refreshing checkbox states for group: {}", groupName);
                
                if (isRGB) {
                    // Update group checkbox
                    updateGroupCheckboxState();
                    
                    // Update child checkboxes
                    if (childrenContainer != null) {
                        for (var node : childrenContainer.getChildren()) {
                            if (node instanceof HBox) {
                                HBox row = (HBox) node;
                                // Find the checkbox and channel info
                                for (int i = 0; i < row.getChildren().size(); i++) {
                                    if (row.getChildren().get(i) instanceof CheckBox) {
                                        CheckBox cb = (CheckBox) row.getChildren().get(i);
                                        // Find matching channel by checkbox text
                                        for (ChannelInfo info : channels) {
                                            if (info.subType != null && info.subType.equals(cb.getText())) {
                                                boolean shouldBeSelected = display.selectedChannels().contains(info.channel);
                                                if (cb.isSelected() != shouldBeSelected) {
                                                    cb.setSelected(shouldBeSelected);
                                                    logger.debug("Updated {} checkbox to {}", info.subType, shouldBeSelected);
                                                }
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Single channel - update checkbox
                    if (!channels.isEmpty()) {
                        ChannelInfo info = channels.get(0);
                        // Find checkbox in rootNode
                        for (var node : rootNode.getChildren()) {
                            if (node instanceof HBox) {
                                for (var child : ((HBox) node).getChildren()) {
                                    if (child instanceof CheckBox) {
                                        CheckBox cb = (CheckBox) child;
                                        boolean shouldBeSelected = display.selectedChannels().contains(info.channel);
                                        if (cb.isSelected() != shouldBeSelected) {
                                            cb.setSelected(shouldBeSelected);
                                            logger.debug("Updated {} checkbox to {}", groupName, shouldBeSelected);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } finally {
                isUpdatingCheckboxes = false;
            }
        }
    }
}

