/*
 * Vogon personal finance/expense analyzer.
 * License TBD.
 * Author: Dmitry Zolotukhin <zlogic@gmail.com>
 */
package org.zlogic.vogon.data;

import java.util.Currency;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Root;
import org.zlogic.vogon.data.events.AccountEventHandler;
import org.zlogic.vogon.data.events.CurrencyEventHandler;
import org.zlogic.vogon.data.events.TransactionEventHandler;
import org.zlogic.vogon.data.interop.FileExporter;
import org.zlogic.vogon.data.interop.FileImporter;
import org.zlogic.vogon.data.interop.VogonExportException;
import org.zlogic.vogon.data.interop.VogonImportException;
import org.zlogic.vogon.data.interop.VogonImportLogicalException;

/**
 * Class for storing the finance data, performing database operations and
 * generating events
 *
 * @author Dmitry Zolotukhin
 */
public class FinanceData {

	/**
	 * Contains exchange rates
	 */
	protected java.util.List<CurrencyRate> exchangeRates;
	/**
	 * Preferred currency
	 */
	protected Currency defaultCurrency;
	/**
	 * Number of transactions in the database
	 */
	protected long transactionsCount = 0;

	/**
	 * Default constructor
	 */
	public FinanceData() {
		restoreFromDatabase();
	}

	/**
	 * Imports and persists data into this instance by using the output of the
	 * specified FileImporter
	 *
	 * @param importer a configured FileImporter instance
	 * @throws VogonImportException in case of import errors (I/O, format,
	 * indexing etc.)
	 * @throws VogonImportLogicalException in case of logical errors (without
	 * meaningful stack trace, just to show an error message)
	 */
	public void importData(FileImporter importer) throws VogonImportException, VogonImportLogicalException {
		importer.importFile();

		restoreFromDatabase();

		populateCurrencies();
		if (!getCurrencies().contains(defaultCurrency))
			if (getCurrencies().size() > 0)
				setDefaultCurrency(getCurrencies().contains(Currency.getInstance(Locale.getDefault())) ? Currency.getInstance(Locale.getDefault()) : getCurrencies().get(0));
			else
				setDefaultCurrency(Currency.getInstance(Locale.getDefault()));

		fireTransactionsUpdated();
		fireAccountsUpdated();
		fireCurrenciesUpdated();
	}

	/**
	 * Exports data by using the specified FileExporter
	 *
	 * @param exporter a configured FileExporter instance
	 * @throws VogonExportException in case of export errors (I/O, format,
	 * indexing etc.)
	 */
	public void exportData(FileExporter exporter) throws VogonExportException {
		exporter.exportFile(this);
	}

	/**
	 * Restores all data from the persistence database
	 */
	private void restoreFromDatabase() {
		EntityManager entityManager = DatabaseManager.getInstance().createEntityManager();
		exchangeRates = getCurrencyRatesFromDatabase(entityManager);
		defaultCurrency = getDefaultCurrencyFromDatabase(entityManager);
		entityManager.close();

		transactionsCount = getTransactionsCountFromDatabase();

		fireTransactionsUpdated();
		fireAccountsUpdated();
		fireCurrenciesUpdated();
	}

	/**
	 * Returns the latest copy of a transaction from the database
	 *
	 * @param transaction the transaction to be searcher (only the id is used)
	 * @return the transaction
	 */
	public FinanceTransaction getUpdatedTransactionFromDatabase(FinanceTransaction transaction) {
		EntityManager entityManager = DatabaseManager.getInstance().createEntityManager();
		CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();

		//Retreive the transactions
		CriteriaQuery<FinanceTransaction> transactionsCriteriaQuery = criteriaBuilder.createQuery(FinanceTransaction.class);
		Root<FinanceTransaction> tr = transactionsCriteriaQuery.from(FinanceTransaction.class);
		tr.fetch(FinanceTransaction_.tags, JoinType.LEFT);
		transactionsCriteriaQuery.where(criteriaBuilder.equal(tr.get(FinanceTransaction_.id), transaction.id));


		FinanceTransaction result = entityManager.createQuery(transactionsCriteriaQuery).getSingleResult();

		//Post-fetch components
		CriteriaQuery<FinanceTransaction> transactionsComponentsFetchCriteriaQuery = criteriaBuilder.createQuery(FinanceTransaction.class);
		Root<FinanceTransaction> trComponentsFetch = transactionsComponentsFetchCriteriaQuery.from(FinanceTransaction.class);
		transactionsComponentsFetchCriteriaQuery.where(criteriaBuilder.equal(tr.get(FinanceTransaction_.id), transaction.id));
		trComponentsFetch.fetch(FinanceTransaction_.components, JoinType.LEFT).fetch(TransactionComponent_.account, JoinType.LEFT);
		entityManager.createQuery(transactionsComponentsFetchCriteriaQuery).getSingleResult();

		entityManager.close();
		return result;
	}

