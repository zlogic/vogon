/*
 * Vogon personal finance/expense analyzer.
 * License TBD.
 * Author: Dmitry Zolotukhin <zlogic@gmail.com>
 */
package org.zlogic.vogon.data.events;

import org.zlogic.vogon.data.FinanceTransaction;

/**
 * Interface for accepting Transaction events. If several listeners are used,
 * the implementation is responsible for distributing the event. Event handling
 * calls should be as short as possible and should not make calls which result
 * in database transactions.
 *
 * @author Dmitry Zolotukhin
 */
public interface TransactionEventHandler {
		/**
		 * A transaction created callback
		 *
		 * @param newTransaction the transaction that was created
		 */
		void transactionCreated(FinanceTransaction newTransaction);
		/**
		 * A transaction updated callback
		 *
		 * @param updatedTransaction the transaction that was updated
		 */
		void transactionUpdated(FinanceTransaction updatedTransaction);

		/**
		 * A transaction updated handler (transaction list has been updated)
		 */
		void transactionsUpdated();
		/**
		 * A transaction deleted callback
		 *
		 * @param deletedTransaction the deleted transaction
		 */
		void transactionDeleted(FinanceTransaction deletedTransaction);
}