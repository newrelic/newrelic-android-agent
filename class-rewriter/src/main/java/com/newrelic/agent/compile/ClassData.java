package com.newrelic.agent.compile;

public class ClassData {
	private final byte[] mainClassBytes;
	private final String shimClassName;
	private final byte[] shimClassBytes;
	private final boolean modified;
	
	public ClassData(final byte[] mainClassBytes, final String shimClassName, final byte[] shimClassBytes, final boolean modified) {
		this.mainClassBytes = mainClassBytes;
		this.shimClassName = shimClassName;
		this.shimClassBytes = shimClassBytes;
		this.modified = modified;
	}
	
	public ClassData(final byte[] mainClassBytes, final boolean modified) {
		this(mainClassBytes, null, null, modified);
	}
	
	public byte[] getMainClassBytes() {
		return mainClassBytes;
	}
	
	public String getShimClassName() {
		return shimClassName;
	}
	
	public byte[] getShimClassBytes() {
		return shimClassBytes;
	}
	
	public boolean isShimPresent() {
		return shimClassName != null;
	}
	
	public boolean isModified() {
		return modified;
	}
}