	/**
	 * Retrieves all transactions from the database (from firstTransaction to
	 * lastTransaction)
	 *
	 * @param firstTransaction the first transaction number to be selected
	 * @param lastTransaction the last transaction number to be selected
	 * @return the list of all transactions stored in the database
	 */
	protected List<FinanceTransaction> getTransactionsFromDatabase(int firstTransaction, int lastTransaction) {
		EntityManager entityManager = DatabaseManager.getInstance().createEntityManager();
		CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();

		//Retreive the transactions
		CriteriaQuery<FinanceTransaction> transactionsCriteriaQuery = criteriaBuilder.createQuery(FinanceTransaction.class);
		Root<FinanceTransaction> tr = transactionsCriteriaQuery.from(FinanceTransaction.class);
		tr.fetch(FinanceTransaction_.tags, JoinType.LEFT);

		transactionsCriteriaQuery.orderBy(
				criteriaBuilder.asc(tr.get(FinanceTransaction_.transactionDate)),
				criteriaBuilder.asc(tr.get(FinanceTransaction_.id)));
		transactionsCriteriaQuery.select(tr).distinct(true);

		//Limit the number of transactions retreived
		TypedQuery query = entityManager.createQuery(transactionsCriteriaQuery);
		if (firstTransaction >= 0)
			query = query.setFirstResult(firstTransaction);
		if (lastTransaction >= 0 && firstTransaction >= 0)
			query = query.setMaxResults(lastTransaction - firstTransaction + 1);

		List<FinanceTransaction> result = query.getResultList();

		//Post-fetch components
		CriteriaQuery<FinanceTransaction> transactionsComponentsFetchCriteriaQuery = criteriaBuilder.createQuery(FinanceTransaction.class);
		Root<FinanceTransaction> trComponentsFetch = transactionsComponentsFetchCriteriaQuery.from(FinanceTransaction.class);
		transactionsComponentsFetchCriteriaQuery.where(tr.in(result));
		trComponentsFetch.fetch(FinanceTransaction_.components, JoinType.LEFT).fetch(TransactionComponent_.account, JoinType.LEFT);
		entityManager.createQuery(transactionsComponentsFetchCriteriaQuery).getResultList();

		entityManager.close();
		return result;
	}

	/**
	 * Returns the number of transactions stored in the database
	 *
	 * @return the number of transactions stored in the database
	 */
	protected long getTransactionsCountFromDatabase() {
		EntityManager entityManager = DatabaseManager.getInstance().createEntityManager();
		CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();

		CriteriaQuery<Long> transactionsCriteriaQuery = criteriaBuilder.createQuery(Long.class);
		Root<FinanceTransaction> tr = transactionsCriteriaQuery.from(FinanceTransaction.class);

		transactionsCriteriaQuery.select(criteriaBuilder.countDistinct(tr));

		Long result = entityManager.createQuery(transactionsCriteriaQuery).getSingleResult();
		entityManager.close();
		transactionsCount = result;
		return result;
	}

	/**
	 * Retrieves all accounts from the database
	 *
	 * @return the list of all accounts stored in the database
	 */
	protected List<FinanceAccount> getAccountsFromDatabase() {
		EntityManager entityManager = DatabaseManager.getInstance().createEntityManager();

		CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
		CriteriaQuery<FinanceAccount> accountsCriteriaQuery = criteriaBuilder.createQuery(FinanceAccount.class);
		Root<FinanceAccount> acc = accountsCriteriaQuery.from(FinanceAccount.class);

		List<FinanceAccount> result = entityManager.createQuery(accountsCriteriaQuery).getResultList();
		entityManager.close();
		return result;
	}

	/**
	 * Retrieves all currency exchange rates from the database
	 *
	 * @param entityManager the entity manager (used for obtaining the same
	 * classes from DB)
	 * @return the list of all currency exchange rates stored in the database
	 */
	protected List<CurrencyRate> getCurrencyRatesFromDatabase(EntityManager entityManager) {
		CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
		CriteriaQuery<CurrencyRate> exchangeRatesCriteriaQuery = criteriaBuilder.createQuery(CurrencyRate.class);
		Root<CurrencyRate> er = exchangeRatesCriteriaQuery.from(CurrencyRate.class);
		exchangeRatesCriteriaQuery.orderBy(
				criteriaBuilder.asc(er.get(CurrencyRate_.source)),
				criteriaBuilder.asc(er.get(CurrencyRate_.destination)));
		return entityManager.createQuery(exchangeRatesCriteriaQuery).getResultList();
	}

	/**
	 * Retrieves the Preferences class instance from the database
	 *
	 * @param entityManager the entity manager (used for obtaining the same
	 * classes from DB)
	 * @return the Preferences class instance, or a new persisted instance if
	 * the database doesn't contain any
	 */
	protected Preferences getPreferencesFromDatabase(EntityManager entityManager) {
		CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
		CriteriaQuery<Preferences> preferencesCriteriaQuery = criteriaBuilder.createQuery(Preferences.class);
		Root<Preferences> prf = preferencesCriteriaQuery.from(Preferences.class);
		Preferences preferences = null;
		try {
			preferences = entityManager.createQuery(preferencesCriteriaQuery).getSingleResult();
		} catch (javax.persistence.NoResultException ex) {
		}
		if (preferences == null) {
			preferences = new Preferences();
			entityManager.getTransaction().begin();
			entityManager.persist(preferences);
			entityManager.getTransaction().commit();
		}
		return preferences;
	}

