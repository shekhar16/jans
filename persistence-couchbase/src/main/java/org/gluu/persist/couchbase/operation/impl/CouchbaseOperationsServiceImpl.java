/*
 * oxCore is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */

package org.gluu.persist.couchbase.operation.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;

import org.gluu.persist.couchbase.impl.CouchbaseBatchOperationWraper;
import org.gluu.persist.couchbase.model.BucketMapping;
import org.gluu.persist.couchbase.model.SearchReturnDataType;
import org.gluu.persist.couchbase.operation.CouchbaseOperationService;
import org.gluu.persist.couchbase.operation.watch.OperationDurationUtil;
import org.gluu.persist.exception.operation.DuplicateEntryException;
import org.gluu.persist.exception.operation.EntryNotFoundException;
import org.gluu.persist.exception.operation.PersistenceException;
import org.gluu.persist.exception.operation.SearchException;
import org.gluu.persist.model.BatchOperation;
import org.gluu.persist.model.PagedResult;
import org.gluu.persist.model.SearchScope;
import org.gluu.persist.operation.auth.PasswordEncryptionHelper;
import org.gluu.util.ArrayHelper;
import org.gluu.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.couchbase.client.core.CouchbaseException;
import com.couchbase.client.core.message.kv.subdoc.multi.Mutation;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.document.JsonDocument;
import com.couchbase.client.java.document.json.JsonArray;
import com.couchbase.client.java.document.json.JsonObject;
import com.couchbase.client.java.query.Delete;
import com.couchbase.client.java.query.N1qlParams;
import com.couchbase.client.java.query.N1qlQuery;
import com.couchbase.client.java.query.N1qlQueryResult;
import com.couchbase.client.java.query.N1qlQueryRow;
import com.couchbase.client.java.query.Select;
import com.couchbase.client.java.query.Statement;
import com.couchbase.client.java.query.consistency.ScanConsistency;
import com.couchbase.client.java.query.dsl.Expression;
import com.couchbase.client.java.query.dsl.Sort;
import com.couchbase.client.java.query.dsl.path.GroupByPath;
import com.couchbase.client.java.query.dsl.path.LimitPath;
import com.couchbase.client.java.query.dsl.path.MutateLimitPath;
import com.couchbase.client.java.query.dsl.path.OffsetPath;
import com.couchbase.client.java.subdoc.DocumentFragment;
import com.couchbase.client.java.subdoc.MutateInBuilder;
import com.couchbase.client.java.subdoc.MutationSpec;

/**
 * Base service which performs all supported Couchbase operations
 *
 * @author Yuriy Movchan Date: 05/10/2018
 */
public class CouchbaseOperationsServiceImpl implements CouchbaseOperationService {

    private static final Logger LOG = LoggerFactory.getLogger(CouchbaseConnectionProvider.class);

    private CouchbaseConnectionProvider connectionProvider;

    @SuppressWarnings("unused")
    private CouchbaseOperationsServiceImpl() {
    }

