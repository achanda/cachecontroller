package net.floodlightcontroller.cachemanager;

import java.util.Comparator;

class HTTPRequestComparator implements Comparator<Object>{
	public int compare(Object o1, Object o2) {
		HTTPRequest p1 = (HTTPRequest) o1;
		HTTPRequest p2 = (HTTPRequest) o2; 
        return p1.getTimestamp().compareTo(p2.getTimestamp());
    }
}
