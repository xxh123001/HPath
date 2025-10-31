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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.util.AffineTransformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import qupath.lib.color.ColorToolsAwt;
import qupath.lib.gui.QuPathGUI;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.PathObjectPainter;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerListener;
import qupath.lib.gui.viewer.overlays.AbstractOverlay;
import qupath.lib.gui.viewer.overlays.PathOverlay;
import qupath.lib.gui.viewer.tools.PathTools;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathROIObject;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.interfaces.ROI;

/**
 * Command to register annotations to the image by marking corresponding control points.
 * Users mark points on the image (target) and corresponding points in the annotations (source),
 * then the command calculates an affine transformation and applies it to all annotations.
 * 
 * @author HPath Team
 */
class AnnotationRegistrationCommand implements Runnable, ChangeListener<ImageData<BufferedImage>>, QuPathViewerListener {

	private static final Logger logger = LoggerFactory.getLogger(AnnotationRegistrationCommand.class);
	
	private static final String TITLE = "Annotation Registration";
	
	private QuPathGUI qupath;
	
	private QuPathViewer viewer = null;
	private PathOverlay overlay = null;
	
	private Stage controlStage = null;
	private ListView<String> pointListView;
	private Label statusLabel;
	private Button addImagePointButton;
	private Button addAnnotationPointButton;
	private Button clearPointsButton;
	private Button applyTransformButton;
	private Button cancelButton;
	private CheckBox useSimilarityTransformCheckBox;
	
	// Control points
	private List<Point2D> imagePoints = new ArrayList<>();
	private List<Point2D> annotationPoints = new ArrayList<>();
	
	private boolean isSelectingImagePoint = false;
	private boolean isSelectingAnnotationPoint = false;
	
	private RegistrationMouseListener mouseListener = new RegistrationMouseListener();
	private KeyHandler keyListener = new KeyHandler();

