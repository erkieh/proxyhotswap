package io.github.proxyhotswap;

import io.github.proxyhotswap.javassist.ClassPool;
import io.github.proxyhotswap.javassist.CtClass;
import io.github.proxyhotswap.javassist.CtMethod;
import io.github.proxyhotswap.javassist.Modifier;
import io.github.proxyhotswap.javassist.NotFoundException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Erki Ehtla
 * 
 */
public class ClassfileBufferSigantureTransformer implements ClassFileTransformer {
	
	private static Map<String, String> classSignatures = new ConcurrentHashMap<>();
	protected static final ClassPool classPool = ClassPool.getDefault();
	
	public byte[] transform(ClassLoader loader, String className, final Class<?> classBeingRedefined,
			ProtectionDomain protectionDomain, final byte[] classfileBuffer) {
		if (classBeingRedefined == null)
			return null;
		CtClass cc = null;
		try {
			cc = classPool.makeClass(new ByteArrayInputStream(classfileBuffer));
			classSignatures.put(classBeingRedefined.getName(), getSignature(cc));
		} catch (IOException | RuntimeException e) {
			TranformationUtils.logError(e);
		} finally {
			TranformationUtils.detachCtClass(cc);
		}
		return null;
	}
	
	private static String getSignature(CtClass cc) {
		StringBuilder strBuilder = new StringBuilder();
		for (CtMethod method : cc.getDeclaredMethods()) {
			strBuilder.append(getMethodString(method));
		}
		return strBuilder.toString();
	}
	
	private static String getMethodString(CtMethod method) {
		try {
			return Modifier.toString(method.getModifiers()) + " " + method.getReturnType().getName() + " "
					+ method.getName() + getParams(method.getParameterTypes()) + ";";
		} catch (NotFoundException e) {
			throw new RuntimeException(e);
		}
	}
	
	private static String getParams(CtClass[] parameterTypes) {
		StringBuilder strB = new StringBuilder("(");
		for (CtClass ctClass : parameterTypes) {
			strB.append(ctClass.getName());
			strB.append(", ");
		}
		strB.append(")");
		return strB.toString();
	}
	
	public static boolean hasClassChanged(Class<?> clazz) {
		String classString = classSignatures.get(clazz.getName());
		if (classString == null)
			return false;
		return !getSignature(clazz).equals(classString);
	}
	
	private static String getSignature(Class<?> cc) {
		StringBuilder strBuilder = new StringBuilder();
		for (Method method : cc.getDeclaredMethods()) {
			strBuilder.append(getMethodString(method));
		}
		return strBuilder.toString();
	}
	
	private static Object getMethodString(Method method) {
		return Modifier.toString(method.getModifiers()) + " " + method.getReturnType().getName() + " "
				+ method.getName() + getParams(method.getParameterTypes()) + ";";
	}
	
	private static String getParams(Class<?>[] parameterTypes) {
		StringBuilder strB = new StringBuilder("(");
		for (Class<?> ctClass : parameterTypes) {
			strB.append(ctClass.getName());
			strB.append(", ");
		}
		strB.append(")");
		return strB.toString();
	}
}
