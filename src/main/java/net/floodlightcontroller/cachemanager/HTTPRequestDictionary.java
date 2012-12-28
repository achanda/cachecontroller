package net.floodlightcontroller.cachemanager;

import java.util.ArrayList;
import java.util.Collection;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

;

public class HTTPRequestDictionary {
	private Multimap<String, HTTPRequest> requests;

	public HTTPRequestDictionary() {
		requests = ArrayListMultimap.create();
	}

	public int size() {
		return requests.size();
	}

	public void add(String key, HTTPRequest value) {
		requests.put(key, value);
	}

	public Collection<HTTPRequest> get(String key) {
		return requests.get(key);
	}

	public String toString() {
		StringBuilder result = new StringBuilder();
		String NEW_LINE = System.getProperty("line.separator");
		for (String key : requests.keySet()) {
			ArrayList<HTTPRequest> temp = (ArrayList<HTTPRequest>) requests
					.get(key);
			result.append("Key: " + key);
			result.append(NEW_LINE);
			result.append(temp.toString());
		}
		return result.toString();
	}
}