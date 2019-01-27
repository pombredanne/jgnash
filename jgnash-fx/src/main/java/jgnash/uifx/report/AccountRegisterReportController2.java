/*
 * jGnash, a personal finance application
 * Copyright (C) 2001-2019 Craig Cavanaugh
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package jgnash.uifx.report;

import jgnash.engine.Account;
import jgnash.engine.AccountGroup;
import jgnash.report.pdf.Report;
import jgnash.report.table.AbstractReportTableModel;
import jgnash.uifx.control.AccountComboBox;
import jgnash.uifx.control.DatePickerEx;
import jgnash.uifx.report.pdf.ReportController;
import jgnash.util.Nullable;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.IOException;
import java.time.LocalDate;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;

/**
 * Account Register Report Controller
 *
 * @author Craig Cavanaugh
 */
public class AccountRegisterReportController2 implements ReportController {

    @FXML
    private TextField memoFilterTextField;

    @FXML
    private TextField payeeFilterTextField;

    @FXML
    private AccountComboBox accountComboBox;

    @FXML
    private CheckBox showSplitsCheckBox;

    @FXML
    private CheckBox showTimestampCheckBox;

    @FXML
    private DatePickerEx startDatePicker;

    @FXML
    private DatePickerEx endDatePicker;

    private static final String SHOW_SPLITS = "showSplits";

    private static final String SHOW_TIMESTAMP = "showTimestamp";

    private Report report = new AccountRegisterReport();

    private Runnable refreshRunnable = null;

    public AccountRegisterReportController2() {
        super();

        report.setTableFont(PDType1Font.COURIER);
        report.setHeaderFont(PDType1Font.HELVETICA_BOLD);
        report.setCellPadding(2.5f);
        report.setBaseFontSize(9);
    }

    private Preferences getPreferences() {
        return Preferences.userNodeForPackage(getClass()).node(getClass().getSimpleName());
    }

    @FXML
    private void initialize() {
        final Preferences preferences = getPreferences();

        showSplitsCheckBox.setSelected(preferences.getBoolean(SHOW_SPLITS, false));
        showTimestampCheckBox.setSelected(preferences.getBoolean(SHOW_TIMESTAMP, false));

        accountComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                refreshAccount(newValue);
                handleReportRefresh();
            }
        });

        final ChangeListener<Object> refreshListener = (observable, oldValue, newValue) -> handleReportRefresh();

        showSplitsCheckBox.selectedProperty().addListener(refreshListener);
        startDatePicker.valueProperty().addListener(refreshListener);
        endDatePicker.valueProperty().addListener(refreshListener);
        payeeFilterTextField.textProperty().addListener(refreshListener);
        memoFilterTextField.textProperty().addListener(refreshListener);
        showTimestampCheckBox.selectedProperty().addListener(refreshListener);
    }

    @Override
    public void setRefreshRunnable(final Runnable runnable) {
        refreshRunnable = runnable;
    }

    @Override
    public void getReport(final Consumer<Report> reportConsumer) {
        reportConsumer.accept(report);
    }

    @Override
    public void refreshReport() {
        handleReportRefresh();
    }

    public void setAccount(@Nullable final Account account) {
        if (account != null) {
            accountComboBox.setValue(account);
        } else {    // load the selected account
            refreshAccount(accountComboBox.getValue());
        }
    }

    private void refreshAccount(final Account account) {
        if (account != null) {
            if (account.getTransactionCount() > 0) {
                startDatePicker.setValue(account.getTransactionAt(0).getLocalDate());
                endDatePicker.setValue(LocalDate.now());
            }
        }
    }

    private void handleReportRefresh() {

        final Preferences preferences = getPreferences();

        preferences.putBoolean(SHOW_SPLITS, showSplitsCheckBox.isSelected());
        preferences.putBoolean(SHOW_TIMESTAMP, showTimestampCheckBox.isSelected());

        addTable();

        // send notification the report has been updated
        if (refreshRunnable != null) {
            refreshRunnable.run();
        }
    }

    private void addTable() {
        AbstractReportTableModel model = createReportModel();

        report.clearReport();

        try {
            report.addTable(model, accountComboBox.getValue().getName(), endDatePicker.getValue().toString());
            report.addFooter();
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    private AbstractReportTableModel createReportModel() {
        final Account account = accountComboBox.getValue();

        // disable the payee filter if an investment account is selected
        payeeFilterTextField.setDisable(account.getAccountType().getAccountGroup() == AccountGroup.INVEST);
        showSplitsCheckBox.setDisable(account.getAccountType().getAccountGroup() == AccountGroup.INVEST);

        return AccountRegisterReport.createReportModel(account, startDatePicker.getValue(), endDatePicker.getValue(),
                showSplitsCheckBox.isSelected(), memoFilterTextField.getText(), payeeFilterTextField.getText(),
                showTimestampCheckBox.isSelected());
    }
}
