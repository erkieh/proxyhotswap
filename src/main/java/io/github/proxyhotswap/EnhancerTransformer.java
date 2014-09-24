package io.github.proxyhotswap;

import io.github.proxyhotswap.javassist.CannotCompileException;
import io.github.proxyhotswap.javassist.ClassPool;
import io.github.proxyhotswap.javassist.CtClass;
import io.github.proxyhotswap.javassist.CtField;
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
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.UUID;
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
				if (!generatorParams.containsKey(className.replaceAll("/", ".")))
					return null;
				
				TransformationState transformationState = getTransformationState(classBeingRedefined);
				switch (transformationState) {
					case NEW:
						transformationStates.put(classBeingRedefined, TransformationState.WAITING);
						// We can't to do the transformation in this event, because we can't see the changes in
						// the class definitons. Schedule a new redefinition event.
						scheduleRedefinition(classBeingRedefined, classfileBuffer);
						return null;
					case WAITING:
						return redefineProxy(className, classBeingRedefined, classfileBuffer);
					default:
						throw new RuntimeException("Unhandeled TransformationState!");
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
			throws IllegalAccessException, InvocationTargetException, CannotCompileException, IOException,
			RuntimeException {
		transformationStates.remove(classBeingRedefined);
		
		GeneratorParams param = generatorParams.get(className.replaceAll("/", "."));
		if (param == null)
			throw new RuntimeException("No Parameters found for redefinition!");
		
		Method genMethod = getGenerateMethod(param.getGenerator());
		if (genMethod == null)
			throw new RuntimeException("No generation Method found for redefinition!");
		
		byte[] result = (byte[]) genMethod.invoke(param.getGenerator(), param.getParam());
		
		CtClass cc = classPool.makeClass(new ByteArrayInputStream(result));
		
		String random = UUID.randomUUID().toString().replace("-", "");
		String clinitFieldName = "clinitCalled" + random;
		CtField f = new CtField(CtClass.booleanType, clinitFieldName, cc);
		f.setModifiers(Modifier.PRIVATE | Modifier.STATIC);
		// init value "true" will be inside clinit, so the field wont actually be initialized to this
		cc.addField(f, "true");
		
		CtMethod[] methods = cc.getDeclaredMethods();
		CtMethod staticHookMethod = null;
		for (CtMethod ctMethod : methods) {
			if (ctMethod.getName().startsWith("CGLIB$STATICHOOK")) {
				staticHookMethod = ctMethod;
				staticHookMethod.insertAfter(clinitFieldName + "=true;");
			}
		}
		
		if (staticHookMethod == null)
			throw new RuntimeException("Could not find CGLIB$STATICHOOK method");
		
		for (CtMethod ctMethod : methods) {
			if (!ctMethod.isEmpty() && !Modifier.isStatic(ctMethod.getModifiers())) {
				ctMethod.insertBefore("if(!" + clinitFieldName + "){" + staticHookMethod.getName()
						+ "();CGLIB$BIND_CALLBACKS(this);}");
			}
		}
		result = cc.toBytecode();
		cc.detach();
		return result;
		
	}
	
	private Method getGenerateMethod(Object generator) {
		Method genMethod = null;
		Method[] methods = generator.getClass().getMethods();
		for (Method method : methods) {
			if (method.getName().equals("generate") && method.getReturnType().getSimpleName().equals("byte[]")) {
				genMethod = method;
				break;
			}
		}
		return genMethod;
	}
	
	private boolean isClassGenerator(final byte[] classfileBuffer) throws IOException, NotFoundException {
		CtClass cc = classPool.makeClass(new ByteArrayInputStream(classfileBuffer));
		CtClass[] interfaces = cc.getInterfaces();
		for (CtClass class1 : interfaces) {
			// We use strings because some libraries repackage cglib to a different namespace to avoid conflicts.
			if (class1.getSimpleName().equals("GeneratorStrategy")) {
				CtMethod[] declaredMethods = class1.getMethods();
				for (CtMethod method : declaredMethods) {
					if (method.getName().equals("generate") && method.getReturnType().getSimpleName().equals("byte[]")) {
						return true;
					}
				}
			}
		}
		return false;
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
