package jgnash.uifx.views.budget;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.GridPane;

import jgnash.engine.Account;
import jgnash.engine.AccountGroup;
import jgnash.engine.Comparators;
import jgnash.engine.Engine;
import jgnash.engine.EngineFactory;
import jgnash.engine.budget.Budget;
import jgnash.engine.budget.BudgetPeriodDescriptor;
import jgnash.engine.budget.BudgetPeriodResults;
import jgnash.engine.budget.BudgetResultsModel;
import jgnash.text.CommodityFormat;
import jgnash.uifx.control.NullTableViewSelectionModel;
import jgnash.uifx.skin.StyleClass;
import jgnash.uifx.skin.ThemeManager;
import jgnash.uifx.util.JavaFXUtils;

/**
 * @author Craig Cavanaugh
 */
public class BudgetTableController {

    private static final String HIDE_VERTICAL_CSS = "jgnash/skin/tableHideVerticalScrollBar.css";
    private static final String HIDE_HEADER_CSS = "jgnash/skin/tableHideColumnHeader.css";

    private static final int ROW_HEIGHT_MULTIPLIER = 2;

    //TODO: Magic number that needs to be fixed or controlled with css
    private static final int BORDER_MARGIN = 2;

    // allow a selection span of +/- the specified number of years
    private static final int YEAR_MARGIN = 10;

    @FXML
    private GridPane gridPane;

    @FXML
    private Button shiftLeftButton;

    @FXML
    private Button shiftRightButton;

    @FXML
    private Spinner<Integer> yearSpinner;

    @FXML
    private ScrollBar verticalScrollBar;

    @FXML
    private TreeTableView<Account> accountTreeView;

    @FXML
    private TableView<Account> periodTable;

    @FXML
    private TableView<Account> accountSummaryTable;

    @FXML
    private TableView<AccountGroup> periodSummaryTable;

    @FXML
    private TableView<AccountGroup> accountGroupPeriodSummaryTable;

    @FXML
    private TableView<AccountGroup> accountTypeTable;

    @FXML
    private ResourceBundle resources;

    private final SimpleObjectProperty<Budget> budgetProperty = new SimpleObjectProperty<>();

    private BudgetResultsModel budgetResultsModel;

    /**
     * This list is updated to track the expanded rows of the TreeTableView.
     * This should be the model for all account specific tables
     */
    private final ObservableList<Account> expandedAccountList = FXCollections.observableArrayList();

    /**
     * This list is updated to track the displayed AccountGroups.
     * This should be the model for all account specific tables
     */
    private final ObservableList<AccountGroup> accountGroupList = FXCollections.observableArrayList();

    private final DoubleProperty rowHeightProperty = new SimpleDoubleProperty();

    /**
     * Bind the max and minimum values of every column to this width
     */
    private final DoubleProperty columnWidthProperty = new SimpleDoubleProperty(75);

    /**
     * Current index to be used for scrolling the display.  If 0 the first period is displayed to the left
     */
    private final IntegerProperty indexProperty = new SimpleIntegerProperty(0);

    /**
     * The number of visible columns
     */
    private final IntegerProperty visibleColumnCountProperty = new SimpleIntegerProperty(4);

