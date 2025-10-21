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

import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.panes.ExpandableChannelPane;

/**
 * Command to show the expandable channel control panel.
 * This replaces the default channel list with a tree-view that allows
 * expanding/collapsing RGB channels.
 */
public class ShowHierarchicalChannelsPaneCommand implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(ShowHierarchicalChannelsPaneCommand.class);
    private QuPathGUI qupath;
    private static Stage channelWindow;
    
    public ShowHierarchicalChannelsPaneCommand(QuPathGUI qupath) {
        this.qupath = qupath;
    }
    
    @Override
    public void run() {
        if (channelWindow != null && channelWindow.isShowing()) {
            channelWindow.toFront();
            return;
        }
        
        var viewer = qupath.getViewer();
        if (viewer == null || viewer.getImageData() == null) {
            logger.warn("No image data available");
            return;
        }
        
        ExpandableChannelPane panel = new ExpandableChannelPane(viewer);
        
        channelWindow = new Stage();
        channelWindow.setTitle("Channels");
        channelWindow.initOwner(qupath.getStage());
        channelWindow.setAlwaysOnTop(true); // Keep floating on top
        channelWindow.setResizable(true);
        
        // Compact floating window size
        Scene scene = new Scene(panel, 380, 250); // Smaller: 380x250 instead of 400x500
        channelWindow.setScene(scene);
        
        // Position at top-right corner of main window (near viewer area)
        Stage mainStage = qupath.getStage();
        if (mainStage != null) {
            double windowWidth = 380;
            double xPos = mainStage.getX() + mainStage.getWidth() - windowWidth - 20; // 20px from right edge
            double yPos = mainStage.getY() + 80; // 80px from top (below menu bar)
            
            channelWindow.setX(xPos);
            channelWindow.setY(yPos);
        }
        
        // Set minimum size
        channelWindow.setMinWidth(280);
        channelWindow.setMinHeight(180);
        
        channelWindow.show();
        
        logger.info("Floating channel panel opened at top-right corner");
    }
}

