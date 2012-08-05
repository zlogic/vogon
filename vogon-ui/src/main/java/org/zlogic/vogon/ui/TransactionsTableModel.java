/*
 * Vogon personal finance/expense analyzer.
 * License TBD.
 * Author: Dmitry Zolotukhin <zlogic@gmail.com>
 */
package org.zlogic.vogon.ui;

import java.text.MessageFormat;
import javax.swing.table.AbstractTableModel;
import org.zlogic.vogon.data.ExpenseTransaction;
import org.zlogic.vogon.data.FinanceAccount;
import org.zlogic.vogon.data.FinanceData;
import org.zlogic.vogon.data.FinanceTransaction;
import org.zlogic.vogon.data.TransferTransaction;

/**
 * Transactions table model class
 *
 * @author Dmitry Zolotukhin
 */
public class TransactionsTableModel extends AbstractTableModel implements FinanceData.TransactionCreatedEventListener, FinanceData.TransactionUpdatedEventListener {

	private java.util.ResourceBundle messages = java.util.ResourceBundle.getBundle("org/zlogic/vogon/ui/messages");
	private FinanceData data = null;

	/**
	 * Default constructor for TransactionsTableModel
	 */
	public TransactionsTableModel() {
	}

	/**
	 * Sets the table data
	 *
	 * @param data
	 */
	public void setFinanceData(FinanceData data) {
		this.data = data;
		fireTableDataChanged();
	}

	@Override
	public int getColumnCount() {
		return 5;
	}

	@Override
	public int getRowCount() {
		return data != null ? data.getTransactions().size() : 0;
	}

	@Override
	public String getColumnName(int col) {
		switch (col) {
			case 0:
				return messages.getString("TRANSACTION_DESCRIPTION");
			case 1:
				return messages.getString("TRANSACTION_DATE");
			case 2:
				return messages.getString("TRANSACTION_TAGS");
			case 3:
				return messages.getString("TRANSACTION_AMOUNT");
			case 4:
				return messages.getString("TRANSACTION_ACCOUNT");
		}
		return null;
	}

	@Override
	public Object getValueAt(int row, int col) {
		FinanceTransaction transaction = data.getTransactions().get(row);
		switch (col) {
			case 0:
				return transaction.getDescription();
			case 1:
				return MessageFormat.format("{0,date,yyyy-MM-dd}", new Object[]{transaction.getDate()});
			case 2:
				return org.zlogic.vogon.data.Utils.join(transaction.getTags(), ",");
			case 3:
				if (transaction.getClass() == ExpenseTransaction.class)
					return ((ExpenseTransaction) transaction).getAmount();
				else if (transaction.getClass() == TransferTransaction.class)
					return ((TransferTransaction) transaction).getAmount();
			case 4:
				if (transaction.getClass() == ExpenseTransaction.class) {
					FinanceAccount[] accounts = ((ExpenseTransaction) transaction).getAccounts();
					StringBuilder builder = new StringBuilder();
					for (int i = 0; i < accounts.length; i++)
						builder.append(i != 0 ? "," : "").append(accounts[i]!=null?accounts[i].getName():messages.getString("INVALID_ACCOUNT"));
					return builder.toString();
				} else if (transaction.getClass() == TransferTransaction.class) {
					FinanceAccount[] toAccounts = ((TransferTransaction) transaction).getToAccounts();
					FinanceAccount[] fromAccounts = ((TransferTransaction) transaction).getFromAccounts();
					StringBuilder builder = new StringBuilder();
					if (fromAccounts.length > 1) {
						builder.append("(");
						for (int i = 0; i < fromAccounts.length; i++)
							builder.append(i != 0 ? "," : "").append(fromAccounts[i]!=null?fromAccounts[i].getName():messages.getString("INVALID_ACCOUNT"));
						builder.append(")");
					} else if (fromAccounts.length == 1)
						builder.append(fromAccounts[0].getName());
					builder.append("->");
					if (toAccounts.length > 1) {
						builder.append("(");
						for (int i = 0; i < toAccounts.length; i++)
							builder.append(i != 0 ? "," : "").append(toAccounts[i]!=null?toAccounts[i].getName():messages.getString("INVALID_ACCOUNT"));
						builder.append(")");
					} else if (toAccounts.length == 1)
						builder.append(toAccounts[0].getName());
					return builder.toString();
				} else
					return "";
		}
		return null;
	}

	@Override
	public Class getColumnClass(int c) {
		switch (c) {
			case 0:
				return String.class;
			case 1:
				return String.class;
			case 2:
				return String.class;
			case 3:
				return Double.class;
			case 4:
				return String.class;
		}
		return null;
	}

	@Override
	public boolean isCellEditable(int row, int col) {
		return false;
	}

	public void deleteTransaction(int row) {
		data.deleteTransaction(data.getTransactions().get(row));
		fireTableRowsDeleted(row, row);
	}

	@Override
	public void transactionCreated(FinanceTransaction newTransaction) {
		int rowIndex = data.getTransactions().indexOf(newTransaction);
		fireTableRowsInserted(rowIndex, rowIndex);
	}

	@Override
	public void transactionUpdated(FinanceTransaction updatedTransaction) {
		int rowIndex = data.getTransactions().indexOf(updatedTransaction);
		fireTableRowsUpdated(rowIndex, rowIndex);
	}

	@Override
	public void transactionsUpdated() {
		fireTableDataChanged();
	}
}