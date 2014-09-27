package io.github.proxyhotswap;

import io.github.proxyhotswap.javassist.CtClass;

/**
 * @author Erki Ehtla
 * 
 */
public class TranformationUtils {
	public static void detachCtClass(CtClass cc) {
		if (cc != null) {
			try {
				cc.detach();
			} catch (Exception e) {
				TranformationUtils.logError(e);
			}
		}
	}
	
	public static void logError(Exception e) {
		e.printStackTrace();
	}
}
