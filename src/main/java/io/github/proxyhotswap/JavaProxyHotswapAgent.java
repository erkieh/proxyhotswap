package io.github.proxyhotswap;

import java.lang.instrument.Instrumentation;

/**
 * @author Erki Ehtla
 * 
 */
public class JavaProxyHotswapAgent {
	public static Instrumentation INSTRUMENTATION;
	
	public static void premain(String agentArgs, Instrumentation inst) {
		inst.addTransformer(new JavaProxyTransformer(inst));
		INSTRUMENTATION = inst;
	}
	
	public static void agentmain(String agentArgs, Instrumentation inst) {
		inst.addTransformer(new JavaProxyTransformer(inst));
		INSTRUMENTATION = inst;
	}
}
