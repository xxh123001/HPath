/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2024 QuPath developers, The University of Edinburgh
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

import java.awt.Desktop;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.controlsfx.control.MasterDetailPane;
import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.controlsfx.control.textfield.TextFields;
import org.controlsfx.glyphfont.FontAwesome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import qupath.fx.controls.PredicateTextField;
import qupath.fx.prefs.controlsfx.PropertyItemBuilder;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.commands.ProjectCommands;
import qupath.lib.gui.commands.JsonToGeoJsonHandler;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.panes.ProjectTreeRow.ImageRow;
import qupath.lib.gui.panes.ProjectTreeRow.MetadataRow;
import qupath.lib.gui.panes.ProjectTreeRow.Type;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.gui.tools.IconFactory.PathIcons;
import qupath.lib.gui.tools.MenuTools;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.ImageServers;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.io.UriUpdater;
import qupath.lib.io.GsonTools;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Component for previewing and selecting images within a project.
 * 
 * @author Pete Bankhead
 * @author Melvin Gelbard
 */
public class ProjectBrowser implements ChangeListener<ImageData<BufferedImage>> {

	private static final Logger logger = LoggerFactory.getLogger(ProjectBrowser.class);
	private static final BooleanProperty keepDescriptionPaneOpenPref = PathPrefs.createPersistentPreference(
			"keepDescriptionOpen",
			false
	);
	private Project<BufferedImage> project;

	// Requested thumbnail max dimensions
	private int thumbnailWidth = 1000;
	private int thumbnailHeight = 600;

	private QuPathGUI qupath;
	private BorderPane panel;

	private ProjectImageTreeModel model = new ProjectImageTreeModel(null);
	private TreeView<ProjectTreeRow> tree;

	 // Keep a record of servers that failed- don't want to keep putting in thumbnails requests if the server is unavailable.
	private Set<ProjectTreeRow> serversFailed = Collections.synchronizedSet(new HashSet<>());
	
	private StringProperty descriptionText = new SimpleStringProperty();
	
	// Enhanced project merger - object list
	private ObservableList<ObjectFileEntry> objectFileList = FXCollections.observableArrayList();
	private TreeView<ObjectTreeItem> objectTreeView;
	
	// Merged results list
	private ObservableList<MergedResultEntry> mergedResultList = FXCollections.observableArrayList();
	private ListView<MergedResultEntry> mergedResultListView;

	// Ordered selection tracking for images and objects
	private ObservableList<ProjectImageEntry<BufferedImage>> orderedImageSelection = FXCollections.observableArrayList();
	private ObservableList<ObjectFileEntry> orderedObjectSelection = FXCollections.observableArrayList();

	// Predicate for filtering tree rows
	private ObjectProperty<Predicate<String>> predicateProperty = new SimpleObjectProperty<>(s -> true);

	private static ObjectProperty<ProjectThumbnailSize> thumbnailSize = PathPrefs.createPersistentPreference("projectThumbnailSize",
			ProjectThumbnailSize.SMALL, ProjectThumbnailSize.class);
	
	// Record if the context menu is showing; this is to block a tooltip obscuring it
	private BooleanProperty contextMenuShowing = new SimpleBooleanProperty();
	
	/**
	 * Metadata keys that will always be present
	 */
	private enum BaseMetadataKeys {
		IMAGE_NAME("Image name"), ENTRY_ID("Entry ID"), URI("URI");

		private final String displayName;

		BaseMetadataKeys(String displayName) {
			this.displayName = displayName;
		}

		String getDisplayName() {
			return displayName;
		}

		String getKey() {
			return "SORT_KEY[" + toString() + "]";
		}

	}
	private static final String UNASSIGNED_NODE = "(Unassigned)";
	private static final String UNDEFINED_VALUE = "Undefined";

	/**
	 * To load thumbnails in the background
	 */
	private static ExecutorService executor;

	/**
	 * Constructor.
	 * @param qupath the current QuPath instance
	 */
	public ProjectBrowser(final QuPathGUI qupath) {
		this.project = qupath.getProject();
		this.qupath = qupath;
		this.tree = new TreeView<>();

		qupath.imageDataProperty().addListener(this);
		
		// Get thumbnails in separate thread
		executor = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("thumbnail-loader", true));

		PathPrefs.maskImageNamesProperty().addListener((v, o, n) -> refreshTree(null));
		PathPrefs.skipProjectUriChecksProperty().addListener((v, o, n) -> tree.refresh());

		panel = new BorderPane();
		panel.getStyleClass().add("project-browser");

		tree.setCellFactory(n -> new ProjectTreeRowCell());
		
		thumbnailSize.addListener((v, o, n) -> tree.refresh());

		tree.setRoot(null);

		tree.setContextMenu(getPopup());

