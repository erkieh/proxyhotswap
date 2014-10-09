package io.github.proxyhotswap.cglib;

import io.github.proxyhotswap.AbstractProxyTransformer;
import io.github.proxyhotswap.TransformationState;
import io.github.proxyhotswap.TransformationUtils;
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
		StringBuilder strB = new StringBuilder();
		for (CtMethod ctMethod : methods) {
			if (ctMethod.getName().startsWith("CGLIB$STATICHOOK")) {
				ctMethod.insertAfter(INIT_FIELD_PREFIX + random + "=true;");
				strB.insert(0, ctMethod.getName() + "();");
				break;
			}
		}
		
		if (strB.length() == 0)
			throw new RuntimeException("Could not find CGLIB$STATICHOOK method");
		return strB.toString() + ";CGLIB$BIND_CALLBACKS(this);";
	}
	
	@Override
	protected byte[] getNewByteCode(ClassLoader loader, String className, Class<?> classBeingRedefined)
			throws Exception {
		GeneratorParams param = GeneratorSpyTransformer.getGeneratorParams().get(
				TransformationUtils.getClassName(className));
		if (param == null)
			throw new RuntimeException("No Parameters found for redefinition!");
		
		Method genMethod = getGenerateMethod(param.getGenerator());
		if (genMethod == null)
			throw new RuntimeException("No generation Method found for redefinition!");
		
		byte[] invoke = (byte[]) genMethod.invoke(param.getGenerator(), param.getParam());
		return invoke;
	}
	
	@Override
	protected CtClass getCtClass(byte[] newByteCode, String className) throws Exception {
		// can use get because generator parameter spy has already loaded it to the clas pool
		return classPool.get(TransformationUtils.getClassName(className));
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
