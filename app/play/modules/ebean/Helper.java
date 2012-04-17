package play.modules.ebean;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

import play.Logger;
import play.Play;

import com.avaje.ebean.EbeanServer;
import com.avaje.ebean.EbeanServerFactory;
import com.avaje.ebean.config.ServerConfig;

class Helper {

	private static Map<String, EbeanServer> SERVERS = new HashMap<String, EbeanServer>();

	@SuppressWarnings({ "unchecked", "rawtypes" })
	static EbeanServer createServer(String name, DataSource dataSource) {
		ServerConfig cfg = new ServerConfig();
		cfg.loadFromProperties();
		cfg.setName(name);
		cfg.setClasses((List) Play.classloader.getAllClasses());
		cfg.setDataSource(new WrappingDatasource(dataSource));
		cfg.setRegister("default".equals(name));
		cfg.setDefaultServer("default".equals(name));
		try {
			return EbeanServerFactory.create(cfg);
		} catch (Throwable t) {
			throw Helper.logWrap(t, "Failed to create ebean server (" + t.getMessage() + ")");
		}
	}

	static EbeanServer getOrCreateServer(String name, DataSource ds) {
		EbeanServer server = null;
		if (name != null) {
			synchronized (SERVERS) {
				server = SERVERS.get(name);
				if (server == null) {
					server = createServer(name, ds);
					SERVERS.put(name, server);
				}
			}
		}
		return server;
	}

	static RuntimeException logWrap(Throwable e) {
		Logger.error(e, e.toString());
		if (e instanceof RuntimeException) {
			return (RuntimeException) e;
		} else {
			return new RuntimeException(e);
		}
	}

	static RuntimeException logWrap(String message) {
		Logger.error(message);
		return new RuntimeException(message);
	}

	static RuntimeException logWrap(Throwable e, String message) {
		Logger.error(e, message);
		if (e instanceof RuntimeException) {
			return (RuntimeException) e;
		} else {
			return new RuntimeException(message, e);
		}
	}

}

/**
 * <code>DataSource</code> wrapper to ensure that every retrieved connection has auto-commit disabled.
 */
class WrappingDatasource implements DataSource {

	private final DataSource wrapped;

	public WrappingDatasource(DataSource wrapped) {
		this.wrapped = wrapped;
	}

	public Connection getConnection() throws SQLException {
		return wrap(wrapped.getConnection());
	}

	public Connection getConnection(String username, String password) throws SQLException {
		return wrap(wrapped.getConnection(username, password));
	}

	public int getLoginTimeout() throws SQLException {
		return wrapped.getLoginTimeout();
	}

	public PrintWriter getLogWriter() throws SQLException {
		return wrapped.getLogWriter();
	}

	public void setLoginTimeout(int seconds) throws SQLException {
		wrapped.setLoginTimeout(seconds);
	}

	public void setLogWriter(PrintWriter out) throws SQLException {
		wrapped.setLogWriter(out);
	}

	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		return wrapped.isWrapperFor(iface);
	}

	public <T> T unwrap(Class<T> iface) throws SQLException {
		return wrapped.unwrap(iface);
	}

	public Logger getParentLogger() {
		return null;
	}

	private static Connection wrap(Connection connection) throws SQLException {
		connection.setAutoCommit(false);
		return connection;
	}
}
