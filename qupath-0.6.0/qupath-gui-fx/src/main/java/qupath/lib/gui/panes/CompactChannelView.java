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

import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;
import qupath.lib.gui.viewer.QuPathViewer;

/**
 * A compact, collapsible channel view that can be added to the Brightness & contrast panel.
 * This provides a tree-like view where RGB channels can be expanded/collapsed.
 */
public class CompactChannelView extends TitledPane {
    
    private ExpandableChannelPane channelPane;
    
    public CompactChannelView(QuPathViewer viewer) {
        this.channelPane = new ExpandableChannelPane(viewer);
        
        setText("Hierarchical Channel View");
        setContent(channelPane);
        setExpanded(true);  // Start expanded
        setCollapsible(true);  // Allow user to collapse
        
        // Styling
        setStyle("-fx-font-size: 11px;");
    }
    
    public ExpandableChannelPane getChannelPane() {
        return channelPane;
    }
}

