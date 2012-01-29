package play.modules.ebean;

import java.util.List;
import javax.sql.DataSource;

import play.Logger;
import play.Play;
import play.PlayPlugin;
import play.classloading.ApplicationClasses.ApplicationClass;
import play.db.DB;
import play.db.jpa.JPAPlugin;

import com.avaje.ebean.EbeanServer;
import com.avaje.ebean.EbeanServerFactory;
import com.avaje.ebean.config.ServerConfig;

public class EbeanPlugin extends PlayPlugin {

	public static EbeanServer defaultServer;

	@SuppressWarnings("unchecked")
	public static EbeanServer createServer(String name, DataSource dataSource) {
		Logger.info("Try to create Ebean server: %s", name);
		EbeanServer result = null;
		ServerConfig cfg = new ServerConfig();
		cfg.loadFromProperties();
		cfg.setName(name);
		cfg.setClasses((List) Play.classloader.getAllClasses());
		cfg.setDataSource(new EbeanDataSourceWrapper(dataSource));
		cfg.setRegister("default".equals(name));
		cfg.setDefaultServer("default".equals(name));
		cfg.add(new EbeanModelAdapter());
		try {
			result = EbeanServerFactory.create(cfg);
			Logger.info("Ebean server created.");
		} catch (Throwable t) {
			Logger.error(t, "Failed to create ebean server");
		}
		return result;
	}

	public EbeanPlugin() {
		super();
	}

	@Override
	public void onLoad() {
		// TODO: Hack! We have to change this once built-in plugins may be
		// deactivated
		for (PlayPlugin plugin : Play.pluginCollection.getEnabledPlugins()) {
			if (plugin instanceof JPAPlugin) {
				Play.pluginCollection.disablePlugin(plugin);
				break;
			}
		}
	}

	@Override
	public void onApplicationStart() {
		if (DB.datasource != null)
			defaultServer = createServer("default", DB.datasource);
	}

	@Override
	public void beforeInvocation() {
		EbeanContext.set(defaultServer);
	}

	@Override
	public void invocationFinally() {
		EbeanContext.set(null);
	}

	@Override
	public void enhance(ApplicationClass applicationClass) throws Exception {
		EbeanEnhancer.class.newInstance().enhanceThisClass(applicationClass);
	}

}