	/**
	 * Retrieves the default currency from the database
	 *
	 * @param entityManager the entity manager (used for obtaining the same
	 * classes from DB)
	 * @return the default currency stored in the database, or the system locale
	 * currency
	 */
	protected Currency getDefaultCurrencyFromDatabase(EntityManager entityManager) {
		Preferences preferences = getPreferencesFromDatabase(entityManager);
		Currency currency = preferences.getDefaultCurrency();
		if (currency == null) {
			currency = Currency.getInstance(Locale.getDefault());

			entityManager.merge(preferences);
		}
		return currency;
	}

	/**
	 * Internal helper function Adds an account to the list & persists it (if
	 * necessary) Safe to call even if the account already exists Should only be
	 * called from an started transaction
	 *
	 * @param account the account to be added
	 * @param entityManager the entity manager with an initiated transaction
	 * @return true if account was added, false if it's already present in the
	 * accounts list
	 */
	protected boolean persistenceAdd(FinanceAccount account, EntityManager entityManager) {
		if (account == null)
			return false;

		boolean result = false;

		if (entityManager.find(FinanceAccount.class, account.id) == null) {
			entityManager.persist(account);
			result = true;
		}

		populateCurrencies();

		return result;
	}

	/**
	 * Internal helper function Adds a transaction to the list & persists it and
	 * its components (if necessary) Safe to call even if the transaction
	 * already exists Should only be called from an started transaction
	 *
	 * @param transaction the transaction to be added
	 * @param entityManager the entity manager
	 * @return true if transaction was added, false if it's already present in
	 * the transactions list
	 */
	protected boolean persistenceAdd(FinanceTransaction transaction, EntityManager entityManager) {
		if (transaction == null)
			return false;

		boolean result = false;

		for (TransactionComponent component : transaction.getComponents())
			if (entityManager.find(TransactionComponent.class, component.id) == null)
				entityManager.persist(component);

		if (entityManager.find(FinanceTransaction.class, transaction.id) == null) {
			entityManager.persist(transaction);
			result = true;
		}

		return result;
	}

	/**
	 * Internal helper function Adds a transaction component to the list &
	 * persists it and its transaction (if necessary) Safe to call even if the
	 * transaction component already exists Should only be called from an
	 * started transaction
	 *
	 * @param component the component to be added
	 * @param entityManager the entity manager
	 * @return true if component was added, false if it's already present in the
	 * components list
	 */
	protected boolean persistenceAdd(TransactionComponent component, EntityManager entityManager) {
		if (component == null)
			return false;

		boolean result = false;

		if (component.getTransaction() != null)
			if (entityManager.find(FinanceTransaction.class, component.getTransaction().id) == null)
				entityManager.persist(component.getTransaction());

		if (entityManager.find(TransactionComponent.class, component.id) == null) {
			entityManager.persist(component);
			result = true;
		}

		return result;
	}

	/**
	 * Returns the total balance for all accounts with a specific currency
	 *
	 * @param currency the currency (or null if the balance should be calculated
	 * for all currencies)
	 * @return the total balance
	 */
	public double getTotalBalance(Currency currency) {
		long totalBalance = 0;
		for (FinanceAccount account : getAccountsFromDatabase()) {
			if (!account.getIncludeInTotal())
				continue;
			if (account.getCurrency() == currency)
				totalBalance += account.getRawBalance();
			else if (currency == null)
				totalBalance += Math.round(account.getBalance() * getExchangeRate(account.getCurrency(), getDefaultCurrency()) * Constants.rawAmountMultiplier);
		}
		return totalBalance / Constants.rawAmountMultiplier;
	}

	/**
	 * Returns the list of all currencies used in this instance
	 *
	 * @return the list of used currencies
	 */
	public List<Currency> getCurrencies() {
		List<Currency> currencies = new LinkedList<>();
		for (CurrencyRate rate : exchangeRates) {
			if (!currencies.contains(rate.getSource()))
				currencies.add(rate.getSource());
			if (!currencies.contains(rate.getDestination()))
				currencies.add(rate.getDestination());
		}
		return currencies;
	}

	/**
	 * Automatically creates missing currency exchange rates Should only be
	 * called from an started transaction
	 */
	protected void populateCurrencies() {
		EntityManager entityManager = DatabaseManager.getInstance().createEntityManager();

		entityManager.getTransaction().begin();

		//Search for missing currencies
		List<CurrencyRate> usedRates = new LinkedList<>();
		List<FinanceAccount> accounts = getAccountsFromDatabase();
		for (FinanceAccount account1 : accounts) {
			for (FinanceAccount account2 : accounts) {
				if (account1.getCurrency() != account2.getCurrency()) {
					CurrencyRate rateFrom = null, rateTo = null;
					//Check that currencies between account1 and account2 can be converted
					for (CurrencyRate rate : exchangeRates) {
						if (rate.getSource() == account1.getCurrency() && rate.getDestination() == account2.getCurrency())
							rateFrom = rate;
						if (rate.getDestination() == account1.getCurrency() && rate.getSource() == account2.getCurrency())
							rateTo = rate;
					}
					//Add missing currency rates
					if (rateFrom == null) {
						CurrencyRate rate = new CurrencyRate(account1.getCurrency(), account2.getCurrency(), 1.0);
						entityManager.persist(rate);
						exchangeRates.add(rate);
						usedRates.add(rate);
					} else if (!usedRates.contains(rateFrom))
						usedRates.add(rateFrom);
					if (rateTo == null) {
						CurrencyRate rate = new CurrencyRate(account2.getCurrency(), account1.getCurrency(), 1.0);
						entityManager.persist(rate);
						exchangeRates.add(rate);
						usedRates.add(rate);
					} else if (!usedRates.contains(rateTo))
						usedRates.add(rateTo);
				}
			}
		}

		//Remove orphaned currencies
		for (CurrencyRate rate : exchangeRates) {
			if (!usedRates.contains(rate)) {
				CurrencyRate foundRate = entityManager.find(CurrencyRate.class, rate.id);
				if (foundRate != null)
					entityManager.remove(foundRate);
			}
		}

		entityManager.getTransaction().commit();

		entityManager.close();

		exchangeRates.clear();
		exchangeRates.addAll(usedRates);
	}

