package com.github.dcevm.test.methods;

import static com.github.dcevm.test.util.HotSwapTestHelper.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.Before;
import org.junit.Test;
import org.springframework.cglib.proxy.Callback;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

public class AddEnhancerMethodProxyTest {
	
	static public class DummyHandler implements InvocationHandler {
		private Object a;
		
		public DummyHandler(Object a) {
			this.a = a;
		}
		
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			return method.invoke(a, args);
		}
	}
	
	// Version 0
	public static class AImpl implements A {
		public int getValue1() {
			return 1;
		}
	}
	
	// Version 1
	public static class AImpl___1 implements A___1 {
		public int getValue2() {
			return 2;
		}
	}
	
	// Version 0
	public interface A {
		public int getValue1();
	}
	
	// Version 1
	public interface A___1 {
		public int getValue2();
	}
	
	@Before
	public void setUp() throws Exception {
		__toVersion__(0);
	}
	
	@Test
	public void addMethodToInterfaceAndImplementation() throws IllegalAccessException, IllegalArgumentException,
			InvocationTargetException {
		
		assert __version__() == 0;
		
		final A a = new AImpl();
		
		assertEquals(1, a.getValue1());
		
		__toVersion__(1);
		
		Method method = getMethod(a, "getValue2");
		assertEquals(2, method.invoke(a, null));
	}
	
	public static class SerializableNoOp implements Serializable, MethodInterceptor {
		private int count;
		
		@Override
		public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
			count++;
			return method.invoke(new AImpl(), args);
		}
		
		public int getInvocationCount() {
			return count;
		}
	}
	
	@Test
	public void accessNewMethodOnProxy() throws IllegalAccessException, IllegalArgumentException,
			InvocationTargetException {
		
		assert __version__() == 0;
		
		SerializableNoOp cb = new SerializableNoOp();
		final A a = createEnhancer(cb);
		
		assertEquals(0, cb.getInvocationCount());
		assertEquals(1, a.getValue1());
		assertEquals(1, cb.getInvocationCount());
		
		__toVersion__(1);
		Method method = getMethod(a, "getValue2");
		assertEquals("getValue2", method.getName());
		assertEquals(2, method.invoke(a, null));
		assertEquals(2, cb.getInvocationCount());
	}
	
	private A createEnhancer(Callback cb) {
		Enhancer enhancer = new Enhancer();
		enhancer.setSuperclass(AImpl.class);
		
		enhancer.setCallback(cb);
		final A a = (A) enhancer.create();
		return a;
	}
	
	@Test
	public void accessNewMethodOnProxyCreatedAfterSwap() throws IllegalAccessException, IllegalArgumentException,
			InvocationTargetException, IOException {
		assert __version__() == 0;
		SerializableNoOp cb = new SerializableNoOp();
		A a = createEnhancer(cb);
		
		assertEquals(0, cb.getInvocationCount());
		assertEquals(1, a.getValue1());
		assertEquals(1, cb.getInvocationCount());
		__toVersion__(1);
		
		a = createEnhancer(cb);
		
		Method method = getMethod(a, "getValue2");
		assertEquals("getValue2", method.getName());
		assertEquals(2, method.invoke(a, null));
		assertEquals(2, cb.getInvocationCount());
	}
	
	private Method getMethod(Object a, String methodName) {
		Method[] declaredMethods = a.getClass().getMethods();
		Method m = null;
		for (Method method : declaredMethods) {
			if (method.getName().equals(methodName))
				m = method;
		}
		if (m == null) {
			fail(a.getClass().getSimpleName() + " does not have method " + methodName);
		}
		return m;
	}
	
}