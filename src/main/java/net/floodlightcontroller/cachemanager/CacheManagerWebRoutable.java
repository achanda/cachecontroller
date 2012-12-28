package net.floodlightcontroller.cachemanager;

import net.floodlightcontroller.restserver.RestletRoutable;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

public class CacheManagerWebRoutable implements RestletRoutable {

	@Override
	public Restlet getRestlet(Context context) {
		Router router = new Router(context);
		router.attach("/{request}/json", CacheManagerResource.class);                // output to proxy
		//router.attach("/save/{request}/json", CacheManagerResource.class);             // input from proxy
		//router.attach("/get/{ip}/json", CacheManagerResource.class);		// query from cache
		return router;
	}

	@Override
	public String basePath() {
		return "/wm/cachemanager";
	}
}