	/**
	 * Adds a new account
	 *
	 * @param account the account to be added
	 */
	public void createAccount(FinanceAccount account) {
		EntityManager entityManager = DatabaseManager.getInstance().createEntityManager();
		entityManager.getTransaction().begin();

		boolean accountAdded = persistenceAdd(account, entityManager);

		entityManager.getTransaction().commit();

		if (accountAdded) {
			fireAccountsUpdated();
			fireAccountCreated(account.id);
			fireCurrenciesUpdated();
		}

		entityManager.close();
	}

	/**
	 * Sets a new account name. Adds the account to the persistence if needed.
	 *
	 * @param account the account to be updated
	 * @param name the new account name
	 */
	public void setAccountName(FinanceAccount account, String name) {
		EntityManager entityManager = DatabaseManager.getInstance().createEntityManager();
		entityManager.getTransaction().begin();

		boolean accountAdded = persistenceAdd(account, entityManager);

		account = entityManager.find(FinanceAccount.class, account.id);

		account.setName(name);
		entityManager.merge(account);

		entityManager.getTransaction().commit();

		if (accountAdded) {
			fireAccountCreated(account.id);
			fireCurrenciesUpdated();
		}
		fireAccountUpdated(account.id);

		entityManager.close();
	}

	/**
	 * Sets a new account currency. Adds the account to the persistence if
	 * needed.
	 *
	 * @param account the account to be updated
	 * @param currency the new account currency
	 */
	public void setAccountCurrency(FinanceAccount account, Currency currency) {
		EntityManager entityManager = DatabaseManager.getInstance().createEntityManager();
		entityManager.getTransaction().begin();

		persistenceAdd(account, entityManager);

		account = entityManager.find(FinanceAccount.class, account.id);

		account.setCurrency(currency);
		entityManager.merge(account);

		entityManager.getTransaction().commit();
		entityManager.close();

		populateCurrencies();

		fireAccountsUpdated();
		fireCurrenciesUpdated();
	}

	/**
	 * Sets if this account should be included in the total for all accounts.
	 *
	 * @param account the account to be updated
	 * @param includeInTotal true if the account should be included in the total
	 */
	public void setAccountIncludeInTotal(FinanceAccount account, boolean includeInTotal) {
		EntityManager entityManager = DatabaseManager.getInstance().createEntityManager();
		entityManager.getTransaction().begin();

		persistenceAdd(account, entityManager);

		account = entityManager.find(FinanceAccount.class, account.id);

		account.setIncludeInTotal(includeInTotal);
		entityManager.merge(account);

		populateCurrencies();

		entityManager.getTransaction().commit();
		entityManager.close();

		fireAccountsUpdated();
	}

	/**
	 * Adds a new transaction
	 *
	 * @param transaction the transaction to be added
	 */
	public void createTransaction(FinanceTransaction transaction) {
		EntityManager entityManager = DatabaseManager.getInstance().createEntityManager();
		entityManager.getTransaction().begin();

		boolean transactionAdded = persistenceAdd(transaction, entityManager);

		entityManager.getTransaction().commit();

		if (transactionAdded) {
			transactionsCount++;
			fireTransactionsUpdated();
			fireTransactionCreated(transaction.id);
		}
		for (FinanceAccount account : transaction.getAccounts())
			fireAccountUpdated(account.id);

		entityManager.close();
	}

	/**
	 * Adds a new transaction
	 *
	 * @param component the component to be added
	 */
	public void createTransactionComponent(TransactionComponent component) {
		EntityManager entityManager = DatabaseManager.getInstance().createEntityManager();
		entityManager.getTransaction().begin();

		if (component.getTransaction() != null) {
			persistenceAdd(component.getTransaction(), entityManager);
			persistenceAdd(component, entityManager);
			if (!component.getTransaction().getComponents().contains(component))
				component.getTransaction().addComponent(component);
			entityManager.merge(component.getTransaction());
		}

		entityManager.getTransaction().commit();

		if (component.getTransaction() != null)
			fireTransactionUpdated(component.getTransaction().id);
		if (component.getAccount() != null)
			fireAccountUpdated(component.getAccount().id);

		entityManager.close();
	}

