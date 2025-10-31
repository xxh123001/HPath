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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.DragDropImportListener;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Handler to convert JSON files to GeoJSON format when dragged into HPath.
 * This allows standard JSON data to be automatically converted to a renderable GeoJSON format.
 * 
 * @author HPath Team
 */
public class JsonToGeoJsonHandler implements DragDropImportListener.DropHandler<JsonElement> {
    
    private static final Logger logger = LoggerFactory.getLogger(JsonToGeoJsonHandler.class);
    
    /**
     * Handle JSON drop and attempt to convert to GeoJSON if needed.
     * 
     * @param viewer the viewer where the drop occurred
     * @param elements the JSON elements that were dropped
     * @return true if the handler successfully processed the drop
     */
    @Override
    public boolean handleDrop(QuPathViewer viewer, List<JsonElement> elements) {
        logger.info("JsonToGeoJsonHandler.handleDrop called with {} elements", elements != null ? elements.size() : 0);
        
        if (elements == null || elements.isEmpty()) {
            logger.warn("No elements provided for JSON import");
            return false;
        }
        
        // Must have an active viewer with an image
        if (viewer == null || viewer.getImageData() == null) {
            logger.warn("No active viewer or image data available for JSON import");
            return false;
        }
        
        try {
            List<PathObject> allObjects = new ArrayList<>();
            
            for (int i = 0; i < elements.size(); i++) {
                JsonElement element = elements.get(i);
                logger.info("Processing element {} of {}", i + 1, elements.size());
                
                if (isGeoJSON(element)) {
                    // Already GeoJSON format, parse directly
                    logger.info("Detected GeoJSON format, parsing objects...");
                    try {
                        List<PathObject> objects = GsonTools.parseObjectsFromGeoJSON(element);
                        logger.info("Successfully parsed {} PathObjects from GeoJSON", objects.size());
                        allObjects.addAll(objects);
                    } catch (Exception e) {
                        logger.error("Error parsing GeoJSON: {}", e.getMessage(), e);
                        throw e;
                    }
                } else {
                    // Try to convert to GeoJSON
                    logger.info("Element is not GeoJSON, attempting to convert...");
                    JsonElement geoJsonElement = convertToGeoJSON(element);
                    if (geoJsonElement != null) {
                        logger.info("Successfully converted to GeoJSON, now parsing...");
                        try {
                            List<PathObject> objects = GsonTools.parseObjectsFromGeoJSON(geoJsonElement);
                            logger.info("Successfully parsed {} PathObjects after conversion", objects.size());
                            allObjects.addAll(objects);
                        } catch (Exception e) {
                            logger.error("Error parsing converted GeoJSON: {}", e.getMessage(), e);
                            throw e;
                        }
                    } else {
                        logger.warn("Could not convert JSON to GeoJSON format");
                        return false;
                    }
                }
            }
            
            logger.info("Total objects collected: {}", allObjects.size());
            
            // Import the objects if we found any
            if (!allObjects.isEmpty()) {
                // When dragging to viewer: clear existing annotations and import all objects
                if (viewer != null) {
                    logger.info("Drag to viewer detected: clearing existing annotations and importing all objects");
                    
                    // Clear all existing annotation objects
                    var hierarchy = viewer.getImageData().getHierarchy();
                    var existingAnnotations = new java.util.ArrayList<>(hierarchy.getAnnotationObjects());
                    if (!existingAnnotations.isEmpty()) {
                        logger.info("Removing {} existing annotations", existingAnnotations.size());
                        hierarchy.removeObjects(existingAnnotations, true);
                    }
                    
                    // Import all objects from the file
                    logger.info("Importing all {} objects", allObjects.size());
                    hierarchy.addObjects(allObjects);
                    return true;
                } else {
                    // Original behavior when not dragging to specific viewer
                    logger.info("Prompting to import {} objects", allObjects.size());
                    return InteractiveObjectImporter.promptToImportObjects(
                            viewer.getImageData().getHierarchy(), 
                            allObjects
                    );
                }
            } else {
                logger.warn("No valid GeoJSON objects found in dropped files");
            }
            
        } catch (JsonParseException e) {
            logger.error("JSON parsing error: {}", e.getMessage(), e);
            return false;
        } catch (Exception e) {
            logger.error("Unexpected error during JSON/GeoJSON handling: {}", e.getMessage(), e);
            return false;
        }
        
        logger.debug("Returning false - handler did not process the drop");
        return false;
    }
    
    /**
     * Check if a JSON element is already in GeoJSON format.
     * 
     * @param element the JSON element to check
     * @return true if the element appears to be GeoJSON
     */
    private boolean isGeoJSON(JsonElement element) {
        if (!element.isJsonObject()) {
            return false;
        }
        
        JsonObject obj = element.getAsJsonObject();
        
        // Check for GeoJSON type field
        if (obj.has("type")) {
            String type = obj.get("type").getAsString();
            // Common GeoJSON types
            return type.equals("Feature") || 
                   type.equals("FeatureCollection") ||
                   type.equals("Point") ||
                   type.equals("LineString") ||
                   type.equals("Polygon") ||
                   type.equals("MultiPoint") ||
                   type.equals("MultiLineString") ||
                   type.equals("MultiPolygon") ||
                   type.equals("GeometryCollection");
        }
        
        return false;
    }
    