		tree.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.ENTER) {
				qupath.openImageEntry(getSelectedEntry());
				e.consume();
			}
		});

		tree.setOnMouseClicked(e -> {
			// Double-click to open image
			if (e.getClickCount() > 1) {
				qupath.openImageEntry(getSelectedEntry());
				e.consume();
			}
			// Ctrl/Cmd + Click for ordered selection
			else if (e.getClickCount() == 1 && (e.isControlDown() || e.isMetaDown())) {
				var selected = getSelectedEntry();
				if (selected != null) {
					if (orderedImageSelection.contains(selected)) {
						orderedImageSelection.remove(selected);
					} else {
						orderedImageSelection.add(selected);
					}
					tree.refresh();
					e.consume();
				}
			}
		});

		Button editDescriptionButton = new Button(
				null,
				IconFactory.createNode(FontAwesome.Glyph.PENCIL, 13)
		);
		editDescriptionButton.setTooltip(new Tooltip("Edit description"));
		editDescriptionButton.setOnAction(e -> promptToEditSelectedImageDescription());
		editDescriptionButton.visibleProperty().bind(Bindings.createBooleanBinding(
				() -> tree.getSelectionModel().getSelectedItem() != null && tree.getSelectionModel().getSelectedItem().getValue().getType().equals(Type.IMAGE),
				tree.getSelectionModel().selectedItemProperty()
		));
		editDescriptionButton.managedProperty().bind(editDescriptionButton.visibleProperty());

		ToggleButton keepDescriptionOpenButton = new ToggleButton(
				null,
				IconFactory.createNode(FontAwesome.Glyph.THUMB_TACK, 13)
		);
		keepDescriptionOpenButton.selectedProperty().set(keepDescriptionPaneOpenPref.get());
		keepDescriptionOpenButton.selectedProperty().addListener((p, o, n) -> keepDescriptionPaneOpenPref.set(n));
		keepDescriptionOpenButton.setTooltip(new Tooltip("Keep description pane open"));
		keepDescriptionOpenButton.setPadding(new Insets(1,9,1,9));

		TitledPane textDescriptionContainer = GuiTools.createLeftRightTitledPane(
				"Description",
				new HBox(5, editDescriptionButton, keepDescriptionOpenButton)
		);
		TextArea textDescription = new TextArea();
		textDescription.textProperty().bind(descriptionText);
		textDescription.setWrapText(true);
		textDescription.setEditable(false);
		textDescriptionContainer.setContent(textDescription);

		MasterDetailPane mdTree = new MasterDetailPane(Side.BOTTOM, tree, textDescriptionContainer, false);
		mdTree.showDetailNodeProperty().bind(Bindings.createBooleanBinding(
				() -> keepDescriptionOpenButton.selectedProperty().get() || descriptionText.get() != null,
				keepDescriptionOpenButton.selectedProperty(), descriptionText
		));
		
		tree.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		tree.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			if (n != null && n.getValue().getType() == ProjectTreeRow.Type.IMAGE)
				descriptionText.set(ProjectTreeRow.getEntry(n.getValue()).getDescription());
			else
				descriptionText.set(null);
		});

		// Create search filter
		var tfFilter = new PredicateTextField<String>();
		tfFilter.setPromptText("Search entry in project");
		tfFilter.setSpacing(0.0);
		var filterTooltip = new Tooltip("Type some text to filter the project entries by name or type.");
		Tooltip.install(tfFilter, filterTooltip);
		predicateProperty.bind(tfFilter.predicateProperty());
		predicateProperty.addListener((m, o, n) -> refreshTree(null));
		var paneUserFilter = GridPaneUtils.createRowGrid(tfFilter);
		
		// Create enhanced project panel with image/object split view
		BorderPane panelTree = createEnhancedProjectPanel(mdTree);

		panel.setBottom(paneUserFilter);
		panel.setCenter(panelTree);

		Button btnOpen = ActionTools.createButton(qupath.getCommonActions().PROJECT_OPEN);
		Button btnCreate = ActionTools.createButton(qupath.getCommonActions().PROJECT_NEW);
		Button btnAdd = ActionTools.createButton(qupath.getCommonActions().PROJECT_ADD_IMAGES);
		GridPane paneButtons = GridPaneUtils.createColumnGridControls(btnCreate, btnOpen, btnAdd);
		paneButtons.prefWidthProperty().bind(panel.widthProperty());
		paneButtons.setPadding(new Insets(5, 5, 5, 5));
		panel.setTop(paneButtons);

		qupath.getPreferencePane().getPropertySheet().getItems().add(
				new PropertyItemBuilder<>(thumbnailSize, ProjectThumbnailSize.class)
						.propertyType(PropertyItemBuilder.PropertyType.CHOICE)
						.name("Project thumbnail size")
						.category("Appearance")
						.choices(Arrays.asList(ProjectThumbnailSize.values()))
						.description("Choose thumbnail size for the project pane")
						.build()
		);
	}

	ContextMenu getPopup() {
		
		Action actionOpenImage = new Action("Open image", e -> qupath.openImageEntry(getSelectedEntry()));
		Action actionRemoveImage = new Action("Remove image(s)", e -> promptToRemoveSelectedImages());
		
		Action actionDuplicateImages = new Action("Duplicate image(s)", e -> promptToDuplicateSelectedImages());
		
		Action actionSetImageName = new Action("Rename image", e -> promptToRenameSelectedImage());
		// Add a metadata value
		Action actionAddMetadataValue = new Action("Add metadata", e -> promptToAddMetadataToSelectedImages());
		
		// Edit the description for the image
		Action actionEditDescription = new Action("Edit description", e -> promptToEditSelectedImageDescription());
		
		// Mask the name of the images and shuffle the entry
		Action actionMaskImageNames = ActionTools.createSelectableAction(PathPrefs.maskImageNamesProperty(), "Mask image names");
		
		// Refresh thumbnail according to current display settings
		Action actionRefreshThumbnail = new Action("Refresh thumbnail", e -> promptToRefreshSelectedThumbnails());
				
		// Open the project directory using Explorer/Finder etc.
		Action actionOpenProjectDirectory = createBrowsePathAction("Project...", () -> getProjectPath());
		Action actionOpenProjectEntryDirectory = createBrowsePathAction("Project entry...", () -> getProjectEntryPath());
		Action actionOpenImageServerDirectory = createBrowsePathAction("Image...", () -> getImageServerPath());
		

		ContextMenu menu = new ContextMenu();
		
		var hasProjectBinding = qupath.projectProperty().isNotNull();
		var menuOpenDirectories = MenuTools.createMenu("Open directory...",
				actionOpenProjectDirectory,
				actionOpenProjectEntryDirectory,
				actionOpenImageServerDirectory);
//		menuOpenDirectories.visibleProperty().bind(hasProjectBinding);
		var separatorOpenDirectories = new SeparatorMenuItem();
		separatorOpenDirectories.visibleProperty().bind(menuOpenDirectories.visibleProperty());

		MenuItem miOpenImage = ActionUtils.createMenuItem(actionOpenImage);
		MenuItem miRemoveImage = ActionUtils.createMenuItem(actionRemoveImage);
		MenuItem miDuplicateImage = ActionUtils.createMenuItem(actionDuplicateImages);
		MenuItem miSetImageName = ActionUtils.createMenuItem(actionSetImageName);
		MenuItem miRefreshThumbnail = ActionUtils.createMenuItem(actionRefreshThumbnail);
		MenuItem miEditDescription = ActionUtils.createMenuItem(actionEditDescription);
		MenuItem miAddMetadata = ActionUtils.createMenuItem(actionAddMetadataValue);
		MenuItem miMaskImages = ActionUtils.createCheckMenuItem(actionMaskImageNames);

		// Create menu for sorting by metadata
		Menu menuSort = new Menu("Sort by...");

		// Set visibility as menu being displayed
		menu.setOnShowing(e -> {
			TreeItem<ProjectTreeRow> selected = tree.getSelectionModel().getSelectedItem();
			ProjectImageEntry<BufferedImage> selectedEntry = selected == null ? null : ProjectTreeRow.getEntry(selected.getValue());
			var entries = getSelectedImageRowsRecursive();
			boolean isImageEntry = selectedEntry != null;

			populateSortByMenu(menuSort);
			
			int nSelectedEntries = ProjectTreeRow.getEntries(entries).size();
			if (nSelectedEntries == 1) {
				actionDuplicateImages.setText("Duplicate image");
				actionRemoveImage.setText("Remove image");
			} else {
				actionDuplicateImages.setText("Duplicate " + nSelectedEntries + " images");
				actionRemoveImage.setText("Remove " + nSelectedEntries + " images");				
			}
			
//			miOpenProjectDirectory.setVisible(project != null && project.getBaseDirectory().exists());
			miOpenImage.setVisible(isImageEntry);
			miDuplicateImage.setVisible(isImageEntry);
			miSetImageName.setVisible(isImageEntry);
			miAddMetadata.setVisible(!entries.isEmpty());
			miEditDescription.setVisible(isImageEntry);
			miRefreshThumbnail.setVisible(isImageEntry && isCurrentImage(selectedEntry));
			miRemoveImage.setVisible(selected != null && project != null && !project.getImageList().isEmpty());

			if (project == null) {
				menuSort.setVisible(false);
				return;
			}

			menuSort.setVisible(true);

			// Handle opening directories - requires Desktop
			menuOpenDirectories.setVisible(Desktop.isDesktopSupported() && hasProjectBinding.get());

			if (menu.getItems().isEmpty())
				e.consume();
		});
		
		SeparatorMenuItem separator = new SeparatorMenuItem();
		separator.visibleProperty().bind(menuSort.visibleProperty());
		menu.getItems().addAll(
				miOpenImage,
				miRemoveImage,
				miDuplicateImage,
				new SeparatorMenuItem(),
				miSetImageName,
				miAddMetadata,
				miEditDescription,
				miMaskImages,
				createThumbnailSizeMenu(),
				miRefreshThumbnail,
				separator,
				menuSort,
				separatorOpenDirectories,
				menuOpenDirectories
				);

		contextMenuShowing.bind(menu.showingProperty());
		
		return menu;
	}

	private Menu createThumbnailSizeMenu() {
		Menu menu = new Menu("Thumbnail size");
		ToggleGroup group = new ToggleGroup();
		for (ProjectThumbnailSize size : ProjectThumbnailSize.values()) {
			RadioMenuItem item = new RadioMenuItem(size.toString());
			item.setOnAction(e -> thumbnailSize.set(size));
			item.setUserData(size);
			menu.getItems().add(item);
			group.getToggles().add(item);
		}
		thumbnailSize.addListener((v, o, n) -> syncToggleGroupByUserData(group, n));
		syncToggleGroupByUserData(group, thumbnailSize.get());
		return menu;
	}

	private void syncToggleGroupByUserData(ToggleGroup group, Object userData) {
		for (var toggle : group.getToggles()) {
			if (Objects.equals(toggle.getUserData(), userData)) {
				group.selectToggle(toggle);
				return;
			}
		}
	}


	private void promptToEditSelectedImageDescription() {
		Project<?> project = getProject();
		ProjectImageEntry<?> entry = getSelectedEntry();
		if (project != null && entry != null) {
			if (showDescriptionEditor(entry)) {
				descriptionText.set(entry.getDescription());
				syncProject(project);
			}
		} else {
			Dialogs.showErrorMessage("Edit image description", "No entry is selected!");
		}
	}


	private void promptToRemoveSelectedImages() {
		Collection<ImageRow> imageRows = getSelectedImageRowsRecursive();
		Collection<ProjectImageEntry<BufferedImage>> entries = ProjectTreeRow.getEntries(imageRows);

		if (entries.isEmpty())
			return;

		// Automatically save and close any entries that are currently open (in any viewer)
		for (var viewer : qupath.getAllViewers()) {
			var imageData = viewer.getImageData();
			var entry = imageData == null ? null : getProject().getEntry(imageData);
			if (entry != null && entries.contains(entry)) {
				try {
					// Auto-save the image data before closing
					logger.info("Auto-saving ImageData before removal: {}", entry.getImageName());
					entry.saveImageData(imageData);
					// Close the viewer
					viewer.setImageData(null);
				} catch (IOException e) {
					logger.error("Error auto-saving ImageData: " + e.getLocalizedMessage(), e);
				}
			}
		}

		if (entries.size() == 1) {
			if (!Dialogs.showConfirmDialog("Remove project entry", "Remove " + entries.iterator().next().getImageName() + " from project?"))
				return;
		} else if (!Dialogs.showYesNoDialog("Remove project entries", String.format("Remove %d entries?", entries.size())))
			return;

		// Auto-delete associated data without asking
		project.removeAllImages(entries, true);
		refreshTree(null);
		syncProject(project);
		if (tree != null) {
			boolean isExpanded = tree.getRoot() != null && tree.getRoot().isExpanded();
			tree.setRoot(model.getRoot());
			tree.getRoot().setExpanded(isExpanded);
		}
	}


	private void promptToRefreshSelectedThumbnails() {
		TreeItem<ProjectTreeRow> path = tree.getSelectionModel().getSelectedItem();
		if (path == null)
			return;
		if (path.getValue().getType() == ProjectTreeRow.Type.IMAGE) {
			ProjectImageEntry<BufferedImage> entry = ProjectTreeRow.getEntry(path.getValue());
			if (!isCurrentImage(entry)) {
				logger.warn("Cannot refresh entry for image that is not open!");
				return;
			}
			BufferedImage imgThumbnail = qupath.getViewer().getRGBThumbnail();
			imgThumbnail = resizeForThumbnail(imgThumbnail);
			try {
				entry.setThumbnail(imgThumbnail);
			} catch (IOException e1) {
				logger.error("Error writing thumbnail", e1);
			}
			tree.refresh();
		}
	}


	private void promptToDuplicateSelectedImages() {
		Collection<ImageRow> imageRows = getSelectedImageRowsRecursive();
		if (imageRows.isEmpty()) {
			logger.debug("Nothing to duplicate - no entries selected");
			return;
		}

		boolean singleImage = false;
		String name = "";
		String title = "Duplicate images";
		String namePrompt = "Append to image name";
		String nameHelp = "Specify text to append to the image name to distinguish duplicated images";
		if (imageRows.size() == 1) {
			title = "Duplicate image";
			namePrompt = "Duplicate image name";
			nameHelp = "Specify name for the duplicated image";
			singleImage = true;
			name = imageRows.iterator().next().getDisplayableString();
			name = GeneralTools.generateDistinctName(
					name,
					project.getImageList().stream().map(p -> p.getImageName()).collect(Collectors.toSet()));
		}
		var params = new ParameterList()
				.addStringParameter("name", namePrompt, name, nameHelp)
				.addBooleanParameter("copyData", "Also duplicate data files", true, "Duplicate any associated data files along with the image");

		if (!GuiTools.showParameterDialog(title, params))
			return;

		boolean copyData = params.getBooleanParameterValue("copyData");
		name = params.getStringParameterValue("name");

		// Ensure we have a single space and then the text to append, with extra whitespace removed
		if (!singleImage && !name.isBlank())
			name = " " + name.strip();

		for (var imageRow : imageRows) {
			try {
				var newEntry = project.addDuplicate(ProjectTreeRow.getEntry(imageRow), copyData);
				if (newEntry != null && !name.isBlank()) {
					if (singleImage)
						newEntry.setImageName(name);
					else
						newEntry.setImageName(newEntry.getImageName() + name);
				}
			} catch (Exception ex) {
				Dialogs.showErrorNotification("Duplicating image", "Error duplicating " + ProjectTreeRow.getEntry(imageRow).getImageName());
				logger.error(ex.getLocalizedMessage(), ex);
			}
		}
		try {
			project.syncChanges();
		} catch (Exception ex) {
			logger.error("Error synchronizing project changes: " + ex.getLocalizedMessage(), ex);
		}
		refreshProject();
		if (imageRows.size() == 1)
			logger.debug("Duplicated 1 image entry");
		else
			logger.debug("Duplicated {} image entries", imageRows.size());
	}


	private void promptToRenameSelectedImage() {
		TreeItem<ProjectTreeRow> path = tree.getSelectionModel().getSelectedItem();
		if (path == null)
			return;
		if (path.getValue().getType() == ProjectTreeRow.Type.IMAGE) {
			if (setProjectEntryImageName(ProjectTreeRow.getEntry(path.getValue())) && project != null)
				syncProject(project);
		}
	}


	private void promptToAddMetadataToSelectedImages() {
		Project<BufferedImage> project = getProject();
		Collection<ImageRow> imageRows = getSelectedImageRowsRecursive();
		if (project != null && !imageRows.isEmpty()) {
			TextField tfMetadataKey = new TextField();
			var suggestions = project.getImageList().stream()
					.map(p -> p.getMetadataKeys())
					.flatMap(Collection::stream)
					.distinct()
					.sorted()
					.toList();
			TextFields.bindAutoCompletion(tfMetadataKey, suggestions);

			TextField tfMetadataValue = new TextField();
			Label labKey = new Label("New key");
			Label labValue = new Label("New value");
			labKey.setLabelFor(tfMetadataKey);
			labValue.setLabelFor(tfMetadataValue);
			tfMetadataKey.setTooltip(new Tooltip("Enter the name for the metadata entry"));
			tfMetadataValue.setTooltip(new Tooltip("Enter the value for the metadata entry"));

			ProjectImageEntry<BufferedImage> entry = imageRows.size() == 1 ? ProjectTreeRow.getEntry(imageRows.iterator().next()) : null;
			int nMetadataValues = entry == null ? 0 : entry.getMetadataKeys().size();

			GridPane pane = new GridPane();
			pane.setVgap(5);
			pane.setHgap(5);
			pane.add(labKey, 0, 0);
			pane.add(tfMetadataKey, 1, 0);
			pane.add(labValue, 0, 1);
			pane.add(tfMetadataValue, 1, 1);
			String name = imageRows.size() + " images";
			if (entry != null) {
				name = entry.getImageName();
				if (nMetadataValues > 0) {
					Label labelCurrent = new Label("Current metadata");
					TextArea textAreaCurrent = new TextArea();
					textAreaCurrent.setEditable(false);

					String keyString = entry.getMetadataSummaryString();
					if (keyString.isEmpty())
						textAreaCurrent.setText("No metadata entries yet");
					else
						textAreaCurrent.setText(keyString);
					textAreaCurrent.setPrefRowCount(3);
					labelCurrent.setLabelFor(textAreaCurrent);

					pane.add(labelCurrent, 0, 2);
					pane.add(textAreaCurrent, 1, 2);
				}
			}

			Dialog<ButtonType> dialog = new Dialog<>();
			dialog.setTitle("Metadata");
			dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
			dialog.getDialogPane().setHeaderText("Set metadata for " + name);
			dialog.getDialogPane().setContent(pane);
			Optional<ButtonType> result = dialog.showAndWait();
			if (result.isPresent() && result.get() == ButtonType.OK) {
				String key = tfMetadataKey.getText().trim();
				String value = tfMetadataValue.getText();
				if (key.isEmpty()) {
					logger.warn("Attempted to set metadata value for {}, but key was empty!", name);
				} else {
					// Set metadata for all entries
					for (var temp : imageRows)
						ProjectTreeRow.getEntry(temp).putMetadataValue(key, value);
					syncProject(project);
					tree.refresh();
				}
			}

			ImageRow selectedImageRow = getSelectedImageRow();
			refreshTree(selectedImageRow);

		} else {
			Dialogs.showErrorMessage("Edit image description", "No entry is selected!");
		}
	}


	/**
	 * Populate the 'Sort by...' menu, recreating values if necessary
	 * @param menuSort
	 * @return
	 */
	private Menu populateSortByMenu(Menu menuSort) {
		Map<String, MenuItem> newItems = new TreeMap<>();
		if (project != null) {
			for (ProjectImageEntry<?> entry : project.getImageList()) {
				// Add all entry metadata keys
				for (String key : entry.getMetadataKeys()) {
					if (!newItems.containsKey(key))
						newItems.put(key, ActionUtils.createMenuItem(createSortByKeyAction(key, key)));
				}
			}
		}
		menuSort.getItems().setAll(newItems.values());

		// Add all additional keys
		for (var key : BaseMetadataKeys.values()) {
			if (!newItems.containsKey(key.getKey()))
				menuSort.getItems().add(ActionUtils.createMenuItem(createSortByKeyAction(key.getDisplayName(), key.getKey())));
		}

		menuSort.getItems().add(0, ActionUtils.createMenuItem(createSortByKeyAction("None", null)));
		menuSort.getItems().add(1, new SeparatorMenuItem());

		return menuSort;
	}

	
	Path getProjectPath() {
		return project == null ? null : project.getPath();
	}

	Path getProjectEntryPath() {
		var selected = tree.getSelectionModel().getSelectedItem();
		if (selected == null)
			return null;
		var item = selected.getValue();
		if (item.getType() == Type.IMAGE)
			return ProjectTreeRow.getEntry(item).getEntryPath();
		return null;
	}
	
	Path getImageServerPath() {
		var selected = tree.getSelectionModel().getSelectedItem();
		if (selected == null)
			return null;
		var item = selected.getValue();
		if (item.getType() == Type.IMAGE) {
			try {
				var uris = ProjectTreeRow.getEntry(item).getURIs();
				if (!uris.isEmpty())
					return GeneralTools.toPath(uris.iterator().next());
			} catch (IOException e) {
				logger.debug("Error converting server path to file path", e);
			}
		}
		return null;
	}
	
	Action createBrowsePathAction(String text, Supplier<Path> func) {
		var action = new Action(text, e -> {
			var path = func.get();
			if (path == null)
				return;
			// Get directory if we will need one
			GuiTools.browseDirectory(path.toFile());
		});
		action.disabledProperty().bind(Bindings.createBooleanBinding(() -> func.get() == null, tree.getSelectionModel().selectedItemProperty()));
		return action;
	}
	
	/**
	 * Try to save a project, showing an error message if this fails.
	 * 
	 * @param project
	 * @return
	 */
	public static boolean syncProject(Project<?> project) {
		try {
			logger.info("Saving project {}...", project);
			project.syncChanges();
			return true;
		} catch (IOException e) {
			Dialogs.showErrorMessage("Save project", e);
			logger.error(e.getMessage(), e);
			return false;
		}
	}
	
	static boolean showDescriptionEditor(ProjectImageEntry<?> entry) {
		TextArea editor = new TextArea();
		editor.setWrapText(true);
		editor.setPromptText(String.format("Enter description for %s", entry.getImageName()));
		editor.setText(entry.getDescription());
		Dialog<ButtonType> dialog = new Dialog<>();
		dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		dialog.setTitle("Image description");
		dialog.getDialogPane().setHeaderText(entry.getImageName());
		dialog.getDialogPane().setContent(editor);
		Platform.runLater(editor::requestFocus);
		Optional<ButtonType> result = dialog.showAndWait();
		if (result.isPresent() && result.get() == ButtonType.OK && editor.getText() != null) {	
			var text = editor.getText();
			entry.setDescription(text.isEmpty() ? null : text);
			return true;
		}
		return false;
	}
 

	private Project<BufferedImage> getProject() {
		return project;
	}

	/**
	 * Get the {@link Pane} component for addition to a scene.
	 * @return
	 */
	public Pane getPane() {
		return panel;
	}
	
	/**
	 * Create enhanced project panel with split view for images and objects
	 */
	private BorderPane createEnhancedProjectPanel(MasterDetailPane mdTree) {
		BorderPane enhancedPanel = new BorderPane();
		
		// Mode toggle button
		ToggleButton modeToggleBtn = new ToggleButton("Simple Mode");
		modeToggleBtn.setStyle("-fx-font-size: 12px;");
		modeToggleBtn.setTooltip(new Tooltip("Toggle between Simple (images only) and All (full layout) modes"));
		
		HBox modeBox = new HBox(10);
		modeBox.setPadding(new Insets(5));
		modeBox.getChildren().add(modeToggleBtn);
		enhancedPanel.setTop(modeBox);
		
		// Upper section: Image list (left) + Object import (right) with resizable divider
		javafx.scene.control.SplitPane topSection = new javafx.scene.control.SplitPane();
		
		// Left: Original image tree with import button
		VBox imagePanel = new VBox(10);
		imagePanel.setStyle("-fx-border-color: #c0c0c0; -fx-border-width: 1; -fx-padding: 10; -fx-background-color: #fafafa;");
		
		// Image label with selection count and collapse button
		Button imageCollapseBtn = new Button("▼");
		imageCollapseBtn.setStyle("-fx-font-size: 12px; -fx-padding: 2 8 2 8;");
		
		Label imageLabel = new Label("Images");
		imageLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
		
		Label imageSelectionLabel = new Label("Selected: 0 (click to select in order)");
		imageSelectionLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
		orderedImageSelection.addListener((javafx.collections.ListChangeListener<ProjectImageEntry<BufferedImage>>) c -> {
			imageSelectionLabel.setText("Selected: " + orderedImageSelection.size() + 
				" (order: " + getImageSelectionOrderDisplay() + ")");
		});
		
		Button clearImageSelectionBtn = new Button("Clear Selection");
		clearImageSelectionBtn.setOnAction(e -> {
			orderedImageSelection.clear();
			// Force complete refresh to remove highlighting
			refreshTree(null);
		});
		clearImageSelectionBtn.setMaxWidth(Double.MAX_VALUE);
		
		HBox imageLabelBox = new HBox(5, imageCollapseBtn, imageLabel, imageSelectionLabel);
		imageLabelBox.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 5;");
		VBox imageHeaderBox = new VBox(5, imageLabelBox, clearImageSelectionBtn);
		
		// Add Import Images button (supports multiple selection)
		Button importImageBtn = new Button("Import Images...");
		importImageBtn.setMaxWidth(Double.MAX_VALUE);
		importImageBtn.setOnAction(e -> {
			if (project == null) {
				Dialogs.showWarningNotification("Import Images", "Please create or open a project first!");
				return;
			}
			
			// Use file chooser for multiple selection
			javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
			chooser.setTitle("Import Images to Project");
			chooser.getExtensionFilters().addAll(
				new javafx.stage.FileChooser.ExtensionFilter("Image Files", "*.svs", "*.tif", "*.tiff", "*.ndpi", "*.vsi", "*.mrxs", "*.scn"),
				new javafx.stage.FileChooser.ExtensionFilter("All Files", "*.*")
			);
			
			List<java.io.File> files = chooser.showOpenMultipleDialog(qupath.getStage());
			if (files != null && !files.isEmpty()) {
				// Convert files to URIs
				List<String> uris = files.stream()
					.map(f -> f.toURI().toString())
					.collect(Collectors.toList());
				
				// Import to project
				ProjectCommands.promptToImportImages(qupath, uris.toArray(new String[0]));
				
				// Refresh tree
				refreshTree(null);
				logger.info("Imported {} images to project", files.size());
			}
		});
		
		// Add Batch Import Folder button
		Button batchImportBtn = new Button("📁 Batch Import Folder");
		batchImportBtn.setMaxWidth(Double.MAX_VALUE);
		batchImportBtn.setTooltip(new Tooltip("Import all images and annotations from folder structure"));
		batchImportBtn.setOnAction(e -> showBatchImportDialog());
		
		// Remove TitledPane wrapper - use mdTree directly
		mdTree.setMaxHeight(Double.MAX_VALUE);
		
		// Note: Tree already has a context menu set up in getPopup(), don't override it here
		
		// Enable drag and drop for images
		mdTree.setOnDragOver(event -> {
			if (event.getDragboard().hasFiles()) {
				event.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
			}
			event.consume();
		});
		
		mdTree.setOnDragDropped(event -> {
			var db = event.getDragboard();
			boolean success = false;
			if (db.hasFiles()) {
				for (var file : db.getFiles()) {
					try {
						// Try to open as image
						qupath.openImage(qupath.getViewer(), file.getAbsolutePath(), true, true);
						success = true;
					} catch (IOException ex) {
						logger.error("Failed to open image: {}", file, ex);
					}
				}
			}
			event.setDropCompleted(success);
			event.consume();
		});
		
		// Wrap content in collapsible container
		VBox imageContentBox = new VBox(10);
		imageContentBox.getChildren().addAll(importImageBtn, batchImportBtn, mdTree);
		VBox.setVgrow(mdTree, javafx.scene.layout.Priority.ALWAYS);
		
		// Create minimized placeholder
		Button imageExpandBtn = new Button("▶ Images");
		imageExpandBtn.setMaxWidth(Double.MAX_VALUE);
		imageExpandBtn.setStyle("-fx-font-size: 12px; -fx-padding: 5;");
		VBox imagePlaceholder = new VBox(imageExpandBtn);
		imagePlaceholder.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 5;");
		imagePlaceholder.setVisible(false);
		imagePlaceholder.setManaged(false);
		
		// Collapse button functionality - hide panel and show placeholder
		imageCollapseBtn.setOnAction(e -> {
			imagePanel.setVisible(false);
			imagePanel.setManaged(false);
			imagePlaceholder.setVisible(true);
			imagePlaceholder.setManaged(true);
		});
		
		// Expand button functionality - show panel and hide placeholder
		imageExpandBtn.setOnAction(e -> {
			imagePanel.setVisible(true);
			imagePanel.setManaged(true);
			imagePlaceholder.setVisible(false);
			imagePlaceholder.setManaged(false);
		});
		
		imagePanel.getChildren().addAll(imageHeaderBox, imageContentBox);
		
		// Wrap imagePanel and placeholder in a container
		javafx.scene.layout.StackPane imageContainer = new javafx.scene.layout.StackPane();
		imageContainer.getChildren().addAll(imagePanel, imagePlaceholder);
		
		// Note: tree.setOnMouseClicked is already set in constructor for double-click to open
		// Don't override it here - the ordered selection will use Ctrl+Click separately
		
		// Right: Object import area
		VBox objectPanel = new VBox(10);
		objectPanel.setStyle("-fx-border-color: #c0c0c0; -fx-border-width: 1; -fx-padding: 10; -fx-background-color: #fafafa;");
		
		// Object label with selection count and collapse button
		Button objectCollapseBtn = new Button("▼");
		objectCollapseBtn.setStyle("-fx-font-size: 12px; -fx-padding: 2 8 2 8;");
		
		Label objectLabel = new Label("Objects (JSON/GeoJSON)");
		objectLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
		
		Label objectSelectionLabel = new Label("Selected: 0 (Ctrl+Click: order | Shift: range)");
		objectSelectionLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
		orderedObjectSelection.addListener((javafx.collections.ListChangeListener<ObjectFileEntry>) c -> {
			objectSelectionLabel.setText("Ordered: " + orderedObjectSelection.size() + 
				" (" + getObjectSelectionOrderDisplay() + ")");
		});
		
		Button clearObjectSelectionBtn = new Button("Clear Selection");
		clearObjectSelectionBtn.setOnAction(e -> {
			orderedObjectSelection.clear();
			refreshObjectTree();
		});
		clearObjectSelectionBtn.setMaxWidth(Double.MAX_VALUE);
		
		HBox objectLabelBox = new HBox(5, objectCollapseBtn, objectLabel, objectSelectionLabel);
		objectLabelBox.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 5;");
		VBox objectHeaderBox = new VBox(5, objectLabelBox, clearObjectSelectionBtn);
		
		// Import Objects button (supports multiple selection)
		Button importObjectBtn = new Button("Import Objects...");
		importObjectBtn.setMaxWidth(Double.MAX_VALUE);
		importObjectBtn.setOnAction(e -> importObjectFilesToList());
		
		// Object tree view with folder grouping
		objectTreeView = new TreeView<>();
		objectTreeView.setShowRoot(false);
		objectTreeView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		
		// Build initial tree
		refreshObjectTree();
		
		// Custom cell factory with selection order
		objectTreeView.setCellFactory(tv -> {
			var cell = new TreeCell<ObjectTreeItem>() {
				@Override
				protected void updateItem(ObjectTreeItem item, boolean empty) {
					super.updateItem(item, empty);
					if (empty || item == null) {
						setText(null);
						setGraphic(null);
						setStyle("");
					} else {
						if (item.isFolder) {
							// Folder node
							setText(String.format("📁 %s (%d)", item.name, getTreeItem().getChildren().size()));
							setStyle("-fx-font-weight: bold;");
						} else {
							// File node
							ObjectFileEntry fileEntry = item.entry;
							int orderIndex = orderedObjectSelection.indexOf(fileEntry);
							if (orderIndex >= 0) {
								setText(String.format("[%d] %s", orderIndex + 1, fileEntry.toString()));
								setStyle("-fx-background-color: lightblue; -fx-font-weight: bold;");
							} else {
								setText(fileEntry.toString());
								setStyle("");
							}
						}
					}
				}
			};
			
			// Enable drag FROM this tree to viewer
			cell.setOnDragDetected(event -> {
				ObjectTreeItem item = cell.getItem();
				if (item != null && !item.isFolder && item.entry != null && !item.entry.objects.isEmpty()) {
					javafx.scene.input.Dragboard db = cell.startDragAndDrop(javafx.scene.input.TransferMode.COPY);
					javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
					
					content.putFiles(java.util.Collections.singletonList(item.entry.file));
					content.putString(String.format("QuPath Objects: %d objects from %s", 
						item.entry.objects.size(), item.entry.file.getName()));
					
					db.setContent(content);
					logger.info("Started dragging: {} ({} objects)", item.entry.file.getName(), item.entry.objects.size());
				}
				event.consume();
			});
			
			return cell;
		});
		
		// Add click handler for ordered selection
		objectTreeView.setOnMouseClicked(event -> {
			if ((event.isControlDown() || event.isMetaDown()) && !event.isShiftDown()) {
				// Ctrl/Cmd + Click for ordered selection toggle
				var selected = objectTreeView.getSelectionModel().getSelectedItem();
				if (selected != null && selected.getValue() != null && !selected.getValue().isFolder) {
					ObjectFileEntry entry = selected.getValue().entry;
					if (entry != null) {
						if (orderedObjectSelection.contains(entry)) {
							orderedObjectSelection.remove(entry);
						} else {
							orderedObjectSelection.add(entry);
						}
						refreshObjectTree();
					}
				}
			}
		});
		
		// Add right-click context menu for objects
		ContextMenu objectContextMenu = new ContextMenu();
		
		MenuItem showPathItem = new MenuItem("📋 Copy file path");
		showPathItem.setOnAction(e -> {
			var selected = getSelectedObjectEntry();
			if (selected != null) {
				javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
				javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
				content.putString(selected.file.getAbsolutePath());
				clipboard.setContent(content);
				System.out.println("✅ 路径已复制: " + selected.file.getAbsolutePath());
				Dialogs.showInfoNotification("Copy Path", "Path copied to clipboard:\n" + selected.file.getAbsolutePath());
			}
		});
		
		MenuItem showFolderItem = new MenuItem("📂 Open in Finder");
		showFolderItem.setOnAction(e -> {
			var selected = getSelectedObjectEntry();
			if (selected != null) {
				try {
					java.io.File folder = selected.file.getParentFile();
					if (folder != null && folder.exists()) {
						java.awt.Desktop.getDesktop().open(folder);
						System.out.println("✅ 已打开文件夹: " + folder.getAbsolutePath());
					}
				} catch (Exception ex) {
					logger.error("Failed to open folder", ex);
				}
			}
		});
		
		MenuItem reloadObjectItem = new MenuItem("🔄 Reload");
		reloadObjectItem.setOnAction(e -> {
			var selected = getSelectedObjectEntry();
			if (selected != null) {
				objectFileList.remove(selected);
				loadObjectFile(selected.file, selected.folderName);
				refreshObjectTree();
				System.out.println("✅ 重新加载: " + selected.file.getName());
			}
		});
		
		MenuItem removeObjectItem = new MenuItem("❌ Remove Selected");
		removeObjectItem.setOnAction(e -> {
			var selectedEntries = getSelectedObjectEntries();
			if (!selectedEntries.isEmpty()) {
				int count = selectedEntries.size();
				
				// Remove from both ordered selection and main list
				for (var item : selectedEntries) {
					orderedObjectSelection.remove(item);
					objectFileList.remove(item);
				}
				
				saveObjectListForProject();
				refreshObjectTree();
				
				if (count == 1) {
					System.out.println("✅ 已删除: " + selectedEntries.get(0).file.getName());
				} else {
					System.out.println("✅ 已删除 " + count + " 个对象文件");
					Dialogs.showInfoNotification("Remove Objects", "Removed " + count + " object file(s)");
				}
			}
		});
		
		objectContextMenu.getItems().addAll(
			showPathItem,
			showFolderItem,
			new SeparatorMenuItem(),
			reloadObjectItem,
			removeObjectItem
		);
		
		objectTreeView.setContextMenu(objectContextMenu);
		
		// Enable drag and drop TO this tree (import files)
		objectTreeView.setOnDragOver(event -> {
			if (event.getDragboard().hasFiles()) {
				event.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
			}
			event.consume();
		});
		
		objectTreeView.setOnDragDropped(event -> {
			var db = event.getDragboard();
			boolean success = false;
			if (db.hasFiles()) {
				for (var file : db.getFiles()) {
					if (file.getName().endsWith(".json") || file.getName().endsWith(".geojson")) {
						loadObjectFile(file);
						success = true;
					}
				}
				// Auto-save and refresh tree after drag-drop import
				if (success) {
					saveObjectListForProject();
					refreshObjectTree();
				}
			}
			event.setDropCompleted(success);
			event.consume();
		});
		
		// Wrap content in collapsible container
		VBox objectContentBox = new VBox(10);
		objectContentBox.getChildren().addAll(importObjectBtn, objectTreeView);
		VBox.setVgrow(objectTreeView, javafx.scene.layout.Priority.ALWAYS);
		
		// Create minimized placeholder
		Button objectExpandBtn = new Button("▶ Objects");
		objectExpandBtn.setMaxWidth(Double.MAX_VALUE);
		objectExpandBtn.setStyle("-fx-font-size: 12px; -fx-padding: 5;");
		VBox objectPlaceholder = new VBox(objectExpandBtn);
		objectPlaceholder.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 5;");
		objectPlaceholder.setVisible(false);
		objectPlaceholder.setManaged(false);
		
		// Collapse button functionality
		objectCollapseBtn.setOnAction(e -> {
			objectPanel.setVisible(false);
			objectPanel.setManaged(false);
			objectPlaceholder.setVisible(true);
			objectPlaceholder.setManaged(true);
		});
		
		// Expand button functionality
		objectExpandBtn.setOnAction(e -> {
			objectPanel.setVisible(true);
			objectPanel.setManaged(true);
			objectPlaceholder.setVisible(false);
			objectPlaceholder.setManaged(false);
		});
		
		objectPanel.getChildren().addAll(objectHeaderBox, objectContentBox);
		
		// Wrap objectPanel and placeholder in a container
		javafx.scene.layout.StackPane objectContainer = new javafx.scene.layout.StackPane();
		objectContainer.getChildren().addAll(objectPanel, objectPlaceholder);
		
		// Add containers to SplitPane with resizable divider
		topSection.getItems().addAll(imageContainer, objectContainer);
		// Set initial divider position to 50-50 split
		topSection.setDividerPositions(0.5);
		// Style for the divider - lighter color
		topSection.setStyle("-fx-background-color: #ffffff; -fx-padding: 0;");
		
		// Middle: Merge buttons
		Button mergeButton = new Button("⬇ Merge Selected (Ordered)");
		mergeButton.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
		mergeButton.setMaxWidth(Double.MAX_VALUE);
		mergeButton.setOnAction(e -> mergeImageWithObjects());
		
		Button batchMergeButton = new Button("🔗 Batch Merge by Name");
		batchMergeButton.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
		batchMergeButton.setMaxWidth(Double.MAX_VALUE);
		batchMergeButton.setOnAction(e -> showBatchMergeDialog());
		
		HBox mergeBox = new HBox(5, mergeButton, batchMergeButton);
		mergeBox.setPadding(new Insets(10, 0, 10, 0));
		HBox.setHgrow(mergeButton, javafx.scene.layout.Priority.ALWAYS);
		HBox.setHgrow(batchMergeButton, javafx.scene.layout.Priority.ALWAYS);
		
		// Lower section: Merged results
		VBox bottomSection = new VBox(10);
		bottomSection.setStyle("-fx-border-color: #c0c0c0; -fx-border-width: 1; -fx-padding: 10; -fx-background-color: #fafafa;");
		
		// Merged label with collapse button
		Button mergedCollapseBtn = new Button("▼");
		mergedCollapseBtn.setStyle("-fx-font-size: 12px; -fx-padding: 2 8 2 8;");
		
		Label mergedLabel = new Label("Merged Results (Double-click to open)");
		mergedLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
		
		HBox mergedLabelBox = new HBox(5, mergedCollapseBtn, mergedLabel);
		mergedLabelBox.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 5;");
		
		mergedResultListView = new ListView<>(mergedResultList);
		mergedResultListView.setPlaceholder(new Label("No merged items yet.\nSelect image and check objects, then click Merge."));
		
		// Enable multiple selection
		mergedResultListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		
		// Double-click to open merged entry
		mergedResultListView.setOnMouseClicked(event -> {
			if (event.getClickCount() == 2) {
				MergedResultEntry selected = mergedResultListView.getSelectionModel().getSelectedItem();
				if (selected != null) {
					openMergedEntry(selected);
				}
			}
		});
		
		// Buttons for merged results
		Button openMergedBtn = new Button("Open");
		openMergedBtn.setOnAction(e -> {
			var selectedItems = mergedResultListView.getSelectionModel().getSelectedItems();
			if (selectedItems != null && !selectedItems.isEmpty()) {
				// Open the first selected item
				openMergedEntry(selectedItems.get(0));
				if (selectedItems.size() > 1) {
					Dialogs.showInfoNotification("Open Merged", 
						"Opened first selected item. Multiple items selected.");
				}
			}
		});
		
		Button removeMergedBtn = new Button("Remove Selected");
		removeMergedBtn.setOnAction(e -> {
			var selectedItems = mergedResultListView.getSelectionModel().getSelectedItems();
			if (selectedItems != null && !selectedItems.isEmpty()) {
				// Make a copy to avoid concurrent modification
				var itemsToRemove = new ArrayList<>(selectedItems);
				int count = itemsToRemove.size();
				
				mergedResultList.removeAll(itemsToRemove);
				
				if (count == 1) {
					System.out.println("✅ 已删除1个合并结果");
				} else {
					System.out.println("✅ 已删除 " + count + " 个合并结果");
					Dialogs.showInfoNotification("Remove Merged", "Removed " + count + " merged item(s)");
				}
			}
		});
		
		Button clearMergedBtn = new Button("Clear All");
		clearMergedBtn.setOnAction(e -> mergedResultList.clear());
		
		HBox mergedBtnBox = new HBox(5, openMergedBtn, removeMergedBtn, clearMergedBtn);
		
		// Wrap content in collapsible container
		VBox mergedContentBox = new VBox(10);
		mergedContentBox.getChildren().addAll(mergedResultListView, mergedBtnBox);
		VBox.setVgrow(mergedResultListView, javafx.scene.layout.Priority.ALWAYS);
		
		// Create minimized placeholder
		Button mergedExpandBtn = new Button("▶ Merged Results");
		mergedExpandBtn.setMaxWidth(Double.MAX_VALUE);
		mergedExpandBtn.setStyle("-fx-font-size: 12px; -fx-padding: 5;");
		VBox mergedPlaceholder = new VBox(mergedExpandBtn);
		mergedPlaceholder.setStyle("-fx-background-color: #f0f0f0; -fx-padding: 5;");
		mergedPlaceholder.setVisible(false);
		mergedPlaceholder.setManaged(false);
		
		// Collapse button functionality
		mergedCollapseBtn.setOnAction(e -> {
			bottomSection.setVisible(false);
			bottomSection.setManaged(false);
			mergedPlaceholder.setVisible(true);
			mergedPlaceholder.setManaged(true);
		});
		
		// Expand button functionality
		mergedExpandBtn.setOnAction(e -> {
			bottomSection.setVisible(true);
			bottomSection.setManaged(true);
			mergedPlaceholder.setVisible(false);
			mergedPlaceholder.setManaged(false);
		});
		
		bottomSection.getChildren().addAll(mergedLabelBox, mergedContentBox);
		
		// Wrap bottomSection and placeholder in a container
		javafx.scene.layout.StackPane mergedContainer = new javafx.scene.layout.StackPane();
		mergedContainer.getChildren().addAll(bottomSection, mergedPlaceholder);
		
		// Combine top section with merge button
		VBox topWithMerge = new VBox(10, topSection, mergeBox);
		VBox.setVgrow(topSection, javafx.scene.layout.Priority.ALWAYS);
		
		// Use vertical SplitPane to make top/bottom resizable
		javafx.scene.control.SplitPane verticalSplit = new javafx.scene.control.SplitPane();
		verticalSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
		verticalSplit.getItems().addAll(topWithMerge, mergedContainer);
		// Set initial split: 60% top, 40% bottom
		verticalSplit.setDividerPositions(0.6);
		
		// Simple mode view - just the image tree
		BorderPane simpleView = new BorderPane();
		simpleView.setCenter(mdTree);
		simpleView.setPadding(new Insets(10));
		
		// Default to All mode (full layout)
		enhancedPanel.setCenter(verticalSplit);
		
		// Mode toggle logic
		modeToggleBtn.selectedProperty().addListener((obs, oldVal, newVal) -> {
			if (newVal) {
				// Simple mode - only show image tree
				enhancedPanel.setCenter(simpleView);
				modeToggleBtn.setText("All Mode");
			} else {
				// All mode - show full layout
				enhancedPanel.setCenter(verticalSplit);
				modeToggleBtn.setText("Simple Mode");
			}
		});
		
		return enhancedPanel;
	}
	
	/**
	 * Refresh object tree to show folder structure
	 */
	private void refreshObjectTree() {
		// Group objects by folder
		Map<String, List<ObjectFileEntry>> folderGroups = new TreeMap<>();
		
		for (ObjectFileEntry entry : objectFileList) {
			String folder = entry.folderName != null ? entry.folderName : "Ungrouped";
			folderGroups.computeIfAbsent(folder, k -> new ArrayList<>()).add(entry);
		}
		
		// Build tree structure
		TreeItem<ObjectTreeItem> root = new TreeItem<>(new ObjectTreeItem("Objects", true, null));
		
		for (Map.Entry<String, List<ObjectFileEntry>> group : folderGroups.entrySet()) {
			TreeItem<ObjectTreeItem> folderNode = new TreeItem<>(
				new ObjectTreeItem(group.getKey(), true, null)
			);
			folderNode.setExpanded(true);
			
			for (ObjectFileEntry entry : group.getValue()) {
				TreeItem<ObjectTreeItem> fileNode = new TreeItem<>(
					new ObjectTreeItem(entry.file.getName(), false, entry)
				);
				folderNode.getChildren().add(fileNode);
			}
			
			root.getChildren().add(folderNode);
		}
		
		objectTreeView.setRoot(root);
	}
	
	/**
	 * Get selected object entry from tree
	 */
	private ObjectFileEntry getSelectedObjectEntry() {
		var selected = objectTreeView.getSelectionModel().getSelectedItem();
		if (selected != null && selected.getValue() != null && !selected.getValue().isFolder) {
			return selected.getValue().entry;
		}
		return null;
	}
	
	/**
	 * Get all selected object entries from tree
	 */
	private List<ObjectFileEntry> getSelectedObjectEntries() {
		var selectedItems = objectTreeView.getSelectionModel().getSelectedItems();
		List<ObjectFileEntry> entries = new ArrayList<>();
		
		if (selectedItems != null) {
			for (var item : selectedItems) {
				if (item != null && item.getValue() != null && !item.getValue().isFolder) {
					if (item.getValue().entry != null) {
						entries.add(item.getValue().entry);
					}
				}
			}
		}
		
		return entries;
	}
	
	/**
	 * Helper class for object tree items
	 */
	static class ObjectTreeItem {
		final String name;
		final boolean isFolder;
		final ObjectFileEntry entry;
		
		ObjectTreeItem(String name, boolean isFolder, ObjectFileEntry entry) {
			this.name = name;
			this.isFolder = isFolder;
			this.entry = entry;
		}
	}
	
	/**
	 * Import object files to the list (supports multiple selection)
	 */
	private void importObjectFilesToList() {
		javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
		chooser.setTitle("Import Object Files");
		chooser.getExtensionFilters().addAll(
			new javafx.stage.FileChooser.ExtensionFilter("JSON/GeoJSON Files", "*.json", "*.geojson"),
			new javafx.stage.FileChooser.ExtensionFilter("All Files", "*.*")
		);
		
		List<java.io.File> files = chooser.showOpenMultipleDialog(qupath.getStage());
		if (files != null && !files.isEmpty()) {
			// Create progress dialog
			int totalFiles = files.size();
			javafx.scene.control.ProgressBar progressBar = new javafx.scene.control.ProgressBar(0);
			progressBar.setPrefWidth(400);
			
			javafx.scene.control.Label progressLabel = new javafx.scene.control.Label("准备导入...");
			
			javafx.scene.layout.VBox progressBox = new javafx.scene.layout.VBox(10);
			progressBox.getChildren().addAll(progressLabel, progressBar);
			progressBox.setPadding(new javafx.geometry.Insets(20));
			
			javafx.scene.control.Dialog<Boolean> progressDialog = new javafx.scene.control.Dialog<>();
			progressDialog.setTitle("导入对象文件");
			progressDialog.setHeaderText("正在导入 " + totalFiles + " 个文件...");
			progressDialog.getDialogPane().setContent(progressBox);
			progressDialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CANCEL);
			
			// Show progress dialog
			progressDialog.show();
			
			// Import files in background thread
			javafx.concurrent.Task<Integer> importTask = new javafx.concurrent.Task<>() {
				@Override
				protected Integer call() throws Exception {
					int count = 0;
					for (int i = 0; i < files.size(); i++) {
						if (isCancelled()) {
							break;
						}
						
						java.io.File file = files.get(i);
						int fileNum = i + 1;
						
						javafx.application.Platform.runLater(() -> {
							progressLabel.setText(String.format("导入第 %d/%d 个文件: %s", 
								fileNum, totalFiles, file.getName()));
							progressBar.setProgress((double) fileNum / totalFiles);
						});
						
						try {
				loadObjectFile(file);
							count++;
							System.out.println(String.format("✅ [%d/%d] 导入成功: %s", fileNum, totalFiles, file.getName()));
						} catch (Exception e) {
							System.err.println(String.format("❌ [%d/%d] 导入失败: %s - %s", 
								fileNum, totalFiles, file.getName(), e.getMessage()));
							logger.error("Failed to import {}", file.getName(), e);
						}
					}
					return count;
				}
			};
			
			importTask.setOnSucceeded(e -> {
				progressDialog.close();
				int imported = importTask.getValue();
				logger.info("Imported {} object files", imported);
			
			// Auto-save after import
			saveObjectListForProject();
				
				// Refresh tree
				refreshObjectTree();
				
				// Show completion message
				Dialogs.showInfoNotification("导入完成", 
					String.format("成功导入 %d/%d 个对象文件", imported, totalFiles));
			});
			
			importTask.setOnCancelled(e -> {
				progressDialog.close();
				logger.info("Import cancelled by user");
			});
			
			importTask.setOnFailed(e -> {
				progressDialog.close();
				Dialogs.showErrorNotification("导入失败", "导入对象文件时出错");
			});
			
			// Handle cancel button
			progressDialog.getDialogPane().lookupButton(javafx.scene.control.ButtonType.CANCEL)
				.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
					importTask.cancel();
					event.consume();
				});
			
			// Run task
			Thread importThread = new Thread(importTask);
			importThread.setDaemon(true);
			importThread.start();
		}
	}
	
	/**
	 * Load a single object file
	 */
	private void loadObjectFile(java.io.File file) {
		loadObjectFile(file, null);
	}
	
	/**
	 * Load a single object file with folder name
	 */
	private void loadObjectFile(java.io.File file, String folderName) {
		try {
			logger.info("Loading object file: {}", file.getAbsolutePath());
			
			String json = new String(java.nio.file.Files.readAllBytes(file.toPath()));
			logger.debug("File content length: {} bytes", json.length());
			
			// Parse JSON
			com.google.gson.JsonElement element = GsonTools.getInstance().fromJson(json, com.google.gson.JsonElement.class);
			
			// Use JsonToGeoJsonHandler logic for conversion
			com.google.gson.JsonElement geoJsonElement = element;
			
			// Check if already GeoJSON
			boolean isGeoJSON = false;
			if (element.isJsonObject()) {
				com.google.gson.JsonObject obj = element.getAsJsonObject();
				if (obj.has("type")) {
					String type = obj.get("type").getAsString();
					isGeoJSON = type.equals("Feature") || type.equals("FeatureCollection");
				}
			}
			
			// If not GeoJSON, try to convert
			if (!isGeoJSON) {
				System.out.println("⚠️ JSON不是GeoJSON格式，尝试转换: " + file.getName());
				logger.info("JSON is not GeoJSON, attempting conversion for: {}", file.getName());
				
				geoJsonElement = JsonToGeoJsonHandler.convertToGeoJSON(element);
				
				if (geoJsonElement != null) {
					System.out.println("✅ 转换成功！");
					logger.info("Successfully converted to GeoJSON");
					
					// Debug: print converted structure
					if (geoJsonElement.isJsonObject()) {
						var obj = geoJsonElement.getAsJsonObject();
						System.out.println("  转换后type: " + (obj.has("type") ? obj.get("type").getAsString() : "无"));
						if (obj.has("features") && obj.get("features").isJsonArray()) {
							System.out.println("  features数量: " + obj.get("features").getAsJsonArray().size());
						}
					}
				} else {
					System.out.println("❌ 转换失败，使用原始JSON");
					logger.warn("Conversion returned null, using original");
					geoJsonElement = element;
				}
			}
			
			// Parse objects
			System.out.println("开始解析对象: " + file.getName());
			List<qupath.lib.objects.PathObject> objects = GsonTools.parseObjectsFromGeoJSON(geoJsonElement);
			
			if (objects == null) {
				System.out.println("❌ 解析返回null！");
				objects = new java.util.ArrayList<>();
			}
			
			System.out.println("解析结果: " + objects.size() + " objects");
			
			ObjectFileEntry entry = new ObjectFileEntry(file, objects, folderName);
			objectFileList.add(entry);
			
			// Refresh tree after adding
			Platform.runLater(() -> refreshObjectTree());
			
			if (objects.isEmpty()) {
				System.out.println("⚠️ " + file.getName() + " - 0 objects!");
				logger.warn("Loaded {} with 0 objects - check JSON format", file.getName());
			} else {
				System.out.println("✅ 成功加载: " + file.getName() + " (" + objects.size() + " objects)");
				logger.info("Successfully loaded: {} ({} objects)", file.getName(), objects.size());
			}
			
		} catch (Exception e) {
			logger.error("Failed to load object file: {}", file.getName(), e);
			// Still add to list with 0 objects
			ObjectFileEntry entry = new ObjectFileEntry(file, new java.util.ArrayList<>(), folderName);
			objectFileList.add(entry);
			
			// Refresh tree after adding
			Platform.runLater(() -> refreshObjectTree());
		}
	}
	
	/**
	 * Merge selected images with objects in order - add to merged results list
	 */
	private void mergeImageWithObjects() {
		logger.info("Merge button clicked");
		
		// Use ordered selections if available
		List<ProjectImageEntry<BufferedImage>> imagesToMerge = orderedImageSelection.isEmpty() ? 
			Collections.singletonList(getSelectedEntry()) : new ArrayList<>(orderedImageSelection);
		
		List<ObjectFileEntry> objectsToMerge = orderedObjectSelection.isEmpty() ?
			objectFileList.stream().filter(ObjectFileEntry::isSelected).collect(Collectors.toList()) :
			new ArrayList<>(orderedObjectSelection);
		
		// Validate selections
		if (imagesToMerge.isEmpty() || imagesToMerge.get(0) == null) {
			Dialogs.showWarningNotification("Merge", 
				"Please select image(s)!\n\nTip: Ctrl+Click to select images in order.");
			return;
		}
		
		if (objectsToMerge.isEmpty()) {
			Dialogs.showWarningNotification("Merge", 
				"Please select object file(s)!\n\nTip: Ctrl+Click to select objects in order.");
			return;
		}
		
		// Pair images with objects by order
		int pairCount = Math.min(imagesToMerge.size(), objectsToMerge.size());
		StringBuilder pairInfo = new StringBuilder("Pairing order:\n");
		
		for (int i = 0; i < pairCount; i++) {
			ProjectImageEntry<BufferedImage> imageEntry = imagesToMerge.get(i);
			ObjectFileEntry objEntry = objectsToMerge.get(i);
			
			MergedResultEntry mergedEntry = new MergedResultEntry(imageEntry, objEntry);
			mergedResultList.add(mergedEntry);
			
			pairInfo.append(String.format("%d. %s + %s\n", 
				i + 1, 
				imageEntry.getImageName(), 
				objEntry.file.getName()));
		}
		
		logger.info("Created {} ordered merged pairs", pairCount);
		
		// Show info about unpaired items
		if (imagesToMerge.size() != objectsToMerge.size()) {
			int unpaired = Math.abs(imagesToMerge.size() - objectsToMerge.size());
			pairInfo.append(String.format("\n⚠️ %d item(s) not paired", unpaired));
		}
		
		Dialogs.showInfoNotification("Merge Complete", pairInfo.toString());
		
		// Clear selections after merge
		orderedImageSelection.clear();
		orderedObjectSelection.clear();
		tree.refresh();
		refreshObjectTree();
	}
	
	/**
	 * Show batch import folder dialog
	 */
	private void showBatchImportDialog() {
		if (project == null) {
			Dialogs.showWarningNotification("Batch Import", "Please create or open a project first!");
			return;
		}
		
		// Select root folder
		javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
		chooser.setTitle("Select Root Folder for Batch Import");
		java.io.File rootFolder = chooser.showDialog(qupath.getStage());
		
		if (rootFolder == null || !rootFolder.isDirectory()) {
			return;
		}
		
		// Scan folder structure
		logger.info("Scanning folder: {}", rootFolder.getAbsolutePath());
		
		// Find all subfolders
		java.io.File[] subfolders = rootFolder.listFiles(java.io.File::isDirectory);
		if (subfolders == null || subfolders.length == 0) {
			Dialogs.showWarningNotification("Batch Import", "No subfolders found in selected directory");
			return;
		}
		
		// Show progress dialog
		var result = Dialogs.showYesNoDialog("Batch Import",
			String.format("Found %d subfolder(s).\n\nEach subfolder will be imported as a group.\nImages and JSON files will be automatically paired.\n\nContinue?",
				subfolders.length));
		
		if (!result) {
			return;
		}
		
		// Process each subfolder
		int totalImages = 0;
		int totalJsons = 0;
		
		for (java.io.File subfolder : subfolders) {
			String folderName = subfolder.getName();
			logger.info("Processing folder: {}", folderName);
			
			// Find all image files
			java.io.File[] imageFiles = subfolder.listFiles((dir, name) -> {
				String lower = name.toLowerCase();
				return lower.endsWith(".svs") || lower.endsWith(".tif") || lower.endsWith(".tiff") ||
					   lower.endsWith(".ndpi") || lower.endsWith(".vsi") || lower.endsWith(".mrxs") ||
					   lower.endsWith(".scn") || lower.endsWith(".jpg") || lower.endsWith(".png");
			});
			
			if (imageFiles != null && imageFiles.length > 0) {
				// Import images to project
				int successCount = 0;
				int failCount = 0;
				
				for (java.io.File imageFile : imageFiles) {
					try {
						String uriString = imageFile.toURI().toString();
						String fileName = imageFile.getName();
						logger.debug("Attempting to import: {}", uriString);
						
						var support = ImageServerProvider.getPreferredUriImageSupport(
							BufferedImage.class, uriString);
						
						if (support != null && !support.getBuilders().isEmpty()) {
							var builder = support.getBuilders().get(0);
							var entry = project.addImage(builder);
							
							if (entry != null) {
								// Set image name to filename
								entry.setImageName(fileName);
								
								// Add folder metadata
								entry.putMetadataValue("Folder", folderName);
								
								successCount++;
								totalImages++;
								logger.info("✓ Imported: {} → {}", folderName, fileName);
							} else {
								failCount++;
								logger.warn("✗ Failed to create entry for: {}", fileName);
							}
						} else {
							failCount++;
							logger.warn("✗ No server support for: {}", fileName);
						}
					} catch (Exception ex) {
						failCount++;
						logger.error("✗ Exception importing {}: {}", imageFile.getName(), ex.getMessage(), ex);
					}
				}
				
				logger.info("Folder {}: {} succeeded, {} failed", folderName, successCount, failCount);
			}
			
			// Find JSON files
			java.io.File[] jsonFiles = subfolder.listFiles((dir, name) -> {
				String lower = name.toLowerCase();
				return lower.endsWith(".json") || lower.endsWith(".geojson");
			});
			
			if (jsonFiles != null && jsonFiles.length > 0) {
				for (java.io.File jsonFile : jsonFiles) {
					loadObjectFile(jsonFile, folderName);
					totalJsons++;
				}
				logger.info("Imported {} JSON file(s) from folder: {}", jsonFiles.length, folderName);
			}
		}
		
		// Save project changes
		try {
			project.syncChanges();
		} catch (IOException ex) {
			logger.error("Failed to save project", ex);
		}
		
		// Auto-save object list
		saveObjectListForProject();
		
		// Set sorting by Folder to show tree structure
		if (model != null) {
			model.setMetadataKey("Folder");
		}
		
		// Refresh trees with folder sorting
		refreshTree(null);
		refreshObjectTree();
		
		// Show completion message
		String message = String.format(
			"Batch import completed!\n\n" +
			"Imported:\n" +
			"  • %d images\n" +
			"  • %d JSON annotation files\n" +
			"  • %d folders\n\n" +
			"Images are now organized by folder in the tree.",
			totalImages, totalJsons, subfolders.length
		);
		
		Dialogs.showInfoNotification("Batch Import Complete", message);
		
		logger.info("Batch import complete: {} images, {} JSONs from {} folders",
			totalImages, totalJsons, subfolders.length);
	}
	
	/**
	 * Show batch merge dialog with regex matching
	 */
	private void showBatchMergeDialog() {
		Dialog<ButtonType> dialog = new Dialog<>();
		dialog.initOwner(qupath.getStage());
		dialog.setTitle("Batch Merge by Name");
		dialog.setHeaderText("Match images and objects using regular expressions");
		dialog.setResizable(true);
		
		// Main layout
		BorderPane mainPane = new BorderPane();
		mainPane.setPrefSize(900, 600);
		
		// Top: Regex input section
		GridPane regexPane = new GridPane();
		regexPane.setHgap(10);
		regexPane.setVgap(10);
		regexPane.setPadding(new Insets(10));
		
		// Image regex
		Label imageRegexLabel = new Label("Image Name Pattern (Regex):");
		TextField imageRegexField = new TextField("(.+)\\.(svs|tif|tiff|ndpi|jpg)$");
		imageRegexField.setPromptText("e.g., (.+)\\.(svs|tif)$ to extract filename without extension");
		imageRegexField.setPrefWidth(350);
		
		// Object regex
		Label objectRegexLabel = new Label("Object Name Pattern (Regex):");
		TextField objectRegexField = new TextField("(.+)\\.(json|geojson)$");
		objectRegexField.setPromptText("e.g., (.+)\\.json$ to extract filename without extension");
		objectRegexField.setPrefWidth(350);
		
		regexPane.add(imageRegexLabel, 0, 0);
		regexPane.add(imageRegexField, 0, 1);
		regexPane.add(objectRegexLabel, 1, 0);
		regexPane.add(objectRegexField, 1, 1);
		
		// Middle: Results section with SplitPane
		javafx.scene.control.SplitPane resultsPane = new javafx.scene.control.SplitPane();
		
		// Left: Matched images
		VBox imageResultBox = new VBox(5);
		Label imageMatchLabel = new Label("Matched Images (0)");
		imageMatchLabel.setStyle("-fx-font-weight: bold;");
		ListView<MatchedItem<ProjectImageEntry<BufferedImage>>> imageMatchList = new ListView<>();
		imageMatchList.setPlaceholder(new Label("No matches. Adjust regex pattern."));
		imageResultBox.getChildren().addAll(imageMatchLabel, imageMatchList);
		VBox.setVgrow(imageMatchList, javafx.scene.layout.Priority.ALWAYS);
		
		// Right: Matched objects
		VBox objectResultBox = new VBox(5);
		Label objectMatchLabel = new Label("Matched Objects (0)");
		objectMatchLabel.setStyle("-fx-font-weight: bold;");
		ListView<MatchedItem<ObjectFileEntry>> objectMatchList = new ListView<>();
		objectMatchList.setPlaceholder(new Label("No matches. Adjust regex pattern."));
		objectResultBox.getChildren().addAll(objectMatchLabel, objectMatchList);
		VBox.setVgrow(objectMatchList, javafx.scene.layout.Priority.ALWAYS);
		
		resultsPane.getItems().addAll(imageResultBox, objectResultBox);
		resultsPane.setDividerPositions(0.5);
		
		// Bottom: Paired results
		VBox pairResultBox = new VBox(5);
		pairResultBox.setPadding(new Insets(10));
		Label pairLabel = new Label("Paired Results (0 pairs)");
		pairLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
		ListView<String> pairList = new ListView<>();
		pairList.setPlaceholder(new Label("Pairs will appear here after matching"));
		pairList.setPrefHeight(120);
		pairResultBox.getChildren().addAll(pairLabel, pairList);
		
		// Auto-match button
		Button matchButton = new Button("🔍 Match by Name");
		matchButton.setStyle("-fx-font-size: 13px;");
		matchButton.setMaxWidth(Double.MAX_VALUE);
		
		// Store matched pairs
		ObservableList<MatchPair> matchedPairs = FXCollections.observableArrayList();
		
		// Match logic
		Runnable performMatch = () -> {
			try {
				String imagePattern = imageRegexField.getText();
				String objectPattern = objectRegexField.getText();
				
				if (imagePattern.isEmpty() || objectPattern.isEmpty()) {
					Dialogs.showWarningNotification("Batch Merge", "Please enter both regex patterns");
			return;
		}
		
				// Match images
				ObservableList<MatchedItem<ProjectImageEntry<BufferedImage>>> imageMatches = 
					FXCollections.observableArrayList();
				java.util.regex.Pattern imgPattern = java.util.regex.Pattern.compile(imagePattern);
				
				if (project != null) {
					for (ProjectImageEntry<BufferedImage> entry : project.getImageList()) {
						java.util.regex.Matcher matcher = imgPattern.matcher(entry.getImageName());
						if (matcher.find() && matcher.groupCount() > 0) {
							String extractedName = matcher.group(1);
							imageMatches.add(new MatchedItem<>(entry, extractedName));
						}
					}
				}
				
				// Match objects
				ObservableList<MatchedItem<ObjectFileEntry>> objectMatches = 
					FXCollections.observableArrayList();
				java.util.regex.Pattern objPattern = java.util.regex.Pattern.compile(objectPattern);
				
				for (ObjectFileEntry entry : objectFileList) {
					java.util.regex.Matcher matcher = objPattern.matcher(entry.file.getName());
					if (matcher.find() && matcher.groupCount() > 0) {
						String extractedName = matcher.group(1);
						objectMatches.add(new MatchedItem<>(entry, extractedName));
					}
				}
				
				// Update lists
				imageMatchList.setItems(imageMatches);
				objectMatchList.setItems(objectMatches);
				imageMatchLabel.setText(String.format("Matched Images (%d)", imageMatches.size()));
				objectMatchLabel.setText(String.format("Matched Objects (%d)", objectMatches.size()));
				
				// Pair by name
				matchedPairs.clear();
				ObservableList<String> pairStrings = FXCollections.observableArrayList();
				
				for (MatchedItem<ProjectImageEntry<BufferedImage>> imgItem : imageMatches) {
					for (MatchedItem<ObjectFileEntry> objItem : objectMatches) {
						if (imgItem.extractedName.equals(objItem.extractedName)) {
							matchedPairs.add(new MatchPair(imgItem.item, objItem.item, imgItem.extractedName));
							pairStrings.add(String.format("✓ %s: %s + %s", 
								imgItem.extractedName,
								imgItem.item.getImageName(),
								objItem.item.file.getName()));
							break; // One-to-one pairing
						}
					}
				}
				
				pairList.setItems(pairStrings);
				pairLabel.setText(String.format("Paired Results (%d pairs)", matchedPairs.size()));
				
				if (matchedPairs.isEmpty()) {
					Dialogs.showInfoNotification("No Pairs", 
						"No matching pairs found. Check that extracted names match.");
				} else {
					logger.info("Found {} matched pairs", matchedPairs.size());
				}
				
			} catch (java.util.regex.PatternSyntaxException ex) {
				Dialogs.showErrorMessage("Regex Error", "Invalid regular expression:\n" + ex.getMessage());
			}
		};
		
		matchButton.setOnAction(e -> performMatch.run());
		
		// Auto-match on Enter key
		imageRegexField.setOnAction(e -> performMatch.run());
		objectRegexField.setOnAction(e -> performMatch.run());
		
		pairResultBox.getChildren().add(matchButton);
		
		// Layout
		VBox centerBox = new VBox(10, resultsPane, pairResultBox);
		VBox.setVgrow(resultsPane, javafx.scene.layout.Priority.ALWAYS);
		
		mainPane.setTop(regexPane);
		mainPane.setCenter(centerBox);
		
		dialog.getDialogPane().setContent(mainPane);
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
		
		// Perform initial match
		Platform.runLater(performMatch);
		
		// Handle OK
		Optional<ButtonType> result = dialog.showAndWait();
		if (result.isPresent() && result.get() == ButtonType.OK) {
			if (matchedPairs.isEmpty()) {
				Dialogs.showWarningNotification("Batch Merge", "No pairs to merge!");
				return;
			}
			
			// Create merged entries
		int count = 0;
			for (MatchPair pair : matchedPairs) {
				MergedResultEntry mergedEntry = new MergedResultEntry(pair.imageEntry, pair.objectEntry);
			mergedResultList.add(mergedEntry);
			count++;
		}
		
			logger.info("Batch merged {} pairs", count);
			Dialogs.showInfoNotification("Batch Merge Complete", 
				String.format("Created %d merged items", count));
		}
	}
	
	/**
	 * Helper class for matched items with extracted name
	 */
	private static class MatchedItem<T> {
		final T item;
		final String extractedName;
		
		MatchedItem(T item, String extractedName) {
			this.item = item;
			this.extractedName = extractedName;
		}
		
		@Override
		public String toString() {
			if (item instanceof ProjectImageEntry) {
				return String.format("[%s] %s", extractedName, ((ProjectImageEntry<?>)item).getImageName());
			} else if (item instanceof ObjectFileEntry) {
				return String.format("[%s] %s", extractedName, ((ObjectFileEntry)item).file.getName());
			}
			return extractedName;
		}
	}
	
	/**
	 * Helper class for matched pairs
	 */
	private static class MatchPair {
		final ProjectImageEntry<BufferedImage> imageEntry;
		final ObjectFileEntry objectEntry;
		final String matchedName;
		
		MatchPair(ProjectImageEntry<BufferedImage> imageEntry, ObjectFileEntry objectEntry, String matchedName) {
			this.imageEntry = imageEntry;
			this.objectEntry = objectEntry;
			this.matchedName = matchedName;
		}
	}
	
	/**
	 * Get display string for image selection order
	 */
	private String getImageSelectionOrderDisplay() {
		if (orderedImageSelection.isEmpty()) {
			return "none";
		}
		if (orderedImageSelection.size() <= 3) {
			return orderedImageSelection.stream()
				.map(e -> e.getImageName())
				.collect(Collectors.joining(", "));
		}
		return String.format("%s, ... (%d total)", 
			orderedImageSelection.get(0).getImageName(),
			orderedImageSelection.size());
	}
	
	/**
	 * Get display string for object selection order
	 */
	private String getObjectSelectionOrderDisplay() {
		if (orderedObjectSelection.isEmpty()) {
			return "none";
		}
		if (orderedObjectSelection.size() <= 3) {
			return orderedObjectSelection.stream()
				.map(e -> e.file.getName())
				.collect(Collectors.joining(", "));
		}
		return String.format("%s, ... (%d total)", 
			orderedObjectSelection.get(0).file.getName(),
			orderedObjectSelection.size());
	}
	
	/**
	 * Open a merged entry (image + objects)
	 */
	private void openMergedEntry(MergedResultEntry entry) {
		try {
			logger.info("Opening merged entry: {}", entry);
			
			// Open image
			qupath.openImageEntry(entry.imageEntry);
			
			// Wait for image to load
			Thread.sleep(500);
			
			// Import objects
			var imageData = qupath.getViewer().getImageData();
			if (imageData != null) {
				imageData.getHierarchy().addObjects(entry.objectEntry.objects);
				imageData.getHierarchy().fireHierarchyChangedEvent(this);
				
				logger.info("Loaded {} with {} objects", 
					entry.imageEntry.getImageName(), entry.objectEntry.objects.size());
				
				Dialogs.showInfoNotification("Opened", 
					String.format("Loaded %s with %d objects", 
						entry.imageEntry.getImageName(), 
						entry.objectEntry.objects.size()));
			}
		} catch (Exception e) {
			logger.error("Failed to open merged entry", e);
			Dialogs.showErrorMessage("Open Error", "Failed to open: " + e.getMessage());
		}
	}
	
	/**
	 * Object file entry with checkbox and folder info
	 */
	static class ObjectFileEntry {
		final java.io.File file;
		final List<qupath.lib.objects.PathObject> objects;
		final String folderName; // Add folder name for grouping
		private javafx.beans.property.BooleanProperty selected = 
			new javafx.beans.property.SimpleBooleanProperty(false);
		
		ObjectFileEntry(java.io.File file, List<qupath.lib.objects.PathObject> objects) {
			this(file, objects, null);
		}
		
		ObjectFileEntry(java.io.File file, List<qupath.lib.objects.PathObject> objects, String folderName) {
			this.file = file;
			this.objects = objects;
			this.folderName = folderName;
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
	 * Merged result entry (image + objects)
	 */
	static class MergedResultEntry {
		final ProjectImageEntry<BufferedImage> imageEntry;
		final ObjectFileEntry objectEntry;
		
		MergedResultEntry(ProjectImageEntry<BufferedImage> imageEntry, ObjectFileEntry objectEntry) {
			this.imageEntry = imageEntry;
			this.objectEntry = objectEntry;
		}
		
		@Override
		public String toString() {
			return String.format("%s + %s (%d objects)", 
				imageEntry.getImageName(), 
				objectEntry.file.getName(), 
				objectEntry.objects.size());
		}
	}

	/**
	 * Save current object list for the project
	 */
	private void saveObjectListForProject() {
		if (project == null || objectFileList.isEmpty()) {
			return;
		}
		
		try {
			java.io.File projectFile = project.getPath().toFile();
			java.io.File objectListFile = new java.io.File(projectFile.getParent(), 
				projectFile.getName().replace(".qpproj", "_objects.json"));
			
			// Save object file paths
			com.google.gson.JsonArray array = new com.google.gson.JsonArray();
			for (ObjectFileEntry entry : objectFileList) {
				com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
				obj.addProperty("path", entry.file.getAbsolutePath());
				obj.addProperty("objectCount", entry.objects.size());
				obj.addProperty("selected", entry.isSelected());
				array.add(obj);
			}
			
			String json = GsonTools.getInstance(true).toJson(array);
			java.nio.file.Files.writeString(objectListFile.toPath(), json);
			
			logger.info("Saved {} object files for project", objectFileList.size());
		} catch (Exception e) {
			logger.error("Failed to save object list", e);
		}
	}
	
	/**
	 * Load object list for the project
	 */
	private void loadObjectListForProject() {
		objectFileList.clear();
		
		if (project == null) {
			return;
		}
		
		try {
			java.io.File projectFile = project.getPath().toFile();
			java.io.File objectListFile = new java.io.File(projectFile.getParent(), 
				projectFile.getName().replace(".qpproj", "_objects.json"));
			
			if (!objectListFile.exists()) {
				logger.debug("No saved object list found for project");
				return;
			}
			
			// Load object file paths
			String json = java.nio.file.Files.readString(objectListFile.toPath());
			com.google.gson.JsonArray array = GsonTools.getInstance().fromJson(json, com.google.gson.JsonArray.class);
			
			int totalFiles = array.size();
			
			// If more than 5 files, show progress dialog
			if (totalFiles > 5) {
				// Create progress dialog
				javafx.scene.control.ProgressBar progressBar = new javafx.scene.control.ProgressBar(0);
				progressBar.setPrefWidth(400);
				
				javafx.scene.control.Label progressLabel = new javafx.scene.control.Label("准备加载对象文件...");
				
				javafx.scene.layout.VBox progressBox = new javafx.scene.layout.VBox(10);
				progressBox.getChildren().addAll(progressLabel, progressBar);
				progressBox.setPadding(new javafx.geometry.Insets(20));
				
				javafx.scene.control.Dialog<Boolean> progressDialog = new javafx.scene.control.Dialog<>();
				progressDialog.setTitle("加载项目对象");
				progressDialog.setHeaderText("正在加载 " + totalFiles + " 个对象文件...");
				progressDialog.getDialogPane().setContent(progressBox);
				
				progressDialog.show();
				
				// Load files
				javafx.concurrent.Task<Integer> loadTask = new javafx.concurrent.Task<>() {
					@Override
					protected Integer call() throws Exception {
						int loaded = 0;
						for (int i = 0; i < array.size(); i++) {
							com.google.gson.JsonElement element = array.get(i);
							if (element.isJsonObject()) {
								com.google.gson.JsonObject obj = element.getAsJsonObject();
								String path = obj.get("path").getAsString();
								java.io.File file = new java.io.File(path);
								
								int fileNum = i + 1;
								javafx.application.Platform.runLater(() -> {
									progressLabel.setText(String.format("加载第 %d/%d 个: %s", 
										fileNum, totalFiles, file.getName()));
									progressBar.setProgress((double) fileNum / totalFiles);
								});
								
								if (file.exists()) {
									loadObjectFile(file);
									
									// Restore selected state
									if (obj.has("selected") && obj.get("selected").getAsBoolean()) {
										javafx.application.Platform.runLater(() -> {
											if (!objectFileList.isEmpty()) {
												objectFileList.get(objectFileList.size() - 1).setSelected(true);
											}
										});
									}
									loaded++;
								} else {
									logger.warn("Object file no longer exists: {}", path);
								}
							}
						}
						return loaded;
					}
				};
				
				loadTask.setOnSucceeded(e -> {
					progressDialog.close();
					int loaded = loadTask.getValue();
					logger.info("Loaded {} object files for project", loaded);
					refreshObjectTree();
				});
				
				loadTask.setOnFailed(e -> {
					progressDialog.close();
					logger.error("Failed to load object list", loadTask.getException());
				});
				
				Thread loadThread = new Thread(loadTask);
				loadThread.setDaemon(true);
				loadThread.start();
				
			} else {
				// For small number of files, load directly without progress dialog
			for (com.google.gson.JsonElement element : array) {
				if (element.isJsonObject()) {
					com.google.gson.JsonObject obj = element.getAsJsonObject();
					String path = obj.get("path").getAsString();
					java.io.File file = new java.io.File(path);
					
					if (file.exists()) {
						loadObjectFile(file);
						
						// Restore selected state
						if (obj.has("selected") && obj.get("selected").getAsBoolean()) {
							if (!objectFileList.isEmpty()) {
								objectFileList.get(objectFileList.size() - 1).setSelected(true);
							}
						}
					} else {
						logger.warn("Object file no longer exists: {}", path);
					}
				}
			}
			
			logger.info("Loaded {} object files for project", objectFileList.size());
				refreshObjectTree();
			}
		} catch (Exception e) {
			logger.error("Failed to load object list", e);
		}
	}
	
	/**
	 * Set the project.
	 * @param project
	 * @return true if the project is now set (even if unchanged), false if the project change was thwarted or cancelled.
	 */
	public boolean setProject(final Project<BufferedImage> project) {
		if (this.project == project)
			return true;
		
		// Save object list for current project before switching
		if (this.project != null) {
			saveObjectListForProject();
		}
		
		this.project = project;
		ProjectTreeRowCell.resetUriStatus();
		
		// Load object list for new project
		if (project != null) {
			loadObjectListForProject();
		}
		model = new ProjectImageTreeModel(project);
		tree.setRoot(model.getRoot());
		tree.getRoot().setExpanded(true);
		Platform.runLater(() -> {
			if (tree.getParent() != null) {
				tree.getParent().layout();
			}
		});
		return true;
	}
	
	/**
	 * Refresh the current project, updating the displayed entries.
	 * Note that this must be called on the JavaFX Application thread.
	 * If it is not, the request will be passed to the application thread 
	 * (and therefore not processed immediately).
	 */
	public void refreshProject() {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> refreshProject());
			return;
		}
		refreshTree(null);
	}

	private void ensureServerInWorkspace(final ImageData<BufferedImage> imageData) {
		if (imageData == null || project == null)
			return;
		
		if (project.getEntry(imageData) != null)
			return;

		var entry = ProjectCommands.addSingleImageToProject(project, imageData.getServer(), null);
		if (entry != null) {
			boolean expanded = tree.getRoot() != null && tree.getRoot().isExpanded();
			tree.setRoot(model.getRoot());
			setSelectedEntry(tree, tree.getRoot(), new ImageRow(project.getEntry(imageData)));
			syncProject(project);
			if (expanded)
				tree.getRoot().setExpanded(true);
			// Copy the ImageData to the current entry
			if (!entry.hasImageData()) {
				try {
					logger.info("Copying ImageData to {}", entry);
					entry.saveImageData(imageData);
				} catch (IOException e) {
					logger.error("Unable to save ImageData: " + e.getLocalizedMessage(), e);
				}
			}
			qupath.refreshProject();
		}
	}

	@Override
	public void changed(final ObservableValue<? extends ImageData<BufferedImage>> source, final ImageData<BufferedImage> imageDataOld, final ImageData<BufferedImage> imageDataNew) {
		if (imageDataNew == null || project == null)
			return;
		ProjectImageEntry<BufferedImage> entry = project.getEntry(imageDataNew);
		if (entry == null) {
			// Previously we gave a choice... now we force the image to be included in the project to avoid complications
//			if (DisplayHelpers.showYesNoDialog("Add to project", "Add " + imageDataNew.getServer().getShortServerName() + " to project?"))
				ensureServerInWorkspace(imageDataNew);
		} else if (!entry.equals(getSelectedEntry()))
			setSelectedEntry(tree, tree.getRoot(), getSelectedImageRow());
		if (tree != null) {
			tree.refresh();
		}
	}

	private static <T> boolean setSelectedEntry(TreeView<T> treeView, TreeItem<T> item, final T object) {
		if (item.getValue() == object) {
			treeView.getSelectionModel().select(item);
			return true;
		}
		for (TreeItem<T> child : item.getChildren()) {
			if (setSelectedEntry(treeView, child, object))
				return true;
		}
		return false;
	}
	
	/**
	 * Resize an image so that its dimensions fit inside thumbnailWidth x thumbnailHeight.
	 * 
	 * Note: this assumes the image can be drawn to a Graphics object.
	 * 
	 * @param imgThumbnail
	 * @return
	 */
	private BufferedImage resizeForThumbnail(BufferedImage imgThumbnail) {
		double scale = Math.min((double)thumbnailWidth / imgThumbnail.getWidth(), (double)thumbnailHeight / imgThumbnail.getHeight());
		if (scale > 1)
			return imgThumbnail;
		BufferedImage imgThumbnail2 = new BufferedImage((int)(imgThumbnail.getWidth() * scale), (int)(imgThumbnail.getHeight() * scale), imgThumbnail.getType());
		Graphics2D g2d = imgThumbnail2.createGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g2d.drawImage(imgThumbnail, 0, 0, imgThumbnail2.getWidth(), imgThumbnail2.getHeight(), null);
		g2d.dispose();
		return imgThumbnail2;
	}

	private ImageData<BufferedImage> getCurrentImageData() {
		return qupath.getViewer().getImageData();
	}

