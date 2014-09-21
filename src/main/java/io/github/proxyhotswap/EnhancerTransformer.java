package io.github.proxyhotswap;

import io.github.proxyhotswap.javassist.CannotCompileException;
import io.github.proxyhotswap.javassist.ClassPool;
import io.github.proxyhotswap.javassist.CtClass;
import io.github.proxyhotswap.javassist.CtMethod;
import io.github.proxyhotswap.javassist.NotFoundException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Erki Ehtla
 * 
 */
public class EnhancerTransformer implements ClassFileTransformer {
	private static final ClassPool classPool = ClassPool.getDefault();
	
	private Instrumentation inst;
	private static Map<String, GeneratorParams> generatorParams = new ConcurrentHashMap<>();
	private static Map<Class<?>, TransformationState> transformationStates = new ConcurrentHashMap<>();
	
	public EnhancerTransformer(Instrumentation inst) {
		this.inst = inst;
	}
	
	public static void register(Object generatorStrategy, Object classGenerator, byte[] bytes) {
		try {
			CtClass cc = classPool.makeClass(new ByteArrayInputStream(bytes));
			String name = cc.getName();
			generatorParams.put(name, new GeneratorParams(generatorStrategy, classGenerator));
			cc.detach();
		} catch (IOException | RuntimeException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public byte[] transform(ClassLoader loader, String className, final Class<?> classBeingRedefined,
			ProtectionDomain protectionDomain, final byte[] classfileBuffer) throws IllegalClassFormatException {
		try {
			if (classBeingRedefined == null) {
				if (isClassGenerator(classfileBuffer)) {
					return addGenerationParameterCollector(classfileBuffer);
				}
			} else {
				if (generatorParams.containsKey(className.replaceAll("/", "."))) {
					TransformationState transformationState = getTransformationState(classBeingRedefined);
					try {
						switch (transformationState) {
							case NEW:
								transformationStates.put(classBeingRedefined, TransformationState.WAITING);
								// We can't to do the transformation in this event, because we can't see the changes in
								// the
								// class
								// definitons. Schedule a new redefinition event.
								scheduleRedefinition(classBeingRedefined, classfileBuffer);
								return null;
							case WAITING:
								return redefineProxy(className, classBeingRedefined, classfileBuffer);
							case REDEFINED:
								initNewProxy(classBeingRedefined);
								return null;
							default:
								throw new RuntimeException("Unhandeled TransformationState!");
						}
					} catch (Exception e) {
						transformationStates.remove(classBeingRedefined);
						throw e;
					}
				}
			}
			return null;
		} catch (Exception e) {
			e.printStackTrace();
			transformationStates.remove(classBeingRedefined);
			throw new RuntimeException(e);
		}
	}
	
	private TransformationState getTransformationState(final Class<?> classBeingRedefined) {
		TransformationState transformationState = transformationStates.get(classBeingRedefined);
		if (transformationState == null)
			transformationState = TransformationState.NEW;
		return transformationState;
	}
	
	private byte[] addGenerationParameterCollector(final byte[] classfileBuffer) throws IOException, NotFoundException,
			CannotCompileException {
		CtClass cc2 = classPool.makeClass(new ByteArrayInputStream(classfileBuffer));
		CtMethod declaredMethod = cc2.getDeclaredMethod("generate");
		declaredMethod.insertAfter(getClass().getName() + ".register($0, $1, $_);");
		byte[] result = cc2.toBytecode();
		cc2.detach();
		return result;
	}
	
	private byte[] redefineProxy(String className, final Class<?> classBeingRedefined, final byte[] classfileBuffer)
			throws IllegalAccessException, InvocationTargetException {
		GeneratorParams param = generatorParams.get(className.replaceAll("/", "."));
		if (param != null) {
			Object generator = param.getGenerator();
			Method[] methods = generator.getClass().getMethods();
			Method genMethod = null;
			for (Method method : methods) {
				if (method.getName().equals("generate") && method.getReturnType().getSimpleName().equals("byte[]")) {
					genMethod = method;
					break;
				}
			}
			if (genMethod != null) {
				byte[] invoke = (byte[]) genMethod.invoke(generator, param.getParam());
				transformationStates.put(classBeingRedefined, TransformationState.REDEFINED);
				scheduleRedefinition(classBeingRedefined, classfileBuffer);
				return invoke;
				
			} else {
				throw new RuntimeException("No generation Method found for redefinition!");
			}
		} else {
			throw new RuntimeException("No Parameters found for redefinition!");
		}
	}
	
	private boolean isClassGenerator(final byte[] classfileBuffer) throws IOException, NotFoundException {
		boolean isGenerator = false;
		CtClass cc = classPool.makeClass(new ByteArrayInputStream(classfileBuffer));
		CtClass[] interfaces = cc.getInterfaces();
		for (CtClass class1 : interfaces) {
			// We use strings because some libraries repackage cglib to a different namespace to avoid conflicts.
			if (class1.getSimpleName().contains("GeneratorStrategy")) {
				CtMethod[] declaredMethods = class1.getMethods();
				for (CtMethod method : declaredMethods) {
					if (method.getName().equals("generate") && method.getReturnType().getSimpleName().equals("byte[]")) {
						isGenerator = true;
						break;
					}
				}
			}
		}
		return isGenerator;
	}
	
	private void initNewProxy(final Class<?> classBeingRedefined) throws IllegalAccessException,
			IllegalArgumentException, InvocationTargetException {
		Method[] methods = classBeingRedefined.getDeclaredMethods();
		for (Method method : methods) {
			if (method.getName().startsWith("CGLIB$STATICHOOK")) {
				method.setAccessible(true);
				method.invoke(null);
			}
		}
		transformationStates.remove(classBeingRedefined);
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