    /**
     * Convert a generic JSON element to GeoJSON format.
     * This method attempts several common conversion patterns:
     * 1. If JSON contains coordinate arrays, create Polygon geometries
     * 2. If JSON contains point data, create Point geometries
     * 3. If JSON contains a list of objects, create a FeatureCollection
     * 
     * @param element the JSON element to convert
     * @return a GeoJSON-formatted JsonElement, or null if conversion fails
     */
    public static JsonElement convertToGeoJSON(JsonElement element) {
        try {
            if (element.isJsonArray()) {
                return convertArrayToGeoJSON(element.getAsJsonArray());
            } else if (element.isJsonObject()) {
                return convertObjectToGeoJSON(element.getAsJsonObject());
            }
        } catch (Exception e) {
            logger.error("Error during JSON to GeoJSON conversion", e);
        }
        return null;
    }
    
    /**
     * Convert a JSON array to a GeoJSON FeatureCollection.
     * 
     * @param array the JSON array
     * @return a GeoJSON FeatureCollection
     */
    private static JsonElement convertArrayToGeoJSON(JsonArray array) {
        JsonObject featureCollection = new JsonObject();
        featureCollection.addProperty("type", "FeatureCollection");
        JsonArray features = new JsonArray();
        
        for (JsonElement item : array) {
            if (item.isJsonObject()) {
                JsonObject feature = convertToFeature(item.getAsJsonObject());
                if (feature != null) {
                    features.add(feature);
                }
            }
        }
        
        if (features.size() > 0) {
            featureCollection.add("features", features);
            return featureCollection;
        }
        
        return null;
    }
    
    /**
     * Convert a JSON object to GeoJSON format.
     * 
     * @param obj the JSON object
     * @return a GeoJSON element
     */
    private static JsonElement convertObjectToGeoJSON(JsonObject obj) {
        // Try to find coordinate data
        if (hasCoordinateData(obj)) {
            JsonObject feature = convertToFeature(obj);
            if (feature != null) {
                // Wrap in FeatureCollection
                JsonObject featureCollection = new JsonObject();
                featureCollection.addProperty("type", "FeatureCollection");
                JsonArray features = new JsonArray();
                features.add(feature);
                featureCollection.add("features", features);
                return featureCollection;
            }
        }
        
        return null;
    }
    
    /**
     * Check if a JSON object contains coordinate data.
     * 
     * @param obj the JSON object
     * @return true if coordinate data is found
     */
    private static boolean hasCoordinateData(JsonObject obj) {
        // Check for common coordinate field names
        return obj.has("coordinates") || 
               obj.has("geometry") ||
               obj.has("points") ||
               obj.has("polygon") ||
               (obj.has("x") && obj.has("y")) ||
               (obj.has("lat") && obj.has("lon")) ||
               (obj.has("latitude") && obj.has("longitude"));
    }
    
    /**
     * Convert a JSON object to a GeoJSON Feature.
     * 
     * @param obj the JSON object
     * @return a GeoJSON Feature
     */
    private static JsonObject convertToFeature(JsonObject obj) {
        JsonObject feature = new JsonObject();
        feature.addProperty("type", "Feature");
        
        // Create geometry
        JsonObject geometry = createGeometry(obj);
        if (geometry == null) {
            return null;
        }
        
        feature.add("geometry", geometry);
        
        // Add properties (all other fields)
        JsonObject properties = new JsonObject();
        for (String key : obj.keySet()) {
            if (!isGeometryField(key)) {
                properties.add(key, obj.get(key));
            }
        }
        
        // 识别label字段并设置为classification
        // Check for 'label' field and map it to 'classification' if present
        if (obj.has("label")) {
            JsonElement labelElement = obj.get("label");
            if (labelElement.isJsonPrimitive()) {
                String labelValue = labelElement.getAsString();
                // Add as 'classification' property for QuPath
                properties.addProperty("classification", labelValue);
                logger.info("Mapped 'label' field ('{}') to 'classification'", labelValue);
            }
        }
        // Also check if label is inside an existing properties object
        else if (obj.has("properties") && obj.get("properties").isJsonObject()) {
            JsonObject existingProps = obj.get("properties").getAsJsonObject();
            if (existingProps.has("label")) {
                JsonElement labelElement = existingProps.get("label");
                if (labelElement.isJsonPrimitive()) {
                    String labelValue = labelElement.getAsString();
                    properties.addProperty("classification", labelValue);
                    logger.info("Mapped nested 'label' field ('{}') to 'classification'", labelValue);
                }
            }
        }
        
        feature.add("properties", properties);
        
        return feature;
    }
    
