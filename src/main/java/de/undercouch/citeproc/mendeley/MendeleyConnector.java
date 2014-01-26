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

package de.undercouch.citeproc.mendeley;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.undercouch.citeproc.csl.CSLItemData;
import de.undercouch.citeproc.remote.AbstractRemoteConnector;

/**
 * Reads documents from the Mendeley REST services. Needs an OAuth API key and
 * secret in order to authenticate. Users of this class should
 * <a href="http://dev.mendeley.com/">register their app</a> to receive such a
 * key and secret.
 * @author Michel Kraemer
 */
public class MendeleyConnector extends AbstractRemoteConnector {
	private static final String OAUTH_ACCESS_TOKEN_URL =
			"http://api.mendeley.com/oauth/access_token/";
	private static final String OAUTH_REQUEST_TOKEN_URL =
			"http://api.mendeley.com/oauth/request_token/";
	private static final String OAUTH_AUTHORIZATION_URL =
			"http://api.mendeley.com/oauth/authorize/?oauth_token=";
	
	/**
	 * The REST end-point used to request a Mendeley user's library
	 */
	private static final String MENDELEY_LIBRARY_ENDPOINT =
			"http://api.mendeley.com/oapi/library/";
	
	/**
	 * The REST end-point used to request a document
	 */
	private static final String MENDELEY_DOCUMENTS_ENDPOINT =
			"http://api.mendeley.com/oapi/library/documents/";
	
	/**
	 * @see AbstractRemoteConnector#AbstractRemoteConnector(String, String)
	 */
	public MendeleyConnector(String consumerKey, String consumerSecret) {
		super(consumerKey, consumerSecret);
	}
	
	@Override
	protected String getOAuthRequestTokenURL() {
		return OAUTH_REQUEST_TOKEN_URL;
	}
	
	@Override
	protected String getOAuthAuthorizationURL() {
		return OAUTH_AUTHORIZATION_URL;
	}
	
	@Override
	protected String getOAuthAccessTokenURL() {
		return OAUTH_ACCESS_TOKEN_URL;
	}
	
	@Override
	public List<String> getItemIDs() throws IOException {
		int totalPages = 1;
		int page = 0;
		List<String> result = new ArrayList<String>();
		
		while (page < totalPages) {
			Map<String, Object> response = performRequest(
					MENDELEY_LIBRARY_ENDPOINT + "?page=" + page, null);
			
			Object otp = response.get("total_pages");
			if (otp instanceof Number) {
				totalPages = ((Number)otp).intValue();
			}
			
			@SuppressWarnings("unchecked")
			List<String> documentIds = (List<String>)response.get("document_ids");
			result.addAll(documentIds);
			
			++page;
		}
		
		return result;
	}
	
	@Override
	public CSLItemData getItem(String documentId) throws IOException {
		Map<String, Object> response = performRequest(
				MENDELEY_DOCUMENTS_ENDPOINT + documentId, null);
		return MendeleyConverter.convert(documentId, response);
	}
	
	@Override
	public Map<String, CSLItemData> getItems(List<String> itemIds) throws IOException {
		Map<String, CSLItemData> result = new LinkedHashMap<String, CSLItemData>(itemIds.size());
		for (String id : itemIds) {
			result.put(id, getItem(id));
		}
		return result;
	}
	
	@Override
	public int getMaxBulkItems() {
		return 1;
	}
}