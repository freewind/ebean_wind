package play.modules.ebean;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import javax.persistence.PostLoad;
import javax.persistence.PostPersist;
import javax.persistence.PostRemove;
import javax.persistence.PostUpdate;
import javax.persistence.PrePersist;
import javax.persistence.PreRemove;
import javax.persistence.PreUpdate;

import play.utils.Java;

import com.avaje.ebean.event.BeanPersistAdapter;
import com.avaje.ebean.event.BeanPersistRequest;

public class EbeanModelAdapter extends BeanPersistAdapter {

	@Override
	public boolean isRegisterFor(Class<?> cls) {
		return EbeanSupport.class.isAssignableFrom(cls);
	}

	@Override
	public void postLoad(Object bean, Set<String> includedProperties) {
		runMethodsWith(bean, PostLoad.class);
	}

	@Override
	public boolean preInsert(BeanPersistRequest<?> request) {
		runMethodsWith(request.getBean(), PrePersist.class);
		return true;
	}

	@Override
	public boolean preUpdate(BeanPersistRequest<?> request) {
		runMethodsWith(request.getBean(), PreUpdate.class);
		return true;
	}

	@Override
	public boolean preDelete(BeanPersistRequest<?> request) {
		runMethodsWith(request.getBean(), PreRemove.class);
		return true;
	}

	@Override
	public void postInsert(BeanPersistRequest<?> request) {
		runMethodsWith(request.getBean(), PostPersist.class);
	}

	@Override
	public void postUpdate(BeanPersistRequest<?> request) {
		runMethodsWith(request.getBean(), PostUpdate.class);
	}

	@Override
	public void postDelete(BeanPersistRequest<?> request) {
		runMethodsWith(request.getBean(), PostRemove.class);
	}

	private void runMethodsWith(Object model, Class<? extends Annotation> annotationCls) {
		List<Method> methods = Java.findAllAnnotatedMethods(model.getClass(), annotationCls);
		for (Method method : methods) {
			try {
				method.setAccessible(true);
				method.invoke(model);
			} catch (Exception e) {
				throw new EbeanException("Exception occurs while invoking method: " + model.getClass().getSimpleName()
						+ "#" + method.getName(), e);
			}
		}
	}

}
