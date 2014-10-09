package io.github.proxyhotswap.cglib;

import io.github.proxyhotswap.TransformationUtils;
import io.github.proxyhotswap.javassist.CannotCompileException;
import io.github.proxyhotswap.javassist.ClassPool;
import io.github.proxyhotswap.javassist.CtClass;
import io.github.proxyhotswap.javassist.CtMethod;
import io.github.proxyhotswap.javassist.NotFoundException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Erki Ehtla
 * 
 */
public class GeneratorSpyTransformer implements ClassFileTransformer {
	
	private static Map<String, GeneratorParams> generatorParams = new ConcurrentHashMap<>();
	protected static final ClassPool classPool = ClassPool.getDefault();
	
	public byte[] transform(ClassLoader loader, String className, final Class<?> classBeingRedefined,
			ProtectionDomain protectionDomain, final byte[] classfileBuffer) {
		if (classBeingRedefined != null)
			return null;
		CtClass cc;
		try {
			cc = classPool.makeClass(new ByteArrayInputStream(classfileBuffer));
			CtClass[] interfaces = cc.getInterfaces();
			for (CtClass class1 : interfaces) {
				// We use strings because some libraries repackage cglib to a different namespace to avoid conflicts.
				if (class1.getSimpleName().equals("GeneratorStrategy")) {
					CtMethod[] declaredMethods = class1.getMethods();
					for (CtMethod method : declaredMethods) {
						if (method.getName().equals("generate")
								&& method.getReturnType().getSimpleName().equals("byte[]")) {
							return addGenerationParameterCollector(classfileBuffer);
						}
					}
				}
			}
		} catch (IOException | RuntimeException | NotFoundException | CannotCompileException e) {
			TransformationUtils.logError(e);
		}
		return null;
	}
	
	public static void register(Object generatorStrategy, Object classGenerator, byte[] bytes) {
		CtClass cc = null;
		try {
			cc = classPool.makeClass(new ByteArrayInputStream(bytes));
			generatorParams.put(cc.getName(), new GeneratorParams(generatorStrategy, classGenerator));
		} catch (IOException | RuntimeException e) {
			TransformationUtils.logError(e);
		} finally {
			TransformationUtils.detachCtClass(cc);
		}
	}
	
	private byte[] addGenerationParameterCollector(final byte[] classfileBuffer) throws IOException, NotFoundException,
			CannotCompileException {
		CtClass cc2 = classPool.makeClass(new ByteArrayInputStream(classfileBuffer));
		try {
			CtMethod declaredMethod = cc2.getDeclaredMethod("generate");
			declaredMethod.insertAfter(getClass().getName() + ".register($0, $1, $_);");
			return cc2.toBytecode();
		} finally {
			TransformationUtils.detachCtClass(cc2);
		}
	}
	
	public static Map<String, GeneratorParams> getGeneratorParams() {
		return generatorParams;
	}
	
}
