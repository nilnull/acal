package com.morphoss.acal.database.resourcesmanager.requesttypes;

import com.morphoss.acal.database.resourcesmanager.ResourceResponse;

public abstract class BlockingResourceRequestWithResponse<E> extends ResourceRequestWithResponse<E> implements BlockingResourceRequest {

	private boolean processed = false;
	private ResourceResponse<E> response;
	
	public BlockingResourceRequestWithResponse() {
		super(null);
	}
	
	protected void postResponse(ResourceResponse<E> r) {
		this.response = r;
		this.processed = true;
	}
	
	@Override
	public boolean isProcessed() { return this.processed; }
	
	public ResourceResponse<E> getResponse() {
		return this.response;
	}
		
}