	/**
	 * Sets new tags for a transaction
	 *
	 * @param transaction the transaction to be updated
	 * @param tags the new tags
	 */
	public void setTransactionTags(FinanceTransaction transaction, String[] tags) {
		EntityManager entityManager = DatabaseManager.getInstance().createEntityManager();
		entityManager.getTransaction().begin();

		transaction = entityManager.find(FinanceTransaction.class, transaction.id);

		boolean transactionAdded = persistenceAdd(transaction, entityManager);

		transaction.setTags(tags);
		entityManager.merge(transaction);

		entityManager.getTransaction().commit();

		if (transactionAdded) {
			fireTransactionsUpdated();
			fireTransactionCreated(transaction.id);
		}
		fireTransactionUpdated(transaction.id);
		for (FinanceAccount account : transaction.getAccounts())
			fireAccountUpdated(account.id);

		entityManager.close();
	}

	/**
	 * Sets a new date for a transaction
	 *
	 * @param transaction the transaction to be updated
	 * @param date the new date
	 */
	public void setTransactionDate(FinanceTransaction transaction, Date date) {
		EntityManager entityManager = DatabaseManager.getInstance().createEntityManager();
		entityManager.getTransaction().begin();

		boolean transactionAdded = persistenceAdd(transaction, entityManager);

		transaction = entityManager.find(FinanceTransaction.class, transaction.id);

		transaction.setDate(date);
		entityManager.merge(transaction);

		entityManager.getTransaction().commit();

		if (transactionAdded) {
			fireTransactionsUpdated();
			fireTransactionCreated(transaction.id);
		}
		fireTransactionUpdated(transaction.id);
		for (FinanceAccount account : transaction.getAccounts())
			fireAccountUpdated(account.id);

		entityManager.close();
	}

	/**
	 * Sets a new description for a transaction
	 *
	 * @param transaction the transaction to be updated
	 * @param description the new description
	 */
	public void setTransactionDescription(FinanceTransaction transaction, String description) {
		EntityManager entityManager = DatabaseManager.getInstance().createEntityManager();
		entityManager.getTransaction().begin();

		boolean transactionAdded = persistenceAdd(transaction, entityManager);

		transaction = entityManager.find(FinanceTransaction.class, transaction.id);

		transaction.setDescription(description);
		entityManager.merge(transaction);

		entityManager.getTransaction().commit();

		if (transactionAdded) {
			fireTransactionsUpdated();
			fireTransactionCreated(transaction.id);
		}
		fireTransactionUpdated(transaction.id);
		for (FinanceAccount account : transaction.getAccounts())
			fireAccountUpdated(account.id);

		entityManager.close();
	}

	/**
	 * Sets a new type for a transaction
	 *
	 * @param transaction the transaction to be updated
	 * @param type the new transaction type
	 */
	public void setTransactionType(FinanceTransaction transaction, FinanceTransaction.Type type) {
		EntityManager entityManager = DatabaseManager.getInstance().createEntityManager();
		entityManager.getTransaction().begin();

		boolean transactionAdded = persistenceAdd(transaction, entityManager);

		transaction = entityManager.find(FinanceTransaction.class, transaction.id);

		transaction.setType(type);
		entityManager.merge(transaction);

		entityManager.getTransaction().commit();

		if (transactionAdded) {
			fireTransactionsUpdated();
			fireTransactionCreated(transaction.id);
		}
		fireTransactionUpdated(transaction.id);
		for (FinanceAccount account : transaction.getAccounts())
			fireAccountUpdated(account.id);

		entityManager.close();
	}

	/**
	 * Sets an transaction component amount
	 *
	 * @param component the component to be updated
	 * @param newAmount the new amount
	 */
	public void setTransactionComponentAmount(TransactionComponent component, double newAmount) {
		EntityManager entityManager = DatabaseManager.getInstance().createEntityManager();
		entityManager.getTransaction().begin();

		boolean transactionAdded = persistenceAdd(component.getTransaction(), entityManager);

		if (entityManager.find(TransactionComponent.class, component.id) == null)
			entityManager.persist(component);

		component = entityManager.find(TransactionComponent.class, component.id);

		//Update accounts
		if (component.getTransaction().getComponents().contains(component))
			component.getTransaction().updateComponentRawAmount(component, Math.round(newAmount * Constants.rawAmountMultiplier));
		else {
			component.setRawAmount(Math.round(newAmount * Constants.rawAmountMultiplier));
			component.getTransaction().addComponent(component);
		}

		//Update component
		entityManager.merge(component);
		if (component.getAccount() != null)
			entityManager.merge(component.getAccount());
		if (component.getTransaction() != null)
			entityManager.merge(component.getTransaction());

		entityManager.getTransaction().commit();

		if (transactionAdded) {
			fireTransactionsUpdated();
			fireTransactionCreated(component.getTransaction().id);
		}
		fireTransactionUpdated(component.getTransaction().id);

		fireAccountUpdated(component.getAccount().id);

		entityManager.close();
	}

