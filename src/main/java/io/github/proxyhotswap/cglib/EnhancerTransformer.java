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
		return strB.toString() + ";CGLIB$BIND_CALLBACKS(this);System.out.println(\"initenhancer " + strB.toString()
				+ "\");" + getClass().getName() + ".write(this);";
	}
	
	public static void write(Object classs) {
		// final String name = classs.getClass().getName();
		// new Thread() {
		// @Override
		// public void run() {
		// CtClass cc = classPool.makeClass(name);
		// try {
		// System.out.println("writing");
		// cc.writeFile("C:\\Users\\Juhtla\\Desktop\\");
		// System.out.println("written");
		// } catch (CannotCompileException | IOException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// } finally {
		// TransformationUtils.detachCtClass(cc);
		// }
		// }
		// }.start();
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
		
		byte[] invoke = (byte[]) genMethod.invoke(param.getGenerator(), param.getParam());
		return invoke;
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
