package io.github.proxyhotswap.java;

import io.github.proxyhotswap.ClassfileBufferSigantureTransformer;
import io.github.proxyhotswap.TransformationUtils;
import io.github.proxyhotswap.javassist.ClassPool;
import io.github.proxyhotswap.javassist.CtClass;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Erki Ehtla
 * 
 */
public class JavassistSimpleJavaProxyTransformer implements ClassFileTransformer {
	protected static final String INIT_FIELD_PREFIX = "initCalled";
	protected static final ClassPool classPool = TransformationUtils.getClassPool();
	
	protected Map<Class<?>, Long> transStart = new ConcurrentHashMap<Class<?>, Long>();
	
	@Override
	public byte[] transform(ClassLoader loader, String className, final Class<?> classBeingRedefined,
			ProtectionDomain protectionDomain, final byte[] classfileBuffer) throws IllegalClassFormatException {
		if (classBeingRedefined == null)
			return null;
		try {
			if (!isProxy(className, classBeingRedefined, classfileBuffer)
					|| !ClassfileBufferSigantureTransformer.hasSuperClassOrInterfaceChanged(classBeingRedefined)) {
				return null;
			}
			String javaClassName = TransformationUtils.getClassName(className);
			CtClass cc = classPool.get(javaClassName);
			return CtClassJavaProxyGenerator.generateProxyClass(javaClassName, cc.getInterfaces());
		} catch (Exception e) {
			TransformationUtils.logError(e);
			return null;
		}
	}
	
	protected boolean isProxy(String className, Class<?> classBeingRedefined, byte[] classfileBuffer) {
		return className.startsWith("com/sun/proxy/$Proxy");
	}
}