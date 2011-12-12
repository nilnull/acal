package com.morphoss.acal.database.resourcesmanager;

/**
 * Represents the response to a CacheRequest. CacheRequests do not necessarily have to generate a response. See @CacheRequest for more info
 * 
 * @author Chris Noldus
 *
 * @param <E>
 */
public interface ResourceResponse<E> {

	/**
	 * Returns the response object generated by a CacheRequest.
	 * @return
	 */
	public E result(); 
}
