package play.modules.ebean;

import java.util.Map;
import javax.sql.DataSource;

import play.Play;
import play.PlayPlugin;
import play.classloading.ApplicationClasses.ApplicationClass;
import play.db.DB;
import play.db.jpa.JPAPlugin;
import play.mvc.Http;
import play.mvc.Http.Request;

import com.avaje.ebean.EbeanServer;

public class EbeanPlugin extends PlayPlugin {

	private static EbeanServer defaultServer;

	private static ThreadLocal<EbeanServer> local = new ThreadLocal<EbeanServer>();

	@Override
	public void onConfigurationRead() {
		PlayPlugin jpaPlugin = Play.pluginCollection.getPluginInstance(JPAPlugin.class);
		if (jpaPlugin != null && Play.pluginCollection.isEnabled(jpaPlugin)) {
			Play.pluginCollection.disablePlugin(jpaPlugin);
		}
	}

	@Override
	public void onApplicationStart() {
		if (DB.datasource != null) {
			defaultServer = Helper.createServer("default", DB.datasource);
		}
	}

	@Override
	public void beforeInvocation() {
		EbeanServer server = defaultServer;

		Request currentRequest = Http.Request.current();
		if (currentRequest != null) {
			// Hook to introduce more data sources
			Map<String, DataSource> ds = (Map<String, DataSource>) currentRequest.args.get("dataSources");
			if (ds != null && ds.size() > 0) {
				// Currently we support single data source
				Map.Entry<String, DataSource> firstEntry = ds.entrySet().iterator().next();
				server = Helper.getOrCreateServer(firstEntry.getKey(), firstEntry.getValue());
			}
		}
		resetLocalServer(server);
	}

	@Override
	public void afterInvocation() {
		EbeanServer server = getLocalServer();
		if (server != null && server.currentTransaction() != null) {
			server.commitTransaction();
		}
	}

	@Override
	public void invocationFinally() {
		resetLocalServer(null);
	}

	@Override
	public void enhance(ApplicationClass applicationClass) throws Exception {
		new EbeanEnhancer().enhanceThisClass(applicationClass);
	}

	private static EbeanServer getLocalServer() {
		return local.get();
	}

	private static void resetLocalServer(EbeanServer server) {
		EbeanServer current = getLocalServer();
		if (current != null && current.currentTransaction() != null) {
			current.endTransaction();
		}
		local.set(server);
	}

}