	public AnnotationRegistrationCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
		this.qupath.imageDataProperty().addListener(this);
	}

	@Override
	public void run() {
		// Already running
		if (this.controlStage != null && this.controlStage.isShowing()) {
			controlStage.toFront();
			return;
		}
		
		// Get the viewer
		viewer = qupath.getViewer();
		var hierarchy = viewer.getHierarchy();
		if (hierarchy == null || viewer.getImageData() == null) {
			Dialogs.showErrorNotification(TITLE, "Please open an image first!");
			return;
		}
		
		// Check if there are annotations
		if (hierarchy.getAnnotationObjects().isEmpty()) {
			Dialogs.showErrorNotification(TITLE, "No annotations found in the current image!");
			return;
		}
		
		// Initialize
		imagePoints.clear();
		annotationPoints.clear();
		
		// Create control panel
		createControlPanel();
		
		// Setup viewer
		viewer.setActiveTool(PathTools.MOVE);
		qupath.getToolManager().setToolSwitchingEnabled(false);
		viewer.addViewerListener(this);
		viewer.getView().addEventFilter(MouseEvent.ANY, mouseListener);
		viewer.getView().addEventFilter(KeyEvent.KEY_PRESSED, keyListener);
		
		// Create overlay
		overlay = new RegistrationOverlay(viewer.getOverlayOptions());
		viewer.getCustomOverlayLayers().add(overlay);
		
		viewer.repaint();
	}
	
	/**
	 * Create the control panel UI
	 */
	private void createControlPanel() {
		controlStage = new Stage();
		controlStage.initOwner(qupath.getStage());
		controlStage.initModality(Modality.NONE);
		controlStage.setTitle(TITLE);
		
		BorderPane mainPane = new BorderPane();
		mainPane.setPadding(new Insets(10));
		
		// Status label
		statusLabel = new Label("Click 'Add Image Point' to start marking points on the image");
		statusLabel.setWrapText(true);
		statusLabel.setStyle("-fx-font-weight: bold;");
		statusLabel.setPadding(new Insets(5));
		
		// Point list
		pointListView = new ListView<>();
		pointListView.setPrefHeight(200);
		
		// Buttons
		addImagePointButton = new Button("Add Image Point");
		addImagePointButton.setMaxWidth(Double.MAX_VALUE);
		addImagePointButton.setOnAction(e -> startSelectingImagePoint());
		
		addAnnotationPointButton = new Button("Add Annotation Point");
		addAnnotationPointButton.setMaxWidth(Double.MAX_VALUE);
		addAnnotationPointButton.setOnAction(e -> startSelectingAnnotationPoint());
		addAnnotationPointButton.setDisable(true);
		
		clearPointsButton = new Button("Clear All Points");
		clearPointsButton.setMaxWidth(Double.MAX_VALUE);
		clearPointsButton.setOnAction(e -> clearAllPoints());
		
		// Similarity transform checkbox
		useSimilarityTransformCheckBox = new CheckBox("Use uniform scaling (no distortion)");
		useSimilarityTransformCheckBox.setSelected(true);  // Default to similarity transform
		useSimilarityTransformCheckBox.setTooltip(new javafx.scene.control.Tooltip(
			"When checked, only uniform scaling, rotation and translation are applied.\n" +
			"Annotations will not be distorted or skewed.\n" +
			"When unchecked, full affine transformation is used (may cause distortion)."
		));
		useSimilarityTransformCheckBox.setWrapText(true);
		useSimilarityTransformCheckBox.setOnAction(e -> updateUI());
		
		applyTransformButton = new Button("Apply Transform");
		applyTransformButton.setMaxWidth(Double.MAX_VALUE);
		applyTransformButton.setOnAction(e -> applyTransform());
		applyTransformButton.setDisable(true);
		
		cancelButton = new Button("Cancel");
		cancelButton.setMaxWidth(Double.MAX_VALUE);
		cancelButton.setOnAction(e -> cleanup(true));
		
		// Layout
		VBox buttonBox = new VBox(10);
		buttonBox.getChildren().addAll(
			addImagePointButton,
			addAnnotationPointButton,
			clearPointsButton,
			useSimilarityTransformCheckBox,
			applyTransformButton,
			cancelButton
		);
		
		VBox centerBox = new VBox(10);
		Label listLabel = new Label("Control Point Pairs:");
		centerBox.getChildren().addAll(listLabel, pointListView);
		VBox.setVgrow(pointListView, Priority.ALWAYS);
		
		mainPane.setTop(statusLabel);
		mainPane.setCenter(centerBox);
		mainPane.setBottom(buttonBox);
		
		Scene scene = new Scene(mainPane, 400, 500);
		controlStage.setScene(scene);
		
		controlStage.setOnCloseRequest(e -> {
			e.consume();
			cleanup(true);
		});
		
		controlStage.show();
	}
	
	/**
	 * Start selecting a point on the image
	 */
	private void startSelectingImagePoint() {
		isSelectingImagePoint = true;
		isSelectingAnnotationPoint = false;
		addImagePointButton.setDisable(true);
		addAnnotationPointButton.setDisable(true);
		statusLabel.setText("Click on the IMAGE to mark control point " + (imagePoints.size() + 1));
		statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: blue;");
	}
	
	/**
	 * Start selecting a point in the annotations
	 */
	private void startSelectingAnnotationPoint() {
		isSelectingAnnotationPoint = true;
		isSelectingImagePoint = false;
		addImagePointButton.setDisable(true);
		addAnnotationPointButton.setDisable(true);
		statusLabel.setText("Click on the ANNOTATION to mark corresponding point " + (annotationPoints.size() + 1));
		statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: red;");
	}
	
	/**
	 * Clear all control points
	 */
	private void clearAllPoints() {
		imagePoints.clear();
		annotationPoints.clear();
		updateUI();
		viewer.repaint();
	}
	
	/**
	 * Update the UI state
	 */
	private void updateUI() {
		// Update list
		pointListView.getItems().clear();
		int n = Math.max(imagePoints.size(), annotationPoints.size());
		for (int i = 0; i < n; i++) {
			String imageStr = i < imagePoints.size() ? 
				String.format("(%.1f, %.1f)", imagePoints.get(i).getX(), imagePoints.get(i).getY()) : "N/A";
			String annotStr = i < annotationPoints.size() ? 
				String.format("(%.1f, %.1f)", annotationPoints.get(i).getX(), annotationPoints.get(i).getY()) : "N/A";
			pointListView.getItems().add(String.format("%d: Image %s -> Annotation %s", i+1, imageStr, annotStr));
		}
		
		// Update button states
		if (!isSelectingImagePoint && !isSelectingAnnotationPoint) {
			int minPoints = useSimilarityTransformCheckBox.isSelected() ? 2 : 3;
			addImagePointButton.setDisable(false);
			addAnnotationPointButton.setDisable(imagePoints.size() <= annotationPoints.size());
			applyTransformButton.setDisable(imagePoints.size() < minPoints || annotationPoints.size() < minPoints);
			
			String transformType = useSimilarityTransformCheckBox.isSelected() ? 
				"similarity (no distortion)" : "affine (may distort)";
			
			if (imagePoints.size() == annotationPoints.size() && imagePoints.size() > 0) {
				statusLabel.setText(String.format("Point pairs: %d. Need at least %d pairs for %s transformation.", 
					imagePoints.size(), minPoints, transformType));
				statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: green;");
			} else if (imagePoints.size() > annotationPoints.size()) {
				statusLabel.setText("Click 'Add Annotation Point' to mark the corresponding point");
				statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: black;");
			} else {
				statusLabel.setText("Click 'Add Image Point' to mark the next point on the image");
				statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: black;");
			}
		}
	}
	
	/**
	 * Calculate and apply the transformation
	 */
	private void applyTransform() {
		boolean useSimilarity = useSimilarityTransformCheckBox.isSelected();
		int minPoints = useSimilarity ? 2 : 3;
		
		if (imagePoints.size() < minPoints || annotationPoints.size() < minPoints) {
			Dialogs.showErrorMessage(TITLE, String.format("Need at least %d point pairs to calculate transformation!", minPoints));
			return;
		}
		
		if (imagePoints.size() != annotationPoints.size()) {
			Dialogs.showErrorMessage(TITLE, "Number of image points and annotation points must match!");
			return;
		}
		
		try {
			// Calculate transformation based on selected type
			AffineTransformation transform = useSimilarity ? 
				calculateSimilarityTransform(annotationPoints, imagePoints) :
				calculateAffineTransform(annotationPoints, imagePoints);
			
			if (transform == null) {
				Dialogs.showErrorMessage(TITLE, "Failed to calculate transformation. Points may be collinear.");
				return;
			}
			
			// Confirm with user
			var response = Dialogs.builder()
				.title(TITLE)
				.contentText("Apply transformation to all annotations in the image?")
				.buttons(ButtonType.YES, ButtonType.NO)
				.showAndWait()
				.orElse(ButtonType.NO);
			
			if (response == ButtonType.YES) {
				var imageData = viewer.getImageData();
				var hierarchy = viewer.getHierarchy();
				
				// Apply to all annotations
				var allAnnotations = hierarchy.getAnnotationObjects();
				int count = 0;
				for (PathObject annotation : allAnnotations) {
					if (annotation instanceof PathROIObject) {
						ROI roi = annotation.getROI();
						var geom = roi.getGeometry();
						var transformedGeom = transform.transform(geom);
						ROI transformedROI = GeometryTools.geometryToROI(transformedGeom, roi.getImagePlane());
						((PathROIObject)annotation).setROI(transformedROI);
						count++;
					}
				}
				
				// Log transformation
				var values = transform.getMatrixEntries();
				String transformType = useSimilarity ? "similarity (uniform scaling)" : "affine";
				String transformString = String.format("[[%f, %f, %f], [%f, %f, %f]]",
						values[0], values[1], values[2],
						values[3], values[4], values[5]);
				logger.info("Applied {} registration transform: {}", transformType, transformString);
				logger.info("Transformed {} annotations", count);
				
				// Add to workflow
				String scriptString = String.format(
						"import qupath.lib.awt.common.AffineTransforms\n" +
						"def transform = AffineTransforms.fromRows(%f, %f, %f, %f, %f, %f)\n" +
						"transformAllObjects(transform)",
						values[0], values[1], values[2], values[3], values[4], values[5]);
				imageData.getHistoryWorkflow().addStep(
						new DefaultScriptableWorkflowStep("Register annotations", scriptString)
				);
				
				hierarchy.fireHierarchyChangedEvent(this);
				viewer.repaint();
				
				Dialogs.showInfoNotification(TITLE, String.format("Successfully transformed %d annotations", count));
				
				cleanup(false);
			}
			
		} catch (Exception e) {
			logger.error("Error applying transformation", e);
			Dialogs.showErrorMessage(TITLE, "Error applying transformation: " + e.getMessage());
		}
	}
	
	/**
	 * Calculate similarity transformation from source points to target points.
	 * Similarity transformation only allows uniform scaling, rotation and translation (no distortion).
	 * Uses least squares method for overdetermined systems (more than 2 points).
	 * 
	 * Transformation matrix:
	 * [a  -b  tx]
	 * [b   a  ty]
	 * 
	 * where a = s*cos(θ), b = s*sin(θ), s is uniform scale, θ is rotation angle
	 * 
	 * @param sourcePoints source control points
	 * @param targetPoints target control points
	 * @return the calculated similarity transformation, or null if calculation fails
	 */
	private AffineTransformation calculateSimilarityTransform(List<Point2D> sourcePoints, List<Point2D> targetPoints) {
		if (sourcePoints.size() != targetPoints.size() || sourcePoints.size() < 2) {
			return null;
		}
		
		int n = sourcePoints.size();
		
		// For similarity transformation: 
		// x' = a*x - b*y + tx
		// y' = b*x + a*y + ty
		// We need to solve for 4 parameters: a, b, tx, ty
		
		// Build matrices for least squares: A * p = t
		double[][] A = new double[2*n][4];
		double[] t = new double[2*n];
		
		for (int i = 0; i < n; i++) {
			double sx = sourcePoints.get(i).getX();
			double sy = sourcePoints.get(i).getY();
			double tx_val = targetPoints.get(i).getX();
			double ty_val = targetPoints.get(i).getY();
			
			// Row for x'
			A[2*i][0] = sx;   // coefficient for a
			A[2*i][1] = -sy;  // coefficient for -b
			A[2*i][2] = 1;    // coefficient for tx
			A[2*i][3] = 0;
			t[2*i] = tx_val;
			
			// Row for y'
			A[2*i+1][0] = sy;   // coefficient for a (note: swapped to maintain similarity)
			A[2*i+1][1] = sx;   // coefficient for b
			A[2*i+1][2] = 0;
			A[2*i+1][3] = 1;    // coefficient for ty
			t[2*i+1] = ty_val;
		}
		
		// Solve using least squares (A^T * A) * p = A^T * t
		double[][] AtA = new double[4][4];
		double[] Att = new double[4];
		
		// Calculate A^T * A
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 4; j++) {
				double sum = 0;
				for (int k = 0; k < 2*n; k++) {
					sum += A[k][i] * A[k][j];
				}
				AtA[i][j] = sum;
			}
		}
		
		// Calculate A^T * t
		for (int i = 0; i < 4; i++) {
			double sum = 0;
			for (int k = 0; k < 2*n; k++) {
				sum += A[k][i] * t[k];
			}
			Att[i] = sum;
		}
		
		// Solve linear system
		double[] params = solveLinearSystem(AtA, Att);
		
		if (params == null) {
			return null;
		}
		
		// Extract parameters: a, b, tx, ty
		double a = params[0];
		double b = params[1];
		double tx = params[2];
		double ty = params[3];
		
		// Create affine transformation with similarity constraint
		// Matrix: [a, -b, tx; b, a, ty]
		return new AffineTransformation(a, -b, tx, b, a, ty);
	}
	
	/**
	 * Calculate affine transformation from source points to target points.
	 * Uses least squares method for overdetermined systems (more than 3 points).
	 * 
	 * @param sourcePoints source control points
	 * @param targetPoints target control points
	 * @return the calculated affine transformation, or null if calculation fails
	 */
	private AffineTransformation calculateAffineTransform(List<Point2D> sourcePoints, List<Point2D> targetPoints) {
		if (sourcePoints.size() != targetPoints.size() || sourcePoints.size() < 3) {
			return null;
		}
		
		int n = sourcePoints.size();
		
		// For affine transformation: [x', y'] = [a, b, c] * [x, y, 1]
		//                                       [d, e, f]
		// We need to solve for 6 parameters: a, b, c, d, e, f
		
		// Build matrices for least squares: A * x = b
		// For x': a*x + b*y + c = x'
		// For y': d*x + e*y + f = y'
		
		double[][] A = new double[2*n][6];
		double[] b = new double[2*n];
		
		for (int i = 0; i < n; i++) {
			double sx = sourcePoints.get(i).getX();
			double sy = sourcePoints.get(i).getY();
			double tx = targetPoints.get(i).getX();
			double ty = targetPoints.get(i).getY();
			
			// Row for x'
			A[2*i][0] = sx;
			A[2*i][1] = sy;
			A[2*i][2] = 1;
			A[2*i][3] = 0;
			A[2*i][4] = 0;
			A[2*i][5] = 0;
			b[2*i] = tx;
			
			// Row for y'
			A[2*i+1][0] = 0;
			A[2*i+1][1] = 0;
			A[2*i+1][2] = 0;
			A[2*i+1][3] = sx;
			A[2*i+1][4] = sy;
			A[2*i+1][5] = 1;
			b[2*i+1] = ty;
		}
		
		// Solve using least squares (A^T * A) * x = A^T * b
		double[][] AtA = new double[6][6];
		double[] Atb = new double[6];
		
		// Calculate A^T * A
		for (int i = 0; i < 6; i++) {
			for (int j = 0; j < 6; j++) {
				double sum = 0;
				for (int k = 0; k < 2*n; k++) {
					sum += A[k][i] * A[k][j];
				}
				AtA[i][j] = sum;
			}
		}
		
		// Calculate A^T * b
		for (int i = 0; i < 6; i++) {
			double sum = 0;
			for (int k = 0; k < 2*n; k++) {
				sum += A[k][i] * b[k];
			}
			Atb[i] = sum;
		}
		
		// Solve linear system using Gaussian elimination
		double[] params = solveLinearSystem(AtA, Atb);
		
		if (params == null) {
			return null;
		}
		
		// Create affine transformation
		// JTS AffineTransformation uses row-major order: m00, m01, m02, m10, m11, m12
		return new AffineTransformation(params[0], params[1], params[2], params[3], params[4], params[5]);
	}
	
	/**
	 * Solve linear system Ax = b using Gaussian elimination with partial pivoting
	 */
	private double[] solveLinearSystem(double[][] A, double[] b) {
		int n = b.length;
		double[][] augmented = new double[n][n+1];
		
		// Create augmented matrix
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				augmented[i][j] = A[i][j];
			}
			augmented[i][n] = b[i];
		}
		
		// Forward elimination with partial pivoting
		for (int k = 0; k < n; k++) {
			// Find pivot
			int maxRow = k;
			for (int i = k+1; i < n; i++) {
				if (Math.abs(augmented[i][k]) > Math.abs(augmented[maxRow][k])) {
					maxRow = i;
				}
			}
			
			// Swap rows
			double[] temp = augmented[k];
			augmented[k] = augmented[maxRow];
			augmented[maxRow] = temp;
			
			// Check for singular matrix
			if (Math.abs(augmented[k][k]) < 1e-10) {
				logger.error("Matrix is singular or nearly singular");
				return null;
			}
			
			// Eliminate column
			for (int i = k+1; i < n; i++) {
				double factor = augmented[i][k] / augmented[k][k];
				for (int j = k; j <= n; j++) {
					augmented[i][j] -= factor * augmented[k][j];
				}
			}
		}
		
		// Back substitution
		double[] x = new double[n];
		for (int i = n-1; i >= 0; i--) {
			double sum = 0;
			for (int j = i+1; j < n; j++) {
				sum += augmented[i][j] * x[j];
			}
			x[i] = (augmented[i][n] - sum) / augmented[i][i];
		}
		
		return x;
	}
	
	/**
	 * Cleanup resources
	 */
	private void cleanup(boolean cancelled) {
		if (viewer != null) {
			viewer.getView().removeEventFilter(MouseEvent.ANY, mouseListener);
			viewer.getView().removeEventFilter(KeyEvent.KEY_PRESSED, keyListener);
			if (overlay != null) {
				viewer.getCustomOverlayLayers().remove(overlay);
			}
			viewer.removeViewerListener(this);
			
			var toolManager = qupath.getToolManager();
			toolManager.setToolSwitchingEnabled(true);
			if (viewer == qupath.getViewer()) {
				viewer.setActiveTool(toolManager.getSelectedTool());
			}
			
			viewer.repaint();
		}
		
		if (controlStage != null) {
			controlStage.close();
			controlStage = null;
		}
		
		imagePoints.clear();
		annotationPoints.clear();
		viewer = null;
		overlay = null;
	}

	@Override
	public void changed(ObservableValue<? extends ImageData<BufferedImage>> source, 
						ImageData<BufferedImage> imageDataOld, 
						ImageData<BufferedImage> imageDataNew) {
		cleanup(true);
	}
	
	/**
	 * Overlay to visualize control points
	 */
	class RegistrationOverlay extends AbstractOverlay {

		RegistrationOverlay(final OverlayOptions overlayOptions) {
			super(overlayOptions);
		}
		
		@Override
		public void paintOverlay(Graphics2D g2d, ImageRegion imageRegion, double downsampleFactor,
				ImageData<BufferedImage> imageData, boolean paintCompletely) {
			
			Stroke stroke = PathObjectPainter.getCachedStroke(PathPrefs.annotationStrokeThicknessProperty().get() * downsampleFactor);
			double radius = 8 * downsampleFactor;
			
			// Draw image points in blue
			Color imagePointColor = ColorToolsAwt.getCachedColor(0, 0, 255, 255);
			Color imagePointFill = ColorToolsAwt.getCachedColor(100, 100, 255, 128);
			for (int i = 0; i < imagePoints.size(); i++) {
				Point2D p = imagePoints.get(i);
				Ellipse2D circle = new Ellipse2D.Double(
					p.getX() - radius, p.getY() - radius,
					radius * 2, radius * 2
				);
				PathObjectPainter.paintShape(circle, g2d, imagePointColor, stroke, imagePointFill);
				
				// Draw number
				g2d.setColor(imagePointColor);
				g2d.drawString(String.valueOf(i+1), (float)(p.getX() + radius + 5), (float)(p.getY()));
			}
			
			// Draw annotation points in red
			Color annotPointColor = ColorToolsAwt.getCachedColor(255, 0, 0, 255);
			Color annotPointFill = ColorToolsAwt.getCachedColor(255, 100, 100, 128);
			for (int i = 0; i < annotationPoints.size(); i++) {
				Point2D p = annotationPoints.get(i);
				Ellipse2D circle = new Ellipse2D.Double(
					p.getX() - radius, p.getY() - radius,
					radius * 2, radius * 2
				);
				PathObjectPainter.paintShape(circle, g2d, annotPointColor, stroke, annotPointFill);
				
				// Draw number
				g2d.setColor(annotPointColor);
				g2d.drawString(String.valueOf(i+1), (float)(p.getX() + radius + 5), (float)(p.getY()));
			}
			
			// Draw lines connecting corresponding points
			if (imagePoints.size() > 0 && annotationPoints.size() > 0) {
				Color lineColor = ColorToolsAwt.getCachedColor(0, 255, 0, 128);
				g2d.setColor(lineColor);
				int n = Math.min(imagePoints.size(), annotationPoints.size());
				for (int i = 0; i < n; i++) {
					Point2D p1 = imagePoints.get(i);
					Point2D p2 = annotationPoints.get(i);
					g2d.drawLine((int)p1.getX(), (int)p1.getY(), (int)p2.getX(), (int)p2.getY());
				}
			}
		}
	}
	
	/**
	 * Mouse listener for point selection
	 */
	class RegistrationMouseListener implements EventHandler<MouseEvent> {
		
		@Override
		public void handle(MouseEvent e) {
			if (e.getEventType() == MouseEvent.MOUSE_CLICKED && e.getClickCount() == 1) {
				handleMouseClick(e);
			}
		}
		
		private void handleMouseClick(MouseEvent e) {
			if (!isSelectingImagePoint && !isSelectingAnnotationPoint) {
				return;
			}
			
			Point2D p = viewer.componentPointToImagePoint(e.getX(), e.getY(), new Point2D.Double(), false);
			
			if (isSelectingImagePoint) {
				imagePoints.add(p);
				isSelectingImagePoint = false;
				logger.info("Added image point {}: ({}, {})", imagePoints.size(), p.getX(), p.getY());
			} else if (isSelectingAnnotationPoint) {
				annotationPoints.add(p);
				isSelectingAnnotationPoint = false;
				logger.info("Added annotation point {}: ({}, {})", annotationPoints.size(), p.getX(), p.getY());
			}
			
			Platform.runLater(() -> updateUI());
			viewer.repaint();
			e.consume();
		}
	}
	
	/**
	 * Key handler for keyboard shortcuts
	 */
	class KeyHandler implements EventHandler<KeyEvent> {

		@Override
		public void handle(KeyEvent event) {
			var code = event.getCode();
			if (code == KeyCode.ESCAPE) {
				if (isSelectingImagePoint || isSelectingAnnotationPoint) {
					isSelectingImagePoint = false;
					isSelectingAnnotationPoint = false;
					Platform.runLater(() -> updateUI());
					event.consume();
				} else {
					cleanup(true);
					event.consume();
				}
			}
		}
	}

	@Override
	public void imageDataChanged(QuPathViewer viewer, ImageData<BufferedImage> imageDataOld,
			ImageData<BufferedImage> imageDataNew) {
		cleanup(true);
	}

	@Override
	public void visibleRegionChanged(QuPathViewer viewer, java.awt.Shape shape) {}

	@Override
	public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected) {}

	@Override
	public void viewerClosed(QuPathViewer viewer) {
		cleanup(true);
	}
	
}

