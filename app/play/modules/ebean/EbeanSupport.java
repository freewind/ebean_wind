package play.modules.ebean;

import javax.persistence.MappedSuperclass;

import play.PlayPlugin;

import com.avaje.ebean.EbeanServer;
import com.avaje.ebean.Query;

@SuppressWarnings("unchecked")
@MappedSuperclass
public class EbeanSupport {

	protected static EbeanServer ebean() {
		return EbeanContext.server();
	}

	public <T extends EbeanSupport> T save() {
		ebean().save(this);
		return (T) this;
	}

	public <T extends EbeanSupport> T update() {
		ebean().update(this);
		return (T) this;
	}

	public <T extends EbeanSupport> T refresh() {
		ebean().refresh(this);
		return (T) this;
	}

	public <T extends EbeanSupport> T delete() {
		ebean().delete(this);
		PlayPlugin.postEvent("JPASupport.objectDeleted", this);
		return (T) this;
	}

	public static <T extends EbeanSupport> T findById(Object id) {
		throw enhancementError();
	}

	public static <T extends EbeanSupport> T get(Object id) {
		throw enhancementError();
	}

	public static <T> Query<T> Query(Class<T> clazz) {
		return ebean().createQuery(clazz);
	}

	private static UnsupportedOperationException enhancementError() {
		return new UnsupportedOperationException(
				"Please annotate your JPA model with @javax.persistence.Entity annotation.");
	}
}
