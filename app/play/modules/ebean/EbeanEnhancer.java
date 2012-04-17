package play.modules.ebean;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.util.HashSet;
import java.util.Set;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.bytecode.AnnotationsAttribute;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.PrePersist;

import org.apache.commons.io.IOUtils;

import play.Logger;
import play.Play;
import play.classloading.ApplicationClasses.ApplicationClass;
import play.classloading.enhancers.Enhancer;

import com.avaje.ebean.enhance.agent.ClassBytesReader;
import com.avaje.ebean.enhance.agent.InputStreamTransform;
import com.avaje.ebean.enhance.agent.Transformer;
import com.avaje.ebean.enhance.asm.ClassReader;
import com.avaje.ebean.enhance.asm.ClassWriter;

public class EbeanEnhancer extends Enhancer {

	static ClassFileTransformer transformer = new PlayAwareTransformer(new PlayClassBytesReader(), "transientInternalFields=true;debug=0");

	public void enhanceThisClass(ApplicationClass applicationClass) throws Exception {
		CtClass ctClass = makeClass(applicationClass);
		if (!ctClass.subtypeOf(classPool.get(Model.class.getName()))
				|| !hasAnnotation(ctClass, Entity.class.getName()) && !hasAnnotation(ctClass, Embeddable.class.getName())) {
			return;
		}

		// Ebean transformations
		byte[] buffer = transformer.transform(Play.classloader, applicationClass.name, null, null, applicationClass.enhancedByteCode);
		if (buffer != null) {
			applicationClass.enhancedByteCode = buffer;
		}

		ctClass = makeClass(applicationClass);

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

		// Done.
		applicationClass.enhancedByteCode = ctClass.toBytecode();
		ctClass.defrost();
		Logger.debug("EBEAN: Class '%s' has been enhanced", ctClass.getName());
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

	private void createAnnotation(CtMethod ctMethod, Class<PrePersist> annotationType) {
		AnnotationsAttribute annotationsAttribute = getAnnotations(ctMethod);
		javassist.bytecode.annotation.Annotation annotation = new javassist.bytecode.annotation.Annotation(
			annotationType.getName(), annotationsAttribute.getConstPool());
		annotationsAttribute.addAnnotation(annotation);
	}

	private static class PlayClassBytesReader implements ClassBytesReader {

		public byte[] getClassBytes(String className, ClassLoader classLoader) {
			ApplicationClass ac = Play.classes.getApplicationClass(className.replace("/", "."));
			return ac != null ? ac.enhancedByteCode : getBytesFromClassPath(className);
		}

		private byte[] getBytesFromClassPath(String className) {
			String resource = className + ".class";
			InputStream is = Play.classloader.getResourceAsStream(resource);
			if (is == null) {
				throw new RuntimeException("Class file not found: " + resource);
			}

			try {
				return InputStreamTransform.readBytes(is);
			} catch (IOException e) {
				throw new RuntimeException("IOException reading bytes for " + className, e);
			} finally {
				IOUtils.closeQuietly(is);
			}
		}

	}

	static class PlayAwareTransformer extends Transformer {

		public PlayAwareTransformer(ClassBytesReader r, String agentArgs) {
			super(r, agentArgs);
		}

		@Override
		protected ClassWriter createClassWriter() {
			return new PlayAwareClassWriter();

		}
	}

	static class PlayAwareClassWriter extends ClassWriter {

		public PlayAwareClassWriter() {
			super(COMPUTE_FRAMES + COMPUTE_MAXS);
		}

		@Override
		protected String getCommonSuperClass(String type1, String type2) {
			try {
				// First put all super classes of type1, including type1 (starting with type2 is equivalent)
				Set<String> superTypes1 = new HashSet<String>();
				String s = type1;
				superTypes1.add(s);
				while (!"java/lang/Object".equals(s)) {
					s = getSuperType(s);
					superTypes1.add(s);
				}
				// Then check type2 and each of it's super classes in sequence if it is in the set
				// First match is the common superclass.
				s = type2;
				while (true) {
					if (superTypes1.contains(s)) return s;
					s = getSuperType(s);
				}
			} catch (Exception e) {
				throw new RuntimeException(e.toString());
			}
		}

		private String getSuperType(String type) throws ClassNotFoundException {
			ApplicationClass ac = Play.classes.getApplicationClass(type.replace('/', '.'));
			try {
				return ac != null ? new ClassReader(ac.enhancedByteCode).getSuperName() : new ClassReader(type).getSuperName();
			} catch (IOException e) {
				throw new ClassNotFoundException(type);
			}
		}

	}

}
