package io.github.proxyhotswap.cglib;

import io.github.proxyhotswap.AbstractProxyTransformer;
import io.github.proxyhotswap.TransformationState;
import io.github.proxyhotswap.javassist.CtClass;
import io.github.proxyhotswap.javassist.CtMethod;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Erki Ehtla
 * 
 */
public class EnhancerTransformer extends AbstractProxyTransformer {
	private GeneratorSpyTransformer generatorSpyTransformer = new GeneratorSpyTransformer();
	
	public EnhancerTransformer(Instrumentation inst) {
		super(inst, new ConcurrentHashMap<Class<?>, TransformationState>());
	}
	
	@Override
	protected boolean isProxy(String className, Class<?> classBeingRedefined, byte[] classfileBuffer) {
		return GeneratorSpyTransformer.getGeneratorParams().containsKey(className.replaceAll("/", "."));
	}
	
	@Override
	protected String getInitCall(CtClass cc, String random) throws Exception {
		CtMethod[] methods = cc.getDeclaredMethods();
		CtMethod staticHookMethod = null;
		for (CtMethod ctMethod : methods) {
			if (ctMethod.getName().startsWith("CGLIB$STATICHOOK")) {
				ctMethod.insertAfter(INIT_FIELD_PREFIX + random + "=true;");
				staticHookMethod = ctMethod;
			}
		}
		
		if (staticHookMethod == null)
			throw new RuntimeException("Could not find CGLIB$STATICHOOK method");
		return staticHookMethod.getName() + "();CGLIB$BIND_CALLBACKS(this);";
	}
	
	@Override
	protected byte[] getNewByteCode(ClassLoader loader, String className, Class<?> classBeingRedefined)
			throws Exception {
		GeneratorParams param = GeneratorSpyTransformer.getGeneratorParams().get(className.replaceAll("/", "."));
		if (param == null)
			throw new RuntimeException("No Parameters found for redefinition!");
		
		Method genMethod = getGenerateMethod(param.getGenerator());
		if (genMethod == null)
			throw new RuntimeException("No generation Method found for redefinition!");
		
		return (byte[]) genMethod.invoke(param.getGenerator(), param.getParam());
	}
	
	private Method getGenerateMethod(Object generator) {
		Method generateMethod = null;
		Method[] methods = generator.getClass().getMethods();
		for (Method method : methods) {
			if (method.getName().equals("generate") && method.getReturnType().getSimpleName().equals("byte[]")) {
				generateMethod = method;
				break;
			}
		}
		return generateMethod;
	}
	
}