	/**
	 * Sets an transaction component account
	 *
	 * @param component the component to be updated
	 * @param newAccount the new account
	 */
	public void setTransactionComponentAccount(TransactionComponent component, FinanceAccount newAccount) {
		EntityManager entityManager = DatabaseManager.getInstance().createEntityManager();
		entityManager.getTransaction().begin();

		boolean transactionAdded = persistenceAdd(component.getTransaction(), entityManager);

		if (entityManager.find(TransactionComponent.class, component.id) == null)
			entityManager.persist(component);

		persistenceAdd(newAccount, entityManager);

		component = entityManager.find(TransactionComponent.class, component.id);
		newAccount = entityManager.find(FinanceAccount.class, newAccount.id);

		FinanceAccount oldAccount = component.getAccount();

		//Update accounts
		if (component.getTransaction().getComponents().contains(component))
			component.getTransaction().updateComponentAccount(component, newAccount);
		else {
			component.setAccount(newAccount);
			component.getTransaction().addComponent(component);
		}

		//Update transaction and accounts in DB
		entityManager.merge(component);
		if (component.getTransaction() != null)
			entityManager.merge(component.getTransaction());
		if (component.getAccount() != null)
			entityManager.merge(component.getAccount());
		if (oldAccount != null && component.getAccount() != oldAccount)
			entityManager.merge(oldAccount);

		entityManager.getTransaction().commit();

		if (transactionAdded) {
			fireTransactionsUpdated();
			fireTransactionCreated(component.getTransaction().id);
		}
		fireTransactionUpdated(component.getTransaction().id);

		fireAccountUpdated(component.getAccount().id);
		if (component.getAccount() != oldAccount && oldAccount != null)
			fireAccountUpdated(oldAccount.id);

		entityManager.close();
	}

	/**
	 * Sets the new exchange rate
	 *
	 * @param rate the currency rate to be modified
	 * @param newRate the new exchange rate
	 */
	public void setExchangeRate(CurrencyRate rate, double newRate) {
		if (!exchangeRates.contains(rate))
			return;
		EntityManager entityManager = DatabaseManager.getInstance().createEntityManager();
		entityManager.getTransaction().begin();

		rate.setExchangeRate(newRate);
		entityManager.merge(rate);

		entityManager.getTransaction().commit();
		entityManager.close();

		fireTransactionsUpdated();
		fireCurrenciesUpdated();
		fireAccountsUpdated();
	}

	/**
	 * Sets the default currency
	 *
	 * @param defaultCurrency the new default currency
	 */
	public void setDefaultCurrency(Currency defaultCurrency) {
		if (defaultCurrency == null)
			return;

		this.defaultCurrency = defaultCurrency;

		EntityManager entityManager = DatabaseManager.getInstance().createEntityManager();
		Preferences preferences = getPreferencesFromDatabase(entityManager);
		entityManager.getTransaction().begin();

		preferences.setDefaultCurrency(defaultCurrency);
		entityManager.merge(preferences);

		entityManager.getTransaction().commit();
		entityManager.close();

		fireTransactionsUpdated();
		fireAccountsUpdated();
	}

	/**
	 * Returns an exchange rate for a pair of currencies
	 *
	 * @param source the source currency
	 * @param destination the target currency
	 * @return the source=>target exchange rate
	 */
	public double getExchangeRate(Currency source, Currency destination) {
		if (source == destination)
			return 1.0;
		for (CurrencyRate rate : exchangeRates) {
			if (rate.getSource() == source && rate.getDestination() == destination)
				return rate.getExchangeRate();
		}
		return Double.NaN;
	}

	/**
	 * Returns the transaction amount converted to a specific currency
	 *
	 * @param transaction the transaction
	 * @param currency the target currency
	 * @return the transaction amount, converted to the target currency
	 */
	public double getAmountInCurrency(FinanceTransaction transaction, Currency currency) {
		double amount = 0;
		for (TransactionComponent component : transaction.getComponents()) {
			double rate = getExchangeRate(component.getAccount() != null ? component.getAccount().getCurrency() : null, currency);
			amount += rate * component.getAmount();
		}
		return amount;
	}

	/**
	 * Deletes a transaction component (with all dependencies)
	 *
	 * @param component the transaction component to delete
	 */
	public void deleteTransactionComponent(TransactionComponent component) {
		EntityManager entityManager = DatabaseManager.getInstance().createEntityManager();
		entityManager.getTransaction().begin();

		persistenceAdd(component.getTransaction(), entityManager);

		component = entityManager.find(TransactionComponent.class, component.id);

		FinanceTransaction transaction = component.getTransaction();
		FinanceAccount account = component.getAccount();
		if (transaction != null) {
			component.getTransaction().removeComponent(component);
			entityManager.merge(transaction);
		}

		entityManager.remove(entityManager.find(TransactionComponent.class, component.id));

		if (account != null)
			entityManager.merge(account);

		entityManager.getTransaction().commit();

		fireTransactionUpdated(transaction.id);
		fireAccountUpdated(account.id);

		entityManager.close();
	}

	/**
	 * Deletes a transaction (with all dependencies)
	 *
	 * @param transaction the transaction to delete
	 */
	public void deleteTransaction(FinanceTransaction transaction) {
		EntityManager entityManager = DatabaseManager.getInstance().createEntityManager();
		entityManager.getTransaction().begin();

		transaction = entityManager.find(FinanceTransaction.class, transaction.id);

		if (transaction == null)
			return;

		List<FinanceAccount> affectedAccounts = transaction.getAccounts();

		//Remove all components
		for (TransactionComponent component : transaction.getComponents())
			entityManager.remove(entityManager.find(TransactionComponent.class, component.id));
		transaction.removeAllComponents();

		//Remove transaction
		FinanceTransaction foundTransaction = entityManager.find(FinanceTransaction.class, transaction.id);
		entityManager.remove(foundTransaction);
		for (FinanceAccount account : affectedAccounts)
			entityManager.merge(account);
		entityManager.getTransaction().commit();

		if (foundTransaction != null)
			transactionsCount--;
		fireTransactionsUpdated();
		fireTransactionDeleted(transaction.id);

		for (FinanceAccount account : affectedAccounts)
			fireAccountUpdated(account.id);

		entityManager.close();
	}

