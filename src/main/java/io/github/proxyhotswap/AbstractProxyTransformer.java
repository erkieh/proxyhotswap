package io.github.proxyhotswap;

import io.github.proxyhotswap.javassist.ClassPool;
import io.github.proxyhotswap.javassist.CtClass;
import io.github.proxyhotswap.javassist.CtField;
import io.github.proxyhotswap.javassist.CtMethod;
import io.github.proxyhotswap.javassist.Modifier;

import java.io.ByteArrayInputStream;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Erki Ehtla
 * 
 */
public abstract class AbstractProxyTransformer implements ClassFileTransformer {
	protected static final String INIT_FIELD_PREFIX = "initCalled";
	protected static final ClassPool classPool = ClassPool.getDefault();
	
	protected Instrumentation inst;
	protected Map<Class<?>, TransformationState> transformationStates;
	protected Map<Class<?>, Long> transStart = new ConcurrentHashMap<Class<?>, Long>();
	
	public AbstractProxyTransformer(Instrumentation inst, Map<Class<?>, TransformationState> transformationStates) {
		this.inst = inst;
		this.transformationStates = transformationStates;
	}
	
	@Override
	public byte[] transform(ClassLoader loader, String className, final Class<?> classBeingRedefined,
			ProtectionDomain protectionDomain, final byte[] classfileBuffer) throws IllegalClassFormatException {
		try {
			if (classBeingRedefined == null || !isProxy(className, classBeingRedefined, classfileBuffer)) {
				return null;
			}
			return transformRedefine(loader, className, classBeingRedefined, protectionDomain, classfileBuffer);
		} catch (Exception e) {
			removeClassState(classBeingRedefined);
			TranformationUtils.logError(e);
			throw new RuntimeException(e);
		}
	}
	
	private boolean isTransformingNeeded(Class<?> classBeingRedefined) {
		Class<?> superclass = classBeingRedefined.getSuperclass();
		if (superclass != null && ClassfileBufferSigantureTransformer.hasClassChanged(superclass))
			return true;
		Class<?>[] interfaces = classBeingRedefined.getInterfaces();
		for (Class<?> clazz : interfaces) {
			if (ClassfileBufferSigantureTransformer.hasClassChanged(clazz))
				return true;
		}
		return false;
	}
	
	protected abstract boolean isProxy(String className, Class<?> classBeingRedefined, byte[] classfileBuffer)
			throws Exception;
	
	protected byte[] transformRedefine(ClassLoader loader, String className, Class<?> classBeingRedefined,
			ProtectionDomain protectionDomain, byte[] classfileBuffer) throws Exception {
		switch (getTransformationstate(classBeingRedefined)) {
			case NEW:
				if (!isTransformingNeeded(classBeingRedefined)) {
					return null;
				}
				transStart.put(classBeingRedefined, System.currentTimeMillis());
				setClassAsWaiting(classBeingRedefined);
				// We can't do the transformation in this event, because we can't see the changes in the class
				// definitons. Schedule a new redefinition event.
				scheduleRedefinition(classBeingRedefined, classfileBuffer);
				return null;
			case WAITING:
				removeClassState(classBeingRedefined);
				System.out.println(System.currentTimeMillis() - transStart.get(classBeingRedefined));
				return generateNewProxyClass(loader, className, classBeingRedefined);
			default:
				throw new RuntimeException("Unhandeled TransformationState!");
		}
	}
	
	protected byte[] generateNewProxyClass(ClassLoader loader, String className, Class<?> classBeingRedefined)
			throws Exception {
		
		byte[] newByteCode = getNewByteCode(loader, className, classBeingRedefined);
		
		CtClass cc = classPool.makeClass(new ByteArrayInputStream(newByteCode));
		try {
			String random = generateRandomString();
			String initFieldName = INIT_FIELD_PREFIX + random;
			addStaticInitStateField(cc, initFieldName);
			
			String method = getInitCall(cc, random);
			
			addInitCallToMethods(cc, initFieldName, method);
			
			return cc.toBytecode();
		} finally {
			TranformationUtils.detachCtClass(cc);
		}
	}
	
	protected abstract String getInitCall(CtClass cc, String random) throws Exception;
	
	protected abstract byte[] getNewByteCode(ClassLoader loader, String className, Class<?> classBeingRedefined)
			throws Exception;
	
	protected TransformationState getTransformationstate(final Class<?> classBeingRedefined) {
		TransformationState transformationState = transformationStates.get(classBeingRedefined);
		if (transformationState == null)
			transformationState = TransformationState.NEW;
		return transformationState;
	}
	
	protected String generateRandomString() {
		return UUID.randomUUID().toString().replace("-", "");
	}
	
	protected void addInitCallToMethods(CtClass cc, String clinitFieldName, String initCall) throws Exception {
		CtMethod[] methods = cc.getDeclaredMethods();
		for (CtMethod ctMethod : methods) {
			if (!ctMethod.isEmpty() && !Modifier.isStatic(ctMethod.getModifiers())) {
				ctMethod.insertBefore("if(!" + clinitFieldName + "){" + initCall + "}");
			}
		}
	}
	
	protected void addStaticInitStateField(CtClass cc, String clinitFieldName) throws Exception {
		CtField f = new CtField(CtClass.booleanType, clinitFieldName, cc);
		f.setModifiers(Modifier.PRIVATE | Modifier.STATIC);
		// init value "true" will be inside clinit, so the field wont actually be initialized to this
		cc.addField(f, "true");
	}
	
	protected void scheduleRedefinition(final Class<?> classBeingRedefined, final byte[] classfileBuffer) {
		new Thread() {
			@Override
			public void run() {
				try {
					inst.redefineClasses(new ClassDefinition(classBeingRedefined, classfileBuffer));
				} catch (ClassNotFoundException | UnmodifiableClassException e) {
					removeClassState(classBeingRedefined);
					throw new RuntimeException(e);
				}
			}
		}.start();
	}
	
	protected TransformationState setClassAsWaiting(Class<?> classBeingRedefined) {
		return transformationStates.put(classBeingRedefined, TransformationState.WAITING);
	}
	
	protected TransformationState removeClassState(Class<?> classBeingRedefined) {
		return transformationStates.remove(classBeingRedefined);
	}
	
}