//	File getBaseDirectory() {
//		return Projects.getBaseDirectory(project);
//	}
//
//	File getProjectFile() {
//		File dirBase = getBaseDirectory();
//		if (dirBase == null || !dirBase.isDirectory())
//			return null;
//		return new File(dirBase, "project" + ProjectIO.getProjectExtension());
//	}

	private boolean isCurrentImage(final ProjectImageEntry<BufferedImage> entry) {
		ImageData<BufferedImage> imageData = getCurrentImageData();
		if (imageData == null || entry == null || project == null)
			return false;
		return project.getEntry(imageData) == entry;
	}
	
	/**
	 * Get all the {@link ProjectTreeRow.ImageRow}s included in the current selection. 
	 * This means that selecting a {@link ProjectTreeRow.MetadataRow} will return all the {@link ProjectTreeRow.ImageRow}s that belong to it.
	 * @return a collection of ImageRows
	 * @see #getSelectedImageRow()
	 */
	private Collection<ImageRow> getSelectedImageRowsRecursive() {
		List<TreeItem<ProjectTreeRow>> selected = tree.getSelectionModel().getSelectedItems();
		if (selected == null)
			return Collections.emptyList();
		return selected.stream().map(p -> {
			if (p.getValue().getType() == ProjectTreeRow.Type.IMAGE)
				return Collections.singletonList((ImageRow)p.getValue());
			return getImageRowsRecursive(p, null);
		}).flatMap(Collection::stream).collect(Collectors.toSet());
	}
	
	private ProjectImageEntry<BufferedImage> getSelectedEntry() {
		TreeItem<ProjectTreeRow> selected = tree.getSelectionModel().getSelectedItem();
		if (selected != null && selected.getValue().getType() == ProjectTreeRow.Type.IMAGE)
			return ((ImageRow)selected.getValue()).getEntry();
		return null;
	}
	
	/**
	 * Get the selected {@link ProjectTreeRow.ImageRow} and return it. 
	 * If nothing is selected or the selected {@link ProjectTreeRow} is not an image entry, return {@code null}.
	 * @return selected ImageRow
	 */
	private ImageRow getSelectedImageRow() {
		TreeItem<ProjectTreeRow> selected = tree.getSelectionModel().getSelectedItem();
		if (selected != null && selected.getValue().getType() == ProjectTreeRow.Type.IMAGE)
			return (ImageRow)selected.getValue();
		return null;
	}

	/**
	 * Get all {@code ImageRow} objects under the specified {@code item}.
	 * <p>
	 * E.g. If supplied with a {@link ProjectTreeRow.MetadataRow}, a collection of 
	 * all {@code ImageRow}s under it will be returned. If supplied with 
	 * a {@link ProjectTreeRow.RootRow}, a collection of all {@code ImageRow}s 
	 * under it will be returned (ignoring the {@link ProjectTreeRow.MetadataRow}s
	 * @param item the start node
	 * @param entries collection where to store the ImageRows found
	 * @return a collection of ImageRows
	 */
	private static Collection<ImageRow> getImageRowsRecursive(final TreeItem<ProjectTreeRow> item, Collection<ImageRow> entries) {
		if (entries == null)
			entries = new HashSet<>();
		if (item.getValue().getType() == ProjectTreeRow.Type.IMAGE)
			entries.add((ImageRow)item.getValue());
		for (TreeItem<ProjectTreeRow> child : item.getChildren()) {
			entries = getImageRowsRecursive(child, entries);
		}
		return entries;
	}
	
	/**
	 * Get all the distinct entry metadata values possible for a given key.
	 * @param metadataKey
	 * @return set of distinct metadata values
	 */
	private Set<String> getAllMetadataValues(String metadataKey) {
		return project.getImageList().stream()
				.map(entry -> {
					try {
						return getDefaultValue(entry, metadataKey);
					} catch (IOException ex) {
						// Could only happen because of call to getURIs()
						logger.warn("Could not get the URI(s) of " + entry.getImageName(), ex.getLocalizedMessage());
					}
					return UNDEFINED_VALUE;
				})
				.collect(Collectors.toSet());
	}
	
	/**
	 * Gets the value of the entry for the specified key.
	 * E.g. if key == URI, the value returned will be the entry's URI.
	 * This method should be used to get sorting values that
	 * are not specifically part of an entry's metadata.
	 * @param <T>
	 * @param entry 
	 * @param key
	 * @return value
	 * @throws IOException 
	 */
	private static <T> String getDefaultValue(ProjectImageEntry<T> entry, String key) throws IOException {
		if (key.equals(BaseMetadataKeys.URI.getKey())) {
			var URIs = entry.getURIs();
			var it = URIs.iterator();
			
			if (URIs.size() == 0)
				return UNDEFINED_VALUE;
			
			if (URIs.size() == 1) {
				URI uri = it.next();
				String fullURI = uri.getPath();
				if (uri.getAuthority() != null)
					return "[remote] " + uri.getAuthority() + fullURI;
				return fullURI.substring(fullURI.lastIndexOf("/")+1, fullURI.length());
			}
			return "Multiple URIs";
		} else if (key.equals(BaseMetadataKeys.IMAGE_NAME.getKey())) {
			return entry.getImageName();
		}  else if (key.equals(BaseMetadataKeys.ENTRY_ID.getKey())) {
			return entry.getID();
		}
		var value = entry.getMetadataValue(key);
		return value == null ? UNASSIGNED_NODE : value;
	}
	
	/**
	 * This method rebuilds the tree, optionally selecting an {@link ImageRow} afterwards.
	 * @param imageToSelect image to select after refreshing
	 */
	private void refreshTree(ImageRow imageToSelect) {
		Platform.runLater(() -> {
			tree.setRoot(null);
			tree.setRoot(new ProjectTreeRowItem(new ProjectTreeRow.RootRow(project)));
			tree.getRoot().setExpanded(true);
			
			try {
				var listOfChildren = tree.getRoot().getChildren();
				for (int i = 0; i < listOfChildren.size(); i++) {
					if (imageToSelect == null) {
						if (listOfChildren.get(i).getChildren().size() > 0) {
							listOfChildren.get(i).setExpanded(true);
							tree.refresh();
							break;
						}							
					} else {
						for (var child: listOfChildren) {
							if (child.getValue().getType() == Type.METADATA) {
								for (var imageChild: child.getChildren()) {
									if (imageChild.getValue().equals(imageToSelect)) {
										child.setExpanded(true);
										tree.getSelectionModel().select(imageChild);
										break;
									}
								}
							} else if (child.getValue().equals(imageToSelect))
								tree.getSelectionModel().select(child);
						}
					}
				}
			} catch (Exception ex) {
				logger.error("Error getting children objects in the ProjectBrowser", ex);
			}
		});
	}

	private Action createSortByKeyAction(final String name, final String key) {
		return new Action(name, e -> {
			if (model == null)
				return;
			model.setMetadataKey(key);
			ImageRow selectedImageRow = getSelectedImageRow();
			refreshTree(selectedImageRow);
		});
	}

	
	/**
	 * Prompt the user to set a new name for a ProjectImageEntry.
	 * 
	 * @param entry
	 * @return true if the entry was changed, false otherwise.
	 */
	private boolean setProjectEntryImageName(final ProjectImageEntry<BufferedImage> entry) {
		Project<BufferedImage> project = qupath.getProject();
		if (project == null) {
			logger.error("Cannot set image name - project is null");
			return false;
		}
		if (entry == null) {
			logger.error("Cannot set image name - entry is null");
			return false;
		}
		
		String name = Dialogs.showInputDialog("Set Image Name", "Enter the new image name", entry.getImageName());
		if (name == null)
			return false;
		
		if (name.trim().isEmpty() || name.equals(entry.getImageName())) {
			logger.warn("Cannot set image name to {} - will ignore", name);
			return false;
		}
		
		// Try to set the name
		boolean changed = setProjectEntryImageName(entry, name);
		if (changed) {
			for (var viewer : qupath.getAllViewers()) {
				var imageData = viewer.getImageData();
				if (imageData == null)
					continue;
				var currentEntry = project.getEntry(imageData);
				if (Objects.equals(entry, currentEntry)) {
					var server = imageData.getServer();
					if (!name.equals(server.getMetadata().getName())) {
						// We update via the ImageData so that a property update is fired
						var metadata2 = new ImageServerMetadata.Builder(server.getMetadata())
								.name(name)
								.build();
						imageData.updateServerMetadata(metadata2);
						// Bit of a cheat to force measurement table updates
						imageData.getHierarchy().fireHierarchyChangedEvent(this);
					}
				}
			}
			tree.refresh();
			qupath.refreshTitle();
		}
		return changed;
	}
	
	
	/**
	 * The the name for a specified ProjectImageEntry.
	 * 
	 * This works hard to do its job... including renaming any data files accordingly.
	 * 
	 * @param entry
	 * @param name
	 * @return
	 */
	private static synchronized <T> boolean setProjectEntryImageName(final ProjectImageEntry<T> entry, final String name) {
		
		if (entry.getImageName().equals(name)) {
			logger.warn("Project image name already set to {} - will be left unchanged", name);
			return false;
		}

		if (name == null) {
			logger.warn("Project entry name cannot be null!");
			return false;
		}

		entry.setImageName(name);
		
		return true;
	}
	
	private List<ImageRow> getAllImageRows() {
		if (!PathPrefs.maskImageNamesProperty().get())
			return project.getImageList().stream().map(entry -> new ImageRow(entry)).toList();
		
		// If 'mask names' is ticked, shuffle the image list for less biased analyses
		var imageList = project.getImageList();
		var indices = IntStream.range(0, imageList.size()).boxed().collect(Collectors.toCollection(ArrayList::new));
		Collections.shuffle(indices);
		return indices.stream().map(index -> new ImageRow(imageList.get(index))).toList();
	}

	private class ProjectImageTreeModel {

		private static final String SORT_KEY = "_SORT_KEY";
		private final ProjectTreeRowItem root;
		private String metadataKey;
		
		private ProjectImageTreeModel(final Project<?> project) {
			this.root = new ProjectTreeRowItem(new ProjectTreeRow.RootRow(project));

			if (project != null) {
				this.metadataKey = project.getMetadata().get(SORT_KEY);
			}
		}
		
		private String getMetadataKey() {
			return metadataKey;
		}
		
		/**
		 * Set the metadata key based on which the entries will be sorted.
		 * @param metadataKey
		 */
		private void setMetadataKey(String metadataKey) {
			this.metadataKey = metadataKey;

			if (metadataKey == null) {
				project.getMetadata().remove(SORT_KEY);
			} else {
				project.getMetadata().put(SORT_KEY, metadataKey);
			}
		}
		
		private ProjectTreeRowItem getRoot() {
			return root;
		}
	}

	private class ProjectTreeRowCell extends TreeCell<ProjectTreeRow> {
		
		private Tooltip tooltip = new Tooltip();

		private Node missingGraphic;

		private StackPane viewPane = new StackPane();
		private Canvas viewCanvas = new Canvas();
		private ImageView viewTooltip = new ImageView();

		private ProjectTreeRow objectCell = null;
		private BooleanProperty showTooltip = new SimpleBooleanProperty();

		private BooleanProperty urisMissing = new SimpleBooleanProperty(false);

		/**
		 * Cache whether or not URIs refer to missing files.
		 * We want to be able to inform the user when files are missing, but we don't want to call Files.exists()
		 * too often, so we retain the result.
		 * This means that, if the file was deleted or moved later, the user will need to refresh the project to see
		 * the change.
		 */
		private static Map<URI, UriUpdater.UriStatus> uriStatus = new ConcurrentHashMap<>();

		/**
		 * Reset the cache of URI statuses (called when a new project is opened).
		 */
		static void resetUriStatus() {
			uriStatus.clear();
		}

		private DoubleBinding viewWidth = Bindings.createDoubleBinding(
				() -> thumbnailSize.get().getWidth(),
				thumbnailSize);

		private DoubleBinding viewHeight = Bindings.createDoubleBinding(
				() -> thumbnailSize.get().getHeight(),
				thumbnailSize);
		
		private ProjectTreeRowCell() {
			viewTooltip.setFitHeight(250);
			viewTooltip.setFitWidth(250);
			viewTooltip.setPreserveRatio(true);
			viewCanvas.getStyleClass().add("project-thumbnail");
			viewCanvas.widthProperty().bind(viewWidth);
			viewCanvas.heightProperty().bind(viewHeight);
			viewPane.getChildren().add(viewCanvas);
			viewPane.prefWidthProperty().bind(viewCanvas.widthProperty());
			viewPane.prefHeightProperty().bind(viewCanvas.heightProperty());
			viewCanvas.opacityProperty().bind(
					Bindings.createDoubleBinding(() -> urisMissing.get() ? 0.2 : 1.0, urisMissing));

			missingGraphic = IconFactory.createNode(
					15, 15, PathIcons.WARNING);
			missingGraphic.getStyleClass().add("missing-uri");
			Tooltip.install(missingGraphic, new Tooltip("File not found"));

			viewPane.getChildren().add(missingGraphic);
			missingGraphic.visibleProperty().bind(urisMissing);

			// Avoid having the tooltip obscure any popup menu
			tooltipProperty().bind(Bindings.createObjectBinding(() -> {
				return showTooltip.get() && !contextMenuShowing.get() ? tooltip : null;
			}, contextMenuShowing, showTooltip));
			
			// Note: For project images, use double-click to open instead of drag-and-drop
			// This avoids conflicts with QuPath's existing drag-drop system
		}
		
		@Override
		public void updateItem(ProjectTreeRow item, boolean empty) {
			super.updateItem(item, empty);
			if (empty || item == null) {
                setText(null);
                setGraphic(null);
                showTooltip.set(false);
                return;
            }

			getStyleClass().setAll("tree-cell");
			urisMissing.set(false);

			if (item.getType() == ProjectTreeRow.Type.ROOT) {
				var children = getTreeItem().getChildren();
				setText(item.getDisplayableString() + (!children.isEmpty() ? " (" + children.size() + ")" : ""));
				setGraphic(null);
				return;
			} else if (item.getType() == ProjectTreeRow.Type.METADATA) {
				var children = getTreeItem().getChildren();
				// TODO: Try not to display count when grouping by ID
				setText(item.getDisplayableString() + (!children.isEmpty() ? " (" + children.size() + ")" : ""));
				setGraphic(null);
				return;
			}
			
			// IMAGE
			ProjectImageEntry<BufferedImage> entry = item.getType() == ProjectTreeRow.Type.IMAGE ? ProjectTreeRow.getEntry(item) : null;
			if (isCurrentImage(entry))
				getStyleClass().add("current-image");
			if (entry != null && !entry.hasImageData())
				getStyleClass().add("no-saved-data");
			
			// Check if this image is in ordered selection
			if (entry != null && orderedImageSelection.contains(entry)) {
				getStyleClass().add("ordered-selection");
				setStyle("-fx-background-color: lightblue;");
			} else {
				// Reset style when not selected
				setStyle("");
			}

			// Check for URIs
			if (entry != null && !PathPrefs.skipProjectUriChecksProperty().get()) {
				try {
					for (var uri : entry.getURIs()) {
						if (uriStatus.computeIfAbsent(uri, ProjectTreeRowCell::checkUri) == UriUpdater.UriStatus.MISSING) {
							urisMissing.set(true);
							break;
						}
					}
				} catch (IOException e) {
					logger.error("Exception checking URIs: {}", e.getMessage(), e);
				}
			}

			if (entry == null) {
				setText(item + " (" + getTreeItem().getChildren().size() + ")");
				tooltip.setText(item.toString());
                showTooltip.set(true);
				setGraphic(null);
			} else {
				setGraphic(viewPane);
				// Set whatever tooltip we have
				tooltip.setGraphic(null);
				showTooltip.set(true);

				// Show selection order in text
				if (orderedImageSelection.contains(entry)) {
					int orderIndex = orderedImageSelection.indexOf(entry);
					setText(String.format("[%d] %s", orderIndex + 1, entry.getImageName()));
				} else {
				setText(entry.getImageName());
				}
				if (urisMissing.get())
					tooltip.setText("Warning: At least one file is missing!\n\n" + entry.getSummary());
				else
					tooltip.setText(entry.getSummary());

				if (thumbnailSize.get() == ProjectThumbnailSize.HIDDEN) {
					viewTooltip.setImage(null);
					viewCanvas.getGraphicsContext2D().clearRect(0, 0, viewCanvas.getWidth(), viewCanvas.getHeight());
				} else {
					try {
						// Fetch the thumbnail or generate it if not present
						BufferedImage img = entry.getThumbnail();
						if (img != null) {
							Image image = SwingFXUtils.toFXImage(img, null);
							viewTooltip.setImage(image);
							tooltip.setGraphic(viewTooltip);
							GuiTools.paintImage(viewCanvas, image);
							objectCell = item;
							if (getGraphic() == null)
								setGraphic(viewPane);
						} else if (!serversFailed.contains(item)) {
							tooltip.setGraphic(viewTooltip);
							viewCanvas.getGraphicsContext2D().clearRect(0, 0, viewCanvas.getWidth(), viewCanvas.getHeight());
							executor.submit(() -> {
								final ProjectTreeRow objectTemp = getItem();
								final ProjectImageEntry<BufferedImage> entryTemp = ProjectTreeRow.getEntry(objectTemp);
								try {
									if (entryTemp != null && objectCell != objectTemp && entryTemp.getThumbnail() == null) {
										try (ImageServer<BufferedImage> server = entryTemp.getServerBuilder().build()) {
											entryTemp.setThumbnail(ProjectCommands.getThumbnailRGB(server));
											objectCell = objectTemp;
											tree.refresh();
										} catch (Exception ex) {
											logger.warn("Error opening ImageServer (thumbnail generation): {}", ex.getLocalizedMessage(), ex);
											Platform.runLater(() -> setGraphic(IconFactory.createNode(15, 15, PathIcons.INACTIVE_SERVER)));
											serversFailed.add(item);
										}
									}
								} catch (IOException ex) {
									logger.warn("Error getting thumbnail: {}", ex.getLocalizedMessage());
									Platform.runLater(() -> setGraphic(IconFactory.createNode(15, 15, PathIcons.INACTIVE_SERVER)));
									serversFailed.add(item);
								}
							});
						} else
							setGraphic(IconFactory.createNode(15, 15, PathIcons.INACTIVE_SERVER));
					} catch (Exception e) {
						setGraphic(IconFactory.createNode(15, 15, PathIcons.INACTIVE_SERVER));
						logger.warn("Unable to read thumbnail for {} ({})", entry.getImageName(), e.getMessage());
						serversFailed.add(item);
					}
				}
			}
		}


		private static UriUpdater.UriStatus checkUri(URI uri) {
			var path = GeneralTools.toPath(uri);
			// In case the check is slow, we make it possible for the user to turn it off.
			// See also https://github.com/qupath/qupath/pull/1298 for performance considerations.
			// TODO: Can we check if this is a network drive, to skip the test?
			if (path == null)
				return UriUpdater.UriStatus.UNKNOWN;
			else if (Files.notExists(path))
				return UriUpdater.UriStatus.MISSING;
			else
				return UriUpdater.UriStatus.EXISTS;
		}
	}

		
	/**
	 * TreeItem to help with the display of project objects.
	 */
	private class ProjectTreeRowItem extends TreeItem<ProjectTreeRow> {
		
		private boolean computed = false;
		
		private ProjectTreeRowItem(ProjectTreeRow obj) {
			super(obj);
		}

		@Override
		public boolean isLeaf() {
			if (computed)
				return super.getChildren().isEmpty();

            return switch (getValue().getType()) {
                case ROOT -> project != null && !project.getImageList().isEmpty() && project.getImageList().stream()
                        .noneMatch(entry -> predicateProperty.get().test(entry.getImageName()));
                case METADATA -> false;
                case IMAGE -> true;
                default ->
                        throw new IllegalArgumentException("Could not understand the type of the object: " + getValue().getType());
            };
			
		}
		
		@Override
		public ObservableList<TreeItem<ProjectTreeRow>> getChildren() {
			if (!isLeaf() && !computed) {
				ObservableList<TreeItem<ProjectTreeRow>> children = FXCollections.observableArrayList();
				var filter = predicateProperty.get();
				var metadataKey = model.getMetadataKey();
				switch (getValue().getType()) {
				case ROOT:
					if (project == null)
						break;
					
					if (metadataKey == null) {
						for (var row: getAllImageRows()) {
							if (!filter.test(row.getDisplayableString()))
								continue;
							children.add(new ProjectTreeRowItem(row));
						}
					} else {
						var values = new ArrayList<>(getAllMetadataValues(metadataKey));
						GeneralTools.smartStringSort(values);
						var potentialChildren = values.stream()
								.map(value -> new ProjectTreeRowItem(new MetadataRow(value)))
								.toList();
						// When sorting by name, we don't want to show grouped by name - since it looks weird,
						// with the name effectively being repeated twice
						if (metadataKey.equals(BaseMetadataKeys.IMAGE_NAME.getKey()))
							potentialChildren = potentialChildren.stream().flatMap(c -> {
								if (c.isLeaf())
									return Stream.empty();
								else
									return c.getChildren().stream();
							}).map(t -> (ProjectTreeRowItem)t).toList();
						// When sorting by entry ID, we want to expand everything - since there should only be one
						// entry per ID
						if (metadataKey.equals(BaseMetadataKeys.ENTRY_ID.getKey()))
							potentialChildren.forEach(c -> c.setExpanded(true));

						children.addAll(potentialChildren);
					}
					break;
				case METADATA:
					if (metadataKey == null || metadataKey.isEmpty())		// This should never happen
						break;
					
					for (var row: getAllImageRows()) {
						if (!filter.test(row.getDisplayableString()))
							continue;
						try {
							var value = getDefaultValue(ProjectTreeRow.getEntry(row), metadataKey);
							if (value != null && value.equals(((MetadataRow)getValue()).getDisplayableString()))
								children.add(new ProjectTreeRowItem(row));
						} catch (IOException ex) {
							logger.warn("Could not get {} from {}", metadataKey, row.getDisplayableString(), ex);
						}
					}
				case IMAGE:
					break;
				default:
					throw new IllegalArgumentException("Could not understand the type of the object: " + getValue().getType());
				}
				computed = true;
				super.getChildren().setAll(children);
			}
			return super.getChildren();
		}
	}

	enum ProjectThumbnailSize {
		HIDDEN, SMALL, MEDIUM, LARGE;

		private static int hiddenSize = 20;

		private double defaultHeight = 40;
		private double defaultWidth = 50;
		
		@Override
		public String toString() {
			switch(this) {
			case HIDDEN:
				return "Hidden";
			case LARGE:
				return "Large";
			case MEDIUM:
				return "Medium";
			case SMALL:
				return "Small";
			default:
				return super.toString();
			}
		}
		
		public double getWidth() {
			switch(this) {
			case LARGE:
				return defaultWidth * 3.0;
			case MEDIUM:
				return defaultWidth * 2.0;
			case HIDDEN:
				return hiddenSize;
			case SMALL:
			default:
				return defaultWidth;
			}
		}
		
		public double getHeight() {
			switch(this) {
			case LARGE:
				return defaultHeight * 3.0;
			case MEDIUM:
				return defaultHeight * 2.0;
			case HIDDEN:
				return hiddenSize;
			case SMALL:
			default:
				return defaultHeight;
			}
		}
	}
}