	/**
	 * Deletes an account (with all dependencies)
	 *
	 * @param account the account to delete
	 */
	public void deleteAccount(FinanceAccount account) {
		EntityManager entityManager = DatabaseManager.getInstance().createEntityManager();
		entityManager.getTransaction().begin();

		//Delete all related transaction components
		for (FinanceTransaction transaction : getTransactions()) {
			List<TransactionComponent> components = transaction.getComponentsForAccount(account);
			transaction.removeComponents(components);
			if (!components.isEmpty())
				entityManager.merge(transaction);
			for (TransactionComponent component : components)
				entityManager.remove(entityManager.find(TransactionComponent.class, component.id));
		}

		//Remove account
		entityManager.remove(entityManager.find(FinanceAccount.class, account.id));

		entityManager.getTransaction().commit();

		populateCurrencies();

		fireTransactionsUpdated();
		fireAccountsUpdated();
		fireAccountDeleted(account.id);
		fireCurrenciesUpdated();

		entityManager.close();
	}

	/**
	 * Recalculates an account's balance based on its transactions
	 *
	 * @param account the account to be updated
	 */
	public void refreshAccountBalance(FinanceAccount account) {
		//Request all transactions from database
		EntityManager tempEntityManager = DatabaseManager.getInstance().createEntityManager();
		CriteriaBuilder criteriaBuilder = tempEntityManager.getCriteriaBuilder();
		CriteriaQuery<FinanceTransaction> transactionsCriteriaQuery = criteriaBuilder.createQuery(FinanceTransaction.class);
		Root<FinanceTransaction> ftr = transactionsCriteriaQuery.from(FinanceTransaction.class);
		FinanceAccount tempAccount = tempEntityManager.find(FinanceAccount.class, account.id);

		//Recalculate balance from related transactions
		tempEntityManager.getTransaction().begin();
		tempAccount.updateRawBalance(-tempAccount.getRawBalance());

		TypedQuery<FinanceTransaction> transactionsBatchQuery = tempEntityManager.createQuery(transactionsCriteriaQuery);

		int currentTransaction = 0;
		boolean done = false;
		while (!done) {
			List<FinanceTransaction> transactions = transactionsBatchQuery.setFirstResult(currentTransaction).setMaxResults(Constants.batchFetchSize).getResultList();
			currentTransaction += transactions.size();
			done = transactions.isEmpty();
			for (FinanceTransaction transaction : transactions)
				for (TransactionComponent component : transaction.getComponentsForAccount(tempAccount))
					tempAccount.updateRawBalance(component.getRawAmount());
		}
		tempEntityManager.getTransaction().commit();

		//Update real account balance from temporary account
		account.updateRawBalance(-account.getRawBalance() + tempAccount.getRawBalance());

		fireAccountUpdated(account.id);

		tempEntityManager.close();
	}

	/**
	 * Deletes all orphaned transactions, accounts and transaction components
	 */
	public void cleanup() {
		EntityManager tempEntityManager = DatabaseManager.getInstance().createEntityManager();
		CriteriaBuilder componentCriteriaBuilder = tempEntityManager.getCriteriaBuilder();
		CriteriaQuery<TransactionComponent> componentsCriteriaQuery = componentCriteriaBuilder.createQuery(TransactionComponent.class);
		Root<TransactionComponent> trc = componentsCriteriaQuery.from(TransactionComponent.class);

		CriteriaBuilder transactionCriteriaBuilder = tempEntityManager.getCriteriaBuilder();
		CriteriaQuery<FinanceTransaction> transactionsCriteriaQuery = transactionCriteriaBuilder.createQuery(FinanceTransaction.class);
		Root<FinanceTransaction> tr = transactionsCriteriaQuery.from(FinanceTransaction.class);

		tempEntityManager.getTransaction().begin();

		//Get all data from DB
		List<TransactionComponent> componentsDB = tempEntityManager.createQuery(componentsCriteriaQuery).getResultList();
		List<FinanceTransaction> transactionsDB = tempEntityManager.createQuery(transactionsCriteriaQuery).getResultList();

		//Remove OK items from list
		for (FinanceTransaction transaction : transactionsDB)
			componentsDB.removeAll(transaction.getComponents());

		//Remove anything that still exists
		for (TransactionComponent component : componentsDB) {
			if (component.getTransaction() != null)
				component.getTransaction().removeComponent(component);
			component.setTransaction(null);
			tempEntityManager.remove(component);
		}

		tempEntityManager.getTransaction().commit();
		tempEntityManager.close();

		populateCurrencies();

		restoreFromDatabase();

		fireTransactionsUpdated();
		fireAccountsUpdated();
		fireCurrenciesUpdated();
	}

	/*
	 * Getters/setters
	 */
	/**
	 * Returns the list of accounts
	 *
	 * @return the list of accounts
	 */
	public List<FinanceAccount> getAccounts() {
		return getAccountsFromDatabase();
	}