    /**
     * Check if a field name indicates geometry data.
     * 
     * @param fieldName the field name
     * @return true if it's a geometry field
     */
    private static boolean isGeometryField(String fieldName) {
        return fieldName.equals("coordinates") ||
               fieldName.equals("geometry") ||
               fieldName.equals("points") ||
               fieldName.equals("polygon") ||
               fieldName.equals("x") ||
               fieldName.equals("y") ||
               fieldName.equals("lat") ||
               fieldName.equals("lon") ||
               fieldName.equals("latitude") ||
               fieldName.equals("longitude");
    }
    
    /**
     * Create a GeoJSON Geometry object from JSON data.
     * 
     * @param obj the JSON object containing coordinate data
     * @return a GeoJSON Geometry object
     */
    private static JsonObject createGeometry(JsonObject obj) {
        JsonObject geometry = new JsonObject();
        
        // Check for direct coordinates field (already in GeoJSON-like format)
        if (obj.has("coordinates")) {
            JsonElement coords = obj.get("coordinates");
            if (coords.isJsonArray()) {
                geometry.addProperty("type", "Polygon");
                geometry.add("coordinates", coords);
                return geometry;
            }
        }
        
        // Check for geometry sub-object
        if (obj.has("geometry")) {
            JsonElement geom = obj.get("geometry");
            if (geom.isJsonObject()) {
                return geom.getAsJsonObject();
            }
        }
        
        // Check for points/polygon arrays
        if (obj.has("points") || obj.has("polygon")) {
            JsonElement pointsElement = obj.has("points") ? obj.get("points") : obj.get("polygon");
            if (pointsElement.isJsonArray()) {
                JsonArray points = pointsElement.getAsJsonArray();
                JsonArray coordinates = convertPointsToCoordinates(points);
                if (coordinates != null && coordinates.size() > 0) {
                    geometry.addProperty("type", "Polygon");
                    JsonArray coordArray = new JsonArray();
                    coordArray.add(coordinates);
                    geometry.add("coordinates", coordArray);
                    return geometry;
                }
            }
        }
        
        // Check for single point (x, y) or (lat, lon)
        if (obj.has("x") && obj.has("y")) {
            JsonArray coords = new JsonArray();
            coords.add(obj.get("x"));
            coords.add(obj.get("y"));
            geometry.addProperty("type", "Point");
            geometry.add("coordinates", coords);
            return geometry;
        }
        
        if (obj.has("lat") && obj.has("lon")) {
            JsonArray coords = new JsonArray();
            coords.add(obj.get("lon"));  // GeoJSON uses [lon, lat] order
            coords.add(obj.get("lat"));
            geometry.addProperty("type", "Point");
            geometry.add("coordinates", coords);
            return geometry;
        }
        
        if (obj.has("latitude") && obj.has("longitude")) {
            JsonArray coords = new JsonArray();
            coords.add(obj.get("longitude"));  // GeoJSON uses [lon, lat] order
            coords.add(obj.get("latitude"));
            geometry.addProperty("type", "Point");
            geometry.add("coordinates", coords);
            return geometry;
        }
        
        return null;
    }
    
    /**
     * Convert an array of points to GeoJSON coordinate format.
     * Ensures that the resulting coordinate array forms a closed ring for polygons.
     * 
     * @param points the array of points
     * @return a JsonArray of coordinates in GeoJSON format
     */
    private static JsonArray convertPointsToCoordinates(JsonArray points) {
        JsonArray coordinates = new JsonArray();
        
        for (JsonElement pointElement : points) {
            if (pointElement.isJsonObject()) {
                JsonObject point = pointElement.getAsJsonObject();
                JsonArray coord = new JsonArray();
                
                // Try different coordinate field names
                if (point.has("x") && point.has("y")) {
                    coord.add(point.get("x"));
                    coord.add(point.get("y"));
                } else if (point.has("lon") && point.has("lat")) {
                    coord.add(point.get("lon"));
                    coord.add(point.get("lat"));
                } else if (point.has("longitude") && point.has("latitude")) {
                    coord.add(point.get("longitude"));
                    coord.add(point.get("latitude"));
                } else {
                    continue; // Skip this point if we can't find coordinates
                }
                
                coordinates.add(coord);
            } else if (pointElement.isJsonArray()) {
                // Point is already an array [x, y]
                JsonArray pointArray = pointElement.getAsJsonArray();
                if (pointArray.size() >= 2) {
                    coordinates.add(pointArray);
                }
            }
        }
        
        // Ensure the ring is closed for polygons
        // In GeoJSON, the first and last coordinates must be identical
        if (coordinates.size() > 0) {
            JsonArray firstPoint = coordinates.get(0).getAsJsonArray();
            JsonArray lastPoint = coordinates.get(coordinates.size() - 1).getAsJsonArray();
            
            // Check if first and last points are different
            boolean isClosed = firstPoint.size() == lastPoint.size();
            if (isClosed) {
                for (int i = 0; i < firstPoint.size(); i++) {
                    if (!firstPoint.get(i).equals(lastPoint.get(i))) {
                        isClosed = false;
                        break;
                    }
                }
            }
            
            // If not closed, add the first point to the end
            if (!isClosed) {
                coordinates.add(firstPoint.deepCopy());
            }
        }
        
        return coordinates;
    }
}

