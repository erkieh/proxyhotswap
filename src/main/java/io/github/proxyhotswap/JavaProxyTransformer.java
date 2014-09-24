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
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import sun.misc.ProxyGenerator;

/**
 * @author Erki Ehtla
 * 
 */
public class JavaProxyTransformer implements ClassFileTransformer {
	private static final ClassPool classPool = ClassPool.getDefault();
	
	private Instrumentation inst;
	
	private static Map<Class<?>, TransformationState> transformationStates = new ConcurrentHashMap<>();
	
	public JavaProxyTransformer(Instrumentation inst) {
		this.inst = inst;
	}
	
	@Override
	public byte[] transform(ClassLoader loader, String className, final Class<?> classBeingRedefined,
			ProtectionDomain protectionDomain, final byte[] classfileBuffer) throws IllegalClassFormatException {
		if (classBeingRedefined == null || !className.startsWith("com/sun/proxy/$Proxy")) {
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
					return generateNewProxyClass(loader, className, classBeingRedefined);
				default:
					throw new RuntimeException("Unhandeled TransformationState!");
			}
		} catch (Exception e) {
			transformationStates.remove(classBeingRedefined);
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
	
	private byte[] generateNewProxyClass(ClassLoader loader, String className, final Class<?> classBeingRedefined)
			throws IOException, RuntimeException, CannotCompileException, NotFoundException {
		transformationStates.remove(classBeingRedefined);
		
		byte[] generateProxyClass = ProxyGenerator.generateProxyClass(className, classBeingRedefined.getInterfaces());
		
		CtClass cc = classPool.makeClass(new ByteArrayInputStream(generateProxyClass));
		
		String random = UUID.randomUUID().toString().replace("-", "");
		String clinitFieldName = "clinitCalled" + random;
		String clinitMethodName = "clinitMethod" + random;
		
		CtField f = new CtField(CtClass.booleanType, clinitFieldName, cc);
		f.setModifiers(Modifier.PRIVATE | Modifier.STATIC);
		// init value "true" will be inside clinit, so the field wont actually be initialized to this
		cc.addField(f, "true");
		
		// clinit method contains the setting of our clinitFieldName to true
		CtMethod method = cc.getClassInitializer().toMethod(clinitMethodName, cc);
		method.setModifiers(Modifier.PRIVATE | Modifier.STATIC);
		cc.addMethod(method);
		
		CtMethod[] methods = cc.getDeclaredMethods();
		for (CtMethod ctMethod : methods) {
			if (!ctMethod.isEmpty() && !Modifier.isStatic(ctMethod.getModifiers())) {
				ctMethod.insertBefore("if(!" + clinitFieldName + "){" + clinitMethodName + "();}");
			}
		}
		
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
