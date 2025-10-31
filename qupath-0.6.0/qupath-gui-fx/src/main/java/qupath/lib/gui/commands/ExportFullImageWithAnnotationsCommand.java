/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2025 QuPath developers, The University of Edinburgh
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

import javafx.scene.control.*;
import javafx.geometry.Insets;
import javafx.scene.layout.GridPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.dialogs.FileChoosers;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.images.servers.RenderedImageServer;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.writers.ImageWriterTools;
import qupath.lib.regions.RegionRequest;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Command to export the full image with annotations overlay as a single image file.
 * 
 * @author HPath Team
 */
public class ExportFullImageWithAnnotationsCommand implements Runnable {
    
    private static final Logger logger = LoggerFactory.getLogger(ExportFullImageWithAnnotationsCommand.class);
    
    private final QuPathGUI qupath;
    
    public ExportFullImageWithAnnotationsCommand(QuPathGUI qupath) {
        this.qupath = qupath;
    }
    
    @Override
    public void run() {
        QuPathViewer viewer = qupath.getViewer();
        ImageData<BufferedImage> imageData = viewer.getImageData();
        
        if (imageData == null) {
            Dialogs.showErrorMessage("Export full image", "No image is currently open!");
            return;
        }
        
        ImageServer<BufferedImage> server = imageData.getServer();
        
        // Create dialog for export options
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Export Full Image with Annotations");
        dialog.setHeaderText("Export complete image with annotations overlay");
        
        GridPane pane = new GridPane();
        pane.setHgap(10);
        pane.setVgap(10);
        pane.setPadding(new Insets(10));
        
        int row = 0;
        
        // Downsample factor
        Label labelDownsample = new Label("Downsample factor");
        TextField tfDownsample = new TextField("4.0");
        tfDownsample.setTooltip(new Tooltip("降采样倍数 - 1=原始分辨率（可能很大），4=缩小4倍"));
        pane.add(labelDownsample, 0, row);
        pane.add(tfDownsample, 1, row);
        row++;
        
        // Estimated size
        Label labelSize = new Label("Estimated size");
        Label labelSizeValue = new Label();
        pane.add(labelSize, 0, row);
        pane.add(labelSizeValue, 1, row);
        row++;
        
        // Update size estimate when downsample changes
        tfDownsample.textProperty().addListener((obs, oldVal, newVal) -> {
            try {
                double downsample = Double.parseDouble(newVal);
                if (downsample > 0) {
                    int width = (int)(server.getWidth() / downsample);
                    int height = (int)(server.getHeight() / downsample);
                    labelSizeValue.setText(String.format("%d x %d pixels", width, height));
                } else {
                    labelSizeValue.setText("Invalid downsample");
                }
            } catch (NumberFormatException e) {
                labelSizeValue.setText("Invalid number");
            }
        });
        tfDownsample.setText("4.0"); // Trigger initial update
        
        dialog.getDialogPane().setContent(pane);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        
        var result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK)
            return;
        
        // Parse downsample
        double downsample;
        try {
            downsample = Double.parseDouble(tfDownsample.getText());
            if (downsample < 1.0) {
                Dialogs.showErrorMessage("Export full image", "Downsample factor must be >= 1.0");
                return;
            }
        } catch (NumberFormatException e) {
            Dialogs.showErrorMessage("Export full image", "Invalid downsample factor: " + tfDownsample.getText());
            return;
        }
        
        // Prompt for output file
        File fileOutput = FileChoosers.promptToSaveFile("Export full image",
                null,
                FileChoosers.createExtensionFilter("PNG", ".png"),
                FileChoosers.createExtensionFilter("JPEG", ".jpg"),
                FileChoosers.createExtensionFilter("TIFF", ".tif"));
        
        if (fileOutput == null)
            return;
        
        // Export the image
        exportFullImageWithAnnotations(viewer, fileOutput, downsample);
    }
    
    /**
     * Export the full image with annotations overlay.
     * 
     * @param viewer the viewer containing the image
     * @param outputFile the output file
     * @param downsample the downsample factor
     */
    private void exportFullImageWithAnnotations(QuPathViewer viewer, File outputFile, double downsample) {
        try {
            logger.info("Starting export of full image with annotations...");
            logger.info("Output file: {}", outputFile.getAbsolutePath());
            logger.info("Downsample: {}", downsample);
            
            // Create rendered server that includes annotations
            ImageServer<BufferedImage> renderedServer = RenderedImageServer.createRenderedServer(viewer);
            
            int width = (int)(renderedServer.getWidth() / downsample);
            int height = (int)(renderedServer.getHeight() / downsample);
            
            logger.info("Exporting image: {} x {} pixels", width, height);
            
            // Create region request for full image
            RegionRequest request = RegionRequest.createInstance(
                    renderedServer.getPath(),
                    downsample,
                    0, 0,
                    renderedServer.getWidth(),
                    renderedServer.getHeight());
            
            // Read the full image
            BufferedImage img = renderedServer.readRegion(request);
            
            if (img == null) {
                Dialogs.showErrorMessage("Export full image", "Failed to create rendered image!");
                return;
            }
            
            logger.info("Image rendered successfully, writing to file...");
            
            // Write to file
            ImageWriterTools.writeImageRegion(renderedServer, request, outputFile.getAbsolutePath());
            
            logger.info("✅ Successfully exported full image with annotations to: {}", outputFile.getAbsolutePath());
            Dialogs.showInfoNotification("Export complete", 
                    "Successfully exported image to:\n" + outputFile.getName());
            
        } catch (IOException e) {
            logger.error("Error exporting full image with annotations", e);
            Dialogs.showErrorMessage("Export full image", 
                    "Failed to export image:\n" + e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during export", e);
            Dialogs.showErrorMessage("Export full image", 
                    "Unexpected error:\n" + e.getMessage());
        }
    }
}

