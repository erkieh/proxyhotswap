package io.github.proxyhotswap;

import io.github.proxyhotswap.cglib.EnhancerTransformer;
import io.github.proxyhotswap.cglib.GeneratorSpyTransformer;
import io.github.proxyhotswap.java.JavassistSimpleJavaProxyTransformer;

import java.lang.instrument.Instrumentation;

/**
 * @author Erki Ehtla
 * 
 */
public class JavaProxyHotswapAgent {
	public static Instrumentation INSTRUMENTATION;
	
	public static void premain(String agentArgs, Instrumentation inst) {
		inst.addTransformer(new ClassfileBufferSigantureTransformer());
		inst.addTransformer(new JavassistSimpleJavaProxyTransformer());
		inst.addTransformer(new GeneratorSpyTransformer());
		inst.addTransformer(new EnhancerTransformer(inst));
		INSTRUMENTATION = inst;
	}
}
