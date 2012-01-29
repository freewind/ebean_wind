package play.modules.ebean;

import java.util.HashMap;
import java.util.Map;

import com.avaje.ebean.EbeanServer;

public class EbeanContext {

	public static ThreadLocal<EbeanContext> local = new ThreadLocal<EbeanContext>();

	private EbeanServer server;

	private Map<String, Object> properties = new HashMap<String, Object>();

	private EbeanContext(EbeanServer server, Object... props) {
		this.server = server;
		for (int i = 0; i < props.length - 1; i += 2)
			properties.put(props[i].toString(), props[i + 1]);
	}

	public static EbeanContext set(EbeanServer server, Object... properties) {
		EbeanContext context = new EbeanContext(server, properties);
		local.set(context);
		return context;
	}

	public static EbeanContext get() {
		EbeanContext context = local.get();
		if (context == null) {
			throw new IllegalStateException("The Ebean context is not initialized.");
		}
		return context;
	}

	public static EbeanServer server() {
		return get().server;
	}

	public static Object property(String propertyName) {
		return get().properties.get(propertyName);
	}

}
