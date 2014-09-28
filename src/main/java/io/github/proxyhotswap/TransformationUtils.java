package io.github.proxyhotswap;

import io.github.proxyhotswap.javassist.CtClass;

/**
 * @author Erki Ehtla
 * 
 */
public class TransformationUtils {
	public static void detachCtClass(CtClass cc) {
		if (cc != null) {
			try {
				cc.detach();
			} catch (Exception e) {
				TransformationUtils.logError(e);
			}
		}
	}
	
	public static void logError(Exception e) {
		e.printStackTrace();
	}
}