    /**
     * The number of periods in the model
     */
    private final IntegerProperty periodCountProperty = new SimpleIntegerProperty(1);

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    @FXML
    private void initialize() {
        updateHeights();

        yearSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                LocalDate.now().getYear() - 10, LocalDate.now().getYear() + YEAR_MARGIN,
                LocalDate.now().getYear(), 1));

        accountTreeView.getStylesheets().addAll(HIDE_VERTICAL_CSS);
        accountTreeView.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY);
        accountTreeView.setShowRoot(false);
        accountTreeView.fixedCellSizeProperty().bind(rowHeightProperty);

        accountSummaryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        accountSummaryTable.getStylesheets().add(HIDE_VERTICAL_CSS);
        accountSummaryTable.setItems(expandedAccountList);
        accountSummaryTable.fixedCellSizeProperty().bind(rowHeightProperty);
        accountSummaryTable.setSelectionModel(new NullTableViewSelectionModel<>(accountSummaryTable));

        accountTypeTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        accountTypeTable.getStylesheets().add(HIDE_HEADER_CSS);
        accountTypeTable.setItems(accountGroupList);
        accountTypeTable.fixedCellSizeProperty().bind(rowHeightProperty);
        accountTypeTable.prefHeightProperty()
                .bind(rowHeightProperty.multiply(Bindings.size(accountGroupList)).add(BORDER_MARGIN));
        accountTypeTable.setSelectionModel(new NullTableViewSelectionModel<>(accountTypeTable));

        accountGroupPeriodSummaryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        accountGroupPeriodSummaryTable.getStylesheets().add(HIDE_HEADER_CSS);
        accountGroupPeriodSummaryTable.setItems(accountGroupList);
        accountGroupPeriodSummaryTable.fixedCellSizeProperty().bind(rowHeightProperty);
        accountGroupPeriodSummaryTable.prefHeightProperty()
                .bind(rowHeightProperty.multiply(Bindings.size(accountGroupList)).add(BORDER_MARGIN));
        accountGroupPeriodSummaryTable.setSelectionModel(new NullTableViewSelectionModel<>(accountGroupPeriodSummaryTable));

        buildAccountTreeTable();
        buildAccountTypeTable();
        buildAccountSummaryTable();
        buildAccountGroupSummaryTable();

        accountTreeView.expandedItemCountProperty().addListener((observable, oldValue, newValue) -> {
            Platform.runLater(this::updateExpandedAccountList);
        });

        budgetProperty.addListener((observable, oldValue, newValue) -> {
            Platform.runLater(BudgetTableController.this::handleBudgetChange);  // push change to end of EDT
        });

        yearSpinner.valueProperty().addListener((observable, oldValue, newValue) -> {
            Platform.runLater(BudgetTableController.this::handleBudgetChange);
        });

        ThemeManager.getFontScaleProperty().addListener((observable, oldValue, newValue) -> {
            updateHeights();
        });

        shiftLeftButton.disableProperty().bind(indexProperty.lessThanOrEqualTo(0));
        shiftRightButton.disableProperty().bind(indexProperty.greaterThanOrEqualTo(periodCountProperty.subtract(visibleColumnCountProperty)));
    }

    @FXML
    private synchronized void handleShiftLeft() {
        lock.writeLock().lock();

        try {
            // remove the right column
            periodTable.getColumns().remove(visibleColumnCountProperty.get() - 1);
            periodSummaryTable.getColumns().remove(visibleColumnCountProperty.get() - 1);

            indexProperty.setValue(indexProperty.get() - 1);

            // insert a new column to the left
            periodTable.getColumns().add(0, buildAccountPeriodResultsColumn(indexProperty.get()));
            periodSummaryTable.getColumns().add(0, buildAccountPeriodSummaryColumn(indexProperty.get()));
        } finally {
            lock.writeLock().unlock();
        }
    }

    @FXML
    private synchronized void handleShiftRight() {
        lock.writeLock().lock();

        try {
            // remove leftmost column
            periodTable.getColumns().remove(0);
            periodSummaryTable.getColumns().remove(0);

            final int newColumn = indexProperty.get() + visibleColumnCountProperty.get();

            // add a new column to the right
            periodTable.getColumns().add(buildAccountPeriodResultsColumn(newColumn));
            periodSummaryTable.getColumns().add(buildAccountPeriodSummaryColumn(newColumn));

            indexProperty.setValue(indexProperty.get() + 1);
        } finally {
            lock.writeLock().unlock();
        }
    }

    SimpleObjectProperty<Budget> budgetProperty() {
        return budgetProperty;
    }

    private void updateHeights() {
        rowHeightProperty.setValue(ThemeManager.getBaseTextHeight() * ROW_HEIGHT_MULTIPLIER);
    }

    /**
     * The table view will lazily create the scrollbars which makes finding them tricky.  We need to check for
     * their existence and try again later if they do not exist.
     */
    private void bindScrollBars() {
        final Optional<ScrollBar> accountScrollBar = JavaFXUtils.findVerticalScrollBar(accountTreeView);
        final Optional<ScrollBar> vDataScrollBar = JavaFXUtils.findVerticalScrollBar(periodTable);
        final Optional<ScrollBar> accountSumScrollBar = JavaFXUtils.findVerticalScrollBar(accountSummaryTable);

        if (!vDataScrollBar.isPresent() || !accountScrollBar.isPresent() || !accountSumScrollBar.isPresent()) {
            Platform.runLater(BudgetTableController.this::bindScrollBars);  //respawn on the application thread
        } else {    // all here, lets bind then now
            verticalScrollBar.minProperty().bindBidirectional(accountScrollBar.get().minProperty());
            verticalScrollBar.maxProperty().bindBidirectional(accountScrollBar.get().maxProperty());
            verticalScrollBar.valueProperty().bindBidirectional(accountScrollBar.get().valueProperty());

            accountScrollBar.get().valueProperty().bindBidirectional(vDataScrollBar.get().valueProperty());
            accountSumScrollBar.get().valueProperty().bindBidirectional(vDataScrollBar.get().valueProperty());
        }
    }

    private void handleBudgetChange() {
        if (budgetProperty.get() != null) {
            final Engine engine = EngineFactory.getEngine(EngineFactory.DEFAULT);
            Objects.requireNonNull(engine);

            budgetResultsModel = new BudgetResultsModel(budgetProperty.get(), yearSpinner.getValue(), engine.getDefaultCurrency());

            periodCountProperty.setValue(budgetResultsModel.getDescriptorList().size());

            loadModel();
        } else {
            accountTreeView.setRoot(null);
            expandedAccountList.clear();
            accountGroupList.clear();
        }
    }

    /**
     * Maintains the list of expanded accounts
     */
    private synchronized void updateExpandedAccountList() {
        final int count = accountTreeView.getExpandedItemCount();

        // Create a new list and update the observable list in one shot to minimize visual updates
        final List<Account> accountList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            accountList.add(accountTreeView.getTreeItem(i).getValue());
        }

        expandedAccountList.setAll(accountList);
    }

    private void loadModel() {
        lock.readLock().lock();

        try {
            loadAccountTree();

            accountGroupList.setAll(budgetResultsModel.getAccountGroupList());

            buildPeriodTable();
            buildPeriodSummaryTable();
            updateExpandedAccountList();

            columnWidthProperty.setValue(getMaxWidth());

            Platform.runLater(this::bindScrollBars);
        } finally {
            lock.readLock().unlock();
        }
    }

    private void loadAccountTree() {
        final Engine engine = EngineFactory.getEngine(EngineFactory.DEFAULT);
        Objects.requireNonNull(engine);

        final TreeItem<Account> root = new TreeItem<>(engine.getRootAccount());
        root.setExpanded(true);

        accountTreeView.setRoot(root);
        loadChildren(root);
    }

    private synchronized void loadChildren(final TreeItem<Account> parentItem) {
        final Account parent = parentItem.getValue();

        parent.getChildren(Comparators.getAccountByCode()).stream().filter(budgetResultsModel::includeAccount).forEach(child ->
        {
            final TreeItem<Account> childItem = new TreeItem<>(child);
            childItem.setExpanded(true);
            parentItem.getChildren().add(childItem);

            if (child.getChildCount() > 0) {
                loadChildren(childItem);
            }
        });
    }

    private void buildAccountTreeTable() {
        // empty column header is needed
        final TreeTableColumn<Account, String> headerColumn = new TreeTableColumn<>("");

        final TreeTableColumn<Account, String> nameColumn = new TreeTableColumn<>(resources.getString("Column.Account"));
        nameColumn.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getValue().getName()));

        headerColumn.getColumns().add(nameColumn);

        accountTreeView.getColumns().add(headerColumn);
    }

    private void buildAccountTypeTable() {
        final TableColumn<AccountGroup, String> nameColumn = new TableColumn<>(resources.getString("Column.Type"));
        nameColumn.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().toString()));

        accountTypeTable.getColumns().add(nameColumn);
    }

    private void buildAccountSummaryTable() {
        final TableColumn<Account, BigDecimal> headerColumn = new TableColumn<>(resources.getString("Title.Summary"));

        final TableColumn<Account, BigDecimal> budgetedColumn = new TableColumn<>(resources.getString("Column.Budgeted"));
        budgetedColumn.setCellValueFactory(param -> {
            if (param.getValue() != null) {
                return new SimpleObjectProperty<>(budgetResultsModel.getResults(param.getValue()).getBudgeted());
            }
            return new SimpleObjectProperty<>(BigDecimal.ZERO);
        });
        budgetedColumn.setCellFactory(param -> new AccountCommodityFormatTableCell());
        budgetedColumn.minWidthProperty().bind(columnWidthProperty);
        budgetedColumn.maxWidthProperty().bind(columnWidthProperty);
        budgetedColumn.setSortable(false);

        headerColumn.getColumns().add(budgetedColumn);

        final TableColumn<Account, BigDecimal> actualColumn = new TableColumn<>(resources.getString("Column.Actual"));
        actualColumn.setCellValueFactory(param -> {
            if (param.getValue() != null) {
                return new SimpleObjectProperty<>(budgetResultsModel.getResults(param.getValue()).getChange());
            }
            return new SimpleObjectProperty<>(BigDecimal.ZERO);
        });
        actualColumn.setCellFactory(param -> new AccountCommodityFormatTableCell());
        actualColumn.minWidthProperty().bind(columnWidthProperty);
        actualColumn.maxWidthProperty().bind(columnWidthProperty);
        actualColumn.setSortable(false);

        headerColumn.getColumns().add(actualColumn);

        final TableColumn<Account, BigDecimal> remainingColumn = new TableColumn<>(resources.getString("Column.Remaining"));
        remainingColumn.setCellValueFactory(param -> {
            if (param.getValue() != null) {
                return new SimpleObjectProperty<>(budgetResultsModel.getResults(param.getValue()).getRemaining());
            }
            return new SimpleObjectProperty<>(BigDecimal.ZERO);
        });
        remainingColumn.setCellFactory(param -> new AccountCommodityFormatTableCell());
        remainingColumn.minWidthProperty().bind(columnWidthProperty);
        remainingColumn.maxWidthProperty().bind(columnWidthProperty);
        remainingColumn.setSortable(false);

        headerColumn.getColumns().add(remainingColumn);

        accountSummaryTable.getColumns().add(headerColumn);
    }

    private TableColumn<Account, BigDecimal> buildAccountPeriodResultsColumn(final int index) {

        final BudgetPeriodDescriptor descriptor = budgetResultsModel.getDescriptorList().get(index);

        final TableColumn<Account, BigDecimal> headerColumn = new TableColumn<>(descriptor.getPeriodDescription());

        final TableColumn<Account, BigDecimal> budgetedColumn = new TableColumn<>(resources.getString("Column.Budgeted"));
        budgetedColumn.setCellValueFactory(param -> {
            if (param.getValue() != null) {
                return new SimpleObjectProperty<>(budgetResultsModel.getResults(descriptor, param.getValue()).getBudgeted());
            }
            return new SimpleObjectProperty<>(BigDecimal.ZERO);
        });
        budgetedColumn.setCellFactory(param -> new AccountCommodityFormatTableCell());
        budgetedColumn.minWidthProperty().bind(columnWidthProperty);
        budgetedColumn.maxWidthProperty().bind(columnWidthProperty);
        budgetedColumn.setSortable(false);

        headerColumn.getColumns().add(budgetedColumn);

        final TableColumn<Account, BigDecimal> actualColumn = new TableColumn<>(resources.getString("Column.Actual"));
        actualColumn.setCellValueFactory(param -> {
            if (param.getValue() != null) {
                return new SimpleObjectProperty<>(budgetResultsModel.getResults(descriptor, param.getValue()).getChange());
            }
            return new SimpleObjectProperty<>(BigDecimal.ZERO);
        });
        actualColumn.setCellFactory(param -> new AccountCommodityFormatTableCell());
        actualColumn.minWidthProperty().bind(columnWidthProperty);
        actualColumn.maxWidthProperty().bind(columnWidthProperty);
        actualColumn.setSortable(false);

        headerColumn.getColumns().add(actualColumn);

        final TableColumn<Account, BigDecimal> remainingColumn = new TableColumn<>(resources.getString("Column.Remaining"));
        remainingColumn.setCellValueFactory(param -> {
            if (param.getValue() != null) {
                return new SimpleObjectProperty<>(budgetResultsModel.getResults(descriptor, param.getValue()).getRemaining());
            }
            return new SimpleObjectProperty<>(BigDecimal.ZERO);
        });
        remainingColumn.setCellFactory(param -> new AccountCommodityFormatTableCell());
        remainingColumn.minWidthProperty().bind(columnWidthProperty);
        remainingColumn.maxWidthProperty().bind(columnWidthProperty);
        remainingColumn.setSortable(false);

        headerColumn.getColumns().add(remainingColumn);

        return headerColumn;
    }

    /**
     * The period table must be rebuilt because of JavaFx issues
     */
    private void buildPeriodTable() {
        // recreate the table and load the new one into the grid pane
        final int row = GridPane.getRowIndex(periodTable);
        final int column = GridPane.getColumnIndex(periodTable);
        gridPane.getChildren().remove(periodTable);
        periodTable = new TableView<>();
        GridPane.setConstraints(periodTable, column, row);
        gridPane.getChildren().add(periodTable);

        periodTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        periodTable.getStylesheets().addAll(HIDE_VERTICAL_CSS);
        periodTable.fixedCellSizeProperty().bind(rowHeightProperty);
        periodTable.setSelectionModel(new NullTableViewSelectionModel<>(periodTable));

        final int index = indexProperty.getValue();
        final int periodCount = visibleColumnCountProperty.get();

        for (int i = index; i < index + periodCount; i++) {
            periodTable.getColumns().add(buildAccountPeriodResultsColumn(i));
        }

        periodTable.setItems(expandedAccountList);
    }

    private TableColumn<AccountGroup, BigDecimal> buildAccountPeriodSummaryColumn(final int index) {
        final BudgetPeriodDescriptor descriptor = budgetResultsModel.getDescriptorList().get(index);

        final TableColumn<AccountGroup, BigDecimal> headerColumn = new TableColumn<>(descriptor.getPeriodDescription());

        final TableColumn<AccountGroup, BigDecimal> budgetedColumn = new TableColumn<>(resources.getString("Column.Budgeted"));
        budgetedColumn.setCellValueFactory(param -> {
            if (param.getValue() != null) {
                return new SimpleObjectProperty<>(budgetResultsModel.getResults(descriptor, param.getValue()).getBudgeted());
            }
            return new SimpleObjectProperty<>(BigDecimal.ZERO);
        });
        budgetedColumn.setCellFactory(param -> new AccountGroupTableCell());
        budgetedColumn.minWidthProperty().bind(columnWidthProperty);
        budgetedColumn.maxWidthProperty().bind(columnWidthProperty);
        budgetedColumn.setSortable(false);

        headerColumn.getColumns().add(budgetedColumn);

        final TableColumn<AccountGroup, BigDecimal> actualColumn = new TableColumn<>(resources.getString("Column.Actual"));
        actualColumn.setCellValueFactory(param -> {
            if (param.getValue() != null) {
                return new SimpleObjectProperty<>(budgetResultsModel.getResults(descriptor, param.getValue()).getChange());
            }
            return new SimpleObjectProperty<>(BigDecimal.ZERO);
        });
        actualColumn.setCellFactory(param -> new AccountGroupTableCell());
        actualColumn.minWidthProperty().bind(columnWidthProperty);
        actualColumn.maxWidthProperty().bind(columnWidthProperty);
        actualColumn.setSortable(false);

        headerColumn.getColumns().add(actualColumn);

        final TableColumn<AccountGroup, BigDecimal> remainingColumn = new TableColumn<>(resources.getString("Column.Remaining"));
        remainingColumn.setCellValueFactory(param -> {
            if (param.getValue() != null) {
                return new SimpleObjectProperty<>(budgetResultsModel.getResults(descriptor, param.getValue()).getRemaining());
            }
            return new SimpleObjectProperty<>(BigDecimal.ZERO);
        });
        remainingColumn.setCellFactory(param -> new AccountGroupTableCell());
        remainingColumn.minWidthProperty().bind(columnWidthProperty);
        remainingColumn.maxWidthProperty().bind(columnWidthProperty);
        remainingColumn.setSortable(false);

        headerColumn.getColumns().add(remainingColumn);

        return headerColumn;
    }

    /**
     * The period summary table must be rebuilt because of JavaFx issues
     */
    private void buildPeriodSummaryTable() {
        // recreate the table and load the new one into the grid pane
        final int row = GridPane.getRowIndex(periodSummaryTable);
        final int column = GridPane.getColumnIndex(periodSummaryTable);
        gridPane.getChildren().remove(periodSummaryTable);
        periodSummaryTable = new TableView<>();
        GridPane.setConstraints(periodSummaryTable, column, row);
        gridPane.getChildren().add(periodSummaryTable);

        periodSummaryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        periodSummaryTable.getStylesheets().addAll(HIDE_VERTICAL_CSS, HIDE_HEADER_CSS);
        periodSummaryTable.fixedCellSizeProperty().bind(rowHeightProperty);
        periodSummaryTable.prefHeightProperty()
                .bind(rowHeightProperty.multiply(Bindings.size(accountGroupList)).add(BORDER_MARGIN));
        periodSummaryTable.setSelectionModel(new NullTableViewSelectionModel<>(periodSummaryTable));

        final int index = indexProperty.getValue();
        final int periodCount = visibleColumnCountProperty.get();

        for (int i = index; i < index + periodCount; i++) {
            periodSummaryTable.getColumns().add(buildAccountPeriodSummaryColumn(i));
        }

        periodSummaryTable.setItems(accountGroupList);
    }

    private void buildAccountGroupSummaryTable() {
        final TableColumn<AccountGroup, BigDecimal> headerColumn = new TableColumn<>(resources.getString("Title.Summary"));

        final TableColumn<AccountGroup, BigDecimal> budgetedColumn = new TableColumn<>(resources.getString("Column.Budgeted"));
        budgetedColumn.setCellValueFactory(param -> {
            if (param.getValue() != null) {
                return new SimpleObjectProperty<>(budgetResultsModel.getResults(param.getValue()).getBudgeted());
            }
            return new SimpleObjectProperty<>(BigDecimal.ZERO);
        });
        budgetedColumn.setCellFactory(param -> new AccountGroupTableCell());
        budgetedColumn.minWidthProperty().bind(columnWidthProperty);
        budgetedColumn.maxWidthProperty().bind(columnWidthProperty);
        budgetedColumn.setSortable(false);

        headerColumn.getColumns().add(budgetedColumn);

        final TableColumn<AccountGroup, BigDecimal> actualColumn = new TableColumn<>(resources.getString("Column.Actual"));
        actualColumn.setCellValueFactory(param -> {
            if (param.getValue() != null) {
                return new SimpleObjectProperty<>(budgetResultsModel.getResults(param.getValue()).getChange());
            }
            return new SimpleObjectProperty<>(BigDecimal.ZERO);
        });
        actualColumn.setCellFactory(param -> new AccountGroupTableCell());
        actualColumn.minWidthProperty().bind(columnWidthProperty);
        actualColumn.maxWidthProperty().bind(columnWidthProperty);
        actualColumn.setSortable(false);

        headerColumn.getColumns().add(actualColumn);

        final TableColumn<AccountGroup, BigDecimal> remainingColumn = new TableColumn<>(resources.getString("Column.Remaining"));
        remainingColumn.setCellValueFactory(param -> {
            if (param.getValue() != null) {
                return new SimpleObjectProperty<>(budgetResultsModel.getResults(param.getValue()).getRemaining());
            }
            return new SimpleObjectProperty<>(BigDecimal.ZERO);
        });
        remainingColumn.setCellFactory(param -> new AccountGroupTableCell());
        remainingColumn.minWidthProperty().bind(columnWidthProperty);
        remainingColumn.maxWidthProperty().bind(columnWidthProperty);
        remainingColumn.setSortable(false);

        headerColumn.getColumns().add(remainingColumn);

        accountGroupPeriodSummaryTable.getColumns().add(headerColumn);
    }

    private double getMaxWidth(final Account account) {
        double max = 0;
        double min = 0;

        BudgetPeriodResults budgetPeriodResults = budgetResultsModel.getResults(account);
        max = Math.max(max, budgetPeriodResults.getBudgeted().doubleValue());
        max = Math.max(max, budgetPeriodResults.getChange().doubleValue());
        max = Math.max(max, budgetPeriodResults.getRemaining().doubleValue());

        min = Math.min(min, budgetPeriodResults.getBudgeted().doubleValue());
        min = Math.min(min, budgetPeriodResults.getChange().doubleValue());
        min = Math.min(min, budgetPeriodResults.getRemaining().doubleValue());


        return Math.max(JavaFXUtils.getDisplayedTextWidth(
                        CommodityFormat.getFullNumberFormat(budgetResultsModel.getBaseCurrency()).format(max), null),
                JavaFXUtils.getDisplayedTextWidth(
                        CommodityFormat.getFullNumberFormat(budgetResultsModel.getBaseCurrency()).format(min), null));
    }

    private double getMaxWidth(final BudgetPeriodDescriptor descriptor) {
        double max = 0;
        double min = 0;

        for (final AccountGroup accountGroup : accountGroupList) {
            BudgetPeriodResults budgetPeriodResults = budgetResultsModel.getResults(descriptor, accountGroup);
            max = Math.max(max, budgetPeriodResults.getBudgeted().doubleValue());
            max = Math.max(max, budgetPeriodResults.getChange().doubleValue());
            max = Math.max(max, budgetPeriodResults.getRemaining().doubleValue());

            min = Math.min(min, budgetPeriodResults.getBudgeted().doubleValue());
            min = Math.min(min, budgetPeriodResults.getChange().doubleValue());
            min = Math.min(min, budgetPeriodResults.getRemaining().doubleValue());
        }

        return Math.max(JavaFXUtils.getDisplayedTextWidth(
                        CommodityFormat.getFullNumberFormat(budgetResultsModel.getBaseCurrency()).format(max), null),
                JavaFXUtils.getDisplayedTextWidth(
                        CommodityFormat.getFullNumberFormat(budgetResultsModel.getBaseCurrency()).format(min), null));
    }

    private double getMaxWidth() {
        double max = 0;

        for (final BudgetPeriodDescriptor descriptor : budgetResultsModel.getDescriptorList()) {
            max = Math.max(max, getMaxWidth(descriptor));
        }

        for (final Account account : expandedAccountList) {
            max = Math.max(max, getMaxWidth(account));
        }

        max = Math.max(max, JavaFXUtils.getDisplayedTextWidth(resources.getString("Column.Budgeted") + BORDER_MARGIN, null));
        max = Math.max(max, JavaFXUtils.getDisplayedTextWidth(resources.getString("Column.Actual") + BORDER_MARGIN, null));
        max = Math.max(max, JavaFXUtils.getDisplayedTextWidth(resources.getString("Column.Remaining") + BORDER_MARGIN, null));

        return Math.ceil(max);
    }

    private class AccountCommodityFormatTableCell extends TableCell<Account, BigDecimal> {

        AccountCommodityFormatTableCell() {
            setStyle("-fx-alignment: center-right;");  // Right align
        }

        @Override
        protected void updateItem(final BigDecimal amount, final boolean empty) {
            super.updateItem(amount, empty);  // required

            if (!empty && amount != null && getTableRow() != null) {
                final Account account = expandedAccountList.get(getTableRow().getIndex());
                final NumberFormat format = CommodityFormat.getFullNumberFormat(account.getCurrencyNode());

                setText(format.format(amount));

                if (amount.signum() < 0) {
                    setId(StyleClass.NORMAL_NEGATIVE_CELL_ID);
                } else {
                    setId(StyleClass.NORMAL_CELL_ID);
                }
            } else {
                setText(null);
            }
        }
    }

    private class AccountGroupTableCell extends TableCell<AccountGroup, BigDecimal> {

        private final NumberFormat format;

        AccountGroupTableCell() {
            setStyle("-fx-alignment: center-right;");  // Right align
            format = CommodityFormat.getFullNumberFormat(budgetResultsModel.getBaseCurrency());
        }

        @Override
        protected void updateItem(final BigDecimal amount, final boolean empty) {
            super.updateItem(amount, empty);  // required

            if (!empty && amount != null && getTableRow() != null) {
                setText(format.format(amount));

                if (amount.signum() < 0) {
                    setId(StyleClass.NORMAL_NEGATIVE_CELL_ID);
                } else {
                    setId(StyleClass.NORMAL_CELL_ID);
                }
            } else {
                setText(null);
            }
        }
    }
}