	/**
	 * Returns the list of transactions (from firstTransaction to
	 * lastTransaction)
	 *
	 * @param firstTransaction the first transaction number to be selected
	 * @param lastTransaction the last transaction number to be selected
	 * @return the list of transactions
	 */
	public List<FinanceTransaction> getTransactions(int firstTransaction, int lastTransaction) {
		return getTransactionsFromDatabase(firstTransaction, lastTransaction);
	}

	/**
	 * Returns a specific transaction from database
	 *
	 * @param index the transaction's index
	 * @return all transactions in the database
	 */
	public FinanceTransaction getTransaction(int index) {
		List<FinanceTransaction> transactions = getTransactionsFromDatabase(index, index);
		return transactions.isEmpty() ? null : transactions.get(0);
	}

	/**
	 * Returns all transactions from database
	 *
	 * @return all transactions in the database
	 */
	public List<FinanceTransaction> getTransactions() {
		return getTransactionsFromDatabase(-1, -1);
	}

	/**
	 * Returns number of transactions
	 *
	 * @return the number of transactions
	 */
	public int getTransactionCount() {
		return (int) transactionsCount;
	}

	/**
	 * Returns the list of currency rates
	 *
	 * @return the list of currency rates
	 */
	public List<CurrencyRate> getCurrencyRates() {
		return exchangeRates;
	}

	/**
	 * Returns the default currency
	 *
	 * @return the default currency
	 */
	public Currency getDefaultCurrency() {
		if (defaultCurrency != null)
			return defaultCurrency;
		else
			return null;
	}

	/*
	 * Assigned event handlers
	 */
	/**
	 * Transaction event handler
	 */
	protected TransactionEventHandler transactionEventHandler;
	/**
	 * Account event handler
	 */
	protected AccountEventHandler accountEventHandler;
	/**
	 * Currency event handler
	 */
	protected CurrencyEventHandler currencyEventHandler;

	/**
	 * Dispatches a transaction created event
	 *
	 * @param transactionId the transaction that was created
	 */
	protected void fireTransactionCreated(long transactionId) {
		if (transactionEventHandler != null)
			transactionEventHandler.transactionCreated(transactionId);
	}

	/**
	 * Dispatches a transaction updated event
	 *
	 * @param transactionId the transaction that was updated
	 */
	protected void fireTransactionUpdated(long transactionId) {
		if (transactionEventHandler != null)
			transactionEventHandler.transactionUpdated(transactionId);
	}

	/**
	 * Dispatches a transactions updated event (all transactions were updated)
	 */
	protected void fireTransactionsUpdated() {
		if (transactionEventHandler != null)
			transactionEventHandler.transactionsUpdated();
	}

	/**
	 * Dispatches a transaction deleted event
	 *
	 * @param transactionId the transaction that was deleted
	 */
	protected void fireTransactionDeleted(long transactionId) {
		if (transactionEventHandler != null)
			transactionEventHandler.transactionDeleted(transactionId);
	}

	/**
	 * Dispatches an account created event
	 *
	 * @param accountId the account that was created
	 */
	protected void fireAccountCreated(long accountId) {
		if (accountEventHandler != null)
			accountEventHandler.accountCreated(accountId);
	}

	/**
	 * Dispatches an account updated event
	 *
	 * @param accountId the account that was updated
	 */
	protected void fireAccountUpdated(long accountId) {
		if (accountEventHandler != null)
			accountEventHandler.accountUpdated(accountId);
	}

	/**
	 * Dispatches an accounts updated event (all accounts were updated)
	 */
	protected void fireAccountsUpdated() {
		if (accountEventHandler != null)
			accountEventHandler.accountsUpdated();
	}

	/**
	 * Dispatches an account deleted event
	 *
	 * @param accountId the account that was deleted
	 */
	protected void fireAccountDeleted(long accountId) {
		if (accountEventHandler != null)
			accountEventHandler.accountDeleted(accountId);
	}

	/**
	 * Dispatches a currencies updated event (all accounts were updated)
	 */
	protected void fireCurrenciesUpdated() {
		if (currencyEventHandler != null)
			currencyEventHandler.currenciesUpdated();
	}

	/**
	 * Adds a new listener for transaction events
	 *
	 * @param transactionEventHandler the event handler
	 */
	public void setTransactionListener(TransactionEventHandler transactionEventHandler) {
		this.transactionEventHandler = transactionEventHandler;
	}

	/**
	 * Returns the listener for transaction events
	 *
	 * @return the event handler
	 */
	public TransactionEventHandler getTransactionListener() {
		return transactionEventHandler;
	}

	/**
	 * Adds a new listener for account events
	 *
	 * @param accountEventHandler the event handler
	 */
	public void setAccountListener(AccountEventHandler accountEventHandler) {
		this.accountEventHandler = accountEventHandler;
	}

	/**
	 * Returns the listener for account events
	 *
	 * @return the event handler
	 */
	public AccountEventHandler getAccountListener() {
		return accountEventHandler;
	}

	/**
	 * Adds a new listener for currency events
	 *
	 * @param currencyEventHandler the event handler
	 */
	public void setCurrencyListener(CurrencyEventHandler currencyEventHandler) {
		this.currencyEventHandler = currencyEventHandler;
	}

	/**
	 * Returns the listener for currency events
	 *
	 * @return the event handler
	 */
	public CurrencyEventHandler getCurrencyListener() {
		return currencyEventHandler;
	}
}
