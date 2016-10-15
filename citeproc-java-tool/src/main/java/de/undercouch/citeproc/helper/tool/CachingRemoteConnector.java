// Copyright 2013 Michel Kraemer
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package de.undercouch.citeproc.helper.tool;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mapdb.DB;
import org.mapdb.DBMaker;

import de.undercouch.citeproc.csl.CSLItemData;
import de.undercouch.citeproc.helper.json.JsonLexer;
import de.undercouch.citeproc.helper.json.JsonParser;
import de.undercouch.citeproc.helper.json.StringJsonBuilderFactory;
import de.undercouch.citeproc.remote.RemoteConnector;
import de.undercouch.citeproc.remote.RemoteConnectorAdapter;

/**
 * A {@link de.undercouch.citeproc.remote.RemoteConnector} that caches
 * items read from the server.
 * @author Michel Kraemer
 */
public class CachingRemoteConnector extends RemoteConnectorAdapter {
	private final DB _db;
	private final Set<String> _itemIds;
	private final Map<String, String> _items;
	
	private boolean _transaction = false;
	
	/**
	 * Creates a connector that caches items read from the server
	 * @param delegate the underlying connector
	 * @param cacheFile a file used to cache items
	 */
	public CachingRemoteConnector(RemoteConnector delegate, File cacheFile) {
		super(delegate);
		
		DB db = null;
		Set<String> itemIds = null;
		Map<String, String> items = null;
		int retry = 2;
		while (retry >= 1) {
			try {
				DBMaker<?> dbMaker = DBMaker.newFileDB(cacheFile);
				dbMaker.closeOnJvmShutdown();
				db = dbMaker.make();
				
				itemIds = db.getTreeSet("itemIds");
				items = db.getHashMap("items");
				
				break;
			} catch (Throwable e) {
				--retry;
				if (retry == 1 && cacheFile.exists()) {
					//unable to open disk cache. remove it and try again.
					try {
						//close db first
						if (db != null) {
							db.close();
							db = null;
						}
					} catch (Throwable t) {
						//ignore
					}
					cacheFile.delete();
					continue;
				}
				
				//disk cache is not available. use in-memory cache
				db = null;
				itemIds = new HashSet<>();
				items = new HashMap<>();
				break;
			}
		}
		
		_db = db;
		_itemIds = itemIds;
		_items = items;
	}
	
	@Override
	public List<String> getItemIDs() throws IOException {
		if (!_itemIds.isEmpty()) {
			return new ArrayList<>(_itemIds);
		}
		List<String> ids = super.getItemIDs();
		try {
			_itemIds.addAll(ids);
			commit();
		} catch (RuntimeException e) {
			rollback();
			throw e;
		}
		return ids;
	}

	@Override
	public CSLItemData getItem(String itemId) throws IOException {
		String item = _items.get(itemId);
		CSLItemData itemData;
		if (item == null) {
			itemData = super.getItem(itemId);
			item = (String)itemData.toJson(new StringJsonBuilderFactory().createJsonBuilder());
			try {
				_items.put(itemId, item);
				commit();
			} catch (RuntimeException e) {
				rollback();
				throw e;
			}
		} else {
			Map<String, Object> m = new JsonParser(
					new JsonLexer(new StringReader(item))).parseObject();
			itemData = CSLItemData.fromJson(m);
		}
		return itemData;
	}
	
	@Override
	public Map<String, CSLItemData> getItems(List<String> itemIds) throws IOException {
		Map<String, CSLItemData> result = new LinkedHashMap<>(itemIds.size());
		List<String> unknownIds = new ArrayList<>();
		
		//load items from cache
		for (String id : itemIds) {
			String item = _items.get(id);
			if (item == null) {
				unknownIds.add(id);
			} else {
				Map<String, Object> m = new JsonParser(
						new JsonLexer(new StringReader(item))).parseObject();
				result.put(id, CSLItemData.fromJson(m));
			}
		}
		
		//load items which are not in the cache yet from remote
		if (!unknownIds.isEmpty()) {
			Map<String, CSLItemData> newItems = super.getItems(unknownIds);
			try {
				for (Map.Entry<String, CSLItemData> e : newItems.entrySet()) {
					String s = (String)e.getValue().toJson(
							new StringJsonBuilderFactory().createJsonBuilder());
					_items.put(e.getKey(), s);
				}
				commit();
			} catch (RuntimeException e) {
				rollback();
				throw e;
			}
			result.putAll(newItems);
		}
		
		return result;
	}
	
	/**
	 * Checks if the cache contains an item with the given ID
	 * @param itemId the item ID
	 * @return true if the cache contains such an item, false otherwise
	 */
	public boolean containsItemId(String itemId) {
		return _items.containsKey(itemId);
	}
	
	/**
	 * @return true if the cache contains a list of item IDs, false
	 * if the cache is empty
	 */
	public boolean hasItemList() {
		return !_itemIds.isEmpty();
	}
	
	/**
	 * Clears the cache
	 */
	public void clear() {
		_itemIds.clear();
		_items.clear();
	}
	
	/**
	 * Starts a transaction on the cache. Cached entries will not
	 * be written to disk until {@link #commitTransaction()} is called.
	 */
	public void beginTransaction() {
		_transaction = true;
	}
	
	/**
	 * Ends a transaction. Does not flush to disk.
	 */
	public void endTransaction() {
		_transaction = false;
	}
	
	/**
	 * Flushes cached entries to disk, but does not end transaction
	 */
	public void commitTransaction() {
		commit(true);
	}
	
	/**
	 * Commit database transaction
	 * @see #commit(boolean)
	 */
	private void commit() {
		commit(false);
	}
	
	/**
	 * Commit database transaction. This method is a NOOP if there is no
	 * database or if {@link #_transaction} is currently true.
	 * @param force true if the database transaction should be committed
	 * regardless of the {@link #_transaction} flag.
	 */
	private void commit(boolean force) {
		if (_db == null) {
			return;
		}
		
		if (_transaction && !force) {
			return;
		}
		
		try {
			_db.commit();
		} catch (Exception e) {
			throw new IllegalStateException("Could not commit transaction", e);
		}
	}
	
	/**
	 * Roll back database transaction. This method is a NOOP if there is no database.
	 */
	private void rollback() {
		if (_db == null) {
			return;
		}
		try {
			_db.rollback();
		} catch (Exception e) {
			throw new IllegalStateException("Could not roll back transaction", e);
		}
	}
}
