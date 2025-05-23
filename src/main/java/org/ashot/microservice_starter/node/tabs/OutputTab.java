package org.ashot.microservice_starter.node.tabs;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.ashot.microservice_starter.Controller;
import org.ashot.microservice_starter.Main;
import org.ashot.microservice_starter.data.icon.Icons;
import org.ashot.microservice_starter.registry.ControllerRegistry;
import org.ashot.microservice_starter.utils.OutputSearch;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

public class OutputTab extends Tab {
    private static final Logger logger = LoggerFactory.getLogger(OutputTab.class);
    private final VirtualizedScrollPane<CodeArea> scrollPane;
    private final CodeArea codeArea;
    private final Process process;
    private final OutputTabOptions outputTabOptions;
    private OutputSearch search;
    private TextField searchField;
    private final VBox outputSearchOptions;
    private boolean usedScrolling = false;
    private boolean searchVisible = false;

    public OutputTab(CodeArea codeArea, Process process, String name) {
        this.outputTabOptions = new OutputTabOptions(this);
        this.codeArea = codeArea;
        this.process = process;
        this.scrollPane = new VirtualizedScrollPane<>(codeArea);
        this.outputSearchOptions = setupSearchOptions();
        setupOutputTab(name);
    }

    public void setupOutputTab(String name) {
        this.codeArea.getStyleClass().addAll("command-output-container", Main.getDarkModeSetting() ? "dark-mode" : "light-mode", Main.getDarkModeSetting() ? "dark-mode-text" : "light-mode-text");
        this.codeArea.setEditable(false);
        this.codeArea.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (e.getDeltaY() < 0) {
                if (scrollPane.getTotalHeightEstimate() - scrollPane.getEstimatedScrollY() <= 1000) {
                    this.usedScrolling = false;
                }
            } else {
                this.usedScrolling = true;
            }
        });
        this.scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        this.scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        this.setText(name.replace("\"", ""));
        this.setContent(new VBox(scrollPane));
        VBox.setVgrow(codeArea, Priority.ALWAYS);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        this.setClosable(true);
        this.setOnClosed(_ -> this.process.destroy());
        this.setupUserInput();
    }

    private void setupUserInput() {
        this.setOnSelectionChanged(_ -> {
            if (isSelected() && searchVisible) {
                showSearch();
            } else {
                if (searchVisible) {
                    closeSearch();
                    searchVisible = true;
                } else {
                    closeSearch();
                }
            }
            this.getSearchOuterContainer().setOnKeyPressed(this::handleSearchTogglingInput);
            this.codeArea.setOnKeyPressed(this::handleCodeAreaUserInput);
        });
    }

    private void handleSearchTogglingInput(KeyEvent event){
        if (event.isControlDown() && event.getCode() == KeyCode.F && isSelected()) {
            toggleSearch();
        }
    }

    public void toggleSearch(){
        if (!this.searchVisible) {
            showSearch();
        } else {
            closeSearch();
        }
    }

    private void handleCodeAreaUserInput(KeyEvent event) {
        try {
            if (event.isControlDown() && event.getCode() == KeyCode.C) {
                this.process.destroy();
                process.getOutputStream().flush();
                Platform.runLater(() -> appendColoredLine("CTRL + C"));
                return;
            } else if (event.isControlDown() && event.getCode() == KeyCode.F) {
                return;
            } else if (event.getCode() == KeyCode.ENTER) {
                process.getOutputStream().write('\n');
            } else {
                process.getOutputStream().write((event.getText()).getBytes());
            }
            process.getOutputStream().flush();
            Platform.runLater(() -> appendColoredLine(event.getText()));
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }

    private void handleSearchFieldUserInput(KeyEvent event){
        if (event.isShiftDown() && event.getCode() == KeyCode.ENTER) {
            usedScrolling = true;
            search.performBackwardSearch(search.getCurrentInput());
        } else if (event.getCode() == KeyCode.ENTER) {
            usedScrolling = true;
            search.performForwardSearch(search.getCurrentInput());
        }
    }

    private VBox getSearchOuterContainer() {
        return (VBox) ControllerRegistry.get("main", Controller.class).getTabs().getParent().getParent();
    }

    private void showSearch() {
        this.searchVisible = true;
        search.setActive(true);
        Platform.runLater(() -> {
            getSearchOuterContainer().getChildren().add(2, this.outputSearchOptions);
            this.searchField.requestFocus();
        });
    }

    private void closeSearch() {
        this.searchVisible = false;
        search.setActive(false);
        Platform.runLater(() -> getSearchOuterContainer().getChildren().remove(this.outputSearchOptions));
    }


    private VBox setupSearchOptions() {
        int FIND_CONTAINER_WIDTH = 250;
        Label findResults = new Label();
        search = new OutputSearch(findResults, codeArea);
        searchField = new TextField();
        searchField.setOnKeyPressed(this::handleSearchFieldUserInput);
        searchField.textProperty().addListener((_, _, input) -> {
            usedScrolling = true;
            search.resetFindIndexToStart();
            search.performForwardSearch(input);
        });
        searchField.setPrefWidth(FIND_CONTAINER_WIDTH);
        Button closeBtn = new Button("", Icons.getCloseButtonIcon(24));
        closeBtn.setOnAction(_ -> this.closeSearch());
        closeBtn.getStyleClass().add("no-outline-btn");
        Label label = new Label("Find");
        BorderPane headerContainer = new BorderPane();
        headerContainer.setLeft(label);
        headerContainer.setPrefWidth(FIND_CONTAINER_WIDTH);
        headerContainer.setRight(closeBtn);
        headerContainer.setPadding(new Insets(0, 5, 0, 5));

        findResults.setLabelFor(searchField);
        findResults.setPadding(new Insets(0, 5, 0, 5));

        VBox innerContainer = new VBox(headerContainer, searchField, findResults);
        innerContainer.setPadding(new Insets(0, 10, 10, 10));
        innerContainer.setFillWidth(false);
        return new VBox(new Separator(Orientation.HORIZONTAL), new HBox(innerContainer));
    }

    public void appendColoredLine(String line) {
        int start = codeArea.getLength();
        line = line.replaceAll("\u001B\\[[;\\d]*m", ""); // Strip for display
        this.codeArea.appendText(line);
        StyleSpansBuilder<Collection<String>> spans = new StyleSpansBuilder<>();
        String defaultFg = Main.getDarkModeSetting() ? "ansi-fg-bright-white" : "ansi-fg-bright-black";
        spans.add(List.of(defaultFg), this.codeArea.getLength());
        this.codeArea.setStyleSpans(start, spans.create());
    }

    public CodeArea getCodeArea() {
        return codeArea;
    }

    public boolean usedScrolling() {
        return usedScrolling;
    }

    public Process getProcess() {
        return process;
    }

    public VirtualizedScrollPane<CodeArea> getScrollPane() {
        return scrollPane;
    }

    public OutputTabOptions getOutputTabOptions() {
        return outputTabOptions;
    }
}
