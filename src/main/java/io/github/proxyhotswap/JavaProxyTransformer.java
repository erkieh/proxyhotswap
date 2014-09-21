package io.github.proxyhotswap;

import io.github.proxyhotswap.javassist.CannotCompileException;
import io.github.proxyhotswap.javassist.ClassPool;
import io.github.proxyhotswap.javassist.CtClass;
import io.github.proxyhotswap.javassist.CtConstructor;
import io.github.proxyhotswap.javassist.CtMethod;
import io.github.proxyhotswap.javassist.Modifier;
import io.github.proxyhotswap.javassist.NotFoundException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.InvocationTargetException;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import sun.misc.ProxyGenerator;

/**
 * @author Erki Ehtla
 * 
 */
public class JavaProxyTransformer implements ClassFileTransformer {
	private static final String STATIC_INIT_METHOD_NAME = "redefinedJavaProxyExplicitInitMethodAddedByJavaAgent";
	private static final ClassPool classPool = ClassPool.getDefault();
	
	private Instrumentation inst;
	private static Map<Class<?>, TransformationState> transformationStates = new ConcurrentHashMap<>();
	
	public JavaProxyTransformer(Instrumentation inst) {
		this.inst = inst;
	}
	
	@Override
	public byte[] transform(ClassLoader loader, String className, final Class<?> classBeingRedefined,
			ProtectionDomain protectionDomain, final byte[] classfileBuffer) throws IllegalClassFormatException {
		if (!className.startsWith("com/sun/proxy/$Proxy")) {
			return null;
		}
		
		TransformationState transformationState = transformationStates.get(classBeingRedefined);
		if (transformationState == null)
			transformationState = TransformationState.NEW;
		try {
			switch (transformationState) {
				case NEW:
					transformationStates.put(classBeingRedefined, TransformationState.WAITING);
					// We can't to do the transformation in this event, because we can't see the changes in the class
					// definitons. Schedule a new redefinition event.
					scheduleRedefinition(classBeingRedefined, classfileBuffer);
					return null;
				case WAITING:
					return redefineProxy(loader, className, classBeingRedefined, classfileBuffer);
				case REDEFINED:
					return initNewProxy(classBeingRedefined);
				default:
					throw new RuntimeException("Unhandeled TransformationState!");
			}
		} catch (Exception e) {
			transformationStates.remove(classBeingRedefined);
			throw new RuntimeException(e);
		}
	}
	
	private byte[] redefineProxy(ClassLoader loader, String className, final Class<?> classBeingRedefined,
			final byte[] classfileBuffer) throws IOException, CannotCompileException, NotFoundException {
		byte[] result = classfileBuffer;
		result = generateNewProxyClass(loader, className, classBeingRedefined);
		transformationStates.put(classBeingRedefined, TransformationState.REDEFINED);
		// We can't call our created static init method in this event, because we can't see the changes in
		// the class definitons. Schedule a new redefinition event where we won't change anything, just call
		// our init method
		scheduleRedefinition(classBeingRedefined, result);
		return result;
	}
	
	private byte[] initNewProxy(final Class<?> classBeingRedefined) throws IllegalAccessException,
			IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		classBeingRedefined.getMethod(STATIC_INIT_METHOD_NAME).invoke(null);
		transformationStates.remove(classBeingRedefined);
		// System.out.println("redefined " + classBeingRedefined.getName());
		return null;
	}
	
	private byte[] generateNewProxyClass(ClassLoader loader, String className, final Class<?> classBeingRedefined)
			throws IOException, RuntimeException, CannotCompileException, NotFoundException {
		byte[] generateProxyClass = ProxyGenerator.generateProxyClass(className, classBeingRedefined.getInterfaces());
		CtClass cc = classPool.makeClass(new ByteArrayInputStream(generateProxyClass));
		CtConstructor ci = cc.getClassInitializer();
		CtMethod method = ci.toMethod(STATIC_INIT_METHOD_NAME, cc);
		method.setModifiers(Modifier.PUBLIC | Modifier.STATIC);
		cc.addMethod(method);
		byte[] result = cc.toBytecode();
		cc.detach();
		return result;
	}
	
	private void scheduleRedefinition(final Class<?> classBeingRedefined, final byte[] classfileBuffer) {
		new Thread() {
			@Override
			public void run() {
				try {
					inst.redefineClasses(new ClassDefinition(classBeingRedefined, classfileBuffer));
				} catch (ClassNotFoundException | UnmodifiableClassException e) {
					transformationStates.remove(classBeingRedefined);
					throw new RuntimeException(e);
				}
			}
		}.start();
	}
}