    public CouchbaseOperationsServiceImpl(CouchbaseConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Override
    public CouchbaseConnectionProvider getConnectionProvider() {
        return connectionProvider;
    }

    @Override
    public boolean authenticate(final String key, final String password) throws SearchException {
        return authenticateImpl(key, password);
    }

    private boolean authenticateImpl(final String key, final String password) throws SearchException {
        Instant startTime = OperationDurationUtil.instance().now();
        
        boolean result = false;
        if (password != null) {
	        JsonObject entry = lookup(key, USER_PASSWORD);
	        Object userPasswordObj = entry.get(USER_PASSWORD);
	
	        String userPassword = null;
	        if (userPasswordObj instanceof JsonArray) {
	            userPassword = ((JsonArray) userPasswordObj).getString(0);
	        } else if (userPasswordObj instanceof String) {
	            userPassword = (String) userPasswordObj;
	        }
	
	        if (userPassword != null) {
	        	result = PasswordEncryptionHelper.compareCredentials(password.getBytes(), userPassword.getBytes());
	        }
        }

        Duration duration = OperationDurationUtil.instance().duration(startTime);

        BucketMapping bucketMapping = connectionProvider.getBucketMappingByKey(key);
        OperationDurationUtil.instance().logDebug("Couchbase operation: bind, duration: {}, bucket: {}, key: {}", duration, bucketMapping.getBucketName(), key);
        
        return result;
    }

    @Override
    public boolean addEntry(String key, JsonObject jsonObject) throws DuplicateEntryException, PersistenceException {
        Instant startTime = OperationDurationUtil.instance().now();

        BucketMapping bucketMapping = connectionProvider.getBucketMappingByKey(key);
        boolean result = addEntryImpl(bucketMapping, key, jsonObject);

        Duration duration = OperationDurationUtil.instance().duration(startTime);
        OperationDurationUtil.instance().logDebug("Couchbase operation: add, duration: {}, bucket: {}, key: {}, json: {}", duration, bucketMapping.getBucketName(), key, jsonObject);
        
        return result;
    }

	private boolean addEntryImpl(BucketMapping bucketMapping, String key, JsonObject jsonObject) throws PersistenceException {
		try {
            JsonDocument jsonDocument = JsonDocument.create(key, jsonObject);
            JsonDocument result = bucketMapping.getBucket().upsert(jsonDocument);
            if (result != null) {
                return true;
            }

        } catch (CouchbaseException ex) {
            throw new PersistenceException("Failed to add entry", ex);
        }

        return false;
	}

    @Deprecated
    protected boolean updateEntry(String key, JsonObject attrs) throws UnsupportedOperationException, SearchException {
        List<MutationSpec> mods = new ArrayList<MutationSpec>();

        for (Entry<String, Object> attrEntry : attrs.toMap().entrySet()) {
            String attributeName = attrEntry.getKey();
            Object attributeValue = attrEntry.getValue();
            if (attributeName.equalsIgnoreCase(CouchbaseOperationService.OBJECT_CLASS) || attributeName.equalsIgnoreCase(CouchbaseOperationService.DN)
                    || attributeName.equalsIgnoreCase(CouchbaseOperationService.USER_PASSWORD)) {
                continue;
            } else {
                if (attributeValue != null) {
                    mods.add(new MutationSpec(Mutation.REPLACE, attributeName, attributeValue));
                }
            }
        }

        return updateEntry(key, mods);
    }

    @Override
    public boolean updateEntry(String key, List<MutationSpec> mods) throws UnsupportedOperationException, SearchException {
        Instant startTime = OperationDurationUtil.instance().now();
        
        BucketMapping bucketMapping = connectionProvider.getBucketMappingByKey(key);
        boolean result = updateEntryImpl(bucketMapping, key, mods);

        Duration duration = OperationDurationUtil.instance().duration(startTime);
        OperationDurationUtil.instance().logDebug("Couchbase operation: modify, duration: {}, bucket: {}, key: {}, mods: {}", duration, bucketMapping.getBucketName(), key, mods);

        return result;
    }

	private boolean updateEntryImpl(BucketMapping bucketMapping, String key, List<MutationSpec> mods) throws SearchException {
		try {
            MutateInBuilder builder = bucketMapping.getBucket().mutateIn(key);

            return modifyEntry(builder, mods);
        } catch (final CouchbaseException ex) {
            throw new SearchException("Failed to update entry", ex);
        }
	}

    protected boolean modifyEntry(MutateInBuilder builder, List<MutationSpec> mods) throws UnsupportedOperationException, SearchException {
        try {
            for (MutationSpec mod : mods) {
                Mutation type = mod.type();
                if (Mutation.DICT_ADD == type) {
                    builder.insert(mod.path(), mod.fragment());
                } else if (Mutation.REPLACE == type) {
                    builder.replace(mod.path(), mod.fragment());
                } else if (Mutation.DELETE == type) {
                    builder.remove(mod.path());
                } else {
                    throw new UnsupportedOperationException("Operation type '" + type + "' is not implemented");
                }
            }

            DocumentFragment<Mutation> result = builder.execute();
            if (result.size() > 0) {
                return result.status(0).isSuccess();
            }

            return false;
        } catch (final CouchbaseException ex) {
            throw new SearchException("Failed to update entry", ex);
        }
    }

    @Override
    public boolean delete(String key) throws EntryNotFoundException {
        Instant startTime = OperationDurationUtil.instance().now();

        BucketMapping bucketMapping = connectionProvider.getBucketMappingByKey(key);
        boolean result = deleteImpl(bucketMapping, key);

        Duration duration = OperationDurationUtil.instance().duration(startTime);
        OperationDurationUtil.instance().logDebug("Couchbase operation: delete, duration: {}, bucket: {}, key: {}", duration, bucketMapping.getBucketName(), key);

        return result;
    }

	private boolean deleteImpl(BucketMapping bucketMapping, String key) throws EntryNotFoundException {
		try {
            JsonDocument result = bucketMapping.getBucket().remove(key);

            return (result != null) && (result.id() != null);
        } catch (CouchbaseException ex) {
            throw new EntryNotFoundException("Failed to delete entry", ex);
        }
	}

    @Override
    public boolean deleteRecursively(String key) throws EntryNotFoundException, SearchException {
        Instant startTime = OperationDurationUtil.instance().now();

        BucketMapping bucketMapping = connectionProvider.getBucketMappingByKey(key);
        boolean result = deleteRecursivelyImpl(bucketMapping, key);

        Duration duration = OperationDurationUtil.instance().duration(startTime);
        OperationDurationUtil.instance().logDebug("Couchbase operation: delete_tree, duration: {}, bucket: {}, key: {}", duration, bucketMapping.getBucketName(), key);

        return result;
    }

	private boolean deleteRecursivelyImpl(BucketMapping bucketMapping, String key) throws SearchException, EntryNotFoundException {
		try {
            MutateLimitPath deleteQuery = Delete.deleteFrom(Expression.i(bucketMapping.getBucketName()))
                    .where(Expression.path("META().id").like(Expression.s(key + "%")));

            N1qlQueryResult result = bucketMapping.getBucket().query(deleteQuery);
            if (!result.finalSuccess()) {
                throw new SearchException(String.format("Failed to delete entries. Query: '%s'. Errors: %s", bucketMapping, result.errors()),
                        result.info().errorCount());
            }

            return true;
        } catch (CouchbaseException ex) {
            throw new EntryNotFoundException("Failed to delete entry", ex);
        }
	}

    @Override
    public JsonObject lookup(String key, String... attributes) throws SearchException {
        Instant startTime = OperationDurationUtil.instance().now();
        
        BucketMapping bucketMapping = connectionProvider.getBucketMappingByKey(key);
        JsonObject result = lookupImpl(bucketMapping, key, attributes);

        Duration duration = OperationDurationUtil.instance().duration(startTime);
        OperationDurationUtil.instance().logDebug("Couchbase operation: lookup, duration: {}, bucket: {}, key: {}, attributes: {}", duration, bucketMapping.getBucketName(), key, attributes);

        return result;
    }

	private JsonObject lookupImpl(BucketMapping bucketMapping, String key, String... attributes) throws SearchException {
		try {
            if (ArrayHelper.isEmpty(attributes)) {
                JsonDocument doc = bucketMapping.getBucket().get(key);
                if (doc != null) {
                    return doc.content();
                }

            } else {
            	N1qlParams params = N1qlParams.build().consistency(ScanConsistency.STATEMENT_PLUS);
                N1qlQuery query = N1qlQuery
                        .simple(Select.select(attributes).from(Expression.i(bucketMapping.getBucketName())).useKeys(Expression.s(key)).limit(1), params);
                N1qlQueryResult result = bucketMapping.getBucket().query(query);
                if (!result.finalSuccess()) {
                    throw new SearchException(String.format("Failed to lookup entry. Errors: %s", result.errors()), result.info().errorCount());
                }

                if (result.allRows().size() == 1) {
                    return result.allRows().get(0).value();
                }
            }
        } catch (CouchbaseException ex) {
            throw new SearchException("Failed to lookup entry", ex);
        }

        throw new SearchException("Failed to lookup entry");
	}

	@Override
    public <O> PagedResult<JsonObject> search(String key, Expression expression, SearchScope scope, String[] attributes, Sort[] orderBy,
            CouchbaseBatchOperationWraper<O> batchOperationWraper, SearchReturnDataType returnDataType, int start, int count, int pageSize) throws SearchException {
        Instant startTime = OperationDurationUtil.instance().now();

        BucketMapping bucketMapping = connectionProvider.getBucketMappingByKey(key);
        PagedResult<JsonObject> result = searchImpl(bucketMapping, key, expression, scope, attributes, orderBy, batchOperationWraper, returnDataType, start, count, pageSize);

        Duration duration = OperationDurationUtil.instance().duration(startTime);
        OperationDurationUtil.instance().logDebug("Couchbase operation: search, duration: {}, bucket: {}, key: {}, expression: {}, scope: {}, attributes: {}, orderBy: {}, batchOperationWraper: {}, returnDataType: {}, start: {}, count: {}, pageSize: {}", duration, bucketMapping.getBucketName(), key, expression, scope, attributes, orderBy, batchOperationWraper, returnDataType, start, count, pageSize);

        return result;
	}

    private <O> PagedResult<JsonObject> searchImpl(BucketMapping bucketMapping, String key, Expression expression, SearchScope scope, String[] attributes, Sort[] orderBy,
            CouchbaseBatchOperationWraper<O> batchOperationWraper, SearchReturnDataType returnDataType, int start, int count, int pageSize) throws SearchException {
        Bucket bucket = bucketMapping.getBucket();

        BatchOperation<O> ldapBatchOperation = null;
        if (batchOperationWraper != null) {
            ldapBatchOperation = (BatchOperation<O>) batchOperationWraper.getBatchOperation();
        }

        if (LOG.isTraceEnabled()) {
            // Find whole DB search
            if (StringHelper.equalsIgnoreCase(key, "_")) {
                LOG.trace("Search in whole DB tree", new Exception());
            }
        }

        Expression scopeExpression;
        if (scope == null) {
            scopeExpression = null;
        } else if (SearchScope.BASE == scope) {
            scopeExpression = Expression.path("META().id").like(Expression.s(key + "%")).and(Expression.path("META().id").notLike(Expression.s(key + "\\\\_%\\\\_")));
        } else {
            scopeExpression = Expression.path("META().id").like(Expression.s(key + "%"));
        }

        Expression finalExpression = scopeExpression;
        if (expression != null) {
            finalExpression = scopeExpression.and(expression);
        }

        String[] select = attributes;
        if (select == null) {
            select = new String[] { "gluu_doc.*", CouchbaseOperationService.DN };
        } else if ((select.length == 1) && StringHelper.isEmpty(select[0])) {
        	// Compatibility with Couchbase persistence layer when application pass filter new String[] { "" }
            select = new String[] { CouchbaseOperationService.DN };
        } else {
            boolean hasDn = Arrays.asList(select).contains(CouchbaseOperationService.DN);
            if (!hasDn) {
                select = ArrayHelper.arrayMerge(select, new String[] { CouchbaseOperationService.DN });
            }
        }
        GroupByPath selectQuery = Select.select(select).from(Expression.i(bucketMapping.getBucketName())).as("gluu_doc").where(finalExpression);

        LimitPath baseQuery = selectQuery;
        if (orderBy != null) {
            baseQuery = selectQuery.orderBy(orderBy);
        }

        List<N1qlQueryRow> searchResultList = new ArrayList<N1qlQueryRow>();

        if ((SearchReturnDataType.SEARCH == returnDataType) || (SearchReturnDataType.SEARCH_COUNT == returnDataType)) {
	        N1qlQueryResult lastResult;
	        if (pageSize > 0) {
	            boolean collectSearchResult;
	
	            Statement query = null;
	            int currentLimit;
	            try {
	                List<N1qlQueryRow> lastSearchResultList;
	                int resultCount = 0;
	                do {
	                    collectSearchResult = true;
	
	                    currentLimit = pageSize;
	                    if (count > 0) {
	                        currentLimit = Math.min(pageSize, count - resultCount);
	                    }
	
	                    query = baseQuery.limit(currentLimit).offset(start + resultCount);
	                    LOG.debug("Execution query: '" + query + "'");
	                    lastResult = bucket.query(N1qlQuery.simple(query, N1qlParams.build().consistency(ScanConsistency.STATEMENT_PLUS)));
	                    if (!lastResult.finalSuccess()) {
	                        throw new SearchException(String.format("Failed to search entries. Query: '%s'. Error: ", query, lastResult.errors()),
	                                lastResult.info().errorCount());
	                    }
	
	                    lastSearchResultList = lastResult.allRows();
	
	                    if (ldapBatchOperation != null) {
	                        collectSearchResult = ldapBatchOperation.collectSearchResult(lastSearchResultList.size());
	                    }
	                    if (collectSearchResult) {
	                        searchResultList.addAll(lastSearchResultList);
	                    }
	
	                    if (ldapBatchOperation != null) {
	                        List<O> entries = batchOperationWraper.createEntities(lastSearchResultList);
	                        ldapBatchOperation.performAction(entries);
	                    }
	
	                    resultCount += lastSearchResultList.size();
	
	                    if ((count > 0) && (resultCount >= count)) {
	                        break;
	                    }
	                } while ((lastSearchResultList.size() > 0) && (lastSearchResultList.size() > 0));
	            } catch (CouchbaseException ex) {
	                throw new SearchException("Failed to search entries. Query: '" + query + "'", ex);
	            }
	        } else {
	            try {
	                Statement query = baseQuery;
	                if (count > 0) {
	                    query = ((LimitPath) query).limit(count);
	                }
	                if (start > 0) {
	                    query = ((OffsetPath) query).offset(start);
	                }
	
	                LOG.debug("Execution query: '" + query + "'");
	                lastResult = bucket.query(N1qlQuery.simple(query, N1qlParams.build().consistency(ScanConsistency.STATEMENT_PLUS)));
	                if (!lastResult.finalSuccess()) {
	                    throw new SearchException(String.format("Failed to search entries. Query: '%s'. Error: ", baseQuery, lastResult.errors()),
	                            lastResult.info().errorCount());
	                }
	
	                searchResultList.addAll(lastResult.allRows());
	            } catch (CouchbaseException ex) {
	                throw new SearchException("Failed to search entries. Query: '" + baseQuery.toString() + "'", ex);
	            }
	        }
        }

        List<JsonObject> resultRows = new ArrayList<JsonObject>(searchResultList.size());
        for (N1qlQueryRow row : searchResultList) {
            resultRows.add(row.value());
        }

        PagedResult<JsonObject> result = new PagedResult<JsonObject>();
        result.setEntries(resultRows);
        result.setEntriesCount(resultRows.size());
        result.setStart(start);

        if ((SearchReturnDataType.COUNT == returnDataType) || (SearchReturnDataType.SEARCH_COUNT == returnDataType)) {
            GroupByPath selectCountQuery = Select.select("COUNT(*) as TOTAL").from(Expression.i(bucketMapping.getBucketName()))
                    .where(finalExpression);
            try {
                LOG.debug("Calculating count. Execution query: '" + selectCountQuery + "'");
                N1qlQueryResult countResult = bucket.query(N1qlQuery.simple(selectCountQuery, N1qlParams.build().consistency(ScanConsistency.STATEMENT_PLUS)));
                if (!countResult.finalSuccess() || (countResult.info().resultCount() != 1)) {
                    throw new SearchException(
                            String.format("Failed to calculate count entries. Query: '%s'. Error: ", selectCountQuery, countResult.errors()),
                            countResult.info().errorCount());
                }
                result.setTotalEntriesCount(countResult.allRows().get(0).value().getInt("TOTAL"));
            } catch (CouchbaseException ex) {
                throw new SearchException("Failed to calculate count entries. Query: '" + selectCountQuery.toString() + "'", ex);
            }
        }

        return result;
    }

    public String[] createStoragePassword(String[] passwords) {
        if (ArrayHelper.isEmpty(passwords)) {
            return passwords;
        }

        String[] results = new String[passwords.length];
        for (int i = 0; i < passwords.length; i++) {
            results[i] = PasswordEncryptionHelper.createStoragePassword(passwords[i], connectionProvider.getPasswordEncryptionMethod());
        }

        return results;
    }

    @Override
    public boolean isBinaryAttribute(String attribute) {
        return this.connectionProvider.isBinaryAttribute(attribute);
    }

    @Override
    public boolean isCertificateAttribute(String attribute) {
        return this.connectionProvider.isCertificateAttribute(attribute);
    }

    @Override
    public boolean destroy() {
        boolean result = true;

        if (connectionProvider != null) {
            try {
                connectionProvider.destory();
            } catch (Exception ex) {
                LOG.error("Failed to destory provider correctly");
                result = false;
            }
        }

        return result;
    }

    @Override
    public boolean isConnected() {
        return connectionProvider.isConnected();
    }

}
