/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.instrumentation.io;

import java.util.ArrayList;
import java.util.List;


class StreamCompleteListenerManager {	
	private boolean streamComplete = false;
	private ArrayList<StreamCompleteListener> streamCompleteListeners = new ArrayList<StreamCompleteListener>();
	
	public boolean isComplete() {
		synchronized (this) {
			return streamComplete;
		}
	}
	
	public void addStreamCompleteListener(StreamCompleteListener streamCompleteListener) {
		synchronized (streamCompleteListeners) {
			this.streamCompleteListeners.add(streamCompleteListener);
		}
	}
	
	public void removeStreamCompleteListener(StreamCompleteListener streamCompleteListener) {
		synchronized (streamCompleteListeners) {
			this.streamCompleteListeners.remove(streamCompleteListener);
		}
	}
	
	public void notifyStreamComplete(final StreamCompleteEvent ev) {
		if (!checkComplete()) {
			for (StreamCompleteListener listener : getStreamCompleteListeners()) {
				listener.streamComplete(ev);
			}
		}
	}
	
	public void notifyStreamError(final StreamCompleteEvent ev) {
		if (!checkComplete()) {
			for (StreamCompleteListener listener : getStreamCompleteListeners()) {
				listener.streamError(ev);
			}
		}
	}
	
	private boolean checkComplete() {
		final boolean streamComplete;
		synchronized (this) {
			streamComplete = isComplete();
			if (!streamComplete) this.streamComplete = true;
		}
		return streamComplete;
	}
	
	private List<StreamCompleteListener> getStreamCompleteListeners() {
		final ArrayList<StreamCompleteListener> listeners;
		synchronized (streamCompleteListeners) {
			listeners = new ArrayList<StreamCompleteListener>(streamCompleteListeners);
			streamCompleteListeners.clear();
		}
		return listeners;
	}
}
