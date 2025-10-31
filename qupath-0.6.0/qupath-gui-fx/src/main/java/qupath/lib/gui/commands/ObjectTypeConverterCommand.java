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

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.control.ButtonType;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;

/**
 * Command to convert between Annotation and Detection objects.
 * 
 * Features:
 * - Convert Annotations to Detections
 * - Convert Detections to Annotations
 * - Preserve measurements, classifications, and metadata
 * - Batch conversion support
 * 
 * @author HPath Team
 */
public class ObjectTypeConverterCommand {
    
    private static final Logger logger = LoggerFactory.getLogger(ObjectTypeConverterCommand.class);
    
    /**
     * Convert selected annotations to detections
     * @param imageData the current image data
     */
    public static void convertAnnotationsToDetections(ImageData<BufferedImage> imageData) {
        if (imageData == null) {
            Dialogs.showErrorMessage("Convert to Detections", "No image is open!");
            return;
        }
        
        var hierarchy = imageData.getHierarchy();
        var selected = hierarchy.getSelectionModel().getSelectedObjects()
                .stream()
                .filter(p -> p.isAnnotation())
                .collect(Collectors.toList());
        
        if (selected.isEmpty()) {
            Dialogs.showErrorMessage("Convert to Detections", "No annotations selected!");
            return;
        }
        
        // Confirm with user
        var response = Dialogs.builder()
            .title("Convert to Detections")
            .contentText(String.format("Convert %d annotation(s) to detection(s)?\n" +
                "This will preserve measurements and classifications.", selected.size()))
            .buttons(ButtonType.YES, ButtonType.NO)
            .showAndWait()
            .orElse(ButtonType.NO);
        
        if (response != ButtonType.YES) {
            return;
        }
        
        // Convert
        List<PathObject> converted = new ArrayList<>();
        for (PathObject annotation : selected) {
            PathObject detection = convertToDetection(annotation);
            if (detection != null) {
                converted.add(detection);
            }
        }
        
        // Remove originals and add converted
        hierarchy.removeObjects(selected, true);
        hierarchy.addObjects(converted);
        
        // Update workflow
        imageData.getHistoryWorkflow().addStep(
            new DefaultScriptableWorkflowStep("Convert annotations to detections",
                String.format("convertAnnotationsToDetections(%d)", selected.size()))
        );
        
        logger.info("Converted {} annotations to detections", converted.size());
        Dialogs.showInfoNotification("Convert to Detections", 
            String.format("Successfully converted %d object(s)", converted.size()));
    }
    
    /**
     * Convert selected detections to annotations
     * @param imageData the current image data
     */
    public static void convertDetectionsToAnnotations(ImageData<BufferedImage> imageData) {
        if (imageData == null) {
            Dialogs.showErrorMessage("Convert to Annotations", "No image is open!");
            return;
        }
        
        var hierarchy = imageData.getHierarchy();
        var selected = hierarchy.getSelectionModel().getSelectedObjects()
                .stream()
                .filter(p -> p.isDetection())
                .collect(Collectors.toList());
        
        if (selected.isEmpty()) {
            Dialogs.showErrorMessage("Convert to Annotations", "No detections selected!");
            return;
        }
        
        // Confirm with user
        var response = Dialogs.builder()
            .title("Convert to Annotations")
            .contentText(String.format("Convert %d detection(s) to annotation(s)?\n" +
                "This will preserve measurements and classifications.", selected.size()))
            .buttons(ButtonType.YES, ButtonType.NO)
            .showAndWait()
            .orElse(ButtonType.NO);
        
        if (response != ButtonType.YES) {
            return;
        }
        
        // Convert
        List<PathObject> converted = new ArrayList<>();
        for (PathObject detection : selected) {
            PathObject annotation = convertToAnnotation(detection);
            if (annotation != null) {
                converted.add(annotation);
            }
        }
        
        // Remove originals and add converted
        hierarchy.removeObjects(selected, true);
        hierarchy.addObjects(converted);
        
        // Update workflow
        imageData.getHistoryWorkflow().addStep(
            new DefaultScriptableWorkflowStep("Convert detections to annotations",
                String.format("convertDetectionsToAnnotations(%d)", selected.size()))
        );
        
        logger.info("Converted {} detections to annotations", converted.size());
        Dialogs.showInfoNotification("Convert to Annotations", 
            String.format("Successfully converted %d object(s)", converted.size()));
    }
    
    /**
     * Convert a single annotation to detection, preserving all properties
     */
    private static PathObject convertToDetection(PathObject annotation) {
        if (!annotation.isAnnotation()) {
            return null;
        }
        
        // Create detection with same ROI and classification
        PathObject detection = PathObjects.createDetectionObject(
            annotation.getROI(),
            annotation.getPathClass()
        );
        
        // Copy measurements
        copyMeasurements(annotation, detection);
        
        // Copy metadata
        copyMetadata(annotation, detection);
        
        // Copy name if exists
        if (annotation.getName() != null) {
            detection.setName(annotation.getName());
        }
        
        // Copy color if exists
        if (annotation.getColor() != null) {
            detection.setColor(annotation.getColor());
        }
        
        // Preserve child objects
        if (annotation.hasChildObjects()) {
            for (PathObject child : annotation.getChildObjects()) {
                detection.addChildObject(child);
            }
        }
        
        return detection;
    }
    
