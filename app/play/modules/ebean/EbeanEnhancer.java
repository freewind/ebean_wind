package play.modules.ebean;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.instrument.ClassFileTransformer;
import java.util.Map;

import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewConstructor;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.annotation.MemberValue;
import javax.persistence.PrePersist;

import org.apache.commons.lang.ArrayUtils;

import play.Logger;
import play.Play;
import play.classloading.ApplicationClasses.ApplicationClass;
import play.classloading.enhancers.Enhancer;
import play.exceptions.UnexpectedException;

import com.avaje.ebean.enhance.agent.ClassBytesReader;
import com.avaje.ebean.enhance.agent.InputStreamTransform;

public class EbeanEnhancer extends Enhancer {

	static ClassFileTransformer transformer = new PlayAwareTransformer(new PlayClassBytesReader(),
			"transientInternalFields=true;debug=0");

	public void enhanceThisClass(ApplicationClass applicationClass) throws Exception {
		// Ebean transformations
		byte[] buffer = transformer.transform(Play.classloader, applicationClass.name, null, null,
				applicationClass.enhancedByteCode);
		if (buffer != null)
			applicationClass.enhancedByteCode = buffer;

		CtClass ctClass = makeClass(applicationClass);

		if (!ctClass.subtypeOf(classPool.get("play.modules.ebean.EbeanSupport"))) {
			// We don't want play style enhancements to happen or classes other
			// than subclasses of EbeanSupport
			return;
		}

		// Enhance only JPA entities
		if (!hasAnnotation(ctClass, "javax.persistence.Entity")) {
			return;
		}

		String entityName = ctClass.getName();

		// Add a default constructor if needed
		try {
			boolean hasDefaultConstructor = false;
			for (CtConstructor constructor : ctClass.getConstructors()) {
				if (constructor.getParameterTypes().length == 0) {
					hasDefaultConstructor = true;
					break;
				}
			}
			if (!hasDefaultConstructor && !ctClass.isInterface()) {
				CtConstructor defaultConstructor = CtNewConstructor.make(
						"private " + ctClass.getSimpleName() + "() {}", ctClass);
				ctClass.addConstructor(defaultConstructor);
			}
		} catch (Exception e) {
			Logger.error(e, "Error in EbeanEnhancer");
			throw new UnexpectedException("Error in EbeanEnhancer", e);
		}

		// @UUID
		CtField id = getUuidField(ctClass);
		if (id != null) {
			String setUUID = "private void __setUUID__() {"
					+ " if(this." + id.getName() + "==null) {"
					+ "    this." + id.getName() + " = java.util.UUID.randomUUID().toString();"
					+ " }}";
			System.out.println(setUUID);
			CtMethod setUUIDMethod = CtMethod.make(setUUID, ctClass);
			createAnnotation(setUUIDMethod, PrePersist.class);
			ctClass.addMethod(setUUIDMethod);
		}

//		// query
//		String query = "public static play.modules.ebean.EbeanQuery query() {"
//				+ " return new play.modules.ebean.EbeanQuery( ebean().createQuery(" + entityName
//				+ ".class)); }";
//		ctClass.addMethod(CtMethod.make(query, ctClass));

		// findById
		String findById = "public static play.modules.ebean.EbeanSupport findById(Object id) { return ("
				+ entityName + ") ebean().find(" + entityName + ".class, id); }";

		ctClass.addMethod(CtMethod.make(findById, ctClass));

		// get
		String get = "public static play.modules.ebean.EbeanSupport get(Object id) {"
				+ " return (" + entityName + ")ebean().find(" + entityName + ".class, id); }";
		ctClass.addMethod(CtMethod.make(get, ctClass));

		// Done.
		applicationClass.enhancedByteCode = ctClass.toBytecode();
		ctClass.defrost();

	}

	private void createAnnotation(CtMethod ctMethod, Class<PrePersist> annotationType) {
		AnnotationsAttribute annotationsAttribute = getAnnotations(ctMethod);
		javassist.bytecode.annotation.Annotation annotation = new javassist.bytecode.annotation.Annotation(
				annotationType.getName(), annotationsAttribute.getConstPool());
		annotationsAttribute.addAnnotation(annotation);
	}

	private CtField getUuidField(CtClass ctClass) {
		CtField[] fields = ctClass.getFields();
		for (CtField field : fields) {
			for (Object anno : field.getAvailableAnnotations()) {
				if (anno.toString().equals("@play.modules.ebean.UUID")) {
					return field;
				}
			}
		}
		return null;
	}

	static class PlayClassBytesReader implements ClassBytesReader {

		public byte[] getClassBytes(String className, ClassLoader classLoader) {
			ApplicationClass ac = Play.classes.getApplicationClass(className.replace("/", "."));
			return ac != null ? ac.enhancedByteCode : getBytesFromClassPath(className);
		}

		private byte[] getBytesFromClassPath(String className) {
			String resource = className + ".class";
			byte[] classBytes = null;
			InputStream is = Play.classloader.getResourceAsStream(resource);
			try {
				classBytes = InputStreamTransform.readBytes(is);
			} catch (IOException e) {
				throw new RuntimeException("IOException reading bytes for " + className, e);
			} finally {
				if (is != null) {
					try {
						is.close();
					} catch (IOException e) {
						throw new RuntimeException("Error closing InputStream for " + className, e);
					}
				}
			}
			return classBytes;
		}

	}

}
