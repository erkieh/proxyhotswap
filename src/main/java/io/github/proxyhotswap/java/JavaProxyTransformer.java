package io.github.proxyhotswap.java;

import io.github.proxyhotswap.AbstractProxyTransformer;
import io.github.proxyhotswap.TransformationState;
import io.github.proxyhotswap.javassist.CtClass;
import io.github.proxyhotswap.javassist.CtMethod;
import io.github.proxyhotswap.javassist.Modifier;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.util.concurrent.ConcurrentHashMap;

import sun.misc.ProxyGenerator;

/**
 * @author Erki Ehtla
 * 
 */
public class JavaProxyTransformer extends AbstractProxyTransformer implements ClassFileTransformer {
	
	public JavaProxyTransformer(Instrumentation inst) {
		super(inst, new ConcurrentHashMap<Class<?>, TransformationState>());
	}
	
	@Override
	protected String getInitCall(CtClass cc, String random) throws Exception {
		// clinit method already contains the setting of our static clinitFieldName to true
		CtMethod method = cc.getClassInitializer().toMethod("initMethod" + random, cc);
		method.setModifiers(Modifier.PRIVATE | Modifier.STATIC);
		cc.addMethod(method);
		return method.getName() + "();";
	}
	
	protected boolean isProxy(String className, Class<?> classBeingRedefined, byte[] classfileBuffer) {
		return className.startsWith("com/sun/proxy/$Proxy");
	}
	
	@Override
	protected byte[] getNewByteCode(ClassLoader loader, String className, Class<?> classBeingRedefined) {
		return ProxyGenerator.generateProxyClass(className, classBeingRedefined.getInterfaces());
	}
}