    /**
     * Convert a single detection to annotation, preserving all properties
     */
    private static PathObject convertToAnnotation(PathObject detection) {
        if (!detection.isDetection()) {
            return null;
        }
        
        // Create annotation with same ROI and classification
        PathObject annotation = PathObjects.createAnnotationObject(
            detection.getROI(),
            detection.getPathClass()
        );
        
        // Copy measurements
        copyMeasurements(detection, annotation);
        
        // Copy metadata
        copyMetadata(detection, annotation);
        
        // Copy name if exists
        if (detection.getName() != null) {
            annotation.setName(detection.getName());
        }
        
        // Copy color if exists
        if (detection.getColor() != null) {
            annotation.setColor(detection.getColor());
        }
        
        // Preserve child objects
        if (detection.hasChildObjects()) {
            for (PathObject child : detection.getChildObjects()) {
                annotation.addChildObject(child);
            }
        }
        
        return annotation;
    }
    
    /**
     * Copy measurements from source to target
     */
    private static void copyMeasurements(PathObject source, PathObject target) {
        var sourceMeasurements = source.getMeasurementList();
        var targetMeasurements = target.getMeasurementList();
        
        for (var measurement : sourceMeasurements.getMeasurements()) {
            targetMeasurements.put(measurement.getName(), measurement.getValue());
        }
        
        targetMeasurements.close();
    }
    
    /**
     * Copy metadata from source to target
     */
    private static void copyMetadata(PathObject source, PathObject target) {
        var sourceMetadata = source.getMetadata();
        for (var entry : sourceMetadata.entrySet()) {
            target.getMetadata().put(entry.getKey(), entry.getValue());
        }
    }
    
    /**
     * Convert all annotations to detections in the current image
     * @param imageData the current image data
     */
    public static void convertAllAnnotationsToDetections(ImageData<BufferedImage> imageData) {
        if (imageData == null) {
            return;
        }
        
        var hierarchy = imageData.getHierarchy();
        var annotations = new ArrayList<>(hierarchy.getAnnotationObjects());
        
        if (annotations.isEmpty()) {
            Dialogs.showInfoNotification("Convert All", "No annotations found!");
            return;
        }
        
        // Confirm
        var response = Dialogs.builder()
            .title("Convert All Annotations")
            .contentText(String.format("Convert ALL %d annotations to detections?", annotations.size()))
            .buttons(ButtonType.YES, ButtonType.NO)
            .showAndWait()
            .orElse(ButtonType.NO);
        
        if (response != ButtonType.YES) {
            return;
        }
        
        // Convert
        List<PathObject> converted = new ArrayList<>();
        for (PathObject annotation : annotations) {
            PathObject detection = convertToDetection(annotation);
            if (detection != null) {
                converted.add(detection);
            }
        }
        
        hierarchy.removeObjects(annotations, true);
        hierarchy.addObjects(converted);
        
        logger.info("Converted {} annotations to detections", converted.size());
        Dialogs.showInfoNotification("Convert All", 
            String.format("Converted %d annotations to detections", converted.size()));
    }
    
    /**
     * Convert all detections to annotations in the current image
     * @param imageData the current image data
     */
    public static void convertAllDetectionsToAnnotations(ImageData<BufferedImage> imageData) {
        if (imageData == null) {
            return;
        }
        
        var hierarchy = imageData.getHierarchy();
        var detections = new ArrayList<>(hierarchy.getDetectionObjects());
        
        if (detections.isEmpty()) {
            Dialogs.showInfoNotification("Convert All", "No detections found!");
            return;
        }
        
        // Confirm
        var response = Dialogs.builder()
            .title("Convert All Detections")
            .contentText(String.format("Convert ALL %d detections to annotations?", detections.size()))
            .buttons(ButtonType.YES, ButtonType.NO)
            .showAndWait()
            .orElse(ButtonType.NO);
        
        if (response != ButtonType.YES) {
            return;
        }
        
        // Convert
        List<PathObject> converted = new ArrayList<>();
        for (PathObject detection : detections) {
            PathObject annotation = convertToAnnotation(detection);
            if (annotation != null) {
                converted.add(annotation);
            }
        }
        
        hierarchy.removeObjects(detections, true);
        hierarchy.addObjects(converted);
        
        logger.info("Converted {} detections to annotations", converted.size());
        Dialogs.showInfoNotification("Convert All", 
            String.format("Converted %d detections to annotations", converted.size()));
    }
}

